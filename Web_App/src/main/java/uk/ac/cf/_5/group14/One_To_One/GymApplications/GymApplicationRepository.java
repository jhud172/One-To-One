package uk.ac.cf._5.group14.One_To_One.GymApplications;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface GymApplicationRepository extends JpaRepository<GymApplication, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from GymApplication application where application.id = :id")
    Optional<GymApplication> findLockedById(@Param("id") Long id);
    Optional<GymApplication> findByAccessToken(String accessToken);
    List<GymApplication> findAllByOrderBySubmittedAtDesc();
    List<GymApplication> findByStatusOrderBySubmittedAtAsc(GymApplicationStatus status);
    long countByStatusIn(List<GymApplicationStatus> statuses);
}
