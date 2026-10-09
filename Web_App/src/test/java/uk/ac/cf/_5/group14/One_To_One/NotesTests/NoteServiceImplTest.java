package uk.ac.cf._5.group14.One_To_One.NotesTests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.ac.cf._5.group14.One_To_One.Notes.*;
import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private NoteFolderRepository folderRepository;

    @org.mockito.Spy
    private NoteSanitizer noteSanitizer = new NoteSanitizer();

    @InjectMocks
    private NoteServiceImpl service;

    @Test
    void createNote_requiresTitle() {
        User user = new User();
        user.setId(1L);

        assertThrows(IllegalArgumentException.class, () ->
                service.createNote(user, 1L, "   ", "content", false));
    }

    @Test
    void updateNote_updatesFields() {
        User user = new User();
        user.setId(1L);

        NoteFolder folder = new NoteFolder();
        folder.setId(2L);

        Note existing = new Note();
        existing.setId(5L);
        existing.setUser(user);
        existing.setFolder(folder);
        existing.setTitle("Old");
        existing.setContent("Old content");

        when(noteRepository.findByIdAndUser(eq(5L), eq(user))).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note updated = service.updateNote(user, 5L, "New title", "New content", true);

        assertEquals("New title", updated.getTitle());
        assertEquals("New content", updated.getContent());
        assertTrue(updated.isPublic());
    }

    @Test
    void richFormattingPersistsWithoutScriptsStylesOrUnsafeLinks() {
        String safe = noteSanitizer.sanitize("<p class='evil ql-align-center' style='text-align:center;color:red'>" +
                "<font size='4'><u>Formatted text</u></font><a href='javascript:alert(1)' onclick='bad()'>Link</a></p>");
        org.assertj.core.api.Assertions.assertThat(safe).contains("ql-align-center", "ql-size-large", "<u>Formatted text</u>")
                .doesNotContain("evil", "style=", "onclick=", "javascript:");
    }
}
