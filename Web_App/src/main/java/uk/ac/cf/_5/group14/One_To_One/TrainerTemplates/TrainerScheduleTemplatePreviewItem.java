package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import java.time.LocalDate;
import java.time.LocalTime;

public class TrainerScheduleTemplatePreviewItem {

    private final LocalDate date;
    private final TrainerScheduleTemplateEntryType type;
    private final String title;
    private final LocalTime timeWindowStart;
    private final LocalTime timeWindowEnd;
    private final boolean duplicate;
    private final String exerciseName;
    private final String notes;

    public TrainerScheduleTemplatePreviewItem(LocalDate date,
                                              TrainerScheduleTemplateEntryType type,
                                              String title,
                                              LocalTime timeWindowStart,
                                              LocalTime timeWindowEnd,
                                              boolean duplicate) {
        this(date, type, title, timeWindowStart, timeWindowEnd, duplicate, null, null);
    }

    public TrainerScheduleTemplatePreviewItem(LocalDate date, TrainerScheduleTemplateEntryType type, String title,
                                              LocalTime timeWindowStart, LocalTime timeWindowEnd, boolean duplicate,
                                              String exerciseName, String notes) {
        this.date = date;
        this.type = type;
        this.title = title;
        this.timeWindowStart = timeWindowStart;
        this.timeWindowEnd = timeWindowEnd;
        this.duplicate = duplicate;
        this.exerciseName = exerciseName;
        this.notes = notes;
    }

    public String getExerciseName() { return exerciseName; }
    public String getNotes() { return notes; }

    public LocalDate getDate() {
        return date;
    }

    public TrainerScheduleTemplateEntryType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public LocalTime getTimeWindowStart() {
        return timeWindowStart;
    }

    public LocalTime getTimeWindowEnd() {
        return timeWindowEnd;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
