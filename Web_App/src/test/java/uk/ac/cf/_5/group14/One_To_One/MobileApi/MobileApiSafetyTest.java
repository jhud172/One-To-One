package uk.ac.cf._5.group14.One_To_One.MobileApi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import uk.ac.cf._5.group14.One_To_One.GymApplications.GymApplicationService;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MobileApiSafetyTest {
    private MobileAuthService auth;
    private JdbcTemplate jdbc;
    private GymApplicationService applications;
    private MobileApiController controller;
    private User gym;

    @BeforeEach
    void setUp() {
        auth = mock(MobileAuthService.class);
        jdbc = mock(JdbcTemplate.class);
        applications = mock(GymApplicationService.class);
        controller = new MobileApiController(auth, jdbc, applications);
        gym = new User();
        gym.setId(1L);
        gym.setGymId(10L);
        gym.setEmail("owner@example.test");
        gym.setRole(Role.GYM_ADMIN);
        when(auth.authenticate("Bearer local-test")).thenReturn(Optional.of(gym));
    }

    @Test
    void gymCannotApproveAnyApplication() {
        assertThatThrownBy(() -> controller.approveGymRequest("Bearer local-test", 2L))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
        verifyNoInteractions(jdbc, applications);
    }

    @Test
    void staffApprovalUsesTheCompleteExistingWorkflow() {
        gym.setRole(Role.PLATFORM_ADMIN);
        controller.approveGymRequest("Bearer local-test", 2L);
        verify(applications).approve(2L, gym, null);
        verifyNoInteractions(jdbc);
    }

    @Test
    void applicationListContainsOnlyTheOwnersApplication() {
        JdbcTemplate isolated = isolatedDatabase();
        isolated.execute("CREATE TABLE gym_applications (id BIGINT, gym_name VARCHAR, admin_email VARCHAR, status VARCHAR, submitted_at TIMESTAMP)");
        isolated.update("INSERT INTO gym_applications VALUES (1, 'Owner Gym', 'owner@example.test', 'APPROVED', CURRENT_TIMESTAMP)");
        isolated.update("INSERT INTO gym_applications VALUES (2, 'Other Gym', 'private@example.test', 'PENDING', CURRENT_TIMESTAMP)");
        var result = new MobileApiController(auth, isolated, applications).gymRequests("Bearer local-test");
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) result.get("requests");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().values()).contains("owner@example.test").doesNotContain("private@example.test");
    }

    @Test
    void operationalTrainerListDoesNotExposeOtherOrUnassociatedTrainers() {
        JdbcTemplate isolated = isolatedDatabase();
        isolated.execute("CREATE TABLE users (id BIGINT, first_name VARCHAR, last_name VARCHAR, email VARCHAR, trainer_verified BOOLEAN, role VARCHAR, gym_id BIGINT)");
        isolated.execute("CREATE TABLE trainer_gym_affiliations (trainer_user_id BIGINT, gym_id BIGINT, status VARCHAR)");
        isolated.update("INSERT INTO users VALUES (1, 'Own', 'Trainer', 'own@example.test', TRUE, 'TRAINER', 10)");
        isolated.update("INSERT INTO users VALUES (2, 'Other', 'Trainer', 'other@example.test', TRUE, 'TRAINER', 20)");
        isolated.update("INSERT INTO users VALUES (3, 'Independent', 'Trainer', 'independent@example.test', TRUE, 'TRAINER', NULL)");
        var result = new MobileApiController(auth, isolated, applications).gymTrainers("Bearer local-test");
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) result.get("trainers");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().values()).contains("own@example.test");
        isolated.update("INSERT INTO trainer_gym_affiliations VALUES (1, 10, 'ENDED')");
        isolated.update("INSERT INTO trainer_gym_affiliations VALUES (2, 10, 'ACTIVE')");
        isolated.update("INSERT INTO trainer_gym_affiliations VALUES (3, 10, 'PENDING')");
        @SuppressWarnings("unchecked")
        var acceptedOnly = (List<Map<String, Object>>) new MobileApiController(auth, isolated, applications).gymTrainers("Bearer local-test").get("trainers");
        assertThat(acceptedOnly).hasSize(1);
        assertThat(acceptedOnly.getFirst().values()).contains("other@example.test").doesNotContain("own@example.test", "independent@example.test");
    }

    @Test
    void nativeSignupCannotBypassGymApplicationReview() {
        UserService users = mock(UserService.class);
        UserRepository repository = mock(UserRepository.class);
        var service = new MobileAuthService(users, repository, jdbc);
        assertThatThrownBy(() -> service.signup(null, Role.GYM_ADMIN))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
        verifyNoInteractions(users, repository, jdbc);
    }

    @Test
    void invalidTrainingDurationCannotWriteALog() {
        var request = new MobileApiController.TrainingLogRequest("2026-10-01", 3, 4, 4, "Training", -5);
        assertThatThrownBy(() -> controller.addLog("Bearer local-test", request))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(400));
        verifyNoInteractions(jdbc);
    }

    @Test
    void invalidMonthReturnsAClientError() throws Exception {
        MockMvcBuilders.standaloneSetup(controller).build()
            .perform(get("/api/mobile/calendar/month").header("Authorization", "Bearer local-test").param("month", "invalid"))
            .andExpect(status().isBadRequest());
    }

    private JdbcTemplate isolatedDatabase() {
        return new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:mobile-safety-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test
    void jdbcColumnLabelsProduceReadableOwnedMobileRecords() {
        JdbcTemplate isolated = isolatedDatabase();
        isolated.execute("CREATE TABLE calendar_tasks (id BIGINT, user_id BIGINT, date DATE, title VARCHAR, time TIME, notes VARCHAR, completed BOOLEAN, requires_log BOOLEAN)");
        isolated.execute("CREATE TABLE schedule_occurrences (id BIGINT, user_id BIGINT, date DATE, schedule_name VARCHAR, completed BOOLEAN, exercise_id BIGINT, custom_exercise_id BIGINT)");
        isolated.execute("CREATE TABLE exercises (id BIGINT, name VARCHAR)");
        isolated.execute("CREATE TABLE custom_exercises (id BIGINT, name VARCHAR)");
        isolated.update("INSERT INTO calendar_tasks VALUES (7, 1, '2026-10-02', 'Owned task', NULL, 'Warm up', FALSE, FALSE)");
        isolated.update("INSERT INTO calendar_tasks VALUES (8, 2, '2026-10-02', 'Private task', NULL, 'Hidden', FALSE, FALSE)");
        isolated.update("INSERT INTO schedule_occurrences VALUES (9, 1, '2026-10-02', 'Strength', FALSE, 4, NULL)");
        isolated.update("INSERT INTO exercises VALUES (4, 'Squat')");
        var api = new MobileApiController(auth, isolated, applications);
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) api.day("Bearer local-test", "2026-10-02").get("items");
        assertThat(items).hasSize(2);
        assertThat(items.getFirst()).containsEntry("id", "task-7").containsEntry("title", "Owned task").containsEntry("notes", "Warm up");
        assertThat(items.getLast()).containsEntry("id", "occurrence-9").containsEntry("title", "Strength").containsEntry("notes", "Squat");
        assertThat(items.getFirst().keySet()).doesNotContain("TITLE", "ID");
        api.completeTask("Bearer local-test", "task-7");
        assertThat(isolated.queryForObject("SELECT completed FROM calendar_tasks WHERE id=7", Boolean.class)).isTrue();
        @SuppressWarnings("unchecked")
        var month = (List<Map<String, Object>>) api.month("Bearer local-test", "2026-10").get("days");
        assertThat(month).hasSize(31);
        assertThat(month.getFirst()).containsEntry("date", "2026-10-01").containsEntry("total", 0).containsEntry("completed", 0);
        assertThat(month.get(1)).containsEntry("date", "2026-10-02").containsEntry("total", 2).containsEntry("completed", 1);
        assertThatThrownBy(() -> api.completeTask("Bearer local-test", "task-8"))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(404));
        assertThat(isolated.queryForObject("SELECT completed FROM calendar_tasks WHERE id=8", Boolean.class)).isFalse();
    }

    @Test
    void malformedTaskIdsReturnClientErrorsWithoutWriting() {
        assertThatThrownBy(() -> controller.completeTask("Bearer local-test", "task-null"))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(400));
        assertThatThrownBy(() -> controller.completeTask("Bearer local-test", "occurrence--1"))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(400));
        verifyNoInteractions(jdbc);
    }

    @Test
    void clientDetailReturnsOwnedReadableLogsAndRejectsAnUnlinkedClient() {
        gym.setRole(Role.TRAINER);
        JdbcTemplate isolated = isolatedDatabase();
        isolated.execute("CREATE TABLE users (id BIGINT, first_name VARCHAR, last_name VARCHAR, email VARCHAR, phone_number VARCHAR)");
        isolated.execute("CREATE TABLE trainer_client_links (trainer_id BIGINT, client_id BIGINT)");
        isolated.execute("CREATE TABLE exercise_log (id BIGINT, user_id BIGINT, date DATE, comments VARCHAR, duration_minutes INT)");
        isolated.update("INSERT INTO users VALUES (2, 'Linked', 'Client', 'linked@example.test', NULL)");
        isolated.update("INSERT INTO users VALUES (3, 'Private', 'Client', 'private@example.test', NULL)");
        isolated.update("INSERT INTO trainer_client_links VALUES (1, 2)");
        isolated.update("INSERT INTO exercise_log VALUES (4, 2, '2026-10-02', 'Strength session', 42)");
        isolated.update("INSERT INTO exercise_log VALUES (5, 3, '2026-10-02', 'Private session', 30)");
        var api = new MobileApiController(auth, isolated, applications);
        var detail = api.trainerClient("Bearer local-test", 2L);
        @SuppressWarnings("unchecked")
        var client = (Map<String, Object>) detail.get("client");
        @SuppressWarnings("unchecked")
        var logs = (List<Map<String, Object>>) detail.get("logs");
        assertThat(client).containsEntry("first_name", "Linked").containsEntry("email", "linked@example.test");
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst()).containsEntry("comments", "Strength session").containsEntry("duration_minutes", 42);
        assertThatThrownBy(() -> api.trainerClient("Bearer local-test", 3L))
            .isInstanceOfSatisfying(MobileApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(403));
    }
}
