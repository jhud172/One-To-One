package uk.ac.cf._5.group14.One_To_One.Support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSupportJourneyTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private SupportRequestRepository requests;
    @MockitoBean private EmailService mail;

    private User admin() {
        String key = UUID.randomUUID().toString().replace("-", "");
        User account = new User(key + "@example.com", "Support", "Fixture", "support_" + key, "fixture-only");
        account.setRole(Role.PLATFORM_ADMIN);
        return users.save(account);
    }

    private SupportRequest request() {
        SupportRequest request = new SupportRequest();
        request.setRequestType(SupportRequestType.QUERY);
        request.setSubject("Fixture issue " + UUID.randomUUID());
        request.setMessage("Private original request");
        request.setSubmitterEmail("recipient@example.com");
        request.setAllowEmailReply(true);
        return requests.save(request);
    }

    @Test
    void failedResponseRetainsDraftAndDoesNotResolveCase() throws Exception {
        User admin = admin(); SupportRequest original = request();
        doThrow(new IllegalStateException("Synthetic provider failure")).when(mail).sendAdminMessage(anyString(), anyString(), anyString());
        mvc.perform(post("/admin/feedback/" + original.getId() + "/respond")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf()).param("response", "Retained response draft"))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("responseDraft", "Retained response draft"));
        SupportRequest unchanged = requests.findById(original.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SupportRequestStatus.NEW);
        assertThat(unchanged.getAdminResponse()).isNull();
        assertThat(unchanged.getRespondedAt()).isNull();
    }

    @Test
    void noConsentAndOversizedResponseNeverContactMailProvider() throws Exception {
        User admin = admin(); SupportRequest original = request();
        original.setAllowEmailReply(false); requests.save(original);
        mvc.perform(post("/admin/feedback/" + original.getId() + "/respond")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf()).param("response", "A response"))
            .andExpect(flash().attributeExists("adminFeedbackError"));
        mvc.perform(post("/admin/feedback/" + original.getId() + "/respond")
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf()).param("response", "x".repeat(5001)))
            .andExpect(flash().attributeExists("adminFeedbackError"));
        verifyNoInteractions(mail);
    }

    @Test
    void outreachRequiresServerPreviewAndConsumedPreviewCannotBeReplayed() throws Exception {
        User admin = admin();
        var preview = mvc.perform(post("/admin/outreach/send").with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("audience", "SPECIFIC").param("specificEmail", "recipient@example.com")
                .param("subject", "Reviewed subject").param("message", "Reviewed body"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/dashboard")).andReturn();
        verifyNoInteractions(mail);
        var page = mvc.perform(get("/admin/dashboard").cookie(preview.getResponse().getCookie("SESSION"))
                .with(user(admin.getUsername()).roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(model().attributeExists("outreachPreview")).andReturn();
        var draft = (AdminSupportController.OutreachPreview) page.getModelAndView().getModel().get("outreachPreview");
        assertThat(draft.recipients()).containsExactly("recipient@example.com");
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/admin/outreach/send").cookie(preview.getResponse().getCookie("SESSION")).with(user(admin.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                    .param("previewToken", draft.token()).param("message", "Forged body").param("specificEmail", "forged@example.com"))
                .andExpect(status().is3xxRedirection());
        }
        verify(mail, times(1)).sendAdminMessage("recipient@example.com", "Reviewed subject", "Reviewed body");
        verifyNoMoreInteractions(mail);
    }

    @Test
    void gymAdministratorsCannotReadGlobalSupportOrTriggerOutreach() throws Exception {
        for (String path : new String[]{"/admin/dashboard", "/admin/feedback"}) {
            mvc.perform(get(path).with(user("fixture_gym").roles("GYM_ADMIN")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        }
        mvc.perform(post("/admin/outreach/send").with(user("fixture_gym").roles("GYM_ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
        verifyNoInteractions(mail);
    }
}
