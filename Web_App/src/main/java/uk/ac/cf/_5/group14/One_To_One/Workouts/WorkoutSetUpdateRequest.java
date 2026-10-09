package uk.ac.cf._5.group14.One_To_One.Workouts;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkoutSetUpdateRequest {
    private Double weight;
    private Integer reps;
    private String notes;
    private Boolean completed;
    // Existing API callers keep patch semantics; the player submits all current values.
    private boolean replaceValues;
}
