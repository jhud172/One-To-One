package uk.ac.cf._5.group14.One_To_One.Vault;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface VaultNoteRepository extends JpaRepository<VaultNote, Long> {

    List<VaultNote> findByUserIdOrderByPinnedDescUpdatedAtDesc(Long userId);

    List<VaultNote> findByUserIdAndNoteTypeOrderByPinnedDescUpdatedAtDesc(Long userId, VaultNoteType noteType);

    List<VaultNote> findByUserIdAndPinnedTrueOrderByUpdatedAtDesc(Long userId);

    Optional<VaultNote> findByIdAndUserId(Long id, Long userId);

    List<VaultNote> findByIdInAndUserId(List<Long> ids, Long userId);

    boolean existsByUserIdAndLinkedDateAndTrainerTemplateEntryId(Long userId, LocalDate linkedDate, Long trainerTemplateEntryId);

    long countByUserId(Long userId);

    long countByUserIdAndPinnedTrue(Long userId);

    @Query("SELECT n.tags FROM VaultNote n WHERE n.userId = :userId AND n.tags <> ''")
    List<String> findTagsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(n) FROM VaultNote n WHERE n.userId = :userId AND n.createdAt >= :from")
    long countByUserIdAndCreatedAtAfter(@Param("userId") Long userId, @Param("from") java.time.Instant from);

    String FILTER = "FROM VaultNote n WHERE n.userId = :userId " +
           "AND (:search IS NULL OR LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '!' OR LOWER(n.content) LIKE LOWER(CONCAT('%', :search, '%')) ESCAPE '!') " +
           "AND (:noteType IS NULL OR n.noteType = :noteType) " +
           "AND (:pinnedOnly = false OR n.pinned = true) " +
           "AND (:fromDate IS NULL OR n.linkedDate >= :fromDate) " +
           "AND (:toDate IS NULL OR n.linkedDate <= :toDate) ";
    String ORDER = "ORDER BY n.pinned DESC, n.updatedAt DESC, n.id DESC";

    @Query("SELECT n " + FILTER + ORDER)
    List<VaultNote> search(@Param("userId") Long userId,
                           @Param("search") String search,
                           @Param("noteType") VaultNoteType noteType,
                           @Param("pinnedOnly") boolean pinnedOnly,
                           @Param("fromDate") LocalDate fromDate,
                           @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(n) " + FILTER)
    long countSearch(@Param("userId") Long userId, @Param("search") String search,
                     @Param("noteType") VaultNoteType noteType, @Param("pinnedOnly") boolean pinnedOnly,
                     @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT n " + FILTER + ORDER)
    List<VaultNote> searchPage(@Param("userId") Long userId, @Param("search") String search,
                              @Param("noteType") VaultNoteType noteType, @Param("pinnedOnly") boolean pinnedOnly,
                              @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate,
                              Pageable pageable);

    // Legacy methods for backward compatibility
    default List<VaultNote> findByUserIdOrderByUpdatedAtDesc(Long userId) {
        return findByUserIdOrderByPinnedDescUpdatedAtDesc(userId);
    }

    default List<VaultNote> findByUserIdAndNoteTypeOrderByUpdatedAtDesc(Long userId, VaultNoteType noteType) {
        return findByUserIdAndNoteTypeOrderByPinnedDescUpdatedAtDesc(userId, noteType);
    }
}
