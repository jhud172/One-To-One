package uk.ac.cf._5.group14.One_To_One.WorkoutTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Workout.SaveWorkoutDTO;
import uk.ac.cf._5.group14.One_To_One.Workout.Workout;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutServiceImpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkoutEditTest {

    @Mock
    private UserService userService;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private WorkoutRepository workoutRepository;

    @Mock
    private AuthHelper authHelper;

    @InjectMocks
    private WorkoutServiceImpl workoutService;

    @Test
    void saveWorkout_shouldUpdateExistingWorkout_WhenIdIsProvided() {
        User mockUser = new User();
        mockUser.setId(10L);

        // Setup an existing workout
        Long existingWorkoutId = 50L;
        Workout existingWorkout = new Workout();
        existingWorkout.setId(existingWorkoutId);
        existingWorkout.setName("Old Name");
        existingWorkout.setNotes("Old Notes");
        existingWorkout.setUserId(10L);

        // Setup dto to update existingWorkout
        SaveWorkoutDTO dto = new SaveWorkoutDTO();
        dto.setId(existingWorkoutId);
        dto.setName("New Updated Name");
        dto.setWorkoutNotes("New Notes");
        dto.setExerciseIds(Collections.singletonList(99L));

        // setup exercises
        Exercise newExercise = new Exercise();
        newExercise.setId(99L);
        List<Exercise> newExercises = Collections.singletonList(newExercise);


        when(authHelper.getAuthenticatedUser()).thenReturn(mockUser);
        when(workoutRepository.findByIdAndUserId(existingWorkoutId, 10L)).thenReturn(Optional.of(existingWorkout));
        when(exerciseRepository.findAllById(dto.getExerciseIds())).thenReturn(newExercises);


        workoutService.saveWorkout(dto);


        ArgumentCaptor<Workout> workoutCaptor = ArgumentCaptor.forClass(Workout.class);
        verify(workoutRepository).save(workoutCaptor.capture());

        Workout updatedWorkout = workoutCaptor.getValue();

        // Check ID matches
        assertEquals(existingWorkoutId, updatedWorkout.getId());
        // Check fields updated
        assertEquals("New Updated Name", updatedWorkout.getName());
        assertEquals("New Notes", updatedWorkout.getNotes());
        assertEquals(1, updatedWorkout.getExercises().size());
        assertEquals(99L, updatedWorkout.getExercises().get(0).getId());
    }

    @Test
    void saveWorkout_shouldThrowException_WhenUpdatingNonExistentWorkout() {
        // Setup
        User mockUser = new User();
        mockUser.setId(10L);

        SaveWorkoutDTO dto = new SaveWorkoutDTO();
        dto.setId(999L); // random nonexistent id
        dto.setName("Ghost Workout");

        when(authHelper.getAuthenticatedUser()).thenReturn(mockUser);
        when(workoutRepository.findByIdAndUserId(999L, 10L)).thenReturn(Optional.empty());


        org.springframework.web.server.ResponseStatusException exception = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> {
            workoutService.saveWorkout(dto);
        });

        assertEquals(404, exception.getStatusCode().value());
        verify(workoutRepository, never()).save(any());
    }
}
