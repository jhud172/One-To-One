package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

public class TrainerLibraryRevisionConflictException extends RuntimeException {
    public TrainerLibraryRevisionConflictException() {
        super("The saved library metadata changed after this editor was opened.");
    }
}
