package uk.ac.cf._5.group14.One_To_One.Verification;

import org.springframework.data.repository.Repository;
import java.util.List;

/** Deliberately exposes no update/delete or unrestricted history lookup. */
public interface VerificationEventRepository extends Repository<VerificationEvent, Long> {
    VerificationEvent save(VerificationEvent event);
    boolean existsByRequestId(Long requestId);
    List<VerificationEvent> findByRequestIdInOrderByCreatedAtAscIdAsc(List<Long> requestIds);
}
