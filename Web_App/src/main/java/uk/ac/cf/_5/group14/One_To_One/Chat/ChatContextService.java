package uk.ac.cf._5.group14.One_To_One.Chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.Level.LevelProgressRepository;
import uk.ac.cf._5.group14.One_To_One.Notes.Note;
import uk.ac.cf._5.group14.One_To_One.Notes.NoteRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatContextService {

    private static final Logger log = LoggerFactory.getLogger(ChatContextService.class);

    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final CalendarTaskRepository calendarTaskRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final NoteRepository noteRepository;
    private final LevelProgressRepository levelProgressRepository;

    public ChatContextService(
            ScheduleOccurrenceRepository scheduleOccurrenceRepository,
            CalendarTaskRepository calendarTaskRepository,
            WorkoutSessionRepository workoutSessionRepository,
            NoteRepository noteRepository,
            LevelProgressRepository levelProgressRepository
    ) {
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.calendarTaskRepository = calendarTaskRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.noteRepository = noteRepository;
        this.levelProgressRepository = levelProgressRepository;
    }

    public ChatContext build(User user, LocalDate requestedDate) {
        LocalDate today = LocalDate.now();
        LocalDate req = requestedDate;

        List<String> todaysTasks = summarizeTasks(calendarTaskRepository.findByUserAndDateOrderByTime(user, today));
        List<String> todaysScheduled = summarizeOccurrences(scheduleOccurrenceRepository.findByUserAndDate(user, today));

        List<String> reqItems = List.of();
        if (req != null && !req.equals(today)) {
            List<String> items = new ArrayList<>();
            items.addAll(summarizeTasks(calendarTaskRepository.findByUserAndDateOrderByTime(user, req)));
            items.addAll(summarizeOccurrences(scheduleOccurrenceRepository.findByUserAndDate(user, req)));
            reqItems = items;
        }

        List<ChatContext.RecentWorkout> recentWorkouts = List.of();
        try {
            List<WorkoutSession> recentSessions = workoutSessionRepository.findTop3ByUserOrderByDateDesc(user);
            recentWorkouts = summarizeWorkouts(recentSessions);
        } catch (InvalidDataAccessResourceUsageException e) {
            log.warn("Workout session context unavailable (likely missing table). Continuing without workout context.", e);
            recentWorkouts = List.of();
        } catch (DataAccessException e) {
            log.warn("Workout session context unavailable due to data access error. Continuing without workout context.", e);
            recentWorkouts = List.of();
        } catch (RuntimeException e) {
            log.warn("Workout session context unavailable due to unexpected error. Continuing without workout context.", e);
            recentWorkouts = List.of();
        }

        List<Note> notes = noteRepository.findByUserOrderByUpdatedAtDesc(user);
        List<String> recentNotes = notes.stream()
                .limit(3)
                .map(n -> n.getTitle() + " (updated " + n.getUpdatedAt().toLocalDate() + ")")
                .toList();

        var lpOpt = levelProgressRepository.findByUser(user);
        Integer points = lpOpt.map(lp -> lp.getPoints()).orElse(null);
        Integer level = lpOpt.map(lp -> lp.getLevel()).orElse(null);

        // Multi-day insights for AI context
        ChatContext.MultiDayInsights multiDayInsights = null;
        try {
            LocalDate from7 = today.minusDays(6);
            List<CalendarTask> tasks7 = calendarTaskRepository.findByUserAndDateBetween(user, from7, today);
            int t7Total = tasks7.size();
            int t7Done = (int) tasks7.stream().filter(t -> Boolean.TRUE.equals(t.getCompleted())).count();
            List<ScheduleOccurrence> occs7 = scheduleOccurrenceRepository.findByUserAndDateBetween(user, from7, today);
            int w7Total = occs7.size();
            int w7Done = (int) occs7.stream().filter(ScheduleOccurrence::isCompleted).count();
            int w7Missed = w7Total - w7Done;
            int total7 = t7Total + w7Total;
            int done7 = t7Done + w7Done;
            int pct7 = total7 > 0 ? (done7 * 100) / total7 : 0;
            String trend = pct7 >= 80 ? "Great week – " + pct7 + "% completion."
                    : pct7 >= 50 ? "Solid week – " + pct7 + "% done."
                    : "Challenging week – only " + pct7 + "% completion.";
            multiDayInsights = new ChatContext.MultiDayInsights(7, t7Done, t7Total, w7Done, w7Total, w7Missed, trend);
        } catch (Exception e) {
            log.warn("Could not compute multi-day insights for chat context", e);
        }

        return new ChatContext(today, req, todaysTasks, todaysScheduled, reqItems, recentWorkouts, recentNotes, points, level, multiDayInsights);
    }

    private List<String> summarizeTasks(List<CalendarTask> tasks) {
        return tasks.stream()
                .limit(10)
                .map(t -> {
                    String time = t.getTime() != null ? t.getTime().toString() : "";
                    String prefix = time.isBlank() ? "" : (time + " - ");
                    String done = Boolean.TRUE.equals(t.getCompleted()) ? " (done)" : "";
                    return prefix + t.getTitle() + done;
                })
                .toList();
    }

    private List<String> summarizeOccurrences(List<ScheduleOccurrence> occs) {
        return occs.stream()
                .limit(10)
                .map(o -> {
                    String exercise = o.getExercise() != null ? o.getExercise().getName() : null;
                    if (exercise == null && o.getCustomExercise() != null) {
                        exercise = o.getCustomExercise().getName();
                    }
                    if (exercise == null) exercise = "(exercise)";
                    String done = o.isCompleted() ? " (done)" : "";
                    String schedule = o.getScheduleName() != null ? o.getScheduleName() : "Schedule";
                    return schedule + ": " + exercise + done;
                })
                .toList();
    }

    private List<ChatContext.RecentWorkout> summarizeWorkouts(List<WorkoutSession> sessions) {
        return sessions.stream()
                .limit(3)
                .map(s -> {
                    int totalSets = 0;
                    if (s.getExerciseSessions() != null) {
                        for (var es : s.getExerciseSessions()) {
                            if (es.getSetLogs() != null) {
                                totalSets += es.getSetLogs().size();
                            }
                        }
                    }
                    String name = s.getNameSnapshot() != null && !s.getNameSnapshot().isBlank()
                            ? s.getNameSnapshot()
                            : (s.getWorkout() != null ? s.getWorkout().getName() : "Workout");
                    return new ChatContext.RecentWorkout(s.getDate(), name, s.isCompleted(), totalSets);
                })
                .toList();
    }
}
