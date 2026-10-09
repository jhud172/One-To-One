package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

public class TrainerScheduleMetadataConflictException extends RuntimeException {
    public TrainerScheduleMetadataConflictException() {
        super("The saved schedule metadata changed after this editor was opened.");
    }
}
