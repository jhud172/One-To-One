package uk.ac.cf._5.group14.One_To_One.Checkins;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import uk.ac.cf._5.group14.One_To_One.Goals.Goal;
import uk.ac.cf._5.group14.One_To_One.Goals.GoalService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationType;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkService;
import uk.ac.cf._5.group14.One_To_One.TrainerTemplates.TrainerScheduleTemplateRepository;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyCheckInResponseTest {
    @Mock WeeklyCheckInRepository checkIns;
    @Mock TrainerCheckInQuestionRepository questions;
    @Mock TrainerScheduleTemplateRepository templates;
    @Mock TrainerClientLinkService links;
    @Mock AccessGuard access;
    @Mock NotificationService notifications;
    @Mock GoalService goals;
    @Mock VaultNoteRepository notes;
    @Mock UserRepository users;
    @Mock ObjectMapper mapper;
    @InjectMocks WeeklyCheckInServiceImpl service;

    private User trainer;
    private WeeklyCheckIn checkIn;

    @BeforeEach
    void prepareOwnedCheckIn() {
        trainer = new User();
        trainer.setId(1L);
        trainer.setRole(Role.TRAINER);
        trainer.setTrainerVerified(true);
        trainer.setEnabled(true);
        checkIn = new WeeklyCheckIn();
        checkIn.setTrainerId(1L);
        checkIn.setClientId(2L);
        when(checkIns.findByIdForUpdate(11L)).thenReturn(Optional.of(checkIn));
    }

    @Test
    void futureWeekIsRejectedBeforeSavingClientCheckIn() {
        reset(checkIns);
        var client = new User();
        client.setId(2L);
        client.setRole(Role.CLIENT);
        var link = new uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink(
                2L, 1L, uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus.ACTIVE);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(client));
        when(links.getActiveLinkForClient(2L)).thenReturn(link);
        when(users.findById(1L)).thenReturn(Optional.of(trainer));
        assertThrows(IllegalArgumentException.class, () -> service.submitCheckIn(client, null,
                java.util.Map.of(), null, java.time.LocalDate.now().plusWeeks(1)));
        verifyNoInteractions(checkIns, notifications, notes);
    }

    @Test
    void revokedTrainerCannotRespondEvenWithAnExistingRelationship() {
        reset(checkIns);
        trainer.setTrainerVerified(false);
        assertThrows(AccessDeniedException.class,
                () -> service.respondToCheckIn(trainer, 11L, "Useful response", null, null));
        verifyNoInteractions(checkIns, access, notifications);
    }

    @Test
    void anotherClientsVisibleGoalCannotBeAttached() {
        var otherClient = new User();
        otherClient.setId(3L);
        var visibleGoal = new Goal();
        visibleGoal.setOwnerUser(otherClient);
        when(goals.getGoalForViewer(trainer, 42L)).thenReturn(visibleGoal);

        assertThrows(AccessDeniedException.class,
                () -> service.respondToCheckIn(trainer, 11L, "Keep going", null, 42L));
        assertThat(checkIn.getGoalId()).isNull();
        assertThat(checkIn.getStatus()).isEqualTo(WeeklyCheckInStatus.SUBMITTED);
        verify(checkIns, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void emptyOrOverlongResponseDoesNotMarkAnsweredOrNotify() {
        assertThrows(IllegalArgumentException.class,
                () -> service.respondToCheckIn(trainer, 11L, "  ", " ", null));
        assertThrows(IllegalArgumentException.class,
                () -> service.respondToCheckIn(trainer, 11L, "Useful response", "x".repeat(601), null));
        assertThat(checkIn.getStatus()).isEqualTo(WeeklyCheckInStatus.SUBMITTED);
        assertThat(checkIn.getRespondedAt()).isNull();
        verify(checkIns, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void blankGoalSelectionClearsAttachmentAndUnchangedReplayDoesNotNotifyAgain() {
        checkIn.setGoalId(42L);
        var client = new User();
        client.setId(2L);
        when(users.findById(2L)).thenReturn(Optional.of(client));
        when(checkIns.save(checkIn)).thenReturn(checkIn);

        service.respondToCheckIn(trainer, 11L, "  Keep going  ", null, null);
        var respondedAt = checkIn.getRespondedAt();
        service.respondToCheckIn(trainer, 11L, "Keep going", null, null);

        assertThat(checkIn.getGoalId()).isNull();
        assertThat(checkIn.getTrainerResponse()).isEqualTo("Keep going");
        assertThat(checkIn.getRespondedAt()).isEqualTo(respondedAt);
        verify(checkIns).save(checkIn);
        verify(notifications).create(eq(client), eq(NotificationType.SYSTEM), anyString(), anyString(), eq("/checkins/client-review/" + checkIn.getId()));
        verifyNoMoreInteractions(notifications);
    }
}
