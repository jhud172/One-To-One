package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.Security.TrainerAccessException;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrainerLibrarySecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TrainerClientLinkRepository linkRepository;

    @Autowired
    private TrainerLibraryService trainerLibraryService;

    @Autowired
    private TrainerLibrarySharedTemplateRepository sharedTemplateRepository;

    @Autowired
    private TrainerLibraryProgrammeDayRepository programmeDayRepository;

    @Autowired
    private TrainerLibraryProgrammeNoteRepository programmeNoteRepository;

    @Autowired
    private TrainerLibraryProgrammeTemplateRepository programmeTemplateRepository;

    @Autowired
    private TrainerLibraryWorkoutItemRepository workoutItemRepository;

    @Autowired
    private TrainerLibraryWorkoutNoteRepository workoutNoteRepository;

    @Autowired
    private TrainerLibraryWorkoutTemplateRepository workoutTemplateRepository;

    @Autowired
    private TrainerLibraryExerciseNoteRepository exerciseNoteRepository;

    @Autowired
    private TrainerLibraryExerciseRepository exerciseRepository;

    private User client;
    private User otherClient;
    private User trainerA;
    private User trainerB;

    @BeforeEach
    void setup() {
        sharedTemplateRepository.deleteAll();
        programmeDayRepository.deleteAll();
        programmeNoteRepository.deleteAll();
        programmeTemplateRepository.deleteAll();
        workoutItemRepository.deleteAll();
        workoutNoteRepository.deleteAll();
        workoutTemplateRepository.deleteAll();
        exerciseNoteRepository.deleteAll();
        exerciseRepository.deleteAll();
        linkRepository.deleteAll();

        String suffix = UUID.randomUUID().toString().replace("-", "");

        client = new User("client+" + suffix + "@example.com", "Client", "One", "tl_client_" + suffix, "password123");
        client.setRole(Role.CLIENT);
        client = userRepository.save(client);

        otherClient = new User("client2+" + suffix + "@example.com", "Client", "Two", "tl_client2_" + suffix, "password123");
        otherClient.setRole(Role.CLIENT);
        otherClient = userRepository.save(otherClient);

        trainerA = new User("trainerA+" + suffix + "@example.com", "Trainer", "A", "tl_trainer_a_" + suffix, "password123");
        trainerA.setRole(Role.TRAINER);
        trainerA.setTrainerVerified(true);
        trainerA = userRepository.save(trainerA);

        trainerB = new User("trainerB+" + suffix + "@example.com", "Trainer", "B", "tl_trainer_b_" + suffix, "password123");
        trainerB.setRole(Role.TRAINER);
        trainerB.setTrainerVerified(true);
        trainerB = userRepository.save(trainerB);
    }

    @Test
    void populatedExercisePagesRenderAndPreserveRejectedDrafts() throws Exception {
        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Searchable press"); form.setPrimaryMuscles("Chest"); form.setEquipment("Bodyweight"); form.setDifficulty("Easy");
        form.setVideoUrl("https://example.com/demo");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), form);
        mockMvc.perform(get("/trainer/library/exercises").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Searchable press")))
                .andExpect(content().string(containsString("library-exercise-v2")));
        mockMvc.perform(get("/trainer/library/exercises").param("q", "bodyweight").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Searchable press")));
        mockMvc.perform(get("/trainer/library/exercises").param("q", "unmatched").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No results found.")))
                .andExpect(content().string(not(containsString("Searchable press"))));
        mockMvc.perform(get("/trainer/library/exercises/create").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/trainer/library/exercises/" + exercise.getId() + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"Easy\"")));
        mockMvc.perform(post("/trainer/library/exercises/" + exercise.getId() + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("name", "Retained <exercise>").param("primaryMuscles", "Chest").param("equipment", "Bodyweight").param("difficulty", "Easy")
                .param("videoUrl", "javascript:alert(1)").param("notesText", "Keep <notes>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Retained &lt;exercise&gt;")))
                .andExpect(content().string(containsString("Keep &lt;notes&gt;")));
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getExerciseOwned(trainerA.getId(), exercise.getId()).getName()).isEqualTo("Searchable press");
        exercise.setVideoUrl("javascript:alert(1)"); exerciseRepository.saveAndFlush(exercise);
        mockMvc.perform(get("/trainer/library/exercises/" + exercise.getId()).with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("javascript:alert(1)"))))
                .andExpect(content().string(containsString("You need an active client")))
                .andExpect(content().string(containsString("<details id=\"shareDialog\"")));
    }

    @Test
    void sharingStaysInsideOwnedResourceAndShowsSavedFeedback() throws Exception {
        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Share press"); form.setPrimaryMuscles("Chest"); form.setEquipment("Bodyweight"); form.setDifficulty("Easy");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), form);
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));
        var result = mockMvc.perform(post("/trainer/library/share").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "EXERCISE").param("templateId", exercise.getId().toString()).param("clientId", client.getId().toString())
                .param("returnUrl", "//outside.example/path"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library/exercises/" + exercise.getId())).andReturn();
        mockMvc.perform(get("/trainer/library/exercises/" + exercise.getId()).with(user(trainerA.getUsername()).roles("TRAINER")).cookie(result.getResponse().getCookies()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Shared with Client One.")))
                .andExpect(content().string(containsString("name=\"clientId\" required")));
        mockMvc.perform(get("/client/assigned-plan").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Share press")));
        trainerA.setTrainerVerified(false); userRepository.saveAndFlush(trainerA);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(client.getId())).isEmpty();
        trainerA.setTrainerVerified(true); userRepository.saveAndFlush(trainerA);
        client.setEnabled(false); userRepository.saveAndFlush(client);
        assertThatThrownBy(() -> trainerLibraryService.shareTemplate(trainerA.getId(), shareFormFor(exercise.getId(), client.getId())))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sharedExerciseInstructionsStayCurrentOnlyForTheActiveClient() throws Exception {
        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Shared instruction exercise"); form.setPrimaryMuscles("Back");
        form.setEquipment("Dumbbells"); form.setDifficulty("Beginner");
        form.setNotesText("Shared instruction <literal>");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), form);
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var owner = mockMvc.perform(get("/trainer/library/exercises/{id}", exercise.getId()).param("lang", locale)
                    .with(user(trainerA.getUsername()).roles("TRAINER"))).andExpect(status().isOk()).andReturn();
            var detail = org.jsoup.Jsoup.parse(owner.getResponse().getContentAsString());
            org.assertj.core.api.Assertions.assertThat(detail.select(".library-exercise-video a")).isEmpty();
            org.assertj.core.api.Assertions.assertThat(detail.select(".library-resource-actions form input[name=_csrf]")).hasSize(1);
            org.assertj.core.api.Assertions.assertThat(detail.select(".library-instruction-sharing").text()).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(detail.text()).doesNotContain("??ui.library.");
        }
        var link = linkRepository.saveAndFlush(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));
        mockMvc.perform(post("/trainer/library/share").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "EXERCISE").param("templateId", exercise.getId().toString()).param("clientId", client.getId().toString())
                .param("q", "Shared+instruction").param("page", "3").param("returnUrl", "https://example.invalid/outside"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainer/library/exercises/" + exercise.getId() + "?q=Shared%2Binstruction&page=3"));
        mockMvc.perform(post("/trainer/library/share").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "EXERCISE").param("templateId", exercise.getId().toString())
                .param("q", "Shared+instruction").param("page", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/trainer/library/exercises/" + exercise.getId() + "?q=Shared%2Binstruction&page=3"));
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(client.getId()).getFirst().notes())
                .extracting(TrainerLibraryExerciseNote::getNoteText).containsExactly("Shared instruction <literal>");
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(otherClient.getId())).isEmpty();
        var recipient = mockMvc.perform(get("/client/assigned-plan").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        var rendered = org.jsoup.Jsoup.parse(recipient.getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(rendered.text()).contains("Shared instruction <literal>");
        org.assertj.core.api.Assertions.assertThat(rendered.select("literal")).isEmpty();
        form.setNotesText("Updated shared instruction");
        trainerLibraryService.updateExercise(trainerA.getId(), exercise.getId(), form);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(client.getId()).getFirst().notes())
                .extracting(TrainerLibraryExerciseNote::getNoteText).containsExactly("Updated shared instruction");
        link.setStatus(TrainerClientLinkStatus.PAUSED); linkRepository.saveAndFlush(link);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(client.getId())).isEmpty();
        link.setStatus(TrainerClientLinkStatus.ACTIVE); linkRepository.saveAndFlush(link);
        trainerA.setEnabled(false); userRepository.saveAndFlush(trainerA);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedExercisesForClient(client.getId())).isEmpty();
    }

    private TrainerLibraryShareForm shareFormFor(Long exerciseId, Long clientId) {
        var form = new TrainerLibraryShareForm(); form.setTemplateType(TrainerLibraryTemplateType.EXERCISE);
        form.setTemplateId(exerciseId); form.setClientId(clientId); return form;
    }

    @Test
    void usedExerciseDeletionPreservesWorkoutAndUnusedDeletionRemovesStaleShares() throws Exception {
        var form = new TrainerLibraryExerciseForm();
        form.setName("Referenced exercise"); form.setPrimaryMuscles("Chest"); form.setEquipment("Bodyweight");
        form.setDifficulty("Easy"); form.setNotesText("Preserve these notes");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), form);
        form.setVideoUrl("https://user:password@example.com/video");
        assertThatThrownBy(() -> trainerLibraryService.updateExercise(trainerA.getId(), exercise.getId(), form)).isInstanceOf(IllegalArgumentException.class);
        var workoutForm = new TrainerLibraryWorkoutTemplateForm(); workoutForm.setTitle("Referenced workout");
        var workout = trainerLibraryService.createWorkout(trainerA.getId(), workoutForm);
        var itemForm = new TrainerLibraryWorkoutItemForm(); itemForm.setExerciseId(exercise.getId());
        itemForm.setSets(3); itemForm.setReps(8); itemForm.setRestSeconds(60); itemForm.setOrderIndex(0);
        var item = trainerLibraryService.addWorkoutItem(trainerA.getId(), workout.getId(), itemForm);
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));
        trainerLibraryService.shareTemplate(trainerA.getId(), shareFormFor(exercise.getId(), client.getId()));
        var blocked = mockMvc.perform(post("/trainer/library/exercises/" + exercise.getId() + "/delete")
                .with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library/exercises/" + exercise.getId())).andReturn();
        mockMvc.perform(get("/trainer/library/exercises/" + exercise.getId()).with(user(trainerA.getUsername()).roles("TRAINER")).cookie(blocked.getResponse().getCookies()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("This exercise is used in a workout.")));
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getExerciseNotes(exercise.getId())).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutItems(workout.getId())).hasSize(1);
        trainerLibraryService.deleteWorkoutItem(trainerA.getId(), workout.getId(), item.getId());
        trainerLibraryService.deleteExercise(trainerA.getId(), exercise.getId());
        org.assertj.core.api.Assertions.assertThat(exerciseRepository.existsById(exercise.getId())).isFalse();
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getExerciseNotes(exercise.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(sharedTemplateRepository.findByClientIdAndTrainerIdAndTemplateTypeAndTemplateId(
                client.getId(), trainerA.getId(), TrainerLibraryTemplateType.EXERCISE, exercise.getId())).isEmpty();
    }

    @Test
    void workoutCompositionAppendsAndRetainsRejectedPrescription() throws Exception {
        var exerciseForm = new TrainerLibraryExerciseForm(); exerciseForm.setName("Composition exercise");
        exerciseForm.setPrimaryMuscles("Chest"); exerciseForm.setEquipment("Bodyweight"); exerciseForm.setDifficulty("Easy");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), exerciseForm);
        var workoutForm = new TrainerLibraryWorkoutTemplateForm(); workoutForm.setTitle("Composition workout");
        workoutForm.setSummary("Searchable summary"); workoutForm.setNotesText("Saved workout note");
        var workout = trainerLibraryService.createWorkout(trainerA.getId(), workoutForm);
        mockMvc.perform(get("/trainer/library/workouts").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Composition workout")));
        mockMvc.perform(get("/trainer/library/workouts").param("q", "summary").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Composition workout")));
        mockMvc.perform(get("/trainer/library/workouts/create").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("maxlength=\"120\"")));
        mockMvc.perform(get("/trainer/library/workouts/" + workout.getId() + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Saved workout note")));
        for (int position = 0; position < 2; position++) {
            mockMvc.perform(post("/trainer/library/workouts/" + workout.getId() + "/items").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                    .param("exerciseId", exercise.getId().toString()).param("sets", "3").param("reps", "8").param("restSeconds", "60").param("rpe", "7"))
                    .andExpect(status().is3xxRedirection());
        }
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutItems(workout.getId()))
                .extracting(TrainerLibraryWorkoutItem::getOrderIndex).containsExactly(0, 1);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutItemCounts(trainerA.getId())).containsEntry(workout.getId(), 2L);
        mockMvc.perform(get("/trainer/library/workouts/" + workout.getId()).with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Composition exercise")));
        mockMvc.perform(post("/trainer/library/workouts/" + workout.getId() + "/items").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("exerciseId", exercise.getId().toString()).param("sets", "3").param("reps", "bad-reps").param("restSeconds", "60").param("rpe", "11"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-reps\"")))
                .andExpect(content().string(containsString("value=\"11\"")));
        mockMvc.perform(post("/trainer/library/workouts/" + workout.getId() + "/items").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("exerciseId", exercise.getId().toString()).param("sets", "3").param("reps", "8").param("restSeconds", "60").param("orderIndex", "0"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutItems(workout.getId())).hasSize(2);
        mockMvc.perform(post("/trainer/library/workouts/" + workout.getId() + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Draft <workout>").param("notesText", "x".repeat(10001)))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Draft &lt;workout&gt;")));
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutOwned(trainerA.getId(), workout.getId()).getTitle()).isEqualTo("Composition workout");
    }

    @Test
    void usedWorkoutDeletionPreservesProgrammeAndAllowsCleanupAfterRemoval() throws Exception {
        var workoutForm = new TrainerLibraryWorkoutTemplateForm(); workoutForm.setTitle("Used workout"); workoutForm.setNotesText("Retain workout note");
        var workout = trainerLibraryService.createWorkout(trainerA.getId(), workoutForm);
        var programmeForm = new TrainerLibraryProgrammeTemplateForm(); programmeForm.setTitle("Used programme"); programmeForm.setWeeks(4);
        var programme = trainerLibraryService.createProgramme(trainerA.getId(), programmeForm);
        var dayForm = new TrainerLibraryProgrammeDayForm(); dayForm.setDayOfWeek("Monday"); dayForm.setOrderIndex(0); dayForm.setWorkoutId(workout.getId());
        var day = trainerLibraryService.addProgrammeDay(trainerA.getId(), programme.getId(), dayForm);
        var rejected = mockMvc.perform(post("/trainer/library/workouts/" + workout.getId() + "/delete").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library/workouts/" + workout.getId())).andReturn();
        mockMvc.perform(get("/trainer/library/workouts/" + workout.getId()).with(user(trainerA.getUsername()).roles("TRAINER")).cookie(rejected.getResponse().getCookies()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("This workout is used in a programme.")));
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutNotes(workout.getId())).hasSize(1);
        trainerLibraryService.deleteProgrammeDay(trainerA.getId(), programme.getId(), day.getId());
        trainerLibraryService.deleteWorkout(trainerA.getId(), workout.getId());
        org.assertj.core.api.Assertions.assertThat(workoutTemplateRepository.existsById(workout.getId())).isFalse();
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getWorkoutNotes(workout.getId())).isEmpty();
    }

    @Test
    void programmeCompositionAppendsOwnedWorkoutsAndRetainsRejectedDrafts() throws Exception {
        var workoutForm = new TrainerLibraryWorkoutTemplateForm(); workoutForm.setTitle("Cycle strength workout");
        var workout = trainerLibraryService.createWorkout(trainerA.getId(), workoutForm);
        var programmeForm = new TrainerLibraryProgrammeTemplateForm(); programmeForm.setTitle("Searchable cycle programme");
        programmeForm.setWeeks(6); programmeForm.setNotesText("Programme coaching note");
        var programme = trainerLibraryService.createProgramme(trainerA.getId(), programmeForm);
        String destination = "/trainer/library/programmes/" + programme.getId();
        mockMvc.perform(get("/trainer/library/programmes").param("q", "cycle").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Searchable cycle programme")))
                .andExpect(content().string(containsString("6 weeks")));
        mockMvc.perform(get("/trainer/library/programmes").param("q", "no-match").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No results found.")))
                .andExpect(content().string(not(containsString("Searchable cycle programme"))));
        mockMvc.perform(get("/trainer/library/programmes/create").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get(destination + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Programme coaching note")));
        for (int position = 0; position < 2; position++) {
            var result = mockMvc.perform(post(destination + "/days").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                    .param("workoutId", workout.getId().toString()).param("dayOfWeek", "Cycle " + (position + 1)))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(destination)).andReturn();
            mockMvc.perform(get(destination).with(user(trainerA.getUsername()).roles("TRAINER")).cookie(result.getResponse().getCookies()))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("Workout added to this programme.")));
        }
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeDays(programme.getId()))
                .extracting(TrainerLibraryProgrammeDay::getOrderIndex).containsExactly(0, 1);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeDayCounts(trainerA.getId())).containsEntry(programme.getId(), 2L);
        mockMvc.perform(post(destination + "/days").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("workoutId", workout.getId().toString()).param("dayOfWeek", "Keep <draft>").param("orderIndex", "0"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Keep &lt;draft&gt;")));
        mockMvc.perform(post(destination + "/days").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("dayOfWeek", "Missing workout"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Missing workout")));
        mockMvc.perform(post(destination + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Draft <programme>").param("weeks", "bad-weeks").param("notesText", "Retain <note>"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("value=\"bad-weeks\"")))
                .andExpect(content().string(containsString("Draft &lt;programme&gt;")));
        mockMvc.perform(post(destination + "/edit").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("title", "Changed title").param("weeks", "0").param("notesText", "x".repeat(10001)))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeOwned(trainerA.getId(), programme.getId()).getTitle()).isEqualTo("Searchable cycle programme");
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeDays(programme.getId())).hasSize(2);
        programmeForm.setWeeks(-1);
        assertThatThrownBy(() -> trainerLibraryService.updateProgramme(trainerA.getId(), programme.getId(), programmeForm)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharedProgrammeShowsRealDaysAndDeletionCleansSharesWithoutRemovingWorkouts() throws Exception {
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));
        var workoutForm = new TrainerLibraryWorkoutTemplateForm(); workoutForm.setTitle("Shared programme workout");
        var workout = trainerLibraryService.createWorkout(trainerA.getId(), workoutForm);
        var programmeForm = new TrainerLibraryProgrammeTemplateForm(); programmeForm.setTitle("Shared cycle programme");
        programmeForm.setNotesText("Client coaching notes");
        var programme = trainerLibraryService.createProgramme(trainerA.getId(), programmeForm);
        var dayForm = new TrainerLibraryProgrammeDayForm(); dayForm.setDayOfWeek("Custom cycle"); dayForm.setWorkoutId(workout.getId());
        trainerLibraryService.addProgrammeDay(trainerA.getId(), programme.getId(), dayForm);
        mockMvc.perform(post("/trainer/library/share").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf())
                .param("templateType", "PROGRAMME").param("templateId", programme.getId().toString()).param("clientId", client.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library/programmes/" + programme.getId()));
        mockMvc.perform(get("/client/assigned-plan").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Shared cycle programme")))
                .andExpect(content().string(containsString("Custom cycle")))
                .andExpect(content().string(containsString("Shared programme workout")))
                .andExpect(content().string(containsString("Client coaching notes")));
        mockMvc.perform(post("/trainer/library/programmes/" + programme.getId() + "/delete").with(user(trainerA.getUsername()).roles("TRAINER")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library/programmes"));
        org.assertj.core.api.Assertions.assertThat(programmeTemplateRepository.existsById(programme.getId())).isFalse();
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeDays(programme.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getProgrammeNotes(programme.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(sharedTemplateRepository.findByClientIdAndTrainerIdAndTemplateTypeAndTemplateId(
                client.getId(), trainerA.getId(), TrainerLibraryTemplateType.PROGRAMME, programme.getId())).isEmpty();
        org.assertj.core.api.Assertions.assertThat(workoutTemplateRepository.existsById(workout.getId())).isTrue();
    }

    @Test
    void trainerCannotViewAnotherTrainersExercise() throws Exception {
        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Push Up");
        form.setPrimaryMuscles("Chest");
        form.setEquipment("Bodyweight");
        form.setDifficulty("Easy");
        form.setNotesText("Keep core tight");

        TrainerLibraryExercise created = trainerLibraryService.createExercise(trainerA.getId(), form);

        mockMvc.perform(get("/trainer/library/exercises/" + created.getId())
                .with(user(trainerB.getUsername()).roles("TRAINER")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/access-denied"));
    }

    @Test
    void shareRequiresActiveTrainerClientLink() throws Exception {
        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Squat");
        form.setPrimaryMuscles("Quads");
        form.setEquipment("Barbell");
        form.setDifficulty("Medium");
        TrainerLibraryExercise created = trainerLibraryService.createExercise(trainerA.getId(), form);

        // No ACTIVE link between trainerA and otherClient
        mockMvc.perform(post("/trainer/library/share")
                .with(user(trainerA.getUsername()).roles("TRAINER"))
                .with(csrf())
                .param("clientId", String.valueOf(otherClient.getId()))
                .param("templateType", TrainerLibraryTemplateType.EXERCISE.name())
                .param("templateId", String.valueOf(created.getId()))
                .param("returnUrl", "/trainer/library/exercises/" + created.getId()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/access-denied"));
    }

    @Test
    void clientAssignedPlanOnlyShowsTemplatesFromActiveTrainer() throws Exception {
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));

        // Workout owned by trainerA and shared to client
        TrainerLibraryWorkoutTemplateForm workoutAForm = new TrainerLibraryWorkoutTemplateForm();
        workoutAForm.setTitle("Full Body A");
        TrainerLibraryWorkoutTemplate workoutA = trainerLibraryService.createWorkout(trainerA.getId(), workoutAForm);

        TrainerLibraryShareForm shareA = new TrainerLibraryShareForm();
        shareA.setClientId(client.getId());
        shareA.setTemplateType(TrainerLibraryTemplateType.WORKOUT);
        shareA.setTemplateId(workoutA.getId());
        shareA.setReturnUrl("/trainer/library/workouts/" + workoutA.getId());
        trainerLibraryService.shareTemplate(trainerA.getId(), shareA);

        var exerciseForm = new TrainerLibraryExerciseForm();
        exerciseForm.setName("Repeated owned row"); exerciseForm.setPrimaryMuscles("Back");
        exerciseForm.setEquipment("Dumbbells"); exerciseForm.setDifficulty("BEGINNER");
        var exercise = trainerLibraryService.createExercise(trainerA.getId(), exerciseForm);
        var prescription = new TrainerLibraryWorkoutItemForm();
        prescription.setExerciseId(exercise.getId()); prescription.setSets(3); prescription.setReps(8); prescription.setRestSeconds(60);
        var first = trainerLibraryService.addWorkoutItem(trainerA.getId(), workoutA.getId(), prescription);
        prescription.setSets(2); prescription.setReps(12); prescription.setRestSeconds(90); prescription.setRpe(7);
        var second = trainerLibraryService.addWorkoutItem(trainerA.getId(), workoutA.getId(), prescription);
        trainerLibraryService.moveWorkoutItem(trainerA.getId(), workoutA.getId(), second.getId(), "UP");
        workoutAForm.setExpectedRevision(trainerLibraryService.getWorkoutRevision(trainerA.getId(), workoutA.getId()));
        workoutAForm.setSummary("Saved after sharing.\nSecond client-visible line.");
        workoutAForm.setNotesText("Updated shared instruction <literal>");
        trainerLibraryService.updateWorkout(trainerA.getId(), workoutA.getId(), workoutAForm);
        org.assertj.core.api.Assertions.assertThat(trainerLibraryService.getAssignedWorkoutsForClient(client.getId()).getFirst().getItems())
                .extracting(TrainerLibraryWorkoutItem::getId).containsExactly(second.getId(), first.getId());

        // Workout owned by trainerB but (maliciously) inserted into shared table
        TrainerLibraryWorkoutTemplateForm workoutBForm = new TrainerLibraryWorkoutTemplateForm();
        workoutBForm.setTitle("Full Body B");
        TrainerLibraryWorkoutTemplate workoutB = trainerLibraryService.createWorkout(trainerB.getId(), workoutBForm);
        sharedTemplateRepository.save(new TrainerLibrarySharedTemplate(trainerB.getId(), client.getId(), TrainerLibraryTemplateType.WORKOUT, workoutB.getId()));

        mockMvc.perform(get("/client/assigned-plan")
                .with(user(client.getUsername()).roles("CLIENT")))
            .andExpect(status().isOk())
            .andExpect(view().name("client-views/client/assigned-plan"))
            .andExpect(content().string(containsString("Full Body A")))
            .andExpect(content().string(not(containsString("Full Body B"))));
        for (String locale : new String[]{"en", "ar", "cy", "de", "es", "fr", "hi", "it", "ja", "ko", "nl", "pl", "pt", "zh"}) {
            var response = mockMvc.perform(get("/client/assigned-plan").param("lang", locale)
                    .with(user(client.getUsername()).roles("CLIENT"))).andExpect(status().isOk()).andReturn();
            var rendered = org.jsoup.Jsoup.parse(response.getResponse().getContentAsString());
            org.assertj.core.api.Assertions.assertThat(rendered.select("main .plan-coaching-note").getFirst().wholeText())
                    .isEqualTo("Saved after sharing.\nSecond client-visible line.");
            org.assertj.core.api.Assertions.assertThat(rendered.select("main .plan-exercise-prescription").getFirst().text()).contains("/10");
            org.assertj.core.api.Assertions.assertThat(rendered.select("main form, main literal")).isEmpty();
            org.assertj.core.api.Assertions.assertThat(rendered.text()).contains("Updated shared instruction <literal>").doesNotContain("Full Body B");
        }
    }

    @Test
    void shareRequiresCsrf() throws Exception {
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));

        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Plank");
        form.setPrimaryMuscles("Core");
        form.setEquipment("Bodyweight");
        form.setDifficulty("Easy");
        TrainerLibraryExercise created = trainerLibraryService.createExercise(trainerA.getId(), form);

        mockMvc.perform(post("/trainer/library/share")
                .with(user(trainerA.getUsername()).roles("TRAINER"))
                .param("clientId", String.valueOf(client.getId()))
                .param("templateType", TrainerLibraryTemplateType.EXERCISE.name())
                .param("templateId", String.valueOf(created.getId()))
                .param("returnUrl", "/trainer/library/exercises/" + created.getId()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void unverifiedTrainerReceivesLibraryGateWithoutRedirectLoop() throws Exception {
        trainerA.setTrainerVerified(false);
        userRepository.saveAndFlush(trainerA);
        mockMvc.perform(get("/trainer/library").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("library-verification")))
                .andExpect(content().string(not(containsString("library-hub-grid"))));
        for (String route : java.util.List.of("exercises", "workouts", "programmes")) {
            mockMvc.perform(get("/trainer/library/" + route + "/create").with(user(trainerA.getUsername()).roles("TRAINER")))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/library?error=trainer-unverified"));
        }
        mockMvc.perform(get("/trainer/library?error=trainer-unverified").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("library-verification")));
        mockMvc.perform(get("/trainer/templates").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("verification")))
                .andExpect(content().string(not(containsString("href=\"/trainer/templates/create\""))));
        mockMvc.perform(get("/trainer/templates/create").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/trainer/templates?error=trainer-unverified"));
        mockMvc.perform(get("/trainer/templates?error=trainer-unverified").with(user(trainerA.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk());
    }

    @Test
    void unverifiedTrainerCannotCreateExercise() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User unverified = new User("trainerU+" + suffix + "@example.com", "Trainer", "U", "tl_trainer_u_" + suffix, "password123");
        unverified.setRole(Role.TRAINER);
        final User savedUnverified = userRepository.save(unverified);

        TrainerLibraryExerciseForm form = new TrainerLibraryExerciseForm();
        form.setName("Push Up");
        form.setPrimaryMuscles("Chest");
        form.setEquipment("Bodyweight");
        form.setDifficulty("Easy");

        assertThatThrownBy(() -> trainerLibraryService.createExercise(savedUnverified.getId(), form))
                .isInstanceOf(TrainerAccessException.class)
                .hasMessage(TrainerLibraryService.ERROR_TRAINER_NOT_VERIFIED)
                .extracting(ex -> ((TrainerAccessException) ex).getReason())
                .isEqualTo(TrainerAccessException.Reason.TRAINER_NOT_VERIFIED);
    }

    @Test
    void assignedLibraryViewsDoNotExposeForeignExerciseOrWorkoutReferences() throws Exception {
        linkRepository.save(new TrainerClientLink(client.getId(), trainerA.getId(), TrainerClientLinkStatus.ACTIVE));
        var exerciseForm = new TrainerLibraryExerciseForm();
        exerciseForm.setName("Private exercise from another trainer"); exerciseForm.setPrimaryMuscles("Core");
        exerciseForm.setEquipment("Bodyweight"); exerciseForm.setDifficulty("Easy");
        var privateExercise = trainerLibraryService.createExercise(trainerB.getId(), exerciseForm);
        var ownWorkoutForm = new TrainerLibraryWorkoutTemplateForm(); ownWorkoutForm.setTitle("Shared own workout");
        var ownWorkout = trainerLibraryService.createWorkout(trainerA.getId(), ownWorkoutForm);
        var item = new TrainerLibraryWorkoutItem(); item.setWorkoutId(ownWorkout.getId()); item.setExerciseId(privateExercise.getId());
        item.setSets(3); item.setReps(8); item.setRestSeconds(60); item.setOrderIndex(0);
        workoutItemRepository.save(item);
        sharedTemplateRepository.save(new TrainerLibrarySharedTemplate(trainerA.getId(), client.getId(), TrainerLibraryTemplateType.WORKOUT, ownWorkout.getId()));
        var foreignWorkoutForm = new TrainerLibraryWorkoutTemplateForm(); foreignWorkoutForm.setTitle("Private workout from another trainer");
        var privateWorkout = trainerLibraryService.createWorkout(trainerB.getId(), foreignWorkoutForm);
        var programmeForm = new TrainerLibraryProgrammeTemplateForm(); programmeForm.setTitle("Shared own programme"); programmeForm.setWeeks(2);
        var programme = trainerLibraryService.createProgramme(trainerA.getId(), programmeForm);
        var day = new TrainerLibraryProgrammeDay(); day.setProgrammeId(programme.getId()); day.setDayOfWeek("Mon");
        day.setWorkoutId(privateWorkout.getId()); day.setOrderIndex(0); programmeDayRepository.save(day);
        sharedTemplateRepository.save(new TrainerLibrarySharedTemplate(trainerA.getId(), client.getId(), TrainerLibraryTemplateType.PROGRAMME, programme.getId()));
        mockMvc.perform(get("/client/assigned-plan").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shared own workout")))
                .andExpect(content().string(containsString("3 sets × 8 reps")))
                .andExpect(content().string(not(containsString("Private exercise from another trainer"))))
                .andExpect(content().string(not(containsString("Private workout from another trainer"))));
    }
}
