package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;

import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExerciseLogServiceImpl implements ExerciseLogService {
    @Autowired
    private ScheduleOccurrenceRepository occurrenceRepo;

    @Autowired
    private CalendarTaskRepository calendarTaskRepo;

    private final ExerciseLogRepository repo;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public ExerciseLogServiceImpl(ExerciseLogRepository repo, ScheduleOccurrenceRepository occurrenceRepo, CalendarTaskRepository calendarTaskRepo) {
        this.repo = repo;
        this.occurrenceRepo = occurrenceRepo;
        this.calendarTaskRepo = calendarTaskRepo;
    }

    @Override
    @Transactional
    public void saveLog(ExerciseLogForm form, User user) {
        ExerciseLog log = new ExerciseLog();
        validateForm(form, user);
        resolveOwnedLinks(log, form, user);
        populateLog(log, form, user);
        ExerciseLog saved = repo.save(log);
        linkSavedLog(saved, log);
    }

    @Override
    @Transactional
    public void updateLog(Long id, ExerciseLogForm form, User user) {
        ExerciseLog log = repo.findOwnedForUpdate(id, user).orElse(null);
        if (log == null) {
            throw new AccessDeniedException("Log unavailable");
        }
        entityManager.refresh(log, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        validateForm(form, user);
        if (!Objects.equals(revision(log), form.getExpectedRevision())) throw new ExerciseLogRevisionConflictException();
        resolveOwnedLinks(log, form, user);
        populateLog(log, form, user);
        ExerciseLog saved = repo.save(log);
        linkSavedLog(saved, log);
    }

    private void populateLog(ExerciseLog log, ExerciseLogForm form, User user) {
        log.setUser(user);
        log.setDate(form.getDate());
        log.setMoodBefore(form.getMoodBefore());
        log.setMoodAfter(form.getMoodAfter());
        log.setConfidence(form.getConfidence());
        log.setComments(form.getComments());
        log.setDurationMinutes(form.getDurationMinutes());
    }

    private void resolveOwnedLinks(ExerciseLog log, ExerciseLogForm form, User user) {
        ScheduleOccurrence previousOccurrence = log.getOccurrence();
        CalendarTask previousTask = log.getCalendarTask();
        if (log.getId() != null) {
            if (previousOccurrence == null) previousOccurrence = occurrenceRepo.findFirstByExerciseLogIdAndUserId(log.getId(), user.getId()).orElse(null);
            if (previousTask == null) previousTask = calendarTaskRepo.findFirstByExerciseLogIdAndUserId(log.getId(), user.getId()).orElse(null);
        }
        if (form.getOccurrenceId() != null && form.getCalendarTaskId() != null) throw new IllegalArgumentException("Choose one calendar entry");
        if ((previousOccurrence != null && !Objects.equals(previousOccurrence.getId(), form.getOccurrenceId()))
                || (previousTask != null && !Objects.equals(previousTask.getId(), form.getCalendarTaskId()))) {
            throw new IllegalArgumentException("Existing calendar links cannot be replaced");
        }
        ScheduleOccurrence occurrence = form.getOccurrenceId() == null ? null : occurrenceRepo.findOwnedForLogUpdate(form.getOccurrenceId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Calendar entry unavailable"));
        CalendarTask task = form.getCalendarTaskId() == null ? null : calendarTaskRepo.findOwnedForLogUpdate(form.getCalendarTaskId(), user.getId())
                .orElseThrow(() -> new AccessDeniedException("Calendar entry unavailable"));
        if (occurrence != null) {
            entityManager.refresh(occurrence, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            requireAvailableLink(log, occurrence.getExerciseLog(), occurrence.getDate(), form.getDate());
            if (occurrence.getUser() == null || !Objects.equals(occurrence.getUser().getId(), user.getId())) throw new AccessDeniedException("Calendar entry unavailable");
        }
        if (task != null) {
            entityManager.refresh(task, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            requireAvailableLink(log, task.getExerciseLog(), task.getDate(), form.getDate());
            if (task.getUser() == null || !Objects.equals(task.getUser().getId(), user.getId())) throw new AccessDeniedException("Calendar entry unavailable");
        }
        log.setOccurrence(occurrence); log.setCalendarTask(task);
    }

    private void requireAvailableLink(ExerciseLog log, ExerciseLog existing, java.time.LocalDate entryDate, java.time.LocalDate logDate) {
        if (existing != null && (log.getId() == null || !Objects.equals(existing.getId(), log.getId()))) throw new IllegalArgumentException("Calendar entry already has a log");
        if (!Objects.equals(entryDate, logDate)) throw new IllegalArgumentException("Use the calendar entry date");
    }

    private void linkSavedLog(ExerciseLog saved, ExerciseLog links) {
        if (links.getOccurrence() != null) {
            links.getOccurrence().setExerciseLog(saved); links.getOccurrence().setCompleted(true); occurrenceRepo.save(links.getOccurrence());
        }
        if (links.getCalendarTask() != null) {
            links.getCalendarTask().setExerciseLog(saved); links.getCalendarTask().setCompleted(true); calendarTaskRepo.save(links.getCalendarTask());
        }
    }

    private void validateForm(ExerciseLogForm form, User user) {
        if (user == null || user.getId() == null) throw new AccessDeniedException("Log owner required");
        if (form.getDate() == null || !validRating(form.getMoodBefore()) || !validRating(form.getMoodAfter()) || !validRating(form.getConfidence())
                || (form.getComments() != null && form.getComments().length() > 300)
                || (form.getDurationMinutes() != null && (form.getDurationMinutes() < 0 || form.getDurationMinutes() > 1440))) {
            throw new IllegalArgumentException("Invalid log values");
        }
    }

    private boolean validRating(Integer value) { return value != null && value >= 1 && value <= 4; }

    @Override
    public List<ExerciseLog> getAllLogs() {
        return new java.util.ArrayList<>(repo.findAll());
    }

    @Override
    public ExerciseLog getLogById(Long id) {
        return repo.findById(id).orElse(null);
    }

    @Override
    public ExerciseLog getLogByIdForUser(Long id, User user) {
        return repo.findByIdAndUser(id, user).orElse(null);
    }

    @Override
    @Transactional
    public java.util.Optional<LogSnapshot> getLogSnapshot(Long id, User user) {
        return repo.findOwnedForUpdate(id, user).map(log -> {
            entityManager.refresh(log, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            return new LogSnapshot(log, revision(log));
        });
    }

    private String revision(ExerciseLog log) {
        var snapshot = new StringBuilder();
        for (Object value : new Object[]{log.getId(), log.getDate(), log.getMoodBefore(), log.getMoodAfter(),
                log.getConfidence(), log.getDurationMinutes(), log.getComments(),
                log.getOccurrence() == null ? null : log.getOccurrence().getId(),
                log.getCalendarTask() == null ? null : log.getCalendarTask().getId()}) {
            String text = value == null ? null : value.toString();
            snapshot.append(text == null ? -1 : text.length()).append(':');
            if (text != null) snapshot.append(text);
        }
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public List<ExerciseLog> getLogsForUser(User user) {
        return repo.findByUser(user);
    }

    public List<ExerciseLog> getLogsByUser(User user) {
        return repo.findByUserOrderByDateDesc(user);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<ExerciseLog> searchHistory(User user, String query,
            java.time.LocalDate from, java.time.LocalDate until, boolean oldest, int page) {
        if (user == null || user.getId() == null) throw new AccessDeniedException("Log owner required");
        if (from != null && until != null && from.isAfter(until)) throw new IllegalArgumentException("Invalid date range");
        String literal = query == null ? "" : query.strip();
        if (literal.length() > 120) literal = literal.substring(0, 120);
        String pattern = "%" + literal.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var direction = oldest ? org.springframework.data.domain.Sort.Direction.ASC : org.springframework.data.domain.Sort.Direction.DESC;
        var sort = org.springframework.data.domain.Sort.by(direction, "date", "id");
        var result = repo.searchHistory(user, user.getId(), pattern, from, until,
                org.springframework.data.domain.PageRequest.of(Math.clamp(page, 0, 9999), 6, sort));
        if (result.getTotalElements() == 0) return new org.springframework.data.domain.PageImpl<>(List.of(),
                org.springframework.data.domain.PageRequest.of(0, 6, sort), 0);
        if (result.getTotalPages() > 0 && result.getNumber() >= result.getTotalPages()) {
            result = repo.searchHistory(user, user.getId(), pattern, from, until,
                    org.springframework.data.domain.PageRequest.of(result.getTotalPages() - 1, 6, sort));
        }
        return result;
    }


    public List<ExerciseLog> findTop5RecentExerciseLogs(User user) {
        return repo.findTop5RecentExerciseLogs(user);
    }
}
