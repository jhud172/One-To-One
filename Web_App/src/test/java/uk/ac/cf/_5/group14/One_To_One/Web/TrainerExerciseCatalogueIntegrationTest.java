package uk.ac.cf._5.group14.One_To_One.Web;

import java.net.URI;
import java.time.Instant;
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
class TrainerExerciseCatalogueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerLibraryExerciseRepository exercises;
    @Autowired TrainerLibraryService library;
    User trainer;

    private User account() {
        String username = "exercise-catalogue-" + UUID.randomUUID();
        var account = new User(username + "@example.invalid", "Local", "Owner", username, "test-password");
        account.setRole(Role.TRAINER); account.setTrainerVerified(true);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() { trainer = account(); }
    private TrainerLibraryExercise exercise(User owner, String name, int order, String video) {
        var exercise = new TrainerLibraryExercise(owner.getId());
        exercise.setName(name); exercise.setPrimaryMuscles("Back"); exercise.setEquipment("Dumbbells");
        exercise.setDifficulty("BEGINNER"); exercise.setVideoUrl(video);
        ReflectionTestUtils.setField(exercise, "createdAt", Instant.parse("2026-10-01T12:00:00Z").plusSeconds(order));
        return exercises.saveAndFlush(exercise);
    }
    private org.jsoup.nodes.Document page(String query, int number, String locale) throws Exception {
        var result = mvc.perform(get("/trainer/library/exercises").param("q", query).param("page", "" + number)
                .param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test void boundedOwnedSearchCoversMetadataLiteralWildcardsAndOutOfRangePages() throws Exception {
        for (int i = 0; i < 21; i++) exercise(trainer, "Movement " + i, i, null);
        exercise(account(), "Foreign private movement", 90, null);
        var first = library.searchExercises(trainer.getId(), "", 0);
        assertThat(first.ownedCount()).isEqualTo(21); assertThat(first.page().getContent()).hasSize(18);
        assertThat(first.page().getContent().getFirst().getName()).isEqualTo("Movement 20");
        assertThat(library.searchExercises(trainer.getId(), "", Integer.MAX_VALUE).page().getNumber()).isEqualTo(1);
        assertThat(library.searchExercises(trainer.getId(), "", -2).page().getNumber()).isZero();
        assertThat(library.searchExercises(trainer.getId(), " dUmBbElLs ", 0).page().getTotalElements()).isEqualTo(21);
        assertThat(library.searchExercises(trainer.getId(), "BACK", 0).page().getTotalElements()).isEqualTo(21);
        var literal = exercise(trainer, "Literal !%_ <movement>", 22, null);
        assertThat(library.searchExercises(trainer.getId(), "!%_", 0).page().getContent())
                .extracting(TrainerLibraryExercise::getId).containsExactly(literal.getId());
        var empty = library.searchExercises(trainer.getId(), "missing", 9999);
        assertThat(empty.page().getNumber()).isZero(); assertThat(empty.page().getTotalElements()).isZero();
        assertThat(library.searchExercises(trainer.getId(), "x".repeat(121), 0).query()).hasSize(120);
        var rendered = page("", 1, "en");
        assertThat(rendered.select(".library-catalogue-card")).hasSize(4);
        assertThat(rendered.text()).contains("19–22 of 22 matches", "Page 2 of 2").doesNotContain("Foreign private");
        assertThat(rendered.select(".library-catalogue-pagination a").eachAttr("href")).containsExactly("/trainer/library/exercises?q=&page=0");
        assertThat(page("missing", 9999, "en").text()).contains("0–0 of 0 matches").doesNotContain("No exercises yet");
    }

    @Test void nativeCatalogueEscapesNamesTranslatesDifficultyAndDoesNotFetchOrLinkUnsafeMedia() throws Exception {
        exercise(trainer, "Safe <movement>", 1, "https://example.invalid/video");
        exercise(trainer, "Legacy unsafe media", 0, "javascript:alert(1)");
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var rendered = page("", 0, locale);
            assertThat(rendered.select(".library-catalogue-card h2").eachText()).containsExactly("Safe <movement>", "Legacy unsafe media");
            assertThat(rendered.select("movement,iframe,main img")).isEmpty();
            assertThat(rendered.text()).doesNotContain("??ui.library.", "javascript:alert", "BEGINNER");
            assertThat(rendered.select("main form[method=get]")).hasSize(1);
            assertThat(rendered.select("main form input[name=_csrf]")).isEmpty();
        }
        var rendered = page("", 0, "en");
        assertThat(rendered.select(".library-catalogue-media > span:not([aria-hidden])").eachText()).containsExactly("Video link available", "No video link");
    }

    @Test void detailCreateEditAndInvalidDraftPreserveSearchPageAndSingleCsrf() throws Exception {
        var saved = exercise(trainer, "Original movement", 0, null);
        String query = "A+B & %_ <literal>";
        var detailResult = mvc.perform(get("/trainer/library/exercises/{id}", saved.getId()).param("q", query).param("page", "2")
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        var detail = org.jsoup.Jsoup.parse(detailResult.getResponse().getContentAsString());
        assertThat(detail.selectFirst("main a.detail-btn-secondary").attr("href")).contains("q=", "page=2", "#exercise-" + saved.getId());
        for (String path : new String[]{"/trainer/library/exercises/create", "/trainer/library/exercises/" + saved.getId() + "/edit"}) {
            var result = mvc.perform(get(path).param("q", query).param("page", "2").with(user(trainer.getUsername()).roles("TRAINER")))
                    .andExpect(status().isOk()).andReturn();
            var form = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString()).selectFirst("main form");
            assertThat(form.attr("action")).contains("q=", "page=2"); assertThat(form.select("input[name=_csrf]")).hasSize(1);
        }
        var rejected = mvc.perform(post("/trainer/library/exercises/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("name", "Draft <movement>").param("primaryMuscles", "Back").param("equipment", "Dumbbells")
                .param("difficulty", "Beginner").param("videoUrl", "javascript:alert(1)").param("notesText", "Keep <this>\nNext line"))
                .andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.selectFirst("#name").val()).isEqualTo("Draft <movement>");
        assertThat(draft.selectFirst("#notesText").val()).isEqualTo("Keep <this>\nNext line");
        assertThat(draft.select("#exercise-errors a[href='#videoUrl']")).hasSize(1);
        assertThat(draft.selectFirst("#videoUrl").attr("aria-invalid")).isEqualTo("true");
        assertThat(exercises.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Original movement");
        var accepted = mvc.perform(post("/trainer/library/exercises/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("name", "Changed movement").param("primaryMuscles", "Back").param("equipment", "Dumbbells").param("difficulty", "Beginner"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(accepted.getFlashMap().get("libraryExerciseSaved")).isEqualTo(true);
        String destination = accepted.getResponse().getRedirectedUrl();
        assertThat(destination).contains("q=A%2BB%20%26%20%25_%20%3Cliteral%3E", "page=2");
        var resumed = mvc.perform(get(URI.create(destination)).cookie(accepted.getResponse().getCookies()).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(resumed.getModelAndView().getModel().get("query")).isEqualTo(query);
        assertThat(org.jsoup.Jsoup.parse(resumed.getResponse().getContentAsString()).select("main [role=status]").text()).contains("Exercise saved.");
    }

    @Test void emptyCatalogueHasRealCreateActionAndDeletionPreservesNativeSearch() throws Exception {
        var empty = page("missing", 8, "en");
        assertThat(empty.select(".library-exercise-empty a")).hasSize(1);
        var saved = exercise(trainer, "Disposable deletion", 0, null);
        var result = mvc.perform(post("/trainer/library/exercises/{id}/delete", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", "Disposable+row").param("page", "1"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/trainer/library/exercises?q=Disposable%2Brow&page=1");
        assertThat(exercises.existsById(saved.getId())).isFalse();
    }
}
