package uk.ac.cf._5.group14.One_To_One.Nutrition;

public class StaleNutritionLogException extends RuntimeException {
    public StaleNutritionLogException() {
        super("The saved entry changed. Review the latest entry before saving your draft.");
    }
}
