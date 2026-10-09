package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProductService;
import uk.ac.cf._5.group14.One_To_One.Operations.ExclusiveScheduledJob;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
@Transactional
public class MerchOrderServiceImpl implements MerchOrderService {

    private static final Logger log = Logger.getLogger(MerchOrderServiceImpl.class.getName());
    static final long PENDING_PAYMENT_TTL_SECONDS = 2L * 60L * 60L;

    private final MerchOrderRepository orderRepo;
    private final MerchProductService productService;

    private final EntityManager entities;
    private final MerchPaymentGateway gateway;

    public MerchOrderServiceImpl(MerchOrderRepository orderRepo, MerchProductService productService, EntityManager entities, MerchPaymentGateway gateway) {
        this.orderRepo = orderRepo;
        this.productService = productService;
        this.entities = entities;
        this.gateway = gateway;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchOrder> getOrdersForUser(Long userId) {
        return orderRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<MerchOrder> searchHistory(Long userId, String search, ShippingStatus status, int page) {
        if (userId == null || page < 1 || search == null || search.length() > 120) {
            throw new IllegalArgumentException("Order filters are invalid.");
        }
        String query = "%" + search.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long count = orderRepo.countHistory(userId, query, status);
        int lastPage = (int) Math.max(1, Math.min(Integer.MAX_VALUE, (count + 19) / 20));
        var bounds = org.springframework.data.domain.PageRequest.of(Math.min(page, lastPage) - 1, 20);
        List<Long> ids = orderRepo.findHistoryIds(userId, query, status, bounds);
        return new org.springframework.data.domain.PageImpl<>(historyDocuments(userId, ids), bounds, count);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MerchOrder> getRecentOrdersForUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("User is required.");
        return historyDocuments(userId, orderRepo.findHistoryIds(userId, "%", null,
                org.springframework.data.domain.PageRequest.of(0, 5)));
    }

    @Override
    @Transactional(readOnly = true)
    public long countOrdersForUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("User is required.");
        return orderRepo.countByUserId(userId);
    }

    private List<MerchOrder> historyDocuments(Long userId, List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        var documents = orderRepo.findHistoryDocuments(userId, ids).stream()
                .collect(java.util.stream.Collectors.toMap(MerchOrder::getId, java.util.function.Function.identity()));
        return ids.stream().map(documents::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchOrder> findByIdForUser(Long id, Long userId) {
        return orderRepo.findByIdAndUserId(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchOrder> findByCheckoutKeyForUser(String key, Long userId) {
        if (key == null) return Optional.empty();
        return orderRepo.findByUserIdAndCheckoutKey(userId, key.toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public MerchOrder createPendingOrderOnce(User user, MerchProduct product, int quantity, String checkoutKey,
                                             java.util.function.Supplier<SavedPaymentMethod> paymentMethod) {
        return createPendingOrderOnce(user, product, quantity, checkoutKey, null, paymentMethod);
    }

    @Override
    public MerchOrder createPendingOrderOnce(User user, MerchProduct product, int quantity, String checkoutKey,
                                             Long expectedUnitMinor, java.util.function.Supplier<SavedPaymentMethod> paymentMethod) {
        if (user == null || user.getId() == null || product == null || product.getId() == null
                || checkoutKey == null || !checkoutKey.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("Checkout is invalid. Open a fresh checkout form.");
        }
        String key = checkoutKey.toLowerCase(java.util.Locale.ROOT);
        User owner = entities.find(User.class, user.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (owner == null) throw new IllegalArgumentException("User is required.");
        // Serialise creation by owner, including card saving, before checking the unique purchase key.
        entities.refresh(owner);
        Optional<MerchOrder> existing = orderRepo.findByUserIdAndCheckoutKey(owner.getId(), key);
        if (existing.isPresent()) {
            MerchOrder order = existing.get();
            entities.refresh(order);
            if (order.getItems().size() != 1 || order.getItems().get(0).getProduct() == null
                    || !product.getId().equals(order.getItems().get(0).getProduct().getId())
                    || quantity != order.getItems().get(0).getQuantity()) {
                throw new IllegalArgumentException("Checkout details changed. Open a fresh checkout form.");
            }
            return order;
        }
        MerchProduct currentProduct = entities.find(MerchProduct.class, product.getId(), jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (currentProduct == null) throw new IllegalArgumentException("Product is not available.");
        entities.refresh(currentProduct);
        if (expectedUnitMinor != null && (currentProduct.getPrice() == null
                || currentProduct.getPrice().movePointRight(2).compareTo(BigDecimal.valueOf(expectedUnitMinor)) != 0)) {
            throw new PriceChangedException();
        }
        MerchOrder order = createPendingOrder(owner, currentProduct, quantity);
        SavedPaymentMethod selected = paymentMethod == null ? null : paymentMethod.get();
        order.setCheckoutKey(key);
        order.setPaymentMethod(selected);
        return orderRepo.saveAndFlush(order);
    }

    @Override
    public MerchOrder createPendingOrder(User user, MerchProduct product, int quantity) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User is required.");
        }
        if (product == null || product.getId() == null || !product.isActive() || product.getPrice() == null
                || product.getPrice().signum() <= 0 || product.getPrice().scale() > 2) {
            throw new IllegalArgumentException("Product is not available.");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1.");
        }

        boolean decremented = productService.decrementStock(product.getId(), quantity);
        if (!decremented) {
            log.warning(() -> "Stock reservation failed: product=" + product.getId() + " requestedQty=" + quantity);
            throw new IllegalStateException("Insufficient stock.");
        }

        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(quantity));

        MerchOrder order = new MerchOrder();
        order.setUser(user);
        order.setTotalAmount(total);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.setRefundStatus(RefundStatus.NONE);
        order.setShippingStatus(ShippingStatus.PENDING);

        MerchOrderItem item = new MerchOrderItem();
        item.setOrder(order);
        item.setProduct(product);
        item.setProductNameSnapshot(product.getName());
        item.setPriceSnapshot(product.getPrice());
        item.setImageUrlSnapshot(product.getImageUrl());
        item.setCategorySnapshot(product.getCategory());
        item.setQuantity(quantity);
        order.getItems().add(item);

        MerchOrder saved = orderRepo.save(order);
        log.info(() -> "Pending merch order created: orderId=" + saved.getId()
                + " userId=" + user.getId()
                + " productId=" + product.getId()
                + " qty=" + quantity
                + " total=" + total);
        return saved;
    }

    @Override
    public MerchOrder prepareHostedCheckout(Long orderId, String provider, String currency, String successUrl, String cancelUrl) {
        MerchOrder order = requireOrder(orderId);
        if (order.getPaymentProvider() != null && !java.util.Objects.equals(order.getPaymentProvider(), provider)) {
            throw new IllegalStateException("A checkout provider cannot be replaced.");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) return order;
        if (order.getCheckoutCurrency() == null) {
            if (currency == null || !currency.matches("[a-z]{3}") || successUrl == null || cancelUrl == null
                    || successUrl.length() > 2048 || cancelUrl.length() > 2048) {
                throw new IllegalArgumentException("Hosted checkout configuration is invalid.");
            }
            order.setCheckoutCurrency(currency);
            order.setCheckoutSuccessUrl(successUrl);
            order.setCheckoutCancelUrl(cancelUrl);
        }
        order.setPaymentProvider(provider);
        return orderRepo.save(order);
    }

    @Override
    public MerchOrder markCheckoutSessionCreated(Long orderId,
                                                 String paymentProvider,
                                                 String paymentReference,
                                                 SavedPaymentMethod paymentMethod) {
        MerchOrder order = requireOrder(orderId);
        if (order.getPaymentProvider() != null && !java.util.Objects.equals(order.getPaymentProvider(), paymentProvider)) {
            throw new IllegalStateException("A checkout provider cannot be replaced.");
        }
        if (order.getPaymentReference() != null) {
            if (paymentReference == null || (java.util.Objects.equals(order.getPaymentProvider(), paymentProvider)
                    && java.util.Objects.equals(order.getPaymentReference(), paymentReference))) return order;
            throw new IllegalStateException("An existing payment session cannot be replaced.");
        }
        if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order is not awaiting payment.");
        }
        order.setPaymentProvider(paymentProvider);
        order.setPaymentReference(paymentReference);
        order.setPaymentMethod(paymentMethod);
        order.setPaymentFailureReason(null);
        return orderRepo.save(order);
    }

    @Override
    public MerchOrder completePaidOrder(Long orderId, String paymentReference) {
        MerchOrder order = requireOrder(orderId);

        if (paymentReference == null || paymentReference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required.");
        }
        if (order.getPaymentReference() == null || !order.getPaymentReference().equals(paymentReference)) {
            throw new IllegalArgumentException("Payment reference mismatch.");
        }
        if (order.getPaymentStatus() == PaymentStatus.PAID) return order;
        if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("Order is not awaiting payment.");
        }

        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaymentFailureReason(null);
        order.setPaymentConfirmedAt(Instant.now());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setShippingStatus(ShippingStatus.PENDING);
        return orderRepo.save(order);
    }

    @Override
    public MerchOrder cancelPendingPayment(Long orderId, String failureReason) {
        MerchOrder order = requireOrder(orderId);
        if (order.getPaymentStatus() != PaymentStatus.PENDING_PAYMENT) {
            return order;
        }

        if (order.getPaymentProvider() != null && !"SIMULATED".equals(order.getPaymentProvider())
                && order.getPaymentReference() == null) {
            throw new IllegalStateException("Hosted payment state is unknown; reserved stock is kept.");
        }
        if (order.getPaymentReference() != null && !"SIMULATED".equals(order.getPaymentProvider())
                && !gateway.expireCheckoutSession(order.getPaymentReference())) {
            throw new IllegalStateException("Hosted payment could not be closed; reserved stock is kept.");
        }
        restoreStock(order);
        order.setPaymentStatus(PaymentStatus.FAILED);
        order.setPaymentFailureReason(failureReason);
        order.setStatus(OrderStatus.CANCELLED);
        order.setShippingStatus(ShippingStatus.CANCELLED);
        return orderRepo.save(order);
    }

    @Override
    public void cancelPendingOrdersForProduct(Long productId) {
        List<MerchOrder> pending = orderRepo.findPendingOrdersContainingProduct(productId);
        for (MerchOrder order : pending) {
            cancelPendingPayment(order.getId(), "Product was removed before checkout completed.");
        }
        if (!pending.isEmpty()) {
            log.info(() -> "Cancelled " + pending.size() + " pending merch order(s) for product=" + productId);
        }
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000L)
    @ExclusiveScheduledJob(value = "abandoned-merch-orders", lockAtMostFor = "PT20M")
    public void expireAbandonedPendingOrders() {
        Instant cutoff = Instant.now().minusSeconds(PENDING_PAYMENT_TTL_SECONDS);
        List<MerchOrder> abandoned = orderRepo.findByPaymentStatusAndCreatedAtBefore(PaymentStatus.PENDING_PAYMENT, cutoff);
        for (MerchOrder order : abandoned) {
            try { cancelPendingPayment(order.getId(), "Checkout expired before payment completed."); }
            catch (IllegalStateException ex) { log.warning("Pending checkout retained for provider reconciliation: order=" + order.getId()); }
        }
        if (!abandoned.isEmpty()) {
            log.info(() -> "Expired " + abandoned.size() + " abandoned merch checkout(s).");
        }
    }

    private MerchOrder requireOrder(Long orderId) {
        MerchOrder order = orderRepo.findLockedById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        // A scheduled sweep may already have loaded this entity before waiting for the lock.
        entities.refresh(order);
        return order;
    }

    private void restoreStock(MerchOrder order) {
        for (MerchOrderItem item : order.getItems()) {
            if (item.getProduct() != null && item.getQuantity() > 0) {
                productService.incrementStock(item.getProduct().getId(), item.getQuantity());
            }
        }
    }
}
