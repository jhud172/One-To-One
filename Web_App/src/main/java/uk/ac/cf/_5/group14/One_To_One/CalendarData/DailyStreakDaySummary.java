package uk.ac.cf._5.group14.One_To_One.CalendarData;

import java.time.LocalDate;

public record DailyStreakDaySummary(
        LocalDate date,
        DailyCompletionStatus status,
        int completionPercentage,
        int completedTasks,
        int totalTasks,
        int completedWorkouts,
        int totalWorkouts,
        int logsNeeded
) {
    public int completedCount() { return completedTasks + completedWorkouts; }
    public int totalCount() { return totalTasks + totalWorkouts; }
    public int remainingTasks() { return Math.max(0, totalTasks - completedTasks); }
    public int remainingWorkouts() { return Math.max(0, totalWorkouts - completedWorkouts); }
    public String statusKey() {
        if (totalCount() == 0) return "empty";
        return switch (status) {
            case GREEN -> "complete";
            case BLUE -> "active";
            case ORANGE -> "partial";
            case RED -> "unfinished";
            default -> "planned";
        };
    }
}
