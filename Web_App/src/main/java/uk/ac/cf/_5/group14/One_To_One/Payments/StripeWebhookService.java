package uk.ac.cf._5.group14.One_To_One.Payments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private static final long SIGNATURE_TOLERANCE_SECONDS = 300L;

    private final ObjectMapper mapper = new ObjectMapper();
    private final PlatformSubscriptionService platformSubscriptionService;
    private final PaymentProviderService paymentProviderService;
    private final StripeWebhookEventStore eventStore;
    private final Clock clock;

    @Value("${app.payments.stripe.webhook-secret:}")
    private String webhookSecret;

    public StripeWebhookService(PlatformSubscriptionService platformSubscriptionService,
                                PaymentProviderService paymentProviderService,
                                StripeWebhookEventStore eventStore,
                                Clock clock) {
        this.platformSubscriptionService = platformSubscriptionService;
        this.paymentProviderService = paymentProviderService;
        this.eventStore = eventStore;
        this.clock = clock;
    }

    public boolean isConfigured() {
        String normalized = webhookSecret == null ? "" : webhookSecret.trim();
        return !normalized.isBlank() && !"false".equalsIgnoreCase(normalized);
    }

    public synchronized StripeWebhookHandlingResult handleWebhook(String payload, String signatureHeader) {
        if (!isConfigured()) {
            return new StripeWebhookHandlingResult(false, "Stripe webhook secret is not configured.");
        }
        if (!verifySignature(payload, signatureHeader)) {
            return new StripeWebhookHandlingResult(false, "Invalid Stripe webhook signature.");
        }

        try {
            JsonNode root = mapper.readTree(payload == null ? "{}" : payload);
            String eventId = root.path("id").asText("");
            String type = root.path("type").asText("");
            if (!eventId.isBlank() && eventStore.hasProcessed(eventId)) {
                return new StripeWebhookHandlingResult(true, "Duplicate Stripe event ignored.");
            }
            JsonNode object = root.path("data").path("object");

            StripeWebhookHandlingResult result = switch (type) {
                case "checkout.session.completed" -> handleCheckoutSessionCompleted(object);
                case "customer.subscription.updated" -> handleSubscriptionUpdated(object);
                case "customer.subscription.deleted" -> handleSubscriptionDeleted(object);
                case "invoice.payment_failed" -> handleInvoicePaymentFailed(object);
                case "invoice.payment_succeeded" -> handleInvoicePaymentSucceeded(object);
                default -> new StripeWebhookHandlingResult(true, "Ignored event type: " + type);
            };
            if (result.accepted() && !eventId.isBlank()) {
                eventStore.recordProcessed(eventId, type);
            }
            return result;
        } catch (Exception e) {
            log.warn("Stripe webhook handling failed", e);
            return new StripeWebhookHandlingResult(false, "Webhook payload could not be processed.");
        }
    }

    private StripeWebhookHandlingResult handleCheckoutSessionCompleted(JsonNode object) {
        JsonNode metadata = object.path("metadata");
        if (!"platform_premium".equalsIgnoreCase(metadata.path("scope").asText(""))) {
            return new StripeWebhookHandlingResult(true, "Ignoring non-platform checkout session.");
        }

        Long userId = parseLong(metadata.path("userId").asText(object.path("client_reference_id").asText("")));
        PlatformPlan plan = parsePlan(metadata.path("plan").asText(""));
        String sessionId = object.path("id").asText("");

        if (userId == null || plan == null || sessionId.isBlank()) {
            return new StripeWebhookHandlingResult(false, "Platform checkout session metadata is incomplete.");
        }

        PaymentSubscriptionVerification verification = paymentProviderService.verifyCheckoutSession(sessionId);
        if (!verification.active()) {
            return new StripeWebhookHandlingResult(false, verification.message());
        }
        if (!verification.belongsTo(userId, plan)) {
            return new StripeWebhookHandlingResult(false, "Verified checkout does not match event metadata.");
        }

        platformSubscriptionService.activateSubscription(
                userId,
                plan,
                verification.customerId(),
                verification.subscriptionId(),
                verification.currentPeriodEnd());
        return new StripeWebhookHandlingResult(true, "Platform subscription activated from checkout completion.");
    }

    private StripeWebhookHandlingResult handleSubscriptionUpdated(JsonNode object) {
        String subscriptionId = object.path("id").asText("");
        Instant currentPeriodEnd = StripeSubscriptionPayload.periodEnd(object);
        boolean cancelAtPeriodEnd = object.path("cancel_at_period_end").asBoolean(false);
        String status = object.path("status").asText("");
        boolean active = "active".equalsIgnoreCase(status) || "trialing".equalsIgnoreCase(status);

        platformSubscriptionService.syncProviderSubscription(subscriptionId, currentPeriodEnd, cancelAtPeriodEnd, active);
        return new StripeWebhookHandlingResult(true, "Platform subscription sync applied.");
    }

    private StripeWebhookHandlingResult handleSubscriptionDeleted(JsonNode object) {
        String subscriptionId = object.path("id").asText("");
        Instant currentPeriodEnd = StripeSubscriptionPayload.periodEnd(object);
        platformSubscriptionService.cancelByProviderSubscriptionId(subscriptionId, currentPeriodEnd);
        return new StripeWebhookHandlingResult(true, "Platform subscription cancellation applied.");
    }

    private StripeWebhookHandlingResult handleInvoicePaymentFailed(JsonNode object) {
        String subscriptionId = invoiceSubscriptionId(object);
        if (subscriptionId.isBlank()) {
            return new StripeWebhookHandlingResult(false, "Stripe invoice subscription id is missing.");
        }
        platformSubscriptionService.markPaymentFailed(subscriptionId);
        return new StripeWebhookHandlingResult(true, "Platform subscription marked past due.");
    }

    private StripeWebhookHandlingResult handleInvoicePaymentSucceeded(JsonNode object) {
        String subscriptionId = invoiceSubscriptionId(object);
        if (subscriptionId.isBlank()) {
            return new StripeWebhookHandlingResult(false, "Stripe invoice subscription id is missing.");
        }
        platformSubscriptionService.markPaymentRecovered(subscriptionId);
        return new StripeWebhookHandlingResult(true, "Platform subscription payment recovery applied.");
    }

    private String invoiceSubscriptionId(JsonNode object) {
        String direct = object.path("subscription").asText("");
        if (!direct.isBlank()) {
            return direct;
        }
        return object.path("parent").path("subscription_details").path("subscription").asText("");
    }

    private boolean verifySignature(String payload, String signatureHeader) {
        Map<String, String> values = parseSignatureHeader(signatureHeader);
        String timestamp = values.get("t");
        String expectedSignature = values.get("v1");
        if (timestamp == null || expectedSignature == null) {
            return false;
        }

        long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            return false;
        }

        long nowSeconds = Instant.now(clock).getEpochSecond();
        if (timestampSeconds < nowSeconds - SIGNATURE_TOLERANCE_SECONDS
                || timestampSeconds > nowSeconds + SIGNATURE_TOLERANCE_SECONDS) {
            return false;
        }

        String signedPayload = timestamp + "." + (payload == null ? "" : payload);
        String computed = computeHmacSha256(webhookSecret, signedPayload);
        return computed != null && MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseSignatureHeader(String signatureHeader) {
        Map<String, String> values = new HashMap<>();
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return values;
        }
        for (String part : signatureHeader.split(",")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2) {
                values.put(pair[0].trim(), pair[1].trim());
            }
        }
        return values;
    }

    private String computeHmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.warn("Stripe webhook signature generation failed", e);
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private PlatformPlan parsePlan(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : PlatformPlan.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Instant parseEpochSeconds(long value) {
        return value > 0L ? Instant.ofEpochSecond(value) : null;
    }
}
