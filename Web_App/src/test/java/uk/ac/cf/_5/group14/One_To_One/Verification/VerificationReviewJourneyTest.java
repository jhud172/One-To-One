package uk.ac.cf._5.group14.One_To_One.Verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.*;
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
class VerificationReviewJourneyTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private TrainerVerificationService service;
    @Autowired private TrainerVerificationRequestRepository requests;
    @MockitoBean private EmailService mail;

    private User account(Role role) {
        String key = UUID.randomUUID().toString().replace("-", "");
        User account = new User(key + "@example.com", "Verification", "Fixture", "verify_" + key, "fixture-only");
        account.setRole(role);
        return users.save(account);
    }

    @Test
    void concurrentRequestCreationProducesOneReviewAndNeedsInfoRemainsOpen() throws Exception {
        User trainer = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        CyclicBarrier start = new CyclicBarrier(2);
        java.util.function.Supplier<Boolean> submit = () -> {
            try {
                start.await(5, TimeUnit.SECONDS);
                service.createVerificationRequest(trainer.getId(), null, "Credential summary");
                return true;
            } catch (IllegalStateException alreadyOpen) { return false; }
            catch (Exception failure) { throw new RuntimeException(failure); }
        };
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = workers.submit(submit::get), second = workers.submit(submit::get);
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        var created = requests.findAll().stream().filter(request -> trainer.getId().equals(request.getTrainerUserId())).toList();
        assertThat(created).hasSize(1);
        service.requestMoreInfo(created.getFirst().getId(), reviewer.getId(), "Please provide qualification details");
        assertThatThrownBy(() -> service.createVerificationRequest(trainer.getId(), null, "Second draft"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("open verification request");
    }

    @Test
    void nonTrainerWrongGymAndOversizedNotesDoNotCreateRequests() {
        User client = account(Role.CLIENT), trainer = account(Role.TRAINER);
        assertThatThrownBy(() -> service.createVerificationRequest(client.getId(), null, "Notes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createVerificationRequest(trainer.getId(), 999999L, "Notes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.createVerificationRequest(trainer.getId(), null, "x".repeat(1001))).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests.findTopByTrainerUserIdOrderBySubmittedAtDesc(trainer.getId())).isEmpty();
        verifyNoInteractions(mail);
    }

    @Test
    void competingApproveAndRejectLeaveOneConsistentDecision() throws Exception {
        User reviewer = account(Role.PLATFORM_ADMIN), trainer = account(Role.TRAINER);
        TrainerVerificationRequest request = requests.save(new TrainerVerificationRequest(trainer.getId(), null));
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> approve = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try { service.approveTrainer(request.getId(), reviewer.getId(), "Checked credentials"); return true; }
                catch (IllegalStateException alreadyReviewed) { return false; }
            });
            Future<Boolean> reject = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try { service.rejectTrainer(request.getId(), reviewer.getId(), "Missing credentials"); return true; }
                catch (IllegalStateException alreadyReviewed) { return false; }
            });
            assertThat(approve.get(15, TimeUnit.SECONDS)).isNotEqualTo(reject.get(15, TimeUnit.SECONDS));
        }
        VerificationStatus status = requests.findById(request.getId()).orElseThrow().getStatus();
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isEqualTo(status == VerificationStatus.APPROVED);
        verify(mail, times(1)).sendTrainerVerificationUpdate(any(User.class), anyString(), anyString());
    }

    @Test
    void approvalConfirmationAndInvalidNotesRetainNativeDraftWithoutVerifyingTrainer() throws Exception {
        User reviewer = account(Role.PLATFORM_ADMIN), trainer = account(Role.TRAINER);
        TrainerVerificationRequest request = requests.save(new TrainerVerificationRequest(trainer.getId(), null));
        mvc.perform(post("/super-admin/verification/" + request.getId() + "/approve")
                .with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")).with(csrf()).param("adminNotes", "Retained notes"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("verificationDraftNotes", "Retained notes"));
        var invalid = mvc.perform(post("/super-admin/verification/" + request.getId() + "/approve")
                .with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")).with(csrf())
                .param("qualificationsChecked", "true").param("adminNotes", "x".repeat(1001)))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage")).andReturn();
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isFalse();
        verifyNoInteractions(mail);
        mvc.perform(get("/super-admin/verification/" + request.getId()).with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN"))
                .flashAttrs(invalid.getFlashMap()))
            .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"qualificationsChecked\"")));
        mvc.perform(get("/super-admin/verification/999999").with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")))
            .andExpect(status().isNotFound());
    }

    @Test
    void existingIndependentTrainerCanSubmitAConfirmedReviewWithoutGrantingVerification() throws Exception {
        User trainer = account(Role.TRAINER);
        mvc.perform(get("/trainer/verification").with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(containsString("professional-review-v2")));
        for (String path : new String[]{"/trainer/library", "/trainer/templates", "/trainer/profile/edit"}) {
            mvc.perform(get(path).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("href=\"/trainer/verification\"")));
        }
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("notes", "Qualifications <draft> & insurance"))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Qualifications &lt;draft&gt; &amp; insurance")));
        assertThat(service.getRequestsForTrainer(trainer.getId())).isEmpty();
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("notes", "Qualifications <draft> & insurance").param("confirmed", "true").param("trainerUserId", "99999"))
            .andExpect(redirectedUrl("/trainer/verification")).andExpect(flash().attribute("reviewSaved", true));
        var request = service.getLatestRequestForTrainer(trainer.getId());
        assertThat(request.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(request.getGymId()).isNull();
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isFalse();
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("notes", "Repeated details").param("confirmed", "true"))
            .andExpect(status().isBadRequest());
        assertThat(service.getRequestsForTrainer(trainer.getId())).hasSize(1);
        verifyNoInteractions(mail);
    }

    @Test
    void trainerCanRespondToTheirOwnInformationRequestAndSeePrivatePreviousRequests() throws Exception {
        User trainer = account(Role.TRAINER), other = account(Role.TRAINER);
        var previous = new TrainerVerificationRequest(trainer.getId(), null);
        previous.setStatus(VerificationStatus.REJECTED);
        previous.setNotes("Previous application");
        requests.save(previous);
        var request = new TrainerVerificationRequest(trainer.getId(), null);
        request.setStatus(VerificationStatus.NEEDS_INFO);
        request.setNotes("Original qualifications");
        request.setAdminNotes("Please clarify insurance");
        requests.save(request);
        var otherRequest = new TrainerVerificationRequest(other.getId(), null);
        otherRequest.setNotes("Private other-trainer evidence");
        requests.save(otherRequest);
        mvc.perform(get("/trainer/verification").with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Please clarify insurance")))
            .andExpect(content().string(containsString("Previous application")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Private other-trainer evidence"))));
        mvc.perform(post("/trainer/verification/" + request.getId() + "/respond")
                .with(user(other.getUsername()).roles("TRAINER")).with(csrf()).param("notes", "Unauthorised edit").param("confirmed", "true"))
            .andExpect(status().isNotFound());
        assertThatThrownBy(() -> service.updateTrainerNotesForTrainer(request.getId(), other.getId(), "Unauthorised service edit"))
            .isInstanceOf(IllegalArgumentException.class);
        mvc.perform(post("/trainer/verification/" + request.getId() + "/respond")
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("notes", "Clarified insurance <details>").param("confirmed", "true"))
            .andExpect(redirectedUrl("/trainer/verification"));
        var saved = requests.findById(request.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(saved.getNotes()).isEqualTo("Clarified insurance <details>");
        assertThat(saved.getAdminNotes()).isEqualTo("Please clarify insurance");
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isFalse();
        verifyNoInteractions(mail);
    }

    @Test
    void verificationWorkspaceRejectsOversizedDraftsVerifiedAccountsMissingCsrfAndStaleRoles() throws Exception {
        User trainer = account(Role.TRAINER), client = account(Role.CLIENT);
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("notes", "x".repeat(1001)).param("confirmed", "true"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER"))
                .param("notes", "Missing CSRF").param("confirmed", "true"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/trainer/verification").with(user(client.getUsername()).roles("TRAINER")))
            .andExpect(status().isForbidden());
        trainer.setTrainerVerified(true);
        users.save(trainer);
        mvc.perform(post("/trainer/verification/request").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("notes", "Already verified").param("confirmed", "true"))
            .andExpect(status().isBadRequest());
        assertThat(service.getRequestsForTrainer(trainer.getId())).isEmpty();
        trainer.setEnabled(false);
        users.save(trainer);
        mvc.perform(get("/trainer/verification").with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }

    @Test
    void simultaneousTrainerResponseAndApprovalFinishWithoutInvertingAccountRequestLocks() throws Exception {
        User trainer = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = new TrainerVerificationRequest(trainer.getId(), null);
        request.setStatus(VerificationStatus.NEEDS_INFO);
        request.setNotes("Original qualifications");
        requests.save(request);
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> respond = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    service.updateTrainerNotesForTrainer(request.getId(), trainer.getId(), "Clarified qualification details");
                    return true;
                } catch (IllegalStateException alreadyReviewed) { return false; }
            });
            Future<?> approve = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                service.approveTrainer(request.getId(), reviewer.getId(), "Synthetic checked credentials");
                return null;
            });
            boolean responded = respond.get(15, TimeUnit.SECONDS);
            approve.get(15, TimeUnit.SECONDS);
            var saved = requests.findById(request.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(VerificationStatus.APPROVED);
            assertThat(saved.getNotes()).isEqualTo(responded ? "Clarified qualification details" : "Original qualifications");
        }
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isTrue();
        verify(mail, times(1)).sendTrainerVerificationUpdate(any(User.class), eq("APPROVED"), anyString());
    }

    @Test
    void reviewedReplayDoesNotRepeatNotificationAndNonPlatformReviewerIsRejected() {
        User reviewer = account(Role.PLATFORM_ADMIN), trainer = account(Role.TRAINER), gymAdmin = account(Role.GYM_ADMIN);
        TrainerVerificationRequest request = requests.save(new TrainerVerificationRequest(trainer.getId(), null));
        assertThatThrownBy(() -> service.approveTrainer(request.getId(), gymAdmin.getId(), "Notes"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        service.approveTrainer(request.getId(), reviewer.getId(), "Checked credentials");
        service.approveTrainer(request.getId(), reviewer.getId(), "Checked credentials");
        verify(mail, times(1)).sendTrainerVerificationUpdate(any(User.class), eq("APPROVED"), eq("Checked credentials"));
    }
}
