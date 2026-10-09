package uk.ac.cf._5.group14.One_To_One.CalendarData;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DailyStreakService {

    private final CalendarTaskRepository calendarTaskRepository;
    private final WorkoutSessionRepository workoutSessionRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    public DailyStreakService(
            CalendarTaskRepository calendarTaskRepository,
            WorkoutSessionRepository workoutSessionRepository,
            ScheduleOccurrenceRepository scheduleOccurrenceRepository
    ) {
        this.calendarTaskRepository = calendarTaskRepository;
        this.workoutSessionRepository = workoutSessionRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
    }

    @Transactional(readOnly = true)
    public List<DailyStreakDaySummary> calculateRange(User user, LocalDate start, LocalDate end, LocalDate today) {
        if (user == null || user.getId() == null || start == null || end == null || today == null) {
            return List.of();
        }
        if (end.isBefore(start)) {
            return List.of();
        }

        List<CalendarTask> tasks = calendarTaskRepository.findByUserAndDateBetween(user, start, end);
        Map<LocalDate, List<CalendarTask>> tasksByDate = groupTasksByDate(tasks);

        List<WorkoutSession> workoutSessions = workoutSessionRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        Map<LocalDate, List<WorkoutSession>> workoutSessionsByDate = groupWorkoutSessionsByDate(workoutSessions);

        List<ScheduleOccurrence> occurrences = scheduleOccurrenceRepository.findByUserAndDateBetween(user, start, end);
        Map<LocalDate, List<ScheduleOccurrence>> occurrencesByDate = groupOccurrencesByDate(occurrences);

        List<DailyStreakDaySummary> results = new ArrayList<>();

        LocalDate d = start;
        while (!d.isAfter(end)) {
            List<CalendarTask> dayTasks = tasksByDate.getOrDefault(d, List.of());
            List<WorkoutSession> daySessions = workoutSessionsByDate.getOrDefault(d, List.of());
            List<ScheduleOccurrence> dayOccurrences = occurrencesByDate.getOrDefault(d, List.of());

            int totalTasks = dayTasks.size();
            int completedTasks = 0;
            int logsNeeded = 0;

            for (CalendarTask task : dayTasks) {
                boolean requiresLog = task.isRequiresLog();
                boolean hasLog = task.getExerciseLog() != null;
                boolean isCompleted = Boolean.TRUE.equals(task.getCompleted());

                if (requiresLog && !hasLog) {
                    logsNeeded++;
                }

                boolean countsAsCompleted = isCompleted && (!requiresLog || hasLog);
                if (countsAsCompleted) {
                    completedTasks++;
                }
            }

            Set<Long> occurrenceIds = dayOccurrences.stream().map(ScheduleOccurrence::getId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            List<WorkoutSession> independentSessions = daySessions.stream()
                    .filter(session -> session.getSourceOccurrenceId() == null
                            || !occurrenceIds.contains(session.getSourceOccurrenceId())).toList();
            Set<Long> completedSourceIds = daySessions.stream().filter(WorkoutSession::isCompleted)
                    .map(WorkoutSession::getSourceOccurrenceId).filter(java.util.Objects::nonNull)
                    .collect(Collectors.toSet());
            int totalWorkouts = independentSessions.size() + dayOccurrences.size();
            int completedWorkouts = 0;

            for (WorkoutSession session : independentSessions) {
                if (session != null && session.isCompleted()) {
                    completedWorkouts++;
                }
            }

            for (ScheduleOccurrence occ : dayOccurrences) {
                // A completed strength session is the modern log for its source occurrence.
                boolean hasLog = occ.getExerciseLog() != null || completedSourceIds.contains(occ.getId());
                boolean isCompleted = occ.isCompleted();

                if (!hasLog) {
                    logsNeeded++;
                }

                if (isCompleted && hasLog) {
                    completedWorkouts++;
                }
            }

            int completionPercentage = DailyCompletionCalculator.computeCompletionPercentage(
                    completedTasks,
                    totalTasks,
                    completedWorkouts,
                    totalWorkouts
            );

            int completedItems = completedTasks + completedWorkouts;
            int totalItems = totalTasks + totalWorkouts;
            DailyCompletionStatus status = DailyCompletionCalculator.computeStatus(d, completedItems, totalItems, today);

            results.add(new DailyStreakDaySummary(
                    d,
                    status,
                    completionPercentage,
                    completedTasks,
                    totalTasks,
                    completedWorkouts,
                    totalWorkouts,
                    logsNeeded
            ));

            d = d.plusDays(1);
        }

        return results;
    }

    private static Map<LocalDate, List<CalendarTask>> groupTasksByDate(List<CalendarTask> tasks) {
        Map<LocalDate, List<CalendarTask>> map = new HashMap<>();
        if (tasks == null) {
            return map;
        }
        for (CalendarTask task : tasks) {
            if (task == null || task.getDate() == null) {
                continue;
            }
            map.computeIfAbsent(task.getDate(), ignored -> new ArrayList<>()).add(task);
        }
        return map;
    }

    private static Map<LocalDate, List<WorkoutSession>> groupWorkoutSessionsByDate(List<WorkoutSession> sessions) {
        Map<LocalDate, List<WorkoutSession>> map = new HashMap<>();
        if (sessions == null) {
            return map;
        }
        for (WorkoutSession session : sessions) {
            if (session == null || session.getDate() == null) {
                continue;
            }
            map.computeIfAbsent(session.getDate(), ignored -> new ArrayList<>()).add(session);
        }
        return map;
    }

    private static Map<LocalDate, List<ScheduleOccurrence>> groupOccurrencesByDate(List<ScheduleOccurrence> occurrences) {
        Map<LocalDate, List<ScheduleOccurrence>> map = new HashMap<>();
        if (occurrences == null) {
            return map;
        }
        for (ScheduleOccurrence occurrence : occurrences) {
            if (occurrence == null || occurrence.getDate() == null) {
                continue;
            }
            map.computeIfAbsent(occurrence.getDate(), ignored -> new ArrayList<>()).add(occurrence);
        }
        return map;
    }
}
