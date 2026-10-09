package uk.ac.cf._5.group14.One_To_One.HealthDataInput;

public class InvalidHealthConditionException extends IllegalArgumentException {
    public InvalidHealthConditionException() {
        super("Selected health conditions are unavailable");
    }
}
