package uk.ac.cf._5.group14.One_To_One.Web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Workout.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Native form persistence, ownership and actual Thymeleaf rendering for the schedule builder. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleWorkoutBuilderIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private WorkoutRepository workouts;
    @Autowired private WorkoutService service;
    @Autowired private ExerciseRepository exercises;
    @Autowired private CustomExerciseRepository customs;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private jakarta.persistence.EntityManager entityManager;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private CustomExercise custom(String owner) {
        CustomExercise item = new CustomExercise(); item.setUserId(users.findByUsername(owner).getId());
        item.setName("Private <custom> movement"); item.setDescription("Description & control");
        return customs.save(item);
    }
    private Workout workout(String owner) {
        Workout item = new Workout(); item.setUserId(users.findByUsername(owner).getId());
        item.setName("Original workout"); item.setNotes("Retain <notes>");
        item.setExercises(new java.util.ArrayList<>(List.of(exercises.findAll().getFirst())));
        item.setCustomExercises(new java.util.ArrayList<>());
        return workouts.save(item);
    }
    private org.jsoup.nodes.Document document(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return org.jsoup.Jsoup.parse(result.getResponse().getContentAsString());
    }

    @Test
    void builderAndBothFragmentEndpointsRenderWithUniqueLabelledFieldsInEveryLocale() throws Exception {
        Workout saved = workout("demo");
        for (String lang : List.of("en","ar","cy","de","es","fr","hi","it","ja","ko","nl","pl","pt","zh")) {
            var doc = document(mvc.perform(get("/workout").param("edit", saved.getId().toString()).param("lang", lang)
                    .with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn());
            assertThat(doc.select("#workout-draft-form")).hasSize(1);
            assertThat(doc.select("[data-schedule-search]")).hasSize(3);
            assertThat(doc.select("[id]").eachAttr("id")).doesNotHaveDuplicates();
            assertThat(doc.selectFirst("#workout-name").val()).isEqualTo(saved.getName());
            assertThat(doc.selectFirst("#workout-notes").val()).isEqualTo(saved.getNotes());
            assertThat(doc.selectFirst(".schedule-workshop__hint").text()).doesNotContain("ui.scheduleWorkout.");
            for (var input : doc.select(".schedule-workshop input:not([type=hidden]), .schedule-workshop textarea, .schedule-workshop select")) {
                assertThat(doc.select("label[for=" + input.id() + "]")).hasSize(1);
            }
        }
        for (String path : List.of("/workout/create", "/workout/edit/" + saved.getId())) {
            var doc = document(mvc.perform(get(path).with(user("demo").roles("CLIENT"))).andExpect(status().isOk()).andReturn());
            assertThat(doc.select("#workout-draft-form")).hasSize(1);
        }
    }

    @Test
    void nativeAddAndRemoveKeepDraftAndSearchWithoutSavingToTheDatabase() throws Exception {
        String id = exercises.findAll().getFirst().getId().toString();
        long before = workouts.count();
        var doc = document(mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("name", "Retain draft")
                .param("workoutNotes", "Keep <these> notes").param("exercise-search-input", "control")
                .param("editAction", "add:e:" + id)).andExpect(status().isOk()).andReturn());
        assertThat(workouts.count()).isEqualTo(before);
        assertThat(doc.selectFirst("#workout-name").val()).isEqualTo("Retain draft");
        assertThat(doc.selectFirst("#workout-notes").val()).isEqualTo("Keep <these> notes");
        assertThat(doc.selectFirst("#exercise-search-input").val()).isEqualTo("control");
        assertThat(doc.select("input[name=exerciseIds]").eachAttr("value")).containsExactly(id);
        var removed = document(mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("name", "Retain draft")
                .param("exerciseIds", id).param("editAction", "remove:e:" + id)).andExpect(status().isOk()).andReturn());
        assertThat(removed.select("input[name=exerciseIds]")).isEmpty();
        assertThat(workouts.count()).isEqualTo(before);
    }

    @Test
    void nativeSavePersistsBothKindsOfOwnedExerciseAndReturnsAnEditablePlan() throws Exception {
        var own = custom("demo"); var standard = exercises.findAll().getFirst();
        var result = mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("name", "  Saved <plan>  ")
                .param("workoutNotes", "Literal <notes> & control").param("exerciseIds", standard.getId().toString())
                .param("customExerciseIds", own.getId().toString())).andExpect(status().is3xxRedirection()).andReturn();
        Workout saved = workouts.findByUserId(users.findByUsername("demo").getId()).stream()
                .filter(item -> item.getName().equals("Saved <plan>")).findFirst().orElseThrow();
        assertThat(saved.getExercises()).containsExactly(standard); assertThat(saved.getCustomExercises()).containsExactly(own);
        assertThat(saved.getNotes()).isEqualTo("Literal <notes> & control");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/workout?edit=" + saved.getId());
        mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).param("name", "Without CSRF").param("exerciseIds", standard.getId().toString()))
                .andExpect(status().is4xxClientError());
        assertThat(workouts.findByUserId(users.findByUsername("demo").getId()))
                .noneMatch(item -> item.getName().equals("Without CSRF"));
    }

    @Test
    void privateWorkoutCannotBeReadEditedOrDeletedByAnotherUser() throws Exception {
        Workout foreign = workout("trainer_demo");
        mvc.perform(get("/workout").param("edit",foreign.getId().toString()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/workout/edit/" + foreign.getId()).with(user("demo").roles("CLIENT")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_JSON).with(user("demo").roles("CLIENT")).with(csrf())
                .content(json.writeValueAsString(Map.of("id",foreign.getId(),"name","Stolen workout","exerciseIds",List.of(exercises.findAll().getFirst().getId())))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/delete-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("id",foreign.getId().toString()))
                .andExpect(status().isNotFound());
        assertThat(foreign.getUserId()).isEqualTo(users.findByUsername("trainer_demo").getId());
        assertThat(foreign.getName()).isEqualTo("Original workout"); assertThat(workouts.existsById(foreign.getId())).isTrue();
    }

    @Test
    void rejectedCustomReferencesAndDuplicateSelectionsLeaveSavedWorkoutUnchanged() throws Exception {
        Workout own = workout("demo"); var foreign = custom("trainer_demo");
        var doc = document(mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("id",own.getId().toString())
                .param("name","Rejected draft").param("workoutNotes","Retained draft notes")
                .param("customExerciseIds",foreign.getId().toString())).andExpect(status().isOk()).andReturn());
        assertThat(doc.selectFirst("#workout-name").val()).isEqualTo("Rejected draft");
        assertThat(doc.selectFirst("#workout-notes").val()).isEqualTo("Retained draft notes");
        assertThat(doc.selectFirst(".schedule-workshop__notice--error")).isNotNull();
        assertThat(doc.select(".schedule-movement")).isEmpty();
        assertThat(own.getName()).isEqualTo("Original workout"); assertThat(own.getNotes()).isEqualTo("Retain <notes>");
        String id = own.getExercises().getFirst().getId().toString();
        mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_JSON).with(user("demo").roles("CLIENT")).with(csrf())
                .content(json.writeValueAsString(Map.of("id",own.getId(),"name","Duplicates","exerciseIds",List.of(Long.valueOf(id),Long.valueOf(id))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.messageKey").value("ui.scheduleWorkout.invalid"));
        assertThat(own.getName()).isEqualTo("Original workout");
    }

    @Test
    void nativeCustomFormSavesUpdatesAndRejectsInvalidVideoWithoutLosingEntries() throws Exception {
        mvc.perform(post("/workout/custom-exercises/save").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("name","Native custom")
                .param("description","Native <description>")).andExpect(redirectedUrl("/workout"));
        CustomExercise saved = customs.findByUserIdOrderByNameAsc(users.findByUsername("demo").getId()).stream()
                .filter(item -> item.getName().equals("Native custom")).findFirst().orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("Native <description>");
        var doc = document(mvc.perform(post("/workout/custom-exercises/save").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("id",saved.getId().toString())
                .param("name","Retained invalid custom").param("videoUrl","javascript:alert(1)"))
                .andExpect(status().isOk()).andReturn());
        assertThat(doc.selectFirst("#custom-exercise-name").val()).isEqualTo("Retained invalid custom");
        assertThat(doc.selectFirst("#custom-exercise-video").val()).isEqualTo("javascript:alert(1)");
        assertThat(saved.getName()).isEqualTo("Native custom");
        mvc.perform(post("/workout/custom-exercises/save").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("id",saved.getId().toString())
                .param("name","Updated custom")).andExpect(redirectedUrl("/workout"));
        assertThat(saved.getName()).isEqualTo("Updated custom");
    }

    @Test
    void catalogueQueriesExcludeBannedTagsAndDoNotDuplicateMultiplePreferenceMatches() {
        var first = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Tag(); first.setName("Workshop preferred one"); first.setCategory("test");
        var second = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Tag(); second.setName("Workshop preferred two"); second.setCategory("test");
        var banned = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Tag(); banned.setName("Workshop excluded"); banned.setCategory("test");
        for (var tag : List.of(first,second,banned)) entityManager.persist(tag);
        var allowed = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        allowed.setName("Workshop permitted"); allowed.setCategory("test"); allowed.setType("test"); allowed.setDifficulty(1);
        allowed.getTags().addAll(List.of(first,second)); exercises.save(allowed);
        var blocked = new uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise();
        blocked.setName("Workshop excluded movement"); blocked.setCategory("test"); blocked.setType("test"); blocked.setDifficulty(1);
        blocked.getTags().addAll(List.of(first,banned)); exercises.save(blocked); entityManager.flush();
        assertThat(exercises.getExercisesExcludingTags(java.util.Set.of(banned))).contains(allowed).doesNotContain(blocked);
        assertThat(exercises.getFilteredSuggestedExercises(java.util.Set.of(first,second),java.util.Set.of(banned))).containsExactly(allowed);
    }

    @Test
    void legacyJsonSaveReturnsTheRealSavedIdAndSupportsOwnedDelete() throws Exception {
        var standard = exercises.findAll().getFirst();
        var response = mvc.perform(post("/save-workout").contentType(MediaType.APPLICATION_JSON)
                .with(user("demo").roles("CLIENT")).with(csrf())
                .content(json.writeValueAsString(Map.of("name","JSON compatible workout","exerciseIds",List.of(standard.getId())))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Workout saved successfully"))
                .andReturn();
        Long id = json.readTree(response.getResponse().getContentAsString()).path("id").asLong();
        assertThat(workouts.findByIdAndUserId(id,users.findByUsername("demo").getId())).isPresent();
        mvc.perform(post("/delete-workout").contentType(MediaType.APPLICATION_JSON)
                .with(user("demo").roles("CLIENT")).with(csrf()).content(json.writeValueAsString(Map.of("id",id))))
                .andExpect(status().isOk());
        assertThat(workouts.existsById(id)).isFalse();
    }

    @Test
    void referencedCustomExerciseIsRejectedBeforeAnySavedRelationshipIsRemoved() throws Exception {
        CustomExercise used = custom("demo"); Workout saved = workout("demo");
        saved.setCustomExercises(new java.util.ArrayList<>(List.of(used))); workouts.saveAndFlush(saved);
        mvc.perform(post("/workout/custom-exercises/" + used.getId() + "/delete")
                .with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.messageKey").value("ui.scheduleWorkout.customBlocked"));
        assertThat(customs.existsById(used.getId())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from workouts_custom_exercises where workout_id = ? and custom_exercise_id = ?",
                Integer.class,saved.getId(),used.getId())).isEqualTo(1);
        mvc.perform(post("/workout/custom-exercises/" + used.getId() + "/remove")
                .with(user("demo").roles("CLIENT")).with(csrf()))
                .andExpect(redirectedUrl("/workout")).andExpect(flash().attribute("workoutNotice","ui.scheduleWorkout.customBlocked"));
        assertThat(customs.existsById(used.getId())).isTrue();
    }

    @Test
    void workoutUsedByScheduleIsRejectedBeforeCascadingDeletion() throws Exception {
        Workout saved = workout("demo");
        jdbc.update("insert into workout_schedule (user_id,day_of_week,workout_id,order_index) values (?,?,?,?)",
                saved.getUserId(),1,saved.getId(),0);
        mvc.perform(post("/delete-workout").contentType(MediaType.APPLICATION_JSON)
                .with(user("demo").roles("CLIENT")).with(csrf()).content(json.writeValueAsString(Map.of("id",saved.getId()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.messageKey").value("ui.scheduleWorkout.deleteBlocked"));
        mvc.perform(post("/delete-workout").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .with(user("demo").roles("CLIENT")).with(csrf()).param("id",saved.getId().toString()))
                .andExpect(redirectedUrl("/workout")).andExpect(flash().attribute("workoutNotice","ui.scheduleWorkout.deleteBlocked"));
        assertThat(workouts.existsById(saved.getId())).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from workout_schedule where workout_id = ?",Integer.class,saved.getId())).isEqualTo(1);
    }
}
