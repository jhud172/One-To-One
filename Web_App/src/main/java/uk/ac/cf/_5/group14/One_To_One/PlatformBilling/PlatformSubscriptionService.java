package uk.ac.cf._5.group14.One_To_One.PlatformBilling;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class PlatformSubscriptionService {

    private final PlatformSubscriptionRepository repository;

    public PlatformSubscriptionService(PlatformSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<PlatformSubscription> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findByUserId(userId);
    }

    @Transactional
    public PlatformSubscription save(PlatformSubscription subscription) {
        return repository.save(subscription);
    }

    @Transactional
    public PlatformSubscription activateSubscription(Long userId,
                                                     PlatformPlan plan,
                                                     String providerCustomerId,
                                                     String providerSubId,
                                                     Instant currentPeriodEnd) {
        if (userId == null || plan == null) {
            throw new IllegalArgumentException("User and plan are required.");
        }

        PlatformSubscription subscription = repository.findByUserId(userId)
                .orElseGet(PlatformSubscription::new);

        // Revisited success URLs and duplicate provider callbacks must not undo cancellation.
        if (subscription.getPlan() == PlatformPlan.INFINITE
                || (providerSubId != null && providerSubId.equals(subscription.getProviderSubId()))) {
            return subscription;
        }

        subscription.setUserId(userId);
        subscription.setPlan(plan);
        subscription.setStatus(PlatformSubscriptionStatus.ACTIVE);
        subscription.setCancelAtPeriodEnd(false);
        subscription.setProviderCustomerId(providerCustomerId);
        subscription.setProviderSubId(providerSubId);
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        return repository.save(subscription);
    }

    @Transactional
    public Optional<PlatformSubscription> syncProviderSubscription(String providerSubId,
                                                                   Instant currentPeriodEnd,
                                                                   boolean cancelAtPeriodEnd,
                                                                   boolean active) {
        if (providerSubId == null || providerSubId.isBlank()) {
            return Optional.empty();
        }
        Optional<PlatformSubscription> existing = repository.findByProviderSubId(providerSubId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        PlatformSubscription subscription = existing.get();
        subscription.setCurrentPeriodEnd(currentPeriodEnd);
        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        if (!active) {
            subscription.setStatus(PlatformSubscriptionStatus.CANCELLED);
        } else if (cancelAtPeriodEnd) {
            subscription.setStatus(PlatformSubscriptionStatus.EXPIRES);
        } else {
            subscription.setStatus(PlatformSubscriptionStatus.ACTIVE);
        }
        return Optional.of(repository.save(subscription));
    }

    @Transactional
    public Optional<PlatformSubscription> cancelByProviderSubscriptionId(String providerSubId, Instant currentPeriodEnd) {
        return syncProviderSubscription(providerSubId, currentPeriodEnd, false, false);
    }

    @Transactional
    public Optional<PlatformSubscription> markPaymentFailed(String providerSubId) {
        Optional<PlatformSubscription> existing = findByProviderSubscriptionId(providerSubId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PlatformSubscription subscription = existing.get();
        subscription.setStatus(PlatformSubscriptionStatus.PAST_DUE);
        return Optional.of(repository.save(subscription));
    }

    @Transactional
    public Optional<PlatformSubscription> markPaymentRecovered(String providerSubId) {
        Optional<PlatformSubscription> existing = findByProviderSubscriptionId(providerSubId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PlatformSubscription subscription = existing.get();
        subscription.setStatus(subscription.isCancelAtPeriodEnd()
                ? PlatformSubscriptionStatus.EXPIRES
                : PlatformSubscriptionStatus.ACTIVE);
        return Optional.of(repository.save(subscription));
    }

    @Transactional
    public Optional<PlatformSubscription> updateCancelAtPeriodEnd(Long userId, boolean cancelAtPeriodEnd) {
        Optional<PlatformSubscription> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PlatformSubscription sub = existing.get();
        sub.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        if (cancelAtPeriodEnd && sub.getStatus() == PlatformSubscriptionStatus.ACTIVE) {
            sub.setStatus(PlatformSubscriptionStatus.EXPIRES);
        }
        if (!cancelAtPeriodEnd && sub.getStatus() == PlatformSubscriptionStatus.EXPIRES) {
            sub.setStatus(PlatformSubscriptionStatus.ACTIVE);
        }
        if (!cancelAtPeriodEnd && sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isBefore(Instant.now())) {
            sub.setCurrentPeriodEnd(null);
        }
        return Optional.of(repository.save(sub));
    }

    @Transactional(readOnly = true)
    public boolean isPremium(Long userId, Clock clock) {
        if (userId == null) {
            return false;
        }
        Optional<PlatformSubscription> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return false;
        }
        PlatformSubscription sub = existing.get();
        if (sub.getPlan() == PlatformPlan.INFINITE) {
            return true;
        }
        if (sub.getStatus() == PlatformSubscriptionStatus.ACTIVE) {
            return true;
        }
        if (sub.getStatus() == PlatformSubscriptionStatus.EXPIRES) {
            Instant now = clock != null ? Instant.now(clock) : Instant.now();
            return sub.getCurrentPeriodEnd() == null || sub.getCurrentPeriodEnd().isAfter(now);
        }
        return false;
    }

    /**
     * Returns the number of days until the subscription renews/expires.
     * Returns null if the user has no subscription, has an INFINITE plan, or has no period end set.
     * Returns a negative number if the period has already ended.
     */
    @Transactional(readOnly = true)
    public Long getDaysUntilRenewal(Long userId, Clock clock) {
        if (userId == null) {
            return null;
        }
        Optional<PlatformSubscription> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return null;
        }
        PlatformSubscription sub = existing.get();
        if (sub.getPlan() == PlatformPlan.INFINITE || sub.getCurrentPeriodEnd() == null) {
            return null;
        }
        Instant now = clock != null ? Instant.now(clock) : Instant.now();
        return ChronoUnit.DAYS.between(now, sub.getCurrentPeriodEnd());
    }

    /**
     * Returns true if the user had a subscription that has since been cancelled or expired.
     */
    @Transactional(readOnly = true)
    public boolean hasExpiredSubscription(Long userId, Clock clock) {
        if (userId == null) {
            return false;
        }
        Optional<PlatformSubscription> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return false;
        }
        PlatformSubscription sub = existing.get();
        if (sub.getStatus() == PlatformSubscriptionStatus.CANCELLED) {
            return true;
        }
        if (sub.getStatus() == PlatformSubscriptionStatus.EXPIRES) {
            Instant now = clock != null ? Instant.now(clock) : Instant.now();
            return sub.getCurrentPeriodEnd() != null && !sub.getCurrentPeriodEnd().isAfter(now);
        }
        return false;
    }

    /**
     * Updates the plan of an existing subscription.
     * Prevents downgrading from INFINITE to a limited plan.
     */
    @Transactional
    public Optional<PlatformSubscription> updatePlan(Long userId, PlatformPlan plan) {
        Optional<PlatformSubscription> existing = findByUserId(userId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PlatformSubscription sub = existing.get();
        if (sub.getPlan() == plan) {
            return existing;
        }
        if (sub.getPlan() == PlatformPlan.INFINITE) {
            return existing;
        }
        sub.setPlan(plan);
        return Optional.of(repository.save(sub));
    }

    private Optional<PlatformSubscription> findByProviderSubscriptionId(String providerSubId) {
        if (providerSubId == null || providerSubId.isBlank()) {
            return Optional.empty();
        }
        return repository.findByProviderSubId(providerSubId.trim());
    }
}
