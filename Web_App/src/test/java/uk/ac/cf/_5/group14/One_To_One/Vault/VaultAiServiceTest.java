package uk.ac.cf._5.group14.One_To_One.Vault;

import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatResponse;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatService;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VaultAiServiceTest {
    private final ChatService chat = mock(ChatService.class);
    private final VaultAiService ai = new VaultAiService(chat);
    private VaultNote note() { return new VaultNote(1L, VaultNoteType.TRAINING, "Selected reflection", "A useful training day"); }

    @Test
    void disabledProviderDoesNotReceivePrivateNotes() {
        assertThatThrownBy(() -> ai.summariseWeek(List.of(note()))).isInstanceOf(IllegalStateException.class);
        verify(chat, never()).chat(anyList());
    }

    @Test
    void unsuccessfulOrEmptyProviderRepliesAreNeverInsights() {
        when(chat.isAvailable()).thenReturn(true);
        when(chat.chat(anyList())).thenReturn(new ChatResponse("Unavailable", false), new ChatResponse(" "));
        assertThatThrownBy(() -> ai.generateInsight(note())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ai.generateInsight(note())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void oversizedSelectionsAreRejectedBeforeProviderCalls() {
        when(chat.isAvailable()).thenReturn(true);
        assertThatThrownBy(() -> ai.summariseWeek(Collections.nCopies(21, note()))).isInstanceOf(IllegalArgumentException.class);
        var longNote = note(); longNote.setContent("x".repeat(20001));
        assertThatThrownBy(() -> ai.rewriteCheckin(List.of(longNote))).isInstanceOf(IllegalArgumentException.class);
        verify(chat, never()).chat(anyList());
    }
}
