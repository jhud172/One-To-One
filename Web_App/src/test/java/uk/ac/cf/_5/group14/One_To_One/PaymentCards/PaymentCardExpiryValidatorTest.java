package uk.ac.cf._5.group14.One_To_One.PaymentCards;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;

class PaymentCardExpiryValidatorTest {
    private PaymentCardExpiryValidator at(String date) {
        return new PaymentCardExpiryValidator(Clock.fixed(Instant.parse(date), ZoneOffset.UTC));
    }

    @Test
    void currentMonthRemainsValidUntilMonthEndsThenExpires() {
        assertThatCode(() -> at("2026-10-31T23:59:59Z").validate((short)10, (short)2026)).doesNotThrowAnyException();
        assertThatThrownBy(() -> at("2026-11-01T00:00:00Z").validate((short)10, (short)2026))
                .isInstanceOf(PaymentCardExpiryValidator.ExpiredCardException.class);
        assertThatCode(() -> at("2026-12-31T23:59:59Z").validate((short)1, (short)2027)).doesNotThrowAnyException();
    }

    @Test
    void invalidCalendarValuesAndPastDatesAreRejected() {
        var validator = at("2026-10-04T12:00:00Z");
        for (short month : new short[]{0, 13}) {
            assertThatThrownBy(() -> validator.validate(month, (short)2026)).isInstanceOf(IllegalArgumentException.class);
        }
        for (short year : new short[]{2023, 2101}) {
            assertThatThrownBy(() -> validator.validate((short)10, year)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> validator.validate((short)9, (short)2026))
                .isInstanceOf(PaymentCardExpiryValidator.ExpiredCardException.class);
    }
}
