package uk.ac.cf._5.group14.One_To_One.ChatV2;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.HttpStatus;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import java.time.Clock;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.CalendarData.CalendarTask;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatContext;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatContextService;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatPromptBuilder;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatResponse;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatService;
import uk.ac.cf._5.group14.One_To_One.Notes.NoteRepository;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Controller
@RequestMapping("/chatv2")
public class ChatV2Controller {

    private final AuthHelper authHelper;
    private final ChatV2ThreadService threadService;
    private final ChatV2ActionExecutor actionExecutor;
    private final ChatV2AiResponseParser aiResponseParser;
    private final ChatService chatService;
    private final ChatContextService chatContextService;
    private final NoteRepository noteRepository;
    private final AccessGuard accessGuard;
    private final UserService userService;
    private final PlatformSubscriptionService subscriptions;
    private final Clock clock;

    public ChatV2Controller(AuthHelper authHelper,
                            ChatV2ThreadService threadService,
                            ChatV2ActionExecutor actionExecutor,
                            ChatV2AiResponseParser aiResponseParser,
                            ChatService chatService,
                            ChatContextService chatContextService,
                            NoteRepository noteRepository,
                            AccessGuard accessGuard,
                            UserService userService, PlatformSubscriptionService subscriptions, Clock clock) {
        this.authHelper = authHelper;
        this.threadService = threadService;
        this.actionExecutor = actionExecutor;
        this.aiResponseParser = aiResponseParser;
        this.chatService = chatService;
        this.chatContextService = chatContextService;
        this.noteRepository = noteRepository;
        this.accessGuard = accessGuard;
        this.userService = userService;
        this.subscriptions = subscriptions;
        this.clock = clock;
    }

    @GetMapping
    public String hub() {
        requireUser();
        return "redirect:/chat";
    }

    @GetMapping("/history")
    public String history(@RequestParam(defaultValue = "") String q,
                          @RequestParam(defaultValue = "1") int page, Model model, Locale locale) {
        User user = requireUser();
        addHistoryList(model, user, null, q, page, locale);
        return "shared-views/chat/hub";
    }

    @GetMapping("/history/folder/{folderId}")
    public String historyFolder(@PathVariable Long folderId, @RequestParam(defaultValue = "") String q,
                                @RequestParam(defaultValue = "1") int page, Model model, Locale locale) {
        User user = requireUser();
        ChatFolder folder = threadService.findFolder(user, folderId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        addHistoryList(model, user, folder, q, page, locale);
        model.addAttribute("folder", folder);
        return "shared-views/chat/folder";
    }

    @GetMapping("/history/thread/{threadId}")
    public String historyThread(@PathVariable Long threadId,
                                @RequestParam(required = false) Integer page,
                                @RequestParam(defaultValue = "1") int returnPage,
                                @RequestParam(required = false) Long collection,
                                @RequestParam(defaultValue = "") String q, Model model, Locale locale) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if ((page != null && page < 1) || returnPage < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (collection != null) threadService.findFolder(user, collection).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        model.addAttribute("folders", threadService.listFolders(user));
        model.addAttribute("searchQuery", historySearch(q));
        model.addAttribute("historyReturnPage", returnPage);
        model.addAttribute("historyCollectionId", collection);
        model.addAttribute("thread", thread);
        var messages = threadService.historyMessagesPage(thread, page);
        model.addAttribute("messages", messages.getContent());
        model.addAttribute("historyPagination", messages);
        model.addAttribute("historyPageKey", "ui.charlie.archive.messagePage");
        model.addAttribute("historyPagingPath", "/chatv2/history/thread/" + threadId);
        model.addAttribute("historyListPath", collection == null ? "/chatv2/history" : "/chatv2/history/folder/" + collection);
        var dates = historyDateFormat(locale);
        model.addAttribute("historyMessageDates", messages.stream().collect(Collectors.toMap(ChatMessage::getId, message -> dates.format(message.getCreatedAt()))));
        return "shared-views/chat/thread";
    }

    private void addHistoryList(Model model, User user, ChatFolder folder, String query, int page, Locale locale) {
        if (page < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String search = historySearch(query);
        model.addAttribute("searchQuery", search);
        model.addAttribute("historyCollectionId", folder == null ? null : folder.getId());
        model.addAttribute("folders", threadService.listFolders(user));
        var history = threadService.historyPage(user, folder, search, page);
        model.addAttribute("threads", history.getContent());
        model.addAttribute("historyPagination", history);
        model.addAttribute("historyPageKey", "ui.charlie.archive.page");
        model.addAttribute("historyPagingPath", folder == null ? "/chatv2/history" : "/chatv2/history/folder/" + folder.getId());
        var dates = historyDateFormat(locale);
        model.addAttribute("historyThreadDates", history.stream().collect(Collectors.toMap(ChatThread::getId, thread -> dates.format(thread.getUpdatedAt()))));
    }

    private static String historySearch(String query) {
        String search = query == null ? "" : query.trim();
        return search.length() > 120 ? search.substring(0, 120) : search;
    }

    private static DateTimeFormatter historyDateFormat(Locale locale) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidInput() {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_INPUT"));
    }

    @PostMapping("/new")
    public String createThread(@RequestParam(name = "folderId", required = false) Long folderId) {
        User user = requireUser();
        ChatFolder folder = folderId != null ? threadService.findFolder(user, folderId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)) : null;
        threadService.createThread(user, folder);
        return "redirect:/chat";
    }

    @GetMapping("/folder/{folderId}")
    public String folder(@PathVariable Long folderId) {
        User user = requireUser();
        threadService.findFolder(user, folderId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        return "redirect:/chat";
    }

    @PostMapping("/folder/new")
    public String createFolder(@RequestParam String name,
                               @RequestParam String colorHex,
                               @RequestParam String iconKey) {
        User user = requireUser();
        String safeIcon = ChatV2IconRegistry.iconMap().containsKey(iconKey) ? iconKey : "chat";
        String safeColor = colorHex == null || colorHex.isBlank() ? "#0f172a" : colorHex.trim();
        threadService.createFolder(user, name, safeColor, safeIcon);
        return "redirect:/chat";
    }

    @PostMapping("/folder/{folderId}/settings")
    public String updateFolder(@PathVariable Long folderId,
                               @RequestParam String name,
                               @RequestParam String colorHex,
                               @RequestParam String iconKey) {
        User user = requireUser();
        ChatFolder folder = threadService.findFolder(user, folderId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        String safeIcon = ChatV2IconRegistry.iconMap().containsKey(iconKey) ? iconKey : folder.getIconKey();
        String safeColor = colorHex == null || colorHex.isBlank() ? folder.getColorHex() : colorHex.trim();
        threadService.updateFolder(folder, name, safeColor, safeIcon);
        return "redirect:/chat";
    }

    @GetMapping("/{threadId}")
    public String thread(@PathVariable Long threadId) {
        User user = requireUser();
        threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        return "redirect:/chat";
    }

    @PostMapping("/{threadId}/rename")
    @ResponseBody
    public ResponseEntity<?> rename(@PathVariable Long threadId, @RequestParam String title) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        threadService.updateThreadSettings(thread, title, null, null, null, null, null);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{threadId}/settings")
    @ResponseBody
    public ResponseEntity<?> settings(@PathVariable Long threadId,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) String colorHex,
                                      @RequestParam(required = false) String iconKey,
                                      @RequestParam(required = false) Boolean pinned,
                                      @RequestParam(required = false) Boolean archived,
                                      @RequestParam(required = false) String customInstructions) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        String safeIcon = iconKey != null && ChatV2IconRegistry.iconMap().containsKey(iconKey) ? iconKey : null;
        threadService.updateThreadSettings(thread, title, colorHex, safeIcon, pinned, archived, customInstructions);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{threadId}/move")
    @ResponseBody
    public ResponseEntity<?> move(@PathVariable Long threadId, @RequestParam(required = false) Long folderId) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        ChatFolder folder = folderId != null ? threadService.findFolder(user, folderId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND)) : null;
        threadService.moveThread(thread, folder);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/{threadId}/message")
    @ResponseBody
    public ResponseEntity<ChatV2Response> message(@PathVariable Long threadId, @RequestBody Map<String, String> payload) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));

        String message = payload != null ? payload.getOrDefault("message", "") : "";
        if (message == null || message.isBlank() || message.trim().length() > 4000) {
            return ResponseEntity.badRequest().body(new ChatV2Response("Message required", List.of(), List.of()));
        }

        if (thread.getType() == ChatType.TRAINER_CLIENT) {
            if (thread.getPeerUserId() == null) {
                return ResponseEntity.status(500).body(new ChatV2Response("System Error: Peer ID not set on this thread", List.of(), List.of()));
            }
            try {
                accessGuard.requireActiveRelationship(user.getId(), thread.getPeerUserId());
            } catch (org.springframework.security.access.AccessDeniedException e) {
                return ResponseEntity.status(403).body(new ChatV2Response("Messaging is disabled because the relationship is not active.", List.of(), List.of()));
            }
            if (uk.ac.cf._5.group14.One_To_One.Messaging.PaymentKeywordDetector.firstMatch(message) != null) {
                return ResponseEntity.unprocessableEntity().body(new ChatV2Response("OFF_PLATFORM_PAYMENT", List.of(), List.of()));
            }
            threadService.appendMessage(thread, ChatMessageRole.USER, message);
            threadService.updateTitleIfNew(thread, message);
            return ResponseEntity.ok(new ChatV2Response("", List.of(), List.of()));
        }

        requirePremium(user);
        ChatV2CommandParser.ParsedCommand parsed = ChatV2CommandParser.parse(message);
        if (parsed == null && !chatService.isAvailable()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ChatV2Response("AI_UNAVAILABLE", List.of(), List.of()));
        }
        threadService.appendMessage(thread, ChatMessageRole.USER, message);
        threadService.updateTitleIfNew(thread, message);

        if (parsed != null) {
            return ResponseEntity.ok(handleCommand(user, parsed));
        }

        ChatContext ctx = chatContextService.build(user, null);
        String systemPrompt = ChatPromptBuilder.buildSystemPrompt(ctx, thread.getCustomInstructions())
            + "\n" + buildStructuredResponseSpec();
        List<ChatService.Message> msgs = new ArrayList<>();
        msgs.add(new ChatService.Message("system", systemPrompt));
        List<ChatMessage> history = threadService.listMessages(thread);
        for (ChatMessage m : history) {
            String role = m.getRole() == ChatMessageRole.USER ? "user" : "assistant";
            msgs.add(new ChatService.Message(role, m.getContent()));
        }

        ChatResponse ai = chatService.chat(msgs);
        String reply = ai != null ? ai.reply() : "AI is unavailable right now.";
        ChatV2Response structured = aiResponseParser.tryParse(reply);
        if (structured == null) {
            threadService.appendMessage(thread, ChatMessageRole.ASSISTANT, reply);
            return ResponseEntity.ok(new ChatV2Response(reply, List.of(), List.of()));
        }

        List<ChatV2ActionResult> executed = new ArrayList<>();
        if (structured.actions() != null) {
            structured.actions().forEach(action -> {
                executed.add(new ChatV2ActionResult(action.type(), action.payload(), "PENDING", "Review before applying"));
            });
        }

        String assistantText = structured.assistantText() != null ? structured.assistantText() : "";
        if (!assistantText.isBlank()) threadService.appendMessage(thread, ChatMessageRole.ASSISTANT, assistantText);
        return ResponseEntity.ok(new ChatV2Response(assistantText, structured.blocks(), executed));
    }

    @PostMapping("/{threadId}/actions")
    @ResponseBody
    public ResponseEntity<ChatV2ActionResult> action(@PathVariable Long threadId, @RequestBody ChatV2ActionRequest request) {
        User user = requireUser();
        ChatThread thread = threadService.findThread(user, threadId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (thread.getType() != ChatType.AI_PERSONAL) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        requirePremium(user);
        if (request == null || request.type() == null || request.payload() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        ChatV2ActionResult result = actionExecutor.execute(user, request.type(), request.payload());
        return ResponseEntity.ok(result);
    }

    private ChatV2Response handleCommand(User user, ChatV2CommandParser.ParsedCommand parsed) {
        List<ChatV2Block> blocks = new ArrayList<>();
        List<ChatV2ActionResult> actions = new ArrayList<>();
        switch (parsed.type()) {
            case "TASK_LIST" -> {
                List<CalendarTask> tasks = actionExecutor.listTodaysTasks(user);
                List<ChatV2BlockItem> items = tasks.stream()
                    .map(t -> new ChatV2BlockItem(t.getTitle(), t.getDate() + (t.getTime() != null ? " " + t.getTime() : ""), t.getCompleted() ? "done" : "open", t.getId()))
                    .toList();
                blocks.add(new ChatV2Block("tasks", "Today", items));
            }
            case "NOTE_SEARCH" -> {
                String q = parsed.payload().getOrDefault("query", "").toString();
                var notes = noteRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                    .filter(n -> (n.getTitle() != null && n.getTitle().toLowerCase().contains(q.toLowerCase()))
                        || (n.getContent() != null && n.getContent().toLowerCase().contains(q.toLowerCase())))
                    .limit(5)
                    .toList();
                List<ChatV2BlockItem> items = notes.stream()
                    .map(n -> new ChatV2BlockItem(n.getTitle(), n.getContent(), "note", n.getId()))
                    .toList();
                blocks.add(new ChatV2Block("notes", "Notes", items));
            }
            case "SCHEDULE_LIST" -> {
                var occs = actionExecutor.listUpcomingSchedules(user);
                List<ChatV2BlockItem> items = new ArrayList<>();
                occs.forEach(o -> {
                    if (o instanceof uk.ac.cf._5.group14.One_To_One.ScheduleData.ScheduleOccurrence occ) {
                        String label = occ.getScheduleName();
                        String value = occ.getDate() != null ? occ.getDate().toString() : "";
                        items.add(new ChatV2BlockItem(label, value, "schedule", occ.getId()));
                    }
                });
                blocks.add(new ChatV2Block("schedule", "Upcoming schedules", items));
            }
            case "UNKNOWN" -> {
                return new ChatV2Response(parsed.assistantText(), List.of(), List.of());
            }
            default -> {
                ChatV2ActionResult result = actionExecutor.execute(user, parsed.type(), parsed.payload());
                actions.add(result);
            }
        }
        return new ChatV2Response(parsed.assistantText(), blocks, actions);
    }

    private void requirePremium(User user) {
        if (!subscriptions.isPremium(user.getId(), clock)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Premium required");
        }
    }

    private User requireUser() {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                throw new ResponseStatusException(UNAUTHORIZED);
            }
            user = userService.findByUsername(authentication.getName());
            if (user == null) {
                throw new ResponseStatusException(UNAUTHORIZED);
            }
        }
        return user;
    }

    private String buildStructuredResponseSpec() {
        return String.join("\n",
                "When helpful, respond with a JSON object that matches this schema:",
                "{",
                "  \"assistantText\": \"...\",",
                "  \"actions\": [",
                "    { \"type\": \"TASK_CREATE\", \"payload\": { \"title\": \"...\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:MM\" } },",
                "    { \"type\": \"TASK_COMPLETE\", \"payload\": { \"taskId\": \"123\" } },",
                "    { \"type\": \"NOTE_CREATE\", \"payload\": { \"title\": \"...\", \"content\": \"...\" } },",
                "    { \"type\": \"SCHEDULE_APPLY\", \"payload\": { \"scheduleId\": \"456\" } }",
                "  ],",
                "  \"blocks\": [",
                "    { \"type\": \"tasks\", \"title\": \"Today\", \"items\": [ { \"label\": \"Task\", \"value\": \"2026-02-05 09:00\", \"status\": \"open\", \"id\": 1 } ] },",
                "    { \"type\": \"notes\", \"title\": \"Notes\", \"items\": [ { \"label\": \"Title\", \"value\": \"Body\", \"status\": \"draft\", \"id\": null } ] },",
                "    { \"type\": \"schedule\", \"title\": \"Upcoming\", \"items\": [ { \"label\": \"Session\", \"value\": \"2026-02-05\", \"status\": \"open\", \"id\": 99 } ] }",
                "  ]",
                "}",
                "Return ONLY valid JSON when using this structured format.");
    }
}
