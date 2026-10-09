package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerLibraryOverviewIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerLibraryExerciseRepository exercises;
    @Autowired TrainerLibraryWorkoutTemplateRepository workouts;
    @Autowired TrainerLibraryProgrammeTemplateRepository programmes;
    @Autowired TrainerLibraryOverviewService overview;
    User trainer;

    private User account(Role role) {
        String username = "library-overview-" + UUID.randomUUID();
        var account = new User(username + "@example.invalid", "Local", "Owner", username, "test-password");
        account.setRole(role); account.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() { trainer = account(Role.TRAINER); }
    private TrainerLibraryExercise exercise(User owner, String name, Instant created) {
        var exercise = new TrainerLibraryExercise(owner.getId());
        exercise.setName(name); exercise.setPrimaryMuscles("Legs"); exercise.setEquipment("Bodyweight");
        exercise.setDifficulty("BEGINNER"); exercise.setDescription("Private descriptive context should not be on the hub.");
        ReflectionTestUtils.setField(exercise, "createdAt", created);
        return exercises.saveAndFlush(exercise);
    }
    private TrainerLibraryWorkoutTemplate workout(User owner, String title, Instant created) {
        var workout = new TrainerLibraryWorkoutTemplate(owner.getId()); workout.setTitle(title);
        ReflectionTestUtils.setField(workout, "createdAt", created);
        return workouts.saveAndFlush(workout);
    }
    private TrainerLibraryProgrammeTemplate programme(User owner, String title, Instant created) {
        var programme = new TrainerLibraryProgrammeTemplate(owner.getId()); programme.setTitle(title); programme.setWeeks(4);
        ReflectionTestUtils.setField(programme, "createdAt", created);
        return programmes.saveAndFlush(programme);
    }
    private org.jsoup.nodes.Document page(String query, String locale) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/trainer/library").with(user(trainer.getUsername()).roles("TRAINER"))
                .param("q", query).param("lang", locale)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test void overviewCountsOnlyOwnedResourcesAndReturnsTheSixActualNewestCreations() {
        var base = Instant.parse("2026-09-20T12:00:00Z");
        for (int index = 0; index < 9; index++) exercise(trainer, "Needle " + index, base.plusSeconds(index * 86400L));
        var newestWorkout = workout(trainer, "Needle workout", Instant.parse("2026-10-02T12:00:00Z"));
        programme(trainer, "Needle programme", Instant.parse("2026-09-10T12:00:00Z"));
        var foreign = account(Role.TRAINER);
        exercise(foreign, "Foreign Needle", Instant.parse("2026-10-03T12:00:00Z"));
        workout(foreign, "Foreign workout", Instant.parse("2026-10-03T12:00:00Z"));
        var summary = overview.overview(trainer.getId(), "");
        assertThat(summary.exerciseCount()).isEqualTo(9); assertThat(summary.workoutCount()).isEqualTo(1);
        assertThat(summary.programmeCount()).isEqualTo(1); assertThat(summary.totalCount()).isEqualTo(11);
        assertThat(summary.items()).hasSize(6);
        assertThat(summary.items().stream().map(TrainerLibraryOverviewService.Item::name))
                .containsExactly("Needle workout", "Needle 8", "Needle 7", "Needle 6", "Needle 5", "Needle 4");
        assertThat(summary.items().getFirst().href()).isEqualTo("/trainer/library/workouts/" + newestWorkout.getId());
        var searched = overview.overview(trainer.getId(), "  nEeDlE  ");
        assertThat(searched.query()).isEqualTo("nEeDlE"); assertThat(searched.totalMatches()).isEqualTo(11);
        assertThat(searched.totalCount()).isEqualTo(11); assertThat(searched.items()).hasSize(6);
    }

    @Test void nativeSearchTreatsWildcardsLiterallyPreservesCategoryQueryAndHasTruthfulEmptyResults() throws Exception {
        exercise(trainer, "Literal %_ movement", Instant.parse("2026-10-01T12:00:00Z"));
        exercise(trainer, "Ordinary movement", Instant.parse("2026-10-02T12:00:00Z"));
        var found = page("%_", "en");
        assertThat(found.selectFirst("#library-query").val()).isEqualTo("%_");
        assertThat(found.select(".library-overview-resource strong").eachText()).containsExactly("Literal %_ movement");
        assertThat(found.select("#library-exercises a").attr("href")).contains("q=%25_");
        assertThat(found.select(".library-hub-count").eachText()).containsExactly("2", "0", "0");
        assertThat(found.text()).contains("Showing 1 of 1 resources").doesNotContain("Private descriptive context");
        var missing = page("Missing name", "en");
        assertThat(missing.select(".library-overview-resource")).isEmpty();
        assertThat(missing.select(".detail-empty").text()).contains("No matching names").doesNotContain("library is empty");
        assertThat(overview.overview(trainer.getId(), "x".repeat(121)).query()).hasSize(120);
    }

    @Test void hubAndOwnedDetailLinksRenderAcrossAllLocalesWithNativeAccessibleSearch() throws Exception {
        var exercise = exercise(trainer, "Saved <movement>", Instant.parse("2026-10-01T12:00:00Z"));
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var rendered = page("Saved", locale);
            assertThat(rendered.select("main form[method=get]")).hasSize(1);
            assertThat(rendered.select("main form input[name=_csrf]")).isEmpty();
            assertThat(rendered.selectFirst("#library-query").attr("maxlength")).isEqualTo("120");
            assertThat(rendered.select("label[for=library-query]")).hasSize(1);
            assertThat(rendered.select(".library-overview-resource strong").eachText()).containsExactly("Saved <movement>");
            assertThat(rendered.select("movement")).isEmpty(); assertThat(rendered.text()).doesNotContain("??ui.library.");
        }
        mvc.perform(get("/trainer/library/exercises/{id}", exercise.getId()).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk());
    }

    @Test void emptyAndUnverifiedViewsRemainDistinctAndOtherRolesCannotReadTheOverview() throws Exception {
        assertThat(page("", "en").select(".detail-empty").text()).contains("coaching library is empty");
        trainer.setTrainerVerified(false); users.saveAndFlush(trainer);
        var gated = page("", "en");
        assertThat(gated.select(".library-verification")).hasSize(1);
        assertThat(gated.select(".library-overview-search,.library-overview-recent,.library-hub-grid")).isEmpty();
        assertThatThrownBy(() -> overview.overview(trainer.getId(), "")).isInstanceOf(AccessDeniedException.class);
        trainer.setTrainerVerified(true); trainer.setEnabled(false); users.saveAndFlush(trainer);
        assertThatThrownBy(() -> overview.overview(trainer.getId(), "")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> overview.overview(account(Role.CLIENT).getId(), "")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> overview.overview(null, "")).isInstanceOf(AccessDeniedException.class);
    }
}
