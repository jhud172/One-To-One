package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleOccurrenceRepository extends JpaRepository<ScheduleOccurrence, Long> {

    long countByUserAndDate(User user, LocalDate date);

    boolean existsByUserAndDateAndCompletedFalse(User user, LocalDate date);

    boolean existsByUserAndDateAndExerciseLogIsNull(User user, LocalDate date);

    boolean existsByUserAndDateAndTrainerTemplateEntryId(User user, LocalDate date, Long trainerTemplateEntryId);

    Optional<ScheduleOccurrence> findByIdAndUserId(Long id, Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select occurrence from ScheduleOccurrence occurrence where occurrence.id = :id and occurrence.user.id = :userId")
    Optional<ScheduleOccurrence> findOwnedForLogUpdate(@org.springframework.data.repository.query.Param("id") Long id,
                                                      @org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select occurrence from ScheduleOccurrence occurrence where occurrence.user = :user and occurrence.date between :from and :to order by occurrence.id")
    List<ScheduleOccurrence> findOwnedRangeForDeployment(@org.springframework.data.repository.query.Param("user") User user,
                                                        @org.springframework.data.repository.query.Param("from") LocalDate from,
                                                        @org.springframework.data.repository.query.Param("to") LocalDate to);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select occurrence from ScheduleOccurrence occurrence where occurrence.user.id = :userId and occurrence.id in :ids order by occurrence.id")
    List<ScheduleOccurrence> findOwnedForUndo(@org.springframework.data.repository.query.Param("userId") Long userId,
                                            @org.springframework.data.repository.query.Param("ids") List<Long> ids);

    Optional<ScheduleOccurrence> findFirstByExerciseLogIdAndUserId(Long exerciseLogId, Long userId);

    @Query("""
        SELECT o FROM ScheduleOccurrence o
        LEFT JOIN FETCH o.exercise
        LEFT JOIN FETCH o.customExercise
        LEFT JOIN FETCH o.schedule
        WHERE o.user = :user
        AND o.date = :date
        AND o.schedule.id = :scheduleId
        ORDER BY o.id
    """)
    List<ScheduleOccurrence> findByUserAndDateAndScheduleId(User user, LocalDate date, Long scheduleId);

    @Query("""
        SELECT o FROM ScheduleOccurrence o
        LEFT JOIN FETCH o.exercise
        LEFT JOIN FETCH o.customExercise
        LEFT JOIN FETCH o.schedule
        JOIN FETCH o.user
        WHERE o.user = :user
        AND o.date = :date
    """)
    List<ScheduleOccurrence> findByUserAndDate(User user, LocalDate date);

    @Query("""
        SELECT o FROM ScheduleOccurrence o
        LEFT JOIN FETCH o.exercise
        LEFT JOIN FETCH o.customExercise
        LEFT JOIN FETCH o.schedule
        JOIN FETCH o.user
        WHERE o.user = :user
        AND o.date BETWEEN :from AND :to
        ORDER BY o.date
    """)
    List<ScheduleOccurrence> findByUserAndDateBetween(User user, LocalDate from, LocalDate to);

    @Query("SELECT o FROM ScheduleOccurrence o WHERE o.user = :user AND o.date >= CURRENT_DATE")
    List<ScheduleOccurrence> findActiveByUser(User user);

    @Query("SELECT o.date FROM ScheduleOccurrence o WHERE o.user = :user AND o.date BETWEEN :from AND :to AND o.completed = true")
    List<LocalDate> findCompletedDatesByUserAndDateBetween(User user, LocalDate from, LocalDate to);

    void deleteBySchedule(Schedule schedule);

    void deleteByScheduleIdAndUserId(Long scheduleId, Long userId);

}
