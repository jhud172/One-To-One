package uk.ac.cf._5.group14.One_To_One.StrengthLog.Service;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.Schedule;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntry;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleEntryRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.ExerciseSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.ScheduledWorkoutSessionViewModel;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.SetLog;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.ExerciseSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.SetLogRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ScheduledWorkoutSessionService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final ExerciseSessionRepository exerciseSessionRepository;
    private final SetLogRepository setLogRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;

    public ScheduledWorkoutSessionService(
            WorkoutSessionRepository workoutSessionRepository,
            ExerciseSessionRepository exerciseSessionRepository,
            SetLogRepository setLogRepository,
            ScheduleOccurrenceRepository scheduleOccurrenceRepository,
            ScheduleEntryRepository scheduleEntryRepository
    ) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.exerciseSessionRepository = exerciseSessionRepository;
        this.setLogRepository = setLogRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.scheduleEntryRepository = scheduleEntryRepository;
    }

    @Transactional(readOnly = true)
    public WorkoutSession requireOwnedSession(User user, Long sessionId) {
        return workoutSessionRepository.findByIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout session not found"));
    }

    @Transactional
    public WorkoutSession launchFromSession(User user, Long sessionId) {
        return requireOwnedSession(user, sessionId);
    }

    @Transactional
    public WorkoutSession launchFromOccurrence(User user, Long occurrenceId) {
        ScheduleOccurrence occurrence = scheduleOccurrenceRepository.findByIdAndUserId(occurrenceId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled workout not found"));

        if (occurrence.getSchedule() != null && occurrence.getSchedule().getId() != null) {
            Optional<WorkoutSession> existing = workoutSessionRepository.findByUserAndDateAndScheduleId(
                    user,
                    occurrence.getDate(),
                    occurrence.getSchedule().getId()
            );
            if (existing.isPresent()) {
                return existing.get();
            }

            List<ScheduleOccurrence> groupedOccurrences = scheduleOccurrenceRepository.findByUserAndDateAndScheduleId(
                    user,
                    occurrence.getDate(),
                    occurrence.getSchedule().getId()
            );
            if (groupedOccurrences.isEmpty()) {
                groupedOccurrences = List.of(occurrence);
            }
            return createSessionFromOccurrences(user, occurrence.getDate(), groupedOccurrences, occurrence.getSchedule());
        }

        return workoutSessionRepository.findByUserAndDateAndSourceOccurrenceId(user, occurrence.getDate(), occurrenceId)
                .orElseGet(() -> createSessionFromOccurrences(user, occurrence.getDate(), List.of(occurrence), null));
    }

    @Transactional(readOnly = true)
    public ScheduledWorkoutSessionViewModel buildViewModel(User user, Long sessionId) {
        return toViewModel(requireOwnedSession(user, sessionId));
    }

    @Transactional
    public void addSet(User user, Long sessionId, Long exerciseSessionId) {
        WorkoutSession session = requireOwnedSession(user, sessionId);
        ExerciseSession exerciseSession = requireExerciseSession(session, exerciseSessionId);

        int nextSetNumber = orderedSets(exerciseSession).stream()
                .mapToInt(SetLog::getSetNumber)
                .max()
                .orElse(0) + 1;

        SetLog setLog = new SetLog();
        setLog.setExerciseSession(exerciseSession);
        setLog.setSetNumber(nextSetNumber);
        exerciseSession.getSetLogs().add(setLog);
        exerciseSessionRepository.save(exerciseSession);
    }

    @Transactional
    public void updateSet(User user, Long sessionId, Long setId, Double weight, Integer reps, String notes) {
        updateSet(user, sessionId, setId, weight, reps, notes, null);
    }

    @Transactional
    public void updateSet(User user, Long sessionId, Long setId, Double weight, Integer reps, String notes, Boolean completed) {
        requireOwnedSession(user, sessionId);
        SetLog setLog = requireSetInSession(sessionId, setId);
        if ((weight != null && (!Double.isFinite(weight) || weight < 0)) || (reps != null && reps < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Weight and repetitions must be zero or greater");
        }
        if (notes != null && notes.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Set notes must be 255 characters or fewer");
        }
        setLog.setWeight(weight);
        setLog.setReps(reps);
        setLog.setNotes(blankToNull(notes));
        if (completed != null) {
            setLog.setCompleted(completed);
        }
        setLogRepository.save(setLog);
        rollUpCompletion(setLog.getExerciseSession());
    }

    @Transactional
    public void toggleSet(User user, Long sessionId, Long setId) {
        requireOwnedSession(user, sessionId);
        SetLog setLog = requireSetInSession(sessionId, setId);
        setLog.setCompleted(!setLog.isCompleted());
        setLogRepository.save(setLog);
        rollUpCompletion(setLog.getExerciseSession());
    }

    @Transactional
    public void deleteSet(User user, Long sessionId, Long setId) {
        requireOwnedSession(user, sessionId);
        SetLog setLog = requireSetInSession(sessionId, setId);
        ExerciseSession exerciseSession = setLog.getExerciseSession();
        exerciseSession.getSetLogs().removeIf(existing -> Objects.equals(existing.getId(), setId));
        setLogRepository.delete(setLog);
        renumberSets(exerciseSession);
        rollUpCompletion(exerciseSession);
    }

    @Transactional
    public WorkoutSession completeSession(User user, Long sessionId) {
        WorkoutSession session = requireOwnedSession(user, sessionId);
        session.setCompleted(true);
        workoutSessionRepository.save(session);
        updateLinkedOccurrences(session, true);
        return session;
    }

    @Transactional(readOnly = true)
    public List<LaunchItem> listOpenLaunchItems(User user, LocalDate date) {
        return listLaunchItems(user, date, date, true);
    }

    @Transactional(readOnly = true)
    public List<LaunchItem> listUpcomingLaunchItems(User user, LocalDate from, LocalDate to) {
        return listLaunchItems(user, from, to, false);
    }

    @Transactional(readOnly = true)
    public LaunchItem nextOpenLaunchItem(User user, LocalDate from) {
        return listLaunchItems(user, from, from.plusDays(21), true).stream()
                .filter(item -> item.summary().exerciseCount() > 0)
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public List<HistoryItem> listRecentCompletedSessions(User user, int limit) {
        if (limit <= 0) return List.of();
        return workoutSessionRepository.findByUserAndCompletedTrueOrderByDateDescIdDesc(
                        user, PageRequest.of(0, Math.min(limit, 20))).stream()
                .map(session -> new HistoryItem(
                        session.getId(),
                        resolveSessionName(session),
                        session.getDate(),
                        summaryFor(session),
                        sessionDestinationPath(session)
                ))
                .toList();
    }

    private List<LaunchItem> listLaunchItems(User user, LocalDate from, LocalDate to, boolean openOnly) {
        List<LaunchItem> items = new ArrayList<>();
        Map<String, WorkoutSession> linkedSessions = new LinkedHashMap<>();

        for (WorkoutSession session : workoutSessionRepository.findByUserAndDateBetweenOrderByDateAsc(user, from, to)) {
            if (session.getSchedule() != null && session.getSchedule().getId() != null) {
                linkedSessions.putIfAbsent("schedule:" + session.getSchedule().getId() + ":" + session.getDate(), session);
                continue;
            }
            if (session.getSourceOccurrenceId() != null) {
                linkedSessions.putIfAbsent("occurrence:" + session.getSourceOccurrenceId(), session);
                continue;
            }
            if (openOnly && session.isCompleted()) {
                continue;
            }
            items.add(savedLaunchItem(session));
        }

        Map<String, List<ScheduleOccurrence>> groupedOccurrences = new LinkedHashMap<>();
        for (ScheduleOccurrence occurrence : scheduleOccurrenceRepository.findByUserAndDateBetween(user, from, to)) {
            String key = occurrenceGroupKey(occurrence);
            groupedOccurrences.computeIfAbsent(key, ignored -> new ArrayList<>()).add(occurrence);
        }

        for (List<ScheduleOccurrence> occurrences : groupedOccurrences.values()) {
            if (occurrences.isEmpty()) {
                continue;
            }
            ScheduleOccurrence first = occurrences.get(0);
            WorkoutSession savedSession = linkedSessions.get(occurrenceGroupKey(first));
            boolean completed = occurrences.stream().allMatch(ScheduleOccurrence::isCompleted)
                    || (savedSession != null && savedSession.isCompleted());
            if (openOnly && completed) {
                continue;
            }
            items.add(new LaunchItem(
                    resolveOccurrenceGroupTitle(occurrences),
                    first.getDate(),
                    first.getSchedule() != null ? "Calendar schedule" : "Scheduled workout",
                    completed ? "Completed" : savedSession != null ? "In progress" : "Ready",
                    savedSession != null ? "/workout-session/launch/session/" + savedSession.getId()
                            : "/workout-session/launch/occurrence/" + first.getId(),
                    completed,
                    savedSession != null ? summaryFor(savedSession) : new ScheduledWorkoutSessionViewModel.Summary(
                            occurrences.size(),
                            (int) occurrences.stream().filter(ScheduleOccurrence::isCompleted).count(),
                            0,
                            0,
                            0,
                            0
                    ),
                    savedSession != null,
                    occurrences.stream().anyMatch(item -> item.getTrainerTemplateId() != null)
                            ? "ui.training.sourceCoach" : "ui.training.sourceCalendar",
                    "/calendar/day/" + first.getDate()
            ));
        }

        // A removed calendar source must not hide an existing saved session.
        linkedSessions.forEach((key, session) -> {
            if (!groupedOccurrences.containsKey(key) && (!openOnly || !session.isCompleted())) {
                items.add(savedLaunchItem(session));
            }
        });
        items.sort(Comparator.comparing(LaunchItem::date)
                .thenComparing(item -> item.summary().exerciseCount() == 0)
                .thenComparing(LaunchItem::started, Comparator.reverseOrder()).thenComparing(LaunchItem::title));
        return items;
    }

    private LaunchItem savedLaunchItem(WorkoutSession session) {
        return new LaunchItem(resolveSessionName(session), session.getDate(),
                session.isCompleted() ? "Completed workout" : "Scheduled workout",
                session.isCompleted() ? "Completed" : "In progress",
                "/workout-session/launch/session/" + session.getId(), session.isCompleted(), summaryFor(session),
                true, session.getWorkout() == null ? "ui.training.sourceSaved" : "ui.training.sourceWorkout",
                "/calendar/day/" + session.getDate());
    }

    private WorkoutSession createSessionFromOccurrences(User user, LocalDate date, List<ScheduleOccurrence> occurrences, Schedule schedule) {
        List<ScheduleOccurrence> orderedOccurrences = orderOccurrences(date, occurrences, schedule);
        if (orderedOccurrences.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Scheduled workout has no exercises");
        }
        boolean completed = orderedOccurrences.stream().allMatch(ScheduleOccurrence::isCompleted);

        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setDate(date);
        session.setSchedule(schedule);
        session.setSourceOccurrenceId(orderedOccurrences.get(0).getId());
        session.setNameSnapshot(resolveOccurrenceGroupTitle(orderedOccurrences));
        session.setCompleted(completed);
        session.setCreatedAt(LocalDateTime.now());

        int orderIndex = 0;
        for (ScheduleOccurrence occurrence : orderedOccurrences) {
            ExerciseSession exerciseSession = new ExerciseSession();
            exerciseSession.setWorkoutSession(session);
            exerciseSession.setExercise(occurrence.getExercise());
            exerciseSession.setCustomExercise(occurrence.getCustomExercise());
            exerciseSession.setOrderIndex(orderIndex++);
            exerciseSession.setCompleted(completed);

            SetLog initialSet = new SetLog();
            initialSet.setExerciseSession(exerciseSession);
            initialSet.setSetNumber(1);
            initialSet.setCompleted(completed);
            exerciseSession.getSetLogs().add(initialSet);

            session.getExerciseSessions().add(exerciseSession);
        }

        return workoutSessionRepository.save(session);
    }

    private List<ScheduleOccurrence> orderOccurrences(LocalDate date, List<ScheduleOccurrence> occurrences, Schedule schedule) {
        if (schedule == null || schedule.getId() == null) {
            return occurrences.stream()
                    .sorted(Comparator.comparing(ScheduleOccurrence::getId))
                    .toList();
        }

        List<ScheduleEntry> entries = scheduleEntryRepository.findByScheduleIdOrderByOrderNumber(schedule.getId()).stream()
                .filter(entry -> entry.getDayOfWeek() == date.getDayOfWeek().getValue())
                .toList();
        if (entries.isEmpty()) {
            return occurrences.stream()
                    .sorted(Comparator.comparing(ScheduleOccurrence::getId))
                    .toList();
        }

        Map<String, Deque<ScheduleOccurrence>> byExercise = new LinkedHashMap<>();
        for (ScheduleOccurrence occurrence : occurrences) {
            byExercise.computeIfAbsent(occurrenceExerciseKey(occurrence), ignored -> new ArrayDeque<>()).add(occurrence);
        }

        List<ScheduleOccurrence> ordered = new ArrayList<>();
        for (ScheduleEntry entry : entries) {
            Deque<ScheduleOccurrence> matches = byExercise.get(entryExerciseKey(entry));
            if (matches != null && !matches.isEmpty()) {
                ordered.add(matches.removeFirst());
            }
        }

        occurrences.stream()
                .filter(occurrence -> ordered.stream().noneMatch(existing -> Objects.equals(existing.getId(), occurrence.getId())))
                .sorted(Comparator.comparing(ScheduleOccurrence::getId))
                .forEach(ordered::add);
        return ordered;
    }

    private ExerciseSession requireExerciseSession(WorkoutSession session, Long exerciseSessionId) {
        return exerciseSessionRepository.findByIdAndWorkoutSessionId(exerciseSessionId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise session not found"));
    }

    private SetLog requireSetInSession(Long sessionId, Long setId) {
        return setLogRepository.findByIdAndExerciseSession_WorkoutSession_Id(setId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workout set not found"));
    }

    private void rollUpCompletion(ExerciseSession exerciseSession) {
        renumberSets(exerciseSession);

        boolean allSetsComplete = !exerciseSession.getSetLogs().isEmpty()
                && exerciseSession.getSetLogs().stream().allMatch(SetLog::isCompleted);
        exerciseSession.setCompleted(allSetsComplete);
        exerciseSessionRepository.save(exerciseSession);

        WorkoutSession session = exerciseSession.getWorkoutSession();
        boolean allExercisesComplete = !session.getExerciseSessions().isEmpty()
                && session.getExerciseSessions().stream().allMatch(ExerciseSession::isCompleted);
        session.setCompleted(allExercisesComplete);
        workoutSessionRepository.save(session);
        updateLinkedOccurrences(session, allExercisesComplete);
    }

    private void renumberSets(ExerciseSession exerciseSession) {
        List<SetLog> ordered = orderedSets(exerciseSession);
        int next = 1;
        boolean changed = false;
        for (SetLog setLog : ordered) {
            if (setLog.getSetNumber() != next) {
                setLog.setSetNumber(next);
                changed = true;
            }
            next++;
        }
        if (changed) {
            exerciseSessionRepository.save(exerciseSession);
        }
    }

    private List<SetLog> orderedSets(ExerciseSession exerciseSession) {
        return exerciseSession.getSetLogs().stream()
                .sorted(Comparator.comparingInt(SetLog::getSetNumber))
                .toList();
    }

    private void updateLinkedOccurrences(WorkoutSession session, boolean completed) {
        linkedOccurrences(session).forEach(occurrence -> occurrence.setCompleted(completed));
    }

    private List<ScheduleOccurrence> linkedOccurrences(WorkoutSession session) {
        if (session.getSchedule() != null && session.getSchedule().getId() != null) {
            return scheduleOccurrenceRepository.findByUserAndDateAndScheduleId(
                    session.getUser(),
                    session.getDate(),
                    session.getSchedule().getId()
            );
        }
        if (session.getSourceOccurrenceId() != null) {
            return scheduleOccurrenceRepository.findByIdAndUserId(session.getSourceOccurrenceId(), session.getUser().getId())
                    .map(List::of)
                    .orElse(List.of());
        }
        return List.of();
    }

    private ScheduledWorkoutSessionViewModel toViewModel(WorkoutSession session) {
        List<ExerciseSession> orderedExercises = exerciseSessionRepository.findByWorkoutSessionOrderByOrderIndexAsc(session);
        List<ScheduledWorkoutSessionViewModel.ExerciseView> exerciseViews = orderedExercises.stream()
                .map(this::toExerciseView)
                .toList();

        return new ScheduledWorkoutSessionViewModel(
                session.getId(),
                resolveSessionName(session),
                session.getDate(),
                session.isCompleted(),
                session.getSchedule() != null ? "Calendar schedule" : "Scheduled workout",
                summaryFor(session),
                exerciseViews
        );
    }

    private ScheduledWorkoutSessionViewModel.ExerciseView toExerciseView(ExerciseSession exerciseSession) {
        List<SetLog> orderedSets = orderedSets(exerciseSession);
        List<ScheduledWorkoutSessionViewModel.SetView> setViews = orderedSets.stream()
                .map(setLog -> new ScheduledWorkoutSessionViewModel.SetView(
                        setLog.getId(),
                        setLog.getSetNumber(),
                        setLog.getWeight(),
                        setLog.getReps(),
                        setLog.getNotes(),
                        setLog.isCompleted()
                ))
                .toList();

        return new ScheduledWorkoutSessionViewModel.ExerciseView(
                exerciseSession.getId(),
                resolveExerciseName(exerciseSession),
                resolveExerciseCategory(exerciseSession),
                resolveExerciseType(exerciseSession),
                computeExerciseState(exerciseSession),
                exerciseSession.isCompleted(),
                setViews,
                exerciseSession.getExercise() == null ? null : exerciseSession.getExercise().getId()
        );
    }

    private ScheduledWorkoutSessionViewModel.Summary summaryFor(WorkoutSession session) {
        List<ExerciseSession> orderedExercises = exerciseSessionRepository.findByWorkoutSessionOrderByOrderIndexAsc(session);
        int exerciseCount = orderedExercises.size();
        int completedExercises = (int) orderedExercises.stream().filter(ExerciseSession::isCompleted).count();
        int totalSets = orderedExercises.stream().mapToInt(exercise -> exercise.getSetLogs().size()).sum();
        int completedSets = (int) orderedExercises.stream()
                .flatMap(exercise -> exercise.getSetLogs().stream())
                .filter(SetLog::isCompleted)
                .count();
        double totalVolume = orderedExercises.stream()
                .flatMap(exercise -> exercise.getSetLogs().stream())
                .filter(setLog -> setLog.getWeight() != null && setLog.getReps() != null)
                .mapToDouble(setLog -> setLog.getWeight() * setLog.getReps())
                .sum();
        int completionPercent = totalSets == 0 ? 0 : (int) Math.round((completedSets * 100.0) / totalSets);
        // Rounding a nearly finished session must not imply every set is done.
        if (completedSets < totalSets) completionPercent = Math.min(completionPercent, 99);

        return new ScheduledWorkoutSessionViewModel.Summary(
                exerciseCount,
                completedExercises,
                totalSets,
                completedSets,
                totalVolume,
                completionPercent
        );
    }

    private String resolveSessionName(WorkoutSession session) {
        if (session.getNameSnapshot() != null && !session.getNameSnapshot().isBlank()) {
            return session.getNameSnapshot();
        }
        if (session.getWorkout() != null && session.getWorkout().getName() != null && !session.getWorkout().getName().isBlank()) {
            return session.getWorkout().getName();
        }
        if (session.getSchedule() != null && session.getSchedule().getName() != null && !session.getSchedule().getName().isBlank()) {
            return session.getSchedule().getName();
        }
        return "Scheduled workout";
    }

    private String resolveOccurrenceGroupTitle(List<ScheduleOccurrence> occurrences) {
        ScheduleOccurrence first = occurrences.get(0);
        if (first.getSchedule() != null && first.getSchedule().getName() != null && !first.getSchedule().getName().isBlank()) {
            return first.getSchedule().getName();
        }
        if (first.getScheduleName() != null && !first.getScheduleName().isBlank()) {
            return first.getScheduleName();
        }
        return resolveOccurrenceTitle(first);
    }

    private String resolveOccurrenceTitle(ScheduleOccurrence occurrence) {
        if (occurrence.getExercise() != null && occurrence.getExercise().getName() != null) {
            return occurrence.getExercise().getName();
        }
        if (occurrence.getCustomExercise() != null && occurrence.getCustomExercise().getName() != null) {
            return occurrence.getCustomExercise().getName();
        }
        return "Scheduled workout";
    }

    private String resolveExerciseName(ExerciseSession exerciseSession) {
        if (exerciseSession.getExercise() != null && exerciseSession.getExercise().getName() != null) {
            return exerciseSession.getExercise().getName();
        }
        if (exerciseSession.getCustomExercise() != null && exerciseSession.getCustomExercise().getName() != null) {
            return exerciseSession.getCustomExercise().getName();
        }
        return "Exercise";
    }

    private String resolveExerciseCategory(ExerciseSession exerciseSession) {
        if (exerciseSession.getExercise() != null && exerciseSession.getExercise().getCategory() != null) {
            return exerciseSession.getExercise().getCategory();
        }
        if (exerciseSession.getCustomExercise() != null && exerciseSession.getCustomExercise().getCategory() != null) {
            return exerciseSession.getCustomExercise().getCategory();
        }
        return "Workout";
    }

    private String resolveExerciseType(ExerciseSession exerciseSession) {
        if (exerciseSession.getExercise() != null && exerciseSession.getExercise().getType() != null) {
            return exerciseSession.getExercise().getType();
        }
        if (exerciseSession.getCustomExercise() != null && exerciseSession.getCustomExercise().getType() != null) {
            return exerciseSession.getCustomExercise().getType();
        }
        return null;
    }

    private String computeExerciseState(ExerciseSession exerciseSession) {
        if (exerciseSession.getSetLogs() == null || exerciseSession.getSetLogs().isEmpty()) {
            return "Not started";
        }
        boolean allComplete = exerciseSession.getSetLogs().stream().allMatch(SetLog::isCompleted);
        if (allComplete) {
            return "Completed";
        }
        boolean anyComplete = exerciseSession.getSetLogs().stream().anyMatch(SetLog::isCompleted);
        return anyComplete ? "In progress" : "Not started";
    }

    private String occurrenceGroupKey(ScheduleOccurrence occurrence) {
        if (occurrence.getSchedule() != null && occurrence.getSchedule().getId() != null) {
            return "schedule:" + occurrence.getSchedule().getId() + ":" + occurrence.getDate();
        }
        return "occurrence:" + occurrence.getId();
    }

    private String entryExerciseKey(ScheduleEntry entry) {
        if (entry.getExercise() != null && entry.getExercise().getId() != null) {
            return "exercise:" + entry.getExercise().getId();
        }
        if (entry.getCustomExercise() != null && entry.getCustomExercise().getId() != null) {
            return "custom:" + entry.getCustomExercise().getId();
        }
        return "entry:" + entry.getId();
    }

    private String occurrenceExerciseKey(ScheduleOccurrence occurrence) {
        if (occurrence.getExercise() != null && occurrence.getExercise().getId() != null) {
            return "exercise:" + occurrence.getExercise().getId();
        }
        if (occurrence.getCustomExercise() != null && occurrence.getCustomExercise().getId() != null) {
            return "custom:" + occurrence.getCustomExercise().getId();
        }
        return "occurrence:" + occurrence.getId();
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String sessionDestinationPath(WorkoutSession session) {
        return session.isCompleted()
                ? "/workout-session/" + session.getId() + "/complete"
                : "/workout-session/" + session.getId();
    }

    public record LaunchItem(
            String title,
            LocalDate date,
            String sourceLabel,
            String statusLabel,
            String launchUrl,
            boolean completed,
            ScheduledWorkoutSessionViewModel.Summary summary,
            boolean started,
            String sourceKey,
            String sourceUrl
    ) {
    }

    public record HistoryItem(
            Long sessionId,
            String title,
            LocalDate date,
            ScheduledWorkoutSessionViewModel.Summary summary,
            String href
    ) {
    }
}
