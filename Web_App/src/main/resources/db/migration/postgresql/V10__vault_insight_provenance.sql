-- Existing insights retain unknown provenance; never infer their source version or date.
ALTER TABLE vault_notes ADD COLUMN IF NOT EXISTS ai_generated_at TIMESTAMP NULL;
ALTER TABLE vault_notes ADD COLUMN IF NOT EXISTS ai_source_revision VARCHAR(64) NULL;
