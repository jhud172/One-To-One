package uk.ac.cf._5.group14.One_To_One.PaymentsTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodService;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SimulatedPaymentCardResolver;
import uk.ac.cf._5.group14.One_To_One.Payments.PaymentCheckoutSession;
import uk.ac.cf._5.group14.One_To_One.Payments.PaymentProviderService;
import uk.ac.cf._5.group14.One_To_One.Payments.PaymentSubscriptionVerification;
import uk.ac.cf._5.group14.One_To_One.Payments.PricingController;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingControllerTest {

    @Mock
    private AuthHelper authHelper;

    @Mock
    private PlatformSubscriptionService platformSubscriptionService;

    @Mock
    private PaymentProviderService paymentProviderService;

    @Mock
    private SavedPaymentMethodService savedPaymentMethodService;

    @Mock
    private SimulatedPaymentCardResolver simulatedPaymentCardResolver;

    @Test
    void checkoutPage_exposesProviderAvailability() {
        PricingController controller = new PricingController(
                authHelper, platformSubscriptionService, paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");

        User user = verifiedUser();
        ConcurrentModel model = new ConcurrentModel();

        when(authHelper.getAuthenticatedUser()).thenReturn(user);
        when(paymentProviderService.isConfigured()).thenReturn(true);
        when(paymentProviderService.providerName()).thenReturn("Stripe");
        when(platformSubscriptionService.findByUserId(5L)).thenReturn(Optional.empty());

        String view = controller.checkoutPage(PlatformPlan.MONTHLY, model, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("public-views/payments/pricing-checkout");
        assertThat(model.getAttribute("paymentProviderConfigured")).isEqualTo(true);
        assertThat(model.getAttribute("paymentProviderName")).isEqualTo("Stripe");
    }

    @Test
    void startCheckout_redirectsToHostedSession() {
        PricingController controller = new PricingController(
                authHelper, platformSubscriptionService, paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");

        User user = verifiedUser();

        when(authHelper.getAuthenticatedUser()).thenReturn(user);
        when(paymentProviderService.isConfigured()).thenReturn(true);
        when(paymentProviderService.createCheckoutSession(
                user,
                PlatformPlan.YEARLY,
                "https://example.test/pricing/checkout/success?plan=YEARLY&session_id={CHECKOUT_SESSION_ID}",
                "https://example.test/pricing/checkout/cancel?plan=YEARLY"))
                .thenReturn(new PaymentCheckoutSession("Stripe", "cs_test_123", "https://checkout.stripe.test/subscription"));

        String view = controller.startCheckout(PlatformPlan.YEARLY, null, null, null, null, null, null, null, false, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:https://checkout.stripe.test/subscription");
    }

    @Test
    void checkoutSuccess_activatesSubscriptionAfterVerification() {
        PricingController controller = new PricingController(
                authHelper, platformSubscriptionService, paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");

        User user = verifiedUser();
        Instant periodEnd = Instant.parse("2026-04-27T00:00:00Z");

        when(authHelper.getAuthenticatedUser()).thenReturn(user);
        when(paymentProviderService.verifyCheckoutSession("cs_test_123"))
                .thenReturn(new PaymentSubscriptionVerification(true, "Stripe", "cus_1", "sub_1", periodEnd, "Subscription activated.", 5L, PlatformPlan.MONTHLY));

        String view = controller.checkoutSuccess(PlatformPlan.MONTHLY, "cs_test_123", new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/pricing");
        verify(platformSubscriptionService).activateSubscription(5L, PlatformPlan.MONTHLY, "cus_1", "sub_1", periodEnd);
    }

    @Test
    void startCheckout_activatesSimulationSubscriptionWhenSimulationModeIsEnabled() {
        PricingController controller = new PricingController(
                authHelper, platformSubscriptionService, paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");

        User user = verifiedUser();

        when(authHelper.getAuthenticatedUser()).thenReturn(user);
        when(paymentProviderService.isConfigured()).thenReturn(true);
        when(paymentProviderService.isSimulationMode()).thenReturn(true);

        String view = controller.startCheckout(PlatformPlan.MONTHLY, 77L, null, null, null, null, null, null, false, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/pricing");
        verify(simulatedPaymentCardResolver).resolve(user, 77L, null, null, null, null, null, null, false);
        verify(platformSubscriptionService).activateSubscription(
                org.mockito.ArgumentMatchers.eq(5L),
                org.mockito.ArgumentMatchers.eq(PlatformPlan.MONTHLY),
                org.mockito.ArgumentMatchers.eq("sim-customer-5"),
                org.mockito.ArgumentMatchers.startsWith("sim-sub-5-"),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }

    private User verifiedUser() {
        User user = new User();
        user.setId(5L);
        user.setEmailVerified(true);
        user.setPhoneVerified(true);
        user.setPhoneNumber("07123456789");
        return user;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"99,MONTHLY", "5,YEARLY", "5,INFINITE"})
    void checkoutSuccessRejectsAnotherMemberOrPlan(long owner, PlatformPlan verifiedPlan) {
        PricingController controller = new PricingController(authHelper, platformSubscriptionService,
                paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");
        when(authHelper.getAuthenticatedUser()).thenReturn(verifiedUser());
        when(paymentProviderService.verifyCheckoutSession("cs_test_other"))
                .thenReturn(new PaymentSubscriptionVerification(true, "Stripe", "cus_other", "sub_other",
                        Instant.now().plusSeconds(3600), "Verified", owner, verifiedPlan));
        var redirect = new RedirectAttributesModelMap();

        assertThat(controller.checkoutSuccess(PlatformPlan.MONTHLY, "cs_test_other", redirect)).isEqualTo("redirect:/pricing");
        assertThat(redirect.getFlashAttributes()).containsKey("pricingError");
        org.mockito.Mockito.verifyNoInteractions(platformSubscriptionService);
    }

    @Test
    void checkoutCannotPurchaseInfiniteAccess() {
        PricingController controller = new PricingController(authHelper, platformSubscriptionService,
                paymentProviderService, savedPaymentMethodService, simulatedPaymentCardResolver, "https://example.test");
        var redirect = new RedirectAttributesModelMap();
        assertThat(controller.startCheckout(PlatformPlan.INFINITE, null, null, null, null, null, null, null, false, redirect))
                .isEqualTo("redirect:/pricing");
        org.mockito.Mockito.verifyNoInteractions(paymentProviderService, platformSubscriptionService, simulatedPaymentCardResolver);
    }
}
