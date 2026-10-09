package uk.ac.cf._5.group14.One_To_One.TrainerTemplates;

import jakarta.validation.constraints.*;

public class TrainerScheduleMetadataForm {

    @NotBlank
    @Size(max = 200)
    private String name;

    @Size(max = 800)
    private String description;

    @Size(max = 500)
    private String tags;

    private boolean archived;

    @Size(max = 64)
    private String expectedRevision;

    public String getExpectedRevision() { return expectedRevision; }
    public void setExpectedRevision(String expectedRevision) { this.expectedRevision = expectedRevision; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
}
