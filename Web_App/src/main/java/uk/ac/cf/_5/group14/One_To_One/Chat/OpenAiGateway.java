package uk.ac.cf._5.group14.One_To_One.Chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class OpenAiGateway implements AiGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenAiGateway.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_UNAVAILABLE_MESSAGE = "AI is unavailable right now. Try again later.";

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client;

    @Value("${app.ai.enabled:true}")
    private boolean enabled;

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${app.ai.model:gpt-4o-mini}")
    private String model;

    @Value("${app.ai.timeout-ms:20000}")
    private long timeoutMs;

    @Value("${app.ai.disclosure:Coach responses may use your recent training, schedule, and progress data to generate a reply. Avoid sharing card details or other sensitive secrets in AI chat.}")
    private String disclosureMessage;

    public OpenAiGateway() {
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String disclosureMessage() {
        return disclosureMessage;
    }

    @Override
    public ChatResponse chat(String message) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("user", message));
        return chat(messages);
    }

    @Override
    public ChatResponse chat(List<AiMessage> messages) {
        if (!isAvailable()) {
            return new ChatResponse(DEFAULT_UNAVAILABLE_MESSAGE, false);
        }

        OkHttpClient requestClient = client.newBuilder()
                .callTimeout(Duration.ofMillis(Math.max(timeoutMs, 1000L)))
                .build();

        try {
            String bodyJson = buildRequestJson(messages);
            Request request = new Request.Builder()
                    .url(API_URL)
                    .post(RequestBody.create(bodyJson, JSON))
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = requestClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("OpenAI chat request failed with status {}", response.code());
                    return new ChatResponse(DEFAULT_UNAVAILABLE_MESSAGE, false);
                }

                String json = response.body() != null ? response.body().string() : "{}";
                return new ChatResponse(extractReply(json));
            }
        } catch (IOException e) {
            log.warn("OpenAI chat request failed", e);
            return new ChatResponse(DEFAULT_UNAVAILABLE_MESSAGE, false);
        }
    }

    private String buildRequestJson(List<AiMessage> messagesIn) throws IOException {
        var root = mapper.createObjectNode();
        root.put("model", model);

        var messages = mapper.createArrayNode();
        if (messagesIn != null) {
            for (AiMessage message : messagesIn) {
                if (message == null) {
                    continue;
                }
                String role = message.role() == null ? "user" : message.role();
                String content = message.content() == null ? "" : message.content();
                if (content.isBlank()) {
                    continue;
                }
                var node = mapper.createObjectNode();
                node.put("role", role);
                node.put("content", content);
                messages.add(node);
            }
        }

        root.set("messages", messages);
        return mapper.writeValueAsString(root);
    }

    private String extractReply(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        String text = content.asText("");
        if (text.isBlank()) throw new IOException("AI response contained no reply");
        return text.trim();
    }
}
