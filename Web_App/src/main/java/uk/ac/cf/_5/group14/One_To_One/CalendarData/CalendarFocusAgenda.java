package uk.ac.cf._5.group14.One_To_One.CalendarData;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;

/** Read-only presentation of the same owned items used by the day planner. */
public record CalendarFocusAgenda(List<Item> pending, List<Item> completed, int total, int percentage) {
    public Item next() {
        return pending.isEmpty() ? null : pending.getFirst();
    }

    public static CalendarFocusAgenda from(List<CalendarTask> tasks, List<WorkoutSession> sessions,
            List<ScheduleOccurrence> occurrences, Map<String, String> slots) {
        var items = new ArrayList<Item>();
        for (var task : tasks) {
            items.add(new Item("task", task.getId(), task.getTitle(), task.getTime(),
                    Boolean.TRUE.equals(task.getCompleted()), "/calendar/task/" + task.getId(), task.getNotes()));
        }
        for (var session : sessions) {
            var title = session.getNameSnapshot();
            if ((title == null || title.isBlank()) && session.getWorkout() != null) {
                title = session.getWorkout().getName();
            }
            items.add(new Item("session", session.getId(), title, readTime(slots.get("workout:" + session.getId())),
                    session.isCompleted(), "/workout-session/launch/session/" + session.getId(), null));
        }
        for (var occurrence : occurrences) {
            var title = occurrence.getExercise() != null ? occurrence.getExercise().getName()
                    : occurrence.getCustomExercise() != null ? occurrence.getCustomExercise().getName() : null;
            items.add(new Item("occurrence", occurrence.getId(), title,
                    readTime(slots.get("occurrence:" + occurrence.getId())), occurrence.isCompleted(),
                    "/workout-session/launch/occurrence/" + occurrence.getId(), null));
        }
        items.sort(Comparator.comparing(Item::time, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::key));
        var pending = items.stream().filter(item -> !item.completed()).toList();
        var completed = items.stream().filter(Item::completed).toList();
        var percentage = items.isEmpty() ? 0 : (int) Math.round(100.0 * completed.size() / items.size());
        return new CalendarFocusAgenda(pending, completed, items.size(), percentage);
    }

    private static LocalTime readTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record Item(String kind, Long id, String title, LocalTime time, boolean completed,
            String href, String notes) {
        public String key() {
            return kind + "-" + id;
        }

        public String typeKey() {
            return switch (kind) {
                case "task" -> "ui.01637";
                case "session" -> "ui.01652";
                default -> "ui.01654";
            };
        }
    }
}
