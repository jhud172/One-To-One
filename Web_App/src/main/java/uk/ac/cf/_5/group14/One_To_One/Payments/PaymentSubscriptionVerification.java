package uk.ac.cf._5.group14.One_To_One.Payments;

import java.time.Instant;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;

public record PaymentSubscriptionVerification(boolean active,
                                              String provider,
                                              String customerId,
                                              String subscriptionId,
                                              Instant currentPeriodEnd,
                                              String message,
                                              Long userId,
                                              PlatformPlan plan) {
    public PaymentSubscriptionVerification(boolean active, String provider, String customerId,
                                           String subscriptionId, Instant currentPeriodEnd, String message) {
        this(active, provider, customerId, subscriptionId, currentPeriodEnd, message, null, null);
    }

    public boolean belongsTo(Long expectedUserId, PlatformPlan expectedPlan) {
        return active && expectedUserId != null && expectedUserId.equals(userId)
                && plan == expectedPlan && (plan == PlatformPlan.MONTHLY || plan == PlatformPlan.YEARLY);
    }
}
