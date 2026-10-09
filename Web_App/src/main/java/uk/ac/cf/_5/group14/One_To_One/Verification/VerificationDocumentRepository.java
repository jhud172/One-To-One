package uk.ac.cf._5.group14.One_To_One.Verification;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VerificationDocumentRepository extends Repository<VerificationDocument, Long> {
    interface Summary {
        Long getId();
        Long getRequestId();
        String getFileName();
        String getContentType();
        long getByteSize();
        Instant getCreatedAt();
    }
    VerificationDocument save(VerificationDocument document);
    void deleteById(Long id);
    long countByRequestId(Long requestId);
    List<Summary> findByRequestIdInOrderByCreatedAtAscIdAsc(List<Long> requestIds);
    Optional<Summary> findProjectedById(Long id);
    @Query("select d.content from VerificationDocument d where d.id = :id")
    Optional<byte[]> findContentById(Long id);
}
