package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Arrays;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.LockModeType;

@Service
public class TrainerReviewService {

    public static final String ERROR_REVIEW_ALREADY_EXISTS = "REVIEW_ALREADY_EXISTS_FOR_LINK";
    public static final String ERROR_INVALID_LINK = "INVALID_TRAINER_CLIENT_LINK";
    public static final String ERROR_LINK_NOT_ELIGIBLE = "LINK_NOT_ELIGIBLE_FOR_REVIEW";
    public static final String ERROR_NOT_CLIENT = "USER_IS_NOT_CLIENT";

    private final TrainerReviewRepository reviewRepository;
    private final TrainerClientLinkRepository linkRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public TrainerReviewService(TrainerReviewRepository reviewRepository,
                               TrainerClientLinkRepository linkRepository,
                               UserRepository userRepository) {
        this.reviewRepository = reviewRepository;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a new review. Only clients with ACTIVE or ENDED link can review.
     * One review per client per trainer per link period.
     */
    @Transactional
    public TrainerReview createReview(Long clientUserId, Long trainerId, Integer stars, String tags, String comment) {
        List<String> invalid = new ArrayList<>();
        if (stars == null || stars < 1 || stars > 5) invalid.add("stars");
        if (comment != null && comment.length() > 10000) invalid.add("comment");
        List<String> selectedTags = tags == null || tags.isBlank() ? List.of()
                : Arrays.stream(tags.split(",", -1)).map(String::trim).distinct().toList();
        if ((tags != null && tags.length() > 500) || selectedTags.stream().anyMatch(tag -> !TrainerReviewTag.isSupported(tag))) invalid.add("tags");
        if (!invalid.isEmpty()) throw new ReviewValidationException(invalid);
        // Verify client role
        // Relationship changes use this same client lock. Serialise eligibility and duplicate checks.
        User client = userRepository.findByIdForUpdate(clientUserId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        entityManager.refresh(client, LockModeType.PESSIMISTIC_WRITE);
        if (client.getRole() != Role.CLIENT) {
            throw new TrainerReviewException(TrainerReviewException.Reason.USER_NOT_CLIENT, ERROR_NOT_CLIENT);
        }
        if (!client.isEnabled()) throw new AccessDeniedException("Client is disabled");

        // Verify trainer exists
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new IllegalArgumentException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER || !trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new IllegalArgumentException("User is not a trainer");
        }

        // Find active or most recent ended link
        TrainerClientLink link = findEligibleLink(clientUserId, trainerId);
        if (link == null) {
            throw new TrainerReviewException(TrainerReviewException.Reason.LINK_NOT_ELIGIBLE, ERROR_LINK_NOT_ELIGIBLE);
        }
        entityManager.refresh(link, LockModeType.PESSIMISTIC_WRITE);
        if (!clientUserId.equals(link.getClientUserId()) || !trainerId.equals(link.getTrainerUserId())
                || (link.getStatus() != TrainerClientLinkStatus.ACTIVE
                    && !(link.getStatus() == TrainerClientLinkStatus.ENDED && link.getActivatedAt() != null))) {
            throw new TrainerReviewException(TrainerReviewException.Reason.LINK_NOT_ELIGIBLE, ERROR_LINK_NOT_ELIGIBLE);
        }

        // Check if review already exists for this link
        if (reviewRepository.existsByTrainerIdAndClientIdAndLinkId(trainerId, clientUserId, link.getId())) {
            throw new TrainerReviewException(TrainerReviewException.Reason.REVIEW_ALREADY_EXISTS, ERROR_REVIEW_ALREADY_EXISTS);
        }

        // Create review
        TrainerReview review = new TrainerReview(trainerId, clientUserId, link.getId(), stars,
                selectedTags.isEmpty() ? null : String.join(",", selectedTags), comment);
        return reviewRepository.save(review);
    }

    /**
     * Find eligible link for review (ACTIVE or ENDED).
     * Returns the most recent eligible link.
     */
    private TrainerClientLink findEligibleLink(Long clientUserId, Long trainerId) {
        // First check for active link
        Optional<TrainerClientLink> activeLink = linkRepository
                .findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(clientUserId, TrainerClientLinkStatus.ACTIVE)
                .filter(link -> link.getTrainerUserId().equals(trainerId));

        if (activeLink.isPresent()) {
            return activeLink.get();
        }

        // If no active link, find most recent ended link with this trainer
        return linkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusAndActivatedAtIsNotNullOrderByUpdatedAtDescIdDesc(
                        trainerId, clientUserId, TrainerClientLinkStatus.ENDED)
                .orElse(null);
    }

    /**
     * Check if client can leave a review for this trainer.
     */
    public boolean canClientReviewTrainer(Long clientUserId, Long trainerId) {
        User client = userRepository.findById(clientUserId).orElse(null);
        User trainer = userRepository.findById(trainerId).orElse(null);
        if (client == null || client.getRole() != Role.CLIENT || !client.isEnabled()
                || trainer == null || trainer.getRole() != Role.TRAINER || !trainer.isEnabled() || !trainer.isTrainerVerified()) return false;
        TrainerClientLink link = findEligibleLink(clientUserId, trainerId);
        if (link == null) {
            return false;
        }
        return !reviewRepository.existsByTrainerIdAndClientIdAndLinkId(trainerId, clientUserId, link.getId());
    }

    /**
     * Get all visible reviews for a trainer.
     */
    public List<TrainerReview> getVisibleReviewsForTrainer(Long trainerId) {
        return reviewRepository.findByTrainerIdAndStatusOrderByCreatedAtDesc(trainerId, ReviewStatus.VISIBLE);
    }

    /**
     * Get average rating for a trainer.
     */
    public Double getAverageRating(Long trainerId) {
        Double avg = reviewRepository.calculateAverageRating(trainerId);
        return avg != null ? avg : 0.0;
    }

    /**
     * Get review count for a trainer.
     */
    public long getReviewCount(Long trainerId) {
        return reviewRepository.countByTrainerIdAndStatus(trainerId, ReviewStatus.VISIBLE);
    }

    /**
     * Report a review for moderation.
     */
    @Transactional
    public void reportReview(Long reviewId, Long reportedByUserId, String reason) {
        TrainerReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));

        // Update review status to REPORTED
        if (review.getStatus() == ReviewStatus.VISIBLE) {
            review.setStatus(ReviewStatus.REPORTED);
            reviewRepository.save(review);
        }
    }

    /**
     * Hide a review (admin only).
     */
    @Transactional
    public void hideReview(Long reviewId, Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (admin.getRole() != Role.PLATFORM_ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Only admins can hide reviews");
        }

        TrainerReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        
        review.setStatus(ReviewStatus.HIDDEN);
        reviewRepository.save(review);
    }

    /**
     * Make a review visible again (admin only).
     */
    @Transactional
    public void makeReviewVisible(Long reviewId, Long adminUserId) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (admin.getRole() != Role.PLATFORM_ADMIN && admin.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException("Only admins can change review visibility");
        }

        TrainerReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        
        review.setStatus(ReviewStatus.VISIBLE);
        reviewRepository.save(review);
    }

    /**
     * Get all reported reviews (admin only).
     */
    public List<TrainerReview> getReportedReviews() {
        return reviewRepository.findByStatusOrderByCreatedAtDesc(ReviewStatus.REPORTED);
    }
}
