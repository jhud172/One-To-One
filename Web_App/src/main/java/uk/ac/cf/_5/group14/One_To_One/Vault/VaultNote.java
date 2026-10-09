package uk.ac.cf._5.group14.One_To_One.Vault;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "vault_notes")
public class VaultNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private VaultNoteType noteType;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "linked_date")
    private LocalDate linkedDate;

    @Column(name = "linked_workout_session_id")
    private Long linkedWorkoutSessionId;

    @Column(name = "trainer_template_id")
    private Long trainerTemplateId;

    @Column(name = "trainer_template_entry_id")
    private Long trainerTemplateEntryId;

    @Column(name = "pinned", nullable = false)
    private boolean pinned = false;

    @Column(name = "tags", nullable = false, length = 255)
    private String tags = "";

    @Column(name = "mood", length = 20)
    private String mood;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_generated_at")
    private Instant aiGeneratedAt;

    @Column(name = "ai_source_revision", length = 64)
    private String aiSourceRevision;

    public Instant getAiGeneratedAt() { return aiGeneratedAt; }
    public void setAiGeneratedAt(Instant value) { aiGeneratedAt = value; }
    public String getAiSourceRevision() { return aiSourceRevision; }
    public void setAiSourceRevision(String value) { aiSourceRevision = value; }

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public VaultNote() {
    }

    public VaultNote(Long userId, VaultNoteType noteType, String title, String content) {
        this.userId = userId;
        this.noteType = noteType;
        this.title = title;
        this.content = content;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (tags == null) {
            tags = "";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
        if (tags == null) {
            tags = "";
        }
    }

    public Long getId() {
        return id;
    }

    @Transient
    public String getRevision() {
        return VaultRevision.of(this);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public VaultNoteType getNoteType() {
        return noteType;
    }

    public void setNoteType(VaultNoteType noteType) {
        this.noteType = noteType;
    }

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

    public LocalDate getLinkedDate() {
        return linkedDate;
    }

    public void setLinkedDate(LocalDate linkedDate) {
        this.linkedDate = linkedDate;
    }

    public Long getLinkedWorkoutSessionId() {
        return linkedWorkoutSessionId;
    }

    public void setLinkedWorkoutSessionId(Long linkedWorkoutSessionId) {
        this.linkedWorkoutSessionId = linkedWorkoutSessionId;
    }

    public Long getTrainerTemplateId() {
        return trainerTemplateId;
    }

    public void setTrainerTemplateId(Long trainerTemplateId) {
        this.trainerTemplateId = trainerTemplateId;
    }

    public Long getTrainerTemplateEntryId() {
        return trainerTemplateEntryId;
    }

    public void setTrainerTemplateEntryId(Long trainerTemplateEntryId) {
        this.trainerTemplateEntryId = trainerTemplateEntryId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public String getTags() {
        return tags == null ? "" : tags;
    }

    public void setTags(String tags) {
        this.tags = tags == null ? "" : tags;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getAiSummary() {
        return aiSummary;
    }

    public void setAiSummary(String aiSummary) {
        this.aiSummary = aiSummary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
