package uk.ac.cf._5.group14.One_To_One.TrainerLibrary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TrainerLibraryWorkoutTemplateForm {

    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 10000)
    private String summary;

    /**
     * Newline-separated notes, stored as separate rows.
     */
    @Size(max = 10000)
    private String notesText;

    @Size(max = 64)
    private String expectedRevision;

    public String getExpectedRevision() { return expectedRevision; }

    public void setExpectedRevision(String expectedRevision) { this.expectedRevision = expectedRevision; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNotesText() {
        return notesText;
    }

    public void setNotesText(String notesText) {
        this.notesText = notesText;
    }
}
