package uk.ac.cf._5.group14.One_To_One.Payments;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.Instant;

/** Supports older subscription payloads and current item-level billing periods. */
final class StripeSubscriptionPayload {
    private StripeSubscriptionPayload() { }

    static Instant periodEnd(JsonNode subscription) {
        long epoch = subscription.path("current_period_end").asLong(0L);
        if (epoch <= 0) {
            for (JsonNode item : subscription.path("items").path("data")) {
                long itemEnd = item.path("current_period_end").asLong(0L);
                if (itemEnd > 0 && (epoch <= 0 || itemEnd < epoch)) epoch = itemEnd;
            }
        }
        if (epoch <= 0) return null;
        try {
            return Instant.ofEpochSecond(epoch);
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
