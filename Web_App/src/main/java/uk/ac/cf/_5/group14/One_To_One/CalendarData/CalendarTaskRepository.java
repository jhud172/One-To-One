package uk.ac.cf._5.group14.One_To_One.CalendarData;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CalendarTaskRepository extends CrudRepository<CalendarTask, Long> {

    List<CalendarTask> findByUserAndDateOrderByTime(User user, LocalDate date);

    long countByUserAndDate(User user, LocalDate date);

    boolean existsByUserAndDateAndRequiresLogTrueAndCompletedFalse(User user, LocalDate date);

    boolean existsByUserAndDateAndRequiresLogTrueAndExerciseLogIsNull(User user, LocalDate date);

    List<CalendarTask> findByUserOrderByDateAscTimeAsc(User user);

    CalendarTask findByIdAndUser(Long id, User user);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select task from CalendarTask task where task.id = :id and task.user.id = :userId")
    java.util.Optional<CalendarTask> findOwnedForLogUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                         @org.springframework.data.repository.query.Param("userId") Long userId);

    java.util.Optional<CalendarTask> findFirstByExerciseLogIdAndUserId(Long exerciseLogId, Long userId);

    List<CalendarTask> findByUserAndDateBetween(User user, LocalDate start, LocalDate end);

    Optional<CalendarTask> findFirstByUserAndDateAndCompletedFalseOrderByTimeAsc(User user, LocalDate date);

    boolean existsByUserAndDateAndTrainerTemplateEntryId(User user, LocalDate date, Long trainerTemplateEntryId);

    long countByUserAndDateBeforeAndCompletedFalse(User user, LocalDate date);

    long countByUserAndDateAndCompletedTrue(User user, LocalDate date);

    @Query("""
        SELECT t FROM CalendarTask t
        JOIN FETCH t.user
        WHERE t.date = :date
        AND t.time IS NOT NULL
        AND t.time >= :fromTime
        AND t.time <= :toTime
        AND t.completed = false
    """)
    List<CalendarTask> findUpcomingTasks(LocalDate date, LocalTime fromTime, LocalTime toTime);
}
