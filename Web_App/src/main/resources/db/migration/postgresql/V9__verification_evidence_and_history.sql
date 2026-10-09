-- Private qualification evidence and append-only application review snapshots.
CREATE TABLE trainer_verification_events (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES trainer_verification_requests(id) ON DELETE CASCADE,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(24) NOT NULL CHECK (action IN ('LEGACY_SNAPSHOT','SUBMITTED','RESPONDED','NEEDS_INFO','APPROVED','REJECTED','DOCUMENT_ADDED','DOCUMENT_REMOVED')),
    previous_status VARCHAR(20),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','NEEDS_INFO','APPROVED','REJECTED')),
    trainer_notes TEXT,
    admin_notes TEXT,
    document_name VARCHAR(120),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_verification_events_request ON trainer_verification_events(request_id, created_at, id);

CREATE TABLE trainer_verification_documents (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES trainer_verification_requests(id) ON DELETE CASCADE,
    uploaded_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(120) NOT NULL,
    content_type VARCHAR(40) NOT NULL CHECK (content_type IN ('application/pdf','image/png','image/jpeg')),
    byte_size BIGINT NOT NULL CHECK (byte_size BETWEEN 1 AND 2097152),
    content BYTEA NOT NULL CHECK (octet_length(content) BETWEEN 1 AND 2097152),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT verification_document_size_matches CHECK (octet_length(content) = byte_size)
);
CREATE INDEX idx_verification_documents_request ON trainer_verification_documents(request_id, created_at, id);

-- Earlier overwritten notes cannot be reconstructed. Label the surviving state
-- honestly; do not assign a fabricated actor or historical transition timestamp.
INSERT INTO trainer_verification_events(request_id, actor_user_id, action, status, trainer_notes, admin_notes, created_at)
SELECT id, NULL, 'LEGACY_SNAPSHOT', status, notes, admin_notes, CURRENT_TIMESTAMP
FROM trainer_verification_requests;
