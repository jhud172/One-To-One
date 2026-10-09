# System Overview

## Purpose

This file is the stable product and platform overview for the One To One repository.

Use this document when you need the broad answer to:

- what the platform is
- which user roles exist
- which feature areas are active in the codebase
- how the application is structured at a technical level

If you need the current development-only behavior, use [System-Overview-Dev-Mode.md](./System-Overview-Dev-Mode.md).

## Platform Summary

One To One is a Spring Boot coaching platform built around trainer-client collaboration. The codebase supports public marketing pages, signup and verification, role-based dashboards, planning and calendar flows, workouts, health and nutrition tracking, goals and check-ins, messaging, profile customization, trainer and gym operations, payments, merch, and admin oversight.

Source inventory refreshed 4 October 2026 (counts do not imply complete runtime acceptance):

- `77` Java files with controller annotations
- `187` HTML/Thymeleaf templates under runtime resources
- `93` JavaScript files under `src/main/resources/static/js`
- `150` CSS files under `src/main/resources/static/css`, including generated bundles
- `224` Java files under `src/test/java`

## Roles

### Guest

Guests can access the public landing and information pages, browse public discovery surfaces, view pricing, and start signup flows.

### Client

Clients are the main end users of the product. Their core surfaces include:

- dashboard
- calendar and scheduling
- goals and progress tracking
- workouts and workout sessions
- health, nutrition, and daily logging
- inbox and chat
- profile, preferences, milestones, and level progress

### Trainer

Trainers can manage their coaching presence and work with clients through:

- trainer dashboard with owned requests, submitted check-in priorities and unread client-message counts; personal scheduling remains in a separate disclosure
- trainer profile
- trainer library and templates, including bounded weekly-blueprint search, actual weekday/check-in counts and retained clone/editor/application context; native application binds reviewed source/client/date/duplicate-policy values, rejects stale review before writing and reports real dated calendar/Vault copies, with native owned-name search, bounded recent creations and real resource totals in the library hub; exercise/workout/programme catalogues have bounded owned search and page-only composition counts, retained return context and shared create/edit draft previews; workout, programme and schedule metadata edits preserve identity and refresh the locked owned row before rejecting stale saves for explicit review; the schedule studio separates metadata, ID-preserving entry/question ordering and its saved weekly preview; native exercise/session ordering preserves prescriptions and repeated references, with saved workout/programme client-instruction previews
- library exercise instructions remain visible to their receiving active clients, including later saved edits; private client notes belong in the client workspace, and contextual sharing/deletion preserve safe exercise navigation
- client assignments and plan sharing, with owned movement/day previews, validated coach notes, current-client check-in/conversation entry and recent phase history in one workspace
- check-in review with retained validation drafts, saved client notes/responses, linked account notifications and owner-only client history/read views
- trainer owner settings with retained validated drafts, public-link visibility preservation and native appearance/calendar/accessibility forms shared with gym owners
- messaging and client relationship pages with pending-request Accept/Decline and owned Pause/Resume/End actions

### Gym Admin

Gym admins handle operational and business-facing areas such as:

- gym dashboard
- trainer onboarding and verification support
- membership management
- gym profile and operational settings

### Platform Admin and Super Admin

Admin-facing areas cover broader platform oversight, including:

- admin dashboard
- feedback and moderation-related surfaces
- development page controls
- super-admin-only routes

## Main Feature Areas

### Public and Marketing

The public site includes the landing experience and supporting pages such as:

- `/`
- `/about`
- `/faq`
- `/pricing`
- `/explore`
- policy pages under `/policies/**`

### Authentication and Verification

The application includes:

- login
- role-based signup
- forgot/reset password
- email verification
- phone verification

Security is handled through Spring Security with method security enabled.

### Dashboards and Role Home Surfaces

The codebase includes dedicated dashboards for:

- client
- trainer
- gym admin
- platform/admin

The root route redirects signed-in users to `/dashboard`.

### Planning, Calendar, and Scheduling

Planning is a major part of the repository and includes:

- day, week, and month calendar views
- task detail and day planning flows
- schedule creation and application flows
- workout-linked scheduling

### Workouts and Training

Training-related modules include:

- workout lists and sessions
- workout templates
- trainer-assigned plans
- workout feedback and logging
- private exercise reflections with literal linked-exercise/note/date search, inclusive date filters, stable database pagination and native previews; recorded set/session history remains separate. Authenticated local development uses the same ownership checks. Owned PDF exports all reflections with numeric 1–4 ratings and optional duration.

Personal workout templates and sessions are separate from scheduled strength sessions. The personal player uses protected Start/Save forms, preserves open sessions on return and displays actual completed-set volume. Optional clips are private recordings for review; automated form analysis is unavailable. Historical placeholder scores remain stored but are not returned as analysis. See the [current implementation checkpoint](qa/2026-10-03-v2-prepared-checkpoint.md#personal-workout-player-follow-up) for verified behaviour and remaining device/failure acceptance.

The separate `/workout` schedule composer resolves public catalogue and owned custom references before saving, supports native form commands and retains existing JSON APIs. Workouts/custom exercises referenced by saved training data cannot be deleted through this flow. Catalogue and custom groups currently have no persisted mixed ordering. Optional AI suggestions use successful provider output only, with an unavailable state instead of invented fallback exercises. Core checks and outstanding acceptance are recorded in the [composer checkpoint](qa/2026-10-03-v2-prepared-checkpoint.md#schedule-workout-composer-and-shared-fragments-follow-up).

Workout UI layouts use `WorkoutTemplate.WorkoutTemplate`, separate from personal exercise templates and coach programmes. The editor stores bounded presentation JSON through labelled native fields and server validation. Built-ins are copy-only; owned/private layout access is enforced for edit, copy, delete and preference selection. Default resolution ignores foreign stored preferences. Both active player controllers receive safe presentation settings through scoped model advice. Theme/density/layout/transition and optional progress/rest presentation apply without changing workout data; component ordering remains a preview blueprint. Referenced template-session layouts cannot be deleted; successful deletion clears obsolete preferences.

The schedule workspace distinguishes reusable schedules from their saved deployment date windows. Multiple plans/windows can coexist. Explicit native settings change calendar visibility without deleting or regenerating occurrences. Calendar day/week/month views use a visible-window union; saved sessions/history retain their records. Logging requests appear on the actual day card without changing completion rules. Legacy removal/deactivation hides windows. Deletion rejects occurrence, deployment, session and coach-assignment references. Native/API copies share a transaction and preserve schedule metadata and entry order while leaving deployment windows separate. Shared inline previews are limited to the active trainer relationship. The native builder copies actual owned workout movements into typed cycles. The saved-plan studio validates ownership/limits and uses a locked revision check; it preserves existing occurrence/session snapshots. Native calendar Preview saves nothing and reports exact cycle dates plus new/existing counts. Apply recalculates the current plan and emits real feedback. Custom-only entry/occurrence mappings allow the missing catalogue reference required by the existing schema. The unused standalone chooser is archived outside runtime resources. JDBC session attribute creation uses conflict-safe H2/PostgreSQL queries to handle parallel first writes without duplicate-key 500 errors.

### Health, Nutrition, and Daily Tracking

The repository contains active modules for:

- health records
- nutrition logging
- day health and day mode
- strength and exercise logging
- conditions and preference-driven defaults

### Goals, Check-Ins, and Progress

Progress-related areas include:

- goals
- check-ins
- achievements
- levels
- milestones surfaced through profile previews

### Messaging and AI

Communication surfaces include:

- inbox and message flows
- chat / AI coach surfaces
- notifications

AI behavior is environment-controlled and uses the configured model and API key from application properties.

### Profile and Personalization

Profile and preference work is a first-class feature area. The repository supports:

- editable user profile details
- avatar and image upload
- bio
- profile theme customization
- visible milestone selection
- weather and time preferences
- accessibility and equipment defaults
- quick-preferences onboarding plus full settings management

### Payments, Billing, and Merch

Commerce and billing surfaces include:

- pricing and checkout
- platform billing
- merch shop and merch checkout
- payment-provider configuration through environment variables

## Technical Architecture

### Backend

- Java 21
- Spring Boot 3.5.7
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- scheduled jobs enabled through `@EnableScheduling`

### Frontend

- Thymeleaf server-rendered templates
- shared fragments
- external JavaScript modules
- Tailwind/PostCSS CSS pipeline
- compiled stylesheet at `src/main/resources/static/css/app.css`

### Data

The application uses different runtime profiles:

- `local`: in-memory H2 database, schema plus seeded demo data, default port `8081`
- `render`: PostgreSQL, versioned Flyway migrations and no automatic demo-data
  seed, default port `8080`

The application boot path also normalises Render/PostgreSQL URLs in
[`src/main/java/uk/ac/cf/_5/group14/One_To_One/OneToOneApplication.java`](../src/main/java/uk/ac/cf/_5/group14/One_To_One/OneToOneApplication.java).
`APP_DATABASE_SCHEMA` can bind a deployment to a dedicated PostgreSQL schema;
the same value is applied to JDBC, Flyway and Hibernate.

## Configuration Model

### Shared Configuration

Common configuration lives in:

- [`src/main/resources/application.properties`](../src/main/resources/application.properties)
- [`src/main/resources/application-local.properties`](../src/main/resources/application-local.properties)
- [`src/main/resources/application-render.properties`](../src/main/resources/application-render.properties)

### Important Runtime Flags

The main repository-level flags and integrations include:

- `DEV_MODE`
- `OPENAI_API_KEY`
- `APP_AI_ENABLED`
- `APP_AI_MODEL`
- `DATABASE_URL`
- `DATABASE_USER`
- `DATABASE_PASSWORD`
- `APP_DATABASE_SCHEMA`
- `STRIPE_SECRET_KEY`
- `STRIPE_WEBHOOK_SECRET`
- `APP_BASE_URL`
- `SPRING_MAIL_*`
- `APP_SMS_PROVIDER` and `TWILIO_*`

## Seed Data and Demo Accounts

Local development seeds run from `classpath:data/*.sql`.

The demo auth seed lives in [`src/main/resources/data/00-auth-demo.sql`](../src/main/resources/data/00-auth-demo.sql) and includes demo accounts for:

- client
- trainer
- gym admin
- platform admin

## Repository Layout

Key paths:

- application entry point: [`src/main/java/uk/ac/cf/_5/group14/One_To_One/OneToOneApplication.java`](../src/main/java/uk/ac/cf/_5/group14/One_To_One/OneToOneApplication.java)
- Java source: [`src/main/java/uk/ac/cf/_5/group14/One_To_One`](../src/main/java/uk/ac/cf/_5/group14/One_To_One)
- templates: [`src/main/resources/templates`](../src/main/resources/templates)
- static assets: [`src/main/resources/static`](../src/main/resources/static)
- schema and seed data: [`src/main/resources`](../src/main/resources)
- tests: [`src/test/java`](../src/test/java)
- deployment container: [`Dockerfile`](../Dockerfile)

## Documentation Rule

Keep this file stable and broad.

When behavior changes in a way that is specific to active testing, development gates, or temporary branch state, update [System-Overview-Dev-Mode.md](./System-Overview-Dev-Mode.md) first. Move changes into this file only when they represent the normal baseline of the repository.

### Private health baseline snapshots (local Version 2.0 acceptance, 4 October)

Owned six-row database history filters literal dates/activity, inclusive date bounds and exact activity, retaining paging/sort. Required measurements/date/activity preserve rejected native drafts; optional catalogue conditions reject unavailable IDs. Reports use saved measurements/calculations and missing-value dashes. Later measurements create a new snapshot; latest baseline conditions inform existing exercise preferences. Native edit/export and full retention/device acceptance remain open.

### Private blood-pressure history and recovery (local Version 2.0 acceptance, 4 October)

Owned stable six-reading DB history includes all dates; the dated period summary remains distinct. Native entry/edit retain named rejected drafts and optional measurement context. Owner/reading locks and content revisions protect stale edits/deletes; reviewed save retains identity/source. Untimed duplicate recovery focuses Time. Shared Cancel confirmation retains drafts, including already rejected inputs. Full script-disabled/clinical-policy/PostgreSQL concurrency/device acceptance remains open; existing API classifications/numeric limits unchanged.

### Daily nutrition date/draft recovery (local Version 2.0 acceptance, 4 October)

Owned intake stays separate from saved targets and calculated totals. Native malformed dates/numeric values retain named literal drafts; entry date binds to the navigator. Owner/date/content revisions plus owner/entry locks protect concurrent empty-day and saved drafts, with explicit reviewed-save recovery. Shared confirmation protects dirty/rejected native day navigation. Existing bounds/calculations/trimmed-note/internal-upsert contracts retained. Full no-script/PostgreSQL concurrency/device acceptance remains open.

### Coaching inbox consistency (local Version 2.0 milestone, 4 October)

Canonical inbox/thread and compatibility entry routes share private message history and native structured check-ins. Both pages load the inbox script once; initialisation is also guarded. Sends return the exact saved message; refreshed coaching locks prevent stale ACTIVE writes, recipient owner locks and rendered snapshot bounds prevent overlapping receipts/reading unseen future messages. Drafts survive rejection/unconfirmed sends; explicit history review precedes uncertain retry. Conversations precede compact notification access; mobile cards have bounded grid tracks and previews/receipts reflect actual history. Local focused gates and sequential role/browser evidence recorded in the prepared checkpoint; full long-history/context/no-script/notification-theme/device and PostgreSQL acceptance remain open.

### Earlier conversation archive (local Version 2.0 milestone, 4 October)

Owned read-only history remains in its original tables, separate from current Charlie/human coaching. Owner-scoped stable queries serve 20 conversations/30 messages, clamp oversized pages before offsets and preserve native search/collection return context. Shared collection navigation uses an optional single cache-versioned compact disclosure; saved human/system labels, literal instructions and RTL dates are accurate. Existing mutation/provider safety remains. Nine focused checks and local browser journeys/captures are recorded in the prepared checkpoint; full no-script, scroll/close, provider and device acceptance remains open. The full web/native goal continues through Notes and remaining pages.

### Personal Notes (local Version 2.0 milestone, 4 October)

Owner-scoped 20-note native/paged API queries use stable date/ID ordering and literal search. The existing array API is retained. Current editors submit optional content revisions; stale writes are rejected under a lock before mutation, retain drafts and require visible current-content review. Display/export sanitises historical HTML without changing stored originals. Native validation/folder destinations, adaptive disclosures, accessible rich controls and dependency-failure fallback remain usable. Reader typography/time and automatic mixed-language direction are fixed. Nine Java/eight JavaScript checks and local browser proof are recorded in the prepared checkpoint; complete no-script/export/copy/delete/device and database-race acceptance remains open. The full goal continues through Training Vault and all remaining web/native work.

### Training Vault (local Version 2.0 milestone, 4 October)

Owned native reflections use stable count-first 20-row pagination, literal filters and retained return context; legacy list methods remain unbounded. Current optional revision hashes cover original reflection/context. Owned-row refresh/write locks reject stale native saves for explicit retained-draft review and prevent checked provider replies being attached to changed content. Optional blank mood is normalised before unchanged-content comparison. Insight provenance adds nullable ai_generated_at and ai_source_revision fields; the first records save time and unknown legacy metadata remains unknown. Local/test schemas and forward V10__vault_insight_provenance.sql are updated; PostgreSQL application is unverified. Original text and insight stay escaped and separate, owned session names remain private, explicit consent/Premium and disabled-provider scope remain enforced. Thirteen Java/three JavaScript checks, 42 locale renders and current local viewport proof are recorded in the prepared checkpoint. Real provider, complete state/device and PostgreSQL race acceptance remain open; the full goal continues through Reviews and every remaining web/native surface.

### Trainer review submission (local Version 2.0 milestone, 4 October)

Both public profiles expose native review entry to eligible clients. Missing/non-trainer/unverified/disabled review targets return 404. Active or previously activated ended client/trainer links qualify; no completed-session requirement is invented. Pair-scoped latest ended lookup avoids reading other clients. Creation refreshes the client/link under the shared client write lock before eligibility and duplicate checks. Repeated/changed-relationship submissions return retained 409 drafts and preserve the original review; deployed concurrent races remain unverified. Named validation retains literal draft fields. Shared tag codes validate new inputs and translate display labels; unknown historical tags stay escaped without data migration. Native radios/checkboxes, mixed-language comment direction, dirty-draft status and one external script remain usable. Nineteen Java/two JavaScript checks, 42 locale renders and local native viewport evidence are recorded in the prepared checkpoint. Full state/no-script/device acceptance remains open and the whole goal continues through merchandise/orders and all remaining web/native surfaces.

### Merchandise checkout retries and uncertain reservations (4 October 2026)

Native checkout retains an owner-scoped UUID in its URL/form and order. Initial
creation locks the owner, atomically reserves stock and resolves a demo card in
one transaction; a replay uses the original order. Completed checkout URLs return
to history on browser Back. Keyless legacy requests remain compatible and do not
gain initial-purchase idempotency. Hosted requests retain original item snapshots,
expiry, currency and return URLs, use a stable provider key, and persist provider
intent before transmission. Unknown results keep stock reserved; known sessions
must be closed/unpaid before release. Provider/reference replacement and hosted
retry after the two-hour window are rejected. Expired unknown sessions require
reconciliation. Safe display drafts exclude PAN/tokens. Local/test schemas and
forward V11 contain nullable fields and an owner/key unique index; no PostgreSQL
migration or live/sandbox hosted request was performed. Native local demo/Back,
concurrent H2 and package evidence is in the prepared checkpoint. Full catalogue,
order-history, initial price/expiry validation and release acceptance remain open.

### Bounded merchandise catalogue and return continuity (4 October 2026)

The native shop counts matching active products and fetches twenty rows ordered
by creation time/ID. Literal search/category/stock filters and clamped native
pages retain query context. Only a truly empty active catalogue shows eight
unsaved samples; filtered emptiness is distinct. Checkout preserves validated
relative shop return URLs. Cached image failures recover to accessible fallback;
secondary media loads on interaction. Final v6e native results focus and action
contrast evidence is in the prepared checkpoint. Order-history paging and
initial-price/card-expiry validation remain current work. No provider request,
PostgreSQL migration or production mutation was performed.

### Native checkout price review and shared expiry (4 October 2026)

Native first purchase compares displayed integer-pence price under an owner and
product lock before reserving stock. A changed price returns a retained draft,
updated server total and explicit confirmation action without mutation. Existing
order retries keep their snapshot. Missing/invalid native identity receives a
fresh reviewable quote; internal legacy overloads remain compatible. The initial
valid quantity total is server rendered. Shared expiry validation uses the
application UTC Clock, accepts the current month through its final day and guards
new/saved simulated selections plus profile creation/edits. Final v6h 33-check,
package and selected native expiry/recovery evidence is in the prepared checkpoint.
Hosted sandbox, PostgreSQL and complete release acceptance remain open; order
history/profile previews are the next bounded-list work.

### Canonical owned order history and profile preview (4 October 2026)

Authenticated `/orders` counts owned merchandise records, selects twenty stable IDs, then fetches only those documents/item snapshots. Literal snapshot/ID search, all eight shipping-state filters, retained invalid forms, accurate empty feedback and native pagination preserve existing ownership, pending resume/cancel and CSRF contracts. Payment and recorded delivery are separate; simulated rows explicitly have no real payment/delivery and omit shipment details. `/profile/orders` remains a private alias. The unused duplicate template/script are archived outside runtime resources and excluded from the executable. Profile fetches only five recent orders and a real count, shown in reachable Options with canonical history links. A stray div previously truncated the content fragment, omitting card/crop/date dialogs; the boundary is fixed. Options gains visible close/focus return/Tab wrap, closed drawers are inert and hidden, invisible editor dialogs stay unfocusable, and the phone panel fits available width. Thirteen local checks, 28 locale renders, exact v6j resources/classes and inspected native desktop/phone/dark-tablet/Arabic captures are recorded in the prepared checkpoint. Full hosted/role/state/no-script/zoom/native-device acceptance remains open; the whole web/native goal continues into gym operations and every remaining page.

## Owned gym operations and trainer roster

Gym operations checks actual enabled gym ownership and uses aggregate current trainer, verification, own latest-review and active membership/subscription counts. Additional accepted gym affiliations remain supported; ended/pending explicit connections exclude legacy primary links. The roster uses at most twenty stable cards, literal search, own-review/verified filters and selected current-gym review documents. Platform verification and gym review are distinct. Native account/calendar disclosures preserve existing creation and personal planning; page-two notes edits retain validated drafts/context. Eighteen local checks, 28 locale renders, exact v6l package evidence and inspected desktop/phone/dark-tablet/Arabic captures are in the prepared checkpoint. Complete Q/no-script/zoom/role-state/native-device acceptance remains open; the full goal continues through membership products and every remaining web/native surface.

## Owned membership catalogue

Membership routes and the trainer roster share actual enabled gym-owner access checks. The catalogue uses real whole-gym totals, grouped counts for selected active subscribers, native literal search/status filters, stable bounded paging and product cards. Status changes lock and refresh the row, preserving current pricing and existing subscriptions; native disclosures state the consequence. Sixteen local checks, 42 relevant locale renders and five inspected captures are recorded in the prepared checkpoint and portable laptop receipt. Full Q acceptance, shared floating-control collision and W139–W141 editing/price-change/history work remain open.
