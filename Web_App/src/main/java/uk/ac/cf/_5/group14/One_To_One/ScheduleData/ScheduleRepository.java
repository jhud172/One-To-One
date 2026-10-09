package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleRepository extends CrudRepository<Schedule, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select schedule from Schedule schedule where schedule.id = :id and schedule.user.id = :userId")
    Optional<Schedule> findOwnedForDeployment(@org.springframework.data.repository.query.Param("id") Long id,
                                              @org.springframework.data.repository.query.Param("userId") Long userId);

    List<Schedule> findByUser(User user);

    Optional<Schedule> findByUserAndNameIgnoreCase(User user, String name);

    @org.springframework.data.jpa.repository.Query(value = """
            SELECT CASE WHEN EXISTS (SELECT 1 FROM schedule_occurrences WHERE schedule_id = :id)
                OR EXISTS (SELECT 1 FROM schedule_applied WHERE schedule_id = :id)
                OR EXISTS (SELECT 1 FROM workout_sessions WHERE schedule_id = :id)
                OR EXISTS (SELECT 1 FROM assigned_schedules WHERE schedule_id = :id)
                THEN TRUE ELSE FALSE END
            """, nativeQuery = true)
    boolean hasSavedTrainingReferences(@org.springframework.data.repository.query.Param("id") Long id);
}
