package uk.ac.cf._5.group14.One_To_One.Merch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethodService;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SimulatedPaymentCardResolver;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchHostedCheckoutSession;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrder;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchPaymentGateway;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchPaymentVerification;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.Optional;
import java.util.Objects;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.PaymentStatus;

@Controller
public class MerchCheckoutController {

    private final MerchProductService productService;
    private final MerchOrderService orderService;
    private final MerchPaymentGateway paymentGateway;
    private final SavedPaymentMethodService savedPaymentMethodService;
    private final SimulatedPaymentCardResolver simulatedPaymentCardResolver;
    private final AuthHelper authHelper;
    private final String siteBaseUrl;

    public MerchCheckoutController(MerchProductService productService,
                                   MerchOrderService orderService,
                                   MerchPaymentGateway paymentGateway,
                                   SavedPaymentMethodService savedPaymentMethodService,
                                   SimulatedPaymentCardResolver simulatedPaymentCardResolver,
                                   AuthHelper authHelper,
                                   @Value("${app.site.base-url:http://localhost:8080}") String siteBaseUrl) {
        this.productService = productService;
        this.orderService = orderService;
        this.paymentGateway = paymentGateway;
        this.savedPaymentMethodService = savedPaymentMethodService;
        this.simulatedPaymentCardResolver = simulatedPaymentCardResolver;
        this.authHelper = authHelper;
        this.siteBaseUrl = trimTrailingSlash(siteBaseUrl);
    }

    public ModelAndView buyForm(Long id, RedirectAttributes ra) {
        return buyForm(id, ra, new org.springframework.ui.ExtendedModelMap(), null, java.util.UUID.randomUUID().toString(), null);
    }

    @GetMapping("/merch/{id}/buy")
    public ModelAndView buyForm(@PathVariable Long id, RedirectAttributes ra, org.springframework.ui.Model model,
                               @RequestParam(value = "orderId", required = false) Long orderId,
                               @RequestParam(value = "checkoutKey", required = false) String checkoutKey,
                               @RequestParam(value = "returnTo", required = false) String returnTo) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return new ModelAndView("redirect:/login");
        }

        if (orderId != null) {
            MerchOrder owned = orderService.findByIdForUser(orderId, user.getId()).orElseThrow(() ->
                    new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
            if (owned.getCheckoutKey() == null) return new ModelAndView("redirect:/orders");
            model.addAttribute("checkoutKey", owned.getCheckoutKey());
        } else if (checkoutKey != null) {
            if (!validKey(checkoutKey)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST);
            model.addAttribute("checkoutKey", checkoutKey.toLowerCase(java.util.Locale.ROOT));
        }
        String shopReturnUrl = shopReturn(returnTo == null && model.containsAttribute("shopReturnUrl")
                ? String.valueOf(model.getAttribute("shopReturnUrl")) : returnTo);
        Optional<MerchProduct> opt = productService.findById(id);
        MerchOrder retry = model.containsAttribute("checkoutKey")
                ? orderService.findByCheckoutKeyForUser(String.valueOf(model.getAttribute("checkoutKey")), user.getId()).orElse(null) : null;
        if (retry != null && (retry.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT
                || retry.getItems().size() != 1 || retry.getItems().get(0).getProduct() == null
                || !id.equals(retry.getItems().get(0).getProduct().getId()))) return new ModelAndView("redirect:/orders");
        if (opt.isEmpty() || (retry == null && (!opt.get().isActive() || opt.get().getStockQuantity() < 1))) {
            ra.addFlashAttribute("shopError", "This product is no longer available.");
            return new ModelAndView("redirect:/merch");
        }
        if (!model.containsAttribute("checkoutKey")) {
            return new ModelAndView(checkoutReturn(id, java.util.UUID.randomUUID().toString(), shopReturnUrl));
        }

        ModelAndView mav = new ModelAndView("shared-views/merch/checkout");
        MerchProduct display = opt.get();
        if (retry != null) {
            var item = retry.getItems().get(0);
            display = new MerchProduct();
            display.setId(id);
            display.setName(item.getProductNameSnapshot());
            display.setPrice(item.getPriceSnapshot());
            display.setImageUrl(item.getImageUrlSnapshot());
            display.setCategory(item.getCategorySnapshot());
            display.setStockQuantity(item.getQuantity());
            mav.addObject("checkoutQuantity", item.getQuantity());
            mav.addObject("checkoutRetry", true);
        }
        mav.addObject("product", display);
        Object initialQuantity = retry == null ? model.getAttribute("checkoutQuantity") : mav.getModel().get("checkoutQuantity");
        mav.addObject("checkoutInitialTotal", initialTotal(display, initialQuantity));
        mav.addObject("shopReturnUrl", shopReturnUrl);
        mav.addObject("checkoutKey", model.getAttribute("checkoutKey"));
        mav.addObject("paymentProviderConfigured", paymentGateway.isConfigured());
        mav.addObject("paymentSimulationMode", paymentGateway.isSimulationMode());
        mav.addObject("paymentProviderName", paymentGateway.providerName());
        mav.addObject("checkoutUncertain", retry != null && retry.getPaymentProvider() != null
                && !"SIMULATED".equals(retry.getPaymentProvider()) && retry.getPaymentReference() == null);
        mav.addObject("savedCards", savedPaymentMethodService.getCardsForUser(user.getId()));
        return mav;
    }

    public String doBuy(Long id, String quantityValue, Long selectedCardId, String newCardHolderName,
                        String newProviderToken, String newLastFour, String newBrand, Short newExpiryMonth,
                        Short newExpiryYear, boolean saveCard, RedirectAttributes ra) {
        return processBuy(id, quantityValue, selectedCardId, newCardHolderName, newProviderToken, newLastFour,
                newBrand, newExpiryMonth, newExpiryYear, saveCard, null, null, null, true, ra);
    }

    @PostMapping("/merch/{id}/buy")
    public String doBuy(@PathVariable Long id,
                        @RequestParam(value = "quantity", defaultValue = "1") String quantityValue,
                        @RequestParam(value = "selectedCardId", required = false) Long selectedCardId,
                        @RequestParam(value = "newCardHolderName", required = false) String newCardHolderName,
                        @RequestParam(value = "newProviderToken", required = false) String newProviderToken,
                        @RequestParam(value = "newLastFour", required = false) String newLastFour,
                        @RequestParam(value = "newBrand", required = false) String newBrand,
                        @RequestParam(value = "newExpiryMonth", required = false) Short newExpiryMonth,
                        @RequestParam(value = "newExpiryYear", required = false) Short newExpiryYear,
                        @RequestParam(value = "saveCard", defaultValue = "false") boolean saveCard,
                        @RequestParam(value = "checkoutKey", required = false) String checkoutKey,
                        @RequestParam(value = "returnTo", required = false) String returnTo,
                        @RequestParam(value = "expectedUnitMinor", required = false) Long expectedUnitMinor,
                        RedirectAttributes ra) {
        return processBuy(id, quantityValue, selectedCardId, newCardHolderName, newProviderToken, newLastFour,
                newBrand, newExpiryMonth, newExpiryYear, saveCard, checkoutKey, returnTo, expectedUnitMinor, false, ra);
    }

    private String processBuy(Long id, String quantityValue, Long selectedCardId, String newCardHolderName,
                              String newProviderToken, String newLastFour, String newBrand, Short newExpiryMonth,
                              Short newExpiryYear, boolean saveCard, String checkoutKey, String returnTo,
                              Long expectedUnitMinor, boolean legacyCaller, RedirectAttributes ra) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        // Cached old forms must review a fresh quote rather than bypass native purchase identity.
        if (!legacyCaller && !validKey(checkoutKey)) {
            checkoutKey = java.util.UUID.randomUUID().toString();
            expectedUnitMinor = null;
        }

        Optional<MerchProduct> opt = productService.findById(id);
        if (opt.isEmpty() || (checkoutKey == null && !opt.get().isActive())) {
            ra.addFlashAttribute("shopError", "Product not available.");
            return "redirect:/merch";
        }
        returnTo = shopReturn(returnTo);
        ra.addFlashAttribute("shopReturnUrl", returnTo);
        int quantity;
        try { quantity = Integer.parseInt(quantityValue); }
        catch (NumberFormatException ex) {
            ra.addFlashAttribute("checkoutError", "Enter a whole quantity within available stock.");
            retainDraft(ra, quantityValue, checkoutKey, selectedCardId, newCardHolderName, newBrand, newExpiryMonth, newExpiryYear, saveCard);
            return checkoutReturn(id, checkoutKey, returnTo);
        }
        if (!paymentGateway.isConfigured()) {
            retainDraft(ra, quantityValue, checkoutKey, selectedCardId, newCardHolderName, newBrand, newExpiryMonth, newExpiryYear, saveCard);
            ra.addFlashAttribute("checkoutError", "Secure checkout is not configured yet.");
            return checkoutReturn(id, checkoutKey, returnTo);
        }

        MerchProduct product = opt.get();
        if (quantity < 1 || (checkoutKey == null && quantity > product.getStockQuantity())) {
            ra.addFlashAttribute("checkoutError", "Choose a quantity within available stock.");
            retainDraft(ra, quantityValue, checkoutKey, selectedCardId, newCardHolderName, newBrand, newExpiryMonth, newExpiryYear, saveCard);
            return checkoutReturn(id, checkoutKey, returnTo);
        }
        MerchOrder order = null;
        try {
            if (checkoutKey != null) {
                if (expectedUnitMinor == null || expectedUnitMinor < 1) {
                    retainDraft(ra, quantityValue, checkoutKey, selectedCardId, newCardHolderName, newBrand, newExpiryMonth, newExpiryYear, saveCard);
                    ra.addFlashAttribute("checkoutQuoteInvalid", true);
                    return checkoutReturn(id, checkoutKey, returnTo);
                }
                order = orderService.createPendingOrderOnce(user, product, quantity, checkoutKey,
                        expectedUnitMinor,
                        paymentGateway.isSimulationMode() ? () -> simulatedPaymentCardResolver.resolve(user,
                                selectedCardId, newCardHolderName, newProviderToken, newLastFour, newBrand,
                                newExpiryMonth, newExpiryYear, saveCard).savedPaymentMethod() : null);
                if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) {
                    ra.addFlashAttribute("orderSuccess", "Order #" + order.getId() + " already exists. Check its current status before starting another checkout.");
                    return "redirect:/orders";
                }
            }
            if (paymentGateway.isSimulationMode()) {
                uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod selected;
                if (order == null) {
                    selected = simulatedPaymentCardResolver.resolve(user, selectedCardId, newCardHolderName,
                            newProviderToken, newLastFour, newBrand, newExpiryMonth, newExpiryYear, saveCard).savedPaymentMethod();
                    order = orderService.createPendingOrder(user, product, quantity);
                } else selected = order.getPaymentMethod();
                String paymentReference = "sim-merch-" + order.getId();
                orderService.markCheckoutSessionCreated(order.getId(), "SIMULATED", paymentReference, selected);
                order = orderService.completePaidOrder(order.getId(), paymentReference);
                ra.addFlashAttribute("orderSuccess", "Demo order #" + order.getId() + " completed. No real payment was taken and this item will not be delivered.");
                return "redirect:/orders";
            }

            if (order == null) order = orderService.createPendingOrder(user, product, quantity);
            if (order.getCreatedAt().plusSeconds(2L * 60L * 60L).isBefore(java.time.Instant.now())) {
                ra.addFlashAttribute("orderError", "This checkout has expired. Its reservation is kept until the provider confirms cancellation. Check your order history.");
                return "redirect:/orders";
            }
            String successUrl = siteBaseUrl + "/merch/checkout/success?orderId=" + order.getId() + "&session_id={CHECKOUT_SESSION_ID}";
            String cancelUrl = siteBaseUrl + "/merch/checkout/cancel?orderId=" + order.getId();
            // Persist provider intent and request configuration before any network call.
            if (checkoutKey != null) {
                order = orderService.prepareHostedCheckout(order.getId(), paymentGateway.providerName(), paymentGateway.currency(), successUrl, cancelUrl);
                if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) return "redirect:/orders";
            } else orderService.markCheckoutSessionCreated(order.getId(), paymentGateway.providerName(), null, null);
            MerchHostedCheckoutSession session = paymentGateway.createCheckoutSession(order, product, quantity, successUrl, cancelUrl);
            orderService.markCheckoutSessionCreated(order.getId(), session.provider(), session.reference(), null);
            return "redirect:" + session.checkoutUrl();
        } catch (Exception e) {
            if (order != null && order.getId() != null) {
                try { orderService.cancelPendingPayment(order.getId(), "Checkout failed before completion."); }
                catch (RuntimeException ignored) { /* Keep a reservation while provider state is uncertain. */ }
            }
            retainDraft(ra, quantityValue, checkoutKey, selectedCardId, newCardHolderName, newBrand, newExpiryMonth, newExpiryYear, saveCard);
            if (e instanceof MerchOrderService.PriceChangedException) ra.addFlashAttribute("checkoutPriceChanged", true);
            else if (e instanceof uk.ac.cf._5.group14.One_To_One.PaymentCards.PaymentCardExpiryValidator.ExpiredCardException) ra.addFlashAttribute("checkoutCardExpired", true);
            else ra.addFlashAttribute("checkoutError", "Checkout could not complete. Check the quantity and demo payment selection, or try again later.");
            return checkoutReturn(id, checkoutKey, returnTo);
        }
    }

    @GetMapping("/merch/checkout/success")
    public String checkoutSuccess(@RequestParam("orderId") Long orderId,
                                  @RequestParam(name = "session_id", required = false) String sessionId,
                                  RedirectAttributes ra) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        Optional<MerchOrder> optOrder = orderService.findByIdForUser(orderId, user.getId());
        if (optOrder.isEmpty()) {
            ra.addFlashAttribute("orderError", "Order not found.");
            return "redirect:/orders";
        }

        MerchOrder existing = optOrder.get();
        if (sessionId == null || !Objects.equals(existing.getPaymentReference(), sessionId)) {
            ra.addFlashAttribute("orderError", "Payment reference does not match this order. Your order has not changed.");
            return "redirect:/orders";
        }
        if (existing.getPaymentStatus() == PaymentStatus.PAID) {
            ra.addFlashAttribute("orderSuccess", "Order #" + existing.getId() + " is already confirmed.");
            return "redirect:/orders";
        }
        try {
            if (!paymentGateway.isConfigured() || paymentGateway.isSimulationMode()) {
                ra.addFlashAttribute("orderError", "Payment verification is unavailable. Your order has not changed.");
                return "redirect:/orders";
            }
            MerchPaymentVerification verification = paymentGateway.verifyCheckoutSession(sessionId);
            if (!verification.paid()) {
                ra.addFlashAttribute("orderError", "Payment is not verified yet. Your order remains pending; no stock has been released.");
                return "redirect:/orders";
            }
            MerchOrder order = orderService.completePaidOrder(orderId, verification.reference());
            ra.addFlashAttribute("orderSuccess", "Order #" + order.getId() + " placed successfully.");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("orderError", "Payment could not be verified. Check your order history before retrying checkout.");
        }
        return "redirect:/orders";
    }

    @GetMapping("/merch/checkout/cancel")
    public String checkoutCancel(@RequestParam("orderId") Long orderId, RedirectAttributes ra) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) {
            return "redirect:/login";
        }

        Optional<MerchOrder> optOrder = orderService.findByIdForUser(orderId, user.getId());
        ra.addFlashAttribute("checkoutError", "Checkout was left before completion. Review the pending order and confirm cancellation to release its stock.");
        return "redirect:/orders";
    }

    @PostMapping("/merch/checkout/cancel")
    public String confirmCheckoutCancel(@RequestParam Long orderId, RedirectAttributes ra) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return "redirect:/login";
        if (orderService.findByIdForUser(orderId, user.getId()).isEmpty()) {
            ra.addFlashAttribute("orderError", "Order not found."); return "redirect:/orders";
        }
        try {
            orderService.cancelPendingPayment(orderId, "Cancelled by the owner before payment confirmation.");
            ra.addFlashAttribute("checkoutError", "Pending checkout cancellation checked. Confirmed payments remain unchanged.");
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("orderError", "Hosted checkout could not be closed. The order and reservation are kept; check payment status before retrying.");
        }
        return "redirect:/orders";
    }

    private void retainDraft(RedirectAttributes ra, String quantity, String key, Long selectedCardId,
                             String holder, String brand, Short month, Short year, boolean saveCard) {
        ra.addFlashAttribute("checkoutQuantity", bounded(quantity, 12));
        if (validKey(key)) ra.addFlashAttribute("checkoutKey", key);
        // Only harmless display fields survive a rejected submit. Never echo PAN, tokens or last-four input.
        ra.addFlashAttribute("checkoutHolder", bounded(holder, 200));
        ra.addFlashAttribute("checkoutBrand", bounded(brand, 50));
        ra.addFlashAttribute("checkoutMonth", month);
        ra.addFlashAttribute("checkoutYear", year);
        ra.addFlashAttribute("checkoutSaveCard", saveCard);
        ra.addFlashAttribute("checkoutDraft", true);
        if (selectedCardId != null && selectedCardId > 0) ra.addFlashAttribute("checkoutSelectedCardId", selectedCardId);
    }

    private String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), limit));
    }

    private java.math.BigDecimal initialTotal(MerchProduct product, Object draftQuantity) {
        try {
            int quantity = draftQuantity == null ? 1 : Integer.parseInt(String.valueOf(draftQuantity));
            if (quantity < 1 || quantity > product.getStockQuantity() || product.getPrice() == null) return null;
            return product.getPrice().multiply(java.math.BigDecimal.valueOf(quantity));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean validKey(String key) {
        return key != null && key.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private String checkoutReturn(Long id, String key, String returnTo) {
        String query = validKey(key) ? "?checkoutKey=" + key.toLowerCase(java.util.Locale.ROOT) : "";
        String back = shopReturn(returnTo);
        if (!"/merch".equals(back)) query += (query.isEmpty() ? "?" : "&") + "returnTo="
                + java.net.URLEncoder.encode(back, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "redirect:/merch/" + id + "/buy" + query;
    }

    private String shopReturn(String value) {
        if (value == null || value.length() > 4096 || value.chars().anyMatch(c -> c < 32)) return "/merch";
        try {
            var uri = java.net.URI.create(value);
            if (!uri.isAbsolute() && uri.getRawAuthority() == null && "/merch".equals(uri.getRawPath())
                    && uri.getRawFragment() == null) return value;
        } catch (IllegalArgumentException ignored) { /* Fall back to the native catalogue route. */ }
        return "/merch";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:8080";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
