package uk.ac.cf._5.group14.One_To_One.GymAffiliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GymAffiliationEventRepository extends JpaRepository<GymAffiliationEvent, Long> {
    List<GymAffiliationEvent> findByAffiliationIdOrderByCreatedAtDescIdDesc(Long affiliationId);
}
