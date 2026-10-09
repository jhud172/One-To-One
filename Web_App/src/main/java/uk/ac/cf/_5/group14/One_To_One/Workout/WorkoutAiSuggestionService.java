package uk.ac.cf._5.group14.One_To_One.Workout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatResponse;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatService;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkoutAiSuggestionService {

    private final ChatService chatService;
    private final ObjectMapper mapper;

    public WorkoutAiSuggestionService(ChatService chatService, ObjectMapper mapper) {
        this.chatService = chatService;
        this.mapper = mapper;
    }

    public List<String> generateSuggestions(String promptContext) {
        if (!chatService.isAvailable()) throw new IllegalStateException("Suggestion provider unavailable");
        String prompt = "You are a fitness coach. Return ONLY valid JSON (no markdown) as an array of exactly 5 short exercise ideas. "
                + "Keep each idea under 40 characters. No numbering.";

        String userText = promptContext == null || promptContext.isBlank()
                ? "Create 5 balanced exercise ideas."
                : promptContext;

        List<ChatService.Message> messages = List.of(
                new ChatService.Message("system", prompt),
                new ChatService.Message("user", userText)
        );

        ChatResponse resp = chatService.chat(messages);
        if (resp == null || !resp.successful()) throw new IllegalStateException("Suggestions not confirmed");
        String raw = resp.reply() == null ? "" : resp.reply();

        List<String> parsed = tryParseJsonArray(raw);
        if (parsed == null || parsed.isEmpty()) {
            throw new IllegalStateException("Suggestions not confirmed");
        }

        List<String> trimmed = new ArrayList<>();
        for (String item : parsed) {
            if (item == null) continue;
            String t = item.trim();
            if (!t.isBlank() && t.length() <= 200 && !trimmed.contains(t)) {
                trimmed.add(t);
            }
            if (trimmed.size() >= 5) {
                break;
            }
        }

        if (trimmed.isEmpty()) throw new IllegalStateException("Suggestions not confirmed");
        return trimmed;
    }

    public boolean isAvailable() {
        return chatService.isAvailable();
    }

    private List<String> tryParseJsonArray(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isBlank()) return null;

        String json = extractFirstJsonArray(trimmed);
        if (json == null) return null;

        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return null;
            }
            List<String> out = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.isTextual()) {
                    out.add(node.asText());
                } else if (node.has("title")) {
                    out.add(node.path("title").asText(""));
                }
            }
            return out;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractFirstJsonArray(String s) {
        int start = s.indexOf('[');
        if (start < 0) return null;

        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            if (c == ']') {
                depth--;
                if (depth == 0) {
                    return s.substring(start, i + 1);
                }
            }
        }

        return null;
    }

}
