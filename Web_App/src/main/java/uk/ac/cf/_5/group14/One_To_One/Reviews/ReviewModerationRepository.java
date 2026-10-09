package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewModerationRepository extends JpaRepository<ReviewModeration, Long> {
    @org.springframework.data.jpa.repository.Query("select m.reviewId from ReviewModeration m where m.id = :id")
    java.util.Optional<Long> findReviewIdById(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Find all pending moderation requests.
     */
    List<ReviewModeration> findByResolvedOrderByReportedAtDesc(boolean resolved);

    /**
     * Find all moderation records for a specific review.
     */
    List<ReviewModeration> findByReviewIdOrderByReportedAtDesc(Long reviewId);

    /**
     * Check if a user has already reported this review.
     */
    boolean existsByReviewIdAndReportedByUserId(Long reviewId, Long reportedByUserId);
}
