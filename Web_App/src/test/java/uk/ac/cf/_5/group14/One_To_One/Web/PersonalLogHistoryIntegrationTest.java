package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.UUID;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.CalendarData.*;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.*;
import uk.ac.cf._5.group14.One_To_One.Users.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PersonalLogHistoryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ExerciseLogRepository logs;
    @Autowired ExerciseLogService service;
    @Autowired CalendarTaskRepository tasks;
    @Autowired DevModeProperties development;
    @Autowired uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository exercises;
    @Autowired uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExercises;
    @Autowired uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository occurrences;
    User owner, other;
    LocalDate date = LocalDate.of(2026,10,3);

    @BeforeEach void setup() { owner=createUser(); other=createUser(); }

    @Test void historyPagesInDatabaseWithStableTiesLiteralSearchAndOwnedDateBounds() {
        java.util.List<Long> ids=new java.util.ArrayList<>();
        for(int i=0;i<7;i++) ids.add(log(owner,date,"Literal %_ <note> " + i).getId());
        log(other,date,"Literal %_ <note> Foreign"); log(owner,date.minusDays(1),"Other day");
        var newest=service.searchHistory(owner,"%_",date,date,false,0);
        assertThat(newest.getTotalElements()).isEqualTo(7); assertThat(newest.getTotalPages()).isEqualTo(2);
        assertThat(newest.getContent()).extracting(ExerciseLog::getId).containsExactly(ids.get(6),ids.get(5),ids.get(4),ids.get(3),ids.get(2),ids.get(1));
        assertThat(service.searchHistory(owner,"%_",date,date,false,9999).getContent()).extracting(ExerciseLog::getId).containsExactly(ids.getFirst());
        assertThat(service.searchHistory(owner,"%_",date,date,true,0).getContent()).extracting(ExerciseLog::getId)
                .containsExactly(ids.get(0),ids.get(1),ids.get(2),ids.get(3),ids.get(4),ids.get(5));
        assertThat(service.searchHistory(owner,"missing",null,null,false,9999).getNumber()).isZero();
        assertThat(service.searchHistory(owner,"2026-10-02",null,null,false,0).getTotalElements()).isEqualTo(1);
    }

    @Test void exerciseAndTaskNamesSearchTheirOwnedLinkedReflection() throws Exception {
        var task=new CalendarTask(); task.setUser(owner); task.setDate(date); task.setTitle("Linked squat <literal>");
        task.setExercise(true); task.setRequiresLog(true); task=tasks.save(task);
        var form=new ExerciseLogForm(); form.setDate(date); form.setMoodBefore(1);form.setMoodAfter(4);form.setConfidence(3);
        form.setCalendarTaskId(task.getId()); form.setComments("Saved linked reflection"); service.saveLog(form,owner);
        var response=mvc.perform(get("/exercise-log/list").param("q","squat").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("logTotal",1L)).andReturn();
        var html=Jsoup.parse(response.getResponse().getContentAsString());
        assertThat(html.selectFirst(".personal-history-card h3").text()).isEqualTo("Linked squat <literal>");
        assertThat(html.selectFirst(".personal-history-origin").text()).isEqualTo("Calendar-linked reflection");
        assertThat(html.selectFirst(".personal-history-card").text()).contains("1 / 4","4 / 4");
        var foreign=new CalendarTask(); foreign.setUser(other); foreign.setDate(date); foreign.setTitle("Foreign private source");
        foreign=tasks.save(foreign);
        // Old malformed associations must not expose another owner's source title in the new summary.
        var legacy=log(owner,date,"Legacy reflection");legacy.setCalendarTask(foreign);logs.saveAndFlush(legacy);
        var legacyPage=mvc.perform(get("/exercise-log/list").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        assertThat(Jsoup.parse(legacyPage.getResponse().getContentAsString()).text()).doesNotContain("Foreign private source");
        assertThat(service.searchHistory(owner,"Foreign private source",null,null,false,0).getContent()).isEmpty();
        var exercise=new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        exercise.setName("History catalogue movement");exercise.setCategory("Strength");exercise.setDifficulty(1);exercise.setType("Strength");
        exercise=exercises.save(exercise);
        var custom=new uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise();
        custom.setUserId(owner.getId());custom.setName("History custom movement <literal>");custom=customExercises.save(custom);
        for(boolean useCustom:new boolean[]{false,true}) {
            var occurrence=new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
            occurrence.setUser(owner);occurrence.setDate(date);occurrence.setScheduleName("Owned history fixture");
            if(useCustom) occurrence.setCustomExercise(custom);else occurrence.setExercise(exercise);
            occurrence=occurrences.save(occurrence);form.setCalendarTaskId(null);form.setOccurrenceId(occurrence.getId());
            service.saveLog(form,owner);
            String name=useCustom?custom.getName():exercise.getName();
            assertThat(service.searchHistory(owner,name,null,null,false,0).getTotalElements()).isEqualTo(1);
        }
        var foreignCustom=new uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise();
        foreignCustom.setUserId(other.getId());foreignCustom.setName("Foreign custom private title");foreignCustom=customExercises.save(foreignCustom);
        var malformed=new uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence();
        malformed.setUser(owner);malformed.setDate(date);malformed.setScheduleName("Old malformed source");malformed.setCustomExercise(foreignCustom);
        malformed=occurrences.save(malformed);var malformedLog=log(owner,date,"Old custom reflection");malformedLog.setOccurrence(malformed);logs.saveAndFlush(malformedLog);
        var privatePage=mvc.perform(get("/exercise-log/list").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        assertThat(Jsoup.parse(privatePage.getResponse().getContentAsString()).text()).doesNotContain("Foreign custom private title");
        assertThat(service.searchHistory(owner,"Foreign custom private title",null,null,false,0).getContent()).isEmpty();
    }

    @Test void invalidDatesKeepNamedEscapedFiltersAndValidPagingRetainsAllContextInFourteenLocales() throws Exception {
        for(int i=0;i<7;i++) log(owner,date,"Literal <draft> " + i);
        for(String locale:new String[]{"en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh"}) {
            var rejected=mvc.perform(get("/exercise-log/list").param("lang",locale).param("q","Literal <draft>")
                    .param("from","not-a-date<literal>").param("until",date.toString()).with(user(owner.getUsername()).roles("CLIENT")))
                    .andExpect(status().isBadRequest()).andReturn();
            var html=Jsoup.parse(rejected.getResponse().getContentAsString());
            assertThat(html.selectFirst("#logSearch").val()).isEqualTo("Literal <draft>");
            assertThat(html.selectFirst("#history-from-draft").text()).isEqualTo("not-a-date<literal>");
            assertThat(html.selectFirst("#from").attr("aria-invalid")).isEqualTo("true");
            assertThat(html.select("#history-errors a[href='#from']")).hasSize(1);
            assertThat(html.select(".personal-history-card")).isEmpty();
            var accepted=mvc.perform(get("/exercise-log/list").param("lang",locale).param("q","Literal <draft>")
                    .param("from",date.toString()).param("until",date.toString()).param("sort","oldest")
                    .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            html=Jsoup.parse(accepted.getResponse().getContentAsString());
            assertThat(html.select(".personal-history-card")).hasSize(6);
            assertThat(html.select("nav.connection-actions a").last().attr("href"))
                    .contains("from=2026-10-03","until=2026-10-03","sort=oldest","page=2");
            assertThat(html.text()).doesNotContain("??ui.log.");
        }
        mvc.perform(get("/exercise-log/list").param("from",date.toString()).param("until",date.minusDays(1).toString())
                .with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("historyFilter","until"));
        mvc.perform(get("/exercise-log/list").param("page",Integer.MIN_VALUE+"").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(model().attribute("logPage",1));
    }

    @Test void authenticatedDevelopmentClientCanUseOwnedLogsButAnonymousAndOtherOwnersCannot() throws Exception {
        var own=log(owner,date,"Own reflection"); boolean before=development.isDevMode(); development.setDevMode(true);
        try {
            mvc.perform(get("/exercise-log/list").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk());
            mvc.perform(get("/exercise-log").with(user(owner.getUsername()).roles("CLIENT"))).andExpect(status().isOk());
            mvc.perform(get("/exercise-log/edit/{id}",own.getId()).with(user(other.getUsername()).roles("CLIENT")))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/exercise-log/list"));
            mvc.perform(get("/exercise-log/list").accept(org.springframework.http.MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection());
            mvc.perform(get("/exercise-log/export/pdf")).andExpect(status().isUnauthorized());
        } finally { development.setDevMode(before); }
    }

    @Test void ownedPdfIsParseablePreservesNotesAndDurationAndUsesTheEditorsActualRatingScale() throws Exception {
        var own=log(owner,date,"Owner export <literal>"); own.setDurationMinutes(37); logs.saveAndFlush(own);
        log(other,date,"Foreign export must stay private");
        var response=mvc.perform(get("/exercise-log/export/pdf").with(user(owner.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition","attachment; filename=exercise_logs.pdf")).andReturn();
        try(var pdf=new PdfReader(response.getResponse().getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            var text=new PdfTextExtractor(pdf).getTextFromPage(1).replaceAll("\\s+", " ");
            assertThat(text).contains("1-4 scale","2026-10-03","Owner export <literal>","Duration (min)","37")
                    .doesNotContain("Foreign export","Neutral","Excellent");
        }
    }

    private User createUser() {
        String name="history-"+UUID.randomUUID(); var user=new User(name+"@example.invalid","Local","Client",name,"test-password");
        user.setRole(Role.CLIENT);return users.saveAndFlush(user);
    }
    private ExerciseLog log(User user,LocalDate day,String note) {
        var log=new ExerciseLog();log.setUser(user);log.setDate(day);log.setMoodBefore(1);log.setMoodAfter(4);
        log.setConfidence(3);log.setComments(note);return logs.saveAndFlush(log);
    }
}
