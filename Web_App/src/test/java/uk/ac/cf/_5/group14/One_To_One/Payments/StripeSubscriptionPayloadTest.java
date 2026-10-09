package uk.ac.cf._5.group14.One_To_One.Payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;

class StripeSubscriptionPayloadTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{\"current_period_end\":1900000000}|1900000000",
        "{\"items\":{\"data\":[{\"current_period_end\":1900000000}]}}|1900000000",
        "{\"items\":{\"data\":[{\"current_period_end\":2000000000},{\"current_period_end\":1900000000}]}}|1900000000",
        "{}|0"
    })
    void handlesLegacyAndCurrentProviderPeriods(String json, long expected) throws Exception {
        assertThat(StripeSubscriptionPayload.periodEnd(new ObjectMapper().readTree(json)))
                .isEqualTo(expected == 0 ? null : Instant.ofEpochSecond(expected));
    }
}
