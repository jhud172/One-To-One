package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Merch.*;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderRepository;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.PaymentStatus;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.RefundStatus;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMerchJourneyTest {
    @Autowired private MockMvc mvc;
    @Autowired private MerchProductRepository products;
    @Autowired private MerchOrderService orders;
    @Autowired private MerchOrderRepository orderRepository;
    @Autowired private UserRepository users;

    private User account(Role role) {
        String key = UUID.randomUUID().toString().replace("-", "");
        User account = new User(key + "@example.com", "Merch", "Fixture", "admin_merch_" + key, "fixture-only");
        account.setRole(role);
        return users.save(account);
    }

    private MerchProduct product() {
        MerchProduct product = new MerchProduct();
        product.setName("Original product " + UUID.randomUUID());
        product.setPrice(new BigDecimal("12.35"));
        product.setStockQuantity(10);
        return products.save(product);
    }

    @Test
    void invalidPriceAndStockKeepDraftWithoutCreatingProduct() throws Exception {
        User admin = account(Role.PLATFORM_ADMIN);
        String draftName = "Retained draft " + UUID.randomUUID();
        long count = products.count();
        mvc.perform(post("/admin/merch/create").with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("name", draftName).param("price", "invalid-price").param("stockQuantity", "-2"))
            .andExpect(status().isBadRequest())
            .andExpect(content().string(containsString(draftName)))
            .andExpect(content().string(containsString("invalid-price")))
            .andExpect(content().string(containsString("for=\"merch-price\"")))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
        assertThat(products.count()).isEqualTo(count);
    }

    @Test
    void stockReservationPreventsStaleAdminEditAndCanBeReviewedBeforeResubmission() throws Exception {
        User admin = account(Role.PLATFORM_ADMIN), client = account(Role.CLIENT);
        MerchProduct original = product();
        orders.createPendingOrder(client, original, 2);
        String draftName = "Reviewed product " + UUID.randomUUID();
        mvc.perform(post("/admin/merch/" + original.getId() + "/update")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("name", draftName).param("price", "14.75").param("stockQuantity", "10").param("originalStock", "10"))
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString(draftName)))
            .andExpect(model().attribute("currentStock", 8));
        MerchProduct unchanged = products.findById(original.getId()).orElseThrow();
        assertThat(unchanged.getStockQuantity()).isEqualTo(8);
        assertThat(unchanged.getName()).isEqualTo(original.getName());
        assertThat(unchanged.getPrice()).isEqualByComparingTo("12.35");
        mvc.perform(post("/admin/merch/" + original.getId() + "/update")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("name", draftName).param("price", "14.75").param("stockQuantity", "8").param("originalStock", "8"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/merch"));
        MerchProduct updated = products.findById(original.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo(draftName);
        assertThat(updated.getStockQuantity()).isEqualTo(8);
    }

    @Test
    void createUsesTrustedAccountAndPublicStoreReflectsSavedDetails() throws Exception {
        User admin = account(Role.PLATFORM_ADMIN);
        String name = "Saved public product " + UUID.randomUUID();
        mvc.perform(post("/admin/merch/create").with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("name", name).param("price", "14.75").param("stockQuantity", "5").param("active", "true")
                .param("createdBy", "999999").param("id", "999999").param("imageUrl", "https://example.com/forged.png"))
            .andExpect(status().is3xxRedirection());
        MerchProduct saved = products.findAll().stream().filter(product -> name.equals(product.getName())).findFirst().orElseThrow();
        assertThat(saved.getCreatedBy()).isEqualTo(admin.getId());
        assertThat(saved.getImageUrl()).isNull();
        assertThat(saved.getId()).isNotEqualTo(999999L);
        mvc.perform(get("/merch").param("search", name)).andExpect(status().isOk())
            .andExpect(content().string(containsString(name))).andExpect(content().string(containsString("£14.75")));
    }

    @Test
    void retirementKeepsRestoredStockAndLeavesConfirmedOrdersUnchanged() throws Exception {
        User admin = account(Role.PLATFORM_ADMIN), client = account(Role.CLIENT);
        MerchProduct product = product();
        var paid = orders.createPendingOrder(client, product, 2);
        orders.markCheckoutSessionCreated(paid.getId(), "SIMULATED", "fixture-paid-reference", null);
        orders.completePaidOrder(paid.getId(), "fixture-paid-reference");
        var pending = orders.createPendingOrder(client, product, 3);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(5);
        mvc.perform(post("/admin/merch/" + product.getId() + "/delete")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        MerchProduct retired = products.findById(product.getId()).orElseThrow();
        assertThat(retired.isActive()).isFalse();
        assertThat(retired.getStockQuantity()).isEqualTo(8);
        assertThat(orderRepository.findById(pending.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        var confirmed = orderRepository.findById(paid.getId()).orElseThrow();
        assertThat(confirmed.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(confirmed.getRefundStatus()).isEqualTo(RefundStatus.NONE);
    }

    @Test
    void administrationIsRoleAndCsrfProtectedAndMissingProductsReturnNotFound() throws Exception {
        User client = account(Role.CLIENT), admin = account(Role.PLATFORM_ADMIN);
        mvc.perform(get("/admin/merch").with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/admin/merch/create").with(user(admin.getUsername()).roles("PLATFORM_ADMIN"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/merch/999999/edit").with(user(admin.getUsername()).roles("PLATFORM_ADMIN"))).andExpect(status().isNotFound());
    }
}
