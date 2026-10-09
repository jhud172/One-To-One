-- A trainer can have several consented gym affiliations. Preserve the original primary-gym field.
CREATE TABLE trainer_gym_affiliations (
    id BIGSERIAL PRIMARY KEY,
    trainer_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    gym_id BIGINT NOT NULL REFERENCES gym_profiles(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','DECLINED','ENDED')),
    initiated_by VARCHAR(20) NOT NULL CHECK (initiated_by IN ('GYM','TRAINER','LEGACY')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_trainer_gym_affiliation UNIQUE(trainer_user_id, gym_id)
);
CREATE INDEX idx_gym_affiliation_status ON trainer_gym_affiliations(gym_id, status);
CREATE TABLE trainer_gym_affiliation_events (
    id BIGSERIAL PRIMARY KEY,
    affiliation_id BIGINT NOT NULL REFERENCES trainer_gym_affiliations(id) ON DELETE CASCADE,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_gym_affiliation_events ON trainer_gym_affiliation_events(affiliation_id, created_at DESC, id DESC);

INSERT INTO trainer_gym_affiliations(trainer_user_id, gym_id, status, initiated_by, created_at, updated_at)
SELECT u.id, g.id, 'ACTIVE', 'LEGACY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u JOIN gym_profiles g ON g.id = u.gym_id WHERE u.role = 'TRAINER';
INSERT INTO trainer_gym_affiliation_events(affiliation_id, actor_user_id, action, created_at)
SELECT id, NULL, 'LEGACY_IMPORTED', created_at FROM trainer_gym_affiliations;
