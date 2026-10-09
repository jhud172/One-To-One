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
