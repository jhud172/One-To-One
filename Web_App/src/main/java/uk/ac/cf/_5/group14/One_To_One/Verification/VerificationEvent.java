package uk.ac.cf._5.group14.One_To_One.Verification;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

/** A private snapshot of a committed transition, never edited by a later response. */
@Entity
@Immutable
@Getter
@Table(name = "trainer_verification_events")
public class VerificationEvent {
    public enum Action { LEGACY_SNAPSHOT, SUBMITTED, RESPONDED, NEEDS_INFO, APPROVED, REJECTED, DOCUMENT_ADDED, DOCUMENT_REMOVED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;
    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24, updatable = false)
    private Action action;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 20, updatable = false)
    private VerificationStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20, updatable = false)
    private VerificationStatus status;
    @Column(name = "trainer_notes", columnDefinition = "TEXT", updatable = false)
    private String trainerNotes;
    @Column(name = "admin_notes", columnDefinition = "TEXT", updatable = false)
    private String adminNotes;
    @Column(name = "document_name", length = 120, updatable = false)
    private String documentName;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationEvent() {}

    public VerificationEvent(TrainerVerificationRequest request, Long actorUserId, Action action,
                             VerificationStatus previousStatus, String documentName) {
        this.requestId = request.getId();
        this.actorUserId = actorUserId;
        this.action = action;
        this.previousStatus = previousStatus;
        this.status = request.getStatus();
        this.trainerNotes = request.getNotes();
        this.adminNotes = request.getAdminNotes();
        this.documentName = documentName;
        this.createdAt = Instant.now();
    }
}
