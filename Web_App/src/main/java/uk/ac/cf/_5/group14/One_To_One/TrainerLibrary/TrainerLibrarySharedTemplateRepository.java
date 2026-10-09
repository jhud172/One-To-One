package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrainerLibrarySharedTemplateRepository extends JpaRepository<TrainerLibrarySharedTemplate, Long> {
    List<TrainerLibrarySharedTemplate> findByTrainerIdAndClientIdOrderBySharedAtDesc(Long trainerId, Long clientId);

    List<TrainerLibrarySharedTemplate> findByClientIdAndTrainerIdOrderBySharedAtDesc(Long clientId, Long trainerId);

    Optional<TrainerLibrarySharedTemplate> findByClientIdAndTrainerIdAndTemplateTypeAndTemplateId(Long clientId, Long trainerId, TrainerLibraryTemplateType templateType, Long templateId);
    void deleteByTrainerIdAndTemplateTypeAndTemplateId(Long trainerId, TrainerLibraryTemplateType templateType, Long templateId);
}
