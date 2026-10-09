package uk.ac.cf._5.group14.One_To_One.Verification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Membership.EmailService;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrainerVerificationService {
    
    private final TrainerVerificationRequestRepository verificationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final jakarta.persistence.EntityManager entities;
    private final VerificationEventRepository events;
    
    /**
     * Create a verification request for a trainer (called by gym admin)
     */
    @Transactional
    public TrainerVerificationRequest createVerificationRequest(
        Long trainerUserId,
        Long gymId,
        String notes
    ) {
        return createVerificationRequest(trainerUserId, gymId, notes, gymId == null ? trainerUserId : null);
    }

    @Transactional
    public TrainerVerificationRequest createVerificationRequest(Long trainerUserId, Long gymId, String notes, Long actorUserId) {
        if (actorUserId != null && gymId != null) validateGymActor(actorUserId, gymId);
        if (notes != null && notes.length() > 1000) throw new IllegalArgumentException("Notes must contain no more than 1000 characters");
        // Serialise request creation on the account, including when no request exists yet.
        User trainer = userRepository.findByIdForUpdate(trainerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        entities.refresh(trainer);
        if (!trainer.isEnabled()) throw new org.springframework.security.access.AccessDeniedException("Enabled trainer account required");
        if (trainer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER) throw new IllegalArgumentException("Only trainer accounts can request verification");
        if (trainer.isTrainerVerified()) throw new IllegalStateException("This trainer is already verified");
        if (gymId != null && !userRepository.isTrainerAffiliatedWithGym(trainerUserId, gymId)) throw new IllegalArgumentException("Trainer does not belong to the requesting gym");
        if (verificationRepository.existsByTrainerUserIdAndStatusIn(trainerUserId,
                List.of(VerificationStatus.PENDING, VerificationStatus.NEEDS_INFO))) {
            throw new IllegalStateException("An open verification request already exists for this trainer");
        }
        
        TrainerVerificationRequest request = new TrainerVerificationRequest();
        request.setTrainerUserId(trainerUserId);
        request.setGymId(gymId);
        request.setNotes(notes == null ? null : notes.trim());
        request.setStatus(VerificationStatus.PENDING);
        request.setSubmittedAt(Instant.now());
        
        request = verificationRepository.save(request);
        recordEvent(request, actorUserId, VerificationEvent.Action.SUBMITTED, null);
        
        log.info("Created verification request {} for trainer {}", request.getId(), trainerUserId);
        
        return request;
    }
    
    /**
     * Get all pending verification requests (for super admin)
     */
    public List<TrainerVerificationRequest> getPendingRequests() {
        return verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING);
    }

    public List<TrainerVerificationRequest> getQueueRequests() {
        List<TrainerVerificationRequest> pending = new java.util.ArrayList<>(verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING));
        List<TrainerVerificationRequest> needsInfo = verificationRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.NEEDS_INFO);
        pending.addAll(needsInfo);
        pending.sort(java.util.Comparator.comparing(TrainerVerificationRequest::getSubmittedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return pending;
    }

    public long countByStatus(VerificationStatus status) {
        return verificationRepository.countByStatus(status);
    }
    
    /**
     * Get verification requests for a specific gym
     */
    public List<TrainerVerificationRequest> getRequestsByGym(Long gymId) {
        return verificationRepository.findByGymIdOrderBySubmittedAtDesc(gymId);
    }
    
    /**
     * Get the latest verification request for a trainer
     */
    public TrainerVerificationRequest getLatestRequestForTrainer(Long trainerUserId) {
        return verificationRepository.findTopByTrainerUserIdOrderBySubmittedAtDesc(trainerUserId)
            .orElse(null);
    }

    public TrainerVerificationRequest getRequestById(Long requestId) {
        return verificationRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
    }

    public TrainerVerificationRequest getRequestForGym(Long requestId, Long gymId) {
        TrainerVerificationRequest request = verificationRepository.findByIdAndGymId(requestId, gymId)
            .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        if (!userRepository.isTrainerAffiliatedWithGym(request.getTrainerUserId(), gymId)) {
            throw new IllegalArgumentException("Verification request not found");
        }
        return request;
    }
    
    /**
     * Approve a trainer (called by super admin)
     */
    @Transactional
    public TrainerVerificationRequest approveTrainer(
        Long requestId,
        Long reviewerUserId,
        String adminNotes
    ) {
        validateReviewer(reviewerUserId);
        validateAdminNotes(adminNotes, false);
        // Responses/affiliation changes lock the trainer first. Approval must use
        // the same order before updating that account to avoid a request/account deadlock.
        TrainerVerificationRequest identifiedRequest = getRequestById(requestId);
        User trainer = userRepository.findByIdForUpdate(identifiedRequest.getTrainerUserId())
            .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        entities.refresh(trainer);
        TrainerVerificationRequest request = lockedRequest(requestId);
        if (request.getStatus() == VerificationStatus.APPROVED && java.util.Objects.equals(request.getAdminNotes(), adminNotes == null ? null : adminNotes.trim())) return request;
        
        if (request.getStatus() != VerificationStatus.PENDING && 
            request.getStatus() != VerificationStatus.NEEDS_INFO) {
            throw new IllegalStateException("Can only approve pending or needs-info requests");
        }
        
        if (trainer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER) throw new IllegalArgumentException("Only trainer accounts can be verified");

        preserveLegacySnapshot(request);
        VerificationStatus previousStatus = request.getStatus();
        if (!trainer.isEnabled()) throw new org.springframework.security.access.AccessDeniedException("Enabled trainer account required");
        request.setStatus(VerificationStatus.APPROVED);
        request.setReviewedAt(Instant.now());
        request.setReviewedByUserId(reviewerUserId);
        request.setAdminNotes(adminNotes == null ? null : adminNotes.trim());
        
        request = verificationRepository.save(request);
        recordEvent(request, reviewerUserId, VerificationEvent.Action.APPROVED, previousStatus);
        
        // Update the trainer's verified status

        trainer.setTrainerVerified(true);
        userRepository.save(trainer);
        
        // Send email notification
        try {
            emailService.sendTrainerVerificationUpdate(trainer, "APPROVED", adminNotes);
        } catch (Exception e) {
            log.error("Failed to send verification email to trainer {}", trainer.getId(), e);
        }
        
        log.info("Approved verification request {} for trainer {}", requestId, trainer.getId());
        
        return request;
    }
    
    /**
     * Reject a trainer verification request (called by super admin)
     */
    @Transactional
    public TrainerVerificationRequest rejectTrainer(
        Long requestId,
        Long reviewerUserId,
        String adminNotes
    ) {
        validateReviewer(reviewerUserId);
        validateAdminNotes(adminNotes, true);
        TrainerVerificationRequest request = lockedRequest(requestId);
        if (request.getStatus() == VerificationStatus.REJECTED && java.util.Objects.equals(request.getAdminNotes(), adminNotes == null ? null : adminNotes.trim())) return request;
        
        if (request.getStatus() != VerificationStatus.PENDING && 
            request.getStatus() != VerificationStatus.NEEDS_INFO) {
            throw new IllegalStateException("Can only reject pending or needs-info requests");
        }
        
        preserveLegacySnapshot(request);
        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request.setReviewedByUserId(reviewerUserId);
        request.setAdminNotes(adminNotes == null ? null : adminNotes.trim());
        
        request = verificationRepository.save(request);
        recordEvent(request, reviewerUserId, VerificationEvent.Action.REJECTED, previousStatus);
        
        // Send email notification
        User trainer = userRepository.findById(request.getTrainerUserId())
            .orElse(null);
        
        if (trainer != null) {
            try {
                emailService.sendTrainerVerificationUpdate(trainer, "REJECTED", adminNotes);
            } catch (Exception e) {
                log.error("Failed to send verification email to trainer {}", trainer.getId(), e);
            }
        }
        
        log.info("Rejected verification request {} for trainer {}", requestId, request.getTrainerUserId());
        
        return request;
    }
    
    /**
     * Request more information from the trainer (called by super admin)
     */
    @Transactional
    public TrainerVerificationRequest requestMoreInfo(
        Long requestId,
        Long reviewerUserId,
        String adminNotes
    ) {
        if (adminNotes == null || adminNotes.isBlank()) {
            throw new IllegalArgumentException("Admin notes are required when requesting more info");
        }
        
        validateReviewer(reviewerUserId);
        validateAdminNotes(adminNotes, true);
        TrainerVerificationRequest request = lockedRequest(requestId);
        if (request.getStatus() == VerificationStatus.NEEDS_INFO && java.util.Objects.equals(request.getAdminNotes(), adminNotes == null ? null : adminNotes.trim())) return request;
        
        if (request.getStatus() != VerificationStatus.PENDING) {
            throw new IllegalStateException("Can only request info for pending requests");
        }
        
        preserveLegacySnapshot(request);
        VerificationStatus previousStatus = request.getStatus();
        request.setStatus(VerificationStatus.NEEDS_INFO);
        request.setReviewedAt(Instant.now());
        request.setReviewedByUserId(reviewerUserId);
        request.setAdminNotes(adminNotes == null ? null : adminNotes.trim());
        
        request = verificationRepository.save(request);
        recordEvent(request, reviewerUserId, VerificationEvent.Action.NEEDS_INFO, previousStatus);
        
        // Send email notification
        User trainer = userRepository.findById(request.getTrainerUserId())
            .orElse(null);
        
        if (trainer != null) {
            try {
                emailService.sendTrainerVerificationUpdate(trainer, "NEEDS_INFO", adminNotes);
            } catch (Exception e) {
                log.error("Failed to send verification email to trainer {}", trainer.getId(), e);
            }
        }
        
        log.info("Requested more info for verification request {}", requestId);
        
        return request;
    }
    
    /**
     * Update trainer notes in response to needs-info request
     */
    @Transactional
    public TrainerVerificationRequest updateTrainerNotes(Long requestId, String notes) {
        return updateTrainerNotes(requestId, notes, null);
    }

    private TrainerVerificationRequest updateTrainerNotes(Long requestId, String notes, Long actorUserId) {
        TrainerVerificationRequest request = lockedRequest(requestId);
        
        if (request.getStatus() != VerificationStatus.NEEDS_INFO) {
            throw new IllegalStateException("Can only update notes for needs-info requests");
        }
        
        if (notes == null || notes.isBlank() || notes.length() > 1000) {
            throw new IllegalArgumentException("Notes must contain between 1 and 1000 characters");
        }
        preserveLegacySnapshot(request);
        VerificationStatus previousStatus = request.getStatus();
        request.setNotes(notes.trim());
        request.setStatus(VerificationStatus.PENDING);
        request.setReviewedAt(null);
        request.setReviewedByUserId(null);
        
        request = verificationRepository.save(request);
        recordEvent(request, actorUserId, VerificationEvent.Action.RESPONDED, previousStatus);
        return request;
    }

    @Transactional
    public TrainerVerificationRequest updateTrainerNotesForGym(Long requestId, Long gymId, String notes) {
        return updateTrainerNotesForGym(requestId, gymId, notes, null);
    }

    @Transactional
    public TrainerVerificationRequest updateTrainerNotesForGym(Long requestId, Long gymId, String notes, Long actorUserId) {
        if (actorUserId != null) validateGymActor(actorUserId, gymId);
        TrainerVerificationRequest request = getRequestForGym(requestId, gymId);
        User trainer = userRepository.findByIdForUpdate(request.getTrainerUserId())
            .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        entities.refresh(trainer);
        if (!trainer.isEnabled()) throw new org.springframework.security.access.AccessDeniedException("Enabled trainer account required");
        // Serialise this edit with affiliation changes so an ended gym cannot keep editing private evidence.
        if (!userRepository.isTrainerAffiliatedWithGym(trainer.getId(), gymId)) {
            throw new IllegalArgumentException("Verification request not found");
        }
        return updateTrainerNotes(request.getId(), notes, actorUserId);
    }

    @Transactional(readOnly = true)
    public List<TrainerVerificationRequest> getRequestsForTrainer(Long trainerUserId) {
        return verificationRepository.findByTrainerUserIdOrderBySubmittedAtDescIdDesc(trainerUserId);
    }

    @Transactional
    public TrainerVerificationRequest updateTrainerNotesForTrainer(Long requestId, Long trainerUserId, String notes) {
        User trainer = userRepository.findByIdForUpdate(trainerUserId)
            .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        entities.refresh(trainer);
        if (!trainer.isEnabled() || trainer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.TRAINER) {
            throw new org.springframework.security.access.AccessDeniedException("Enabled trainer account required");
        }
        TrainerVerificationRequest request = lockedRequest(requestId);
        if (!java.util.Objects.equals(request.getTrainerUserId(), trainerUserId)) {
            throw new IllegalArgumentException("Verification request not found");
        }
        return updateTrainerNotes(requestId, notes, trainerUserId);
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, List<VerificationEvent>> getHistoryForRequests(List<TrainerVerificationRequest> requests) {
        if (requests.isEmpty()) return java.util.Map.of();
        return events.findByRequestIdInOrderByCreatedAtAscIdAsc(requests.stream().map(TrainerVerificationRequest::getId).toList())
            .stream().collect(java.util.stream.Collectors.groupingBy(VerificationEvent::getRequestId));
    }

    // Called only while the owning request/account is locked. A legacy snapshot
    // records what still exists, without inventing earlier transitions or actors.
    void preserveLegacySnapshot(TrainerVerificationRequest request) {
        if (!events.existsByRequestId(request.getId())) {
            recordEvent(request, null, VerificationEvent.Action.LEGACY_SNAPSHOT, null);
        }
    }

    void recordEvent(TrainerVerificationRequest request, Long actorUserId, VerificationEvent.Action action, VerificationStatus previousStatus) {
        events.save(new VerificationEvent(request, actorUserId, action, previousStatus, null));
    }

    private void validateGymActor(Long actorId, Long gymId) {
        User actor = userRepository.findById(actorId).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Gym administrator required"));
        if (!actor.isEnabled() || actor.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.GYM_ADMIN
            || !java.util.Objects.equals(actor.getGymId(), gymId)) {
            throw new org.springframework.security.access.AccessDeniedException("Gym administrator required");
        }
    }

    private TrainerVerificationRequest lockedRequest(Long id) {
        TrainerVerificationRequest request = verificationRepository.findLockedById(id)
            .orElseThrow(() -> new IllegalArgumentException("Verification request not found"));
        entities.refresh(request);
        return request;
    }

    private void validateReviewer(Long id) {
        User reviewer = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Reviewer not found"));
        if (!reviewer.isEnabled()) throw new org.springframework.security.access.AccessDeniedException("Enabled reviewer required");
        if (reviewer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.PLATFORM_ADMIN
            && reviewer.getRole() != uk.ac.cf._5.group14.One_To_One.Users.Role.SUPER_ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Platform reviewer required");
        }
    }

    private void validateAdminNotes(String notes, boolean required) {
        if ((required && (notes == null || notes.isBlank())) || (notes != null && notes.length() > 1000)) {
            throw new IllegalArgumentException("Review notes must contain " + (required ? "1" : "0") + "–1000 characters");
        }
    }

    /**
     * Check if a trainer is verified
     */
    public boolean isTrainerVerified(Long trainerId) {
        return userRepository.findById(trainerId)
            .map(User::isTrainerVerified)
            .orElse(false);
    }
}
