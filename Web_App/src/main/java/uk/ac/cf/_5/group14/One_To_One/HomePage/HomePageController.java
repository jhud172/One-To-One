package uk.ac.cf._5.group14.One_To_One.HomePage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.ModelAndView;

import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ConditionsPreferences.UserPreference.UserPreferenceService;
import uk.ac.cf._5.group14.One_To_One.Config.DevModeProperties;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogService;
import uk.ac.cf._5.group14.One_To_One.FeedbackData.AdaptiveFeedbackService;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Security.SecurityUtils;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

@Controller
public class HomePageController {

    private final ExerciseLogService exerciseLogService;
    private final CalendarTaskRepository calendarTaskRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final TrainerClientLinkRepository trainerClientLinkRepository;
    private final MessagingService messagingService;
    private final AuthHelper authHelper;
    private final UserService userService;
    private final UserPreferenceService userPreferenceService;
    private final AdaptiveFeedbackService adaptiveFeedbackService;
    private final MessageSource messageSource;
    
    @Autowired
    private DevModeProperties devModeProperties;

    private static final DateTimeFormatter PRETTY_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter TASK_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.UK);

    public HomePageController(
            ExerciseLogService exerciseLogService,
            CalendarTaskRepository calendarTaskRepository,
            ScheduleOccurrenceRepository scheduleOccurrenceRepository,
            TrainerClientLinkRepository trainerClientLinkRepository,
            MessagingService messagingService,
            AuthHelper authHelper,
            UserService userService,
            UserPreferenceService userPreferenceService,
            AdaptiveFeedbackService adaptiveFeedbackService,
            MessageSource messageSource
    ) {
        this.exerciseLogService = exerciseLogService;
        this.calendarTaskRepository = calendarTaskRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.trainerClientLinkRepository = trainerClientLinkRepository;
        this.messagingService = messagingService;
        this.authHelper = authHelper;
        this.userService = userService;
        this.userPreferenceService = userPreferenceService;
        this.adaptiveFeedbackService = adaptiveFeedbackService;
        this.messageSource = messageSource;
    }

    // Clean up bookmarked links from the previous session-expiry redirect.
    @GetMapping(value = "/", params = "expired")
    public ModelAndView cleanExpiredHomepage() {
        return new ModelAndView("redirect:/");
    }

    @GetMapping("/")
    public ModelAndView homePage(Authentication authentication, Locale locale) {
        User user = resolveCurrentUser(authentication);

        if (user == null) {
            ModelAndView mav = new ModelAndView("public-views/home/public");
            mav.addObject("isDevMode", devModeProperties.isDevMode());
            mav.addObject("pageDescription", messageSource.getMessage(
                    "home.001.page.description",
                    null,
                    "Find a verified personal trainer, follow a plan built around your life and keep every session, message and sign of progress connected with One To One.",
                    locale
            ));
            return mav;
        }
        
        ModelAndView mav = new ModelAndView("redirect:/dashboard");
        mav.addObject("isDevMode", devModeProperties.isDevMode());
        return mav;
    }

    @GetMapping("/about")
    public ModelAndView aboutPage() {
        ModelAndView mav = new ModelAndView("public-views/public/about");
        mav.addObject("pageDescription", "See how One To One connects clients, verified trainers and visible progress in one focused coaching relationship.");
        return mav;
    }

    @GetMapping("/faq")
    public ModelAndView faqPage() {
        ModelAndView mav = new ModelAndView("public-views/public/faq");
        mav.addObject("pageDescription", "Clear answers about joining One To One, trainer verification, coaching, subscriptions, payments and account support.");
        return mav;
    }

    @GetMapping("/home")
    public ModelAndView roleHome(Authentication authentication) {
        User user = resolveCurrentUser(authentication);
        if (user == null) {
            return new ModelAndView("redirect:/");
        }

        if (SecurityUtils.hasRole(authentication, "SUPER_ADMIN") || SecurityUtils.hasRole(authentication, "PLATFORM_ADMIN")) {
            return new ModelAndView("redirect:/admin/dashboard");
        }
        if (SecurityUtils.hasRole(authentication, "GYM_ADMIN")) {
            return new ModelAndView("redirect:/gym/dashboard");
        }
        if (SecurityUtils.hasRole(authentication, "TRAINER")) {
            return new ModelAndView("redirect:/trainer/dashboard");
        }

        ModelAndView mav = new ModelAndView();
        mav.addObject("pageTitle", "Home");
        mav.addObject("disableChatHistory", true);
        mav.addObject("currentUser", user);

        LocalDate today = LocalDate.now();
        CalendarTask nextTask = calendarTaskRepository
            .findFirstByUserAndDateAndCompletedFalseOrderByTimeAsc(user, today)
            .orElse(null);

        mav.addObject("nextTaskTitle", nextTask != null ? nextTask.getTitle() : "No tasks scheduled");
        mav.addObject("nextTaskTime", nextTask != null && nextTask.getTime() != null ? nextTask.getTime().format(TASK_TIME) : null);
        mav.addObject("nextTaskDate", today);
        mav.addObject("overdueCount", calendarTaskRepository.countByUserAndDateBeforeAndCompletedFalse(user, today));
        mav.addObject("completedToday", calendarTaskRepository.countByUserAndDateAndCompletedTrue(user, today));

        mav.setViewName("public-views/home/user");

        return mav;
    }

    private User resolveCurrentUser(Authentication authentication) {
        User sessionUser = authHelper.getAuthenticatedUser();
        if (sessionUser != null) {
            return sessionUser;
        }
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userService.findByUsername(authentication.getName());
    }

    private boolean hasWorkoutToLog(User user, LocalDate date) {
        boolean scheduleNeedsLog = scheduleOccurrenceRepository.existsByUserAndDateAndCompletedFalse(user, date)
                || scheduleOccurrenceRepository.existsByUserAndDateAndExerciseLogIsNull(user, date);

        boolean calendarNeedsLog = calendarTaskRepository.existsByUserAndDateAndRequiresLogTrueAndCompletedFalse(user, date)
                || calendarTaskRepository.existsByUserAndDateAndRequiresLogTrueAndExerciseLogIsNull(user, date);

        return scheduleNeedsLog || calendarNeedsLog;
    }

    private List<UpcomingItem> buildUpcomingItems(User user, LocalDate from, LocalDate to) {
        List<CalendarTask> tasks = calendarTaskRepository.findByUserAndDateBetween(user, from, to);
        List<ScheduleOccurrence> occurrences = scheduleOccurrenceRepository.findByUserAndDateBetween(user, from, to);

        List<UpcomingItemCandidate> all = new ArrayList<>();

        for (CalendarTask task : tasks) {
            if (Boolean.TRUE.equals(task.getCompleted())) {
                continue;
            }
            LocalDate date = task.getDate();
            if (date == null || date.isBefore(from)) {
                continue;
            }
            LocalTime time = task.getTime() != null ? task.getTime() : LocalTime.MIDNIGHT;
            String dateLabel = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " " + date.format(PRETTY_DATE);
            String href = "/calendar/day/" + date;
            all.add(new UpcomingItemCandidate(date, time, new UpcomingItem("Task", task.getTitle(), dateLabel, href)));
        }

        for (ScheduleOccurrence occ : occurrences) {
            if (occ.isCompleted()) {
                continue;
            }
            LocalDate date = occ.getDate();
            if (date == null || date.isBefore(from)) {
                continue;
            }
            String dateLabel = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.UK) + " " + date.format(PRETTY_DATE);
            String href = "/calendar/day/" + date;
            String title = occ.getScheduleName() != null ? occ.getScheduleName() : "Workout session";
            all.add(new UpcomingItemCandidate(date, LocalTime.NOON, new UpcomingItem("Session", title, dateLabel, href)));
        }

        all.sort(Comparator
                .comparing(UpcomingItemCandidate::date)
                .thenComparing(UpcomingItemCandidate::time));

        List<UpcomingItem> top3 = new ArrayList<>(3);
        for (UpcomingItemCandidate c : all) {
            top3.add(c.item());
            if (top3.size() == 3) {
                break;
            }
        }
        return top3;
    }

    private record UpcomingItemCandidate(LocalDate date, LocalTime time, UpcomingItem item) {
    }

    private Long findTrainerConversationId(User user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        TrainerClientLink link = trainerClientLinkRepository.findActiveByClientId(user.getId()).orElse(null);
        if (link == null) {
            return null;
        }
        return messagingService.ensureThreadForLink(link).getId();
    }

}
