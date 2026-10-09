package uk.ac.cf._5.group14.One_To_One.StrengthLog;

import java.time.LocalDate;
import java.util.List;

public record ScheduledWorkoutSessionViewModel(
        Long sessionId,
        String workoutName,
        LocalDate date,
        boolean completed,
        String sourceLabel,
        Summary summary,
        List<ExerciseView> exercises
) {

    public record Summary(
            int exerciseCount,
            int completedExercises,
            int totalSets,
            int completedSets,
            double totalVolume,
            int completionPercent
    ) {
    }

    public record ExerciseView(
            Long exerciseSessionId,
            String displayName,
            String category,
            String type,
            String status,
            boolean completed,
            List<SetView> sets,
            Long catalogueExerciseId
    ) {
    }

    public record SetView(
            Long setId,
            int setNumber,
            Double weight,
            Integer reps,
            String notes,
            boolean completed
    ) {
    }
}
