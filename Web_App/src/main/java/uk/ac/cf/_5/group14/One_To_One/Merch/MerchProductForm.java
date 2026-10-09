package uk.ac.cf._5.group14.One_To_One.Merch;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class MerchProductForm {
    @NotBlank @Size(max = 200) private String name;
    @Size(max = 2000) private String description;
    @NotNull @DecimalMin("0.01") @DecimalMax("99999999.99") @Digits(integer = 8, fraction = 2) private BigDecimal price;
    @Size(max = 100) private String category;
    @NotNull @Min(0) private Integer stockQuantity = 0;
    @Min(0) private Integer originalStock;
    private boolean active = true;

    public static MerchProductForm from(MerchProduct product) {
        MerchProductForm form = new MerchProductForm();
        form.setName(product.getName()); form.setDescription(product.getDescription()); form.setPrice(product.getPrice());
        form.setCategory(product.getCategory()); form.setStockQuantity(product.getStockQuantity()); form.setOriginalStock(product.getStockQuantity()); form.setActive(product.isActive());
        return form;
    }
}
