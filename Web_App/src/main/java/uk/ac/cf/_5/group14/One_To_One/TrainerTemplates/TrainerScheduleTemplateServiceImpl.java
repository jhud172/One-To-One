package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTaskRepository;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence;
import uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrenceRepository;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.Users.Role;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNote;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteRepository;
import uk.ac.cf._5.group14.One_To_One.Vault.VaultNoteType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class TrainerScheduleTemplateServiceImpl implements TrainerScheduleTemplateService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    private final TrainerScheduleTemplateRepository templateRepository;
    private final TrainerScheduleTemplateEntryRepository entryRepository;
    private final CalendarTaskRepository calendarTaskRepository;
    private final ScheduleOccurrenceRepository scheduleOccurrenceRepository;
    private final VaultNoteRepository vaultNoteRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestionRepository questionRepository;

    public TrainerScheduleTemplateServiceImpl(TrainerScheduleTemplateRepository templateRepository,
                                              TrainerScheduleTemplateEntryRepository entryRepository,
                                              CalendarTaskRepository calendarTaskRepository,
                                              ScheduleOccurrenceRepository scheduleOccurrenceRepository,
                                              VaultNoteRepository vaultNoteRepository,
                                              UserRepository userRepository,
                                              AccessGuard accessGuard,
                                              uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestionRepository questionRepository) {
        this.templateRepository = templateRepository;
        this.entryRepository = entryRepository;
        this.calendarTaskRepository = calendarTaskRepository;
        this.scheduleOccurrenceRepository = scheduleOccurrenceRepository;
        this.vaultNoteRepository = vaultNoteRepository;
        this.userRepository = userRepository;
        this.accessGuard = accessGuard;
        this.questionRepository = questionRepository;
    }

    @Override
    public TrainerScheduleTemplate createTemplate(User trainer, String name, String description, String tags) {
        requireTrainer(trainer);
        validateMetadata(name, description, tags);
        TrainerScheduleTemplate template = new TrainerScheduleTemplate();
        template.setTrainerId(trainer.getId());
        template.setName(trimOrNull(name));
        template.setDescription(trimOrNull(description));
        template.setTags(trimOrNull(tags));
        template.setArchived(false);
        template.setVersion(1);
        return templateRepository.save(template);
    }

    @Override
    public TrainerScheduleTemplate updateTemplate(User trainer, Long templateId, String name, String description, String tags, boolean archived) {
        TrainerScheduleTemplate template = lockOwned(trainer, templateId);
        validateMetadata(name, description, tags);
        template.setName(trimOrNull(name));
        template.setDescription(trimOrNull(description));
        template.setTags(trimOrNull(tags));
        template.setArchived(archived);
        return templateRepository.save(template);
    }

    @Override
    public TrainerScheduleTemplate saveMetadata(User trainer, Long templateId, TrainerScheduleMetadataForm form) {
        var template = lockOwned(trainer, templateId);
        entityManager.refresh(template, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        validateMetadata(form.getName(), form.getDescription(), form.getTags());
        if (!java.util.Objects.equals(form.getExpectedRevision(), metadataRevision(template))) {
            throw new TrainerScheduleMetadataConflictException();
        }
        template.setName(trimOrNull(form.getName()));
        template.setDescription(trimOrNull(form.getDescription()));
        template.setTags(trimOrNull(form.getTags()));
        template.setArchived(form.isArchived());
        return templateRepository.save(template);
    }

    @Override
    public MetadataSnapshot getMetadataSnapshot(User trainer, Long templateId) {
        var template = lockOwned(trainer, templateId);
        entityManager.refresh(template, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return new MetadataSnapshot(template, metadataRevision(template));
    }

    private String metadataRevision(TrainerScheduleTemplate template) {
        var snapshot = new StringBuilder();
        for (String value : new String[] {template.getName(), template.getDescription(), template.getTags(), Boolean.toString(template.isArchived())}) {
            snapshot.append(value == null ? -1 : value.length()).append(':');
            if (value != null) snapshot.append(value);
        }
        return digestRevision(snapshot);
    }

    private String digestRevision(StringBuilder snapshot) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private TrainerScheduleTemplate lockOwned(User trainer, Long templateId) {
        requireTrainer(trainer);
        return templateRepository.findOwnedForUpdate(templateId, trainer.getId())
                .orElseThrow(() -> new AccessDeniedException("Template not found"));
    }

    @Override
    public TrainerScheduleTemplateEntry addEntry(User trainer, Long templateId, TrainerScheduleTemplateEntry entry) {
        requireTrainer(trainer);
        TrainerScheduleTemplate template = templateRepository.findOwnedForUpdate(templateId, trainer.getId())
                .orElseThrow(() -> new AccessDeniedException("Template not found"));
        validateEntry(trainer, entry);
        entry.setTemplate(template);
        entry.setOrderIndex(nextOrderIndex(templateId));
        return entryRepository.save(entry);
    }

    @Override
    public TrainerScheduleTemplateEntry updateEntry(User trainer, Long templateId, Long entryId, TrainerScheduleTemplateEntry entry) {
        TrainerScheduleTemplate template = lockOwned(trainer, templateId);
        TrainerScheduleTemplateEntry existing = entryRepository.findById(entryId)
                .orElseThrow(() -> new AccessDeniedException("Entry not found"));
        if (!existing.getTemplate().getId().equals(template.getId())) {
            throw new AccessDeniedException("Entry not owned by template");
        }
        validateEntry(trainer, entry);
        existing.setDayOfWeek(entry.getDayOfWeek());
        existing.setTimeWindowStart(entry.getTimeWindowStart());
        existing.setTimeWindowEnd(entry.getTimeWindowEnd());
        existing.setType(entry.getType());
        existing.setTitle(entry.getTitle());
        existing.setDefaultsJson(entry.getDefaultsJson());
        existing.setIntensityLabel(entry.getIntensityLabel());
        existing.setIntensityLevel(entry.getIntensityLevel());
        existing.setExercise(entry.getExercise());
        existing.setCustomExercise(entry.getCustomExercise());
        return entryRepository.save(existing);
    }

    @Override
    public void deleteEntry(User trainer, Long templateId, Long entryId) {
        TrainerScheduleTemplate template = lockOwned(trainer, templateId);
        TrainerScheduleTemplateEntry existing = entryRepository.findById(entryId)
                .orElseThrow(() -> new AccessDeniedException("Entry not found"));
        if (!existing.getTemplate().getId().equals(template.getId())) {
            throw new AccessDeniedException("Entry not owned by template");
        }
        entryRepository.delete(existing);
    }

    @Override
    public boolean moveEntry(User trainer, Long templateId, Long entryId, String direction) {
        lockOwned(trainer, templateId);
        int step = "UP".equals(direction) ? -1 : "DOWN".equals(direction) ? 1 : 0;
        if (step == 0) throw new IllegalArgumentException("Invalid movement");
        var entries = entryRepository.findByTemplateIdOrderByOrderIndexAsc(templateId);
        int index = -1;
        for (int position = 0; position < entries.size(); position++) {
            if (entries.get(position).getId().equals(entryId)) index = position;
        }
        if (index < 0) throw new AccessDeniedException("Entry not found");
        if (index + step < 0 || index + step >= entries.size()) return false;
        java.util.Collections.swap(entries, index, index + step);
        for (int position = 0; position < entries.size(); position++) entries.get(position).setOrderIndex(position + 1);
        entryRepository.saveAll(entries);
        return true;
    }

    @Override
    public TrainerScheduleTemplate cloneTemplate(User trainer, Long templateId) {
        TrainerScheduleTemplate source = lockOwned(trainer, templateId);
        TrainerScheduleTemplate clone = new TrainerScheduleTemplate();
        clone.setTrainerId(source.getTrainerId());
        String copyName = source.getName();
        if (copyName.length() > 193) copyName = copyName.substring(0, 193);
        clone.setName(copyName + " (Copy)");
        clone.setDescription(source.getDescription());
        clone.setTags(source.getTags());
        clone.setArchived(false);
        clone.setVersion(source.getVersion() + 1);
        clone = templateRepository.save(clone);

        List<TrainerScheduleTemplateEntry> entries = entryRepository.findByTemplateIdOrderByOrderIndexAsc(source.getId());
        int order = 1;
        for (TrainerScheduleTemplateEntry entry : entries) {
            TrainerScheduleTemplateEntry copy = new TrainerScheduleTemplateEntry();
            copy.setTemplate(clone);
            copy.setDayOfWeek(entry.getDayOfWeek());
            copy.setTimeWindowStart(entry.getTimeWindowStart());
            copy.setTimeWindowEnd(entry.getTimeWindowEnd());
            copy.setType(entry.getType());
            copy.setTitle(entry.getTitle());
            copy.setDefaultsJson(entry.getDefaultsJson());
            copy.setIntensityLabel(entry.getIntensityLabel());
            copy.setIntensityLevel(entry.getIntensityLevel());
            copy.setExercise(entry.getExercise());
            copy.setCustomExercise(entry.getCustomExercise());
            copy.setOrderIndex(order++);
            entryRepository.save(copy);
        }

        for (var question : questionRepository.findByTemplateIdOrderByOrderIndexAsc(source.getId())) {
            var copy = new uk.ac.cf._5.group14.One_To_One.Checkins.TrainerCheckInQuestion();
            copy.setTemplateId(clone.getId());
            copy.setPrompt(question.getPrompt());
            copy.setRequired(question.isRequired());
            copy.setOrderIndex(question.getOrderIndex());
            questionRepository.save(copy);
        }
        return clone;
    }

    @Override
    public List<TrainerScheduleTemplatePreviewItem> previewApply(User trainer,
                                                                 Long templateId,
                                                                 Long clientId,
                                                                 LocalDate startDate,
                                                                 LocalDate endDate,
                                                                 boolean idempotent) {
        return previewApplication(trainer, templateId, clientId, startDate, endDate, idempotent).items();
    }

    @Override
    public ApplicationPreview previewApplication(User trainer, Long templateId, Long clientId,
                                                 LocalDate startDate, LocalDate endDate, boolean idempotent) {
        TrainerScheduleTemplate template = freshApplicationTemplate(trainer, templateId);
        validateDateRange(startDate, endDate);
        if (template.isArchived()) throw new IllegalArgumentException("Archived templates cannot be applied");
        User client = loadClientForTrainer(trainer, clientId);
        List<TrainerScheduleTemplateEntry> entries = freshApplicationEntries(template.getId());
        List<TrainerScheduleTemplatePreviewItem> preview = new ArrayList<>();

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            int dow = cursor.getDayOfWeek().getValue();
            for (TrainerScheduleTemplateEntry entry : entries) {
                if (entry.getDayOfWeek() != dow) {
                    continue;
                }
                boolean duplicate = idempotent && isDuplicate(client, cursor, entry);
                preview.add(new TrainerScheduleTemplatePreviewItem(
                        cursor,
                        entry.getType(),
                        entry.getTitle(),
                        entry.getTimeWindowStart(),
                        entry.getTimeWindowEnd(),
                        duplicate,
                        entry.getExercise() != null ? entry.getExercise().getName()
                                : entry.getCustomExercise() != null ? entry.getCustomExercise().getName() : null,
                        entry.getType() == TrainerScheduleTemplateEntryType.WORKOUT ? null : defaultNoteBody(entry)
                ));
            }
            cursor = cursor.plusDays(1);
        }

        return new ApplicationPreview(List.copyOf(preview), applicationRevision(template, entries, clientId, startDate, endDate, idempotent));
    }

    @Override
    public int applyTemplate(User trainer,
                             Long templateId,
                             Long clientId,
                             LocalDate startDate,
                             LocalDate endDate,
                             boolean idempotent) {
        return applyWithRevision(trainer, templateId, clientId, startDate, endDate, idempotent, null);
    }

    @Override
    public int applyReviewedTemplate(User trainer, Long templateId, Long clientId, LocalDate startDate,
                                     LocalDate endDate, boolean idempotent, String expectedRevision) {
        if (expectedRevision == null) throw new TrainerScheduleApplicationConflictException();
        return applyWithRevision(trainer, templateId, clientId, startDate, endDate, idempotent, expectedRevision);
    }

    private int applyWithRevision(User trainer, Long templateId, Long clientId, LocalDate startDate,
                                   LocalDate endDate, boolean idempotent, String expectedRevision) {
        TrainerScheduleTemplate template = freshApplicationTemplate(trainer, templateId);
        validateDateRange(startDate, endDate);
        if (template.isArchived()) throw new IllegalArgumentException("Archived templates cannot be applied");
        if (clientId == null) throw new AccessDeniedException("Client not found");
        userRepository.findByIdForUpdate(clientId).orElseThrow(() -> new AccessDeniedException("Client not found"));
        User client = loadClientForTrainer(trainer, clientId);
        List<TrainerScheduleTemplateEntry> entries = freshApplicationEntries(template.getId());
        if (expectedRevision != null && !java.util.Objects.equals(expectedRevision,
                applicationRevision(template, entries, clientId, startDate, endDate, idempotent))) {
            throw new TrainerScheduleApplicationConflictException();
        }
        int created = 0;

        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            int dow = cursor.getDayOfWeek().getValue();
            for (TrainerScheduleTemplateEntry entry : entries) {
                if (entry.getDayOfWeek() != dow) {
                    continue;
                }
                if (idempotent && isDuplicate(client, cursor, entry)) {
                    continue;
                }
                boolean saved = createFromEntry(template, entry, client, cursor);
                if (saved) {
                    created++;
                }
            }
            cursor = cursor.plusDays(1);
        }

        return created;
    }

    private TrainerScheduleTemplate freshApplicationTemplate(User trainer, Long templateId) {
        var template = lockOwned(trainer, templateId);
        entityManager.refresh(template, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return template;
    }

    private List<TrainerScheduleTemplateEntry> freshApplicationEntries(Long templateId) {
        var entries = entryRepository.findByTemplateIdOrderByOrderIndexAsc(templateId);
        // A controller or earlier service call may already have managed these rows before the parent lock.
        entries.forEach(entityManager::refresh);
        return entries;
    }

    private String applicationRevision(TrainerScheduleTemplate template, List<TrainerScheduleTemplateEntry> entries,
                                       Long clientId, LocalDate startDate, LocalDate endDate, boolean idempotent) {
        var snapshot = new StringBuilder(metadataRevision(template));
        appendApplicationFields(snapshot, template.getId(), clientId, startDate, endDate, idempotent);
        for (var entry : entries) {
            appendApplicationFields(snapshot, entry.getId(), entry.getOrderIndex(), entry.getDayOfWeek(), entry.getType(),
                    entry.getTitle(), entry.getTimeWindowStart(), entry.getTimeWindowEnd(), entry.getDefaultsJson(),
                    entry.getIntensityLabel(), entry.getIntensityLevel(),
                    entry.getExercise() == null ? null : entry.getExercise().getId(),
                    entry.getCustomExercise() == null ? null : entry.getCustomExercise().getId());
        }
        return digestRevision(snapshot);
    }

    private void appendApplicationFields(StringBuilder snapshot, Object... values) {
        for (Object value : values) {
            String text = java.util.Objects.toString(value, null);
            snapshot.append(text == null ? -1 : text.length()).append(':');
            if (text != null) snapshot.append(text);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrainerScheduleTemplate> listForTrainer(User trainer) {
        requireTrainer(trainer);
        return templateRepository.findByTrainerIdOrderByUpdatedAtDesc(trainer.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateCatalogue searchForTrainer(User trainer, String rawQuery, int requestedPage) {
        requireTrainer(trainer);
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() > 120) query = query.substring(0, 120);
        String pattern = "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt", "id");
        var page = templateRepository.searchOwned(trainer.getId(), pattern,
                org.springframework.data.domain.PageRequest.of(Math.clamp(requestedPage, 0, 9999), 18, sort));
        if (page.getTotalPages() > 0 && page.getNumber() >= page.getTotalPages()) {
            page = templateRepository.searchOwned(trainer.getId(), pattern,
                    org.springframework.data.domain.PageRequest.of(page.getTotalPages() - 1, 18, sort));
        }
        if (page.getTotalElements() == 0 && page.getNumber() > 0) {
            page = org.springframework.data.domain.Page.empty(org.springframework.data.domain.PageRequest.of(0, 18, sort));
        }
        var entryCounts = new java.util.HashMap<Long, Long>();
        var questionCounts = new java.util.HashMap<Long, Long>();
        var weekdayCounts = new java.util.HashMap<Long, java.util.Map<Integer, Long>>();
        var ids = page.getContent().stream().map(TrainerScheduleTemplate::getId).toList();
        if (!ids.isEmpty()) {
            for (var count : entryRepository.countOwnedWeekdaysOnPage(trainer.getId(), ids)) {
                Long id = (Long) count[0];
                Long total = (Long) count[2];
                entryCounts.merge(id, total, Long::sum);
                weekdayCounts.computeIfAbsent(id, ignored -> new java.util.LinkedHashMap<>()).put((Integer) count[1], total);
            }
            for (var count : questionRepository.countByTemplateIds(ids)) {
                questionCounts.put((Long) count[0], (Long) count[1]);
            }
        }
        return new TemplateCatalogue(query, page, templateRepository.countByTrainerId(trainer.getId()),
                java.util.Map.copyOf(entryCounts), java.util.Map.copyOf(questionCounts), java.util.Map.copyOf(weekdayCounts));
    }

    @Override
    @Transactional(readOnly = true)
    public TrainerScheduleTemplate getForTrainer(User trainer, Long templateId) {
        requireTrainer(trainer);
        return templateRepository.findByIdAndTrainerId(templateId, trainer.getId())
                .orElseThrow(() -> new AccessDeniedException("Template not found"));
    }

    private void requireTrainer(User trainer) {
        if (trainer == null || trainer.getRole() != Role.TRAINER) {
            throw new AccessDeniedException("Trainer role required");
        }
        if (!trainer.isTrainerVerified() || !trainer.isEnabled()) {
            throw new AccessDeniedException("TRAINER_NOT_VERIFIED");
        }
    }

    private User loadClientForTrainer(User trainer, Long clientId) {
        requireTrainer(trainer);
        accessGuard.requireTrainerAccessClient(trainer.getId(), clientId);
        User client = userRepository.findById(clientId).orElseThrow(() -> new AccessDeniedException("Client not found"));
        entityManager.refresh(client);
        if (client.getRole() != Role.CLIENT || !client.isEnabled()) throw new AccessDeniedException("Client unavailable");
        return client;
    }

    private void validateEntry(User trainer, TrainerScheduleTemplateEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Entry is required");
        }
        validateText(entry.getTitle(), 200, true);
        validateText(entry.getDefaultsJson(), 10000, false);
        validateText(entry.getIntensityLabel(), 80, false);
        if (entry.getIntensityLevel() != null && (entry.getIntensityLevel() < 1 || entry.getIntensityLevel() > 10)) {
            throw new IllegalArgumentException("Intensity must be between 1 and 10");
        }
        if (entry.getTimeWindowStart() != null && entry.getTimeWindowEnd() != null
                && !entry.getTimeWindowStart().isBefore(entry.getTimeWindowEnd())) {
            throw new IllegalArgumentException("End time must follow start time");
        }
        if (entry.getCustomExercise() != null && !trainer.getId().equals(entry.getCustomExercise().getUserId())) {
            throw new AccessDeniedException("Custom exercise not owned");
        }
        if (entry.getExercise() != null && entry.getCustomExercise() != null) throw new IllegalArgumentException("Choose one exercise");
        if (entry.getDayOfWeek() < 1 || entry.getDayOfWeek() > 7) {
            throw new IllegalArgumentException("Day of week invalid");
        }
        if (entry.getType() == null) {
            throw new IllegalArgumentException("Entry type required");
        }
        if (entry.getType() == TrainerScheduleTemplateEntryType.WORKOUT
                && entry.getExercise() == null
                && entry.getCustomExercise() == null) {
            throw new IllegalArgumentException("Workout entry requires an exercise");
        }
    }

    private int nextOrderIndex(Long templateId) {
        int last = entryRepository.findByTemplateIdOrderByOrderIndexAsc(templateId).stream()
                .mapToInt(TrainerScheduleTemplateEntry::getOrderIndex).max().orElse(0);
        if (last == Integer.MAX_VALUE) throw new IllegalArgumentException("No available entry position");
        return last + 1;
    }

    private boolean isDuplicate(User client, LocalDate date, TrainerScheduleTemplateEntry entry) {
        if (entry.getType() == TrainerScheduleTemplateEntryType.WORKOUT) {
            return scheduleOccurrenceRepository.existsByUserAndDateAndTrainerTemplateEntryId(client, date, entry.getId());
        }
        if (entry.getType() == TrainerScheduleTemplateEntryType.NOTE) {
            return vaultNoteRepository.existsByUserIdAndLinkedDateAndTrainerTemplateEntryId(client.getId(), date, entry.getId());
        }
        return calendarTaskRepository.existsByUserAndDateAndTrainerTemplateEntryId(client, date, entry.getId());
    }

    private boolean createFromEntry(TrainerScheduleTemplate template,
                                    TrainerScheduleTemplateEntry entry,
                                    User client,
                                    LocalDate date) {
        switch (entry.getType()) {
            case WORKOUT -> {
                ScheduleOccurrence occurrence = new ScheduleOccurrence();
                occurrence.setUser(client);
                occurrence.setExercise(entry.getExercise());
                occurrence.setCustomExercise(entry.getCustomExercise());
                occurrence.setScheduleName(template.getName());
                occurrence.setDate(date);
                occurrence.setCompleted(false);
                occurrence.setTrainerTemplateId(template.getId());
                occurrence.setTrainerTemplateEntryId(entry.getId());
                scheduleOccurrenceRepository.save(occurrence);
                return true;
            }
            case NOTE -> {
                VaultNote note = new VaultNote();
                note.setUserId(client.getId());
                note.setNoteType(VaultNoteType.REFLECTION);
                note.setTitle(entry.getTitle());
                note.setContent(defaultNoteBody(entry));
                note.setLinkedDate(date);
                note.setTrainerTemplateId(template.getId());
                note.setTrainerTemplateEntryId(entry.getId());
                vaultNoteRepository.save(note);
                return true;
            }
            case TASK -> {
                CalendarTask task = new CalendarTask();
                task.setUser(client);
                task.setDate(date);
                task.setTime(entry.getTimeWindowStart());
                task.setTitle(entry.getTitle());
                task.setNotes(defaultNoteBody(entry));
                task.setExercise(false);
                task.setCompleted(false);
                task.setTrainerTemplateId(template.getId());
                task.setTrainerTemplateEntryId(entry.getId());
                calendarTaskRepository.save(task);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private String defaultNoteBody(TrainerScheduleTemplateEntry entry) {
        if (entry.getDefaultsJson() == null) {
            return null;
        }
        String trimmed = entry.getDefaultsJson().trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before end date");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) > 365 || endDate.equals(LocalDate.MAX)) {
            throw new IllegalArgumentException("Apply at most 366 days at a time");
        }
    }

    private void validateMetadata(String name, String description, String tags) {
        validateText(name, 200, true);
        validateText(description, 800, false);
        validateText(tags, 500, false);
    }

    private void validateText(String value, int limit, boolean required) {
        if ((required && (value == null || value.isBlank())) || (value != null && value.length() > limit)) {
            throw new IllegalArgumentException("Invalid template field");
        }
    }
}
