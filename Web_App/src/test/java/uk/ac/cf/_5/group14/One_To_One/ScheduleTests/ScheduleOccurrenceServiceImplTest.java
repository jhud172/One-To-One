package uk.ac.cf._5.group14.One_To_One.ScheduleTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExercise;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.Exercise;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.*;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ScheduleOccurrenceServiceImplTest {

    @Mock private ScheduleEntryService scheduleEntryService;
    @Mock private ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    @Mock private ScheduleAppliedRepository scheduleAppliedRepository;
    @Spy private ScheduleDeploymentPlanner planner = new ScheduleDeploymentPlanner();

    @InjectMocks
    private ScheduleOccurrenceServiceImpl scheduleOccurrenceService;

    @Test
    void generateOccurrences_ShouldReturnEarly_WhenArgumentsNull() {
        scheduleOccurrenceService.generateOccurrencesForSchedule(null, new User(), LocalDate.now(), LocalDate.now(), 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
        scheduleOccurrenceService.generateOccurrencesForSchedule(new Schedule(), null, LocalDate.now(), LocalDate.now(), 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
        scheduleOccurrenceService.generateOccurrencesForSchedule(new Schedule(), new User(), null, LocalDate.now(), 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
        scheduleOccurrenceService.generateOccurrencesForSchedule(new Schedule(), new User(), LocalDate.now(), null, 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void generateOccurrences_ShouldReturnEarly_WhenNoEntries() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        User user = new User();
        LocalDate start = LocalDate.of(2023, 9, 25);
        LocalDate end = LocalDate.of(2023, 9, 27);
        when(scheduleEntryService.getEntries(schedule.getId())).thenReturn(Collections.emptyList());
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, start, end, 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void generateOccurrences_ShouldCreateOccurrencesForMatchingDays() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setName("Test");
        User user = new User();
        user.setId(1L);
        ScheduleEntry mondayEntry = new ScheduleEntry();
        mondayEntry.setDayOfWeek(1);
        Exercise exercise = new Exercise();
        mondayEntry.setExercise(exercise);
        ScheduleEntry tuesdayEntry = new ScheduleEntry();
        tuesdayEntry.setDayOfWeek(2);
        CustomExercise customExercise = new CustomExercise();
        tuesdayEntry.setCustomExercise(customExercise);
        when(scheduleEntryService.getEntries(schedule.getId())).thenReturn(Arrays.asList(mondayEntry, tuesdayEntry));
        LocalDate start = LocalDate.of(2023, 9, 25);
        LocalDate end = LocalDate.of(2023, 9, 26);
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, start, end, 1);
        ArgumentCaptor<ScheduleOccurrence> captor = ArgumentCaptor.forClass(ScheduleOccurrence.class);
        verify(scheduleOccurrenceRepository, times(2)).save(captor.capture());
        List<ScheduleOccurrence> saved = captor.getAllValues();
        ScheduleOccurrence occ0 = saved.get(0);
        assertEquals(user, occ0.getUser());
        assertEquals(schedule, occ0.getSchedule());
        assertEquals("Test", occ0.getScheduleName());
        assertEquals(start, occ0.getDate());
        assertEquals(exercise, occ0.getExercise());
        assertNull(occ0.getCustomExercise());
        ScheduleOccurrence occ1 = saved.get(1);
        assertEquals(user, occ1.getUser());
        assertEquals(schedule, occ1.getSchedule());
        assertEquals("Test", occ1.getScheduleName());
        assertEquals(end, occ1.getDate());
        assertNull(occ1.getExercise());
        assertEquals(customExercise, occ1.getCustomExercise());
    }

    @Test
    void getOccurrencesForUserOnDate_ShouldReturnList() {
        User user = new User();
        user.setId(1L);
        LocalDate date = LocalDate.now();
        List<ScheduleOccurrence> list = Arrays.asList(new ScheduleOccurrence(), new ScheduleOccurrence());
        when(scheduleOccurrenceRepository.findByUserAndDate(user, date)).thenReturn(list);
        List<ScheduleOccurrence> result = scheduleOccurrenceService.getOccurrencesForUserOnDate(user, date);
        assertEquals(list, result);
    }

    @Test
    void getOccurrencesForUserInMonth_ShouldGroupByDate() {
        User user = new User();
        LocalDate date1 = LocalDate.of(2023, 9, 25);
        LocalDate date2 = LocalDate.of(2023, 9, 26);
        ScheduleOccurrence occ1 = new ScheduleOccurrence();
        occ1.setDate(date1);
        ScheduleOccurrence occ2 = new ScheduleOccurrence();
        occ2.setDate(date1);
        ScheduleOccurrence occ3 = new ScheduleOccurrence();
        occ3.setDate(date2);
        when(scheduleOccurrenceRepository.findByUserAndDateBetween(eq(user), any(), any())).thenReturn(Arrays.asList(occ1, occ2, occ3));
        Map<LocalDate, List<ScheduleOccurrence>> result = scheduleOccurrenceService.getOccurrencesForUserInMonth(user, 2023, 9);
        assertEquals(2, result.size());
        assertEquals(2, result.get(date1).size());
        assertEquals(1, result.get(date2).size());
    }

    @Test
    void getActiveSchedulesForUser_ShouldReturnList() {
        User user = new User();
        List<ScheduleOccurrence> list = Arrays.asList(new ScheduleOccurrence());
        when(scheduleOccurrenceRepository.findActiveByUser(user)).thenReturn(list);
        List<ScheduleOccurrence> result = scheduleOccurrenceService.getActiveSchedulesForUser(user);
        assertEquals(list, result);
    }

    @Test
    void getOccurrencesByRange_ShouldGroupByDate() {
        User user = new User();
        LocalDate date1 = LocalDate.of(2023, 9, 25);
        LocalDate date2 = LocalDate.of(2023, 9, 26);
        ScheduleOccurrence occ1 = new ScheduleOccurrence();
        occ1.setDate(date1);
        ScheduleOccurrence occ2 = new ScheduleOccurrence();
        occ2.setDate(date2);
        when(scheduleOccurrenceRepository.findByUserAndDateBetween(eq(user), any(), any())).thenReturn(Arrays.asList(occ1, occ2));
        Map<LocalDate, List<ScheduleOccurrence>> result = scheduleOccurrenceService.getOccurrencesByRange(user, date1, date2);
        assertEquals(2, result.size());
        assertEquals(1, result.get(date1).size());
        assertEquals(1, result.get(date2).size());
    }

    @Test
    void generateOccurrences_ShouldReturnEarly_WhenEndBeforeStart() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        User user = new User();
        LocalDate start = LocalDate.of(2023, 9, 26);
        LocalDate end = LocalDate.of(2023, 9, 25);
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, start, end, 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void generateOccurrences_ShouldHandleEntryWithBothExerciseTypes() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setName("Both");
        User user = new User();
        LocalDate date = LocalDate.of(2023, 9, 25); // Monday
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDayOfWeek(1);
        entry.setExercise(new Exercise());
        entry.setCustomExercise(new CustomExercise());
        when(scheduleEntryService.getEntries(schedule.getId())).thenReturn(List.of(entry));
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, date, date, 1);
        ArgumentCaptor<ScheduleOccurrence> captor = ArgumentCaptor.forClass(ScheduleOccurrence.class);
        verify(scheduleOccurrenceRepository).save(captor.capture());
        ScheduleOccurrence saved = captor.getValue();
        assertNotNull(saved.getExercise());
        assertNotNull(saved.getCustomExercise());
    }

    @Test
    void generateOccurrences_ShouldSkipEntryWithNoExerciseTypes() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setName("Skip");
        User user = new User();
        LocalDate date = LocalDate.of(2023, 9, 25);
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDayOfWeek(1);
        when(scheduleEntryService.getEntries(schedule.getId())).thenReturn(List.of(entry));
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, date, date, 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void generateOccurrences_ShouldSkipWhenNoMatchingDays() {
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        User user = new User();
        LocalDate start = LocalDate.of(2023, 9, 25); // Monday
        LocalDate end = LocalDate.of(2023, 9, 26);   // Tuesday
        ScheduleEntry entry = new ScheduleEntry();
        entry.setDayOfWeek(5);
        when(scheduleEntryService.getEntries(schedule.getId())).thenReturn(List.of(entry));
        scheduleOccurrenceService.generateOccurrencesForSchedule(schedule, user, start, end, 1);
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void explicitTwoWeekIntervalKeepsCompletedMatchesAndAddsOnlyMissingDates() {
        var plan = new Schedule(); plan.setId(9L); plan.setName("Fortnightly");
        var user = new User(); user.setId(1L);
        var row = new ScheduleEntry(); row.setSchedule(plan); row.setDayOfWeek(1);
        var exercise = new Exercise(); exercise.setId(5L); row.setExercise(exercise);
        var start = LocalDate.of(2027,1,4); var end = start.plusWeeks(6).minusDays(1);
        var existing = new ScheduleOccurrence(); existing.setSchedule(plan); existing.setUser(user);
        existing.setExercise(exercise); existing.setDate(start.plusWeeks(2)); existing.setCompleted(true);
        when(scheduleEntryService.getEntries(9L)).thenReturn(List.of(row));
        when(scheduleOccurrenceRepository.findByUserAndDateBetween(user,start,end)).thenReturn(List.of(existing));
        scheduleOccurrenceService.generateOccurrencesForSchedule(plan,user,start,end,2);
        var saved = ArgumentCaptor.forClass(ScheduleOccurrence.class);
        verify(scheduleOccurrenceRepository,times(2)).save(saved.capture());
        assertEquals(List.of(start,start.plusWeeks(4)),saved.getAllValues().stream().map(ScheduleOccurrence::getDate).toList());
        assertTrue(existing.isCompleted());
    }

    @Test
    void customTenDayCycleIncludesDayEightAndRejectsInvalidIntervals() {
        var plan = new Schedule(); plan.setId(9L); plan.setScheduleType(ScheduleType.CUSTOM);
        plan.setCustomDayCount(10); plan.setRotationMode(RotationMode.CONTINUOUS_ROTATION);
        var user = new User(); var row = new ScheduleEntry(); row.setSchedule(plan); row.setDayOfWeek(8);
        row.setCustomExercise(new CustomExercise());
        var start = LocalDate.of(2027,1,4); var end = start.plusWeeks(4).minusDays(1);
        scheduleOccurrenceService.generateOccurrencesForSchedule(plan,user,start,end,0);
        verifyNoInteractions(scheduleEntryService,scheduleOccurrenceRepository);
        when(scheduleEntryService.getEntries(9L)).thenReturn(List.of(row));
        scheduleOccurrenceService.generateOccurrencesForSchedule(plan,user,start,end,1);
        var saved = ArgumentCaptor.forClass(ScheduleOccurrence.class);
        verify(scheduleOccurrenceRepository,times(3)).save(saved.capture());
        assertEquals(List.of(LocalDate.of(2027,1,11),LocalDate.of(2027,1,21),LocalDate.of(2027,1,31)),
                saved.getAllValues().stream().map(ScheduleOccurrence::getDate).toList());
    }
}
