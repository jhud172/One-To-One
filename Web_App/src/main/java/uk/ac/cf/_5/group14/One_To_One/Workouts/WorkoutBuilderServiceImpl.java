package uk.ac.cf._5.group14.One_To_One.Workouts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class WorkoutBuilderServiceImpl implements WorkoutBuilderService {

    private final WorkoutTemplateRepository templateRepository;
    private final WorkoutSessionRepository sessionRepository;
    private final WorkoutSetLogRepository setLogRepository;
    private final WorkoutPerformanceService workoutPerformanceService;
    private final uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository exerciseRepository;
    private final uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExerciseRepository;

    public WorkoutBuilderServiceImpl(WorkoutTemplateRepository templateRepository,
                                     WorkoutSessionRepository sessionRepository,
                                     WorkoutSetLogRepository setLogRepository,
                                     WorkoutPerformanceService workoutPerformanceService,
                                     uk.ac.cf._5.group14.One_To_One.ExerciseData.ExerciseRepository exerciseRepository,
                                     uk.ac.cf._5.group14.One_To_One.CustomExerciseData.CustomExerciseRepository customExerciseRepository) {
        this.templateRepository = templateRepository;
        this.sessionRepository = sessionRepository;
        this.setLogRepository = setLogRepository;
        this.workoutPerformanceService = workoutPerformanceService;
        this.exerciseRepository = exerciseRepository;
        this.customExerciseRepository = customExerciseRepository;
    }

    @Override
    public List<WorkoutTemplate> listTemplates(User user) {
        return templateRepository.findByOwnerUserOrderByUpdatedAtDesc(user);
    }

    @Override
    public WorkoutTemplate createTemplate(User user, WorkoutTemplateForm form) {
        WorkoutTemplate template = new WorkoutTemplate();
        template.setOwnerUser(user);
        template.setOwnerRole(user.getRole() == null ? Role.CLIENT : user.getRole());
        applyTemplateForm(user, template, form);
        return templateRepository.save(template);
    }

    @Override
    public WorkoutTemplate updateTemplate(User user, Long templateId, WorkoutTemplateForm form) {
        WorkoutTemplate template = getTemplate(user, templateId);
        applyTemplateForm(user, template, form);
        return templateRepository.save(template);
    }

    @Override
    public WorkoutTemplate getTemplate(User user, Long templateId) {
        return templateRepository.findByIdAndOwnerUser(templateId, user)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Override
    public void deleteTemplate(User user, Long templateId) {
        WorkoutTemplate template = getTemplate(user, templateId);
        if (sessionRepository.existsByTemplate(template)) {
            throw new IllegalArgumentException("ui.studio.deleteBlocked");
        }
        templateRepository.delete(template);
    }

    @Override
    public java.util.Optional<WorkoutSession> findOpenSession(User user, Long templateId) {
        return sessionRepository.findFirstByUserAndTemplateAndCompletedFalseOrderByStartedAtDescIdDesc(user, getTemplate(user, templateId));
    }

    @Override
    public WorkoutSession startSession(User user, Long templateId) {
        WorkoutTemplate template = getTemplate(user, templateId);
        var existing = sessionRepository.findFirstByUserAndTemplateAndCompletedFalseOrderByStartedAtDescIdDesc(user, template);
        if (existing.isPresent()) return existing.get();
        if (template.getExercises().isEmpty()) {
            throw new IllegalArgumentException("ui.studio.emptyExercise");
        }
        WorkoutSession session = new WorkoutSession();
        session.setUser(user);
        session.setTemplate(template);
        session.setNameSnapshot(template.getName());
        session.setStartedAt(LocalDateTime.now());
        session.setCompleted(false);
        session.setTotalVolume(0.0);

        List<WorkoutExercise> ordered = new ArrayList<>(template.getExercises());
        ordered.sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex));
        for (WorkoutExercise exercise : ordered) {
            int targetSets = Math.max(1, exercise.getSets());
            for (int i = 1; i <= targetSets; i++) {
                WorkoutSetLog log = new WorkoutSetLog();
                log.setSession(session);
                log.setExerciseName(exercise.getExerciseName());
                log.setExerciseOrder(exercise.getOrderIndex());
                log.setSetNumber(i);
                log.setTargetReps(exercise.getReps());
                log.setRestSeconds(exercise.getRestSeconds());
                log.setNotes(exercise.getNotes());
                session.getSetLogs().add(log);
            }
        }

        return sessionRepository.save(session);
    }

    @Override
    public WorkoutSession getSession(User user, Long sessionId) {
        return sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }

    @Override
    public WorkoutSetLog updateSet(User user, Long sessionId, Long setId, WorkoutSetUpdateRequest request) {
        WorkoutSession session = getSession(user, sessionId);
        WorkoutSetLog setLog = setLogRepository.findByIdAndSession(setId, session)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));

        boolean wasCompleted = setLog.isCompleted();
        Double previousWeight = setLog.getWeight();
        Integer previousReps = setLog.getReps();

        if (request == null) {
            return setLog;
        }

        if ((request.getWeight() != null && (!Double.isFinite(request.getWeight()) || request.getWeight() < 0))
                || (request.getReps() != null && request.getReps() < 0)
                || (request.getNotes() != null && request.getNotes().length() > 500)) {
            throw new IllegalArgumentException("ui.personal.invalid");
        }
        // Validate the full proposed rollup before changing a managed entity.
        double proposedVolume = 0;
        for (WorkoutSetLog candidate : session.getSetLogs()) {
            boolean changed = candidate.getId().equals(setId);
            boolean completed = changed && request.getCompleted() != null ? request.getCompleted() : candidate.isCompleted();
            Double weight = changed && (request.isReplaceValues() || request.getWeight() != null) ? request.getWeight() : candidate.getWeight();
            Integer reps = changed && (request.isReplaceValues() || request.getReps() != null) ? request.getReps() : candidate.getReps();
            if (completed && weight != null && reps != null) proposedVolume += weight * reps;
        }
        if (!Double.isFinite(proposedVolume)) throw new IllegalArgumentException("ui.personal.invalid");

        if (request.isReplaceValues() || request.getWeight() != null) {
            setLog.setWeight(request.getWeight());
        }
        if (request.isReplaceValues() || request.getReps() != null) {
            setLog.setReps(request.getReps());
        }
        if (request.getNotes() != null) {
            setLog.setNotes(request.getNotes());
        }
        if (request.getCompleted() != null) {
            setLog.setCompleted(request.getCompleted());
        }

        setLogRepository.save(setLog);
        rollupSession(session);

        boolean weightChanged = !Objects.equals(previousWeight, setLog.getWeight());
        boolean repsChanged = !Objects.equals(previousReps, setLog.getReps());
        boolean completionChanged = request.getCompleted() != null && request.getCompleted() && !wasCompleted;
        boolean shouldEvaluate = completionChanged || weightChanged || repsChanged;
        workoutPerformanceService.maybeNotifyPr(user, session, setLog, shouldEvaluate);
        return setLog;
    }

    private void applyTemplateForm(User user, WorkoutTemplate template, WorkoutTemplateForm form) {
        if (form == null || trimToNull(form.getName()) == null || form.getName().length() > 200
                || (form.getDescription() != null && form.getDescription().length() > 600)
                || (form.getExercises() != null && form.getExercises().size() > 50)) {
            throw new IllegalArgumentException("ui.studio.invalid");
        }
        // Resolve and validate every row before changing a persisted template or orphaned children.
        var existingRows = new HashMap<Long, WorkoutExercise>();
        template.getExercises().forEach(exercise -> existingRows.put(exercise.getId(), exercise));
        var submittedIds = new HashSet<Long>();
        List<PreparedExercise> resolved = new ArrayList<>();
        for (WorkoutExerciseForm row : form.getExercises() == null ? List.<WorkoutExerciseForm>of() : form.getExercises()) {
            if (row == null) continue;
            WorkoutExercise existing = null;
            if (row.getId() != null) {
                existing = existingRows.get(row.getId());
                if (existing == null || !submittedIds.add(row.getId())) throw new IllegalArgumentException("ui.studio.reference");
            }
            String resolvedName = trimToNull(row.getExerciseName());
            Long exerciseId = null;
            Long customId = null;
            String ref = trimToNull(row.getExerciseRef());
            if (ref != null) {
                if (!ref.matches("[ec]:[1-9][0-9]*")) throw new IllegalArgumentException("ui.studio.reference");
                Long id;
                try { id = Long.parseLong(ref.substring(2)); }
                catch (NumberFormatException exception) { throw new IllegalArgumentException("ui.studio.reference"); }
                if (ref.startsWith("e:")) {
                    resolvedName = exerciseRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("ui.studio.reference")).getName();
                    exerciseId = id;
                } else {
                    resolvedName = customExerciseRepository.findByIdAndUserId(id, user.getId())
                            .orElseThrow(() -> new IllegalArgumentException("ui.studio.reference")).getName();
                    customId = id;
                }
            }
            if (resolvedName == null) {
                if (trimToNull(row.getNotes()) != null) throw new IllegalArgumentException("ui.studio.invalid");
                continue; // An untouched row can remain an honest empty draft.
            }
            if (resolvedName.length() > 200 || (row.getNotes() != null && row.getNotes().length() > 500)
                    || !within(row.getSets(), 1, 50) || !within(row.getReps(), 1, 1000)
                    || !within(row.getRestSeconds(), 0, 3600)) throw new IllegalArgumentException("ui.studio.limits");
            WorkoutExercise exercise = new WorkoutExercise();
            exercise.setTemplate(template);
            exercise.setExerciseName(resolvedName);
            exercise.setExerciseId(exerciseId);
            exercise.setCustomExerciseId(customId);
            exercise.setSets(safeInt(row.getSets(), 3));
            exercise.setReps(safeInt(row.getReps(), 10));
            exercise.setRestSeconds(row.getRestSeconds() == null ? 60 : row.getRestSeconds());
            exercise.setNotes(trimToNull(row.getNotes()));
            exercise.setOrderIndex(resolved.size());
            resolved.add(new PreparedExercise(exercise, existing));
        }
        template.setName(form.getName().trim());
        template.setDescription(trimToNull(form.getDescription()));
        List<WorkoutExercise> retained = new ArrayList<>();
        for (PreparedExercise prepared : resolved) {
            WorkoutExercise values = prepared.values();
            WorkoutExercise exercise = prepared.existing() == null ? values : prepared.existing();
            if (prepared.existing() != null) {
                exercise.setExerciseName(values.getExerciseName());
                exercise.setExerciseId(values.getExerciseId());
                exercise.setCustomExerciseId(values.getCustomExerciseId());
                exercise.setSets(values.getSets());
                exercise.setReps(values.getReps());
                exercise.setRestSeconds(values.getRestSeconds());
                exercise.setNotes(values.getNotes());
                exercise.setOrderIndex(values.getOrderIndex());
            }
            retained.add(exercise);
        }
        template.getExercises().removeIf(exercise -> !retained.contains(exercise));
        for (WorkoutExercise exercise : retained) {
            if (!template.getExercises().contains(exercise)) template.getExercises().add(exercise);
        }
        template.getExercises().sort(Comparator.comparingInt(WorkoutExercise::getOrderIndex));
    }

    private record PreparedExercise(WorkoutExercise values, WorkoutExercise existing) { }

    private boolean within(Integer value, int min, int max) { return value == null || (value >= min && value <= max); }

    private void rollupSession(WorkoutSession session) {
        boolean allCompleted = !session.getSetLogs().isEmpty() && session.getSetLogs().stream().allMatch(WorkoutSetLog::isCompleted);
        session.setCompleted(allCompleted);
        if (allCompleted && session.getCompletedAt() == null) {
            session.setCompletedAt(LocalDateTime.now());
        }
        if (!allCompleted) session.setCompletedAt(null);

        double volume = 0.0;
        for (WorkoutSetLog log : session.getSetLogs()) {
            if (!log.isCompleted()) {
                continue;
            }
            if (log.getWeight() != null && log.getReps() != null) {
                volume += log.getWeight() * log.getReps();
            }
        }
        session.setTotalVolume(volume);
        sessionRepository.save(session);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private int safeInt(Integer value, int fallback) {
        if (value == null || value < 1) {
            return fallback;
        }
        return value;
    }

}
