package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.ac.cf._5.group14.One_To_One.Profile.BMITrackerDto;
import uk.ac.cf._5.group14.One_To_One.Profile.BloodPressureTrackerDto;
import uk.ac.cf._5.group14.One_To_One.Profile.CholesterolTrackerDto;
import uk.ac.cf._5.group14.One_To_One.Profile.WaistHeightRatioTrackerDto;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {

    @Query("""
            select hr from HealthRecord hr where hr.user = :owner
            and (:fromDate is null or hr.baselineDate >= :fromDate)
            and (:untilDate is null or hr.baselineDate <= :untilDate)
            and (:activity = '' or hr.activityLevel = :activity)
            and (lower(coalesce(hr.activityLevel, '')) like :pattern escape '!'
                 or lower(cast(hr.baselineDate as string)) like :pattern escape '!')
            order by case when hr.baselineDate is null then 1 else 0 end
            """)
    org.springframework.data.domain.Page<HealthRecord> searchHistory(@Param("owner") User owner,
            @Param("pattern") String pattern, @Param("fromDate") LocalDateTime fromDate,
            @Param("untilDate") LocalDateTime untilDate, @Param("activity") String activity,
            org.springframework.data.domain.Pageable pageable);

    Optional<HealthRecord> findTopByUserOrderByBaselineDateDescIdDesc(User user);

    Optional<HealthRecord> findByIdAndUser(Long id, User user);

        List<HealthRecord> findTop2ByUserOrderByBaselineDateDescIdDesc(User user);

    @Query("select hr.bmi as BMI, " +
            "hr.baselineDate as dateRated " +
            "from HealthRecord hr " +
            "where hr.user = :user " +
            "and hr.baselineDate >= :dateBefore " +
            "order by hr.baselineDate asc ")
    List<BMITrackerDto> getBMIs(@Param("user") User user, @Param("dateBefore") LocalDateTime dateBefore);

    @Query("select hr.waistHeightRatio as waistHeightRatio, " +
            "hr.baselineDate as dateRated " +
            "from HealthRecord hr " +
            "where hr.user = :user " +
            "and hr.baselineDate >= :dateBefore " +
            "order by hr.baselineDate asc ")
    List<WaistHeightRatioTrackerDto> getWaistHeightRatios(@Param("user") User user, @Param("dateBefore") LocalDateTime dateBefore);

    @Query("select hr.cholesterol as cholesterol, " +
            "hr.baselineDate as dateRated " +
            "from HealthRecord hr " +
            "where hr.user = :user " +
            "and hr.baselineDate >= :dateBefore " +
            "order by hr.baselineDate asc ")
    List<CholesterolTrackerDto> getCholesterolMeasurements(@Param("user") User user, @Param("dateBefore") LocalDateTime dateBefore);

    @Query("select hr.systolicBloodPressure as systolicBP," +
            "hr.diastolicBloodPressure as diastolicBP, " +
            "hr.baselineDate as dateRated " +
            "from HealthRecord hr " +
            "where hr.user = :user " +
            "and hr.baselineDate >= :dateBefore " +
            "order by hr.baselineDate asc ")
    List<BloodPressureTrackerDto> getBPMeasurements(@Param("user") User user, @Param("dateBefore") LocalDateTime dateBefore);


    List<HealthRecord> findAllByUser(User user);
}
