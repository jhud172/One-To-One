package uk.ac.cf._5.group14.One_To_One.Chat;

/**
 * DTO representing a chat response.
 */
public record ChatResponse(String reply, boolean successful) {
    public ChatResponse(String reply) {
        this(reply, true);
    }
}
