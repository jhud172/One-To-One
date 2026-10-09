package uk.ac.cf._5.group14.One_To_One.Notes;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import uk.ac.cf._5.group14.One_To_One.Level.LevelService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Web controller for managing notes and folders.
 */
@Controller
@RequestMapping("/notes")
public class NotesController {

    private final NoteFolderService folderService;
    private final NoteService noteService;
    private final AuthHelper authHelper;
    private final LevelService levelService;
    private final NoteSanitizer noteSanitizer;

    public NotesController(NoteFolderService folderService,
                           NoteService noteService,
                           AuthHelper authHelper,
                           LevelService levelService, NoteSanitizer noteSanitizer) {
        this.folderService = folderService;
        this.noteService = noteService;
        this.authHelper = authHelper;
        this.levelService = levelService;
        this.noteSanitizer = noteSanitizer;
    }

    @GetMapping
    public String index(HttpSession session, Model model) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        folderService.ensureDefaults(user);
        List<NoteFolder> folders = folderService.getFoldersForUser(user);
        model.addAttribute("folders", folders);

        Long activeFolderId = folders.isEmpty() ? null : folders.get(0).getId();
        model.addAttribute("activeFolderId", activeFolderId);
        populatePage(model, user, activeFolderId, null, 1, "/notes");
        model.addAttribute("activeNote", null);
        model.addAttribute("q", "");
        return "shared-views/notes/index";
    }

    @GetMapping(params = {"folderId"})
    public String indexFolder(@RequestParam Long folderId,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) Long noteId,
                              @RequestParam(defaultValue = "1") int page,
                              HttpSession session,
                              Model model) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        folderService.ensureDefaults(user);
        List<NoteFolder> folders = folderService.getFoldersForUser(user);
        NoteFolder activeFolder = folderService.getFolderForUser(user, folderId);
        var result = populatePage(model, user, folderId, q, page, "/notes");
        List<Note> notes = result.getContent();
        Note activeNote = null;
        if (noteId != null) {
            activeNote = noteService.getNoteForUser(user, noteId);
            if (!folderId.equals(activeNote.getFolder().getId())) {
                return "redirect:/notes?folderId=" + activeNote.getFolder().getId() + "&noteId=" + noteId;
            }
        } else if (!notes.isEmpty()) {
            activeNote = notes.get(0);
        }

        model.addAttribute("folders", folders);
        model.addAttribute("activeFolderId", activeFolder.getId());
        model.addAttribute("notes", notes);
        model.addAttribute("activeNote", activeNote);
        return "shared-views/notes/index";
    }

    @GetMapping("/folders/{id}")
    public String folderView(@PathVariable Long id,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "1") int page,
                             HttpSession session,
                             Model model) {
        User user = authHelper.getAuthenticatedUser(session);
        model.addAttribute("folders", folderService.getFoldersForUser(user));
        NoteFolder activeFolder = folderService.getFolderForUser(user, id);
        model.addAttribute("activeFolder", activeFolder);
        populatePage(model, user, id, q, page, "/notes/folders/" + id);
        return "shared-views/notes/folders";
    }

    @PostMapping("/folders/new")
    public String createFolder(@RequestParam String name,
                               @RequestParam(required = false) String colour,
                               HttpSession session, Model model, HttpServletResponse response) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";
        try {
            folderService.createFolder(user, name, colour);
            return "redirect:/notes";
        } catch (IllegalArgumentException invalid) {
            model.addAttribute("newFolderDraft", name); model.addAttribute("notesFolderInvalid", true);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return index(session, model);
        }
    }

    @PostMapping("/folders/{id}/rename")
    public String renameFolder(@PathVariable Long id, @RequestParam String name,
                               HttpSession session, Model model, HttpServletResponse response) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";
        folderService.getFolderForUser(user, id);
        try {
            folderService.renameFolder(user, id, name);
            return "redirect:/notes/folders/" + id;
        } catch (IllegalArgumentException invalid) {
            model.addAttribute("renameFolderDraft", name); model.addAttribute("notesFolderInvalid", true);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return folderView(id, null, 1, session, model);
        }
    }

    @PostMapping("/folders/{id}/delete")
    public String deleteFolder(@PathVariable Long id, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        folderService.deleteFolder(user, id);
        return "redirect:/notes";
    }

    @GetMapping("/folders/{folderId}/new")
    public String newNote(@PathVariable Long folderId,
                          Model model,
                          HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        model.addAttribute("folders", folderService.getFoldersForUser(user));
        NoteFolder folder = folderService.getFolderForUser(user, folderId);
        model.addAttribute("folder", folder);
        model.addAttribute("note", new Note());
        model.addAttribute("notePlainText", "");
        return "shared-views/notes/note-form";
    }

    @PostMapping("/folders/{folderId}/new")
    public String createNote(@PathVariable Long folderId,
                             @RequestParam(name = "folderId", required = false) Long selectedFolderId,
                             @RequestParam String title, @RequestParam String content,
                             @RequestParam(required = false) String plainContent,
                             @RequestParam(value="noteColour", required=false) String noteColour,
                             HttpSession session, Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";
        Long destinationId = selectedFolderId == null ? folderId : selectedFolderId;
        NoteFolder folder = folderService.getFolderForUser(user, destinationId);
        if (plainContent != null) content = plainMarkup(plainContent);
        try {
            Note note = noteService.create(user, destinationId, title, content, noteColour);
            levelService.addPoints(user, 5);
            redirect.addFlashAttribute("noteSaved", true);
            return "redirect:/notes/" + note.getId();
        } catch (IllegalArgumentException invalid) {
            return rejectedNote(model, response, user, null, folder, title, content, noteColour);
        }
    }

    @GetMapping("/{id}")
    public String viewNote(@PathVariable Long id,
                           Model model,
                           HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.getNoteForUser(user, id);
        model.addAttribute("note", note);
        return "shared-views/notes/note-view";
    }

    @GetMapping("/{id}/edit")
    public String editNoteForm(@PathVariable Long id,
                               Model model,
                               HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.getNoteForUser(user, id);
        model.addAttribute("note", note);
        model.addAttribute("notePlainText", org.jsoup.Jsoup.parse(note.getContent()).wholeText());
        model.addAttribute("folder", note.getFolder());
        model.addAttribute("folders", folderService.getFoldersForUser(user));
        return "shared-views/notes/note-form";
    }

    @PostMapping("/{id}/edit")
    public String updateNote(@PathVariable Long id,
                             @RequestParam String title, @RequestParam String content,
                             @RequestParam(required = false) String plainContent,
                             @RequestParam(value="noteColour", required=false) String noteColour,
                             @RequestParam(required = false) Long folderId,
                             @RequestParam(required = false) String revision,
                             HttpSession session, Model model, HttpServletResponse response, RedirectAttributes redirect) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) return "redirect:/login";
        Note existing = noteService.getNoteForUser(user, id);
        NoteFolder destination = folderId == null ? existing.getFolder() : folderService.getFolderForUser(user, folderId);
        if (plainContent != null) content = plainMarkup(plainContent);
        try {
            noteService.updateChecked(user, id, title, content, folderId, noteColour, revision);
            levelService.addPoints(user, 2);
            redirect.addFlashAttribute("noteSaved", true);
            return "redirect:/notes/" + id;
        } catch (IllegalArgumentException invalid) {
            model.addAttribute("noteRevision", revision == null ? existing.getRevision() : revision);
            return rejectedNote(model, response, user, id, destination, title, content, noteColour);
        } catch (StaleNoteException stale) {
            Note current = noteService.getNoteForUser(user, id);
            model.addAttribute("noteConflict", true);
            model.addAttribute("currentNote", current);
            model.addAttribute("noteRevision", current.getRevision());
            String view = rejectedNote(model, response, user, id, destination, title, content, noteColour);
            response.setStatus(HttpServletResponse.SC_CONFLICT);
            return view;
        }
    }

    private String rejectedNote(Model model, HttpServletResponse response, User user, Long id, NoteFolder folder,
                                String title, String content, String colour) {
        Note draft = new Note(); draft.setId(id); draft.setFolder(folder); draft.setTitle(title);
        draft.setContent(noteSanitizer.sanitize(content)); draft.setColour(colour);
        model.addAttribute("note", draft); model.addAttribute("notePlainText", org.jsoup.Jsoup.parse(draft.getContent()).wholeText());
        model.addAttribute("folder", folder);
        model.addAttribute("folders", folderService.getFoldersForUser(user)); model.addAttribute("noteInvalid", true);
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        return "shared-views/notes/note-form";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<java.util.Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(java.util.Map.of("error", "INVALID_INPUT"));
    }

    @ExceptionHandler(StaleNoteException.class)
    @ResponseBody
    public ResponseEntity<java.util.Map<String, String>> staleInput() {
        return ResponseEntity.status(409).body(java.util.Map.of("error", "STALE_NOTE"));
    }

    @PostMapping("/{id}/delete")
    public String deleteNote(@PathVariable Long id,
                             HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        noteService.delete(user, id);
        return "redirect:/notes";
    }

    // -------- Notes v2 API --------

    private org.springframework.data.domain.Page<Note> populatePage(Model model, User user, Long folder, String q, int page, String path) {
        String query = q == null ? "" : q.trim();
        if (query.length() > 120) query = query.substring(0, 120);
        var result = noteService.searchPage(user, folder, query, page);
        model.addAttribute("notes", result.getContent());
        model.addAttribute("notesPage", result);
        model.addAttribute("notesPagingPath", path);
        model.addAttribute("notesPagingFolder", folder);
        model.addAttribute("q", query);
        return result;
    }

    @GetMapping("/api/notes/page")
    @ResponseBody
    public NotePageDto pagedNotes(@RequestParam(required = false) Long folderId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(defaultValue = "1") int page, HttpSession session) {
        return NotePageDto.from(noteService.searchPage(authHelper.getAuthenticatedUser(session), folderId, q, page));
    }

    @GetMapping("/api/folders")
    @ResponseBody
    public List<NoteFolderDto> listFolders(HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) {
            return List.of();
        }
        folderService.ensureDefaults(user);
        return folderService.getFoldersForUser(user).stream()
                .map(NoteFolderDto::from)
                .toList();
    }

    @PostMapping("/api/folders")
    @ResponseBody
    public NoteFolderDto createFolder(@RequestBody NoteFolderCreateRequest request, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        NoteFolder folder = folderService.createFolder(user, request.getName(), request.getColour());
        return NoteFolderDto.from(folder);
    }

    @PostMapping("/api/folders/{id}/rename")
    @ResponseBody
    public NoteFolderDto renameFolder(@PathVariable Long id,
                                      @RequestBody NoteFolderRenameRequest request,
                                      HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        NoteFolder folder = folderService.renameFolder(user, id, request.getName());
        return NoteFolderDto.from(folder);
    }

    @DeleteMapping("/api/folders/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteFolderApi(@PathVariable Long id, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        folderService.deleteFolder(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/notes")
    @ResponseBody
    public List<NoteSummaryDto> listNotes(@RequestParam(required = false) Long folderId,
                                          @RequestParam(required = false) String q,
                                          HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        if (user == null) {
            return List.of();
        }
        return noteService.search(user, folderId, q).stream()
                .map(NoteSummaryDto::from)
                .toList();
    }

    @GetMapping("/api/notes/{id}")
    @ResponseBody
    public NoteDetailDto getNote(@PathVariable Long id, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.getNoteForUser(user, id);
        return NoteDetailDto.from(note);
    }

    @PostMapping("/api/notes")
    @ResponseBody
    public NoteDetailDto createNote(@RequestBody NoteCreateRequest request, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.create(user, request.getFolderId(), request.getTitle(), request.getContent(), request.getColour());
        levelService.addPoints(user, 2);
        return NoteDetailDto.from(note);
    }

    @PostMapping("/api/notes/{id}")
    @ResponseBody
    public NoteDetailDto updateNote(@PathVariable Long id,
                                    @RequestBody NoteUpdateRequest request,
                                    HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.updateChecked(user, id, request.getTitle(), request.getContent(), request.getFolderId(), request.getColour(), request.getRevision());
        return NoteDetailDto.from(note);
    }

    @DeleteMapping("/api/notes/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteNoteApi(@PathVariable Long id, HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        noteService.delete(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export/{id}")
    public ResponseEntity<byte[]> exportNote(@PathVariable Long id,
                                             @RequestParam(defaultValue = "html") String format,
                                             HttpSession session) {
        User user = authHelper.getAuthenticatedUser(session);
        Note note = noteService.getNoteForUser(user, id);

        String safeTitle = note.getTitle() != null ? note.getTitle().trim().replaceAll("[^a-zA-Z0-9-_ ]", "") : "note";
        String filename = safeTitle.isBlank() ? "note" : safeTitle;

        if (!"html".equalsIgnoreCase(format)) {
            format = "html";
        }

        String html = """
                <!doctype html>
                <html lang=\"en\">
                <head><meta charset=\"utf-8\"><title>%s</title></head>
                <body><h1>%s</h1><article>%s</article></body>
                </html>
                """.formatted(escapeHtml(note.getTitle()), escapeHtml(note.getTitle()), note.getContent());

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".html\"")
                .contentType(MediaType.TEXT_HTML)
                .contentLength(bytes.length)
                .body(bytes);
    }

    private String plainMarkup(String text) {
        return "<p>" + escapeHtml(text).replace("\r\n", "\n").replace("\n", "<br>") + "</p>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
