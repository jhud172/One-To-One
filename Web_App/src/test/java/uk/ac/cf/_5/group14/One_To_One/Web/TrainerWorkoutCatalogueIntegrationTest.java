package uk.ac.cf._5.group14.One_To_One.Web;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerWorkoutCatalogueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerLibraryWorkoutTemplateRepository workouts;
    @Autowired TrainerLibraryExerciseRepository exercises;
    @Autowired TrainerLibraryService library;
    User trainer;

    private User account() {
        String username = "workout-catalogue-" + UUID.randomUUID();
        var account = new User(username + "@example.invalid", "Local", "Owner", username, "test-password");
        account.setRole(Role.TRAINER); account.setTrainerVerified(true);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() { trainer = account(); }
    private TrainerLibraryWorkoutTemplate workout(User owner, String title, String summary, int order) {
        var workout = new TrainerLibraryWorkoutTemplate(owner.getId());
        workout.setTitle(title); workout.setSummary(summary);
        ReflectionTestUtils.setField(workout, "createdAt", Instant.parse("2026-10-01T12:00:00Z").plusSeconds(order));
        return workouts.saveAndFlush(workout);
    }
    private TrainerLibraryExercise movement(User owner) {
        var exercise = new TrainerLibraryExercise(owner.getId());
        exercise.setName("Controlled row"); exercise.setPrimaryMuscles("Back"); exercise.setEquipment("Dumbbells"); exercise.setDifficulty("BEGINNER");
        return exercises.saveAndFlush(exercise);
    }
    private TrainerLibraryWorkoutItem item(User owner, Long workoutId) {
        var form = new TrainerLibraryWorkoutItemForm();
        form.setExerciseId(movement(owner).getId()); form.setSets(3); form.setReps(8); form.setRestSeconds(60);
        return library.addWorkoutItem(owner.getId(), workoutId, form);
    }
    private org.jsoup.nodes.Document page(String query, int number, String locale) throws Exception {
        var result = mvc.perform(get("/trainer/library/workouts").param("q", query).param("page", "" + number)
                .param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test void searchBoundsOwnedRowsCountsAndLiteralMetadataWithoutInventingNullText() throws Exception {
        for (int i = 0; i < 22; i++) workout(trainer, "Session " + i, i == 0 ? null : "Controlled strength", i);
        var foreign = account();
        var hidden = workout(foreign, "Foreign private session", "Controlled strength", 90);
        item(foreign, hidden.getId());
        var first = library.searchWorkouts(trainer.getId(), "", 0);
        assertThat(first.ownedCount()).isEqualTo(22); assertThat(first.page().getContent()).hasSize(18);
        assertThat(first.page().getContent().getFirst().getTitle()).isEqualTo("Session 21");
        assertThat(library.searchWorkouts(trainer.getId(), "", Integer.MAX_VALUE).page().getNumber()).isEqualTo(1);
        assertThat(library.searchWorkouts(trainer.getId(), "", -2).page().getNumber()).isZero();
        assertThat(library.searchWorkouts(trainer.getId(), " cOnTrOlLeD ", 0).page().getTotalElements()).isEqualTo(21);
        assertThat(library.searchWorkouts(trainer.getId(), "null", 0).page().getTotalElements()).isZero();
        var literal = workout(trainer, "Literal !%_ <session>", "Specific prescription", 23);
        item(trainer, literal.getId());
        assertThat(library.searchWorkouts(trainer.getId(), "!%_", 0).page().getContent())
                .extracting(TrainerLibraryWorkoutTemplate::getId).containsExactly(literal.getId());
        var counts = library.getWorkoutItemCounts(trainer.getId(), List.of(literal.getId(), hidden.getId()));
        assertThat(counts).containsEntry(literal.getId(), 1L).doesNotContainKey(hidden.getId());
        assertThat(library.getWorkoutItemCounts(trainer.getId(), List.of())).isEmpty();
        assertThat(library.getWorkoutItemCounts(trainer.getId(), List.of(first.page().getContent().getFirst().getId()))).isEmpty();
        assertThat(library.searchWorkouts(trainer.getId(), "missing", 9999).page().getNumber()).isZero();
        assertThat(library.searchWorkouts(trainer.getId(), "x".repeat(121), 0).query()).hasSize(120);
        var rendered = page("", 1, "en");
        assertThat(rendered.select(".library-catalogue-card")).hasSize(5);
        assertThat(rendered.text()).contains("19–23 of 23 matches", "Page 2 of 2").doesNotContain("Foreign private");
        assertThat(rendered.select(".library-catalogue-pagination a").eachAttr("href")).containsExactly("/trainer/library/workouts?q=&page=0");
    }

    @Test void nativeCatalogueSeparatesEmptySearchAndEscapesAllLocaleContent() throws Exception {
        assertThat(page("missing", 3, "en").select(".library-exercise-empty a")).hasSize(1);
        workout(trainer, "Safe <session>", "Cue <literal>\nSecond line", 0);
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var rendered = page("", 0, locale);
            assertThat(rendered.select(".library-catalogue-card h2").eachText()).containsExactly("Safe <session>");
            assertThat(rendered.select("session,literal,iframe")).isEmpty();
            assertThat(rendered.select(".library-catalogue-card h2[dir=auto]")).hasSize(1);
            assertThat(rendered.text()).doesNotContain("??ui.library.", "Workout Programmes");
            assertThat(rendered.select("main form[method=get]")).hasSize(1);
            assertThat(rendered.select("main form input[name=_csrf]")).isEmpty();
        }
        assertThat(page("missing", 9999, "en").text()).contains("0–0 of 0 matches").doesNotContain("No workouts yet");
    }

    @Test void createEditAndRejectedDraftKeepLiteralQueryAndSavedFeedback() throws Exception {
        var saved = workout(trainer, "Original session", null, 0);
        String query = "A+B & %_ <literal>";
        for (String path : new String[]{"/trainer/library/workouts/create", "/trainer/library/workouts/" + saved.getId() + "/edit"}) {
            var result = mvc.perform(get(path).param("q", query).param("page", "2").with(user(trainer.getUsername()).roles("TRAINER")))
                    .andExpect(status().isOk()).andReturn();
            var form = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString()).selectFirst("main form");
            assertThat(form.attr("action")).contains("q=", "page=2"); assertThat(form.select("input[name=_csrf]")).hasSize(1);
            assertThat(form.select("[data-library-preview]")).hasSize(3);
            assertThat(form.select("[data-library-preview=summary]").text()).isEmpty();
        }
        var rejected = mvc.perform(post("/trainer/library/workouts/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("title", " ").param("summary", "Draft <literal>").param("notesText", "Keep this\nNext line"))
                .andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.selectFirst("#summary").val()).isEqualTo("Draft <literal>");
        assertThat(draft.selectFirst("#notesText").val()).isEqualTo("Keep this\nNext line");
        assertThat(draft.selectFirst("#title").attr("aria-invalid")).isEqualTo("true");
        assertThat(draft.select("#workout-errors a[href='#title']")).hasSize(1);
        assertThat(draft.selectFirst("[data-library-preview=summary]").wholeText()).isEqualTo("Draft <literal>");
        assertThat(workouts.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("Original session");
        var accepted = mvc.perform(post("/trainer/library/workouts/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("expectedRevision", library.getWorkoutRevision(trainer.getId(), saved.getId()))
                .param("title", "Changed <session>").param("summary", "Summary\nNext line"))
                .andExpect(status().is3xxRedirection()).andReturn();
        String destination = accepted.getResponse().getRedirectedUrl();
        assertThat(destination).contains("q=A%2BB%20%26%20%25_%20%3Cliteral%3E", "page=2");
        var resumed = mvc.perform(get(URI.create(destination)).cookie(accepted.getResponse().getCookies()).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        var detail = org.jsoup.Jsoup.parse(resumed.getResponse().getContentAsString());
        assertThat(detail.select("main [role=status]").text()).contains("Workout saved.");
        assertThat(detail.select("main form[method=post]")).allSatisfy(form -> assertThat(form.select("input[name=_csrf]")).hasSize(1));
        assertThat(detail.selectFirst("input[name=q]").val()).isEqualTo(query);
        assertThat(detail.selectFirst("main a.detail-btn-secondary").attr("href")).contains("page=2", "#workout-" + saved.getId());
        var created = mvc.perform(post("/trainer/library/workouts/create").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "New session")).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(created.getResponse().getRedirectedUrl()).matches("/trainer/library/workouts/[0-9]+");
    }

    @Test void itemErrorsDeletionAndShareReturnOnlyToOwnedInternalContext() throws Exception {
        var saved = workout(trainer, "Disposable session", null, 0);
        var exercise = movement(trainer);
        String query = "Disposable+row";
        String detail = "/trainer/library/workouts/" + saved.getId() + "?q=Disposable%2Brow&page=1";
        var rejected = mvc.perform(post("/trainer/library/workouts/{id}/items", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", query).param("page", "1").param("exerciseId", exercise.getId().toString()).param("sets", "bad").param("reps", "8").param("restSeconds", "60"))
                .andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.selectFirst("#sets").val()).isEqualTo("bad");
        assertThat(draft.selectFirst("form.workout-prescription-form").attr("action")).contains("page=1");
        mvc.perform(post("/trainer/library/workouts/{id}/items", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", query).param("page", "1").param("exerciseId", exercise.getId().toString()).param("sets", "3").param("reps", "8").param("restSeconds", "60"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(detail));
        Long itemId = library.getWorkoutItems(saved.getId()).getFirst().getId();
        mvc.perform(post("/trainer/library/workouts/{id}/items/{item}/delete", saved.getId(), itemId)
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "1"))
                .andExpect(redirectedUrl(detail));
        assertThat(library.getWorkoutItems(saved.getId())).isEmpty();
        mvc.perform(post("/trainer/library/share").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "WORKOUT").param("templateId", saved.getId().toString())
                .param("q", query).param("page", "1").param("returnUrl", "https://outside.example.invalid/"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(detail)).andExpect(flash().attribute("libraryShareError", true));
        mvc.perform(post("/trainer/library/workouts/{id}/delete", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", query).param("page", "1"))
                .andExpect(redirectedUrl("/trainer/library/workouts?q=Disposable%2Brow&page=1"));
        assertThat(workouts.existsById(saved.getId())).isFalse();
    }

    @Test void nativeSequenceMovementPreservesDuplicateBlocksAndPrescriptionsUnderTheUniquePositionConstraint() throws Exception {
        var saved = workout(trainer, "Ordered session", "Saved instructions", 0);
        var exercise = movement(trainer);
        var form = new TrainerLibraryWorkoutItemForm();
        form.setExerciseId(exercise.getId()); form.setSets(3); form.setReps(8); form.setRestSeconds(60);
        var first = library.addWorkoutItem(trainer.getId(), saved.getId(), form);
        form.setSets(2); form.setReps(12); form.setRestSeconds(90); form.setRpe(7);
        var second = library.addWorkoutItem(trainer.getId(), saved.getId(), form);
        form.setOrderIndex(Integer.MAX_VALUE);
        var last = library.addWorkoutItem(trainer.getId(), saved.getId(), form);
        String path = "/trainer/library/workouts/" + saved.getId() + "/items/" + second.getId() + "/move";
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("direction", "UP").param("q", "Ordered+session").param("page", "2"))
                .andExpect(redirectedUrl("/trainer/library/workouts/" + saved.getId() + "?q=Ordered%2Bsession&page=2#workout-item-" + second.getId()))
                .andExpect(flash().attribute("libraryOrderSaved", true));
        assertThat(library.getWorkoutItems(saved.getId())).extracting(TrainerLibraryWorkoutItem::getId)
                .containsExactly(second.getId(), first.getId(), last.getId());
        assertThat(second.getSets()).isEqualTo(2); assertThat(second.getReps()).isEqualTo(12);
        assertThat(second.getRestSeconds()).isEqualTo(90); assertThat(second.getRpe()).isEqualTo(7);
        assertThat(first.getSets()).isEqualTo(3); assertThat(first.getRpe()).isNull();
        assertThat(library.moveWorkoutItem(trainer.getId(), saved.getId(), second.getId(), "UP")).isFalse();
        assertThat(library.moveWorkoutItem(trainer.getId(), saved.getId(), last.getId(), "DOWN")).isFalse();
        assertThat(library.moveWorkoutItem(trainer.getId(), saved.getId(), last.getId(), "UP")).isTrue();
        assertThat(library.getWorkoutItems(saved.getId())).extracting(TrainerLibraryWorkoutItem::getId)
                .containsExactly(second.getId(), last.getId(), first.getId());
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "SIDEWAYS"))
                .andExpect(flash().attribute("libraryOrderError", true));
        var foreignOwner = account();
        var foreignWorkout = workout(foreignOwner, "Private", null, 0);
        var foreignItem = item(foreignOwner, foreignWorkout.getId());
        mvc.perform(post("/trainer/library/workouts/{wid}/items/{iid}/move", saved.getId(), foreignItem.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "UP"))
                .andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post(path).with(user(foreignOwner.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "UP"))
                .andExpect(redirectedUrl("/access-denied"));
        assertThat(library.getWorkoutItems(saved.getId())).extracting(TrainerLibraryWorkoutItem::getId)
                .containsExactly(second.getId(), last.getId(), first.getId());
        mvc.perform(post("/trainer/library/workouts/{wid}/items/{iid}/delete", saved.getId(), Long.MAX_VALUE)
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())).andExpect(redirectedUrl("/access-denied"));
    }

    @Test void savedCompositionPreviewAndRejectedPrescriptionExposeNamedNativeActionsInEveryLocale() throws Exception {
        var saved = workout(trainer, "Literal <session>", "Saved summary\nSecond line", 0);
        var first = item(trainer, saved.getId());
        item(trainer, saved.getId());
        String path = "/trainer/library/workouts/" + saved.getId();
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var result = mvc.perform(get(path).param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                    .andExpect(status().isOk()).andReturn();
            var rendered = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(rendered.select(".workout-item-list > li")).hasSize(2);
            assertThat(rendered.select(".workout-client-preview .workout-preview-list > li")).hasSize(2);
            assertThat(rendered.select(".workout-client-preview h2").text()).isEqualTo("Literal <session>");
            assertThat(rendered.select("main session")).isEmpty();
            assertThat(rendered.select(".workout-sequence-position").text()).doesNotContain("ui.library.");
            assertThat(rendered.select(".workout-sequence-actions button[disabled]")).hasSize(2);
            assertThat(rendered.selectFirst("#move-up-" + first.getId()).attr("aria-labelledby"))
                    .contains("workout-exercise-" + first.getId(), "workout-position-" + first.getId());
            for (var nativeForm : rendered.select("main form")) {
                assertThat(nativeForm.select("input[name=_csrf]")).hasSize(1);
            }
        }
        var rejected = mvc.perform(post(path + "/items").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("exerciseId", first.getExerciseId().toString()).param("sets", "0").param("reps", "12")
                .param("restSeconds", "90").param("rpe", "7")).andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.select("#prescription-errors a").eachAttr("href")).containsExactly("#sets");
        assertThat(draft.selectFirst("#sets").attr("aria-invalid")).isEqualTo("true");
        assertThat(draft.selectFirst("#reps").val()).isEqualTo("12");
        assertThat(draft.selectFirst("#rpe").val()).isEqualTo("7");
        assertThat(library.getWorkoutItems(saved.getId())).hasSize(2);
    }

    @Test void staleMetadataIsRetainedForExplicitReviewWithoutOverwritingSavedInstructionsOrItems() throws Exception {
        var saved = workout(trainer, "Original title", "Original summary", 0);
        var item = item(trainer, saved.getId());
        String oldRevision = library.getWorkoutRevision(trainer.getId(), saved.getId());
        var fresh = new TrainerLibraryWorkoutTemplateForm();
        fresh.setTitle("Latest <title>"); fresh.setSummary("Latest summary"); fresh.setNotesText("Latest client instruction");
        fresh.setExpectedRevision(oldRevision);
        library.updateWorkout(trainer.getId(), saved.getId(), fresh);
        var rejected = mvc.perform(post("/trainer/library/workouts/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", "Latest+row").param("page", "2")
                .param("expectedRevision", oldRevision).param("title", "Retained draft <title>")
                .param("summary", "Retained summary\nSecond line").param("notesText", "Retained draft instruction"))
                .andExpect(status().isConflict()).andReturn();
        var review = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(review.selectFirst("#title").val()).isEqualTo("Retained draft <title>");
        assertThat(review.selectFirst("#summary").val()).isEqualTo("Retained summary\nSecond line");
        assertThat(review.selectFirst(".library-workout-conflict").text()).contains("Latest <title>", "Latest client instruction");
        assertThat(review.select("main title")).isEmpty();
        assertThat(review.select("#workout-errors")).isEmpty();
        assertThat(review.select(".library-workout-conflict[role=alert]")).hasSize(1);
        assertThat(review.select("main button[type=submit]").text()).isEqualTo("Save reviewed draft");
        assertThat(workouts.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("Latest <title>");
        assertThat(library.getWorkoutNotes(saved.getId())).extracting(TrainerLibraryWorkoutNote::getNoteText).containsExactly("Latest client instruction");
        assertThat(library.getWorkoutItems(saved.getId())).extracting(TrainerLibraryWorkoutItem::getId).containsExactly(item.getId());
        String reviewedRevision = review.selectFirst("input[name=expectedRevision]").val();
        assertThat(reviewedRevision).hasSize(64).isNotEqualTo(oldRevision);
        mvc.perform(post("/trainer/library/workouts/{id}/edit", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("expectedRevision", reviewedRevision).param("title", "Reviewed draft").param("notesText", "Reviewed instruction"))
                .andExpect(status().is3xxRedirection());
        assertThat(workouts.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("Reviewed draft");
        assertThat(library.getWorkoutItems(saved.getId())).extracting(TrainerLibraryWorkoutItem::getId).containsExactly(item.getId());
        mvc.perform(post("/trainer/library/workouts/{id}/edit", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Missing revision must not overwrite"))
                .andExpect(status().isConflict());
        assertThat(workouts.findById(saved.getId()).orElseThrow().getTitle()).isEqualTo("Reviewed draft");
    }
}
