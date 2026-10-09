package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.*;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleWorkspaceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleEntryRepository entries;
    @Autowired ScheduleAppliedRepository applied;
    @Autowired ScheduleOccurrenceRepository occurrences;
    @Autowired ScheduleOccurrenceService calendar;
    @Autowired ExerciseRepository exercises;
    @Autowired WorkoutSessionRepository sessions;
    @Autowired TrainerClientLinkRepository links;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Schedule plan(String owner) {
        Schedule plan=new Schedule(); plan.setUser(users.findByUsername(owner));
        plan.setName("Plan <literal>"); return schedules.save(plan);
    }
    private ScheduleApplied window(Schedule plan, LocalDate start, boolean shown) {
        ScheduleApplied window=new ScheduleApplied(); window.setUser(users.findByUsername("demo"));
        window.setSchedule(plan); window.setDateApplied(start); window.setDurationWeeks(1);
        window.setShownOnCalendar(shown); return applied.save(window);
    }
    private ScheduleOccurrence occurrence(Schedule plan,LocalDate date) {
        ScheduleOccurrence item=new ScheduleOccurrence(); item.setUser(users.findByUsername("demo"));
        item.setSchedule(plan); item.setScheduleName(plan.getName()); item.setDate(date);
        item.setExercise(exercises.findAll().iterator().next()); return occurrences.saveAndFlush(item);
    }
    private ScheduleEntry entry(Schedule plan,int day) {
        ScheduleEntry item=new ScheduleEntry(); item.setSchedule(plan); item.setDayOfWeek(day);
        item.setExercise(exercises.findAll().iterator().next()); item.setOrderNumber(3);
        return entries.saveAndFlush(item);
    }
    private org.jsoup.nodes.Document page(String lang) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/schedules").param("lang",lang).with(user("demo").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void allLocalesRenderRealStatusesSingleCsrfNativeSettingsAndSafeInlinePreviews() throws Exception {
        Schedule plan=plan("demo"); entry(plan,2); window(plan,LocalDate.now(),false);
        for(String lang:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc=page(lang);
            assertThat(doc.select("main")).hasSize(1);
            assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.select(".schedule-centre").text()).doesNotContain("ui.scheduleCentre.","Loading...");
            assertThat(doc.select(".schedule-centre h4").eachText()).contains("Plan <literal>");
            assertThat(doc.select(".schedule-centre__preview summary")).isNotEmpty();
            assertThat(doc.select(".schedule-centre__search[hidden]")).hasSize(1);
            for(var form:doc.select(".schedule-centre form")) {
                assertThat(form.select("input[name=_csrf]")).hasSize(1);
                assertThat(form.select("button[type=submit]")).isNotEmpty();
            }
            assertThat(doc.select("[data-auto-submit]")).isEmpty();
        }
        var english=page("en");
        assertThat(english.select(".schedule-centre__plan .schedule-centre__status").eachText()).contains("Hidden");
    }

    @Test void hideRestoreAndLegacyRemovePreserveOccurrenceIdsFlagsSessionsAndCounts() throws Exception {
        var owner=users.findByUsername("demo"); var plan=plan("demo");
        LocalDate date=LocalDate.now(); var deployment=window(plan,date,true); var item=occurrence(plan,date);
        item.setCompleted(true); occurrences.saveAndFlush(item);
        WorkoutSession session=new WorkoutSession(); session.setUser(owner); session.setSchedule(plan); session.setCreatedAt(java.time.LocalDateTime.now());
        session.setDate(date); session.setSourceOccurrenceId(item.getId()); session.setCompleted(true); sessions.saveAndFlush(session);
        long before=occurrences.count();
        mvc.perform(post("/schedules/applied/"+deployment.getId()+"/settings").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules?saved"));
        assertThat(calendar.getOccurrencesForUserOnDate(owner,date)).noneMatch(o->o.getId().equals(item.getId()));
        assertThat(occurrences.findById(item.getId()).orElseThrow().isCompleted()).isTrue();
        assertThat(sessions.findById(session.getId())).isPresent();
        mvc.perform(post("/schedules/applied/"+deployment.getId()+"/settings").with(user("demo").roles("CLIENT")).with(csrf())
                .param("shownOnCalendar","true").param("requiresLogging","true")).andExpect(redirectedUrl("/schedules?saved"));
        assertThat(calendar.getOccurrencesForUserOnDate(owner,date)).anyMatch(o->o.getId().equals(item.getId()) && o.isLoggingRequested());
        assertThat(occurrences.count()).isEqualTo(before);
        mvc.perform(post("/schedules/applied/"+deployment.getId()+"/remove").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules?hidden"));
        assertThat(applied.findById(deployment.getId())).isPresent();
        assertThat(occurrences.count()).isEqualTo(before);
    }

    @Test void overlappingWindowsUseVisibleUnionAndDoNotHideOtherDatesPlansOrUsers() {
        var owner=users.findByUsername("demo"); LocalDate date=LocalDate.now(); var plan=plan("demo");
        var first=window(plan,date,false); var item=occurrence(plan,date); var later=occurrence(plan,date.plusDays(8));
        var unrelated=occurrence(plan("demo"),date);
        assertThat(calendar.getOccurrencesByRange(owner,date,date.plusDays(8)).get(date)).contains(unrelated).doesNotContain(item);
        assertThat(calendar.getOccurrencesForUserInMonth(owner,date.getYear(),date.getMonthValue()).values().stream().flatMap(List::stream))
                .noneMatch(o->o.getId().equals(item.getId()));
        assertThat(calendar.getOccurrencesForUserOnDate(owner,date.plusDays(8))).contains(later);
        var second=window(plan,date.plusDays(1),true);
        assertThat(calendar.getOccurrencesForUserOnDate(owner,date)).doesNotContain(item);
        second.setDateApplied(date); applied.saveAndFlush(second);
        assertThat(calendar.getOccurrencesForUserOnDate(owner,date)).contains(item);
        assertThat(first.isShownOnCalendar()).isFalse();
        assertThat(calendar.getOccurrencesForUserOnDate(users.findByUsername("trainer_demo"),date)).doesNotContain(item,unrelated);
    }

    @Test void deleteRejectsOccurrencesDeploymentsAndSessionsButUnusedPlanCanBeDeleted() throws Exception {
        for(int type=0;type<4;type++) {
            var plan=plan("demo");
            if(type==0) occurrence(plan,LocalDate.now());
            if(type==1) window(plan,LocalDate.now(),false);
            if(type==2) { WorkoutSession session=new WorkoutSession(); session.setUser(users.findByUsername("demo"));
                session.setDate(LocalDate.now()); session.setSchedule(plan); session.setCreatedAt(java.time.LocalDateTime.now()); sessions.saveAndFlush(session); }
            if(type==3) jdbc.update("insert into assigned_schedules(trainer_id,client_id,schedule_id) values(?,?,?)",
                    users.findByUsername("demo").getId(),users.findByUsername("trainer_demo").getId(),plan.getId());
            mvc.perform(post("/schedules/"+plan.getId()+"/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                    .andExpect(redirectedUrl("/schedules")).andExpect(flash().attribute("scheduleCentreError","ui.scheduleCentre.deleteBlocked"));
            assertThat(schedules.findById(plan.getId())).isPresent();
        }
        var unused=plan("demo"); entry(unused,2);
        mvc.perform(post("/schedules/"+unused.getId()+"/delete").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules?deleted"));
        assertThat(schedules.findById(unused.getId())).isEmpty();
    }

    @Test void nativeAndApiCopiesPreserveRotationBoundsEntriesAndDoNotCopyDeployments() throws Exception {
        var plan=plan("demo"); plan.setName("x".repeat(200)); plan.setScheduleType(ScheduleType.CUSTOM);
        plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION); plan.setCustomDayCount(10); plan.setTemplateId("source-template");
        schedules.save(plan); entry(plan,4); window(plan,LocalDate.now(),true);
        for(String prefix:List.of("/schedules/","/api/schedules/")) {
            var result=mvc.perform(post(prefix+plan.getId()+"/duplicate").with(user("demo").roles("CLIENT")).with(csrf())).andReturn();
            Long id=prefix.startsWith("/api") ? new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("id").asLong()
                    : Long.valueOf(result.getResponse().getRedirectedUrl().split("/")[2]);
            var copy=schedules.findById(id).orElseThrow();
            assertThat(copy.getName()).hasSize(200); assertThat(copy.getRotationMode()).isEqualTo(plan.getRotationMode());
            assertThat(copy.getScheduleType()).isEqualTo(plan.getScheduleType()); assertThat(copy.getCustomDayCount()).isEqualTo(10);
            assertThat(copy.getTemplateId()).isEqualTo("source-template");
            assertThat(entries.findBySchedule(copy)).hasSize(1); assertThat(entries.findBySchedule(copy).getFirst().getOrderNumber()).isEqualTo(3);
            assertThat(applied.findByUserAndSchedule(users.findByUsername("demo"),copy)).isEmpty();
        }
    }

    @Test void foreignActionsAndTokenlessSettingsCannotMutate() throws Exception {
        var foreign=plan("trainer_demo"); var deployment=window(foreign,LocalDate.now(),true);
        deployment.setUser(users.findByUsername("trainer_demo")); applied.saveAndFlush(deployment);
        for(String action:List.of("delete","duplicate","deactivate"))
            mvc.perform(post("/schedules/"+foreign.getId()+"/"+action).with(user("demo").roles("CLIENT")).with(csrf()))
                    .andExpect(redirectedUrl("/schedules?error"));
        for(String action:List.of("settings","remove"))
            mvc.perform(post("/schedules/applied/"+deployment.getId()+"/"+action).with(user("demo").roles("CLIENT")).with(csrf()))
                    .andExpect(redirectedUrl("/schedules?error"));
        assertThat(deployment.isShownOnCalendar()).isTrue();
        var result=mvc.perform(post("/schedules/applied/"+deployment.getId()+"/settings").with(user("trainer_demo").roles("TRAINER"))).andReturn();
        assertThat(result.getResponse().getStatus()).isBetween(400,499);
        assertThat(deployment.isShownOnCalendar()).isTrue();
    }

    @Test void sharedPlanPreviewIsServerRenderedOnlyWhileTrainerRelationshipIsActive() throws Exception {
        var shared=plan("trainer_demo"); entry(shared,3);
        var link=links.saveAndFlush(new TrainerClientLink(users.findByUsername("demo").getId(),users.findByUsername("trainer_demo").getId(),TrainerClientLinkStatus.ACTIVE));
        assertThat(page("en").select(".schedule-centre__preview").text()).contains("Wednesday");
        link.setStatus(TrainerClientLinkStatus.ENDED); links.saveAndFlush(link);
        assertThat(page("en").select(".schedule-centre__plan[data-schedule-name]").eachText()).noneMatch(text->text.contains("Wednesday"));
    }

    @Test void legacyDeactivateHidesOnlyOwnedDeploymentsAndRetainsRecords() throws Exception {
        var own=plan("demo"); var deployment=window(own,LocalDate.now(),true); var item=occurrence(own,LocalDate.now());
        mvc.perform(post("/schedules/"+own.getId()+"/deactivate").with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules?deactivated"));
        assertThat(deployment.isShownOnCalendar()).isFalse();
        assertThat(occurrences.findById(item.getId())).isPresent();
        assertThat(applied.findById(deployment.getId())).isPresent();
    }
}
