# One To One: client journey and section audit

Date: 8 September 2026. Target: local app at http://localhost:8081. Evidence: this run's browser interactions, saved screenshots, DOM observations and server logs. This is a focused client-journey audit, not a production release assessment.

## Verdict

The app starts successfully and has a distinctive visual identity. Client authentication, sign-up step validation and saving workout data work. The highest priority is a broken workout completion screen, followed by overlapping navigation and misleading coaching actions. The dashboard needs a clearer order of importance, particularly on phones.

The application implementation was not edited. This report and local evidence were created. The local demo workout was opened, one set was saved as 40 kg × 5 reps, and that set was marked done. The dashboard subsequently showed 1/1 completed workouts. No real account was created, and no payment, trainer request, message or external submission was made.

## Startup and verification

- `npm run build:css`: passed; generated assets produced no tracked diff.
- Hidden background `gradlew.bat bootRun --args=--spring.profiles.active=local`: started successfully.
- Java: Temurin 21.0.11. Available Node: 24.15.0, whereas package.json specifies 22.22.x. CSS compilation passed, but the runtime mismatch should be removed for reproducible development.
- Port 8081 listener: Java PID 36548 at verification time.
- Homepage: HTTP 200.
- Log: local profile active, Tomcat started on 8081, application started in 13.66 seconds. Startup stderr was empty.
- Development mode was visibly active. Findings involving its banner are confirmed for this local configuration; production visibility was not tested.
- Viewports: 1280×720 desktop, 1024×768 tablet, 390×844 phone. Temporary viewport overrides were reset afterwards.
- No Java test suite or full automated site simulation was run: this pass exercised the selected journeys interactively and traced failures in source/logs.

## Journey steps and health

| Step | Journey or screen | Health and observed outcome | Evidence |
|---|---|---|---|
| 1 | Homepage → Join as a client | Works, but sends a client-specific choice to the generic account chooser | 01-home.png |
| 2 | Account chooser → Client | Works; clear roles, unnecessary for the client-specific entry route | 02-account-choice.png |
| 3 | Client sign-up identity | Empty Continue is blocked; visible alert and focus on email field | 03-signup-validation.png |
| 4 | Client sign-up security | Advances with synthetic identity input; step heading receives focus; no account submitted | 04-signup-security.png |
| 5 | Client login | Local demo credentials reach dashboard | 05-login.png |
| 6 | First dashboard visit and tour | Automatic seven-step tour; dismissal eventually returns to dashboard; full tour not audited | 06-dashboard.png |
| 7 | Normal dashboard, phone and tablet | Content works; header collisions and excessive navigation/summary space | 08, 11, 12, 20 |
| 8 | Open trainer plan → My Trainer | No plan displayed; only active relationship and disabled trainer requests | 09, 10 |
| 9 | Dashboard → scheduled workout → Save set | 40 kg × 5 reps persists after reload; volume becomes 200 kg | 13, 14 |
| 10 | Mark final set done → completion | Blocked: completion template fails; later dashboard shows completion recorded | workout-completion-error.txt |
| 11 | Explore → directory results | Cards, prices and filters appear after scrolling; excessive introductory space | 16, 17 |
| 12 | Trainer profile → Start Training | Link points to /signup, which redirects signed-in client to dashboard, losing trainer context | 18, 19 |

The numbered screenshot files are in `Web_App/output/ux-audit-2026-09-08/`. Screenshot 07 was rejected as evidence of the normal planner because the tour obscured it. The failed completion page could not be screenshotted because the browser tool refused its generated error-page URL; the server exception was preserved instead. Do not treat missing screenshot 15 as an accepted capture.

## Prioritised changes

### 1. High: fix the workout completion template

Reproduction: sign in as the demo client, open today's Client Premium Plan, save the existing set, and mark it done. Completing the final set redirects to `/workout-session/39/complete` in this run. The browser reports an incomplete response and the server reports a Thymeleaf parsing exception.

Cause confirmed in `src/main/resources/templates/shared-views/workout-session/complete.html:69`: the conditional class expression closes `${exercise.completed}` before the ternary, then has an unmatched closing brace after the final branch. Use one balanced expression around the entire condition, or the supported outer conditional syntax consistently.

Acceptance: render completion summaries with completed and incomplete exercise statuses; exercise a full session through the browser; confirm the summary, calendar and dashboard agree after reload. The persisted completion and failed confirmation must be tested separately. Add a template-rendering regression check, since testing a controller's returned view name alone would miss this defect.

### 2. High: repair header reflow

At 1280 px and 1024 px, the navigation collides with the brand and development badge. At 390 px, the badge crosses the logo and the menu control occupies the profile-card area. Page-level scroll-width checks pass, so a horizontal-overflow assertion alone misses the issue.

Files: `templates/universal-fragments/layout/navbar.html`, `static/css/components/core/navbar.css`, and related profile-card styling. Inspect the combined width of brand, links, language selector, development badge and account controls. Collapse navigation before these collide; move the development indicator into the compact menu on narrow screens. Verify with development mode both on and off, long names and all role variants.

Acceptance: no overlapping visible bounds at 390, 768, 1024 and 1280 px; menu and profile buttons independently reachable by pointer and keyboard; focus visible when the menu is open.

### 3. High: make trainer actions reflect account and relationship state

Two confirmed handoff problems:

- Dashboard “Open trainer plan” and “Manage coaching” both lead to `/client/trainers` when there are no assigned plans. The destination displays no plan or explanation that one has not been assigned.
- Public trainer-profile “Start Training” always links to `/signup`. For the already authenticated client it returned to the dashboard instead of continuing with that trainer.

Files: `Dashboard/DashboardController.java:671` and `:1015`, `templates/client-views/dashboard/fragments/client-dashboard-shell.html`, and `templates/public-views/public/profile.html:41`.

Acceptance: assigned plans open the actual plan; no-plan state says “Your trainer has not shared a plan yet” with an appropriate message-coach action. Guest registration preserves the chosen trainer. Existing clients see actions appropriate to no trainer, pending request, this active trainer, or another active trainer. Preserve the one-active-trainer rule.

### 4. Medium: explain disabled requests consistently

On My Trainer, both Request buttons are disabled because the demo client already has an active coach, but there is no explanation beside them. On Explore, Request trainer remains enabled for the same signed-in client. No request was submitted, so backend enforcement is not assessed here.

Files: `templates/client-views/client/trainers.html:98`, parallel `my-trainer.html`, and `templates/client-views/explore/index.html`.

Acceptance: show the current relationship, explain why another request is unavailable and offer the supported management path. Use the same state presentation in the directory and profile. Do not merely enable the disabled controls.

### 5. Medium: bring today's task above the coaching summary

At 390×844, Coaching focus occupies about 1008 CSS px and the Action hub starts at document y≈1158. Users see account/relationship facts and multiple navigation bars before the task the dashboard claims to prioritise. The phone also shows crowded relationship statistic labels.

Files: `templates/client-views/dashboard/fragments/client-dashboard-shell.html:252` and `:407`, `static/css/components/dashboard/client-dashboard-refresh.css`, and the platform-panel styles.

Recommendation: first show today's workout/task and a single primary action; then a compact coach/message summary; then weekly progress. Keep the full relationship details behind a deliberate expansion or their existing panel. Consolidate the stacked quick-panel and platform-navigation rows on phones.

Acceptance: the next training action is visible in the first phone viewport; text does not collide; fixed controls leave usable reading and input space; keyboard focus is not obscured.

### 6. Medium: repair light-theme notice contrast

The payment notice on My Trainer uses pale amber text over a pale translucent amber surface. Computed heading colour was `rgb(254, 243, 199)` and the notice background was `rgba(251, 191, 36, 0.1)`. It is visibly hard to read in the captured light theme.

Files: `templates/client-views/client/trainers.html:47` and `:52`, plus the equivalent notice in `my-trainer.html`. Use dark foreground tokens in light mode and separate dark-mode tokens; also inspect the status badge and search-field boundary.

Acceptance: measure the composited foreground/background contrast, including links and helper text. Normal text should meet 4.5:1 under [WCAG 2.2](https://www.w3.org/TR/WCAG22/#contrast-minimum). No full accessibility conformance claim is made by this audit.

### 7. Medium: preserve the client choice on homepage entry

The main, role-panel and final client CTAs all point to `/signup` rather than `/signup/client`.

File: `templates/public-views/home/public.html:36`, `:399`, `:468`.

Acceptance: each client-specific CTA opens client registration directly. Keep the generic account chooser for genuinely generic “Get started” entry points.

### 8. Medium: move trainer discovery closer to its task

The directory's first desktop viewport is dominated by the heading and decoration; the filters and result cards require a substantial scroll. Its most prominent initial actions are Back to home and Open inbox, neither of which helps compare trainers.

Files: `templates/client-views/explore/index.html` and the existing public/explore styles.

Recommendation: use a compact heading, show search and result count immediately, and keep price, specialisms, verification, location and profile actions together. On phone, offer an explicit filter control and prioritise the results. This is a hierarchy change within the existing brand, not a new design system.

### 9. Low: tighten state feedback and copy

Examples observed: “1 exercises”, “1 tasks”, “1 workouts” and “1 days”; workout Save set and Mark done are separate controls without much explanation; final-set completion navigates automatically although Finish workout is also presented.

Recommendation: plural-aware messages and a clear distinction between saving a log and completing it. Decide whether final-set completion should finish the whole session automatically; ensure the copy and confirmation match that behaviour. Keep weight units visible near the field.

## Recommended implementation sequence

1. Repair completion rendering and add meaningful render coverage.
2. Fix the shared header and phone navigation layout.
3. Correct plan/profile destinations and relationship-state guidance together.
4. Repair notice contrast and direct client CTAs.
5. Simplify the dashboard and directory hierarchy; validate on phone before wider rollout.

## Evidence gallery

The captures below are current-run evidence. Image paths point to the local output folder in this checkout.

![Homepage](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/01-home.png)
![Account choice](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/02-account-choice.png)
![Sign-up validation](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/03-signup-validation.png)
![Sign-up security step](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/04-signup-security.png)
![Client login](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/05-login.png)
![First-visit tour](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/06-dashboard.png)
![Normal dashboard](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/08-dashboard-ready.png)
![Trainer connection destination](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/09-coaching.png)
![Trainer connection on phone](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/10-coaching-mobile.png)
![Dashboard on phone](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/11-dashboard-mobile.png)
![Phone navigation menu](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/12-mobile-menu.png)
![Workout entry](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/13-workout.png)
![Saved workout set](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/14-workout-saved.png)
![Directory introduction](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/16-explore.png)
![Directory results](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/17-directory-results.png)
![Trainer profile](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/18-trainer-profile.png)
![Profile CTA returns signed-in client to dashboard](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/19-profile-signup-loop.png)
![Tablet dashboard and header overlap](G:/No OneDrive Work/My Website/Crystal-Powers-OneToOne/One To One/One-To-One/Web_App/output/ux-audit-2026-09-08/20-dashboard-tablet.png)

## Limits

The audit used the local development configuration and a seeded premium client with an existing trainer. It did not validate a real new-client onboarding completion, trainer/gym/admin workflows, email/SMS delivery, paid booking, payment providers, screen-reader behaviour, reduced motion, production state or every page/theme. Keyboard evidence is limited to observed focus movement and the mobile menu's opening behaviour. Trainer discovery was viewed, but filters and request submission were not exercised.

Use [W3C reflow guidance](https://www.w3.org/WAI/WCAG22/Understanding/reflow.html) for follow-up responsive acceptance. Passing a scroll-width check does not prove that controls remain unobscured. Screenshot findings should be paired with keyboard and assistive-technology checks after implementation.
