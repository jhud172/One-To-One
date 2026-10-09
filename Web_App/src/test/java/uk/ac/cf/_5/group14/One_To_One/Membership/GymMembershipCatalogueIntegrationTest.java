package uk.ac.cf._5.group14.One_To_One.Membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.jsoup.Jsoup;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.GymProfile.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GymMembershipCatalogueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired GymProfileRepository gyms;
    @Autowired GymMembershipProductRepository products;
    @Autowired GymMemberSubscriptionRepository subscriptions;
    @Autowired MembershipProductService service;
    @Autowired PriceChangeEventRepository events;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailService emailService;
    private static final List<String> LOCALES = List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh");

    private User account(Role role) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        var user = new User(unique + "@example.com", "Local", "Fixture", "membership_" + unique, "fixture-only");
        user.setRole(role); return users.saveAndFlush(user);
    }
    private User admin() {
        var admin = account(Role.GYM_ADMIN);
        var gym = gyms.saveAndFlush(new GymProfile(admin.getId(), "Catalogue fixture gym"));
        admin.setGymId(gym.getId()); return users.saveAndFlush(admin);
    }
    private record Fixture(User admin, User other, List<GymMembershipProduct> products) {}
    private Fixture fixture() {
        var admin = admin(); var other = admin(); var list = new ArrayList<GymMembershipProduct>();
        for (int i = 0; i < 26; i++) {
            var product = new GymMembershipProduct(admin.getGymId(), "Product " + String.format("%02d", i + 1), 2500 + i);
            product.setDescription(i == 0 ? "Literal 100%_!+ & <description>" : "Owned product description " + i);
            product.setActive(i % 2 == 0); product = products.saveAndFlush(product); list.add(product);
            jdbc.update("update gym_membership_products set created_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-10-01T12:00:00Z")), product.getId());
            if (i < 4) {
                var member = account(Role.CLIENT);
                var subscription = new GymMemberSubscription(member.getId(), admin.getGymId(), product.getId(), Instant.now().plusSeconds(86400));
                if (i == 3) subscription.setStatus(SubscriptionStatus.CANCELLED);
                subscriptions.saveAndFlush(subscription);
            }
        }
        products.saveAndFlush(new GymMembershipProduct(other.getGymId(), "FOREIGN PRIVATE PRODUCT", 9999));
        return new Fixture(admin, other, list);
    }

    @Test
    void countsLiteralSearchStablePagesAndSubscriberCountsCoverTheActualGym() throws Exception {
        var f = fixture(); var gymId = f.admin().getGymId();
        assertThat(service.countProducts(gymId)).isEqualTo(26);
        assertThat(service.countActiveProducts(gymId)).isEqualTo(13);
        assertThat(subscriptions.countByGymIdAndStatus(gymId, SubscriptionStatus.ACTIVE)).isEqualTo(3);
        var expected = new ArrayList<>(f.products().subList(16, 26)); Collections.reverse(expected);
        assertThat(service.searchProducts(gymId, "", "", 0, 10).getContent().stream().map(GymMembershipProduct::getId))
            .containsExactlyElementsOf(expected.stream().map(GymMembershipProduct::getId).toList());
        assertThat(service.searchProducts(gymId, "", "", Integer.MAX_VALUE, 10).getNumber()).isEqualTo(2);
        assertThat(service.searchProducts(gymId, "", "", Integer.MAX_VALUE, 10).getNumberOfElements()).isEqualTo(6);
        assertThat(service.searchProducts(gymId, "100%_!+ &", "ACTIVE", 0, 10).getContent()).singleElement()
            .extracting(GymMembershipProduct::getId).isEqualTo(f.products().getFirst().getId());
        assertThat(service.searchProducts(gymId, "100%_!+ &", "INACTIVE", 0, 10)).isEmpty();
        var html = Jsoup.parse(mvc.perform(get("/gym/admin/memberships").param("lang", "en").param("page", "2")
            .with(user(f.admin().getUsername()).roles("GYM_ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(html.select(".gym-product-card")).hasSize(6);
        assertThat(html.select(".gym-products-metrics dd").eachText()).containsExactly("26", "13", "3");
        assertThat(html.selectFirst("#membership-results").text()).isEqualTo("21–26 of 26 matches");
        assertThat(html.selectFirst("[data-product-id='" + f.products().getFirst().getId() + "'] .gym-product-facts > div:last-child dd").text()).isEqualTo("1");
        verifyNoInteractions(emailService);
    }

    @Test
    void nativeCardsRenderAllLocalesWithRetainedInvalidFiltersAndStatusContext() throws Exception {
        var f = fixture(); var admin = f.admin();
        for (String locale : LOCALES) {
            var html = Jsoup.parse(mvc.perform(get("/gym/admin/memberships").param("lang", locale)
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(html.select(".gym-product-card")).hasSize(10);
            assertThat(html.select("main").text()).doesNotContain("??", "FOREIGN PRIVATE PRODUCT");
            assertThat(html.select(".gym-product-deactivation[open]")).isEmpty();
            assertThat(html.select(".gym-product-deactivation form input[name='_csrf']")).hasSize(5);
        }
        var invalid = Jsoup.parse(mvc.perform(get("/gym/admin/memberships").param("lang", "en")
            .param("search", "x".repeat(121)).param("state", "UNKNOWN")
            .with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(invalid.select(".gym-product-card")).isEmpty();
        assertThat(invalid.select("#membership-state option[value=UNKNOWN]")).hasSize(1);
        assertThat(invalid.selectFirst("#membership-search").val()).hasSize(120);
        var product = f.products().getFirst(); product.setPriceCents(3399); products.saveAndFlush(product);
        // Keep the managed object stale while the stored price changes, as can happen between controller read and lock.
        jdbc.update("update gym_membership_products set price_cents = ? where id = ?", 4499, product.getId());
        mvc.perform(post("/gym/admin/memberships/" + product.getId() + "/status").with(csrf())
            .with(user(admin.getUsername()).roles("GYM_ADMIN")).param("active", "false").param("priceCents", "1")
            .param("search", "100%_!+ &").param("state", "ACTIVE").param("page", "2").param("size", "10"))
            .andExpect(redirectedUrl("/gym/admin/memberships?search=100%25_%21%2B+%26&state=ACTIVE&page=2&size=10#membership-results"));
        assertThat(products.findById(product.getId()).orElseThrow().isActive()).isFalse();
        assertThat(products.findById(product.getId()).orElseThrow().getPriceCents()).isEqualTo(4499);
        assertThat(subscriptions.countByGymIdAndStatus(admin.getGymId(), SubscriptionStatus.ACTIVE)).isEqualTo(3);
        verifyNoInteractions(emailService);
    }

    @Test
    void metadataEditPreservesFreshPriceSubscriptionsAndNativeCatalogueContext() throws Exception {
        var f = fixture(); var admin = f.admin(); var product = f.products().getFirst();
        var draft = MembershipProductForm.from(product);
        draft.setName("  Updated owned product  "); draft.setDescription("Updated <details>");
        draft.setActive(false); draft.setPriceDollars(java.math.BigDecimal.ONE);
        jdbc.update("update gym_membership_products set price_cents = ? where id = ?", 4499, product.getId());
        service.updateProduct(product.getId(), admin.getGymId(), draft);
        products.flush();
        var saved = products.findById(product.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Updated owned product");
        assertThat(saved.getPriceCents()).isEqualTo(4499);
        assertThat(saved.getBillingPeriod()).isEqualTo(BillingPeriod.MONTHLY);
        assertThat(saved.isActive()).isFalse();
        assertThat(subscriptions.countByProductIdAndStatus(product.getId(), SubscriptionStatus.ACTIVE)).isEqualTo(1);

        String path = "/gym/admin/memberships/" + product.getId() + "/edit";
        var invalid = Jsoup.parse(mvc.perform(post(path).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
            .param("lang", "en").param("name", "").param("description", "Retained <draft>")
            .param("search", "100%_!+ &").param("state", "ACTIVE").param("page", "2").param("size", "10"))
            .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(invalid.selectFirst("#description").val()).isEqualTo("Retained <draft>");
        assertThat(invalid.selectFirst("#name").attr("aria-invalid")).isEqualTo("true");
        assertThat(invalid.selectFirst("input[name=search]").val()).isEqualTo("100%_!+ &");
        assertThat(invalid.selectFirst(".gym-membership-current").text()).contains("44.99", "USD");
        mvc.perform(post(path).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
            .param("name", "Native saved name").param("description", "Native details").param("active", "true")
            .param("priceDollars", "0.01").param("priceCents", "1").param("gymId", f.other().getGymId().toString())
            .param("search", "100%_!+ &").param("state", "ACTIVE").param("page", "2").param("size", "10"))
            .andExpect(redirectedUrl("/gym/admin/memberships?search=100%25_%21%2B+%26&state=ACTIVE&page=2&size=10#membership-results"));
        assertThat(saved.getName()).isEqualTo("Native saved name");
        assertThat(saved.getPriceCents()).isEqualTo(4499);
        assertThat(saved.getGymId()).isEqualTo(admin.getGymId());
        verifyNoInteractions(emailService);
    }

    @Test
    void editorRendersEveryLocaleAndCreatesExactPriceWithRetainedContext() throws Exception {
        var f = fixture(); var admin = f.admin(); var product = f.products().getFirst();
        for (String locale : LOCALES) {
            for (String path : List.of("/gym/admin/memberships/create", "/gym/admin/memberships/" + product.getId() + "/edit")) {
                var html = Jsoup.parse(mvc.perform(get(path).param("lang", locale).param("search", "100%_!+ &")
                    .param("state", "ACTIVE").param("page", "2").with(user(admin.getUsername()).roles("GYM_ADMIN")))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                assertThat(html.select("main").text()).doesNotContain("??", "FOREIGN PRIVATE PRODUCT");
                assertThat(html.select(".gym-membership-fields input[name='_csrf']")).hasSize(1);
                assertThat(html.selectFirst("input[name=search]").val()).isEqualTo("100%_!+ &");
                if (path.endsWith("/edit")) assertThat(html.select("#priceDollars, #billingPeriod")).isEmpty();
                else assertThat(html.select("#priceDollars, #billingPeriod")).hasSize(2);
            }
        }
        mvc.perform(post("/gym/admin/memberships/create").with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
            .param("lang", "en").param("name", "New exact price product").param("priceDollars", "12.35")
            .param("billingPeriod", "MONTHLY").param("active", "true").param("search", "new").param("page", "2"))
            .andExpect(redirectedUrl("/gym/admin/memberships?search=new&state=&page=2&size=10#membership-results"));
        assertThat(service.searchProducts(admin.getGymId(), "New exact price product", "", 0, 10).getContent())
            .singleElement().extracting(GymMembershipProduct::getPriceCents).isEqualTo(1235);
        assertThatThrownBy(() -> service.updateProduct(product.getId(), f.other().getGymId(), MembershipProductForm.from(product)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(emailService);
    }

    @Test
    void priceQuoteReviewAndLondonEffectiveDatePreserveDraftAndRejectStalePrice() throws Exception {
        var admin = admin(); var product = products.saveAndFlush(new GymMembershipProduct(admin.getGymId(), "Quote fixture", 2500));
        String path = "/gym/admin/memberships/" + product.getId() + "/price-change";
        var date = java.time.LocalDate.now(java.time.ZoneId.of("Europe/London")).plusMonths(8);
        jdbc.update("update gym_membership_products set price_cents = ? where id = ?", 3500, product.getId());
        mvc.perform(post(path).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
            .param("lang", "en").param("newPriceDollars", "40.00").param("reason", "Retained quote reason")
            .param("effectiveDate", date.toString()).param("quotedPriceCents", "2500").param("confirmPriceChange", "true"))
            .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("current price needs review")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Retained quote reason")));
        assertThat(events.countByProductId(product.getId())).isZero();
        verifyNoInteractions(emailService);
        var originalZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            mvc.perform(post(path).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
                .param("newPriceDollars", "40.00").param("reason", "Retained quote reason")
                .param("effectiveDate", date.toString()).param("quotedPriceCents", "3500").param("confirmPriceChange", "true"))
                .andExpect(status().is3xxRedirection());
        } finally { TimeZone.setDefault(originalZone); }
        assertThat(events.findByProductIdOrderByCreatedAtDescIdDesc(product.getId())).singleElement().satisfies(event -> {
            assertThat(event.getOldPriceCents()).isEqualTo(3500);
            assertThat(event.getNewPriceCents()).isEqualTo(4000);
            assertThat(event.getEffectiveAt()).isEqualTo(date.atStartOfDay(java.time.ZoneId.of("Europe/London")).toInstant());
        });
        assertThat(product.getPriceCents()).isEqualTo(3500);
        for (String locale : LOCALES) {
            var review = Jsoup.parse(mvc.perform(get(path).param("lang", locale)
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(review.select("main").text()).doesNotContain("??");
            assertThat(review.selectFirst("input[name=quotedPriceCents]").val()).isEqualTo("3500");
            assertThat(review.select("script[src*=price-change-page.js]")).hasSize(1);
            var history = Jsoup.parse(mvc.perform(get("/gym/admin/memberships/" + product.getId() + "/price-history")
                .param("lang", locale).with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(history.select("main").text()).doesNotContain("??").contains("USD", "Europe/London", "Retained quote reason");
        }
        mvc.perform(post(path).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN"))
            .param("newPriceDollars", "41.00").param("reason", "Missing quote")
            .param("effectiveDate", date.toString()).param("confirmPriceChange", "true"))
            .andExpect(status().isBadRequest());
        assertThat(events.countByProductId(product.getId())).isEqualTo(1);
        verifyNoInteractions(emailService);
    }

    @Test
    void stableHistoryClampsOversizedPagesAndDuePricesPreserveNewerMetadata() throws Exception {
        var admin = admin(); var product = products.saveAndFlush(new GymMembershipProduct(admin.getGymId(), "Old metadata", 2500));
        var due = Instant.now().minusSeconds(3600); var ids = new ArrayList<Long>();
        for (int i = 0; i < 26; i++) {
            var event = events.saveAndFlush(new PriceChangeEvent(admin.getGymId(), product.getId(), 2500, 3000+i,
                due, "History fixture " + i, admin.getId()));
            ids.add(event.getId());
            jdbc.update("update price_change_events set created_at = ? where id = ?", java.sql.Timestamp.from(due), event.getId());
        }
        var page = service.getPriceChangeHistory(product.getId(), org.springframework.data.domain.PageRequest.of(Integer.MAX_VALUE, 10));
        assertThat(page.getNumber()).isEqualTo(2); assertThat(page.getNumberOfElements()).isEqualTo(6);
        var expected = new ArrayList<>(ids.subList(0,6)); Collections.reverse(expected);
        assertThat(page.getContent().stream().map(PriceChangeEvent::getId)).containsExactlyElementsOf(expected);
        events.saveAndFlush(new PriceChangeEvent(admin.getGymId(), product.getId(), 3025, 9999,
            Instant.now().plusSeconds(86400), "Future fixture", admin.getId()));
        jdbc.update("update gym_membership_products set name = ?, description = ?, active = false where id = ?",
            "Newer metadata", "Newer description", product.getId());
        service.applyDuePriceChangesForProduct(product); products.flush();
        assertThat(product.getPriceCents()).isEqualTo(3025);
        assertThat(product.getName()).isEqualTo("Newer metadata");
        assertThat(product.getDescription()).isEqualTo("Newer description"); assertThat(product.isActive()).isFalse();
        mvc.perform(get("/gym/admin/memberships/" + product.getId() + "/price-history")
            .param("page", "2147483647").with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isOk());
        verifyNoInteractions(emailService);
    }

    @Test
    void foreignGymPointerIsDeniedForEveryMembershipSurfaceAndWrite() throws Exception {
        var f = fixture(); var admin = f.admin(); var product = f.products().getFirst();
        admin.setGymId(f.other().getGymId()); users.saveAndFlush(admin);
        String path = "/gym/admin/memberships";
        for (String suffix : List.of("", "/create", "/" + product.getId() + "/edit", "/" + product.getId() + "/price-change", "/" + product.getId() + "/price-history"))
            mvc.perform(get(path + suffix).with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isForbidden());
        for (String suffix : List.of("/create", "/" + product.getId() + "/edit", "/" + product.getId() + "/price-change", "/" + product.getId() + "/status"))
            mvc.perform(post(path + suffix).with(csrf()).with(user(admin.getUsername()).roles("GYM_ADMIN")).param("active", "false"))
                .andExpect(status().isForbidden());
        assertThat(products.findById(product.getId()).orElseThrow().isActive()).isTrue();
        verifyNoInteractions(emailService);
    }
}
