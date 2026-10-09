package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import uk.ac.cf._5.group14.One_To_One.Users.AuthHelper;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import java.util.Map;

@Controller
@RequestMapping("/orders")
public class OrdersController {
    private final MerchOrderService merchOrderService;
    private final AuthHelper authHelper;

    public OrdersController(MerchOrderService merchOrderService, AuthHelper authHelper) {
        this.merchOrderService = merchOrderService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ModelAndView getOrders(
            @RequestParam(value = "search", defaultValue = "") String search,
            @RequestParam(value = "status", defaultValue = "") String statusFilter,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "created_at") String sortField) {
        User user = authHelper.getAuthenticatedUser();
        if (user == null) return new ModelAndView("redirect:/login");
        var view = new ModelAndView("shared-views/orders/orders");
        boolean invalid = search.length() > 120 || statusFilter.length() > 30 || page < 0;
        ShippingStatus shippingStatus = null;
        boolean unknownShippingStatus = false;
        if (!statusFilter.isEmpty()) {
            try { shippingStatus = ShippingStatus.valueOf(statusFilter); }
            catch (IllegalArgumentException ex) { invalid = true; unknownShippingStatus = true; }
        }
        // Legacy size/sort links remain accepted; history uses one stable twenty-order bound.
        Page<MerchOrder> history = invalid ? Page.empty(PageRequest.of(0, 20))
                : merchOrderService.searchHistory(user.getId(), search, shippingStatus, Math.max(1, page));
        if (invalid) view.setStatus(HttpStatus.BAD_REQUEST);
        view.addObject("user", user);
        view.addObject("search", search.substring(0, Math.min(121, search.length())));
        view.addObject("statusFilter", statusFilter.substring(0, Math.min(31, statusFilter.length())));
        view.addObject("historyFilterInvalid", invalid);
        view.addObject("unknownShippingStatus", unknownShippingStatus);
        view.addObject("historyPage", history);
        view.addObject("orders", history.getContent());
        view.addObject("ownedOrderCount", merchOrderService.countOrdersForUser(user.getId()));
        view.addObject("firstOrder", history.isEmpty() ? 0L : history.getPageable().getOffset() + 1);
        view.addObject("lastOrder", history.isEmpty() ? 0L : history.getPageable().getOffset() + history.getNumberOfElements());
        view.addObject("shippingStatuses", ShippingStatus.values());
        view.addObject("shippingLabels", Map.of(
                "PENDING", "ui.00168", "PROCESSING", "ui.02203",
                "SHIPPED", "ui.02204", "OUT_FOR_DELIVERY", "ui.02205",
                "DELIVERED", "ui.02206", "CANCELLED", "ui.02207",
                "RETURNED", "ui.02220", "FAILED_DELIVERY", "ui.02221"));
        view.addObject("paymentLabels", Map.of(
                "PENDING_PAYMENT", "ui.orders.paymentPending", "PAID", "ui.orders.paymentPaid",
                "FAILED", "ui.orders.paymentFailed", "REFUNDED", "ui.orders.paymentRefunded"));
        return view;
    }
}
