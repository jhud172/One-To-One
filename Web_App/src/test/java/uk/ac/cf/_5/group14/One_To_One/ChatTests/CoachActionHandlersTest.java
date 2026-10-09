package uk.ac.cf._5.group14.One_To_One.ChatTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskService;
import uk.ac.cf._5.group14.One_To_One.Chat.ApplyScheduleActionHandler;
import uk.ac.cf._5.group14.One_To_One.Chat.ApplyScheduleActionPayload;
import uk.ac.cf._5.group14.One_To_One.Chat.CreateTaskActionHandler;
import uk.ac.cf._5.group14.One_To_One.Chat.CreateTaskActionPayload;
import uk.ac.cf._5.group14.One_To_One.Chat.CoachActionExecution;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleApplicationService;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachActionHandlersTest {

    @Mock
    private CalendarTaskService calendarTaskService;

    @InjectMocks
    private CreateTaskActionHandler createTaskActionHandler;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleApplicationService scheduleApplicationService;

    @Mock
    private TrainerClientLinkRepository trainerClientLinkRepository;

    @InjectMocks
    private ApplyScheduleActionHandler applyScheduleActionHandler;

    @Test
    void createTaskValidationRejectsMissingTitle() {
        User user = new User();
        user.setId(5L);
        CreateTaskActionPayload payload = new CreateTaskActionPayload(LocalDate.now(), null, " ", null, null);

        List<String> errors = createTaskActionHandler.validate(payload, user);

        assertFalse(errors.isEmpty());
        verifyNoInteractions(calendarTaskService);
    }

    @Test
    void applyScheduleRejectsUnauthorizedTrainerSchedule() {
        User user = new User();
        user.setId(1L);
        User trainer = new User();
        trainer.setId(2L);

        Schedule trainerSchedule = new Schedule();
        trainerSchedule.setId(10L);
        trainerSchedule.setName("Pro Plan");
        trainerSchedule.setUser(trainer);

        when(scheduleRepository.findByUserAndNameIgnoreCase(eq(user), anyString())).thenReturn(Optional.empty());
        when(trainerClientLinkRepository.findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(1L, TrainerClientLinkStatus.ACTIVE))
                .thenReturn(Optional.empty());

        ApplyScheduleActionPayload payload = new ApplyScheduleActionPayload("Pro Plan", LocalDate.now(), 4);
        CoachActionExecution execution = applyScheduleActionHandler.execute(payload, user);

        assertFalse(execution.success());
        verifyNoInteractions(scheduleApplicationService);
    }

    @Test
    void directExecutionRejectsInvalidDetailsBeforeReadingOrSaving() {
        assertFalse(applyScheduleActionHandler.execute(null, null).success());
        User user = new User(); user.setId(1L);
        assertFalse(applyScheduleActionHandler.execute(new ApplyScheduleActionPayload(" ", null, 13), user).success());
        verifyNoInteractions(scheduleRepository, trainerClientLinkRepository, scheduleApplicationService);
    }

    @Test
    void emptyPlanReturnsUsefulFailureInsteadOfClaimingApplication() {
        User user = new User(); user.setId(1L);
        Schedule plan = new Schedule(); plan.setId(10L); plan.setUser(user); plan.setName("Empty plan");
        when(scheduleRepository.findByUserAndNameIgnoreCase(user, "Empty plan")).thenReturn(Optional.of(plan));
        when(scheduleApplicationService.apply(plan, user, "2027-01-04", "4"))
                .thenThrow(new IllegalArgumentException("empty"));
        var result = applyScheduleActionHandler.execute(new ApplyScheduleActionPayload("Empty plan", LocalDate.of(2027,1,4), 4), user);
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("no movements"));
    }
}
