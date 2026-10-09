package uk.ac.cf._5.group14.One_To_One.Workout;

import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

public interface WorkoutService {
    Workout saveWorkout(SaveWorkoutDTO dto);

    void deleteWorkout(Long workoutId);


    List<Workout> getWorkouts();

    Workout getWorkoutToEdit(Long workoutId);
}
