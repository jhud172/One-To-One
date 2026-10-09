package uk.ac.cf._5.group14.One_To_One.CalendarTests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskService;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskWarningService;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyCompletionStatus;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyStreakDaySummary;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyStreakService;
import uk.ac.cf._5.group14.One_To_One.CalendarData.TaskAiGenerationService;
import uk.ac.cf._5.group14.One_To_One.CalendarData.TaskTemplateService;
import uk.ac.cf._5.group14.One_To_One.DayMode.DayModeService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalLinkService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.CalendarController;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleService;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.WorkoutScheduleService;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.WorkoutSessionService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import java.time.Clock;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationSseRegistry;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleAppliedRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DayOptimisationRepository;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarDayModelBuilder;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;

@WebMvcTest(CalendarController.class)
@ActiveProfiles("test")
public class CalendarDayViewStreakBarTooltipAccessibilityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CalendarTaskService taskService;

    @MockitoBean
    private CalendarTaskWarningService taskWarningService;

    @MockitoBean
    private TaskAiGenerationService taskAiGenerationService;

    @MockitoBean
    private TaskTemplateService taskTemplateService;

    @MockitoBean
    private ScheduleService scheduleService;

    @MockitoBean
    private ScheduleOccurrenceService scheduleOccurrenceService;

    @MockitoBean
    private WorkoutScheduleService workoutScheduleService;

    @MockitoBean
    private WorkoutSessionService workoutSessionService;

    @MockitoBean
    private AuthHelper authHelper;

    @MockitoBean
    private UserSettingsService userSettingsService;

    @MockitoBean
    private DailyStreakService dailyStreakService;

    @MockitoBean
    private DayModeService dayModeService;

    @MockitoBean
    private GoalLinkService goalLinkService;

    @MockitoBean
    private ScheduleAppliedRepository scheduleAppliedRepository;

    @MockitoBean
    private NotificationSseRegistry sseRegistry;

    @MockitoBean
    private DayOptimisationRepository dayOptimisationRepository;

    @MockitoBean
    private CalendarDayModelBuilder calendarDayModelBuilder;

    @Test
    public void streakPillTooltipShouldBeKeyboardAccessible() throws Exception {
        User sessionUser = new User();
        LocalDate date = LocalDate.of(2026, 1, 15);

        given(taskService.getTasks(eq(sessionUser), eq(date))).willReturn(Collections.emptyList());
        given(scheduleOccurrenceService.getOccurrencesForUserOnDate(eq(sessionUser), eq(date))).willReturn(Collections.emptyList());
        given(workoutScheduleService.findByUserAndDayOfWeek(eq(sessionUser), any(Integer.class))).willReturn(Collections.emptyList());
        given(taskTemplateService.listRecents(eq(sessionUser), eq(6))).willReturn(Collections.emptyList());
        given(taskTemplateService.listFavourites(eq(sessionUser))).willReturn(Collections.emptyList());
        given(taskTemplateService.listAll(eq(sessionUser))).willReturn(Collections.emptyList());

        given(dailyStreakService.calculateRange(eq(sessionUser), any(LocalDate.class), any(LocalDate.class), any(LocalDate.class)))
            .willReturn(
                List.of(
                    new DailyStreakDaySummary(
                        LocalDate.of(2026, 1, 15),
                        DailyCompletionStatus.GREEN,
                        100,
                        1,
                        1,
                        0,
                        0,
                        0
                    )
                )
            );

        mvc.perform(get("/calendar/day/2026-01-15").sessionAttr("user", sessionUser))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/calendar/day/2026-01-15\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-describedby=\"daily-streak-tooltip-2026-01-15\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("role=\"tooltip\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"daily-streak-tooltip-2026-01-15\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("aria-label=\"Thursday 15 January 2026: Complete, 1/1 completed\"")));
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
        AuthHelper authHelper() {
            return mock(AuthHelper.class);
        }

        @Bean
        PlatformSubscriptionService platformSubscriptionService() {
            return mock(PlatformSubscriptionService.class);
        }

        @Bean
        Clock systemClock() {
            return Clock.systemDefaultZone();
        }
    
        @Bean
        public DevModeProperties devModeProperties() {
            return new DevModeProperties();
        }
}
}
