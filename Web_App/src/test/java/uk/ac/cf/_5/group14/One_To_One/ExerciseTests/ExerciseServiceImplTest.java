package uk.ac.cf._5.group14.One_To_One.ExerciseTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.ConditionsPreferences.UserPreference.UserPreferenceRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseServiceImpl;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Tag;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExerciseServiceImplTest {

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private UserPreferenceRepository userPreferenceRepository;

    @InjectMocks
    private ExerciseServiceImpl exerciseService;

    @Test
    void getAllExercises_ShouldReturnAll() {
        Exercise e1 = new Exercise();
        Exercise e2 = new Exercise();
        when(exerciseRepository.findAll()).thenReturn(Arrays.asList(e1, e2));
        List<Exercise> result = exerciseService.getAllExercises();
        assertEquals(2, result.size());
        assertTrue(result.contains(e1));
        assertTrue(result.contains(e2));
    }

    @Test
    void getExerciseById_ShouldReturnExercise_WhenExists() {
        Exercise exercise = new Exercise();
        when(exerciseRepository.findById(1L)).thenReturn(Optional.of(exercise));
        Exercise result = exerciseService.getExerciseById(1L);
        assertEquals(exercise, result);
    }

    @Test
    void getExerciseById_ShouldReturnNull_WhenNotExists() {
        when(exerciseRepository.findById(1L)).thenReturn(Optional.empty());
        Exercise result = exerciseService.getExerciseById(1L);
        assertNull(result);
    }

    @Test
    void saveExercise_ShouldCallRepositorySave() {
        Exercise exercise = new Exercise();
        exerciseService.saveExercise(exercise);
        verify(exerciseRepository).save(exercise);
    }

    @Test
    void noPreferencesStillProvidesACatalogue() {
        User user = new User(); user.setId(1L);
        Exercise exercise = new Exercise(); exercise.setName("Available movement");
        when(userPreferenceRepository.getPreferredTags(user)).thenReturn(Set.of());
        when(userPreferenceRepository.getBannedTags(user)).thenReturn(Set.of());
        when(exerciseRepository.findAll()).thenReturn(List.of(exercise));
        assertEquals(List.of(exercise), exerciseService.suggestExercises(user));
    }

    @Test
    void noPreferencesMustNotBypassSavedConditionExclusions() {
        User user = new User(); user.setId(1L);
        Tag banned = new Tag(); banned.setId(1L);
        Exercise permitted = new Exercise(); permitted.setName("Permitted movement");
        when(userPreferenceRepository.getPreferredTags(user)).thenReturn(Set.of());
        when(userPreferenceRepository.getBannedTags(user)).thenReturn(Set.of(banned));
        when(exerciseRepository.getExercisesExcludingTags(Set.of(banned))).thenReturn(List.of(permitted));
        assertEquals(List.of(permitted), exerciseService.suggestExercises(user));
        verify(exerciseRepository, never()).findAll();
    }
}
