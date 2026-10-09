package uk.ac.cf._5.group14.One_To_One.Reviews;

import java.util.List;

/** Field names identify the retained draft controls; no submitted text enters the error message. */
public class ReviewValidationException extends IllegalArgumentException {
    private final List<String> fields;

    public ReviewValidationException(List<String> fields) {
        super("Invalid review fields");
        this.fields = List.copyOf(fields);
    }

    public List<String> getFields() { return fields; }
}
