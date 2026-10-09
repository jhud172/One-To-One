package uk.ac.cf._5.group14.One_To_One.WorkoutTests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatResponse;
import uk.ac.cf._5.group14.One_To_One.Chat.ChatService;
import uk.ac.cf._5.group14.One_To_One.Workout.WorkoutAiSuggestionService;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkoutAiSuggestionServiceTest {
    private final ChatService chat = mock(ChatService.class);
    private final WorkoutAiSuggestionService service = new WorkoutAiSuggestionService(chat, new ObjectMapper());

    @Test void unavailableProviderMakesNoRequestAndNeverInventsSuggestions() {
        when(chat.isAvailable()).thenReturn(false);
        assertThatThrownBy(() -> service.generateSuggestions("")).isInstanceOf(IllegalStateException.class);
        verify(chat, never()).chat(anyList());
    }
    @Test void malformedOrUnsuccessfulResponsesNeverBecomeCannedAiSuggestions() {
        when(chat.isAvailable()).thenReturn(true);
        when(chat.chat(anyList())).thenReturn(new ChatResponse("offline placeholder", false), new ChatResponse("not JSON"));
        for (int i = 0; i < 2; i++) assertThatThrownBy(() -> service.generateSuggestions("")).isInstanceOf(IllegalStateException.class);
    }
    @Test void genuineIdeasAreTrimmedAndDeduplicatedWithoutFabricatedPadding() {
        when(chat.isAvailable()).thenReturn(true);
        when(chat.chat(anyList())).thenReturn(new ChatResponse("[\"  Row  \",\"Row\",\"\",\"Controlled squat\"]"));
        assertThat(service.generateSuggestions("Legs")).containsExactly("Row", "Controlled squat");
    }
}
