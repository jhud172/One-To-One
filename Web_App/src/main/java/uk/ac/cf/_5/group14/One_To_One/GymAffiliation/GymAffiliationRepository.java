package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface GymAffiliationRepository extends JpaRepository<TrainerGymAffiliation, Long> {
    Optional<TrainerGymAffiliation> findByTrainerUserIdAndGymId(Long trainerId, Long gymId);
    List<TrainerGymAffiliation> findByTrainerUserIdOrderByUpdatedAtDesc(Long trainerId);
    List<TrainerGymAffiliation> findByGymIdOrderByUpdatedAtDesc(Long gymId);
}
