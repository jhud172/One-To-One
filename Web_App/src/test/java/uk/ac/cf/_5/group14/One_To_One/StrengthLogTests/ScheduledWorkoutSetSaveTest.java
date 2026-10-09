package uk.ac.cf._5.group14.One_To_One.StrengthLogTests;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.ExerciseSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.SetLog;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.ExerciseSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.SetLogRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Service.ScheduledWorkoutSessionService;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledWorkoutSetSaveTest {
    @Mock private WorkoutSessionRepository sessions;
    @Mock private ExerciseSessionRepository exercises;
    @Mock private SetLogRepository sets;
    @Mock private ScheduleOccurrenceRepository occurrences;
    @Mock private ScheduleEntryRepository entries;
    @InjectMocks private ScheduledWorkoutSessionService service;
    private User user;
    private WorkoutSession session;
    private SetLog set;

    @BeforeEach
    void prepareOwnedSet() {
        user = new User();
        user.setId(1L);
        session = new WorkoutSession();
        session.setId(10L);
        session.setUser(user);
        ExerciseSession exercise = new ExerciseSession();
        exercise.setWorkoutSession(session);
        session.getExerciseSessions().add(exercise);
        set = new SetLog();
        set.setId(20L);
        set.setSetNumber(1);
        set.setExerciseSession(exercise);
        exercise.getSetLogs().add(set);
        when(sessions.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(session));
    }

    @Test
    void completionSavesEnteredValuesAndUpdatesProgressTogether() {
        when(sets.findByIdAndExerciseSession_WorkoutSession_Id(20L, 10L)).thenReturn(Optional.of(set));
        service.updateSet(user, 10L, 20L, 42.5, 8, "  Controlled tempo  ", true);
        assertThat(set.getWeight()).isEqualTo(42.5);
        assertThat(set.getReps()).isEqualTo(8);
        assertThat(set.getNotes()).isEqualTo("Controlled tempo");
        assertThat(set.isCompleted()).isTrue();
        assertThat(set.getExerciseSession().isCompleted()).isTrue();
        assertThat(session.isCompleted()).isTrue();
        verify(sets).save(set);
    }

    @Test
    void invalidWeightCannotChangeValuesOrCompletion() {
        when(sets.findByIdAndExerciseSession_WorkoutSession_Id(20L, 10L)).thenReturn(Optional.of(set));
        assertThatThrownBy(() -> service.updateSet(user, 10L, 20L, Double.NaN, 8, "New note", true))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(set.getWeight()).isNull();
        assertThat(set.isCompleted()).isFalse();
        verify(sets, never()).save(any());
    }

    @Test
    void setFromAnotherSessionCannotBeUpdated() {
        when(sets.findByIdAndExerciseSession_WorkoutSession_Id(20L, 10L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSet(user, 10L, 20L, 42.5, 8, "New note", true))
                .isInstanceOf(ResponseStatusException.class);
        verify(sets, never()).save(any());
    }

    @Test
    void oversizedNotesCannotChangeSavedValues() {
        when(sets.findByIdAndExerciseSession_WorkoutSession_Id(20L, 10L)).thenReturn(Optional.of(set));
        set.setWeight(30.0);
        assertThatThrownBy(() -> service.updateSet(user, 10L, 20L, 42.5, 8, "x".repeat(256), true))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(set.getWeight()).isEqualTo(30.0);
        assertThat(set.getNotes()).isNull();
        assertThat(set.isCompleted()).isFalse();
        verify(sets, never()).save(any());
    }

    @Test
    void nearlyFinishedSetsCannotRoundToComplete() {
        var exercise = set.getExerciseSession();
        exercise.getSetLogs().clear();
        for (int index = 0; index < 200; index++) {
            var entry = new SetLog();
            entry.setSetNumber(index + 1);
            entry.setCompleted(index < 199);
            exercise.getSetLogs().add(entry);
        }
        when(exercises.findByWorkoutSessionOrderByOrderIndexAsc(session)).thenReturn(java.util.List.of(exercise));
        assertThat(service.buildViewModel(user, 10L).summary().completionPercent()).isEqualTo(99);
        exercise.getSetLogs().getLast().setCompleted(true);
        assertThat(service.buildViewModel(user, 10L).summary().completionPercent()).isEqualTo(100);
    }
}
