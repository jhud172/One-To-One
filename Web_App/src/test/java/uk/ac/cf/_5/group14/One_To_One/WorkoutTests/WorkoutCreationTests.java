package uk.ac.cf._5.group14.One_To_One.WorkoutTests;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;
import uk.ac.cf._5.group14.One_To_One.Workout.SaveWorkoutDTO;
import uk.ac.cf._5.group14.One_To_One.Workout.Workout;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutRepository;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutServiceImpl;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WorkoutCreationTests {

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


    // Successful case
    @Test
    void saveWorkout_shouldMapDtoToEntityAndSave() {

        // Set up test
        User mockUser = new User();
        mockUser.setId(10L);

        SaveWorkoutDTO dto = new SaveWorkoutDTO();
        dto.setName("Leg Day");
        dto.setWorkoutNotes("Focus on form, go to failure");
        dto.setExerciseIds(Arrays.asList(1L, 2L));

        Exercise ex1 = new Exercise();
        ex1.setId(1L);
        Exercise ex2 = new Exercise();
        ex2.setId(2L);
        List<Exercise> mockExercises = Arrays.asList(ex1, ex2);

        // Define mock responses
        given(authHelper.getAuthenticatedUser()).willReturn(mockUser);
        when(exerciseRepository.findAllById(dto.getExerciseIds())).thenReturn(mockExercises);

        workoutService.saveWorkout(dto);

        ArgumentCaptor<Workout> workoutCaptor = ArgumentCaptor.forClass(Workout.class);
        // Catch workout being passed to the repo
        verify(workoutRepository, times(1)).save(workoutCaptor.capture());

        Workout savedWorkout = workoutCaptor.getValue();

        // Assertions
        assertEquals("Leg Day", savedWorkout.getName(), "Workout name should match DTO");
        assertEquals("Focus on form, go to failure", savedWorkout.getNotes(), "Notes should match DTO");
        assertEquals(10L, savedWorkout.getUserId(), "User ID should be set from authenticated user");

        assertEquals(2, savedWorkout.getExercises().size());
        assertEquals(mockExercises, savedWorkout.getExercises());
    }

    // Missing references must never silently save a smaller workout.
    @Test
    void saveWorkout_shouldHandleNonexistentExercises() {

        User mockUser = new User();
        mockUser.setId(10L);

        SaveWorkoutDTO dto = new SaveWorkoutDTO();
        dto.setName("Leg Day");
        dto.setWorkoutNotes("Focus on form, go to failure");

        Long validExId = 1L;
        Long invalidExId = 670L;

        dto.setExerciseIds(Arrays.asList(validExId, invalidExId));

        Exercise validEx = new Exercise();
        validEx.setId(validExId);

        when(exerciseRepository.findAllById(dto.getExerciseIds()))
                .thenReturn(List.of(validEx));
        when(authHelper.getAuthenticatedUser()).thenReturn(mockUser);

        IllegalArgumentException exception = Assertions.assertThrows(IllegalArgumentException.class,
                () -> workoutService.saveWorkout(dto));
        assertEquals("ui.studio.reference", exception.getMessage());
        verify(workoutRepository, never()).save(any());


    }

    @Test
    void saveWorkout_shouldFail_WhenUserNotFound() {

        SaveWorkoutDTO dto = new SaveWorkoutDTO();
        dto.setName("Leg Day (No user)");
        dto.setWorkoutNotes("Focus on form, go to failure, throw an error");


        when(authHelper.getAuthenticatedUser())
                .thenThrow(new RuntimeException("User not found in context"));

       // Test should fail as it should not be possible to save a workout without a userId
        Assertions.assertThrows(
                RuntimeException.class,
                () -> workoutService.saveWorkout(dto)
        );
        // Check no workout was saved
        verify(workoutRepository, never()).save(any());
    }
}


