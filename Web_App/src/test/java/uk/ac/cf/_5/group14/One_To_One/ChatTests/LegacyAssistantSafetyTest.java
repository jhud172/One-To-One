package uk.ac.cf._5.group14.One_To_One.ChatTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import uk.ac.cf._5.group14.One_To_One.Chat.*;
import uk.ac.cf._5.group14.One_To_One.ChatV2.*;
import uk.ac.cf._5.group14.One_To_One.Notes.NoteRepository;
import uk.ac.cf._5.group14.One_To_One.PlatformBilling.PlatformSubscriptionService;
import uk.ac.cf._5.group14.One_To_One.Security.AccessGuard;
import uk.ac.cf._5.group14.One_To_One.Users.*;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacyAssistantSafetyTest {
    @Mock AuthHelper authHelper;
    @Mock ChatV2ThreadService threadService;
    @Mock ChatV2ActionExecutor actionExecutor;
    @Mock ChatV2AiResponseParser aiResponseParser;
    @Mock ChatService chatService;
    @Mock ChatContextService chatContextService;
    @Mock NoteRepository noteRepository;
    @Mock AccessGuard accessGuard;
    @Mock UserService userService;
    @Mock PlatformSubscriptionService subscriptions;
    @Mock Clock clock;
    @InjectMocks ChatV2Controller controller;

    private User ownedThread() {
        User owner = new User(); owner.setId(1L);
        ChatThread thread = new ChatThread(); thread.setId(2L); thread.setUser(owner); thread.setTitle("Earlier chat");
        when(authHelper.getAuthenticatedUser()).thenReturn(owner);
        when(threadService.findThread(owner, 2L)).thenReturn(Optional.of(thread));
        return owner;
    }

    @Test
    void aLegacyEndpointCannotBypassPremiumOrExecuteAnAction() {
        ownedThread();
        assertThatThrownBy(() -> controller.message(2L, Map.of("message", "Plan a workout")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(() -> controller.action(2L, new ChatV2ActionRequest("TASK_CREATE", Map.of("title", "Unpaid task"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(403));
        verifyNoInteractions(chatService, actionExecutor, chatContextService);
        verify(threadService, never()).appendMessage(any(), any(), any());
    }

    @Test
    void aDisabledProviderReturnsBeforeSavingOrLoadingPrivateContext() {
        ownedThread(); when(subscriptions.isPremium(1L, clock)).thenReturn(true);
        assertThat(controller.message(2L, Map.of("message", "Plan a workout")).getStatusCode().value()).isEqualTo(503);
        verifyNoInteractions(chatContextService, actionExecutor);
        verify(threadService, never()).appendMessage(any(), any(), any());
        verify(chatService, never()).chat(anyList());
    }

    @Test
    void generatedActionsRemainProposalsUntilAnExplicitActionRequest() {
        User owner = ownedThread(); when(subscriptions.isPremium(1L, clock)).thenReturn(true);
        when(chatService.isAvailable()).thenReturn(true);
        when(chatContextService.build(owner, null)).thenReturn(new ChatContext(LocalDate.now(), null,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, null));
        when(threadService.listMessages(any())).thenReturn(List.of());
        when(chatService.chat(anyList())).thenReturn(new ChatResponse("mock structured reply"));
        when(aiResponseParser.tryParse("mock structured reply")).thenReturn(new ChatV2Response("Suggested plan", List.of(),
                List.of(new ChatV2ActionResult("TASK_CREATE", Map.of("title", "Proposed workout"), "OK", "Generated"))));
        var response = controller.message(2L, Map.of("message", "Suggest a workout"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().actions().getFirst().status()).isEqualTo("PENDING");
        verifyNoInteractions(actionExecutor);
    }

    @Test
    void folderAndThreadMetadataAreValidatedBeforeMutation() {
        var folders = mock(ChatFolderRepository.class);
        var threads = mock(ChatThreadRepository.class);
        var messages = mock(ChatMessageRepository.class);
        var service = new ChatV2ThreadService(folders, threads, messages);
        User owner = new User(); owner.setId(1L);
        ChatFolder folder = new ChatFolder(); folder.setUser(owner); folder.setName("Original");
        folder.setColorHex("#0f172a"); folder.setIconKey("chat");
        assertThatThrownBy(() -> service.updateFolder(folder, "Updated", "red;bad", "chat"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(folder.getName()).isEqualTo("Original");
        ChatThread thread = new ChatThread(); thread.setUser(owner); thread.setTitle("Original title");
        assertThatThrownBy(() -> service.updateThreadSettings(thread, "Updated", "#ffffff", "chat", true, false, "x".repeat(10001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(thread.getTitle()).isEqualTo("Original title"); assertThat(thread.isPinned()).isFalse();
        User other = new User(); other.setId(3L); folder.setUser(other);
        assertThatThrownBy(() -> service.moveThread(thread, folder)).isInstanceOf(IllegalArgumentException.class);
        assertThat(thread.getFolder()).isNull(); verifyNoInteractions(folders, threads, messages, chatService);
    }
}
