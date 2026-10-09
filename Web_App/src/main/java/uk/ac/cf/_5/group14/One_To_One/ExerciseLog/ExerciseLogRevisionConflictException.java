package uk.ac.cf._5.group14.One_To_One.ExerciseLog;

public class ExerciseLogRevisionConflictException extends RuntimeException {
    public ExerciseLogRevisionConflictException() {
        super("The saved reflection changed after this editor was opened.");
    }
}
