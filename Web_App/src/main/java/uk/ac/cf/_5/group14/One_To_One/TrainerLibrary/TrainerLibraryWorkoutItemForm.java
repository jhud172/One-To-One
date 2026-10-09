package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;

public class TrainerLibraryWorkoutItemForm {

    @NotNull
    private Long exerciseId;

    @NotNull
    @Min(1)
    private Integer sets;

    @NotNull
    @Min(1)
    private Integer reps;

    @NotNull
    @Min(0)
    private Integer restSeconds;

    @Min(1)
    @Max(10)
    private Integer rpe;

    @Min(0)
    private Integer orderIndex;

    public Long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(Long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public Integer getSets() {
        return sets;
    }

    public void setSets(Integer sets) {
        this.sets = sets;
    }

    public Integer getReps() {
        return reps;
    }

    public void setReps(Integer reps) {
        this.reps = reps;
    }

    public Integer getRestSeconds() {
        return restSeconds;
    }

    public void setRestSeconds(Integer restSeconds) {
        this.restSeconds = restSeconds;
    }

    public Integer getRpe() {
        return rpe;
    }

    public void setRpe(Integer rpe) {
        this.rpe = rpe;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }
}
