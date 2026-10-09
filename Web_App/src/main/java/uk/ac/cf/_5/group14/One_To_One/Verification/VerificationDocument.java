package uk.ac.cf._5.group14.One_To_One.Verification;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import java.time.Instant;

/** Bounded private evidence stored atomically with its review event. No public file path. */
@Entity @Immutable @Getter
@Table(name = "trainer_verification_documents")
public class VerificationDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;
    @Column(name = "uploaded_by_user_id", nullable = false, updatable = false)
    private Long uploadedByUserId;
    @Column(name = "file_name", nullable = false, length = 120, updatable = false)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 40, updatable = false)
    private String contentType;
    @Column(name = "byte_size", nullable = false, updatable = false)
    private long byteSize;
    @Column(name = "content", nullable = false, columnDefinition = "bytea", updatable = false)
    private byte[] content;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VerificationDocument() {}
    public VerificationDocument(Long requestId, Long ownerId, String fileName, String contentType, byte[] content) {
        this.requestId = requestId;
        this.uploadedByUserId = ownerId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content.clone();
        this.byteSize = content.length;
        this.createdAt = Instant.now();
    }
}
