package uk.ac.cf._5.group14.One_To_One.WorkoutTemplate;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;

public interface WorkoutUiTemplateRepository extends JpaRepository<WorkoutTemplate, Long> {

    List<WorkoutTemplate> findByUserIsNullOrderByName();

    List<WorkoutTemplate> findByUserOrderByUpdatedAtDesc(User user);

    Optional<WorkoutTemplate> findFirstByUserIsNullAndIsDefaultTrue();

    Optional<WorkoutTemplate> findFirstByUserAndIsDefaultTrue(User user);

    Optional<WorkoutTemplate> findByIdAndUser(Long id, User user);

    @org.springframework.data.jpa.repository.Query(value = "SELECT EXISTS(SELECT 1 FROM workout_template_sessions WHERE template_id = :id)", nativeQuery = true)
    boolean isReferencedBySession(@org.springframework.data.repository.query.Param("id") Long id);
}
