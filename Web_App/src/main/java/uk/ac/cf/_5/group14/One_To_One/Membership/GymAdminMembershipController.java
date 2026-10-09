package uk.ac.cf._5.group14.One_To_One.Membership;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import uk.ac.cf._5.group14.One_To_One.GymProfile.GymWorkspaceAccessService;

@Slf4j
@Controller
@RequestMapping("/gym/admin/memberships")
@RequiredArgsConstructor
public class GymAdminMembershipController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;
    
    private final MembershipProductService membershipService;
    private final GymMemberSubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final GymWorkspaceAccessService workspace;
    private final MessageSource messages;
    
    @InitBinder("product")
    void bindProduct(WebDataBinder binder) {
        binder.setAllowedFields("name", "description", "priceDollars", "billingPeriod", "active");
    }

    @InitBinder("priceChange")
    void bindPriceChange(WebDataBinder binder) {
        binder.setAllowedFields("newPriceDollars", "reason", "effectiveDate");
    }

    /**
     * List all membership products for the gym
     */
    @GetMapping
    public String listProducts(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", required = false) Integer size,
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "") String state,
        HttpServletResponse response,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        int pageSize = resolvePageSize(size);
        boolean invalid = search.length() > 120 || !List.of("", "ACTIVE", "INACTIVE").contains(state);
        if (invalid) response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Page<GymMembershipProduct> productsPage = invalid ? Page.empty(PageRequest.of(0, pageSize))
            : membershipService.searchProducts(admin.getGymId(), search, state, page, pageSize);
        List<GymMembershipProduct> products = productsPage.getContent();

        Map<Long, Long> subscriberCounts = new HashMap<>();
        long totalSubscribers = subscriptionRepository.countByGymIdAndStatus(admin.getGymId(), SubscriptionStatus.ACTIVE);
        for (GymMembershipProduct product : products) subscriberCounts.put(product.getId(), 0L);
        if (!products.isEmpty()) {
            subscriptionRepository.countForProducts(admin.getGymId(), SubscriptionStatus.ACTIVE, products.stream().map(GymMembershipProduct::getId).toList())
                .forEach(count -> subscriberCounts.put(count.getProductId(), count.getSubscribers()));
        }

        model.addAttribute("productsPage", productsPage);
        model.addAttribute("products", products);
        model.addAttribute("subscriberCounts", subscriberCounts);
        model.addAttribute("subscribersCount", totalSubscribers);
        model.addAttribute("activeProductsCount", membershipService.countActiveProducts(admin.getGymId()));
        model.addAttribute("totalProductsCount", membershipService.countProducts(admin.getGymId()));
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("search", search.length() > 120 ? search.substring(0, 120) : search);
        model.addAttribute("state", state.length() > 40 ? state.substring(0, 40) : state);
        model.addAttribute("productFilterInvalid", invalid);
        model.addAttribute("rangeStart", productsPage.isEmpty() ? 0 : productsPage.getNumber() * (long) pageSize + 1);
        model.addAttribute("rangeEnd", productsPage.getNumber() * (long) pageSize + productsPage.getNumberOfElements());
        
        return "gym-views/gym-admin/memberships/list";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(
        @PathVariable Long id,
        @RequestParam("active") boolean active,
        @RequestParam(defaultValue = "") String search,
        @RequestParam(defaultValue = "") String state,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(required = false) Integer size,
        @AuthenticationPrincipal UserDetails userDetails,
        RedirectAttributes redirectAttributes
    ) {
        User admin = getUserFromDetails(userDetails);

        requireOwnedProduct(id, admin.getGymId());
        membershipService.setProductActive(id, admin.getGymId(), active);

        redirectAttributes.addFlashAttribute(
            "successMessage",
            messages.getMessage(active ? "ui.gymProducts.activated" : "ui.gymProducts.deactivated", null, LocaleContextHolder.getLocale())
        );
        if (search.length() > 120 || !List.of("", "ACTIVE", "INACTIVE").contains(state)
            || (search.isEmpty() && state.isEmpty() && page == 0 && size == null)) return "redirect:/gym/admin/memberships";
        return "redirect:/gym/admin/memberships?search=" + URLEncoder.encode(search, StandardCharsets.UTF_8)
            + "&state=" + state + "&page=" + Math.max(0, page) + "&size=" + resolvePageSize(size) + "#membership-results";
    }
    
    /**
     * Show form to create a new product
     */
    @GetMapping("/create")
    public String showCreateForm(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            model.addAttribute("error", "You must be associated with a gym to create memberships");
            return "system-views/error/403";
        }
        
        model.addAttribute("product", new MembershipProductForm());
        return "gym-views/gym-admin/memberships/form";
    }
    
    /**
     * Create a new membership product
     */
    @PostMapping("/create")
    public String createProduct(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @ModelAttribute("product") MembershipProductForm product,
        BindingResult result,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "You must be associated with a gym");
            return "redirect:/gym/admin/memberships";
        }

        if (product.getPriceDollars() == null) {
            result.rejectValue("priceDollars", "NotNull", "Price is required");
        }

        if (result.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "gym-views/gym-admin/memberships/form";
        }
        
        GymMembershipProduct created = new GymMembershipProduct(admin.getGymId(), product.getName().trim(), product.toPriceCents());
        created.setDescription(product.getDescription());
        created.setBillingPeriod(product.getBillingPeriod());
        created.setActive(product.isActive());
        membershipService.createProduct(created);
        
        redirectAttributes.addFlashAttribute("successMessage", "Membership product created successfully");
        return "redirect:/gym/admin/memberships";
    }
    
    /**
     * Show form to edit an existing product
     */
    @GetMapping("/{id}/edit")
    public String showEditForm(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            model.addAttribute("error", "Access denied");
            return "system-views/error/403";
        }
        
        GymMembershipProduct product = requireOwnedProduct(id, admin.getGymId());
        model.addAttribute("product", MembershipProductForm.from(product));
        model.addAttribute("editingId", product.getId());
        model.addAttribute("currentPrice", product.getPriceDollars());
        return "gym-views/gym-admin/memberships/form";
    }
    
    /**
     * Update an existing product (name, description, active status only - not price)
     */
    @PostMapping("/{id}/edit")
    public String updateProduct(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @ModelAttribute("product") MembershipProductForm updatedProduct,
        BindingResult result,
        HttpServletResponse response,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
            return "redirect:/gym/admin/memberships";
        }
        
        GymMembershipProduct existingProduct = requireOwnedProduct(id, admin.getGymId());
        model.addAttribute("editingId", existingProduct.getId());
        model.addAttribute("currentPrice", existingProduct.getPriceDollars());
        if (result.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "gym-views/gym-admin/memberships/form";
        }
        
        // Update allowed fields only (not price)
        existingProduct.setName(updatedProduct.getName().trim());
        existingProduct.setDescription(updatedProduct.getDescription());
        existingProduct.setActive(updatedProduct.isActive());
        
        membershipService.updateProduct(existingProduct);
        
        redirectAttributes.addFlashAttribute("successMessage", "Product updated successfully");
        return "redirect:/gym/admin/memberships";
    }
    
    /**
     * Show price change confirmation form
     */
    @GetMapping("/{id}/price-change")
    public String showPriceChangeForm(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            model.addAttribute("error", "Access denied");
            return "system-views/error/403";
        }
        
        GymMembershipProduct product = requireOwnedProduct(id, admin.getGymId());
        long affectedCount = subscriptionRepository.countByProductIdAndStatus(id, SubscriptionStatus.ACTIVE);
        LocalDate defaultEffectiveDate = resolveDefaultEffectiveDate(id).orElse(LocalDate.now().plusDays(30));

        PriceChangeRequest priceChange = new PriceChangeRequest();
        priceChange.setEffectiveDate(defaultEffectiveDate);

        model.addAttribute("product", product);
        model.addAttribute("affectedMemberCount", affectedCount);
        model.addAttribute("defaultEffectiveDate", defaultEffectiveDate);
        model.addAttribute("priceChange", priceChange);
        
        return "gym-views/gym-admin/memberships/price-change";
    }
    
    /**
     * Execute price change
     */
    @PostMapping("/{id}/price-change")
    public String executePriceChange(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @ModelAttribute("priceChange") PriceChangeRequest priceChange,
        BindingResult result,
        HttpServletResponse response,
        @RequestParam(defaultValue = "false") boolean confirmPriceChange,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Access denied");
            return "redirect:/gym/admin/memberships";
        }

        GymMembershipProduct product = requireOwnedProduct(id, admin.getGymId());

        if (!confirmPriceChange) result.reject("Confirmation", "Confirm the price change before submitting");
        Integer newPriceCents = null;
        if (!result.hasFieldErrors("newPriceDollars")) {
            try {
                newPriceCents = priceChange.toNewPriceCents();
            } catch (ArithmeticException ex) {
                result.rejectValue("newPriceDollars", "Range", "Enter a valid price with at most two decimal places");
            }
        }
        if (newPriceCents == null && !result.hasFieldErrors("newPriceDollars")) {
            result.rejectValue("newPriceDollars", "NotNull", "New price is required");
        } else if (newPriceCents != null && newPriceCents < 0) {
            result.rejectValue("newPriceDollars", "Min", "Price must be zero or greater");
        } else if (newPriceCents != null && newPriceCents.equals(product.getPriceCents())) {
            result.rejectValue("newPriceDollars", "Same", "New price must be different from current price");
        }

        LocalDate effectiveDate = priceChange.getEffectiveDate();
        if (effectiveDate != null && !effectiveDate.isAfter(LocalDate.now())) {
            result.rejectValue("effectiveDate", "Future", "Effective date must be in the future");
        }

        if (result.hasErrors()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            long affectedCount = subscriptionRepository.countByProductIdAndStatus(id, SubscriptionStatus.ACTIVE);
            LocalDate defaultEffectiveDate = resolveDefaultEffectiveDate(id).orElse(LocalDate.now().plusDays(30));
            model.addAttribute("product", product);
            model.addAttribute("affectedMemberCount", affectedCount);
            model.addAttribute("defaultEffectiveDate", defaultEffectiveDate);
            return "gym-views/gym-admin/memberships/price-change";
        }

        Instant effectiveAt = effectiveDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
        log.info("[AUDIT] Price change request submitted by user {} for product {} (gym {}). New price: ${}, effective {}, reason length {}",
            admin.getId(), product.getId(), product.getGymId(), newPriceCents / 100.0, effectiveAt, priceChange.getReason().length());

        membershipService.initiatePriceChange(
            id,
            newPriceCents,
            effectiveAt,
            priceChange.getReason(),
            admin.getId()
        );

        redirectAttributes.addFlashAttribute(
            "successMessage",
            "Price change initiated successfully. Email delivery was attempted immediately; delivery is not tracked."
        );
        return "redirect:/gym/admin/memberships/" + id + "/price-change";
    }
    
    /**
     * View price change history for a product
     */
    @GetMapping("/{id}/price-history")
    public String viewPriceHistory(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", required = false) Integer size,
        Model model
    ) {
        User admin = getUserFromDetails(userDetails);
        
        if (admin.getGymId() == null) {
            model.addAttribute("error", "Access denied");
            return "system-views/error/403";
        }
        
        GymMembershipProduct product = requireOwnedProduct(id, admin.getGymId());

        int pageSize = resolvePageSize(size);
        Page<PriceChangeEvent> historyPage = membershipService.getPriceChangeHistory(
            id,
            PageRequest.of(Math.max(page, 0), pageSize)
        );
        List<PriceChangeEvent> history = historyPage.getContent();
        
        model.addAttribute("product", product);
        model.addAttribute("priceChanges", history);
        model.addAttribute("priceChangesPage", historyPage);
        model.addAttribute("pageSize", pageSize);

        int increaseCount = (int) historyPage.getContent().stream()
            .filter(change -> change.getNewPriceCents() > change.getOldPriceCents())
            .count();
        int decreaseCount = (int) historyPage.getContent().stream()
            .filter(change -> change.getNewPriceCents() < change.getOldPriceCents())
            .count();

        Map<Long, User> changedByUsers = new HashMap<>();
        List<Long> changerIds = history.stream()
            .map(PriceChangeEvent::getChangedByUserId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
        if (!changerIds.isEmpty()) {
            userRepository.findAllById(changerIds)
                .forEach(user -> changedByUsers.put(user.getId(), user));
        }

        model.addAttribute("increaseCount", increaseCount);
        model.addAttribute("decreaseCount", decreaseCount);
        model.addAttribute("changedByUsers", changedByUsers);
        
        return "gym-views/gym-admin/memberships/price-history";
    }

    private Optional<LocalDate> resolveDefaultEffectiveDate(Long productId) {
        return subscriptionRepository.findByProductIdAndStatus(productId, SubscriptionStatus.ACTIVE)
            .stream()
            .map(GymMemberSubscription::getRenewsAt)
            .filter(Objects::nonNull)
            .map(instant -> instant.atZone(ZoneId.of("Europe/London")).toLocalDate())
            .filter(date -> date.isAfter(LocalDate.now(ZoneId.of("Europe/London"))))
            .min(Comparator.naturalOrder());
    }
    
    private GymMembershipProduct requireOwnedProduct(Long id, Long gymId) {
        try {
            return membershipService.getProductByIdAndGymId(id, gymId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private User getUserFromDetails(UserDetails userDetails) {
        if (userDetails == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        User admin = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (workspace.ownedGym(admin).isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return admin;
    }

    private int resolvePageSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
