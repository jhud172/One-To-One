package uk.ac.cf._5.group14.One_To_One.Workout;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutRepository extends JpaRepositoryImplementation<Workout,Long> {
    List<Workout> findByUserId(Long userId);

    // used when a single workout is needed along with its exercises (better performance than fetching exercises eagerly all the time)
    @EntityGraph(attributePaths = {"exercises"})
    Optional<Workout> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Query(value = """
            select exists (select 1 from workout_schedule where workout_id = :id)
                or exists (select 1 from workout_sessions where workout_id = :id)
                or exists (select 1 from workout_template_sessions where workout_id = :id)
            """, nativeQuery = true)
    boolean isReferencedByTrainingData(@org.springframework.data.repository.query.Param("id") Long id);
}
