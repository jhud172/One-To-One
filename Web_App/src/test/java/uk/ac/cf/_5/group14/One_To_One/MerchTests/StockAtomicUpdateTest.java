package uk.ac.cf._5.group14.One_To_One.MerchTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProductService;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrder;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderRepository;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderServiceImpl;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.PaymentStatus;
import uk.ac.cf._5.group14.One_To_One.MerchOrders.RefundStatus;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests that stock decrement is performed atomically via a single conditional
 * DB update (not read-then-write), preventing overselling under concurrency.
 */
@ExtendWith(MockitoExtension.class)
class StockAtomicUpdateTest {

    @Mock
    private MerchOrderRepository orderRepo;

    @Mock
    private MerchProductService productService;

    @Mock private jakarta.persistence.EntityManager entities;
    @Mock private uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchPaymentGateway gateway;

    @InjectMocks
    private MerchOrderServiceImpl orderService;

    private MerchProduct activeProduct(int stock) {
        MerchProduct p = new MerchProduct();
        p.setId(42L);
        p.setName("Test Product");
        p.setPrice(BigDecimal.valueOf(10.00));
        p.setStockQuantity(stock);
        p.setActive(true);
        return p;
    }

    private User testUser() {
        User u = new User();
        u.setId(1L);
        return u;
    }

    @Test
    void createPendingOrder_usesAtomicDecrement_notReadThenWrite() {
        MerchProduct product = activeProduct(5);
        when(productService.decrementStock(42L, 2)).thenReturn(true);
        when(orderRepo.save(any())).thenAnswer(inv -> {
            MerchOrder o = inv.getArgument(0);
            o.setId(99L);
            return o;
        });

        MerchOrder order = orderService.createPendingOrder(testUser(), product, 2);

        // Verify atomic decrement was called (not a direct field mutation)
        verify(productService).decrementStock(42L, 2);
        assertNotNull(order);
        assertEquals(RefundStatus.NONE, order.getRefundStatus());
        assertEquals(PaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
    }

    @Test
    void createPendingOrder_throwsWhenAtomicDecrementReturnsFalse() {
        MerchProduct product = activeProduct(1);
        when(productService.decrementStock(42L, 2)).thenReturn(false);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> orderService.createPendingOrder(testUser(), product, 2));

        assertTrue(ex.getMessage().toLowerCase().contains("stock"));
        verify(orderRepo, never()).save(any());
    }

    @Test
    void createPendingOrder_setsImageAndCategorySnapshot() {
        MerchProduct product = activeProduct(5);
        product.setImageUrl("/uploads/merch/shirt.jpg");
        product.setCategory("Apparel");
        when(productService.decrementStock(42L, 1)).thenReturn(true);
        when(orderRepo.save(any())).thenAnswer(inv -> {
            MerchOrder o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        MerchOrder order = orderService.createPendingOrder(testUser(), product, 1);

        assertFalse(order.getItems().isEmpty());
        assertEquals("/uploads/merch/shirt.jpg", order.getItems().get(0).getImageUrlSnapshot());
        assertEquals("Apparel", order.getItems().get(0).getCategorySnapshot());
    }

    @Test
    void cancelPendingPayment_restoresReservedStock() {
        MerchOrder order = new MerchOrder();
        order.setId(55L);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.getItems().add(buildItem(activeProduct(5), order, 2));

        when(orderRepo.findLockedById(55L)).thenReturn(java.util.Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchOrder cancelled = orderService.cancelPendingPayment(55L, "Checkout cancelled.");

        verify(productService).incrementStock(42L, 2);
        assertEquals(PaymentStatus.FAILED, cancelled.getPaymentStatus());
    }

    @Test
    void completePaidOrder_marksOrderAsPaid() {
        MerchOrder order = new MerchOrder();
        order.setId(71L);
        order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.setPaymentReference("cs_test_123");

        when(orderRepo.findLockedById(71L)).thenReturn(java.util.Optional.of(order));
        when(orderRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MerchOrder completed = orderService.completePaidOrder(71L, "cs_test_123");

        assertEquals(PaymentStatus.PAID, completed.getPaymentStatus());
        assertNotNull(completed.getPaymentConfirmedAt());
    }

    @Test
    void openOrUnverifiedHostedPaymentCannotReleaseReservedStock() {
        var order = new MerchOrder(); order.setId(55L); order.setPaymentStatus(PaymentStatus.PENDING_PAYMENT);
        order.setPaymentProvider("Stripe"); order.setPaymentReference("cs_test_open");
        order.getItems().add(buildItem(activeProduct(5), order, 2));
        when(orderRepo.findLockedById(55L)).thenReturn(java.util.Optional.of(order));
        assertThrows(IllegalStateException.class, () -> orderService.cancelPendingPayment(55L, "Cancel"));
        verify(productService, never()).incrementStock(anyLong(), anyInt());
        verify(orderRepo, never()).save(any());
        assertEquals(PaymentStatus.PENDING_PAYMENT, order.getPaymentStatus());
    }

    private uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItem buildItem(MerchProduct product, MerchOrder order, int quantity) {
        uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItem item = new uk.ac.cf._5.group14.One_To_One.MerchOrders.MerchOrderItem();
        item.setProduct(product);
        item.setOrder(order);
        item.setQuantity(quantity);
        return item;
    }
}
