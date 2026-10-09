# One To One

One To One is a Spring Boot coaching platform for trainer-client collaboration, planning, tracking, messaging, profile customization, payments, and role-based dashboards.

## Read This First

The main documentation now lives in [`docs/README.md`](./docs/README.md).

Core docs:

- stable platform overview: [`docs/System-Overview.md`](./docs/System-Overview.md)
- dev-mode-active overview: [`docs/System-Overview-Dev-Mode.md`](./docs/System-Overview-Dev-Mode.md)
- startup and deployment guide: [`docs/README.md`](./docs/README.md)
- professional review evidence and history: [`docs/verification-evidence.md`](./docs/verification-evidence.md)

## Quick Start

Prerequisites:

- Java 21
- Node.js and npm

Use the pinned Node version before installing frontend tooling:

```bash
nvm use
```

Optional local overrides:

- copy [`.env.example`](./.env.example) to [`../.env`](../.env) if you need to provide mail, SMS, AI, payment, or base-URL settings for local runs
- `bootRun` loads the repository-root `.env` first, then falls back to `Web_App/.env` for older local setups
- database keys in `.env` are ignored when the active profile is `local` or `test`, so production PostgreSQL settings cannot replace the embedded H2 datasource

Install frontend dependencies:

```bash
npm install
```

Build the frontend assets:

```bash
npm run build:frontend
```

Run the app:

```bash
./gradlew bootRun
```

Default local behavior:

- Spring profile: `local`
- URL: `http://localhost:8081`
- database: H2 in memory

Local DevTools reloads use a trigger file so compilation cannot restart the app with missing controllers/services. After editing source or templates, run `./gradlew reloadLocalPreview` (Windows: `.\gradlew.bat reloadLocalPreview`) in another terminal. It completes classes/resources before touching the marker. The marker lives in its own `build/local-preview-control` classpath directory, so resource copies cannot delete or recreate it. `bootRun` prepares a missing marker once; the marker is excluded from the executable package. Build changed frontend assets first with `npm run build:frontend`, or `npm run build:css` for CSS-only edits. Refresh the browser after the normal application-ready log. The initial `bootRun` command is unchanged. This uses Spring Boot's [documented trigger-file support](https://docs.spring.io/spring-boot/3.5/reference/using/devtools.html#using.devtools.restart.triggerfile).

The application also ignores an inherited `DATABASE_URL` for explicit `local` and `test` profiles. Render and other non-embedded profiles retain PostgreSQL URL normalisation.

## Owner account settings

Trainer `/profile` separates account details, public trainer details, photo, display/accessibility and data controls through native section links. Verified trainers can open their public listing or the professional editor; both edit the same saved trainer profile. Account/photo saves preserve public social visibility. Rejected trainer and gym edits retain their drafts through database-backed session redirects, with links to the invalid fields. Clearing an optional session price removes it; omitting that field preserves it.

Trainer and gym owners share native appearance, calendar-display and accessibility forms. Each saves explicitly. Invalid/missing appearance or calendar values preserve existing choices. Full media, account-verification and device acceptance remains tracked in the [Version 2.0 checkpoint](docs/qa/2026-10-03-v2-prepared-checkpoint.md#trainer-owner-settings-and-retained-profile-drafts--w085).

## Interactive homepage sculpture

The homepage includes a real Blender-modelled 3D logo, an opening-book network demo and an exploded construction view. Drag or use arrow keys to rotate; press Enter or select **Step inside the connection** to explore. **Make it yours** controls colours, lighting, separation, distance, mesh display and motion. The sample workout stays in the browser and resets on reload.

The generated GLB, Cycles-rendered poster and JavaScript bundle are included, so running or packaging the application does not require Blender. `npm run build:3d` rebuilds the browser bundle. The editable scene and reproducible model script are described in [the sculpture guide](docs/brand/INTERACTIVE_SCULPTURE.md).

## Personal workout sessions

Personal workouts use their own session model alongside scheduled training. Start uses a protected POST; an existing open session resumes, and legacy start links show a confirmation when a new session is needed. Save each set explicitly; returning retains saved values. Blank player fields can clear old weight/repetition values, while other API clients retain partial-update behaviour.

Optional private recordings support MP4/WebM uploads up to 8 MiB and camera capture where supported. No automated form analysis is available: the previous placeholder scoring job has been removed, and historical placeholder scores are not exposed as analysis. Camera/device and wider failure acceptance remains open in the [Version 2.0 checkpoint](docs/qa/2026-10-03-v2-prepared-checkpoint.md#personal-workout-player-follow-up).

## Schedule workout composer

`/workout` creates reusable workouts for the schedule builder, using its separate catalogue/custom-exercise model. Native forms save, edit, add and remove movements; enhanced search and custom-exercise creation preserve the current draft. Existing JSON routes remain available. Catalogue and custom movements currently save in separate groups; this model does not persist their order.

Deletion is blocked when saved training data references a workout or custom exercise. Suggestions require a configured, successful AI provider response; no fabricated fallback suggestions are supplied. See the [composer checkpoint](docs/qa/2026-10-03-v2-prepared-checkpoint.md#schedule-workout-composer-and-shared-fragments-follow-up) for verification and remaining acceptance.

## Workout display layouts

Workout display layouts are managed at `/workout-templates`. Built-in layouts can be copied; only your own layouts can be edited. The preferred layout applies appearance, spacing, transitions and optional progress/rest presentation when you open either active workout player. Logging controls and saved sets remain available. The component canvas stores an ordered preview blueprint; arbitrary component placement is not applied to the live players yet. Native save/preference forms work independently of the editor enhancement, and layouts referenced by saved template sessions are protected from deletion.

## Schedule workspace

`/schedules/builder` provides native draft commands and bounded atomic saving. It copies movements from owned saved workouts into weekly, daily or 1–14-day custom plans; catalogue movements precede custom movements. Changing the structure keeps existing rows and highlights incompatible days. Templates choose structure only. Daily plans repeat daily, continuous custom plans repeat by cycle length, weekly custom plans repeat by whole weeks, and Run once generates one cycle. Explicit custom calendar recurrence can override the interval. Existing calendar movements are retained when a reusable plan changes.

`/schedules` separates reusable plans from saved calendar date windows. Several windows can run together. Native settings forms save visibility and logging requests explicitly; hiding or restoring a window preserves occurrence IDs, completion flags and saved records. Calendar views hide matching hidden windows, while saved training history remains available. The logging flag requests a log on the day card and preserves existing completion rules.

`/schedules/{id}/entries` edits every saved cycle day, including owned custom movements. Native commands change the draft; Save commits it atomically with a revision check. Undo reverses enhanced row edits on the current page. Clear and Reset require confirmation; Reset reloads the stored plan. Editing a reusable plan retains existing calendar occurrences and session snapshots. Applying the plan remains a separate action.

`/schedules/{id}/apply` shares the calendar date planner. Preview dates submits a native read-only command, showing actual dates and new/existing movement counts. Apply recalculates and validates the stored plan, retains existing records and reports additions or an already-scheduled result on the calendar. Empty/invalid plans retain fields with feedback. New windows are visible without requiring logging; existing window settings are retained. The unused alternate chooser is archived in documentation.

JDBC session attribute creation uses a conflict-safe upsert for H2 and Spring Session's built-in PostgreSQL customiser. Parallel first-time attribute writes retain both requests' unrelated attributes instead of failing with a duplicate-key error. Session expiry, authentication and CSRF validation retain their existing rules.

Used plans cannot be deleted when referenced by calendar items, deployment windows, workout sessions or coach assignments. Native/API copies retain type, rotation, cycle and ordered entries without copying deployments. Owned and active-trainer shared plans have native inline previews. See the [schedule checkpoint](docs/qa/2026-10-03-v2-prepared-checkpoint.md#schedule-control-centre-follow-up--w073) for current verification and remaining acceptance.

## Useful Commands

Watch CSS:

```bash
npm run watch:css
```

Run tests:

```bash
./gradlew test
```

Run the automated browser simulation across public, client, trainer, gym, admin and super-admin profiles:

```bash
npm run qa:simulate
```

The simulation starts the local app when needed, exercises feature workflows, checks every configured page against page/element/design/text/accessibility criteria, and writes Markdown, JSON, JUnit and screenshot evidence under `output/playwright/site-simulation`. See [`docs/qa/SITE_SIMULATION_STANDARDS.md`](./docs/qa/SITE_SIMULATION_STANDARDS.md).

Check dependency drift:

```bash
./gradlew dependencyUpdates
npm outdated
```

## Key Paths

- Java source: [`src/main/java/uk/ac/cf/_5/group14/One_To_One`](./src/main/java/uk/ac/cf/_5/group14/One_To_One)
- templates: [`src/main/resources/templates`](./src/main/resources/templates)
- static assets: [`src/main/resources/static`](./src/main/resources/static)
- application config: [`src/main/resources`](./src/main/resources)
- deployment container: [`Dockerfile`](./Dockerfile)
- repo tooling and MCP workers: [`tools`](./tools)
- generated local artifacts and logs: [`output`](./output)

## Deployment

The repository is set up to run the application on Render, with the public hostname expected to be served through Cloudflare. See [`docs/README.md`](./docs/README.md) for the environment-variable list and deployment notes.
