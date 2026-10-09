package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.jsoup.Jsoup;
import uk.ac.cf._5.group14.One_To_One.Merch.*;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.payments.stripe.secret-key=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderHistoryWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired MerchProductRepository products;
    @Autowired MerchOrderRepository repository;
    @Autowired MerchOrderService service;
    private static final List<String> LOCALES = List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh");

    private User owner() {
        String key = UUID.randomUUID().toString().replace("-", "");
        var user = new User(key + "@example.com", "History", "Fixture", "history_" + key, "fixture-only");
        user.setRole(Role.CLIENT); return users.save(user);
    }

    private List<MerchOrder> history(User owner, int count) {
        var product = new MerchProduct(); product.setName("Live product must not replace snapshots");
        product.setPrice(new BigDecimal("12.35")); product.setStockQuantity(100); product = products.save(product);
        var rows = new ArrayList<MerchOrder>();
        for (int index = 0; index < count; index++) {
            var row = new MerchOrder(); row.setUser(owner); row.setTotalAmount(new BigDecimal("24.70"));
            row.setCreatedAt(Instant.parse("2026-10-01T12:00:00Z")); row.setShippingStatus(ShippingStatus.values()[index % 8]);
            row.setPaymentStatus(row.getShippingStatus() == ShippingStatus.PENDING ? PaymentStatus.PENDING_PAYMENT
                    : row.getShippingStatus() == ShippingStatus.RETURNED ? PaymentStatus.REFUNDED
                    : row.getShippingStatus() == ShippingStatus.CANCELLED ? PaymentStatus.FAILED : PaymentStatus.PAID);
            row.setPaymentProvider(index == 1 ? "SIMULATED" : "Stripe");
            if (row.getPaymentStatus() == PaymentStatus.PAID) row.setStatus(OrderStatus.CONFIRMED);
            String name = index == 0 ? "Snapshot 100%_!+ & <literal>" : index == count - 1 ? "" : "Snapshot fixture " + index;
            int lines = index == 0 ? 2 : 1;
            for (int line = 0; line < lines; line++) {
                var item = new MerchOrderItem(); item.setOrder(row); item.setProduct(index == count - 1 ? null : product);
                item.setProductNameSnapshot(name); item.setPriceSnapshot(new BigDecimal("12.35")); item.setQuantity(lines == 1 ? 2 : 1);
                row.getItems().add(item);
            }
            rows.add(repository.saveAndFlush(row));
        }
        return rows;
    }

    private org.jsoup.nodes.Document page(User owner, String path, String locale, String search, String shipping, String number) throws Exception {
        return Jsoup.parse(mvc.perform(get(path).param("lang", locale).param("search", search).param("status", shipping).param("page", number)
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void databaseHistoryIsBoundedStableLiteralOwnedAndRecentPreviewFetchesFive() {
        var owner = owner(); var rows = history(owner, 26); var other = owner(); history(other, 3);
        var descending = rows.reversed().stream().map(MerchOrder::getId).toList();
        var first = service.searchHistory(owner.getId(), "", null, 1);
        assertThat(first.getTotalElements()).isEqualTo(26);
        assertThat(first.getContent().stream().map(MerchOrder::getId)).containsExactlyElementsOf(descending.subList(0, 20));
        var second = service.searchHistory(owner.getId(), "", null, 2);
        assertThat(second.getContent().stream().map(MerchOrder::getId)).containsExactlyElementsOf(descending.subList(20, 26));
        assertThat(service.searchHistory(owner.getId(), "", null, Integer.MAX_VALUE).getNumber()).isEqualTo(1);
        var literal = service.searchHistory(owner.getId(), "100%_!+ &", null, 1);
        assertThat(literal.getTotalElements()).isEqualTo(1);
        assertThat(literal.getContent()).singleElement().satisfies(order -> assertThat(order.getItems()).hasSize(2));
        assertThat(service.searchHistory(owner.getId(), "100%_!+ &", ShippingStatus.DELIVERED, 1)).isEmpty();
        assertThat(service.getRecentOrdersForUser(owner.getId()).stream().map(MerchOrder::getId)).containsExactlyElementsOf(descending.subList(0, 5));
        assertThat(service.countOrdersForUser(owner.getId())).isEqualTo(26);
        assertThat(service.searchHistory(other.getId(), "fixture 20", null, 1)).isEmpty();
        assertThatThrownBy(() -> service.searchHistory(owner.getId(), "x".repeat(121), null, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nativeHistoryRendersAllLocalesAndRetainsFiltersTruthfulDemoAndEmptyFeedback() throws Exception {
        var owner = owner(); history(owner, 26);
        for (String locale : LOCALES) {
            var html = page(owner, "/orders", locale, "", "", "1");
            assertThat(html.select(".order-card")).hasSize(20);
            assertThat(html.select(".merch-history-status dd").eachText()).allSatisfy(text -> assertThat(text).isNotBlank().doesNotContain("??"));
            assertThat(html.selectFirst(".merch-pagination a").attr("href")).contains("page=2");
            assertThat(html.selectFirst("main").text()).doesNotContain("Live product must not replace snapshots");
        }
        var second = page(owner, "/orders", "en", "", "", "2");
        assertThat(second.select(".order-card")).hasSize(6);
        assertThat(second.selectFirst("#order-results").text()).isEqualTo("21–26 of 26 matches");
        var filtered = page(owner, "/orders", "en", "100%_!+ &", "PENDING", "1");
        assertThat(filtered.selectFirst("#order-search").val()).isEqualTo("100%_!+ &");
        assertThat(filtered.selectFirst("#order-status option[selected]").val()).isEqualTo("PENDING");
        assertThat(filtered.select(".order-card")).hasSize(1);
        assertThat(filtered.selectFirst(".merch-history-items").text()).contains("Snapshot 100%_!+ & <literal>");
        assertThat(filtered.select("literal")).isEmpty();
        var demo = page(owner, "/orders", "en", "fixture 1", "PROCESSING", "1");
        assertThat(demo.selectFirst(".merch-history-demo").text()).contains("No real payment");
        assertThat(demo.selectFirst(".merch-history-card:has(.merch-history-demo) .merch-history-status").text()).contains("Demo · no delivery");
        assertThat(demo.select(".merch-history-shipping")).isEmpty();
        var invalid = Jsoup.parse(mvc.perform(get("/orders").param("status", "NOT_A_STATUS").param("search", "x".repeat(121))
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(invalid.select(".order-card")).isEmpty();
        assertThat(invalid.selectFirst(".merch-history-error[role=alert]").text()).isNotBlank();
        assertThat(page(owner(), "/orders", "en", "", "", "1").selectFirst(".merch-history-empty").text()).contains("No purchases yet");
        mvc.perform(get("/profile/orders").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(redirectedUrl("/orders"));
    }

    @Test
    void nativeProfileShowsOwnedRecentPreviewAndRealCountInAllLocales() throws Exception {
        var owner = owner(); var rows = history(owner, 26); history(owner(), 3);
        var recent = rows.reversed().subList(0, 5).stream().map(order -> String.valueOf(order.getId())).toList();
        for (String locale : LOCALES) {
            var html = Jsoup.parse(mvc.perform(get("/profile").param("lang", locale).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andExpect(model().attribute("orderCount", 26L))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(html.select(".profile-recent-order")).hasSize(5);
            assertThat(html.select("#options-drawer-root .profile-recent-order")).hasSize(5);
            // A stray closing tag previously dropped these dialogs from the content fragment.
            assertThat(html.select("#edit-card-modal, #profile-image-editor-modal, #custom-date-picker")).hasSize(3);
            assertThat(html.select(".profile-settings-drawer[inert]")).hasSize(3);
            assertThat(html.select("#close-options-drawer")).hasSize(1);
            assertThat(html.select(".profile-recent-order").eachAttr("data-order-id")).containsExactlyInAnyOrderElementsOf(recent);
            assertThat(html.selectFirst(".profile-orders-link").attr("href")).isEqualTo("/orders");
        }
    }
}
