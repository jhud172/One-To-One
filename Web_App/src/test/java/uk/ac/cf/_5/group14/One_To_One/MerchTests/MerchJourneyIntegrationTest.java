package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Merch.*;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.payments.stripe.secret-key=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchJourneyIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private MerchProductRepository products;
    @Autowired private MerchOrderService orders;
    @Autowired private MerchOrderRepository orderRepository;
    @Autowired private UserRepository users;
    @Autowired private uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodRepository cards;
    @Autowired private java.time.Clock clock;

    private User client() {
        String key = UUID.randomUUID().toString().replace("-", "");
        var user = new User(key + "@example.com", "Merch", "Fixture", "merch_" + key, "fixture-only");
        user.setRole(Role.CLIENT); return users.save(user);
    }

    private MerchProduct product() {
        var product = new MerchProduct(); product.setName("Unique training product " + UUID.randomUUID());
        product.setCategory("Equipment"); product.setPrice(new BigDecimal("12.35")); product.setStockQuantity(10);
        return products.save(product);
    }

    @Test
    void checkoutRendersKeyQuantityAndTranslatedActionsInEveryLocale() throws Exception {
        User owner = client(); MerchProduct product = product();
        mvc.perform(get("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/merch/" + product.getId() + "/buy?checkoutKey=*"));
        for (String locale : java.util.List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var html = org.jsoup.Jsoup.parse(mvc.perform(get("/merch/" + product.getId() + "/buy")
                    .param("lang", locale).param("checkoutKey", UUID.randomUUID().toString()).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            var form = html.selectFirst("#merch-checkout-form");
            assertThat(form).isNotNull();
            assertThat(form.select("input[name=checkoutKey]")).hasSize(1);
            assertThat(form.selectFirst("input[name=checkoutKey]").val()).matches("[0-9a-f-]{36}");
            assertThat(form.selectFirst("input[name=quantity]").val()).isEqualTo("1");
            assertThat(form.selectFirst("input[name=expectedUnitMinor]").val()).isEqualTo("1235");
            assertThat(html.selectFirst(".merch-checkout-v2 h1").text()).isNotBlank().doesNotContain("??");
            assertThat(form.selectFirst("button[type=submit]").text()).isNotBlank().doesNotContain("??");
        }
    }

    @Test
    void concurrentPurchaseRetriesReuseOneReservationAndResolvePaymentOnlyOnce() throws Exception {
        User owner = client(); MerchProduct product = product();
        String key = UUID.randomUUID().toString();
        var selections = new java.util.concurrent.atomic.AtomicInteger();
        Long first, second;
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            var gate = new CyclicBarrier(2);
            Callable<Long> buy = () -> {
                gate.await(5, TimeUnit.SECONDS);
                return orders.createPendingOrderOnce(owner, product, 2, key, 1235L,
                        () -> { selections.incrementAndGet(); return null; }).getId();
            };
            Future<Long> one = workers.submit(buy), two = workers.submit(buy);
            first = one.get(10, TimeUnit.SECONDS); second = two.get(10, TimeUnit.SECONDS);
        }
        assertThat(second).isEqualTo(first);
        assertThat(selections).hasValue(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(orders.getOrdersForUser(owner.getId())).hasSize(1);
        assertThatThrownBy(() -> orders.createPendingOrderOnce(owner, product, 3, key, null))
                .isInstanceOf(IllegalArgumentException.class);
        orders.cancelPendingPayment(first, "Fixture cancellation");
        assertThat(orders.createPendingOrderOnce(owner, product, 2, key,
                () -> { throw new AssertionError("A consumed key must not resolve another card"); }).getId()).isEqualTo(first);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orders.findByCheckoutKeyForUser(key, client().getId())).isEmpty();
    }

    @Test
    void staleNativePriceRequiresReviewWithoutReservingStockOrSavingCardAndRetryKeepsSnapshot() throws Exception {
        User owner = client(); MerchProduct product = product(); String key = UUID.randomUUID().toString();
        var first = org.jsoup.Jsoup.parse(mvc.perform(get("/merch/" + product.getId() + "/buy")
                .param("checkoutKey", key).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String expected = first.selectFirst("input[name=expectedUnitMinor]").val();
        product.setPrice(new BigDecimal("15.67")); products.saveAndFlush(product);
        var rejected = mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("checkoutKey", key).param("expectedUnitMinor", expected).param("quantity", "2")
                .param("newCardHolderName", "Price review fixture").param("newBrand", "Visa").param("saveCard", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("checkoutPriceChanged", true))
                .andReturn();
        assertThat(orders.getOrdersForUser(owner.getId())).isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(cards.countByUserId(owner.getId())).isZero();
        // Spring Session replaces mock sessions; follow the application's actual response cookie.
        var review = org.jsoup.Jsoup.parse(mvc.perform(get(java.net.URI.create(rejected.getResponse().getRedirectedUrl()))
                .cookie(rejected.getResponse().getCookies())
                .with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("checkoutPriceChanged", true))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(review.selectFirst(".merch-feedback[role=alert]").text()).contains("price changed");
        assertThat(review.selectFirst("#merch-checkout-form button[type=submit]").text()).isEqualTo("Confirm updated price and continue");
        assertThat(review.selectFirst("input[name=expectedUnitMinor]").val()).isEqualTo("1567");
        assertThat(review.selectFirst("#merch-newCardHolderName").val()).isEqualTo("Price review fixture");
        assertThat(review.selectFirst("#merch-order-total").text()).contains("31.34");
        assertThat(review.selectFirst("#merch-total-panel").hasAttr("hidden")).isFalse();
        mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("checkoutKey", key).param("quantity", "2"))
                .andExpect(flash().attribute("checkoutQuoteInvalid", true));
        var current = java.time.YearMonth.now(clock);
        for (String quote : java.util.List.of("1567", "1235")) {
            mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                    .param("checkoutKey", key).param("expectedUnitMinor", quote).param("quantity", "2")
                    .param("newCardHolderName", "Price review fixture").param("newBrand", "Visa")
                    .param("newProviderToken", "sim_price_fixture").param("newLastFour", "4242").param("saveCard", "true")
                    .param("newExpiryMonth", String.valueOf(current.getMonthValue())).param("newExpiryYear", String.valueOf(current.getYear())))
                    .andExpect(redirectedUrl("/orders"));
            product = products.findById(product.getId()).orElseThrow();
            product.setPrice(new BigDecimal("19.99")); products.saveAndFlush(product);
        }
        assertThat(orders.getOrdersForUser(owner.getId())).singleElement().satisfies(order -> {
            assertThat(order.getTotalAmount()).isEqualByComparingTo("31.34");
            assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        });
        assertThat(cards.countByUserId(owner.getId())).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
    }

    @Test
    void expiredNewAndHistoricalSavedCardsRejectCheckoutAndProfileChangesWithoutMutation() throws Exception {
        User owner = client(); MerchProduct product = product(); var previous = java.time.YearMonth.now(clock).minusMonths(1);
        String key = UUID.randomUUID().toString();
        mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("checkoutKey", key).param("expectedUnitMinor", "1235").param("quantity", "2")
                .param("newCardHolderName", "Expired local fixture").param("newProviderToken", "sim_expired_fixture")
                .param("newLastFour", "4242").param("newBrand", "Visa").param("saveCard", "true")
                .param("newExpiryMonth", String.valueOf(previous.getMonthValue())).param("newExpiryYear", String.valueOf(previous.getYear())))
                .andExpect(flash().attribute("checkoutCardExpired", true));
        assertThat(cards.countByUserId(owner.getId())).isZero();
        var saved = new uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod();
        saved.setUser(owner); saved.setCardHolderName("Historical expired fixture"); saved.setBrand("Visa"); saved.setLastFour("4242");
        saved.setProviderPaymentMethodId("fixture-only-not-used"); saved.setExpiryMonth((short) previous.getMonthValue()); saved.setExpiryYear((short) previous.getYear());
        saved = cards.saveAndFlush(saved);
        mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("checkoutKey", key).param("expectedUnitMinor", "1235").param("quantity", "2").param("selectedCardId", saved.getId().toString()))
                .andExpect(flash().attribute("checkoutCardExpired", true));
        assertThat(orders.getOrdersForUser(owner.getId())).isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        mvc.perform(post("/profile/settings/cards/add").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("cardHolderName", "Expired add fixture").param("providerToken", "sim_expired_add")
                .param("lastFour", "4242").param("brand", "Visa")
                .param("expiryMonth", String.valueOf(previous.getMonthValue())).param("expiryYear", String.valueOf(previous.getYear())))
                .andExpect(flash().attribute("cardError", containsString("expired")));
        mvc.perform(post("/profile/settings/cards/" + saved.getId() + "/edit").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("cardHolderName", "Should not be persisted").param("brand", "Changed")
                .param("expiryMonth", String.valueOf(previous.getMonthValue())).param("expiryYear", String.valueOf(previous.getYear())))
                .andExpect(flash().attribute("cardError", containsString("expired")));
        assertThat(cards.countByUserId(owner.getId())).isEqualTo(1);
        assertThat(cards.findById(saved.getId()).orElseThrow().getCardHolderName()).isEqualTo("Historical expired fixture");
    }

    @Test
    void oldOrInvalidNativeIdentityCannotBypassCurrentPriceReview() throws Exception {
        User owner = client(); MerchProduct product = product();
        for (String identity : java.util.List.of("", "invalid-checkout")) {
            var request = post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                    .param("quantity", "2").param("expectedUnitMinor", "1235").param("newCardHolderName", "Older form fixture");
            if (!identity.isEmpty()) request.param("checkoutKey", identity);
            var rejected = mvc.perform(request).andExpect(status().is3xxRedirection())
                    .andExpect(flash().attribute("checkoutQuoteInvalid", true)).andReturn();
            var reviewed = org.jsoup.Jsoup.parse(mvc.perform(get(java.net.URI.create(rejected.getResponse().getRedirectedUrl()))
                    .cookie(rejected.getResponse().getCookies()).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(reviewed.selectFirst(".merch-feedback[role=alert]").text()).contains("Review the current price");
            assertThat(reviewed.selectFirst("input[name=checkoutKey]").val()).matches("[0-9a-f-]{36}");
            assertThat(reviewed.selectFirst("#merch-newCardHolderName").val()).isEqualTo("Older form fixture");
            assertThat(reviewed.selectFirst("#merch-order-total").text()).contains("24.70");
        }
        assertThat(orders.getOrdersForUser(owner.getId())).isEmpty();
        assertThat(cards.countByUserId(owner.getId())).isZero();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test
    void failedPaymentSelectionRollsBackOrderAndStockAndUnknownHostedStateKeepsReservation() throws Exception {
        User owner = client(); MerchProduct product = product();
        assertThatThrownBy(() -> orders.createPendingOrderOnce(owner, product, 2, UUID.randomUUID().toString(),
                () -> { throw new IllegalArgumentException("Synthetic rejected card"); }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(orders.getOrdersForUser(owner.getId())).isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        var order = orders.createPendingOrderOnce(owner, product, 2, UUID.randomUUID().toString(), null);
        orders.markCheckoutSessionCreated(order.getId(), "Stripe", null, null);
        assertThatThrownBy(() -> orders.cancelPendingPayment(order.getId(), "Uncertain response"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("unknown");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        orders.markCheckoutSessionCreated(order.getId(), "Stripe", "cs_test_fixture", null);
        orders.markCheckoutSessionCreated(order.getId(), "Stripe", null, null);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getPaymentReference()).isEqualTo("cs_test_fixture");
        assertThatThrownBy(() -> orders.markCheckoutSessionCreated(order.getId(), "SIMULATED", "sim-fixture", null))
                .isInstanceOf(IllegalStateException.class);
        orders.prepareHostedCheckout(order.getId(), "Stripe", "gbp", "https://example.test/original", "https://example.test/cancel");
        orders.prepareHostedCheckout(order.getId(), "Stripe", "eur", "https://example.test/changed", "https://example.test/changed");
        var stored = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(stored.getCheckoutCurrency()).isEqualTo("gbp");
        assertThat(stored.getCheckoutSuccessUrl()).isEqualTo("https://example.test/original");
        mvc.perform(get("/merch/" + product.getId() + "/buy").param("orderId", order.getId().toString())
                        .with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("readonly")))
                .andExpect(content().string(containsString("Reserved quantity")));
        mvc.perform(get("/merch/" + product.getId() + "/buy").param("orderId", order.getId().toString())
                        .with(user(client().getUsername()).roles("CLIENT")))
                .andExpect(status().isNotFound());
    }

    @Test
    void nativeDemoReplayAfterLastStockIsReservedKeepsOnePaidOrder() throws Exception {
        User owner = client(); MerchProduct product = product();
        String key = UUID.randomUUID().toString();
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/merch/" + product.getId() + "/buy").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                    .param("quantity", "10").param("checkoutKey", key).param("expectedUnitMinor", "1235")
                    .param("newCardHolderName", "Synthetic checkout fixture").param("newProviderToken", "sim_fixture_only")
                    .param("newLastFour", "4242").param("newBrand", "Visa")
                    .param("newExpiryMonth", "12").param("newExpiryYear", "2030"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        }
        mvc.perform(get("/merch/" + product.getId() + "/buy").param("checkoutKey", key)
                .with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        var owned = orders.getOrdersForUser(owner.getId());
        assertThat(owned).hasSize(1);
        assertThat(owned.get(0).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
    }

    @Test
    void concurrentCancellationRestoresReservedStockExactlyOnce() throws Exception {
        User owner = client(); MerchProduct product = product();
        var pending = orders.createPendingOrder(owner, product, 2);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            var gate = new CyclicBarrier(2);
            Callable<MerchOrder> cancel = () -> { gate.await(5, TimeUnit.SECONDS); return orders.cancelPendingPayment(pending.getId(), "Fixture cancellation"); };
            Future<MerchOrder> one = workers.submit(cancel), two = workers.submit(cancel);
            one.get(10, TimeUnit.SECONDS); two.get(10, TimeUnit.SECONDS);
        }
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void nativeShopAndOrderHistoryKeepTotalsPrivateAndCancellationProtected() throws Exception {
        User owner = client(), other = client(); MerchProduct product = product();
        var pending = orders.createPendingOrder(owner, product, 2);
        mvc.perform(get("/merch").param("search", product.getName()).param("category", "Equipment"))
            .andExpect(status().isOk()).andExpect(content().string(containsString(product.getName())))
            .andExpect(content().string(containsString("merch-native-filters")));
        mvc.perform(get("/merch/" + product.getId() + "/buy").param("checkoutKey", UUID.randomUUID().toString()).with(user(owner.getUsername()).roles("CLIENT")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("id=\"merch-order-total\"")))
            .andExpect(content().string(containsString("for=\"merch-quantity\"")));
        mvc.perform(get("/orders").with(user(owner.getUsername()).roles("CLIENT")).param("search", product.getName()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("£24.70")))
            .andExpect(content().string(containsString("merch-pending-actions")));
        mvc.perform(get("/orders").with(user(other.getUsername()).roles("CLIENT")).param("search", pending.getId().toString()))
            .andExpect(status().isOk()).andExpect(content().string(not(containsString(product.getName()))));
        mvc.perform(get("/profile/orders").with(user(owner.getUsername()).roles("CLIENT")))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        mvc.perform(get("/merch/checkout/cancel").param("orderId", pending.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")))
            .andExpect(status().is3xxRedirection());
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        mvc.perform(post("/merch/checkout/cancel").param("orderId", pending.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/merch/checkout/cancel").param("orderId", pending.getId().toString()).with(user(other.getUsername()).roles("CLIENT")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PENDING_PAYMENT);
        mvc.perform(post("/merch/checkout/cancel").param("orderId", pending.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }
}
