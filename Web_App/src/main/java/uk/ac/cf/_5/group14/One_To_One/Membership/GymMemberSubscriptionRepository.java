package uk.ac.cf._5.group14.One_To_One.Membership;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GymMemberSubscriptionRepository extends JpaRepository<GymMemberSubscription, Long> {
    
    List<GymMemberSubscription> findByProductIdAndStatus(Long productId, SubscriptionStatus status);
    
    List<GymMemberSubscription> findByUserIdAndGymId(Long userId, Long gymId);
    
    Optional<GymMemberSubscription> findByUserIdAndGymIdAndStatus(Long userId, Long gymId, SubscriptionStatus status);
    
    long countByProductIdAndStatus(Long productId, SubscriptionStatus status);
    
    long countByGymIdAndStatus(Long gymId, SubscriptionStatus status);

    interface ProductSubscriberCount {
        Long getProductId();
        long getSubscribers();
    }

    @org.springframework.data.jpa.repository.Query("select s.productId as productId, count(s) as subscribers from GymMemberSubscription s where s.gymId = :gymId and s.status = :status and s.productId in :productIds group by s.productId")
    List<ProductSubscriberCount> countForProducts(@org.springframework.data.repository.query.Param("gymId") Long gymId, @org.springframework.data.repository.query.Param("status") SubscriptionStatus status, @org.springframework.data.repository.query.Param("productIds") List<Long> productIds);

    List<GymMemberSubscription> findByGymIdAndStatus(Long gymId, SubscriptionStatus status);
}
