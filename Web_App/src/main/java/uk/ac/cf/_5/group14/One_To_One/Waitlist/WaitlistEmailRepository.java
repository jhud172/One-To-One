package uk.ac.cf._5.group14.One_To_One.Waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaitlistEmailRepository extends JpaRepository<WaitlistEmail, Long> {
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
}
