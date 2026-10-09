package uk.ac.cf._5.group14.One_To_One.Notes;

public class NoteUpdateRequest {
    private String title;
    private String content;
    private Long folderId;
    private String colour;
    private String revision;

    public String getRevision() { return revision; }
    public void setRevision(String revision) { this.revision = revision; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getFolderId() {
        return folderId;
    }

    public void setFolderId(Long folderId) {
        this.folderId = folderId;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }
}
