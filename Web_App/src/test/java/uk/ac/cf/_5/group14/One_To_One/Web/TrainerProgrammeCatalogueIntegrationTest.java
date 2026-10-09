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
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerProgrammeCatalogueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerLibraryProgrammeTemplateRepository programmes;
    @Autowired TrainerLibraryService library;
    @Autowired TrainerClientLinkRepository links;
    @Autowired TrainerLibraryProgrammeDayRepository programmeDays;
    @Autowired TrainerLibraryWorkoutTemplateRepository workouts;
    @Autowired TrainerLibrarySharedTemplateRepository shares;
    User trainer;

    private User account() {
        String name = "programme-catalogue-" + UUID.randomUUID();
        var account = new User(name + "@example.invalid", "Local", "Owner", name, "test-password");
        account.setRole(Role.TRAINER); account.setTrainerVerified(true);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() { trainer = account(); }
    private TrainerLibraryProgrammeTemplate programme(User owner, String title, Integer weeks, int order) {
        var programme = new TrainerLibraryProgrammeTemplate(owner.getId());
        programme.setTitle(title); programme.setWeeks(weeks);
        ReflectionTestUtils.setField(programme, "createdAt", Instant.parse("2026-10-01T12:00:00Z").plusSeconds(order));
        return programmes.saveAndFlush(programme);
    }
    private TrainerLibraryProgrammeDay day(User owner, Long programmeId) {
        var metadata = new TrainerLibraryWorkoutTemplateForm(); metadata.setTitle("Owned strength session");
        var workout = library.createWorkout(owner.getId(), metadata);
        var form = new TrainerLibraryProgrammeDayForm(); form.setDayOfWeek("Strength A"); form.setWorkoutId(workout.getId());
        return library.addProgrammeDay(owner.getId(), programmeId, form);
    }
    private org.jsoup.nodes.Document page(String query, int page, String locale) throws Exception {
        var response = mvc.perform(get("/trainer/library/programmes").param("q", query).param("page", "" + page)
                .param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        return org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
    }

    @Test void boundedTitleSearchClampsPagesAndCountsOnlyOwnedDisplayedSequences() throws Exception {
        for (int index = 0; index < 22; index++) programme(trainer, "Foundation " + index, index == 0 ? null : 4, index);
        var foreignOwner = account();
        var hidden = programme(foreignOwner, "Private !%_ programme", 6, 30);
        day(foreignOwner, hidden.getId());
        var first = library.searchProgrammes(trainer.getId(), "", 0);
        assertThat(first.page().getContent()).hasSize(18);
        assertThat(first.page().getContent().getFirst().getTitle()).isEqualTo("Foundation 21");
        assertThat(first.ownedCount()).isEqualTo(22);
        assertThat(library.searchProgrammes(trainer.getId(), "", Integer.MAX_VALUE).page().getNumber()).isEqualTo(1);
        assertThat(library.searchProgrammes(trainer.getId(), "", -5).page().getNumber()).isZero();
        var literal = programme(trainer, "Literal !%_ <plan>", null, 25);
        day(trainer, literal.getId());
        assertThat(library.searchProgrammes(trainer.getId(), "!%_", 0).page().getContent())
                .extracting(TrainerLibraryProgrammeTemplate::getId).containsExactly(literal.getId());
        assertThat(library.searchProgrammes(trainer.getId(), " foundation ", 0).page().getTotalElements()).isEqualTo(22);
        assertThat(library.searchProgrammes(trainer.getId(), "missing", 9999).page().getNumber()).isZero();
        assertThat(library.searchProgrammes(trainer.getId(), "x".repeat(121), 0).query()).hasSize(120);
        assertThat(library.getProgrammeDayCounts(trainer.getId(), List.of(literal.getId(), hidden.getId())))
                .containsOnly(entry(literal.getId(), 1L));
        assertThat(library.getProgrammeDayCounts(trainer.getId(), List.of())).isEmpty();
        var rendered = page("", 9999, "en");
        assertThat(rendered.select(".library-catalogue-card")).hasSize(5);
        assertThat(rendered.select(".library-catalogue-pagination a").eachAttr("href"))
                .containsExactly("/trainer/library/programmes?q=&page=0");
        assertThat(rendered.text()).doesNotContain("Private !%_ programme");
    }

    @Test void resourceShellSeparatesEmptyFromNoMatchAndEscapesNativeTitlesInEveryLocale() throws Exception {
        assertThat(page("", 0, "en").select(".library-exercise-empty").text()).contains("No programmes yet");
        var saved = programme(trainer, "Safe <plan>", null, 0);
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var rendered = page("Safe", 0, locale);
            assertThat(rendered.select(".library-catalogue-card")).hasSize(1);
            assertThat(rendered.select(".library-catalogue-card h2").text()).isEqualTo("Safe <plan>");
            assertThat(rendered.select("main plan")).isEmpty();
            assertThat(rendered.select(".library-programme-sequence").text()).isNotBlank().doesNotContain("ui.library.");
            assertThat(rendered.select(".library-catalogue-card a").eachAttr("href"))
                    .allMatch(href -> href.contains("/" + saved.getId()) && href.contains("q=Safe&page=0"));
        }
        assertThat(page("missing", 0, "en").select(".library-exercise-empty").text()).contains("No results found").doesNotContain("No programmes yet");
    }

    @Test void nativeEditCompositionShareAndDeletionKeepLiteralInternalCatalogueContext() throws Exception {
        var saved = programme(trainer, "Foundation+rhythm", 4, 0);
        var client = account(); client.setRole(Role.CLIENT); users.saveAndFlush(client);
        links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var initialDay = day(trainer, saved.getId());
        String query = "Foundation+rhythm";
        String destination = "/trainer/library/programmes/" + saved.getId() + "?q=Foundation%2Brhythm&page=2";
        var detail = mvc.perform(get("/trainer/library/programmes/{id}", saved.getId()).param("q", query).param("page", "2")
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        var nativePage = org.jsoup.Jsoup.parse(detail.getResponse().getContentAsString());
        assertThat(nativePage.selectFirst(".detail-actions-sticky a").attr("href"))
                .isEqualTo("/trainer/library/programmes?q=Foundation%2Brhythm&page=2#programme-" + saved.getId());
        assertThat(nativePage.selectFirst("#shareDialog input[name=q]").val()).isEqualTo(query);
        var rejected = mvc.perform(post("/trainer/library/programmes/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("title", "Retained draft <plan>").param("weeks", "not-a-number").param("notesText", "Retained instruction"))
                .andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.selectFirst("#weeks").val()).isEqualTo("not-a-number");
        assertThat(draft.selectFirst("[data-library-preview=weeks]").text()).isEqualTo("not-a-number");
        assertThat(draft.selectFirst("#weeks").attr("aria-invalid")).isEqualTo("true");
        assertThat(draft.select("#programme-errors a[href='#weeks']")).hasSize(1);
        assertThat(draft.selectFirst("main form").attr("action")).isEqualTo(destination.replace("?", "/edit?"));
        var accepted = mvc.perform(post("/trainer/library/programmes/{id}/edit", saved.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2")
                .param("expectedRevision", library.getProgrammeRevision(trainer.getId(), saved.getId()))
                .param("title", "Foundation+rhythm updated").param("weeks", "6"))
                .andExpect(redirectedUrl(destination)).andExpect(flash().attribute("libraryProgrammeSaved", true)).andReturn();
        var acknowledged = mvc.perform(get(URI.create(destination)).cookie(accepted.getResponse().getCookies())
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        assertThat(org.jsoup.Jsoup.parse(acknowledged.getResponse().getContentAsString()).select("main [role=status]").text())
                .contains("Programme saved.");
        assertThat(library.getProgrammeDays(saved.getId())).extracting(TrainerLibraryProgrammeDay::getId).containsExactly(initialDay.getId());
        mvc.perform(post("/trainer/library/share").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "PROGRAMME").param("templateId", saved.getId().toString()).param("q", query).param("page", "2")
                .param("returnUrl", "https://outside.example.invalid/")).andExpect(redirectedUrl(destination))
                .andExpect(flash().attribute("libraryShareError", true));
        mvc.perform(post("/trainer/library/programmes/{id}/days/{day}/delete", saved.getId(), initialDay.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("q", query).param("page", "2"))
                .andExpect(redirectedUrl(destination));
        mvc.perform(post("/trainer/library/programmes/{id}/delete", saved.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", query).param("page", "2")).andExpect(redirectedUrl("/trainer/library/programmes?q=Foundation%2Brhythm&page=2"));
        assertThat(programmes.existsById(saved.getId())).isFalse();
        mvc.perform(post("/trainer/library/programmes/create").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "New shell")).andExpect(status().is3xxRedirection());
    }

    @Test void nativeMetadataConflictRetainsDraftRequiresReviewAndKeepsIdentitySequenceAndSharing() throws Exception {
        var saved = programme(trainer, "Original programme", 4, 0);
        var firstDay = day(trainer, saved.getId());
        var client = account(); client.setRole(Role.CLIENT); users.saveAndFlush(client);
        links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var share = new TrainerLibraryShareForm(); share.setTemplateType(TrainerLibraryTemplateType.PROGRAMME);
        share.setTemplateId(saved.getId()); share.setClientId(client.getId());
        library.shareTemplate(trainer.getId(), share);
        String path = "/trainer/library/programmes/" + saved.getId() + "/edit";
        var originalPage = mvc.perform(get(path).with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        String oldRevision = org.jsoup.Jsoup.parse(originalPage.getResponse().getContentAsString())
                .selectFirst("input[name=expectedRevision]").val();
        assertThat(oldRevision).hasSize(64);
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("expectedRevision", oldRevision).param("title", "Latest <programme>").param("weeks", "8")
                .param("notesText", "Latest instruction\nSecond instruction"))
                .andExpect(status().is3xxRedirection());
        var stale = mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("expectedRevision", oldRevision).param("q", "Original+programme").param("page", "2")
                .param("title", "Retained <draft>").param("weeks", "6").param("notesText", "Draft instruction\nNext line"))
                .andExpect(status().isConflict()).andReturn();
        var review = org.jsoup.Jsoup.parse(stale.getResponse().getContentAsString());
        assertThat(review.select(".library-programme-conflict h3").text()).isEqualTo("Latest <programme>");
        assertThat(review.select(".library-programme-conflict").text()).contains("8 weeks", "Latest instruction");
        assertThat(review.selectFirst("#title").val()).isEqualTo("Retained <draft>");
        assertThat(review.selectFirst("[data-library-preview=notesText]").wholeText()).isEqualTo("Draft instruction\nNext line");
        assertThat(review.select("#programme-errors")).isEmpty();
        assertThat(review.select("main button[type=submit]").text()).isEqualTo("Save reviewed draft");
        assertThat(review.select("main form input[name=_csrf]")).hasSize(1);
        assertThat(review.select("main programme, main draft")).isEmpty();
        assertThat(saved.getTitle()).isEqualTo("Latest <programme>");
        String refreshedRevision = review.selectFirst("input[name=expectedRevision]").val();
        assertThat(refreshedRevision).isNotEqualTo(oldRevision);
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Missing revision")).andExpect(status().isConflict());
        assertThat(saved.getTitle()).isEqualTo("Latest <programme>");
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("expectedRevision", refreshedRevision).param("q", "Original+programme").param("page", "2")
                .param("title", "Retained <draft>").param("weeks", "6").param("notesText", "Draft instruction\nNext line"))
                .andExpect(redirectedUrl("/trainer/library/programmes/" + saved.getId() + "?q=Original%2Bprogramme&page=2"));
        assertThat(saved.getTitle()).isEqualTo("Retained <draft>");
        assertThat(saved.getWeeks()).isEqualTo(6);
        assertThat(library.getProgrammeDays(saved.getId())).extracting(TrainerLibraryProgrammeDay::getId).containsExactly(firstDay.getId());
        assertThat(library.getProgrammeNotes(saved.getId())).extracting(TrainerLibraryProgrammeNote::getNoteText)
                .containsExactly("Draft instruction", "Next line");
        assertThat(library.getAssignedProgrammesForClient(client.getId()))
                .extracting(view -> view.getProgramme().getId()).containsExactly(saved.getId());
        var other = account();
        mvc.perform(get(path).with(user(other.getUsername()).roles("TRAINER"))).andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post(path).with(user(other.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Foreign draft")).andExpect(redirectedUrl("/access-denied"));
    }

    @Test void editorPreviewAndNativeFormRenderLiteralMetadataInAllUiLocales() throws Exception {
        var form = new TrainerLibraryProgrammeTemplateForm(); form.setTitle("Literal <programme>");
        form.setNotesText("Instruction <literal>\nNext line");
        var saved = library.createProgramme(trainer.getId(), form);
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            for (String path : List.of("/trainer/library/programmes/create", "/trainer/library/programmes/" + saved.getId() + "/edit")) {
                var response = mvc.perform(get(path).param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                        .andExpect(status().isOk()).andReturn();
                var rendered = org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
                assertThat(rendered.select("main").text()).doesNotContain("ui.library.", "??");
                assertThat(rendered.select("main form input[name=_csrf]")).hasSize(1);
                assertThat(rendered.selectFirst("#notesText").attr("dir")).isEqualTo("auto");
                assertThat(rendered.selectFirst("#weeks").attr("aria-describedby")).isEqualTo("weeks-help");
                if (path.endsWith("/edit")) {
                    assertThat(rendered.selectFirst("[data-library-preview=title]").text()).isEqualTo("Literal <programme>");
                    assertThat(rendered.selectFirst("[data-library-preview=notesText]").wholeText()).isEqualTo("Instruction <literal>\nNext line");
                    assertThat(rendered.select("main programme, main literal")).isEmpty();
                }
            }
        }
    }

    @Test void nativeProgrammeMovementPreservesRepeatedWorkoutIdentityAndLiveClientOrder() throws Exception {
        var saved = programme(trainer, "Cycle <programme>", null, 0);
        var first = day(trainer, saved.getId());
        var repeat = new TrainerLibraryProgrammeDayForm(); repeat.setDayOfWeek("Strength A"); repeat.setWorkoutId(first.getWorkoutId());
        var second = library.addProgrammeDay(trainer.getId(), saved.getId(), repeat);
        repeat.setDayOfWeek("Recovery cycle"); repeat.setOrderIndex(Integer.MAX_VALUE);
        var last = library.addProgrammeDay(trainer.getId(), saved.getId(), repeat);
        String metadataRevision = library.getProgrammeRevision(trainer.getId(), saved.getId());
        var client = account(); client.setRole(Role.CLIENT); users.saveAndFlush(client);
        links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var share = new TrainerLibraryShareForm(); share.setTemplateType(TrainerLibraryTemplateType.PROGRAMME);
        share.setTemplateId(saved.getId()); share.setClientId(client.getId()); library.shareTemplate(trainer.getId(), share);
        String path = "/trainer/library/programmes/" + saved.getId() + "/days/" + second.getId() + "/move";
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("direction", "UP").param("q", "Cycle+plan").param("page", "2"))
                .andExpect(redirectedUrl("/trainer/library/programmes/" + saved.getId() + "?q=Cycle%2Bplan&page=2#programme-day-" + second.getId()))
                .andExpect(flash().attribute("libraryOrderSaved", true));
        assertThat(library.getProgrammeDays(saved.getId())).extracting(TrainerLibraryProgrammeDay::getId)
                .containsExactly(second.getId(), first.getId(), last.getId());
        assertThat(second.getWorkoutId()).isEqualTo(first.getWorkoutId());
        assertThat(second.getDayOfWeek()).isEqualTo("Strength A");
        assertThat(library.getProgrammeRevision(trainer.getId(), saved.getId())).isEqualTo(metadataRevision);
        assertThat(library.moveProgrammeDay(trainer.getId(), saved.getId(), second.getId(), "UP")).isFalse();
        assertThat(library.moveProgrammeDay(trainer.getId(), saved.getId(), last.getId(), "DOWN")).isFalse();
        assertThat(library.moveProgrammeDay(trainer.getId(), saved.getId(), last.getId(), "UP")).isTrue();
        assertThat(library.getAssignedProgrammesForClient(client.getId()).getFirst().getDays())
                .extracting(TrainerLibraryProgrammeDay::getId).containsExactly(second.getId(), last.getId(), first.getId());
        mvc.perform(post(path).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "SIDEWAYS"))
                .andExpect(flash().attribute("libraryOrderError", true));
        var other = account(); var foreign = programme(other, "Private cycle", null, 0); var foreignDay = day(other, foreign.getId());
        mvc.perform(post("/trainer/library/programmes/{pid}/days/{did}/move", saved.getId(), foreignDay.getId())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "UP"))
                .andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post(path).with(user(other.getUsername()).roles("TRAINER")).with(csrf()).param("direction", "UP"))
                .andExpect(redirectedUrl("/access-denied"));
        mvc.perform(post("/trainer/library/programmes/{pid}/days/{did}/delete", saved.getId(), Long.MAX_VALUE)
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())).andExpect(redirectedUrl("/access-denied"));
        library.deleteProgramme(trainer.getId(), saved.getId());
        assertThat(programmes.existsById(saved.getId())).isFalse();
        assertThat(programmeDays.findByProgrammeIdOrderByOrderIndexAsc(saved.getId())).isEmpty();
        assertThat(shares.findByClientIdAndTrainerIdOrderBySharedAtDesc(client.getId(), trainer.getId())).isEmpty();
        assertThat(workouts.existsById(first.getWorkoutId())).isTrue();
        assertThat(library.getAssignedProgrammesForClient(client.getId())).isEmpty();
    }

    @Test void nativeProgrammeSequenceAndClientPreviewRenderAllLocalesAndRetainRejectedLabels() throws Exception {
        var saved = programme(trainer, "Literal <programme>", 6, 0);
        var first = day(trainer, saved.getId());
        var repeat = new TrainerLibraryProgrammeDayForm(); repeat.setDayOfWeek("Strength A"); repeat.setWorkoutId(first.getWorkoutId());
        library.addProgrammeDay(trainer.getId(), saved.getId(), repeat);
        String path = "/trainer/library/programmes/" + saved.getId();
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var result = mvc.perform(get(path).param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                    .andExpect(status().isOk()).andReturn();
            var rendered = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(rendered.select(".programme-day-list > li")).hasSize(2);
            assertThat(rendered.select(".programme-client-preview .workout-preview-list > li")).hasSize(2);
            assertThat(rendered.select(".programme-client-preview h2").text()).isEqualTo("Literal <programme>");
            assertThat(rendered.select("main programme")).isEmpty();
            assertThat(rendered.select("main").text()).doesNotContain("ui.library.");
            assertThat(rendered.select(".workout-sequence-actions button[disabled]")).hasSize(2);
            assertThat(rendered.selectFirst("#move-up-" + first.getId()).attr("aria-labelledby"))
                    .contains("programme-label-" + first.getId(), "programme-position-" + first.getId());
            assertThat(rendered.selectFirst(".library-resource-actions button").attr("aria-describedby"))
                    .isEqualTo("programme-delete-help");
            assertThat(rendered.select("main form[method=post]")).allSatisfy(form -> assertThat(form.select("input[name=_csrf]")).hasSize(1));
        }
        var rejected = mvc.perform(post(path + "/days").with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("dayOfWeek", "   ").param("workoutId", first.getWorkoutId().toString()))
                .andExpect(status().isBadRequest()).andReturn();
        var draft = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(draft.selectFirst("#workoutId").selectFirst("option[selected]").val()).isEqualTo(first.getWorkoutId().toString());
        assertThat(draft.select("#day-errors a[href='#dayOfWeek']")).hasSize(1);
        assertThat(draft.selectFirst("#dayOfWeek").attr("aria-invalid")).isEqualTo("true");
        assertThat(library.getProgrammeDays(saved.getId())).hasSize(2);
    }
}
