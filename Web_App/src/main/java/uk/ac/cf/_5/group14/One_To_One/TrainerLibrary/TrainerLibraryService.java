package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Security.TrainerAccessException;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;

import java.util.*;

@Service
public class TrainerLibraryService {

    public static final String ERROR_TRAINER_NOT_VERIFIED = "TRAINER_NOT_VERIFIED";

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final TrainerLibraryExerciseRepository exerciseRepository;
    private final TrainerLibraryExerciseNoteRepository exerciseNoteRepository;
    private final TrainerLibraryWorkoutTemplateRepository workoutTemplateRepository;
    private final TrainerLibraryWorkoutItemRepository workoutItemRepository;
    private final TrainerLibraryWorkoutNoteRepository workoutNoteRepository;
    private final TrainerLibraryProgrammeTemplateRepository programmeTemplateRepository;
    private final TrainerLibraryProgrammeDayRepository programmeDayRepository;
    private final TrainerLibraryProgrammeNoteRepository programmeNoteRepository;
    private final TrainerLibrarySharedTemplateRepository sharedTemplateRepository;
    private final TrainerClientLinkRepository trainerClientLinkRepository;
    private final UserRepository userRepository;

    public TrainerLibraryService(TrainerLibraryExerciseRepository exerciseRepository,
                                TrainerLibraryExerciseNoteRepository exerciseNoteRepository,
                                TrainerLibraryWorkoutTemplateRepository workoutTemplateRepository,
                                TrainerLibraryWorkoutItemRepository workoutItemRepository,
                                TrainerLibraryWorkoutNoteRepository workoutNoteRepository,
                                TrainerLibraryProgrammeTemplateRepository programmeTemplateRepository,
                                TrainerLibraryProgrammeDayRepository programmeDayRepository,
                                TrainerLibraryProgrammeNoteRepository programmeNoteRepository,
                                TrainerLibrarySharedTemplateRepository sharedTemplateRepository,
                                TrainerClientLinkRepository trainerClientLinkRepository,
                                UserRepository userRepository) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseNoteRepository = exerciseNoteRepository;
        this.workoutTemplateRepository = workoutTemplateRepository;
        this.workoutItemRepository = workoutItemRepository;
        this.workoutNoteRepository = workoutNoteRepository;
        this.programmeTemplateRepository = programmeTemplateRepository;
        this.programmeDayRepository = programmeDayRepository;
        this.programmeNoteRepository = programmeNoteRepository;
        this.sharedTemplateRepository = sharedTemplateRepository;
        this.trainerClientLinkRepository = trainerClientLinkRepository;
        this.userRepository = userRepository;
    }

    public List<TrainerLibraryExercise> listExercises(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        return exerciseRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId);
    }

    public List<TrainerLibraryWorkoutTemplate> listWorkouts(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        return workoutTemplateRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId);
    }

    public Map<Long, Long> getWorkoutItemCounts(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] count : workoutItemRepository.countOwnedWorkoutItems(trainerId)) {
            counts.put(((Number) count[0]).longValue(), ((Number) count[1]).longValue());
        }
        return counts;
    }

    public Map<Long, Long> getWorkoutItemCounts(Long trainerId, List<Long> workoutIds) {
        requireVerifiedTrainer(trainerId);
        if (workoutIds.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] count : workoutItemRepository.countOwnedWorkoutItemsOnPage(trainerId, workoutIds)) {
            counts.put(((Number) count[0]).longValue(), ((Number) count[1]).longValue());
        }
        return counts;
    }

    public List<TrainerLibraryProgrammeTemplate> listProgrammes(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        return programmeTemplateRepository.findByTrainerIdOrderByCreatedAtDesc(trainerId);
    }

    @Transactional(readOnly = true)
    public ExerciseCatalogue searchExercises(Long trainerId, String rawQuery, int requestedPage) {
        requireVerifiedTrainer(trainerId);
        String query = libraryQuery(rawQuery);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id");
        var page = exerciseRepository.searchOwned(trainerId, pattern,
                org.springframework.data.domain.PageRequest.of(Math.clamp(requestedPage, 0, 9999), 18, sort));
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            page = exerciseRepository.searchOwned(trainerId, pattern,
                    org.springframework.data.domain.PageRequest.of(page.getTotalPages() - 1, 18, sort));
        }
        if (page.getTotalElements() == 0 && page.getNumber() > 0) {
            page = org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 18, sort));
        }
        return new ExerciseCatalogue(query, page, exerciseRepository.countByTrainerId(trainerId));
    }

    static String libraryQuery(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        return query.length() > 120 ? query.substring(0, 120) : query;
    }

    public record ExerciseCatalogue(String query, org.springframework.data.domain.Page<TrainerLibraryExercise> page,
                                    long ownedCount) { }

    @Transactional(readOnly = true)
    public WorkoutCatalogue searchWorkouts(Long trainerId, String rawQuery, int requestedPage) {
        requireVerifiedTrainer(trainerId);
        String query = libraryQuery(rawQuery);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id");
        var page = workoutTemplateRepository.searchOwned(trainerId, pattern,
                org.springframework.data.domain.PageRequest.of(Math.clamp(requestedPage, 0, 9999), 18, sort));
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            page = workoutTemplateRepository.searchOwned(trainerId, pattern,
                    org.springframework.data.domain.PageRequest.of(page.getTotalPages() - 1, 18, sort));
        }
        if (page.getTotalElements() == 0 && page.getNumber() > 0) {
            page = org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 18, sort));
        }
        return new WorkoutCatalogue(query, page, workoutTemplateRepository.countByTrainerId(trainerId));
    }

    public record WorkoutCatalogue(String query, org.springframework.data.domain.Page<TrainerLibraryWorkoutTemplate> page,
                                   long ownedCount) { }

    @Transactional(readOnly = true)
    public ProgrammeCatalogue searchProgrammes(Long trainerId, String rawQuery, int requestedPage) {
        requireVerifiedTrainer(trainerId);
        String query = libraryQuery(rawQuery);
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt", "id");
        var page = programmeTemplateRepository.findByTrainerIdAndTitleContainingIgnoreCase(trainerId, query,
                org.springframework.data.domain.PageRequest.of(Math.clamp(requestedPage, 0, 9999), 18, sort));
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            page = programmeTemplateRepository.findByTrainerIdAndTitleContainingIgnoreCase(trainerId, query,
                    org.springframework.data.domain.PageRequest.of(page.getTotalPages() - 1, 18, sort));
        }
        if (page.getTotalElements() == 0 && page.getNumber() > 0) {
            page = org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 18, sort));
        }
        return new ProgrammeCatalogue(query, page, programmeTemplateRepository.countByTrainerId(trainerId));
    }

    public record ProgrammeCatalogue(String query, org.springframework.data.domain.Page<TrainerLibraryProgrammeTemplate> page,
                                     long ownedCount) { }

    public Map<Long, Long> getProgrammeDayCounts(Long trainerId, List<Long> programmeIds) {
        requireVerifiedTrainer(trainerId);
        if (programmeIds.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] count : programmeDayRepository.countOwnedProgrammeDaysOnPage(trainerId, programmeIds)) {
            counts.put(((Number) count[0]).longValue(), ((Number) count[1]).longValue());
        }
        return counts;
    }

    public Map<Long, Long> getProgrammeDayCounts(Long trainerId) {
        requireVerifiedTrainer(trainerId);
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] count : programmeDayRepository.countOwnedProgrammeDays(trainerId)) {
            counts.put(((Number) count[0]).longValue(), ((Number) count[1]).longValue());
        }
        return counts;
    }

    @Transactional
    public TrainerLibraryExercise createExercise(Long trainerId, TrainerLibraryExerciseForm form) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryExercise exercise = new TrainerLibraryExercise(trainerId);
        applyExerciseForm(exercise, form);
        exercise = exerciseRepository.save(exercise);
        replaceExerciseNotes(exercise.getId(), form.getNotesText());
        return exercise;
    }

    @Transactional
    public TrainerLibraryExercise updateExercise(Long trainerId, Long exerciseId, TrainerLibraryExerciseForm form) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryExercise exercise = exerciseRepository.findByIdAndTrainerId(exerciseId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        applyExerciseForm(exercise, form);
        exercise = exerciseRepository.save(exercise);
        replaceExerciseNotes(exercise.getId(), form.getNotesText());
        return exercise;
    }

    public TrainerLibraryExercise getExerciseOwned(Long trainerId, Long exerciseId) {
        requireVerifiedTrainer(trainerId);
        return exerciseRepository.findByIdAndTrainerId(exerciseId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
    }

    public List<TrainerLibraryExerciseNote> getExerciseNotes(Long exerciseId) {
        return exerciseNoteRepository.findByExerciseIdOrderByIdAsc(exerciseId);
    }

    @Transactional
    public void deleteExercise(Long trainerId, Long exerciseId) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryExercise exercise = exerciseRepository.findOwnedForUpdate(exerciseId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        if (workoutItemRepository.existsByExerciseId(exerciseId)) throw new IllegalArgumentException("Exercise is used in a workout");
        exerciseNoteRepository.deleteByExerciseId(exerciseId);
        sharedTemplateRepository.deleteByTrainerIdAndTemplateTypeAndTemplateId(trainerId, TrainerLibraryTemplateType.EXERCISE, exerciseId);
        exerciseRepository.delete(exercise);
        exerciseRepository.flush();
    }

    @Transactional
    public TrainerLibraryWorkoutTemplate createWorkout(Long trainerId, TrainerLibraryWorkoutTemplateForm form) {
        requireVerifiedTrainer(trainerId);
        validateWorkoutForm(form);
        TrainerLibraryWorkoutTemplate wt = new TrainerLibraryWorkoutTemplate(trainerId);
        wt.setTitle(form.getTitle());
        wt.setSummary(form.getSummary());
        wt = workoutTemplateRepository.save(wt);
        replaceWorkoutNotes(wt.getId(), form.getNotesText());
        return wt;
    }

    @Transactional
    public TrainerLibraryWorkoutTemplate updateWorkout(Long trainerId, Long workoutId, TrainerLibraryWorkoutTemplateForm form) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryWorkoutTemplate wt = workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        entityManager.refresh(wt, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        validateWorkoutForm(form);
        if (!Objects.equals(form.getExpectedRevision(), workoutRevision(wt, getWorkoutNotes(workoutId)))) {
            throw new TrainerLibraryRevisionConflictException();
        }
        wt.setTitle(form.getTitle());
        wt.setSummary(form.getSummary());
        wt = workoutTemplateRepository.save(wt);
        replaceWorkoutNotes(wt.getId(), form.getNotesText());
        return wt;
    }

    @Transactional
    public String getWorkoutRevision(Long trainerId, Long workoutId) {
        return getWorkoutMetadataSnapshot(trainerId, workoutId).revision();
    }

    @Transactional
    public WorkoutMetadataSnapshot getWorkoutMetadataSnapshot(Long trainerId, Long workoutId) {
        requireVerifiedTrainer(trainerId);
        var workout = workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        entityManager.refresh(workout, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var notes = List.copyOf(getWorkoutNotes(workoutId));
        return new WorkoutMetadataSnapshot(workout, notes, workoutRevision(workout, notes));
    }

    public record WorkoutMetadataSnapshot(TrainerLibraryWorkoutTemplate workout,
                                          List<TrainerLibraryWorkoutNote> notes, String revision) { }

    private String workoutRevision(TrainerLibraryWorkoutTemplate workout, List<TrainerLibraryWorkoutNote> notes) {
        // Length prefixes make arbitrary user text unambiguous without changing the database schema.
        var snapshot = new StringBuilder();
        appendRevisionField(snapshot, workout.getTitle());
        appendRevisionField(snapshot, workout.getSummary());
        notes.forEach(note -> appendRevisionField(snapshot, note.getNoteText()));
        return metadataRevision(snapshot);
    }

    private String metadataRevision(StringBuilder snapshot) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void appendRevisionField(StringBuilder snapshot, String value) {
        snapshot.append(value == null ? -1 : value.length()).append(':');
        if (value != null) snapshot.append(value);
    }

    public TrainerLibraryWorkoutTemplate getWorkoutOwned(Long trainerId, Long workoutId) {
        requireVerifiedTrainer(trainerId);
        return workoutTemplateRepository.findByIdAndTrainerId(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
    }

    public List<TrainerLibraryWorkoutItem> getWorkoutItems(Long workoutId) {
        return workoutItemRepository.findByWorkoutIdOrderByOrderIndexAsc(workoutId);
    }

    public List<TrainerLibraryWorkoutNote> getWorkoutNotes(Long workoutId) {
        return workoutNoteRepository.findByWorkoutIdOrderByIdAsc(workoutId);
    }

    @Transactional
    public TrainerLibraryWorkoutItem addWorkoutItem(Long trainerId, Long workoutId, TrainerLibraryWorkoutItemForm form) {
        requireVerifiedTrainer(trainerId);
        workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        if (form.getExerciseId() == null || form.getSets() == null || form.getSets() < 1
                || form.getReps() == null || form.getReps() < 1 || form.getRestSeconds() == null || form.getRestSeconds() < 0
                || (form.getRpe() != null && (form.getRpe() < 1 || form.getRpe() > 10))) {
            throw new IllegalArgumentException("Invalid prescription");
        }
        Integer position = form.getOrderIndex();
        if (position == null) {
            int last = workoutItemRepository.findByWorkoutIdOrderByOrderIndexAsc(workoutId).stream()
                    .mapToInt(TrainerLibraryWorkoutItem::getOrderIndex).max().orElse(-1);
            if (last == Integer.MAX_VALUE) throw new IllegalArgumentException("No available position");
            position = last + 1;
        }
        if (position < 0 || workoutItemRepository.existsByWorkoutIdAndOrderIndex(workoutId, position)) {
            throw new IllegalArgumentException("Position is already occupied");
        }

        TrainerLibraryExercise exercise = exerciseRepository.findById(form.getExerciseId())
                .orElseThrow(() -> new IllegalArgumentException("Exercise not found"));
        if (!Objects.equals(exercise.getTrainerId(), trainerId)) {
            throw new AccessDeniedException("Exercise not owned");
        }

        TrainerLibraryWorkoutItem item = new TrainerLibraryWorkoutItem();
        item.setWorkoutId(workoutId);
        item.setExerciseId(form.getExerciseId());
        item.setSets(form.getSets());
        item.setReps(form.getReps());
        item.setRestSeconds(form.getRestSeconds());
        item.setRpe(form.getRpe());
        item.setOrderIndex(position);
        return workoutItemRepository.save(item);
    }

    @Transactional
    public void deleteWorkoutItem(Long trainerId, Long workoutId, Long itemId) {
        requireVerifiedTrainer(trainerId);
        workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        TrainerLibraryWorkoutItem item = workoutItemRepository.findById(itemId)
                .orElseThrow(() -> new AccessDeniedException("Item not found"));
        if (!Objects.equals(item.getWorkoutId(), workoutId)) {
            throw new AccessDeniedException("Wrong workout");
        }
        workoutItemRepository.delete(item);
    }

    @Transactional
    public boolean moveWorkoutItem(Long trainerId, Long workoutId, Long itemId, String direction) {
        requireVerifiedTrainer(trainerId);
        workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        var items = workoutItemRepository.findByWorkoutIdOrderByOrderIndexAsc(workoutId);
        int current = -1;
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(items.get(index).getId(), itemId)) current = index;
        }
        if (current < 0) throw new AccessDeniedException("Wrong workout");
        int offset = switch (direction == null ? "" : direction) {
            case "UP" -> -1;
            case "DOWN" -> 1;
            default -> throw new IllegalArgumentException("Invalid direction");
        };
        int target = current + offset;
        if (target < 0 || target >= items.size()) return false;
        var item = items.get(current);
        var neighbour = items.get(target);
        int previous = item.getOrderIndex();
        int next = neighbour.getOrderIndex();
        var occupied = new HashSet<Integer>();
        items.forEach(row -> occupied.add(row.getOrderIndex()));
        int temporary = 0;
        while (occupied.contains(temporary)) temporary++;
        // Flush each leg through a free non-negative position to preserve the SQL unique constraint.
        item.setOrderIndex(temporary);
        workoutItemRepository.flush();
        neighbour.setOrderIndex(previous);
        workoutItemRepository.flush();
        item.setOrderIndex(next);
        workoutItemRepository.flush();
        return true;
    }

    @Transactional
    public void deleteWorkout(Long trainerId, Long workoutId) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryWorkoutTemplate wt = workoutTemplateRepository.findOwnedForUpdate(workoutId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        if (programmeDayRepository.existsByWorkoutId(workoutId)) throw new IllegalArgumentException("Workout is used in a programme");
        workoutNoteRepository.deleteByWorkoutId(workoutId);
        workoutItemRepository.deleteByWorkoutId(workoutId);
        sharedTemplateRepository.deleteByTrainerIdAndTemplateTypeAndTemplateId(trainerId, TrainerLibraryTemplateType.WORKOUT, workoutId);
        workoutTemplateRepository.delete(wt);
        workoutTemplateRepository.flush();
    }

    @Transactional
    public TrainerLibraryProgrammeTemplate createProgramme(Long trainerId, TrainerLibraryProgrammeTemplateForm form) {
        requireVerifiedTrainer(trainerId);
        validateProgrammeForm(form);
        TrainerLibraryProgrammeTemplate pt = new TrainerLibraryProgrammeTemplate(trainerId);
        pt.setTitle(form.getTitle());
        pt.setWeeks(form.getWeeks());
        pt = programmeTemplateRepository.save(pt);
        replaceProgrammeNotes(pt.getId(), form.getNotesText());
        return pt;
    }

    @Transactional
    public TrainerLibraryProgrammeTemplate updateProgramme(Long trainerId, Long programmeId, TrainerLibraryProgrammeTemplateForm form) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryProgrammeTemplate pt = programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        entityManager.refresh(pt, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        validateProgrammeForm(form);
        if (!Objects.equals(form.getExpectedRevision(), programmeRevision(pt, getProgrammeNotes(programmeId)))) {
            throw new TrainerLibraryRevisionConflictException();
        }
        pt.setTitle(form.getTitle());
        pt.setWeeks(form.getWeeks());
        pt = programmeTemplateRepository.save(pt);
        replaceProgrammeNotes(pt.getId(), form.getNotesText());
        return pt;
    }

    @Transactional
    public String getProgrammeRevision(Long trainerId, Long programmeId) {
        return getProgrammeMetadataSnapshot(trainerId, programmeId).revision();
    }

    @Transactional
    public ProgrammeMetadataSnapshot getProgrammeMetadataSnapshot(Long trainerId, Long programmeId) {
        requireVerifiedTrainer(trainerId);
        var programme = programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        entityManager.refresh(programme, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var notes = List.copyOf(getProgrammeNotes(programmeId));
        return new ProgrammeMetadataSnapshot(programme, notes, programmeRevision(programme, notes));
    }

    public record ProgrammeMetadataSnapshot(TrainerLibraryProgrammeTemplate programme,
                                            List<TrainerLibraryProgrammeNote> notes, String revision) { }

    private String programmeRevision(TrainerLibraryProgrammeTemplate programme, List<TrainerLibraryProgrammeNote> notes) {
        var snapshot = new StringBuilder();
        appendRevisionField(snapshot, programme.getTitle());
        appendRevisionField(snapshot, programme.getWeeks() == null ? null : programme.getWeeks().toString());
        notes.forEach(note -> appendRevisionField(snapshot, note.getNoteText()));
        return metadataRevision(snapshot);
    }

    public TrainerLibraryProgrammeTemplate getProgrammeOwned(Long trainerId, Long programmeId) {
        requireVerifiedTrainer(trainerId);
        return programmeTemplateRepository.findByIdAndTrainerId(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
    }

    public List<TrainerLibraryProgrammeDay> getProgrammeDays(Long programmeId) {
        return programmeDayRepository.findByProgrammeIdOrderByOrderIndexAsc(programmeId);
    }

    public List<TrainerLibraryProgrammeNote> getProgrammeNotes(Long programmeId) {
        return programmeNoteRepository.findByProgrammeIdOrderByIdAsc(programmeId);
    }

    @Transactional
    public TrainerLibraryProgrammeDay addProgrammeDay(Long trainerId, Long programmeId, TrainerLibraryProgrammeDayForm form) {
        requireVerifiedTrainer(trainerId);
        programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        validateLibraryText(form.getDayOfWeek(), 20, true);
        if (form.getWorkoutId() == null) throw new IllegalArgumentException("Workout is required");
        Integer position = form.getOrderIndex();
        if (position == null) {
            int last = programmeDayRepository.findByProgrammeIdOrderByOrderIndexAsc(programmeId).stream()
                    .mapToInt(TrainerLibraryProgrammeDay::getOrderIndex).max().orElse(-1);
            if (last == Integer.MAX_VALUE) throw new IllegalArgumentException("No available position");
            position = last + 1;
        }
        if (position < 0 || programmeDayRepository.existsByProgrammeIdAndOrderIndex(programmeId, position)) {
            throw new IllegalArgumentException("Position is already occupied");
        }
        TrainerLibraryWorkoutTemplate workout = workoutTemplateRepository.findById(form.getWorkoutId())
                .orElseThrow(() -> new IllegalArgumentException("Workout not found"));
        if (!Objects.equals(workout.getTrainerId(), trainerId)) {
            throw new AccessDeniedException("Workout not owned");
        }

        TrainerLibraryProgrammeDay day = new TrainerLibraryProgrammeDay();
        day.setProgrammeId(programmeId);
        day.setDayOfWeek(form.getDayOfWeek().strip());
        day.setWorkoutId(form.getWorkoutId());
        day.setOrderIndex(position);
        return programmeDayRepository.save(day);
    }

    @Transactional
    public void deleteProgrammeDay(Long trainerId, Long programmeId, Long dayId) {
        requireVerifiedTrainer(trainerId);
        programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        TrainerLibraryProgrammeDay day = programmeDayRepository.findById(dayId)
                .orElseThrow(() -> new AccessDeniedException("Day not found"));
        if (!Objects.equals(day.getProgrammeId(), programmeId)) {
            throw new AccessDeniedException("Wrong programme");
        }
        programmeDayRepository.delete(day);
    }

    @Transactional
    public boolean moveProgrammeDay(Long trainerId, Long programmeId, Long dayId, String direction) {
        requireVerifiedTrainer(trainerId);
        programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        var days = programmeDayRepository.findByProgrammeIdOrderByOrderIndexAsc(programmeId);
        int current = -1;
        for (int index = 0; index < days.size(); index++) {
            if (Objects.equals(days.get(index).getId(), dayId)) current = index;
        }
        if (current < 0) throw new AccessDeniedException("Wrong programme");
        int offset = switch (direction == null ? "" : direction) {
            case "UP" -> -1;
            case "DOWN" -> 1;
            default -> throw new IllegalArgumentException("Invalid direction");
        };
        int target = current + offset;
        if (target < 0 || target >= days.size()) return false;
        var day = days.get(current);
        var neighbour = days.get(target);
        int previous = day.getOrderIndex();
        int next = neighbour.getOrderIndex();
        var occupied = new HashSet<Integer>();
        days.forEach(row -> occupied.add(row.getOrderIndex()));
        int temporary = 0;
        while (occupied.contains(temporary)) temporary++;
        // Use a free position and flush each leg to respect the database uniqueness constraint.
        day.setOrderIndex(temporary);
        programmeDayRepository.flush();
        neighbour.setOrderIndex(previous);
        programmeDayRepository.flush();
        day.setOrderIndex(next);
        programmeDayRepository.flush();
        return true;
    }

    @Transactional
    public void deleteProgramme(Long trainerId, Long programmeId) {
        requireVerifiedTrainer(trainerId);
        TrainerLibraryProgrammeTemplate pt = programmeTemplateRepository.findOwnedForUpdate(programmeId, trainerId)
                .orElseThrow(() -> new AccessDeniedException("Not owner"));
        programmeNoteRepository.deleteByProgrammeId(programmeId);
        programmeDayRepository.deleteByProgrammeId(programmeId);
        sharedTemplateRepository.deleteByTrainerIdAndTemplateTypeAndTemplateId(trainerId, TrainerLibraryTemplateType.PROGRAMME, programmeId);
        programmeTemplateRepository.delete(pt);
        programmeTemplateRepository.flush();
    }

    @Transactional
    public void shareTemplate(Long trainerId, TrainerLibraryShareForm form) {
        requireVerifiedTrainer(trainerId);
        if (form == null || form.getTemplateType() == null || form.getTemplateId() == null || form.getClientId() == null) {
            throw new IllegalArgumentException("Select a resource and recipient");
        }
        requireTemplateOwned(trainerId, form.getTemplateType(), form.getTemplateId());
        User client = userRepository.findByIdForUpdate(form.getClientId()).orElseThrow(() -> new AccessDeniedException("Client unavailable"));
        entityManager.refresh(client, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (client.getRole() != Role.CLIENT || !client.isEnabled()) throw new AccessDeniedException("Client unavailable");
        boolean activeLink = trainerClientLinkRepository
                .existsByTrainerUserIdAndClientUserIdAndStatus(trainerId, form.getClientId(), TrainerClientLinkStatus.ACTIVE);
        if (!activeLink) {
            throw new AccessDeniedException("Client not ACTIVE linked");
        }

        Optional<TrainerLibrarySharedTemplate> existing = sharedTemplateRepository
                .findByClientIdAndTrainerIdAndTemplateTypeAndTemplateId(form.getClientId(), trainerId, form.getTemplateType(), form.getTemplateId());
        if (existing.isPresent()) {
            return;
        }

        TrainerLibrarySharedTemplate shared = new TrainerLibrarySharedTemplate(trainerId, form.getClientId(), form.getTemplateType(), form.getTemplateId());
        sharedTemplateRepository.save(shared);
    }

    public Optional<TrainerClientLink> getActiveLinkForClient(Long clientId) {
        User client = userRepository.findById(clientId).orElse(null);
        if (client == null || client.getRole() != Role.CLIENT || !client.isEnabled()) return Optional.empty();
        return trainerClientLinkRepository.findFirstByClientUserIdAndStatusOrderByUpdatedAtDesc(clientId, TrainerClientLinkStatus.ACTIVE)
                .filter(link -> userRepository.findById(link.getTrainerUserId())
                        .filter(trainer -> trainer.getRole() == Role.TRAINER && trainer.isEnabled() && trainer.isTrainerVerified()).isPresent());
    }

    public List<TrainerLibraryAssignedExerciseView> getAssignedExercisesForClient(Long clientId) {
        var active = getActiveLinkForClient(clientId);
        if (active.isEmpty()) return List.of();
        Long trainerId = active.get().getTrainerUserId();
        var shares = sharedTemplateRepository.findByClientIdAndTrainerIdOrderBySharedAtDesc(clientId, trainerId);
        var result = new ArrayList<TrainerLibraryAssignedExerciseView>();
        for (Long id : shares.stream().filter(share -> share.getTemplateType() == TrainerLibraryTemplateType.EXERCISE)
                .map(TrainerLibrarySharedTemplate::getTemplateId).distinct().toList()) {
            exerciseRepository.findByIdAndTrainerId(id, trainerId).ifPresent(exercise -> result.add(
                    new TrainerLibraryAssignedExerciseView(exercise, exerciseNoteRepository.findByExerciseIdOrderByIdAsc(id),
                            TrainerLibraryExerciseForm.isSafeVideoUrl(exercise.getVideoUrl()) ? exercise.getVideoUrl() : null)));
        }
        return result;
    }

    public List<TrainerLibraryAssignedWorkoutView> getAssignedWorkoutsForClient(Long clientId) {
        Optional<TrainerClientLink> active = getActiveLinkForClient(clientId);
        if (active.isEmpty()) {
            return List.of();
        }
        Long trainerId = active.get().getTrainerUserId();

        List<TrainerLibrarySharedTemplate> shares = sharedTemplateRepository.findByClientIdAndTrainerIdOrderBySharedAtDesc(clientId, trainerId);
        List<Long> workoutIds = shares.stream()
                .filter(s -> s.getTemplateType() == TrainerLibraryTemplateType.WORKOUT)
                .map(TrainerLibrarySharedTemplate::getTemplateId)
                .distinct()
                .toList();

        List<TrainerLibraryAssignedWorkoutView> result = new ArrayList<>();
        for (Long workoutId : workoutIds) {
            TrainerLibraryWorkoutTemplate workout = workoutTemplateRepository.findByIdAndTrainerId(workoutId, trainerId)
                    .orElse(null);
            if (workout == null) {
                continue;
            }
            List<TrainerLibraryWorkoutItem> items = workoutItemRepository.findByWorkoutIdOrderByOrderIndexAsc(workoutId);
            List<TrainerLibraryWorkoutNote> notes = workoutNoteRepository.findByWorkoutIdOrderByIdAsc(workoutId);

            Set<Long> exerciseIds = new HashSet<>();
            for (TrainerLibraryWorkoutItem item : items) {
                exerciseIds.add(item.getExerciseId());
            }

            Map<Long, TrainerLibraryExercise> exercisesById = new HashMap<>();
            if (!exerciseIds.isEmpty()) {
                for (TrainerLibraryExercise ex : exerciseRepository.findAllById(exerciseIds)) {
                    if (Objects.equals(ex.getTrainerId(), trainerId)) exercisesById.put(ex.getId(), ex);
                }
            }

            result.add(new TrainerLibraryAssignedWorkoutView(workout, items, notes, exercisesById));
        }
        return result;
    }

    public List<TrainerLibraryAssignedProgrammeView> getAssignedProgrammesForClient(Long clientId) {
        Optional<TrainerClientLink> active = getActiveLinkForClient(clientId);
        if (active.isEmpty()) {
            return List.of();
        }
        Long trainerId = active.get().getTrainerUserId();

        List<TrainerLibrarySharedTemplate> shares = sharedTemplateRepository.findByClientIdAndTrainerIdOrderBySharedAtDesc(clientId, trainerId);
        List<Long> programmeIds = shares.stream()
                .filter(s -> s.getTemplateType() == TrainerLibraryTemplateType.PROGRAMME)
                .map(TrainerLibrarySharedTemplate::getTemplateId)
                .distinct()
                .toList();

        List<TrainerLibraryAssignedProgrammeView> result = new ArrayList<>();
        for (Long programmeId : programmeIds) {
            TrainerLibraryProgrammeTemplate programme = programmeTemplateRepository.findByIdAndTrainerId(programmeId, trainerId)
                    .orElse(null);
            if (programme == null) {
                continue;
            }
            List<TrainerLibraryProgrammeDay> days = programmeDayRepository.findByProgrammeIdOrderByOrderIndexAsc(programmeId);
            List<TrainerLibraryProgrammeNote> notes = programmeNoteRepository.findByProgrammeIdOrderByIdAsc(programmeId);

            Set<Long> workoutIds = new HashSet<>();
            for (TrainerLibraryProgrammeDay day : days) {
                workoutIds.add(day.getWorkoutId());
            }
            Map<Long, TrainerLibraryWorkoutTemplate> workoutsById = new HashMap<>();
            if (!workoutIds.isEmpty()) {
                for (TrainerLibraryWorkoutTemplate w : workoutTemplateRepository.findAllById(workoutIds)) {
                    if (Objects.equals(w.getTrainerId(), trainerId)) workoutsById.put(w.getId(), w);
                }
            }

            result.add(new TrainerLibraryAssignedProgrammeView(programme, days, notes, workoutsById));
        }
        return result;
    }

    private void applyExerciseForm(TrainerLibraryExercise exercise, TrainerLibraryExerciseForm form) {
        validateLibraryText(form.getName(), 120, true);
        validateLibraryText(form.getPrimaryMuscles(), 255, true);
        validateLibraryText(form.getEquipment(), 255, true);
        validateLibraryText(form.getDifficulty(), 30, true);
        validateLibraryText(form.getDescription(), 10000, false);
        validateLibraryText(form.getNotesText(), 10000, false);
        validateLibraryText(form.getVideoUrl(), 500, false);
        if (!form.isVideoUrlValid()) throw new IllegalArgumentException("Unsafe video link");
        exercise.setName(form.getName());
        exercise.setDescription(form.getDescription());
        exercise.setPrimaryMuscles(form.getPrimaryMuscles());
        exercise.setEquipment(form.getEquipment());
        exercise.setDifficulty(form.getDifficulty());
        exercise.setVideoUrl(form.getVideoUrl());
    }

    private void validateLibraryText(String value, int limit, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.length() > limit)) {
            throw new IllegalArgumentException("Invalid exercise field");
        }
    }

    private void validateWorkoutForm(TrainerLibraryWorkoutTemplateForm form) {
        validateLibraryText(form.getTitle(), 120, true);
        validateLibraryText(form.getSummary(), 10000, false);
        validateLibraryText(form.getNotesText(), 10000, false);
    }

    private void validateProgrammeForm(TrainerLibraryProgrammeTemplateForm form) {
        validateLibraryText(form.getTitle(), 120, true);
        validateLibraryText(form.getNotesText(), 10000, false);
        if (form.getWeeks() != null && form.getWeeks() < 1) throw new IllegalArgumentException("Weeks must be positive");
    }

    @Transactional
    protected void replaceExerciseNotes(Long exerciseId, String notesText) {
        exerciseNoteRepository.deleteByExerciseId(exerciseId);
        for (String line : splitNotes(notesText)) {
            exerciseNoteRepository.save(new TrainerLibraryExerciseNote(exerciseId, line));
        }
    }

    @Transactional
    protected void replaceWorkoutNotes(Long workoutId, String notesText) {
        workoutNoteRepository.deleteByWorkoutId(workoutId);
        for (String line : splitNotes(notesText)) {
            workoutNoteRepository.save(new TrainerLibraryWorkoutNote(workoutId, line));
        }
    }

    @Transactional
    protected void replaceProgrammeNotes(Long programmeId, String notesText) {
        programmeNoteRepository.deleteByProgrammeId(programmeId);
        for (String line : splitNotes(notesText)) {
            programmeNoteRepository.save(new TrainerLibraryProgrammeNote(programmeId, line));
        }
    }

    private List<String> splitNotes(String notesText) {
        if (notesText == null || notesText.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String raw : notesText.split("\\r?\\n")) {
            String line = raw.trim();
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void requireTemplateOwned(Long trainerId, TrainerLibraryTemplateType type, Long templateId) {
        switch (type) {
            case EXERCISE -> {
                if (exerciseRepository.findOwnedForUpdate(templateId, trainerId).isEmpty()) {
                    throw new AccessDeniedException("Not owner");
                }
            }
            case WORKOUT -> {
                if (workoutTemplateRepository.findOwnedForUpdate(templateId, trainerId).isEmpty()) {
                    throw new AccessDeniedException("Not owner");
                }
            }
            case PROGRAMME -> {
                if (programmeTemplateRepository.findOwnedForUpdate(templateId, trainerId).isEmpty()) {
                    throw new AccessDeniedException("Not owner");
                }
            }
            default -> throw new IllegalArgumentException("Unknown type");
        }
    }

    private void requireVerifiedTrainer(Long trainerId) {
        User trainer = userRepository.findById(trainerId)
                .orElseThrow(() -> new AccessDeniedException("Trainer not found"));
        if (trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("User is not a trainer");
        }
        if (!trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new TrainerAccessException(TrainerAccessException.Reason.TRAINER_NOT_VERIFIED);
        }
    }
}
