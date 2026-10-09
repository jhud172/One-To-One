package uk.ac.cf._5.group14.One_To_One.Workout;


import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.AllArgsConstructor;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;


@Service
@AllArgsConstructor
public class WorkoutServiceImpl implements WorkoutService {

    private final UserService userService;
    private final ExerciseRepository exerciseRepository;
    private final CustomExerciseRepository customExerciseRepository;
    private final WorkoutRepository workoutRepository;
    private final AuthHelper authHelper;

    @Transactional
    public Workout saveWorkout(SaveWorkoutDTO dto){
        Long userId = authHelper.getAuthenticatedUser().getId();
        Workout workout;

        if (dto.getId() != null) {
            workout = workoutRepository.findByIdAndUserId(dto.getId(), userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        } else {
            workout = new Workout();
        }

        if (dto.getName() == null || dto.getName().isBlank() || dto.getName().trim().length() > 200
                || (dto.getWorkoutNotes() != null && dto.getWorkoutNotes().length() > 5000)) {
            throw new IllegalArgumentException("ui.scheduleWorkout.invalid");
        }
        List<Long> exerciseIds = uniqueIds(dto.getExerciseIds());
        List<Long> customIds = uniqueIds(dto.getCustomExerciseIds());
        if (exerciseIds.size() + customIds.size() < 1 || exerciseIds.size() + customIds.size() > 50) {
            throw new IllegalArgumentException("ui.scheduleWorkout.invalid");
        }
        Map<Long, Exercise> found = exerciseIds.isEmpty() ? Map.of()
                : toList(exerciseRepository.findAllById(exerciseIds)).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
        if (found.size() != exerciseIds.size()) {
            throw new IllegalArgumentException("ui.studio.reference");
        }
        List<Exercise> exercises = exerciseIds.stream().map(found::get).toList();
        List<CustomExercise> customExercises = new ArrayList<>();
        for (Long id : customIds) {
            customExercises.add(customExerciseRepository.findByIdAndUserId(id, userId)
                    .orElseThrow(() -> new IllegalArgumentException("ui.studio.reference")));
        }

        // Resolve and validate every reference before changing an existing workout.
        workout.setExercises(new ArrayList<>(exercises));
        workout.setCustomExercises(customExercises);
        workout.setUserId(userId);
        workout.setName(dto.getName().trim());
        workout.setNotes(dto.getWorkoutNotes());

        return workoutRepository.save(workout);
    }

    @Transactional
    public void deleteWorkout(Long workoutId) {
        Long userId = authHelper.getAuthenticatedUser().getId();
        Workout workout = workoutRepository.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (workoutRepository.isReferencedByTrainingData(workoutId)) {
            throw new org.springframework.dao.DataIntegrityViolationException("Workout is used by saved training data");
        }
        workoutRepository.delete(workout);
    }

    public List<Workout> getWorkouts() {
        Long userId = authHelper.getAuthenticatedUser().getId();

        return workoutRepository.findByUserId(userId);
    }

    public Workout getWorkoutToEdit(Long workoutId) {
        Long userId = authHelper.getAuthenticatedUser().getId();

        return workoutRepository.findByIdAndUserId(workoutId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private List<Long> uniqueIds(List<Long> ids) {
        if (ids == null) return List.of();
        if (ids.size() > 50 || ids.stream().anyMatch(id -> id == null || id <= 0)
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("ui.scheduleWorkout.invalid");
        }
        return ids;
    }

    private <T> List<T> toList(Iterable<T> items) {
        List<T> results = new ArrayList<>();
        for (T item : items) {
            results.add(item);
        }
        return results;
    }
}
