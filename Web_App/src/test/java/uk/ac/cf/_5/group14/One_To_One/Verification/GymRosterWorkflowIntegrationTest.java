package uk.ac.cf._5.group14.One_To_One.Verification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.jsoup.Jsoup;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import uk.ac.cf._5.group14.One_To_One.GymProfile.*;
import uk.ac.cf._5.group14.One_To_One.GymAffiliation.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GymRosterWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired GymProfileRepository gyms;
    @Autowired GymAffiliationRepository connections;
    @Autowired TrainerVerificationRequestRepository requests;
    @Autowired GymTrainerRosterService roster;
    @Autowired GymOperationsService operations;
    private static final List<String> LOCALES = List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh");

    private User account(Role role, String first, String last) {
        String unique = UUID.randomUUID().toString().replace("-", "");
        var user = new User(unique + "@example.com", first, last, "fixture_" + unique, "fixture-only");
        user.setRole(role); return users.saveAndFlush(user);
    }

    private User admin() {
        var user = account(Role.GYM_ADMIN, "Owned", "Gym");
        var gym = gyms.saveAndFlush(new GymProfile(user.getId(), "Owned operations gym"));
        user.setGymId(gym.getId()); return users.saveAndFlush(user);
    }

    private TrainerVerificationRequest review(User trainer, User admin, VerificationStatus status, String notes) {
        var row = new TrainerVerificationRequest(); row.setTrainerUserId(trainer.getId()); row.setGymId(admin.getGymId());
        row.setStatus(status); row.setNotes(notes); row.setSubmittedAt(Instant.parse("2026-10-01T12:00:00Z"));
        return requests.saveAndFlush(row);
    }

    private Fixture fixture() {
        var admin = admin(); var other = admin(); var trainers = new ArrayList<User>();
        for (int index = 0; index < 26; index++) {
            var trainer = account(Role.TRAINER, "Roster", index == 0 ? "00 100%_!+ & <literal>" : "%02d".formatted(index));
            trainer.setGymId(admin.getGymId()); trainer.setTrainerVerified(index % 2 == 0);
            trainer.setEnabled(index != 25); trainer = users.saveAndFlush(trainer); trainers.add(trainer);
            review(trainer, admin, VerificationStatus.REJECTED, "Earlier own review");
            review(trainer, admin, List.of(VerificationStatus.PENDING, VerificationStatus.NEEDS_INFO, VerificationStatus.APPROVED, VerificationStatus.REJECTED).get(index % 4), "Own latest review " + index);
        }
        var additional = account(Role.TRAINER, "Roster", "26"); additional.setGymId(other.getGymId()); additional.setTrainerVerified(true);
        additional = users.saveAndFlush(additional);
        connections.saveAndFlush(new TrainerGymAffiliation(additional.getId(), admin.getGymId(), GymAffiliationStatus.ACTIVE, "GYM"));
        trainers.add(additional);
        var ended = account(Role.TRAINER, "Roster", "Ended"); ended.setGymId(admin.getGymId()); users.saveAndFlush(ended);
        connections.saveAndFlush(new TrainerGymAffiliation(ended.getId(), admin.getGymId(), GymAffiliationStatus.ENDED, "LEGACY"));
        review(ended, admin, VerificationStatus.NEEDS_INFO, "ENDED PRIVATE NOTES");
        var pending = account(Role.TRAINER, "Roster", "Pending connection"); pending.setGymId(admin.getGymId()); users.saveAndFlush(pending);
        connections.saveAndFlush(new TrainerGymAffiliation(pending.getId(), admin.getGymId(), GymAffiliationStatus.PENDING, "GYM"));
        review(pending, admin, VerificationStatus.PENDING, "PENDING CONNECTION PRIVATE NOTES");
        review(trainers.get(1), other, VerificationStatus.NEEDS_INFO, "FOREIGN PRIVATE REVIEW");
        return new Fixture(admin, other, trainers, ended, pending);
    }

    @Test
    void realCountsAndBoundedRosterHonourMultiGymEndingsLiteralSearchAndStableLatestReview() {
        var fixture = fixture(); var admin = fixture.admin();
        var first = roster.search(admin, "", "", false, 1);
        assertThat(first.getTotalElements()).isEqualTo(27);
        assertThat(first.getContent().stream().map(User::getId)).containsExactlyElementsOf(fixture.trainers().subList(0, 20).stream().map(User::getId).toList());
        assertThat(roster.search(admin, "", "", false, 2).getNumberOfElements()).isEqualTo(7);
        assertThat(roster.search(admin, "", "", false, Integer.MAX_VALUE).getNumber()).isEqualTo(1);
        assertThat(roster.search(admin, "100%_!+ &", "", true, 1).getContent()).singleElement().extracting(User::getId).isEqualTo(fixture.trainers().getFirst().getId());
        assertThat(roster.search(admin, "100%_!+ &", "NEEDS_INFO", false, 1)).isEmpty();
        var latest = roster.latestReviews(admin, first.getContent());
        assertThat(latest).hasSize(20);
        assertThat(latest.get(fixture.trainers().getFirst().getId()).getStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(roster.latestReviews(admin, List.of(fixture.ended(), fixture.pending()))).isEmpty();
        assertThat(roster.search(admin, "", "NOT_SUBMITTED", false, 1).getContent()).singleElement().extracting(User::getId).isEqualTo(fixture.trainers().getLast().getId());
        var stats = operations.forAdmin(admin);
        assertThat(stats.trainers()).isEqualTo(27); assertThat(stats.verified()).isEqualTo(14);
        assertThat(stats.pending()).isEqualTo(7); assertThat(stats.needsInfo()).isEqualTo(7);
        assertThat(stats.gymName()).isEqualTo("Owned operations gym");
    }

    @Test
    void nativeRosterAndDashboardRenderAllLocalesWithSeparateReviewAndVerificationAndBoundedCards() throws Exception {
        var fixture = fixture(); var admin = fixture.admin();
        for (String locale : LOCALES) {
            var html = Jsoup.parse(mvc.perform(get("/gym/admin/trainers").param("lang", locale).with(user(admin.getUsername()).roles("GYM_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(html.select(".gym-roster-card")).hasSize(20);
            assertThat(html.select("main").text()).doesNotContain("??", "FOREIGN PRIVATE REVIEW", "ENDED PRIVATE NOTES", "PENDING CONNECTION PRIVATE NOTES");
            assertThat(html.select("#gym-create-account[open]")).isEmpty();
            assertThat(html.selectFirst(".gym-roster-pagination a").attr("href")).contains("page=2");
            var dashboard = Jsoup.parse(mvc.perform(get("/gym/dashboard").param("lang", locale).with(user(admin.getUsername()).roles("GYM_ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(dashboard.selectFirst("h1").text()).contains("Owned operations gym");
            assertThat(dashboard.select(".gym-operations-metrics dd").eachText()).startsWith("27", "14", "7", "7");
            assertThat(dashboard.select(".gym-operational-calendar[open]")).isEmpty();
        }
        var second = Jsoup.parse(mvc.perform(get("/gym/admin/trainers").param("lang", "en").param("page", "2").with(user(admin.getUsername()).roles("GYM_ADMIN")))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(second.select(".gym-roster-card")).hasSize(7);
        assertThat(second.selectFirst("#trainer-results").text()).isEqualTo("21–27 of 27 matches");
        var extra = second.selectFirst("[data-trainer-id='" + fixture.trainers().getLast().getId() + "']");
        assertThat(extra.select(".gym-roster-status dd").eachText()).contains("Verified", "No review submitted by this gym");
        assertThat(second.select("[data-trainer-id='" + fixture.trainers().get(25).getId() + "'] .gym-notes-editor")).isEmpty();
        mvc.perform(get("/gym/admin/trainers").param("search", "x".repeat(121)).param("review", "PENDING")
            .with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isBadRequest())
            .andExpect(result -> assertThat(Jsoup.parse(result.getResponse().getContentAsString()).select(".gym-roster-card")).isEmpty());
    }

    @Test
    void notesValidationRetainsPageTwoDraftAndSuccessfulResubmissionKeepsEncodedContext() throws Exception {
        var fixture = fixture(); var admin = fixture.admin(); var trainer = fixture.trainers().get(21);
        var request = requests.findLatestForGymRoster(admin.getGymId(), List.of(trainer.getId())).getFirst();
        String action = "/gym/admin/trainers/" + request.getId() + "/update-notes";
        String tooLong = "x".repeat(1001);
        var rejected = Jsoup.parse(mvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
            .param("page", "2").param("notes", tooLong)).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
        assertThat(rejected.select(".gym-roster-card")).hasSize(7);
        assertThat(rejected.selectFirst("#request-notes-" + request.getId()).val()).isEqualTo(tooLong);
        assertThat(rejected.selectFirst("#request-notes-" + request.getId()).closest("details").hasAttr("open")).isTrue();
        assertThat(requests.findById(request.getId()).orElseThrow().getNotes()).isEqualTo("Own latest review 21");
        mvc.perform(post(action).with(user(admin.getUsername()).roles("GYM_ADMIN")).with(csrf())
            .param("search", "Roster + &").param("review", "NEEDS_INFO").param("page", "2").param("notes", "Retained literal <updated> notes"))
            .andExpect(redirectedUrl("/gym/admin/trainers?search=Roster+%2B+%26&review=NEEDS_INFO&verifiedOnly=false&page=2#trainer-results"));
        assertThat(requests.findById(request.getId()).orElseThrow().getStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    @Test
    void foreignGymPointerAndOtherRolesDoNotRevealOwnedRosterOrOperations() throws Exception {
        var fixture = fixture(); var admin = fixture.admin(); admin.setGymId(fixture.other().getGymId()); users.saveAndFlush(admin);
        assertThat(operations.forAdmin(admin).hasGym()).isFalse();
        assertThatThrownBy(() -> roster.search(admin, "", "", false, 1)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        mvc.perform(get("/gym/admin/trainers").with(user(admin.getUsername()).roles("GYM_ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/gym/admin/trainers").with(user(fixture.trainers().getFirst().getUsername()).roles("TRAINER"))).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(get("/gym/admin/trainers")).andExpect(status().isUnauthorized());
    }

    private record Fixture(User admin, User other, List<User> trainers, User ended, User pending) {}
}
