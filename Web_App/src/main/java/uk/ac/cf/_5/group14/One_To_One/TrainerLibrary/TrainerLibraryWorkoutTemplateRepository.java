package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerLibraryWorkoutTemplateRepository extends JpaRepository<TrainerLibraryWorkoutTemplate, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select workout from TrainerLibraryWorkoutTemplate workout
            where workout.trainerId = :trainerId and
              (lower(workout.title) like lower(:pattern) escape '!'
               or lower(coalesce(workout.summary, '')) like lower(:pattern) escape '!')
            """)
    org.springframework.data.domain.Page<TrainerLibraryWorkoutTemplate> searchOwned(
            @org.springframework.data.repository.query.Param("trainerId") Long trainerId,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            org.springframework.data.domain.Pageable pageable);

    long countByTrainerId(Long trainerId);
    org.springframework.data.domain.Page<TrainerLibraryWorkoutTemplate> findByTrainerIdAndTitleContainingIgnoreCase(
            Long trainerId, String title, org.springframework.data.domain.Pageable pageable);
    List<TrainerLibraryWorkoutTemplate> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);

    Optional<TrainerLibraryWorkoutTemplate> findByIdAndTrainerId(Long id, Long trainerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select workout from TrainerLibraryWorkoutTemplate workout where workout.id = :id and workout.trainerId = :trainerId")
    Optional<TrainerLibraryWorkoutTemplate> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                              @org.springframework.data.repository.query.Param("trainerId") Long trainerId);
}
