package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public class TrainerLibraryExerciseForm {

    @NotBlank
    @Size(max = 120)
    private String name;

    @Size(max = 10000)
    private String description;

    @NotBlank
    @Size(max = 255)
    private String primaryMuscles;

    @NotBlank
    @Size(max = 255)
    private String equipment;

    @NotBlank
    @Size(max = 30)
    private String difficulty;

    @Size(max = 500)
    private String videoUrl;

    /**
     * Newline-separated notes, stored as separate rows.
     */
    @Size(max = 10000)
    private String notesText;

    @AssertTrue
    public boolean isVideoUrlValid() {
        return isSafeVideoUrl(videoUrl);
    }

    public static boolean isSafeVideoUrl(String url) {
        return uk.ac.cf._5.group14.One_To_One.Security.SafeHttpUrl.isSafe(url);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPrimaryMuscles() {
        return primaryMuscles;
    }

    public void setPrimaryMuscles(String primaryMuscles) {
        this.primaryMuscles = primaryMuscles;
    }

    public String getEquipment() {
        return equipment;
    }

    public void setEquipment(String equipment) {
        this.equipment = equipment;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getNotesText() {
        return notesText;
    }

    public void setNotesText(String notesText) {
        this.notesText = notesText;
    }
}
