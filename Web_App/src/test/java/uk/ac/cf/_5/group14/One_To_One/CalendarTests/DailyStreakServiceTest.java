package uk.ac.cf._5.group14.One_To_One.CalendarTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyCompletionStatus;
import uk.ac.cf._5.group14.One_To_One.CalendarData.DailyStreakService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class DailyStreakServiceTest {

    @Mock
    private CalendarTaskRepository calendarTaskRepository;

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;

    @Mock
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @InjectMocks
    private DailyStreakService dailyStreakService;

    @Test
    void completedStrengthLogCountsItsSourceOnceAndExistingLogsAreNotRequestedAgain() {
        var user = new User(); user.setId(123L);
        var date = LocalDate.of(2026, 1, 15);
        var occurrence = new ScheduleOccurrence();
        occurrence.setId(7L); occurrence.setDate(date); occurrence.setCompleted(true);
        var linked = new WorkoutSession();
        linked.setId(8L); linked.setDate(date); linked.setSourceOccurrenceId(7L); linked.setCompleted(true);
        var task = new CalendarTask();
        task.setDate(date); task.setRequiresLog(true); task.setCompleted(false);
        task.setExerciseLog(new uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLog());
        given(calendarTaskRepository.findByUserAndDateBetween(user, date, date)).willReturn(List.of(task));
        given(workoutSessionRepository.findByUserAndDateBetweenOrderByDateDesc(user, date, date)).willReturn(List.of(linked));
        given(scheduleOccurrenceRepository.findByUserAndDateBetween(user, date, date)).willReturn(List.of(occurrence));
        var result = dailyStreakService.calculateRange(user, date, date, date).getFirst();
        assertThat(result.totalWorkouts()).isEqualTo(1);
        assertThat(result.completedWorkouts()).isEqualTo(1);
        assertThat(result.completedTasks()).isZero();
        assertThat(result.logsNeeded()).isZero();
        assertThat(result.completionPercentage()).isEqualTo(50);
    }

    @Test
    void independentSessionIdCollisionIsNotASourceLinkAndIncompleteSourcesStillNeedLogs() {
        var user = new User(); user.setId(123L);
        var date = LocalDate.of(2026, 1, 15);
        var occurrence = new ScheduleOccurrence(); occurrence.setId(7L); occurrence.setDate(date);
        var independent = new WorkoutSession(); independent.setId(7L); independent.setDate(date); independent.setCompleted(true);
        var linked = new WorkoutSession(); linked.setId(8L); linked.setDate(date); linked.setSourceOccurrenceId(7L);
        given(calendarTaskRepository.findByUserAndDateBetween(user, date, date)).willReturn(List.of());
        given(workoutSessionRepository.findByUserAndDateBetweenOrderByDateDesc(user, date, date)).willReturn(List.of(independent, linked));
        given(scheduleOccurrenceRepository.findByUserAndDateBetween(user, date, date)).willReturn(List.of(occurrence));
        var result = dailyStreakService.calculateRange(user, date, date, date).getFirst();
        assertThat(result.totalWorkouts()).isEqualTo(2);
        assertThat(result.completedWorkouts()).isEqualTo(1);
        assertThat(result.logsNeeded()).isEqualTo(1);
    }

    @Test
    void nullInputsShouldReturnEmpty() {
        assertThat(dailyStreakService.calculateRange(null, LocalDate.now(), LocalDate.now(), LocalDate.now())).isEmpty();
        assertThat(dailyStreakService.calculateRange(new User(), LocalDate.now(), LocalDate.now(), LocalDate.now())).isEmpty();
        User u = new User();
        u.setId(1L);
        assertThat(dailyStreakService.calculateRange(u, null, LocalDate.now(), LocalDate.now())).isEmpty();
        assertThat(dailyStreakService.calculateRange(u, LocalDate.now(), null, LocalDate.now())).isEmpty();
        assertThat(dailyStreakService.calculateRange(u, LocalDate.now(), LocalDate.now(), null)).isEmpty();

        then(calendarTaskRepository).shouldHaveNoInteractions();
        then(workoutSessionRepository).shouldHaveNoInteractions();
        then(scheduleOccurrenceRepository).shouldHaveNoInteractions();
    }

    @Test
    void rangeShouldComputeDeterministicStatusesAndPercentages() {
        User user = new User();
        user.setId(123L);

        LocalDate start = LocalDate.of(2026, 1, 13);
        LocalDate end = LocalDate.of(2026, 1, 15);
        LocalDate today = LocalDate.of(2026, 1, 15);

        // 2026-01-13: 1 task, none completed (past day => RED)
        CalendarTask t13 = new CalendarTask();
        t13.setDate(LocalDate.of(2026, 1, 13));
        t13.setCompleted(false);
        t13.setRequiresLog(false);

        // 2026-01-14: 2 tasks, one requires log but missing it (=> ORANGE)
        CalendarTask t14a = new CalendarTask();
        t14a.setDate(LocalDate.of(2026, 1, 14));
        t14a.setCompleted(true);
        t14a.setRequiresLog(false);

        CalendarTask t14b = new CalendarTask();
        t14b.setDate(LocalDate.of(2026, 1, 14));
        t14b.setCompleted(true);
        t14b.setRequiresLog(true);
        t14b.setExerciseLog(null);

        // 2026-01-15: one completed workout session (=> GREEN)
        WorkoutSession ws15 = new WorkoutSession();
        ws15.setDate(LocalDate.of(2026, 1, 15));
        ws15.setCompleted(true);

        // 2026-01-14: one schedule occurrence completed but missing log (counts as workout but not completed)
        ScheduleOccurrence occ14 = new ScheduleOccurrence();
        occ14.setDate(LocalDate.of(2026, 1, 14));
        occ14.setCompleted(true);
        occ14.setExerciseLog(null);

        given(calendarTaskRepository.findByUserAndDateBetween(user, start, end)).willReturn(List.of(t13, t14a, t14b));
        given(workoutSessionRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end)).willReturn(List.of(ws15));
        given(scheduleOccurrenceRepository.findByUserAndDateBetween(user, start, end)).willReturn(List.of(occ14));

        var result = dailyStreakService.calculateRange(user, start, end, today);

        assertThat(result).hasSize(3);

        var day13 = result.get(0);
        assertThat(day13.date()).isEqualTo(LocalDate.of(2026, 1, 13));
        assertThat(day13.totalTasks()).isEqualTo(1);
        assertThat(day13.completedTasks()).isEqualTo(0);
        assertThat(day13.totalWorkouts()).isEqualTo(0);
        assertThat(day13.completedWorkouts()).isEqualTo(0);
        assertThat(day13.status()).isEqualTo(DailyCompletionStatus.RED);

        var day14 = result.get(1);
        assertThat(day14.date()).isEqualTo(LocalDate.of(2026, 1, 14));
        assertThat(day14.totalTasks()).isEqualTo(2);
        assertThat(day14.completedTasks()).isEqualTo(1);
        assertThat(day14.totalWorkouts()).isEqualTo(1);
        assertThat(day14.completedWorkouts()).isEqualTo(0);
        assertThat(day14.status()).isEqualTo(DailyCompletionStatus.ORANGE);
        assertThat(day14.logsNeeded()).isGreaterThanOrEqualTo(1);

        var day15 = result.get(2);
        assertThat(day15.date()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(day15.totalTasks()).isEqualTo(0);
        assertThat(day15.completedTasks()).isEqualTo(0);
        assertThat(day15.totalWorkouts()).isEqualTo(1);
        assertThat(day15.completedWorkouts()).isEqualTo(1);
        assertThat(day15.status()).isEqualTo(DailyCompletionStatus.GREEN);

        then(calendarTaskRepository).should().findByUserAndDateBetween(user, start, end);
        then(workoutSessionRepository).should().findByUserAndDateBetweenOrderByDateDesc(user, start, end);
        then(scheduleOccurrenceRepository).should().findByUserAndDateBetween(user, start, end);
    }
}
