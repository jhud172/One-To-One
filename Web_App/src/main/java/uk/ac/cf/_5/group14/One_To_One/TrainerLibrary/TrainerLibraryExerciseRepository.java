package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerLibraryExerciseRepository extends JpaRepository<TrainerLibraryExercise, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select exercise from TrainerLibraryExercise exercise
            where exercise.trainerId = :trainerId and
              (lower(exercise.name) like lower(:pattern) escape '!'
               or lower(exercise.primaryMuscles) like lower(:pattern) escape '!'
               or lower(exercise.equipment) like lower(:pattern) escape '!')
            """)
    org.springframework.data.domain.Page<TrainerLibraryExercise> searchOwned(
            @org.springframework.data.repository.query.Param("trainerId") Long trainerId,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            org.springframework.data.domain.Pageable pageable);

    long countByTrainerId(Long trainerId);
    org.springframework.data.domain.Page<TrainerLibraryExercise> findByTrainerIdAndNameContainingIgnoreCase(
            Long trainerId, String name, org.springframework.data.domain.Pageable pageable);
    List<TrainerLibraryExercise> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);

    Optional<TrainerLibraryExercise> findByIdAndTrainerId(Long id, Long trainerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select exercise from TrainerLibraryExercise exercise where exercise.id = :id and exercise.trainerId = :trainerId")
    Optional<TrainerLibraryExercise> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                       @org.springframework.data.repository.query.Param("trainerId") Long trainerId);
}
