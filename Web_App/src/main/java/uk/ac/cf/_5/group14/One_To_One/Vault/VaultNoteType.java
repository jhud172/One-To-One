package uk.ac.cf._5.group14.One_To_One.Vault;

public enum VaultNoteType {
    TRAINING,
    NUTRITION,
    INJURY,
    GOAL,
    REFLECTION,
    CHECKIN;

    public String getLabelKey() {
        return switch (this) {
            case TRAINING -> "ui.00306";
            case NUTRITION -> "ui.02198";
            case INJURY -> "ui.vault.type.INJURY";
            case GOAL -> "ui.00595";
            case REFLECTION -> "ui.00576";
            case CHECKIN -> "ui.inbox.checkin";
        };
    }
}
