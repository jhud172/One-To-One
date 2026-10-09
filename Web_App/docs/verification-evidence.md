# Professional review evidence and history

Implemented for the Version 2.0 preview on 2 October 2026. Page acceptance is
tracked in the [page audit](audits/one-to-one-v2-design-workflow-usability-audit-2026-10-01.md)
under W150, W151 and W176.

## Trainer workflow

An enabled trainer opens `/trainer/verification`, submits professional details
and confirms their accuracy. A request remains pending until an authorised
platform reviewer decides it. A gym affiliation or document upload does not
verify the trainer.

While the request is pending or needs information, the trainer can attach
qualification or insurance documents. A needs-info request retains the current
notes for editing. Resubmission returns the request to pending and preserves
earlier notes and reviewer feedback in the timeline. After rejection, a new
request has its own history and documents.

The document form supports PDF, PNG and JPEG, up to five saved documents per
request and 2 MiB per document. Images are limited to 4096 pixels per side and
12 million pixels in total. An entire upload batch is checked before any file
is saved. The trainer can remove an incorrect document while the review is
open, freeing a document slot. Removal deletes its stored bytes and records a
timeline event. Closed-review documents cannot be changed through this flow.

Forms, document links and expandable timeline details use native HTML. They
retain CSRF protection and work with scripting disabled. Browser proof includes
keyboard file selection, replacement upload, removal and a needs-info reply;
the full device/locale acceptance checklist remains separate.

## Access and storage

`VerificationEvidenceService` stores bounded document bytes, metadata and file
events in the same database transaction. It does not create a public upload URL
or depend on an ephemeral filesystem volume. History/list queries use document
metadata projections and do not load file contents.

`GET /verification/documents/{id}` checks the current enabled database account,
then the owning request, before retrieving bytes. Downloads are available to
the owning trainer and enabled platform/super administrators. Gym administrators
and other trainers cannot retrieve these files. Existing gym-assisted note
editing still requires the requesting gym's current affiliation; it does not
grant access to qualification files.

Responses use attachment disposition, `application/octet-stream`, `no-store`,
`nosniff`, no-referrer and a restrictive sandbox policy. Filenames are bounded,
normalised and detached from supplied path components. Images are decoded and
re-encoded without their uploaded metadata or appended payloads. PDF header and
end-marker checks identify the accepted format; they are **not** a complete PDF
parser, a malware scan or a qualification authenticity check. No document is
rendered inline or used to grant verification automatically.

## Review events

`VerificationEvent` records the request, actor where known, time, action,
previous/resulting status and note snapshots. Application code exposes no event
editing/deletion path. Submission, needs-info feedback, responses, approval,
rejection, attachment and removal each append an event. Identical replayed
review decisions add neither duplicate events nor duplicate notifications.

For older requests, V9 records an explicitly labelled surviving-state snapshot.
The first new transition also preserves a snapshot if a legacy request has no
events. Earlier overwritten notes cannot be reconstructed; no actor or historic
transition is invented. Trainer and reviewer pages reuse one translated timeline
fragment.

Trainer responses, evidence changes and approval lock the trainer before the
request. Request creation uses the same account lock, preventing duplicate open
applications. Upload quotas and closure checks run inside those locks. Genuine
concurrent H2 tests cover competing submissions, uploads, replies and decisions.

## Schema and release acceptance

`V9__verification_evidence_and_history.sql` creates the two private tables,
indexes, byte-size/format constraints and legacy snapshots. Local/test
`schema.sql` and the retained `schema-render.sql` definition include matching
tables. PostgreSQL migration V9 is prepared and packaged; it has not been run
against staging or production.

Foreign keys remove documents/events with their parent request. Account removal
cascades through owned requests/documents; retained events for someone else's
request lose the removed actor identifier. There is currently no scheduled
retention purge. Production retention periods, closed-review withdrawal,
backup handling, document scanning, database protection and audit-access review
remain release acceptance work. A gym-assisted file-sharing permission has not
been introduced.

Review notifications retain the existing email-service behaviour. There is no
delivery ledger/reconciliation yet. Browser testing used an explicitly
email-disabled disposable H2 preview; its condition report selected
`NoOpEmailService`, and the review-update log confirmed that delivery was skipped.
Automated tests mock the email service. No real qualification decision or
provider message is part of this evidence.

Commands, report counts and browser screenshots are recorded in the
[implementation log](qa/2026-10-01-v2-implementation.md) and
[prepared checkpoint](qa/2026-10-02-v2-prepared-checkpoint.md).
