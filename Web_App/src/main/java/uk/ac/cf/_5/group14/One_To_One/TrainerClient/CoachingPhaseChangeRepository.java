package uk.ac.cf._5.group14.One_To_One.TrainerClient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoachingPhaseChangeRepository extends JpaRepository<CoachingPhaseChange, Long> {

    List<CoachingPhaseChange> findTop5ByLinkIdOrderByChangedAtDescIdDesc(Long linkId);

    List<CoachingPhaseChange> findByLinkIdOrderByChangedAtDesc(Long linkId);
}
