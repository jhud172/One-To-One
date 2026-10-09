package uk.ac.cf._5.group14.One_To_One.Chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyCompletionStatus;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyStreakDaySummary;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyStreakService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Builds structured context for the chat page and AI requests.
 * Provides personalised summaries for zero-blank state and context injection.
 */
@Service
public class ChatContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(ChatContextBuilder.class);

    private final CalendarTaskRepository calendarTaskRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final DailyStreakService dailyStreakService;
    private final Clock clock;

    public ChatContextBuilder(
            CalendarTaskRepository calendarTaskRepository,
            ScheduleOccurrenceRepository scheduleOccurrenceRepository,
            DailyStreakService dailyStreakService,
            Clock clock
    ) {
        this.calendarTaskRepository = calendarTaskRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.dailyStreakService = dailyStreakService;
        this.clock = clock;
    }

    /**
     * Builds the page summary DTO for the chat page model and /chat/context endpoint.
     */
    public ChatSummaryDto buildSummary(User user) {
        LocalDate today = LocalDate.now(clock);
        LocalTime now = LocalTime.now(clock);

        String greeting = computeGreeting(now, user);

        // Today's tasks
        int tasksDone = 0;
        int tasksTotal = 0;
        try {
            List<CalendarTask> todayTasks = calendarTaskRepository.findByUserAndDateOrderByTime(user, today);
            tasksTotal = todayTasks.size();
            tasksDone = (int) todayTasks.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
        } catch (Exception e) {
            log.warn("Could not load today's tasks for chat summary", e);
        }

        // Today's schedule occurrences (workouts)
        int workoutsDone = 0;
        int workoutsTotal = 0;
        String nextWorkoutName = null;
        String nextWorkoutDate = null;
        try {
            List<ScheduleOccurrence> todayOccurrences = scheduleOccurrenceRepository.findByUserAndDate(user, today);
            workoutsTotal = todayOccurrences.size();
            workoutsDone = (int) todayOccurrences.stream().filter(ScheduleOccurrence::isCompleted).count();

            // Find next workout: first future occurrence after today
            List<ScheduleOccurrence> active = scheduleOccurrenceRepository.findActiveByUser(user);
            ScheduleOccurrence next = active.stream()
                    .filter(o -> o.getDate() != null && o.getDate().isAfter(today))
                    .min(Comparator.comparing(ScheduleOccurrence::getDate))
                    .orElse(null);
            if (next != null) {
                nextWorkoutDate = next.getDate().toString();
                String exerciseName = next.getExercise() != null ? next.getExercise().getName() : null;
                if (exerciseName == null && next.getCustomExercise() != null) {
                    exerciseName = next.getCustomExercise().getName();
                }
                nextWorkoutName = next.getScheduleName() != null ? next.getScheduleName() :
                        (exerciseName != null ? exerciseName : "Workout");
            }
        } catch (Exception e) {
            log.warn("Could not load workout data for chat summary", e);
        }

        // Streak: consecutive days (going back from yesterday) where completion >= 1 item done
        int streakDays = 0;
        try {
            LocalDate streakEnd = today.minusDays(1);
            LocalDate streakStart = streakEnd.minusDays(13); // look back up to 14 days
            if (!streakEnd.isBefore(streakStart)) {
                List<DailyStreakDaySummary> range = dailyStreakService.calculateRange(user, streakStart, streakEnd, today);
                // Count consecutive days from yesterday backwards where status is GREEN or ORANGE
                for (int i = range.size() - 1; i >= 0; i--) {
                    DailyStreakDaySummary day = range.get(i);
                    if (day.status() == DailyCompletionStatus.GREEN || day.status() == DailyCompletionStatus.ORANGE) {
                        streakDays++;
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not compute streak for chat summary", e);
        }

        // Today's completion percentage
        int totalItems = tasksTotal + workoutsTotal;
        int doneItems = tasksDone + workoutsDone;
        int completionPct = totalItems > 0 ? (doneItems * 100) / totalItems : 0;

        // Multi-day insights
        int s7TasksDone = 0, s7TasksTotal = 0, s7WoDone = 0, s7WoTotal = 0, s7Missed = 0;
        int s30TasksDone = 0, s30TasksTotal = 0, s30WoDone = 0, s30WoTotal = 0, s30Missed = 0;
        String trendNote = null;
        try {
            LocalDate from7 = today.minusDays(6);
            List<CalendarTask> tasks7 = calendarTaskRepository.findByUserAndDateBetween(user, from7, today);
            s7TasksTotal = tasks7.size();
            s7TasksDone = (int) tasks7.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
            List<ScheduleOccurrence> occs7 = scheduleOccurrenceRepository.findByUserAndDateBetween(user, from7, today);
            s7WoTotal = occs7.size();
            s7WoDone = (int) occs7.stream().filter(ScheduleOccurrence::isCompleted).count();
            s7Missed = s7WoTotal - s7WoDone;

            LocalDate from30 = today.minusDays(29);
            List<CalendarTask> tasks30 = calendarTaskRepository.findByUserAndDateBetween(user, from30, today);
            s30TasksTotal = tasks30.size();
            s30TasksDone = (int) tasks30.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
            List<ScheduleOccurrence> occs30 = scheduleOccurrenceRepository.findByUserAndDateBetween(user, from30, today);
            s30WoTotal = occs30.size();
            s30WoDone = (int) occs30.stream().filter(ScheduleOccurrence::isCompleted).count();
            s30Missed = s30WoTotal - s30WoDone;

            trendNote = buildTrendNote(s7TasksDone, s7TasksTotal, s7WoDone, s7WoTotal);
        } catch (Exception e) {
            log.warn("Could not compute multi-day insights for chat summary", e);
        }

        return new ChatSummaryDto(
                greeting,
                today.toString(),
                tasksDone,
                tasksTotal,
                workoutsDone,
                workoutsTotal,
                completionPct,
                nextWorkoutName,
                nextWorkoutDate,
                streakDays,
                s7TasksDone, s7TasksTotal, s7WoDone, s7WoTotal, s7Missed,
                s30TasksDone, s30TasksTotal, s30WoDone, s30WoTotal, s30Missed,
                trendNote
        );
    }

    private String buildTrendNote(int tasksDone, int tasksTotal, int woDone, int woTotal) {
        if (tasksTotal == 0 && woTotal == 0) return "No scheduled items in the last 7 days.";
        int total = tasksTotal + woTotal;
        int done = tasksDone + woDone;
        int pct = total > 0 ? (done * 100) / total : 0;
        if (pct >= 80) return "ðŸ”¥ Great week — " + pct + "% completion rate!";
        if (pct >= 50) return "ðŸ“ˆ Solid effort this week (" + pct + "% done). Keep pushing!";
        return "âš ï¸ Only " + pct + "% completed this week. Consider reducing workload or improving consistency.";
    }

    private String computeGreeting(LocalTime now, User user) {
        String name = (user != null && user.getFirstName() != null && !user.getFirstName().isBlank())
                ? ", " + user.getFirstName()
                : "";
        int hour = now.getHour();
        if (hour >= 5 && hour < 12) return "Good morning" + name;
        if (hour >= 12 && hour < 17) return "Good afternoon" + name;
        if (hour >= 17 && hour < 21) return "Good evening" + name;
        return "Hey" + name;
    }

    /**
     * Returns the time-of-day theme token for use in data attributes and CSS.
     */
    public String computeTimeTheme(LocalTime now) {
        int hour = now.getHour();
        if (hour >= 5 && hour < 12) return "morning";
        if (hour >= 12 && hour < 17) return "midday";
        if (hour >= 17 && hour < 21) return "evening";
        return "night";
    }
}
