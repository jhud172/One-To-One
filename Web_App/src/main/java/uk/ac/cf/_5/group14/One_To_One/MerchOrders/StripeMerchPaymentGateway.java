package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class StripeMerchPaymentGateway implements MerchPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeMerchPaymentGateway.class);
    private static final String STRIPE_API_BASE = "https://api.stripe.com/v1";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(java.time.Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${app.payments.stripe.secret-key:}")
    private String secretKey;

    @Value("${app.payments.currency:gbp}")
    private String currency;

    @Override
    public String currency() { return currency; }

    @Override
    public boolean isConfigured() {
        return !normalizedSecretKey().isBlank();
    }

    @Override
    public boolean isSimulationMode() {
        return "false".equalsIgnoreCase(normalizedSecretKey());
    }

    @Override
    public String providerName() {
        return isSimulationMode() ? "Simulated checkout" : "Stripe";
    }

    @Override
    public MerchHostedCheckoutSession createCheckoutSession(MerchOrder order,
                                                            MerchProduct product,
                                                            int quantity,
                                                            String successUrl,
                                                            String cancelUrl) {
        ensureConfigured();

        Request request = buildCheckoutRequest(order, successUrl, cancelUrl);
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                log.warn("Stripe checkout session creation failed with status {}", response.code());
                throw new IllegalStateException("Secure checkout is unavailable right now.");
            }

            JsonNode root = mapper.readTree(body);
            String sessionId = root.path("id").asText("");
            String checkoutUrl = root.path("url").asText("");
            if (sessionId.isBlank() || checkoutUrl.isBlank()) {
                throw new IllegalStateException("Secure checkout response was incomplete.");
            }
            return new MerchHostedCheckoutSession("Stripe", sessionId, checkoutUrl);
        } catch (IOException ex) {
            throw new IllegalStateException("Secure checkout is unavailable right now.", ex);
        }
    }

    Request buildCheckoutRequest(MerchOrder order, String successUrl, String cancelUrl) {
        if (order.getItems().size() != 1) throw new IllegalArgumentException("Checkout needs one saved order item.");
        MerchOrderItem item = order.getItems().get(0);

        FormBody.Builder form = new FormBody.Builder()
                .add("mode", "payment")
                .add("expires_at", String.valueOf(order.getCreatedAt().plusSeconds(MerchOrderServiceImpl.PENDING_PAYMENT_TTL_SECONDS).getEpochSecond()))
                .add("success_url", order.getCheckoutSuccessUrl() == null ? successUrl : order.getCheckoutSuccessUrl())
                .add("cancel_url", order.getCheckoutCancelUrl() == null ? cancelUrl : order.getCheckoutCancelUrl())
                .add("client_reference_id", String.valueOf(order.getId()))
                .add("metadata[scope]", "merch")
                .add("metadata[orderId]", String.valueOf(order.getId()))
                .add("payment_intent_data[metadata][scope]", "merch")
                .add("payment_intent_data[metadata][orderId]", String.valueOf(order.getId()))
                .add("line_items[0][quantity]", String.valueOf(item.getQuantity()))
                .add("line_items[0][price_data][currency]", order.getCheckoutCurrency() == null ? currency : order.getCheckoutCurrency())
                .add("line_items[0][price_data][unit_amount]", String.valueOf(toMinorUnits(item.getPriceSnapshot())))
                .add("line_items[0][price_data][product_data][name]", item.getProductNameSnapshot());

        return new Request.Builder()
                .url(STRIPE_API_BASE + "/checkout/sessions")
                .post(form.build())
                .addHeader("Authorization", "Bearer " + normalizedSecretKey())
                .addHeader("Idempotency-Key", "merch-checkout-" + order.getId())
                .build();

    }

    @Override
    public MerchPaymentVerification verifyCheckoutSession(String paymentReference) {
        ensureConfigured();
        if (paymentReference == null || !paymentReference.matches("cs_[A-Za-z0-9_]{1,240}")) {
            return new MerchPaymentVerification(false, providerName(), paymentReference, "Missing payment reference.");
        }

        Request request = new Request.Builder()
                .url(STRIPE_API_BASE + "/checkout/sessions/" + paymentReference)
                .get()
                .addHeader("Authorization", "Bearer " + normalizedSecretKey())
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "{}";
            if (!response.isSuccessful()) {
                log.warn("Stripe checkout session verification failed with status {}", response.code());
                return new MerchPaymentVerification(false, providerName(), paymentReference, "Secure checkout could not be verified.");
            }

            JsonNode root = mapper.readTree(body);
            boolean paid = isPaidMerchSession(root, paymentReference);
            String message = paid ? "Payment confirmed." : "Payment was not completed.";
            return new MerchPaymentVerification(paid, providerName(), paymentReference, message);
        } catch (IOException e) {
            log.warn("Stripe checkout session verification failed", e);
            return new MerchPaymentVerification(false, providerName(), paymentReference, "Secure checkout could not be verified.");
        }
    }

    @Override
    public boolean expireCheckoutSession(String reference) {
        if (!isConfigured() || isSimulationMode() || reference == null
                || !reference.matches("cs_[A-Za-z0-9_]{1,240}")) return false;
        Request read = new Request.Builder().url(STRIPE_API_BASE + "/checkout/sessions/" + reference)
                .addHeader("Authorization", "Bearer " + normalizedSecretKey()).get().build();
        try (Response response = client.newCall(read).execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            JsonNode session = mapper.readTree(response.body().string());
            if (!reference.equals(session.path("id").asText(""))
                    || !"merch".equals(session.path("metadata").path("scope").asText(""))) return false;
            if (isExpiredUnpaidSession(session, reference)) return true;
            if (!"open".equals(session.path("status").asText(""))) return false;
        } catch (IOException ex) {
            log.warn("Could not check hosted checkout before cancellation"); return false;
        }
        Request expire = new Request.Builder().url(STRIPE_API_BASE + "/checkout/sessions/" + reference + "/expire")
                .addHeader("Authorization", "Bearer " + normalizedSecretKey()).post(new FormBody.Builder().build()).build();
        try (Response response = client.newCall(expire).execute()) {
            return response.isSuccessful() && response.body() != null
                    && isExpiredUnpaidSession(mapper.readTree(response.body().string()), reference);
        } catch (IOException ex) {
            log.warn("Could not close hosted checkout; reservation retained"); return false;
        }
    }

    static boolean isExpiredUnpaidSession(JsonNode session, String reference) {
        return reference != null && reference.equals(session.path("id").asText(""))
                && "expired".equals(session.path("status").asText(""))
                && "unpaid".equals(session.path("payment_status").asText(""))
                && "merch".equals(session.path("metadata").path("scope").asText(""));
    }

    static boolean isPaidMerchSession(JsonNode root, String reference) {
        return "paid".equalsIgnoreCase(root.path("payment_status").asText(""))
                && reference != null && reference.equals(root.path("id").asText(""))
                && "payment".equals(root.path("mode").asText(""))
                && "merch".equals(root.path("metadata").path("scope").asText(""));
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Secure checkout is not configured yet.");
        }
    }

    private String normalizedSecretKey() {
        return secretKey == null ? "" : secretKey.trim();
    }

    private long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
