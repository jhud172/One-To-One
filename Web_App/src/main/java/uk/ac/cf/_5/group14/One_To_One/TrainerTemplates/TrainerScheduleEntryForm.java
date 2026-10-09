package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import jakarta.validation.constraints.*;

public class TrainerScheduleEntryForm {

    @NotNull
    @Min(1)
    @Max(7)
    private Integer dayOfWeek;

    @Size(max = 8)
    private String timeWindowStart;

    @Size(max = 8)
    private String timeWindowEnd;

    @NotNull
    private TrainerScheduleTemplateEntryType type;

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 10000)
    private String defaultsJson;

    @Size(max = 80)
    private String intensityLabel;

    @Min(1)
    @Max(10)
    private Integer intensityLevel;

    private Long exerciseId;

    private Long customExerciseId;

    public Integer getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(Integer dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getTimeWindowStart() { return timeWindowStart; }
    public void setTimeWindowStart(String timeWindowStart) { this.timeWindowStart = timeWindowStart; }

    public String getTimeWindowEnd() { return timeWindowEnd; }
    public void setTimeWindowEnd(String timeWindowEnd) { this.timeWindowEnd = timeWindowEnd; }

    public TrainerScheduleTemplateEntryType getType() { return type; }
    public void setType(TrainerScheduleTemplateEntryType type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDefaultsJson() { return defaultsJson; }
    public void setDefaultsJson(String defaultsJson) { this.defaultsJson = defaultsJson; }

    public String getIntensityLabel() { return intensityLabel; }
    public void setIntensityLabel(String intensityLabel) { this.intensityLabel = intensityLabel; }

    public Integer getIntensityLevel() { return intensityLevel; }
    public void setIntensityLevel(Integer intensityLevel) { this.intensityLevel = intensityLevel; }

    public Long getExerciseId() { return exerciseId; }
    public void setExerciseId(Long exerciseId) { this.exerciseId = exerciseId; }

    public Long getCustomExerciseId() { return customExerciseId; }
    public void setCustomExerciseId(Long customExerciseId) { this.customExerciseId = customExerciseId; }
}
