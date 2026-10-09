package uk.ac.cf._5.group14.One_To_One.Web;

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
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.*;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInService;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerScheduleCatalogueIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerScheduleTemplateRepository templates;
    @Autowired TrainerScheduleTemplateService library;
    @Autowired TrainerScheduleTemplateEntryRepository entries;
    @Autowired WeeklyCheckInService checkins;
    User trainer;

    private User account() {
        String name = "schedule-catalogue-" + UUID.randomUUID();
        var account = new User(name + "@example.invalid", "Local", "Owner", name, "test-password");
        account.setRole(Role.TRAINER); account.setTrainerVerified(true);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() { trainer = account(); }
    private TrainerScheduleTemplate template(User owner, String name, String tags, boolean archived, int order) {
        var template = new TrainerScheduleTemplate(); template.setTrainerId(owner.getId());
        template.setName(name); template.setTags(tags); template.setArchived(archived);
        ReflectionTestUtils.setField(template, "createdAt", Instant.parse("2026-10-01T12:00:00Z").plusSeconds(order));
        return templates.saveAndFlush(template);
    }
    private void addEntry(User owner, Long templateId) {
        var entry = new TrainerScheduleTemplateEntry(); entry.setDayOfWeek(1);
        entry.setType(TrainerScheduleTemplateEntryType.TASK); entry.setTitle("Monday check");
        library.addEntry(owner, templateId, entry);
    }
    private org.jsoup.nodes.Document page(String query, int page, String locale) throws Exception {
        var result = mvc.perform(get("/trainer/templates").param("q", query).param("page", "" + page).param("lang", locale)
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test void boundedOwnedSearchEscapesWildcardsAndCountsOnlyDisplayedTemplateEntriesAndQuestions() throws Exception {
        for (int index = 0; index < 22; index++) template(trainer, "Foundation " + index, index == 0 ? null : "strength", false, index);
        var other = account(); var foreign = template(other, "Private !%_", "strength", false, 30); addEntry(other, foreign.getId());
        var first = library.searchForTrainer(trainer, "", 0);
        assertThat(first.page().getContent()).hasSize(18);
        assertThat(first.page().getContent().getFirst().getName()).isEqualTo("Foundation 21");
        assertThat(first.ownedCount()).isEqualTo(22);
        assertThat(library.searchForTrainer(trainer, "", Integer.MAX_VALUE).page().getNumber()).isEqualTo(1);
        assertThat(library.searchForTrainer(trainer, "", -5).page().getNumber()).isZero();
        var literal = template(trainer, "Literal !%_ <blueprint>", "recovery+cycle", true, 40); addEntry(trainer, literal.getId());
        checkins.addQuestion(trainer, literal.getId(), "How was recovery?", true);
        var searched = library.searchForTrainer(trainer, "!%_", 0);
        assertThat(searched.page().getContent()).extracting(TrainerScheduleTemplate::getId).containsExactly(literal.getId());
        assertThat(searched.entryCounts()).containsOnly(entry(literal.getId(), 1L));
        assertThat(searched.questionCounts()).containsOnly(entry(literal.getId(), 1L));
        assertThat(searched.weekdayCounts()).containsOnly(entry(literal.getId(), java.util.Map.of(1, 1L)));
        assertThat(library.searchForTrainer(trainer, " RECOVERY+cycle ", 0).page().getContent())
                .extracting(TrainerScheduleTemplate::getId).containsExactly(literal.getId());
        assertThat(library.searchForTrainer(trainer, "strength", 0).page().getTotalElements()).isEqualTo(21);
        assertThat(library.searchForTrainer(trainer, "missing", 9999).page().getNumber()).isZero();
        assertThat(library.searchForTrainer(trainer, "x".repeat(121), 0).query()).hasSize(120);
        assertThat(entries.countOwnedWeekdaysOnPage(trainer.getId(), List.of(literal.getId(), foreign.getId())))
                .allSatisfy(row -> assertThat(row[0]).isEqualTo(literal.getId()));
        assertThat(page("", 9999, "en").select(".library-catalogue-card")).hasSize(5);
    }

    @Test void nativeCatalogueDistinguishesStatesAndLocalisesLiteralArchivedAndActiveCards() throws Exception {
        assertThat(page("", 0, "en").select(".library-exercise-empty").text()).contains("No templates yet");
        var active = template(trainer, "Literal <blueprint>", null, false, 0);
        addEntry(trainer, active.getId());
        var archived = template(trainer, "Archived <blueprint>", "strength", true, 1);
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var rendered = page("blueprint", 0, locale);
            assertThat(rendered.select(".library-catalogue-card")).hasSize(2);
            assertThat(rendered.select("main blueprint")).isEmpty();
            assertThat(rendered.select("main").text()).doesNotContain("ui.schedule.", "??");
            assertThat(rendered.select("#schedule-template-" + active.getId() + " a[href*='/apply']")).hasSize(1);
            assertThat(rendered.select("#schedule-template-" + archived.getId() + " a[href*='/apply']")).isEmpty();
            assertThat(rendered.select("main form[method=post] input[name=_csrf]")).hasSize(2);
            assertThat(rendered.select("#schedule-template-" + active.getId() + " .schedule-catalogue-rhythm li")).hasSize(1);
            assertThat(rendered.selectFirst("#clone-template-" + active.getId()).attr("aria-labelledby"))
                    .contains("template-title-" + active.getId());
            assertThat(rendered.selectFirst("main a[href='/trainer/templates']").text()).isNotBlank();
        }
        assertThat(page("missing", 0, "en").select(".library-exercise-empty").text()).contains("No results found").doesNotContain("No templates yet");
        trainer.setTrainerVerified(false); users.saveAndFlush(trainer);
        var restricted = mvc.perform(get("/trainer/templates").with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        var denied = org.jsoup.Jsoup.parse(restricted.getResponse().getContentAsString());
        assertThat(denied.select("main .library-catalogue-card, main form")).isEmpty();
        assertThat(denied.select("main a[href='/trainer/verification']")).hasSize(1);
    }

    @Test void nativeEditorCloneAndPreviewKeepBoundedLiteralInternalReturnContext() throws Exception {
        var saved = template(trainer, "Foundation+rhythm", "strength", false, 0); addEntry(trainer, saved.getId());
        checkins.addQuestion(trainer, saved.getId(), "First prompt", true);
        String query = "Foundation+rhythm";
        String root = "/trainer/templates/" + saved.getId();
        var response = mvc.perform(get(root + "/edit").param("q", query).param("page", "2")
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        var rendered = org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(rendered.selectFirst("main .detail-header a").attr("href"))
                .isEqualTo("/trainer/templates?q=Foundation%2Brhythm&page=2#schedule-template-" + saved.getId());
        assertThat(rendered.select("main form[method=post]").eachAttr("action"))
                .allMatch(action -> action.contains("q=Foundation%2Brhythm&page=2"));
        mvc.perform(post(root + "/edit").param("q", query).param("page", "2").param("name", saved.getName())
                .param("expectedRevision", library.getMetadataSnapshot(trainer, saved.getId()).revision())
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl(root + "/edit?q=Foundation%2Brhythm&page=2"));
        var cloned = mvc.perform(post(root + "/clone").param("q", query).param("page", "2")
                .param("returnUrl", "https://outside.example.invalid/")
                .with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(cloned.getResponse().getRedirectedUrl()).matches("/trainer/templates/[0-9]+/edit\\?q=Foundation%2Brhythm&page=2");
        var copied = library.listForTrainer(trainer).stream().filter(t -> !t.getId().equals(saved.getId())).findFirst().orElseThrow();
        assertThat(entries.findByTemplateIdOrderByOrderIndexAsc(copied.getId())).hasSize(1);
        assertThat(checkins.listQuestions(copied.getId())).extracting(q -> q.getPrompt()).containsExactly("First prompt");
        var preview = mvc.perform(get(root + "/apply").param("q", query).param("page", "2")
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
        var apply = org.jsoup.Jsoup.parse(preview.getResponse().getContentAsString());
        assertThat(apply.selectFirst("main form[method=get] input[name=q]").val()).isEqualTo(query);
        assertThat(apply.selectFirst("main form[method=get] input[name=page]").val()).isEqualTo("2");
        var other = account();
        mvc.perform(post(root + "/clone").with(user(other.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(redirectedUrl("/access-denied"));
    }
}
