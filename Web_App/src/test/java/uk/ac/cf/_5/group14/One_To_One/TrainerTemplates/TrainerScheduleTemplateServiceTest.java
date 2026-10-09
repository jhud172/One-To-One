package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.ConditionsPreferences.UserPreference.UserPreferenceRepository;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TrainerScheduleTemplateServiceTest {

    @Autowired
    private TrainerScheduleTemplateService templateService;

    @Autowired
    private TrainerScheduleTemplateRepository templateRepository;

    @Autowired
    private TrainerScheduleTemplateEntryRepository entryRepository;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private TrainerClientLinkRepository trainerClientLinkRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @Autowired
    private WorkoutRepository workoutRepository;

    @Autowired private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired private uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInService weeklyCheckInService;

    private User trainer;
    private User client;
    private Exercise exercise;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().replace("-", "");

        trainer = new User("trainer+" + suffix + "@example.com", "Trainer", "One", "trainer_template_" + suffix, "password123");
        trainer.setRole(Role.TRAINER);
        trainer.setTrainerVerified(true);
        trainer = userRepository.save(trainer);

        client = new User("client+" + suffix + "@example.com", "Client", "One", "client_template_" + suffix, "password123");
        client.setRole(Role.CLIENT);
        client = userRepository.save(client);

        exercise = new Exercise();
        exercise.setName("Push Up");
        exercise.setCategory("Bodyweight");
        exercise.setDifficulty(1);
        exercise.setType("Strength");
        exercise = exerciseRepository.save(exercise);
    }

    @Test
    void applyTemplateCreatesScheduleOccurrencesForClient() {
        trainerClientLinkRepository.save(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));

        TrainerScheduleTemplate template = templateService.createTemplate(trainer, "Week A", "", "");
        TrainerScheduleTemplateEntry entry = new TrainerScheduleTemplateEntry();
        entry.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        entry.setType(TrainerScheduleTemplateEntryType.WORKOUT);
        entry.setTitle("Workout A");
        entry.setExercise(exercise);
        templateService.addEntry(trainer, template.getId(), entry);

        LocalDate today = LocalDate.now();
        int created = templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true);

        assertThat(created).isEqualTo(1);
        assertThat(scheduleOccurrenceRepository.findByUserAndDate(client, today)).hasSize(1);
    }

    @Test
    void applyTemplateBlockedWithoutActiveLink() {
        TrainerScheduleTemplate template = templateService.createTemplate(trainer, "Week A", "", "");
        TrainerScheduleTemplateEntry entry = new TrainerScheduleTemplateEntry();
        entry.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        entry.setType(TrainerScheduleTemplateEntryType.WORKOUT);
        entry.setTitle("Workout A");
        entry.setExercise(exercise);
        templateService.addEntry(trainer, template.getId(), entry);

        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void idempotentApplyPreventsDuplicates() {
        trainerClientLinkRepository.save(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));

        TrainerScheduleTemplate template = templateService.createTemplate(trainer, "Week A", "", "");
        TrainerScheduleTemplateEntry entry = new TrainerScheduleTemplateEntry();
        entry.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        entry.setType(TrainerScheduleTemplateEntryType.WORKOUT);
        entry.setTitle("Workout A");
        entry.setExercise(exercise);
        templateService.addEntry(trainer, template.getId(), entry);

        LocalDate today = LocalDate.now();
        templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true);
        templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true);

        assertThat(scheduleOccurrenceRepository.findByUserAndDate(client, today)).hasSize(1);
    }

    @Test
    void createTemplateBlockedForUnverifiedTrainer() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User unverified = new User("trainerU+" + suffix + "@example.com", "Trainer", "U", "trainer_unverified_" + suffix, "password123");
        unverified.setRole(Role.TRAINER);
        final User savedUnverified = userRepository.save(unverified);

        assertThatThrownBy(() -> templateService.createTemplate(savedUnverified, "Week X", "", ""))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void editorRetainsInvalidDraftsAndCloneIncludesOrderedQuestions() throws Exception {
        var template = templateService.createTemplate(trainer, "Searchable weekly rhythm", "Existing description", "strength");
        String route = "/trainer/templates/" + template.getId();
        mvc.perform(get("/trainer/templates/create")
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk());
        mvc.perform(get("/trainer/templates").param("q", "strength")
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Searchable weekly rhythm")));
        mvc.perform(post(route + "/edit")
                .with(user(trainer.getUsername()).roles("TRAINER"))
                .with(csrf())
                .param("name", "Retain <draft>").param("description", "x".repeat(801)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Retain &lt;draft&gt;")));
        assertThat(templateService.getForTrainer(trainer, template.getId()).getName()).isEqualTo("Searchable weekly rhythm");
        var first = weeklyCheckInService.addQuestion(trainer, template.getId(), "First prompt", true);
        var second = weeklyCheckInService.addQuestion(trainer, template.getId(), "Second prompt", false);
        weeklyCheckInService.deleteQuestion(trainer, template.getId(), first.getId());
        var third = weeklyCheckInService.addQuestion(trainer, template.getId(), "Third prompt", true);
        assertThat(third.getOrderIndex()).isGreaterThan(second.getOrderIndex());
        var entry = new TrainerScheduleTemplateEntry(); entry.setDayOfWeek(1); entry.setType(TrainerScheduleTemplateEntryType.TASK); entry.setTitle("Monday rhythm");
        var firstEntry = templateService.addEntry(trainer, template.getId(), entry);
        var next = new TrainerScheduleTemplateEntry(); next.setDayOfWeek(2); next.setType(TrainerScheduleTemplateEntryType.NOTE); next.setTitle("Tuesday rhythm");
        var secondEntry = templateService.addEntry(trainer, template.getId(), next);
        templateService.deleteEntry(trainer, template.getId(), firstEntry.getId());
        var appended = new TrainerScheduleTemplateEntry(); appended.setDayOfWeek(3); appended.setType(TrainerScheduleTemplateEntryType.TASK); appended.setTitle("Wednesday rhythm");
        assertThat(templateService.addEntry(trainer, template.getId(), appended).getOrderIndex()).isGreaterThan(secondEntry.getOrderIndex());
        mvc.perform(post(route + "/entries")
                .with(user(trainer.getUsername()).roles("TRAINER"))
                .with(csrf())
                .param("dayOfWeek", "1").param("type", "TASK").param("title", "Keep <entry>")
                .param("timeWindowStart", "12:00").param("timeWindowEnd", "11:00").param("defaultsJson", "Retain <notes>"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Keep &lt;entry&gt;")));
        mvc.perform(post(route + "/questions")
                .with(user(trainer.getUsername()).roles("TRAINER"))
                .with(csrf()).param("prompt", "x".repeat(301)))
                .andExpect(status().isBadRequest());
        var clone = templateService.cloneTemplate(trainer, template.getId());
        assertThat(weeklyCheckInService.listQuestions(clone.getId())).extracting(uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestion::getPrompt)
                .containsExactly("Second prompt", "Third prompt");
        assertThat(entryRepository.findByTemplateIdOrderByOrderIndexAsc(clone.getId())).hasSize(2);
        mvc.perform(get(route + "/edit")
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("for=\"entryDay\"")));
    }

    @Test
    void applicationPreviewsNamedClientAndCountsIdempotentSavesAndRejectsInvalidRanges() throws Exception {
        trainerClientLinkRepository.save(new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.ACTIVE));
        var template = templateService.createTemplate(trainer, "Application rhythm", "", "");
        var today = LocalDate.now();
        var entry = new TrainerScheduleTemplateEntry(); entry.setDayOfWeek(today.getDayOfWeek().getValue());
        entry.setType(TrainerScheduleTemplateEntryType.WORKOUT); entry.setTitle("Workout calendar preview"); entry.setExercise(exercise);
        templateService.addEntry(trainer, template.getId(), entry);
        String route = "/trainer/templates/" + template.getId() + "/apply";
        mvc.perform(get(route)
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Client One")))
                .andExpect(content().string(not(containsString("Client #"))));
        mvc.perform(get(route).param("clientId", client.getId().toString())
                .param("start", today.toString()).param("end", today.toString()).param("idempotent", "true")
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Workout calendar preview")));
        assertThat(scheduleOccurrenceRepository.findByUserAndDate(client, today)).isEmpty();
        var applied = mvc.perform(post(route).param("clientId", client.getId().toString())
                .param("start", today.toString()).param("end", today.toString()).param("idempotent", "true")
                .param("expectedApplyRevision", templateService.previewApplication(trainer, template.getId(), client.getId(), today, today, true).revision())
                .with(user(trainer.getUsername()).roles("TRAINER"))
                .with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(applied.getFlashMap().get("appliedCount")).isEqualTo(1);
        mvc.perform(get(applied.getResponse().getRedirectedUrl())
                .cookie(applied.getResponse().getCookies())
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Saved 1 new items")))
                .andExpect(content().string(containsString("Already applied")));
        assertThat(templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true)).isZero();
        mvc.perform(post(route).param("clientId", client.getId().toString())
                .param("start", "bad-date").param("end", today.toString())
                .with(user(trainer.getUsername()).roles("TRAINER"))
                .with(csrf()))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today.plusDays(366), true)).isInstanceOf(IllegalArgumentException.class);
        templateService.updateTemplate(trainer, template.getId(), template.getName(), "", "", true);
        assertThatThrownBy(() -> templateService.applyTemplate(trainer, template.getId(), client.getId(), today, today, true)).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(get(route)
                .with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("This template is archived")));
        assertThat(scheduleOccurrenceRepository.findByUserAndDate(client, today)).hasSize(1);
    }
}
