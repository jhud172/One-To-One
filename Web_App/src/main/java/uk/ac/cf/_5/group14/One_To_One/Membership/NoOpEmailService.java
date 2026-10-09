package uk.ac.cf._5.group14.One_To_One.Membership;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import uk.ac.cf._5.group14.One_To_One.Users.User;

/**
 * Fallback no-op email service used when no SMTP mail host is configured.
 * All methods log a warning but do not send any actual emails.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(value = EmailService.class, ignored = NoOpEmailService.class)
public class NoOpEmailService implements EmailService {

    @Override
    public void sendPriceChangeNotification(
            User user,
            String productName,
            double oldPriceDollars,
            double newPriceDollars,
            Instant effectiveAt,
            String reason,
            String manageUrl) {
        log.warn("No email provider configured - skipping price change notification to {}", user.getEmail());
    }

    @Override
    public void sendTrainerVerificationUpdate(User user, String status, String adminNotes) {
        log.warn("No email provider configured - skipping trainer verification update to {}", user.getEmail());
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl, Instant expiresAt) {
        log.warn("No email provider configured - skipping password reset email to {}", user.getEmail());
    }

    @Override
    public void sendEmailVerification(User user, String verifyUrl, String code, Instant expiresAt) {
        log.warn("No email provider configured - skipping email verification to {}", user.getEmail());
    }

    @Override
    public void sendAdminMessage(String to, String subject, String body) {
        throw new IllegalStateException("No email provider is configured for outbound messages.");
    }
}
