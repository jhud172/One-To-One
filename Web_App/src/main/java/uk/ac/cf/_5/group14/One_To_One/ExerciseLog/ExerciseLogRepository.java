package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.cf._5.group14.One_To_One.Profile.AverageConfidenceTrackerDto;
import uk.ac.cf._5.group14.One_To_One.Profile.AverageMoodTrackerDto;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


public interface ExerciseLogRepository extends JpaRepository<ExerciseLog, Long> {

    @Query("select avg(el.moodAfter) - avg(el.moodBefore) as moodDifference, " +
            "max(el.date) as dateRated from ExerciseLog el " +
            "where el.user = :user " +
            "and el.date >= :dateBefore " +
            "group by month(el.date), year(el.date) " +
            "order by el.date asc ")
    List<AverageMoodTrackerDto> getAverageMoodDifference(@Param("user") User user, @Param("dateBefore") LocalDate dateBefore);

    @Query("select avg(el.confidence) as confidence, " +
            "max(el.date) as dateRated from ExerciseLog el " +
            "where el.user = :user " +
            "and el.date >= :dateBefore " +
            "group by month(el.date), year(el.date) " +
            "order by el.date asc ")
    List<AverageConfidenceTrackerDto> getAverageConfidence(@Param("user") User user, @Param("dateBefore") LocalDate dateBefore);

    List<ExerciseLog> findByUser(User user);
    Optional<ExerciseLog> findByIdAndUser(Long id, User user);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select log from ExerciseLog log where log.id = :id and log.user = :owner")
    Optional<ExerciseLog> findOwnedForUpdate(@Param("id") Long id, @Param("owner") User user);

    List<ExerciseLog> findByUserOrderByDateDesc(User user);

    @Query("""
            select log from ExerciseLog log
            left join log.occurrence occurrence on occurrence.user = :owner
            left join occurrence.exercise exercise
            left join occurrence.customExercise customExercise on customExercise.userId = :ownerId
            left join log.calendarTask task on task.user = :owner
            where log.user = :owner
              and (:fromDate is null or log.date >= :fromDate)
              and (:untilDate is null or log.date <= :untilDate)
              and (lower(coalesce(log.comments, '')) like lower(:pattern) escape '!'
                or cast(log.date as String) like :pattern escape '!'
                or lower(coalesce(exercise.name, '')) like lower(:pattern) escape '!'
                or lower(coalesce(customExercise.name, '')) like lower(:pattern) escape '!'
                or lower(coalesce(task.title, '')) like lower(:pattern) escape '!')
            """)
    org.springframework.data.domain.Page<ExerciseLog> searchHistory(
            @Param("owner") User owner, @Param("ownerId") Long ownerId, @Param("pattern") String pattern,
            @Param("fromDate") LocalDate from, @Param("untilDate") LocalDate until,
            org.springframework.data.domain.Pageable pageable);

    @Query("select el from ExerciseLog el where el.user = :user " +
            "order by el.date desc limit 3")
    List<ExerciseLog> findTop5RecentExerciseLogs(@Param("user") User user);

}
