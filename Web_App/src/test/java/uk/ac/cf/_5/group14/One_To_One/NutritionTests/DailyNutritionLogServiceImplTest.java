package uk.ac.cf._5.group14.One_To_One.NutritionTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLog;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogRepository;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogService;
import uk.ac.cf._5.group14.One_To_One.Nutrition.DailyNutritionLogServiceImpl;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyNutritionLogServiceImplTest {

    @Mock
    private DailyNutritionLogRepository repository;

    @Mock private uk.ac.cf._5.group14.One_To_One.Users.UserRepository users;
    @Mock private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private DailyNutritionLogServiceImpl service;

    @Test
    void summarizeShouldComputeDerivedValues() {
        DailyNutritionLog log = new DailyNutritionLog();
        log.setDate(LocalDate.of(2026, 2, 6));
        log.setCalories(2000);
        log.setProteinGrams(150);
        log.setCarbsGrams(200);
        log.setFatGrams(70);
        log.setFibreGrams(30);

        DailyNutritionLogService.DailyNutritionSummary summary = service.summarize(log);

        assertThat(summary.macroCalories()).isEqualTo(2030);
        assertThat(summary.totalMacroGrams()).isEqualTo(450);
        assertThat(summary.calorieDelta()).isEqualTo(-30);
        assertThat(summary.summaryText()).contains("Calories");
    }

    @Test
    void upsertShouldRejectNegativeValues() {
        User user = new User();
        user.setId(1L);

        DailyNutritionLogService.UpsertRequest request = new DailyNutritionLogService.UpsertRequest(
                -10,
                100,
                200,
                60,
                null,
                null,
                null
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.upsert(user, LocalDate.of(2026, 2, 6), request));
    }

    @Test
    void getRangeShouldMapSummaries() {
        User user = new User();
        user.setId(2L);

        DailyNutritionLog log = new DailyNutritionLog();
        log.setDate(LocalDate.of(2026, 2, 6));
        log.setCalories(1800);
        log.setProteinGrams(120);
        log.setCarbsGrams(190);
        log.setFatGrams(60);
        log.setFibreGrams(25);

        when(repository.findByUserAndDateBetweenOrderByDateAsc(eq(user), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(log));

        var result = service.getRange(user, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 7));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).summaryText()).contains("Calories");
    }

    @Test
    void invalidNotesAreRejectedBeforeAnExistingEntryCanBeMutated() {
        User user = new User(); user.setId(3L);
        DailyNutritionLog existing = new DailyNutritionLog(); existing.setCalories(1900);
        org.mockito.Mockito.lenient().when(repository.findByUserAndDate(eq(user), any(LocalDate.class)))
                .thenReturn(java.util.Optional.of(existing));
        var invalid = new DailyNutritionLogService.UpsertRequest(2100, 120, 200, 65, null, null, "x".repeat(1001));
        assertThrows(IllegalArgumentException.class, () -> service.upsert(user, LocalDate.now(), invalid));
        assertThat(existing.getCalories()).isEqualTo(1900);
        org.mockito.Mockito.verifyNoInteractions(repository);
    }
}
