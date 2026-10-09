package uk.ac.cf._5.group14.One_To_One.Health.BloodPressure;

public enum BpCategory {
    LOW("Low", "bg-blue-100 text-blue-700"),
    NORMAL("Normal", "bg-emerald-100 text-emerald-700"),
    ELEVATED("Elevated", "bg-yellow-100 text-yellow-700"),
    HIGH_STAGE1("High Stage 1", "bg-orange-100 text-orange-700"),
    HIGH_STAGE2("High Stage 2", "bg-red-100 text-red-700"),
    CRISIS("Hypertensive Crisis", "bg-red-200 text-red-800");

    public final String label;
    public final String badgeClass;

    BpCategory(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    /** Classify based on systolic and diastolic. */
    public static BpCategory classify(int systolic, int diastolic) {
        if (systolic >= 180 || diastolic >= 120) return CRISIS;
        if (systolic >= 140 || diastolic >= 90)  return HIGH_STAGE2;
        if (systolic >= 130 || diastolic >= 80)  return HIGH_STAGE1;
        if (systolic < 90 || diastolic < 60) return LOW;
        if (systolic >= 120 && diastolic < 80)   return ELEVATED;
        return NORMAL;
    }
}
