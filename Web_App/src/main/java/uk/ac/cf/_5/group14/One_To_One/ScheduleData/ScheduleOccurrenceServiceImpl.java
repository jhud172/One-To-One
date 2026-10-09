package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ScheduleOccurrenceServiceImpl implements ScheduleOccurrenceService {
    private final ScheduleEntryService scheduleEntryService;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final ScheduleAppliedRepository scheduleAppliedRepository;
    private final ScheduleDeploymentPlanner planner;

    @Override
    public List<ScheduleOccurrence> getActiveSchedulesForUser(User user) {
        return scheduleOccurrenceRepository.findActiveByUser(user);
    }

    @Override
    public void generateOccurrencesForSchedule(
            Schedule schedule,
            User user,
            LocalDate startDate,
            LocalDate endDate,
            int everyNWeeks
    ) {
        if (schedule == null || user == null || startDate == null || endDate == null) {
            return;
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days < 1 || days > 366 || everyNWeeks < 1 || everyNWeeks > 52) return;
        List<ScheduleEntry> entries = scheduleEntryService.getEntries(schedule.getId());
        if (entries.isEmpty()) return;
        var window = new ScheduleDeploymentPlanner.Window(startDate, endDate, (int) ((days + 6) / 7),
                everyNWeeks == 1 ? "weekly" : "custom", everyNWeeks, "week");
        var planned = planner.plan(window, entries);
        if (planned.isEmpty()) return;
        var missing = planner.missingOccurrences(planned,
                scheduleOccurrenceRepository.findByUserAndDateBetween(user, startDate, endDate), schedule.getId());
        for (var row : missing) {
            var occurrence = new ScheduleOccurrence();
            occurrence.setUser(user); occurrence.setExercise(row.entry().getExercise());
            occurrence.setCustomExercise(row.entry().getCustomExercise()); occurrence.setSchedule(schedule);
            occurrence.setScheduleName(schedule.getName()); occurrence.setDate(row.date());
            scheduleOccurrenceRepository.save(occurrence);
        }
    }

    @Override
    public List<ScheduleOccurrence> getOccurrencesForUserOnDate(User user, LocalDate date) {
        return visibleForCalendar(user, scheduleOccurrenceRepository.findByUserAndDate(user, date));
    }

    @Override
    public Map<LocalDate, List<ScheduleOccurrence>> getOccurrencesForUserInMonth(
            User user,
            int year,
            int month
    ) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());

        List<ScheduleOccurrence> all =
                scheduleOccurrenceRepository.findByUserAndDateBetween(user, from, to);

        Map<LocalDate, List<ScheduleOccurrence>> map = new HashMap<>();
        for (ScheduleOccurrence occ : visibleForCalendar(user, all)) {
            map.computeIfAbsent(occ.getDate(), d -> new ArrayList<>()).add(occ);
        }
        return map;
    }

    @Override
    public Map<LocalDate, List<ScheduleOccurrence>> getOccurrencesByRange(User user, LocalDate start, LocalDate end) {
        Map<LocalDate, List<ScheduleOccurrence>> map = new HashMap<>();

        List<ScheduleOccurrence> list =
                scheduleOccurrenceRepository.findByUserAndDateBetween(user, start, end);

        for (ScheduleOccurrence occ : visibleForCalendar(user, list)) {
            map.computeIfAbsent(occ.getDate(), d -> new ArrayList<>()).add(occ);
        }

        return map;
    }

    private List<ScheduleOccurrence> visibleForCalendar(User user, List<ScheduleOccurrence> occurrences) {
        if (occurrences.isEmpty()) return occurrences;
        Map<Long, List<ScheduleApplied>> bySchedule = new HashMap<>();
        for (ScheduleApplied applied : scheduleAppliedRepository.findByUser(user)) {
            if (applied.getSchedule() != null) {
                bySchedule.computeIfAbsent(applied.getSchedule().getId(), ignored -> new ArrayList<>()).add(applied);
            }
        }
        return occurrences.stream().filter(occurrence -> {
            List<ScheduleApplied> matching = occurrence.getSchedule() == null ? List.of()
                    : bySchedule.getOrDefault(occurrence.getSchedule().getId(), List.of()).stream()
                        .filter(applied -> covers(applied, occurrence.getDate())).toList();
            occurrence.setLoggingRequested(matching.stream().anyMatch(applied -> applied.isShownOnCalendar() && applied.isRequiresLogging()));
            // Legacy occurrences without a deployment remain visible. Overlapping windows use a union.
            return matching.isEmpty() || matching.stream().anyMatch(ScheduleApplied::isShownOnCalendar);
        }).toList();
    }

    static boolean covers(ScheduleApplied applied, LocalDate date) {
        if (applied.getDateApplied() == null || date == null) return false;
        return !date.isBefore(applied.getDateApplied())
                && date.isBefore(applied.getDateApplied().plusWeeks(Math.max(1, applied.getDurationWeeks())));
    }
}
