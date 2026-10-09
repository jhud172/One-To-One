# PostgreSQL migrations

`V1__baseline_schema.sql` is the first Flyway baseline for a fresh PostgreSQL
database. It intentionally contains schema only and creates no user, role,
subscription or demonstration records.

For every later database change:

1. add a new `V{number}__short_description.sql` file;
2. never edit a migration that has run in a shared environment;
3. make the forward migration safe for the current application version;
4. add focused repository or integration coverage;
5. take and verify a staging backup before applying a destructive change.

Flyway Community migrations are forward-only. Repair a failed release with a
new forward migration. Restore a backup into a separate empty database when a
data rollback is required; do not attempt an in-place downgrade.

`V9__verification_evidence_and_history.sql` adds bounded private qualification
files and append-only review snapshots. It labels surviving legacy request state
without reconstructing overwritten history. Review the [evidence behaviour and
retention limits](../../../../../../docs/verification-evidence.md) before staging
acceptance. The local/test explicit schemas include matching tables; production
migration/backfill acceptance remains separate from H2 tests.

`V10__vault_insight_provenance.sql` adds nullable saved-insight time and source
revision fields. Existing insights remain readable with unknown provenance;
no date or source revision is invented for them. Current generation saves only
against the unchanged reflection revision and records its save time. Reflection
content/context edits clear the insight and its metadata together. Local/test
schemas include the same columns. Applying this forward migration and concurrent
acceptance on PostgreSQL remain separate from local H2 checks.

`V11__merch_checkout_idempotency.sql` adds a nullable checkout UUID and an
owner-scoped unique index, plus the original hosted currency and return URLs.
Native checkout forms keep their identity in the page URL and reuse one order
and reservation on retry or browser Back. Existing orders and legacy requests without a key retain their prior
behaviour. Hosted requests use the order ID as their provider idempotency key
and saved item snapshots as their line item. Unknown hosted results keep stock
reserved for reconciliation. Apply and verify this migration separately on
staging PostgreSQL; local H2 checks do not prove that deployment acceptance.
