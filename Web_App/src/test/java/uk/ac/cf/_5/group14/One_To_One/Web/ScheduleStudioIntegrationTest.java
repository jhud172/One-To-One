package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.*;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Workout.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleStudioIntegrationTest {
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleEntryRepository entries;
    @Autowired ScheduleStudioService studio;
    @Autowired ScheduleOccurrenceRepository occurrences;
    @Autowired ScheduleAppliedRepository applied;
    @Autowired WorkoutSessionRepository sessions;
    @Autowired ExerciseRepository exercises;
    @Autowired CustomExerciseRepository customs;
    @Autowired WorkoutRepository workouts;

    private Schedule plan(String owner) {
        Schedule plan=new Schedule(); plan.setUser(users.findByUsername(owner)); plan.setName("Saved <plan>");
        plan.setDescription("Original description"); plan.setScheduleType(ScheduleType.CUSTOM); plan.setCustomDayCount(10);
        plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION); return schedules.save(plan);
    }
    private ScheduleEntry entry(Schedule plan,int day,int order) {
        var row=new ScheduleEntry(); row.setSchedule(plan); row.setDayOfWeek(day); row.setOrderNumber(order);
        row.setExercise(exercises.findAll().getFirst()); return entries.saveAndFlush(row);
    }
    private CustomExercise custom(String owner) {
        var movement=new CustomExercise(); movement.setUserId(users.findByUsername(owner).getId());
        movement.setName("Private <movement>"); return customs.save(movement);
    }
    private LinkedMultiValueMap<String,String> form(Schedule plan) {
        var draft=studio.load(plan); var fields=new LinkedMultiValueMap<String,String>();
        fields.add("name",draft.name()); fields.add("description",draft.description()); fields.add("revision",draft.revision());
        fields.add("scheduleType",draft.type().name()); fields.add("rotationMode",draft.rotation().name());
        fields.add("customDayCount",String.valueOf(draft.dayCount()));
        for(var row:draft.movements()) {fields.add("entryIds",String.valueOf(row.entryId()));fields.add("days",String.valueOf(row.day()));fields.add("movementKeys",row.key());}
        return fields;
    }
    private org.jsoup.nodes.Document render(Schedule plan,LinkedMultiValueMap<String,String> fields,String command) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(post("/schedules/"+plan.getId()+"/entries/save").params(fields)
                .param("editAction",command).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private org.jsoup.nodes.Document page(Schedule plan,String language) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(get("/schedules/"+plan.getId()+"/entries").param("lang",language)
                .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void allLocalesRenderEveryCustomDayOwnedCustomNamesLabelledFormsAndRealDeploymentStatus() throws Exception {
        var plan=plan("demo"); entry(plan,8,0); var owned=custom("demo"); var row=entry(plan,8,1);
        row.setExercise(null); row.setCustomExercise(owned); entries.saveAndFlush(row);
        for(String language:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc=page(plan,language); assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.select(".schedule-detail").text()).doesNotContain("ui.scheduleDetail.","Today at","Custom Exercise");
            assertThat(doc.select(".schedule-placement h3").eachText()).contains(owned.getName());
            assertThat(doc.select("[data-preview-day]")).hasSize(10);
            assertThat(doc.select("[data-preview-day=8] [data-day-count]").text()).isEqualTo("2");
            assertThat(doc.select("[data-preview-day=2] [data-day-count]").text()).isEqualTo("0");
            assertThat(doc.selectFirst("[data-detail-total]").attr("data-template")).contains("COUNTPLACEHOLDER");
            assertThat(doc.select("select[name=days] option[selected]").eachAttr("value")).containsExactly("8","8");
            assertThat(doc.select("#schedule-detail-form input[name=_csrf]")).hasSize(1);
            for(var field:doc.select(".schedule-detail input:not([type=hidden]), .schedule-detail textarea, .schedule-detail select"))
                assertThat(doc.select("label[for="+field.id()+"]")).hasSize(1);
        }
        assertThat(page(plan,"en").selectFirst(".schedule-detail__status").text()).isEqualTo("Not applied");
        var window=new ScheduleApplied(); window.setUser(users.findByUsername("demo")); window.setSchedule(plan);
        window.setDateApplied(LocalDate.now()); window.setDurationWeeks(1); window.setShownOnCalendar(false); applied.saveAndFlush(window);
        assertThat(page(plan,"en").selectFirst(".schedule-detail__status").text()).isEqualTo("Hidden");
        window.setShownOnCalendar(true); applied.saveAndFlush(window);
        assertThat(page(plan,"en").selectFirst(".schedule-detail__status").text()).isEqualTo("Applied");
    }

    @Test void nativeSaveKeepsEntryIdsAndCompletedOccurrencesAndSessionsWhileSavingActualOrderMetadataAndCustomMovements() throws Exception {
        var plan=plan("demo"); var first=entry(plan,8,0); var second=entry(plan,8,1); var owned=custom("demo");
        var date=LocalDate.now(); var occurrence=new ScheduleOccurrence(); occurrence.setUser(plan.getUser()); occurrence.setSchedule(plan);
        occurrence.setScheduleName(plan.getName()); occurrence.setExercise(first.getExercise()); occurrence.setDate(date); occurrence.setCompleted(true);
        occurrences.saveAndFlush(occurrence);
        var session=new WorkoutSession(); session.setUser(plan.getUser()); session.setSchedule(plan); session.setDate(date);
        session.setCreatedAt(java.time.LocalDateTime.now()); session.setCompleted(true); sessions.saveAndFlush(session);
        var fields=form(plan); fields.set("name","  Updated <plan>  "); fields.set("description"," Retain <description> ");
        fields.put("entryIds",List.of(second.getId().toString(),first.getId().toString()));
        fields.put("days",List.of("10","10")); fields.put("movementKeys",List.of("c:"+owned.getId(),"e:"+first.getExercise().getId()));
        mvc.perform(post("/schedules/"+plan.getId()+"/entries/save").params(fields).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules/"+plan.getId()+"/entries?saved"));
        var saved=entries.findBySchedule(plan).stream().sorted(java.util.Comparator.comparingInt(ScheduleEntry::getOrderNumber)).toList();
        assertThat(saved).extracting(ScheduleEntry::getId).containsExactly(second.getId(),first.getId());
        assertThat(saved).extracting(ScheduleEntry::getDayOfWeek).containsOnly(10);
        assertThat(saved.getFirst().getCustomExercise()).isEqualTo(owned); assertThat(saved.getFirst().getExercise()).isNull();
        assertThat(schedules.findById(plan.getId()).orElseThrow().getName()).isEqualTo("Updated <plan>");
        assertThat(occurrences.findById(occurrence.getId()).orElseThrow().isCompleted()).isTrue();
        assertThat(occurrences.findById(occurrence.getId()).orElseThrow().getScheduleName()).isEqualTo("Saved <plan>");
        assertThat(sessions.findById(session.getId()).orElseThrow().isCompleted()).isTrue();
    }

    @Test void nativeCommandsAreDraftOnlyAndClearResetRequireExplicitConfirmation() throws Exception {
        var plan=plan("demo"); var first=entry(plan,8,0); var second=entry(plan,8,1); var fields=form(plan);
        fields.set("name","Unsaved name"); fields.set("addDay","2"); fields.set("addMovement","e:"+first.getExercise().getId());
        assertThat(render(plan,fields,"add").select(".schedule-placement")).hasSize(3);
        assertThat(render(plan,fields,"up:1").select("input[name=entryIds]").eachAttr("value"))
                .containsExactly(second.getId().toString(),first.getId().toString());
        assertThat(render(plan,fields,"forward").select("select[name=days] option[selected]").eachAttr("value")).containsExactly("9","9");
        assertThat(render(plan,fields,"backward").select("select[name=days] option[selected]").eachAttr("value")).containsExactly("7","7");
        assertThat(render(plan,fields,"spread").select("select[name=days] option[selected]").eachAttr("value")).containsExactly("1","2");
        assertThat(render(plan,fields,"clear").select(".schedule-placement")).hasSize(2);
        assertThat(render(plan,fields,"confirmClear").select(".schedule-placement")).isEmpty();
        assertThat(render(plan,fields,"reset").selectFirst("#detail-name").val()).isEqualTo("Unsaved name");
        assertThat(render(plan,fields,"confirmReset").selectFirst("#detail-name").val()).isEqualTo("Saved <plan>");
        fields.set("scheduleType","DAILY"); assertThat(render(plan,fields,"refresh").select(".schedule-placement")).hasSize(2);
        assertThat(plan.getName()).isEqualTo("Saved <plan>"); assertThat(entries.findBySchedule(plan)).hasSize(2);
    }

    @Test void foreignReferencesDuplicateIdsAndOverlongMetadataRejectBeforeMutationAndCsrfIsRequired() throws Exception {
        var own=plan("demo"); var first=entry(own,8,0); var second=entry(own,8,1);
        var foreign=plan("trainer_demo"); var privateEntry=entry(foreign,1,0); var privateMovement=custom("trainer_demo");
        for(String invalid:List.of("foreignEntry","privateMovement","duplicates","name","description","outside")) {
            var fields=form(own); fields.set("name","Rejected draft");
            switch(invalid) {
                case "foreignEntry" -> fields.put("entryIds",List.of(privateEntry.getId().toString(),second.getId().toString()));
                case "privateMovement" -> fields.put("movementKeys",List.of("c:"+privateMovement.getId(),"e:"+second.getExercise().getId()));
                case "duplicates" -> fields.put("entryIds",List.of(first.getId().toString(),first.getId().toString()));
                case "name" -> fields.set("name","x".repeat(201));
                case "description" -> fields.set("description","x".repeat(501));
                default -> fields.put("days",List.of("11","8"));
            }
            assertThat(render(own,fields,"save").select("[role=alert]")).isNotEmpty();
            assertThat(own.getName()).isEqualTo("Saved <plan>");
            assertThat(entries.findBySchedule(own)).extracting(ScheduleEntry::getDayOfWeek).containsOnly(8);
        }
        mvc.perform(post("/schedules/"+own.getId()+"/entries/save").params(form(own)).with(user("demo").roles("CLIENT")))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/schedules/"+foreign.getId()+"/entries/save").params(form(foreign)).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/schedules?error"));
        assertThat(privateEntry.getSchedule().getId()).isEqualTo(foreign.getId());
    }

    @Test void staleDraftIsRetainedAndCannotOverwriteANewerSavedPlan() throws Exception {
        var plan=plan("demo"); entry(plan,8,0); var fields=form(plan); fields.set("name","Old draft name");
        plan.setName("New saved name"); schedules.save(plan);
        var doc=render(plan,fields,"save");
        assertThat(doc.selectFirst("#detail-name").val()).isEqualTo("Old draft name");
        assertThat(doc.select("[role=alert]").text()).contains("changed elsewhere");
        assertThat(plan.getName()).isEqualTo("New saved name");
        assertThat(render(plan,fields,"confirmReset").selectFirst("#detail-name").val()).isEqualTo("New saved name");
    }

    @Test void legacyAppendCannotBindForeignEntryAndLegacyCreateCannotStealAnExistingSchedule() throws Exception {
        var own=plan("demo"); var foreign=plan("trainer_demo"); var foreignEntry=entry(foreign,1,0);
        mvc.perform(get("/schedules/create").with(user("demo").roles("CLIENT"))).andExpect(redirectedUrl("/schedules/builder"));
        mvc.perform(post("/schedules/"+own.getId()+"/entries").with(user("demo").roles("CLIENT")).with(csrf())
                .param("id",foreignEntry.getId().toString()).param("dayOfWeek","2").param("exercise.id",foreignEntry.getExercise().getId().toString()))
                .andExpect(redirectedUrl("/schedules/"+own.getId()+"/entries?error"));
        assertThat(entries.findBySchedule(own)).isEmpty(); assertThat(foreignEntry.getSchedule()).isEqualTo(foreign);
        mvc.perform(post("/schedules/"+own.getId()+"/entries").with(user("demo").roles("CLIENT")).with(csrf())
                .param("dayOfWeek","8").param("exercise.id",foreignEntry.getExercise().getId().toString()))
                .andExpect(redirectedUrl("/schedules/"+own.getId()+"/entries"));
        assertThat(entries.findBySchedule(own)).hasSize(1);
        var result=mvc.perform(post("/schedules/create").with(user("demo").roles("CLIENT")).with(csrf())
                .param("id",foreign.getId().toString()).param("user.id",foreign.getUser().getId().toString()).param("name","New owned plan"))
                .andExpect(status().is3xxRedirection()).andReturn();
        Long created=Long.valueOf(result.getResponse().getRedirectedUrl().split("/")[2]);
        assertThat(created).isNotEqualTo(foreign.getId());
        assertThat(schedules.findById(created).orElseThrow().getUser().getId()).isEqualTo(users.findByUsername("demo").getId());
        assertThat(foreign.getName()).isEqualTo("Saved <plan>");
    }

    @Test void composerSaveReopensEveryCustomMovementAndNativeApplyUsesTheCycleWithoutDuplicates() throws Exception {
        var workout=new Workout(); workout.setUserId(users.findByUsername("demo").getId()); workout.setName("Source workout");
        workout.setExercises(new ArrayList<>(List.of(exercises.findAll().getFirst()))); workout.setCustomExercises(new ArrayList<>(List.of(custom("demo"))));
        workouts.saveAndFlush(workout);
        var result=mvc.perform(post("/schedules/builder/save").with(user("demo").roles("CLIENT")).with(csrf()).param("composerForm","true")
                .param("name","Full cycle").param("scheduleType","CUSTOM").param("rotationMode","CONTINUOUS_ROTATION").param("customDayCount","10")
                .param("days","8").param("workoutIds",workout.getId().toString())).andExpect(status().is3xxRedirection()).andReturn();
        var plan=schedules.findById(Long.valueOf(result.getResponse().getRedirectedUrl().split("/")[2])).orElseThrow();
        assertThat(page(plan,"en").select(".schedule-placement")).hasSize(2);
        mvc.perform(get("/api/schedules/"+plan.getId()+"/metadata").with(user("demo").roles("CLIENT")))
                .andExpect(jsonPath("$.cycleDayCount").value(10)).andExpect(jsonPath("$.activeDayIndexes[0]").value(8))
                .andExpect(jsonPath("$.activeDayLabels[0]").value("Day 8"));
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post("/schedules/"+plan.getId()+"/apply").with(user("demo").roles("CLIENT")).with(csrf())
                .param("startDate","2027-01-04").param("weeks","4")).andExpect(redirectedUrl("/calendar"));
        Long savedPlanId=plan.getId(); entityManager.flush(); entityManager.clear();
        plan=schedules.findById(savedPlanId).orElseThrow();
        var dates=occurrences.findByUserAndDateBetween(plan.getUser(),LocalDate.of(2027,1,4),LocalDate.of(2027,1,31)).stream()
                .filter(item->item.getSchedule().getId().equals(savedPlanId)).map(ScheduleOccurrence::getDate).sorted().toList();
        assertThat(dates).containsExactly(LocalDate.of(2027,1,11),LocalDate.of(2027,1,11),LocalDate.of(2027,1,21),
                LocalDate.of(2027,1,21),LocalDate.of(2027,1,31),LocalDate.of(2027,1,31));
        assertThat(applied.findByUserAndSchedule(plan.getUser(),plan)).hasSize(1);
    }
}
