package uk.ac.cf._5.group14.One_To_One.PaymentCards;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.YearMonth;

@Component
public class PaymentCardExpiryValidator {
    private final Clock clock;

    public PaymentCardExpiryValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(short month, short year) {
        if (month < 1 || month > 12 || year < 2024 || year > 2100) {
            throw new IllegalArgumentException("A valid card expiry month and year are required.");
        }
        // A card remains valid through the last day of its expiry month.
        if (YearMonth.of(year, month).isBefore(YearMonth.now(clock))) {
            throw new ExpiredCardException();
        }
    }

    public static class ExpiredCardException extends IllegalArgumentException {
        public ExpiredCardException() {
            super("This demo card has expired. Choose another card or update its expiry.");
        }
    }
}
