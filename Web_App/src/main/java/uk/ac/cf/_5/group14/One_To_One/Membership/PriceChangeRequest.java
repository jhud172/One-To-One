package uk.ac.cf._5.group14.One_To_One.Membership;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PriceChangeRequest {

    @NotNull(message = "New price is required")
    @DecimalMin(value = "0.00", inclusive = true, message = "Price must be zero or greater")
    @DecimalMax(value = "21474836.47", message = "Price is too large")
    @Digits(integer = 8, fraction = 2, message = "Enter a price with at most two decimal places")
    private BigDecimal newPriceDollars;
    
    @NotBlank(message = "Reason for price change is required")
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;

    @NotNull(message = "Effective date is required")
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate effectiveDate;

    public Integer toNewPriceCents() {
        if (newPriceDollars == null) {
            return null;
        }
        return newPriceDollars.movePointRight(2).intValueExact();
    }
}
