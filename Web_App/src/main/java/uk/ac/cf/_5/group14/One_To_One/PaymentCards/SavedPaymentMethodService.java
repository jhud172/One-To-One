package uk.ac.cf._5.group14.One_To_One.PaymentCards;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodService {

    List<SavedPaymentMethod> getCardsForUser(Long userId);

    Optional<SavedPaymentMethod> findByIdForUser(Long id, Long userId);

    /**
     * Resolves the provider token for a user-owned payment method.
     * The decrypted value must be passed only to the configured payment
     * provider and must never be rendered or logged.
     */
    String resolveProviderTokenForUser(Long id, Long userId);

    /**
     * Adds a new card for the user.
     * The raw PAN must never be passed here; callers should instead obtain a
     * provider-issued token (e.g. from Stripe/Adyen hosted fields) client-side and
     * pass only that opaque token alongside display metadata.
     *
     * @param user            owner
     * @param cardHolderName  name on card
     * @param providerToken   opaque token from the payment provider – never the raw PAN
     * @param lastFour        last 4 digits of the card (for display only)
     * @param brand           Visa / Mastercard / Amex etc.
     * @param expiryMonth     1-12
     * @param expiryYear      4-digit year
     * @param makeDefault     whether to mark this card as default
     * @return the persisted entity
     */
    SavedPaymentMethod addCard(User user,
                               String cardHolderName,
                               String providerToken,
                               String lastFour,
                               String brand,
                               short expiryMonth,
                               short expiryYear,
                               boolean makeDefault);

    /**
     * Updates an existing card's non-sensitive fields.
     */
    SavedPaymentMethod updateCard(User user,
                                  Long cardId,
                                  String cardHolderName,
                                  String brand,
                                  short expiryMonth,
                                  short expiryYear,
                                  boolean makeDefault);

    void deleteCard(User user, Long cardId);

    void setDefault(User user, Long cardId);
}
