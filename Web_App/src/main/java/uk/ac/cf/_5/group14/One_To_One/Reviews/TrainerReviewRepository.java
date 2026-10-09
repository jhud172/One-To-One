package uk.ac.cf._5.group14.One_To_One.Reviews;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TrainerReviewRepository extends JpaRepository<TrainerReview, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TrainerReview r where r.id = :id")
    Optional<TrainerReview> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Find all visible reviews for a trainer.
     */
    List<TrainerReview> findByTrainerIdAndStatusOrderByCreatedAtDesc(Long trainerId, ReviewStatus status);

    /**
     * Check if a review already exists for this link.
     */
    boolean existsByTrainerIdAndClientIdAndLinkId(Long trainerId, Long clientId, Long linkId);

    /**
     * Find a specific review by IDs.
     */
    Optional<TrainerReview> findByTrainerIdAndClientIdAndLinkId(Long trainerId, Long clientId, Long linkId);

    /**
     * Calculate average rating for a trainer (only VISIBLE reviews).
     */
    @Query("SELECT AVG(r.stars) FROM TrainerReview r WHERE r.trainerId = :trainerId AND r.status = 'VISIBLE'")
    Double calculateAverageRating(Long trainerId);

    /**
     * Count visible reviews for a trainer.
     */
    long countByTrainerIdAndStatus(Long trainerId, ReviewStatus status);

    /**
     * Find all reviews (for moderation).
     */
    List<TrainerReview> findByStatusOrderByCreatedAtDesc(ReviewStatus status);
}
