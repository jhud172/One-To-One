package uk.ac.cf._5.group14.One_To_One.Notes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Binds an edit to the saved contents without changing the existing database schema. */
public final class NoteRevision {
    private NoteRevision() { }

    public static String of(Note note) {
        var value = new StringBuilder();
        Object[] fields = {note.getId(), note.getUser().getId(), note.getFolder().getId(),
                note.getTitle(), note.getContent(), note.getColour(), note.getPinned(), note.isPublic()};
        for (Object field : fields) {
            String part = field == null ? null : field.toString();
            value.append(part == null ? -1 : part.length()).append(':');
            if (part != null) value.append(part);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
