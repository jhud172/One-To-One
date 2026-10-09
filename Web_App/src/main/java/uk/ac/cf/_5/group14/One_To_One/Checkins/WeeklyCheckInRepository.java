package uk.ac.cf._5.group14.One_To_One.Checkins;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeeklyCheckInRepository extends JpaRepository<WeeklyCheckIn, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select checkIn from WeeklyCheckIn checkIn where checkIn.id = :id")
    Optional<WeeklyCheckIn> findByIdForUpdate(@Param("id") Long id);

    List<WeeklyCheckIn> findTop5ByTrainerIdAndClientIdOrderByWeekStartDateDescIdDesc(Long trainerId, Long clientId);

    List<WeeklyCheckIn> findTop5ByTrainerIdAndStatusAndClientIdInOrderBySubmittedAtAscIdAsc(
            Long trainerId, WeeklyCheckInStatus status, List<Long> clientIds);

    long countByTrainerIdAndStatusAndClientIdIn(Long trainerId, WeeklyCheckInStatus status, List<Long> clientIds);

    long countByTrainerIdAndStatusAndClientIdInAndSubmittedAtLessThanEqual(
            Long trainerId, WeeklyCheckInStatus status, List<Long> clientIds, Instant submittedAt);

    List<WeeklyCheckIn> findByTrainerIdOrderBySubmittedAtDesc(Long trainerId);

    List<WeeklyCheckIn> findByClientIdOrderBySubmittedAtDesc(Long clientId);
    List<WeeklyCheckIn> findTop5ByClientIdOrderByWeekStartDateDescIdDesc(Long clientId);

    Optional<WeeklyCheckIn> findByTrainerIdAndClientIdAndWeekStartDate(Long trainerId, Long clientId, LocalDate weekStartDate);
}
