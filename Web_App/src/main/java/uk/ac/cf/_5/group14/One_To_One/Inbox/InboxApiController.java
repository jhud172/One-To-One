package uk.ac.cf._5.group14.One_To_One.Inbox;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingException;
import org.springframework.web.bind.annotation.*;
import uk.ac.cf._5.group14.One_To_One.Messaging.Message;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadState;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadStateRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inbox")
public class InboxApiController {

    private final InboxService inboxService;
    private final AuthHelper authHelper;
    private final UserRepository userRepository;
    private final MessageReadStateRepository readStateRepository;

    public InboxApiController(InboxService inboxService,
                              AuthHelper authHelper,
                              UserRepository userRepository,
                              MessageReadStateRepository readStateRepository) {
        this.inboxService = inboxService;
        this.authHelper = authHelper;
        this.userRepository = userRepository;
        this.readStateRepository = readStateRepository;
    }

    @GetMapping("/threads")
    public List<InboxThreadDto> listThreads() {
        User user = requireUser();
        return inboxService.listConversations(user).stream()
                .map(item -> new InboxThreadDto(
                        item.getConversationId(),
                        item.getTitle(),
                        item.getLastMessageSnippet(),
                        item.getLastMessageAt(),
                        item.getUnreadCount()
                ))
                .toList();
    }

    @GetMapping("/threads/{threadId}")
    public InboxThreadDetailDto thread(@PathVariable Long threadId) {
        User user = requireUser();
        MessageThread thread = inboxService.getConversationOrThrow(user, threadId);
        List<Message> messages = inboxService.getMessages(user, threadId);

        Long otherUserId = user.getId().equals(thread.getClientId()) ? thread.getTrainerId() : thread.getClientId();
        User otherUser = userRepository.findById(otherUserId).orElse(null);

        List<Long> messageIds = messages.stream().map(Message::getId).toList();
        List<MessageReadState> otherReads = messageIds.isEmpty()
                ? List.of()
                : readStateRepository.findByUserIdAndMessageIdIn(otherUserId, messageIds);
        Map<Long, Instant> readMap = otherReads.stream()
                .collect(Collectors.toMap(MessageReadState::getMessageId, MessageReadState::getReadAt));

        List<InboxMessageDto> messageDtos = messages.stream()
                .map(m -> new InboxMessageDto(
                        m.getId(),
                        m.getSenderUserId(),
                        m.getType().name(),
                        m.getBodyText(),
                        m.getCreatedAt(),
                        m.getAttachmentName(),
                        m.getAttachmentUrl(),
                        m.getAttachmentType(),
                        readMap.containsKey(m.getId())
                ))
                .toList();

        return new InboxThreadDetailDto(
                thread.getId(),
                thread.getStatus().name(),
                user.getId(),
                otherUserId,
                otherUser != null ? otherUser.getFullName() : "Trainer/Client",
                otherUser != null ? otherUser.getUsername() : null,
                messageDtos
        );
    }

    @PostMapping("/threads/{threadId}/read")
    public Map<String, Object> markRead(@PathVariable Long threadId, @RequestParam(required = false) Long upToId) {
        User user = requireUser();
        if (upToId == null) inboxService.markRead(user, threadId);
        else {
            if (upToId < 1) throw new IllegalArgumentException("Invalid read position");
            inboxService.markRead(user, threadId, upToId);
        }
        return Map.of("status", "ok");
    }

    @PostMapping("/threads/{threadId}/send")
    public ResponseEntity<?> send(@PathVariable Long threadId, @RequestBody SendMessageRequest request) {
        User user = requireUser();
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message required"));
        }
        boolean hasBody = request.bodyText() != null && !request.bodyText().isBlank();
        boolean hasAttachment = request.attachmentUrl() != null && !request.attachmentUrl().isBlank();
        if (!hasBody && !hasAttachment) {
            return ResponseEntity.badRequest().body(Map.of("message", "Message or attachment required"));
        }
        Message message = inboxService.sendMessage(
                user,
                threadId,
                hasBody ? request.bodyText() : "(attachment)",
                request.attachmentName(),
                request.attachmentUrl(),
                request.attachmentType()
        );
        if (message == null) return ResponseEntity.badRequest().body(Map.of("reason", "INVALID_MESSAGE"));
        return ResponseEntity.ok(Map.of("id", message.getId()));
    }

    @ExceptionHandler(MessagingException.class)
    public ResponseEntity<Map<String, String>> messagingError(MessagingException exception) {
        HttpStatus status = exception.getReason() == MessagingException.Reason.OFF_PLATFORM_PAYMENT
                ? HttpStatus.UNPROCESSABLE_ENTITY : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(Map.of("reason", exception.getReason().name()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidMessage(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("reason", "INVALID_MESSAGE"));
    }

    private User requireUser() {
        User user = authHelper.getAuthenticatedUser();
        if (user == null || user.getId() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return user;
    }

    public record InboxThreadDto(Long threadId,
                                 String title,
                                 String lastMessageSnippet,
                                 Instant lastMessageAt,
                                 long unreadCount) {
    }

    public record InboxThreadDetailDto(Long threadId,
                                       String status,
                                       Long currentUserId,
                                       Long otherUserId,
                                       String otherUserName,
                                       String otherUsername,
                                       List<InboxMessageDto> messages) {
    }

    public record InboxMessageDto(Long id,
                                  Long senderUserId,
                                  String type,
                                  String bodyText,
                                  Instant createdAt,
                                  String attachmentName,
                                  String attachmentUrl,
                                  String attachmentType,
                                  boolean readByOther) {
    }

    public record SendMessageRequest(String bodyText,
                                     String attachmentName,
                                     String attachmentUrl,
                                     String attachmentType) {
    }
}
