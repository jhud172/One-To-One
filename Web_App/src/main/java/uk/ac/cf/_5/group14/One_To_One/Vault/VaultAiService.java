package uk.ac.cf._5.group14.One_To_One.Vault;

import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatResponse;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatService;

import java.util.ArrayList;
import java.util.List;

@Service
public class VaultAiService {

    private final ChatService chatService;

    public VaultAiService(ChatService chatService) {
        this.chatService = chatService;
    }

    public boolean isAvailable() { return chatService.isAvailable(); }

    public String summariseWeek(List<VaultNote> notes) {
        requireAvailable();
        List<ChatService.Message> messages = new ArrayList<>();
        messages.add(new ChatService.Message("system",
                "You are a supportive fitness coach. Summarise the week based only on the provided notes. " +
                "Return: (1) 3-6 bullet summary, (2) 2-4 actionable next steps, (3) one short encouragement line."));
        messages.add(new ChatService.Message("user", buildNotesPayload(notes)));

        ChatResponse response = chatService.chat(messages);
        return successfulReply(response);
    }

    public String rewriteCheckin(List<VaultNote> notes) {
        requireAvailable();
        List<ChatService.Message> messages = new ArrayList<>();
        messages.add(new ChatService.Message("system",
                "Rewrite the following training notes into a concise client check-in message. " +
                "Keep it friendly, first-person, and under 120 words. Use short sentences."));
        messages.add(new ChatService.Message("user", buildNotesPayload(notes)));

        ChatResponse response = chatService.chat(messages);
        return successfulReply(response);
    }

    public String generateInsight(VaultNote note) {
        requireAvailable();
        List<ChatService.Message> messages = new ArrayList<>();
        messages.add(new ChatService.Message("system",
                "You are an intelligent fitness assistant. Analyse the following training note and provide a concise insight. " +
                "Identify themes and practical training questions to discuss with the trainer. Do not diagnose conditions or infer injury or nutrition risks. " +
                "Keep your response under 80 words. Be direct and actionable."));
        messages.add(new ChatService.Message("user", buildNotesPayload(List.of(note))));

        ChatResponse response = chatService.chat(messages);
        return successfulReply(response);
    }

    private void requireAvailable() {
        if (!isAvailable()) throw new IllegalStateException("AI unavailable");
    }

    private String successfulReply(ChatResponse response) {
        if (response == null || !response.successful() || response.reply() == null
                || response.reply().isBlank() || response.reply().length() > 20000) {
            throw new IllegalStateException("AI unavailable");
        }
        return response.reply();
    }

    private String buildNotesPayload(List<VaultNote> notes) {
        if (notes == null || notes.isEmpty()) {
            throw new IllegalArgumentException("Select reflections first");
        }
        if (notes.size() > 20) throw new IllegalArgumentException("Select up to 20 reflections");
        StringBuilder sb = new StringBuilder();
        sb.append("Selected notes:\n");
        for (VaultNote note : notes) {
            if (note == null) continue;
            sb.append("- [").append(note.getNoteType()).append("] ");
            sb.append(note.getTitle() == null ? "(untitled)" : note.getTitle());
            if (note.getLinkedDate() != null) {
                sb.append(" (date: ").append(note.getLinkedDate()).append(")");
            }
            if (note.getMood() != null && !note.getMood().isBlank()) {
                sb.append(" (mood: ").append(note.getMood()).append(")");
            }
            sb.append("\n");
            String content = note.getContent() == null ? "" : note.getContent().trim();
            if (!content.isBlank()) {
                sb.append(content).append("\n");
            }
            sb.append("\n");
        }
        if (sb.length() > 20000) throw new IllegalArgumentException("Selection is too long");
        return sb.toString();
    }
}
