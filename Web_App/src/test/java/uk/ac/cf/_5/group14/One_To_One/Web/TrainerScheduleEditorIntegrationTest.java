package uk.ac.cf._5.group14.One_To_One.Web;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.*;
import uk.ac.cf._5.group14.One_To_One.Checkins.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerScheduleEditorIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerScheduleTemplateService library;
    @Autowired TrainerScheduleTemplateEntryRepository entries;
    @Autowired WeeklyCheckInService checkins;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User trainer;
    TrainerScheduleTemplate template;

    private User account() {
        String name = "schedule-editor-" + UUID.randomUUID();
        var account = new User(name + "@example.invalid", "Local", "Owner", name, "test-password");
        account.setRole(Role.TRAINER); account.setTrainerVerified(true);
        return users.saveAndFlush(account);
    }
    @BeforeEach void setup() {
        trainer = account(); template = library.createTemplate(trainer, "Original <blueprint>", "Original description", "recovery+cycle");
        entityManager.flush();
    }
    private TrainerScheduleTemplateEntry entry(String title, int day) {
        var row = new TrainerScheduleTemplateEntry(); row.setTitle(title); row.setDayOfWeek(day);
        row.setType(TrainerScheduleTemplateEntryType.TASK); row.setDefaultsJson("Literal <instruction>");
        return library.addEntry(trainer, template.getId(), row);
    }

    @Test void staleManagedScheduleMetadataRejectsOverwriteBeforeAnyMutation() {
        String revision = library.getMetadataSnapshot(trainer, template.getId()).revision();
        library.getForTrainer(trainer, template.getId());
        jdbc.update("update trainer_schedule_templates set name = ?, archived = ? where id = ?", "Latest schedule", true, template.getId());
        var draft = new TrainerScheduleMetadataForm(); draft.setName("Older draft"); draft.setExpectedRevision(revision);
        assertThatThrownBy(() -> library.saveMetadata(trainer, template.getId(), draft)).isInstanceOf(TrainerScheduleMetadataConflictException.class);
        assertThat(jdbc.queryForObject("select name from trainer_schedule_templates where id = ?", String.class, template.getId())).isEqualTo("Latest schedule");
        var latest = library.getMetadataSnapshot(trainer, template.getId());
        assertThat(latest.template().isArchived()).isTrue(); assertThat(latest.revision()).isNotEqualTo(revision);
    }

    @Test void conflictRetainsDraftAndNativeReviewedSavePreservesCompositionAndContext() throws Exception {
        var row = entry("Monday <task>", 1); var question = checkins.addQuestion(trainer, template.getId(), "Recovery <question>?", true);
        entityManager.flush();
        String revision = library.getMetadataSnapshot(trainer, template.getId()).revision();
        jdbc.update("update trainer_schedule_templates set name = ? where id = ?", "Latest saved <schedule>", template.getId());
        var rejected = mvc.perform(post("/trainer/templates/{id}/edit", template.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", "recovery+cycle").param("page", "2").param("expectedRevision", revision)
                .param("name", "Retained <draft>").param("description", "Retained instruction").param("tags", "recovery+cycle"))
                .andExpect(status().isConflict()).andReturn();
        var rendered = org.jsoup.Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(rendered.selectFirst("#templateName").val()).isEqualTo("Retained <draft>");
        assertThat(rendered.selectFirst("[data-library-preview=name]").text()).isEqualTo("Retained <draft>");
        assertThat(rendered.select(".schedule-metadata-conflict").text()).contains("Latest saved <schedule>");
        assertThat(rendered.select("main schedule, main draft")).isEmpty();
        String latestRevision = rendered.selectFirst("input[name=expectedRevision]").val();
        assertThat(latestRevision).hasSize(64).isNotEqualTo(revision);
        mvc.perform(post("/trainer/templates/{id}/edit", template.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("q", "recovery+cycle").param("page", "2").param("expectedRevision", latestRevision)
                .param("name", "Reviewed <draft>").param("tags", "recovery+cycle"))
                .andExpect(redirectedUrl("/trainer/templates/" + template.getId() + "/edit?q=recovery%2Bcycle&page=2"));
        assertThat(entries.findByTemplateIdOrderByOrderIndexAsc(template.getId())).extracting(TrainerScheduleTemplateEntry::getId).containsExactly(row.getId());
        assertThat(checkins.listQuestions(template.getId())).extracting(TrainerCheckInQuestion::getId).containsExactly(question.getId());
    }

    @Test void nativeEntryAndQuestionReorderPreservesIdentityAndRejectsForeignOrMissingRows() throws Exception {
        var first = entry("Monday <task>", 1); var second = entry("Wednesday <note>", 3);
        var required = checkins.addQuestion(trainer, template.getId(), "Required recovery?", true);
        var optional = checkins.addQuestion(trainer, template.getId(), "Optional focus?", false);
        entityManager.flush();
        assertThat(library.moveEntry(trainer, template.getId(), first.getId(), "UP")).isFalse();
        mvc.perform(post("/trainer/templates/{id}/entries/{row}/move", template.getId(), second.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("direction", "UP").param("q", "recovery+cycle").param("page", "2"))
                .andExpect(redirectedUrl("/trainer/templates/" + template.getId() + "/edit?q=recovery%2Bcycle&page=2#schedule-entry-" + second.getId()))
                .andExpect(flash().attribute("scheduleOrderSaved", true));
        assertThat(entries.findByTemplateIdOrderByOrderIndexAsc(template.getId())).extracting(TrainerScheduleTemplateEntry::getId).containsExactly(second.getId(), first.getId());
        assertThat(second.getDayOfWeek()).isEqualTo(3); assertThat(second.getDefaultsJson()).isEqualTo("Literal <instruction>");
        assertThat(checkins.moveQuestion(trainer, template.getId(), optional.getId(), "UP")).isTrue();
        assertThat(checkins.listQuestions(template.getId())).extracting(TrainerCheckInQuestion::getId).containsExactly(optional.getId(), required.getId());
        assertThat(optional.isRequired()).isFalse(); assertThat(required.isRequired()).isTrue();
        var foreign = account();
        assertThatThrownBy(() -> library.moveEntry(foreign, template.getId(), first.getId(), "DOWN")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> checkins.moveQuestion(foreign, template.getId(), required.getId(), "UP")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> library.deleteEntry(trainer, template.getId(), Long.MAX_VALUE)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> checkins.deleteQuestion(trainer, template.getId(), Long.MAX_VALUE)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void editorAndRetainedFieldErrorsRenderTranslatedAndEscapedInEveryUiLocale() throws Exception {
        entry("Weekly <task>", 1); checkins.addQuestion(trainer, template.getId(), "Recovery <question>?", true); entityManager.flush();
        for (String locale : List.of("en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh")) {
            var result = mvc.perform(get("/trainer/templates/{id}/edit", template.getId()).param("lang", locale).with(user(trainer.getUsername()).roles("TRAINER")))
                    .andExpect(status().isOk()).andReturn();
            var rendered = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(rendered.select("main").text()).doesNotContain("ui.schedule.", "ui.library.");
            assertThat(rendered.select("#schedule-week-preview .workout-item-card")).hasSize(7);
            assertThat(rendered.select("main task, main blueprint, main question")).isEmpty();
            assertThat(rendered.select("#schedule-metadata input[name=expectedRevision]").val()).hasSize(64);
            assertThat(rendered.select("main form")).allSatisfy(form -> assertThat(form.select("input[name=_csrf]")).hasSize(1));
        }
        var result = mvc.perform(post("/trainer/templates/{id}/entries", template.getId()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Retained <entry>").param("dayOfWeek", "3").param("type", "TASK").param("intensityLevel", "0"))
                .andExpect(status().isBadRequest()).andReturn();
        var retained = org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
        assertThat(retained.selectFirst("#entryTitle").val()).isEqualTo("Retained <entry>");
        assertThat(retained.select("#schedule-entry-errors a[href='#entryIntensityLevel']")).hasSize(1);
        assertThat(retained.selectFirst("#entryIntensityLevel").attr("aria-invalid")).isEqualTo("true");
    }
}
