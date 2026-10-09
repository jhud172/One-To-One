package uk.ac.cf._5.group14.One_To_One.TrainerClient;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TrainerClientLinkRepository extends JpaRepository<TrainerClientLink, Long> {
    Optional<TrainerClientLink> findFirstByTrainerUserIdAndClientUserIdAndStatusAndActivatedAtIsNotNullOrderByUpdatedAtDescIdDesc(
            Long trainerUserId, Long clientUserId, TrainerClientLinkStatus status);

    Optional<TrainerClientLink> findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(Long clientUserId, TrainerClientLinkStatus status);

    List<TrainerClientLink> findByTrainerUserIdAndStatusOrderByCreatedAtAsc(Long trainerUserId, TrainerClientLinkStatus status);

    List<TrainerClientLink> findByClientUserIdAndStatusOrderByUpdatedAtDesc(Long clientUserId, TrainerClientLinkStatus status);

    boolean existsByClientUserIdAndStatus(Long clientUserId, TrainerClientLinkStatus status);

    boolean existsByTrainerUserIdAndClientUserIdAndStatus(Long trainerUserId, Long clientUserId, TrainerClientLinkStatus status);

    List<TrainerClientLink> findByTrainerUserIdAndStatusOrderByUpdatedAtDesc(Long trainerUserId, TrainerClientLinkStatus status);

    Optional<TrainerClientLink> findFirstByTrainerUserIdAndClientUserIdAndStatusOrderByUpdatedAtDesc(Long trainerUserId,
                                                                                                   Long clientUserId,
                                                                                                   TrainerClientLinkStatus status);

    Optional<TrainerClientLink> findFirstByTrainerUserIdAndClientUserIdAndStatusInOrderByUpdatedAtDesc(Long trainerUserId,
                                                                                                       Long clientUserId,
                                                                                                       List<TrainerClientLinkStatus> statuses);

    @Query("select l from TrainerClientLink l where l.trainerUserId = :trainerId order by l.updatedAt desc")
    List<TrainerClientLink> findByTrainerIdOrderByUpdatedAtDesc(@Param("trainerId") Long trainerId);

    @Query("select l from TrainerClientLink l where l.clientUserId = :clientId order by l.updatedAt desc")
    List<TrainerClientLink> findByClientIdOrderByUpdatedAtDesc(@Param("clientId") Long clientId);

    default Optional<TrainerClientLink> findActiveByClientId(Long clientId) {
        return findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(clientId, TrainerClientLinkStatus.ACTIVE);
    }

    default List<TrainerClientLink> findPendingByTrainerId(Long trainerId) {
        return findByTrainerUserIdAndStatusOrderByCreatedAtAsc(trainerId, TrainerClientLinkStatus.REQUESTED);
    }
}
