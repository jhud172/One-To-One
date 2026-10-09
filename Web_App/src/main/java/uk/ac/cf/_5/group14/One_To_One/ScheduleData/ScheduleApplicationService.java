package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;

/** Native application uses the same occurrence and duplicate rules as calendar deployment. */
@Service
@RequiredArgsConstructor
public class ScheduleApplicationService {
    private final ScheduleRepository schedules;
    private final ScheduleEntryService entries;
    private final ScheduleOccurrenceRepository occurrences;
    private final ScheduleAppliedRepository applications;
    private final ScheduleDeploymentPlanner planner;
    @PersistenceContext private EntityManager entityManager;

    public record DatePreview(LocalDate date, int movements, int added, int already) { }
    public record Preview(ScheduleDeploymentPlanner.Window window, List<DatePreview> dates,
                          int movements, int added, int already) { }

    @Transactional(readOnly = true)
    public Preview preview(Schedule schedule, User user, String startDate, String weeks) {
        var window = window(startDate, weeks);
        var planned = planner.plan(window, validatedEntries(schedule));
        var missing = planner.missingOccurrences(planned,
                occurrences.findByUserAndDateBetween(user, window.start(), window.end()), schedule.getId());
        Map<LocalDate,Integer> totalByDate = new TreeMap<>(), addedByDate = new TreeMap<>();
        planned.forEach(row -> totalByDate.merge(row.date(), 1, Integer::sum));
        missing.forEach(row -> addedByDate.merge(row.date(), 1, Integer::sum));
        var dates = totalByDate.entrySet().stream().map(row -> {
            int added = addedByDate.getOrDefault(row.getKey(), 0);
            return new DatePreview(row.getKey(), row.getValue(), added, row.getValue() - added);
        }).toList();
        return new Preview(window, dates, planned.size(), missing.size(), planned.size() - missing.size());
    }

    @Transactional
    public int apply(Schedule accessibleSchedule, User user, String startDate, String weeks) {
        var schedule = schedules.findOwnedForDeployment(accessibleSchedule.getId(),
                accessibleSchedule.getUser().getId()).orElseThrow(() -> new IllegalArgumentException("unavailable"));
        entityManager.refresh(schedule);
        var window = window(startDate, weeks);
        var planned = planner.plan(window, validatedEntries(schedule));
        if (planned.isEmpty()) throw new IllegalArgumentException("empty");
        var missing = planner.missingOccurrences(planned,
                occurrences.findByUserAndDateBetween(user, window.start(), window.end()), schedule.getId());
        if (missing.isEmpty()) return 0;
        var applied = new ScheduleApplied();
        applied.setSchedule(schedule); applied.setUser(user); applied.setDateApplied(window.start());
        applied.setDurationWeeks(window.weeks()); applied.setShownOnCalendar(true); applied.setRequiresLogging(false);
        applications.save(applied);
        for (var row : missing) {
            var occurrence = new ScheduleOccurrence();
            occurrence.setUser(user); occurrence.setSchedule(schedule); occurrence.setScheduleName(schedule.getName());
            occurrence.setDate(row.date()); occurrence.setExercise(row.entry().getExercise());
            occurrence.setCustomExercise(row.entry().getCustomExercise());
            occurrences.save(occurrence);
        }
        return missing.size();
    }

    private ScheduleDeploymentPlanner.Window window(String startDate, String weeks) {
        var window = planner.resolveWindow(Map.of("startDate",startDate,"weeks",weeks,"scope","weeks"));
        if (window == null) throw new IllegalArgumentException("window");
        return window;
    }

    private List<ScheduleEntry> validatedEntries(Schedule schedule) {
        var rows = entries.getEntriesBySchedule(schedule);
        int cycle = ScheduleStudioService.cycleDays(schedule);
        if (cycle < 1 || cycle > 14 || rows.size() > 500) throw new IllegalArgumentException("plan");
        for (var row : rows) {
            if (row.getDayOfWeek() < 1 || row.getDayOfWeek() > cycle
                    || (row.getExercise() == null) == (row.getCustomExercise() == null)
                    || (row.getCustomExercise() != null
                        && !schedule.getUser().getId().equals(row.getCustomExercise().getUserId()))) {
                throw new IllegalArgumentException("plan");
            }
        }
        return rows;
    }
}
