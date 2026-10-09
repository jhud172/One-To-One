package uk.ac.cf._5.group14.One_To_One.Support;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import uk.ac.cf._5.group14.One_To_One.Membership.NoOpEmailService;
import uk.ac.cf._5.group14.One_To_One.Membership.SmtpEmailService;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OutboundAcceptanceTest {
    @Test
    void missingProviderCannotClaimMessageAcceptance() {
        assertThatThrownBy(() -> new NoOpEmailService().sendAdminMessage("fixture@example.com", "Fixture", "Body"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void adminMessagePropagatesSmtpRejectionEvenWhenOtherNotificationsIgnoreFailures() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new IllegalStateException("Fixture rejection")).when(sender).send(any(SimpleMailMessage.class));
        SmtpEmailService service = new SmtpEmailService(sender);
        ReflectionTestUtils.setField(service, "smtpUsername", "fixture-only");
        ReflectionTestUtils.setField(service, "smtpPassword", "fixture-only");
        ReflectionTestUtils.setField(service, "failOnError", false);
        assertThatThrownBy(() -> service.sendAdminMessage("fixture@example.com", "Fixture", "Body"))
            .isInstanceOf(IllegalStateException.class);
    }
}
