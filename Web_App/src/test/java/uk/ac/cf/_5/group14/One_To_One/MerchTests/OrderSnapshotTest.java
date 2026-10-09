package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProductService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrder;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItem;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderRepository;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderServiceImpl;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.PaymentStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that order items capture a full snapshot of product attributes at
 * the time of purchase so that deactivating/deleting the product later does
 * not corrupt historical order rendering.
 */
@ExtendWith(MockitoExtension.class)
class OrderSnapshotTest {

    @Mock
    private MerchOrderRepository orderRepo;

    @Mock
    private MerchProductService productService;

    @Mock private jakarta.persistence.EntityManager entities;
    @Mock private uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchPaymentGateway gateway;

    @InjectMocks
    private MerchOrderServiceImpl orderService;

    @Test
    void createPendingOrder_snapshotsAllProductFields() {
        MerchProduct product = new MerchProduct();
        product.setId(1L);
        product.setName("Limited Edition Shirt");
        product.setPrice(BigDecimal.valueOf(29.99));
        product.setImageUrl("/uploads/merch/shirt.jpg");
        product.setCategory("Apparel");
        product.setStockQuantity(10);
        product.setActive(true);

        User user = new User();
        user.setId(5L);

        when(productService.decrementStock(1L, 1)).thenReturn(true);
        when(orderRepo.save(any())).thenAnswer(inv -> {
            MerchOrder o = inv.getArgument(0);
            o.setId(7L);
            return o;
        });

        MerchOrder order = orderService.createPendingOrder(user, product, 1);

        // Deactivate product to simulate soft-delete
        product.setActive(false);
        product.setName("(removed)");
        product.setImageUrl(null);
        product.setCategory(null);

        // Order item snapshot must still hold the original values
        assertFalse(order.getItems().isEmpty());
        MerchOrderItem item = order.getItems().get(0);
        assertEquals("Limited Edition Shirt", item.getProductNameSnapshot(),
                "Name snapshot must be preserved after product deactivation");
        assertEquals(0, BigDecimal.valueOf(29.99).compareTo(item.getPriceSnapshot()),
                "Price snapshot must be preserved after product deactivation");
        assertEquals("/uploads/merch/shirt.jpg", item.getImageUrlSnapshot(),
                "Image URL snapshot must be preserved after product deactivation");
        assertEquals("Apparel", item.getCategorySnapshot(),
                "Category snapshot must be preserved after product deactivation");
        assertEquals(PaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
    }

    @Test
    void deleteProduct_onlyCancelsPendingOrders() {
        // This is tested via MerchOrderService.cancelPendingOrdersForProduct
        // which queries only PENDING orders via findPendingOrdersContainingProduct.
        // Here we verify the service delegates to the correct method.
        when(orderRepo.findPendingOrdersContainingProduct(1L)).thenReturn(java.util.List.of());

        orderService.cancelPendingOrdersForProduct(1L);

        verify(orderRepo).findPendingOrdersContainingProduct(1L);
        // Ensure findActiveOrdersContainingProduct (old broad method) is not called
        verifyNoMoreInteractions(orderRepo);
    }
}
