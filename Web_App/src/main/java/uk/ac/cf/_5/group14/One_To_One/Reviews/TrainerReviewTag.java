package uk.ac.cf._5.group14.One_To_One.Reviews;

import java.util.Arrays;

/** Persisted tag codes remain compatible; only their display labels are translated. */
public enum TrainerReviewTag {
    PROFESSIONAL("Professional", "ui.02229"),
    RESPONSIVE("Responsive", "ui.02230"),
    KNOWLEDGEABLE("Knowledgeable", "ui.02231"),
    MOTIVATING("Motivating", "ui.02232"),
    SUPPORTIVE("Supportive", "ui.02233"),
    PATIENT("Patient", "ui.02234");

    private final String value;
    private final String labelKey;

    TrainerReviewTag(String value, String labelKey) { this.value=value; this.labelKey=labelKey; }

    public static boolean isSupported(String value) { return labelKey(value) != null; }

    public static String labelKey(String value) {
        return Arrays.stream(values()).filter(tag -> tag.value.equals(value))
                .map(tag -> tag.labelKey).findFirst().orElse(null);
    }
}
