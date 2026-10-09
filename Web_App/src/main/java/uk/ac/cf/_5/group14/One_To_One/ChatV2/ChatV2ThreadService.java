package uk.ac.cf._5.group14.One_To_One.ChatV2;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ChatV2ThreadService {

    private final ChatFolderRepository folderRepository;
    private final ChatThreadRepository threadRepository;
    private final ChatMessageRepository messageRepository;

    public ChatV2ThreadService(ChatFolderRepository folderRepository,
                               ChatThreadRepository threadRepository,
                               @Qualifier("chatV2MessageRepository") ChatMessageRepository messageRepository) {
        this.folderRepository = folderRepository;
        this.threadRepository = threadRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<ChatFolder> listFolders(User user) {
        return folderRepository.findByUserOrderBySortOrderAscNameAsc(user);
    }

    @Transactional(readOnly = true)
    public Optional<ChatFolder> findFolder(User user, Long folderId) {
        return folderRepository.findByIdAndUser(folderId, user);
    }

    @Transactional
    public ChatFolder createFolder(User user, String name, String colorHex, String iconKey) {
        String safeName = requiredText(name, 120);
        String safeColor = colour(colorHex);
        String safeIcon = icon(iconKey);
        ChatFolder folder = new ChatFolder();
        folder.setUser(user);
        folder.setName(safeName);
        folder.setColorHex(safeColor);
        folder.setIconKey(safeIcon);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    @Transactional
    public ChatFolder updateFolder(ChatFolder folder, String name, String colorHex, String iconKey) {
        String safeName = requiredText(name, 120);
        String safeColor = colour(colorHex);
        String safeIcon = icon(iconKey);
        folder.setName(safeName);
        folder.setColorHex(safeColor);
        folder.setIconKey(safeIcon);
        folder.setUpdatedAt(Instant.now());
        return folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public List<ChatThread> listThreads(User user) {
        return threadRepository.findByUserAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<ChatThread> listHistory(User user) {
        return threadRepository.findByUserOrderByPinnedDescUpdatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public Page<ChatThread> historyPage(User user, ChatFolder folder, String query, int page) {
        requireFolderOwner(user, folder);
        long count = folder == null ? threadRepository.countByUserAndTitleContainingIgnoreCase(user, query)
                : threadRepository.countByUserAndFolderAndTitleContainingIgnoreCase(user, folder, query);
        PageRequest request = historyPageRequest(page, count, 20);
        List<ChatThread> rows = folder == null
                ? threadRepository.findByUserAndTitleContainingIgnoreCaseOrderByPinnedDescUpdatedAtDescIdDesc(user, query, request)
                : threadRepository.findByUserAndFolderAndTitleContainingIgnoreCaseOrderByPinnedDescUpdatedAtDescIdDesc(user, folder, query, request);
        return new PageImpl<>(rows, request, count);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessage> historyMessagesPage(ChatThread thread, Integer page) {
        long count = messageRepository.countByThread(thread);
        int requested = page == null ? (int) Math.max(1, Math.min(Integer.MAX_VALUE, (count + 29) / 30)) : page;
        PageRequest request = historyPageRequest(requested, count, 30);
        return new PageImpl<>(messageRepository.findByThreadOrderByCreatedAtAscIdAsc(thread, request), request, count);
    }

    private static PageRequest historyPageRequest(int page, long count, int size) {
        if (page < 1) throw new IllegalArgumentException("Page must be positive");
        long last = Math.max(1, (count + size - 1) / size);
        return PageRequest.of((int) Math.min((long) page - 1, last - 1), size);
    }

    @Transactional(readOnly = true)
    public List<ChatThread> listThreadsInFolder(User user, ChatFolder folder) {
        return threadRepository.findByUserAndFolderAndArchivedFalseOrderByPinnedDescUpdatedAtDesc(user, folder);
    }

    @Transactional(readOnly = true)
    public Optional<ChatThread> findThread(User user, Long threadId) {
        return threadRepository.findByIdAndUser(threadId, user);
    }

    @Transactional
    public ChatThread createThread(User user, ChatFolder folder) {
        requireFolderOwner(user, folder);
        ChatThread thread = new ChatThread();
        thread.setUser(user);
        thread.setFolder(folder);
        thread.setTitle("New chat");
        thread.setColorHex("#0f172a");
        thread.setIconKey("chat");
        thread.setPinned(false);
        thread.setArchived(false);
        thread.setCreatedAt(Instant.now());
        thread.setUpdatedAt(Instant.now());
        return threadRepository.save(thread);
    }

    @Transactional
    public ChatThread updateThreadSettings(ChatThread thread, String title, String colorHex, String iconKey,
                                          Boolean pinned, Boolean archived, String customInstructions) {
        String safeTitle = title == null || title.isBlank() ? null : requiredText(title, 160);
        String safeColour = colorHex == null || colorHex.isBlank() ? null : colour(colorHex);
        String safeIcon = iconKey == null || iconKey.isBlank() ? null : icon(iconKey);
        if (customInstructions != null && customInstructions.length() > 10000) {
            throw new IllegalArgumentException("Instructions must be at most 10,000 characters");
        }
        if (safeTitle != null) {
            thread.setTitle(safeTitle);
        }
        if (colorHex != null && !colorHex.isBlank()) {
            thread.setColorHex(safeColour);
        }
        if (iconKey != null && !iconKey.isBlank()) {
            thread.setIconKey(safeIcon);
        }
        if (pinned != null) {
            thread.setPinned(pinned);
        }
        if (archived != null) {
            thread.setArchived(archived);
        }
        if (customInstructions != null) {
            thread.setCustomInstructions(customInstructions.trim());
        }
        thread.setUpdatedAt(Instant.now());
        return threadRepository.save(thread);
    }

    @Transactional
    public ChatThread moveThread(ChatThread thread, ChatFolder folder) {
        requireFolderOwner(thread.getUser(), folder);
        thread.setFolder(folder);
        thread.setUpdatedAt(Instant.now());
        return threadRepository.save(thread);
    }

    @Transactional
    public ChatMessage appendMessage(ChatThread thread, ChatMessageRole role, String content) {
        String safeContent = requiredText(content, 10000);
        ChatMessage msg = new ChatMessage();
        msg.setThread(thread);
        msg.setRole(role);
        msg.setContent(safeContent);
        msg.setCreatedAt(Instant.now());
        ChatMessage saved = messageRepository.save(msg);
        thread.setUpdatedAt(Instant.now());
        threadRepository.save(thread);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> listMessages(ChatThread thread) {
        return messageRepository.findByThreadOrderByCreatedAtAsc(thread);
    }

    @Transactional
    public void updateTitleIfNew(ChatThread thread, String firstUserMessage) {
        if (thread == null || firstUserMessage == null) return;
        String current = thread.getTitle() == null ? "" : thread.getTitle();
        if (!current.equalsIgnoreCase("New chat")) return;

        String fallback = buildFallbackTitle(firstUserMessage);
        thread.setTitle(fallback);
        threadRepository.save(thread);

    }

    private static String requiredText(String value, int limit) {
        if (value == null || value.isBlank() || value.trim().length() > limit) {
            throw new IllegalArgumentException("Invalid text length");
        }
        return value.trim();
    }

    private static String colour(String value) {
        if (value == null || !value.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Invalid colour");
        }
        return value;
    }

    private static String icon(String value) {
        if (value == null || !ChatV2IconRegistry.iconMap().containsKey(value)) throw new IllegalArgumentException("Invalid icon");
        return value;
    }

    private static void requireFolderOwner(User user, ChatFolder folder) {
        if (folder != null && (user == null || user.getId() == null || folder.getUser() == null
                || !user.getId().equals(folder.getUser().getId()))) {
            throw new IllegalArgumentException("Folder is not available");
        }
    }

    private String buildFallbackTitle(String text) {
        String cleaned = text.trim().replaceAll("\n", " ");
        if (cleaned.isBlank()) return "New chat";
        String[] words = cleaned.split("\\s+");
        StringBuilder sb = new StringBuilder();
        int max = Math.min(words.length, 7);
        for (int i = 0; i < max; i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        String title = sb.toString();
        return title.length() > 60 ? title.substring(0, 60).trim() + "..." : title;
    }
}
