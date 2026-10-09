package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerLibraryProgrammeTemplateRepository extends JpaRepository<TrainerLibraryProgrammeTemplate, Long> {
    long countByTrainerId(Long trainerId);
    org.springframework.data.domain.Page<TrainerLibraryProgrammeTemplate> findByTrainerIdAndTitleContainingIgnoreCase(
            Long trainerId, String title, org.springframework.data.domain.Pageable pageable);
    List<TrainerLibraryProgrammeTemplate> findByTrainerIdOrderByCreatedAtDesc(Long trainerId);

    Optional<TrainerLibraryProgrammeTemplate> findByIdAndTrainerId(Long id, Long trainerId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select programme from TrainerLibraryProgrammeTemplate programme where programme.id = :id and programme.trainerId = :trainerId")
    Optional<TrainerLibraryProgrammeTemplate> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                                @org.springframework.data.repository.query.Param("trainerId") Long trainerId);
}
