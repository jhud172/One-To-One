package uk.ac.cf._5.group14.One_To_One.Vault;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Binds a draft/insight to its saved reflection without a schema migration. */
public final class VaultRevision {
    private VaultRevision() { }

    public static String of(VaultNote note) {
        var value = new StringBuilder();
        Object[] fields = {note.getId(), note.getUserId(), note.getNoteType(), note.getTitle(),
                note.getContent(), note.getLinkedDate(), note.getLinkedWorkoutSessionId(), note.getTags(), note.getMood()};
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
