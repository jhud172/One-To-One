package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Merch.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.payments.stripe.secret-key=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MerchCatalogueWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MerchProductRepository products;
    @Autowired MerchProductService service;
    @Autowired UserRepository users;

    private MerchProduct product(String name, int stock) {
        var p = new MerchProduct(); p.setName(name); p.setCategory("QA + &");
        p.setPrice(new BigDecimal("12.35")); p.setStockQuantity(stock);
        p.setCreatedAt(Instant.parse("2026-10-01T12:00:00Z"));
        return products.save(p);
    }

    private User client() {
        String key = UUID.randomUUID().toString();
        var u = new User(key + "@example.com", "Catalogue", "Fixture", "catalogue_" + key, "fixture-only");
        u.setRole(Role.CLIENT); return users.save(u);
    }

    @Test
    void catalogueHasBoundedStablePagesAndLiteralWildcardSearch() {
        String prefix = "Catalogue " + UUID.randomUUID();
        var inserted = new ArrayList<MerchProduct>();
        for (int i = 0; i < 25; i++) inserted.add(product(prefix + " item " + i, i < 5 ? 0 : 5));
        var literal = product(prefix + " 100%_!+", 3); inserted.add(literal);
        var inactive = product(prefix + " retired", 5); inactive.setActive(false); products.save(inactive);
        var first = service.searchCatalogue(prefix, "QA + &", false, 1);
        var last = service.searchCatalogue(prefix, "QA + &", false, Integer.MAX_VALUE);
        assertThat(first.getTotalElements()).isEqualTo(26); assertThat(first.getContent()).hasSize(20);
        assertThat(first.getContent().stream().map(MerchProduct::getId).toList())
                .containsExactlyElementsOf(inserted.stream().map(MerchProduct::getId).sorted(Comparator.reverseOrder()).limit(20).toList());
        assertThat(last.getNumber()).isEqualTo(1); assertThat(last.getContent()).hasSize(6);
        assertThat(last.getContent().stream().map(MerchProduct::getId).toList())
                .doesNotContainAnyElementsOf(first.getContent().stream().map(MerchProduct::getId).toList());
        assertThat(service.searchCatalogue(prefix, "QA + &", true, 1).getTotalElements()).isEqualTo(21);
        assertThat(service.searchCatalogue("100%_!+", "QA + &", false, 1).getContent())
                .extracting(MerchProduct::getId).containsExactly(literal.getId());
        assertThat(service.searchCatalogue(prefix, "Missing category", false, 1).getTotalElements()).isZero();
    }

    @Test
    void nativeCatalogueRetainsLiteralFilterContextThroughCheckoutAndRejectsExternalReturnUrls() throws Exception {
        var owner = client(); var product = product("Catalogue A+B %_! " + UUID.randomUUID(), 5);
        var shop = Jsoup.parse(mvc.perform(get("/merch").param("search", "A+B %_!").param("category", "QA + &")
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String buy = shop.selectFirst("a[href*='/buy']").attr("href");
        String redirected = mvc.perform(get(URI.create(buy)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var checkout = Jsoup.parse(mvc.perform(get(URI.create(redirected)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String back = checkout.selectFirst(".merch-checkout-v2 a").attr("href");
        var returned = Jsoup.parse(mvc.perform(get(URI.create(back)).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(returned.selectFirst("#product-search").val()).isEqualTo("A+B %_!");
        assertThat(returned.selectFirst("#product-category option[selected]").val()).isEqualTo("QA + &");
        assertThat(returned.select(".product-card h2").eachText()).containsExactly(product.getName());
        for (String unsafe : List.of("https://outside.example/merch", "//outside.example/merch", "/merch/../login")) {
            var html = Jsoup.parse(mvc.perform(get("/merch/" + product.getId() + "/buy")
                            .param("checkoutKey", UUID.randomUUID().toString()).param("returnTo", unsafe)
                            .with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(html.selectFirst(".merch-checkout-v2 a").attr("href")).isEqualTo("/merch");
        }
    }

    @Test
    void emptyAndInvalidFiltersRenderTruthfulAccessibleFeedbackAcrossLocales() throws Exception {
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var html = Jsoup.parse(mvc.perform(get("/merch").param("lang", locale).param("search", "no product " + UUID.randomUUID()))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
            assertThat(html.selectFirst(".guest-store__empty").text()).isNotBlank().doesNotContain("??").doesNotContain("Store Coming Soon");
            assertThat(html.select(".product-card")).isEmpty();
        }
        var invalid = Jsoup.parse(mvc.perform(get("/merch").param("search", "x".repeat(121)).param("page", "-1"))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(invalid.selectFirst(".merch-feedback[role=alert]").text()).contains("120");
        assertThat(invalid.selectFirst("#product-search").val()).hasSize(121);
    }
}
