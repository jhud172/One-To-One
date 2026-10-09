package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.util.UUID;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentPolicyQueueTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private OffPlatformPaymentAttemptRepository attempts;
    @Autowired private MessageThreadRepository threads;
    @Autowired private TrainerClientLinkRepository links;

    @Test
    void flaggedPreviewRedactsSensitiveDetailsBeforeClipping() {
        String text = "paypal fixture@example.com +44 7700 900123 4111 1111 1111 1111 GB82 WEST 1234 5698 7654 32 https://example.com/private?token=fixture";
        String preview = AdminOffPlatformPaymentController.redactPreview(text);
        assertThat(preview).contains("paypal").doesNotContain("fixture@example.com", "7700", "4111", "GB82", "private?token");
    }

    @Test
    void nativeQueueFiltersAndPagesWithoutRenderingRawBlockedBody() throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        User admin = new User(key + "@example.com", "Policy", "Fixture", "policy_" + key, "fixture-only");
        admin.setRole(Role.PLATFORM_ADMIN); admin = users.save(admin);
        User client = new User("client_" + key + "@example.com", "Client", "Fixture", "client_" + key, "fixture-only");
        client.setRole(Role.CLIENT); client = users.save(client);
        User trainer = new User("trainer_" + key + "@example.com", "Trainer", "Fixture", "trainer_" + key, "fixture-only");
        trainer.setRole(Role.TRAINER); trainer = users.save(trainer);
        var link = links.save(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var thread = threads.save(new MessageThread(client.getId(), trainer.getId(), link.getId(), MessageThreadStatus.OPEN));
        for (int index = 0; index < 26; index++) {
            attempts.save(new OffPlatformPaymentAttempt(thread.getId(), client.getId(), key, "paypal confidential@example.com 4111 1111 1111 1111"));
        }
        mvc.perform(get("/admin/off-platform-payments").with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).param("keyword", key))
            .andExpect(status().isOk()).andExpect(model().attribute("hasNext", true))
            .andExpect(content().string(not(containsString("confidential@example.com"))))
            .andExpect(content().string(not(containsString("4111 1111 1111 1111"))));
        var second = mvc.perform(get("/admin/off-platform-payments").with(user(admin.getUsername()).roles("PLATFORM_ADMIN"))
                .param("keyword", key).param("page", "1"))
            .andExpect(status().isOk()).andExpect(model().attribute("hasPrevious", true)).andReturn();
        assertThat((java.util.List<?>) second.getModelAndView().getModel().get("attempts")).hasSize(1);
        mvc.perform(get("/admin/off-platform-payments").with(user("fixture_client").roles("CLIENT")))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
    }
}
