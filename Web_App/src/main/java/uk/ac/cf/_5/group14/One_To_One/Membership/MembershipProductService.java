package uk.ac.cf._5.group14.One_To_One.Membership;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class MembershipProductService {
    
    private final GymMembershipProductRepository productRepository;
    private final GymMemberSubscriptionRepository subscriptionRepository;
    private final PriceChangeEventRepository priceChangeEventRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final java.time.Clock clock;
    private final uk.ac.cf._5.group14.One_To_One.GymProfile.GymWorkspaceAccessService workspace;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Value("${app.links.manageMembershipUrl:/dashboard}")
    private String manageMembershipUrl;
    
    /**
     * Create a new membership product for a gym
     */
    @Transactional
    public GymMembershipProduct createProduct(GymMembershipProduct product) {
        log.info("Creating new membership product: {} for gym {}", product.getName(), product.getGymId());
        return productRepository.save(product);
    }
    
    /**
     * Update an existing membership product (excluding price)
     */
    @Transactional
    public GymMembershipProduct updateProduct(Long id, Long gymId, MembershipProductForm draft) {
        // Copy only editable values after refreshing the locked row. A request may
        // already hold an older managed price, billing period or scheduled change.
        GymMembershipProduct product = productRepository.findLockedById(id)
            .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied"));
        entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(product.getGymId(), gymId)) throw new IllegalArgumentException("Product not found or access denied");
        product.setName(draft.getName().trim());
        product.setDescription(draft.getDescription());
        product.setActive(draft.isActive());
        log.info("Updating membership product details: {}", id);
        return productRepository.save(product);
    }

    /** Lock the current row so a status toggle cannot overwrite a concurrent price change. */
    @Transactional
    public void setProductActive(Long id, Long gymId, boolean active) {
        GymMembershipProduct product = productRepository.findLockedById(id)
            .filter(p -> Objects.equals(p.getGymId(), gymId))
            .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied"));
        // The controller may already have loaded this row into the request persistence context.
        entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(product.getGymId(), gymId)) throw new IllegalArgumentException("Product not found or access denied");
        product.setActive(active);
        productRepository.save(product);
    }

    public long countProducts(Long gymId) { return productRepository.countByGymId(gymId); }
    public long countActiveProducts(Long gymId) { return productRepository.countByGymIdAndActive(gymId, true); }

    /** Count before clamping; only the selected bounded page needs scheduled-price resolution. */
    @Transactional
    public Page<GymMembershipProduct> searchProducts(Long gymId, String search, String state, int page, int size) {
        if (search == null || search.length() > 120 || state == null || !List.of("", "ACTIVE", "INACTIVE").contains(state) || size < 1 || size > 50)
            throw new IllegalArgumentException("Invalid product filters");
        String pattern = "%" + search.trim().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = productRepository.countCatalogue(gymId, pattern, state);
        int selected = (int) Math.min(Math.max(0L, page), Math.max(0L, (total - 1) / size));
        var pageable = PageRequest.of(selected, size);
        var products = total == 0 ? List.<GymMembershipProduct>of() : productRepository.findCatalogue(gymId, pattern, state, pageable);
        products.forEach(this::applyDuePriceChangesForProduct);
        return new PageImpl<>(products, pageable, total);
    }
    
    /**
     * Get all products for a gym
     */
    @Transactional
    public List<GymMembershipProduct> getProductsByGymId(Long gymId) {
        List<GymMembershipProduct> products = productRepository.findByGymIdOrderByCreatedAtDesc(gymId);
        products.forEach(this::applyDuePriceChangesForProduct);
        return products;
    }

    /**
     * Get paginated products for a gym
     */
    @Transactional
    public Page<GymMembershipProduct> getProductsByGymId(Long gymId, Pageable pageable) {
        Page<GymMembershipProduct> products = productRepository.findByGymIdOrderByCreatedAtDesc(gymId, pageable);
        products.forEach(this::applyDuePriceChangesForProduct);
        return products;
    }
    
    /**
     * Get active products for a gym
     */
    @Transactional
    public List<GymMembershipProduct> getActiveProductsByGymId(Long gymId) {
        List<GymMembershipProduct> products = productRepository.findByGymIdAndActiveOrderByCreatedAtDesc(gymId, true);
        products.forEach(this::applyDuePriceChangesForProduct);
        return products;
    }
    
    /**
     * Get a product by ID and gym ID (for authorization)
     */
    @Transactional
    public GymMembershipProduct getProductByIdAndGymId(Long productId, Long gymId) {
        GymMembershipProduct product = productRepository.findByIdAndGymId(productId, gymId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found or access denied"));
        applyDuePriceChangesForProduct(product);
        if (!Objects.equals(product.getGymId(), gymId)) throw new IllegalArgumentException("Product not found or access denied");
        return product;
    }
    
    /**
     * Initiate a price change for a membership product.
     * This creates an audit trail, counts affected members, and attempts configured
     * email notifications synchronously.
     * 
     * @param productId The ID of the product
     * @param newPriceCents The new price in cents
     * @param effectiveAt When the new price takes effect (defaults to next renewal for all members)
     * @param reason The reason for the price change (required)
     * @param changedByUserId The ID of the user making the change
     * @return The created PriceChangeEvent
     */
    @Transactional
    public PriceChangeEvent initiatePriceChange(
        Long productId,
        Integer newPriceCents,
        Instant effectiveAt,
        String reason,
        Long changedByUserId
    ) {
        return initiatePriceChange(productId, newPriceCents, effectiveAt, reason, changedByUserId, null);
    }

    /** Native submissions must review the current price; legacy internal callers retain their contract. */
    @Transactional
    public PriceChangeEvent initiatePriceChange(Long productId, Integer newPriceCents, Instant effectiveAt,
                                                String reason, Long changedByUserId, Integer quotedPriceCents) {
        if (newPriceCents == null || newPriceCents < 0) {
            throw new IllegalArgumentException("A valid price is required");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new IllegalArgumentException("Reason is required for price changes");
        }
        
        if (effectiveAt == null) {
            throw new IllegalArgumentException("Effective date is required for price changes");
        }
        
        // Get the product
        GymMembershipProduct product = productRepository.findLockedById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
        entityManager.refresh(product, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        userRepository.findById(changedByUserId)
            .filter(user -> workspace.ownedGym(user).filter(gym -> Objects.equals(gym.getId(), product.getGymId())).isPresent())
            .orElseThrow(() -> new IllegalArgumentException("Access denied"));
        reason = reason.trim();
        var duplicate = priceChangeEventRepository.findFirstByProductIdAndNewPriceCentsAndEffectiveAtAndReasonOrderByCreatedAtDesc(
            productId, newPriceCents, effectiveAt, reason);
        if (duplicate.isPresent()) return duplicate.get();

        if (effectiveAt.isBefore(clock.instant())) throw new IllegalArgumentException("Effective date cannot be in the past");
        applyDuePriceChangesForProduct(product);
        if (quotedPriceCents != null && !quotedPriceCents.equals(product.getPriceCents()))
            throw new PriceReviewRequiredException();

        Integer oldPriceCents = product.getPriceCents();
        
        if (oldPriceCents.equals(newPriceCents)) {
            throw new IllegalArgumentException("New price must be different from current price");
        }
        
        // Count affected active subscribers
        List<GymMemberSubscription> activeSubscriptions = 
            subscriptionRepository.findByProductIdAndStatus(productId, SubscriptionStatus.ACTIVE);
        
        int affectedCount = activeSubscriptions.size();
        
        log.info("[AUDIT] Price change requested by user {} for product {}: ${} -> ${}, effective {}, affecting {} members",
            changedByUserId, productId, oldPriceCents / 100.0, newPriceCents / 100.0, effectiveAt, affectedCount);
        log.info("Initiating price change for product {}: ${} -> ${}, affecting {} members",
            productId, oldPriceCents / 100.0, newPriceCents / 100.0, affectedCount);
        
        // Create audit event
        PriceChangeEvent event = new PriceChangeEvent();
        event.setGymId(product.getGymId());
        event.setProductId(productId);
        event.setOldPriceCents(oldPriceCents);
        event.setNewPriceCents(newPriceCents);
        event.setEffectiveAt(effectiveAt);
        event.setReason(reason);
        event.setChangedByUserId(changedByUserId);
        event.setAffectedMemberCount(affectedCount);
        
        event = priceChangeEventRepository.save(event);

        log.info("[AUDIT] Price change confirmed by user {} for product {}. Event id: {}",
            changedByUserId, productId, event.getId());

        if (!effectiveAt.isAfter(clock.instant())) {
            product.setPriceCents(newPriceCents);
            productRepository.save(product);
        }
        
        // Attempt email notifications synchronously for all affected members.
        for (GymMemberSubscription subscription : activeSubscriptions) {
            User user = userRepository.findById(subscription.getUserId())
                .orElse(null);
            
            if (user != null) {
                try {
                    emailService.sendPriceChangeNotification(
                        user,
                        product.getName(),
                        oldPriceCents / 100.0,
                        newPriceCents / 100.0,
                        effectiveAt,
                        reason,
                        manageMembershipUrl
                    );
                } catch (Exception e) {
                    log.error("Failed to send price change email to user {}", user.getId(), e);
                }
            }
        }
        
        log.info(
            "Price change complete. Attempted {} email notifications; delivery is not tracked.",
            affectedCount
        );
        
        return event;
    }

    @Transactional
    public void applyDuePriceChangesForProduct(GymMembershipProduct product) {
        PriceChangeEvent latestEffective = priceChangeEventRepository
            .findFirstByProductIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(product.getId(), clock.instant());

        if (latestEffective != null) {
            GymMembershipProduct current = productRepository.findLockedById(product.getId()).orElseThrow();
            entityManager.refresh(current, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            // Re-read after acquiring the product lock; another accepted change may now be due.
            latestEffective = priceChangeEventRepository
                .findFirstByProductIdAndEffectiveAtLessThanEqualOrderByEffectiveAtDescIdDesc(product.getId(), clock.instant());
            if (latestEffective == null) return;
            if (current != product) product.setPriceCents(current.getPriceCents());
            if (latestEffective.getNewPriceCents().equals(current.getPriceCents())) return;
            current.setPriceCents(latestEffective.getNewPriceCents());
            productRepository.save(current);
            if (current != product) product.setPriceCents(current.getPriceCents());
            log.info("Applied due price change for product {} to ${}",
                product.getId(), latestEffective.getNewPriceCents() / 100.0);
        }
    }
    
    /**
     * Get price change history for a product
     */
    public List<PriceChangeEvent> getPriceChangeHistory(Long productId) {
        return priceChangeEventRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
    }

    public Page<PriceChangeEvent> getPriceChangeHistory(Long productId, Pageable pageable) {
        int size = Math.min(50, Math.max(1, pageable.getPageSize()));
        long count = priceChangeEventRepository.countByProductId(productId);
        int page = (int) Math.min(Math.max(0L, pageable.getPageNumber()), Math.max(0L, (count - 1) / size));
        return priceChangeEventRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId, PageRequest.of(page, size));
    }

    public static class PriceReviewRequiredException extends IllegalArgumentException {
        public PriceReviewRequiredException() { super("Review the current price before confirming this change"); }
    }
    
    /**
     * Get price change history for a gym
     */
    public List<PriceChangeEvent> getPriceChangeHistoryByGym(Long gymId) {
        return priceChangeEventRepository.findByGymIdOrderByCreatedAtDesc(gymId);
    }
    
    /**
     * Subscribe a user to a membership product
     */
    @Transactional
    public GymMemberSubscription createSubscription(
        Long userId,
        Long gymId,
        Long productId,
        Instant renewsAt
    ) {
        // Check if user already has an active subscription for this gym
        subscriptionRepository.findByUserIdAndGymIdAndStatus(userId, gymId, SubscriptionStatus.ACTIVE)
            .ifPresent(sub -> {
                throw new IllegalArgumentException("User already has an active subscription for this gym");
            });
        
        GymMemberSubscription subscription = new GymMemberSubscription();
        subscription.setUserId(userId);
        subscription.setGymId(gymId);
        subscription.setProductId(productId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setRenewsAt(renewsAt);
        
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Cancel a subscription (will expire at end of current period)
     */
    @Transactional
    public void cancelSubscription(Long subscriptionId) {
        GymMemberSubscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        
        subscriptionRepository.save(subscription);
        
        log.info("Subscription {} cancelled. Will expire at {}", subscriptionId, subscription.getRenewsAt());
    }
}
