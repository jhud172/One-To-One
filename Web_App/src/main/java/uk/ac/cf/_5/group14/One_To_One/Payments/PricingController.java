package uk.ac.cf._5.group14.One_To_One.Payments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodService;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SimulatedPaymentCardResolver;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformPlan;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscription;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

@Controller
public class PricingController {

    private final AuthHelper authHelper;
    private final PlatformSubscriptionService platformSubscriptionService;
    private final PaymentProviderService paymentProviderService;
    private final SavedPaymentMethodService savedPaymentMethodService;
    private final SimulatedPaymentCardResolver simulatedPaymentCardResolver;
    private final String siteBaseUrl;

    public PricingController(AuthHelper authHelper,
                             PlatformSubscriptionService platformSubscriptionService,
                             PaymentProviderService paymentProviderService,
                             SavedPaymentMethodService savedPaymentMethodService,
                             SimulatedPaymentCardResolver simulatedPaymentCardResolver,
                             @Value("${app.site.base-url:http://localhost:8080}") String siteBaseUrl) {
        this.authHelper = authHelper;
        this.platformSubscriptionService = platformSubscriptionService;
        this.paymentProviderService = paymentProviderService;
        this.savedPaymentMethodService = savedPaymentMethodService;
        this.simulatedPaymentCardResolver = simulatedPaymentCardResolver;
        this.siteBaseUrl = trimTrailingSlash(siteBaseUrl);
    }

    @GetMapping("/pricing")
    public String pricingPage(Model model) {
        model.addAttribute("pageTitle", "Pricing");
        model.addAttribute("pageDescription", "Compare One To One monthly and yearly platform plans, with clear features before you choose how to join.");
        model.addAttribute("plans", List.of(
                new CheckoutPlanInfo(PlatformPlan.MONTHLY, "Monthly", "£12 / month", "Most flexible",
                        List.of("Premium dashboards", "Weekly insights", "Priority support")),
                new CheckoutPlanInfo(PlatformPlan.YEARLY, "Yearly", "£108 / year", "Recommended",
                        List.of("Save £36 a year", "Premium dashboards", "Weekly insights", "Priority support"))
        ));
        model.addAttribute("paymentProviderConfigured", paymentProviderService.isConfigured());
        model.addAttribute("paymentSimulationMode", paymentProviderService.isSimulationMode());
        model.addAttribute("paymentProviderName", paymentProviderService.providerName());

        User user = authHelper.getAuthenticatedUser();
        if (user != null) {
            model.addAttribute("platformSubscription",
                    platformSubscriptionService.findByUserId(user.getId()).orElse(null));
        }
        return "public-views/payments/pricing";
    }

    @GetMapping("/pricing/checkout")
    public String checkoutPage(@RequestParam("plan") PlatformPlan plan, Model model, RedirectAttributes redirectAttributes) {
        if (plan != PlatformPlan.MONTHLY && plan != PlatformPlan.YEARLY) {
            redirectAttributes.addFlashAttribute("pricingError", "Choose a monthly or yearly plan.");
            return "redirect:/pricing";
        }
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login?next=/pricing/checkout?plan=" + plan.name();
        }

        if (isAccountIncomplete(user)) {
            redirectAttributes.addFlashAttribute("verifyError", "Verify your email and phone before starting checkout.");
            return "redirect:/profile";
        }

        model.addAttribute("pageTitle", "Checkout");
        model.addAttribute("selectedPlan", plan);
        model.addAttribute("paymentProviderConfigured", paymentProviderService.isConfigured());
        model.addAttribute("paymentSimulationMode", paymentProviderService.isSimulationMode());
        model.addAttribute("paymentProviderName", paymentProviderService.providerName());

        CheckoutPlanInfo planInfo = plan == PlatformPlan.YEARLY
                ? new CheckoutPlanInfo(PlatformPlan.YEARLY, "Yearly", "£108 / year", "Recommended",
                List.of("Save £36 a year", "Premium dashboards", "Weekly insights", "Priority support"))
                : new CheckoutPlanInfo(PlatformPlan.MONTHLY, "Monthly", "£12 / month", "Most flexible",
                List.of("Premium dashboards", "Weekly insights", "Priority support"));

        model.addAttribute("planInfo", planInfo);

        PlatformSubscription subscription = platformSubscriptionService.findByUserId(user.getId()).orElse(null);
        model.addAttribute("platformSubscription", subscription);
        model.addAttribute("savedCards", savedPaymentMethodService.getCardsForUser(user.getId()));

        return "public-views/payments/pricing-checkout";
    }

    @PostMapping("/pricing/checkout")
    public String startCheckout(@RequestParam("plan") PlatformPlan plan,
                                @RequestParam(value = "selectedCardId", required = false) Long selectedCardId,
                                @RequestParam(value = "newCardHolderName", required = false) String newCardHolderName,
                                @RequestParam(value = "newProviderToken", required = false) String newProviderToken,
                                @RequestParam(value = "newLastFour", required = false) String newLastFour,
                                @RequestParam(value = "newBrand", required = false) String newBrand,
                                @RequestParam(value = "newExpiryMonth", required = false) Short newExpiryMonth,
                                @RequestParam(value = "newExpiryYear", required = false) Short newExpiryYear,
                                @RequestParam(value = "saveCard", defaultValue = "false") boolean saveCard,
                                RedirectAttributes redirectAttributes) {
        if (plan != PlatformPlan.MONTHLY && plan != PlatformPlan.YEARLY) {
            redirectAttributes.addFlashAttribute("pricingError", "Choose a monthly or yearly plan.");
            return "redirect:/pricing";
        }
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login?next=/pricing/checkout?plan=" + plan.name();
        }
        if (isAccountIncomplete(user)) {
            redirectAttributes.addFlashAttribute("verifyError", "Verify your email and phone before starting checkout.");
            return "redirect:/profile";
        }
        if (!paymentProviderService.isConfigured()) {
            redirectAttributes.addFlashAttribute("pricingError", "Secure subscription checkout is not configured yet.");
            return "redirect:/pricing/checkout?plan=" + plan.name();
        }

        try {
            if (paymentProviderService.isSimulationMode()) {
                simulatedPaymentCardResolver.resolve(
                        user,
                        selectedCardId,
                        newCardHolderName,
                        newProviderToken,
                        newLastFour,
                        newBrand,
                        newExpiryMonth,
                        newExpiryYear,
                        saveCard);

                platformSubscriptionService.activateSubscription(
                        user.getId(),
                        plan,
                        "sim-customer-" + user.getId(),
                        "sim-sub-" + user.getId() + "-" + System.currentTimeMillis(),
                        simulationPeriodEnd(plan));

                redirectAttributes.addFlashAttribute(
                        "pricingSuccess",
                        "Platform Premium demo access is now active. No real payment was taken.");
                return "redirect:/pricing";
            }

            String successUrl = siteBaseUrl + "/pricing/checkout/success?plan=" + plan.name() + "&session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = siteBaseUrl + "/pricing/checkout/cancel?plan=" + plan.name();
            PaymentCheckoutSession session = paymentProviderService.createCheckoutSession(user, plan, successUrl, cancelUrl);
            return "redirect:" + session.checkoutUrl();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("pricingError", e.getMessage());
            return "redirect:/pricing/checkout?plan=" + plan.name();
        }
    }

    @GetMapping("/pricing/checkout/success")
    public String checkoutSuccess(@RequestParam("plan") PlatformPlan plan,
                                  @RequestParam(name = "session_id", required = false) String sessionId,
                                  RedirectAttributes redirectAttributes) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        PaymentSubscriptionVerification verification;
        try {
            verification = paymentProviderService.verifyCheckoutSession(sessionId);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("pricingError", "Subscription checkout could not be verified. Please try again.");
            return "redirect:/pricing";
        }
        if (!verification.active()) {
            redirectAttributes.addFlashAttribute("pricingError", verification.message());
            return "redirect:/pricing";
        }
        if (!verification.belongsTo(user.getId(), plan)) {
            redirectAttributes.addFlashAttribute("pricingError", "This checkout does not match your account and selected plan.");
            return "redirect:/pricing";
        }

        platformSubscriptionService.activateSubscription(
                user.getId(),
                plan,
                verification.customerId(),
                verification.subscriptionId(),
                verification.currentPeriodEnd());

        redirectAttributes.addFlashAttribute("pricingSuccess", "Platform Premium is now active.");
        return "redirect:/pricing";
    }

    @GetMapping("/pricing/checkout/cancel")
    public String checkoutCancel(@RequestParam("plan") PlatformPlan plan, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("pricingError", "Subscription checkout was cancelled.");
        return "redirect:/pricing/checkout?plan=" + plan.name();
    }

    private boolean isAccountIncomplete(User user) {
        if (user == null) {
            return true;
        }
        boolean emailUnverified = !user.isEmailVerified();
        boolean phoneUnverified = user.getPhoneNumber() != null && !user.getPhoneNumber().isBlank() && !user.isPhoneVerified();
        return emailUnverified || phoneUnverified;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Instant simulationPeriodEnd(PlatformPlan plan) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime end = plan == PlatformPlan.YEARLY ? now.plusYears(1) : now.plusMonths(1);
        return end.toInstant();
    }
}
