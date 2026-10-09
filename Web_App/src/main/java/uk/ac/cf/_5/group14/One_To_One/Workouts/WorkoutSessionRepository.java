package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository("workoutPlayerSessionRepository")
public interface WorkoutSessionRepository extends JpaRepository<WorkoutSession, Long> {
    boolean existsByTemplate(WorkoutTemplate template);
    List<WorkoutSession> findByUserOrderByStartedAtDesc(User user);
    Optional<WorkoutSession> findByIdAndUser(Long id, User user);
    Optional<WorkoutSession> findFirstByUserAndTemplateAndCompletedFalseOrderByStartedAtDescIdDesc(User user, WorkoutTemplate template);
    List<WorkoutSession> findByUserAndTemplateAndStartedAtBetween(User user, WorkoutTemplate template, LocalDateTime start, LocalDateTime end);
}
