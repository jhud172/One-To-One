package uk.ac.cf._5.group14.One_To_One.CalendarData;

public enum ActivityType {
    GYM("Gym", "\uD83C\uDFCB\uFE0F"),
    SWIM("Swim", "\uD83C\uDFCA"),
    RUN("Run", "\uD83C\uDFC3"),
    BIKE("Bike", "\uD83D\uDEB4"),
    CUSTOM("Custom", "\u26A1");

    private final String label;
    private final String icon;

    ActivityType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }
}
