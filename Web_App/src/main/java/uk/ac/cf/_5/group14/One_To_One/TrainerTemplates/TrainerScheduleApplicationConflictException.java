package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

public class TrainerScheduleApplicationConflictException extends RuntimeException {
    public TrainerScheduleApplicationConflictException() {
        super("The schedule or application selection changed after preview.");
    }
}
