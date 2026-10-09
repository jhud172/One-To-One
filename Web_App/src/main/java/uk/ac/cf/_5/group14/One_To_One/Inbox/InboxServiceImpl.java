package uk.ac.cf._5.group14.One_To_One.Inbox;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Inbox.dto.ConversationListItemDto;
import uk.ac.cf._5.group14.One_To_One.Messaging.Message;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadState;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageReadStateRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThread;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageThreadRepository;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessageType;
import uk.ac.cf._5.group14.One_To_One.Messaging.MessagingService;
import uk.ac.cf._5.group14.One_To_One.Messaging.ThreadMessageRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class InboxServiceImpl implements InboxService {

    private final MessageThreadRepository threadRepository;
    private final ThreadMessageRepository messageRepository;
    private final MessageReadStateRepository readStateRepository;
    private final MessagingService messagingService;
    private final TrainerClientLinkRepository linkRepository;
    private final UserRepository userRepository;

    public InboxServiceImpl(MessageThreadRepository threadRepository,
                            ThreadMessageRepository messageRepository,
                            MessageReadStateRepository readStateRepository,
                            MessagingService messagingService,
                            TrainerClientLinkRepository linkRepository,
                            UserRepository userRepository) {
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
        this.readStateRepository = readStateRepository;
        this.messagingService = messagingService;
        this.linkRepository = linkRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationListItemDto> listConversations(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }

        List<MessageThread> threads = threadRepository.findByUserId(user.getId());
        List<ConversationListItemDto> items = new ArrayList<>();

        for (MessageThread thread : threads) {
            Long otherUserId = user.getId().equals(thread.getClientId()) ? thread.getTrainerId() : thread.getClientId();
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            String title = otherUser != null ? otherUser.getFullName() : "Conversation";

            Optional<Message> lastMessage = messageRepository.findTop1ByThread_IdOrderByCreatedAtDescIdDesc(thread.getId());
            String snippet = lastMessage.map(m -> m.getBodyText() == null ? "" : m.getBodyText())
                    .map(s -> s.length() > 180 ? s.substring(0, 180) : s)
                    .orElse("");

            Instant lastAt = lastMessage.map(Message::getCreatedAt).orElse(null);
            long unread = readStateRepository.countUnreadForThread(thread.getId(), user.getId());

            ConversationListItemDto dto = new ConversationListItemDto();
            dto.setConversationId(thread.getId());
            dto.setTitle(title);
            dto.setLastMessageSnippet(snippet);
            dto.setLastMessageAt(lastAt);
            dto.setUnreadCount(unread);
            items.add(dto);
        }

        items.sort(Comparator.comparing(ConversationListItemDto::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ConversationListItemDto::getConversationId, Comparator.reverseOrder()));
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public MessageThread getConversationOrThrow(User user, Long threadId) {
        if (user == null || user.getId() == null) {
            throw new SecurityException("User not authenticated");
        }
        return messagingService.getThreadForUser(threadId, user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> getMessages(User user, Long threadId) {
        getConversationOrThrow(user, threadId);
        return messageRepository.findByThread_IdOrderByCreatedAtAscIdAsc(threadId);
    }

    @Override
    @Transactional
    public void markRead(User user, Long threadId) {
        readThrough(user, threadId, null);
    }

    @Override
    @Transactional
    public void markRead(User user, Long threadId, Long upToId) {
        if (upToId == null || upToId < 1) return;
        readThrough(user, threadId, upToId);
    }

    private void readThrough(User user, Long threadId, Long upToId) {
        if (user == null || user.getId() == null) {
            return;
        }
        getConversationOrThrow(user, threadId);

        // Concurrent native/poll read requests for this recipient must not insert duplicate receipts.
        userRepository.findByIdForUpdate(user.getId()).orElseThrow(() -> new SecurityException("Recipient unavailable"));

        List<Long> unreadMessageIds = upToId == null ? messageRepository.findUnreadMessageIds(threadId, user.getId())
                : messageRepository.findUnreadMessageIdsThrough(threadId, user.getId(), upToId);
        if (unreadMessageIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (Long messageId : unreadMessageIds) {
            readStateRepository.save(new MessageReadState(messageId, threadId, user.getId(), now));
        }
    }

    @Override
    @Transactional(noRollbackFor = uk.ac.cf._5.group14.One_To_One.Messaging.MessagingException.class)
    public Message sendMessage(User user, Long threadId, String body, String attachmentName, String attachmentUrl, String attachmentType) {
        boolean hasBody = body != null && !body.isBlank();
        if (!hasBody && (attachmentUrl == null || attachmentUrl.isBlank())) {
            throw new IllegalArgumentException("A message or attachment link is required");
        }
        MessageThread thread = getConversationOrThrow(user, threadId);
        return messagingService.sendMessage(thread.getId(), user.getId(), MessageType.TEXT, hasBody ? body.trim() : "(attachment)", attachmentName, attachmentUrl, attachmentType);
    }

    @Override
    @Transactional
    public Long startOrGetDirectConversation(User currentUser, Long otherUserId) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new SecurityException("User not authenticated");
        }
        if (otherUserId == null || otherUserId.equals(currentUser.getId())) {
            throw new IllegalArgumentException("Invalid user");
        }
        TrainerClientLink link = findLinkBetween(currentUser.getId(), otherUserId)
                .orElseThrow(() -> new AccessDeniedException("Active relationship required"));
        return messagingService.ensureThreadForLink(link).getId();
    }

    private Optional<TrainerClientLink> findLinkBetween(Long userId, Long otherUserId) {
        List<TrainerClientLinkStatus> statuses = List.of(
                TrainerClientLinkStatus.ACTIVE,
                TrainerClientLinkStatus.REQUESTED,
                TrainerClientLinkStatus.PAUSED,
                TrainerClientLinkStatus.ENDED
        );
        Optional<TrainerClientLink> asTrainer = linkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusInOrderByUpdatedAtDesc(userId, otherUserId, statuses);
        if (asTrainer.isPresent()) {
            return asTrainer;
        }
        return linkRepository
                .findFirstByTrainerUserIdAndClientUserIdAndStatusInOrderByUpdatedAtDesc(otherUserId, userId, statuses);
    }
}
