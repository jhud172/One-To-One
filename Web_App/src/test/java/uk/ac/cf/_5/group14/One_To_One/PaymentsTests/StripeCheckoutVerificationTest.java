package uk.ac.cf._5.group14.One_To_One.PaymentsTests;

import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.cf._5.group14.One_To_One.Payments.StripePaymentProviderService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;

import static org.assertj.core.api.Assertions.assertThat;

class StripeCheckoutVerificationTest {
    private static final String SESSION = """
            {"status":"complete","payment_status":"paid","mode":"subscription",
             "customer":"cus_test","subscription":"sub_test","client_reference_id":"5",
             "metadata":{"scope":"platform_premium","userId":"5","plan":"MONTHLY"}}
            """;

    private StripePaymentProviderService provider(String sessionBody) {
        var provider = new StripePaymentProviderService();
        ReflectionTestUtils.setField(provider, "secretKey", "local-checkout-fixture");
        var interceptedClient = new OkHttpClient.Builder().addInterceptor(chain -> {
            String body = chain.request().url().encodedPath().contains("/checkout/sessions/")
                    ? sessionBody : "{\"status\":\"active\",\"items\":{\"data\":[{\"current_period_end\":1900000000}]}}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("Local test fixture").body(ResponseBody.create(body, MediaType.get("application/json"))).build();
        }).build();
        ReflectionTestUtils.setField(provider, "client", interceptedClient);
        return provider;
    }

    @Test
    void verifiedPaidSessionCarriesItsAuthoritativeOwnerAndPlan() {
        var result = provider(SESSION).verifyCheckoutSession("cs_test_fixture");
        assertThat(result.active()).isTrue();
        assertThat(result.belongsTo(5L, PlatformPlan.MONTHLY)).isTrue();
        assertThat(result.belongsTo(6L, PlatformPlan.MONTHLY)).isFalse();
        assertThat(result.belongsTo(5L, PlatformPlan.YEARLY)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"unpaid", "other-scope", "wrong-reference", "missing-owner", "infinite-plan"})
    void rejectsUnpaidOrUnboundSession(String defect) {
        String body = switch (defect) {
            case "unpaid" -> SESSION.replace("\"paid\"", "\"unpaid\"");
            case "other-scope" -> SESSION.replace("platform_premium", "merch");
            case "wrong-reference" -> SESSION.replace("\"client_reference_id\":\"5\"", "\"client_reference_id\":\"99\"");
            case "missing-owner" -> SESSION.replace("\"userId\":\"5\"", "\"userId\":\"\"");
            default -> SESSION.replace("MONTHLY", "INFINITE");
        };
        assertThat(provider(body).verifyCheckoutSession("cs_test_fixture").active()).isFalse();
    }
}
