package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkoutSetVideoRepository extends JpaRepository<WorkoutSetVideo, Long> {

    Optional<WorkoutSetVideo> findTopBySetLogOrderByCreatedAtDesc(WorkoutSetLog setLog);

    Optional<WorkoutSetVideo> findByIdAndSetLog(Long id, WorkoutSetLog setLog);

    List<WorkoutSetVideo> findBySetLogSessionOrderByCreatedAtDescIdDesc(WorkoutSession session);
}
