package uk.ac.cf._5.group14.One_To_One.DashboardTests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckIn;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInRepository;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.Dashboard.DashboardController;
import uk.ac.cf._5.group14.One_To_One.Dashboard.DashboardSummaryService;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.DashboardSummaryDto;
import uk.ac.cf._5.group14.One_To_One.DayMode.DayMode;
import uk.ac.cf._5.group14.One_To_One.DayMode.DayModeService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.HealthRecordRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService;
import uk.ac.cf._5.group14.One_To_One.Messaging.ThreadMessageRepository;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLog;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogRepository;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardActionHubView;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedWorkout;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.TrainerAssignmentService;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryAssignedProgrammeView;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryAssignedWorkoutView;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsRepository;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

@WebMvcTest(DashboardController.class)
@ActiveProfiles("test")
class ClientDashboardMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private uk.ac.cf._5.group14.One_To_One.GymProfile.GymOperationsService gymOperationsService;

    @MockitoBean(name = "authHelper")
    private AuthHelper authHelper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DashboardSummaryService dashboardSummaryService;

    @MockitoBean
    private TrainerClientLinkRepository trainerClientLinkRepository;

    @MockitoBean
    private UserSettingsService userSettingsService;

    @MockitoBean
    private UserSettingsRepository userSettingsRepository;

    @MockitoBean
    private HealthRecordRepository healthRecordRepository;

    @MockitoBean
    private DailyNutritionLogRepository dailyNutritionLogRepository;

    @MockitoBean
    private GoalService goalService;

    @MockitoBean
    private TrainerAssignmentService trainerAssignmentService;

    @MockitoBean
    private TrainerLibraryService trainerLibraryService;

    @MockitoBean
    private WeeklyCheckInRepository weeklyCheckInRepository;

    @MockitoBean
    private MessageThreadRepository messageThreadRepository;

    @MockitoBean
    private ThreadMessageRepository threadMessageRepository;

    @MockitoBean
    private MessagingService messagingService;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private CalendarTaskRepository calendarTaskRepository;

    @MockitoBean
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @MockitoBean
    private DayModeService dayModeService;

    @MockitoBean(name = "platformSubscriptionService")
    private PlatformSubscriptionService platformSubscriptionService;

    private User client;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setId(1L);
        client.setFirstName("Ava");
        client.setLastName("Client");
        client.setUsername("ava");
        client.setBio("Client bio");
        client.setRole(Role.CLIENT);

        given(authHelper.getAuthenticatedUser()).willReturn(client);
        given(dashboardSummaryService.getSummary(client)).willReturn(buildSummary(true));
        given(goalService.listGoalsForViewer(any(User.class), any(), any(), any(), anyBoolean())).willReturn(List.of());
        given(userSettingsService.getOrCreate(any(User.class))).willReturn(null);
        given(userSettingsRepository.findById(any())).willReturn(Optional.empty());
        given(healthRecordRepository.findTop2ByUserOrderByBaselineDateDescIdDesc(any(User.class))).willReturn(List.of());
        given(trainerAssignmentService.listSchedulesForClient(1L)).willReturn(List.of());
        given(trainerAssignmentService.listWorkoutsForClient(1L)).willReturn(List.of());
        given(trainerLibraryService.getAssignedWorkoutsForClient(1L)).willReturn(List.of());
        given(trainerLibraryService.getAssignedProgrammesForClient(1L)).willReturn(List.of());
        given(weeklyCheckInRepository.findByClientIdOrderBySubmittedAtDesc(1L)).willReturn(List.of());
        given(trainerClientLinkRepository.findByClientUserIdAndStatusOrderByUpdatedAtDesc(1L, TrainerClientLinkStatus.REQUESTED)).willReturn(List.of());
        given(trainerClientLinkRepository.findActiveByClientId(1L)).willReturn(Optional.empty());
        given(threadMessageRepository.findTop30ByThread_IdOrderByIdDesc(any())).willReturn(List.of());
        given(dayModeService.determine(any(User.class), any(LocalDate.class))).willReturn(DayMode.REST_DAY);
        given(platformSubscriptionService.isPremium(any(), any(Clock.class))).willReturn(false);
        given(platformSubscriptionService.findByUserId(any())).willReturn(Optional.empty());
        given(platformSubscriptionService.getDaysUntilRenewal(any(), any(Clock.class))).willReturn(null);
        given(platformSubscriptionService.hasExpiredSubscription(any(), any(Clock.class))).willReturn(false);
        given(calendarTaskRepository.findByUserAndDateOrderByTime(any(User.class), any(LocalDate.class))).willReturn(List.of(
                buildTask("Review week progress", LocalTime.of(8, 0)),
                buildTask("Daily priority check-in", LocalTime.of(12, 30))));
        given(scheduleOccurrenceRepository.findByUserAndDate(any(User.class), any(LocalDate.class))).willReturn(List.of(
                buildWorkout("Demo Live Week Plan")));
    }

    @Test
    void dashboardRendersActiveTrainerContextAndKeepsLeftRailOrder() throws Exception {
        User trainer = buildTrainer();
        TrainerClientLink activeLink = buildActiveLink(44L, trainer.getId());
        MessageThread thread = new MessageThread(client.getId(), trainer.getId(), 44L, null);
        ReflectionTestUtils.setField(thread, "id", 55L);

        WeeklyCheckIn checkIn = new WeeklyCheckIn();
        checkIn.setTrainerId(trainer.getId());
        checkIn.setClientId(client.getId());
        checkIn.setWeekStartDate(LocalDate.of(2026, 3, 9));
        checkIn.setNextWeekFocus("Keep protein consistent");
        checkIn.setTrainerResponse("Aim for simple wins and keep the week tidy.");

        given(dashboardSummaryService.getSummary(client)).willReturn(buildSummary(true));
        given(trainerClientLinkRepository.findActiveByClientId(1L)).willReturn(Optional.of(activeLink));
        given(userRepository.findById(trainer.getId())).willReturn(Optional.of(trainer));
        given(weeklyCheckInRepository.findByClientIdOrderBySubmittedAtDesc(1L)).willReturn(List.of(checkIn));
        given(messageThreadRepository.findByLinkId(44L)).willReturn(Optional.of(thread));

        MvcResult result = mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"trainer-rail-card\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-dashboard-temperature-unit=\"CELSIUS\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-dashboard-weather-display-mode=\"VISUAL\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-dashboard-time-display-format=\"TWELVE_HOUR\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Keep protein consistent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/inbox/55")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-action-hub")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Local Time And Weather Context")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Using Your Local Area")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ambience-permission-message")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-ambience-graph-toggle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-selected-mode=\"temperature\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Weather outlook for the next 24 hours")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No Milestones Displayed")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("First coaching win ahead"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Track Body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Track Schedule")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-profile-preview-trigger")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("premium-module-card"))))
                .andReturn();

        String html = result.getResponse().getContentAsString();
        assertThat(html.indexOf("Explore Platform")).isLessThan(html.indexOf("Trainer Overview"));
        assertThat(html.indexOf("Trainer Overview")).isLessThan(html.indexOf("Keep Long-Term Progress Visible"));
    }

    @Test
    void dashboardFallsBackToPlanSummaryWhenTrainerHasNoNotes() throws Exception {
        User trainer = buildTrainer();
        TrainerClientLink activeLink = buildActiveLink(45L, trainer.getId());
        TrainerLibraryAssignedWorkoutView sharedWorkout = mock(TrainerLibraryAssignedWorkoutView.class);

        given(trainerClientLinkRepository.findActiveByClientId(1L)).willReturn(Optional.of(activeLink));
        given(userRepository.findById(trainer.getId())).willReturn(Optional.of(trainer));
        given(trainerLibraryService.getAssignedWorkoutsForClient(1L)).willReturn(List.of(sharedWorkout));
        given(messageThreadRepository.findByLinkId(45L)).willReturn(Optional.empty());
        MessageThread ensured = new MessageThread(client.getId(), trainer.getId(), 45L, null);
        ReflectionTestUtils.setField(ensured, "id", 77L);
        given(messagingService.ensureThreadForLink(activeLink)).willReturn(ensured);

        mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Plan status")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 shared workout currently supporting your dashboard.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/client/assigned-plan")));
    }

    @Test
    void dashboardRendersPendingTrainerState() throws Exception {
        User trainer = buildTrainer();
        TrainerClientLink pendingLink = new TrainerClientLink(client.getId(), trainer.getId(), TrainerClientLinkStatus.REQUESTED);
        pendingLink.setRequestedAt(Instant.parse("2026-03-10T08:00:00Z"));

        given(trainerClientLinkRepository.findByClientUserIdAndStatusOrderByUpdatedAtDesc(1L, TrainerClientLinkStatus.REQUESTED)).willReturn(List.of(pendingLink));
        given(userRepository.findById(trainer.getId())).willReturn(Optional.of(trainer));

        mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Request sent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Awaiting trainer approval")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review trainers")));
    }

    @Test
    void dashboardRemovesAiCoachAndKeepsEmptyTrainerStateWhenUserIsNotPremium() throws Exception {
        given(dashboardSummaryService.getSummary(client)).willReturn(buildSummary(false));

        mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No trainer connected")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Track Body")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Track Schedule")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-testid=\"profile-rail-card\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("premium-module-card"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-week-day-button")));
    }

    @Test
    void dashboardUsesPremiumOrbMarkupWhenClientIsPremium() throws Exception {
        given(platformSubscriptionService.isPremium(any(), any(Clock.class))).willReturn(true);

        mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nav-premium-badge premium-orb-badge")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Personal snapshot"))));
    }

    @Test
    void completedWorkoutsDoNotInventNutritionIntakeOrMissedMeals() throws Exception {
        MvcResult result = mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        var body = (DashboardController.TopActionCard) model.get("bodyActionCard");
        assertThat(body.headline()).isEqualTo("Ready to log");
        var hub = (ClientDashboardActionHubView) model.get("actionHub");
        var meal = hub.getAllCards().stream().filter(card -> card.getKey().equals("meal")).findFirst().orElseThrow();
        assertThat(meal.getStats()).contains("No intake logged today", "Last entry Not logged yet");
        assertThat(meal.isMissed()).isFalse();
        assertThat(meal.isTimed()).isFalse();
    }

    @Test
    void dashboardShowsSavedNutritionAndActualWeeklyNutritionCount() throws Exception {
        LocalDate today = LocalDate.of(2026, 3, 13);
        DailyNutritionLog log = new DailyNutritionLog();
        log.setDate(today);
        log.setCalories(2375);
        given(dailyNutritionLogRepository.findByUserAndDate(client, today)).willReturn(Optional.of(log));
        given(dailyNutritionLogRepository.findByUserAndDateBetweenOrderByDateAsc(client, today.minusDays(4), today))
                .willReturn(List.of(log));
        MvcResult result = mvc.perform(get("/dashboard").with(user("ava").roles("CLIENT")))
                .andExpect(status().isOk()).andReturn();
        var model = result.getModelAndView().getModel();
        assertThat(((DashboardController.TopActionCard) model.get("bodyActionCard")).headline()).isEqualTo("2375 kcal");
        var hub = (ClientDashboardActionHubView) model.get("actionHub");
        assertThat(hub.getAllCards().stream().filter(card -> card.getKey().equals("meal")).findFirst().orElseThrow().getStats())
                .contains("2375 kcal logged", "Last entry Today");
        @SuppressWarnings("unchecked")
        var weekly = (List<DashboardController.WeeklySummaryCard>) model.get("weeklySummaryCards");
        assertThat(weekly.stream().filter(card -> card.subtitle().equals("Nutrition logs recorded")).findFirst().orElseThrow().value())
                .isEqualTo("1");
    }

    @Test
    void trainerDashboardUsesOwnedRelationshipsAndPersonalDisplayName() throws Exception {
        User trainer = buildTrainer();
        given(authHelper.getAuthenticatedUser()).willReturn(trainer);
        given(dashboardSummaryService.getSummary(trainer)).willReturn(buildSummary(true));
        given(trainerClientLinkRepository.findByTrainerUserIdAndStatusOrderByUpdatedAtDesc(trainer.getId(), TrainerClientLinkStatus.ACTIVE))
                .willReturn(List.of(buildActiveLink(90L, trainer.getId())));
        given(trainerClientLinkRepository.findPendingByTrainerId(trainer.getId()))
                .willReturn(List.of(new TrainerClientLink(22L, trainer.getId(), TrainerClientLinkStatus.REQUESTED)));
        MvcResult result = mvc.perform(get("/trainer/dashboard").with(user(trainer.getUsername()).roles("TRAINER")))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel())
                .containsEntry("activeClientCount", 1).containsEntry("pendingClientCount", 1)
                .containsEntry("trainerDisplayName", trainer.getFirstName());
        String html = result.getResponse().getContentAsString();
        assertThat(html.indexOf("trainer-client-priorities")).isLessThan(html.indexOf("trainer-personal-schedule"));
        assertThat(html).contains("/trainer/clients#clientRequests", "Your planned workouts");
    }

    private DashboardSummaryDto buildSummary(boolean premium) {
        LocalDate today = LocalDate.of(2026, 3, 13);
        List<DashboardSummaryDto.WeekDaySummary> week = List.of(
                new DashboardSummaryDto.WeekDaySummary(
                        today, "Fri", "13", 2, 1, 1, 0, true, false, "today",
                        List.of("Daily priority check-in", "Review week progress"),
                        List.of("Demo Live Week Plan")),
                new DashboardSummaryDto.WeekDaySummary(
                        today.plusDays(1), "Sat", "14", 1, 1, 0, 0, false, true, "upcoming",
                        List.of("Mobility check"),
                        List.of("Recovery circuit")),
                new DashboardSummaryDto.WeekDaySummary(
                        today.plusDays(2), "Sun", "15", 0, 1, 0, 0, false, false, "upcoming",
                        List.of(),
                        List.of("Tempo run")));
        return new DashboardSummaryDto(2, 1, today, week, 1, 3, 0, premium, 2, 1, "Charlie context");
    }

    private User buildTrainer() {
        User trainer = new User();
        trainer.setId(99L);
        trainer.setFirstName("Mia");
        trainer.setLastName("Trainer");
        trainer.setUsername("miafit");
        trainer.setBio("Strength and nutrition coach focused on simple, repeatable plans.");
        trainer.setRole(Role.TRAINER);
        return trainer;
    }

    private TrainerClientLink buildActiveLink(Long linkId, Long trainerId) {
        TrainerClientLink link = new TrainerClientLink(client.getId(), trainerId, TrainerClientLinkStatus.ACTIVE);
        ReflectionTestUtils.setField(link, "id", linkId);
        ReflectionTestUtils.setField(link, "updatedAt", Instant.parse("2026-03-13T08:00:00Z"));
        link.setActivatedAt(Instant.parse("2026-03-01T08:00:00Z"));
        return link;
    }

    private CalendarTask buildTask(String title, LocalTime time) {
        CalendarTask task = new CalendarTask();
        task.setUser(client);
        task.setTitle(title);
        task.setDate(LocalDate.of(2026, 3, 13));
        task.setTime(time);
        task.setCompleted(false);
        return task;
    }

    private ScheduleOccurrence buildWorkout(String scheduleName) {
        ScheduleOccurrence occurrence = new ScheduleOccurrence();
        occurrence.setUser(client);
        occurrence.setDate(LocalDate.of(2026, 3, 13));
        occurrence.setScheduleName(scheduleName);
        occurrence.setCompleted(false);
        return occurrence;
    }

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean("testSecurityFilterChain")
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }

        @Bean
        Clock systemClock() {
            return Clock.fixed(Instant.parse("2026-03-13T12:00:00Z"), ZoneId.of("UTC"));
        }

        @Bean
        DevModeProperties devModeProperties() {
            return new DevModeProperties();
        }
    }
}
