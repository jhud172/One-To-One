package uk.ac.cf._5.group14.One_To_One.PaymentCards;

import org.springframework.stereotype.Service;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Service
public class SimulatedPaymentCardResolver {

    private final SavedPaymentMethodService savedPaymentMethodService;
    private final PaymentCardExpiryValidator expiry;

    public SimulatedPaymentCardResolver(SavedPaymentMethodService savedPaymentMethodService, PaymentCardExpiryValidator expiry) {
        this.savedPaymentMethodService = savedPaymentMethodService;
        this.expiry = expiry;
    }

    public SimulatedPaymentCardSelection resolve(User user,
                                                 Long selectedCardId,
                                                 String newCardHolderName,
                                                 String newProviderToken,
                                                 String newLastFour,
                                                 String newBrand,
                                                 Short newExpiryMonth,
                                                 Short newExpiryYear,
                                                 boolean saveCard) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("You must be signed in to choose a payment method.");
        }

        if (selectedCardId != null) {
            SavedPaymentMethod saved = savedPaymentMethodService.findByIdForUser(selectedCardId, user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Saved card not found."));
            expiry.validate(saved.getExpiryMonth(), saved.getExpiryYear());
            return new SimulatedPaymentCardSelection(
                    saved,
                    saved.getCardHolderName(),
                    saved.getBrand(),
                    saved.getLastFour());
        }

        String holderName = requireText(newCardHolderName, "Cardholder name is required.");
        String providerToken = requireText(newProviderToken, "A simulated card token is required.");
        String brand = requireText(newBrand, "Card brand is required.");
        String lastFour = normalizeLastFour(newLastFour);
        short expiryMonth = normalizeMonth(newExpiryMonth);
        short expiryYear = normalizeYear(newExpiryYear);
        expiry.validate(expiryMonth, expiryYear);

        if (saveCard) {
            SavedPaymentMethod saved = savedPaymentMethodService.addCard(
                    user,
                    holderName,
                    providerToken,
                    lastFour,
                    brand,
                    expiryMonth,
                    expiryYear,
                    false);
            return new SimulatedPaymentCardSelection(
                    saved,
                    saved.getCardHolderName(),
                    saved.getBrand(),
                    saved.getLastFour());
        }

        return new SimulatedPaymentCardSelection(null, holderName, brand, lastFour);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String normalizeLastFour(String value) {
        String digits = value == null ? "" : value.replaceAll("\\D", "");
        if (digits.length() != 4) {
            throw new IllegalArgumentException("Last four digits are required.");
        }
        return digits;
    }

    private short normalizeMonth(Short value) {
        if (value == null || value < 1 || value > 12) {
            throw new IllegalArgumentException("A valid expiry month is required.");
        }
        return value;
    }

    private short normalizeYear(Short value) {
        if (value == null || value < 2024 || value > 2100) {
            throw new IllegalArgumentException("A valid expiry year is required.");
        }
        return value;
    }
}
