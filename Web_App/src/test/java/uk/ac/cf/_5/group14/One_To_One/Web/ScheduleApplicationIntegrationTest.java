package uk.ac.cf._5.group14.One_To_One.Web;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.*;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleApplicationIntegrationTest {
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleEntryRepository entries;
    @Autowired ScheduleOccurrenceRepository occurrences;
    @Autowired ScheduleAppliedRepository applications;
    @Autowired ExerciseRepository exercises;
    @Autowired CustomExerciseRepository customs;
    @Autowired uk.ac.cf._5.group14.One_To_One.Chat.ApplyScheduleActionHandler assistantApply;
    @Autowired uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository trainerLinks;

    private Schedule plan(String owner) {
        var plan=new Schedule(); plan.setUser(users.findByUsername(owner)); plan.setName("Plan <review>");
        plan.setScheduleType(ScheduleType.CUSTOM); plan.setCustomDayCount(10);
        plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION); return schedules.save(plan);
    }
    private ScheduleEntry entry(Schedule plan,int day) {
        var row=new ScheduleEntry(); row.setSchedule(plan); row.setDayOfWeek(day); row.setOrderNumber(0);
        row.setExercise(exercises.findAll().getFirst()); return entries.saveAndFlush(row);
    }
    private org.jsoup.nodes.Document preview(Schedule plan,String start,String weeks,String lang) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(post("/schedules/"+plan.getId()+"/apply/preview")
                .with(user("demo").roles("CLIENT")).with(csrf()).param("startDate",start).param("weeks",weeks).param("lang",lang))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void allLocalesHaveOneLabelledNativeFormAndExactCustomDatePreviewWithoutSaving() throws Exception {
        var plan=plan("demo"); entry(plan,8); var customRow=entry(plan,2);
        var custom=new CustomExercise(); custom.setUserId(plan.getUser().getId()); custom.setName("Owned custom movement");
        customRow.setExercise(null); customRow.setCustomExercise(customs.save(custom)); entries.saveAndFlush(customRow);
        Long planId=plan.getId(); entityManager.clear(); plan=schedules.findById(planId).orElseThrow();
        long before=occurrences.count(),windows=applications.count();
        for(String lang:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc=preview(plan,"2027-01-04","4",lang);
            assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.select("#schedule-application-form input[name=_csrf]")).hasSize(1);
            assertThat(doc.select("[data-application-date]").eachAttr("data-application-date"))
                    .containsExactly("2027-01-05","2027-01-11","2027-01-15","2027-01-21","2027-01-25","2027-01-31");
            assertThat(doc.select(".schedule-application").text()).doesNotContain("ui.application.");
            for(var field:doc.select(".schedule-application input:not([type=hidden])"))
                assertThat(doc.select("label[for="+field.id()+"]")).hasSize(1);
        }
        assertThat(occurrences.count()).isEqualTo(before); assertThat(applications.count()).isEqualTo(windows);
    }

    @Test void previewAndRepeatApplicationKeepCompletedMovementAndShowActualSavedFeedback() throws Exception {
        var plan=plan("demo"); var row=entry(plan,8);
        var completed=new ScheduleOccurrence(); completed.setSchedule(plan); completed.setUser(plan.getUser());
        completed.setDate(LocalDate.of(2027,1,11)); completed.setExercise(row.getExercise()); completed.setScheduleName("Original snapshot");
        completed.setCompleted(true); completed=occurrences.saveAndFlush(completed); Long retainedId=completed.getId();
        var doc=preview(plan,"2027-01-04","4","en");
        assertThat(doc.selectFirst(".schedule-composer__notice[role=status]").text())
                .contains("New movements: 2","Already scheduled: 1");
        for(int attempt=0;attempt<2;attempt++) {
            mvc.perform(post("/schedules/"+plan.getId()+"/apply").with(user("demo").roles("CLIENT")).with(csrf())
                    .param("startDate","2027-01-04").param("weeks","4"))
                    .andExpect(redirectedUrl("/calendar")).andExpect(flash().attribute("scheduleApplicationAdded",attempt==0?2:0));
        }
        assertThat(occurrences.findByUserAndDateBetween(plan.getUser(),LocalDate.of(2027,1,4),LocalDate.of(2027,1,31)))
                .filteredOn(item->plan.getId().equals(item.getSchedule().getId())).hasSize(3);
        assertThat(applications.findByUserAndSchedule(plan.getUser(),plan)).hasSize(1);
        var retained=occurrences.findById(retainedId).orElseThrow();
        assertThat(retained.isCompleted()).isTrue(); assertThat(retained.getScheduleName()).isEqualTo("Original snapshot");
    }

    @Test void invalidEmptyForeignAndTokenlessRequestsCannotCreatePartialCalendarItems() throws Exception {
        var plan=plan("demo"); long before=occurrences.count(),windows=applications.count();
        var invalid=preview(plan,"2027-01-04","999","en");
        assertThat(invalid.selectFirst("#apply-weeks").val()).isEqualTo("999");
        assertThat(invalid.select("[role=alert]")).hasSize(1);
        assertThat(preview(plan,"2027-01-04","4","en").select("[data-application-date]")).isEmpty();
        mvc.perform(post("/schedules/"+plan.getId()+"/apply").with(user("demo").roles("CLIENT")).with(csrf())
                .param("startDate","2027-01-04").param("weeks","4"))
                .andExpect(redirectedUrl("/schedules/"+plan.getId()+"/apply")).andExpect(flash().attribute("applyNotice","ui.application.empty"));
        var row=entry(plan,14);
        assertThat(preview(plan,"2027-01-04","4","en").select("[role=alert]").text()).contains("invalid movements");
        row.setDayOfWeek(1); row.setExercise(null);
        var foreign=new CustomExercise(); foreign.setUserId(users.findByUsername("trainer_demo").getId()); foreign.setName("Private");
        row.setCustomExercise(customs.save(foreign)); entries.saveAndFlush(row);
        assertThat(preview(plan,"2027-01-04","4","en").select("[role=alert]").text()).contains("invalid movements");
        var other=plan("trainer_demo");
        mvc.perform(post("/schedules/"+other.getId()+"/apply/preview").with(user("demo").roles("CLIENT")).with(csrf())
                .param("startDate","2027-01-04").param("weeks","4")).andExpect(redirectedUrl("/schedules?error"));
        for(String suffix:List.of("apply","apply/preview")) {
            mvc.perform(post("/schedules/"+plan.getId()+"/"+suffix).with(user("demo").roles("CLIENT"))
                    .param("startDate","2027-01-04").param("weeks","4")).andExpect(status().is4xxClientError());
        }
        assertThat(occurrences.count()).isEqualTo(before); assertThat(applications.count()).isEqualTo(windows);
    }

    @Test void dailyDatesRemainLocalAcrossTheMarchClockChange() throws Exception {
        var plan=plan("demo"); plan.setScheduleType(ScheduleType.DAILY); plan.setCustomDayCount(1); schedules.save(plan);
        entry(plan,1);
        assertThat(preview(plan,"2027-03-27","1","en").select("[data-application-date]").eachAttr("data-application-date"))
                .containsExactly("2027-03-27","2027-03-28","2027-03-29","2027-03-30","2027-03-31","2027-04-01","2027-04-02");
    }

    @Test void assistantUsesCustomDatesAndRepeatsWithoutDuplicatingApplicationWindows() {
        var plan=plan("demo"); var row=entry(plan,8);
        var custom=new CustomExercise(); custom.setUserId(plan.getUser().getId()); custom.setName("Custom assistant movement");
        row.setExercise(null); row.setCustomExercise(customs.save(custom)); entries.saveAndFlush(row);
        Long id=plan.getId(); entityManager.clear(); plan=schedules.findById(id).orElseThrow();
        var user=plan.getUser(); var start=LocalDate.of(2027,1,4);
        var payload=new uk.ac.cf._5.group14.One_To_One.Chat.ApplyScheduleActionPayload(plan.getName(),start,4);
        var first=assistantApply.execute(payload,user);
        assertThat(first.success()).isTrue(); assertThat(first.reply()).contains("Added 3 movements","4 Jan 2027");
        var repeat=assistantApply.execute(payload,user);
        assertThat(repeat.success()).isTrue(); assertThat(repeat.reply()).contains("already scheduled","settings are retained");
        entityManager.flush(); entityManager.clear();
        assertThat(occurrences.findByUserAndDateBetween(user,start,start.plusWeeks(4).minusDays(1)))
                .filteredOn(item->id.equals(item.getSchedule().getId())).extracting(ScheduleOccurrence::getDate)
                .containsExactlyInAnyOrder(LocalDate.of(2027,1,11),LocalDate.of(2027,1,21),LocalDate.of(2027,1,31));
        assertThat(applications.findByUserAndSchedule(user,schedules.findById(id).orElseThrow())).hasSize(1);
    }

    @Test void assistantCanApplyOnlyTheCurrentActiveTrainersPlan() {
        var plan=plan("trainer_demo"); entry(plan,8);
        var client=users.findByUsername("demo");
        var link=new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(client.getId(),plan.getUser().getId(),
                uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE);
        trainerLinks.saveAndFlush(link);
        var payload=new uk.ac.cf._5.group14.One_To_One.Chat.ApplyScheduleActionPayload(plan.getName(),LocalDate.of(2027,1,4),4);
        assertThat(assistantApply.execute(payload,client).success()).isTrue();
        assertThat(applications.findByUserAndSchedule(client,plan)).hasSize(1);
        link.setStatus(uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ENDED); trainerLinks.saveAndFlush(link);
        assertThat(assistantApply.execute(payload,client).success()).isFalse();
        assertThat(applications.findByUserAndSchedule(client,plan)).hasSize(1);
    }
}
