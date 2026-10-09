package uk.ac.cf._5.group14.One_To_One.Vault;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class VaultNoteService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entities;

    private final VaultNoteRepository vaultNoteRepository;

    private final WorkoutSessionRepository sessions;

    public VaultNoteService(VaultNoteRepository vaultNoteRepository, WorkoutSessionRepository sessions) {
        this.vaultNoteRepository = vaultNoteRepository;
        this.sessions = sessions;
    }

    public List<VaultNote> listForUser(Long userId, VaultNoteType type) {
        if (userId == null) return Collections.emptyList();
        if (type == null) {
            return vaultNoteRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId);
        }
        return vaultNoteRepository.findByUserIdAndNoteTypeOrderByPinnedDescUpdatedAtDesc(userId, type);
    }

    public List<VaultNote> search(Long userId, String query, VaultNoteType type,
                                   boolean pinnedOnly, LocalDate fromDate, LocalDate toDate) {
        if (userId == null) return Collections.emptyList();
        String searchTerm = query(query, fromDate, toDate);
        return vaultNoteRepository.search(userId, searchTerm, type, pinnedOnly, fromDate, toDate);
    }

    @Transactional(readOnly = true)
    public VaultNotePage searchPage(Long userId, String query, VaultNoteType type,
                                   boolean pinnedOnly, LocalDate fromDate, LocalDate toDate, int page) {
        if (page < 1) throw new IllegalArgumentException("Invalid page");
        if (userId == null) return new VaultNotePage(List.of(), 1, 1, 0);
        String term = query(query, fromDate, toDate);
        long total = vaultNoteRepository.countSearch(userId, term, type, pinnedOnly, fromDate, toDate);
        int pages = (int) Math.max(1, (total + 19) / 20);
        int current = Math.min(page, pages);
        return new VaultNotePage(vaultNoteRepository.searchPage(userId, term, type, pinnedOnly, fromDate, toDate,
                PageRequest.of(current - 1, 20)), current, pages, total);
    }

    private String query(String value, LocalDate from, LocalDate to) {
        String term = value == null || value.isBlank() ? null : value.trim();
        if (term != null && term.length() > 120) throw new IllegalArgumentException("Search is too long");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Date range is reversed");
        return term == null ? null : term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    public Optional<VaultNote> getForUser(Long id, Long userId) {
        if (id == null || userId == null) return Optional.empty();
        return vaultNoteRepository.findByIdAndUserId(id, userId);
    }

    public VaultNote create(Long userId,
                             VaultNoteType type,
                             String title,
                             String content,
                             LocalDate linkedDate,
                             Long linkedWorkoutSessionId,
                             String tags,
                             String mood) {
        validate(userId, type, title, content, linkedWorkoutSessionId, tags, mood);
        VaultNote note = new VaultNote(userId, type, title.trim(), content);
        note.setLinkedDate(linkedDate);
        note.setLinkedWorkoutSessionId(linkedWorkoutSessionId);
        note.setTags(tags != null ? tags.trim() : "");
        note.setMood(mood == null || mood.isBlank() ? null : mood);
        return vaultNoteRepository.save(note);
    }

    public VaultNote create(Long userId,
                             VaultNoteType type,
                             String title,
                             String content,
                             LocalDate linkedDate,
                             Long linkedWorkoutSessionId) {
        return create(userId, type, title, content, linkedDate, linkedWorkoutSessionId, "", null);
    }

    public Optional<VaultNote> update(Long id,
                                      Long userId,
                                      VaultNoteType type,
                                      String title,
                                      String content,
                                      LocalDate linkedDate,
                                      Long linkedWorkoutSessionId,
                                      String tags,
                                      String mood) {
        return updateChecked(id, userId, type, title, content, linkedDate, linkedWorkoutSessionId, tags, mood, null);
    }

    public Optional<VaultNote> updateChecked(Long id, Long userId, VaultNoteType type, String title, String content,
                                            LocalDate linkedDate, Long linkedWorkoutSessionId, String tags, String mood,
                                            String revision) {
        Optional<VaultNote> existing = getForUser(id, userId);
        if (existing.isEmpty()) return Optional.empty();
        VaultNote note = existing.get();
        lock(note);
        if (revision != null && !revision.equals(note.getRevision())) throw new StaleReflectionException();
        validate(userId, type, title, content, linkedWorkoutSessionId, tags, mood);
        String savedMood = mood == null || mood.isBlank() ? null : mood;
        if (!Objects.equals(note.getTitle(), title.trim()) || !Objects.equals(note.getContent(), content)
                || note.getNoteType() != type || !Objects.equals(note.getMood(), savedMood)
                || !Objects.equals(note.getLinkedDate(), linkedDate)
                || !Objects.equals(note.getLinkedWorkoutSessionId(), linkedWorkoutSessionId)
                || !Objects.equals(note.getTags(), tags == null ? "" : tags.trim())) {
            note.setAiSummary(null);
            note.setAiGeneratedAt(null);
            note.setAiSourceRevision(null);
        }
        note.setNoteType(type);
        note.setTitle(title.trim());
        note.setContent(content);
        note.setLinkedDate(linkedDate);
        note.setLinkedWorkoutSessionId(linkedWorkoutSessionId);
        note.setTags(tags != null ? tags.trim() : "");
        note.setMood(savedMood);
        return Optional.of(vaultNoteRepository.save(note));
    }

    public Optional<VaultNote> update(Long id,
                                      Long userId,
                                      VaultNoteType type,
                                      String title,
                                      String content,
                                      LocalDate linkedDate,
                                      Long linkedWorkoutSessionId) {
        Optional<VaultNote> existing = getForUser(id, userId);
        if (existing.isEmpty()) return Optional.empty();

        VaultNote note = existing.get();
        return update(id, userId, type, title, content, linkedDate, linkedWorkoutSessionId, note.getTags(), note.getMood());
    }

    public boolean delete(Long id, Long userId) {
        Optional<VaultNote> existing = getForUser(id, userId);
        if (existing.isEmpty()) return false;
        vaultNoteRepository.delete(existing.get());
        return true;
    }

    public Optional<VaultNote> togglePin(Long id, Long userId) {
        Optional<VaultNote> existing = getForUser(id, userId);
        if (existing.isEmpty()) return Optional.empty();
        VaultNote note = existing.get();
        lock(note);
        note.setPinned(!note.isPinned());
        return Optional.of(vaultNoteRepository.save(note));
    }

    public Optional<VaultNote> saveAiSummary(Long id, Long userId, String summary) {
        return saveAiSummaryChecked(id, userId, summary, null);
    }

    public Optional<VaultNote> saveAiSummaryChecked(Long id, Long userId, String summary, String revision) {
        Optional<VaultNote> existing = getForUser(id, userId);
        if (existing.isEmpty()) return Optional.empty();
        VaultNote note = existing.get();
        lock(note);
        if (revision != null && !revision.equals(note.getRevision())) throw new StaleReflectionException();
        if (summary == null || summary.isBlank() || summary.length() > 20000) throw new IllegalArgumentException("Invalid insight");
        note.setAiSummary(summary);
        note.setAiGeneratedAt(Instant.now());
        note.setAiSourceRevision(revision == null ? null : note.getRevision());
        return Optional.of(vaultNoteRepository.save(note));
    }

    private void lock(VaultNote note) {
        if (entities != null) {
            try { entities.refresh(note, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
            catch (jakarta.persistence.EntityNotFoundException gone) { throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
        }
    }

    public List<VaultNote> getManyForUser(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty() || userId == null) return Collections.emptyList();
        var found = vaultNoteRepository.findByIdInAndUserId(ids, userId);
        var byId = new HashMap<Long, VaultNote>();
        found.forEach(note -> byId.put(note.getId(), note));
        return ids.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    private void validate(Long userId, VaultNoteType type, String title, String content,
                          Long sessionId, String tags, String mood) {
        if (userId == null || type == null || title == null || title.isBlank() || title.trim().length() > 120
                || content == null || content.isBlank() || content.length() > 10000
                || content.getBytes(StandardCharsets.UTF_8).length > 60000
                || (tags != null && tags.length() > 255)
                || (mood != null && !mood.isBlank() && !Set.of("GREAT", "GOOD", "NEUTRAL", "LOW", "POOR").contains(mood))) {
            throw new IllegalArgumentException("Check reflection fields");
        }
        if (sessionId != null) {
            var session = sessions.findById(sessionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (session.getUser() == null || !Objects.equals(session.getUser().getId(), userId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
    }

    public Map<String, Object> getMetrics(Long userId) {
        Map<String, Object> metrics = new HashMap<>();
        if (userId == null) return metrics;

        long total = vaultNoteRepository.countByUserId(userId);
        metrics.put("totalNotes", total);

        LocalDate now = LocalDate.now();
        LocalDate firstOfMonth = now.with(TemporalAdjusters.firstDayOfMonth());
        Instant firstOfMonthInstant = firstOfMonth.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        long thisMonth = vaultNoteRepository.countByUserIdAndCreatedAtAfter(userId, firstOfMonthInstant);
        metrics.put("notesThisMonth", thisMonth);

        metrics.put("pinnedCount", vaultNoteRepository.countByUserIdAndPinnedTrue(userId));

        // Most used tag
        Map<String, Integer> tagCounts = new HashMap<>();
        for (String tags : vaultNoteRepository.findTagsByUserId(userId)) {
            if (tags != null && !tags.isBlank()) {
                for (String tag : tags.split(",")) {
                    String t = tag.trim().toLowerCase(java.util.Locale.ROOT);
                    if (!t.isEmpty()) {
                        tagCounts.merge(t, 1, Integer::sum);
                    }
                }
            }
        }
        String topTag = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElse(null);
        metrics.put("topTag", topTag);

        return metrics;
    }
}
