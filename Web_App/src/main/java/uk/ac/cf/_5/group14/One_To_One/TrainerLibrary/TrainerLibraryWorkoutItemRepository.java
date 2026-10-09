package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainerLibraryWorkoutItemRepository extends JpaRepository<TrainerLibraryWorkoutItem, Long> {
    @org.springframework.data.jpa.repository.Query("select item.workoutId, count(item) from TrainerLibraryWorkoutItem item where item.workoutId in :workoutIds and item.workoutId in (select workout.id from TrainerLibraryWorkoutTemplate workout where workout.trainerId = :trainerId) group by item.workoutId")
    List<Object[]> countOwnedWorkoutItemsOnPage(@org.springframework.data.repository.query.Param("trainerId") Long trainerId,
                                               @org.springframework.data.repository.query.Param("workoutIds") List<Long> workoutIds);

    List<TrainerLibraryWorkoutItem> findByWorkoutIdOrderByOrderIndexAsc(Long workoutId);

    void deleteByWorkoutId(Long workoutId);
    boolean existsByExerciseId(Long exerciseId);
    boolean existsByWorkoutIdAndOrderIndex(Long workoutId, Integer orderIndex);

    @org.springframework.data.jpa.repository.Query("select item.workoutId, count(item) from TrainerLibraryWorkoutItem item where item.workoutId in (select workout.id from TrainerLibraryWorkoutTemplate workout where workout.trainerId = :trainerId) group by item.workoutId")
    List<Object[]> countOwnedWorkoutItems(@org.springframework.data.repository.query.Param("trainerId") Long trainerId);
}
