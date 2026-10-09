package uk.ac.cf._5.group14.One_To_One.ScheduleTests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleAppliedRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntry;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleRepository;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScheduleApiDeploymentMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleEntryRepository scheduleEntryRepository;

    @Autowired
    private ScheduleOccurrenceRepository occurrenceRepository;

    @Autowired
    private ScheduleAppliedRepository appliedRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CalendarTaskRepository taskRepository;

    @Autowired
    private WorkoutSessionRepository sessionRepository;

    @BeforeEach
    void resetIdentitySequences() {
        jdbcTemplate.execute("ALTER TABLE schedules ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.execute("ALTER TABLE schedule_entries ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.execute("ALTER TABLE schedule_occurrences ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.execute("ALTER TABLE schedule_applied ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.execute("ALTER TABLE calendar_tasks ALTER COLUMN id RESTART WITH 1000");
        jdbcTemplate.execute("ALTER TABLE workout_sessions ALTER COLUMN id RESTART WITH 1000");
    }

    @Test
    void applyDeploymentCreatesOccurrencesAndUndoRemovesThem() throws Exception {
        User client = savedClient();
        Exercise exercise = existingExercise(0);
        Schedule schedule = savedSchedule(client, exercise, 1);

        String applyResponse = mockMvc.perform(post("/api/schedules/" + schedule.getId() + "/deployment/apply")
                        .with(user(client.getUsername()).roles("CLIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedDate": "2026-05-11",
                                  "scope": "week",
                                  "strategy": "merge"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.strategy").value("merge"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), schedule.getId()))
                .hasSize(1);
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).hasSize(1);

        JsonNode json = objectMapper.readTree(applyResponse);
        String undoToken = json.get("undoToken").asText();

        mockMvc.perform(post("/api/schedules/" + schedule.getId() + "/deployment/undo")
                        .with(user(client.getUsername()).roles("CLIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"undoToken\":\"" + undoToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), schedule.getId()))
                .isEmpty();
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).isEmpty();
    }

    @Test
    void replaceDeploymentRestoresPreviousOccurrenceOnUndo() throws Exception {
        User client = savedClient();
        Exercise originalExercise = existingExercise(0);
        Exercise replacementExercise = existingExercise(1);
        Schedule replacementSchedule = savedSchedule(client, replacementExercise, 1);

        ScheduleOccurrence existing = new ScheduleOccurrence();
        existing.setUser(client);
        Schedule originalSchedule = savedSchedule(client, originalExercise, 1);
        existing.setSchedule(originalSchedule);
        existing.setScheduleName("Existing schedule item");
        existing.setExercise(originalExercise);
        existing.setDate(LocalDate.of(2026, 5, 11));
        existing = occurrenceRepository.save(existing);

        String applyResponse = mockMvc.perform(post("/api/schedules/" + replacementSchedule.getId() + "/deployment/apply")
                        .with(user(client.getUsername()).roles("CLIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedDate": "2026-05-11",
                                  "scope": "week",
                                  "strategy": "replace"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.replaced").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(occurrenceRepository.findById(existing.getId())).isEmpty();
        assertThat(occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), replacementSchedule.getId()))
                .hasSize(1)
                .allSatisfy(occurrence -> assertThat(occurrence.getExercise().getId()).isEqualTo(replacementExercise.getId()));

        String undoToken = objectMapper.readTree(applyResponse).get("undoToken").asText();
        mockMvc.perform(post("/api/schedules/" + replacementSchedule.getId() + "/deployment/undo")
                        .with(user(client.getUsername()).roles("CLIENT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"undoToken\":\"" + undoToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), originalSchedule.getId()))
                .hasSize(1)
                .allSatisfy(occurrence -> {
                    assertThat(occurrence.getScheduleName()).isEqualTo("Existing schedule item");
                    assertThat(occurrence.getExercise().getId()).isEqualTo(originalExercise.getId());
                });
        assertThat(appliedRepository.findByUserAndSchedule(client, replacementSchedule)).isEmpty();
    }

    @Test
    void rejectsInvalidWindowsAndIntervalsWithoutWritingCalendarData() throws Exception {
        User client = savedClient();
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        for (String recurrence : List.of(
                "{\"repeat\":\"custom\",\"interval\":0}",
                "{\"repeat\":\"custom\",\"interval\":-1}",
                "{\"repeat\":\"custom\",\"interval\":1.5}",
                "{\"repeat\":\"custom\",\"interval\":2,\"unit\":\"invalid\"}",
                "{\"repeat\":\"invalid\"}",
                "{\"repeat\":\"weekly\",\"endDate\":\"2026-05-10\"}",
                "{\"repeat\":\"weekly\",\"endDate\":\"2028-05-11\"}",
                "{\"repeat\":\"weekly\",\"endDate\":\"2026-02-30\"}")) {
            for (String action : List.of("impact", "apply")) {
                deployment(client, schedule, action, "{\"startDate\":\"2026-05-11\",\"recurrence\":" + recurrence + "}")
                        .andExpect(status().isBadRequest());
            }
        }
        assertThat(occurrenceRepository.findByUserAndDateBetween(client, LocalDate.of(2026, 1, 1), LocalDate.of(2029, 1, 1))).isEmpty();
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).isEmpty();
    }

    @Test
    void customWeeklyRepeatIncludesEveryScheduledWeekdayInsideTheExactWindow() throws Exception {
        User client = savedClient();
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        ScheduleEntry wednesday = new ScheduleEntry();
        wednesday.setSchedule(schedule);
        wednesday.setExercise(existingExercise(1));
        wednesday.setDayOfWeek(3);
        wednesday.setOrderNumber(2);
        scheduleEntryRepository.save(wednesday);
        String payload = """
                {"startDate":"2026-05-11","recurrence":{"repeat":"custom","interval":2,"unit":"week","endDate":"2026-06-07"}}
                """;
        deployment(client, schedule, "impact", payload).andExpect(status().isOk()).andExpect(jsonPath("$.summary.added").value(4));
        deployment(client, schedule, "apply", payload).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(4));
        assertThat(occurrenceRepository.findByUserAndDateBetween(client, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 6, 7)))
                .extracting(ScheduleOccurrence::getDate).containsExactly(LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 13), LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 27));
        deployment(client, schedule, "apply", payload).andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0))
                .andExpect(jsonPath("$.alreadyScheduled").value(4)).andExpect(jsonPath("$.undoToken").doesNotExist());
        deployment(client, schedule, "apply", payload.replace("\"startDate\"", "\"strategy\":\"replace\",\"startDate\""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.replaced").value(0));
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).hasSize(1);
        deployment(client, schedule, "impact", """
                {"startDate":"2026-05-12","recurrence":{"repeat":"weekly","endDate":"2026-05-12"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.summary.added").value(0));
    }

    @Test
    void replacementAndUndoPreserveHistoryAndForeignRequestsCannotConsumeUndo() throws Exception {
        User client = savedClient();
        User other = savedClient();
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        Schedule historical = savedSchedule(client, existingExercise(1), 1);
        ScheduleOccurrence completed = new ScheduleOccurrence();
        completed.setUser(client);
        completed.setSchedule(historical);
        completed.setScheduleName("Completed history");
        completed.setExercise(existingExercise(1));
        completed.setDate(LocalDate.of(2026, 5, 11));
        completed.setCompleted(true);
        completed = occurrenceRepository.save(completed);
        String payload = """
                {"startDate":"2026-05-11","strategy":"replace","recurrence":{"repeat":"weekly","endDate":"2026-05-11"}}
                """;
        deployment(client, schedule, "impact", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.replaced").value(0)).andExpect(jsonPath("$.summary.protectedEntries").value(1));
        String response = deployment(client, schedule, "apply", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.replaced").value(0)).andReturn().getResponse().getContentAsString();
        String undo = objectMapper.writeValueAsString(java.util.Map.of("undoToken", objectMapper.readTree(response).get("undoToken").asText()));
        deployment(other, schedule, "undo", undo).andExpect(status().isForbidden());
        List<ScheduleOccurrence> created = occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), schedule.getId());
        created.get(0).setCompleted(true);
        occurrenceRepository.save(created.get(0));
        deployment(client, schedule, "undo", undo).andExpect(status().isConflict());
        assertThat(occurrenceRepository.findById(completed.getId())).isPresent();
        assertThat(occurrenceRepository.findById(created.get(0).getId())).isPresent();
        created.get(0).setCompleted(false);
        occurrenceRepository.save(created.get(0));
        deployment(client, schedule, "undo", undo).andExpect(status().isOk());
        assertThat(occurrenceRepository.findById(completed.getId())).isPresent();
        assertThat(occurrenceRepository.findById(created.get(0).getId())).isEmpty();
    }

    @Test
    void nativeApplicationUsesStartDateAndValidatesWithoutDuplicatingWorkouts() throws Exception {
        User client = savedClient();
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        mockMvc.perform(get("/schedules/" + schedule.getId() + "/apply").with(user(client.getUsername()).roles("CLIENT")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/schedules/" + schedule.getId() + "/apply").with(user(client.getUsername()).roles("CLIENT")).with(csrf())
                        .param("startDate", "2026-05-13").param("weeks", "0"))
                .andExpect(redirectedUrl("/schedules/" + schedule.getId() + "/apply"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/schedules/" + schedule.getId() + "/apply").with(user(client.getUsername()).roles("CLIENT")).with(csrf())
                            .param("startDate", "2026-05-13").param("weeks", "1"))
                    .andExpect(redirectedUrl("/calendar"));
        }
        assertThat(occurrenceRepository.findByUserAndDateBetween(client, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)))
                .extracting(ScheduleOccurrence::getDate).containsExactly(LocalDate.of(2026, 5, 18));
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).hasSize(1);
    }

    @Test
    void monthlyDatesDoNotDriftAndDailyAndLeapYearIntervalsAreExplicit() throws Exception {
        User client = savedClient();
        Schedule monthly = savedSchedule(client, existingExercise(0), 6);
        String response = deployment(client, monthly, "impact", """
                {"startDate":"2026-01-31","recurrence":{"repeat":"monthly","endDate":"2026-04-30"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.summary.added").value(4)).andReturn().getResponse().getContentAsString();
        List<String> dates = new ArrayList<>();
        objectMapper.readTree(response).get("plannedOccurrences").forEach(entry -> dates.add(entry.get("date").asText()));
        assertThat(dates).containsExactly("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30");
        Schedule daily = savedSchedule(client, existingExercise(0), 1);
        deployment(client, daily, "impact", """
                {"startDate":"2026-05-11","recurrence":{"repeat":"daily","endDate":"2026-05-13"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.summary.added").value(3))
                .andExpect(jsonPath("$.plannedOccurrences[2].date").value("2026-05-13"));
        Schedule yearly = savedSchedule(client, existingExercise(0), 4);
        deployment(client, yearly, "impact", """
                {"startDate":"2024-02-29","recurrence":{"repeat":"yearly","endDate":"2025-02-28"}}
                """).andExpect(status().isOk()).andExpect(jsonPath("$.summary.added").value(2))
                .andExpect(jsonPath("$.plannedOccurrences[1].date").value("2025-02-28"));
    }

    @Test
    void bothCalendarPanelsRenderOneDateEditorAndTranslatedConsequences() throws Exception {
        User client = savedClient();
        savedSchedule(client, existingExercise(0), 1);
        for (String view : List.of("month", "week")) {
            for (String language : List.of("en", "fr", "ar")) {
                String html = mockMvc.perform(get("/calendar").param("view", view).param("lang", language)
                                .with(user(client.getUsername()).roles("CLIENT")))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                assertThat(html).contains("id=\"scheduleDrawer\"", "data-impact-summary=", "id=\"schedule-deploy-start\"",
                        "id=\"schedule-repeat-end\"", "role=\"status\"", "/css/bundles/calendar.css?v=", "<noscript>")
                        .doesNotContain("id=\"schedule-scope-start\"", "??ui.deploy.", "Mon Tue Wed Fri");
            }
        }
    }

    @Test
    void standaloneWorkoutsAndTasksAreConflictsAndSurviveReplacementAndUndo() throws Exception {
        User client = savedClient();
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        CalendarTask task = new CalendarTask();
        task.setUser(client);
        task.setDate(LocalDate.of(2026, 5, 11));
        task.setTitle("Existing calendar task");
        task = taskRepository.save(task);
        WorkoutSession session = savedSession(client, null);
        String payload = """
                {"startDate":"2026-05-11","strategy":"skip","recurrence":{"repeat":"weekly","endDate":"2026-05-11"}}
                """;
        deployment(client, schedule, "impact", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.added").value(0)).andExpect(jsonPath("$.summary.skipped").value(1))
                .andExpect(jsonPath("$.summary.taskConflicts").value(1)).andExpect(jsonPath("$.summary.sessionConflicts").value(1))
                .andExpect(jsonPath("$.conflictsByDate['2026-05-11']").value(2));
        deployment(client, schedule, "apply", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(0)).andExpect(jsonPath("$.skipped").value(1));
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).isEmpty();
        String response = deployment(client, schedule, "apply", payload.replace("skip", "replace"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.replaced").value(0))
                .andReturn().getResponse().getContentAsString();
        String undo = objectMapper.writeValueAsString(java.util.Map.of("undoToken", objectMapper.readTree(response).get("undoToken").asText()));
        deployment(client, schedule, "undo", undo).andExpect(status().isOk());
        assertThat(taskRepository.findById(task.getId())).isPresent();
        assertThat(sessionRepository.findById(session.getId())).isPresent();
        assertThat(occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), schedule.getId())).isEmpty();
    }

    @Test
    void strengthSessionsProtectTheirSourceOccurrenceWithoutDoubleCountingConflicts() throws Exception {
        User client = savedClient();
        Schedule historical = savedSchedule(client, existingExercise(1), 1);
        Schedule schedule = savedSchedule(client, existingExercise(0), 1);
        ScheduleOccurrence existing = new ScheduleOccurrence();
        existing.setUser(client);
        existing.setSchedule(historical);
        existing.setScheduleName("In-progress strength session");
        existing.setExercise(existingExercise(1));
        existing.setDate(LocalDate.of(2026, 5, 11));
        existing = occurrenceRepository.save(existing);
        savedSession(client, existing.getId());
        String payload = """
                {"startDate":"2026-05-11","strategy":"replace","recurrence":{"repeat":"weekly","endDate":"2026-05-11"}}
                """;
        deployment(client, schedule, "impact", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.added").value(1)).andExpect(jsonPath("$.summary.replaced").value(0))
                .andExpect(jsonPath("$.summary.protectedEntries").value(1)).andExpect(jsonPath("$.summary.sessionConflicts").value(0))
                .andExpect(jsonPath("$.conflictsByDate['2026-05-11']").value(1));
        String response = deployment(client, schedule, "apply", payload).andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1)).andExpect(jsonPath("$.replaced").value(0))
                .andReturn().getResponse().getContentAsString();
        ScheduleOccurrence created = occurrenceRepository.findByUserAndDateAndScheduleId(client, LocalDate.of(2026, 5, 11), schedule.getId()).getFirst();
        savedSession(client, created.getId());
        String undo = objectMapper.writeValueAsString(java.util.Map.of("undoToken", objectMapper.readTree(response).get("undoToken").asText()));
        deployment(client, schedule, "undo", undo).andExpect(status().isConflict());
        assertThat(occurrenceRepository.findById(existing.getId())).isPresent();
        assertThat(occurrenceRepository.findById(created.getId())).isPresent();
        assertThat(appliedRepository.findByUserAndSchedule(client, schedule)).hasSize(1);
    }

    private WorkoutSession savedSession(User client, Long sourceOccurrenceId) {
        WorkoutSession session = new WorkoutSession();
        session.setUser(client);
        session.setDate(LocalDate.of(2026, 5, 11));
        session.setNameSnapshot("Synthetic strength session");
        session.setCreatedAt(java.time.LocalDateTime.now());
        session.setSourceOccurrenceId(sourceOccurrenceId);
        return sessionRepository.save(session);
    }

    private org.springframework.test.web.servlet.ResultActions deployment(User client, Schedule schedule, String action, String payload) throws Exception {
        return mockMvc.perform(post("/api/schedules/" + schedule.getId() + "/deployment/" + action)
                .with(user(client.getUsername()).roles("CLIENT")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private User savedClient() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        User client = new User("schedule+" + suffix + "@example.com", "Schedule", "Client", "schedule_client_" + suffix, "password123");
        client.setRole(Role.CLIENT);
        return userRepository.save(client);
    }

    private Exercise existingExercise(int index) {
        List<Exercise> exercises = new ArrayList<>();
        exerciseRepository.findAll().forEach(exercises::add);
        assertThat(exercises).hasSizeGreaterThan(index);
        return exercises.get(index);
    }

    private Schedule savedSchedule(User user, Exercise exercise, int dayOfWeek) {
        Schedule schedule = new Schedule();
        schedule.setUser(user);
        schedule.setName("Audit Schedule " + UUID.randomUUID());
        schedule = scheduleRepository.save(schedule);

        ScheduleEntry entry = new ScheduleEntry();
        entry.setSchedule(schedule);
        entry.setExercise(exercise);
        entry.setDayOfWeek(dayOfWeek);
        entry.setOrderNumber(1);
        scheduleEntryRepository.save(entry);
        return schedule;
    }
}
