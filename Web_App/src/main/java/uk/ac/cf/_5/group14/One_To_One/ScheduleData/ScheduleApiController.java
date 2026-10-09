package uk.ac.cf._5.group14.One_To_One.ScheduleData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleDeploymentPlanner.Window;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleDeploymentPlanner.PlannedOccurrence;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.Security.CurrentUser;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleApiController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleApiController.class);
    private static final long UNDO_WINDOW_SECONDS = 30;
    private final ConcurrentMap<String, UndoOperation> undoOperations = new ConcurrentHashMap<>();

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private ScheduleDeploymentPlanner deploymentPlanner;

    @Autowired
    private ScheduleEntryService scheduleEntryService;

    @Autowired
    private ScheduleOccurrenceRepository scheduleOccurrenceRepository;

    @Autowired
    private ScheduleAppliedRepository scheduleAppliedRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CustomExerciseRepository customExerciseRepository;

    @Autowired
    private CalendarTaskRepository calendarTaskRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private ScheduleCopyService scheduleCopyService;

    /**
     * Get schedule metadata (frequency, active days, etc.)
     */
    @GetMapping("/{id}/metadata")
    public ResponseEntity<Map<String, Object>> getScheduleMetadata(
            @PathVariable Long id,
            @CurrentUser(required = false) User user) {
        
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        
        Schedule schedule = scheduleService.findById(id);
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }

        // Check access rights - only owner can access for now
        if (schedule.getUser() == null || !schedule.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> metadata = calculateMetadata(schedule);
        return ResponseEntity.ok(metadata);
    }

    /**
     * Get metadata for multiple schedules in a single request (batch)
     */
    @GetMapping("/metadata/batch")
    public ResponseEntity<Map<String, Map<String, Object>>> getBatchMetadata(
            @RequestParam List<Long> ids,
            @CurrentUser(required = false) User user) {
        
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Map<String, Object>> result = new HashMap<>();
        
        for (Long id : ids) {
            Schedule schedule = scheduleService.findById(id);
            if (schedule != null && schedule.getUser() != null && 
                schedule.getUser().getId().equals(user.getId())) {
                result.put(id.toString(), calculateMetadata(schedule));
            }
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * Calculate metadata for a schedule
     */
    private Map<String, Object> calculateMetadata(Schedule schedule) {
        List<ScheduleEntry> entries = scheduleEntryService.getEntriesBySchedule(schedule);
        int cycleDays=Math.min(14,Math.max(1,ScheduleStudioService.cycleDays(schedule)));

        java.util.TreeSet<Integer> activeDaySet = new java.util.TreeSet<>();
        for (ScheduleEntry entry : entries) {
            if (entry == null) continue;
            int day = entry.getDayOfWeek();
            if (day >= 1 && day <= cycleDays) {
                activeDaySet.add(day);
            }
        }
        List<Integer> activeDayIndexes = new java.util.ArrayList<>(activeDaySet);

        List<String> activeDayLabels = activeDayIndexes.stream()
            .map(day -> schedule.getScheduleType()!=ScheduleType.WEEKLY ? "Day " + day : switch (day) {
                case 1 -> "Mon";
                case 2 -> "Tue";
                case 3 -> "Wed";
                case 4 -> "Thu";
                case 5 -> "Fri";
                case 6 -> "Sat";
                case 7 -> "Sun";
                default -> "Day " + day;
            })
            .collect(Collectors.toList());
        
        // Calculate metadata
        long activeDaysCount = activeDayIndexes.size();
        
        int totalExercises = entries.size();
        int maxConsecutiveDays = 0;
        if (!activeDayIndexes.isEmpty()) {
            List<Integer> doubled = new java.util.ArrayList<>(activeDayIndexes);
            doubled.addAll(activeDayIndexes.stream().map(day -> day + cycleDays).toList());
            int streak = 1;
            for (int i = 1; i < doubled.size(); i++) {
                if (doubled.get(i) - doubled.get(i - 1) == 1) {
                    streak += 1;
                    maxConsecutiveDays = Math.max(maxConsecutiveDays, streak);
                } else {
                    streak = 1;
                }
            }
            maxConsecutiveDays = Math.min(cycleDays, Math.max(maxConsecutiveDays, 1));
        }

        boolean noRestDays = activeDaysCount >= cycleDays;

        boolean imbalancedStructure = false;
        if (activeDayIndexes.size() >= 3) {
            int largestGap = 0;
            for (int i = 0; i < activeDayIndexes.size(); i++) {
                int current = activeDayIndexes.get(i);
                int nextBase = (i + 1 < activeDayIndexes.size()) ? activeDayIndexes.get(i + 1) : activeDayIndexes.get(0);
                int next = (i + 1 < activeDayIndexes.size()) ? nextBase : nextBase + cycleDays;
                largestGap = Math.max(largestGap, next - current - 1);
            }
            imbalancedStructure = largestGap >= 3;
        }

        List<Map<String, String>> healthWarnings = new java.util.ArrayList<>();
        if (noRestDays) {
            healthWarnings.add(Map.of(
                    "key", "no-rest-days",
                    "label", "Every cycle day has movements",
                    "description", "This plan has movements on all " + cycleDays + " cycle days."
            ));
        }
        if (maxConsecutiveDays >= 5) {
            healthWarnings.add(Map.of(
                    "key", "long-streak",
                    "label", "Consecutive planned days",
                    "description", "This plan has movements on " + maxConsecutiveDays + " consecutive cycle days."
            ));
        }
        if (imbalancedStructure) {
            healthWarnings.add(Map.of(
                    "key", "imbalanced",
                    "label", "Gap between planned days",
                    "description", "This plan has a gap of at least three cycle days without movements."
            ));
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionsPerWeek", activeDaysCount);
        metadata.put("activeDays", activeDaysCount);
        metadata.put("activeDayIndexes", activeDayIndexes);
        metadata.put("activeDayLabels", activeDayLabels);
        metadata.put("totalExercises", totalExercises);
        metadata.put("restDays", Math.max(0, cycleDays - activeDaysCount));
        metadata.put("cycleDayCount",cycleDays);
        metadata.put("scheduleType",schedule.getScheduleType());
        metadata.put("rotationMode",schedule.getRotationMode());
        metadata.put("noRestDays", noRestDays);
        metadata.put("maxConsecutiveDays", maxConsecutiveDays);
        metadata.put("imbalancedStructure", imbalancedStructure);
        metadata.put("healthWarnings", healthWarnings);
        
        return metadata;
    }

    /**
     * Get schedule preview with weekly structure
     */
    @GetMapping("/{id}/preview")
    public ResponseEntity<Map<String, Object>> getSchedulePreview(
            @PathVariable Long id,
            @CurrentUser(required = false) User user) {
        
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        
        Schedule schedule = scheduleService.findById(id);
        if (schedule == null) {
            return ResponseEntity.notFound().build();
        }

        // Check access rights - only owner can access for now
        if (schedule.getUser() == null || !schedule.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        List<ScheduleEntry> entries = scheduleEntryService.getEntriesBySchedule(schedule);
        
        Map<String, Object> preview = new HashMap<>();
        preview.put("id", schedule.getId());
        preview.put("name", schedule.getName());
        preview.put("description", schedule.getDescription());
        
        // Convert entries to a simple format for the preview
        List<Map<String, Object>> entryMaps = entries.stream()
                .map(entry -> {
                    Map<String, Object> entryMap = new HashMap<>();
                    entryMap.put("dayOfWeek", entry.getDayOfWeek());
                    entryMap.put("orderNumber", entry.getOrderNumber());
                    
                    if (entry.getExercise() != null) {
                        Map<String, String> exercise = new HashMap<>();
                        exercise.put("name", entry.getExercise().getName());
                        entryMap.put("exercise", exercise);
                    }
                    
                    if (entry.getCustomExercise() != null) {
                        Map<String, String> customExercise = new HashMap<>();
                        customExercise.put("name", entry.getCustomExercise().getName());
                        entryMap.put("customExercise", customExercise);
                    }
                    
                    return entryMap;
                })
                .collect(Collectors.toList());
        
        preview.put("entries", entryMaps);
        
        return ResponseEntity.ok(preview);
    }

    /**
     * Duplicate a schedule
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Map<String, Object>> duplicateSchedule(
            @PathVariable Long id,
            @CurrentUser(required = false) User user) {
        
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        
        Schedule original = scheduleService.findById(id);
        if (original == null) {
            return ResponseEntity.notFound().build();
        }

        // Check access rights - can only duplicate own schedules
        if (original.getUser() == null || !original.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        Schedule duplicate = scheduleCopyService.copy(original, user);

        Map<String, Object> response = new HashMap<>();
        response.put("id", duplicate.getId());
        response.put("name", duplicate.getName());
        response.put("success", true);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deployment/impact")
    public ResponseEntity<Map<String, Object>> deploymentImpact(
            @PathVariable Long id,
            @CurrentUser(required = false) User user,
            @RequestBody Map<String, Object> request) {

        Schedule schedule = validateOwnedSchedule(id, user);
        if (schedule == null) {
            if (user == null) return ResponseEntity.status(401).build();
            return ResponseEntity.status(403).build();
        }

        Window window = deploymentPlanner.resolveWindow(request);
        if (window == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid deployment window"));
        }

        String strategy = normalizeStrategy(request.get("strategy") instanceof String value ? value : null);
        ImpactComputation impact = computeImpact(schedule, user, window, strategy);
        return ResponseEntity.ok(impact.toMap());
    }

    @PostMapping("/{id}/deployment/apply")
    @Transactional
    public ResponseEntity<Map<String, Object>> applyDeployment(
            @PathVariable Long id,
            @CurrentUser(required = false) User user,
            @RequestBody Map<String, Object> request) {

        Schedule schedule = user == null ? null : scheduleRepository.findOwnedForDeployment(id, user.getId()).orElse(null);
        if (schedule == null) {
            if (user == null) return ResponseEntity.status(401).build();
            return ResponseEntity.status(403).build();
        }

        Window window = deploymentPlanner.resolveWindow(request);
        if (window == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid deployment window"));
        }

        String strategy = normalizeStrategy(request.get("strategy") instanceof String value ? value : null);
        ImpactComputation impact = computeImpact(schedule, user, window, strategy, true);

        Set<LocalDate> conflictDays = new HashSet<>(impact.conflictDates);
        undoOperations.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(java.time.Instant.now()));
        List<ScheduleOccurrenceSnapshot> removedSnapshots = new ArrayList<>();
        if ("replace".equals(strategy) && !impact.replaceableConflicts.isEmpty()) {
            for (ScheduleOccurrence existing : impact.replaceableConflicts) {
                if (protectedOccurrence(existing)) continue;
                removedSnapshots.add(ScheduleOccurrenceSnapshot.from(existing));
            }
            scheduleOccurrenceRepository.deleteAll(impact.replaceableConflicts);
        }

        int created = 0;
        List<Long> createdOccurrenceIds = new ArrayList<>();
        for (PlannedOccurrence planned : impact.plannedOccurrences) {
            if ("skip".equals(strategy) && conflictDays.contains(planned.date())) {
                continue;
            }

            ScheduleOccurrence occ = new ScheduleOccurrence();
            occ.setUser(user);
            occ.setSchedule(schedule);
            occ.setScheduleName(schedule.getName());
            occ.setDate(planned.date());
            if (planned.entry().getExercise() != null) {
                occ.setExercise(planned.entry().getExercise());
            }
            if (planned.entry().getCustomExercise() != null) {
                occ.setCustomExercise(planned.entry().getCustomExercise());
            }
            if (occ.getExercise() == null && occ.getCustomExercise() == null) {
                continue;
            }

            ScheduleOccurrence saved = scheduleOccurrenceRepository.save(occ);
            if (saved.getId() != null) {
                createdOccurrenceIds.add(saved.getId());
            }
            created += 1;
        }

        if (created == 0 && removedSnapshots.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "created", 0, "replaced", 0,
                    "skipped", impact.skippedByStrategy, "alreadyScheduled", impact.alreadyScheduled,
                    "windowStart", window.start().toString(), "windowEnd", window.end().toString(), "strategy", strategy));
        }

        ScheduleApplied applied = new ScheduleApplied();
        applied.setSchedule(schedule);
        applied.setUser(user);
        applied.setDateApplied(window.start());
        applied.setShownOnCalendar(true);
        applied.setRequiresLogging(false);
        applied.setDurationWeeks(window.weeks());
        scheduleAppliedRepository.save(applied);

        String undoToken = UUID.randomUUID().toString();
        undoOperations.put(
            undoToken,
            new UndoOperation(
                user.getId(),
                schedule.getId(),
                applied.getId(),
                createdOccurrenceIds,
                removedSnapshots,
                java.time.Instant.now().plusSeconds(UNDO_WINDOW_SECONDS)
            )
        );

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("created", created);
        response.put("replaced", removedSnapshots.size());
        response.put("alreadyScheduled", impact.alreadyScheduled);
        response.put("skipped", "skip".equals(strategy) ? impact.skippedByStrategy : 0);
        response.put("conflictDates", impact.conflictDates.stream().map(LocalDate::toString).toList());
        response.put("windowStart", window.start().toString());
        response.put("windowEnd", window.end().toString());
        response.put("strategy", strategy);
        response.put("undoToken", undoToken);
        response.put("undoExpiresInSeconds", UNDO_WINDOW_SECONDS);

        LOGGER.info(
            "schedule_deploy_applied userId={} scheduleId={} scope={} strategy={} start={} end={} created={} replaced={} skipped={} conflicts={}",
            user.getId(),
            schedule.getId(),
            window.scope(),
            strategy,
            window.start(),
            window.end(),
            created,
            removedSnapshots.size(),
            "skip".equals(strategy) ? impact.skippedByStrategy : 0,
            impact.conflictDates.size()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deployment/undo")
    @Transactional
    public ResponseEntity<Map<String, Object>> undoDeployment(
            @PathVariable Long id,
            @CurrentUser(required = false) User user,
            @RequestBody Map<String, Object> request) {

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String undoToken = request.get("undoToken") instanceof String token ? token : null;
        if (undoToken == null || undoToken.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing undo token"));
        }

        UndoOperation operation = undoOperations.get(undoToken);
        if (operation == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Undo token is invalid or expired"));
        }

        if (operation.expiresAt().isBefore(java.time.Instant.now())) {
            undoOperations.remove(undoToken, operation);
            return ResponseEntity.badRequest().body(Map.of("error", "Undo window expired"));
        }

        if (!Objects.equals(operation.userId(), user.getId()) || !Objects.equals(operation.scheduleId(), id)) {
            return ResponseEntity.status(403).build();
        }

        if (scheduleRepository.findOwnedForDeployment(id, user.getId()).isEmpty()) {
            return ResponseEntity.status(403).build();
        }
        List<ScheduleOccurrence> createdOccurrences = operation.createdOccurrenceIds().isEmpty() ? List.of()
                : scheduleOccurrenceRepository.findOwnedForUndo(user.getId(), operation.createdOccurrenceIds());
        if (createdOccurrences.stream().anyMatch(ScheduleApiController::protectedOccurrence)
                || (!operation.createdOccurrenceIds().isEmpty() && workoutSessionRepository.existsByUserAndSourceOccurrenceIdIn(user, operation.createdOccurrenceIds()))) {
            return ResponseEntity.status(409).body(Map.of("error", "A deployed workout has been completed or linked to a training record; undo would remove its history"));
        }
        if (!undoOperations.remove(undoToken, operation)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Undo token has already been used"));
        }

        if (operation.appliedId() != null) {
            scheduleAppliedRepository.findById(operation.appliedId()).ifPresent((applied) -> {
                if (applied.getUser() != null && Objects.equals(applied.getUser().getId(), user.getId())) {
                    scheduleAppliedRepository.delete(applied);
                }
            });
        }

        if (!operation.createdOccurrenceIds().isEmpty()) {
            createdOccurrences = createdOccurrences.stream()
                    .filter(occ -> occ.getUser() != null && Objects.equals(occ.getUser().getId(), user.getId()))
                    .toList();
            if (!createdOccurrences.isEmpty()) {
                scheduleOccurrenceRepository.deleteAll(createdOccurrences);
            }
        }

        if (!operation.removedSnapshots().isEmpty()) {
            for (ScheduleOccurrenceSnapshot snapshot : operation.removedSnapshots()) {
                ScheduleOccurrence restored = snapshot.toOccurrence(user, scheduleService, exerciseRepository, customExerciseRepository);
                scheduleOccurrenceRepository.save(restored);
            }
        }

        LOGGER.info(
                "schedule_deploy_undo userId={} scheduleId={} restoredOccurrences={} removedCreatedOccurrences={}",
                user.getId(),
                id,
                operation.removedSnapshots().size(),
                operation.createdOccurrenceIds().size()
        );

        return ResponseEntity.ok(Map.of("success", true));
    }

    private Schedule validateOwnedSchedule(Long id, User user) {
        if (user == null) return null;
        Schedule schedule = scheduleService.findById(id);
        if (schedule == null || schedule.getUser() == null) return null;
        if (!Objects.equals(schedule.getUser().getId(), user.getId())) return null;
        return schedule;
    }

    private String normalizeStrategy(String strategy) {
        if (strategy == null) return "merge";
        String normalized = strategy.trim().toLowerCase();
        if ("replace".equals(normalized) || "skip".equals(normalized)) {
            return normalized;
        }
        return "merge";
    }

    private ImpactComputation computeImpact(Schedule schedule, User user, Window window, String strategy) {
        return computeImpact(schedule, user, window, strategy, false);
    }

    private ImpactComputation computeImpact(Schedule schedule, User user, Window window, String strategy, boolean lockOccurrences) {
        List<ScheduleEntry> entries = scheduleEntryService.getEntriesBySchedule(schedule);
        List<PlannedOccurrence> plannedOccurrences = deploymentPlanner.plan(window, entries);

        Set<LocalDate> plannedDates = plannedOccurrences.stream().map(PlannedOccurrence::date).collect(Collectors.toSet());

        List<ScheduleOccurrence> existingRange = plannedDates.isEmpty()
                ? List.of()
                : (lockOccurrences ? scheduleOccurrenceRepository.findOwnedRangeForDeployment(user, window.start(), window.end())
                        : scheduleOccurrenceRepository.findByUserAndDateBetween(user, window.start(), window.end())).stream()
                    .filter(occ -> plannedDates.contains(occ.getDate()))
                    .toList();

        List<CalendarTask> taskConflicts = plannedDates.isEmpty()
            ? List.of()
            : calendarTaskRepository.findByUserAndDateBetween(user, window.start(), window.end()).stream()
                .filter(task -> task != null && task.getDate() != null && plannedDates.contains(task.getDate()))
                .toList();

        List<WorkoutSession> sessions = plannedDates.isEmpty() ? List.of()
                : workoutSessionRepository.findByUserAndDateBetweenOrderByDateAsc(user, window.start(), window.end()).stream()
                    .filter(session -> plannedDates.contains(session.getDate())).toList();
        Set<Long> linkedOccurrenceIds = sessions.stream().map(WorkoutSession::getSourceOccurrenceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> existingIds = existingRange.stream().map(ScheduleOccurrence::getId).collect(Collectors.toSet());
        List<WorkoutSession> sessionConflicts = sessions.stream()
                .filter(session -> session.getSourceOccurrenceId() == null || !existingIds.contains(session.getSourceOccurrenceId())).toList();
        long protectedEntries = existingRange.stream()
                .filter(occ -> protectedOccurrence(occ) || linkedOccurrenceIds.contains(occ.getId())).count() + sessionConflicts.size();

        Map<LocalDate, Long> conflictsByDate = existingRange.stream()
                .collect(Collectors.groupingBy(ScheduleOccurrence::getDate, Collectors.counting()));

        Map<LocalDate, Long> taskConflictsByDate = taskConflicts.stream()
            .collect(Collectors.groupingBy(CalendarTask::getDate, Collectors.counting()));

        for (Map.Entry<LocalDate, Long> entry : taskConflictsByDate.entrySet()) {
            conflictsByDate.merge(entry.getKey(), entry.getValue(), (left, right) -> left + right);
        }

        for (WorkoutSession session : sessionConflicts) {
            conflictsByDate.merge(session.getDate(), 1L, Long::sum);
        }
        List<LocalDate> conflictDates = conflictsByDate.keySet().stream().sorted().toList();

        Set<Long> matchingIds = deploymentPlanner.matchingOccurrenceIds(plannedOccurrences, existingRange, schedule.getId());
        List<ScheduleOccurrence> replaceable = existingRange.stream()
                .filter(occ -> !protectedOccurrence(occ) && !linkedOccurrenceIds.contains(occ.getId()) && !matchingIds.contains(occ.getId())).toList();
        List<ScheduleOccurrence> retained = existingRange.stream()
                .filter(occ -> !"replace".equals(strategy) || !replaceable.contains(occ)).toList();
        List<PlannedOccurrence> additions = deploymentPlanner.missingOccurrences(plannedOccurrences, retained, schedule.getId());
        long alreadyScheduled = plannedOccurrences.size() - additions.size();
        plannedOccurrences = additions;

        long skippedByStrategy = 0;
        if ("skip".equals(strategy) && !conflictDates.isEmpty()) {
            Set<LocalDate> conflictDaySet = new HashSet<>(conflictDates);
            skippedByStrategy = plannedOccurrences.stream()
                    .filter(p -> conflictDaySet.contains(p.date()))
                    .count();
        }

        return new ImpactComputation(window, strategy, plannedOccurrences, existingRange, taskConflicts, conflictDates, conflictsByDate, skippedByStrategy, alreadyScheduled, replaceable, sessionConflicts.size(), protectedEntries);
    }
    
    private static boolean protectedOccurrence(ScheduleOccurrence occurrence) {
        return occurrence.isCompleted() || occurrence.isMissed() || occurrence.getExerciseLog() != null;
    }

    private static final class ImpactComputation {
        private final Window window;
        private final String strategy;
        private final List<PlannedOccurrence> plannedOccurrences;
        private final List<ScheduleOccurrence> existingConflicts;
        private final List<CalendarTask> taskConflicts;
        private final List<LocalDate> conflictDates;
        private final Map<LocalDate, Long> conflictsByDate;
        private final long skippedByStrategy;
        private final long alreadyScheduled;
        private final List<ScheduleOccurrence> replaceableConflicts;
        private final int sessionConflicts;
        private final long protectedEntries;

        private ImpactComputation(
                Window window,
                String strategy,
                List<PlannedOccurrence> plannedOccurrences,
                List<ScheduleOccurrence> existingConflicts,
                List<CalendarTask> taskConflicts,
                List<LocalDate> conflictDates,
                Map<LocalDate, Long> conflictsByDate,
                long skippedByStrategy,
                long alreadyScheduled,
                List<ScheduleOccurrence> replaceableConflicts,
                int sessionConflicts,
                long protectedEntries
        ) {
            this.window = window;
            this.strategy = strategy;
            this.plannedOccurrences = plannedOccurrences;
            this.existingConflicts = existingConflicts;
            this.taskConflicts = taskConflicts;
            this.conflictDates = conflictDates;
            this.conflictsByDate = conflictsByDate;
            this.skippedByStrategy = skippedByStrategy;
            this.alreadyScheduled = alreadyScheduled;
            this.replaceableConflicts = replaceableConflicts;
            this.sessionConflicts = sessionConflicts;
            this.protectedEntries = protectedEntries;
        }

        private Map<String, Object> toMap() {
            long added = plannedOccurrences.size();
            long replaced = "replace".equals(strategy) ? replaceableConflicts.size() : 0;
            long skipped = "skip".equals(strategy) ? skippedByStrategy : 0;

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("plannedEntries", plannedOccurrences.size() + alreadyScheduled);
            summary.put("alreadyScheduled", alreadyScheduled);
            summary.put("protectedEntries", protectedEntries);
            summary.put("sessionConflicts", sessionConflicts);
            summary.put("added", Math.max(0, added - skipped));
            summary.put("replaced", replaced);
            summary.put("skipped", skipped);
            summary.put("existingConflicts", existingConflicts.size());
            summary.put("taskConflicts", taskConflicts.size());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("windowStart", window.start().toString());
            response.put("windowEnd", window.end().toString());
            response.put("scope", window.scope());
            response.put("weeks", window.weeks());
            response.put("strategy", strategy);
            response.put("summary", summary);
            Set<LocalDate> skippedDates = "skip".equals(strategy) ? new HashSet<>(conflictDates) : Set.of();
            response.put("plannedOccurrences", plannedOccurrences.stream()
                    .filter(planned -> !skippedDates.contains(planned.date()))
                    .map(planned -> Map.of("date", planned.date().toString(), "entryId", planned.entry().getId(),
                            "name", planned.entry().getCustomExercise() != null ? planned.entry().getCustomExercise().getName()
                                    : planned.entry().getExercise().getName())).toList());
            response.put("conflictDates", conflictDates.stream().map(LocalDate::toString).toList());
            response.put(
                    "conflictsByDate",
                    conflictsByDate.entrySet().stream().collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue
                    ))
            );

            return response;
        }
    }

    private record UndoOperation(
            Long userId,
            Long scheduleId,
            Long appliedId,
            List<Long> createdOccurrenceIds,
            List<ScheduleOccurrenceSnapshot> removedSnapshots,
            java.time.Instant expiresAt
    ) {}

    private record ScheduleOccurrenceSnapshot(
            LocalDate date,
            Long scheduleId,
            Long exerciseId,
            Long customExerciseId,
            String scheduleName,
            boolean completed,
            boolean missed,
            java.time.Instant missedAt,
            Long trainerTemplateId,
            Long trainerTemplateEntryId
    ) {
        private static ScheduleOccurrenceSnapshot from(ScheduleOccurrence occurrence) {
            return new ScheduleOccurrenceSnapshot(
                    occurrence.getDate(),
                    occurrence.getSchedule() != null ? occurrence.getSchedule().getId() : null,
                    occurrence.getExercise() != null ? occurrence.getExercise().getId() : null,
                    occurrence.getCustomExercise() != null ? occurrence.getCustomExercise().getId() : null,
                    occurrence.getScheduleName(),
                    occurrence.isCompleted(),
                    occurrence.isMissed(),
                    occurrence.getMissedAt(),
                    occurrence.getTrainerTemplateId(),
                    occurrence.getTrainerTemplateEntryId()
            );
        }

        private ScheduleOccurrence toOccurrence(
            User user,
            ScheduleService scheduleService,
            ExerciseRepository exerciseRepository,
            CustomExerciseRepository customExerciseRepository
        ) {
            ScheduleOccurrence occurrence = new ScheduleOccurrence();
            occurrence.setUser(user);
            Schedule schedule = scheduleId != null ? scheduleService.findById(scheduleId) : null;
            occurrence.setSchedule(schedule);
            occurrence.setScheduleName(scheduleName != null ? scheduleName : (schedule != null ? schedule.getName() : "Schedule"));
            occurrence.setDate(date);
            occurrence.setCompleted(completed);
            occurrence.setMissed(missed);
            occurrence.setMissedAt(missedAt);
            occurrence.setTrainerTemplateId(trainerTemplateId);
            occurrence.setTrainerTemplateEntryId(trainerTemplateEntryId);

            if (exerciseId != null) {
                exerciseRepository.findById(exerciseId).ifPresent(occurrence::setExercise);
            }
            if (customExerciseId != null) {
                customExerciseRepository.findById(customExerciseId).ifPresent(occurrence::setCustomExercise);
            }
            return occurrence;
        }
    }
}
