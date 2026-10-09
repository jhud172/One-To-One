package uk.ac.cf._5.group14.One_To_One.Membership;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

/** Editable fields only; ownership and persisted pricing come from the server. */
@Data
public class MembershipProductForm {
    @NotBlank @Size(max = 200)
    private String name;
    @Size(max = 1000)
    private String description;
    @DecimalMin("0.00") @DecimalMax("21474836.47") @Digits(integer = 8, fraction = 2)
    private BigDecimal priceDollars;
    @NotNull
    private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
    private boolean active = true;

    public static MembershipProductForm from(GymMembershipProduct product) {
        MembershipProductForm form = new MembershipProductForm();
        form.setName(product.getName());
        form.setDescription(product.getDescription());
        form.setPriceDollars(BigDecimal.valueOf(product.getPriceCents(), 2));
        form.setBillingPeriod(product.getBillingPeriod());
        form.setActive(product.isActive());
        return form;
    }

    public int toPriceCents() { return priceDollars.movePointRight(2).intValueExact(); }
}
