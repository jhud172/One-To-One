package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class StripeMerchVerificationTest {
    @Test
    void retryRequestUsesStableKeyAndOrderSnapshotWithoutNetwork() throws Exception {
        var gateway = new StripeMerchPaymentGateway();
        org.springframework.test.util.ReflectionTestUtils.setField(gateway, "currency", "gbp");
        var product = new uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct();
        product.setName("Original item"); product.setPrice(new java.math.BigDecimal("12.35"));
        var order = new MerchOrder(); order.setId(44L); order.setCreatedAt(java.time.Instant.parse("2026-10-04T12:00:00Z"));
        order.setCheckoutCurrency("gbp"); order.setCheckoutSuccessUrl("https://example.test/success");
        order.setCheckoutCancelUrl("https://example.test/cancel");
        var item = new MerchOrderItem(); item.setProduct(product); item.setQuantity(2);
        item.setProductNameSnapshot(product.getName()); item.setPriceSnapshot(product.getPrice()); order.getItems().add(item);
        var first = gateway.buildCheckoutRequest(order, "https://example.test/success", "https://example.test/cancel");
        product.setName("Changed live product"); product.setPrice(new java.math.BigDecimal("99.99"));
        org.springframework.test.util.ReflectionTestUtils.setField(gateway, "currency", "eur");
        var retry = gateway.buildCheckoutRequest(order, "https://example.test/changed", "https://example.test/changed");
        var originalBody = new okio.Buffer(); first.body().writeTo(originalBody);
        var retryBody = new okio.Buffer(); retry.body().writeTo(retryBody);
        assertThat(first.header("Idempotency-Key")).isEqualTo("merch-checkout-44");
        assertThat(retry.header("Idempotency-Key")).isEqualTo(first.header("Idempotency-Key"));
        assertThat(retryBody.readUtf8()).isEqualTo(originalBody.readUtf8()).contains("1235").contains("Original%20item");
    }

    @Test
    void completedUnpaidAndUnmatchedSessionsCannotConfirmAnOrder() {
        var root = new ObjectMapper().createObjectNode();
        root.put("id", "cs_test_owned"); root.put("mode", "payment"); root.put("status", "complete");
        root.put("payment_status", "unpaid"); root.putObject("metadata").put("scope", "merch");
        assertThat(StripeMerchPaymentGateway.isPaidMerchSession(root, "cs_test_owned")).isFalse();
        root.put("payment_status", "paid");
        assertThat(StripeMerchPaymentGateway.isPaidMerchSession(root, "cs_test_owned")).isTrue();
        assertThat(StripeMerchPaymentGateway.isPaidMerchSession(root, "cs_test_other")).isFalse();
        root.put("mode", "subscription");
        assertThat(StripeMerchPaymentGateway.isPaidMerchSession(root, "cs_test_owned")).isFalse();
    }
}
