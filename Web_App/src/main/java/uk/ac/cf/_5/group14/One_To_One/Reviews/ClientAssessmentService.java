package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing private client assessments (trainer-only).
 */
@Service
public class ClientAssessmentService {

    private final ClientAssessmentRepository assessmentRepository;
    private final TrainerClientLinkRepository linkRepository;
    private final UserRepository userRepository;

    public ClientAssessmentService(ClientAssessmentRepository assessmentRepository,
                                  TrainerClientLinkRepository linkRepository,
                                  UserRepository userRepository) {
        this.assessmentRepository = assessmentRepository;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create or update a client assessment.
     * Only trainers who have/had a link with the client can assess them.
     */
    @Transactional
    public ClientAssessment saveAssessment(Long trainerId, Long clientId, 
                                          Integer reliabilityScore, Integer communicationScore, 
                                          String privateNotes) {
        requireAssessmentAccess(trainerId, clientId);
        validateScore(reliabilityScore);
        validateScore(communicationScore);
        if (privateNotes != null && privateNotes.length() > 10000) {
            throw new IllegalArgumentException("Assessment notes must not exceed 10000 characters");
        }

        // Find or create assessment
        ClientAssessment assessment = assessmentRepository.findByTrainerIdAndClientId(trainerId, clientId)
                .orElse(new ClientAssessment(trainerId, clientId));

        assessment.setReliabilityScore(reliabilityScore);
        assessment.setCommunicationScore(communicationScore);
        assessment.setPrivateNotes(privateNotes);

        return assessmentRepository.save(assessment);
    }

    /**
     * Get assessment for a specific client (trainer-only).
     */
    public Optional<ClientAssessment> getAssessment(Long trainerId, Long clientId) {
        requireAssessmentAccess(trainerId, clientId);
        return assessmentRepository.findByTrainerIdAndClientId(trainerId, clientId);
    }

    /**
     * Get all assessments by a trainer.
     */
    public List<ClientAssessment> getAllAssessmentsByTrainer(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        return assessmentRepository.findByTrainerIdOrderByUpdatedAtDesc(trainerId);
    }

    /**
     * Delete an assessment.
     */
    @Transactional
    public void deleteAssessment(Long trainerId, Long clientId) {
        requireAssessmentAccess(trainerId, clientId);
        ClientAssessment assessment = assessmentRepository.findByTrainerIdAndClientId(trainerId, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found"));

        assessmentRepository.delete(assessment);
    }

    private void requireVerifiedTrainer(Long trainerId) {
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new AccessDeniedException("Trainer access required"));
        if (trainer.getRole() != Role.TRAINER || !trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new AccessDeniedException("Verified trainer access required");
        }
    }

    private void requireAssessmentAccess(Long trainerId, Long clientId) {
        requireVerifiedTrainer(trainerId);
        boolean coached = linkRepository.findByClientIdOrderByUpdatedAtDesc(clientId).stream()
                .anyMatch(link -> trainerId.equals(link.getTrainerUserId()) &&
                        (link.getStatus() == TrainerClientLinkStatus.ACTIVE ||
                         (link.getActivatedAt() != null &&
                          (link.getStatus() == TrainerClientLinkStatus.PAUSED || link.getStatus() == TrainerClientLinkStatus.ENDED))));
        // Withdrawn or rejected requests are ENDED too, but never became coaching relationships.
        if (!coached) throw new AccessDeniedException("A current or previous coaching relationship is required");
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new AccessDeniedException("Client access denied"));
        if (client.getRole() != Role.CLIENT) throw new AccessDeniedException("Client access denied");
    }

    private void validateScore(Integer score) {
        if (score != null && (score < 1 || score > 5)) {
            throw new IllegalArgumentException("Assessment scores must be between 1 and 5");
        }
    }
}
