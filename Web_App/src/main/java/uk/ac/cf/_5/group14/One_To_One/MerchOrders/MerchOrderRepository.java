package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MerchOrderRepository extends JpaRepository<MerchOrder, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM MerchOrder o WHERE o.id = :id")
    Optional<MerchOrder> findLockedById(@Param("id") Long id);

    List<MerchOrder> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    String HISTORY_FILTER = " WHERE o.user.id = :userId "
            + "AND (:shippingStatus IS NULL OR o.shippingStatus = :shippingStatus) "
            + "AND (CAST(o.id AS string) LIKE :query ESCAPE '!' OR EXISTS "
            + "(SELECT i.id FROM MerchOrderItem i WHERE i.order = o AND LOWER(i.productNameSnapshot) LIKE :query ESCAPE '!'))";

    @Query("SELECT COUNT(o) FROM MerchOrder o" + HISTORY_FILTER)
    long countHistory(@Param("userId") Long userId, @Param("query") String query,
                      @Param("shippingStatus") ShippingStatus shippingStatus);

    @Query("SELECT o.id FROM MerchOrder o" + HISTORY_FILTER + " ORDER BY o.createdAt DESC, o.id DESC")
    List<Long> findHistoryIds(@Param("userId") Long userId, @Param("query") String query,
                             @Param("shippingStatus") ShippingStatus shippingStatus,
                             org.springframework.data.domain.Pageable pageable);

    // Fetch collections only after paging IDs, so a collection join never disables the SQL bound.
    @Query("SELECT DISTINCT o FROM MerchOrder o LEFT JOIN FETCH o.items i LEFT JOIN FETCH i.product "
            + "WHERE o.user.id = :userId AND o.id IN :ids")
    List<MerchOrder> findHistoryDocuments(@Param("userId") Long userId, @Param("ids") List<Long> ids);

    Optional<MerchOrder> findByIdAndUserId(Long id, Long userId);

    Optional<MerchOrder> findByUserIdAndCheckoutKey(Long userId, String checkoutKey);

    List<MerchOrder> findByPaymentStatusAndCreatedAtBefore(PaymentStatus paymentStatus, Instant createdBefore);

    /**
     * Finds PENDING orders that contain the given product.
     * CONFIRMED/SHIPPED/DELIVERED orders are intentionally excluded – those must
     * be handled by explicit admin action, not an automatic cancellation.
     */
    @Query("SELECT DISTINCT o FROM MerchOrder o JOIN o.items i " +
           "WHERE i.product.id = :productId " +
           "AND o.status = uk.ac.cf._5.group14.One_To_One.MerchOrders.OrderStatus.PENDING")
    List<MerchOrder> findPendingOrdersContainingProduct(@Param("productId") Long productId);
}
