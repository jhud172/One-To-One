package uk.ac.cf._5.group14.One_To_One.CalendarData;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class CalendarTaskWarningService {

    private final CalendarTaskWarningRepository warningRepository;

    public CalendarTaskWarningService(CalendarTaskWarningRepository warningRepository) {
        this.warningRepository = warningRepository;
    }

    @Transactional(readOnly = true)
    public List<CalendarTaskWarning> listWarningsForTask(Long taskId) {
        if (taskId == null) return List.of();
        return warningRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    @Transactional(readOnly = true)
    public Map<Long, List<CalendarTaskWarning>> listWarningsForTasks(Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) return Map.of();

        List<CalendarTaskWarning> warnings = warningRepository.findByTaskIdIn(taskIds);
        if (warnings == null || warnings.isEmpty()) return Map.of();

        Map<Long, List<CalendarTaskWarning>> byTaskId = new HashMap<>();
        for (CalendarTaskWarning w : warnings) {
            if (w == null || w.getTask() == null || w.getTask().getId() == null) continue;
            byTaskId.computeIfAbsent(w.getTask().getId(), k -> new ArrayList<>()).add(w);
        }

        for (List<CalendarTaskWarning> list : byTaskId.values()) {
            list.sort(Comparator.comparing(CalendarTaskWarning::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        return byTaskId;
    }

    @Transactional
    public void deleteWarningsReferencingTask(Long taskId) {
        if (taskId == null) return;
        warningRepository.deleteByTaskId(taskId);
        warningRepository.deleteByTriggerTaskId(taskId);
    }

    @Transactional
    public CalendarTaskWarning addTimeWarning(CalendarTask task, LocalTime triggerTime) {
        if (task == null || task.getId() == null || triggerTime == null) {
            return null;
        }

        for (CalendarTaskWarning existing : listWarningsForTask(task.getId())) {
            if (existing.getTriggerType() == CalendarTaskWarningTriggerType.TIME && triggerTime.equals(existing.getTriggerTime())) return existing;
        }
        CalendarTaskWarning warning = new CalendarTaskWarning();
        warning.setTask(task);
        warning.setTriggerType(CalendarTaskWarningTriggerType.TIME);
        warning.setTriggerTime(triggerTime);
        return warningRepository.save(warning);
    }

    @Transactional
    public CalendarTaskWarning addOnTaskCompleteWarning(CalendarTask task, CalendarTask triggerTask) {
        if (task == null || task.getId() == null || triggerTask == null || triggerTask.getId() == null) {
            return null;
        }
        if (task.getUser() == null || triggerTask.getUser() == null) {
            return null;
        }
        if (task.getUser().getId() == null || !task.getUser().getId().equals(triggerTask.getUser().getId())) {
            return null;
        }
        if (task.getId().equals(triggerTask.getId())) {
            return null;
        }

        for (CalendarTaskWarning existing : listWarningsForTask(task.getId())) {
            if (existing.getTriggerType() == CalendarTaskWarningTriggerType.ON_TASK_COMPLETE
                    && existing.getTriggerTask() != null && triggerTask.getId().equals(existing.getTriggerTask().getId())) return existing;
        }
        CalendarTaskWarning warning = new CalendarTaskWarning();
        warning.setTask(task);
        warning.setTriggerType(CalendarTaskWarningTriggerType.ON_TASK_COMPLETE);
        warning.setTriggerTask(triggerTask);
        return warningRepository.save(warning);
    }

    @Transactional
    public boolean deleteWarningForTask(Long warningId, CalendarTask task) {
        if (warningId == null || task == null || task.getId() == null) return false;
        CalendarTaskWarning warning = warningRepository.findById(warningId).orElse(null);
        if (warning == null || warning.getTask() == null || !task.getId().equals(warning.getTask().getId())) return false;
        warningRepository.delete(warning);
        return true;
    }

    @Transactional
    public void onTaskCompleted(CalendarTask completedTask, Instant now) {
        if (completedTask == null || completedTask.getId() == null || now == null) return;

        List<CalendarTaskWarning> warnings = warningRepository.findByTriggerTaskIdAndTriggeredAtIsNull(completedTask.getId());
        if (warnings == null || warnings.isEmpty()) return;

        for (CalendarTaskWarning w : warnings) {
            w.setTriggeredAt(now);
        }
        warningRepository.saveAll(warnings);
    }

    /**
     * Computes and sets transient flags (inGrace/late) for the provided tasks, and lazily marks
     * TIME warnings as triggered once their trigger time has passed.
     */
    @Transactional
    public void applyWarningStates(List<CalendarTask> tasks, Instant now) {
        if (tasks == null || tasks.isEmpty() || now == null) return;

        List<Long> ids = tasks.stream()
                .map(CalendarTask::getId)
                .filter(Objects::nonNull)
                .toList();

        if (ids.isEmpty()) return;

        List<CalendarTaskWarning> warnings = warningRepository.findByTaskIdIn(ids);
        Map<Long, List<CalendarTaskWarning>> warningsByTask = new HashMap<>();
        if (warnings != null) {
            for (CalendarTaskWarning w : warnings) {
                if (w == null || w.getTask() == null || w.getTask().getId() == null) continue;
                warningsByTask.computeIfAbsent(w.getTask().getId(), k -> new ArrayList<>()).add(w);
            }
        }

        ZoneId zone = ZoneId.systemDefault();
        List<CalendarTaskWarning> toUpdate = new ArrayList<>();

        for (CalendarTask task : tasks) {
            if (task == null || task.getId() == null) continue;

            task.setInGrace(false);
            task.setLate(false);

            if (Boolean.TRUE.equals(task.getCompleted())) {
                continue;
            }

            List<CalendarTaskWarning> tw = warningsByTask.getOrDefault(task.getId(), List.of());
            Instant earliest = null;

            for (CalendarTaskWarning w : tw) {
                if (w == null || w.getTriggerType() == null) continue;

                Instant trigger = w.getTriggeredAt();

                if (trigger == null && w.getTriggerType() == CalendarTaskWarningTriggerType.TIME) {
                    LocalTime t = w.getTriggerTime();
                    if (t != null && task.getDate() != null) {
                        Instant scheduled = LocalDateTime.of(task.getDate(), t).atZone(zone).toInstant();
                        if (!now.isBefore(scheduled)) {
                            w.setTriggeredAt(scheduled);
                            toUpdate.add(w);
                            trigger = scheduled;
                        }
                    }
                }

                if (trigger != null && !now.isBefore(trigger) && (earliest == null || trigger.isBefore(earliest))) {
                    earliest = trigger;
                }
            }

            Integer graceMins = task.getGracePeriodMinutes();
            if (earliest == null || graceMins == null || graceMins <= 0) {
                continue;
            }

            Instant graceEnd = earliest.plus(Duration.ofMinutes(graceMins));
            if (now.isBefore(graceEnd)) {
                task.setInGrace(true);
            } else {
                task.setLate(true);
            }
        }

        if (!toUpdate.isEmpty()) {
            warningRepository.saveAll(toUpdate);
        }
    }
}
