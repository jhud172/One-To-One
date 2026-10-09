package uk.ac.cf._5.group14.One_To_One.Workouts;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkoutExerciseForm {
    private Long id;
    private String exerciseRef;
    private String exerciseName;
    private Long exerciseId;
    private Long customExerciseId;
    private Integer sets;
    private Integer reps;
    private Integer restSeconds;
    private String notes;
}
