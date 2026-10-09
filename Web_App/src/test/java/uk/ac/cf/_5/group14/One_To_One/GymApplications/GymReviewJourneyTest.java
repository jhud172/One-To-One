package uk.ac.cf._5.group14.One_To_One.GymApplications;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymProfileRepository;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.Verification.EmailVerificationService;
import java.util.UUID;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GymReviewJourneyTest {
    @Autowired private GymApplicationService service;
    @Autowired private GymApplicationRepository applications;
    @Autowired private GymApplicationMessageRepository messages;
    @Autowired private UserRepository users;
    @Autowired private GymProfileRepository gyms;
    @Autowired private MockMvc mvc;
    @MockitoBean private EmailService mail;
    @MockitoBean private EmailVerificationService verification;

    private User reviewer() {
        String key = UUID.randomUUID().toString().replace("-", "");
        User reviewer = new User(key + "@example.com", "Review", "Fixture", "review_" + key, "fixture-only");
        reviewer.setRole(Role.PLATFORM_ADMIN);
        return users.save(reviewer);
    }

    private GymApplication application() {
        String key = UUID.randomUUID().toString().replace("-", "");
        GymApplication application = new GymApplication();
        application.setGymName("Review gym " + key); application.setAdminEmail("gym_" + key + "@example.com");
        application.setGymUsername("gym_" + key); application.setRequestedPasswordHash("{noop}fixture-only");
        application.setAddress("1 Fixture Street"); application.setCity("Cardiff");
        application.setContactName("Gym Fixture"); application.setContactPhone("07123456789");
        application.setAccessToken(key + key);
        return applications.save(application);
    }

    @Test
    void concurrentApprovalCreatesOneAccountAndOneApprovalMessage() throws Exception {
        User reviewer = reviewer(); GymApplication application = application();
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            CyclicBarrier start = new CyclicBarrier(2);
            Callable<Long> approve = () -> {
                start.await(5, TimeUnit.SECONDS);
                return service.approve(application.getId(), reviewer, "Welcome").getApprovedUserId();
            };
            Future<Long> first = workers.submit(approve), second = workers.submit(approve);
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        GymApplication approved = applications.findById(application.getId()).orElseThrow();
        User account = users.findById(approved.getApprovedUserId()).orElseThrow();
        assertThat(gyms.findByUserId(account.getId())).isPresent();
        assertThat(messages.findByApplicationIdOrderByCreatedAtAsc(application.getId())).hasSize(1);
        verify(mail, times(1)).sendAdminMessage(eq(application.getAdminEmail()), anyString(), anyString());
        verify(verification, times(1)).sendVerification(any(User.class));
    }

    @Test
    void approvalSurvivesUnavailableNotificationAndCannotBeReopenedByReviewAction() {
        User reviewer = reviewer(); GymApplication application = application();
        doThrow(new IllegalStateException("Fixture mail rejection")).when(mail).sendAdminMessage(anyString(), anyString(), anyString());
        GymApplication approved = service.approve(application.getId(), reviewer, null);
        assertThat(approved.getApprovedUserId()).isNotNull();
        assertThat(messages.findByApplicationIdOrderByCreatedAtAsc(application.getId())).singleElement()
            .satisfies(record -> assertThat(record.isEmailed()).isFalse());
        assertThat(service.approve(application.getId(), reviewer, null).getApprovedUserId()).isEqualTo(approved.getApprovedUserId());
        assertThatThrownBy(() -> service.requestMoreInfo(application.getId(), reviewer, "Subject", "Message"))
            .isInstanceOf(IllegalStateException.class);
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(GymApplicationStatus.APPROVED);
    }

    @Test
    void invalidReviewMessageKeepsApplicationUnchangedAndNativeDraftAccessible() throws Exception {
        User reviewer = reviewer(); GymApplication application = application();
        var result = mvc.perform(post("/admin/gym-applications/" + application.getId() + "/request-info")
                .with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("subject", "Retained subject").param("message", "x".repeat(5001)))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("applicationDraftAction", "request-info")).andReturn();
        assertThat(applications.findById(application.getId()).orElseThrow().getStatus()).isEqualTo(GymApplicationStatus.PENDING);
        assertThat(messages.findByApplicationIdOrderByCreatedAtAsc(application.getId())).isEmpty();
        verifyNoInteractions(mail);
        mvc.perform(get("/admin/gym-applications/" + application.getId()).with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN"))
                .flashAttrs(result.getFlashMap()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("for=\"application-request-info-message\"")));
        mvc.perform(get("/admin/gym-applications").with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN"))
                .param("search", application.getGymName()).param("status", "PENDING"))
            .andExpect(status().isOk()).andExpect(content().string(containsString(application.getGymName())));
        mvc.perform(get("/signup/gym/application/" + application.getAccessToken()))
            .andExpect(status().isOk()).andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Cache-Control", "no-store"));
    }
}
