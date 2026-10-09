package uk.ac.cf._5.group14.One_To_One.Nutrition;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.ac.cf._5.group14.One_To_One.Users.User;

@Service
public class DailyNutritionLogServiceImpl implements DailyNutritionLogService {

    private static final int MAX_CALORIES = 20000;
    private static final int MAX_MACRO_GRAMS = 1000;
    private static final int MAX_FIBRE_GRAMS = 200;
    private static final int MAX_WATER_ML = 10000;
    private static final int MAX_NOTES_LENGTH = 1000;

    private final DailyNutritionLogRepository repository;
    private final uk.ac.cf._5.group14.One_To_One.Users.UserRepository users;
    private final jakarta.persistence.EntityManager entityManager;

    public DailyNutritionLogServiceImpl(DailyNutritionLogRepository repository,
                                       uk.ac.cf._5.group14.One_To_One.Users.UserRepository users,
                                       jakarta.persistence.EntityManager entityManager) {
        this.repository = repository;
        this.users = users;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public DailyNutritionLog getOrCreateForDate(User user, LocalDate date) {
        validateUser(user);
        LocalDate targetDate = validateDate(date);

        return repository.findByUserAndDate(user, targetDate)
            .orElseGet(() -> {
                DailyNutritionLog empty = new DailyNutritionLog();
                empty.setUser(user);
                empty.setDate(targetDate);
                empty.setCalories(0);
                empty.setProteinGrams(0);
                empty.setCarbsGrams(0);
                empty.setFatGrams(0);
                return empty;
            });
    }

    @Override
    @Transactional
    public DailyNutritionLog upsert(User user, LocalDate date, UpsertRequest request) {
        return saveEntry(user, date, request, null, false);
    }

    @Override
    @Transactional
    public DailyNutritionLog upsert(User user, LocalDate date, UpsertRequest request, String expectedRevision) {
        return saveEntry(user, date, request, expectedRevision, true);
    }

    private DailyNutritionLog saveEntry(User user, LocalDate date, UpsertRequest request, String expectedRevision, boolean checkRevision) {
        validateUser(user);
        LocalDate targetDate = validateDate(date);
        if (request == null) {
            throw new IllegalArgumentException("Nutrition data is required");
        }

        validateRequired("Calories", request.calories(), MAX_CALORIES);
        validateRequired("Protein grams", request.proteinGrams(), MAX_MACRO_GRAMS);
        validateRequired("Carbs grams", request.carbsGrams(), MAX_MACRO_GRAMS);
        validateRequired("Fat grams", request.fatGrams(), MAX_MACRO_GRAMS);
        validateOptional("Fibre grams", request.fibreGrams(), MAX_FIBRE_GRAMS);
        validateOptional("Water ml", request.waterMl(), MAX_WATER_ML);

        String notes = trimToNull(request.notes());
        if (notes != null && notes.length() > MAX_NOTES_LENGTH) {
            throw new IllegalArgumentException("Notes must be " + MAX_NOTES_LENGTH + " characters or less");
        }

        // Lock the owner even when the date has no row yet, so concurrent first entries are serialised.
        users.findByIdForUpdate(user.getId()).orElseThrow(() -> new SecurityException("Nutrition owner unavailable"));
        DailyNutritionLog log = repository.findByUserAndDate(user, targetDate)
            .orElseGet(() -> {
                DailyNutritionLog created = new DailyNutritionLog();
                created.setUser(user);
                created.setDate(targetDate);
                return created;
            });

        if (log.getId() != null) {
            try { entityManager.refresh(log, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
            catch (jakarta.persistence.EntityNotFoundException removed) { throw new StaleNutritionLogException(); }
        }
        if (checkRevision && !revision(log).equals(expectedRevision)) throw new StaleNutritionLogException();

        log.setCalories(request.calories());
        log.setProteinGrams(request.proteinGrams());
        log.setCarbsGrams(request.carbsGrams());
        log.setFatGrams(request.fatGrams());
        log.setFibreGrams(request.fibreGrams());
        log.setWaterMl(request.waterMl());
        log.setNotes(notes);

        return repository.save(log);
    }

    @Override
    public String revision(DailyNutritionLog log) {
        // Empty dates and recorded zeroes are distinct; include the owner/date to bind the draft to its day.
        Object[] values = log.getId() == null
                ? new Object[]{log.getUser().getId(), log.getDate(), null}
                : new Object[]{log.getUser().getId(), log.getDate(), log.getId(), log.getUpdatedAt(), log.getCalories(),
                    log.getProteinGrams(), log.getCarbsGrams(), log.getFatGrams(), log.getFibreGrams(), log.getWaterMl(), log.getNotes()};
        var text = new StringBuilder();
        for (Object value : values) {
            String part = value == null ? null : value.toString();
            text.append(part == null ? -1 : part.length()).append(':');
            if (part != null) text.append(part);
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyNutritionRangeSummary> getRange(User user, LocalDate start, LocalDate end) {
        validateUser(user);
        LocalDate startDate = validateDate(start);
        LocalDate endDate = validateDate(end);
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date must be on or after start date");
        }

        return repository.findByUserAndDateBetweenOrderByDateAsc(user, startDate, endDate)
            .stream()
            .map(log -> {
                DailyNutritionSummary summary = summarize(log);
                return new DailyNutritionRangeSummary(
                    summary.date(),
                    summary.calories(),
                    summary.proteinGrams(),
                    summary.carbsGrams(),
                    summary.fatGrams(),
                    summary.fibreGrams(),
                    summary.waterMl(),
                    summary.macroCalories(),
                    summary.totalMacroGrams(),
                    summary.calorieDelta(),
                    summary.summaryText()
                );
            })
            .toList();
    }

    @Override
    public DailyNutritionSummary summarize(DailyNutritionLog log) {
        if (log == null || log.getDate() == null) {
            return null;
        }

        int calories = safe(log.getCalories());
        int protein = safe(log.getProteinGrams());
        int carbs = safe(log.getCarbsGrams());
        int fat = safe(log.getFatGrams());
        int fibre = safe(log.getFibreGrams());

        int macroCalories = (protein * 4) + (carbs * 4) + (fat * 9);
        int totalMacroGrams = protein + carbs + fat + fibre;
        int calorieDelta = calories - macroCalories;

        return new DailyNutritionSummary(
            log.getDate(),
            log.getCalories(),
            log.getProteinGrams(),
            log.getCarbsGrams(),
            log.getFatGrams(),
            log.getFibreGrams(),
            log.getWaterMl(),
            macroCalories,
            totalMacroGrams,
            calorieDelta,
            log.getNotes(),
            stringifyDayLog(log)
        );
    }

    @Override
    public String stringifyDayLog(DailyNutritionLog log) {
        if (log == null || log.getDate() == null) {
            return "No nutrition data";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Date: ").append(log.getDate());
        summary.append(" | Calories: ").append(safe(log.getCalories()));
        summary.append(" | Protein: ").append(safe(log.getProteinGrams())).append("g");
        summary.append(" | Carbs: ").append(safe(log.getCarbsGrams())).append("g");
        summary.append(" | Fat: ").append(safe(log.getFatGrams())).append("g");

        if (log.getFibreGrams() != null) {
            summary.append(" | Fibre: ").append(log.getFibreGrams()).append("g");
        }
        if (log.getWaterMl() != null) {
            summary.append(" | Water: ").append(log.getWaterMl()).append("ml");
        }
        if (log.getNotes() != null && !log.getNotes().isBlank()) {
            summary.append(" | Notes: ").append(log.getNotes().trim());
        }

        return summary.toString();
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static LocalDate validateDate(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("Date is required");
        }
        return date;
    }

    private static void validateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
    }

    private static String trimToNull(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void validateRequired(String label, Integer value, int max) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
        validateRange(label, value, max);
    }

    private static void validateOptional(String label, Integer value, int max) {
        if (value == null) {
            return;
        }
        validateRange(label, value, max);
    }

    private static void validateRange(String label, Integer value, int max) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be 0 or more");
        }
        if (value > max) {
            throw new IllegalArgumentException(label + " must be " + max + " or less");
        }
    }
}
