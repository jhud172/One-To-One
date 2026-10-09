package uk.ac.cf._5.group14.One_To_One.Vault;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.Repository.WorkoutSessionRepository;
import uk.ac.cf._5.group14.One_To_One.StrengthLog.WorkoutSession;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/vault")
public class VaultController {
    private static final String[] MOODS = {"GREAT", "GOOD", "NEUTRAL", "LOW", "POOR"};
    private final AuthHelper authHelper;
    private final UserService userService;
    private final VaultNoteService notes;
    private final VaultAiService ai;
    private final WorkoutSessionRepository sessions;
    private final PlatformSubscriptionService subscriptions;
    private final Clock clock;

    public VaultController(AuthHelper authHelper, UserService userService, VaultNoteService notes,
                           VaultAiService ai, WorkoutSessionRepository sessions,
                           PlatformSubscriptionService subscriptions, Clock clock) {
        this.authHelper = authHelper; this.userService = userService; this.notes = notes;
        this.ai = ai; this.sessions = sessions; this.subscriptions = subscriptions; this.clock = clock;
    }

    private User currentUser() {
        User user = authHelper.getAuthenticatedUser();
        if (user != null) return user;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Not authenticated");
        user = userService.findByUsername(auth.getName());
        if (user == null) throw new AccessDeniedException("User not found");
        return user;
    }

    @GetMapping
    public String index(@RequestParam(required = false) String type,
                        @RequestParam(required = false) String search,
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to,
                        @RequestParam(defaultValue = "false") boolean pinned,
                        @RequestParam(defaultValue = "1") int page,
                        Model model, HttpServletResponse response) {
        User user = currentUser();
        List<VaultNote> found = List.of();
        VaultNoteType selectedType = null;
        VaultNotePage library = new VaultNotePage(List.of(), 1, 1, 0);
        try {
            selectedType = parseType(type);
            library = notes.searchPage(user.getId(), search, selectedType, pinned, parseDate(from), parseDate(to), page);
            found = library.notes();
        } catch (IllegalArgumentException ex) {
            response.setStatus(400); model.addAttribute("vaultInvalid", true);
        }
        model.addAttribute("noteTypes", VaultNoteType.values());
        model.addAttribute("selectedType", selectedType);
        model.addAttribute("notes", found);
        model.addAttribute("sessionsById", loadSessions(found, user));
        model.addAttribute("searchQuery", search);
        model.addAttribute("fromDate", from);
        model.addAttribute("toDate", to);
        model.addAttribute("pinnedOnly", pinned);
        model.addAttribute("vaultPage", library);
        model.addAttribute("vaultReturnTo", libraryUrl(type, search, from, to, pinned, library.page()));
        model.addAttribute("vaultPrevious", libraryUrl(type, search, from, to, pinned, Math.max(1, library.page() - 1)));
        model.addAttribute("vaultNext", libraryUrl(type, search, from, to, pinned, library.page() + 1));
        model.addAttribute("metrics", notes.getMetrics(user.getId()));
        aiState(model, user);
        return "shared-views/vault/index";
    }

    @GetMapping("/new")
    public String newNote(@RequestParam(required = false) String returnTo, Model model) {
        form(model, currentUser(), null, Map.of("returnTo", safeReturnTo(returnTo, "/vault")));
        return "shared-views/vault/note-form";
    }

    @PostMapping("/new")
    public String create(@RequestParam Map<String, String> values, Model model,
                         HttpServletResponse response, RedirectAttributes flash) {
        return save(null, values, model, response, flash);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, @RequestParam(required = false) String returnTo, Model model) {
        User user = currentUser(); form(model, user, owned(id, user), Map.of("returnTo", safeReturnTo(returnTo, "/vault")));
        return "shared-views/vault/note-form";
    }

    @PostMapping("/{id}/edit")
    public String update(@PathVariable Long id, @RequestParam Map<String, String> values,
                         Model model, HttpServletResponse response, RedirectAttributes flash) {
        return save(id, values, model, response, flash);
    }

    private String save(Long id, Map<String, String> values, Model model,
                        HttpServletResponse response, RedirectAttributes flash) {
        User user = currentUser();
        VaultNote original = id == null ? null : owned(id, user);
        try {
            VaultNoteType type = parseType(values.get("noteType"));
            if (type == null) throw new IllegalArgumentException("Select a type");
            LocalDate date = parseDate(values.get("linkedDate"));
            Long session = parseId(values.get("linkedWorkoutSessionId"));
            VaultNote saved = id == null
                    ? notes.create(user.getId(), type, values.get("title"), values.get("content"), date, session, values.get("tags"), values.get("mood"))
                    : notes.updateChecked(id, user.getId(), type, values.get("title"), values.get("content"), date, session, values.get("tags"), values.get("mood"), values.get("revision"))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            flash.addFlashAttribute("vaultSaved", true);
            return "redirect:" + detailUrl(saved.getId(), safeReturnTo(values.get("returnTo"), "/vault"));
        } catch (StaleReflectionException ex) {
            response.setStatus(409);
            VaultNote current = owned(id, user);
            var retained = new HashMap<>(values);
            // The displayed comparison is the only route to an intentional subsequent save.
            retained.put("revision", current.getRevision());
            form(model, user, current, retained);
            model.addAttribute("vaultConflict", current);
            model.addAttribute("vaultConflictSession", current.getLinkedWorkoutSessionId() == null ? null :
                    sessions.findById(current.getLinkedWorkoutSessionId()).filter(s -> isOwned(s, user)).orElse(null));
            return "shared-views/vault/note-form";
        } catch (IllegalArgumentException ex) {
            response.setStatus(400); form(model, user, original, values);
            model.addAttribute("vaultInvalid", true); return "shared-views/vault/note-form";
        }
    }

    private void form(Model model, User user, VaultNote original, Map<String, String> draft) {
        Map<String, String> values = new HashMap<>();
        values.put("noteType", original == null ? "TRAINING" : original.getNoteType().name());
        if (original != null) {
            values.put("revision", original.getRevision());
            values.put("title", original.getTitle()); values.put("content", original.getContent());
            values.put("linkedDate", Objects.toString(original.getLinkedDate(), ""));
            values.put("linkedWorkoutSessionId", Objects.toString(original.getLinkedWorkoutSessionId(), ""));
            values.put("tags", original.getTags()); values.put("mood", original.getMood());
        }
        if (draft != null) values.putAll(draft);
        values.put("returnTo", safeReturnTo(values.get("returnTo"), "/vault"));
        var recent = new ArrayList<>(sessions.findTop20ByUserOrderByDateDesc(user));
        Long draftSession = null;
        try { draftSession = parseId(values.get("linkedWorkoutSessionId")); } catch (IllegalArgumentException ignored) { }
        if (draftSession != null) {
            var linked = sessions.findById(draftSession).filter(s -> isOwned(s, user));
            linked.filter(s -> recent.stream().noneMatch(r -> r.getId().equals(s.getId()))).ifPresent(recent::add);
        }
        model.addAttribute("editingId", original == null ? null : original.getId());
        model.addAttribute("draft", values); model.addAttribute("noteTypes", VaultNoteType.values());
        model.addAttribute("moods", MOODS); model.addAttribute("recentSessions", recent);
        model.addAttribute("vaultReturnTo", values.get("returnTo"));
        model.addAttribute("vaultDetailReturnTo", original == null ? values.get("returnTo") : detailUrl(original.getId(), values.get("returnTo")));
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, @RequestParam(required = false) String returnTo, Model model) {
        User user = currentUser(); VaultNote note = owned(id, user);
        model.addAttribute("note", note);
        model.addAttribute("vaultReturnTo", safeReturnTo(returnTo, "/vault"));
        model.addAttribute("vaultDetailReturnTo", detailUrl(id, safeReturnTo(returnTo, "/vault")));
        model.addAttribute("linkedSession", note.getLinkedWorkoutSessionId() == null ? null
                : sessions.findById(note.getLinkedWorkoutSessionId()).filter(s -> isOwned(s, user)).orElse(null));
        aiState(model, user); return "shared-views/vault/note-view";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(required = false) String returnTo, RedirectAttributes flash) {
        User user = currentUser(); owned(id, user);
        if (!notes.delete(id, user.getId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        flash.addFlashAttribute("vaultDeleted", true); return "redirect:" + safeReturnTo(returnTo, "/vault");
    }

    @PostMapping("/{id}/pin")
    public String pin(@PathVariable Long id, @RequestParam(required = false) String returnTo) {
        User user = currentUser(); owned(id, user);
        notes.togglePin(id, user.getId()); return "redirect:" + safeReturnTo(returnTo, "/vault/" + id);
    }

    @PostMapping("/ai/summarise-week")
    public String summarise(@RequestParam(required = false) List<Long> noteIds,
                            @RequestParam(required = false) String returnTo,
                            @RequestParam(defaultValue = "false") boolean aiConsent, RedirectAttributes flash) {
        return generate(noteIds, returnTo, aiConsent, "summary", false, flash);
    }

    @PostMapping("/ai/rewrite-checkin")
    public String rewrite(@RequestParam(required = false) List<Long> noteIds,
                          @RequestParam(required = false) String returnTo,
                          @RequestParam(defaultValue = "false") boolean aiConsent, RedirectAttributes flash) {
        return generate(noteIds, returnTo, aiConsent, "rewrite", false, flash);
    }

    @PostMapping("/ai/insight/{id}")
    public String insight(@PathVariable Long id, @RequestParam(required = false) String returnTo,
                          @RequestParam(defaultValue = "false") boolean aiConsent, RedirectAttributes flash) {
        return generate(List.of(id), safeReturnTo(returnTo, "/vault/" + id), aiConsent, "insight", true, flash);
    }

    private String generate(List<Long> noteIds, String returnTo, boolean consent,
                            String kind, boolean persist, RedirectAttributes flash) {
        User user = currentUser();
        List<Long> ids = noteIds == null ? List.of() : noteIds.stream().filter(Objects::nonNull).distinct().toList();
        String back = safeReturnTo(returnTo, "/vault");
        if (ids.isEmpty() || ids.size() > 20) return aiError(flash, "selection", back);
        List<VaultNote> selected = notes.getManyForUser(ids, user.getId());
        if (selected.size() != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!consent) return aiError(flash, "consent", back);
        if (!subscriptions.isPremium(user.getId(), clock)) return aiError(flash, "premium", back);
        if (!ai.isAvailable()) return aiError(flash, "unavailable", back);
        String sourceRevision = persist ? selected.getFirst().getRevision() : null;
        try {
            String result = switch (kind) {
                case "summary" -> ai.summariseWeek(selected);
                case "rewrite" -> ai.rewriteCheckin(selected);
                default -> ai.generateInsight(selected.getFirst());
            };
            if (persist) notes.saveAiSummaryChecked(ids.getFirst(), user.getId(), result, sourceRevision)
                    .orElseThrow(() -> new StaleReflectionException());
            flash.addFlashAttribute("aiResultKind", kind); flash.addFlashAttribute("aiResult", result);
            flash.addFlashAttribute("aiResultGeneratedAt", clock.instant());
            flash.addFlashAttribute("aiResultTitles", selected.stream().map(VaultNote::getTitle).toList());
            return "redirect:" + back;
        } catch (StaleReflectionException ex) {
            return aiError(flash, "stale", back);
        } catch (IllegalArgumentException ex) {
            return aiError(flash, "selection", back);
        } catch (RuntimeException ex) {
            return aiError(flash, "unavailable", back);
        }
    }

    private String aiError(RedirectAttributes flash, String code, String back) {
        flash.addFlashAttribute("vaultAiError", code); return "redirect:" + back;
    }

    private void aiState(Model model, User user) {
        boolean premium = subscriptions.isPremium(user.getId(), clock);
        model.addAttribute("vaultPremium", premium);
        model.addAttribute("vaultAiAvailable", ai.isAvailable());
        model.addAttribute("vaultAiEnabled", premium && ai.isAvailable());
    }

    private VaultNote owned(Long id, User user) {
        return notes.getForUser(id, user.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private boolean isOwned(WorkoutSession session, User user) {
        return session.getUser() != null && Objects.equals(session.getUser().getId(), user.getId());
    }

    private Map<Long, WorkoutSession> loadSessions(List<VaultNote> reflections, User user) {
        List<Long> ids = reflections.stream().map(VaultNote::getLinkedWorkoutSessionId).filter(Objects::nonNull).distinct().toList();
        Map<Long, WorkoutSession> result = new HashMap<>();
        for (var session : sessions.findAllById(ids)) if (isOwned(session, user)) result.put(session.getId(), session);
        return result;
    }

    private VaultNoteType parseType(String type) {
        return type == null || type.isBlank() ? null : VaultNoteType.valueOf(type);
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value); } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid date"); }
    }

    private Long parseId(String value) {
        return value == null || value.isBlank() ? null : Long.valueOf(value);
    }

    private String safeReturnTo(String value, String fallback) {
        return value != null && value.matches("/vault(?:/[0-9]+)?(?:\\?[^\\r\\n\\\\]*)?") ? value : fallback;
    }

    private String libraryUrl(String type, String search, String from, String to, boolean pinned, int page) {
        var uri = UriComponentsBuilder.fromPath("/vault");
        if (type != null && !type.isBlank()) uri.queryParam("type", type);
        if (search != null && !search.isBlank()) uri.queryParam("search", search);
        if (from != null && !from.isBlank()) uri.queryParam("from", from);
        if (to != null && !to.isBlank()) uri.queryParam("to", to);
        if (pinned) uri.queryParam("pinned", true);
        if (page > 1) uri.queryParam("page", page);
        return uri.build().encode().toUriString();
    }

    private String detailUrl(Long id, String back) {
        return "/vault".equals(back) ? "/vault/" + id : UriComponentsBuilder.fromPath("/vault/" + id)
                .queryParam("returnTo", back).build().encode().toUriString();
    }
}
