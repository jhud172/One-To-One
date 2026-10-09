package uk.ac.cf._5.group14.One_To_One.Verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class VerificationEvidenceJourneyTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerVerificationService verification;
    @Autowired VerificationEvidenceService evidence;
    @Autowired TrainerVerificationRequestRepository requests;
    @Autowired VerificationDocumentRepository documents;
    @Autowired VerificationEventRepository events;
    @MockitoBean EmailService mail;

    private User account(Role role) {
        String key = UUID.randomUUID().toString().replace("-", "");
        User user = new User(key + "@example.com", "Evidence", "Fixture", "evidence_" + key, "fixture-only");
        user.setRole(role);
        return users.save(user);
    }

    private MockMultipartFile png(String name, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("files", name, "image/png", output.toByteArray());
    }

    private List<VerificationDocumentRepository.Summary> files(TrainerVerificationRequest request) {
        return documents.findByRequestIdInOrderByCreatedAtAscIdAsc(List.of(request.getId()));
    }

    private List<VerificationEvent> history(TrainerVerificationRequest request) {
        return events.findByRequestIdInOrderByCreatedAtAscIdAsc(List.of(request.getId()));
    }

    @Test
    void privateNativeUploadAndDownloadUseFreshAccountOwnershipAndAttachmentHeaders() throws Exception {
        User trainer = account(Role.TRAINER), other = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualifications <original>");
        mvc.perform(multipart("/trainer/verification/" + request.getId() + "/documents")
                .file(png("C:\\private\\Certificate<script>.png", 2, 2)).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(redirectedUrl("/trainer/verification")).andExpect(flash().attribute("reviewFilesSaved", true));
        var saved = files(request).getFirst();
        assertThat(saved.getFileName()).isEqualTo("Certificate_script_.png");
        assertThat(history(request)).extracting(VerificationEvent::getAction)
            .containsExactly(VerificationEvent.Action.SUBMITTED, VerificationEvent.Action.DOCUMENT_ADDED);
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isFalse();
        for (User allowed : List.of(trainer, reviewer)) {
            mvc.perform(get("/verification/documents/" + saved.getId()).with(user(allowed.getUsername()).roles(allowed.getRole().name())))
                .andExpect(status().isOk()).andExpect(content().contentType("application/octet-stream"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", containsString("sandbox")));
        }
        mvc.perform(get("/verification/documents/" + saved.getId()).with(user(other.getUsername()).roles("TRAINER")))
            .andExpect(status().isNotFound());
        User gym = account(Role.GYM_ADMIN);
        assertThatThrownBy(() -> evidence.download(saved.getId(), gym.getUsername())).isInstanceOf(IllegalArgumentException.class);
        trainer.setRole(Role.CLIENT); users.save(trainer);
        mvc.perform(get("/verification/documents/" + saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isNotFound());
        reviewer.setEnabled(false); users.save(reviewer);
        mvc.perform(get("/verification/documents/" + saved.getId()).with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }

    @Test
    void rejectedBatchDoesNotStoreAnyBytesOrEventsAndImagesAreReencoded() throws Exception {
        User trainer = account(Role.TRAINER);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualification summary");
        var invalid = new MockMultipartFile("files", "malicious.pdf", "application/pdf", "<html>script</html>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/trainer/verification/" + request.getId() + "/documents")
                .file(png("valid.png", 2, 2)).file(invalid).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(status().isBadRequest()).andExpect(content().string(containsString("No files from this upload were saved")));
        assertThat(files(request)).isEmpty();
        assertThat(history(request)).hasSize(1);
        assertThatThrownBy(() -> evidence.upload(request.getId(), trainer.getId(), List.of(
            new MockMultipartFile("files", "big.png", "image/png", new byte[2 * 1024 * 1024 + 1]))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evidence.upload(request.getId(), trainer.getId(), List.of(png("wide.png", 4097, 1))))
            .isInstanceOf(IllegalArgumentException.class);
        byte[] original = png("scan.png", 3, 3).getBytes();
        var appended = new ByteArrayOutputStream(); appended.write(original); appended.write("<script>private marker</script>".getBytes(StandardCharsets.UTF_8));
        evidence.upload(request.getId(), trainer.getId(), List.of(new MockMultipartFile("files", "scan.png", "text/html", appended.toByteArray())));
        var saved = files(request).getFirst();
        byte[] clean = evidence.download(saved.getId(), trainer.getUsername()).bytes();
        assertThat(new String(clean, StandardCharsets.ISO_8859_1)).doesNotContain("private marker");
        var decoded = ImageIO.read(new java.io.ByteArrayInputStream(clean));
        assertThat(decoded.getWidth()).isEqualTo(3);
        assertThat(decoded.getHeight()).isEqualTo(3);
        assertThat(decoded.getRGB(0, 0)).isEqualTo(0xff000000);
        assertThat(saved.getContentType()).isEqualTo("image/png");
        verifyNoInteractions(mail);
    }

    @Test
    void uploadRejectsForeignRequestsMissingCsrfClosedReviewsAndDisabledOrStaleTrainers() throws Exception {
        User trainer = account(Role.TRAINER), other = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualification summary");
        String route = "/trainer/verification/" + request.getId() + "/documents";
        mvc.perform(multipart(route).file(png("certificate.png", 2, 2)).with(user(other.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(status().isNotFound());
        mvc.perform(multipart(route).file(png("certificate.png", 2, 2)).with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isUnauthorized());
        trainer.setEnabled(false); users.save(trainer);
        mvc.perform(multipart(route).file(png("certificate.png", 2, 2)).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(status().isForbidden());
        trainer.setEnabled(true); trainer.setRole(Role.CLIENT); users.save(trainer);
        mvc.perform(multipart(route).file(png("certificate.png", 2, 2)).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(status().isForbidden());
        trainer.setRole(Role.TRAINER); users.save(trainer);
        verification.rejectTrainer(request.getId(), reviewer.getId(), "Missing credentials");
        mvc.perform(multipart(route).file(png("certificate.png", 2, 2)).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(status().isBadRequest());
        assertThat(files(request)).isEmpty();
        assertThat(history(request)).extracting(VerificationEvent::getAction)
            .containsExactly(VerificationEvent.Action.SUBMITTED, VerificationEvent.Action.REJECTED);
    }

    @Test
    void concurrentUploadsCannotExceedTheRequestQuota() throws Exception {
        User trainer = account(Role.TRAINER);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualification summary");
        evidence.upload(request.getId(), trainer.getId(), List.of(png("1.png", 2, 2), png("2.png", 2, 2), png("3.png", 2, 2), png("4.png", 2, 2)));
        CyclicBarrier start = new CyclicBarrier(2);
        Callable<Boolean> upload = () -> {
            start.await(5, TimeUnit.SECONDS);
            try { evidence.upload(request.getId(), trainer.getId(), List.of(png("last.png", 2, 2))); return true; }
            catch (IllegalArgumentException full) { return false; }
        };
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(upload); var second = workers.submit(upload);
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(files(request)).hasSize(5);
        assertThat(history(request)).filteredOn(event -> event.getAction() == VerificationEvent.Action.DOCUMENT_ADDED).hasSize(5);
        verifyNoInteractions(mail);
    }

    @Test
    void repeatedReviewsRetainEachEarlierNoteAndIdempotentDecisionsAddNoDuplicateEvents() throws Exception {
        User trainer = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN), other = account(Role.TRAINER);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Original <qualification>");
        verification.requestMoreInfo(request.getId(), reviewer.getId(), "First insurance question");
        verification.requestMoreInfo(request.getId(), reviewer.getId(), "First insurance question");
        verification.updateTrainerNotesForTrainer(request.getId(), trainer.getId(), "Revised qualification");
        verification.requestMoreInfo(request.getId(), reviewer.getId(), "Second insurance question");
        verification.rejectTrainer(request.getId(), reviewer.getId(), "Missing insurance");
        verification.rejectTrainer(request.getId(), reviewer.getId(), "Missing insurance");
        var newRequest = verification.createVerificationRequest(trainer.getId(), null, "New application");
        assertThat(history(request)).extracting(VerificationEvent::getAction).containsExactly(
            VerificationEvent.Action.SUBMITTED, VerificationEvent.Action.NEEDS_INFO, VerificationEvent.Action.RESPONDED,
            VerificationEvent.Action.NEEDS_INFO, VerificationEvent.Action.REJECTED);
        assertThat(history(request).getFirst().getTrainerNotes()).isEqualTo("Original <qualification>");
        assertThat(history(request).get(1).getAdminNotes()).isEqualTo("First insurance question");
        assertThat(history(request).get(2).getActorUserId()).isEqualTo(trainer.getId());
        assertThat(history(request).get(3).getActorUserId()).isEqualTo(reviewer.getId());
        assertThat(history(newRequest)).hasSize(1);
        mvc.perform(get("/trainer/verification").with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Original &lt;qualification&gt;")))
            .andExpect(content().string(containsString("First insurance question")))
            .andExpect(content().string(containsString("Second insurance question")));
        mvc.perform(get("/trainer/verification").with(user(other.getUsername()).roles("TRAINER")))
            .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(containsString("First insurance question"))));
        mvc.perform(get("/super-admin/verification/" + request.getId()).with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(content().string(containsString("First insurance question")));
        reviewer.setRole(Role.CLIENT); users.save(reviewer);
        mvc.perform(get("/super-admin/verification/" + request.getId()).with(user(reviewer.getUsername()).roles("PLATFORM_ADMIN")))
            .andExpect(status().isForbidden());
    }

    @Test
    void olderRequestsKeepAnHonestSnapshotBeforeTheirFirstNewResponse() {
        User trainer = account(Role.TRAINER);
        var request = new TrainerVerificationRequest(trainer.getId(), null);
        request.setStatus(VerificationStatus.NEEDS_INFO); request.setNotes("Earlier notes"); request.setAdminNotes("Earlier question");
        requests.save(request);
        verification.updateTrainerNotesForTrainer(request.getId(), trainer.getId(), "New response");
        assertThat(history(request)).extracting(VerificationEvent::getAction)
            .containsExactly(VerificationEvent.Action.LEGACY_SNAPSHOT, VerificationEvent.Action.RESPONDED);
        assertThat(history(request).getFirst().getActorUserId()).isNull();
        assertThat(history(request).getFirst().getTrainerNotes()).isEqualTo("Earlier notes");
    }

    @Test
    void pdfEvidenceDownloadsIntactAndOwnerCanRemoveItOnlyWhileTheReviewIsOpen() throws Exception {
        User trainer = account(Role.TRAINER), other = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualifications");
        byte[] pdf = ("%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
            + "2 0 obj\n<< /Type /Pages /Kids [] /Count 0 >>\nendobj\n"
            + "trailer\n<< /Root 1 0 R /Size 3 >>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
        evidence.upload(request.getId(), trainer.getId(), List.of(new MockMultipartFile("files", "qualification.pdf", "application/pdf", pdf)));
        var file = files(request).getFirst();
        assertThat(file.getContentType()).isEqualTo("application/pdf");
        assertThat(evidence.download(file.getId(), reviewer.getUsername()).bytes()).isEqualTo(pdf);
        String remove = "/trainer/verification/" + request.getId() + "/documents/" + file.getId() + "/remove";
        mvc.perform(post(remove).with(user(other.getUsername()).roles("TRAINER")).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post(remove).with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isUnauthorized());
        mvc.perform(post(remove).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
            .andExpect(redirectedUrl("/trainer/verification")).andExpect(flash().attribute("reviewFileRemoved", true));
        assertThat(files(request)).isEmpty();
        assertThat(documents.findContentById(file.getId())).isEmpty();
        mvc.perform(get("/verification/documents/" + file.getId()).with(user(trainer.getUsername()).roles("TRAINER")))
            .andExpect(status().isNotFound());
        assertThat(history(request)).extracting(VerificationEvent::getAction).containsExactly(
            VerificationEvent.Action.SUBMITTED, VerificationEvent.Action.DOCUMENT_ADDED, VerificationEvent.Action.DOCUMENT_REMOVED);
        evidence.upload(request.getId(), trainer.getId(), List.of(png("replacement.png", 2, 2)));
        var replacement = files(request).getFirst();
        verification.approveTrainer(request.getId(), reviewer.getId(), "Synthetic test approval");
        assertThatThrownBy(() -> evidence.remove(request.getId(), trainer.getId(), replacement.getId())).isInstanceOf(IllegalStateException.class);
        assertThat(files(request)).hasSize(1);
    }

    @Test
    void uploadAndApprovalUseTheSameLockOrderAndNeverAttachFilesAfterClosure() throws Exception {
        User trainer = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualifications");
        CyclicBarrier start = new CyclicBarrier(2);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var upload = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try { evidence.upload(request.getId(), trainer.getId(), List.of(png("concurrent.png", 2, 2))); return true; }
                catch (IllegalStateException closed) { return false; }
            });
            var approve = workers.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                verification.approveTrainer(request.getId(), reviewer.getId(), "Synthetic test approval");
                return true;
            });
            boolean attached = upload.get(15, TimeUnit.SECONDS);
            assertThat(approve.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(files(request)).hasSize(attached ? 1 : 0);
        }
        assertThat(history(request).getLast().getAction()).isEqualTo(VerificationEvent.Action.APPROVED);
        assertThat(users.findById(trainer.getId()).orElseThrow().isTrainerVerified()).isTrue();
        verify(mail, times(1)).sendTrainerVerificationUpdate(any(User.class), eq("APPROVED"), anyString());
    }

    @Test
    void disabledReviewersAndTrainersCannotStartOrApproveVerification() {
        User trainer = account(Role.TRAINER), reviewer = account(Role.PLATFORM_ADMIN);
        var request = verification.createVerificationRequest(trainer.getId(), null, "Qualifications");
        reviewer.setEnabled(false); users.save(reviewer);
        assertThatThrownBy(() -> verification.approveTrainer(request.getId(), reviewer.getId(), "Checked"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        reviewer.setEnabled(true); users.save(reviewer);
        trainer.setEnabled(false); users.save(trainer);
        assertThatThrownBy(() -> verification.approveTrainer(request.getId(), reviewer.getId(), "Checked"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        User disabled = account(Role.TRAINER); disabled.setEnabled(false); users.save(disabled);
        assertThatThrownBy(() -> verification.createVerificationRequest(disabled.getId(), null, "Qualifications"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(history(request)).hasSize(1);
        verifyNoInteractions(mail);
    }
}
