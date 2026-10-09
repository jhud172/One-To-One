package uk.ac.cf._5.group14.One_To_One.Nutrition;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;

public interface DailyNutritionLogService {

    DailyNutritionLog getOrCreateForDate(User user, LocalDate date);

    DailyNutritionLog upsert(User user, LocalDate date, UpsertRequest request);

    DailyNutritionLog upsert(User user, LocalDate date, UpsertRequest request, String expectedRevision);

    String revision(DailyNutritionLog log);

    List<DailyNutritionRangeSummary> getRange(User user, LocalDate start, LocalDate end);

    DailyNutritionSummary summarize(DailyNutritionLog log);

    String stringifyDayLog(DailyNutritionLog log);

    record UpsertRequest(
        Integer calories,
        Integer proteinGrams,
        Integer carbsGrams,
        Integer fatGrams,
        Integer fibreGrams,
        Integer waterMl,
        String notes
    ) {
    }

    record DailyNutritionSummary(
        LocalDate date,
        Integer calories,
        Integer proteinGrams,
        Integer carbsGrams,
        Integer fatGrams,
        Integer fibreGrams,
        Integer waterMl,
        int macroCalories,
        int totalMacroGrams,
        int calorieDelta,
        String notes,
        String summaryText
    ) {
    }

    record DailyNutritionRangeSummary(
        LocalDate date,
        Integer calories,
        Integer proteinGrams,
        Integer carbsGrams,
        Integer fatGrams,
        Integer fibreGrams,
        Integer waterMl,
        int macroCalories,
        int totalMacroGrams,
        int calorieDelta,
        String summaryText
    ) {
    }
}
