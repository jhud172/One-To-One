package uk.ac.cf._5.group14.One_To_One.Merch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MerchProductRepository extends JpaRepository<MerchProduct, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MerchProduct p where p.id = :id")
    java.util.Optional<MerchProduct> findLockedById(@Param("id") Long id);

    List<MerchProduct> findByActiveTrueOrderByCreatedAtDesc();

    List<MerchProduct> findAllByOrderByCreatedAtDesc();

    long countByActiveTrue();

    @Query("SELECT DISTINCT p.category FROM MerchProduct p WHERE p.active = true AND p.category IS NOT NULL ORDER BY p.category")
    List<String> findActiveCategories();

    String FILTER = "FROM MerchProduct p WHERE p.active = true " +
            "AND (:term IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!' " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :term, '%')) ESCAPE '!') " +
            "AND (:category IS NULL OR p.category = :category) AND (:inStock = false OR p.stockQuantity > 0) ";

    @Query("SELECT COUNT(p) " + FILTER)
    long countCatalogue(@Param("term") String term, @Param("category") String category, @Param("inStock") boolean inStock);

    @Query("SELECT p " + FILTER + "ORDER BY p.createdAt DESC, p.id DESC")
    List<MerchProduct> findCatalogue(@Param("term") String term, @Param("category") String category,
                                   @Param("inStock") boolean inStock, org.springframework.data.domain.Pageable page);

    /**
     * Atomically decrements stock by {@code qty} only if sufficient stock exists.
     * Returns the number of rows updated (1 = success, 0 = insufficient stock).
     */
    @Modifying
    @Query("UPDATE MerchProduct p SET p.stockQuantity = p.stockQuantity - :qty " +
           "WHERE p.id = :id AND p.active = true AND :qty > 0 AND p.stockQuantity >= :qty")
    int decrementStock(@Param("id") Long id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE MerchProduct p SET p.stockQuantity = p.stockQuantity + :qty WHERE p.id = :id")
    int incrementStock(@Param("id") Long id, @Param("qty") int qty);
}
