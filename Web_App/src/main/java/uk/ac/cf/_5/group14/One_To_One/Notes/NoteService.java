package uk.ac.cf._5.group14.One_To_One.Notes;

import uk.ac.cf._5.group14.One_To_One.Users.User;

import java.util.List;

public interface NoteService {

    Note create(User user, Long folderId, String title, String content, String noteColour);

    Note update(User user, Long noteId, String title, String content, Long newFolderId, String noteColour);

    Note updateChecked(User user, Long noteId, String title, String content, Long newFolderId, String noteColour, String revision);

    void delete(User user, Long noteId);

    Note getNoteForUser(User user, Long noteId);

    List<Note> getNotesForFolder(User user, Long folderId, String query);

    List<Note> search(User user, Long folderId, String query);

    org.springframework.data.domain.Page<Note> searchPage(User user, Long folderId, String query, int page);
}
