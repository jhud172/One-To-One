package uk.ac.cf._5.group14.One_To_One.PaymentCards;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
@Transactional
public class SavedPaymentMethodServiceImpl implements SavedPaymentMethodService {

    private static final int MAX_CARDS_PER_USER = 10;
    private static final Logger log = Logger.getLogger(SavedPaymentMethodServiceImpl.class.getName());

    private final SavedPaymentMethodRepository repo;
    private final CardEncryptionService cardEncryptionService;
    private final PaymentCardExpiryValidator expiry;

    public SavedPaymentMethodServiceImpl(
            SavedPaymentMethodRepository repo,
            CardEncryptionService cardEncryptionService, PaymentCardExpiryValidator expiry) {
        this.repo = repo;
        this.cardEncryptionService = cardEncryptionService;
        this.expiry = expiry;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SavedPaymentMethod> getCardsForUser(Long userId) {
        return repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SavedPaymentMethod> findByIdForUser(Long id, Long userId) {
        return repo.findByIdAndUserId(id, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveProviderTokenForUser(Long id, Long userId) {
        SavedPaymentMethod card = repo.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccessDeniedException("Card not found"));
        return cardEncryptionService.decrypt(card.getProviderPaymentMethodId());
    }

    @Override
    public SavedPaymentMethod addCard(User user,
                                      String cardHolderName,
                                      String providerToken,
                                      String lastFour,
                                      String brand,
                                      short expiryMonth,
                                      short expiryYear,
                                      boolean makeDefault) {
        if (repo.countByUserId(user.getId()) >= MAX_CARDS_PER_USER) {
            throw new IllegalStateException("Maximum of " + MAX_CARDS_PER_USER + " cards allowed per account.");
        }
        if (providerToken == null || providerToken.isBlank()) {
            throw new IllegalArgumentException("A valid payment provider token is required.");
        }
        if (lastFour == null || !lastFour.matches("\\d{4}")) {
            throw new IllegalArgumentException("Last four digits are required.");
        }
        expiry.validate(expiryMonth, expiryYear);

        boolean isFirstCard = repo.countByUserId(user.getId()) == 0;
        boolean shouldBeDefault = makeDefault || isFirstCard;

        if (shouldBeDefault) {
            repo.clearDefaultForUser(user.getId());
        }

        SavedPaymentMethod card = new SavedPaymentMethod();
        card.setUser(user);
        card.setCardHolderName(cardHolderName.trim());
        card.setLastFour(lastFour);
        card.setBrand(brand.trim());
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);
        card.setProviderPaymentMethodId(cardEncryptionService.encrypt(providerToken.trim()));
        card.setDefault(shouldBeDefault);

        log.info(() -> "Payment method added for user=" + user.getId() + " brand=" + brand + " last4=" + lastFour);
        return repo.save(card);
    }

    @Override
    public SavedPaymentMethod updateCard(User user,
                                         Long cardId,
                                         String cardHolderName,
                                         String brand,
                                         short expiryMonth,
                                         short expiryYear,
                                         boolean makeDefault) {
        SavedPaymentMethod card = repo.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Card not found"));

        expiry.validate(expiryMonth, expiryYear);
        card.setCardHolderName(cardHolderName.trim());
        card.setBrand(brand.trim());
        card.setExpiryMonth(expiryMonth);
        card.setExpiryYear(expiryYear);

        if (makeDefault) {
            repo.clearDefaultForUser(user.getId());
            card.setDefault(true);
        }

        return repo.save(card);
    }

    @Override
    public void deleteCard(User user, Long cardId) {
        SavedPaymentMethod card = repo.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Card not found"));
        boolean wasDefault = card.isDefault();
        repo.delete(card);

        // If the deleted card was default, promote the oldest remaining card
        if (wasDefault) {
            List<SavedPaymentMethod> remaining =
                    repo.findByUserIdOrderByIsDefaultDescCreatedAtDesc(user.getId());
            if (!remaining.isEmpty()) {
                repo.clearDefaultForUser(user.getId());
                SavedPaymentMethod newDefault = remaining.get(0);
                newDefault.setDefault(true);
                repo.save(newDefault);
            }
        }
    }

    @Override
    public void setDefault(User user, Long cardId) {
        SavedPaymentMethod card = repo.findByIdAndUserId(cardId, user.getId())
                .orElseThrow(() -> new AccessDeniedException("Card not found"));
        repo.clearDefaultForUser(user.getId());
        card.setDefault(true);
        repo.save(card);
    }
}
