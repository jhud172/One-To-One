package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;

public interface MerchOrderService {

    List<MerchOrder> getOrdersForUser(Long userId);

    org.springframework.data.domain.Page<MerchOrder> searchHistory(Long userId, String search, ShippingStatus status, int page);

    List<MerchOrder> getRecentOrdersForUser(Long userId);

    long countOrdersForUser(Long userId);

    Optional<MerchOrder> findByIdForUser(Long id, Long userId);

    Optional<MerchOrder> findByCheckoutKeyForUser(String key, Long userId);

    MerchOrder createPendingOrder(User user, MerchProduct product, int quantity);

    MerchOrder createPendingOrderOnce(User user, MerchProduct product, int quantity, String checkoutKey,
                                     java.util.function.Supplier<SavedPaymentMethod> paymentMethod);

    MerchOrder createPendingOrderOnce(User user, MerchProduct product, int quantity, String checkoutKey,
                                     Long expectedUnitMinor, java.util.function.Supplier<SavedPaymentMethod> paymentMethod);

    class PriceChangedException extends IllegalArgumentException {
        public PriceChangedException() {
            super("The product price changed. Review the updated total before confirming again.");
        }
    }

    MerchOrder prepareHostedCheckout(Long orderId, String provider, String currency, String successUrl, String cancelUrl);

    MerchOrder markCheckoutSessionCreated(Long orderId,
                                          String paymentProvider,
                                          String paymentReference,
                                          SavedPaymentMethod paymentMethod);

    MerchOrder completePaidOrder(Long orderId, String paymentReference);

    MerchOrder cancelPendingPayment(Long orderId, String failureReason);

    /**
     * Called when a product is being deactivated:
     * cancels only PENDING orders containing that product.
     * CONFIRMED/SHIPPED/DELIVERED orders are left untouched.
     */
    void cancelPendingOrdersForProduct(Long productId);
}
