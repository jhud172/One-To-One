package uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Workout.Workout;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    Optional<WorkoutSession> findByIdAndUserId(Long id, Long userId);
    Optional<WorkoutSession> findByUserAndDateAndWorkout(User user, LocalDate date, Workout workout);
    Optional<WorkoutSession> findByUserAndDateAndScheduleId(User user, LocalDate date, Long scheduleId);
    Optional<WorkoutSession> findByUserAndDateAndSourceOccurrenceId(User user, LocalDate date, Long sourceOccurrenceId);
    List<WorkoutSession> findByUserAndDate(User user, LocalDate date);

    boolean existsByUserAndSourceOccurrenceIdIn(User user, List<Long> sourceOccurrenceIds);

    List<WorkoutSession> findTop3ByUserOrderByDateDesc(User user);

    List<WorkoutSession> findTop20ByUserOrderByDateDesc(User user);

    List<WorkoutSession> findByUserAndCompletedTrueOrderByDateDescIdDesc(User user, Pageable pageable);

    List<WorkoutSession> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate from, LocalDate to);
    List<WorkoutSession> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate from, LocalDate to);

    List<WorkoutSession> findByUserAndDateBetweenAndCompletedTrue(User user, LocalDate from, LocalDate to);

    @Query("select distinct ws.user from WorkoutSession ws where ws.date = :date and ws.completed = true")
    List<User> findDistinctUsersWithCompletedWorkouts(@Param("date") LocalDate date);
}
