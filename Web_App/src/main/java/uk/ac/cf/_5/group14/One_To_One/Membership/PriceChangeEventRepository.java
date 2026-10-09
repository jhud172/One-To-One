package uk.ac.cf._5.group14.One_To_One.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PriceChangeEventRepository extends JpaRepository<PriceChangeEvent, Long> {
    
    java.util.Optional<PriceChangeEvent> findFirstByProductIdAndNewPriceCentsAndEffectiveAtAndReasonOrderByCreatedAtDesc(
        Long productId, Integer newPriceCents, Instant effectiveAt, String reason);

    List<PriceChangeEvent> findByProductIdOrderByCreatedAtDesc(Long productId);

    Page<PriceChangeEvent> findByProductIdOrderByCreatedAtDesc(Long productId, Pageable pageable);

    long countByProductId(Long productId);
    List<PriceChangeEvent> findByProductIdOrderByCreatedAtDescIdDesc(Long productId);
    Page<PriceChangeEvent> findByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);
    PriceChangeEvent findFirstByProductIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(Long productId, Instant effectiveAt);
    
    List<PriceChangeEvent> findByGymIdOrderByCreatedAtDesc(Long gymId);

    PriceChangeEvent findFirstByProductIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDesc(Long productId, Instant effectiveAt);
}
