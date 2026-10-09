package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OffPlatformPaymentAttemptRepository extends JpaRepository<OffPlatformPaymentAttempt, Long> {
	List<OffPlatformPaymentAttempt> findAllByOrderByCreatedAtDesc();
    @org.springframework.data.jpa.repository.Query("select attempt from OffPlatformPaymentAttempt attempt where :keyword = '' or lower(coalesce(attempt.matchedKeyword, '')) like lower(concat('%', :keyword, '%'))")
    org.springframework.data.domain.Page<OffPlatformPaymentAttempt> findByMatchedKeywordContainingIgnoreCase(@org.springframework.data.repository.query.Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);
}
