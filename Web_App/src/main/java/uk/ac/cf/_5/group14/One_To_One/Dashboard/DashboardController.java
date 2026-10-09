package uk.ac.cf._5.group14.One_To_One.Dashboard;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckIn;
import uk.ac.cf._5.group14.One_To_One.Checkins.WeeklyCheckInRepository;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardActionCardView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardActionHubView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardActionUpcomingView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardIdentityView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardProfileRailView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardTrainerActivityItemView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardTrainerContextView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardTrainerMessagePanelView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardTrainerMessageView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.ClientDashboardTrainerRailView;
import uk.ac.cf._5.group14.One_To_One.Dashboard.dto.DashboardSummaryDto;
import uk.ac.cf._5.group14.One_To_One.Goals.Goal;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalStatus;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalTimeframe;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.HealthRecord;
import uk.ac.cf._5.group14.One_To_One.HealthDataInput.HealthRecordRepository;
import uk.ac.cf._5.group14.One_To_One.HomePage.MiniWeekDay;
import uk.ac.cf._5.group14.One_To_One.Level.LevelProgress;
import uk.ac.cf._5.group14.One_To_One.Messaging.Message;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageType;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService;
import uk.ac.cf._5.group14.One_To_One.Messaging.ThreadMessageRepository;
import uk.ac.cf._5.group14.One_To_One.Notifications.Notification;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLog;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogRepository;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscription;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedSchedule;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.AssignedWorkout;
import uk.ac.cf._5.group14.One_To_One.TrainerAssignments.TrainerAssignmentService;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryAssignedProgrammeView;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryAssignedWorkoutView;
import uk.ac.cf._5.group14.One_To_One.TrainerLibrary.TrainerLibraryService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettings;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsRepository;
import uk.ac.cf._5.group14.One_To_One.UserSettings.UserSettingsService;
import uk.ac.cf._5.group14.One_To_One.UserSettings.TimeDisplayFormatPreference;
import uk.ac.cf._5.group14.One_To_One.UserSettings.WeatherDisplayModePreference;
import uk.ac.cf._5.group14.One_To_One.UserSettings.WeatherTemperatureUnitPreference;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymOperationsService;

@Controller
public class DashboardController {

    private static final DateTimeFormatter PRETTY_DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter FULL_DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserRepository userRepository;
    private final DashboardSummaryService dashboardSummaryService;
    private final TrainerClientLinkRepository trainerClientLinkRepository;
    private final UserSettingsService userSettingsService;
    private final UserSettingsRepository userSettingsRepository;
    private final HealthRecordRepository healthRecordRepository;
    private final DailyNutritionLogRepository dailyNutritionLogRepository;
    private final GoalService goalService;
    private final TrainerAssignmentService trainerAssignmentService;
    private final TrainerLibraryService trainerLibraryService;
    private final WeeklyCheckInRepository weeklyCheckInRepository;
    private final MessageThreadRepository messageThreadRepository;
    private final ThreadMessageRepository threadMessageRepository;
    private final MessagingService messagingService;
    private final NotificationService notificationService;
    private final PlatformSubscriptionService platformSubscriptionService;
    private final CalendarTaskRepository calendarTaskRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final Clock clock;
    private final GymOperationsService gymOperationsService;
    private final TrainerDashboardService trainerDashboardService;

    public DashboardController(AuthHelper authHelper,
                               UserService userService,
                               UserRepository userRepository,
                               DashboardSummaryService dashboardSummaryService,
                               TrainerClientLinkRepository trainerClientLinkRepository,
                               UserSettingsService userSettingsService,
                               UserSettingsRepository userSettingsRepository,
                               HealthRecordRepository healthRecordRepository,
                               DailyNutritionLogRepository dailyNutritionLogRepository,
                               GoalService goalService,
                               TrainerAssignmentService trainerAssignmentService,
                               TrainerLibraryService trainerLibraryService,
                               WeeklyCheckInRepository weeklyCheckInRepository,
                               MessageThreadRepository messageThreadRepository,
                               ThreadMessageRepository threadMessageRepository,
                               MessagingService messagingService,
                               NotificationService notificationService,
                               PlatformSubscriptionService platformSubscriptionService,
                               CalendarTaskRepository calendarTaskRepository,
                               ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                               Clock clock,
                               GymOperationsService gymOperationsService,
                               TrainerDashboardService trainerDashboardService) {
        this.authHelper = authHelper;
        this.userService = userService;
        this.userRepository = userRepository;
        this.dashboardSummaryService = dashboardSummaryService;
        this.trainerClientLinkRepository = trainerClientLinkRepository;
        this.userSettingsService = userSettingsService;
        this.userSettingsRepository = userSettingsRepository;
        this.healthRecordRepository = healthRecordRepository;
        this.dailyNutritionLogRepository = dailyNutritionLogRepository;
        this.goalService = goalService;
        this.trainerAssignmentService = trainerAssignmentService;
        this.trainerLibraryService = trainerLibraryService;
        this.weeklyCheckInRepository = weeklyCheckInRepository;
        this.messageThreadRepository = messageThreadRepository;
        this.threadMessageRepository = threadMessageRepository;
        this.messagingService = messagingService;
        this.notificationService = notificationService;
        this.platformSubscriptionService = platformSubscriptionService;
        this.calendarTaskRepository = calendarTaskRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.clock = clock;
        this.gymOperationsService = gymOperationsService;
        this.trainerDashboardService = trainerDashboardService;
    }

    private User currentUserOrThrow(Authentication authentication) {
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser != null) {
            return sessionUser;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("Not authenticated");
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("User not found");
        }
        return user;
    }

    @GetMapping("/dashboard")
    public String dashboardRouter(Authentication authentication, Model model) {
        if (SecurityUtils.hasRole(authentication, "SUPER_ADMIN") || SecurityUtils.hasRole(authentication, "PLATFORM_ADMIN")) {
            return "redirect:/admin/dashboard";
        }
        if (SecurityUtils.hasRole(authentication, "GYM_ADMIN")) {
            return "redirect:/gym/dashboard";
        }
        if (SecurityUtils.hasRole(authentication, "TRAINER")) {
            return "redirect:/trainer/dashboard";
        }

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("disableChatHistory", true);
        model.addAttribute("disableGlobalChatbot", true);
        model.addAttribute("compactTopContent", true);

        User user = currentUserOrThrow(authentication);
        DashboardSummaryDto summary = dashboardSummaryService.getSummary(user);
        LocalDate today = summary.getToday();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(clock), clock.getZone());

        List<MiniWeekDay> miniWeek = summary.getWeek().stream()
                .map(day -> new MiniWeekDay(
                        day.getDate(),
                        day.getLabel(),
                        day.getDate().format(PRETTY_DATE_FMT),
                        day.getTasksCount(),
                        day.getWorkoutsCount(),
                        day.isToday(),
                        day.isTomorrow(),
                        day.getDayStatus(),
                        day.getWorkoutsCount() > 0,
                        day.getTaskTitles(),
                        day.getWorkoutTitles()))
                .toList();

        MiniWeekDay todayDay = !miniWeek.isEmpty()
                ? miniWeek.get(0)
                : new MiniWeekDay(today, "Today", today.format(PRETTY_DATE_FMT), summary.getTasksDueToday(),
                summary.getWorkoutsDueToday(), true, false, "today", summary.getWorkoutsDueToday() > 0, List.of(), List.of());
        List<WeekPreviewDay> weekDays = buildWeekPreviewDays(user, summary);

        int todayTasks = todayDay.taskCount();
        int todayWorkouts = todayDay.scheduledCount();
        int todayTotal = todayTasks + todayWorkouts;
        String todayPath = pathForDate(today);

        List<CalendarTask> openTasks = calendarTaskRepository.findByUserAndDateOrderByTime(user, today).stream()
                .filter(task -> !Boolean.TRUE.equals(task.getCompleted()) && !task.isMissed())
                .sorted(Comparator.comparing(task -> task.getTime() != null ? task.getTime() : LocalTime.MAX))
                .toList();
        List<ScheduleOccurrence> openWorkouts = scheduleOccurrenceRepository.findByUserAndDate(user, today).stream()
                .filter(item -> !item.isCompleted() && !item.isMissed())
                .toList();

        int weekTasksTotal = summary.getWeek().stream().mapToInt(DashboardSummaryDto.WeekDaySummary::getTasksCount).sum();
        int weekTasksCompleted = summary.getWeek().stream().mapToInt(DashboardSummaryDto.WeekDaySummary::getTasksCompletedCount).sum();
        int weekTasksRemaining = Math.max(weekTasksTotal - weekTasksCompleted, 0);
        int weekWorkoutsTotal = summary.getWeek().stream().mapToInt(DashboardSummaryDto.WeekDaySummary::getWorkoutsCount).sum();
        int weekWorkoutsCompleted = summary.getWeek().stream().mapToInt(DashboardSummaryDto.WeekDaySummary::getWorkoutsCompletedCount).sum();
        int weekWorkoutsRemaining = Math.max(weekWorkoutsTotal - weekWorkoutsCompleted, 0);

        UserSettings settings = userSettingsService.getOrCreate(user);
        TimeDisplayFormatPreference timeDisplayFormat = resolveTimeDisplayFormat(settings);
        boolean isPremium = platformSubscriptionService.isPremium(user.getId(), clock);
        Optional<DailyNutritionLog> todayNutrition = dailyNutritionLogRepository.findByUserAndDate(user, today);
        int intakeCalories = todayNutrition.map(DailyNutritionLog::getCalories).orElse(0);
        boolean intakeLoggedToday = todayNutrition.isPresent();
        String intakeLastLogged = intakeLoggedToday ? "Today" : dailyNutritionLogRepository
                .findTopByUserAndDateLessThanEqualOrderByDateDescIdDesc(user, today)
                .map(log -> log.getDate().format(PRETTY_DATE_FMT)).orElse("Not logged yet");
        LocalDate nutritionWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        int nutritionLogsThisWeek = dailyNutritionLogRepository
                .findByUserAndDateBetweenOrderByDateAsc(user, nutritionWeekStart, today).size();

        List<Goal> userGoals = goalService.listGoalsForViewer(user, null, null, null, false);
        long activeGoalsCount = userGoals.stream().filter(goal -> goal.getStatus() == GoalStatus.ACTIVE).count();
        long completedGoalsCount = userGoals.stream().filter(goal -> goal.getStatus() == GoalStatus.COMPLETED).count();

        model.addAttribute("summary", summary);
        model.addAttribute("miniWeek", miniWeek);
        model.addAttribute("todayPath", todayPath);
        model.addAttribute("todayTasks", todayTasks);
        model.addAttribute("todayWorkouts", todayWorkouts);
        model.addAttribute("leftToComplete", todayTotal);
        model.addAttribute("activeGoalsCount", activeGoalsCount);
        model.addAttribute("completedGoalsCount", completedGoalsCount);
        model.addAttribute("goalSections", buildGoalSections(userGoals, today));
        model.addAttribute("weeklySummaryCards",
                buildWeeklySummaryCards(user, settings, summary, weekWorkoutsCompleted, weekWorkoutsRemaining,
                        weekTasksCompleted, weekTasksRemaining, weekTasksTotal, nutritionLogsThisWeek));
        model.addAttribute("dashboardAmbience",
                new DashboardAmbienceView(
                        settings == null || settings.isDashboardImmersionEnabled(),
                        trimToNull(settings != null ? settings.getDashboardWeatherPreset() : null) != null
                                ? settings.getDashboardWeatherPreset().trim().toLowerCase(Locale.ROOT)
                                : "auto",
                        settings != null && settings.getWeatherTemperatureUnit() != null
                                ? settings.getWeatherTemperatureUnit().name()
                                : WeatherTemperatureUnitPreference.CELSIUS.name(),
                        settings != null && settings.getWeatherDisplayMode() != null
                                ? settings.getWeatherDisplayMode().name()
                                : WeatherDisplayModePreference.VISUAL.name()));
        model.addAttribute("dashboardTimeDisplayFormat", timeDisplayFormat.name());
        model.addAttribute("profileRail",
                buildProfileRailView(user, settings, isPremium, buildPremiumTooltip(user.getId()), summary, todayTotal));
        model.addAttribute("trainerRail", buildTrainerRailView(user, timeDisplayFormat));
        model.addAttribute("bodyActionCard",
                buildBodyActionCard(settings, intakeCalories, intakeLoggedToday, intakeLastLogged, nutritionLogsThisWeek));
        model.addAttribute("scheduleActionCard",
                buildScheduleActionCard(todayTasks, todayWorkouts, todayTotal, todayPath, todayDay));
        model.addAttribute("actionHub",
                buildActionHubView(today, todayPath, miniWeek, openTasks, openWorkouts, intakeCalories,
                        intakeLastLogged, intakeLoggedToday, now, timeDisplayFormat));
        model.addAttribute("weekDays", weekDays);

        return "client-views/dashboard/client-dashboard";
    }

    @GetMapping("/dashboard/trainer-thread/{threadId}/messages")
    @ResponseBody
    public DashboardMessageResponse trainerThreadMessages(@PathVariable Long threadId,
                                                          @RequestParam(required = false) Long beforeId,
                                                          Authentication authentication) {
        User user = currentUserOrThrow(authentication);
        TimeDisplayFormatPreference timeDisplayFormat = resolveTimeDisplayFormat(userSettingsService.getOrCreate(user));
        MessageThread thread = messagingService.getThreadForUser(threadId, user.getId());
        List<Message> batch = beforeId != null
                ? threadMessageRepository.findTop30ByThread_IdAndIdLessThanOrderByIdDesc(thread.getId(), beforeId)
                : threadMessageRepository.findTop30ByThread_IdOrderByIdDesc(thread.getId());
        boolean hasMore = batch.size() == 30;
        Collections.reverse(batch);
        return new DashboardMessageResponse(toTrainerMessages(batch, user.getId(), timeDisplayFormat), hasMore);
    }

    @PostMapping("/dashboard/trainer-thread/{threadId}/messages")
    @ResponseBody
    public DashboardSendMessageResponse sendTrainerThreadMessage(@PathVariable Long threadId,
                                                                 @RequestParam("body") String body,
                                                                 Authentication authentication) {
        User user = currentUserOrThrow(authentication);
        String clean = trimToNull(body);
        if (clean == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message cannot be empty");
        }
        try {
            messagingService.sendMessage(threadId, user.getId(), MessageType.TEXT, clean);
            Message saved = threadMessageRepository.findTop1ByThread_IdOrderByCreatedAtDesc(threadId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Message not saved"));
            TimeDisplayFormatPreference timeDisplayFormat = resolveTimeDisplayFormat(userSettingsService.getOrCreate(user));
            return new DashboardSendMessageResponse(toTrainerMessage(saved, user.getId(), timeDisplayFormat));
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Messaging is unavailable");
        }
    }

    @PostMapping("/dashboard/ambience-preference")
    @ResponseBody
    public DashboardAmbienceView saveDashboardAmbiencePreference(@RequestParam("enabled") boolean enabled,
                                                                 @RequestParam("weather") String weather,
                                                                 Authentication authentication) {
        User user = currentUserOrThrow(authentication);
        UserSettings settings = userSettingsService.getOrCreate(user);
        settings.setDashboardImmersionEnabled(enabled);
        settings.setDashboardWeatherPreset(trimToNull(weather) != null ? weather.trim().toLowerCase(Locale.ROOT) : "auto");
        userSettingsRepository.save(settings);
        return new DashboardAmbienceView(
                settings.isDashboardImmersionEnabled(),
                settings.getDashboardWeatherPreset(),
                settings.getWeatherTemperatureUnit() != null
                        ? settings.getWeatherTemperatureUnit().name()
                        : WeatherTemperatureUnitPreference.CELSIUS.name(),
                settings.getWeatherDisplayMode() != null
                        ? settings.getWeatherDisplayMode().name()
                        : WeatherDisplayModePreference.VISUAL.name());
    }

    @GetMapping("/dashboard/trainer-activity")
    @ResponseBody
    public List<ClientDashboardTrainerActivityItemView> trainerActivity(Authentication authentication) {
        User client = currentUserOrThrow(authentication);
        TimeDisplayFormatPreference timeDisplayFormat = resolveTimeDisplayFormat(userSettingsService.getOrCreate(client));
        TrainerClientLink activeLink = trainerClientLinkRepository.findActiveByClientId(client.getId()).orElse(null);
        if (activeLink == null) {
            return List.of();
        }

        User trainer = userRepository.findById(activeLink.getTrainerUserId()).orElse(null);
        MessageThread thread = resolveTrainerThread(activeLink);
        return buildTrainerActivityItems(client, trainer, thread, timeDisplayFormat);
    }

    @GetMapping("/client/dashboard")
    public String clientDashboard() {
        return "redirect:/dashboard";
    }

    @GetMapping({"/dashboard/public", "/client/dashboard/public"})
    public String clientDashboardPublic(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        model.addAttribute("pageTitle", "Coaching Workspace");
        model.addAttribute("pageDescription", "Preview the connected One To One coaching workspace for sessions, feedback and visible progress.");
        return "public-views/dashboard/client-dashboard-public";
    }

    @GetMapping("/trainer/dashboard")
    public String trainerDashboard(Authentication authentication, Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("disableChatHistory", true);
        User trainer = currentUserOrThrow(authentication);
        DashboardSummaryDto summary = dashboardSummaryService.getSummary(trainer);
        model.addAttribute("summary", summary);
        model.addAttribute("trainerDisplayName", trimToNull(trainer.getFirstName()) != null
                ? trainer.getFirstName().trim() : trainer.getUsername());
        boolean trainerReady = trainer.isEnabled() && trainer.isTrainerVerified();
        model.addAttribute("trainerReady", trainerReady);
        var coaching = trainerReady ? trainerDashboardService.forTrainer(trainer)
                : new TrainerDashboardService.Summary(0, 0, 0, 0, 0, List.of());
        model.addAttribute("coaching", coaching);
        model.addAttribute("activeClientCount", coaching.activeClients());
        model.addAttribute("pendingClientCount", coaching.pendingRequests());
        return "trainer-views/dashboard/trainer-dashboard";
    }

    @GetMapping("/gym/dashboard")
    public String gymDashboard(Authentication authentication, Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("disableChatHistory", true);
        User admin = currentUserOrThrow(authentication);
        DashboardSummaryDto summary = dashboardSummaryService.getSummary(admin);
        model.addAttribute("summary", summary);
        model.addAttribute("gymOperations", gymOperationsService.forAdmin(admin));
        return "gym-views/dashboard/gym-dashboard";
    }

    private TopActionCard buildBodyActionCard(UserSettings settings,
                                              int intakeCalories,
                                              boolean intakeLoggedToday,
                                              String intakeLastLogged,
                                              int logsThisWeekCount) {
        Integer calorieTarget = settings != null ? settings.getMacroTargetCalories() : null;
        Integer proteinTarget = settings != null ? settings.getMacroTargetProtein() : null;
        String headline = intakeLoggedToday ? intakeCalories + " kcal" : "Ready to log";
        String subhead = calorieTarget != null ? "Target " + calorieTarget + " kcal" : "Set a calorie target";
        List<String> chips = new ArrayList<>();
        chips.add(intakeLoggedToday ? "Last log " + intakeLastLogged : "No entries today");
        chips.add(proteinTarget != null ? proteinTarget + "g protein goal" : "Add body targets");
        chips.add(logsThisWeekCount + " nutrition logs this week");
        return new TopActionCard(
                "Daily tracking",
                "Track Body",
                "/health-record/list",
                "BODY",
                headline,
                subhead,
                intakeLoggedToday
                        ? "Keep body and nutrition inputs current so the rest of the dashboard stays sharp."
                        : "Log weight, food, or recovery data first to anchor the rest of the day.",
                chips);
    }

    private TopActionCard buildScheduleActionCard(int todayTasks,
                                                  int todayWorkouts,
                                                  int todayTotal,
                                                  String todayPath,
                                                  MiniWeekDay todayDay) {
        String headline = todayTotal > 0 ? todayTotal + " items live today" : "Day is clear";
        String subhead = todayTasks + " tasks | " + todayWorkouts + " workouts";
        List<String> chips = new ArrayList<>();
        chips.add(todayTotal > 0 ? "Open today and keep the flow moving" : "Use the planner to prep ahead");
        if (!todayDay.taskTitles().isEmpty()) {
            chips.add("Next task " + todayDay.taskTitles().get(0));
        }
        if (!todayDay.workoutTitles().isEmpty()) {
            chips.add("Workout " + todayDay.workoutTitles().get(0));
        }
        return new TopActionCard(
                "Planner",
                "Track Schedule",
                todayPath,
                "PLAN",
                headline,
                subhead,
                todayTotal > 0
                        ? "Your day plan lives here. Open it first when you want the clearest next move."
                        : "Your board is light today, so use the planner to set up the next active block.",
                chips.stream().limit(3).toList());
    }

    private ClientDashboardActionHubView buildActionHubView(LocalDate today,
                                                            String todayPath,
                                                            List<MiniWeekDay> miniWeek,
                                                            List<CalendarTask> openTasks,
                                                            List<ScheduleOccurrence> openWorkouts,
                                                            int intakeCalories,
                                                            String intakeLastLogged,
                                                            boolean intakeLoggedToday,
                                                            LocalDateTime now,
                                                            TimeDisplayFormatPreference timeDisplayFormat) {
        List<ClientDashboardActionCardView> cards = new ArrayList<>();

        if (!openTasks.isEmpty()) {
            CalendarTask firstTask = openTasks.get(0);
            boolean missed = firstTask.getTime() != null && firstTask.getTime().isBefore(now.toLocalTime());
            String targetIso = firstTask.getTime() != null
                    ? LocalDateTime.of(today, firstTask.getTime()).atZone(clock.getZone()).toInstant().toString()
                    : null;
            cards.add(new ClientDashboardActionCardView(
                    "task",
                    "Task",
                    trimToNull(firstTask.getTitle()) != null ? firstTask.getTitle() : "Open today",
                    buildTaskDescription(firstTask, openTasks.size(), now.toLocalTime(), timeDisplayFormat),
                    todayPath,
                    "Open Day",
                    "outline",
                    "TASK",
                    resolveTaskBadge(firstTask, now.toLocalTime()),
                    buildTaskStats(openTasks, timeDisplayFormat),
                    "Route",
                    "Clear the most time-sensitive task before the rest of the board fans out.",
                    resolveTaskPriority(firstTask, now.toLocalTime()),
                    firstTask.getTime() != null ? formatLocalTime(firstTask.getTime(), timeDisplayFormat) : String.valueOf(openTasks.size()),
                    firstTask.getTime() != null ? "scheduled" : "tasks open",
                    targetIso,
                    firstTask.getTime() != null,
                    missed,
                    missed ? "Missed" : (firstTask.getTime() != null ? "Timed task" : "Open task")));
        }

        if (!openWorkouts.isEmpty()) {
            String workoutTitle = resolveWorkoutTitle(openWorkouts.get(0));
            cards.add(new ClientDashboardActionCardView(
                    "workout",
                    "Workout",
                    workoutTitle,
                    openWorkouts.size() > 1
                            ? openWorkouts.size() + " workouts are sitting in today's plan. Start the first session and the rest of the day becomes clearer."
                            : "Your workout is already scheduled. Open the day, review the session, and start when ready.",
                    workoutLaunchPath(openWorkouts.get(0)),
                    "Start Workout",
                    "emerald",
                    "WORK",
                    "Today",
                    List.of(openWorkouts.size() + " workout" + (openWorkouts.size() == 1 ? "" : "s") + " queued", "Planner ready"),
                    "Focus",
                    "Training stays highest-value when it remains visible before lower-friction admin tasks.",
                    !openTasks.isEmpty() ? 360 : 620,
                    String.valueOf(openWorkouts.size()),
                    "workouts waiting",
                    null,
                    false,
                    false,
                    "Queued"));
        }

        cards.add(new ClientDashboardActionCardView(
                "meal",
                "Nutrition",
                intakeLoggedToday ? "Review today’s nutrition" : "Log today’s nutrition",
                intakeLoggedToday ? "Your saved intake is available to review or update."
                        : "No nutrition record is saved for today. Add your intake when you are ready.",
                "/nutrition",
                "Log Meal",
                "soft",
                "MEAL",
                intakeLoggedToday ? "Saved" : "Ready",
                List.of(intakeLoggedToday ? intakeCalories + " kcal logged" : "No intake logged today", "Last entry " + intakeLastLogged),
                "Baseline",
                "Nutrition context makes the rest of the dashboard easier to interpret.",
                180,
                intakeLoggedToday ? intakeCalories + " kcal" : "Ready to log",
                "today’s nutrition",
                null,
                false,
                false,
                intakeLoggedToday ? "Saved intake" : "Nutrition log"));

        cards.sort(Comparator.comparingInt(ClientDashboardActionCardView::getPriority).reversed());
        ClientDashboardActionCardView primaryCard = cards.isEmpty() ? null : cards.get(0);
        List<ClientDashboardActionCardView> secondaryCards = cards.size() > 1 ? cards.subList(1, cards.size()) : List.of();
        ClientDashboardActionUpcomingView upcoming = buildActionUpcomingView(today, todayPath, miniWeek, openTasks, openWorkouts, now, timeDisplayFormat);

        return new ClientDashboardActionHubView(
                "What should I do next?",
                primaryCard != null
                        ? "One clear next move is surfaced first. Switch to all if you want the full board."
                        : "Nothing urgent is blocking the day, so use the planner or nutrition log to stay ahead.",
                !cards.isEmpty(),
                "Nothing urgent right now",
                "Your board is clear enough to choose deliberately. Open the planner to prep ahead or log a meal to keep your day complete.",
                upcoming,
                primaryCard,
                secondaryCards,
                List.copyOf(cards));
    }

    private ClientDashboardActionUpcomingView buildActionUpcomingView(LocalDate today,
                                                                     String todayPath,
                                                                     List<MiniWeekDay> miniWeek,
                                                                     List<CalendarTask> openTasks,
                                                                     List<ScheduleOccurrence> openWorkouts,
                                                                     LocalDateTime now,
                                                                     TimeDisplayFormatPreference timeDisplayFormat) {
        LocalTime currentTime = now.toLocalTime();
        CalendarTask nextTimedTask = openTasks.stream()
                .filter(task -> task.getTime() != null && task.getTime().isAfter(currentTime))
                .findFirst()
                .orElse(null);
        if (nextTimedTask != null) {
            LocalDateTime target = LocalDateTime.of(today, nextTimedTask.getTime());
            return new ClientDashboardActionUpcomingView(
                    true,
                    "Timed next",
                    trimToNull(nextTimedTask.getTitle()) != null ? nextTimedTask.getTitle() : "Upcoming task",
                    "Due today at " + formatLocalTime(nextTimedTask.getTime(), timeDisplayFormat) + ".",
                    todayPath,
                    target.atZone(clock.getZone()).toInstant().toString(),
                    "Live countdown",
                    true);
        }
        if (!openWorkouts.isEmpty()) {
            return new ClientDashboardActionUpcomingView(
                    true,
                    "Workout queue",
                    resolveWorkoutTitle(openWorkouts.get(0)),
                    "This session is already in today's plan and ready when you are.",
                    workoutLaunchPath(openWorkouts.get(0)),
                    null,
                    "Today",
                    false);
        }
        MiniWeekDay nextLoadedDay = miniWeek.stream()
                .filter(day -> !day.date().isBefore(today))
                .filter(day -> day.taskCount() + day.scheduledCount() > 0)
                .skip(1)
                .findFirst()
                .orElse(null);
        if (nextLoadedDay == null) {
            return new ClientDashboardActionUpcomingView(false, null, null, null, null, null, null, false);
        }
        String title = !nextLoadedDay.workoutTitles().isEmpty()
                ? nextLoadedDay.workoutTitles().get(0)
                : (!nextLoadedDay.taskTitles().isEmpty() ? nextLoadedDay.taskTitles().get(0) : "Upcoming day");
        return new ClientDashboardActionUpcomingView(
                true,
                nextLoadedDay.scheduledCount() > 0 ? "Next workout day" : "Next task day",
                title,
                nextLoadedDay.prettyDate() + " has " + nextLoadedDay.taskCount() + " tasks and "
                        + nextLoadedDay.scheduledCount() + " workouts lined up.",
                pathForDate(nextLoadedDay.date()),
                null,
                nextLoadedDay.prettyDate(),
                false);
    }

    private ClientDashboardProfileRailView buildProfileRailView(User user,
                                                                UserSettings settings,
                                                                boolean isPremium,
                                                                String premiumTooltip,
                                                                DashboardSummaryDto summary,
                                                                int todayTotal) {
        ClientDashboardIdentityView identity = buildIdentityView(user, settings, isPremium, premiumTooltip);
        LevelProgress progress = user != null ? user.getLevelProgress() : null;
        int points = progress != null ? progress.getPoints() : 0;
        int level = progress != null ? progress.getLevel() : 1;
        List<String> milestones = parseProfileMilestones(settings != null ? settings.getProfileMilestoneKeys() : null).stream()
                .map(this::mapMilestoneTitle)
                .toList();
        String bio = trimToNull(user != null ? user.getBio() : null);
        return new ClientDashboardProfileRailView(
                identity,
                bio != null ? bio : "Add a short bio so your dashboard has more identity at a glance.",
                points,
                level,
                milestones,
                "Daily focus",
                todayTotal > 0
                        ? "Keep the next action visible and the rest of the day gets easier to follow."
                        : "Your plan is light today, so use the extra space to prepare tomorrow cleanly.",
                summary.getWorkoutStreak());
    }

    private ClientDashboardTrainerRailView buildTrainerRailView(User client, TimeDisplayFormatPreference timeDisplayFormat) {
        TrainerClientLink activeLink = trainerClientLinkRepository.findActiveByClientId(client.getId()).orElse(null);
        TrainerClientLink pendingLink = trainerClientLinkRepository
                .findByClientUserIdAndStatusOrderByUpdatedAtDesc(client.getId(), TrainerClientLinkStatus.REQUESTED)
                .stream()
                .findFirst()
                .orElse(null);

        if (activeLink != null) {
            User trainer = userRepository.findById(activeLink.getTrainerUserId()).orElse(null);
            UserSettings trainerSettings = trainer != null ? userSettingsRepository.findById(trainer.getId()).orElse(null) : null;
            List<AssignedWorkout> assignedWorkouts = trainerAssignmentService.listWorkoutsForClient(client.getId());
            List<AssignedSchedule> assignedSchedules = trainerAssignmentService.listSchedulesForClient(client.getId());
            List<TrainerLibraryAssignedWorkoutView> sharedWorkouts = trainerLibraryService.getAssignedWorkoutsForClient(client.getId());
            List<TrainerLibraryAssignedProgrammeView> sharedProgrammes = trainerLibraryService.getAssignedProgrammesForClient(client.getId());
            LevelProgress trainerProgress = trainer != null ? trainer.getLevelProgress() : null;
            MessageThread thread = resolveTrainerThread(activeLink);

            String planHref = resolveTrainerPlanHref(sharedWorkouts.size(), sharedProgrammes.size(), assignedWorkouts.size(), assignedSchedules.size());
            String planCtaLabel = sharedWorkouts.size() + sharedProgrammes.size() > 0 ? "View assigned plan" : "Open trainer plan";
            ClientDashboardTrainerContextView context = buildTrainerContext(activeLink, assignedWorkouts, assignedSchedules,
                    sharedWorkouts.size(), sharedProgrammes.size(), trainer);

            return new ClientDashboardTrainerRailView(
                    "ACTIVE",
                    "Active coach",
                    trainer != null
                            ? buildIdentityView(trainer, trainerSettings,
                            platformSubscriptionService.isPremium(trainer.getId(), clock),
                            buildPremiumTooltip(trainer.getId()))
                            : null,
                    trimToNull(trainer != null ? trainer.getBio() : null),
                    trainerProgress != null ? trainerProgress.getPoints() : 0,
                    trainerProgress != null ? trainerProgress.getLevel() : 1,
                    parseProfileMilestones(trainerSettings != null ? trainerSettings.getProfileMilestoneKeys() : null)
                            .stream()
                            .map(this::mapMilestoneTitle)
                            .limit(4)
                            .toList(),
                    resolveCoachingPhase(activeLink, "Coaching active"),
                    buildRelationshipLabel(activeLink, "Connected"),
                    trainer != null ? trainer.getFullName() : null,
                    thread != null ? thread.getId() : null,
                    thread != null ? "/inbox/" + thread.getId() : null,
                    planHref,
                    "/client/trainers",
                    planCtaLabel,
                    context,
                    buildTrainerActivityItems(client, trainer, thread, timeDisplayFormat),
                    buildTrainerMessagePanel(thread, client.getId(), timeDisplayFormat));
        }

        if (pendingLink != null) {
            User trainer = userRepository.findById(pendingLink.getTrainerUserId()).orElse(null);
            UserSettings trainerSettings = trainer != null ? userSettingsRepository.findById(trainer.getId()).orElse(null) : null;
            LevelProgress trainerProgress = trainer != null ? trainer.getLevelProgress() : null;
            return new ClientDashboardTrainerRailView(
                    "REQUESTED",
                    "Request sent",
                    trainer != null
                            ? buildIdentityView(trainer, trainerSettings,
                            platformSubscriptionService.isPremium(trainer.getId(), clock),
                            buildPremiumTooltip(trainer.getId()))
                            : null,
                    trimToNull(trainer != null ? trainer.getBio() : null),
                    trainerProgress != null ? trainerProgress.getPoints() : 0,
                    trainerProgress != null ? trainerProgress.getLevel() : 1,
                    parseProfileMilestones(trainerSettings != null ? trainerSettings.getProfileMilestoneKeys() : null)
                            .stream()
                            .map(this::mapMilestoneTitle)
                            .limit(4)
                            .toList(),
                    "Awaiting trainer approval",
                    buildRelationshipLabel(pendingLink, "Requested"),
                    trainer != null ? trainer.getFullName() : null,
                    null,
                    null,
                    "/client/trainers",
                    "/client/trainers",
                    "Review trainers",
                    new ClientDashboardTrainerContextView(
                            "Next step",
                            "Waiting for confirmation",
                            "Once the request is accepted, coach notes, shared plans, and messaging can appear here.",
                            "You can still browse trainers at any time."),
                    List.of(),
                    new ClientDashboardTrainerMessagePanelView(
                            null,
                            false,
                            false,
                            "Messaging opens after approval",
                            "You will be able to message this trainer here once the coaching request is accepted.",
                            List.of()));
        }

        return new ClientDashboardTrainerRailView(
                "EMPTY",
                "No trainer connected",
                null,
                null,
                0,
                1,
                List.of(),
                "Independent mode",
                "Optional coaching",
                null,
                null,
                null,
                "/client/trainers",
                "/client/trainers",
                "Connect with a trainer",
                new ClientDashboardTrainerContextView(
                        "Why it helps",
                        "Bring coaching into the same dashboard",
                        "A trainer can add notes, share plans, and keep your next steps visible without adding clutter.",
                        "Explore trainers if you want more hands-on guidance."),
                List.of(),
                new ClientDashboardTrainerMessagePanelView(
                        null,
                        false,
                        false,
                        "No trainer conversation yet",
                        "Connect with a trainer if you want planning, notes, and messaging inside the dashboard.",
                        List.of()));
    }

    private ClientDashboardTrainerContextView buildTrainerContext(TrainerClientLink activeLink,
                                                                  List<AssignedWorkout> assignedWorkouts,
                                                                  List<AssignedSchedule> assignedSchedules,
                                                                  int sharedWorkoutCount,
                                                                  int sharedProgrammeCount,
                                                                  User trainer) {
        WeeklyCheckIn recentCheckIn = weeklyCheckInRepository.findByClientIdOrderBySubmittedAtDesc(activeLink.getClientUserId()).stream()
                .filter(checkIn -> checkIn.getTrainerId() != null && checkIn.getTrainerId().equals(activeLink.getTrainerUserId()))
                .filter(checkIn -> trimToNull(checkIn.getTrainerResponse()) != null || trimToNull(checkIn.getNextWeekFocus()) != null)
                .findFirst()
                .orElse(null);
        if (recentCheckIn != null) {
            String focus = trimToNull(recentCheckIn.getNextWeekFocus());
            String response = trimToNull(recentCheckIn.getTrainerResponse());
            return new ClientDashboardTrainerContextView(
                    "Coach feedback",
                    focus != null ? focus : "Latest weekly review",
                    response != null ? response : "Your trainer added a new focus for the coming week.",
                    "Week of " + recentCheckIn.getWeekStartDate().format(FULL_DATE_FMT));
        }

        LatestTrainerNote latestTrainerNote = findLatestTrainerNote(assignedWorkouts, assignedSchedules);
        if (latestTrainerNote != null) {
            return new ClientDashboardTrainerContextView(
                    "Latest note",
                    latestTrainerNote.sourceTitle(),
                    latestTrainerNote.noteBody(),
                    latestTrainerNote.meta());
        }

        if (sharedWorkoutCount + sharedProgrammeCount > 0 || !assignedWorkouts.isEmpty() || !assignedSchedules.isEmpty()) {
            return new ClientDashboardTrainerContextView(
                    "Plan status",
                    "Your trainer has current work ready",
                    buildPlanSummaryBody(sharedWorkoutCount, sharedProgrammeCount, assignedWorkouts.size(), assignedSchedules.size()),
                    "Open the trainer plan to review details and notes.");
        }

        String trainerBio = trimToNull(trainer != null ? trainer.getBio() : null);
        return new ClientDashboardTrainerContextView(
                "Coach context",
                "Your trainer link is active",
                trainerBio != null
                        ? trainerBio
                        : "Use the trainer card to keep your coach relationship visible while you manage your day-to-day plan.",
                "Message your trainer or open the plan when you need direction.");
    }

    private MessageThread resolveTrainerThread(TrainerClientLink link) {
        if (link == null || link.getStatus() != TrainerClientLinkStatus.ACTIVE) {
            return null;
        }
        return messageThreadRepository.findByLinkId(link.getId())
                .orElseGet(() -> messagingService.ensureThreadForLink(link));
    }

    private List<ClientDashboardTrainerActivityItemView> buildTrainerActivityItems(User client,
                                                                                   User trainer,
                                                                                   MessageThread thread,
                                                                                   TimeDisplayFormatPreference timeDisplayFormat) {
        if (client == null || trainer == null) {
            return List.of();
        }

        return notificationService.list(client, 20).stream()
                .filter(notification -> notification.getDismissedAt() == null)
                .filter(notification -> isTrainerNotification(notification, trainer, thread))
                .limit(6)
                .map(notification -> new ClientDashboardTrainerActivityItemView(
                        resolveTrainerNotificationIcon(notification),
                        trimToNull(notification.getTitle()) != null ? notification.getTitle().trim() : "Trainer update",
                        trimToNull(notification.getMessage()) != null ? notification.getMessage().trim() : "Your trainer sent a new update.",
                        buildNotificationMeta(notification, timeDisplayFormat),
                        trimToNull(notification.getCtaUrl()),
                        notification.getId(),
                        notification.getReadAt() == null,
                        notification.getReadAt() == null,
                        true))
                .toList();
    }

    private boolean isTrainerNotification(Notification notification, User trainer, MessageThread thread) {
        if (notification == null || trainer == null) {
            return false;
        }

        String title = lower(trimToNull(notification.getTitle()));
        String message = lower(trimToNull(notification.getMessage()));
        String ctaUrl = lower(trimToNull(notification.getCtaUrl()));
        List<String> trainerTokens = new ArrayList<>();
        if (trimToNull(trainer.getFullName()) != null) {
            trainerTokens.add(lower(trainer.getFullName()));
        }
        if (trimToNull(trainer.getFirstName()) != null) {
            trainerTokens.add(lower(trainer.getFirstName()));
        }
        if (trimToNull(trainer.getLastName()) != null) {
            trainerTokens.add(lower(trainer.getLastName()));
        }
        if (trimToNull(trainer.getUsername()) != null) {
            trainerTokens.add(lower(trainer.getUsername()));
            trainerTokens.add("@" + lower(trainer.getUsername()));
        }

        boolean mentionsTrainer = trainerTokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .anyMatch(token -> (title != null && title.contains(token))
                        || (message != null && message.contains(token))
                        || (ctaUrl != null && ctaUrl.contains(token)));

        boolean threadMatch = thread != null
                && ctaUrl != null
                && ctaUrl.contains("/inbox/" + thread.getId());
        boolean requestState = title != null && (
                title.contains("request accepted")
                        || title.contains("coaching ended")
                        || title.contains("coach"));
        boolean messageState = title != null && title.contains("new message");

        return threadMatch || (messageState && mentionsTrainer) || (requestState && mentionsTrainer);
    }

    private String resolveTrainerNotificationIcon(Notification notification) {
        String title = lower(trimToNull(notification != null ? notification.getTitle() : null));
        String ctaUrl = lower(trimToNull(notification != null ? notification.getCtaUrl() : null));
        if (title != null && title.contains("message")) {
            return "message";
        }
        if (ctaUrl != null && ctaUrl.contains("/assigned-plan")) {
            return "plan";
        }
        if (ctaUrl != null && ctaUrl.contains("/client/plan")) {
            return "task";
        }
        return "coach";
    }

    private String buildNotificationMeta(Notification notification, TimeDisplayFormatPreference timeDisplayFormat) {
        StringBuilder meta = new StringBuilder();
        if (notification.getReadAt() == null) {
            meta.append("Unread");
        } else {
            meta.append("Seen");
        }
        if (notification.getCreatedAt() != null) {
            if (!meta.isEmpty()) {
                meta.append(" | ");
            }
            meta.append(formatInstantWithDate(notification.getCreatedAt(), timeDisplayFormat));
        }
        return meta.toString();
    }

    private ClientDashboardTrainerMessagePanelView buildTrainerMessagePanel(MessageThread thread,
                                                                            Long currentUserId,
                                                                            TimeDisplayFormatPreference timeDisplayFormat) {
        if (thread == null || currentUserId == null) {
            return new ClientDashboardTrainerMessagePanelView(
                    null,
                    false,
                    false,
                    "No messages yet",
                    "When your coach messages you, the latest conversation will appear here.",
                    List.of());
        }
        List<Message> latest = threadMessageRepository.findTop30ByThread_IdOrderByIdDesc(thread.getId());
        boolean hasMore = latest.size() == 30;
        Collections.reverse(latest);
        return new ClientDashboardTrainerMessagePanelView(
                thread.getId(),
                true,
                hasMore,
                "Start the conversation",
                "Send a message when you need direction, clarification, or a plan adjustment.",
                toTrainerMessages(latest, currentUserId, timeDisplayFormat));
    }

    private List<ClientDashboardTrainerMessageView> toTrainerMessages(List<Message> messages,
                                                                      Long currentUserId,
                                                                      TimeDisplayFormatPreference timeDisplayFormat) {
        return messages.stream()
                .map(message -> toTrainerMessage(message, currentUserId, timeDisplayFormat))
                .toList();
    }

    private ClientDashboardTrainerMessageView toTrainerMessage(Message message,
                                                               Long currentUserId,
                                                               TimeDisplayFormatPreference timeDisplayFormat) {
        return new ClientDashboardTrainerMessageView(
                message.getId(),
                currentUserId != null && currentUserId.equals(message.getSenderUserId()),
                message.getBodyText(),
                message.getCreatedAt() != null
                        ? formatInstantWithDate(message.getCreatedAt(), timeDisplayFormat)
                        : "");
    }

    private LatestTrainerNote findLatestTrainerNote(List<AssignedWorkout> assignedWorkouts, List<AssignedSchedule> assignedSchedules) {
        AssignedWorkout workoutWithNote = assignedWorkouts.stream()
                .filter(item -> trimToNull(item.getTrainerNotes()) != null)
                .findFirst()
                .orElse(null);
        AssignedSchedule scheduleWithNote = assignedSchedules.stream()
                .filter(item -> trimToNull(item.getTrainerNotes()) != null)
                .findFirst()
                .orElse(null);

        if (workoutWithNote == null && scheduleWithNote == null) {
            return null;
        }
        if (scheduleWithNote == null) {
            return new LatestTrainerNote(
                    workoutWithNote.getWorkoutTemplate() != null ? workoutWithNote.getWorkoutTemplate().getName() : "Assigned workout",
                    trimToNull(workoutWithNote.getTrainerNotes()),
                    "Assigned " + workoutWithNote.getAssignedAt().toLocalDate().format(FULL_DATE_FMT));
        }
        if (workoutWithNote == null) {
            Schedule schedule = scheduleWithNote.getSchedule();
            return new LatestTrainerNote(
                    schedule != null ? schedule.getName() : "Assigned schedule",
                    trimToNull(scheduleWithNote.getTrainerNotes()),
                    "Assigned " + scheduleWithNote.getAssignedAt().toLocalDate().format(FULL_DATE_FMT));
        }
        if (scheduleWithNote.getAssignedAt().isAfter(workoutWithNote.getAssignedAt())) {
            Schedule schedule = scheduleWithNote.getSchedule();
            return new LatestTrainerNote(
                    schedule != null ? schedule.getName() : "Assigned schedule",
                    trimToNull(scheduleWithNote.getTrainerNotes()),
                    "Assigned " + scheduleWithNote.getAssignedAt().toLocalDate().format(FULL_DATE_FMT));
        }
        return new LatestTrainerNote(
                workoutWithNote.getWorkoutTemplate() != null ? workoutWithNote.getWorkoutTemplate().getName() : "Assigned workout",
                trimToNull(workoutWithNote.getTrainerNotes()),
                "Assigned " + workoutWithNote.getAssignedAt().toLocalDate().format(FULL_DATE_FMT));
    }

    private String resolveTrainerPlanHref(int sharedWorkoutCount, int sharedProgrammeCount, int assignedWorkoutCount, int assignedScheduleCount) {
        if (sharedWorkoutCount + sharedProgrammeCount > 0) {
            return "/client/assigned-plan";
        }
        if (assignedWorkoutCount + assignedScheduleCount > 0) {
            return "/client/plan";
        }
        return "/client/trainers";
    }

    private String resolveTrainerMessageHref(TrainerClientLink link) {
        if (link == null || link.getStatus() != TrainerClientLinkStatus.ACTIVE) {
            return null;
        }
        MessageThread thread = messageThreadRepository.findByLinkId(link.getId())
                .orElseGet(() -> messagingService.ensureThreadForLink(link));
        return thread != null ? "/inbox/" + thread.getId() : null;
    }

    private String buildPlanSummaryBody(int sharedWorkoutCount,
                                        int sharedProgrammeCount,
                                        int assignedWorkoutCount,
                                        int assignedScheduleCount) {
        List<String> parts = new ArrayList<>();
        if (sharedProgrammeCount > 0) {
            parts.add(buildCountLabel(sharedProgrammeCount, "shared programme"));
        }
        if (sharedWorkoutCount > 0) {
            parts.add(buildCountLabel(sharedWorkoutCount, "shared workout"));
        }
        if (assignedWorkoutCount > 0) {
            parts.add(buildCountLabel(assignedWorkoutCount, "assigned workout"));
        }
        if (assignedScheduleCount > 0) {
            parts.add(buildCountLabel(assignedScheduleCount, "assigned schedule"));
        }
        if (parts.isEmpty()) {
            return "Your coach relationship is live, but no plan items have been pushed into the dashboard yet.";
        }
        return joinNonBlank(parts) + " currently supporting your dashboard.";
    }

    private String buildRelationshipLabel(TrainerClientLink link, String prefix) {
        Instant timestamp = link.getActivatedAt() != null
                ? link.getActivatedAt()
                : (link.getRequestedAt() != null ? link.getRequestedAt() : link.getUpdatedAt());
        if (timestamp == null) {
            return prefix;
        }
        return prefix + " " + FULL_DATE_FMT.format(timestamp.atZone(clock.getZone()).toLocalDate());
    }

    private String resolveCoachingPhase(TrainerClientLink link, String fallback) {
        String customLabel = trimToNull(link.getCoachingPhaseLabel());
        if (customLabel != null) {
            return customLabel;
        }
        if (link.getCoachingPhase() != null) {
            return humanize(link.getCoachingPhase().name());
        }
        return fallback;
    }

    private ClientDashboardIdentityView buildIdentityView(User user,
                                                          UserSettings settings,
                                                          boolean premium,
                                                          String premiumTooltip) {
        String bannerTheme = settings != null && trimToNull(settings.getProfileBannerTheme()) != null
                ? settings.getProfileBannerTheme()
                : "AURORA";
        String ringStyle = settings != null && trimToNull(settings.getProfileRingStyle()) != null
                ? settings.getProfileRingStyle()
                : "NEON_DUAL";
        String cardBackStyle = settings != null && trimToNull(settings.getProfileCardBackStyle()) != null
                ? settings.getProfileCardBackStyle()
                : "GLASS";
        String username = user != null ? trimToNull(user.getUsername()) : null;
        String initial = username != null && !username.isBlank()
                ? username.substring(0, 1).toUpperCase(Locale.UK)
                : "U";
        return new ClientDashboardIdentityView(
                user != null ? user.getFullName() : "Profile",
                username,
                user != null ? trimToNull(user.getProfileImageUrl()) : null,
                initial,
                bannerTheme,
                ringStyle,
                cardBackStyle,
                premium,
                premiumTooltip);
    }

    private String buildPremiumTooltip(Long userId) {
        if (userId == null) {
            return "Premium active";
        }
        Optional<PlatformSubscription> existing = platformSubscriptionService.findByUserId(userId);
        if (existing.isEmpty()) {
            return "Premium active";
        }
        PlatformSubscription subscription = existing.get();
        if (subscription.getPlan() == PlatformPlan.INFINITE) {
            return "Lifetime Premium active";
        }
        Long daysLeft = platformSubscriptionService.getDaysUntilRenewal(userId, clock);
        if (daysLeft != null && daysLeft >= 0 && daysLeft <= 5) {
            return subscription.getStatus().name().equals("EXPIRES")
                    ? "Access ends in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s")
                    : "Renews in " + daysLeft + " day" + (daysLeft == 1 ? "" : "s");
        }
        return "Premium Active";
    }

    private String pathForDate(LocalDate date) {
        if (date == null) {
            return "/calendar";
        }
        return "/calendar/day/" + date;
    }

    private int ratioPercent(int value, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.round((value * 100.0) / total)));
    }

    private List<WeeklySummaryCard> buildWeeklySummaryCards(User user,
                                                            UserSettings settings,
                                                            DashboardSummaryDto summary,
                                                            int weekWorkoutsCompleted,
                                                            int weekWorkoutsRemaining,
                                                            int weekTasksCompleted,
                                                            int weekTasksRemaining,
                                                            int weekTasksTotal,
                                                            int nutritionLogsThisWeek) {
        List<String> selected = parseWeeklyMetricKeys(settings != null ? settings.getWeeklySummaryMetrics() : null);

        List<HealthRecord> topHealthRecords = user != null
                ? healthRecordRepository.findTop2ByUserOrderByBaselineDateDescIdDesc(user)
                : List.of();

        List<WeeklySummaryCard> cards = new ArrayList<>();
        for (String key : selected) {
            switch (key) {
                case "WORKOUTS_COMPLETED" -> cards.add(new WeeklySummaryCard(
                        "Workouts completed",
                        weekWorkoutsCompleted + " / " + Math.max(weekWorkoutsCompleted + weekWorkoutsRemaining, 1),
                        "Completed inside this week",
                        ratioPercent(weekWorkoutsCompleted, weekWorkoutsCompleted + weekWorkoutsRemaining),
                        "Progress against your planned sessions",
                        "emerald"));
                case "TASKS_COMPLETED" -> cards.add(new WeeklySummaryCard(
                        "Tasks completed",
                        weekTasksCompleted + " / " + Math.max(weekTasksTotal, 1),
                        "Checklist items cleared",
                        ratioPercent(weekTasksCompleted, weekTasksTotal),
                        "Task completion this week",
                        "outline"));
                case "MEALS_LOGGED" -> cards.add(new WeeklySummaryCard(
                        "Meals logged",
                        String.valueOf(nutritionLogsThisWeek),
                        "Nutrition logs recorded",
                        ratioPercent(nutritionLogsThisWeek, 7),
                        "Logging consistency over the last 7 days",
                        "emerald"));
                case "HABITS_COMPLETED" -> cards.add(new WeeklySummaryCard(
                        "Habits completed",
                        String.valueOf(weekTasksCompleted),
                        "Habits marked complete",
                        ratioPercent(weekTasksCompleted, Math.max(weekTasksCompleted + weekTasksRemaining, 1)),
                        "Follow-through across the week",
                        "outline"));
                case "WEIGHT_TREND" -> cards.add(buildWeightTrendCard(topHealthRecords));
                case "WORKOUT_STREAK" -> cards.add(new WeeklySummaryCard(
                        "Workout streak",
                        summary.getWorkoutStreak() + " days",
                        "Current consecutive streak",
                        ratioPercent(summary.getWorkoutStreak(), 7),
                        "Recent consistency streak",
                        "emerald"));
                default -> {
                }
            }
            if (cards.size() >= 4) {
                break;
            }
        }

        if (cards.isEmpty()) {
            cards.add(new WeeklySummaryCard(
                    "Workouts completed",
                    weekWorkoutsCompleted + " / " + Math.max(weekWorkoutsCompleted + weekWorkoutsRemaining, 1),
                    "Completed inside this week",
                    ratioPercent(weekWorkoutsCompleted, weekWorkoutsCompleted + weekWorkoutsRemaining),
                    "Progress against your planned sessions",
                    "emerald"));
            cards.add(new WeeklySummaryCard(
                    "Meals logged",
                    String.valueOf(nutritionLogsThisWeek),
                    "Nutrition logs recorded",
                    ratioPercent(nutritionLogsThisWeek, 7),
                    "Logging consistency over the last 7 days",
                    "outline"));
            cards.add(new WeeklySummaryCard(
                    "Habits completed",
                    String.valueOf(weekTasksCompleted),
                    "Habits marked complete",
                    ratioPercent(weekTasksCompleted, Math.max(weekTasksCompleted + weekTasksRemaining, 1)),
                    "Follow-through across the week",
                    "outline"));
        }
        return cards;
    }

    private WeeklySummaryCard buildWeightTrendCard(List<HealthRecord> records) {
        if (records == null || records.isEmpty() || records.get(0).getWeightKg() == null) {
            return new WeeklySummaryCard(
                    "Weight trend",
                    "Not tracked",
                    "Add body records to unlock this",
                    12,
                    "No recent body data",
                    "outline");
        }

        Double latest = records.get(0).getWeightKg();
        if (records.size() < 2 || records.get(1).getWeightKg() == null) {
            return new WeeklySummaryCard(
                    "Weight trend",
                    String.format(Locale.UK, "%.1f kg", latest),
                    "Latest recorded weight",
                    100,
                    "Single logged body record available",
                    "emerald");
        }

        Double previous = records.get(1).getWeightKg();
        double delta = latest - previous;
        String value = String.format(Locale.UK, "%+.1f kg", delta);
        return new WeeklySummaryCard(
                "Weight trend",
                value,
                delta < 0 ? "Moving down from the previous log" : (delta > 0 ? "Moving up from the previous log" : "Stable against the previous log"),
                100,
                "Compared to your previous body record",
                delta <= 0 ? "emerald" : "outline");
    }

    private List<WeekPreviewDay> buildWeekPreviewDays(User user, DashboardSummaryDto summary) {
        List<DashboardSummaryDto.WeekDaySummary> summaryDays = summary != null && summary.getWeek() != null
                ? summary.getWeek()
                : List.of();
        if (user == null || summaryDays.isEmpty()) {
            return List.of();
        }
        LocalDate start = summaryDays.get(0).getDate();
        LocalDate end = summaryDays.get(summaryDays.size() - 1).getDate();

        Map<LocalDate, List<CalendarTask>> tasksByDate = new HashMap<>();
        for (CalendarTask task : calendarTaskRepository.findByUserAndDateBetween(user, start, end)) {
            tasksByDate.computeIfAbsent(task.getDate(), ignored -> new ArrayList<>()).add(task);
        }

        Map<LocalDate, List<ScheduleOccurrence>> workoutsByDate = new HashMap<>();
        for (ScheduleOccurrence occurrence : scheduleOccurrenceRepository.findByUserAndDateBetween(user, start, end)) {
            workoutsByDate.computeIfAbsent(occurrence.getDate(), ignored -> new ArrayList<>()).add(occurrence);
        }

        return summaryDays.stream()
                .map(day -> new WeekPreviewDay(
                        day.getDate(),
                        day.getLabel(),
                        day.getDate().format(PRETTY_DATE_FMT),
                        day.getTasksCount(),
                        day.getWorkoutsCount(),
                        day.isToday(),
                        day.isTomorrow(),
                        day.getDayStatus(),
                        pathForDate(day.getDate()),
                        buildWeekPreviewItems(tasksByDate.getOrDefault(day.getDate(), List.of()), day.getDate(), "task"),
                        buildWeekPreviewItems(workoutsByDate.getOrDefault(day.getDate(), List.of()), day.getDate(), "workout")))
                .toList();
    }

    private List<WeekPreviewItem> buildWeekPreviewItems(List<?> items, LocalDate date, String kind) {
        List<WeekPreviewItem> previewItems = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof CalendarTask task) {
                previewItems.add(new WeekPreviewItem(
                        task.getId(),
                        trimToNull(task.getTitle()) != null ? task.getTitle() : "Task",
                        pathForDate(date) + "?focusTab=tasks&focusTask=" + task.getId(),
                        "task"));
            } else if (item instanceof ScheduleOccurrence occurrence) {
                previewItems.add(new WeekPreviewItem(
                        occurrence.getId(),
                        resolveWorkoutTitle(occurrence),
                        workoutLaunchPath(occurrence),
                        "workout"));
            }
        }
        return previewItems;
    }

    private String workoutLaunchPath(ScheduleOccurrence occurrence) {
        if (occurrence == null || occurrence.getId() == null) {
            return "/workout-management";
        }
        return "/workout-session/launch/occurrence/" + occurrence.getId();
    }

    private List<GoalSectionView> buildGoalSections(List<Goal> goals, LocalDate today) {
        return List.of(
                new GoalSectionView("week", "Week goals",
                        "Short weekly wins show up here once you add them on the goals page.",
                        buildGoalCardsForTimeframe(goals, today, GoalTimeframe.WEEK, 3)),
                new GoalSectionView("month", "Month goals",
                        "Monthly goals give the dashboard a clear longer rhythm without crowding today.",
                        buildGoalCardsForTimeframe(goals, today, GoalTimeframe.MONTH, 3)),
                new GoalSectionView("target", "Targets",
                        "Targets are ideal for weight, performance, or milestone outcomes with a specific finish line.",
                        buildGoalCardsForTimeframe(goals, today, GoalTimeframe.TARGET, 3)));
    }

    private List<GoalPreviewCard> buildGoalCardsForTimeframe(List<Goal> goals,
                                                             LocalDate today,
                                                             GoalTimeframe timeframe,
                                                             int limit) {
        return goals.stream()
                .filter(goal -> (goal.getTimeframe() != null ? goal.getTimeframe() : GoalTimeframe.TARGET) == timeframe)
                .sorted(Comparator
                        .comparing((Goal goal) -> goal.getTargetDate() != null ? goal.getTargetDate() : LocalDate.MAX)
                        .thenComparing(goal -> goal.getPriority() != null ? goal.getPriority() : Integer.MAX_VALUE))
                .limit(limit)
                .map(goal -> new GoalPreviewCard(
                        goal.getId(),
                        goal.getTitle(),
                        goal.getStatus() != null ? humanize(goal.getStatus().name()) : "Draft",
                        buildGoalMetric(goal),
                        buildGoalTimeline(goal, today),
                        estimateGoalProgress(goal, today),
                        resolveGoalTone(goal),
                        goal.getTimeframe() != null ? goal.getTimeframe().name() : GoalTimeframe.TARGET.name()))
                .toList();
    }

    private String resolveGoalTone(Goal goal) {
        if (goal == null || goal.getStatus() == null) {
            return "draft";
        }
        return switch (goal.getStatus()) {
            case COMPLETED -> "complete";
            case ACTIVE -> "active";
            case PAUSED -> "paused";
            default -> "draft";
        };
    }

    private int estimateGoalProgress(Goal goal, LocalDate today) {
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            return 100;
        }
        if (goal.getStatus() == GoalStatus.ACTIVE && goal.getStartDate() != null && goal.getTargetDate() != null
                && !goal.getTargetDate().isBefore(goal.getStartDate())) {
            long totalDays = Math.max(ChronoUnit.DAYS.between(goal.getStartDate(), goal.getTargetDate()), 1);
            long elapsed = ChronoUnit.DAYS.between(goal.getStartDate(), today);
            long bounded = Math.min(Math.max(elapsed, 0), totalDays);
            return (int) Math.max(12, Math.min(100, Math.round((bounded * 100.0) / totalDays)));
        }
        if (goal.getStatus() == GoalStatus.ACTIVE) {
            return 56;
        }
        return 18;
    }

    private String buildGoalMetric(Goal goal) {
        if (goal.getTargetMetricValue() != null && trimToNull(goal.getTargetMetricName()) != null) {
            String unit = trimToNull(goal.getTargetMetricUnit());
            String value = goal.getTargetMetricValue() % 1 == 0
                    ? String.valueOf(goal.getTargetMetricValue().intValue())
                    : String.format(Locale.UK, "%.1f", goal.getTargetMetricValue());
            return value + (unit != null ? unit : "") + " " + goal.getTargetMetricName();
        }
        if (goal.getGoalType() != null) {
            return humanize(goal.getGoalType().name());
        }
        return "Goal";
    }

    private String buildGoalTimeline(Goal goal, LocalDate today) {
        if (goal.getStatus() == GoalStatus.COMPLETED) {
            return "Completed";
        }
        if (goal.getTargetDate() != null) {
            if (!goal.getTargetDate().isBefore(today)) {
                long days = ChronoUnit.DAYS.between(today, goal.getTargetDate());
                return days == 0 ? "Due today" : "Due in " + days + " day" + (days == 1 ? "" : "s");
            }
            long daysOver = ChronoUnit.DAYS.between(goal.getTargetDate(), today);
            return daysOver == 0 ? "Due today" : daysOver + " day" + (daysOver == 1 ? "" : "s") + " overdue";
        }
        return "No end date set";
    }

    private String buildTaskDescription(CalendarTask task,
                                        int totalOpen,
                                        LocalTime now,
                                        TimeDisplayFormatPreference timeDisplayFormat) {
        String title = trimToNull(task.getTitle()) != null ? task.getTitle() : "Open task";
        if (task.getTime() != null && task.getTime().isBefore(now)) {
            return title + " is already past its scheduled time. Clear it first to reduce drag across the rest of the day.";
        }
        if (task.getTime() != null) {
            return title + " is scheduled for " + formatLocalTime(task.getTime(), timeDisplayFormat)
                    + ". Handling it next keeps the rest of today's list cleaner.";
        }
        return totalOpen > 1
                ? totalOpen + " tasks are still open today. Starting with this one keeps the planner under control."
                : "You have one open task left today, so clearing it now removes the biggest blocker from the board.";
    }

    private String resolveTaskBadge(CalendarTask task, LocalTime now) {
        if (task.getTime() == null) {
            return "Due today";
        }
        if (task.getTime().isBefore(now)) {
            return "Overdue";
        }
        if (task.getTime().isBefore(now.plusMinutes(90))) {
            return "Soon";
        }
        return "Scheduled";
    }

    private int resolveTaskPriority(CalendarTask task, LocalTime now) {
        if (task.getTime() == null) {
            return 640;
        }
        if (task.getTime().isBefore(now)) {
            return 980;
        }
        if (task.getTime().isBefore(now.plusMinutes(90))) {
            return 920;
        }
        return 760;
    }

    private List<String> buildTaskStats(List<CalendarTask> openTasks, TimeDisplayFormatPreference timeDisplayFormat) {
        CalendarTask firstTask = openTasks.get(0);
        List<String> stats = new ArrayList<>();
        stats.add(openTasks.size() + " task" + (openTasks.size() == 1 ? "" : "s") + " open");
        stats.add(firstTask.getTime() != null
                ? "Next at " + formatLocalTime(firstTask.getTime(), timeDisplayFormat)
                : "No set time on first task");
        return stats;
    }

    private String resolveWorkoutTitle(ScheduleOccurrence workout) {
        if (trimToNull(workout.getScheduleName()) != null) {
            return workout.getScheduleName();
        }
        if (workout.getExercise() != null && trimToNull(workout.getExercise().getName()) != null) {
            return workout.getExercise().getName();
        }
        if (workout.getCustomExercise() != null && trimToNull(workout.getCustomExercise().getName()) != null) {
            return workout.getCustomExercise().getName();
        }
        return "Scheduled workout";
    }

    private List<String> parseWeeklyMetricKeys(String raw) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw != null && !raw.isBlank()) {
            for (String value : raw.split(",")) {
                if (value != null && !value.isBlank()) {
                    keys.add(value.trim().toUpperCase(Locale.ROOT));
                }
                if (keys.size() >= 6) {
                    break;
                }
            }
        }
        if (keys.isEmpty()) {
            keys.add("WORKOUTS_COMPLETED");
            keys.add("MEALS_LOGGED");
            keys.add("HABITS_COMPLETED");
        }
        return List.copyOf(keys);
    }

    private List<String> parseProfileMilestones(String raw) {
        Set<String> keys = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        for (String part : raw.split(",")) {
            if (part != null && !part.isBlank()) {
                keys.add(part.trim().toUpperCase(Locale.ROOT));
            }
            if (keys.size() >= 6) {
                break;
            }
        }
        return List.copyOf(keys);
    }

    private String mapMilestoneTitle(String key) {
        return switch (key) {
            case "FIRST_WORKOUT" -> "First workout logged";
            case "TEN_WORKOUTS" -> "10 workouts";
            case "LEVEL_5" -> "Reached Level 5";
            case "LEVEL_10" -> "Reached Level 10";
            case "VERIFIED_EMAIL" -> "Verified email";
            case "VERIFIED_PHONE" -> "Verified phone";
            case "PREMIUM_MEMBER" -> "Premium member";
            default -> "Milestone";
        };
    }

    private TimeDisplayFormatPreference resolveTimeDisplayFormat(UserSettings settings) {
        if (settings == null || settings.getTimeDisplayFormat() == null) {
            return TimeDisplayFormatPreference.TWELVE_HOUR;
        }
        return settings.getTimeDisplayFormat();
    }

    private String formatLocalTime(LocalTime time, TimeDisplayFormatPreference timeDisplayFormat) {
        if (time == null) {
            return "";
        }
        DateTimeFormatter formatter = timeDisplayFormat == TimeDisplayFormatPreference.TWENTY_FOUR_HOUR
                ? DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
                : DateTimeFormatter.ofPattern("h:mm a", Locale.UK);
        String formatted = time.format(formatter);
        return timeDisplayFormat == TimeDisplayFormatPreference.TWENTY_FOUR_HOUR
                ? formatted
                : formatted.toUpperCase(Locale.UK);
    }

    private String formatInstantWithDate(Instant instant, TimeDisplayFormatPreference timeDisplayFormat) {
        if (instant == null) {
            return "";
        }
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, clock.getZone());
        return localDateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM", Locale.UK))
                + " "
                + formatLocalTime(localDateTime.toLocalTime(), timeDisplayFormat);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String lower(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private String humanize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String lower = raw.toLowerCase(Locale.UK).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String buildCountLabel(int count, String singular) {
        if (count <= 0) {
            return "";
        }
        return count + " " + (count == 1 ? singular : singular + "s");
    }

    private String joinNonBlank(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
    }

    private record LatestTrainerNote(String sourceTitle, String noteBody, String meta) {
    }

    public record TopActionCard(String kicker,
                                String title,
                                String href,
                                String icon,
                                String headline,
                                String subhead,
                                String body,
                                List<String> chips) {
    }

    public record WeeklySummaryCard(String title,
                                    String value,
                                    String subtitle,
                                    int progressPercent,
                                    String progressLabel,
                                    String tone) {
    }

    public record GoalPreviewCard(Long id,
                                  String title,
                                  String statusLabel,
                                  String metricLabel,
                                  String timelineLabel,
                                  int progressPercent,
                                  String tone,
                                  String timeframeKey) {
    }

    public record GoalSectionView(String key,
                                  String title,
                                  String emptyMessage,
                                  List<GoalPreviewCard> cards) {
    }

    public record WeekPreviewItem(Long id,
                                  String title,
                                  String href,
                                  String kind) {
    }

    public record WeekPreviewDay(LocalDate date,
                                 String dayLabel,
                                 String prettyDate,
                                 int taskCount,
                                 int workoutCount,
                                 boolean today,
                                 boolean tomorrow,
                                 String dayStatus,
                                 String path,
                                 List<WeekPreviewItem> tasks,
                                 List<WeekPreviewItem> workouts) {
    }

    public record DashboardMessageResponse(List<ClientDashboardTrainerMessageView> messages,
                                           boolean hasMore) {
    }

    public record DashboardSendMessageResponse(ClientDashboardTrainerMessageView message) {
    }

    public record DashboardAmbienceView(boolean enabled,
                                        String weather,
                                        String temperatureUnit,
                                        String displayMode) {
    }
}
