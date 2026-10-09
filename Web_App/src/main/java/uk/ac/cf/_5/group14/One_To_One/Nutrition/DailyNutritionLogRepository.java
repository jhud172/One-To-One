package uk.ac.cf._5.group14.One_To_One.Nutrition;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyNutritionLogRepository extends JpaRepository<DailyNutritionLog, Long> {
    Optional<DailyNutritionLog> findByUserAndDate(User user, LocalDate date);

    List<DailyNutritionLog> findByUserAndDateBetweenOrderByDateAsc(User user, LocalDate start, LocalDate end);

    Optional<DailyNutritionLog> findTopByUserOrderByDateDescIdDesc(User user);

    Optional<DailyNutritionLog> findTopByUserAndDateLessThanEqualOrderByDateDescIdDesc(User user, LocalDate date);
}
