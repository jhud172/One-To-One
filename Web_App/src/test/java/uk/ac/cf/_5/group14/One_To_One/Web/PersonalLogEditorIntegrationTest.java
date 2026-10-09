package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.CalendarData.*;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.*;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonalLogEditorIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ExerciseLogRepository logs;
    @Autowired ExerciseLogService service;
    @Autowired CalendarTaskRepository tasks;
    @Autowired ScheduleOccurrenceRepository occurrences;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    User owner, other;
    LocalDate date=LocalDate.of(2026,10,4);

    @BeforeEach void setup() { owner=createUser();other=createUser(); }

    @Test void staleManagedReflectionKeepsLatestValuesAndReviewedDraftUpdatesTheSameRecord() throws Exception {
        var log=log("Original reflection");
        String before=service.getLogSnapshot(log.getId(),owner).orElseThrow().revision();
        jdbc.update("update exercise_log set comments=?, duration_minutes=? where id=?", "Latest saved <literal>",45,log.getId());
        long count=logs.count();
        var rejected=mvc.perform(post("/exercise-log/edit/{id}",log.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision",before).param("date",date.toString()).param("moodBefore","2").param("moodAfter","4")
                .param("confidence","3").param("durationMinutes","37").param("comments"," Retained <draft>\nNext step "))
                .andExpect(status().isConflict()).andReturn();
        var html=Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(html.selectFirst(".personal-log-conflict").text()).contains("Latest saved <literal>","45");
        assertThat(html.selectFirst("#comments").wholeText()).isEqualTo(" Retained <draft>\nNext step ");
        assertThat(html.selectFirst("#durationMinutes").val()).isEqualTo("37");
        assertThat(jdbc.queryForObject("select comments from exercise_log where id=?",String.class,log.getId())).isEqualTo("Latest saved <literal>");
        String reviewed=html.selectFirst("input[name=expectedRevision]").val();
        assertThat(reviewed).hasSize(64).isNotEqualTo(before);
        mvc.perform(post("/exercise-log/edit/{id}",log.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("expectedRevision",reviewed).param("date",date.toString()).param("moodBefore","2").param("moodAfter","4")
                .param("confidence","3").param("durationMinutes","37").param("comments"," Retained <draft>\nNext step "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/exercise-log/view/"+log.getId()));
        entities.flush();assertThat(logs.count()).isEqualTo(count);
        assertThat(jdbc.queryForObject("select comments from exercise_log where id=?",String.class,log.getId())).isEqualTo(" Retained <draft>\nNext step ");
        assertThat(log.getCalendarTask()).isNull();assertThat(log.getOccurrence()).isNull();
        mvc.perform(post("/exercise-log/edit/{id}",log.getId()).with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("date",date.toString()).param("moodBefore","2").param("moodAfter","4").param("confidence","3"))
                .andExpect(status().isConflict());
        mvc.perform(get("/exercise-log/edit/{id}",log.getId()).with(user(other.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/exercise-log/list"));
    }

    @Test void rejectedLinkedDraftNamesFieldsKeepsLiteralInputAndHidesForeignSourceNames() throws Exception {
        var task=task(owner,"Canonical calendar <source>");
        var rejected=mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("calendarTaskId",task.getId().toString()).param("exerciseType","Invented source")
                .param("date","bad-date<literal>").param("moodBefore","9").param("moodAfter","4")
                .param("confidence","3").param("durationMinutes","1500").param("comments","Literal <draft>"))
                .andExpect(status().isBadRequest()).andReturn();
        var html=Jsoup.parse(rejected.getResponse().getContentAsString());
        assertThat(html.text()).contains("Canonical calendar <source>","bad-date<literal>").doesNotContain("Invented source");
        assertThat(html.selectFirst("#date").hasAttr("readonly")).isTrue();
        assertThat(html.selectFirst("#durationMinutes").val()).isEqualTo("1500");
        assertThat(html.selectFirst("#comments").val()).isEqualTo("Literal <draft>");
        assertThat(html.select("form[data-exercise-log-form] input[name=_csrf]")).hasSize(1);
        assertThat(html.select("#log-errors a")).hasSize(3);
        for(var anchor:html.select("#log-errors a")) assertThat(html.select(anchor.attr("href"))).hasSize(1);
        var foreign=task(other,"Foreign private source");
        var invalid=mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("calendarTaskId",foreign.getId().toString()).param("date",date.toString()).param("moodBefore","9"))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(Jsoup.parse(invalid.getResponse().getContentAsString()).text()).doesNotContain("Foreign private source");
        mvc.perform(post("/exercise-log").with(user(owner.getUsername()).roles("CLIENT")).with(csrf())
                .param("calendarTaskId",foreign.getId().toString()).param("date",date.toString())
                .param("moodBefore","2").param("moodAfter","4").param("confidence","3"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/access-denied"));
    }

    @Test void duplicateChecksRefreshBothCalendarSourceTypesAlreadyLoadedInTheRequest() {
        var existing=log("Previously saved reflection");
        var task=task(owner,"Owned preloaded task");
        var occurrence=new ScheduleOccurrence();occurrence.setUser(owner);occurrence.setDate(date);occurrence.setScheduleName("Owned preloaded occurrence");
        occurrence=occurrences.save(occurrence);entities.flush();
        assertThat(task.getExerciseLog()).isNull();assertThat(occurrence.getExerciseLog()).isNull();
        jdbc.update("update calendar_tasks set exercise_log_id=? where id=?",existing.getId(),task.getId());
        jdbc.update("update schedule_occurrences set exercise_log_id=? where id=?",existing.getId(),occurrence.getId());
        long count=logs.count();var form=form();form.setCalendarTaskId(task.getId());
        assertThatThrownBy(()->service.saveLog(form,owner)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has a log");
        form.setCalendarTaskId(null);form.setOccurrenceId(occurrence.getId());
        assertThatThrownBy(()->service.saveLog(form,owner)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("already has a log");
        assertThat(logs.count()).isEqualTo(count);
        assertThat(task.getExerciseLog().getId()).isEqualTo(existing.getId());
        assertThat(occurrence.getExerciseLog().getId()).isEqualTo(existing.getId());
    }

    @Test void linkedEditorAndSavedDetailsKeepCanonicalContextAndActualRatingsInFourteenLocales() throws Exception {
        var task=task(owner,"Owned calendar <literal>");var form=form();form.setCalendarTaskId(task.getId());
        for(String locale:new String[]{"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"}) {
            var response=mvc.perform(get("/exercise-log/add-calendar").param("taskId",task.getId().toString()).param("lang",locale)
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var html=Jsoup.parse(response.getResponse().getContentAsString());
            assertThat(html.text()).contains("Owned calendar <literal>").doesNotContain("??ui.log.");
            assertThat(html.select("input[type=radio]")).hasSize(12);
            assertThat(html.select("form[data-exercise-log-form] input[name=_csrf]")).hasSize(1);
            assertThat(html.selectFirst("#date").val()).isEqualTo(date.toString());
            assertThat(html.selectFirst("#date").hasAttr("readonly")).isTrue();
            assertThat(html.selectFirst(".personal-log-origin a").attr("href")).contains("/calendar/day/2026-10-04");
        }
        service.saveLog(form,owner);entities.flush();var saved=task.getExerciseLog();
        for(String locale:new String[]{"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"}) {
            var response=mvc.perform(get("/exercise-log/view/{id}",saved.getId()).param("lang",locale)
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var html=Jsoup.parse(response.getResponse().getContentAsString());
            assertThat(html.text()).contains("Owned calendar <literal>","Literal <notes>","37","1 / 4","4 / 4","3 / 4").doesNotContain("??ui.log.");
            assertThat(html.select("bdi[dir=ltr]")).hasSize(3);
        }
        mvc.perform(get("/exercise-log/add-calendar").param("taskId",task.getId().toString()).with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/exercise-log/edit/"+saved.getId()));
    }

    private User createUser() {
        String name="editor-"+UUID.randomUUID();var user=new User(name+"@example.invalid","Local","Client",name,"test-password");
        user.setRole(Role.CLIENT);return users.saveAndFlush(user);
    }
    private ExerciseLog log(String note) {
        var log=new ExerciseLog();log.setUser(owner);log.setDate(date);log.setMoodBefore(1);log.setMoodAfter(4);log.setConfidence(3);
        log.setComments(note);return logs.saveAndFlush(log);
    }
    private CalendarTask task(User user,String name) {
        var task=new CalendarTask();task.setUser(user);task.setDate(date);task.setTitle(name);task.setExercise(true);task.setRequiresLog(true);
        task=tasks.save(task);entities.flush();return task;
    }
    private ExerciseLogForm form() {
        var form=new ExerciseLogForm();form.setDate(date);form.setMoodBefore(1);form.setMoodAfter(4);form.setConfidence(3);
        form.setDurationMinutes(37);form.setComments("Literal <notes>");return form;
    }
}
