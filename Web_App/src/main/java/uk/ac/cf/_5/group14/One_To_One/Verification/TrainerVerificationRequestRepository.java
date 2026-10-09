package uk.ac.cf._5.group14.One_To_One.Verification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrainerVerificationRequestRepository extends JpaRepository<TrainerVerificationRequest, Long> {
    String LATEST_GYM_REVIEW = """
        not exists (select newer.id from TrainerVerificationRequest newer where newer.gymId = r.gymId and newer.trainerUserId = r.trainerUserId
          and (newer.submittedAt > r.submittedAt or (newer.submittedAt = r.submittedAt and newer.id > r.id)))
        """;

    @org.springframework.data.jpa.repository.Query("select r from TrainerVerificationRequest r, User u where r.gymId = :gymId and r.trainerUserId = u.id and r.trainerUserId in :trainerIds and "
        + LATEST_GYM_REVIEW + " and " + uk.ac.cf._5.group14.One_To_One.Users.UserRepository.CURRENT_GYM_TRAINER)
    List<TrainerVerificationRequest> findLatestForGymRoster(@org.springframework.data.repository.query.Param("gymId") Long gymId,
        @org.springframework.data.repository.query.Param("trainerIds") java.util.Collection<Long> trainerIds);

    @org.springframework.data.jpa.repository.Query("select count(r) from TrainerVerificationRequest r, User u where r.gymId = :gymId and r.trainerUserId = u.id and r.status = :status and "
        + LATEST_GYM_REVIEW + " and " + uk.ac.cf._5.group14.One_To_One.Users.UserRepository.CURRENT_GYM_TRAINER)
    long countLatestForCurrentGymRoster(@org.springframework.data.repository.query.Param("gymId") Long gymId,
        @org.springframework.data.repository.query.Param("status") VerificationStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select request from TrainerVerificationRequest request where request.id = :id")
    Optional<TrainerVerificationRequest> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    
    List<TrainerVerificationRequest> findByStatusOrderBySubmittedAtAsc(VerificationStatus status);
    
    List<TrainerVerificationRequest> findByGymIdOrderBySubmittedAtDesc(Long gymId);
    List<TrainerVerificationRequest> findByTrainerUserIdOrderBySubmittedAtDescIdDesc(Long trainerUserId);
    
    Optional<TrainerVerificationRequest> findByTrainerUserIdAndStatus(Long trainerUserId, VerificationStatus status);
    boolean existsByTrainerUserIdAndStatusIn(Long trainerUserId, java.util.Collection<VerificationStatus> statuses);
    
    Optional<TrainerVerificationRequest> findTopByTrainerUserIdOrderBySubmittedAtDesc(Long trainerUserId);

    Optional<TrainerVerificationRequest> findByIdAndGymId(Long id, Long gymId);

    long countByStatus(VerificationStatus status);
}
