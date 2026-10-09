package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerScheduleTemplateRepository extends JpaRepository<TrainerScheduleTemplate, Long> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "entries")
    List<TrainerScheduleTemplate> findByTrainerIdOrderByUpdatedAtDesc(Long trainerId);

    Optional<TrainerScheduleTemplate> findByIdAndTrainerId(Long id, Long trainerId);

    boolean existsByTrainerId(Long trainerId);

    long countByTrainerId(Long trainerId);

    @org.springframework.data.jpa.repository.Query("select template from TrainerScheduleTemplate template where template.trainerId = :trainerId and (lower(template.name) like lower(:pattern) escape '!' or lower(coalesce(template.tags, '')) like lower(:pattern) escape '!')")
    org.springframework.data.domain.Page<TrainerScheduleTemplate> searchOwned(
            @org.springframework.data.repository.query.Param("trainerId") Long trainerId,
            @org.springframework.data.repository.query.Param("pattern") String pattern,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select template from TrainerScheduleTemplate template where template.id = :id and template.trainerId = :trainerId")
    Optional<TrainerScheduleTemplate> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                        @org.springframework.data.repository.query.Param("trainerId") Long trainerId);
}
