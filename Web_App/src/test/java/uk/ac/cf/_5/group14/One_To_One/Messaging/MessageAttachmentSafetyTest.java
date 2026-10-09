package uk.ac.cf._5.group14.One_To_One.Messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLink;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkRepository;
import uk.ac.cf._5.group14.One_To_One.TrainerClient.TrainerClientLinkStatus;
import uk.ac.cf._5.group14.One_To_One.Notifications.NotificationService;
import uk.ac.cf._5.group14.One_To_One.Users.UserRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageAttachmentSafetyTest {
    private MessagingService service;
    private ThreadMessageRepository messages;
    private OffPlatformPaymentAttemptRepository attempts;

    @BeforeEach
    void prepare() {
        var threads = mock(MessageThreadRepository.class);
        messages = mock(ThreadMessageRepository.class);
        var links = mock(TrainerClientLinkRepository.class);
        attempts = mock(OffPlatformPaymentAttemptRepository.class);
        var thread = new MessageThread(2L, 4L, 3L, MessageThreadStatus.OPEN);
        when(threads.findById(1L)).thenReturn(Optional.of(thread));
        when(links.findById(3L)).thenReturn(Optional.of(new TrainerClientLink(2L, 4L, TrainerClientLinkStatus.ACTIVE)));
        var users = mock(UserRepository.class);
        when(users.findByIdForUpdate(2L)).thenReturn(Optional.of(new uk.ac.cf._5.group14.One_To_One.Users.User()));
        service = new MessagingService(threads, messages, links, attempts, mock(NotificationService.class), users);
    }

    @Test
    void unsafeSchemesCredentialsAndOversizedLinksCannotBeSaved() {
        for (String url : new String[]{"javascript:alert(1)", "data:text/html,<script>", "https://user:secret@example.com", "https://example.com/" + "a".repeat(500)}) {
            assertThrows(IllegalArgumentException.class, () -> service.sendMessage(1L, 2L, MessageType.TEXT, "Exercise demo", "Demo", url, "link"));
        }
        verifyNoInteractions(messages);
    }

    @Test
    void aPaymentLinkCannotBypassTheMessagePolicy() {
        var error = assertThrows(MessagingException.class,
                () -> service.sendMessage(1L, 2L, MessageType.TEXT, "Here is the link", null, "https://paypal.me/example", "link"));
        assertThat(error.getReason()).isEqualTo(MessagingException.Reason.OFF_PLATFORM_PAYMENT);
        verify(attempts).save(any(OffPlatformPaymentAttempt.class));
        verifyNoInteractions(messages);
    }

    @Test
    void normalExerciseLinksRemainSupported() {
        service.sendMessage(1L, 2L, MessageType.TEXT, " Exercise demo ", "Demo", " https://example.com/exercise ", "link");
        verify(messages).save(argThat(message -> message.getAttachmentUrl().equals("https://example.com/exercise")
                && message.getBodyText().equals("Exercise demo")));
    }
}
