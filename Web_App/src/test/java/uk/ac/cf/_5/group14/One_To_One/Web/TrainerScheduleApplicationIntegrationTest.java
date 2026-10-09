package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.time.LocalTime;
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
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerScheduleApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired TrainerScheduleTemplateService library;
    @Autowired TrainerClientLinkRepository links;
    @Autowired CalendarTaskRepository tasks;
    @Autowired ScheduleOccurrenceRepository occurrences;
    @Autowired VaultNoteRepository notes;
    @Autowired ExerciseRepository exercises;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    User trainer, client;
    TrainerScheduleTemplate template;
    LocalDate date = LocalDate.of(2026, 10, 5);

    private User account(Role role) {
        String name = "schedule-application-" + UUID.randomUUID();
        var user = new User(name + "@example.invalid", "Local", "<Owner>", name, "test-password");
        user.setRole(role); user.setTrainerVerified(role == Role.TRAINER);
        return users.saveAndFlush(user);
    }
    @BeforeEach void setup() {
        trainer = account(Role.TRAINER); client = account(Role.CLIENT);
        links.saveAndFlush(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        template = library.createTemplate(trainer, "Literal <weekly plan>", "Saved description", "recovery+cycle");
    }
    private TrainerScheduleTemplateEntry entry(TrainerScheduleTemplateEntryType type, String title) {
        var row = new TrainerScheduleTemplateEntry(); row.setTitle(title); row.setDayOfWeek(date.getDayOfWeek().getValue());
        row.setType(type); row.setDefaultsJson("Literal <instruction>\nSecond line"); row.setTimeWindowStart(LocalTime.of(9, 30));
        if (type == TrainerScheduleTemplateEntryType.WORKOUT) {
            var exercise = new Exercise(); exercise.setName("Saved <movement>"); exercise.setCategory("Bodyweight");
            exercise.setDifficulty(1); exercise.setType("Strength"); row.setExercise(exercises.saveAndFlush(exercise));
        }
        return library.addEntry(trainer, template.getId(), row);
    }
    private String route() { return "/trainer/templates/" + template.getId() + "/apply"; }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder previewRequest(String locale) {
        return get(route()).param("clientId", client.getId().toString()).param("start", date.toString()).param("end", date.toString())
                .param("idempotent", "true").param("lang", locale).param("q", "recovery+cycle").param("page", "2")
                .with(user(trainer.getUsername()).roles("TRAINER"));
    }

    @Test void reviewedSaveCreatesActualCopiesAndIdempotentRepeatKeepsHistoricalValues() {
        var task = entry(TrainerScheduleTemplateEntryType.TASK, "Saved <task>");
        entry(TrainerScheduleTemplateEntryType.WORKOUT, "Blueprint session title");
        entry(TrainerScheduleTemplateEntryType.NOTE, "Saved <reflection>"); entityManager.flush();
        var preview = library.previewApplication(trainer, template.getId(), client.getId(), date, date, true);
        assertThat(preview.items()).hasSize(3);
        assertThat(preview.items().get(1).getExerciseName()).isEqualTo("Saved <movement>");
        assertThat(preview.items().get(1).getNotes()).isNull();
        assertThat(tasks.countByUserAndDate(client, date)).isZero();
        assertThat(library.applyReviewedTemplate(trainer, template.getId(), client.getId(), date, date, true, preview.revision())).isEqualTo(3);
        var savedTask = tasks.findByUserAndDateOrderByTime(client, date).getFirst();
        assertThat(savedTask.getTitle()).isEqualTo("Saved <task>"); assertThat(savedTask.getTime()).isEqualTo(LocalTime.of(9,30));
        assertThat(savedTask.getNotes()).isEqualTo("Literal <instruction>\nSecond line");
        assertThat(occurrences.findByUserAndDate(client,date)).hasSize(1);
        assertThat(notes.findByUserIdOrderByPinnedDescUpdatedAtDesc(client.getId())).hasSize(1);
        var changed = new TrainerScheduleTemplateEntry(); changed.setDayOfWeek(task.getDayOfWeek()); changed.setType(task.getType());
        changed.setTitle("Later template task"); changed.setDefaultsJson("Later template notes");
        library.updateEntry(trainer, template.getId(), task.getId(), changed);
        library.updateTemplate(trainer, template.getId(), "Later template name", "", "", false);
        var repeat = library.previewApplication(trainer, template.getId(), client.getId(), date, date, true);
        assertThat(repeat.items()).allMatch(TrainerScheduleTemplatePreviewItem::isDuplicate);
        assertThat(library.applyReviewedTemplate(trainer, template.getId(), client.getId(), date, date, true, repeat.revision())).isZero();
        assertThat(tasks.findByUserAndDateOrderByTime(client,date).getFirst().getTitle()).isEqualTo("Saved <task>");
        assertThat(savedTask.getNotes()).isEqualTo("Literal <instruction>\nSecond line");
        var extra = library.previewApplication(trainer, template.getId(), client.getId(), date, date, false);
        assertThat(extra.items()).noneMatch(TrainerScheduleTemplatePreviewItem::isDuplicate);
        assertThat(library.applyReviewedTemplate(trainer, template.getId(), client.getId(), date, date, false, extra.revision())).isEqualTo(3);
        assertThat(tasks.countByUserAndDate(client,date)).isEqualTo(2);
    }

    @Test void staleEntryOrChangedSelectionWritesNothingAndNativeConflictProvidesNewReviewedPreview() throws Exception {
        var row = entry(TrainerScheduleTemplateEntryType.TASK,"Old <task>"); entityManager.flush();
        var old = library.previewApplication(trainer,template.getId(),client.getId(),date,date,true);
        jdbc.update("update trainer_schedule_template_entries set title = ? where id = ?", "Latest <task>",row.getId());
        assertThatThrownBy(() -> library.applyReviewedTemplate(trainer,template.getId(),client.getId(),date,date,true,old.revision()))
                .isInstanceOf(TrainerScheduleApplicationConflictException.class);
        assertThat(tasks.countByUserAndDate(client,date)).isZero();
        var fresh=library.previewApplication(trainer,template.getId(),client.getId(),date,date,true);
        assertThat(fresh.items().getFirst().getTitle()).isEqualTo("Latest <task>");
        assertThatThrownBy(() -> library.applyReviewedTemplate(trainer,template.getId(),client.getId(),date,date.plusDays(1),true,fresh.revision()))
                .isInstanceOf(TrainerScheduleApplicationConflictException.class);
        var response=mvc.perform(post(route()).with(user(trainer.getUsername()).roles("TRAINER")).with(csrf())
                .param("clientId",client.getId().toString()).param("start",date.toString()).param("end",date.toString()).param("idempotent","true")
                .param("expectedApplyRevision",old.revision()).param("q","recovery+cycle").param("page","2"))
                .andExpect(status().isConflict()).andReturn();
        var rendered=org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(rendered.select("main [role=alert]").text()).contains("Nothing was saved");
        assertThat(rendered.select(".schedule-application-preview").text()).contains("Latest <task>");
        assertThat(rendered.selectFirst(".schedule-reviewed-application input[name=expectedApplyRevision]").val()).isEqualTo(fresh.revision());
        assertThat(rendered.selectFirst(".schedule-reviewed-application button").text()).isEqualTo("Apply reviewed preview");
        assertThat(tasks.countByUserAndDate(client,date)).isZero();
    }

    @Test void nativeReviewedPreviewAndNamedConfirmationRenderInFourteenLocalesWithoutWriting() throws Exception {
        entry(TrainerScheduleTemplateEntryType.TASK,"Safe <task>"); entityManager.flush();
        for (String locale:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var result=mvc.perform(previewRequest(locale)).andExpect(status().isOk()).andReturn();
            var rendered=org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
            assertThat(rendered.select("main").text()).doesNotContain("ui.schedule.","ui.library.");
            assertThat(rendered.select("main task, main owner")).isEmpty();
            var form=rendered.selectFirst(".schedule-reviewed-application");
            assertThat(form.select("input[name=_csrf]")).hasSize(1);
            assertThat(form.selectFirst("input[name=expectedApplyRevision]").val()).hasSize(64);
            assertThat(form.attr("data-confirm")).contains(client.getFullName(),date.toString(),"1").doesNotContain("{0}","{3}");
            assertThat(form.attr("action")).isEqualTo(route()+"?q=recovery%2Bcycle&page=2");
            assertThat(rendered.selectFirst("form[method=get] input[name=q]").val()).isEqualTo("recovery+cycle");
        }
        assertThat(tasks.countByUserAndDate(client,date)).isZero();
    }

    @Test void invalidDatesRetainSelectedClientAndNamedErrorsAndForeignClientsCannotBeApplied() throws Exception {
        entry(TrainerScheduleTemplateEntryType.TASK,"Safe task"); entityManager.flush();
        var invalid=mvc.perform(previewRequest("en").param("end",date.minusDays(1).toString())).andExpect(status().isBadRequest()).andReturn();
        var rendered=org.jsoup.Jsoup.parse(invalid.getResponse().getContentAsString());
        assertThat(rendered.select("#application-errors a[href='#applyEnd']")).hasSize(1);
        assertThat(rendered.selectFirst("#applyClient option[selected]").val()).isEqualTo(client.getId().toString());
        assertThat(rendered.selectFirst("#applyEnd").attr("aria-invalid")).isEqualTo("true");
        assertThat(rendered.select(".schedule-reviewed-application")).isEmpty();
        var foreign=account(Role.CLIENT);
        mvc.perform(get(route()).param("clientId",foreign.getId().toString()).param("start",date.toString()).param("end",date.toString())
                .with(user(trainer.getUsername()).roles("TRAINER"))).andExpect(redirectedUrl("/access-denied"));
        assertThat(tasks.countByUserAndDate(client,date)).isZero(); assertThat(tasks.countByUserAndDate(foreign,date)).isZero();
    }

    @Test void disabledClientCannotBeAppliedEvenWhenPreviouslyManagedAsEnabled() {
        entry(TrainerScheduleTemplateEntryType.TASK,"Safe task"); entityManager.flush();
        var preview=library.previewApplication(trainer,template.getId(),client.getId(),date,date,true);
        jdbc.update("update users set enabled = ? where id = ?",false,client.getId());
        assertThatThrownBy(() -> library.applyReviewedTemplate(trainer,template.getId(),client.getId(),date,date,true,preview.revision()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(tasks.countByUserAndDate(client,date)).isZero();
    }
}
