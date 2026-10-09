package uk.ac.cf._5.group14.One_To_One.Membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfile;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileRepository;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDate;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GymAdminMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GymProfileRepository gymProfileRepository;

    @Autowired
    private GymMembershipProductRepository productRepository;

    @Autowired
    private GymMemberSubscriptionRepository subscriptionRepository;

    @Autowired private PriceChangeEventRepository priceChangeEvents;
    @MockBean private EmailService emailService;

    /** Creates a persisted GymProfile for the given user and returns its auto-generated ID. */
    private Long createGym(User owner, String name) {
        return gymProfileRepository.save(new GymProfile(owner.getId(), name)).getId();
    }

    @Test
    void listShowsOnlyAdminGymProductsWithPaginationAndCounts() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        User admin = new User("admin+" + suffix + "@example.com", "Gym", "Admin", "gym_admin_" + suffix, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        Long gymId = createGym(admin, "Admin Gym");
        admin.setGymId(gymId);
        admin = userRepository.save(admin);

        User member = new User("member+" + suffix + "@example.com", "Member", "One", "gym_member_" + suffix, "password123");
        member.setRole(Role.CLIENT);
        member = userRepository.save(member);

        User otherOwner = new User("other+" + suffix + "@example.com", "Other", "Owner", "gym_other_" + suffix, "password123");
        otherOwner = userRepository.save(otherOwner);
        Long otherGymId = createGym(otherOwner, "Other Gym");

        GymMembershipProduct product1 = new GymMembershipProduct(gymId, "Standard", 3000);
        product1 = productRepository.save(product1);

        GymMembershipProduct product2 = new GymMembershipProduct(gymId, "Premium", 5000);
        product2 = productRepository.save(product2);

        GymMembershipProduct otherGymProduct = new GymMembershipProduct(otherGymId, "Other", 2000);
        productRepository.save(otherGymProduct);

        GymMemberSubscription subscription = new GymMemberSubscription(
            member.getId(),
            gymId,
            product2.getId(),
            Instant.now().plus(30, ChronoUnit.DAYS)
        );
        subscriptionRepository.save(subscription);

        mockMvc.perform(get("/gym/admin/memberships")
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))
                .param("page", "0")
                .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(view().name("gym-views/gym-admin/memberships/list"))
            .andExpect(model().attribute("productsPage", hasProperty("totalElements", is(2L))))
            .andExpect(model().attribute("products", hasSize(1)))
            .andExpect(model().attribute("subscriberCounts", hasEntry(product2.getId(), 1L)))
            .andExpect(model().attribute("pageSize", is(1)));
    }

    @Test
    void gymAdminCanToggleProductStatus() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        User admin = new User("admin2+" + suffix + "@example.com", "Gym", "Admin", "gym_admin2_" + suffix, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        Long gymId = createGym(admin, "Toggle Gym");
        admin.setGymId(gymId);
        admin = userRepository.save(admin);

        GymMembershipProduct product = new GymMembershipProduct(gymId, "Monthly", 2500);
        product = productRepository.save(product);

        mockMvc.perform(post("/gym/admin/memberships/" + product.getId() + "/status")
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))
                .with(csrf())
                .param("active", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/gym/admin/memberships"));

        GymMembershipProduct updated = productRepository.findById(product.getId()).orElseThrow();
        assertFalse(updated.isActive());
    }

    @Test
    void createRequiresNameAndPrice() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        User admin = new User("admin3+" + suffix + "@example.com", "Gym", "Admin", "gym_admin3_" + suffix, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        Long gymId = createGym(admin, "Create Test Gym");
        admin.setGymId(gymId);
        admin = userRepository.save(admin);

        mockMvc.perform(post("/gym/admin/memberships/create")
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))
                .with(csrf())
                .param("gymId", String.valueOf(gymId))
                .param("billingPeriod", "MONTHLY"))
            .andExpect(status().isBadRequest())
            .andExpect(view().name("gym-views/gym-admin/memberships/form"))
            .andExpect(model().attributeHasFieldErrors("product", "name", "priceDollars"));
    }

    @Test
    void editRequiresName() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        User admin = new User("admin4+" + suffix + "@example.com", "Gym", "Admin", "gym_admin4_" + suffix, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        Long gymId = createGym(admin, "Edit Test Gym");
        admin.setGymId(gymId);
        admin = userRepository.save(admin);

        GymMembershipProduct product = new GymMembershipProduct(gymId, "Monthly", 2500);
        product = productRepository.save(product);

        mockMvc.perform(post("/gym/admin/memberships/" + product.getId() + "/edit")
                .with(user(admin.getUsername()).roles("GYM_ADMIN"))
                .with(csrf())
                .param("id", String.valueOf(product.getId()))
                .param("gymId", String.valueOf(gymId))
                .param("priceCents", String.valueOf(product.getPriceCents()))
                .param("billingPeriod", "MONTHLY")
                .param("name", "")
                .param("description", "Updated description")
                .param("active", "true"))
            .andExpect(status().isBadRequest())
            .andExpect(view().name("gym-views/gym-admin/memberships/form"))
            .andExpect(model().attributeHasFieldErrors("product", "name"));
    }

    @Test
    void priceHistoryDeniesOtherGymProduct() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        User admin = new User("admin5+" + suffix + "@example.com", "Gym", "Admin", "gym_admin5_" + suffix, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        Long gymId = createGym(admin, "Price History Gym");
        admin.setGymId(gymId);
        admin = userRepository.save(admin);

        User otherOwner = new User("other5+" + suffix + "@example.com", "Other", "Owner", "gym_other5_" + suffix, "password123");
        otherOwner = userRepository.save(otherOwner);
        Long otherGymId = createGym(otherOwner, "Other Gym 5");

        GymMembershipProduct otherProduct = new GymMembershipProduct(otherGymId, "Other", 4000);
        otherProduct = productRepository.save(otherProduct);

        mockMvc.perform(get("/gym/admin/memberships/" + otherProduct.getId() + "/price-history")
                .with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isNotFound());
    }

    private User newAdmin(String label) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        User admin = new User(unique + "@example.com", "Gym", "Admin", label + unique, "password123");
        admin.setRole(Role.GYM_ADMIN);
        admin = userRepository.save(admin);
        admin.setGymId(createGym(admin, label));
        return userRepository.save(admin);
    }

    @Test
    void createFormHasNativeActionAndCreationFieldsAndUsesServerGym() throws Exception {
        User admin = newAdmin("native");
        mockMvc.perform(get("/gym/admin/memberships/create").with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("action=\"/gym/admin/memberships/create\"")))
            .andExpect(content().string(containsString("id=\"priceDollars\"")))
            .andExpect(content().string(not(containsString("memberships/null/edit"))));
        mockMvc.perform(post("/gym/admin/memberships/create").with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                .param("name", "  Native monthly  ").param("priceDollars", "49.99").param("billingPeriod", "MONTHLY")
                .param("gymId", "999999").param("priceCents", "1").param("active", "true"))
            .andExpect(status().is3xxRedirection());
        GymMembershipProduct saved = productRepository.findByGymIdOrderByCreatedAtDesc(admin.getGymId()).get(0);
        assertEquals("Native monthly", saved.getName());
        assertEquals(4999, saved.getPriceCents());
        assertEquals(admin.getGymId(), saved.getGymId());
    }

    @Test
    void invalidCreationPricesRetainDraftAndNeverSave() throws Exception {
        User admin = newAdmin("prices");
        for (String price : new String[]{"21474836.48", "9.999", "-1", "not-a-number"}) {
            mockMvc.perform(post("/gym/admin/memberships/create").with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                    .param("name", "Keep this name").param("priceDollars", price))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("product", "priceDollars"))
                .andExpect(content().string(containsString("Keep this name")))
                .andExpect(content().string(containsString("action=\"/gym/admin/memberships/create\"")));
        }
        assertTrue(productRepository.findByGymIdOrderByCreatedAtDesc(admin.getGymId()).isEmpty());
    }

    @Test
    void editingKeepsTrustedActionAndCannotAlterPriceOrOwnership() throws Exception {
        User admin = newAdmin("editor");
        GymMembershipProduct product = productRepository.save(new GymMembershipProduct(admin.getGymId(), "Original", 2500));
        String action = "/gym/admin/memberships/" + product.getId() + "/edit";
        mockMvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf()).param("name", ""))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("action=\"" + action + "\"")))
            .andExpect(content().string(containsString("25.00")));
        assertEquals("Original", productRepository.findById(product.getId()).orElseThrow().getName());
        mockMvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                .param("name", "Updated").param("priceDollars", "1.00").param("priceCents", "1").param("gymId", "999999"))
            .andExpect(status().is3xxRedirection());
        GymMembershipProduct saved = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(2500, saved.getPriceCents());
        assertEquals(admin.getGymId(), saved.getGymId());
        User other = newAdmin("other");
        mockMvc.perform(post(action).with(user(other.getUsername()).roles("GYM_ADMIN")).with(csrf()).param("name", ""))
            .andExpect(status().isNotFound());
    }

    @Test
    void priceChangeValidatesDraftRequiresConsentAndDeduplicatesAuditAndEmail() throws Exception {
        User admin = newAdmin("audit");
        GymMembershipProduct product = productRepository.save(new GymMembershipProduct(admin.getGymId(), "Audited", 2500));
        User member = userRepository.save(new User(UUID.randomUUID() + "@example.com", "Member", "One", "member_" + UUID.randomUUID(), "password123"));
        subscriptionRepository.save(new GymMemberSubscription(member.getId(), admin.getGymId(), product.getId(), Instant.now().plus(40, ChronoUnit.DAYS)));
        String action = "/gym/admin/memberships/" + product.getId() + "/price-change";
        String effective = LocalDate.now().plusDays(30).toString();
        for (String price : new String[]{"9999999999", "24.999", "wrong"}) {
            mockMvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                    .param("newPriceDollars", price).param("effectiveDate", effective).param("reason", "Retain this reason"))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("priceChange", "newPriceDollars"))
                .andExpect(content().string(containsString("Retain this reason")));
        }
        mockMvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                .param("newPriceDollars", "29.99").param("effectiveDate", effective).param("reason", "Annual review"))
            .andExpect(status().isBadRequest());
        assertTrue(priceChangeEvents.findByProductIdOrderByCreatedAtDesc(product.getId()).isEmpty());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
                    .param("newPriceDollars", "29.99").param("effectiveDate", effective).param("reason", "Annual review").param("confirmPriceChange", "true").param("quotedPriceCents", "2500"))
                .andExpect(status().is3xxRedirection());
        }
        assertEquals(1, priceChangeEvents.findByProductIdOrderByCreatedAtDesc(product.getId()).size());
        assertEquals(2500, productRepository.findById(product.getId()).orElseThrow().getPriceCents());
        verify(emailService, times(1)).sendPriceChangeNotification(any(), eq("Audited"), eq(25.0), eq(29.99), any(), eq("Annual review"), any());
        mockMvc.perform(get("/gym/admin/memberships/" + product.getId() + "/price-history").with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Annual review")))
            .andExpect(content().string(containsString("affected")))
            .andExpect(content().string(not(containsString("member notified"))));
    }
}
