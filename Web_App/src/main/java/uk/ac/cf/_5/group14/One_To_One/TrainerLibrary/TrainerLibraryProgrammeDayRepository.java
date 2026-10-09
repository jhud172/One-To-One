package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TrainerLibraryProgrammeDayRepository extends JpaRepository<TrainerLibraryProgrammeDay, Long> {
    List<TrainerLibraryProgrammeDay> findByProgrammeIdOrderByOrderIndexAsc(Long programmeId);

    void deleteByProgrammeId(Long programmeId);
    boolean existsByWorkoutId(Long workoutId);
    boolean existsByProgrammeIdAndOrderIndex(Long programmeId, Integer orderIndex);

    @org.springframework.data.jpa.repository.Query("select day.programmeId, count(day) from TrainerLibraryProgrammeDay day where day.programmeId in (select programme.id from TrainerLibraryProgrammeTemplate programme where programme.trainerId = :trainerId) group by day.programmeId")
    List<Object[]> countOwnedProgrammeDays(@org.springframework.data.repository.query.Param("trainerId") Long trainerId);

    @org.springframework.data.jpa.repository.Query("select day.programmeId, count(day) from TrainerLibraryProgrammeDay day where day.programmeId in :programmeIds and day.programmeId in (select programme.id from TrainerLibraryProgrammeTemplate programme where programme.trainerId = :trainerId) group by day.programmeId")
    List<Object[]> countOwnedProgrammeDaysOnPage(@org.springframework.data.repository.query.Param("trainerId") Long trainerId,
                                               @org.springframework.data.repository.query.Param("programmeIds") List<Long> programmeIds);
}
