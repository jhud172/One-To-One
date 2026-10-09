package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

public class StaleBloodPressureReadingException extends IllegalStateException {
    public StaleBloodPressureReadingException() { super("Reading changed; review the latest saved values"); }
}
