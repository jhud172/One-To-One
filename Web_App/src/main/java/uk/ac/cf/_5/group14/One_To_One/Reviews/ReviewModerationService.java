package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.List;

@Service
public class ReviewModerationService {
    private final ReviewModerationRepository moderationRepository;
    private final TrainerReviewRepository reviewRepository;
    private final UserRepository userRepository;

    public ReviewModerationService(ReviewModerationRepository moderationRepository,
                                   TrainerReviewRepository reviewRepository,
                                   UserRepository userRepository) {
        this.moderationRepository = moderationRepository;
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ReviewModeration reportReview(Long reviewId, Long reportedByUserId, String reason) {
        User reporter = userRepository.findById(reportedByUserId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated account required"));
        if (!reporter.isEnabled()) throw new AccessDeniedException("Enabled account required");
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new IllegalArgumentException("A report reason of at most 1000 characters is required");
        }
        TrainerReview review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        if (review.getStatus() == ReviewStatus.HIDDEN) throw new IllegalStateException("Review is already hidden");
        if (moderationRepository.existsByReviewIdAndReportedByUserId(reviewId, reportedByUserId)) {
            throw new IllegalStateException("You have already reported this review");
        }
        ReviewModeration moderation = moderationRepository.save(new ReviewModeration(reviewId, reportedByUserId, reason.trim()));
        if (review.getStatus() == ReviewStatus.VISIBLE) {
            review.setStatus(ReviewStatus.REPORTED);
            reviewRepository.save(review);
        }
        return moderation;
    }

    public List<ReviewModeration> getPendingModerations(Long adminUserId) {
        verifyAdmin(adminUserId);
        return moderationRepository.findByResolvedOrderByReportedAtDesc(false);
    }

    public List<ReviewModeration> getResolvedModerations(Long adminUserId) {
        verifyAdmin(adminUserId);
        return moderationRepository.findByResolvedOrderByReportedAtDesc(true);
    }

    public List<TrainerReview> getReviewsForModeration(List<Long> reviewIds, Long adminUserId) {
        verifyAdmin(adminUserId);
        return reviewRepository.findAllById(reviewIds);
    }

    @Transactional
    public void resolveModerationAndHideReview(Long moderationId, Long adminUserId, String resolutionNotes) {
        resolveModeration(moderationId, adminUserId, resolutionNotes, ReviewStatus.HIDDEN);
    }

    @Transactional
    public void resolveModerationKeepVisible(Long moderationId, Long adminUserId, String resolutionNotes) {
        resolveModeration(moderationId, adminUserId, resolutionNotes, ReviewStatus.VISIBLE);
    }

    private void resolveModeration(Long moderationId, Long adminUserId, String notes, ReviewStatus status) {
        verifyAdmin(adminUserId);
        if (notes == null || notes.isBlank() || notes.length() > 10000) {
            throw new IllegalArgumentException("Resolution notes must contain 1 to 10000 characters");
        }
        Long reviewId = moderationRepository.findReviewIdById(moderationId)
                .orElseThrow(() -> new IllegalArgumentException("Moderation record not found"));
        // Serialise decisions per review before loading the report's current resolved state.
        TrainerReview review = reviewRepository.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        ReviewModeration moderation = moderationRepository.findById(moderationId).orElseThrow();
        if (moderation.isResolved()) throw new IllegalStateException("This report has already been resolved");
        review.setStatus(status);
        reviewRepository.save(review);
        Instant resolvedAt = Instant.now();
        // One decision resolves every pending report for this review, preventing stale queue actions reversing it.
        for (ReviewModeration pending : moderationRepository.findByReviewIdOrderByReportedAtDesc(reviewId)) {
            if (pending.isResolved()) continue;
            pending.setResolved(true);
            pending.setResolvedAt(resolvedAt);
            pending.setResolvedByUserId(adminUserId);
            pending.setResolutionNotes(notes.trim());
            moderationRepository.save(pending);
        }
    }

    public List<ReviewModeration> getModerationsForReview(Long reviewId, Long adminUserId) {
        verifyAdmin(adminUserId);
        return moderationRepository.findByReviewIdOrderByReportedAtDesc(reviewId);
    }

    private void verifyAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("Admin access required"));
        if (!user.isEnabled() || (user.getRole() != Role.PLATFORM_ADMIN && user.getRole() != Role.SUPER_ADMIN)) {
            throw new AccessDeniedException("Enabled admin access required");
        }
    }
}
