package uk.ac.cf._5.group14.One_To_One.Notes;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

/**
 * Implementation of note business logic.
 */
@Service
@Transactional
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final NoteFolderRepository folderRepository;
    private final NoteSanitizer noteSanitizer;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entities;

    public NoteServiceImpl(NoteRepository noteRepository,
                           NoteFolderRepository folderRepository,
                           NoteSanitizer noteSanitizer) {
        this.noteRepository = noteRepository;
        this.folderRepository = folderRepository;
        this.noteSanitizer = noteSanitizer;
    }

    // Backwards-compatible wrapper used by older tests
    public Note createNote(User user, Long folderId, String title, String content, boolean isPublic) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title required");
        }
        Note note = create(user, folderId, title, content, null);
        note.setPublic(isPublic);
        return noteRepository.save(note);
    }

    // Backwards-compatible wrapper used by older tests
    public Note updateNote(User user, Long noteId, String title, String content, boolean isPublic) {
        Note note = update(user, noteId, title, content, null, null);
        note.setPublic(isPublic);
        return noteRepository.save(note);
    }

    @Override
    public Note create(User user, Long folderId, String title, String content, String noteColour) {
        String safeTitle = title(title);
        String safeContent = content(content);
        NoteFolder folder = folderRepository.findByIdAndUser(folderId, user)
                .orElseThrow(NoteServiceImpl::missing);
        Note note = new Note();
        note.setUser(user);
        note.setFolder(folder);
        note.setTitle(safeTitle);
        note.setContent(safeContent);
        note.setColour(noteColour);
        return noteRepository.save(note);
    }

    @Override
    public Note update(User user, Long noteId, String title, String content, Long newFolderId, String noteColour) {
        return updateChecked(user, noteId, title, content, newFolderId, noteColour, null);
    }

    @Override
    public Note updateChecked(User user, Long noteId, String title, String content, Long newFolderId, String noteColour, String revision) {
        Note note = noteRepository.findByIdAndUser(noteId, user).orElseThrow(NoteServiceImpl::missing);
        // Refresh the owned row under its write lock: an earlier request may have cached an old copy.
        if (entities != null) {
            try { entities.refresh(note, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
            catch (jakarta.persistence.EntityNotFoundException removed) { throw new StaleNoteException(); }
        }
        if (revision != null && !NoteRevision.of(note).equals(revision)) throw new StaleNoteException();
        String safeTitle = title(title);
        String safeContent = content(content);
        NoteFolder destination = newFolderId == null || note.getFolder().getId().equals(newFolderId)
                ? note.getFolder() : folderRepository.findByIdAndUser(newFolderId, user).orElseThrow(NoteServiceImpl::missing);
        note.setTitle(safeTitle);
        note.setContent(safeContent);
        if (noteColour != null) note.setColour(noteColour);
        note.setFolder(destination);
        return noteRepository.save(note);
    }

    @Override
    public void delete(User user, Long noteId) {
        Note note = noteRepository.findByIdAndUser(noteId, user)
                .orElseThrow(NoteServiceImpl::missing);
        noteRepository.delete(note);
    }

    @Override
    public Note getNoteForUser(User user, Long noteId) {
        Note stored = noteRepository.findByIdAndUser(noteId, user).orElseThrow(NoteServiceImpl::missing);
        return displayCopy(stored);
    }

    @Override
    public List<Note> getNotesForFolder(User user, Long folderId, String query) {
        NoteFolder folder = folderRepository.findByIdAndUser(folderId, user)
                .orElseThrow(NoteServiceImpl::missing);
        return noteRepository.searchNotes(user, folder, query(query)).stream().map(this::displayCopy).toList();
    }

    @Override
    public List<Note> search(User user, Long folderId, String query) {
        NoteFolder folder = null;
        if (folderId != null) {
            folder = folderRepository.findByIdAndUser(folderId, user)
                    .orElseThrow(NoteServiceImpl::missing);
        }
        return noteRepository.searchNotes(user, folder, query(query)).stream().map(this::displayCopy).toList();
    }

    private static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Note or folder unavailable");
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Note> searchPage(User user, Long folderId, String search, int page) {
        if (page < 1) throw new IllegalArgumentException("Page must be positive");
        NoteFolder folder = folderId == null ? null : folderRepository.findByIdAndUser(folderId, user)
                .orElseThrow(NoteServiceImpl::missing);
        String filter = query(search);
        long count = noteRepository.countSearch(user, folder, filter);
        int last = (int) Math.max(1, (count + 19) / 20);
        var request = org.springframework.data.domain.PageRequest.of(Math.min(page, last) - 1, 20);
        var notes = noteRepository.searchPage(user, folder, filter, request).stream().map(this::displayCopy).toList();
        return new org.springframework.data.domain.PageImpl<>(notes, request, count);
    }

    private static String title(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 120) {
            throw new IllegalArgumentException("A title up to 120 characters is required");
        }
        return value.trim();
    }

    private String content(String value) {
        if (value != null && (value.length() > 20000 || value.getBytes(StandardCharsets.UTF_8).length > 60000)) {
            throw new IllegalArgumentException("Note content is too long");
        }
        String safe = noteSanitizer.sanitize(value);
        if (safe.getBytes(StandardCharsets.UTF_8).length > 60000) throw new IllegalArgumentException("Note content is too long");
        return safe;
    }

    private static String query(String value) {
        if (value == null) return null;
        String search = value.trim();
        String bounded = search.length() > 120 ? search.substring(0, 120) : search;
        return bounded.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private Note displayCopy(Note stored) {
        Note safe = new Note(); safe.setId(stored.getId()); safe.setUser(stored.getUser()); safe.setFolder(stored.getFolder());
        safe.setTitle(stored.getTitle()); safe.setContent(noteSanitizer.sanitize(stored.getContent()));
        safe.setColour(stored.getColour()); safe.setPinned(stored.getPinned()); safe.setPublic(stored.isPublic());
        safe.setCreatedAt(stored.getCreatedAt()); safe.setUpdatedAt(stored.getUpdatedAt());
        safe.setRevision(NoteRevision.of(stored));
        return safe;
    }
}
