# Laptop handoff — 9 October 2026

This commit preserves the current web/native Version 2.0 work, design assets, screenshots, checks and page-by-page audit. The rebuild is unfinished. The saved desktop goal is currently paused; continue the existing scope on the laptop rather than treating this as a finished release.

## Pick up the repository

The branch is `main`, remote `https://github.com/jhud172/One-To-One.git`. In an existing clean laptop checkout run `git pull --ff-only origin main`. Keep any laptop changes safe before pulling.

Read `Web_App/AGENTS.md`, the [page checklist](../audits/one-to-one-v2-design-workflow-usability-audit-2026-10-01.md), [prepared checkpoint](2026-10-03-v2-prepared-checkpoint.md), [implementation record](2026-10-01-v2-implementation.md) and [system overview](../System-Overview.md).

## Current stopping point

- W136/W137 gym operations and trainer roster are implemented locally: owned aggregate counts, bounded roster, multiple-gym preservation, private latest reviews, retained native notes and account/calendar disclosures. Eighteen accepted focused checks and eight inspected captures are recorded. Full acceptance stays open.
- W138 membership catalogue is implemented locally: product cards, whole-gym totals, literal search/status filters, ten/ten/six stable paging for the 26-row fixture, grouped subscriber counts and native deactivation. Every membership route checks actual gym ownership. Status updates refresh under a row lock, preserving a newer stored price and existing subscribers.
- Latest accepted W138 gate: sixteen checks across three suites, fourteen catalogue and twenty-eight shared roster/dashboard locale renders, cache `20261004v6m`, exact package proof and five inspected captures. Tests/build were run on **4 October**, not again for this handoff. The [portable receipt](evidence/2026-10-04-gym-membership-checkpoint.json) records these results.
- Local H2 preview fixtures, build logs, helper scripts, generated runtime output, `.env`, uploads, dependencies and IDE settings are ignored. The labelled extra QA products/trainers/screenshots do not imply those records exist in a fresh laptop database. Meaningful integration test fixtures are in committed test source. Configure local secrets separately if required; a provider-free local run needs no production secrets.

## Continue next

1. Finish W139 membership creation/editing. The metadata edit path still saves a previously read entity: apply the same lock/refresh approach used by status changes so editing details cannot overwrite a newer price. Preserve price/billing-period protection, subscribers, validation and draft/context recovery. Improve the editor and inspect its actual states.
2. Finish W140/W141 price-change and history design/workflows: effective-date clarity/timezone, audit state, duplicate submission, no-subscriber state, deterministic bounded history and truthful notification/delivery wording. Keep email/provider tests mocked or provider-free.
3. Fix the shared floating quick-actions launcher overlapping a lower membership-card action on tablet. Mobile duplicate launchers are already suppressed; preserve the existing dock destinations and keyboard behaviour.
4. Continue every remaining numbered web page and native Android surface in the audit. Existing W107 authored technique/equipment/media/model, W114/W115 context/notification, W133 hosted provider and all full role/state/zoom/no-script/back/refresh/device gaps remain open. Do not check whole-product acceptance from source or build success alone.

## Local commands

Use Java 21 and the repository's pinned Node setup. From `Web_App`:

```powershell
npm ci
npm run build:frontend
.\gradlew.bat bootRun --args='--spring.profiles.active=local --server.port=8081 --app.email.provider=none --app.sms.provider=console --app.ai.enabled=false'
```

After subsequent source/template changes, build changed assets first, then run `.\gradlew.bat reloadLocalPreview` in another terminal and wait for a fresh `Started OneToOneApplication` log before refreshing. Read the normal startup guide for environment details.

Relevant focused checks, when the next changes require them:

```powershell
.\gradlew.bat test --tests '*GymMembershipCatalogueIntegrationTest' --tests '*GymAdminMembershipControllerTest' --tests '*GymRosterWorkflowIntegrationTest' bootJar --console=plain
node tools/i18n/check-bundle-parity.mjs
```

For Android, use Android Studio and `Phone-App/application`; start with `gradlew.bat assembleDebug` once the SDK is configured. No new native-device acceptance was established during the latest web milestones. No production deployment is requested by this handoff.
