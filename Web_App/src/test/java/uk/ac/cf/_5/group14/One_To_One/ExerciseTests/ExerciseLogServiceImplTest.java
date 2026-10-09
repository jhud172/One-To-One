package uk.ac.cf._5.group14.One_To_One.ExerciseTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLog;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogForm;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseLog.ExerciseLogServiceImpl;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExerciseLogServiceImplTest {

    @Mock
    private ExerciseLogRepository exerciseLogRepository;

    @Mock
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Mock
    private CalendarTaskRepository calendarTaskRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private ExerciseLogServiceImpl exerciseLogService;

    @org.junit.jupiter.api.BeforeEach
    void injectPersistenceContext() {
        org.springframework.test.util.ReflectionTestUtils.setField(exerciseLogService, "entityManager", entityManager);
    }

    @Test
    void saveLog_ShouldSaveLogWithoutAssociations() {
        ExerciseLogForm form = new ExerciseLogForm();
        form.setDate(LocalDate.of(2023, 9, 25));
        form.setMoodBefore(2);
        form.setMoodAfter(3);
        form.setConfidence(4);
        form.setComments("Good");
        form.setDurationMinutes(30);
        User user = new User();
        user.setId(1L);
        ExerciseLog saved = new ExerciseLog();
        when(exerciseLogRepository.save(any())).thenReturn(saved);
        exerciseLogService.saveLog(form, user);
        ArgumentCaptor<ExerciseLog> captor = ArgumentCaptor.forClass(ExerciseLog.class);
        verify(exerciseLogRepository).save(captor.capture());
        ExerciseLog savedLog = captor.getValue();
        assertEquals(user, savedLog.getUser());
        assertEquals(form.getDate(), savedLog.getDate());
        assertEquals(form.getMoodBefore(), savedLog.getMoodBefore());
        assertEquals(form.getMoodAfter(), savedLog.getMoodAfter());
        assertEquals(form.getConfidence(), savedLog.getConfidence());
        assertEquals(form.getComments(), savedLog.getComments());
        assertEquals(form.getDurationMinutes(), savedLog.getDurationMinutes());
        verify(scheduleOccurrenceRepository, never()).findById(any());
        verify(scheduleOccurrenceRepository, never()).save(any());
        verify(calendarTaskRepository, never()).findById(any());
        verify(calendarTaskRepository, never()).save(any());
    }

    @Test
    void saveLog_ShouldAssociateOccurrence_WhenOccurrenceIdPresent() {
        ExerciseLogForm form = new ExerciseLogForm();
        form.setDate(LocalDate.now());
        form.setMoodBefore(1);
        form.setMoodAfter(2);
        form.setConfidence(3);
        form.setComments(null);
        form.setDurationMinutes(20);
        form.setOccurrenceId(1L);
        User user = new User();
        user.setId(1L);
        ExerciseLog savedLog = new ExerciseLog();
        when(exerciseLogRepository.save(any())).thenReturn(savedLog);
        ScheduleOccurrence occurrence = new ScheduleOccurrence();
        occurrence.setId(1L); occurrence.setUser(user); occurrence.setDate(form.getDate());
        when(scheduleOccurrenceRepository.findOwnedForLogUpdate(1L, 1L)).thenReturn(Optional.of(occurrence));
        exerciseLogService.saveLog(form, user);
        verify(scheduleOccurrenceRepository).save(occurrence);
        assertEquals(savedLog, occurrence.getExerciseLog());
    }

    @Test
    void saveLog_ShouldNotSaveOccurrence_WhenOccurrenceNotFound() {
        ExerciseLogForm form = new ExerciseLogForm();
        form.setDate(LocalDate.now());
        form.setMoodBefore(1);
        form.setMoodAfter(2);
        form.setConfidence(3);
        form.setComments(null);
        form.setDurationMinutes(20);
        form.setOccurrenceId(99L);
        User user = new User();
        user.setId(1L);
        ExerciseLog saved = new ExerciseLog();
        when(scheduleOccurrenceRepository.findOwnedForLogUpdate(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> exerciseLogService.saveLog(form, user));
        verify(exerciseLogRepository, never()).save(any());
        verify(scheduleOccurrenceRepository, never()).save(any());
    }

    @Test
    void saveLog_ShouldAssociateCalendarTask_WhenCalendarTaskIdPresent() {
        ExerciseLogForm form = new ExerciseLogForm();
        form.setDate(LocalDate.now());
        form.setMoodBefore(1);
        form.setMoodAfter(2);
        form.setConfidence(3);
        form.setComments(null);
        form.setDurationMinutes(20);
        form.setCalendarTaskId(1L);
        User user = new User();
        user.setId(1L);
        ExerciseLog savedLog = new ExerciseLog();
        when(exerciseLogRepository.save(any())).thenReturn(savedLog);
        CalendarTask task = new CalendarTask();
        task.setId(1L); task.setUser(user); task.setDate(form.getDate());
        task.setCompleted(false);
        when(calendarTaskRepository.findOwnedForLogUpdate(1L, 1L)).thenReturn(Optional.of(task));
        exerciseLogService.saveLog(form, user);
        verify(calendarTaskRepository).save(task);
        assertEquals(savedLog, task.getExerciseLog());
        assertTrue(task.getCompleted());
    }

    @Test
    void saveLog_ShouldNotSaveCalendarTask_WhenTaskNotFound() {
        ExerciseLogForm form = new ExerciseLogForm();
        form.setDate(LocalDate.now());
        form.setMoodBefore(1);
        form.setMoodAfter(2);
        form.setConfidence(3);
        form.setComments(null);
        form.setDurationMinutes(20);
        form.setCalendarTaskId(99L);
        User user = new User();
        user.setId(1L);
        when(calendarTaskRepository.findOwnedForLogUpdate(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> exerciseLogService.saveLog(form, user));
        verify(exerciseLogRepository, never()).save(any());
        verify(calendarTaskRepository, never()).save(any());
    }

    @Test
    void getAllLogs_ShouldReturnList() {
        ExerciseLog el1 = new ExerciseLog();
        ExerciseLog el2 = new ExerciseLog();
        when(exerciseLogRepository.findAll()).thenReturn(Arrays.asList(el1, el2));
        List<ExerciseLog> result = exerciseLogService.getAllLogs();
        assertEquals(2, result.size());
        assertTrue(result.contains(el1));
        assertTrue(result.contains(el2));
    }

    @Test
    void getLogById_ShouldReturnLog_WhenExists() {
        ExerciseLog log = new ExerciseLog();
        when(exerciseLogRepository.findById(1L)).thenReturn(Optional.of(log));
        ExerciseLog result = exerciseLogService.getLogById(1L);
        assertEquals(log, result);
    }

    @Test
    void getLogById_ShouldReturnNull_WhenNotExists() {
        when(exerciseLogRepository.findById(1L)).thenReturn(Optional.empty());
        ExerciseLog result = exerciseLogService.getLogById(1L);
        assertNull(result);
    }

    @Test
    void getLogsForUser_ShouldReturnList() {
        User user = new User();
        List<ExerciseLog> list = Arrays.asList(new ExerciseLog(), new ExerciseLog());
        when(exerciseLogRepository.findByUser(user)).thenReturn(list);
        List<ExerciseLog> result = exerciseLogService.getLogsForUser(user);
        assertEquals(list, result);
    }

    @Test
    void getLogsByUser_ShouldReturnList() {
        User user = new User();
        List<ExerciseLog> list = Arrays.asList(new ExerciseLog());
        when(exerciseLogRepository.findByUserOrderByDateDesc(user)).thenReturn(list);
        List<ExerciseLog> result = exerciseLogService.getLogsByUser(user);
        assertEquals(list, result);
    }
}
