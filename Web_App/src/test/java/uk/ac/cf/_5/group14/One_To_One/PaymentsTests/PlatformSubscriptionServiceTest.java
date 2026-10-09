package uk.ac.cf._5.group14.One_To_One.PaymentsTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscription;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionRepository;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformSubscriptionServiceTest {

    @Mock
    private PlatformSubscriptionRepository repository;

    @InjectMocks
    private PlatformSubscriptionService service;

    @Test
    void activateSubscription_createsOrUpdatesActivePremiumRecord() {
        Instant periodEnd = Instant.parse("2026-04-27T00:00:00Z");

        when(repository.findByUserId(7L)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSubscription subscription = service.activateSubscription(
                7L,
                PlatformPlan.YEARLY,
                "cus_1",
                "sub_1",
                periodEnd);

        assertThat(subscription.getUserId()).isEqualTo(7L);
        assertThat(subscription.getPlan()).isEqualTo(PlatformPlan.YEARLY);
        assertThat(subscription.getStatus()).isEqualTo(PlatformSubscriptionStatus.ACTIVE);
        assertThat(subscription.getProviderCustomerId()).isEqualTo("cus_1");
        assertThat(subscription.getProviderSubId()).isEqualTo("sub_1");
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
    }

    @Test
    void paymentFailureAndRetryMoveSubscriptionThroughPastDueAndActive() {
        PlatformSubscription subscription = new PlatformSubscription();
        subscription.setProviderSubId("sub_1");
        subscription.setStatus(PlatformSubscriptionStatus.ACTIVE);

        when(repository.findByProviderSubId("sub_1")).thenReturn(Optional.of(subscription));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSubscription failed = service.markPaymentFailed("sub_1").orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(PlatformSubscriptionStatus.PAST_DUE);

        PlatformSubscription recovered = service.markPaymentRecovered("sub_1").orElseThrow();
        assertThat(recovered.getStatus()).isEqualTo(PlatformSubscriptionStatus.ACTIVE);
    }

    @Test
    void duplicateCheckoutCannotReverseScheduledCancellation() {
        PlatformSubscription subscription = new PlatformSubscription();
        subscription.setUserId(7L);
        subscription.setPlan(PlatformPlan.MONTHLY);
        subscription.setProviderSubId("sub_existing");
        subscription.setCancelAtPeriodEnd(true);
        subscription.setStatus(PlatformSubscriptionStatus.EXPIRES);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(subscription));

        assertThat(service.activateSubscription(7L, PlatformPlan.MONTHLY, "cus_1", "sub_existing", Instant.now()))
                .isSameAs(subscription);
        assertThat(subscription.isCancelAtPeriodEnd()).isTrue();
        assertThat(subscription.getStatus()).isEqualTo(PlatformSubscriptionStatus.EXPIRES);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void recurringCheckoutCannotDowngradeLifetimeAccess() {
        PlatformSubscription subscription = new PlatformSubscription();
        subscription.setPlan(PlatformPlan.INFINITE);
        when(repository.findByUserId(7L)).thenReturn(Optional.of(subscription));
        assertThat(service.activateSubscription(7L, PlatformPlan.MONTHLY, "cus_1", "sub_new", Instant.now())).isSameAs(subscription);
        assertThat(subscription.getPlan()).isEqualTo(PlatformPlan.INFINITE);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void successfulRetryKeepsScheduledCancellation() {
        PlatformSubscription subscription = new PlatformSubscription();
        subscription.setProviderSubId("sub_1");
        subscription.setStatus(PlatformSubscriptionStatus.PAST_DUE);
        subscription.setCancelAtPeriodEnd(true);

        when(repository.findByProviderSubId("sub_1")).thenReturn(Optional.of(subscription));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PlatformSubscription recovered = service.markPaymentRecovered("sub_1").orElseThrow();

        assertThat(recovered.getStatus()).isEqualTo(PlatformSubscriptionStatus.EXPIRES);
    }
}
