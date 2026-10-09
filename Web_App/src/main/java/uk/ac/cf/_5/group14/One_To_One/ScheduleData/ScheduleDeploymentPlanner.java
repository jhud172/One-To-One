package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;

/** Shared date rules for native forms and calendar deployment previews. */
@Component
public class ScheduleDeploymentPlanner {
    private static final Set<String> REPEATS = Set.of("daily", "weekly", "monthly", "yearly", "custom", "forever");
    private static final Set<String> UNITS = Set.of("day", "week", "month", "year");

    public Window resolveWindow(Map<String, Object> request) {
        try {
            LocalDate start = date(request.get("startDate"));
            LocalDate selected = date(request.get("selectedDate"));
            LocalDate anchor = start != null ? start : selected != null ? selected : LocalDate.now();
            if (request.containsKey("recurrence")) {
                if (!(request.get("recurrence") instanceof Map<?, ?> raw)) return null;
                String repeat = text(raw.get("repeat"), "weekly");
                if (!REPEATS.contains(repeat)) return null;
                int interval = "custom".equals(repeat) ? integer(raw.get("interval"), 1) : 1;
                String unit = text(raw.get("unit"), "week").replaceFirst("s$", "");
                if (interval < 1 || interval > 52 || !UNITS.contains(unit)) return null;
                LocalDate end = date(raw.get("endDate"));
                if (end == null) end = "forever".equals(repeat) ? anchor.plusYears(1).minusDays(1) : anchor.plusDays(6);
                return bounded(anchor, end, repeat, interval, unit);
            }
            String scope = text(request.get("scope"), "week");
            if (!Set.of("week", "weeks", "forward").contains(scope)) return null;
            int weeks = integer(request.get("weeks"), "forward".equals(scope) ? 8 : 1);
            if (weeks < 1 || weeks > 52) return null;
            if ("week".equals(scope)) anchor = anchor.with(DayOfWeek.MONDAY);
            LocalDate end = anchor.plusWeeks("week".equals(scope) ? 1 : weeks).minusDays(1);
            return bounded(anchor, end, "weekly", 1, "week");
        } catch (IllegalArgumentException | DateTimeException exception) {
            return null;
        }
    }

    private Window bounded(LocalDate start, LocalDate end, String repeat, int interval, String unit) {
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days < 1 || days > 366) return null;
        return new Window(start, end, (int) ((days + 6) / 7), repeat, interval, unit);
    }

    public List<PlannedOccurrence> plan(Window window, List<ScheduleEntry> entries) {
        List<PlannedOccurrence> planned = new ArrayList<>();
        for (ScheduleEntry entry : entries) {
            Schedule schedule = entry.getSchedule();
            ScheduleType type = schedule != null && schedule.getScheduleType() != null ? schedule.getScheduleType() : ScheduleType.WEEKLY;
            RotationMode rotation = schedule != null && schedule.getRotationMode() != null ? schedule.getRotationMode() : RotationMode.WEEKLY_REPEAT;
            int cycleDays = type == ScheduleType.DAILY ? 1 : type == ScheduleType.CUSTOM
                    ? schedule.getCustomDayCount() == null ? 7 : schedule.getCustomDayCount() : 7;
            if (cycleDays < 1 || cycleDays > 14 || entry.getDayOfWeek() < 1 || entry.getDayOfWeek() > cycleDays
                    || (entry.getExercise() == null && entry.getCustomExercise() == null)) continue;
            LocalDate first = type == ScheduleType.WEEKLY
                    ? window.start().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(entry.getDayOfWeek())))
                    : window.start().plusDays(entry.getDayOfWeek() - 1L);
            String unit = "custom".equals(window.scope()) ? window.unit()
                    : "daily".equals(window.scope()) ? "day"
                    : "monthly".equals(window.scope()) ? "month"
                    : "yearly".equals(window.scope()) ? "year" : "week";
            for (long cycle = 0; ; cycle++) {
                long offset = cycle * window.interval();
                // Advance from the original date so short months do not cause permanent date drift.
                LocalDate date = switch (unit) {
                    case "day" -> first.plusDays(offset);
                    case "month" -> first.plusMonths(offset);
                    case "year" -> first.plusYears(offset);
                    default -> "custom".equals(window.scope()) ? first.plusWeeks(offset)
                            : type == ScheduleType.DAILY ? first.plusDays(offset)
                            : type == ScheduleType.CUSTOM ? first.plusDays(offset * (rotation == RotationMode.CONTINUOUS_ROTATION
                                    ? cycleDays : ((cycleDays + 6) / 7) * 7L)) : first.plusWeeks(offset);
                };
                if (date.isAfter(window.end())) break;
                planned.add(new PlannedOccurrence(date, entry));
                if (rotation == RotationMode.NONE) break;
            }
        }
        planned.sort(Comparator.comparing(PlannedOccurrence::date).thenComparing(p -> p.entry().getOrderNumber()));
        return planned;
    }

    public List<PlannedOccurrence> missingOccurrences(List<PlannedOccurrence> planned,
                                                     List<ScheduleOccurrence> existing, Long scheduleId) {
        Map<OccurrenceKey, Integer> counts = new HashMap<>();
        for (ScheduleOccurrence occurrence : existing) {
            if (occurrence.getSchedule() == null || !Objects.equals(occurrence.getSchedule().getId(), scheduleId)) continue;
            counts.merge(new OccurrenceKey(occurrence.getDate(),
                    occurrence.getExercise() != null ? occurrence.getExercise().getId() : null,
                    occurrence.getCustomExercise() != null ? occurrence.getCustomExercise().getId() : null), 1, Integer::sum);
        }
        List<PlannedOccurrence> missing = new ArrayList<>();
        for (PlannedOccurrence occurrence : planned) {
            OccurrenceKey key = new OccurrenceKey(occurrence.date(),
                    occurrence.entry().getExercise() != null ? occurrence.entry().getExercise().getId() : null,
                    occurrence.entry().getCustomExercise() != null ? occurrence.entry().getCustomExercise().getId() : null);
            int count = counts.getOrDefault(key, 0);
            if (count > 0) counts.put(key, count - 1);
            else missing.add(occurrence);
        }
        return missing;
    }

    public Set<Long> matchingOccurrenceIds(List<PlannedOccurrence> planned, List<ScheduleOccurrence> existing, Long scheduleId) {
        Map<OccurrenceKey, Integer> counts = new HashMap<>();
        for (PlannedOccurrence occurrence : planned) {
            counts.merge(new OccurrenceKey(occurrence.date(),
                    occurrence.entry().getExercise() != null ? occurrence.entry().getExercise().getId() : null,
                    occurrence.entry().getCustomExercise() != null ? occurrence.entry().getCustomExercise().getId() : null), 1, Integer::sum);
        }
        Set<Long> matching = new HashSet<>();
        // Keep history before pending duplicates when only part of a repeated exercise is needed.
        List<ScheduleOccurrence> ordered = existing.stream()
                .sorted(Comparator.comparing(occ -> !(occ.isCompleted() || occ.isMissed() || occ.getExerciseLog() != null))).toList();
        for (ScheduleOccurrence occurrence : ordered) {
            if (occurrence.getSchedule() == null || !Objects.equals(occurrence.getSchedule().getId(), scheduleId)) continue;
            OccurrenceKey key = new OccurrenceKey(occurrence.getDate(),
                    occurrence.getExercise() != null ? occurrence.getExercise().getId() : null,
                    occurrence.getCustomExercise() != null ? occurrence.getCustomExercise().getId() : null);
            int count = counts.getOrDefault(key, 0);
            if (count > 0) {
                matching.add(occurrence.getId());
                counts.put(key, count - 1);
            }
        }
        return matching;
    }

    private LocalDate date(Object raw) {
        if (raw == null) return null;
        if (!(raw instanceof String value) || value.isBlank()) throw new IllegalArgumentException("Invalid date");
        return LocalDate.parse(value.trim());
    }

    private int integer(Object raw, int fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Number number && number.doubleValue() == number.intValue()) return number.intValue();
        if (raw instanceof String value) return Integer.parseInt(value.trim());
        throw new IllegalArgumentException("Invalid interval");
    }

    private String text(Object raw, String fallback) {
        if (raw == null) return fallback;
        if (!(raw instanceof String value) || value.isBlank()) throw new IllegalArgumentException("Invalid option");
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public record Window(LocalDate start, LocalDate end, int weeks, String scope, int interval, String unit) {}
    public record PlannedOccurrence(LocalDate date, ScheduleEntry entry) {}
    private record OccurrenceKey(LocalDate date, Long exerciseId, Long customExerciseId) {}
}
