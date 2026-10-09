package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationType;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.util.List;
import java.net.URI;

@Service
public class MessagingService {

    private final MessageThreadRepository threadRepository;
    private final ThreadMessageRepository threadMessageRepository;
    private final TrainerClientLinkRepository linkRepository;
    private final OffPlatformPaymentAttemptRepository offPlatformPaymentAttemptRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public MessagingService(MessageThreadRepository threadRepository,
                            ThreadMessageRepository threadMessageRepository,
                            TrainerClientLinkRepository linkRepository,
                            OffPlatformPaymentAttemptRepository offPlatformPaymentAttemptRepository,
                            NotificationService notificationService,
                            UserRepository userRepository) {
        this.threadRepository = threadRepository;
        this.threadMessageRepository = threadMessageRepository;
        this.linkRepository = linkRepository;
        this.offPlatformPaymentAttemptRepository = offPlatformPaymentAttemptRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @Transactional
    public MessageThread ensureThreadForLink(TrainerClientLink link) {
        MessageThreadStatus desired = desiredThreadStatus(link.getStatus());
        return threadRepository.findByLinkId(link.getId())
                .map(existing -> {
                    if (existing.getStatus() != desired) {
                        existing.setStatus(desired);
                        return threadRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> threadRepository.save(new MessageThread(
                        link.getClientUserId(),
                        link.getTrainerUserId(),
                        link.getId(),
                        desired)));
    }

    @Transactional
    public MessageThread getThreadForUser(Long threadId, Long userId) {
        MessageThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));

        requireParticipant(thread, userId);

        TrainerClientLink link = linkRepository.findById(thread.getLinkId())
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));

        // Keep thread status in sync with link status (defensive in case link was edited elsewhere).
        MessageThreadStatus desired = desiredThreadStatus(link.getStatus());
        if (thread.getStatus() != desired) {
            thread.setStatus(desired);
            thread = threadRepository.save(thread);
        }

        return thread;
    }

    public List<MessageThread> getTrainerInboxThreads(Long trainerId) {
        return threadRepository.findByTrainerIdAndStatusOrderByCreatedAtDesc(trainerId, MessageThreadStatus.OPEN);
    }

    public List<MessageThread> getClientInboxThreads(Long clientId) {
        return threadRepository.findByClientIdAndStatusOrderByCreatedAtDesc(clientId, MessageThreadStatus.OPEN);
    }

    public List<Message> getMessagesForThread(Long threadId, Long userId) {
        MessageThread thread = getThreadForUser(threadId, userId);
        return threadMessageRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId());
    }

    @Transactional(noRollbackFor = MessagingException.class)
    public void sendMessage(Long threadId, Long senderUserId, MessageType type, String bodyText) {
        sendMessage(threadId, senderUserId, type, bodyText, null, null, null);
    }

    @Transactional(noRollbackFor = MessagingException.class)
    public Message sendMessage(Long threadId,
                            Long senderUserId,
                            MessageType type,
                            String bodyText,
                            String attachmentName,
                            String attachmentUrl,
                            String attachmentType) {
        MessageThread thread = threadRepository.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Thread not found"));

        requireParticipant(thread, senderUserId);

        // Relationship transitions use this same client row. Check current state after acquiring it.
        userRepository.findByIdForUpdate(thread.getClientId())
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        if (entityManager != null) entityManager.refresh(thread, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        TrainerClientLink link = linkRepository.findById(thread.getLinkId())
                .orElseThrow(() -> new IllegalArgumentException("Link not found"));
        if (entityManager != null) entityManager.refresh(link);

        // Thread must be OPEN and link must be ACTIVE to send.
        if (desiredThreadStatus(link.getStatus()) == MessageThreadStatus.LOCKED) {
            thread.setStatus(MessageThreadStatus.LOCKED);
            threadRepository.save(thread);
            throw new MessagingException(MessagingException.Reason.THREAD_LOCKED);
        }
        if (thread.getStatus() != MessageThreadStatus.OPEN) {
            throw new MessagingException(MessagingException.Reason.THREAD_LOCKED);
        }
        if (link.getStatus() != TrainerClientLinkStatus.ACTIVE) {
            throw new MessagingException(MessagingException.Reason.THREAD_NOT_ACTIVE);
        }

        if (bodyText == null || bodyText.isBlank()) {
            throw new IllegalArgumentException("Message body cannot be empty");
        }
        String trimmed = bodyText.trim();
        if (trimmed.length() > 4000 || type == null) {
            throw new IllegalArgumentException("Message must be at most 4,000 characters and have a type");
        }
        String safeAttachmentUrl = validateAttachmentUrl(attachmentUrl);
        String safeAttachmentName = optionalText(attachmentName, 200);
        String safeAttachmentType = optionalText(attachmentType, 100);
        String matched = PaymentKeywordDetector.firstMatch(trimmed + (safeAttachmentUrl == null ? "" : "\n" + safeAttachmentUrl));
        if (matched != null) {
            offPlatformPaymentAttemptRepository.save(new OffPlatformPaymentAttempt(
                    thread.getId(),
                    senderUserId,
                    matched,
                    trimmed
            ));
            throw new MessagingException(MessagingException.Reason.OFF_PLATFORM_PAYMENT);
        }

        Message saved = threadMessageRepository.save(new Message(
                thread,
                senderUserId,
                type,
                trimmed,
                safeAttachmentName,
                safeAttachmentUrl,
                safeAttachmentType
        ));

        Long recipientId = senderUserId.equals(thread.getTrainerId()) ? thread.getClientId() : thread.getTrainerId();
        User recipient = userRepository.findById(recipientId).orElse(null);
        User sender = userRepository.findById(senderUserId).orElse(null);
        if (recipient != null) {
            String senderName = sender != null ? sender.getFullName() : "Someone";
            notificationService.create(recipient, NotificationType.SYSTEM, "New message", "New message from " + senderName + ".", "/inbox/" + thread.getId());
        }
        return saved;
    }

    private void requireParticipant(MessageThread thread, Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("Not authenticated");
        }
        boolean isParticipant = userId.equals(thread.getClientId()) || userId.equals(thread.getTrainerId());
        if (!isParticipant) {
            throw new AccessDeniedException("Not a participant in this thread");
        }
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) throw new IllegalArgumentException("Attachment field is too long");
        return trimmed;
    }

    private String validateAttachmentUrl(String value) {
        String trimmed = optionalText(value, 500);
        if (trimmed == null) return null;
        try {
            URI uri = URI.create(trimmed);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Attachment must be an HTTP or HTTPS link without credentials");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid attachment link");
        }
        return trimmed;
    }

    private static MessageThreadStatus desiredThreadStatus(TrainerClientLinkStatus linkStatus) {
        return linkStatus == TrainerClientLinkStatus.ACTIVE ? MessageThreadStatus.OPEN : MessageThreadStatus.LOCKED;
    }
}
