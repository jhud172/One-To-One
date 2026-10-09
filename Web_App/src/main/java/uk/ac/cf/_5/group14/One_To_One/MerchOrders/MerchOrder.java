package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.cf._5.group14.One_To_One.PaymentCards.SavedPaymentMethod;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "merch_orders", uniqueConstraints = @UniqueConstraint(
        name = "uq_merch_order_user_checkout", columnNames = {"user_id", "checkout_key"}))
@Getter
@Setter
public class MerchOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "checkout_key", length = 36)
    private String checkoutKey;

    @Column(name = "checkout_currency", length = 3)
    private String checkoutCurrency;

    @Column(name = "checkout_success_url", length = 2048)
    private String checkoutSuccessUrl;

    @Column(name = "checkout_cancel_url", length = 2048)
    private String checkoutCancelUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status = OrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING_PAYMENT;

    @Column(name = "payment_provider", length = 50)
    private String paymentProvider;

    @Column(name = "payment_reference", length = 200)
    private String paymentReference;

    @Column(name = "payment_failure_reason", length = 500)
    private String paymentFailureReason;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_status", nullable = false, length = 20)
    private RefundStatus refundStatus = RefundStatus.NONE;

    @Column(name = "refund_reference", length = 200)
    private String refundReference;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private SavedPaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_status", nullable = false, length = 30)
    private ShippingStatus shippingStatus = ShippingStatus.PENDING;

    @Column(name = "tracking_number", length = 200)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MerchOrderItem> items = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = Instant.now();
    }
}

