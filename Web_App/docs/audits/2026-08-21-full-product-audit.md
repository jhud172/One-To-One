# One To One full product, design, usability, and engineering audit

Audit date: 21 August 2026  
App state: running locally at http://localhost:8081 with the local profile and development mode  
Scope: public site, login, sign-up choice, trainer sign-up, password recovery, trainer discovery, client dashboard, onboarding, calendar, responsive shell, accessibility signals, internationalisation, security, dependencies, tests, and production configuration  
Companion report: [Security best-practices audit](./2026-08-21-security-best-practices-report.md)

## Outcome

One To One already looks distinctive and substantially more premium than a generic fitness template. The public and authentication visual system is cohesive, typography is confident, forms are well labelled, motion has clear intent, and the recovery journey is especially strong.

The product is not ready for a production release yet for two different reasons:

1. Security has a release-blocking mobile role-provisioning flaw and missing abuse controls.
2. The signed-in mobile shell is visibly broken at 390 px: header items clip and overlap, while multiple fixed action systems compete for the same bottom-screen space and obscure task content.

Overall product health: **Amber, with a Red security gate.**

## Health scorecard

| Area | Health | Evidence-led assessment |
| --- | --- | --- |
| Brand and visual direction | Green | Distinctive dark/mint fitness identity, strong typography, restrained depth, consistent cards and actions |
| Public-site usability | Amber-Green | Clear conversion paths and no sampled overflow; homepage and directory introductions are longer than the task requires |
| Authentication and recovery design | Green | Cohesive role-aware journey, labelled fields, clear progress, excellent recovery copy and responsive layout |
| Signed-in desktop experience | Amber | Polished surfaces, but dense navigation and header crowding appear at 1280 px |
| Signed-in mobile experience | Red | Header clipping plus several fixed overlays obscure content and compete for attention |
| Accessibility | Amber-Green | Good semantic landmarks, headings, labels, names, and no sampled horizontal overflow; density, focus/overlay behaviour, and motion still need a formal WCAG pass |
| Internationalisation | Amber | Language system is extensive, but the check reports 65 remaining unlocalised strings |
| Security | Red | Public mobile gym-admin provisioning, unthrottled mobile login/AI, and incomplete reset revocation block release |
| Engineering quality | Amber-Green | 617 tests pass and production schema controls are strong; mobile security coverage and two excluded suites leave gaps |
| Dependency hygiene | Amber | Java update report is cleanly generated, but npm audit reports two fixable build-tool advisories |
| Operational readiness | Amber | Dedicated liveness/readiness probes pass; aggregate local health is 503 and production HTTPS headers still need live verification |

## What is working well

### 1. The public visual identity feels deliberate

The home, auth, directory, and recovery pages share a recognisable system instead of looking like separate templates. Mint actions, precise borders, deep green surfaces, restrained line art, and the serif accent all reinforce the brand. The home and recovery pages remain coherent when reduced from 1280 × 720 to 390 × 844.

### 2. Authentication is clearer and more trustworthy

- Login supports role selection without changing the server field contract.
- Sign-up begins with an understandable client/trainer/gym choice.
- Trainer registration exposes progress and required information.
- Recovery says “We’ll send a reset link if an account exists,” which avoids account-enumerating copy.
- Fields observed in the auth journey have accessible labels, and login exposes a polite live region for feedback.
- Primary links and actions use consistent wording and placement.

### 3. Responsive implementation avoids the most common structural failure

No horizontal overflow was detected in the sampled public home, login, trainer sign-up, trainer directory, client dashboard, calendar, or password recovery pages at 390 px. This is a meaningful positive result given the size and density of the app.

### 4. The server and tests provide a useful quality base

The full Java suite was forced to rerun: 617 tests passed with no failures, errors, or skips. CSS production compilation passed, dependency updates completed successfully, CSRF and unauthorised-route checks behaved correctly, and private media checks are thoughtfully implemented.

## Journey evidence and findings

### 1. Public home — visually strong, but too long on mobile

Health: Amber-Green

Evidence:

- Desktop and mobile hero states are visually strong and retain clear login/get-started actions.
- The sampled page has one main heading, no missing sampled image alt text, and no unnamed sampled buttons.
- The 390 px document is approximately 7,989 px tall.
- A branded entry splash was still the only visible content at about 900 ms and the full page settled after roughly 3.5 seconds in this run.

Impact:

- The long page asks a mobile visitor to process a large amount of persuasive content before reaching the end.
- The splash adds atmosphere, but repeat visitors may experience it as a delay rather than value.

Recommendation:

- Keep the premium first impression, but gate the splash to the first visit in a session, cap it near 600–700 ms, skip it for reduced-motion users, and never delay first interaction.
- Merge overlapping proof/explanation sections and move deeper detail behind progressive disclosure or dedicated About/How it works pages.
- Keep the first mobile screen focused on proposition, proof, and one primary conversion choice.

### 2. Login — polished, with vertical-priority issues

Health: Amber-Green

Evidence:

- Role selection, labels, password visibility, forgot-password access, and sign-up transition are understandable.
- At 1280 × 720, the password area reaches the bottom of the first viewport and the main login action is below the fold.
- At 390 × 844 in development mode, the development alert occupies most of the space above the actual credentials.
- The development notice is not a production-state concern, but it affects local usability and QA speed.

Impact:

- A returning desktop user cannot immediately see the primary submit action at a common laptop-height viewport.
- Local mobile QA requires unnecessary scrolling before the core task.

Recommendation:

- Reduce non-essential vertical spacing and alert height at viewports under 800 px tall.
- Keep the role selector and primary submit action inside the initial laptop viewport.
- Collapse the development notice to a one-line expandable banner on mobile.

### 3. Sign-up choice and trainer registration — coherent, but step one is heavy

Health: Amber-Green

Evidence:

- The account-type choice uses balanced cards and clear role differentiation.
- Trainer sign-up is explicitly “Step 1 of 3.”
- Step one contains six required fields.
- On the sampled 390 px screen, the Continue action is around 1,295 px from the top of a roughly 2,081 px document.
- No horizontal overflow was detected.

Impact:

- The journey looks trustworthy but asks for a substantial first commitment before the user sees the next stage.
- Validation errors near the top can be far from the Continue action on mobile.

Recommendation:

- Make the first step identity-only: name, email, and password.
- Move username/phone or professional detail to the next contextual step unless the backend truly requires them immediately.
- Add a compact mobile progress summary and a sticky, safe-area-aware Continue action only when it does not cover validation content.
- Preserve entered values and focus the first invalid field with an inline summary.

### 4. Password recovery — the benchmark auth screen

Health: Green visually; Amber for backend security

Evidence:

- The page explains Request → Verify → Secure without clutter.
- The form label, textbox, submit button, and return-to-login path are clear in the accessibility tree.
- The mobile layout retains hierarchy and no horizontal overflow.
- The visual and written treatment is consistent with login and sign-up.

Recommendation:

- Use this screen as the compositional benchmark for the rest of auth.
- Keep the design; harden the backend by hashing tokens, throttling requests, equalising timing, and revoking all active sessions after success.

### 5. Trainer directory — discovery starts too late

Health: Amber

Evidence:

- The desktop first viewport is dominated by the directory hero; filters and trainer cards appear only after scrolling.
- On mobile, the filter region begins around 722 px and the first trainer around 1,017 px.
- The results state itself is polished and readable once reached.

Impact:

- Visitors arriving with the explicit task “find a trainer” do not see a trainer or useful filter in their first viewport.
- The page prioritises explanation over task completion.

Recommendation:

- Compress the hero to a results header of roughly 240–320 px on desktop and 180–240 px on mobile.
- Put location/specialism search and result count immediately below the title.
- Show at least one trainer card in the first desktop viewport and the top of the first card in the first mobile viewport.
- Make filters a mobile sheet with persistent active-filter count rather than a long pre-results block.

### 6. First client dashboard visit — onboarding takes control away

Health: Amber

Evidence:

- The first signed-in visit opened a seven-step tour.
- The tour auto-scrolled away from the page top to Action Hub and dimmed the interface.
- The tour bubble and hand-drawn spotlight dominate the screen before the user establishes where they are.

Impact:

- A new user has to learn the tour before learning the product.
- Automatic displacement harms orientation and can make keyboard/screen-reader focus harder to predict.

Recommendation:

- Replace the seven-step forced walkthrough with a three-step, user-invoked orientation:
  1. What needs attention today.
  2. Where the plan/calendar lives.
  3. Where to contact the coach.
- Do not auto-scroll until the user chooses Next.
- Keep Skip and “Don’t show again” always visible.
- Move advanced features into contextual first-use nudges.

### 7. Client dashboard after onboarding — too many simultaneous priorities

Health: Amber on desktop, Red on mobile

Evidence:

- Desktop document height is about 4,879 px and the main area exposes roughly 35 links.
- Mobile document height is about 6,546 px.
- Action Hub begins around 1,212 px on mobile, so the named action centre is not the immediate action centre.
- The desktop page combines coaching focus, help/trust, action cards, timeline, platform navigation, quick actions, and chat.

Impact:

- The product communicates breadth but weakens “what should I do next?”
- Duplicate navigation systems increase cognitive load and maintenance burden.

Recommendation:

- Rebuild the top of the dashboard around one Today card:
  - next workout/task,
  - status,
  - one primary action,
  - one secondary coach action.
- Move Help and trust into support/settings.
- Collapse low-frequency progress modules by default.
- Keep one global navigation model and one contextual action model.

### 8. Signed-in mobile shell — release-blocking layout collision

Health: Red

Evidence:

- At 390 × 844, the development badge is clipped off the left edge.
- Logo, account card, language control, and menu compete inside the header; elements visually overlap/crop.
- The dashboard has approximately 12 fixed-position elements in the inspected state.
- Chat, quick actions, and the platform panel occupy the lower portion of the viewport at the same time.
- Calendar shows the same clipped header and competing fixed controls.
- There is no horizontal document overflow, so the defect is visual clipping/stacking rather than a simple oversized body.

Impact:

- Content is obscured and the user’s tap targets compete for the same space.
- The shell makes every signed-in mobile feature feel less stable, regardless of the page content quality.
- Fixed overlays can trap focus or cover validation/toast messages.

Recommendation:

- Treat this as the first design implementation task.
- Create a dedicated mobile header:
  - logo mark only,
  - optional compact environment dot/badge,
  - avatar/menu trigger,
  - move language selection inside the menu.
- At widths below 768 px, retain only one bottom navigation surface.
- Merge quick actions into the selected bottom-navigation item or a single central action button.
- Reposition chat above the navigation only when open/needed; otherwise expose it as a nav destination.
- Add safe-area padding and a shared overlay z-index contract.
- Test closed/open states at 320, 360, 390, 430, 768, 1024, and 1280 px.

### 9. Desktop signed-in header — crowding starts too early

Health: Amber

Evidence:

- At 1280 px, the brand and Dashboard label visibly run together on the calendar.
- Navigation, development badge, language, and the profile card consume nearly the full width.

Recommendation:

- Add an intermediate desktop/tablet breakpoint before full mobile collapse.
- Shorten or iconise secondary utilities first.
- Set explicit minimum gaps and prevent the logo/primary-navigation clusters from shrinking into each other.

### 10. Calendar — capable, but control density is high

Health: Amber

Evidence:

- The page exposes month/week/day, layout help, schedules, heatmap legend, today/date/task jump controls, previous/next week, chat, quick actions, and platform navigation.
- The semantic tree provides a useful H1 and labels for date/task inputs.
- The mobile first viewport is mostly controls; the actual week cards begin near the fixed bottom layer.

Impact:

- Advanced capability is visible, but the routine task “what is today?” competes with navigation and power tools.

Recommendation:

- Make Today and the current date/week the primary mobile surface.
- Move jump-to-date, jump-to-task, detailed/grouped layout, and heatmap legend into one Filters/Tools sheet.
- Preserve direct week navigation and expose advanced controls only on demand.

## Accessibility and inclusive-design audit

### Confirmed positives

- Representative pages use banner, navigation, main, contentinfo, region, heading, list, group, dialog, status, and tooltip semantics.
- Sampled form controls have accessible names.
- Sampled public images did not show missing alt text.
- Sampled buttons did not show missing accessible names.
- No horizontal overflow was detected at 390 px across the audited journeys.
- The CSS includes reduced-motion handling in the public experience.

### Remaining risks

1. The forced tour and automatic scrolling need keyboard, focus-order, Escape, and screen-reader verification.
2. Multiple simultaneous fixed panels need focus containment and obscured-content checks.
3. Muted small copy over translucent dark/light surfaces needs automated and manual contrast measurement.
4. Custom tooltips, selectors, platform panels, and language controls need keyboard-only state testing.
5. Every animation should preserve meaning when reduced motion is active; splash delays should be removed rather than merely de-animated.
6. The full release should be tested against WCAG 2.2 AA, including 200% zoom, 400% reflow, focus not obscured, target size, and error identification.

## Internationalisation audit

npm run i18n:check failed with 65 unlocalised strings:

- birthday/mission-vi.html: 63
- public-views/auth/signup-client.html: 1
- public-views/auth/signup-gym.html: 1
- public-views/auth/signup-trainer.html: 1

The auth count is small but important because the app presents language choice prominently. Resolve the three auth strings before calling the journey complete in every supported language. The birthday page should be treated as a separate content-localisation task.

## Performance and motion audit

What was verified:

- CSS production compilation succeeds.
- Static resources use versioned URLs and one-day caching configuration.
- Text compression is enabled.
- Public pages visually settle without layout overflow in the sampled states.

Risks to profile:

- The mobile entry splash delays usable content in the observed run.
- Very long public/dashboard documents increase style/layout work and interaction scanning cost.
- Numerous fixed/translucent/blurred layers may increase mobile compositing cost.
- Multiple navigation/action systems add JavaScript listeners and state synchronisation.
- Third-party Quill and Sortable assets add network and supply-chain cost.

Plan:

- Capture production Lighthouse/Web Vitals for home, login, explore, dashboard, and calendar.
- Set targets of LCP under 2.5 s, INP under 200 ms, CLS under 0.1 at the 75th percentile.
- Profile low-end mobile with reduced motion and 4× CPU slowdown.
- Remove non-essential above-the-fold effects before reducing visual quality elsewhere.

## Engineering and operational audit

### Passed

- Full Java rerun: 617 tests, 0 failures, 0 errors, 0 skipped.
- CSS production build.
- Gradle dependency update report.
- git diff --check; only existing line-ending warnings were reported.
- Local root page and explicit liveness/readiness probes.

### Failed or incomplete

- npm audit: 1 high and 1 moderate build-tool advisory; fixes available.
- i18n check: 65 unlocalised strings.
- The broad standards simulation did not complete inside the bounded audit run and its partial output was not used as completion evidence.
- The default Gradle test task excludes UserRepositoryTest and UserPreferenceFullContainerMockTests.
- No dedicated MobileApi/MobileAuth security tests were found.
- Local aggregate /actuator/health returned 503 while liveness/readiness each returned 200.

## Security summary

Security release status: Red.

Release-blocking findings:

1. Public mobile gym sign-up directly provisions GYM_ADMIN and can reach application approval operations.
2. Password reset does not revoke active sessions or mobile tokens.
3. Mobile login bypasses the existing web throttle.
4. Public provider-backed AI has no endpoint-specific quota.

Important hardening:

- Fail closed if development mode is enabled in a production profile.
- Hash reset tokens at rest and rate-limit recovery.
- Replace the confirmed user-derived innerHTML sink and introduce CSP through report-only mode.
- Self-host or add integrity metadata to third-party scripts.
- Move mobile token DDL into Flyway.

Full evidence and source locations are in the [security report](./2026-08-21-security-best-practices-report.md).

## Prioritised implementation plan

### Phase 0 — Security release gate

Objective: remove direct privilege escalation and close account-abuse paths.

Work:

1. Reuse the gym application service from mobile; never create GYM_ADMIN from public sign-up.
2. Protect mobile administrative operations with shared method-level authorisation.
3. Introduce shared throttling for web/mobile login and password recovery.
4. Revoke all sessions and mobile tokens on password reset.
5. Quota or authenticate provider-backed public AI.
6. Add a complete mobile role/ownership integration-test matrix.

Done when:

- All negative role tests return 401/403.
- Public gym sign-up creates only an application.
- Old tokens fail immediately after reset.
- Abuse tests demonstrate bounded requests.
- All 617 existing tests plus the new security tests pass.

### Phase 1 — Mobile shell rescue

Objective: make every signed-in mobile page stable before redesigning individual modules.

Work:

1. Build a compact mobile header and intermediate 768–1280 px breakpoint.
2. Consolidate platform navigation, quick actions, and chat into one safe-area-aware system.
3. Define one overlay/focus/z-index contract.
4. Add visual regression fixtures for all open/closed states.

Done when:

- No clipping or overlap at 320–430 px.
- Main content is never covered by the persistent navigation.
- Only one primary fixed action/navigation layer is visible when panels are closed.
- Keyboard focus is never obscured.

### Phase 2 — Task-first dashboard, calendar, and discovery

Objective: surface the user’s next task before explanation or advanced controls.

Work:

1. Dashboard: one Today card and one primary action above the fold.
2. Directory: compact hero, immediate search/results, mobile filter sheet.
3. Calendar: default to today/current period; place advanced jump/layout tools in a sheet.
4. Reduce duplicate navigation and relocate Help and trust.

Done when:

- A client can identify and start the next workout in one screen and two interactions.
- A visitor sees a trainer/result in the first desktop viewport and the first card edge on mobile.
- Calendar content appears in the first mobile viewport.

### Phase 3 — Onboarding and authentication refinement

Objective: preserve the premium feel while reducing friction.

Work:

1. Replace the seven-step forced tour with three opt-in orientation steps plus contextual first-use hints.
2. Cap/skip the public splash and honour reduced motion.
3. Keep login submit visible at 720 px height.
4. Reduce first-step trainer registration fields and improve mobile error focus.
5. Localise the remaining auth strings.

Done when:

- First dashboard visit does not auto-scroll without consent.
- Repeat visitors are not delayed by the splash.
- Login’s primary action is visible at 1280 × 720.
- Auth journeys pass keyboard-only, 200% zoom, and mobile validation tests.

### Phase 4 — Hardening, performance, and release evidence

Objective: produce evidence suitable for a production go/no-go decision.

Work:

1. CSP report-only rollout, remediation, then enforcement.
2. Dependency patch lane and npm audit clean-up.
3. WCAG 2.2 AA audit and contrast/focus fixes.
4. Web Vitals/Lighthouse budget and low-end-mobile profiling.
5. Render-profile startup, Flyway validation, HTTPS header verification, and full database-backed test job.

Done when:

- No high/critical dependency findings.
- CSP is enforced without known violations.
- WCAG 2.2 AA acceptance checks pass.
- Production Web Vitals meet the stated targets.
- The release report contains live HTTPS, database, migration, and backup/restore evidence.

## Screenshot evidence

All screenshots were captured from the running app in this audit:

- [Public home desktop](../../output/playwright/audit-2026-08-21/01-public-home-desktop.png)
- [Login desktop](../../output/playwright/audit-2026-08-21/02-login-desktop.png)
- [Sign-up choice desktop](../../output/playwright/audit-2026-08-21/03-signup-choice-desktop.png)
- [Trainer sign-up desktop](../../output/playwright/audit-2026-08-21/04-trainer-signup-desktop.png)
- [Trainer directory entry desktop](../../output/playwright/audit-2026-08-21/05-explore-desktop.png)
- [Trainer directory results desktop](../../output/playwright/audit-2026-08-21/06-explore-results-desktop.png)
- [Mobile entry splash](../../output/playwright/audit-2026-08-21/07a-mobile-entry-splash.png)
- [Public home mobile](../../output/playwright/audit-2026-08-21/07-public-home-mobile.png)
- [Login mobile](../../output/playwright/audit-2026-08-21/08-login-mobile.png)
- [Trainer sign-up mobile](../../output/playwright/audit-2026-08-21/09-trainer-signup-mobile.png)
- [Trainer directory mobile](../../output/playwright/audit-2026-08-21/10-explore-mobile.png)
- [First dashboard tour](../../output/playwright/audit-2026-08-21/11-client-dashboard-desktop.png)
- [Settled dashboard desktop](../../output/playwright/audit-2026-08-21/12-client-dashboard-settled-desktop.png)
- [Dashboard mobile shell collision](../../output/playwright/audit-2026-08-21/13-client-dashboard-mobile.png)
- [Calendar desktop](../../output/playwright/audit-2026-08-21/14-calendar-desktop.png)
- [Calendar mobile shell collision](../../output/playwright/audit-2026-08-21/15-calendar-mobile.png)
- [Password recovery desktop](../../output/playwright/audit-2026-08-21/16-forgot-password-desktop.png)
- [Password recovery mobile](../../output/playwright/audit-2026-08-21/17-forgot-password-mobile.png)

## Commands and checks run

- .\gradlew.bat bootRun --args=--spring.profiles.active=local --console=plain
- .\gradlew.bat test --rerun-tasks --console=plain
- .\gradlew.bat dependencyUpdates --console=plain
- npm run build:css
- npm audit --audit-level=low --json
- npm run i18n:check
- npm run qa:simulate:standards (bounded run; incomplete and not used as pass evidence)
- git diff --check
- Local route/header/CSRF/CORS/ownership checks
- Desktop and mobile browser walkthroughs of the listed journeys

## Final recommendation

Do not spend the next implementation cycle adding more visual effects. The current visual direction is already strong. The highest-value work is:

1. close the mobile security boundary;
2. fix the shared signed-in mobile shell;
3. make dashboard, directory, and calendar task-first;
4. then refine onboarding, motion, and polish.

That sequence preserves the brand work while turning it into a product that feels secure, calm, and intentional under real use.
