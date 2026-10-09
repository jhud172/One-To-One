package uk.ac.cf._5.group14.One_To_One.PaymentsTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.cf._5.group14.One_To_One.Payments.PaymentProviderService;
import uk.ac.cf._5.group14.One_To_One.Payments.PaymentSubscriptionVerification;
import uk.ac.cf._5.group14.One_To_One.Payments.StripeWebhookHandlingResult;
import uk.ac.cf._5.group14.One_To_One.Payments.StripeWebhookEventStore;
import uk.ac.cf._5.group14.One_To_One.Payments.StripeWebhookService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private PlatformSubscriptionService platformSubscriptionService;

    @Mock
    private PaymentProviderService paymentProviderService;

    @Mock
    private StripeWebhookEventStore eventStore;

    @Test
    void handleWebhook_activatesSubscriptionOnCompletedCheckoutSession() throws Exception {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");

        String payload = """
                {
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_test_123",
                      "client_reference_id": "9",
                      "metadata": {
                        "scope": "platform_premium",
                        "plan": "MONTHLY",
                        "userId": "9"
                      }
                    }
                  }
                }
                """;

        when(paymentProviderService.verifyCheckoutSession("cs_test_123"))
                .thenReturn(new PaymentSubscriptionVerification(true, "Stripe", "cus_1", "sub_1", Instant.parse("2026-04-27T00:00:00Z"), "Subscription activated.", 9L, uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan.MONTHLY));

        StripeWebhookHandlingResult result = service.handleWebhook(payload, signature("whsec_test", payload, currentTimestamp()));

        assertThat(result.accepted()).isTrue();
        verify(platformSubscriptionService).activateSubscription(
                9L,
                uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan.MONTHLY,
                "cus_1",
                "sub_1",
                Instant.parse("2026-04-27T00:00:00Z"));
    }

    @Test
    void handleWebhook_syncsExistingSubscriptionFromProviderUpdate() throws Exception {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");

        String payload = """
                {
                  "type": "customer.subscription.updated",
                  "data": {
                    "object": {
                      "id": "sub_1",
                      "status": "active",
                      "cancel_at_period_end": true,
                      "current_period_end": 1777248000
                    }
                  }
                }
                """;

        StripeWebhookHandlingResult result = service.handleWebhook(payload, signature("whsec_test", payload, currentTimestamp()));

        assertThat(result.accepted()).isTrue();
        verify(platformSubscriptionService).syncProviderSubscription(
                "sub_1",
                Instant.ofEpochSecond(1777248000L),
                true,
                true);
    }

    @Test
    void handleWebhook_processesTheSameStripeEventOnlyOnce() throws Exception {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");

        String payload = """
                {
                  "id": "evt_duplicate_1",
                  "type": "customer.subscription.updated",
                  "data": {
                    "object": {
                      "id": "sub_1",
                      "status": "active",
                      "cancel_at_period_end": false,
                      "current_period_end": 1777248000
                    }
                  }
                }
                """;
        String timestamp = currentTimestamp();
        String signature = signature("whsec_test", payload, timestamp);
        when(eventStore.hasProcessed("evt_duplicate_1")).thenReturn(false, true);

        StripeWebhookHandlingResult first = service.handleWebhook(payload, signature);
        StripeWebhookHandlingResult duplicate = service.handleWebhook(payload, signature);

        assertThat(first.accepted()).isTrue();
        assertThat(duplicate.accepted()).isTrue();
        assertThat(duplicate.message()).containsIgnoringCase("duplicate");
        verify(platformSubscriptionService, times(1)).syncProviderSubscription(
                "sub_1",
                Instant.ofEpochSecond(1777248000L),
                false,
                true);
    }

    @Test
    void handleWebhook_appliesPaymentFailureAndSuccessfulRetry() throws Exception {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");

        String failedPayload = """
                {
                  "id": "evt_invoice_failed_1",
                  "type": "invoice.payment_failed",
                  "data": {
                    "object": {
                      "subscription": "sub_1"
                    }
                  }
                }
                """;
        String succeededPayload = """
                {
                  "id": "evt_invoice_succeeded_1",
                  "type": "invoice.payment_succeeded",
                  "data": {
                    "object": {
                      "subscription": "sub_1"
                    }
                  }
                }
                """;

        StripeWebhookHandlingResult failed = service.handleWebhook(
                failedPayload,
                signature("whsec_test", failedPayload, currentTimestamp()));
        StripeWebhookHandlingResult retried = service.handleWebhook(
                succeededPayload,
                signature("whsec_test", succeededPayload, currentTimestamp()));

        assertThat(failed.accepted()).isTrue();
        assertThat(retried.accepted()).isTrue();
        verify(platformSubscriptionService).markPaymentFailed("sub_1");
        verify(platformSubscriptionService).markPaymentRecovered("sub_1");
    }

    @Test
    void handleWebhook_rejectsInvalidSignature() {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");

        StripeWebhookHandlingResult result = service.handleWebhook("{\"type\":\"checkout.session.completed\"}", "t=1,v1=invalid");

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("signature");
    }

    @Test
    void handleWebhook_rejectsAValidButStaleSignature() throws Exception {
        StripeWebhookService service = new StripeWebhookService(
                platformSubscriptionService,
                paymentProviderService,
                eventStore,
                Clock.systemUTC());
        ReflectionTestUtils.setField(service, "webhookSecret", "whsec_test");
        String payload = "{\"type\":\"sandbox.replay\"}";
        String staleTimestamp = String.valueOf(Instant.now().minusSeconds(301).getEpochSecond());

        StripeWebhookHandlingResult result = service.handleWebhook(
                payload,
                signature("whsec_test", payload, staleTimestamp));

        assertThat(result.accepted()).isFalse();
        assertThat(result.message()).contains("signature");
    }

    private String currentTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private String signature(String secret, String payload, String timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }
}
