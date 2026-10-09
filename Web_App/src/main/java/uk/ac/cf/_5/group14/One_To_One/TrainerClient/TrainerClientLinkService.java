package uk.ac.cf._5.group14.One_To_One.TrainerClient;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationType;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.List;

@Service
public class TrainerClientLinkService {

    public static final String ERROR_CLIENT_ALREADY_HAS_ACTIVE_TRAINER = "CLIENT_ALREADY_HAS_ACTIVE_TRAINER";
    public static final String ERROR_TRAINER_NOT_VERIFIED = "TRAINER_NOT_VERIFIED";

    private final TrainerClientLinkRepository trainerClientLinkRepository;
    private final UserRepository userRepository;
    private final MessagingService messagingService;
    private final NotificationService notificationService;
    private final CoachingPhaseChangeRepository coachingPhaseChangeRepository;

    public TrainerClientLinkService(TrainerClientLinkRepository trainerClientLinkRepository,
                                   UserRepository userRepository,
                                   MessagingService messagingService,
                                   NotificationService notificationService,
                                   CoachingPhaseChangeRepository coachingPhaseChangeRepository) {
        this.trainerClientLinkRepository = trainerClientLinkRepository;
        this.userRepository = userRepository;
        this.messagingService = messagingService;
        this.notificationService = notificationService;
        this.coachingPhaseChangeRepository = coachingPhaseChangeRepository;
    }

    public TrainerClientLink getActiveLinkForClient(Long clientUserId) {
        return trainerClientLinkRepository
                .findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(clientUserId, TrainerClientLinkStatus.ACTIVE)
                .orElse(null);
    }

    public boolean clientHasActiveTrainer(Long clientUserId) {
        return trainerClientLinkRepository.existsByClientUserIdAndStatus(clientUserId, TrainerClientLinkStatus.ACTIVE);
    }

    public List<TrainerClientLink> getPendingRequestsForTrainer(Long trainerUserId) {
        return trainerClientLinkRepository.findPendingByTrainerId(trainerUserId);
    }

    public List<TrainerClientLink> getActiveClientsForTrainer(Long trainerUserId) {
        return trainerClientLinkRepository.findByTrainerUserIdAndStatusOrderByUpdatedAtDesc(trainerUserId, TrainerClientLinkStatus.ACTIVE);
    }

    public TrainerClientLink getActiveLinkForTrainerClient(Long trainerUserId, Long clientUserId) {
        return trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId,
                        clientUserId,
                        TrainerClientLinkStatus.ACTIVE)
                .orElse(null);
    }

    @Transactional
    public TrainerClientLink requestLink(Long clientUserId, Long trainerUserId) {
        // Lock the client row to enforce the one-active-trainer rule transactionally.
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        requireEnabledClient(client);
        if (clientHasActiveTrainer(clientUserId)) {
            throw new TrainerClientLinkException(
                    TrainerClientLinkException.Reason.CLIENT_ALREADY_HAS_ACTIVE_TRAINER,
                    ERROR_CLIENT_ALREADY_HAS_ACTIVE_TRAINER
            );
        }

        User trainer = userRepository.findById(trainerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new IllegalArgumentException("User is not a trainer");
        }
        requireVerifiedTrainer(trainer);
        TrainerClientLink existingRequest = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId, clientUserId, TrainerClientLinkStatus.REQUESTED)
                .orElse(null);
        if (existingRequest != null) return existingRequest;

        TrainerClientLink link = new TrainerClientLink(clientUserId, trainerUserId, TrainerClientLinkStatus.REQUESTED);
        link.setRequestedAt(Instant.now());
        link = trainerClientLinkRepository.save(link);
        messagingService.ensureThreadForLink(link);

        notificationService.createIfNotRecentlySent(trainer, NotificationType.SYSTEM, "New Client Request", client.getFullName() + " has requested you as their trainer.", 60);

        return link;
    }

    public TrainerClientLink requestTrainer(Long clientUserId, Long trainerUserId) {
        return requestLink(clientUserId, trainerUserId);
    }

    @Transactional
    public void acceptRequest(Long trainerUserId, Long clientUserId) {
        User trainer = userRepository.findById(trainerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("User is not a trainer");
        }
        requireVerifiedTrainer(trainer);

        // Read pending state only after acquiring the client lock, including withdrawals.
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        requireEnabledClient(client);

        TrainerClientLink link = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId,
                        clientUserId,
                        TrainerClientLinkStatus.REQUESTED)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        if (!trainerUserId.equals(link.getTrainerUserId())) {
            throw new AccessDeniedException("Cannot accept another trainer's request");
        }
        if (link.getStatus() != TrainerClientLinkStatus.REQUESTED) {
            throw new IllegalStateException("Only requested links can be accepted");
        }

        List<TrainerClientLink> activeLinks = trainerClientLinkRepository
                .findByClientUserIdAndStatusOrderByUpdatedAtDesc(link.getClientUserId(), TrainerClientLinkStatus.ACTIVE);

        if (!activeLinks.isEmpty()) {
            throw new TrainerClientLinkException(TrainerClientLinkException.Reason.CLIENT_ALREADY_HAS_ACTIVE_TRAINER,
                    ERROR_CLIENT_ALREADY_HAS_ACTIVE_TRAINER);
        }
        Instant now = Instant.now();

        link.setStatus(TrainerClientLinkStatus.ACTIVE);
        link.setActivatedAt(now);
        link.setPausedAt(null);
        link = trainerClientLinkRepository.save(link);
        messagingService.ensureThreadForLink(link);

        notificationService.create(client, NotificationType.SYSTEM, "Request Accepted", trainer.getFullName() + " accepted your request!");
    }

    @Transactional
    public void declineRequest(Long trainerUserId, Long clientUserId) {
        User trainer = userRepository.findById(trainerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) throw new AccessDeniedException("Trainer access required");
        requireVerifiedTrainer(trainer);
        userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        TrainerClientLink request = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId, clientUserId, TrainerClientLinkStatus.REQUESTED)
                .orElseThrow(() -> new IllegalArgumentException("Pending request not found"));
        request.setStatus(TrainerClientLinkStatus.ENDED);
        request.setEndedAt(Instant.now());
        request = trainerClientLinkRepository.save(request);
        messagingService.ensureThreadForLink(request);
    }

    @Transactional
    public void withdrawRequest(Long clientUserId, Long trainerUserId) {
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        if (client.getRole() != Role.CLIENT || !client.isEnabled()) throw new AccessDeniedException("Client access required");
        TrainerClientLink request = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId, clientUserId, TrainerClientLinkStatus.REQUESTED)
                .orElseThrow(() -> new IllegalArgumentException("Pending request not found"));
        request.setStatus(TrainerClientLinkStatus.ENDED);
        request.setEndedAt(Instant.now());
        trainerClientLinkRepository.save(request);
        messagingService.ensureThreadForLink(request);
    }

    @Transactional
    public void pauseLink(Long trainerUserId, Long clientUserId) {
        User trainer = userRepository.findById(trainerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("User is not a trainer");
        }
        requireVerifiedTrainer(trainer);

        userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        TrainerClientLink link = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId,
                        clientUserId,
                        TrainerClientLinkStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active link not found"));

        if (!trainerUserId.equals(link.getTrainerUserId())) {
            throw new AccessDeniedException("Cannot pause another trainer's link");
        }

        link.setStatus(TrainerClientLinkStatus.PAUSED);
        link.setPausedAt(Instant.now());
        link = trainerClientLinkRepository.save(link);
        messagingService.ensureThreadForLink(link);
    }

    @Transactional
    public void resumeLink(Long trainerUserId, Long clientUserId) {
        User trainer = userRepository.findById(trainerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) throw new AccessDeniedException("User is not a trainer");
        requireVerifiedTrainer(trainer);
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        requireEnabledClient(client);
        TrainerClientLink link = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId, clientUserId, TrainerClientLinkStatus.PAUSED)
                .orElseThrow(() -> new IllegalArgumentException("Paused link not found"));
        if (!trainerUserId.equals(link.getTrainerUserId())) throw new AccessDeniedException("Cannot resume another trainer's link");
        if (trainerClientLinkRepository.existsByClientUserIdAndStatus(clientUserId, TrainerClientLinkStatus.ACTIVE)) {
            throw new TrainerClientLinkException(TrainerClientLinkException.Reason.CLIENT_ALREADY_HAS_ACTIVE_TRAINER,
                    "Client already has an active trainer");
        }
        link.setStatus(TrainerClientLinkStatus.ACTIVE);
        link.setPausedAt(null);
        link = trainerClientLinkRepository.save(link);
        messagingService.ensureThreadForLink(link);
    }

    @Transactional
    public void endLink(Long trainerUserId, Long clientUserId) {
        User trainer = userRepository.findById(trainerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("User is not a trainer");
        }
        requireVerifiedTrainer(trainer);
        userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        TrainerClientLink link = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusInOrderByUpdatedAtDesc(
                        trainerUserId,
                        clientUserId,
                        List.of(TrainerClientLinkStatus.ACTIVE, TrainerClientLinkStatus.PAUSED))
                .orElseThrow(() -> new IllegalArgumentException("Active or paused link not found"));

        if (!trainerUserId.equals(link.getTrainerUserId())) {
            throw new AccessDeniedException("Cannot end another trainer's link");
        }

        link.setStatus(TrainerClientLinkStatus.ENDED);
        link.setEndedAt(Instant.now());
        link = trainerClientLinkRepository.save(link);
        messagingService.ensureThreadForLink(link);

        userRepository.findById(clientUserId).ifPresent(client -> {
            notificationService.create(client, NotificationType.SYSTEM, "Coaching Ended", trainer.getFullName() + " has ended the coaching relationship.");
        });
    }

    public List<TrainerClientLink> listTrainerClients(Long trainerUserId) {
        return trainerClientLinkRepository.findByTrainerIdOrderByUpdatedAtDesc(trainerUserId);
    }

    public List<TrainerClientLink> listClientTrainerLinks(Long clientUserId) {
        return trainerClientLinkRepository.findByClientIdOrderByUpdatedAtDesc(clientUserId);
    }

    @Transactional
    public TrainerClientLink changeCoachingPhase(Long trainerUserId,
                                                 Long clientUserId,
                                                 CoachingPhase newPhase,
                                                 String customLabel,
                                                 String notes) {
        User trainer = userRepository.findById(trainerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("User is not a trainer");
        }
        requireVerifiedTrainer(trainer);

        String label = trimToNull(customLabel);
        String auditNotes = trimToNull(notes);
        if (newPhase == null || (label != null && label.length() > 120)
                || (auditNotes != null && auditNotes.length() > 800)) {
            throw new IllegalArgumentException("Choose a phase; labels allow 120 characters and notes 800");
        }
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        requireEnabledClient(client);

        TrainerClientLink link = trainerClientLinkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(
                        trainerUserId,
                        clientUserId,
                        TrainerClientLinkStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Active link not found"));

        CoachingPhase oldPhase = link.getCoachingPhase();
        CoachingPhaseChange change = new CoachingPhaseChange();
        change.setLinkId(link.getId());
        change.setTrainerId(trainerUserId);
        change.setOldPhase(oldPhase);
        change.setOldLabel(link.getCoachingPhaseLabel());
        change.setNewPhase(newPhase);
        change.setNewLabel(label);
        change.setNotes(auditNotes);
        coachingPhaseChangeRepository.save(change);

        link.setCoachingPhase(newPhase);
        link.setCoachingPhaseLabel(label);
        if (link.getCoachingPhaseStartedAt() == null || oldPhase != newPhase) {
            link.setCoachingPhaseStartedAt(Instant.now());
        }
        link.setCoachingPhaseUpdatedAt(Instant.now());
        return trainerClientLinkRepository.save(link);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void requireEnabledClient(User client) {
        if (client.getRole() != Role.CLIENT || !client.isEnabled()) {
            throw new AccessDeniedException("Only an enabled client can start or resume coaching");
        }
    }

    private void requireVerifiedTrainer(User trainer) {
        if (trainer == null || !trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new TrainerClientLinkException(
                    TrainerClientLinkException.Reason.TRAINER_NOT_VERIFIED,
                    ERROR_TRAINER_NOT_VERIFIED
            );
        }
    }
}
