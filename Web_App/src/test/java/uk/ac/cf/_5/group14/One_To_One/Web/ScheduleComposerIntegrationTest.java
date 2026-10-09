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
import uk.ac.cf._5.group14.One_To_One.ExerciseData.*;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
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
class ScheduleComposerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired WorkoutRepository workouts;
    @Autowired ExerciseRepository exercises;
    @Autowired CustomExerciseRepository customs;
    @Autowired ScheduleRepository schedules;
    @Autowired ScheduleEntryRepository entries;
    @Autowired ScheduleComposerService composer;
    @Autowired ScheduleDeploymentPlanner planner;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Workout workout(String owner) {
        Workout workout=new Workout(); workout.setUserId(users.findByUsername(owner).getId());
        workout.setName("Workout <literal>"); workout.setExercises(new ArrayList<>(List.of(exercises.findAll().getLast())));
        workout.setCustomExercises(new ArrayList<>()); return workouts.saveAndFlush(workout);
    }
    private CustomExercise custom(String owner) {
        CustomExercise movement=new CustomExercise(); movement.setUserId(users.findByUsername(owner).getId());
        movement.setName("Custom <movement>"); return customs.save(movement);
    }
    private LinkedMultiValueMap<String,String> form(String id) {
        var fields=new LinkedMultiValueMap<String,String>(); fields.add("composerForm","true"); fields.add("name","Retain <draft>");
        fields.add("scheduleType","CUSTOM"); fields.add("rotationMode","CONTINUOUS_ROTATION"); fields.add("customDayCount","10");
        fields.add("days","8"); fields.add("workoutIds",id); return fields;
    }
    private org.jsoup.nodes.Document render(LinkedMultiValueMap<String,String> fields,String command,String lang) throws Exception {
        return org.jsoup.Jsoup.parse(mvc.perform(post("/schedules/builder/save").params(fields).param("editAction",command).param("lang",lang)
                .with(user("demo").roles("CLIENT")).with(csrf())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test void everyLocaleRendersLabelledNativeCommandsAndRetainsDraftOnRefresh() throws Exception {
        var workout=workout("demo"); long before=schedules.count();
        for(String lang:List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc=render(form(workout.getId().toString()),"refresh",lang);
            assertThat(doc.select(".schedule-composer h1")).hasSize(1);
            assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.select(".schedule-composer").text()).doesNotContain("ui.composer.","day.8");
            assertThat(doc.selectFirst("#composer-name").val()).isEqualTo("Retain <draft>");
            assertThat(doc.selectFirst("#composer-cycle").val()).isEqualTo("10");
            assertThat(doc.select("select[name=days] option[selected]").eachAttr("value")).containsExactly("8");
            assertThat(doc.select("#schedule-composer-form input[name=_csrf]")).hasSize(1);
            assertThat(doc.select("#schedule-composer-form select[name=workoutIds] option[selected]").eachAttr("value"))
                    .containsExactly(workout.getId().toString());
            for(var field:doc.select(".schedule-composer input:not([type=hidden]), .schedule-composer select"))
                assertThat(doc.select("label[for="+field.id()+"]")).hasSize(1);
        }
        assertThat(schedules.count()).isEqualTo(before);
    }

    @Test void nativeCommandsRetainNameRowsAndSettingsWithoutSavingOrDroppingOutsideCycleRows() throws Exception {
        var first=workout("demo"); var second=workout("demo"); var fields=form(first.getId().toString());
        fields.add("addDay","2"); fields.add("addWorkout",second.getId().toString()); long before=schedules.count();
        var added=render(fields,"add","en"); assertThat(added.select(".schedule-placement")).hasSize(2);
        fields.add("days","2"); fields.add("workoutIds",second.getId().toString());
        var reordered=render(fields,"up:1","en");
        assertThat(reordered.select("select[name=workoutIds] option[selected]").eachAttr("value"))
                .containsExactly(second.getId().toString(),first.getId().toString());
        assertThat(render(fields,"remove:0","en").select("select[name=workoutIds] option[selected]").eachAttr("value"))
                .containsExactly(second.getId().toString());
        fields.set("scheduleType","DAILY");
        var resized=render(fields,"refresh","en"); assertThat(resized.select(".schedule-placement")).hasSize(2);
        assertThat(resized.select(".schedule-placement .schedule-composer__notice--error")).hasSize(2);
        fields.set("templateId","full-body-3day");
        assertThat(render(fields,"template","en").select(".schedule-placement")).hasSize(2);
        assertThat(schedules.count()).isEqualTo(before);
    }

    @Test void savesActualWorkoutMovementsDespiteExerciseIdCollisionAndImportsCustomOnlyWorkouts() throws Exception {
        var workout=workout("demo"); var ownCustom=custom("demo"); workout.getCustomExercises().add(ownCustom); workouts.saveAndFlush(workout);
        // Create a catalogue ID collision without changing any workout foreign keys.
        if(!exercises.existsById(workout.getId())) jdbc.update("insert into exercises(id,name,category,difficulty,type) values(?,?,?,?,?)",
                workout.getId(),"Wrong ID-collision movement","Strength",1,"Strength");
        var selected=exercises.findAll().stream().filter(movement->!movement.getId().equals(workout.getId())).findFirst().orElseThrow();
        workout.setExercises(new ArrayList<>(List.of(selected))); workouts.saveAndFlush(workout);
        assertThat(exercises.findById(workout.getId())).isPresent();
        var fields=form(workout.getId().toString());
        var result=mvc.perform(post("/schedules/builder/save").params(fields).with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        Long id=Long.valueOf(result.getResponse().getRedirectedUrl().split("/")[2]); var saved=schedules.findById(id).orElseThrow();
        var imported=entries.findBySchedule(saved).stream().sorted(java.util.Comparator.comparingInt(ScheduleEntry::getOrderNumber)).toList();
        assertThat(saved.getName()).isEqualTo("Retain <draft>"); assertThat(saved.getCustomDayCount()).isEqualTo(10);
        assertThat(imported).hasSize(2); assertThat(imported.getFirst().getExercise()).isEqualTo(workout.getExercises().getFirst());
        assertThat(imported.getLast().getCustomExercise()).isEqualTo(ownCustom);
        assertThat(imported).extracting(ScheduleEntry::getDayOfWeek).containsOnly(8);
        assertThat(imported).extracting(ScheduleEntry::getOrderNumber).containsExactly(0,1);
        var customOnly=workout("demo"); customOnly.getExercises().clear(); customOnly.getCustomExercises().add(ownCustom); workouts.saveAndFlush(customOnly);
        var copy=composer.save(composer.read(form(customOnly.getId().toString())),users.findByUsername("demo"));
        assertThat(entries.findBySchedule(copy)).hasSize(1).allSatisfy(row->{assertThat(row.getExercise()).isNull(); assertThat(row.getCustomExercise()).isEqualTo(ownCustom);});
    }

    @Test void foreignMissingEmptyAndInvalidWorkoutsDoNotPartiallySaveAndNativeCsrfIsEnforced() throws Exception {
        var own=workout("demo"); var foreign=workout("trainer_demo"); var empty=workout("demo");
        empty.getExercises().clear(); workouts.saveAndFlush(empty);
        long before=schedules.count(); long entryCount=entries.count();
        for(String invalid:List.of(foreign.getId().toString(),"9223372036854775807",empty.getId().toString())) {
            var fields=form(own.getId().toString()); fields.add("days","1"); fields.add("workoutIds",invalid);
            var doc=render(fields,"save","en"); assertThat(doc.select("[role=alert]")).isNotEmpty();
            assertThat(doc.selectFirst("#composer-name").val()).isEqualTo("Retain <draft>");
        }
        var privateMovement=custom("trainer_demo"); privateMovement.setName("Private movement must stay private"); customs.save(privateMovement);
        own.getCustomExercises().add(privateMovement); workouts.saveAndFlush(own);
        assertThat(render(form(own.getId().toString()),"save","en").select("[role=alert]")).isNotEmpty();
        assertThat(render(form(own.getId().toString()),"refresh","en").select(".schedule-composer").text())
                .doesNotContain("Private movement must stay private");
        mvc.perform(post("/schedules/builder/save").params(form(own.getId().toString())).with(user("demo").roles("CLIENT")))
                .andExpect(status().is4xxClientError());
        assertThat(schedules.count()).isEqualTo(before); assertThat(entries.count()).isEqualTo(entryCount);
    }

    @Test void legacyDayLabelsAreValidatedAndDuplicateJsonKeysRejected() {
        var workout=workout("demo"); var fields=form(workout.getId().toString()); fields.remove("composerForm");
        fields.set("payload","{\"Day 8\":["+workout.getId()+"]}");
        assertThat(composer.read(fields).placements()).containsExactly(new ScheduleComposerService.Placement(8,workout.getId()));
        for(String payload:List.of("{\"nonsense\":[1]}","{\"Mon\":[1],\"Mon\":[2]}","{\"Day 15\":[1]}","{\"Mon\":[1.5]}")) {
            fields.set("payload",payload); assertThatThrownBy(()->composer.read(fields)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void placementAndMovementLimitsRejectBeforeAnyScheduleOrEntriesAreWritten() {
        var workout=workout("demo"); var movements=new ArrayList<>(exercises.findAll());
        while(movements.size()<6) {
            var movement=new Exercise(); movement.setName("Bounded import "+movements.size()); movement.setCategory("Strength");
            movement.setDifficulty(1); movement.setType("Strength"); movements.add(exercises.saveAndFlush(movement));
        }
        workout.setExercises(new ArrayList<>(movements.subList(0,6))); workouts.saveAndFlush(workout);
        var fields=form(workout.getId().toString()); fields.set("name","Bounded import");
        for(int index=1;index<84;index++) { fields.add("days","1"); fields.add("workoutIds",workout.getId().toString()); }
        long before=schedules.count(); long entryCount=entries.count();
        assertThatThrownBy(()->composer.save(composer.read(fields),users.findByUsername("demo"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(schedules.count()).isEqualTo(before); assertThat(entries.count()).isEqualTo(entryCount);
        for(int index=84;index<101;index++) { fields.add("days","1"); fields.add("workoutIds",workout.getId().toString()); }
        assertThatThrownBy(()->composer.read(fields)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void dailyCustomAndSingleCyclePlansUseRealCycleDatesAndExplicitCalendarIntervalsStillApply() {
        var plan=new Schedule(); plan.setScheduleType(ScheduleType.DAILY); plan.setCustomDayCount(1);
        var row=new ScheduleEntry(); row.setSchedule(plan); row.setDayOfWeek(1); row.setExercise(exercises.findAll().getFirst());
        var window=new ScheduleDeploymentPlanner.Window(LocalDate.of(2027,1,4),LocalDate.of(2027,1,31),4,"weekly",1,"week");
        assertThat(planner.plan(window,List.of(row))).hasSize(28);
        plan.setScheduleType(ScheduleType.CUSTOM); plan.setCustomDayCount(10); plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION); row.setDayOfWeek(8);
        assertThat(planner.plan(window,List.of(row))).extracting(ScheduleDeploymentPlanner.PlannedOccurrence::date)
                .containsExactly(LocalDate.of(2027,1,11),LocalDate.of(2027,1,21),LocalDate.of(2027,1,31));
        plan.setRotationMode(RotationMode.WEEKLY_REPEAT);
        assertThat(planner.plan(window,List.of(row))).extracting(ScheduleDeploymentPlanner.PlannedOccurrence::date)
                .containsExactly(LocalDate.of(2027,1,11),LocalDate.of(2027,1,25));
        plan.setRotationMode(RotationMode.NONE); assertThat(planner.plan(window,List.of(row))).hasSize(1);
        plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION);
        var explicit=new ScheduleDeploymentPlanner.Window(window.start(),window.end(),4,"custom",2,"week");
        assertThat(planner.plan(explicit,List.of(row))).extracting(ScheduleDeploymentPlanner.PlannedOccurrence::date)
                .containsExactly(LocalDate.of(2027,1,11),LocalDate.of(2027,1,25));
    }
}
