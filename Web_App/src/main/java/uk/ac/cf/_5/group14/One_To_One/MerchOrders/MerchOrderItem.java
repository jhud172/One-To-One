package uk.ac.cf._5.group14.One_To_One.MerchOrders;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import uk.ac.cf._5.group14.One_To_One.Merch.MerchProduct;

import java.math.BigDecimal;

@Entity
@Table(name = "merch_order_items")
@Getter
@Setter
public class MerchOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private MerchOrder order;

    /** May be null if product was deleted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private MerchProduct product;

    @Column(name = "product_name_snapshot", nullable = false, length = 200)
    private String productNameSnapshot;

    @Column(name = "price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "image_url_snapshot", length = 500)
    private String imageUrlSnapshot;

    @Column(name = "category_snapshot", length = 100)
    private String categorySnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;
    public BigDecimal getLineTotal() {
        return priceSnapshot == null ? null : priceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }
}
