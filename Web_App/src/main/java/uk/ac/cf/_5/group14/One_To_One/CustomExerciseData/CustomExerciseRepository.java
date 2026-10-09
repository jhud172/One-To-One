package uk.ac.cf._5.group14.One_To_One.CustomExerciseData;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomExerciseRepository extends CrudRepository<CustomExercise, Long> {

	List<CustomExercise> findByUserIdOrderByNameAsc(Long userId);

	Optional<CustomExercise> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Query(value = """
            select exists (select 1 from workouts_custom_exercises where custom_exercise_id = :id)
                or exists (select 1 from workout_template_exercises where custom_exercise_id = :id)
                or exists (select 1 from trainer_schedule_template_entries where custom_exercise_id = :id)
                or exists (select 1 from schedule_entries where custom_exercise_id = :id)
                or exists (select 1 from schedule_occurrences where custom_exercise_id = :id)
                or exists (select 1 from exercise_sessions where custom_exercise_id = :id)
                or exists (select 1 from workout_session_exercises where custom_exercise_id = :id)
            """, nativeQuery = true)
    boolean isReferencedByTrainingData(@org.springframework.data.repository.query.Param("id") Long id);

}
