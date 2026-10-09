package uk.ac.cf._5.group14.One_To_One.Membership;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.ac.cf._5.group14.One_To_One.Users.User;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email", name = "provider", havingValue = "smtp")
public class SmtpEmailService implements EmailService {

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;

    @Value("${app.email.from:${spring.mail.username:no-reply@healthyhabits.local}}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${app.email.fail-on-error:true}")
    private boolean failOnError;

    @Override
    public void sendPriceChangeNotification(
        User user,
        String productName,
        double oldPriceDollars,
        double newPriceDollars,
        Instant effectiveAt,
        String reason,
        String manageUrl
    ) {
        String subject = "Price Change Notice for " + productName;
        String body = "Hello " + user.getFirstName() + " " + user.getLastName() + ",\n\n"
            + "We are writing to inform you of a price change for your " + productName + " membership.\n\n"
            + "Current Price: $" + String.format("%.2f", oldPriceDollars) + "\n"
            + "New Price: $" + String.format("%.2f", newPriceDollars) + "\n"
            + "Effective Date: " + DATE_FORMATTER.format(effectiveAt) + "\n\n"
            + "Reason: " + reason + "\n\n"
            + "Manage or cancel your membership: " + manageUrl + "\n\n"
            + "Thank you.";
        sendEmail(user.getEmail(), subject, body);
    }

    @Override
    public void sendTrainerVerificationUpdate(User user, String status, String adminNotes) {
        String subject = "Trainer Verification Status Update";
        StringBuilder body = new StringBuilder();
        body.append("Hello ").append(user.getFirstName()).append(" ").append(user.getLastName()).append(",\n\n");
        body.append("Your trainer verification request status is now: ").append(status).append(".\n");
        if (adminNotes != null && !adminNotes.isBlank()) {
            body.append("\nAdmin Notes: ").append(adminNotes).append("\n");
        }
        body.append("\nThank you.");
        sendEmail(user.getEmail(), subject, body.toString());
    }

    @Override
    public void sendPasswordReset(User user, String resetUrl, Instant expiresAt) {
        String subject = "Reset your password";
        String body = "Hello " + user.getFirstName() + " " + user.getLastName() + ",\n\n"
            + "We received a request to reset your password.\n"
            + "Use the link below to set a new password (valid until " + DATE_FORMATTER.format(expiresAt) + "): \n"
            + resetUrl + "\n\n"
            + "If you did not request this, you can ignore this email.";
        sendEmail(user.getEmail(), subject, body);
    }

    @Async
    @Override
    public void sendEmailVerification(User user, String verifyUrl, String code, Instant expiresAt) {
        String subject = "Verify your email";
        String body = "Hello " + user.getFirstName() + " " + user.getLastName() + ",\n\n"
            + "Please verify your email address.\n\n"
            + "Your verification code is: " + code + "\n\n"
            + "Enter this code on the verification page, or click the link below:\n"
            + verifyUrl + "\n\n"
            + "This code is valid until " + DATE_FORMATTER.format(expiresAt) + ".\n\n"
            + "If you did not request this, you can ignore this email.";
        sendEmail(user.getEmail(), subject, body);
    }

    @Override
    public void sendAdminMessage(String to, String subject, String body) {
        sendEmail(to, subject, body, true);
    }

    private void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, failOnError);
    }

    private void sendEmail(String to, String subject, String body, boolean requireAcceptance) {
        try {
            if (smtpUsername == null || smtpUsername.isBlank() || smtpPassword == null || smtpPassword.isBlank()) {
                throw new IllegalStateException(
                    "SMTP credentials are missing. Set SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD."
                );
            }

            String from = (fromAddress == null || fromAddress.isBlank())
                ? "no-reply@healthyhabits.local"
                : fromAddress;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setFrom(from);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}", to, e);
            if (requireAcceptance) {
                throw new IllegalStateException("SMTP email delivery failed for " + to, e);
            }
        }
    }
}
