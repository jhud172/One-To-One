# One To One 2.0 — design, workflow and usability audit

**Owner:** James · **Audit date:** 1 October 2026 · **Target:** a major Version 2.0 release · **State:** implementation in progress; individual acceptance remains tracked below.

**Active rebuild:** the full 2.0 goal is underway. The [implementation record](../qa/2026-10-01-v2-implementation.md) records shared-system changes and verification as each slice completes. Original evidence below remains the starting-state audit; open page acceptance boxes are not implied complete by global styling changes.

This is the working checklist for rebuilding One To One as a premium futuristic coaching platform. Every current web template is accounted for individually, including shared fragments, alternate/legacy screens, errors and development pages. Native Android screens and embedded settings flows are included separately. A checked box means its stated work and evidence are complete; looking better alone does not complete a page.

**Stabilisation update, 1 October 2026:** the subsequent [prepared-baseline report](../qa/2026-10-01-prepared-baseline.md) records fixes for preferences rendering, partial error responses, doctype handling, login/tour navigation, responsive header/dashboard/calendar defects, contrast and mobile API/application workflow problems. The original findings and screenshots below describe the starting state. The full 2.0 redesign tasks remain unchecked; consult the baseline report for current fix status and verification limits.

## Start here

- [Product understanding and scope](#product-understanding-and-scope)
- [Current evidence and highest-impact findings](#current-evidence-and-highest-impact-findings)
- [Proposed 2.0 design direction](#proposed-20-design-direction)
- [Workflow changes across pages](#workflow-changes-across-pages)
- [3D and animation production brief](#3d-and-animation-production-brief)
- [Shared acceptance standard](#shared-acceptance-standard)
- [Implementation order](#implementation-order)
- [Every web page and shared component](#every-web-page-and-shared-component)
- [Embedded flows that need their own checks](#embedded-flows-that-need-their-own-checks)
- [Android page-by-page checklist](#android-page-by-page-checklist)
- [Verification record and release gates](#verification-record-and-release-gates)

## Product understanding and scope

One To One connects clients to verified personal trainers and gyms, then supports the actual coaching relationship: programmes, schedules, workout execution, messages, goals, check-ins and progress. Trainers need a useful client-management workspace; gyms need trainer and membership operations; platform staff need support, application and verification queues. Tracking features should support training and coaching, not become a separate generic fitness product.

The current web app uses Java 21, Spring Boot, Spring Security and server-rendered Thymeleaf. CSS uses Tailwind/PostCSS with route bundles. Browser behaviour is in external JavaScript. The homepage already imports a real Blender-created GLB using Three.js. Preserve the existing backend architecture, role protection, CSRF, validation, routing and data contracts while replacing the visual and interaction layer. Necessary new features must have explicitly defined backend behaviour, rather than simulated controls presented as finished functionality.

Current repository inventory: **170 HTML templates** and **74 Java files named `*Controller.java`**. That second count includes API controllers and a commented-out legacy LoginController; it is not a count of 74 active page routes. Fragments are included in the 170 and are not separate public pages. One URL can render different templates by role or state. The page list records direct render references where identifiable and flags private-helper, legacy and error-dispatch cases instead of inventing URLs.

**Rebuild inventory update:** the starting inventory above remains the audit baseline. A new shared `schedule-drawer.html` now serves both existing month/week compatibility fragments (W060/W061). Together with the shared confirmation-dialog fragment, assigned-schedule viewer (W171), private assessment history (W172) and previously missing review moderation page (W173), the current HTML source count was 175. The gym connection workspace (W174), private affiliation history (W175) and trainer professional-review workspace (W176) bring it to **178**. The shared private qualification-document/timeline fragment now brings the current count to **179**. The 176 individual W entries and three additional shared components account for that source inventory. W077 retirement subsequently removes one dormant template from runtime resources; the archived source remains documented. Shared components add no route; new pages have their own checklists.

The Android app is more than a WebView: MainActivity launches the Compose TrainerHubApp, with public and authenticated native screens backed by a mobile repository/API. A separate MobileWebsiteApp WebView implementation and older local trainer-management screen files also exist. Their actual runtime reachability needs verification before migration or removal. Web mobile screenshots do not prove native Android behaviour.

James's requested version is **2.0**, not an incremental 1.1 polish pass. At the initial inspection the Gradle app version was `0.0.1-SNAPSHOT` and Android versionName was `1.0`. The 2 October prepared checkpoint updates these to `2.0.0-SNAPSHOT` and `2.0-preview`; the CSS cache remains a date-based identifier. These are different mechanisms; the dated implementation log records the preview metadata updates. Production release naming still requires the actual release acceptance gates.

The checkout already contained substantial uncommitted changes. They were preserved. The original audit added the checklist and evidence. The subsequent authorised rebuild is recorded in the implementation log and page milestones below.

## Current evidence and highest-impact findings

Evidence comes from this audit run on the local H2 application at port 8081, using the app browser and synthetic seeded demo data. No production account, real payment, real review decision or outbound client communication was used. Some public screens show a development-mode notice; that is a local environment observation, not proof of production exposure.

**Coverage levels:** `V` = current screenshot inspected plus DOM/page state checked; `S` = source structure and controls inventoried, with recommendations that still require runtime review; `B` = runtime attempt blocked, named below; `L` = legacy/alternate use needs confirmation; `R` = retired after caller/reference verification, with an archive and preserved live replacement. `V` is not a claim that every control, responsive size or backend state passed. The remaining visual and behavioural checks stay unchecked per page.

| Finding | Evidence | Impact and required change |
| --- | --- | --- |
| P0: preferences do not render | Browser attempted `/select-preferences`; server reports `#fields.hasAnyErrors()` outside adequate form context at select-preferences.html line 33 | Fix the binding context and add render/invalid-submit coverage before this flow is signed off. |
| P0: error recovery cascades after that failure | Server reports error dispatch after the template exception and `Cannot create a session after the response has been committed`; browser reports incomplete chunked response | Make error recovery independent of failing page/session assumptions; verify the entire exception path. The final cause of every cascade has not been isolated. |
| P0: header collisions | Desktop client screenshot 15 and mobile screenshot 26 | Logo overlaps navigation on desktop; mobile development marker/profile/menu crowd the logo. Document overflow was false, showing why that check alone is insufficient. |
| P1: open 3D story loses readability | Screenshot 02 | Pale heading and story text sit over very bright artwork. Give the story its own dependable contrast and measure it in both themes. |
| P1: the product has several visual identities | Screenshots 01, 03, 14 and 15 | Cream/green homepage, dark green guest pages and pale utility workspaces need one coherent 2.0 visual language. |
| P1: daily actions lose hierarchy | Screenshots 15, 19 and 20 | Status, help, filters and nested panels compete with today's workout or check-in. Prioritise the next useful action. |
| P1: mobile calendar hides the week below tools | Screenshot 27 | Several control rows consume nearly the whole first screen. Use a compact date/view toolbar and an agenda-first narrow layout. |
| P1: several persistent controls compete | Screenshots 26 and 27 | Bottom shortcuts, an additional icon row and floating actions occupy valuable space. Design one navigational hierarchy and prove it does not hide content/focus. |
| P1: first-use tour intercepts normal work | Screenshot 15a and captured DOM | Calendar navigation returned to the dashboard while the tour was active; Skip released it. Make learning optional and non-blocking. |
| P1: profile and relationship text wash out | Screenshots 18 and 24 | Very pale biography, status and restriction text is difficult to read against light surfaces. Fix actual component/theme contrast. |
| P1: utility pages inherit large marketing introductions | Screenshots 05, 06 and 13 | FAQ answers, directory filters and support form appear below large heroes. Put the task where the user can reach it quickly. |
| P0 for release: terms remain unpublished | Screenshot 12; subscription-terms source | Terms and subscription terms show Awaiting review. Complete reviewed content and test the links from signup/checkout before release. |
| P1: duplication complicates the experience | Current controller/template references | Legacy messages redirect to inbox; /chatv2 redirects to /chat; two plan models and multiple workout/template surfaces remain. Reconcile vocabulary and navigation, retaining IDs, records and compatibility. |

Strengths to build on: clear verified-coaching positioning, working client demo login, meaningful role signup stages, seeded coaching relationship and session history, reusable fragments, separated CSS/JS, route stylesheet bundles, motion/layer tokens and an actual editable 3D asset. The inventory found no inline style attributes/style blocks or inline JavaScript code blocks in the 170 templates using a structural scan; this is a useful baseline rather than a full parser/security certification.

### Numbered browser walkthrough

The screenshot records below are linked to page entries and displayed in the evidence gallery later in this file. Captures showing early reveal animations were replaced with settled captures before being accepted. The open sculpture capture was taken after the opening settled and scrolled to show the expanded journey. The tour is intentionally recorded as a blocking state. The preferences error did not yield an accepted product screenshot; it is `B`, with a named log finding.

Trainer demo credential entry was attempted, but a successful trainer session was not established during this audit. Do not infer a diagnosed authentication defect from that attempt. Trainer, gym, admin and super-admin page proposals therefore remain source-based and require authorised local role sessions for their visual pass. No protected-page screenshot is substituted with a screenshot of the login screen.

## Proposed 2.0 design direction

**Working direction: Connected Performance.** Keep the One To One name, the recognisable connection motif and blue/orange signature. Replace the current page styling as a coordinated new system. This is a proposed direction to review during the first implementation slice, not an approved mock-up or a completed branding exercise.

| Area | Proposed 2.0 treatment |
| --- | --- |
| Atmosphere | Midnight navy background, controlled cyan illumination and warm orange connection accents; spatial depth around a small number of meaningful objects. |
| Starting palette | Background `#060914`, surface `#101827`, raised surface `#172339`, primary text `#F4F8FF`, secondary text `#B7C5D9`, cyan `#49DAFF`, orange `#FF9546`, secondary violet `#9A91FF`. These are art-direction starting points, not tested contrast tokens. |
| Accessible light mode | A fully designed pale surface system with dark readable text, matching hierarchy and equivalent states; no reuse of pale dark-theme labels over white. |
| Typography | Crisp contemporary sans serif, restrained geometric headings and legible body text. Avoid mixing the current decorative serif emphasis with a technical dashboard identity unless deliberately selected in visual review. Reuse available fonts before introducing another network dependency. |
| Surfaces | Fewer, more deliberate panels with consistent spacing/radii; solid readable form surfaces; selective translucency for navigation/overlays with tested fallbacks. |
| Public experience | Strong visual storytelling and a memorable opening model, followed by real trainer discovery, proof and short relevant demos. |
| Client experience | Today, coach and progress as the main organising ideas. A useful first screen, touch-friendly session controls and calm focus mode. |
| Trainer experience | A professional coaching workspace organised around client needs, programme work and communication. |
| Gym/admin experience | Clear queues, evidence and operational actions in the same visual family; higher density where it makes review faster. |
| Motion | Selection, hierarchy, progress and spatial transitions explain changes. Decorative movement is optional, bounded and paused out of view. |
| Media | Real trainer/gym/exercise/product images and authored assets with licences. Mark sample people/data explicitly. No invented testimonials, qualifications or metrics. |

### Shared design work

- [ ] D01 Create and review a visual target for homepage closed/open, client dashboard, trainer client workspace, mobile calendar and session player before rolling the system across all pages.
- [x] D02 Establish colour, type, spacing, radii, shadows, motion, focus and layer tokens with measured light/dark contrast. Semantic web tokens and corresponding native palette implemented; 20 web text/control pairs measured. Existing named motion/layer tokens retained. Evidence and remaining page-level checks are in the implementation record.
- [ ] D03 Rework base, navbar, profile control, shortcut dock, assistant entry, footer and overlays; validate all roles and long/translated content. Shared no-script menu, role links, protected sign-out and enhancement cleanup are implemented; actual guest/client/trainer/gym phone and keyboard proof recorded on 2 October. Both administrator fallback roles have rendered regression coverage. Full theme/device/translated shell acceptance remains open.
- [ ] D04 Build common page header, search/filter, list/card, form, empty/error/loading, dialog, drawer, status, timeline and chart patterns using existing architecture.
- [ ] D05 Consolidate duplicate CSS overrides and fragments after documenting dependencies; regenerate assets through the existing build pipeline.
- [ ] D06 Preserve British English, localisation keys, semantic headings, page metadata and real SEO routes during the visual migration.

## Workflow changes across pages

Every journey needs entry, progress, success, failure and return behaviour. The following proposed flow changes must be verified against the services before implementation.

| Journey | 2.0 target and checks |
| --- | --- |
| New client | Understand service → discover a verified trainer or join → verify account → choose relevant preferences → request coach → know the request state and next step. Do not force all optional profile settings before browsing. |
| Returning client | Open dashboard → see the next useful session/task → open the plan → record actual sets → finish → send relevant reflection/check-in → see real progress. |
| Coach relationship | None/requested/active/paused/ended are explicit. Exactly one active trainer. Changing a coach must explain the impact on plans, history, communication and payments. |
| Trainer | Verify professional status → present profile → review requests → accept eligible client → assess → build/assign a plan → schedule → review session/check-in → respond. |
| Gym | Apply → track review → respond to missing information → approved account → associate trainers → submit verification → manage membership products and price-change history. Trainers can belong to multiple gyms. |
| Calendar and training | One clear vocabulary for exercise → workout → programme/schedule → assignment → calendar occurrence → workout session → recorded sets. Distinguish workout display templates from programme templates. |
| Messages and Charlie | One human-coaching inbox; one recognisable assistant entry. Preserve legacy thread histories and redirects. AI proposals must be previewed before schedule/workout changes. |
| Health and progress | Record only useful data with context and privacy. Goals, milestones and levels explain real criteria and support the coaching plan. |
| Purchase/subscription | Plan choice survives sign-in; price, billing period and payment status are clear; sandbox tests cover repeated/failed/pending requests and cancellation. |
| Recovery | Errors identify the problem, retain drafts where feasible and provide safe retry/help. A tour, popup or expired session must not strand the user. |

### High-value feature backlog

These are **proposed capabilities**, not claims that the current backend already supports them. Establish contracts and acceptance tests before building a control.

- [ ] F01 Add a trustworthy Today summary combining actual next workout, coach action and check-in due state across dashboard/calendar.
- [ ] F02 Add safe draft preservation and refresh/resume behaviour to programme builders, long forms and workout recording; show server-saved status separately from local drafts.
- [ ] F03 Add programme/assignment previews and conflict-aware schedule application with clear repeat-application behaviour.
- [ ] F04 Make unread coaching messages and actionable reminders consistent across dashboard, inbox and native app.
- [ ] F05 Present coach relationship and professional verification lifecycles consistently, including reasons a next action is unavailable.
- [ ] F06 Provide useful exercise media linked to the actual exercise library; support text/static fallback before optional 3D demonstrations.
- [ ] F07 Review whether a complete trainer-session availability → booking request → confirmation/reschedule/cancel → platform payment journey exists. The inspected page/controller inventory does not establish a dedicated booking/availability surface. Define and implement missing parts only after checking existing services/data; label any new screens as new scope.
- [ ] F08 Reconcile mobile and web account/verification/approval rules, especially native signup versus web gym application and verified-only trainer visibility.
- [ ] F09 Add a clear privacy/export/account-control area using existing services and role access, with predictable completion/pending states.
- [ ] F10 Restrict optional gamification, merch and supplements to places that support the user's immediate goal; keep the coaching journey dominant.

## 3D and animation production brief

The current entrance is already an imported solid GLB, not a flat image effect. Its editable Blender scene, generator and browser scene can be evolved. Current assets are approximately 0.53 MB GLB, 0.71 MB poster PNG and 0.66 MB generated browser bundle, measured on disk during this audit. These are uncompressed file sizes, not measured network/GPU costs.

### Rebuilt opening sequence

- [ ] M01 Produce a new model iteration that keeps the blue/orange connection identity but improves silhouette, proportions, bevels, materials and inner structure.
- [ ] M02 Design the closed resting view, interaction cue, opening midpoint and fully open composition before authoring geometry.
- [ ] M03 On intentional activation: gently align → separate/open → reveal the connected coaching world → settle into readable Trainer/Gym/Workout/Activity destinations. Keep main join/browse links available throughout.
- [ ] M04 Give selected nodes a real visual emphasis and matching DOM story state; clicking a destination should make its next action obvious.
- [ ] M05 On mobile, use a purposeful camera/crop and compact content order; avoid forcing users to scroll past an oversized scene to find its controls.
- [ ] M06 Reduce the number of controls visible initially. Put material/lighting/wireframe customisation in an optional advanced panel; retain useful existing functionality.
- [ ] M07 Keep closing, Escape, replay, pause and keyboard activation predictable; restore focus and preserve page scroll context.
- [ ] M08 Provide a reduced-motion route with immediate readable state changes and a matching static poster/text journey if WebGL or model loading fails.

### Asset authoring and import checklist

| Asset | Purpose | Required source/output | Release position |
| --- | --- | --- | --- |
| Signature connection sculpture | Homepage identity and opening | Editable `.blend`, generated `.glb`, new poster, material/animation contract | Core 2.0 |
| Interior connection environment | Opening reveals a useful coaching network | Optimised GLB geometry authored as part of the sculpture or a lazy-loaded scene; real labelled DOM destinations | Core only if its performance/UX target is met |
| Exercise demonstration models | Explain selected exercise technique | Licensed/authored rigged model and validated clips, textual instructions and static/video fallback | Selective later slice; never required for logging |
| Progress/milestone objects | Optional earned-state presentation | Small reusable mesh/material family with static representations | Optional after core coaching journey |
| Product rotation media | Show an actual merchandise product | Licensed product images/model; on-demand viewer | Optional store enhancement |

- [ ] M09 Preserve or explicitly migrate `Wing_Left`, `Wing_Right`, `Face_*`, `Circuit_*`, `Shell_*`, `Core_*` and `Wordmark` names used by the current browser animation; verify the GLB contract test after export.
- [ ] M10 Verify scale, Y-up axes, front-facing +Z, hinge pivots, normals, bounding box, materials, lights and framing in the actual page.
- [ ] M11 Optimise geometry/textures and use compression only with a tested loader/deployment path; choose budgets from measured devices rather than arbitrary polygon promises.
- [ ] M12 Load the scene progressively with a correct-size poster and useful error state; avoid layout shift and blocking normal page actions.
- [ ] M13 Maintain one controlled render loop, pause offscreen/background work, cap expensive effects and adapt resolution/quality to weaker devices.
- [ ] M14 Record licence/source/author, editable files and export settings for every imported model/image; do not hotlink or import unreviewed third-party assets.
- [ ] M15 Keep model, poster, source scene, generated bundle and licence notice in sync. Rebuild through the current tooling and compare served assets to the build output.
- [ ] M16 Verify desktop, tablet and representative phones with WebGL disabled, slow load, failed GLB, reduced motion and keyboard-only interaction.

The existing authoring paths are [sculpture guide](../brand/INTERACTIVE_SCULPTURE.md), [Blender generator](../../tools/brand/build-logo.py), [editable scene](../../design/brand/one-to-one-sculpture.blend), [browser scene](../../src/main/frontend/logo-experience.js), [animation clock](../../src/main/frontend/animation-clock.mjs) and [bundle builder](../../tools/brand/build-web.mjs). The current [Three.js GLTFLoader documentation](https://threejs.org/docs/pages/GLTFLoader.html) is the reference for supported import capabilities; source code alone does not prove model performance.

### Motion specification to prototype and measure

| Interaction | Starting treatment | Reduced-motion equivalent |
| --- | --- | --- |
| Hover/focus/selection | Short colour/edge/position response around 120–180 ms; focus is always visible | Immediate state colour/outline |
| Dialog/drawer/tab | Clear 180–300 ms movement with stable surrounding layout | Immediate disclosure or minimal fade |
| Page section entrance | Subtle once-only reveal; content remains available if JS fails | Content visible immediately |
| Sculpture opening | A short deliberate sequence; current documented entrance is 2.65 seconds, so prototype a tighter sequence and measure comprehension | Immediate open state or very short fade |
| Saved/completed action | Brief confirmed-state feedback from the real server result | Static success text/icon |
| Chart/progress | Animate changes in data without pretending data is live | Static values/table |

These timings are proposal starting points, not tested targets. Avoid scroll hijacking, compulsory intros, uncontrolled particles, flashing, arbitrary card motion and looping celebrations in daily work.

## Shared acceptance standard

Apply this to each page/component before checking its completion box. A template reviewed in source is not visually signed off. The accessibility target is WCAG 2.2 AA with manual checks; this audit is not a compliance certificate. The [W3C quick reference](https://www.w3.org/WAI/WCAG22/quickref/) supplies the criteria for contrast, keyboard operation, reflow, focus, target size, labels, errors and status announcements.

- [ ] Q01 Verify page purpose, one primary next action, useful back/return behaviour and matching role terminology.
- [ ] Q02 Capture final desktop, tablet and phone states; also check 320px reflow, landscape and zoom. Count overlap/cropping, not just document overflow.
- [ ] Q03 Measure text/control contrast in every supported theme; test selected, disabled, error and overlay states. Prefer comfortable 44px touch controls even where the criterion's exceptions/minimum differ.
- [ ] Q04 Check keyboard entry, focus order, visible/unobscured focus, labels, accessible names, Escape, modal focus containment and restoration.
- [ ] Q05 Verify loading, empty, populated, error, success, disabled/locked and relevant expired-session states with realistic local synthetic data.
- [ ] Q06 Verify reduced motion, pause controls, no compulsory 3D dependency, useful text/media alternatives and visible no-JS fallbacks for essential content.
- [ ] Q07 Run meaningful service/integration tests where behaviour changes; validate role ownership, CSRF, server validation and safe return URLs.
- [ ] Q08 Check slow/failing requests, saved-versus-unsaved status and duplicate submits; retain user work when feasible.
- [ ] Q09 Keep HTML markup/imports only; use external CSS/JS and shared fragments; preserve build, localisation and asset-cache contracts.
- [ ] Q10 Record fresh screenshots, actual test commands/results and remaining limitations. Never check Done while a required verification is unresolved.

### Implementation file map

| Concern | Existing place to evolve |
| --- | --- |
| Global layout/theme/route assets | base.html; Config/UiStyleBundleAdvice.java; css/components/core; css/entries; tailwind.css; core JS |
| Guest and authentication | public-views templates; css/components/public; css/components/account; js/public; js/auth |
| Sculpture | frontend/logo-experience.js and animation-clock.mjs; homepage fragment; misc/logo-experience.css and home-cosmos.css; tools/brand |
| Dashboard | role dashboard templates; client dashboard fragments; css/components/dashboard; existing dashboard JS/services |
| Calendar/schedules/workouts | shared calendar/session templates; trainer schedule/workout/library templates; css/components/calendar and training; matching JS/controllers |
| Profiles/settings | role profile templates; css/components/account; ProfileController and UserSettings/preferences services |
| Messaging/assistant/content | inbox/chat/notes/vault templates; css/components/chat and misc; existing external JS and controllers |
| Native app | Phone-App/application/app Compose screens, shared components/theme, mobile repository and MobileApi |

Do not edit generated CSS or the 3D bundle as the source of a redesign. Change the source files and rebuild. Backend work is needed only for changed or missing behaviour, and must retain existing contracts or introduce deliberate migrations.

## Implementation order

Work one slice at a time, review its final screens, complete its functional path and then move on. The sequence below is a proposed dependency order; all boxes are initially unchecked.

| Slice | Deliverable | Gate before proceeding |
| --- | --- | --- |
| 0 | Fix preference rendering, robust error recovery, current navigation collisions and establish local role access | Actual page render and safe navigation proven |
| 1 | Approve the visual target and shared 2.0 foundations | Desktop/mobile target, accessible tokens, common shell and component states |
| 2 | Public homepage and rebuilt opening sculpture | Closed/open/fallback/reduced-motion journey works with usable join/browse links |
| 3 | About, discovery, trainer profile, FAQ, support, pricing, policies | Guest understands product, can compare trainers and complete the next step |
| 4 | Role signup, login, verification and recovery | All roles, errors, code entry, return destinations and approval states tested |
| 5 | Client dashboard, trainer connection, plan, calendar and workout execution | Complete coach→plan→session→feedback path |
| 6 | Goals, check-ins, progress, health and profile/settings | Real saved state and clear privacy/accessibility controls |
| 7 | Trainer client management, library, programme/schedule authoring and reviews | Complete request→assignment→review→response path |
| 8 | Human inbox, Charlie, notes and Vault | Histories retained, drafts/recovery work, AI proposals truthful and deliberate |
| 9 | Gym, platform admin, verification, billing and commerce | Correct role access, evidence review and sandbox consequential flows |
| 10 | Native Android alignment, final compatibility cleanup and release | Native-device evidence plus whole-product regression and release naming |

- [ ] R00 Slice 0 complete.
- [ ] R01 Slice 1 complete.
- [ ] R02 Slice 2 complete.
- [ ] R03 Slice 3 complete.
- [ ] R04 Slice 4 complete.
- [ ] R05 Slice 5 complete.
- [ ] R06 Slice 6 complete.
- [ ] R07 Slice 7 complete.
- [ ] R08 Slice 8 complete.
- [ ] R09 Slice 9 complete.
- [ ] R10 Slice 10 complete.

## Every web page and shared component

The following **170 entries** are in a practical review order, beginning with the public homepage and its opening model. Shared components appear later in the inventory but must be addressed early through the implementation slices. Priority: P0 = blocks dependable use/release; P1 = core 2.0 journey; P2 = secondary or compatibility work. Source-only observations name what is present and the risk/opportunity; they do not assert a visual defect without a current screenshot.

For a missing direct route, trace the controller/private helper and template references before implementing or retiring it. For 403/404/500 views, the render depends on dispatch/status rather than treating every referencing endpoint as an ordinary page URL. Completion notes should include date, changed files, screenshots, tests and any remaining decision.

### Page navigation index

Use a W identifier to refer to one page when implementing/reviewing. Check the actual task boxes in its entry.

| ID | Page/component | Priority | Current coverage |
| --- | --- | --- | --- |
| [W001](#w001) | Public homepage | P1 | V |
| [W002](#w002) | Opening sculpture and connected journey | P1 | V |
| [W003](#w003) | About and trust | P1 | V |
| [W004](#w004) | FAQ and help discovery | P1 | V |
| [W005](#w005) | Verified trainer discovery | P1 | V |
| [W006](#w006) | Public member profile | P1 | S |
| [W007](#w007) | Public trainer profile and reviews | P1 | V |
| [W008](#w008) | Premium pricing | P1 | V |
| [W009](#w009) | Premium checkout | P1 | S |
| [W010](#w010) | Public dashboard demonstration | P1 | S |
| [W011](#w011) | Signed-in alternate home | P1 | S |
| [W012](#w012) | Client, trainer and gym login | P1 | V |
| [W013](#w013) | Account type choice | P1 | V |
| [W014](#w014) | Client registration | P1 | V |
| [W015](#w015) | Trainer registration | P1 | V |
| [W016](#w016) | Trainer account confirmation | P1 | S |
| [W017](#w017) | Gym application | P1 | V |
| [W018](#w018) | Gym application status | P1 | S |
| [W019](#w019) | Legacy generic registration template | P2 | L/S |
| [W020](#w020) | Request password reset | P1 | V |
| [W021](#w021) | Set replacement password | P1 | S |
| [W022](#w022) | Logout confirmation | P2 | S |
| [W023](#w023) | Development/demo entry | P2 | L/S |
| [W024](#w024) | Social login controls | P1 | S |
| [W025](#w025) | Email code verification | P1 | S |
| [W026](#w026) | Email link confirmation | P1 | S |
| [W027](#w027) | Phone verification | P1 | S |
| [W028](#w028) | Privacy information | P1 | S |
| [W029](#w029) | Terms of Service | P0 | V |
| [W030](#w030) | Subscription terms | P0 | S |
| [W031](#w031) | Platform payment policy | P1 | S |
| [W032](#w032) | Contact support | P1 | V |
| [W033](#w033) | Client dashboard | P1 | V |
| [W034](#w034) | Dashboard content shell | P1 | V |
| [W035](#w035) | Dashboard member identity | P1 | S |
| [W036](#w036) | Client profile, settings and billing | P1 | V |
| [W037](#w037) | Quick preference onboarding | P1 | S |
| [W038](#w038) | Preference editor | P0 | B |
| [W039](#w039) | Preference summary | P2 | S |
| [W040](#w040) | My trainer relationship and search | P1 | V |
| [W041](#w041) | Alternate My Trainer template | P2 | L/S |
| [W042](#w042) | Assigned library plan | P1 | S |
| [W043](#w043) | Assigned workout and schedule plan | P1 | S |
| [W044](#w044) | Trainer assessment of a client | P1 | S |
| [W045](#w045) | Weekly client check-in | P1 | S |
| [W046](#w046) | Goals overview | P1 | V |
| [W047](#w047) | Create a goal | P1 | S |
| [W048](#w048) | Edit a goal | P1 | S |
| [W049](#w049) | Goal detail and activity links | P1 | S |
| [W050](#w050) | Goal check-in history | P1 | S |
| [W051](#w051) | Reusable goal chip | P1 | S |
| [W052](#w052) | Milestones and achievements | P2 | S |
| [W053](#w053) | Personal progress level | P2 | S |
| [W054](#w054) | Leaderboard | P2 | S |
| [W055](#w055) | Month calendar | P1 | V |
| [W056](#w056) | Week calendar | P1 | V |
| [W057](#w057) | Day planner and health panels | P1 | V |
| [W058](#w058) | Focused day timeline | P1 | V |
| [W059](#w059) | Task detail and reminders | P1 | V |
| [W060](#w060) | Month schedule deployment drawer | P1 | V |
| [W061](#w061) | Week schedule deployment drawer | P1 | V |
| [W062](#w062) | Calendar consistency indicator | P2 | V |
| [W063](#w063) | Training entry and session history | P1 | V |
| [W064](#w064) | Scheduled workout execution | P1 | V |
| [W065](#w065) | Scheduled workout completion | P1 | V |
| [W066](#w066) | Personal workout library and builder | P1 | V |
| [W067](#w067) | Edit personal workout | P1 | V |
| [W068](#w068) | Personal workout studio | P1 | V |
| [W069](#w069) | Workout search component | P1 | V |
| [W070](#w070) | Workout list and detail fragments | P1 | V |
| [W071](#w071) | Workout display templates | P2 | V |
| [W072](#w072) | Workout layout editor | P2 | V |
| [W073](#w073) | Schedule control centre | P1 | V |
| [W074](#w074) | Schedule builder | P1 | V |
| [W075](#w075) | Schedule entries and application | P1 | V |
| [W076](#w076) | Apply schedule form | P1 | V |
| [W077](#w077) | Alternate schedule chooser | P2 | R |
| [W078](#w078) | Schedule-linked workout builder | P1 | V |
| [W079](#w079) | Trainer dashboard | P1 | V |
| [W080](#w080) | Client relationship management | P1 | V |
| [W081](#w081) | Alternate client requests list | P2 | R |
| [W082](#w082) | Alternate active clients list | P2 | R |
| [W083](#w083) | Client coaching workspace | P1 | V |
| [W084](#w084) | Trainer check-in review | P1 | V |
| [W085](#w085) | Trainer owner account profile | P1 | V |
| [W086](#w086) | Professional trainer profile editor | P1 | V |
| [W087](#w087) | Trainer content library hub | P1 | V |
| [W088](#w088) | Trainer exercise library | P1 | V |
| [W089](#w089) | Create trainer exercise | P1 | V |
| [W090](#w090) | Edit trainer exercise | P1 | V |
| [W091](#w091) | Trainer exercise detail | P1 | V |
| [W092](#w092) | Trainer workout library | P1 | V |
| [W093](#w093) | Create coach workout | P1 | V |
| [W094](#w094) | Edit coach workout metadata | P1 | V |
| [W095](#w095) | Coach workout composition | P1 | V |
| [W096](#w096) | Programme library | P1 | V |
| [W097](#w097) | Create programme | P1 | V |
| [W098](#w098) | Edit programme metadata | P1 | V |
| [W099](#w099) | Programme composition | P1 | V |
| [W100](#w100) | Schedule template library | P1 | V |
| [W101](#w101) | Schedule template and check-in editor | P1 | V |
| [W102](#w102) | Apply schedule template to a client | P1 | V |
| [W103](#w103) | Library share dialog | P1 | V |
| [W104](#w104) | Personal exercise log history | P1 | V |
| [W105](#w105) | Create or edit exercise log | P1 | V |
| [W106](#w106) | Exercise log detail | P1 | V |
| [W107](#w107) | Exercise instruction page | P2 | V |
| [W108](#w108) | Health record history | P1 | V |
| [W109](#w109) | Health record entry | P1 | V |
| [W110](#w110) | Health record detail | P1 | V |
| [W111](#w111) | Blood-pressure readings | P1 | V |
| [W112](#w112) | Edit blood-pressure reading | P1 | V |
| [W113](#w113) | Daily nutrition record | P2 | V |
| [W114](#w114) | Coaching inbox and notifications | P1 | V |
| [W115](#w115) | Coaching conversation | P1 | V |
| [W116](#w116) | Legacy client messages inbox | P2 | V |
| [W117](#w117) | Legacy trainer messages inbox | P2 | V |
| [W118](#w118) | Legacy thread and check-in composer | P1 | V |
| [W119](#w119) | Charlie assistant command centre | P1 | V |
| [W120](#w120) | Legacy assistant conversation hub | P2 | V |
| [W121](#w121) | Assistant conversation folder | P2 | V |
| [W122](#w122) | Alternative assistant thread | P2 | V |
| [W123](#w123) | Assistant sidebar | P1 | V |
| [W124](#w124) | Personal notes hub | P2 | V |
| [W125](#w125) | Note folder detail | P2 | V |
| [W126](#w126) | Note editor | P2 | V |
| [W127](#w127) | Note reader | P2 | V |
| [W128](#w128) | Training reflection Vault | P2 | V |
| [W129](#w129) | Vault reflection editor | P2 | V |
| [W130](#w130) | Reflection and AI insight | P2 | V |
| [W131](#w131) | Trainer review submission | P1 | V |
| [W132](#w132) | Merchandise store | P2 | V |
| [W133](#w133) | Merchandise checkout | P1 | V |
| [W134](#w134) | Merchandise order history | P2 | R |
| [W135](#w135) | General order history | P2 | V |
| [W136](#w136) | Gym operations dashboard | P1 | V |
| [W137](#w137) | Gym trainer verification and invitation | P1 | V |
| [W138](#w138) | Membership products | P1 | V |
| [W139](#w139) | Create or edit membership product | P1 | S |
| [W140](#w140) | Membership price change | P1 | S |
| [W141](#w141) | Membership price history | P1 | S |
| [W142](#w142) | Gym profile and account settings | P1 | S |
| [W143](#w143) | Platform operations dashboard | P1 | S |
| [W144](#w144) | Support administration | P1 | S |
| [W145](#w145) | Gym application queue | P1 | S |
| [W146](#w146) | Gym application review | P1 | S |
| [W147](#w147) | Off-platform payment moderation | P1 | S |
| [W148](#w148) | Product administration | P2 | S |
| [W149](#w149) | Product editor | P2 | S |
| [W150](#w150) | Trainer verification queue | P1 | S |
| [W151](#w151) | Trainer verification decision | P1 | S |
| [W152](#w152) | Shared document and page layout | P1 | V |
| [W153](#w153) | Primary navigation and mobile menu | P0 | V |
| [W154](#w154) | Shared footer | P1 | S |
| [W155](#w155) | Profile menu and account identity | P1 | V |
| [W156](#w156) | Language selector | P1 | S |
| [W157](#w157) | Persistent shortcut panel | P1 | V |
| [W158](#w158) | Quick action launcher | P1 | V |
| [W159](#w159) | Global Charlie widget | P1 | V |
| [W160](#w160) | First-use guided tour | P1 | V |
| [W161](#w161) | Development-mode notice | P2 | S |
| [W162](#w162) | Page development status display | P2 | S |
| [W163](#w163) | Development route hub | P2 | S |
| [W164](#w164) | Temporarily restricted feature | P1 | S |
| [W165](#w165) | Login required state | P1 | S |
| [W166](#w166) | Access denied | P1 | S |
| [W167](#w167) | Page not found | P1 | S |
| [W168](#w168) | Server error | P0 | S |
| [W169](#w169) | Generic error state | P1 | S |
| [W170](#w170) | Birthday Mission VI special page | P2 | S |
| [W171](#w171) | Client read-only assigned schedule | P1 | S |
| [W172](#w172) | Trainer private assessment history | P1 | S |
| [W173](#w173) | Admin review moderation | P1 | S |
| [W174](#w174) | Trainer and gym connection workspace | P1 | V |
| [W175](#w175) | Private gym connection history | P1 | V |
| [W176](#w176) | Trainer professional-review workspace | P1 | V |
| [W177](#w177) | Client saved check-in and coach response | P1 | V |
| [W178](#w178) | Personal workout start confirmation | P1 | S |


<a id="w001"></a>

### W001 — Public homepage

**Priority:** P1 · **Source:** [public-views/home/public.html](../../src/main/resources/templates/public-views/home/public.html)

**Route/context:** `/`.

**Coverage:** **V** — inspected current state: [01-home-closed](evidence/2026-10-01-v2/01-home-closed.png), [28-home-mobile](evidence/2026-10-01-v2/28-home-mobile.png), [30-home-tablet](evidence/2026-10-01-v2/30-home-tablet.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: bright cream/green hero, blue/orange sculpture and darker lower sections do not form one visual language; mobile places the sculpture below the first viewport.

- [ ] W001.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W001.2 **Design, styling and motion:** Build a cinematic midnight entrance with a compact mobile composition, direct join/browse actions and a continuous coaching story; animate depth and selection without delaying navigation.
- [ ] W001.3 **Workflow, usability and features:** Keep role entry and verified-trainer discovery visible before opening 3D; label sample progress and sample coach proof clearly; test every CTA and all demo tabs.
- [ ] W001.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w002"></a>

### W002 — Opening sculpture and connected journey

**Priority:** P1 · **Source:** [public-views/home/fragments/logo-experience.html](../../src/main/resources/templates/public-views/home/fragments/logo-experience.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [01-home-closed](evidence/2026-10-01-v2/01-home-closed.png), [02-home-open](evidence/2026-10-01-v2/02-home-open.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: click opens the scene, but pale journey headings wash out over the bright hero; three scene modes plus four story destinations increase interaction complexity.

- [ ] W002.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W002.2 **Design, styling and motion:** Reauthor the solid logo and its opening choreography; use a lit interior network, deliberate camera framing and a legible opaque story surface; replace the subtle icon-only launch with a named action.
- [ ] W002.3 **Workflow, usability and features:** Keep one scene state owner; provide click, touch, Enter, Escape, pause and replay; give the same four destinations in the poster fallback; prove closing returns focus.
- [ ] W002.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w003"></a>

### W003 — About and trust

**Priority:** P1 · **Source:** [public-views/public/about.html](../../src/main/resources/templates/public-views/public/about.html)

**Route/context:** `/about`.

**Coverage:** **V** — inspected current state: [03-about](evidence/2026-10-01-v2/03-about.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: a large headline and decorative orbital illustration occupy the first screen; current strengths are a clear coaching proposition and join/browse links.

- [ ] W003.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W003.2 **Design, styling and motion:** Create a shorter editorial introduction with real coaching imagery and a tangible explanation of verification; use subtle depth transitions shared with the homepage.
- [ ] W003.3 **Workflow, usability and features:** Show how trainer selection, a shared plan and feedback work in order; distinguish illustrative people from real verified listings.
- [ ] W003.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w004"></a>

### W004 — FAQ and help discovery

**Priority:** P1 · **Source:** [public-views/public/faq.html](../../src/main/resources/templates/public-views/public/faq.html)

**Route/context:** `/faq`.

**Coverage:** **V** — inspected current state: [05-faq](evidence/2026-10-01-v2/05-faq.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: the full-height marketing hero delays the search and answers users came to find.

- [ ] W004.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Implemented: topic chips, URL search state, answer hashes and contextual support subject; browser checked no-results/Clear, restored privacy answer and support prefill. Evidence: `evidence/2026-10-01-v2-implementation/faq-light-390.png`. Both-theme and keyboard acceptance still pending.
- [ ] W004.2 **Design, styling and motion:** Put searchable questions, topic chips and the most useful answers above decorative imagery; animate answer expansion with stable layout.
- [x] W004.3 **Workflow, usability and features:** Retain deep links and search state; ensure no-results recovery and keyboard accordion operation; connect unresolved questions to support with relevant context. Verified in local browser: query/topic/hash restoration, no-results/Clear, Enter on native summary and subject prefill at support. Full page acceptance remains open.
- [ ] W004.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w005"></a>

### W005 — Verified trainer discovery

**Priority:** P1 · **Source:** [client-views/explore/index.html](../../src/main/resources/templates/client-views/explore/index.html)

**Route/context:** `/explore`.

**Coverage:** **V** — inspected current state: [06-explore](evidence/2026-10-01-v2/06-explore.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: directory title dominates the first screen, while filters and the two seeded trainers sit further down.

- [ ] W005.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W005.2 **Design, styling and motion:** Build an image-led discovery workspace with compact search, real trainer cards, visible location/price/speciality and a mobile filter sheet.
- [ ] W005.3 **Workflow, usability and features:** Preserve filter URLs and verified-only visibility; explain active-coach request restrictions; show useful empty results and return-to-results state.
- [ ] W005.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w006"></a>

### W006 — Public member profile

**Priority:** P1 · **Source:** [public-views/public/profile.html](../../src/main/resources/templates/public-views/public/profile.html)

**Route/context:** `/u/{username}`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: public profile exposes rating/review presentation; public and owner information must be deliberately separated.

- [ ] W006.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W006.2 **Design, styling and motion:** Create a polished public identity header with restrained depth, evidence-based badges and readable review summaries.
- [ ] W006.3 **Workflow, usability and features:** Review exactly which fields are public; distinguish member and trainer profiles; test anonymous viewing and ensure private health/contact information stays private.
- [ ] W006.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w007"></a>

### W007 — Public trainer profile and reviews

**Priority:** P1 · **Source:** [trainer-views/trainer/profile/view.html](../../src/main/resources/templates/trainer-views/trainer/profile/view.html)

**Route/context:** `/trainers/{trainerId}/profile`.

**Coverage:** **V** — fresh anonymous light-theme portfolio inspected at [desktop](evidence/2026-10-01-v2-implementation/trainer-portfolio-light-desktop.png) and [390px](evidence/2026-10-01-v2-implementation/trainer-portfolio-light-390.png). Authenticated actions, other themes and review states remain open.

**Current finding/opportunity:** Source: professional profile, reviews and report-review modal share one template; legitimacy and the request action need clear hierarchy.

**Implementation progress:** Semantic portfolio surfaces now show verified status, real bio/specialities, saved location/gym/session price, request/pending/current-coach actions and real review counts. Social links have translated names and validated HTTP(S) destinations. Native report disclosures use labelled, bounded, CSRF-protected forms and retain invalid reasons. Trainer sign-in codes remain owner-only, handle malformed historical values safely and are explicitly private. Narrow public GET access no longer blocks visitors or the owning trainer; request/review/admin operations stay protected. Source/render/security proof and the anonymous desktop/phone captures are recorded in the implementation document. The stretched desktop button was found and fixed; full browser acceptance remains open.

- [ ] W007.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W007.2 **Design, styling and motion:** Design a trainer portfolio with real media, verification details, specialities, price and gym affiliations; use controlled gallery motion.
- [ ] W007.3 **Workflow, usability and features:** Make coach availability and request state clear; keep reports and reviews accessible; verify only authorised users can request or report and do not invent review counts.
- [ ] W007.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w008"></a>

### W008 — Premium pricing

**Priority:** P1 · **Source:** [public-views/payments/pricing.html](../../src/main/resources/templates/public-views/payments/pricing.html)

**Route/context:** `/pricing`.

**Coverage:** **V** — inspected current state: [04-pricing](evidence/2026-10-01-v2/04-pricing.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: oversized marketing text consumes the first viewport before the practical plan choice.

- [ ] W008.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Corrected yearly saving to £36 against unchanged £12/month and £108/year charges. Shared public skin implemented; pricing layout and selected-plan login journey remain in review.
- [ ] W008.2 **Design, styling and motion:** Place plan costs, billing period, benefits and one clear comparison near the top; use a gentle period-switch transition and restrained 3D accent if justified.
- [ ] W008.3 **Workflow, usability and features:** Separate platform premium from trainer-session and gym charges; make billing period and cancellation information explicit; preserve selected plan through sign-in.
- [ ] W008.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w009"></a>

### W009 — Premium checkout

**Priority:** P1 · **Source:** [public-views/payments/pricing-checkout.html](../../src/main/resources/templates/public-views/payments/pricing-checkout.html)

**Route/context:** `/pricing/checkout`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: saved cards, new provider token and summary are combined; real payment readiness was not tested.

- [ ] W009.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Fixed: verified provider checkout must match member and paid plan; unpaid, malformed metadata and lifetime purchases rejected; duplicate activation cannot reverse cancellation or downgrade lifetime access. Subscription periods support current item-level and legacy payloads. Local provider fixtures and controller/webhook checks passed; provider sandbox visual/transaction acceptance remains open.
- [ ] W009.2 **Design, styling and motion:** Build a calm, compact checkout with a stable order summary, readable status and explicit final action; avoid ambient 3D.
- [ ] W009.3 **Workflow, usability and features:** Show recurring amount and period before confirmation; handle provider-disabled, pending, failed, duplicate and completed states; verify provider token collection in a sandbox.
- [ ] W009.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w010"></a>

### W010 — Public dashboard demonstration

**Priority:** P1 · **Source:** [public-views/dashboard/client-dashboard-public.html](../../src/main/resources/templates/public-views/dashboard/client-dashboard-public.html)

**Route/context:** `/client/dashboard/public`, `/dashboard/public`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: a 476-line preview duplicates concepts from the actual client dashboard and can overpromise the signed-in experience.

- [ ] W010.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W010.2 **Design, styling and motion:** Reuse the final 2.0 dashboard presentation with visibly labelled synthetic data and a concise interactive walkthrough.
- [ ] W010.3 **Workflow, usability and features:** Define what demo actions simulate and what requires joining; keep demo and real account state separate; check every demo tab and destination.
- [ ] W010.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w011"></a>

### W011 — Signed-in alternate home

**Priority:** P1 · **Source:** [public-views/home/user.html](../../src/main/resources/templates/public-views/home/user.html)

**Route/context:** `/home`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: /home has role redirects but retains a separate client Today Focus/Performance surface alongside /dashboard.

- [ ] W011.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W011.2 **Design, styling and motion:** Choose one canonical client entry and bring any unique useful content into it; reuse 2.0 next-session presentation.
- [ ] W011.3 **Workflow, usability and features:** Preserve /home as a redirect or a purposeful distinct destination after reviewing contracts; prevent duplicated navigation and differing next-workout information.
- [ ] W011.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w012"></a>

### W012 — Client, trainer and gym login

**Priority:** P1 · **Source:** [public-views/auth/login.html](../../src/main/resources/templates/public-views/auth/login.html)

**Route/context:** `/login`.

**Coverage:** **V** — inspected current state: [14-login](evidence/2026-10-01-v2/14-login.png), [29-login-mobile](evidence/2026-10-01-v2/29-login-mobile.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: role tabs and credential guidance exist; mobile development notice pushes fields low; trainer demo completion was not established in this run.

**Implementation progress:** Trainer demo pointer sign-in now reaches the correct workspace. Fixed short-segment paste clearing earlier trainer/gym segments; complete-code paste remains supported. Shared loading state retains its label. On 2 October actual script-disabled client, trainer and gym sign-in/sign-out succeeded at phone width: native role links, complete code fields and disabled inactive required fields preserve the existing security contracts. Enhanced trainer/gym sign-in still succeeds after restoring scripts. Ten final login integration regressions pass. Every-role typing/paste, error/recovery, remaining device/theme and safe-return acceptance remain open.

- [ ] W012.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W012.2 **Design, styling and motion:** Use a focused secure-access surface with compact brand presence, readable fields, visible role context and a smooth role transition.
- [ ] W012.3 **Workflow, usability and features:** Preserve credential contracts and authorisation codes; verify full-code paste and segmented typing independently; test every role, errors, forgotten password and safe return destination.
- [ ] W012.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w013"></a>

**Fresh browser progress, 1 October 2026:** Real native synthetic client/platform-admin login succeeded. The public shop has verified light-theme contrast, loaded product fronts and no horizontal desktop overflow at `20261001v2ac`; screenshots and exact limits are recorded in `docs/qa/2026-10-01-v2-implementation.md`. Additional grid/admin contrast source refinements and full device/theme/motion acceptance remain open.

### W013 — Account type choice

**Priority:** P1 · **Source:** [public-views/auth/signup-choice.html](../../src/main/resources/templates/public-views/auth/signup-choice.html)

**Route/context:** `/signup`.

**Coverage:** **V** — inspected current state: [07-signup-choice](evidence/2026-10-01-v2/07-signup-choice.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: three distinct role cards are understandable, but a large left heading takes most of the vertical space.

- [ ] W013.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W013.2 **Design, styling and motion:** Use three purposeful role entry surfaces with short outcomes and explicit differences; provide a compact mobile sequence.
- [ ] W013.3 **Workflow, usability and features:** Explain that clients join, trainers need verification and gyms apply for approval; keep the chosen role through subsequent steps.
- [ ] W013.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w014"></a>

### W014 — Client registration

**Priority:** P1 · **Source:** [public-views/auth/signup-client.html](../../src/main/resources/templates/public-views/auth/signup-client.html)

**Route/context:** `/signup/client`.

**Coverage:** **V** — inspected current state: [08-signup-client](evidence/2026-10-01-v2/08-signup-client.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: a two-step identity/security wizard is present and provides useful structure.

- [ ] W014.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W014.2 **Design, styling and motion:** Create a welcoming, efficient onboarding surface with clear progress, stronger labels and stable transitions; defer optional profile decoration.
- [ ] W014.3 **Workflow, usability and features:** Preserve entered values when moving backwards; connect account creation to verification, relevant preferences and trainer discovery; validate both steps on keyboard/mobile.
- [ ] W014.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w015"></a>

### W015 — Trainer registration

**Priority:** P1 · **Source:** [public-views/auth/signup-trainer.html](../../src/main/resources/templates/public-views/auth/signup-trainer.html)

**Route/context:** `/signup/trainer`.

**Coverage:** **V** — inspected current state: [09-signup-trainer](evidence/2026-10-01-v2/09-signup-trainer.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: three steps collect secure identity, professional profile and online presence; account creation and verified status need separate wording.

- [ ] W015.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W015.2 **Design, styling and motion:** Build a professional onboarding journey with a meaningful verification checklist and optional media later; show progress without excessive decoration.
- [ ] W015.3 **Workflow, usability and features:** Clarify what can be used while awaiting verification; preserve authorisation-code delivery and recovery; test incomplete credentials and review-required states.
- [ ] W015.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w016"></a>

### W016 — Trainer account confirmation

**Priority:** P1 · **Source:** [public-views/auth/signup-trainer-success.html](../../src/main/resources/templates/public-views/auth/signup-trainer-success.html)

**Route/context:** `/signup/trainer/success`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: confirmation includes copyable trainer code; success must not imply approval to coach.

- [ ] W016.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W016.2 **Design, styling and motion:** Show a clear account-created state, protected code storage guidance and a verification progress rail; use one brief completion animation.
- [ ] W016.3 **Workflow, usability and features:** Give an explicit next step to verification/profile setup; test copy feedback and keyboard access; avoid exposing the code in analytics or public previews.
- [ ] W016.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w017"></a>

### W017 — Gym application

**Priority:** P1 · **Source:** [public-views/auth/signup-gym.html](../../src/main/resources/templates/public-views/auth/signup-gym.html)

**Route/context:** `/signup/gym`.

**Coverage:** **V** — inspected current state: [10-signup-gym](evidence/2026-10-01-v2/10-signup-gym.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: organisation, location and security steps exist and explain approval-before-account creation.

- [ ] W017.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W017.2 **Design, styling and motion:** Design a polished organisation application with clearer requirements and a short review process summary.
- [ ] W017.3 **Workflow, usability and features:** Retain entered data and progress; state application versus active-account status; verify submission validation and follow-up access with local or sandbox mail.
- [ ] W017.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w018"></a>

### W018 — Gym application status

**Priority:** P1 · **Source:** [public-views/auth/signup-gym-application.html](../../src/main/resources/templates/public-views/auth/signup-gym-application.html)

**Route/context:** `/signup/gym/application/{token}`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: token-based status page contains an application summary and update-message form.

- [ ] W018.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W018.2 **Design, styling and motion:** Create an application timeline with current state, missing information and one next action; animate only real status changes.
- [ ] W018.3 **Workflow, usability and features:** Treat the token as private; explain submitted/needs-info/approved/declined states and reply status; verify expired access and avoid publishing sensitive application details.
- [ ] W018.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w019"></a>

### W019 — Legacy generic registration template

**Priority:** P2 · **Source:** [public-views/auth/signup.html](../../src/main/resources/templates/public-views/auth/signup.html)

**Route/context:** No direct GET render found; inspect helper/caller and alternate/legacy use before implementation.

**Coverage:** **L/S** — source inventoried; active/alternate use and runtime presentation need confirmation.

**Current finding/opportunity:** Source: a generic signup template remains beside role-specific registration; active rendering must be confirmed before restyling.

- [ ] W019.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W019.2 **Design, styling and motion:** Migrate any unique behaviour into the role onboarding system; retire only after proving the template is unused.
- [ ] W019.3 **Workflow, usability and features:** Check controller and fragment references, historic links and tests; preserve compatibility redirects and document any removal.
- [ ] W019.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w020"></a>

### W020 — Request password reset

**Priority:** P1 · **Source:** [public-views/auth/forgot-password.html](../../src/main/resources/templates/public-views/auth/forgot-password.html)

**Route/context:** `/forgot-password`.

**Coverage:** **V** — inspected current state: [11-forgot-password](evidence/2026-10-01-v2/11-forgot-password.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: recovery explains that a link is sent if the account exists and keeps a direct login route.

- [ ] W020.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W020.2 **Design, styling and motion:** Keep a compact recovery screen with readable confirmation and one clear next step; use a short status transition.
- [ ] W020.3 **Workflow, usability and features:** Preserve account-enumeration protection; test delayed mail, resend guidance and return to login using controlled test delivery.
- [ ] W020.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w021"></a>

### W021 — Set replacement password

**Priority:** P1 · **Source:** [public-views/auth/reset-password.html](../../src/main/resources/templates/public-views/auth/reset-password.html)

**Route/context:** `/reset-password`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: reset page has expired-link recovery and password visibility controls; no reset was performed.

- [ ] W021.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W021.2 **Design, styling and motion:** Match the recovery surface and show requirements inline with an accessible strength indicator.
- [ ] W021.3 **Workflow, usability and features:** Verify valid/expired/used tokens and confirmation mismatch; preserve secure server validation and return-to-login flow; do not change real user credentials during audit.
- [ ] W021.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w022"></a>

### W022 — Logout confirmation

**Priority:** P2 · **Source:** [public-views/auth/confirm-logout.html](../../src/main/resources/templates/public-views/auth/confirm-logout.html)

**Route/context:** `/confirm-logout`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: a separate confirmation template exists alongside direct profile-menu sign out.

- [ ] W022.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W022.2 **Design, styling and motion:** Use a lightweight, coherent confirmation surface only where unsaved work or session behaviour warrants it.
- [ ] W022.3 **Workflow, usability and features:** Define whether logout affects this session or all devices; preserve POST/CSRF logout and safe return; test cancellation and signed-out navigation.
- [ ] W022.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w023"></a>

### W023 — Development/demo entry

**Priority:** P2 · **Source:** [public-views/auth/login-demo.html](../../src/main/resources/templates/public-views/auth/login-demo.html)

**Route/context:** No direct GET render found; inspect helper/caller and alternate/legacy use before implementation.

**Coverage:** **L/S** — source inventoried; active/alternate use and runtime presentation need confirmation.

**Current finding/opportunity:** Source: demo education and notification signup are mixed with development-only access.

- [ ] W023.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W023.2 **Design, styling and motion:** Build a clearly labelled test-mode entry with role examples and short safe instructions; keep the release experience uncluttered.
- [ ] W023.3 **Workflow, usability and features:** Ensure demo credentials and development links do not reach production; make demo-only state explicit; audit notification submission separately.
- [ ] W023.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w024"></a>

### W024 — Social login controls

**Priority:** P1 · **Source:** [public-views/auth/fragments/social-auth-buttons.html](../../src/main/resources/templates/public-views/auth/fragments/social-auth-buttons.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: Google sign-in fragment is present; configured availability and callback behaviour were not checked.

- [ ] W024.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W024.2 **Design, styling and motion:** Use consistent provider branding and concise loading/failure states; prevent decorative disabled provider buttons.
- [ ] W024.3 **Workflow, usability and features:** Show only available providers or explain unavailability; preserve role and return destination through callback; verify with a sandbox account.
- [ ] W024.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w025"></a>

### W025 — Email code verification

**Priority:** P1 · **Source:** [public-views/verify/email-code.html](../../src/main/resources/templates/public-views/verify/email-code.html)

**Route/context:** `/verify/email/code`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: email code form includes resend cooldown and destination email context.

- [ ] W025.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W025.2 **Design, styling and motion:** Create a focused verification step with accessible code entry, paste support and readable timer; animate verification outcome once.
- [ ] W025.3 **Workflow, usability and features:** Test complete paste, invalid/expired codes, cooldown, change-email recovery and next destination without sending real mail.
- [ ] W025.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w026"></a>

### W026 — Email link confirmation

**Priority:** P1 · **Source:** [public-views/verify/email-confirm.html](../../src/main/resources/templates/public-views/verify/email-confirm.html)

**Route/context:** `/verify/email`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: success/failure confirmation is distinct from code verification.

- [ ] W026.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W026.2 **Design, styling and motion:** Use an unmistakable confirmed/expired state and one appropriate continuation action.
- [ ] W026.3 **Workflow, usability and features:** Keep repeated-link handling safe; distinguish already verified from invalid; preserve the intended onboarding destination.
- [ ] W026.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w027"></a>

### W027 — Phone verification

**Priority:** P1 · **Source:** [public-views/verify/phone-code.html](../../src/main/resources/templates/public-views/verify/phone-code.html)

**Route/context:** `/verify/phone/code`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: SMS code entry, resend and returnTo are exposed in this template.

- [ ] W027.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W027.2 **Design, styling and motion:** Match email verification while clearly identifying phone delivery and editable destination context.
- [ ] W027.3 **Workflow, usability and features:** Test incomplete/expired code, delivery delay, cooldown and safe returnTo; use controlled SMS delivery and preserve privacy.
- [ ] W027.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w028"></a>

### W028 — Privacy information

**Priority:** P1 · **Source:** [public-views/policies/privacy.html](../../src/main/resources/templates/public-views/policies/privacy.html)

**Route/context:** `/policies/privacy`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: privacy template includes collection, purposes and commitments; content completeness is not certified.

- [ ] W028.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W028.2 **Design, styling and motion:** Use a readable document layout with section navigation, effective date and a print-friendly surface; keep motion minimal.
- [ ] W028.3 **Workflow, usability and features:** Map explanations to actual uploads, health data, messaging, AI and retention/export flows; arrange qualified content review before release.
- [ ] W028.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w029"></a>

### W029 — Terms of Service

**Priority:** P0 · **Source:** [public-views/policies/terms.html](../../src/main/resources/templates/public-views/policies/terms.html)

**Route/context:** `/policies/terms`.

**Coverage:** **V** — inspected current state: [12-terms](evidence/2026-10-01-v2/12-terms.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: Terms of Service shows Awaiting review/Page unavailable; the signup journey needs published reviewed terms.

- [ ] W029.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W029.2 **Design, styling and motion:** Provide a legible legal document layout with anchored sections, version and effective date; no decorative reading obstruction.
- [ ] W029.3 **Workflow, usability and features:** Obtain reviewed terms matching actual service behaviour before launch; preserve acceptance/version records where required; verify the signup link opens the current text.
- [ ] W029.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w030"></a>

### W030 — Subscription terms

**Priority:** P0 · **Source:** [public-views/policies/subscription-terms.html](../../src/main/resources/templates/public-views/policies/subscription-terms.html)

**Route/context:** `/policies/subscription-terms`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: this page also remains Awaiting review instead of providing subscription terms.

- [ ] W030.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W030.2 **Design, styling and motion:** Use the same accessible document pattern as terms, with clear recurring-payment and cancellation sections.
- [ ] W030.3 **Workflow, usability and features:** Align reviewed wording with actual billing states and pricing; complete sandbox purchase/cancellation verification before release.
- [ ] W030.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w031"></a>

### W031 — Platform payment policy

**Priority:** P1 · **Source:** [public-views/policies/payments.html](../../src/main/resources/templates/public-views/policies/payments.html)

**Route/context:** `/policies/payments`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: explains in-platform payments and blocked content; enforcement experience should be understandable.

- [ ] W031.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W031.2 **Design, styling and motion:** Present concise permitted/prohibited examples and a direct support path in the new document style.
- [ ] W031.3 **Workflow, usability and features:** Make blocked-message recovery clear without losing drafts; verify policy links from checkout and messaging and preserve payment-integrity controls.
- [ ] W031.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w032"></a>

### W032 — Contact support

**Priority:** P1 · **Source:** [shared-views/support/index.html](../../src/main/resources/templates/shared-views/support/index.html)

**Route/context:** `/support`.

**Coverage:** **V** — inspected current state: [13-support](evidence/2026-10-01-v2/13-support.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: an oversized hero delays the support form; useful issue categories and reply consent are present in source.

- [ ] W032.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W032.2 **Design, styling and motion:** Place issue selection and the form near the top; introduce compact case feedback and accessible validation.
- [ ] W032.3 **Workflow, usability and features:** Keep typed message on error; show a reference and confirmation after accepted submission; clarify reply expectations without promising an unimplemented SLA.
- [ ] W032.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w033"></a>

### W033 — Client dashboard

**Priority:** P1 · **Source:** [client-views/dashboard/client-dashboard.html](../../src/main/resources/templates/client-views/dashboard/client-dashboard.html)

**Route/context:** `/dashboard`.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: coaching status is prominent, but nested cards and repeated navigation dilute the next training action.

**Implementation progress, 2 October:** mobile shortcuts are native destinations before enhancement, and later action/goal sections are readable and focusable without scripts. A blocked dashboard-script request was also observed, with native Help navigation reaching support. Dates open their actual calendar day without scripts; action/goal tabs and drawers retain keyboard behaviour with scripts. Separate goal scope IDs resolve duplicate mobile/desktop IDs. Placeholder preview/countdown values remain hidden until populated. Selected light/dark phone/tablet and enhanced desktop observations are recorded below; the full page/state acceptance boxes remain open.

**3 October drawer repair:** actual client notification testing found the open phone panel at y=-542 px and only 51 px wide. The inherited open transform and dock backdrop filter created containing blocks for the fixed panel. Removed these two containing-block effects from the phone dock while retaining panel motion and the overlay. All five 390 px panels now fit the viewport (369 px wide, bottom 682 px), accept interaction and restore their own collapsed handle on Escape. The coach-response notification opens the owner's new W177 read view and changes Unread to Seen. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#saved-coaching-review-and-client-read-view--w084w177) records actual scope. Tablet/dark/RTL/landscape and complete dashboard acceptance remain open. Dashboard notification times currently use the UTC application clock while review timestamps use the local rendering zone; timezone consistency remains a shared follow-up.

- [ ] W033.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W033.2 **Design, styling and motion:** Build a premium daily workspace prioritising next session, coach message and one progress signal; use brief state transitions and optional subtle depth.
- [ ] W033.3 **Workflow, usability and features:** Give no-coach, request-pending, active-coach, rest-day and overdue states explicit next actions; keep real data authoritative across dashboard/calendar.
- [ ] W033.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w034"></a>

### W034 — Dashboard content shell

**Priority:** P1 · **Source:** [client-views/dashboard/fragments/client-dashboard-shell.html](../../src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-shell.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Source: 907-line shared shell mixes discovery, relationship, goals, help and multiple controls; live mobile stacks many panels.

**Implementation progress, 2 October:** goal/action sections use fragment links as their baseline; the script assigns tab roles, selected state and inert inactive panels after enhancement. Goal IDs include mobile/desktop scope. The seven date cards are native day links; enhanced keyboard arrows select the matching preview and preserve the Open Day destination. Calendar navigation exposed an enum-comparison render error after a local reload; the affected day/streak templates now compare stable status names and render correctly after the subsequent reload.

- [ ] W034.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W034.2 **Design, styling and motion:** Split into focused shared fragments with one action priority and consistent card density; animate disclosures with preserved scroll position.
- [ ] W034.3 **Workflow, usability and features:** Move secondary discovery/help behind purposeful entries; keep widgets backed by their existing data contracts; test empty and populated layouts.
- [ ] W034.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w035"></a>

### W035 — Dashboard member identity

**Priority:** P1 · **Source:** [client-views/dashboard/fragments/client-dashboard-identity.html](../../src/main/resources/templates/client-views/dashboard/fragments/client-dashboard-identity.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: identity fragment is separate, but role/profile/premium status also appears in navigation.

- [ ] W035.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W035.2 **Design, styling and motion:** Use a compact consistent identity treatment and show status with text as well as colour.
- [ ] W035.3 **Workflow, usability and features:** Avoid duplicating account controls; verify long names, missing images, free/premium states and public-versus-private details.
- [ ] W035.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w036"></a>

### W036 — Client profile, settings and billing

**Priority:** P1 · **Source:** [client-views/profile/profile.html](../../src/main/resources/templates/client-views/profile/profile.html)

**Route/context:** `/profile` (client-selected template).

**Coverage:** **V** — inspected current state: [18-client-profile](evidence/2026-10-01-v2/18-client-profile.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: very pale biography text is hard to read on white; source is 1519 lines and combines identity, health, customisation, settings, cards and account controls.

- [ ] W036.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W036.2 **Design, styling and motion:** Create a profile overview with clearly separated Appearance, Training, Accessibility, Billing and Security panels; preview customisation without compromising text contrast.
- [ ] W036.3 **Workflow, usability and features:** Preserve verified fields, locked birth date, uploads, unsaved changes, exports and deletion safeguards; test each nested panel as a separate flow using the subchecklist.
- [ ] W036.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w037"></a>

### W037 — Quick preference onboarding

**Priority:** P1 · **Source:** [client-views/conditions-preference/quick-preferences.html](../../src/main/resources/templates/client-views/conditions-preference/quick-preferences.html)

**Route/context:** `/select-preferences` (initial/incomplete preference state; helper-selected template).

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: starter presets exist before the full editor; presets can imply settings the user has not reviewed.

- [ ] W037.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W037.2 **Design, styling and motion:** Design concise selectable preset cards with a plain summary and visible Skip; keep motion to selection feedback.
- [ ] W037.3 **Workflow, usability and features:** Explain exactly what each preset changes; allow later adjustment; ensure accessibility defaults are meaningful and do not silently infer health conditions.
- [ ] W037.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w038"></a>

### W038 — Preference editor

**Priority:** P0 · **Source:** [client-views/conditions-preference/select-preferences.html](../../src/main/resources/templates/client-views/conditions-preference/select-preferences.html)

**Route/context:** `/select-preferences` (full editor); `/preferences` and `/preferences/edit` redirect here.

**Coverage:** **B** — runtime failed; template-binding and recovery blockers recorded above.

**Current finding/opportunity:** Runtime blocked: #fields.hasAnyErrors() at template line 33 is evaluated outside the adequate form binding context; the response fails to render.

- [ ] W038.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W038.2 **Design, styling and motion:** Repair binding context first, then organise display/accessibility, training and health-related preferences into readable sections with persistent save feedback.
- [ ] W038.3 **Workflow, usability and features:** Add a real GET render regression and invalid-submit test; retain values on failure; verify saved preferences apply across the app and reset explains its impact.
- [ ] W038.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w039"></a>

### W039 — Preference summary

**Priority:** P2 · **Source:** [client-views/conditions-preference/view-preferences.html](../../src/main/resources/templates/client-views/conditions-preference/view-preferences.html)

**Route/context:** Dormant template. A current controller/template reference trace finds no render caller; `/preferences` and `/preferences/edit` both redirect to the active `/select-preferences` flow. Preserve the dormant asset pending the wider legacy-surface decision.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** The summary has no current caller. Its missing standalone detail stylesheet does not affect the active editor. Do not import unused detail styles globally or count this asset as a verified live page. W039 remains a legacy-surface decision.

- [ ] W039.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W039.2 **Design, styling and motion:** Reuse an understandable settings summary with explicit defaults and Edit links; avoid maintaining a disconnected duplicate.
- [ ] W039.3 **Workflow, usability and features:** Trace active references and decide summary versus retirement; confirm read-only values match saved settings and preserve old entry routes.
- [ ] W039.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w040"></a>

### W040 — My trainer relationship and search

**Priority:** P1 · **Source:** [client-views/client/trainers.html](../../src/main/resources/templates/client-views/client/trainers.html)

**Route/context:** `/client/trainers`.

**Coverage:** **V** — inspected current state: [24-client-trainers](evidence/2026-10-01-v2/24-client-trainers.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: active relationship and request-disabled explanation are faint against the light background; search shares the same screen.

**Implementation progress:** Semantic current/pending/paused workspace, verified directory/profile links and search recovery are implemented. Repeated requests are idempotent; clients can withdraw only their own pending requests. Acceptance now locks before reading pending state and rejects an existing active coach instead of silently replacing them. Relationship and rendering checks pass; fresh complete-page browser acceptance remains open.

- [ ] W040.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W040.2 **Design, styling and motion:** Build a clear coaching relationship workspace with readable state, message/plan actions and secondary discovery.
- [ ] W040.3 **Workflow, usability and features:** Explain why another request is disabled while an active coach exists; confirm pending/active/paused/ended states and preserve the one-active-trainer rule.
- [ ] W040.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w041"></a>

### W041 — Alternate My Trainer template

**Priority:** P2 · **Source:** [client-views/client/my-trainer.html](../../src/main/resources/templates/client-views/client/my-trainer.html)

**Route/context:** `/client/my-trainer → /client/trainers` — preserve compatibility/history; confirm whether this template should remain.

**Coverage:** **L/S** — source inventoried; active/alternate use and runtime presentation need confirmation.

**Current finding/opportunity:** Source: /client/my-trainer redirects to /client/trainers; this alternate template needs an active-use check.

- [ ] W041.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W041.2 **Design, styling and motion:** Reuse the canonical relationship design and carry over any unique functionality after comparison.
- [ ] W041.3 **Workflow, usability and features:** Verify whether End exists only in this template; preserve safe coach-ending behaviour and compatibility redirects before retiring duplicates.
- [ ] W041.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w042"></a>

### W042 — Assigned library plan

**Priority:** P1 · **Source:** [client-views/client/assigned-plan.html](../../src/main/resources/templates/client-views/client/assigned-plan.html)

**Route/context:** `/client/assigned-plan`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: assigned workouts and programmes form a separate plan surface from /client/plan.

**Implementation progress:** Shared plan navigation now distinguishes the coach's library programme from assignment feedback and actual calendar sessions. Semantic workspace cards explain the reference-only workflow, and translated prescription labels retain the existing records. A regression verifies that foreign exercise/workout references cannot reveal another trainer's library names. Fresh responsive, theme and interaction acceptance remains open.

**Sharing continuation:** Separately shared exercises now appear with actual notes and safe video links. Library reads omit resources when the client is disabled or the active trainer is disabled, unverified or has the wrong role. These states are covered by the focused library checks; recipient browser refresh remains open.

- [ ] W042.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W042.2 **Design, styling and motion:** Present one clear coach-authored programme view with week, session sequence, coach notes and progress.
- [ ] W042.3 **Workflow, usability and features:** Identify which assignment model drives each plan; connect to calendar/session actions without creating duplicate workouts; verify missing and replaced assignments.
- [ ] W042.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w043"></a>

### W043 — Assigned workout and schedule plan

**Priority:** P1 · **Source:** [client-views/client/plan.html](../../src/main/resources/templates/client-views/client/plan.html)

**Route/context:** `/client/plan`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: this second plan surface includes client notes, feedback and completion controls.

**Implementation progress:** Labelled feedback controls retain invalid drafts through Spring Session and reject notes/feedback over the existing 1,200-character limit before mutation. Copy distinguishes assignment completion from logged training. Previously broken schedule links now lead to the owned, read-only W171 page. Real-template and ownership checks pass; browser acceptance remains open.

- [ ] W043.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W043.2 **Design, styling and motion:** Unify the visible programme vocabulary and visual hierarchy with assigned library plans while preserving their different source records.
- [ ] W043.3 **Workflow, usability and features:** Distinguish coach notes from client feedback; explain completion versus actual logged session; preserve assignment IDs, authorisation and saved feedback.
- [ ] W043.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w044"></a>

### W044 — Trainer assessment of a client

**Priority:** P1 · **Source:** [client-views/client/assessment-form.html](../../src/main/resources/templates/client-views/client/assessment-form.html)

**Route/context:** `/trainer/clients/{clientId}/assessment`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: assessment includes reliability, communication and private notes; role placement under client-views is misleading.

**Implementation progress:** Assessment reads now verify the enabled, verified trainer and actual coaching history before loading client details. Withdrawn/rejected requests never count as past coaching. Scores and bounded private notes validate before mutation; invalid drafts and success feedback survive Spring Session. Labelled fields and semantic workspace styles replace the earlier form. A focused assessment/review/rendering run passed 88 tests in 41 seconds; browser acceptance remains open.

- [ ] W044.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W044.2 **Design, styling and motion:** Create a clearly trainer-only review form with neutral wording and private-note visibility cues.
- [ ] W044.3 **Workflow, usability and features:** Verify the actual access route and who can read each field; keep subjective coaching notes private and separate from public reviews.
- [ ] W044.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w045"></a>

### W045 — Weekly client check-in

**Priority:** P1 · **Source:** [client-views/checkins/client-submit.html](../../src/main/resources/templates/client-views/checkins/client-submit.html)

**Route/context:** `/checkins/client-submit`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: template questions, week start and optional notes are gathered in one form.

**Implementation progress:** Added a focused reflection surface with labelled required questions, explicit template loading, dirty-answer confirmation and empty/no-coach guidance. Template IDs are checked against the active coach before exposing questions. Fixed blank optional dates causing server errors; invalid/duplicate submissions retain escaped drafts. Submission locks the client row and validates a current verified/enabled coach and non-future week. Real rendering verifies foreign prompt denial, blank-date save and duplicate draft retention. Browser review and full notification/concurrency acceptance remain open.

- [ ] W045.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W045.2 **Design, styling and motion:** Build a focused weekly reflection with clear progress and accessible scale controls; keep feedback animation short.
- [ ] W045.3 **Workflow, usability and features:** Keep question labels stable and typed answers on failure; confirm submit/review state, already-submitted behaviour and return to the relevant goal/coach.
- [ ] W045.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w046"></a>

### W046 — Goals overview

**Priority:** P1 · **Source:** [client-views/goals/index.html](../../src/main/resources/templates/client-views/goals/index.html)

**Route/context:** `/goals`.

**Coverage:** **V** — inspected current state: [19-goals](evidence/2026-10-01-v2/19-goals.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: milestones and filter panels push current goals down; week/month/targets tabs are a useful organising pattern.

- [ ] W046.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W046.2 **Design, styling and motion:** Put active goals and next check-in first with readable progress visuals; make filters compact and keep milestones secondary.
- [ ] W046.3 **Workflow, usability and features:** Preserve archived/type/status filters and tab state; provide clear create-goal empty states; verify dashboard goal summaries match.
- [ ] W046.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w047"></a>

### W047 — Create a goal

**Priority:** P1 · **Source:** [client-views/goals/create.html](../../src/main/resources/templates/client-views/goals/create.html)

**Route/context:** `/goals/create`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: goal creation is a separate form; measure, deadline and linked activity should be understandable together.

- [ ] W047.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W047.2 **Design, styling and motion:** Use a clear target editor with examples, units and a small outcome preview; animate valid selection only.
- [ ] W047.3 **Workflow, usability and features:** Verify required fields, start/target values and dates; explain how the goal will be measured and where check-ins happen.
- [ ] W047.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w048"></a>

### W048 — Edit a goal

**Priority:** P1 · **Source:** [client-views/goals/edit.html](../../src/main/resources/templates/client-views/goals/edit.html)

**Route/context:** `/goals/{id}/edit`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: similar structure to goal creation risks divergent validation and labels.

- [ ] W048.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W048.2 **Design, styling and motion:** Share the create-goal form component and clearly distinguish editing an existing target.
- [ ] W048.3 **Workflow, usability and features:** Protect recorded progress when targets change; test validation, unsaved work and return to the correct detail page.
- [ ] W048.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w049"></a>

### W049 — Goal detail and activity links

**Priority:** P1 · **Source:** [client-views/goals/detail.html](../../src/main/resources/templates/client-views/goals/detail.html)

**Route/context:** `/goals/{id}`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: goal summary, adherence and task/occurrence linking are combined.

- [ ] W049.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W049.2 **Design, styling and motion:** Create a progress-first detail view with readable time series and an accessible activity-link picker.
- [ ] W049.3 **Workflow, usability and features:** Explain what contributes to progress; prevent confusing duplicate links; verify archived/completed goals and link errors.
- [ ] W049.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w050"></a>

### W050 — Goal check-in history

**Priority:** P1 · **Source:** [client-views/goals/checkins.html](../../src/main/resources/templates/client-views/goals/checkins.html)

**Route/context:** `/goals/{id}/checkins`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: history and new check-in form coexist on one page.

- [ ] W050.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W050.2 **Design, styling and motion:** Show chronological progress with a compact new-entry panel and a smooth saved-state update.
- [ ] W050.3 **Workflow, usability and features:** Keep measurement units and dates visible; verify out-of-order entries, invalid values and goal ownership.
- [ ] W050.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w051"></a>

### W051 — Reusable goal chip

**Priority:** P1 · **Source:** [client-views/goals/fragments/goal-chip.html](../../src/main/resources/templates/client-views/goals/fragments/goal-chip.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: a seven-line chip needs to carry consistent status and focus behaviour across screens.

- [ ] W051.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W051.2 **Design, styling and motion:** Use compact text-plus-icon status, meaningful accessible naming and a clear focus ring.
- [ ] W051.3 **Workflow, usability and features:** Test long titles and completed/archived states wherever this fragment appears; ensure colour is not the only status signal.
- [ ] W051.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w052"></a>

### W052 — Milestones and achievements

**Priority:** P2 · **Source:** [client-views/achievements/index.html](../../src/main/resources/templates/client-views/achievements/index.html)

**Route/context:** `/achievements`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: unlocked and undiscovered milestones can become a detached gamification destination.

- [ ] W052.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W052.2 **Design, styling and motion:** Create a restrained achievement gallery tied to training consistency, with optional authored 3D badges and static equivalents.
- [ ] W052.3 **Workflow, usability and features:** Explain the real earning criteria and progress; keep private achievements private; test locked and missing milestones without fabricated rewards.
- [ ] W052.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w053"></a>

### W053 — Personal progress level

**Priority:** P2 · **Source:** [shared-views/levels/me.html](../../src/main/resources/templates/shared-views/levels/me.html)

**Route/context:** `/levels/me`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: level/progress view sits apart from goals and achievements.

- [ ] W053.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W053.2 **Design, styling and motion:** Unify it under progress with readable level requirements and a subtle milestone reveal.
- [ ] W053.3 **Workflow, usability and features:** Explain points and their connection to useful actions; avoid pressure or public exposure by default; verify the displayed totals are real.
- [ ] W053.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w054"></a>

### W054 — Leaderboard

**Priority:** P2 · **Source:** [shared-views/levels/leaderboard.html](../../src/main/resources/templates/shared-views/levels/leaderboard.html)

**Route/context:** `/levels`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: public ranking can compete with the personal coaching focus.

- [ ] W054.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W054.2 **Design, styling and motion:** Make rankings an optional compact progress view with clear period, display name and context; avoid continuous celebration effects.
- [ ] W054.3 **Workflow, usability and features:** Review participation/privacy controls and tie handling; ensure the feature rewards useful training behaviour rather than undermining coaching.
- [ ] W054.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w055"></a>

### W055 — Month calendar

**Priority:** P1 · **Source:** [shared-views/calendar/month.html](../../src/main/resources/templates/shared-views/calendar/month.html)

**Route/context:** `/calendar` (month view selected by view/state parameters).

**Coverage:** **V** — inspected current state: [16-calendar-month](evidence/2026-10-01-v2/16-calendar-month.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: several navigation/control rows delay the grid; month cells, schedules and information controls compete for attention.

- [ ] W055.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W055.2 **Design, styling and motion:** Build a clean month workspace with compact date/view controls, restrained activity indicators and an agenda alternative on mobile.
- [ ] W055.3 **Workflow, usability and features:** Keep selected date and filters across views; make every populated day keyboard accessible; verify empty dates and schedule drawer return focus.
- [ ] W055.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w056"></a>

### W056 — Week calendar

**Priority:** P1 · **Source:** [shared-views/calendar/week.html](../../src/main/resources/templates/shared-views/calendar/week.html)

**Route/context:** `/calendar` (week view selected by view/state parameters).

**Coverage:** **V** — inspected current state: [17-calendar-week](evidence/2026-10-01-v2/17-calendar-week.png), [27-calendar-week-mobile](evidence/2026-10-01-v2/27-calendar-week-mobile.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: mobile spends almost its first viewport on controls before showing the actual week.

- [ ] W056.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W056.2 **Design, styling and motion:** Use a compact week strip plus a readable agenda on mobile; keep the full week grid for suitable widths; animate date changes without shifting controls.
- [ ] W056.3 **Workflow, usability and features:** Retain view/date state, jump-to-workout and accessible labels; ensure real activity appears before advanced tools on narrow screens.
- [ ] W056.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w057"></a>

### W057 — Day planner and health panels

**Priority:** P1 · **Source:** [shared-views/calendar/day.html](../../src/main/resources/templates/shared-views/calendar/day.html)

**Route/context:** `/calendar/day/{dateStr}`.

**Coverage:** **V** — inspected current state: [22-calendar-day](evidence/2026-10-01-v2/22-calendar-day.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: daily page auto-scrolls to a tall timeline; source is 1088 lines including tasks, workouts, reflection and health.

**Implementation progress, 2 October:** section controls are native fragment links and all sections are readable before enhancement. Task creation becomes an inline, labelled form before scripts set up the dialog; native CSRF completion and task-detail navigation work. Workout titles now have existing launch destinations beneath their enhanced drawers. Empty progress/Overview states distinguish an unplanned day from a completed day; five feedback messages cover all 14 UI locales. Tab setup and day navigation use stable identifiers instead of English labels. Arrow keys stay within tabs, rapid switches cannot hide the selected panel, reduced motion suppresses tab animation, and the task dialog traps/returns focus. Calendar files now use versioned script URLs. A browser-discovered completion/reopening mismatch is fixed across list/timeline buttons, progress and accessible labels; in-flight requests are locked and failures restore state with translated feedback.

- [x] Selected native phone task creation → completion → task details, enhanced creation/completion/reopening/timeline control, saved state after reload, blocked-request rollback and retry.
- [x] Selected failed-enhancement-script fallback, English/French keyboard tabs, reduced motion, light phone/desktop and dark phone/tablet views. Original appearance, language and browser overrides restored.

Evidence: [native completed task](evidence/v2-day-native-completed-task-phone-20261002.png), [native task form](evidence/v2-day-native-task-form-phone-20261002.png), [dark tablet](evidence/v2-day-native-dark-tablet-20261002.png), [failed script/dialog](evidence/v2-day-script-failed-dialog-phone-20261002.png), [enhanced desktop](evidence/v2-day-enhanced-desktop-20261002.png). Detailed commands and scope limits are in the implementation log. Remaining work includes compact day composition/current-time behaviour, full workout logging/reminder and cross-view totals, late/grace-period state, full reflection/health workflows and complete locale/device acceptance. These milestones do not complete W057.1–W057.4.

**Subsequent workout consistency/modal repair, 2 October:** day and focus now share recurring and saved sessions, with ID deduplication and an occurrence represented once when it has a linked session. Previously the day planner omitted saved sessions outside recurring schedules. Drawer content IDs, timeline launchers and card timing updates distinguish sessions from occurrences. Actual independent records with the same numeric ID open their own content and native launch URLs from both list and timeline. The workout drawer uses a native modal dialog above the floating controls, with a 44px close target, keyboard containment, Escape and focus restoration. Native links remain the fallback. Selected final phone evidence: [occurrence drawer](evidence/v2-day-occurrence-drawer-phone-20261002.png). Complete drag-save/rollback, workout logging and cross-view lifecycle acceptance remains open.

- [ ] W057.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W057.2 **Design, styling and motion:** Create a day workspace with a short next-action summary, agenda and collapsible supporting panels; make focus/timeline modes intentional.
- [ ] W057.3 **Workflow, usability and features:** Preserve date, draft reflection and task state; verify current-time scrolling does not hide context or move keyboard focus; distinguish completed tasks from logged workouts.
- [ ] W057.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w058"></a>

### W058 — Focused day timeline

**Priority:** P1 · **Source:** [shared-views/calendar/focus.html](../../src/main/resources/templates/shared-views/calendar/focus.html)

**Route/context:** `/calendar/focus/{dateStr}`.

**Coverage:** **V** — rebuilt focused agenda inspected in the client role: [desktop](evidence/v2-focus-mixed-desktop-20261002.png), [native empty phone](evidence/v2-focus-empty-phone-20261002.png), [native completion](evidence/v2-focus-native-completed-phone-20261002.png), [all complete](evidence/v2-focus-native-all-complete-phone-20261002.png), [dark/reduced-motion tablet](evidence/v2-focus-dark-reduced-tablet-20261002.png), [native dark 320px](evidence/v2-focus-native-dark-320-20261002.png).

**Confirmed fault and repair, 2 October:** the populated original page returned HTTP 500 because it read nonexistent `CalendarTask.startTime` and `duration` properties. Its stylesheet paths were obsolete and its agenda depended on the full day script. The replacement uses the shared theme/language/security head and a dedicated 7,751-byte compiled bundle, while suppressing the navbar, footer, assistant and dock. A read-only presentation model combines real task times and saved session/occurrence slots, keeps typed identities separate, sorts the agenda and provides completion/next-action states. Native task forms retain CSRF protection and return to the same focused date; completed items and task notes use native disclosures. All 17 new messages cover the 14 UI locales. Entrance/disclosure motion is restrained and honours reduced motion.

- [x] Selected empty, mixed, completed and reopened states; native planning → focus completion → completed disclosure with scripts disabled. Workout links use the established launch/review routes.
- [x] Selected light desktop/phone, dark tablet/320px native reflow, reduced motion, keyboard date/disclosure controls and native dated exit.
- [x] Enhanced exit restores the chosen date, timeline tab, saved position and focus-mode entry link. The final `20261002v2r` browser check returned to 3 October with the link focused and scroll at 642px versus 645px on entry: [return position](evidence/v2-focus-return-desktop-20261002.png). The dated native exit was also verified with scripts disabled.
- [x] Real rendering/integration coverage for owned mixed items, actual saved timeline slots, escaped title/notes, distinct identities, native completion, CSRF rejection, safe return allowlisting, day/focus totals and exclusion of an occurrence's linked session.
- [ ] Complete locale/RTL, zoom, expired-session, slow/failing-request and recurring/logging/reminder lifecycle acceptance. An 844×390 landscape DOM check found no horizontal overflow and a visible 44px exit, but capture attempts were cropped/blank; those images are excluded from accepted evidence and visual landscape acceptance remains open. Token contrast checks do not prove every rendered/error/overlay combination.

- [ ] W058.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W058.2 **Design, styling and motion:** Create a distraction-reduced agenda with clear date, next item and exit-to-day action; suppress unnecessary decorative motion.
- [x] W058.3 **Workflow, usability and features:** Preserve the chosen date and scroll position on exit; verify empty days and keyboard navigation independently from the full planner.
- [ ] W058.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w059"></a>

### W059 — Task detail and reminders

**Priority:** P1 · **Source:** [shared-views/calendar/task-detail.html](../../src/main/resources/templates/shared-views/calendar/task-detail.html)

**Route/context:** `/calendar/task/{id}`.

**Coverage:** **V** — selected synthetic client states observed, including native phone editing/rules/deletion, desktop, dark tablet/narrow view, French and Arabic RTL. Whole-page release acceptance remains open.

**Current finding/opportunity:** Source: task completion, delete, grace periods and warning rules share a small detail page.

**Implementation and repairs, 2 October:** replaced the nested gradient/card layout with the shared 2.0 task workspace, dated navigation, a real title/time/notes editor and a separate advanced rules disclosure. Native CSRF completion returns to this task; the timeline link uses the actual native panel fragment. Invalid title/time/grace input now gives translated feedback and preserves saved task data; invalid edit title/notes are retained for correction. Rule removal is scoped to the owned task, matching rules are reused, and completion triggers must reference another owned task on the same date. Task deletion explains its effect on rules belonging to other tasks and requires a native acknowledgement before the enhanced confirmation. The underlying service removes incoming/outgoing rules before deleting the task.

Time rules now record their trigger even without a grace period; future trigger timestamps cannot start grace early. The page displays planned/completed/in-grace/late state, saved grace minutes and the actual server timezone (`Europe/London` in this preview). Copy explains that these are calendar status rules, rather than promising email/SMS/push delivery. User titles are isolated with `bdi`; title/notes fields choose their direction from the entered content. Forty-four task messages cover all 14 UI locales, including context-specific time/type nouns after the French/Arabic browser check found ambiguous inherited labels. Dedicated compiled CSS is 6,637 bytes; shared shell/theme assets and existing routes remain in use, at cache `20261002v2t`.

**Evidence:** [desktop](evidence/v2-task-editor-desktop-20261002.png), [native phone](evidence/v2-task-native-editor-phone-20261002.png), [native populated rules and grace](evidence/v2-task-native-rules-phone-20261002.png), [actual past-date late state](evidence/v2-task-native-late-phone-20261002.png), [native deletion acknowledgement](evidence/v2-task-native-delete-phone-20261002.png), [enhanced confirmation](evidence/v2-task-delete-confirm-phone-20261002.png), [dark reduced-motion tablet](evidence/v2-task-dark-reduced-tablet-20261002.png), [native dark 320px](evidence/v2-task-native-dark-320-20261002.png), [French](evidence/v2-task-french-rule-phone-20261002.png), [Arabic RTL](evidence/v2-task-native-arabic-phone-20261002.png). Selected widths have no document overflow and measured primary/navigation/rule controls meet 44px targets. Native required-title validation, safe notes editing, completion/reopening, duplicate time rule, owned completion trigger, in-grace state, past time-rule activation without grace, late state, rule/grace removal and task deletion were observed. Enhanced Escape cancels deletion and returns focus to Delete; confirmed deletion removes the dependent rule on the other task, preserves that task and makes the deleted detail redirect to the calendar. Synthetic theme/language were restored; final browser reconnection timed out, so final emulation restoration and any additional captures must be rechecked on the next working browser connection.

**Verification:** 31 tests / five suites, no failures/errors/skipped, 39 seconds; one real integration/render follow-up for the visible grace state and bootJar, 36 seconds. Subsequent translation/direction/whitespace-only packaging and current archive inspection pass. All 42 localisation bundles pass. The focused tests cover native rendering/CSRF fields, safe output, invalid edit/time/grace feedback, owned triggers, duplicate rules, foreign-user/foreign-rule rejection, completion-return, grace removal and deletion dependencies. These runs are separate, not cumulative or a replacement whole-app regression.

- [ ] W059.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W059.2 **Design, styling and motion:** Use an accessible detail drawer/page with clearly separated task content, schedule and advanced reminders.
- [ ] W059.3 **Workflow, usability and features:** Explain grace periods in plain language; confirm deletion and reminder impact; test invalid times, timezone transitions and task ownership.
  - Selected native/enhanced rules, deletion and ownership/validation checks pass. DST gap/overlap boundaries, alternative server timezone, all activity/required-log cases, expired/failed/slow submissions, zoom and complete landscape/device/locale acceptance remain open. Concurrent duplicate-rule submissions have no new database uniqueness constraint; the normal repeated submission is covered.
- [ ] W059.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w060"></a>

### W060 — Month schedule deployment drawer

**Priority:** P1 · **Source:** [shared-views/calendar/fragments/schedule-drawer-month.html](../../src/main/resources/templates/shared-views/calendar/fragments/schedule-drawer-month.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — selected current light desktop/phone and shared dark/reduced-motion tablet states accepted; complete acceptance remains open.

**Current finding/opportunity:** The common month/week workspace now shows exact date bounds, real planned workouts and conflict consequences. Invalid repeat settings, lost weekdays, duplicate reapplications, destructive replacement/undo, overlapping footers and an inaccessible Undo layer were repaired. Remaining dynamic translation, advanced interaction and concurrency acceptance is listed below.

- [ ] W060.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Selected live month states: exact October bounds, fortnightly preview, empty non-matching day, invalid interval, editable selected date, reviewed deployment and successful Undo. Native apply validation and cross-type conflicts/history have real integration checks. Full role/locale/error/expiry/advanced interaction acceptance remains open.
- [x] W060.2 **Design, styling and motion:** One reusable deployment workspace, actual planned-date preview, conflict summary, visible selected dates and short reduced-motion-aware transition. Accepted current desktop/phone and shared tablet evidence: `v2-deployment-month-desktop-20261002.png`, `v2-deployment-month-phone-20261002.png`, `v2-deployment-applied-phone-20261002.png`, `v2-deployment-week-reduced-tablet-20261002.png` and `v2-deployment-week-dark-reduced-tablet-20261002.png` under `evidence/`. Footer remains outside the scrolling body; tablet panel fills the viewport; Cancel and Undo remain reachable.
- [ ] W060.3 **Workflow, usability and features:** Preserve start/end and idempotence; make repeat-application consequences clear; verify focus trapping, Escape and return to the triggering control.
  - Exact start/end, original-weekday repeats, monthly/leap-year boundaries, deliberate duplicate entry multiplicity, sequential idempotence, foreign Undo and protected history checks pass. Standalone workout/task conflicts and linked strength records are covered without counting a linked session twice. Replacement only removes pending schedule workouts. Keyboard wraps between Visible range and Cancel; Escape makes the drawer inert and returns to Schedules. Same-schedule and ordered occurrence locks are implemented; no orchestrated PostgreSQL/concurrent timing or multi-instance Undo proof is claimed.
  - 3 October: translated repeat-unit accessible name and result toast implemented in all fourteen UI locales. Normal and dormant confirmation paths share an awaited Undo handler; dormant simulation supplies an explicit recurrence end and ignores stale responses. Current live normal custom-repeat deployment adds one disposable workout and Undo removes it. Dormant controls are not rendered by the common template and still have no accepted runtime coverage. Still open: complete role/locale/RTL/zoom/320px/landscape states, storage-denied/slow/error/retry/expiry UI, long names, filters/pinning/drag and remaining dynamic translation.
- [ ] W060.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w061"></a>

### W061 — Week schedule deployment drawer

**Priority:** P1 · **Source:** [shared-views/calendar/fragments/schedule-drawer-week.html](../../src/main/resources/templates/shared-views/calendar/fragments/schedule-drawer-week.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — selected light/dark reduced-motion tablet states accepted; complete acceptance remains open.

**Current finding/opportunity:** Week uses the same date planner and component as month. Current full-height panel, visible-week context and exact custom date range have selected browser proof; complete cross-view and advanced acceptance remains open.

- [ ] W061.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Week renders the same component as month with week-specific labels/range; off-screen week buffers are inert. Earlier next-week transition/semantic hiding proof is retained. Current browser verifies visible 28 September–4 October and custom 6–20 October yielding three planned Tuesday workouts. Dark/light 768 × 1024 captures are accepted; no horizontal overflow, panel top 0/height 1024 and reduced-motion transition verified.
- [x] W061.2 **Design, styling and motion:** Share the common deployment component while passing week-specific range context. Common `scheduleDrawer(viewMode)` implemented; month/week rendering and shared-surface contracts pass. Deployment acceptance remains tracked separately.
- [ ] W061.3 **Workflow, usability and features:** Check selected week, custom ranges, conflict handling and applied-result counts against the month implementation.
  - Common month/week rendering in English/French/Arabic and backend deployment consequences pass in the ten-test integration gate. Current week custom-range result agrees with the exact planner. Full populated role/locale/RTL/native/error/conflict browser acceptance remains open; selected successful phone apply/Undo was performed from month.
- [ ] W061.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w062"></a>

### W062 — Calendar consistency indicator

**Priority:** P2 · **Source:** [shared-views/calendar/fragments/daily-streak-bar.html](../../src/main/resources/templates/shared-views/calendar/fragments/daily-streak-bar.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — selected current light desktop/phone and dark reduced-motion tablet states accepted; full acceptance remains open.

**Current finding/opportunity:** The former unexplained streak is now a quiet fourteen-day logged-completion strip with real dates, visible counts, translated accessible names and native completion details. Missing required logs prevent an earned completion; a scheduled workout and its linked strength log count once. Independent records with coincident numeric IDs remain separate. Empty days are neutral, not achievements or failures.

**Implementation progress, 3 October:** all eighteen history messages render in all fourteen UI locales. The raw day planner and circular indicator describe marked completion; history explicitly includes required logs. Completing/reopening a task updates history and the ring; a deliberately failed save restores the previous counts. Modern source-linked completed strength sessions satisfy the occurrence log requirement. Existing logs on unfinished tasks no longer appear as missing logs. Current cache: `20261003v2c`; commands, files and limitations: [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md).

- [ ] W062.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Real premium-client populated and sparse history, native empty-day navigation and native details tested. All fourteen UI locales have actual server rendering; complete role/locale/RTL/browser-state acceptance remains open.
- [x] W062.2 **Design, styling and motion:** Quiet day markers with dates/counts, selected-day emphasis, full accessible descriptions, native disclosure and keyboard focus. No achievement animation added. Desktop uses fourteen columns, phone/tablet seven and 320px four; measured phone targets exceed 44px. Accepted current pixels: `v2-history-desktop-20261003.png`, `v2-history-phone-20261003.png`, `v2-history-dark-tablet-20261003.png` in `evidence/`. Reduced motion, tooltip Escape and colour-independent detail text observed.
- [ ] W062.3 **Workflow, usability and features:** Define what earns a day and how rest days behave; test sparse data, missed days and colour-independent recognition.
  - Complete means every planned item is completed with required logs; no planned items means No items. Missing-log, modern linked-session and independent ID-collision cases pass focused tests. Browser verifies partial 2/3, active 1/5, empty 0/0, task reopening, failed-save rollback and native date navigation. Complete/missed/future/unavailable states and explicit rest-day scheduling still need the complete browser/role acceptance pass; this component does not invent a rest-day classification.
- [ ] W062.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w063"></a>

### W063 — Training entry and session history

**Priority:** P1 · **Source:** [shared-views/workout-management/index.html](../../src/main/resources/templates/shared-views/workout-management/index.html)

**Route/context:** `/workout-management`.

**Coverage:** **V** — inspected current state: [20-workout-management](evidence/2026-10-01-v2/20-workout-management.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: scheduled workouts, session history and personal logs are accessible but arranged as large utility cards.

**Implementation progress, 3 October:** owned completed history is filtered in the database before its bounded result limit, ordered by date and ID. Newer unfinished sessions no longer hide older completed records. A real database/render regression verifies sixteen completed records behind twenty-two newer unfinished ones, twelve returned in stable order, empty non-positive limits and another trainer's private record excluded.

The rebuilt training launch now features one meaningful next workout, prioritises an existing started session and keeps further workouts in native details. Upcoming workouts, completed sessions and editable exercise entries use compact, separate lists with complete accessible action names. Actual coach-template provenance identifies coach-assigned calendar groups. Each source opens its real calendar day; saved sessions remain reachable after a source is removed, including independent sessions without a workout object. Planned exercise counts no longer masquerade as saved sets: only real saved sets produce progress. Twenty-four messages were added to all fourteen UI locales; no JavaScript or dependency was added for this page.

Actual local browser journey: launch, add a second set, save 42.5 kg × 8, return to **1 of 2 / 50%**, resume with values intact, save 40 kg × 8, then review the completed **2 of 2 / 660 kg** history entry. Completing the last set routes automatically to the completion summary; no separate Finish click is claimed. The final CSS iteration keeps the desktop Start/Resume control at y572.86–620.86, above the dock. Final light desktop/phone, dark reduced-motion tablet and Arabic RTL tablet images were inspected: [desktop](evidence/v2-training-launch-desktop-20261003.png), [phone](evidence/v2-training-launch-phone-20261003.png), [dark tablet](evidence/v2-training-launch-dark-tablet-20261003.png), [Arabic tablet](evidence/v2-training-launch-arabic-tablet-20261003.png). No document overflow at 320/390/768/1280 widths; 320px requires normal scrolling. Native details opens with Enter and a 3px focus outline, and real workout launch works with scripting disabled. Full role/state/contrast/zoom/landscape/failure acceptance remains open.

Focused real-render/query/set-save/performance/package gate: **14 tests / 3 suites, 51s**, zero failures/errors/skipped. After the CSS/shared-shell repair, the separate overlapping performance/package gate passed **8 tests / 1 suite, 25s**. CSS build and all 42 localisation bundles passed. Cache **20261003v2f**; final executable inspection verifies 24 source resources, 4 class files, strict UTF-8, all locale keys and exclusion of the build-only fixture. Details and changed-file groups are in the [3 October checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md).

- [ ] W063.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W063.2 **Design, styling and motion:** Create a training launch surface that gives today's workout one primary action and keeps upcoming/history in compact lists.
- [ ] W063.3 **Workflow, usability and features:** Clarify coach-assigned versus personal sessions and completed sessions versus editable logs; retain consistent links to calendar and source workouts.
- [ ] W063.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w064"></a>

### W064 — Scheduled workout execution

**Priority:** P1 · **Source:** [shared-views/workout-session/session.html](../../src/main/resources/templates/shared-views/workout-session/session.html)

**Route/context:** `/workout-session/{sessionId}`.

**Coverage:** **V** — current light desktop/phone and dark reduced-motion tablet screenshots inspected as pixels on 3 October; focused owned save/resume/failure/deletion/finish journeys verified. Full Q01–Q10 remains open.

**Current finding/opportunity:** The redesigned player groups actual session progress, exercise context and labelled set values. Failed enhanced saves retain drafts; other unsaved sets block actions that navigate away. The existing cardio launch still uses generic weight/repetition sets: dedicated duration/distance support needs a defined data contract and separate acceptance.

- [ ] W064.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Current evidence: owned client player, saved/unsaved/saving/unconfirmed status, exact server summaries, optional native timer/exercise details, labelled values/actions and progressive native POSTs. Thirteen named messages render across all fourteen UI locales. Foreign saves, invalid numbers/oversized notes, duplicate save requests and late updates to finished sessions are rejected or safely bounded by focused tests. Complete role/state/theme/contrast/zoom/landscape, storage-denied/slow/expired-session failure and exercise-type/media review remain open.
- [x] W064.2 **Design, styling and motion:** Build a full-focus workout interface with large thumb-friendly inputs, current exercise, rest timer and visible saved status; optional exercise media is user-controlled.
  - Accepted current `v2-player-{desktop,phone,dark-tablet}-20261003.png` and active `v2-player-phone-set-20261003.png` under `evidence/`. No overflow at 320/390/768/1280px; set inputs are 51.59px high, actions at least 44px. Desktop save controls end at y629.19, above the dock at 1280 × 720. Native disclosures retain keyboard access; optional rest timer is compact/closed by default. No exercise media was introduced or presented as available.
- [x] W064.3 **Workflow, usability and features:** Verify save/retry, refresh/resume, duplicate sets, finish and accidental deletion; confirm session data survives failures before promising offline behaviour.
  - A deliberately blocked owned save kept all typed values and restored controls; retry saved the same set. Another dirty set blocks Finish; saving one draft retains the other. Escape cancels deletion and returns focus; approved middle-set deletion leaves stable IDs and renumbers Set 3 to Set 2. Actual 42.5 kg × 8 resumes at 1/2 / 50%; saving 40 kg × 8 finishes at 2/2 / 660 kg. Script-disabled native save works. Explicit partial finish confirms its meaning and leaves the set incomplete at 0/1, with saved notes intact. Timer start/pause survives reload. This proves these local journeys, without promising offline persistence or completing the wider failure matrix.
- [ ] W064.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.
  - Focused real-render/ownership/save/performance gate: 14 tests / 3 suites, no failures/errors/skipped, 49s; final CSS passed and resource-only packaging passed in 10s. All 42 locale bundles retain exact parity; executable matches 27 resources / 5 classes, strict UTF-8, cache `20261003v2k`; build-only fixture excluded. Full evidence/changed-file groups: [3 October checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md).


<a id="w065"></a>

### W065 — Scheduled workout completion

**Priority:** P1 · **Source:** [shared-views/workout-session/complete.html](../../src/main/resources/templates/shared-views/workout-session/complete.html)

**Route/context:** `/workout-session/{sessionId}/complete`.

**Coverage:** **V** — actual client results inspected on desktop, phone and dark/Arabic RTL tablet; complete Q01–Q10 acceptance remains open.

**Current finding/opportunity:** Finished sessions now keep saved entries distinct from completed sets. The scheduled date is labelled accurately; the model does not record an actual finish timestamp. No new points, completion write or imaginary next workout occurs on viewing results.

- [ ] W065.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Earlier populated-summary HTTP 500 and escaped-set-note repairs retained. Current compact result shows actual counts, progress, recorded volume and native per-exercise details. Only every saved set marked complete receives the success treatment; empty and partial finished sessions say Session saved. Incomplete percentages cannot round to 100% (199/200 is 99%). Volume explicitly includes saved unfinished entries.
  - Twenty-one result messages supplied to all fourteen UI locales. Actual scheduled dates/status/count copy is localised; mixed-language saved notes use direction isolation. User exercise/plan names remain original data. Native details, back/day links and protected coach form remain usable with scripting disabled.
  - Real next workout comes from owned unfinished training within the next three weeks, skipping empty shells/completed/foreign records. Coach check-in follows existing active-link/template availability; without a template the protected existing conversation-opening POST opens the actual coach thread, creating an empty thread if needed, without sending a message. Existing archived-template rules are unchanged and need separate lifecycle review.
  - Evidence: [desktop](evidence/v2-result-desktop-20261003.png), [phone](evidence/v2-result-phone-20261003.png), [dark tablet](evidence/v2-result-dark-tablet-20261003.png), [Arabic RTL](evidence/v2-result-arabic-tablet-20261003.png), [empty](evidence/v2-result-empty-phone-20261003.png), [partial](evidence/v2-result-partial-phone-20261003.png). Actual 2/2 / 660 kg, empty 0/0 and explicit partial finish retain owned history and values. No-JS disclosure/coach navigation, 320px reflow and 44px actions checked. Full roles/state/contrast/200% text/landscape/error/expiry matrix remains open.
- [x] W065.2 **Design, styling and motion:** Concise actual summary, native exercise details, genuine next workout and available coaching entry; success styling only for confirmed sets. No extra page script, motion dependency or schema.
- [x] W065.3 **Workflow, usability and features:** Core local zero-set/partial/full/history/next-workout/coach workflows checked. Completion/counts remain server-authoritative; no points are awarded by this result page. Broader role/failure/template-lifecycle acceptance stays in .1/.4.
- [ ] W065.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.
  - Final functional/render/ownership/native-save/performance gate: 16 tests / three suites, zero failures/errors/skipped, 48s. Final note-direction/cache resource packaging passed 14s; archive exactly matches 29 resources / six classes with strict UTF-8, twenty-one result keys per locale and build-only fixture exclusion. CSS and all 42 bundle/placeholder checks passed. Cache `20261003v2o`. See [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md) for limitations and reload evidence.


<a id="w066"></a>

### W066 — Personal workout library and builder

**Priority:** P1 · **Source:** [trainer-views/workouts/index.html](../../src/main/resources/templates/trainer-views/workouts/index.html)

**Route/context:** `/workouts`.

**Coverage:** **V** — current desktop, phone, dark tablet and Arabic RTL browser screenshots inspected; core local create/library workflows exercised. Full acceptance remains open.

**Current finding/opportunity:** Browser testing confirmed that creating a workout discarded every exercise: the controller accepted only name/description. Creation now binds all rows and resolves public/owned custom references in the service before mutation. The redesigned studio has searchable owned workouts, a server-rendered exercise library and labelled, reorderable builder rows. Actual counts replace fabricated duration/English count copy; native forms have one CSRF token. Saving explicitly does not schedule or publish a workout. Custom Save now uses the existing API; unsupported metadata controls were removed. Saved player sessions protect their template from deletion.

- [ ] W066.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Core evidence: [3 October checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#personal-workout-studio-follow-up). Desktop/390px/320px, dark 820px tablet, Arabic RTL tabs, actual reorder/remove/create/custom retry, zero rest, retained notes, empty drafts and protected/unused deletion checked. Full roles/states, scripting-disabled controls, expiry/offline and reduced-motion runtime acceptance remain open.
- [x] W066.2 **Design, styling and motion:** Coherent studio, searchable actual data, distinct builder, draft-preserving tabs, keyboard reorder, shared tokens and a short fade with a reduced-motion CSS fallback. Fresh screenshots show the final cache `20261003v2q`; no reduced-motion runtime screenshot is claimed.
- [x] W066.3 **Workflow, usability and features:** Account ownership/scheduling explained; create persists ordered rows and bounded values; errors retain drafts; references are resolved against ownership; zero rest retained; custom save/retry and favourites work locally; empty drafts cannot start. Twelve focused tests passed across two suites in 41 seconds, including real rendering of all three modes in fourteen locales. The edit/player visual rebuilds remain W067/W068.
- [ ] W066.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w067"></a>

### W067 — Edit personal workout

**Priority:** P1 · **Source:** [trainer-views/workouts/edit.html](../../src/main/resources/templates/trainer-views/workouts/edit.html)

**Route/context:** `/workouts/{id}/edit`.

**Coverage:** **V** — current desktop, phone, dark tablet and Arabic RTL editor screenshots inspected; core local editing exercised. Full acceptance remains open.

**Current finding/opportunity:** The old editor had unlabelled fields, duplicated CSRF markup and unstable names after removing a row. The shared studio row presentation now provides labelled fields and bounded values, native and enhanced add/remove/reorder controls, contiguous names and visible save feedback. Submitted row IDs are validated against the owned template before any mutation, preserving existing row identities during editing. Started session set snapshots remain unchanged.

- [ ] W067.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Core evidence: [desktop](evidence/v2-workout-editor-desktop-20261003.png), [phone](evidence/v2-workout-editor-phone-20261003.png), [phone controls](evidence/v2-workout-editor-phone-controls-20261003.png), [dark tablet](evidence/v2-workout-editor-dark-tablet-20261003.png), [Arabic RTL phone](evidence/v2-workout-editor-arabic-phone-20261003.png). Actual create, Enter save, keyboard reorder, middle-row removal, new manual row and final save retained the ordered values, zero rest and escaped notes. At 320px no horizontal overflow; inputs 48–50px; focused Save bottom 567px above dock top 759px. Full role/state/contrast/200% text/landscape/expiry/offline/no-script/motion/dirty-dialog matrix remains open.
- [x] W067.2 **Design, styling and motion:** Shared structured exercise fieldsets, explicit labels/units, large controls, responsive layout and keyboard reorder/focus. Existing shared light/dark tokens and RTL layout retained; immediate row movement adds no decorative animation.
- [x] W067.3 **Workflow, usability and features:** Core add/remove/reorder/save works in the local browser; Enter saves rather than invoking a row command. Existing row identifiers and started set snapshots are protected by database tests. Foreign/duplicate row IDs and invalid values retain draft feedback without database mutation. Native draft commands are verified through real server form tests; browser no-script and dirty-navigation dialog acceptance remain open.
- [ ] W067.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.
  - Focused real database/render/shell gate: 15 tests / two suites, zero failures/errors/skipped, 44s. All fourteen editor locales render; 42 bundles retain exact key/placeholder parity. Final archive matches 35 resources / six classes, strict UTF-8, 28 studio keys per each of fourteen UI locales, fixture excluded; cache `20261003v2r`. See [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#personal-workout-editor-follow-up).


<a id="w068"></a>

### W068 — Personal workout studio

**Priority:** P1 · **Source:** [trainer-views/workouts/start.html](../../src/main/resources/templates/trainer-views/workouts/start.html)

**Route/context:** `/workouts/studio/{sessionId}`, `/workouts/{id}/start`.

**Coverage:** **V** — fresh final-cache desktop/phone/dark/Arabic player and start-confirmation screenshots inspected; core set/save/resume/rollup workflows exercised. Full acceptance remains open.

**Current finding/opportunity:** This uses the separate `Workouts.WorkoutSession` model. The old template used Thymeleaf’s reserved HTTP-session context, producing an empty heading, missing session ID and malformed save/goal URLs. It now uses an explicit `playerSession` model, actual counts/progress and labelled native set forms. GET Start no longer creates records; protected POST starts or resumes and redirects to the canonical session. The old video job fabricated fixed rep/tempo/confidence results without analysing a clip; it has been removed. Recordings remain available for private review, with an explicit unavailable-analysis explanation. Stored historical placeholder scores are retained in the database but are never exposed as analysis.

- [ ] W068.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
  - Actual set Save, 340 kg / 50% rollup, reload/resume, completed 100%, reopening, clearing weight/reps, zero rest and timer start/pause/resume/reset checked locally. Final Start button POST and legacy GET confirmation/resume checked. 320px has no horizontal overflow; focused Save bottom 472px clears dock top 759px. Core final screenshots: [desktop](evidence/v2-personal-player-desktop-20261003.png), [phone](evidence/v2-personal-player-phone-20261003.png), [phone controls](evidence/v2-personal-player-phone-controls-20261003.png), [dark tablet](evidence/v2-personal-player-dark-tablet-20261003.png), [Arabic RTL](evidence/v2-personal-player-arabic-phone-20261003.png), [recording disclosure](evidence/v2-personal-recording-phone-20261003.png), [start confirmation](evidence/v2-personal-start-confirmation-20261003.png). Counts remain 1 / 2 in RTL. Camera footage, native upload/removal in the browser, no-JS/reduced-motion, all roles/states/contrast/200%/landscape/error/expiry/concurrency remain open.
- [x] W068.2 **Design, styling and motion:** Shared scheduled-player hierarchy and large labelled controls, actual progress and volume, native exercise/goal disclosures and secondary recording disclosure. Rest controls appear with enhancement; no extra animation library or decorative motion.
- [ ] W068.3 **Workflow, usability and features:** Core owned set/native/API saves and sequential resume are verified. Partial API callers retain patch semantics; full player submissions can clear values. Invalid/non-finite/negative values, oversized notes and overflowing volume are rejected before mutation. Reopening clears the completion timestamp. Native recording storage/removal and historical score suppression have real integration/unit proof; camera capture/Stop, playable clips and browser upload/removal still require acceptance. No automated form analysis is claimed.
- [ ] W068.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.
  - Final protected-start/editor/library/player/recording integration gate: 12 tests / one suite, zero failures/errors/skipped, 42s. Earlier main 25-test / three-suite gate passed 47s; targeted recording follow-up passed one test in 34s. These overlap and are not cumulative suite acceptance. CSS, JS syntax and all 42 bundle/placeholder checks passed. Final executable matches 38 resources / fourteen classes, strict UTF-8, thirteen personal-player keys per each of fourteen UI locales, fixture excluded, cache `20261003v2u`. [Prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#personal-workout-player-follow-up).


<a id="w069"></a>

### W069 — Workout search component

**Priority:** P1 · **Source:** [trainer-views/workouts/fragments/searchbar.html](../../src/main/resources/templates/trainer-views/workouts/fragments/searchbar.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — core browser behaviour and current desktop/phone/dark/Arabic viewport inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Three labelled unique searches now share clear/count/no-result behaviour, retain searches through native Save and clear with Escape. Verified in the real W078 parent; missing-fragment rendering repaired. [Current checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-workout-composer-and-shared-fragments-follow-up) records desktop/phone/dark/Arabic viewport evidence and the 32-test gate.

- [ ] W069.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W069.2 **Design, styling and motion:** Use a labelled compact search control with clear/reset feedback and a stable results region.
- [x] W069.3 **Workflow, usability and features:** Test unique IDs when multiple searchbars exist, keyboard clear and no-results messages; keep search state during editing.
- [ ] W069.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w070"></a>

### W070 — Workout list and detail fragments

**Priority:** P1 · **Source:** [trainer-views/workouts/fragments/workout-frags.html](../../src/main/resources/templates/trainer-views/workouts/fragments/workout-frags.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — core browser behaviour and current desktop/phone/dark/Arabic viewport inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Create/edit callers use one native editor retaining field names, actual owned selections, notes and confirmed save feedback. Native add/remove do not save the draft; details open on demand. Final browser Remove verified after fixing form-name masking. Catalogue/custom groups have no persisted mixed order; ordering remains implementation work. See the [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-workout-composer-and-shared-fragments-follow-up).

- [ ] W070.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W070.2 **Design, styling and motion:** Consolidate equivalent markup into shared workout list/detail components with consistent hierarchy.
- [x] W070.3 **Workflow, usability and features:** Verify both existing variants before consolidation; retain exercise selection, field names and save feedback across callers.
- [ ] W070.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w071"></a>

### W071 — Workout display templates

**Priority:** P2 · **Source:** [trainer-views/workout-templates/index.html](../../src/main/resources/templates/trainer-views/workout-templates/index.html)

**Route/context:** `/workout-templates`.

**Coverage:** **V** — current desktop/phone/dark/Arabic pixels and core workflows verified; full Q01–Q10 remains open. See [3 October checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#workout-display-studio-follow-up--w071w072).

**Current finding/opportunity:** Source: personal and built-in templates have preferred/delete actions; this is presentation customisation rather than a training programme.

- [ ] W071.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W071.2 **Design, styling and motion:** Show actual session-layout previews and clearly label built-in versus user templates.
- [x] W071.3 **Workflow, usability and features:** Explain where preferred layout applies; protect in-use templates on deletion; do not confuse display templates with coach programmes.
- [ ] W071.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


**Remaining fidelity requirement:** Saved component order and Timer/Notes modules are blueprint previews; live players currently apply presentation preferences only. Complete live placement and accurate preview parity before full acceptance. Browser no-JS/reduced-motion/drag/dirty-dialog, full role/failure/concurrency, 200% text/landscape and native-device checks remain open.

<a id="w072"></a>

### W072 — Workout layout editor

**Priority:** P2 · **Source:** [trainer-views/workout-templates/builder.html](../../src/main/resources/templates/trainer-views/workout-templates/builder.html)

**Route/context:** `/workout-templates/builder`, `/workout-templates/builder/{id}`.

**Coverage:** **V** — current desktop/phone/dark/Arabic pixels and core workflows verified; full Q01–Q10 remains open. See [3 October checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#workout-display-studio-follow-up--w071w072).

**Current finding/opportunity:** Source: Components/Canvas/Properties editor exposes configJson and layoutType.

- [ ] W072.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W072.2 **Design, styling and motion:** Create a polished layout studio with an actual preview, clear inspector and a keyboard alternative to placement; motion follows selection.
- [x] W072.3 **Workflow, usability and features:** Validate configuration and save/reload parity; provide undo/reset and graceful unsupported configurations; keep the JSON contract internal.
- [ ] W072.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


**Remaining fidelity requirement:** Saved component order and Timer/Notes modules are blueprint previews; live players currently apply presentation preferences only. Complete live placement and accurate preview parity before full acceptance. Browser no-JS/reduced-motion/drag/dirty-dialog, full role/failure/concurrency, 200% text/landscape and native-device checks remain open.

<a id="w073"></a>

### W073 — Schedule control centre

**Priority:** P1 · **Source:** [trainer-views/schedule/list.html](../../src/main/resources/templates/trainer-views/schedule/list.html)

**Route/context:** `/schedules`.

**Coverage:** **V** — current desktop/phone/dark/RTL pixels and core native workflows verified; full Q01–Q10 remains open. See [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-control-centre-follow-up--w073).

**Current finding/opportunity:** Source: 400-line page mixes active schedule, browse, calendar visibility and logging requirements.

- [ ] W073.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W073.2 **Design, styling and motion:** Create a schedule workspace with a clear active plan, concise library rows and contextual status; remove duplicated explanatory panels.
- [x] W073.3 **Workflow, usability and features:** Explain active versus shown-on-calendar versus requires-logging; verify toggles, archived schedules and changing active schedule without losing existing occurrences.
- [ ] W073.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


**Acceptance limits:** Several plans/windows can run concurrently; hidden windows preserve records rather than providing a separate archive model. Requires logging now requests logs on the day card without changing completion rules. Browser all-calendar/no-JS/reduced-motion, full role/failure/concurrency/PostgreSQL and native-device acceptance remain open.

<a id="w074"></a>

### W074 — Schedule builder

**Priority:** P1 · **Source:** [trainer-views/schedule/builder.html](../../src/main/resources/templates/trainer-views/schedule/builder.html)

**Route/context:** `/schedules/builder`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Native typed composer now imports actual owned workout movements, preserves draft rows across settings/templates, exposes invalid cycles and supports keyboard commands/Undo. Daily/custom/NONE calendar planning is covered. Final 44-test gate/package and local native/phone/RTL evidence passed; W075 follow-up now restores custom day eight and actual deployment status; see its later checkpoint. See [3 October composer checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-composer-follow-up--w074). Full save/reopen, visual cycle preview and Q01–Q10 acceptance remain open.

- [ ] W074.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W074.2 **Design, styling and motion:** Use a step-based schedule composer with a live week/rotation preview, accessible placement and a stable properties panel.
- [ ] W074.3 **Workflow, usability and features:** Preserve payload semantics and draft edits; validate custom cycle lengths, empty days, templates and save/reopen parity.
- [ ] W074.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w075"></a>

### W075 — Schedule entries and application

**Priority:** P1 · **Source:** [trainer-views/schedule/add-entry.html](../../src/main/resources/templates/trainer-views/schedule/add-entry.html)

**Route/context:** `/schedules/{id:\\d+}/entries`.

**Coverage:** **V** — current core native/desktop/phone/dark/RTL behaviour inspected; complete acceptance remains open.

**Current finding/opportunity:** All custom days and actual deployment status now render; typed revision-checked native saving preserves entry IDs and existing occurrence/session snapshots. Clear/Reset confirmations, bounded row Undo and translated totals are defined. Legacy binding and concurrent session insert defects are fixed. Browser native save/reopen, ordering, reset, repeat application, narrow focus and dark/RTL pixels verified. See [W075 checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#saved-schedule-studio-follow-up--w075). Full visual date preview and Q01–Q10 remain open.

- [ ] W075.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W075.2 **Design, styling and motion:** Design one schedule detail studio with visible saved/draft state, calendar preview and clearly separated deployment action.
- [ ] W075.3 **Workflow, usability and features:** Define which changes are reversible; test undo/reset, logging toggles and repeat application; explain updates to existing occurrences.
- [ ] W075.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w076"></a>

### W076 — Apply schedule form

**Priority:** P1 · **Source:** [trainer-views/schedule/apply.html](../../src/main/resources/templates/trainer-views/schedule/apply.html)

**Route/context:** `/schedules/{id}/apply`.

**Coverage:** **V** — current native preview/apply/repeat feedback and desktop/phone/dark/RTL inspected; full acceptance remains open.

**Current finding/opportunity:** The native form now shares V2 surfaces and the date planner, previews actual dates/new/existing movement counts without saving, validates all stored movement references and reports real calendar results. Custom-only reload mappings are corrected. Final 31-test gate/package and fresh browser evidence passed. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#native-calendar-application-and-retired-chooser--w076w077) names full role/failure/concurrency/device gaps.

- [ ] W076.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W076.2 **Design, styling and motion:** Reuse the deployment component and show actual dates plus generated-session preview.
- [x] W076.3 **Workflow, usability and features:** Test range validation, duplicate application and DST boundaries; preserve existing POST and route compatibility.
  - Related native web form repair: same shared planner, preserved POST route/access, retained invalid values/feedback, labels and a required 1–52-week range. Starting on Wednesday no longer creates the preceding Monday; applying again creates no duplicate occurrence or application record. Native GET/validation/exact-date/reapplication integration checks pass. Full form redesign, browser/device proof and timezone boundary acceptance remain open.
- [ ] W076.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w077"></a>

### W077 — Alternate schedule chooser

**Priority:** P2 · **Archived source:** [select-schedule.html](retired-templates/select-schedule.html)

**Route/context:** No active caller or GET route remains. The live /schedules workspace links to the preserved /schedules/{id}/apply route.

**Coverage:** **R** — retired after complete source/caller trace; archived outside runtime resources and excluded from the verified executable.

**Current finding/opportunity:** Canonical selection already exists in W073. No controller, template or JavaScript caller referenced this dormant chooser; translations and historic audits were the remaining mentions. Archived it and removed its obsolete CSS. Active native selection/application is verified through W073/W076; no unused route was invented. Runtime/device checks for the retired template are inapplicable; replacement acceptance remains tracked on its live pages.

- [x] W077.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W077.2 **Design, styling and motion:** If active, reuse a concise schedule picker; otherwise retire after reference verification.
- [x] W077.3 **Workflow, usability and features:** Trace caller and saved links before removal; confirm the chosen schedule persists into the next editing/application step.
- [x] W077.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w078"></a>

### W078 — Schedule-linked workout builder

**Priority:** P1 · **Source:** [trainer-views/schedule/workout.html](../../src/main/resources/templates/trainer-views/schedule/workout.html)

**Route/context:** `/workout`.

**Coverage:** **V** — core browser behaviour and current desktop/phone/dark/Arabic viewport inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** W078 rebuilt with its W069/W070 dependencies: shared draft, library/custom authoring, real saved-plan cards and schedule context. Explicit reference checks protect history/plans; fabricated AI fallback removed. Actual native Save and clean Continue verified; dirty Continue/provider/order lifecycle remains open. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-workout-composer-and-shared-fragments-follow-up) records evidence and remaining acceptance.

- [ ] W078.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W078.2 **Design, styling and motion:** Reuse the exercise/workout composer while keeping schedule context visible; use selective preview media instead of permanent 3D.
- [ ] W078.3 **Workflow, usability and features:** Preserve draft work through Continue; show AI-disabled/limit states and validate generated suggestions before accepting them.
- [ ] W078.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w079"></a>

### W079 — Trainer dashboard

**Priority:** P1 · **Source:** [trainer-views/dashboard/trainer-dashboard.html](../../src/main/resources/templates/trainer-views/dashboard/trainer-dashboard.html)

**Route/context:** `/trainer/dashboard`.

**Coverage:** **V** — current desktop/mobile evidence and DOM reviewed; unfinished state and interaction acceptance is listed below.

**Current finding/opportunity:** Coaching priorities now lead the page, with actual request, submitted check-in, unread-message and active-client counts. Remaining acceptance includes multi-gym trainers and the complete Q01–Q10 state matrix.

**Implementation progress:** 3 October: added four real priority cards, the five oldest submitted check-ins for currently active owned clients, a seven-day waiting count and a direct next-action link. Waiting time describes elapsed days; it does not invent a deadline. Personal scheduling remains available in a native keyboard-operable disclosure. Unverified/disabled trainers see verification as their next step and receive no coaching queue. Eighteen messages cover all fourteen UI languages. Reviewed desktop, 390/320 px, landscape controls, dark tablet and Arabic phone; the initial clipped mobile header and route fade were fixed. Actual browser sign-in, oldest-review destination and unread count changing from one to zero after opening its conversation pass. Quick actions now stays open when launched through Navigation, with Escape restoring the navigation trigger. Focused dashboard/security/shell gate passed 28 tests; subsequent recovery/dashboard/shell gate passed 24 tests. See [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#trainer-priorities-and-shared-recovery--w079) for exact gates, source and evidence. Multi-gym, complete state, text-zoom, no-JS and remaining Q01–Q10 acceptance stay open.

- [ ] W079.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W079.2 **Design, styling and motion:** Create a professional coaching workspace with clients needing attention, next appointment/task and programme actions; keep motion brief.
- [ ] W079.3 **Workflow, usability and features:** Prioritise requests, overdue check-ins and unread messages using real data; verify independent and multi-gym trainers with empty/populated states.
- [ ] W079.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w080"></a>

### W080 — Client relationship management

**Priority:** P1 · **Source:** [trainer-views/trainer/clients.html](../../src/main/resources/templates/trainer-views/trainer/clients.html)

**Route/context:** `/trainer/clients`.

**Coverage:** **V** — current desktop/mobile evidence and DOM reviewed; unfinished state and interaction acceptance is listed below.

**Current finding/opportunity:** Source: pending requests, current clients, message, pause and end coexist; relationship consequences need clarity.

**Implementation progress:** 3 October follow-up: client-specific native dialogue confirms Accept/Pause/End consequences, with the same translated explanation available in a keyboard-operable native disclosure. Actual status, request date and operation feedback are translated; each POST form now has one generated CSRF field. Unverified trainers see verification guidance and disabled mutation controls. Accept/Resume now require an enabled CLIENT account as well as a verified trainer and the existing client-row lock/one-active-coach rule. Focused four-suite gate and bootJar pass 28 tests in 52 s, cache v3l. Actual local browser Pause locks the saved thread, removes coaching dashboard priorities and shows translated feedback; Resume restores the same relationship and its actions. Cancel and Escape retain Active state and restore the named trigger. Reviewed 390 px confirmation/control screenshots and 320 px Arabic layout. Full dark-theme/zoom/state/concurrent-request Q01–Q10 acceptance remains open. See [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#client-relationship-consequences--w080). Continue canonical pending-request decline/consolidation in W081/W082.

- [ ] W080.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W080.2 **Design, styling and motion:** Create a searchable client workspace with text-based relationship status and meaningful priority indicators.
- [ ] W080.3 **Workflow, usability and features:** Explain accept/pause/end outcomes; preserve one-active-trainer enforcement and authorisation; verify request races and empty states using local seeded data.
- [ ] W080.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w081"></a>

### W081 — Alternate client requests list

**Priority:** P2 · **Archived source:** [client-requests.html](retired-templates/client-requests.html)

**Route/context:** `/trainer/requests → /trainer/clients` — preserve compatibility/history; confirm whether this template should remain.

**Coverage:** **R** — unused template archived outside runtime resources after caller/reference verification; canonical replacement remains W080.

**Current finding/opportunity:** No controller/include/script renders this template. Its Accept/Decline forms targeted missing endpoints using relationship IDs and its date referenced a missing property. Added the useful Decline action to the canonical workspace with actual client IDs, verified ownership, a locked pending row, retained history and translated confirmation/feedback. Existing /trainer/requests redirect is preserved and browser-verified. Decline cancellation retains the request; confirmed local Decline removes it and changes dashboard requests from one to zero while keeping the other active client. The executable excludes the archive. Full live-page acceptance remains W080. See [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#canonical-request-decline-and-retired-lists--w081w082).

- [x] W081.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W081.2 **Design, styling and motion:** Carry any unique actions into the canonical client workspace and retire only after use is proven absent.
- [x] W081.3 **Workflow, usability and features:** Preserve historic links and accept/decline feedback; confirm request count and state are consistent with the dashboard.
- [x] W081.4 **Complete:** retired after caller verification and preservation of live replacement; runtime/device checks of this unused template are inapplicable. Canonical W080 Q01–Q10 remains open.


<a id="w082"></a>

### W082 — Alternate active clients list

**Priority:** P2 · **Archived source:** [active-clients.html](retired-templates/active-clients.html)

**Route/context:** No direct GET render found; inspect helper/caller and alternate/legacy use before implementation.

**Coverage:** **R** — no active render/include/script caller; archived outside runtime resources and excluded from the verified executable.

**Current finding/opportunity:** Complete caller trace confirms this small roster is unused. Its direct message entry already exists as the same owned /inbox/start/{clientId} POST in W080; only ACTIVE verified client rows offer it. Active/Paused text, search, Resume and other client actions remain in the canonical workspace. No nonexistent historical GET route was invented. Updated the route contract to check the live POST and retired runtime paths; final 48-test gate/archive pass. Live browser Pause/Resume and retained locked conversation proof is recorded under W080. Remaining replacement acceptance stays there.

- [x] W082.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W082.2 **Design, styling and motion:** Consolidate the unused roster into the canonical client presentation and preserve active-only message/actions.
- [x] W082.3 **Workflow, usability and features:** Trace the active route/caller; preserve direct message entry and client access controls before consolidation.
- [x] W082.4 **Complete:** retired after caller verification; runtime/device checks of this unused template are inapplicable. Canonical W080 Q01–Q10 remains open.


<a id="w083"></a>

### W083 — Client coaching workspace

**Priority:** P1 · **Source:** [trainer-views/trainer/client-detail.html](../../src/main/resources/templates/trainer-views/trainer/client-detail.html)

**Route/context:** `/trainer/clients/{clientId}`.

**Coverage:** **V** — actual desktop/phone/Arabic previews and saved phase/assignment behaviour inspected; complete state/privacy/Q01–Q10 acceptance remains open.

**Current finding/opportunity:** Client context now connects programmes, recent check-ins, the existing conversation, goals and consent-gated signals. Programme selection reveals the owned movement/day preview before assignment. Database-length validation, retained rejection drafts and real success feedback replace uncontrolled note failures.

**Implementation progress:** Added owned programme previews, overview counts, seven native section links, five recent client/trainer check-ins, the existing conversation entry and five recent phase changes. Read-only summary DTOs exclude client answers/message bodies and other trainers' records. Phase labels/types and goal types are translated; assignment notes explicitly disclose client visibility. Assignment/phase writes lock the client, validate 800-character notes/120-character labels and retain invalid selection/text through JDBC session redirects. Final gate: 34 tests/five suites and bootJar pass, cache v3o, 39 matching resources/37 classes. Actual desktop/390 px/320 px Arabic previews, native keyboard disclosure, saved assignment feedback and phase history inspected. Full consent combinations, dark/zoom/reduced-motion and Q01–Q10 remain open. [Evidence and checks](../qa/2026-10-03-v2-prepared-checkpoint.md#client-coaching-context-previews-and-safe-form-saves--w083).

- [ ] W083.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W083.2 **Design, styling and motion:** Build a client-centred workspace with overview, programme, check-ins and conversation tabs; preserve context during transitions. Native section navigation keeps form drafts in the same document; previews progressively enhance native disclosures.
- [ ] W083.3 **Workflow, usability and features:** Verify assignment preview and ownership, phase changes, health-data permissions and safe coach notes; make the next useful action obvious.
- [ ] W083.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w084"></a>

### W084 — Trainer check-in review

**Priority:** P1 · **Source:** [trainer-views/checkins/trainer-review.html](../../src/main/resources/templates/trainer-views/checkins/trainer-review.html)

**Route/context:** `/checkins/trainer-review/{id}`.

**Coverage:** **V** — desktop client notes/composer and phone error/saved states inspected; client notification/read journey verified locally. Complete Q01–Q10 remains open.

**Current finding/opportunity:** Source: client responses, trainer reply, next-week focus and linked goal share a review form.

**Implementation progress:** Submitted client notes now accompany answers; damaged saved answer JSON produces a warning while preserving valid answers/notes. Saved feedback, response timestamp and explicit sharing/notification guidance distinguish the draft from the client's saved response. Completed owned goal attachments stay selected on later edits; inaccessible/deleted goals receive a neutral unavailable option. One CSRF field per form. Response writes acquire the check-in lock; unchanged sequential repeats retain the timestamp and create one notification. Notifications link to the new owner-only W177 read view. Client submission now exposes five recent historical check-ins and preserves actual submitted feedback. Final functional gate passes 26 tests/five suites in 59 s (v3p); presentation refinements package successfully at v3r, 43 matching resources/41 classes. Local native empty-save error, successful synthetic save, notification opening/Seen state and responded/awaiting client history verified. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#saved-coaching-review-and-client-read-view--w084w177). Concurrent stress, all themes/zoom/no-script/role matrices and native deep links remain open.

- [ ] W084.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W084.2 **Design, styling and motion:** Place client answers beside a focused coaching response composer with a readable week/status header.
- [x] W084.3 **Workflow, usability and features:** Separate draft reply from sent/saved review; test values retained on error and the client's resulting notification/view. Real H2/JDBC-session regression and disposable local browser journey recorded; full Q acceptance remains separate.
- [ ] W084.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w085"></a>

### W085 — Trainer owner account profile

**Priority:** P1 · **Source:** [trainer-views/profile/profile.html](../../src/main/resources/templates/trainer-views/profile/profile.html)

**Route/context:** `/profile` (trainer-selected template).

**Coverage:** **V** — rebuilt owner surface, actual local form journeys and inspected desktop/phone/Arabic settings evidence; full acceptance remains open.

**Current finding/opportunity:** Source: trainer details, social links, account settings and images coexist with a second professional editor.

**Implementation progress:** The owner account now has five native section links, shared 2.0 controls, public-listing entry and separate professional editor. Trainer/gym owners share native appearance/calendar/accessibility forms. Account saves preserve public social visibility; trainer detail validation uses a detached draft before account/image changes. Invalid text/raw numeric input survives actual JDBC-session redirects, and optional price can clear independently. Fixed non-serialisable profile drafts, duplicate gym CSRF and nested main/inline progress markup. Final functional gate passes 30 tests/five suites in 56 s at v3t; presentation-only v3u package passes separately in 12 s. Actual rejected/successful account save, error-link focus, owner/editor/public parity, signed-out private-code omission, price clearing, dark/phone/Arabic and native Space accessibility save verified. Full media/account-verification/state matrices remain open. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#trainer-owner-settings-and-retained-profile-drafts--w085).

- [ ] W085.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W085.2 **Design, styling and motion:** Separate private account settings from a clear public-profile preview using the shared 2.0 settings layout. Native section navigation, verified listing entry, reusable preferences and inspected phone/desktop surfaces implemented.
- [ ] W085.3 **Workflow, usability and features:** Verify the relationship to /trainer/profile/edit and synchronise fields; preserve trainer code, verification, gym associations and upload restrictions.
- [ ] W085.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w086"></a>

### W086 — Professional trainer profile editor

**Priority:** P1 · **Source:** [trainer-views/trainer/profile/edit.html](../../src/main/resources/templates/trainer-views/trainer/profile/edit.html)

**Route/context:** `/trainer/profile/edit`.

**Coverage:** **V** — actual rejected/save/public-visibility/account-continuity journey and inspected desktop/phone/Arabic views; full acceptance remains open.

**Current finding/opportunity:** Source: location, primary gym, price, biography, specialities and social links overlap owner profile fields.

**Implementation progress:** Rebuilt the editor with semantic panels, native labelled social visibility, real saved public preview, private-code guidance and shared clipboard feedback. Strict editable-field binding protects identity/code. Native and server bounds, social URL validation and escaped invalid drafts prevent lost work and database errors; malformed codes render safely. Whole-pound pricing and independent gym affiliations remain intact. Actual wrong-domain rejection retains a literal multiline draft; native correction/visibility save updates the public listing and owner account fields. Desktop, phone and Arabic images inspected. The owner/account follow-up fixes unchanged-save feedback and the local partial-compilation reload race; 16 focused profile tests/two suites pass in 56 s. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#professional-editor-continuity-and-controlled-local-reload--w086). Media/cropping and full state/save-preview acceptance remain open.

- [ ] W086.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W086.2 **Design, styling and motion:** Build a professional editor with a real public preview and clear field visibility; media uploads have deliberate cropping guidance.
- [ ] W086.3 **Workflow, usability and features:** Show exactly what clients will see; preserve price units and multiple-gym capability; validate social URLs and save/preview parity.
- [ ] W086.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w087"></a>

### W087 — Trainer content library hub

**Priority:** P1 · **Source:** [trainer-views/trainer/library.html](../../src/main/resources/templates/trainer-views/trainer/library.html)

**Route/context:** `/trainer/library`.

**Coverage:** **V** — actual empty/populated/search and owned-resource navigation; inspected desktop, phone and Arabic evidence. Complete-state acceptance remains open.

**Current finding/opportunity:** The former small hub lacked search and recency and loaded whole owned libraries for counts. Native search and bounded recent creations now work with actual owned resources. Downstream schedule/display terminology and complete-state acceptance remain open.

**Implementation progress:** Native name search, Clear, bounded recent creations, true owned/matching counts and resource descriptions now join the three category cards. Queries fetch at most six resources per type and display six globally newest matches. Actual browser creates all three types, opens an owned result and verifies missing-name recovery; desktop/390 px/Arabic 320 px images were inspected. Focused gate/package: 13 tests/three suites, 49 s, v3v, 21 resources/eight classes match. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#bounded-coaching-library-overview--w087). Full Q01–Q10 and downstream sharing/template journeys remain open.

- [ ] W087.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W087.2 **Design, styling and motion:** Create one coherent coaching library with resource types, recent items and useful search; avoid separate inconsistent card systems.
- [ ] W087.3 **Workflow, usability and features:** Distinguish exercise, workout, programme, schedule template and display template; keep permissions and client-sharing context explicit.
- [ ] W087.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w088"></a>

### W088 — Trainer exercise library

**Priority:** P1 · **Source:** [trainer-views/trainer/exercises/list.html](../../src/main/resources/templates/trainer-views/trainer/exercises/list.html)

**Route/context:** `/trainer/library/exercises`.

**Coverage:** **V** — actual empty/create/search/detail/edit/return/no-match recovery; inspected light desktop, phone, Arabic 320 px and dark tablet. Full-state acceptance remains open.

**Current finding/opportunity:** Source: searchable exercise resources have create/detail flows and a no-content state.

**Implementation progress:** Eighteen-result owned database pages now search names/muscles/equipment with literal wildcard handling, show matching and owned totals, clamp stale pages and preserve query/page/card return context. Cards show translated difficulty and honest safe-video-link availability. Native browser search/edit/rejection/save/Back/Clear works; inspected desktop/phone/Arabic/dark tablet. Final shared catalogue/editor gate: 20 tests/four suites, 40 s, v3x, 25 resources/five classes match. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#exercise-catalogue-retained-navigation-and-shared-draft-preview--w088w089w090). Licensed thumbnails, complete sharing states and Q01–Q10 acceptance remain open.

- [ ] W088.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W088.2 **Design, styling and motion:** Use a compact searchable exercise catalogue with muscle/equipment tags, useful real thumbnails and a media fallback.
- [ ] W088.3 **Workflow, usability and features:** Clarify private versus shared exercises; retain filters and return position; test missing media and empty search results.
- [ ] W088.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w089"></a>

### W089 — Create trainer exercise

**Priority:** P1 · **Source:** [trainer-views/trainer/exercises/create.html](../../src/main/resources/templates/trainer-views/trainer/exercises/create.html)

**Route/context:** `/trainer/library/exercises/create`.

**Coverage:** **V** — native creation, escaped live draft values and saved feedback exercised in the browser; shared form/preview responsive images inspected. Complete-state acceptance remains open.

**Current finding/opportunity:** Source: basic details, exercise information and coaching notes are spread across a long creation form.

**Implementation progress:** Shared required/length controls now feed a responsive unsaved text/metadata/video-URL preview, without fetching or embedding unvalidated media. Native creation retains search context and confirms saved feedback. Named rejected-field links, clear required difficulty and padded controls match editing. Real browser literal multiline preview/create/save and the final 20-test/four-suite gate pass at v3x. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#exercise-catalogue-retained-navigation-and-shared-draft-preview--w088w089w090). Full media/educational-asset/no-script/state acceptance remains open.

- [ ] W089.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W089.2 **Design, styling and motion:** Build a structured exercise editor with live text/media preview and readable required/optional grouping.
- [ ] W089.3 **Workflow, usability and features:** Validate media sources, units and instructions; preserve drafts and ownership; add 3D only for approved educational assets with text alternatives.
- [ ] W089.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w090"></a>

### W090 — Edit trainer exercise

**Priority:** P1 · **Source:** [trainer-views/trainer/exercises/edit.html](../../src/main/resources/templates/trainer-views/trainer/exercises/edit.html)

**Route/context:** `/trainer/library/exercises/{id}/edit`.

**Coverage:** **V** — actual rejected/retained/saved editing and focused error links; inspected desktop, phone, Arabic 320 px and dark tablet. Complete-state acceptance remains open.

**Current finding/opportunity:** Source: editing is much smaller than creation and may present fields differently.

**Implementation progress:** Create/edit share the same structured controls and responsive unsaved preview. Server-rendered initial text remains available independently of the external preview script, which uses literal textContent. Actual unsafe-video rejection preserves multiline notes, its error link focuses the field, valid save acknowledges success and Back restores the search/card. Dark-tablet keyboard Save focus is above the platform panel; Cancel leaves saved description unchanged. Final focused package gate: 20 tests/four suites, 40 s, v3x. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#exercise-catalogue-retained-navigation-and-shared-draft-preview--w088w089w090). Full reference/media, slow-request, zoom/no-script and Q01–Q10 acceptance remain open.

- [ ] W090.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W090.2 **Design, styling and motion:** Share the exercise form and preview components with creation for consistent field treatment.
- [ ] W090.3 **Workflow, usability and features:** Verify existing media/metadata survives edits and references remain stable; test invalid changes and safe return to detail.
- [ ] W090.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w091"></a>

### W091 — Trainer exercise detail

**Priority:** P1 · **Source:** [trainer-views/trainer/exercises/view.html](../../src/main/resources/templates/trainer-views/trainer/exercises/view.html)

**Route/context:** `/trainer/library/exercises/{id}`.

**Coverage:** **V** — actual owned detail/sharing, receiving-client read/role denial, Cancel/Escape and focus restoration; inspected desktop, phone, Arabic 320 px and dark tablet. Complete-state acceptance remains open.

**Current finding/opportunity:** Exercise instructions are shared live with receiving clients; the old coaching-notes label alone did not communicate this. Detail lacked a missing-video state and placed deletion beside instructions. Actual sharing also dropped the catalogue query. These findings and mixed-direction library punctuation are addressed; complete-state acceptance remains open.

**Implementation progress:** Multiline instructions, translated difficulty and safe optional/missing-video states now lead into separate share/resource actions. Client-visible instructions/later edits are explicitly distinguished from private client-workspace notes. Share retains bounded query/page while its route remains server-derived. Actual local client reads the shared literal instructions and cannot open trainer management; Cancel/Escape restore deletion focus. Final functional gate: 19 tests/three suites, 51 s at v3z; copy/cache-only package 12 s at v4a without a functional rerun, 26 resources/five classes match. [Checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#exercise-instructions-sharing-privacy-and-deletion-context--w091). Media playback, runtime deletion/concurrency, full RTL/contrast/state/Q01–Q10 acceptance remain open.

- [ ] W091.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W091.2 **Design, styling and motion:** Create an instructional detail view with readable technique steps and optional user-started video/3D demonstration.
- [x] W091.3 **Workflow, usability and features:** Explicitly distinguish shared exercise instructions from private client-workspace notes; verify share permissions, deletion impact on assigned content and missing media recovery.
- [ ] W091.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w092"></a>

### W092 — Trainer workout library

**Priority:** P1 · **Source:** [trainer-views/trainer/workouts/list.html](../../src/main/resources/templates/trainer-views/trainer/workouts/list.html)

**Route/context:** `/trainer/library/workouts`.

**Coverage:** **V** — actual native create/edit/search/share/item addition and client reading; inspected empty/populated desktop, 390 px phone, Arabic 320 px and dark 820 px tablet in the [W092 checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#bounded-workout-catalogue-and-retained-resource-context--w092). Full acceptance remains open.

**Current finding/opportunity:** Workouts were described as programmes, search loaded the whole library, an empty summary could match “null”, and native share/edit returns lost the catalogue search. These are corrected. Complete pagination/browser-state and acceptance work remains open.

**Implementation progress:** Consistent session naming, eighteen-row owned title/summary search, literal wildcard handling, stale-page clamping, page-only owned item counts and clear create/no-match/pagination states. Native metadata/composition/share/delete returns retain safe query/page context; accepted saves report true feedback. RTL user-authored fields/instructions now preserve their text direction. CSS/parity/package/focused checks pass: 31 tests/four suites, 54 s, v4b, 31 resources/eight classes matching the executable; final placeholder-only package 9 s. Actual native journeys and inspected responsive images are recorded; full Q01–Q10 remains open.

- [ ] W092.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W092.2 **Design, styling and motion:** Use consistent workout naming, searchable resources and a concise create/assign action area.
- [ ] W092.3 **Workflow, usability and features:** Clarify composition versus assignment versus scheduling; preserve selected client context and archived-resource behaviour.
- [ ] W092.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w093"></a>

### W093 — Create coach workout

**Priority:** P1 · **Source:** [trainer-views/trainer/workouts/create.html](../../src/main/resources/templates/trainer-views/trainer/workouts/create.html)

**Route/context:** `/trainer/library/workouts/create`.

**Coverage:** **V** — current v4d desktop editor/conflict captures inspected; v4c phone creation proof is historical. Full device/state acceptance remains open.

**Current finding/opportunity:** Source: first step creates metadata then continues to adding exercises.

**Implementation progress, 3 October:** Shared native create/edit metadata form with literal, live unsaved title/summary/instruction preview; required bounded fields, named error links and retained rejected values. Creation explains save → exercise composition and that a library workout does not schedule a client. Native creation, whitespace-title rejection, keyboard error/preview focus and save were verified. Sequence preview and unsaved exercise work continue under W095. See [current checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#shared-workout-metadata-preview-and-stale-save-recovery--w093w094).

- [ ] W093.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W093.2 **Design, styling and motion:** Make a clear workout composer with metadata and exercise sequence preview; preserve progress across steps.
- [ ] W093.3 **Workflow, usability and features:** Keep unsaved exercise work and validate required metadata; explain when the workout is ready to share or assign.
- [ ] W093.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w094"></a>

### W094 — Edit coach workout metadata

**Priority:** P1 · **Source:** [trainer-views/trainer/workouts/edit.html](../../src/main/resources/templates/trainer-views/trainer/workouts/edit.html)

**Route/context:** `/trainer/library/workouts/{id}/edit`.

**Coverage:** **V** — current v4d desktop editor/conflict captures inspected; v4c phone creation proof is historical. Full device/state acceptance remains open.

**Current finding/opportunity:** Source: a small editor handles an existing library workout.

**Implementation progress, 3 October:** Shared form explicitly distinguishes unsaved values from saved client-visible metadata. Owned row-locked metadata snapshots and a content revision reject stale saves with 409, preserve the draft and show latest values for explicit reviewed saving. Native two-editor conflict/review/save and Cancel without mutation were verified. Real database tests retain workout/item identity, reject missing revisions and preserve newer metadata. No schema/dependency change. Receiving-client browser recheck and deployment-database parallel commits remain open. See [current checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#shared-workout-metadata-preview-and-stale-save-recovery--w093w094).

- [ ] W094.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W094.2 **Design, styling and motion:** Reuse the workout metadata form and make draft/published state understandable.
- [x] W094.3 **Workflow, usability and features:** Verify edits to assigned workouts have an explicit policy; preserve workout identity and guard against lost updates.
- [ ] W094.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w095"></a>

### W095 — Coach workout composition

**Priority:** P1 · **Source:** [trainer-views/trainer/workouts/view.html](../../src/main/resources/templates/trainer-views/trainer/workouts/view.html)

**Route/context:** `/trainer/library/workouts/{id}`.

**Coverage:** **V** — actual native composition/add/rejection/reorder/remove/share/used-deletion and receiving-client flow; inspected desktop, phone, Arabic 320 px and dark tablet captures. Full acceptance remains open.

**Current finding/opportunity:** Source: items, add/remove, notes, share and delete form the composition workspace.

**Implementation progress, 3 October:** Structured ordinal exercise blocks have native named Up/Down/Remove actions, disabled boundaries, actual units/RPE scale, linked field errors and a saved client-instruction preview. Moves lock the owned workout and flush a free non-negative position between swaps, preserving SQL uniqueness and item identity/prescriptions. Missing/foreign items and invalid directions cannot mutate another workout. Repeated exercises are valid distinct blocks. Add errors retain the draft; the shared editor warns before leaving changed forms. Sharing/live edits versus calendar scheduling are explicit. Contextual deletion is separate from instructions; a confirmed used-workout deletion preserves its programme/items and reports how to recover. Receiving clients see later metadata and reordered prescriptions, with no trainer mutation forms. See [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#structured-workout-sequence-and-client-preview--w095).

- [ ] W095.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W095.2 **Design, styling and motion:** Build a structured exercise sequence with accessible reorder actions, prescription units and a client preview.
- [x] W095.3 **Workflow, usability and features:** Check duplicate items, required prescriptions, sharing and deletion impact; distinguish library editing from changes to existing client assignments.
- [ ] W095.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w096"></a>

### W096 — Programme library

**Priority:** P1 · **Source:** [trainer-views/trainer/programmes/list.html](../../src/main/resources/templates/trainer-views/trainer/programmes/list.html)

**Route/context:** `/trainer/library/programmes`.

**Coverage:** **V** — actual native creation/duration/day count/search/share/return and no-match/Clear verified; inspected light desktop/390 px, Arabic 320 px and dark 820 px tablet images. Full acceptance remains open.

**Current finding/opportunity:** Source: programmes have a separate create/detail flow with a no-content state.

**Implementation progress, 3 October:** Eighteen owned database results per page replace full-library filtering. Title search treats wildcards literally, uses stable newest ordering and clamps stale pages. Counts aggregate only displayed owned programmes. Resource cards show actual optional duration and sessions in their ordered sequence, with honest no-duration/no-session states. Sharing is explicitly a library reference, not calendar scheduling. Native create/edit/rejected drafts/composition/share/delete preserve bounded query/page context and a Back card anchor; accepted metadata saves report real feedback. Current screenshots and a 25-test focused gate are recorded in the [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#bounded-programme-catalogue-and-safe-context--w096). Composition reordering/editor upgrades follow under W097–W099; complete client-entry context and Q01–Q10 acceptance remain open.

- [ ] W096.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W096.2 **Design, styling and motion:** Create a programme library showing duration, training rhythm and useful status; use the same resource shell as workouts.
- [ ] W096.3 **Workflow, usability and features:** Explain that a programme combines days/workouts over time; retain client assignment context and search/empty states.
- [ ] W096.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w097"></a>

### W097 — Create programme

**Priority:** P1 · **Source:** [trainer-views/trainer/programmes/create.html](../../src/main/resources/templates/trainer-views/trainer/programmes/create.html)

**Route/context:** `/trainer/library/programmes/create`.

**Coverage:** **V** — selected actual native draft/create/rejection, two-editor conflict/reviewed save and unchanged Cancel; inspected desktop, 390 px phone, Arabic 320 px and dark 820 px tablet evidence. Complete acceptance remains open.

**Current finding/opportunity:** Source: a minimal programme form creates the shell of a larger plan.

**Implementation progress, 3 October:** Shared metadata fields and literal live/server previews retain title, optional positive weeks and instructions, including raw rejected duration. Named validation links focus invalid fields. Creation explains the next session-arrangement step; edits explain live receiving-client impact and private-note placement. Owned locked metadata fingerprints reject stale/missing revisions before mutation; a 409 retains the draft alongside latest saved values and requires explicit reviewed saving. Programme identity, days and active-client shares remain intact in real H2 checks. Seven messages supplied to fourteen UI locales. The [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#programme-metadata-preview-and-conflict-recovery--w097w098) records 27 focused tests, exact package proof and accepted current images. Calendar assignment, full Q01–Q10, runtime dirty-navigation warning and deployment-database concurrency remain open.

- [ ] W097.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W097.2 **Design, styling and motion:** Use a focused metadata step with duration, objective and a preview of the next composition step.
- [x] W097.3 **Workflow, usability and features:** Validate the existing fields and preserve programme identity; distinguish required structure from optional coaching description.
- [ ] W097.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w098"></a>

### W098 — Edit programme metadata

**Priority:** P1 · **Source:** [trainer-views/trainer/programmes/edit.html](../../src/main/resources/templates/trainer-views/trainer/programmes/edit.html)

**Route/context:** `/trainer/library/programmes/{id}/edit`.

**Coverage:** **V** — selected actual native draft/create/rejection, two-editor conflict/reviewed save and unchanged Cancel; inspected desktop, 390 px phone, Arabic 320 px and dark 820 px tablet evidence. Complete acceptance remains open.

**Current finding/opportunity:** Source: edit and create forms are near-identical but independent.

**Implementation progress, 3 October:** Shared metadata fields and literal live/server previews retain title, optional positive weeks and instructions, including raw rejected duration. Named validation links focus invalid fields. Creation explains the next session-arrangement step; edits explain live receiving-client impact and private-note placement. Owned locked metadata fingerprints reject stale/missing revisions before mutation; a 409 retains the draft alongside latest saved values and requires explicit reviewed saving. Programme identity, days and active-client shares remain intact in real H2 checks. Seven messages supplied to fourteen UI locales. The [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#programme-metadata-preview-and-conflict-recovery--w097w098) records 27 focused tests, exact package proof and accepted current images. Calendar assignment, full Q01–Q10, runtime dirty-navigation warning and deployment-database concurrency remain open.

- [ ] W098.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W098.2 **Design, styling and motion:** Share the form with creation and make changes to an existing programme clear.
- [x] W098.3 **Workflow, usability and features:** Test edits without dropping programme days or client links; preserve drafts and validation feedback.
- [ ] W098.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w099"></a>

### W099 — Programme composition

**Priority:** P1 · **Source:** [trainer-views/trainer/programmes/view.html](../../src/main/resources/templates/trainer-views/trainer/programmes/view.html)

**Route/context:** `/trainer/library/programmes/{id}`.

**Coverage:** **V** — native repeated-session add/move, invalid-label recovery, remove, saved preview, delete/Escape, active-client share and later receiving-client read. Current desktop/phone/Arabic/dark-tablet images inspected; full acceptance remains open.

**Current finding/opportunity:** Source: programme days, add/remove, notes and sharing coexist.

**Implementation progress, 3 October:** Numbered session cards expose native move/remove actions named with cycle label and ordinal; repeated workout references remain distinct. All composition mutations use the owned programme lock. A three-flush swap through a free non-negative position respects the actual unique constraint and sparse positions. Missing/foreign days produce controlled access rejection. Header shows actual optional duration/count, saved order feedback, sharing/calendar distinction; native preview shows the actual saved cycle/notes. Destructive removal has a separate resource section and contextual effect. Client cycle labels use their own text direction and missing references no longer expose internal IDs. Arbitrary cycle labels are supported, so no weekly calendar grid is inferred from programme duration. [Checkpoint evidence](../qa/2026-10-03-v2-prepared-checkpoint.md#programme-sequence-and-receiving-client-continuity--w099) records 29 focused functional tests, seven final tests and fresh native proof. Full Q01–Q10/no-script/dirty-navigation and deployment-database concurrency acceptance stay open.

- [ ] W099.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W099.2 **Design, styling and motion:** Use a visual week/day composer with an accessible list alternative and clear programme overview.
- [x] W099.3 **Workflow, usability and features:** Verify day order, repeated workouts, empty days and assignment impact; make share versus assign meanings explicit.
- [ ] W099.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w100"></a>

### W100 — Schedule template library

**Priority:** P1 · **Source:** [trainer-views/trainer/templates/index.html](../../src/main/resources/templates/trainer-views/trainer/templates/index.html)

**Route/context:** `/trainer/templates`.

**Coverage:** **V** — native creation, weekday entry/question counts, literal tag search, retained clone structure/context, archive and no-match/Clear. Inspected desktop, 390 px phone, Arabic 320 px and dark 820 px tablet. Full acceptance remains open.

**Current finding/opportunity:** Source: schedule templates include cloning and can be confused with workout display templates.

**Implementation progress, 3 October:** Eighteen owned results per page replace full-library loading/filtering. Stable metadata-update/ID order, literal name/tag search, total/match ranges, distinct empty/no-match and page-only weekday/entry/question aggregation. Shared resource cards show actual saved weekly pattern and archived/active state; explicit guidance distinguishes weekly application from programme references and display layouts. Native create/edit/entry/question/clone/apply preview retain bounded query/page/card context; default redirects stay compatible. Clone preserves entries/questions with new IDs without creating assignments. Actual archived cards omit Apply. The [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#bounded-schedule-catalogue-and-native-context--w100) records nine focused tests, exact current package and accepted native proof. Full Q01–Q10, client application W102, no-script/zoom/motion and deployment-database concurrency remain open.

- [ ] W100.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W100.2 **Design, styling and motion:** Show actual schedule rhythm and check-in inclusion in the shared library style.
- [x] W100.3 **Workflow, usability and features:** Use clear schedule-template naming; verify clone ownership and retained structure without duplicating assignments.
- [ ] W100.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w101"></a>

### W101 — Schedule template and check-in editor

**Priority:** P1 · **Source:** [trainer-views/trainer/templates/edit.html](../../src/main/resources/templates/trainer-views/trainer/templates/edit.html)

**Route/context:** `/trainer/templates/create`, `/trainer/templates/{id}/edit`.

**Coverage:** **V** — accepted native studio, saved weekly preview, rejection/focus, stale-form review, Arabic phone and dark tablet evidence; complete Q01–Q10 remains open.

**Current finding/opportunity:** Source: metadata, entries and weekly check-in questions are edited together.

**Implementation progress:** Metadata, weekly entries and check-in questions now have separate semantic, labelled sections and bounded native/server controls. Invalid metadata, time windows, exercise selection and questions retain escaped drafts in controlled 400 responses. Entry/question appends use template locks and the highest saved position, avoiding duplicate indexes after deletion. Clone retains questions and entries with new clone IDs and preserves original assignments. Focused checks pass; reorder, client preview, authenticated browser and complete visual acceptance remain open.

**Implementation progress, 3 October:** Focused metadata/draft preview, native section navigation, separate entry/question composition and an actual seven-day saved pattern. Named native movement controls preserve row IDs, weekdays, times and required flags. Metadata saves lock and refresh the latest owned row before comparing a revision; stale requests retain drafts and show saved values/archive state for explicit review. The same stale persistence-context bug was reproduced and fixed in workout/programme editors. Missing/foreign removal/movement is controlled. Six new messages render in fourteen UI locales. Checkbox/radio dirty snapshots now use checked state. The [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#schedule-studio-and-fresh-metadata-comparison--w101) records 29 passing focused checks, exact package and real native add/reorder/remove/reject/reviewed-save evidence. Final header/removal polish was recaptured. Browser dirty-navigation prompts, no-script/zoom/motion, every role/edge state, deployment-database concurrency and native device acceptance remain open.

- [ ] W101.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W101.2 **Design, styling and motion:** Create a focused schedule-template studio with separate session and check-in sections and a client preview.
- [x] W101.3 **Workflow, usability and features:** Preserve entry/question IDs and ordering; test add/remove/save, archived state and how updates affect already-applied templates.
- [ ] W101.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w102"></a>

### W102 — Apply schedule template to a client

**Priority:** P1 · **Source:** [trainer-views/trainer/templates/apply.html](../../src/main/resources/templates/trainer-views/trainer/templates/apply.html)

**Route/context:** `/trainer/templates/{id}/apply`.

**Coverage:** **V** — accepted native exact-date preview, rejection/focus, named confirmation/Escape, stale application review, saved/duplicate results, Arabic phone, dark tablet and client task proof; full Q01–Q10 remains open.

**Current finding/opportunity:** Source: client, date range and idempotent flag are exposed in the application form.

**Implementation progress:** Application now shows real enabled active-client names, labelled dates, a read-only date preview, counts for new/skipped items and translated saved counts. Preserve skip-duplicates selection; lock the client while applying, bound ranges to 366 days and reject archived templates. Preview does not write to the calendar; saved copies remain separate from later template edits. Focused checks cover exact client/date preview, one save then zero idempotent duplicates, invalid dates/range/archive and retained calendar data. Browser confirmation, responsive/theme/keyboard and deployment-database concurrency acceptance remain open.

**Implementation progress, 3 October:** Native application is bound to a fresh locked template/entry snapshot plus client, exact dates and duplicate policy. Stale/missing previews return retained-selection 409, refreshed real items/counts and explicit reviewed Apply; no partial save. Client state is refreshed after the save lock. Named errors retain client/dates and focus the rejected control. Concrete confirmation, dated cards, selected workout movement, actual copied task/reflection notes, clear destinations and duplicate policy. Task time has a visible separator, counts are responsive cards and ISO date order is isolated within Arabic RTL. The [checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md#reviewed-schedule-application-and-saved-client-copies--w102) records eighteen focused checks and exact final package, actual two-tab rejection, four-item v4o save and final three-item v4p application/repeat preview. H2 tests preserve historical copies through later template edits and deliberate non-idempotent copies. Calendar task/workout read proof is native; Vault reflection storage is proved by integration checks while native Vault reading remains restricted by Dev Hub. Full no-script/zoom/motion/role-state/deployment-concurrency/device acceptance remains open.

- [ ] W102.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W102.2 **Design, styling and motion:** Use an assignment confirmation with client identity, concrete dates and a conflict/duplicate preview.
- [x] W102.3 **Workflow, usability and features:** Explain repeat-application behaviour in plain language; verify correct client ownership and result counts; retain idempotence semantics.
- [ ] W102.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w103"></a>

### W103 — Library share dialog

**Priority:** P1 · **Source:** [trainer-views/trainer/fragments/library-share-dialog.html](../../src/main/resources/templates/trainer-views/trainer/fragments/library-share-dialog.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — current native sharing and inspected desktop/phone/Arabic/dark screenshots; remaining full acceptance is recorded below.

**Current finding/opportunity:** Source: resource sharing is a reusable modal and needs a consistent authorisation story.

**Implementation progress:** Resource-named native recipient disclosure, translated active-relationship scope and later-edit propagation, named real success, eligible selection retained on rejection, described error, trigger expansion state and Escape returning to the current opening control. Owned parent locks serialise sharing with deletion; fresh locked client reads reject disabled recipients. Nineteen focused checks pass, including repeated references, all resource types, revoked access, canonical escaped query/page recovery and fourteen locales. Native EXERCISE/WORKOUT/PROGRAMME sharing succeeds; final exercise later-edit propagation is read as the client. Final cache v4r; source/build/JAR equality confirmed. See the 3 October checkpoint for exact screenshot/version boundaries. No-script, zoom/reduced-motion, deployment-database races and full Q01–Q10 remain open.

- [ ] W103.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W103.2 **Design, styling and motion:** Use a clearly named recipient picker with resource preview and an accessible success state; keep the transition short.
- [x] W103.3 **Workflow, usability and features:** Confirm recipient eligibility, permission scope and whether edits propagate; test Escape/focus restoration and error recovery.
- [ ] W103.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w104"></a>

### W104 — Personal exercise log history

**Priority:** P1 · **Source:** [shared-views/exercise-log/exercise-log-list.html](../../src/main/resources/templates/shared-views/exercise-log/exercise-log-list.html)

**Route/context:** `/exercise-log/list`.

**Coverage:** **V** — current native history/search/date-error/paging/view/edit journey and inspected responsive/RTL/dark screenshots; full acceptance remains open.

**Current finding/opportunity:** Source: logs and preview dialog form a separate history area from sessions.

**Implementation progress:** Database-owned six-row pagination with date/ID ordering, literal notes/date/catalogue/custom/task-name search, retained inclusive date filters and controlled named 400 recovery. Personal reflections are explained separately from set/session records; native keyboard disclosures show actual escaped notes and compact rating/duration summaries. Source titles remain owner-scoped even for malformed old links. Removed the controller’s obsolete blanket development-mode denial while preserving authentication/ownership and Dev Hub policy. Owned PDF uses the editor’s numeric scale, includes duration and no longer returns an empty successful file on generation failure. Twenty-two focused checks in four suites pass; native seven-log history, page two, date correction, no-match/Clear and real view/edit paths work. Arabic fraction reversal was caught visually and corrected with numeric isolation. Final cache v4t, eighteen resources/seven classes source/build/JAR equality; exact evidence/version boundaries and export acceptance gaps recorded in the prepared checkpoint.

- [ ] W104.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W104.2 **Design, styling and motion:** Create a readable history list with exercise/date filters and useful actual summaries; share the session-history visual language.
- [x] W104.3 **Workflow, usability and features:** Explain personal logs versus coached sessions; preserve edit/view paths and preview accessibility; verify empty and long histories.
- [ ] W104.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w105"></a>

### W105 — Create or edit exercise log

**Priority:** P1 · **Source:** [shared-views/exercise-log/exercise-log-form.html](../../src/main/resources/templates/shared-views/exercise-log/exercise-log-form.html)

**Route/context:** `/exercise-log`, `/exercise-log/add-calendar`, `/exercise-log/add-occurrence`, `/exercise-log/edit/{id}`.

**Coverage:** **V** — native saved/edit/conflict/error and linked-context journeys with inspected responsive/RTL/dark screenshots; full acceptance remains open.

**Current finding/opportunity:** Source: standalone and calendar/occurrence log paths share one form.

**Implementation progress:** Native editor shows owner-resolved calendar/exercise origin, canonical readonly linked date, named retained 400 recovery, twelve large 1–4 choices, literal live draft and translated character count. Owned locked/refreshed revision snapshots reject stale or missing revisions before mutation; native 409 compares latest saved values with the retained draft and an explicit reviewed save updates the same ID. Calendar source locks now refresh preloaded entities before duplicate checks. Twenty-six checks in five suites pass; actual standalone creation, two-tab conflict/review, native duration recovery and occurrence-linked save/reopen verified. Arabic counter reversal was caught visually and isolated. Desktop/phone/320px RTL/dark screenshots inspected; full acceptance and native dirty-navigation confirmation remain open.

- [ ] W105.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W105.2 **Design, styling and motion:** Use a compact log editor with clear exercise, date and prescription units; show origin context visibly.
- [x] W105.3 **Workflow, usability and features:** Verify log association for all entry routes, validation and saved values; avoid accidentally duplicating a calendar workout.
- [ ] W105.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w106"></a>

### W106 — Exercise log detail

**Priority:** P1 · **Source:** [shared-views/exercise-log/exercise-log-view.html](../../src/main/resources/templates/shared-views/exercise-log/exercise-log-view.html)

**Route/context:** `/exercise-log/view/{id}`.

**Coverage:** **V** — native saved/edit/conflict/error and linked-context journeys with inspected responsive/RTL/dark screenshots; full acceptance remains open.

**Current finding/opportunity:** Source: log details and comments use a separate summary page.

**Implementation progress:** Saved detail shows real 1–4 fractions, optional duration, literal multiline notes and owner-resolved source context with the actual calendar date/link. Private view/edit access and fourteen locale renders pass focused checks; actual reviewed values match the saved record. Desktop/phone/320px Arabic/dark-tablet screenshots inspected. Missing legacy values render clearly, foreign or unavailable sources do not expose private names. Full no-script/zoom/reduced-motion and deployment-database acceptance remain open.

- [ ] W106.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W106.2 **Design, styling and motion:** Use a focused recorded-session summary with clear date, values, notes and edit path.
- [x] W106.3 **Workflow, usability and features:** Ensure displayed values match the saved record and remain private; test missing/deleted references and ownership.
- [ ] W106.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w107"></a>

### W107 — Exercise instruction page

**Priority:** P2 · **Source:** [shared-views/exercise-log/ExerciseTutorial.html](../../src/main/resources/templates/shared-views/exercise-log/ExerciseTutorial.html)

**Route/context:** `/exercise/{id}`.

**Coverage:** **V** — native session/instruction/keyboard-return/unsaved-set guard journey and inspected desktop/phone/RTL/dark captures; content/media and full acceptance remain open.

**Current finding/opportunity:** Source: a minimal exercise description page lacks a richer instructional structure.

**Implementation progress:** Catalogue session entries now have named native instruction links. Owned context resolves the exact exercise entry (including repeated catalogue movements), real saved session/date and same-entry return; foreign, absent and mismatched context return controlled 404. Completed context returns the existing summary. Unsaved sets block enhanced instruction navigation with retained draft/focus, then actual save → instructions → keyboard return preserves the same session/set. Panels show literal authored description and real category/type, clear blank-guidance/media fallbacks, optional named external video and no automatically loaded media. Eight strings in fourteen UI locales; fifteen checks in four suites pass, with twenty resources/five classes source/build/JAR parity, v4v. Inspected 1280/390/320px Arabic/dark 820px screenshots. Existing catalogue descriptions can be short summaries and contain no dedicated equipment or richer movement data. Technique steps, equipment, media rights and authored diagram/3D acceptance remain open; this is not a completed instructional-content library.

- [ ] W107.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W107.2 **Design, styling and motion:** Add readable technique steps, equipment and optional licensed video or authored demonstration model; motion starts only on request.
- [ ] W107.3 **Workflow, usability and features:** Validate instructional content and media rights; provide text equivalent, missing-media fallback and a clear return to the workout.
- [ ] W107.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


- [ ] W107.A Complete source-backed technique/equipment content and rights evidence for catalogue demonstrations; provide an authored diagram/model only where its movement can be verified.

<a id="w108"></a>

### W108 — Health record history

**Priority:** P1 · **Source:** [client-views/health-record/health-record-list.html](../../src/main/resources/templates/client-views/health-record/health-record-list.html)

**Route/context:** `/health-record/list`.

**Coverage:** **V** — native create/error/filter/page/detail journey plus inspected desktop, phone, Arabic RTL and dark tablet captures; complete acceptance remains open.

**Current finding/opportunity:** Source: health records have list/form/detail pages alongside a separate blood-pressure module.

**Implementation progress:** Owned database history now has six-row stable date/ID paging, literal date/activity search, inclusive date bounds, exact activity filters, retained sort/filter links and distinct empty/no-match states. Invalid or reversed dates name their field and retain raw malformed drafts. The native seven-snapshot journey retained every filter on page two (7–7 of 7), rejected reversed dates with Until date focus, and cleared a literal no-match query. Retention/export remains full acceptance follow-up. Four new workflow cases plus existing private-record access checks pass: ten checks/two suites, 42 s; exact twenty-one resources/eight classes source/build/JAR, cache v4w. Fourteen UI locales retain parity. Full Q01–Q10 acceptance is still open.

- [ ] W108.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W108.2 **Design, styling and motion:** Build a private chronological record list with readable measurement summaries and compact filters.
- [x] W108.3 **Workflow, usability and features:** Clarify the purpose of each record type and who can access it; test no records, missing fields and retention/export behaviour.
- [ ] W108.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w109"></a>

### W109 — Health record entry

**Priority:** P1 · **Source:** [client-views/health-record/health-record-form.html](../../src/main/resources/templates/client-views/health-record/health-record-form.html)

**Route/context:** `/health-record`.

**Coverage:** **V** — native create/error/filter/page/detail journey plus inspected desktop, phone, Arabic RTL and dark tablet captures; complete acceptance remains open.

**Current finding/opportunity:** Source: one form combines several health and body measurements.

**Implementation progress:** Required measurement/date/activity fields now have explicit units, number-format guidance, associated named errors, retained invalid values and optional catalogue conditions. Unavailable condition IDs are rejected without writes; activity is limited to existing choices. Native weight 0 produced a retained draft, selected Asthma and Weight focus; correcting to 72 saved a real snapshot. Conditions remain optional and the latest baseline condition selection informs existing exercise preferences. Four new workflow cases plus existing private-record access checks pass: ten checks/two suites, 42 s; exact twenty-one resources/eight classes source/build/JAR, cache v4w. Fourteen UI locales retain parity. Full Q01–Q10 acceptance is still open.

- [ ] W109.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W109.2 **Design, styling and motion:** Group fields by measurement with units, optionality and consent context; use a calm readable form rather than dramatic 3D.
- [x] W109.3 **Workflow, usability and features:** Preserve user input on error and avoid inferring diagnoses; verify numeric ranges, dates and visibility rules using synthetic health data.
- [ ] W109.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w110"></a>

### W110 — Health record detail

**Priority:** P1 · **Source:** [client-views/health-record/health-record-view.html](../../src/main/resources/templates/client-views/health-record/health-record-view.html)

**Route/context:** `/health-record/{id}`.

**Coverage:** **V** — native create/error/filter/page/detail journey plus inspected desktop, phone, Arabic RTL and dark tablet captures; complete acceptance remains open.

**Current finding/opportunity:** Source: cardiovascular, body, activity and condition sections are displayed together.

**Implementation progress:** The owned saved report shows real date/time, BP 120 / 80, cholesterol 4.3, weight 72, height 180, waist 82, computed BMI 22.22 and waist/height ratio 0.46, literal conditions and translated activity. Missing nullable values use dashes; existing non-null body/date schema remains intact. Saved snapshots explain Add new rather than suggesting an absent edit route. Export and full sensitive-data acceptance remain open. Four new workflow cases plus existing private-record access checks pass: ten checks/two suites, 42 s; exact twenty-one resources/eight classes source/build/JAR, cache v4w. Fourteen UI locales retain parity. Full Q01–Q10 acceptance is still open.

- [ ] W110.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W110.2 **Design, styling and motion:** Create a readable private report with dates, units and clearly missing values; keep charts optional and evidence-based.
- [x] W110.3 **Workflow, usability and features:** Verify access control, edit/export paths and sensitive-field handling; do not replace professional health guidance with automated conclusions.
- [ ] W110.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w111"></a>

### W111 — Blood-pressure readings

**Priority:** P1 · **Source:** [client-views/health/blood-pressure.html](../../src/main/resources/templates/client-views/health/blood-pressure.html)

**Route/context:** `/health/blood-pressure`.

**Coverage:** **V** — actual native create/reject/edit/conflict/delete/paging and keyboard journeys with inspected desktop, phone, RTL and dark tablet captures; full acceptance remains open.

**Current finding/opportunity:** Source: readings, optional fields, range selection and deletion form a health-specific flow.

**Implementation progress:** Owned history now has stable date/time/ID database pages of six, including untimed readings and all saved dates. Period statistics identify their actual date window, independently of all-date history; no reading classification was added. Native form errors name fields, preserve raw malformed dates/enum values and retain actual numeric/context/note drafts. Duplicate untimed readings link to Time. Confirmed deletion updates history/statistics, clamps the former last page, and checks a revision so an older history cannot delete a changed reading. Native seven-record paging, six-reading period summary, date/context/notes, stale deletion and actual delete feedback are accepted locally. Twenty messages in fourteen UI locales; twenty-four checks/five suites passed, 57 s, then focused visual/script/copy packages. Final v4z source/build/JAR parity: twenty-two resources/six classes. Delete target measured 45 px; RTL timestamps isolated together and singular day corrected. Full Q01–Q10 remains open.

- [ ] W111.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W111.2 **Design, styling and motion:** Prioritise quick recording and a readable dated history/chart with text equivalent; keep decorative motion minimal.
- [x] W111.3 **Workflow, usability and features:** Use consistent units and measurement context; validate values and timestamps, distinguish missing from zero, and confirm delete impact.
- [ ] W111.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w112"></a>

### W112 — Edit blood-pressure reading

**Priority:** P1 · **Source:** [client-views/health/blood-pressure-edit.html](../../src/main/resources/templates/client-views/health/blood-pressure-edit.html)

**Route/context:** `/health/blood-pressure/edit/{id}`.

**Coverage:** **V** — actual native create/reject/edit/conflict/delete/paging and keyboard journeys with inspected desktop, phone, RTL and dark tablet captures; full acceptance remains open.

**Current finding/opportunity:** Source: an independent edit form may diverge from the add form.

**Implementation progress:** The shared measurement editor keeps the real date/time, optional translated arm/position and literal multiline notes. SHA-256 content revision plus owner/reading locks prevent an older editor overwriting newer data; native 409 presents latest saved values beside the retained draft, then reviewed save updates the same ID. Range/page return is retained. Dirty and already rejected drafts receive shared Cancel confirmation; Escape preserves values/focus, and approved leave returns once. Direct browser/tab-close/no-script and full concurrency/device acceptance remain open. Twenty messages in fourteen UI locales; twenty-four checks/five suites passed, 57 s, then focused visual/script/copy packages. Final v4z source/build/JAR parity: twenty-two resources/six classes. Delete target measured 45 px; RTL timestamps isolated together and singular day corrected. Full Q01–Q10 remains open.

- [ ] W112.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W112.2 **Design, styling and motion:** Share the measurement form, retain date/time and show that an existing reading is being changed.
- [x] W112.3 **Workflow, usability and features:** Test invalid values, unsaved changes and saved chart/history updates; preserve reading ownership.
- [ ] W112.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w113"></a>

### W113 — Daily nutrition record

**Priority:** P2 · **Source:** [shared-views/nutrition/daily-log.html](../../src/main/resources/templates/shared-views/nutrition/daily-log.html)

**Route/context:** `/nutrition`.

**Coverage:** **V** — native date error/correction, rejected save, two-editor conflict/reviewed save, reload and dirty date navigation; inspected desktop/phone/RTL/dark tablet captures. Full acceptance remains open.

**Current finding/opportunity:** Source: date navigation, daily inputs and summary create another tracking surface.

**Implementation progress:** Owned daily intake remains separate from saved targets; empty days have blank required inputs and optional blank values remain unrecorded rather than zero. Native date binding now returns controlled 400 with literal malformed-date echo/named focus instead of framework-only failure. Date navigation selects the day; the entry date is read-only when valid. Numeric errors retain actual drafts and original revision. Owner lock, refreshed row lock and SHA-256 owner/date/content revision protect concurrent first entries and older saved drafts; native 409 shows actual saved summary beside retained draft and explicit reviewed save updates the same day/ID. Native link and GET navigation share unsaved confirmation, including rejected drafts; Escape restores values/focus and approved leave returns once. Existing numerical/calculation/trimmed-note contracts retained, LTR units/dates, literal notes, translated required/optional guidance and nine recovery messages in fourteen UI locales. Fifteen checks/four suites passed (74 s), final CSS/resource package 9 s; exact v5a nineteen resources/ten classes verified. Eight inspected `v2-nutrition-*20261004.png` captures and native synthetic journeys accepted locally; full Q01–Q10/no-script/real PostgreSQL concurrency/device acceptance remains open.

- [ ] W113.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W113.2 **Design, styling and motion:** Build a concise coach-relevant daily record with readable totals and clear missing information; avoid turning this into an unrelated calorie app.
- [x] W113.3 **Workflow, usability and features:** Clarify whether figures are entered or calculated and show units; verify selected date, save/reload and privacy using synthetic data.
- [ ] W113.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w114"></a>

### W114 — Coaching inbox and notifications

**Priority:** P1 · **Source:** [shared-views/inbox/index.html](../../src/main/resources/templates/shared-views/inbox/index.html)

**Route/context:** `/inbox`.

**Coverage:** **V** — canonical client/trainer journeys and inspected local captures, compatible old entry routes and same history verified; full page/state/device acceptance remains open.

**Current finding/opportunity:** Live: notifications occupy a large panel before the coach conversation; conversations and system updates need clearer emphasis.

**Implementation progress:** Conversation-first compact notifications and actual participant identity; one-script/one-send bug repaired, grid clipping and stale preview repaired, safe literal attachments and readable actual Sent/Read receipts. Exact saved acknowledgement and stable timestamp/ID history; refreshed coaching locks and snapshot-bounded recipient locks. Failed/uncertain drafts and independent structured check-ins retained; native compatibility entries use the same store. Final 12-check/four-suite Java and five-case JavaScript gates pass; exact v5f 21 resources/11 classes and inspected sequential-role/native phone/RTL/dark reduced-motion evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Longer histories, actual failing-network/no-script/native-device and complete Q01–Q10 remain open; W115 session/goal context is not yet implemented.

- [ ] W114.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W114.2 **Design, styling and motion:** Create a conversation-first workspace with coach identity, unread status and compact notification access.
- [ ] W114.3 **Workflow, usability and features:** Preserve legacy message redirects and unread state; distinguish coach communication from Charlie; verify no-coach and failed-load states.
- [ ] W114.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w115"></a>

### W115 — Coaching conversation

**Priority:** P1 · **Source:** [shared-views/inbox/thread.html](../../src/main/resources/templates/shared-views/inbox/thread.html)

**Route/context:** `/inbox/{conversationId}`.

**Coverage:** **V** — canonical client/trainer journeys and inspected local captures, compatible old entry routes and same history verified; full page/state/device acceptance remains open.

**Current finding/opportunity:** Source: current inbox thread supports message composition and reading.

**Implementation progress:** Conversation-first compact notifications and actual participant identity; one-script/one-send bug repaired, grid clipping and stale preview repaired, safe literal attachments and readable actual Sent/Read receipts. Exact saved acknowledgement and stable timestamp/ID history; refreshed coaching locks and snapshot-bounded recipient locks. Failed/uncertain drafts and independent structured check-ins retained; native compatibility entries use the same store. Final 12-check/four-suite Java and five-case JavaScript gates pass; exact v5f 21 resources/11 classes and inspected sequential-role/native phone/RTL/dark reduced-motion evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Longer histories, actual failing-network/no-script/native-device and complete Q01–Q10 remain open; W115 session/goal context is not yet implemented.

- [ ] W115.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W115.2 **Design, styling and motion:** Use a readable conversation stream with session/goal context, delivery status and a stable mobile composer.
- [ ] W115.3 **Workflow, usability and features:** Preserve drafts on failure and reconnect; test message length, blocked payment language, scrolling, unread handling and private access.
- [ ] W115.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w116"></a>

### W116 — Legacy client messages inbox

**Priority:** P2 · **Source:** [shared-views/messages/client-inbox.html](../../src/main/resources/templates/shared-views/messages/client-inbox.html)

**Route/context:** `/client/messages → /inbox` — preserve compatibility/history; confirm whether this template should remain.

**Coverage:** **V** — canonical client/trainer journeys and inspected local captures, compatible old entry routes and same history verified; full page/state/device acceptance remains open.

**Current finding/opportunity:** Source: MessagingController redirects /client/messages to /inbox; the old template remains.

**Implementation progress:** Conversation-first compact notifications and actual participant identity; one-script/one-send bug repaired, grid clipping and stale preview repaired, safe literal attachments and readable actual Sent/Read receipts. Exact saved acknowledgement and stable timestamp/ID history; refreshed coaching locks and snapshot-bounded recipient locks. Failed/uncertain drafts and independent structured check-ins retained; native compatibility entries use the same store. Final 12-check/four-suite Java and five-case JavaScript gates pass; exact v5f 21 resources/11 classes and inspected sequential-role/native phone/RTL/dark reduced-motion evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Longer histories, actual failing-network/no-script/native-device and complete Q01–Q10 remain open; W115 session/goal context is not yet implemented.

- [ ] W116.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W116.2 **Design, styling and motion:** Reuse the canonical coaching inbox and retain only unique behaviour proven necessary.
- [x] W116.3 **Workflow, usability and features:** Verify migration of historic threads and compatible links; remove only after checking references/tests and avoid losing message history.
- [ ] W116.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w117"></a>

### W117 — Legacy trainer messages inbox

**Priority:** P2 · **Source:** [shared-views/messages/trainer-inbox.html](../../src/main/resources/templates/shared-views/messages/trainer-inbox.html)

**Route/context:** `/trainer/messages → /inbox` — preserve compatibility/history; confirm whether this template should remain.

**Coverage:** **V** — canonical client/trainer journeys and inspected local captures, compatible old entry routes and same history verified; full page/state/device acceptance remains open.

**Current finding/opportunity:** Source: /trainer/messages redirects to /inbox; old list presentation remains.

**Implementation progress:** Conversation-first compact notifications and actual participant identity; one-script/one-send bug repaired, grid clipping and stale preview repaired, safe literal attachments and readable actual Sent/Read receipts. Exact saved acknowledgement and stable timestamp/ID history; refreshed coaching locks and snapshot-bounded recipient locks. Failed/uncertain drafts and independent structured check-ins retained; native compatibility entries use the same store. Final 12-check/four-suite Java and five-case JavaScript gates pass; exact v5f 21 resources/11 classes and inspected sequential-role/native phone/RTL/dark reduced-motion evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Longer histories, actual failing-network/no-script/native-device and complete Q01–Q10 remain open; W115 session/goal context is not yet implemented.

- [ ] W117.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W117.2 **Design, styling and motion:** Use the shared conversation workspace with trainer/client context rather than a separate styling system.
- [x] W117.3 **Workflow, usability and features:** Confirm client roster and unread counts survive migration; keep role permissions and old entry links functioning.
- [ ] W117.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w118"></a>

### W118 — Legacy thread and check-in composer

**Priority:** P1 · **Source:** [shared-views/messages/thread.html](../../src/main/resources/templates/shared-views/messages/thread.html)

**Route/context:** `/messages/{threadId} → /inbox/{threadId}` — preserve compatibility/history; confirm whether this template should remain.

**Coverage:** **V** — canonical client/trainer journeys and inspected local captures, compatible old entry routes and same history verified; full page/state/device acceptance remains open.

**Current finding/opportunity:** Source: /messages/{threadId} redirects to inbox, but the old template includes a structured check-in composer.

**Implementation progress:** Conversation-first compact notifications and actual participant identity; one-script/one-send bug repaired, grid clipping and stale preview repaired, safe literal attachments and readable actual Sent/Read receipts. Exact saved acknowledgement and stable timestamp/ID history; refreshed coaching locks and snapshot-bounded recipient locks. Failed/uncertain drafts and independent structured check-ins retained; native compatibility entries use the same store. Final 12-check/four-suite Java and five-case JavaScript gates pass; exact v5f 21 resources/11 classes and inspected sequential-role/native phone/RTL/dark reduced-motion evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Longer histories, actual failing-network/no-script/native-device and complete Q01–Q10 remain open; W115 session/goal context is not yet implemented.

- [ ] W118.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W118.2 **Design, styling and motion:** Carry useful structured check-ins into the current conversation system with clear message type presentation.
- [x] W118.3 **Workflow, usability and features:** Determine whether those check-in actions are still live; preserve historic structured messages and blocked-payment controls before retiring markup.
- [ ] W118.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w119"></a>

### W119 — Charlie assistant command centre

**Priority:** P1 · **Source:** [shared-views/chat/chat.html](../../src/main/resources/templates/shared-views/chat/chat.html)

**Route/context:** `/chat`.

**Coverage:** **V** — inspected current state: [25-charlie](evidence/2026-10-01-v2/25-charlie.png). Remaining tabs, controls and states still require review.

**Current finding/opportunity:** Live: three-column assistant view includes many suggestion chips and supplement prompts; it competes with useful planning actions.

**Implementation progress:** Version 2.0 semantic conversation surface, four primary suggestions plus expandable prompts, editable chip drafts, labelled composer/log, safe title/insight text, native limit dialog, conversation reload and retained failed drafts are implemented. Three mocked-request JavaScript regressions and a premium real-template regression pass. Fresh responsive/theme/keyboard screenshots and provider acceptance remain open.

Schedule actions now share the native transactional application service, preserving custom cycles, active trainer access and completed matches; repeat requests retain the existing window and report actual added/already-scheduled counts. Empty or invalid plans return failure rather than false success. A focused 23-test gate and package passed, including real custom-only reload and current/ended trainer application checks. Compatibility generation also respects week intervals. Actual assistant proposal preview/confirmation and provider transmission remain unverified; see the 3 October checkpoint.

- [ ] W119.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W119.2 **Design, styling and motion:** Create a focused assistant workspace with a small contextual action set, clean conversation surface and optional side insights; use restrained response/status motion.
- [ ] W119.3 **Workflow, usability and features:** Separate AI from human coaching; show provider-disabled, limits and retry clearly; preview schedule/workout proposals before applying them; verify actual capabilities without transmitting private data.
- [ ] W119.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w120"></a>

### W120 — Legacy assistant conversation hub

**Priority:** P2 · **Source:** [shared-views/chat/hub.html](../../src/main/resources/templates/shared-views/chat/hub.html)

**Route/context:** `/chatv2 → /chat`; owned read-only history at `/chatv2/history`.

**Coverage:** **V** — actual owned archive journeys and inspected local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: /chatv2 currently redirects to /chat; old hub/folder/thread templates remain alongside live mutation APIs.

**Implementation progress:** Stable owner-scoped 20-conversation/30-message pagination, native search/return context, latest-message entry, literal saved instructions and accurate human/system labels. Shared named collections have selected feedback and an adaptive native disclosure; one correctly versioned optional script, bounded mobile panels and separated RTL dates. Nine focused Java checks and inspected desktop/phone/tablet/RTL/keyboard captures pass; exact v5j package and detailed limitations are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Complete no-script interactions, browser back/close/scroll retention and full Q01–Q10 remain open. Existing mutation/provider safety is retained without claiming actual provider/reset/draft acceptance.

- [ ] W120.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W120.2 **Design, styling and motion:** Use the current Charlie conversation architecture and retain useful organisation from the older system.
- [x] W120.3 **Workflow, usability and features:** Trace templates and data models before removal; preserve histories, mutation APIs and folder membership; do not silently merge unrelated conversations.
- [ ] W120.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w121"></a>

### W121 — Assistant conversation folder

**Priority:** P2 · **Source:** [shared-views/chat/folder.html](../../src/main/resources/templates/shared-views/chat/folder.html)

**Route/context:** `/chatv2/folder/{id} → /chat`; owned collection at `/chatv2/history/folder/{id}`.

**Coverage:** **V** — actual owned archive journeys and inspected local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: folder rename/colour/icon and new-chat controls exist in the alternate chat system.

**Implementation progress:** Stable owner-scoped 20-conversation/30-message pagination, native search/return context, latest-message entry, literal saved instructions and accurate human/system labels. Shared named collections have selected feedback and an adaptive native disclosure; one correctly versioned optional script, bounded mobile panels and separated RTL dates. Nine focused Java checks and inspected desktop/phone/tablet/RTL/keyboard captures pass; exact v5j package and detailed limitations are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Complete no-script interactions, browser back/close/scroll retention and full Q01–Q10 remain open. Existing mutation/provider safety is retained without claiming actual provider/reset/draft acceptance.

- [ ] W121.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W121.2 **Design, styling and motion:** Use a compact conversation collection with readable folder identity and stable selection feedback.
- [x] W121.3 **Workflow, usability and features:** Verify ownership, empty folders, rename errors and keyboard navigation; keep folder colour decorative rather than the only identifier.
- [ ] W121.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w122"></a>

### W122 — Alternative assistant thread

**Priority:** P2 · **Source:** [shared-views/chat/thread.html](../../src/main/resources/templates/shared-views/chat/thread.html)

**Route/context:** `/chatv2/{id} → /chat`; owned message history at `/chatv2/history/thread/{id}`.

**Coverage:** **V** — actual owned archive journeys and inspected local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: thread includes instructions/settings/reset controls apart from current Charlie chat.

**Implementation progress:** Stable owner-scoped 20-conversation/30-message pagination, native search/return context, latest-message entry, literal saved instructions and accurate human/system labels. Shared named collections have selected feedback and an adaptive native disclosure; one correctly versioned optional script, bounded mobile panels and separated RTL dates. Nine focused Java checks and inspected desktop/phone/tablet/RTL/keyboard captures pass; exact v5j package and detailed limitations are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Complete no-script interactions, browser back/close/scroll retention and full Q01–Q10 remain open. Existing mutation/provider safety is retained without claiming actual provider/reset/draft acceptance.

- [ ] W122.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W122.2 **Design, styling and motion:** Use the canonical assistant thread style and keep advanced instructions in an accessible secondary panel.
- [ ] W122.3 **Workflow, usability and features:** Confirm saved instructions, history and reset scope before migration; verify disabled provider and draft recovery.
- [ ] W122.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w123"></a>

### W123 — Assistant sidebar

**Priority:** P1 · **Source:** [shared-views/chat/fragments/sidebar.html](../../src/main/resources/templates/shared-views/chat/fragments/sidebar.html)

**Route/context:** Shared collection navigation rendered once by all three owned archive views.

**Coverage:** **V** — actual owned archive journeys and inspected local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: a sidebar fragment serves the alternate conversation organisation flow.

**Implementation progress:** Stable owner-scoped 20-conversation/30-message pagination, native search/return context, latest-message entry, literal saved instructions and accurate human/system labels. Shared named collections have selected feedback and an adaptive native disclosure; one correctly versioned optional script, bounded mobile panels and separated RTL dates. Nine focused Java checks and inspected desktop/phone/tablet/RTL/keyboard captures pass; exact v5j package and detailed limitations are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Complete no-script interactions, browser back/close/scroll retention and full Q01–Q10 remain open. Existing mutation/provider safety is retained without claiming actual provider/reset/draft acceptance.

- [ ] W123.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W123.2 **Design, styling and motion:** Create a reusable searchable conversation list with compact mobile drawer and clear active state.
- [ ] W123.3 **Workflow, usability and features:** Preserve selected conversation, keyboard order and scroll position; test long titles, empty histories and folder grouping.
- [ ] W123.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w124"></a>

### W124 — Personal notes hub

**Priority:** P2 · **Source:** [shared-views/notes/index.html](../../src/main/resources/templates/shared-views/notes/index.html)

**Route/context:** `/notes` (folder selection is a parameter, not a `/notes/folderId` page).

**Coverage:** **V** — owned native journeys and inspected current local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: standalone notes and folders coexist with a richer Vault module.

**Implementation progress:** Owned stable 20-note pagination and literal search; revision-aware saves retain stale drafts for explicit current-content review. Native folder destinations, validation, adaptive navigation, accessible rich controls and dependency-failure fallback are accepted locally. Reader typography/time metadata and mixed-language direction are repaired. Nine focused Java and eight JavaScript checks pass; exact v5o package and desktop/phone/dark tablet/RTL evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full no-script, formatting/export/copy, approved deletion, browser back/close/scroll, database races and Q01–Q10 remain open.

- [ ] W124.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W124.2 **Design, styling and motion:** Define Notes as quick capture and Vault as curated coaching insight, or unify them deliberately; use a consistent content library.
- [x] W124.3 **Workflow, usability and features:** Trace routes and data ownership; preserve existing notes/folders and search behaviour; ensure users understand where to save training reflections.
- [ ] W124.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w125"></a>

### W125 — Note folder detail

**Priority:** P2 · **Source:** [shared-views/notes/folders.html](../../src/main/resources/templates/shared-views/notes/folders.html)

**Route/context:** `/notes/folders/{id}`.

**Coverage:** **V** — owned native journeys and inspected current local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: rename/delete/search actions share the folder view.

**Implementation progress:** Owned stable 20-note pagination and literal search; revision-aware saves retain stale drafts for explicit current-content review. Native folder destinations, validation, adaptive navigation, accessible rich controls and dependency-failure fallback are accepted locally. Reader typography/time metadata and mixed-language direction are repaired. Nine focused Java and eight JavaScript checks pass; exact v5o package and desktop/phone/dark tablet/RTL evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full no-script, formatting/export/copy, approved deletion, browser back/close/scroll, database races and Q01–Q10 remain open.

- [ ] W125.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W125.2 **Design, styling and motion:** Build a concise content list with contextual folder management and readable empty/search states.
- [x] W125.3 **Workflow, usability and features:** Explain deleting folder versus deleting its contents; protect drafts and verify return to the correct folder after editing.
- [ ] W125.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w126"></a>

### W126 — Note editor

**Priority:** P2 · **Source:** [shared-views/notes/note-form.html](../../src/main/resources/templates/shared-views/notes/note-form.html)

**Route/context:** `/notes/folders/{folderId}/new`, `/notes/{id}/edit`.

**Coverage:** **V** — owned native journeys and inspected current local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: colour selection and content fields form a simple note editor.

**Implementation progress:** Owned stable 20-note pagination and literal search; revision-aware saves retain stale drafts for explicit current-content review. Native folder destinations, validation, adaptive navigation, accessible rich controls and dependency-failure fallback are accepted locally. Reader typography/time metadata and mixed-language direction are repaired. Nine focused Java and eight JavaScript checks pass; exact v5o package and desktop/phone/dark tablet/RTL evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full no-script, formatting/export/copy, approved deletion, browser back/close/scroll, database races and Q01–Q10 remain open.

- [ ] W126.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W126.2 **Design, styling and motion:** Use a clean distraction-reduced editor with visible saved/unsaved state and accessible colour labels.
- [x] W126.3 **Workflow, usability and features:** Preserve drafts, length validation and folder destination; show save failure without clearing text.
- [ ] W126.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w127"></a>

### W127 — Note reader

**Priority:** P2 · **Source:** [shared-views/notes/note-view.html](../../src/main/resources/templates/shared-views/notes/note-view.html)

**Route/context:** `/notes/{id}`.

**Coverage:** **V** — owned native journeys and inspected current local captures; complete page/state/device acceptance remains open.

**Current finding/opportunity:** Source: note content, edit and delete confirmation coexist.

**Implementation progress:** Owned stable 20-note pagination and literal search; revision-aware saves retain stale drafts for explicit current-content review. Native folder destinations, validation, adaptive navigation, accessible rich controls and dependency-failure fallback are accepted locally. Reader typography/time metadata and mixed-language direction are repaired. Nine focused Java and eight JavaScript checks pass; exact v5o package and desktop/phone/dark tablet/RTL evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full no-script, formatting/export/copy, approved deletion, browser back/close/scroll, database races and Q01–Q10 remain open.

- [ ] W127.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W127.2 **Design, styling and motion:** Create a readable note surface with date/folder context and quiet secondary actions.
- [ ] W127.3 **Workflow, usability and features:** Test safe rich-text rendering, ownership, edit return and deletion confirmation; preserve selection/copy for note content.
- [ ] W127.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w128"></a>

### W128 — Training reflection Vault

**Priority:** P2 · **Source:** [shared-views/vault/index.html](../../src/main/resources/templates/shared-views/vault/index.html)

**Route/context:** `/vault`.

**Coverage:** **V** — owned native journeys and inspected local captures; complete state/device acceptance remains open.

**Current finding/opportunity:** Source: filters, pinning, deletion and bulk AI summaries add substantial complexity.

**Implementation progress:** Owned stable 20-reflection pagination, literal combined filters and retained return context are accepted locally. Compact metrics/cards, honest account counts, filtered empty state and disabled page-only AI scope are verified. Actual enabled bulk generation remains open. Thirteen Java checks (ten workflow/security plus three AI unit), three JavaScript checks, 42 locale renders and exact v5v package evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full Q01–Q10 and external/device acceptance remain open.

- [ ] W128.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W128.2 **Design, styling and motion:** Create a reflection library with useful training/date/type filters, compact cards and clearly selected items.
- [x] W128.3 **Workflow, usability and features:** Explain what Summarise visible will send and which notes are included; verify empty results, bulk selection and provider-disabled state.
- [ ] W128.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w129"></a>

### W129 — Vault reflection editor

**Priority:** P2 · **Source:** [shared-views/vault/note-form.html](../../src/main/resources/templates/shared-views/vault/note-form.html)

**Route/context:** `/vault/new`, `/vault/{id}/edit`.

**Coverage:** **V** — owned native journeys and inspected local captures; complete state/device acceptance remains open.

**Current finding/opportunity:** Source: note type, date, tags, mood and linked session are collected together.

**Implementation progress:** Owned native editors retain all invalid/stale draft fields and show current saved content before an explicit same-ID reviewed save. Optional mood, actual workout-session links and historical owned-session choices are retained. Unchanged optional blank mood preserves existing insight. Thirteen Java checks (ten workflow/security plus three AI unit), three JavaScript checks, 42 locale renders and exact v5v package evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full Q01–Q10 and external/device acceptance remain open.

- [ ] W129.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W129.2 **Design, styling and motion:** Use contextual reflection prompts and clear session links in a compact editor.
- [x] W129.3 **Workflow, usability and features:** Preserve metadata and typed reflection on errors; validate session ownership and dates; do not force unnecessary mood/health disclosure.
- [ ] W129.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w130"></a>

### W130 — Reflection and AI insight

**Priority:** P2 · **Source:** [shared-views/vault/note-view.html](../../src/main/resources/templates/shared-views/vault/note-view.html)

**Route/context:** `/vault/{id}`.

**Coverage:** **V** — owned native journeys and inspected local captures; complete state/device acceptance remains open.

**Current finding/opportunity:** Source: insight generation, summary, pin and delete are combined with personal content.

**Implementation progress:** Escaped original reflection and insight are separated. Nullable saved-time/source-revision provenance is persisted and legacy unknown provenance is honest. Mocked provider failures/stale replies cannot replace or attach obsolete insight; actual enabled provider generation remains unverified. Thirteen Java checks (ten workflow/security plus three AI unit), three JavaScript checks, 42 locale renders and exact v5v package evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full Q01–Q10 and external/device acceptance remain open.

- [ ] W130.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W130.2 **Design, styling and motion:** Clearly separate the original reflection from generated insight and show provenance/date; use subtle saved/result transitions.
- [ ] W130.3 **Workflow, usability and features:** Verify failed/disabled generation, selected-note scope and safe content rendering; keep original notes unchanged unless explicitly edited.
- [ ] W130.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w131"></a>

### W131 — Trainer review submission

**Priority:** P1 · **Source:** [shared-views/review/form.html](../../src/main/resources/templates/shared-views/review/form.html)

**Route/context:** `/trainers/{trainerId}/review`.

**Coverage:** **V** — actual local two-editor/native keyboard journey and inspected viewport captures; full state/device acceptance remains open.

**Current finding/opportunity:** Source: star controls and tags need useful accessible names and eligibility checks.

**Implementation progress:** Native eligible entry now works from both profiles; missing/unavailable targets return 404. Locked eligibility/duplicate checks preserve conflict drafts without replacing the original review. Named invalid fields, literal mixed-language comments, translated known tags and truthful public-review status are accepted locally. Nineteen Java checks, two JavaScript checks, 42 locale renders and exact v5y package evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Full Q01–Q10, deployed concurrency and device acceptance remain open.

- [ ] W131.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W131.2 **Design, styling and motion:** Create a clear experience review with keyboard-operable rating, labelled tags and visible submission status.
- [x] W131.3 **Workflow, usability and features:** Require actual eligible relationship/session evidence where implemented; preserve text on errors and distinguish private feedback from public review.
- [ ] W131.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w132"></a>

### W132 — Merchandise store

**Priority:** P2 · **Source:** [shared-views/merch/shop.html](../../src/main/resources/templates/shared-views/merch/shop.html)

**Route/context:** `/merch`.

**Coverage:** **V** — inspected final native catalogue/filter/empty/media/return and desktop/phone/dark/reduced-motion/Arabic evidence; complete acceptance remains open.

**Current finding/opportunity:** Source: store categories and product cards form a peripheral commerce journey.

**Implementation progress:** Active catalogue is bounded to twenty stable matching rows, with literal search, category/stock filtering, native paging and truthful filtered emptiness. Checkout preserves validated shop return state. Cached/eager image failures and secondary-media errors recover visibly. Final v6e repairs legacy dark-action contrast, adds native results focus and compacts phone empty feedback. Twenty-four Java checks/four JavaScript checks/twenty-eight locale renders and final package/native evidence are in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Complete role/public/no-script/zoom/device and product-art-direction acceptance remains open.

- [ ] W132.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W132.2 **Design, styling and motion:** Give the shop polished real product imagery, compact categories and consistent product treatment; optional rotation media loads on request.
- [x] W132.3 **Workflow, usability and features:** Keep coaching navigation primary; show price, stock and product detail truthfully; verify filtering, unavailable items and image fallbacks.
- [ ] W132.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w133"></a>

**Local workflow repair, 4 October 2026:** Unavailable-product feedback is now visible; final v6b native `/merch/999999999/buy` redirects to `/merch` with its alert. Final v6e catalogue pagination, filter return context, truthful empty states and cached/broken media now have local functional/native evidence; complete acceptance remains open.

**Fresh browser progress, 1 October 2026:** Real native synthetic client/platform-admin login succeeded. The public shop has verified light-theme contrast, loaded product fronts and no horizontal desktop overflow at `20261001v2ac`; screenshots and exact limits are recorded in `docs/qa/2026-10-01-v2-implementation.md`. Additional grid/admin contrast source refinements and full device/theme/motion acceptance remain open.

### W133 — Merchandise checkout

**Priority:** P1 · **Source:** [shared-views/merch/checkout.html](../../src/main/resources/templates/shared-views/merch/checkout.html)

**Route/context:** `/merch/{id}/buy`.

**Coverage:** **V** — final native rejected/demo/history-Back journey and inspected desktop/phone/dark/reduced-motion/Arabic captures; full provider/device/state acceptance remains open.

**Current finding/opportunity:** Source: quantity, saved/new payment method and order summary form a payment journey.

**Implementation progress:** Native URL/form identity now persists one owned order/reservation through concurrent retry and browser Back. Stable hosted request snapshots/currency/URLs and provider-intent persistence protect uncertain reservations; safe rejected display fields and translated actions are retained. Twenty-one Java checks, two JavaScript checks, fourteen checkout renders and exact final v6b package/native evidence are recorded in the [prepared checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md). Legacy internal callers remain compatible; keyless/invalid native HTTP forms must review a fresh checkout quote. Final v6h first-purchase price acknowledgement and shared current-date expiry now pass 33 focused checks and selected native expiry/recovery evidence. Hosted sandbox/PostgreSQL/full Q01–Q10 acceptance remains open.

- [ ] W133.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W133.2 **Design, styling and motion:** Reuse the calm checkout pattern with quantity, actual totals and clear final action; keep animation limited to feedback.
- [ ] W133.3 **Workflow, usability and features:** Verify stock, quantity, delivery cost if supported, provider-disabled/pending/failure states and duplicate submission with sandbox payment.
- [ ] W133.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


- [ ] W133.A Prove hosted sandbox retry/timeout/cancellation and apply V11 with concurrent PostgreSQL acceptance; unknown expired sessions require reconciliation.
- [x] W133.B Add explicit changed-price acknowledgement before a first purchase and current-date validation for new/saved demo card expiry.

<a id="w134"></a>

### W134 — Merchandise order history

**Priority:** P2 · **Archived source:** [merch-orders.html](retired-templates/merch-orders.html), [merch-orders-page.js](retired-templates/merch-orders-page.js).

**Route/context:** `/profile/orders` remains a private compatibility redirect to canonical `/orders`.

**Coverage:** **R** — controller/template/script caller trace found no live caller of the duplicate template; archived outside runtime resources and excluded from the verified executable.

**Current finding/opportunity:** The duplicate client-filtered view used the same merchandise order store. Canonical W135 now owns the bounded order history; order IDs, account ownership and the existing alias remain. Both retired files are preserved in the audit archive. The source/build/JAR exclusion and native alias destination are verified. Replacement visual/provider/state acceptance remains under W135.

- [x] W134.1 Verify the current caller and alias; record the retired surface and preserved replacement.
- [x] W134.2 **Design, styling and motion:** Consolidate the unused duplicate into canonical W135.
- [x] W134.3 **Workflow, usability and features:** Preserve the same order store, IDs, owner isolation and `/profile/orders` redirect.
- [x] W134.4 **Complete:** retired after caller verification; runtime/device checks of the unused files are inapplicable. Canonical W135 Q01–Q10 remains open.


<a id="w135"></a>

### W135 — General order history

**Priority:** P2 · **Source:** [shared-views/orders/orders.html](../../src/main/resources/templates/shared-views/orders/orders.html)

**Route/context:** `/orders`.

**Coverage:** **V** — current desktop/phone, dark tablet and Arabic 320 px captures and native order/profile links inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Source: active/completed/cancelled sections and search coexist with merch orders.

**Implementation progress:** 4 October: canonical owned history now counts and selects at most twenty stable IDs before fetching only their snapshots; no collection join is paginated. Search treats %, _, !, + and & literally; all eight delivery filters, retained values, empty/invalid feedback, range and native page links work. Payment and recorded delivery are separate; simulated records explicitly show no payment/delivery and never expose invented tracking. Pending resume/cancel ownership, reservation and CSRF contracts remain. Profile loads only five recent orders plus the real total and links into canonical history from Options. A stray profile closing tag previously omitted card/crop/date dialogs; fixed the fragment, added Options Close, bounded the phone drawer to available width, made closed drawers inert and closed invisible dialogs unfocusable, and added Tab wrap/Escape focus return. Final accepted thirteen checks/three suites include 28 history/profile locale renders; all 42 bundles have exact key/placeholder parity. v6j executable matches 22 resources/34 classes and excludes retired history assets, DevTools, preview SQL and reload marker. Final native proof covers 26 owned fixtures split 20/6, literal search/filter empty state, alias, recent-order link, phone keyboard drawer wrap/close, dark tablet and Arabic narrow layout. [Checks, screenshots and remaining gaps](../qa/2026-10-03-v2-prepared-checkpoint.md#canonical-owned-order-history-and-profile-dialog-repair--w134w135). Hosted delivery/payment, full state/role/zoom/no-script/back/refresh and native-device Q acceptance remain open.

- [ ] W135.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W135.2 **Design, styling and motion:** Create a single understandable orders entry with type/status filters if models remain distinct.
- [x] W135.3 **Workflow, usability and features:** Preserve status/search links and receipts where implemented; reconcile duplicate histories before changing route ownership.
- [ ] W135.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w136"></a>

### W136 — Gym operations dashboard

**Priority:** P1 · **Source:** [gym-views/dashboard/gym-dashboard.html](../../src/main/resources/templates/gym-views/dashboard/gym-dashboard.html)

**Route/context:** `/gym/dashboard`.

**Coverage:** **V** — current authenticated viewport captures and native actions inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Source review only: overview, week and shortcuts establish a gym role surface.

**Implementation progress:** 4 October: owned gym name and aggregate current trainer/verified/latest-review counts lead the operations dashboard. Active subscribers are labelled accurately. The personal calendar is a native disclosure; existing today/week links and shortcuts remain. Twenty-seven connected trainers, including an accepted additional gym affiliation, produce 27/14/7/7; ended/pending connections and foreign reviews stay excluded. Native notes resubmission changes the real pending/needs-info counts to 8/6. Desktop/phone and dark tablet captures are inspected. Eighteen accepted checks across four suites include 28 actual roster/dashboard renders in fourteen locales; final v6l source/build/JAR evidence is recorded in the prepared checkpoint. Complete Q01–Q10 remains open.

- [ ] W136.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W136.2 **Design, styling and motion:** Create a premium operations workspace prioritising trainer approval state, membership changes and relevant upcoming work.
- [x] W136.3 **Workflow, usability and features:** Show verified/in-review trainers and real gym data; verify empty/new gym, multiple trainers and role-specific permissions.
- [ ] W136.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w137"></a>

### W137 — Gym trainer verification and invitation

**Priority:** P1 · **Source:** [gym-views/gym-admin/trainers.html](../../src/main/resources/templates/gym-views/gym-admin/trainers.html)

**Route/context:** `/gym/admin/trainers`.

**Coverage:** **V** — current authenticated viewport captures and native actions inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Source: invitation, verification submission, trainer list and notes dialogs are combined.

**Implementation progress:** 4 October: the roster leads with native literal search, own-gym review and verified filters, real range/empty feedback and at most twenty stable trainer cards. The final page has seven. Latest reviews use submitted date and ID; only selected current trainers and this gym’s notes are loaded. Platform verification is distinct from an own-gym review. Account creation moves into a native disclosure and still states that no invitation email is sent. Existing account/review transaction, password re-entry, notes limits, CSRF and consent-based multiple-gym connections are preserved. Invalid notes keep the page-two draft open; success retains encoded filters/page and submits for review. Ownership now checks the actual enabled gym owner. Desktop/phone, dark tablet and Arabic 320 px captures are inspected. Existing multiple-gym support was preserved rather than introduced here. Full Q01–Q10 and external/device acceptance remain open.

- [ ] W137.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W137.2 **Design, styling and motion:** Build a trainer operations list with clear invitation/verification state and a compact invitation panel.
- [x] W137.3 **Workflow, usability and features:** Explain invited versus platform verified and gym association; preserve multiple-gym membership; test rejected/missing-info states and private notes.
- [ ] W137.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w138"></a>

### W138 — Membership products

**Priority:** P1 · **Source:** [gym-views/gym-admin/memberships/list.html](../../src/main/resources/templates/gym-views/gym-admin/memberships/list.html)

**Route/context:** `/gym/admin/memberships`.

**Coverage:** **V** — current owned desktop/phone, dark tablet and Arabic 320 px membership captures inspected; complete Q01–Q10 remains open.

**Current finding/opportunity:** Source: product listing includes active status and deactivation.

**Implementation progress:** 4 October: replace the clipped wide table with product cards, whole-gym totals, native literal search/status filters, stable bounded pages and truthful empty/invalid feedback. Preserve legacy zero-based pages and bounded size; clamp oversized pages. Subscriber counts use one grouped query for the selected products. Native deactivation explains existing subscriptions continue; success retains filters/page. Lock and refresh the product before changing status so an already loaded stale entity cannot overwrite a newer stored price. All membership routes now use the same actual enabled gym-owner guard as the trainer roster. Sixteen accepted checks across three suites include fourteen catalogue and twenty-eight shared roster/dashboard locale renders; final v6m package matches 21 resources/24 classes. Native totals are 26/13/3, pages ten/ten/six, literal search one/zero retained results, and reversible deactivation/reactivation preserves price/subscribers. Five inspected captures and portable acceptance receipt are linked from the laptop handoff. No real notices/provider request or native-device proof. The existing floating quick-actions launcher can overlap a lower card action on tablet; shared launcher/dock collision acceptance remains open alongside Q01–Q10.

- [ ] W138.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [x] W138.2 **Design, styling and motion:** Use a compact product workspace with clear price, period, active status and edit/history actions.
- [x] W138.3 **Workflow, usability and features:** Explain product versus subscriber count; confirm deactivation impact; verify empty products and role ownership.
- [ ] W138.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w139"></a>

### W139 — Create or edit membership product

**Priority:** P1 · **Source:** [gym-views/gym-admin/memberships/form.html](../../src/main/resources/templates/gym-views/gym-admin/memberships/form.html)

**Route/context:** `/gym/admin/memberships/create`, `/gym/admin/memberships/{id}/edit`.

**Coverage:** **V** — selected light English desktop/phone editor states inspected on 9 October; complete acceptance remains open.

**Current finding/opportunity:** Source: one form handles both modes and shows current price.

**Implementation progress:** Fix the broken create mode that hid price/billing fields and submitted to memberships/null/edit. Use a dedicated editable-fields form with decimal price bounds and a trusted edit ID/current price from the server. Retain invalid drafts with HTTP 400. Create products using the authenticated gym and exact minor-unit conversion; ordinary edits cannot alter persisted price, billing period or gym ownership. Native create/edit/error/foreign-owner regressions pass. Full browser/device and financial currency migration remain open; the existing membership ledger remains USD.

**9 October laptop continuation:** Locked/refreshed metadata mutation preserves a newer stored price and existing subscriptions; native editor retains bounded catalogue context and invalid drafts. Current-price/monthly-billing/subscriber summary, semantic responsive controls and truthful delivery wording are implemented. Final 14 tests/two suites, 28 editor locale renders, CSS/package and 42 localisation bundles pass. Selected desktop/390px phone evidence and verification limits are recorded in the [membership editor checkpoint](../qa/2026-10-09-v2-membership-editor.md). W139.1/.2/.4 and full Q01–Q10 remain open.

- [ ] W139.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W139.2 **Design, styling and motion:** Create a clear product editor with readable billing period and preview; separate creation from price changes.
- [x] W139.3 **Workflow, usability and features:** Preserve validation and existing subscribers; prevent edits from silently bypassing the price-change workflow.
- [ ] W139.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w140"></a>

### W140 — Membership price change

**Priority:** P1 · **Source:** [gym-views/gym-admin/memberships/price-change.html](../../src/main/resources/templates/gym-views/gym-admin/memberships/price-change.html)

**Route/context:** `/gym/admin/memberships/{id}/price-change`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: impact, subscriber warning and Confirm & Notify are consequential actions.

**Implementation progress:** Replace the script-only confirmation modal with a native submitted form, required server-checked confirmation and progressive before/after/date preview using the shared confirmation dialogue. Bound and validate prices before conversion; invalid or unconfirmed requests retain their draft with HTTP 400. Lock the product and reuse an identical future price-change event so repeats do not create duplicate audit entries or notifications. State that configured email delivery is attempted, not tracked. Owned, invalid, confirmation and sequential replay tests use mocked delivery and pass. Concurrent replay and actual delivery/provider acceptance remain open.

**9 October continuation:** Native submissions now require the price displayed during review; stale/missing quotes cannot create another event or attempt notification. Lock/refresh and actual enabled gym-owner checks preserve current price/metadata. Date validation/default/minimum and midnight use an injected clock in Europe/London. Selected provider-free browser evidence verifies the populated progressive preview, shared confirmation, zero subscribers, future change and identical replay remaining one event. See the [price consistency checkpoint](../qa/2026-10-09-v2-membership-price-history.md) for fresh results and limits. Controlled notification-body preview and complete page/release acceptance remain open.

- [ ] W140.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W140.2 **Design, styling and motion:** Use an explicit before/after comparison, affected subscribers, effective date and confirmation summary; no distracting motion.
- [ ] W140.3 **Workflow, usability and features:** Verify notification preview, duplicate submission and no-subscriber states using controlled delivery; never send real notices during design audit.
- [ ] W140.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w141"></a>

### W141 — Membership price history

**Priority:** P1 · **Source:** [gym-views/gym-admin/memberships/price-history.html](../../src/main/resources/templates/gym-views/gym-admin/memberships/price-history.html)

**Route/context:** `/gym/admin/memberships/{id}/price-history`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: chronology and no-change state provide an audit trail.

**Implementation progress:** Keep owned audit records and actor context, use British date formatting with an explicit London zone and distinguish affected subscribers from confirmed delivery. Label increase/decrease figures as counts on the current page. Render actual populated history successfully and return 404 for other-gym product history. Native regression coverage passes. Full browser, large-history chronology/pagination and the USD-to-UK monetary policy migration remain open.

**9 October continuation:** History uses deterministic creation-date/ID ordering, bounded pages and oversized requests clamped to the last page. Due-price application selects effective-date/ID order under a refreshed product lock and preserves newer metadata/status. History explicitly shows USD, London times and future scheduled context, keeping event records intact. Focused same-time twenty-six-event, future exclusion, stale metadata and fourteen-locale render evidence is recorded in the [price consistency checkpoint](../qa/2026-10-09-v2-membership-price-history.md). Whole-page/role/device and Q01–Q10 acceptance remain open.

- [ ] W141.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W141.2 **Design, styling and motion:** Create a readable history with effective date, old/new values and actor context where available.
- [ ] W141.3 **Workflow, usability and features:** Check chronology, currency and future changes; preserve immutable financial/audit records and product ownership.
- [ ] W141.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w142"></a>

### W142 — Gym profile and account settings

**Priority:** P1 · **Source:** [gym-views/profile/profile.html](../../src/main/resources/templates/gym-views/profile/profile.html)

**Route/context:** `/profile` (gym-selected template).

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: organisation details, account security, image and data controls share one template.

**Implementation progress:** Align gym identity, details and account forms with shared Version 2.0 surfaces and focus controls; guard blank historical initials and label deletion confirmation. Preserve rejected gym/account drafts and show linked field errors. Validate gym bounds before any managed-account or file mutation; validate shared account edits on a detached candidate. Invalid replacement uploads cannot remove the existing photo, and failed textual fields cannot partially mutate the managed account. Clipboard feedback now reports success or failure through translated status messages. The 101-test profile/image/render batch passes. Full authenticated browser/device, export lifecycle and account-deletion acceptance remain open.

- [ ] W142.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W142.2 **Design, styling and motion:** Separate public gym presentation from private administrator settings with a true public preview.
- [ ] W142.3 **Workflow, usability and features:** Keep address/contact visibility explicit; preserve trainer associations, image limits, exports and deletion safeguards; test role-based settings access.
- [ ] W142.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w143"></a>

### W143 — Platform operations dashboard

**Priority:** P1 · **Source:** [admin-views/dashboard/admin-dashboard.html](../../src/main/resources/templates/admin-views/dashboard/admin-dashboard.html)

**Route/context:** `/admin/dashboard`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source review only: operations, support, outreach email and mode controls are mixed.

- [ ] W143.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W143.2 **Design, styling and motion:** Create a restrained operational workspace with queues, meaningful counts and contextual tools; keep brand polish without a cinematic hero.
- [ ] W143.3 **Workflow, usability and features:** Separate consequential outreach/mode changes from routine review; validate recipients and preview messages; maintain role/audit controls.
- [ ] W143.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w144"></a>

**Implementation progress, 1 October 2026:** Platform-only global operations, retained support/review drafts, server-held outreach preview, locked idempotent gym approval, truthful mail acceptance, native review filters and paged/redacted payment-policy evidence implemented as applicable. 129 tests across 9 suites passed in 51 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Full browser/device acceptance, durable mail reconciliation and human payment false-positive resolution remain open. Completion boxes remain unchecked.

### W144 — Support administration

**Priority:** P1 · **Source:** [admin-views/admin/feedback.html](../../src/main/resources/templates/admin-views/admin/feedback.html)

**Route/context:** `/admin/feedback`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: viewed/status updates and response email actions share the support list.

- [ ] W144.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W144.2 **Design, styling and motion:** Create a triage workspace with case status, original message and response draft clearly separated.
- [ ] W144.3 **Workflow, usability and features:** Keep response drafts on failure and show delivery status truthfully; test assignment/status transitions using controlled mail.
- [ ] W144.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w145"></a>

**Implementation progress, 1 October 2026:** Platform-only global operations, retained support/review drafts, server-held outreach preview, locked idempotent gym approval, truthful mail acceptance, native review filters and paged/redacted payment-policy evidence implemented as applicable. 129 tests across 9 suites passed in 51 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Full browser/device acceptance, durable mail reconciliation and human payment false-positive resolution remain open. Completion boxes remain unchecked.

### W145 — Gym application queue

**Priority:** P1 · **Source:** [admin-views/admin/gym-applications.html](../../src/main/resources/templates/admin-views/admin/gym-applications.html)

**Route/context:** `/admin/gym-applications`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: minimal queue needs enough state to support consistent review.

- [ ] W145.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W145.2 **Design, styling and motion:** Build a searchable review queue with submitted date, status, organisation and next action.
- [ ] W145.3 **Workflow, usability and features:** Verify empty and large queues, filtering and deep links; prevent accidental disclosure of application details outside authorised roles.
- [ ] W145.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w146"></a>

**Implementation progress, 1 October 2026:** Platform-only global operations, retained support/review drafts, server-held outreach preview, locked idempotent gym approval, truthful mail acceptance, native review filters and paged/redacted payment-policy evidence implemented as applicable. 129 tests across 9 suites passed in 51 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Full browser/device acceptance, durable mail reconciliation and human payment false-positive resolution remain open. Completion boxes remain unchecked.

### W146 — Gym application review

**Priority:** P1 · **Source:** [admin-views/admin/gym-application-detail.html](../../src/main/resources/templates/admin-views/admin/gym-application-detail.html)

**Route/context:** `/admin/gym-applications/{id}`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: application summary/timeline, follow-up and approve/decline controls are consequential.

- [ ] W146.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W146.2 **Design, styling and motion:** Create a review workspace with evidence, missing information and decision summary; visibly separate email from approval actions.
- [ ] W146.3 **Workflow, usability and features:** Confirm approved-account creation is idempotent; test needs-info/declined states, private tokens and controlled communications.
- [ ] W146.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w147"></a>

**Implementation progress, 1 October 2026:** Platform-only global operations, retained support/review drafts, server-held outreach preview, locked idempotent gym approval, truthful mail acceptance, native review filters and paged/redacted payment-policy evidence implemented as applicable. 129 tests across 9 suites passed in 51 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Full browser/device acceptance, durable mail reconciliation and human payment false-positive resolution remain open. Completion boxes remain unchecked.

### W147 — Off-platform payment moderation

**Priority:** P1 · **Source:** [admin-views/admin/off-platform-payments.html](../../src/main/resources/templates/admin-views/admin/off-platform-payments.html)

**Route/context:** `/admin/off-platform-payments`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: attempted off-platform payment list is a sensitive moderation surface.

- [ ] W147.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W147.2 **Design, styling and motion:** Use a readable evidence queue with reason, context and status; avoid celebratory motion.
- [ ] W147.3 **Workflow, usability and features:** Verify redaction, role access and false-positive handling; keep moderation decisions and user recovery guidance consistent.
- [ ] W147.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w148"></a>

**Implementation progress, 1 October 2026:** Platform-only global operations, retained support/review drafts, server-held outreach preview, locked idempotent gym approval, truthful mail acceptance, native review filters and paged/redacted payment-policy evidence implemented as applicable. 129 tests across 9 suites passed in 51 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Full browser/device acceptance, durable mail reconciliation and human payment false-positive resolution remain open. Completion boxes remain unchecked.

### W148 — Product administration

**Priority:** P2 · **Source:** [admin-views/merch/admin-list.html](../../src/main/resources/templates/admin-views/merch/admin-list.html)

**Route/context:** `/admin/merch`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: product list offers deletion and is separate from the public store.

- [ ] W148.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W148.2 **Design, styling and motion:** Create a compact product workspace with stock/status, preview and safe secondary actions.
- [ ] W148.3 **Workflow, usability and features:** Explain deactivation versus deletion and existing-order impact; verify filters, empty lists and authorisation.
- [ ] W148.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w149"></a>

**Implementation progress, 1 October 2026:** Native product filters/editor, retained invalid drafts, trusted metadata, locked stock-conflict review, bounded image decoding and truthful retirement messaging implemented. 13 tests across 3 suites passed in 31 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Fresh full editor/browser/device acceptance remains open; completion boxes remain unchecked.

### W149 — Product editor

**Priority:** P2 · **Source:** [admin-views/merch/admin-form.html](../../src/main/resources/templates/admin-views/merch/admin-form.html)

**Route/context:** `/admin/merch/new`, `/admin/merch/{id}/edit`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: name, description, price, stock, category, image and active state are editable.

- [ ] W149.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W149.2 **Design, styling and motion:** Build a practical editor with real product preview and image requirements; use clear save feedback.
- [ ] W149.3 **Workflow, usability and features:** Validate currency/stock/media and preserve values on failure; verify public-store preview matches saved product data.
- [ ] W149.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w150"></a>

**Implementation progress, 1 October 2026:** Native product filters/editor, retained invalid drafts, trusted metadata, locked stock-conflict review, bounded image decoding and truthful retirement messaging implemented. 13 tests across 3 suites passed in 31 seconds; see `docs/qa/2026-10-01-v2-implementation.md`. Fresh full editor/browser/device acceptance remains open; completion boxes remain unchecked.

### W150 — Trainer verification queue

**Priority:** P1 · **Source:** [admin-views/super-admin/verification-queue.html](../../src/main/resources/templates/admin-views/super-admin/verification-queue.html)

**Route/context:** `/super-admin/verification/queue`.

**Coverage:** **V** — Selected native queue and private reviewer workflow observed in the disposable email-disabled preview. Complete theme/device/filter/state acceptance remains open.

**Current finding/opportunity:** Source: 318-line queue includes decision dialogs and evidence review actions.

- [ ] W150.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W150.2 **Design, styling and motion:** Create an evidence-led queue with status, age and missing-information markers; keep decision controls deliberate.
- [ ] W150.3 **Workflow, usability and features:** Preserve approval/rejection/request-info permissions and audit trail; test keyboard dialogs, empty queue and duplicate decisions.
- [ ] W150.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w151"></a>

### W151 — Trainer verification decision

**Priority:** P1 · **Source:** [admin-views/super-admin/verification-detail.html](../../src/main/resources/templates/admin-views/super-admin/verification-detail.html)

**Route/context:** `/super-admin/verification/{id}`.

**Coverage:** **V** — Selected private evidence/history and native needs-info review observed. Final styled desktop request/decision columns and script-disabled phone stack are observed; full decision/theme/device acceptance remains open.

**Current finding/opportunity:** Source: request details and approve/reject/info actions need a clear evidence standard.

- [ ] W151.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W151.2 **Design, styling and motion:** Build a focused review surface with document preview, criteria and private decision notes; no ambient 3D.
- [ ] W151.3 **Workflow, usability and features:** Verify secure document access, reasons, concurrent review and downstream trainer visibility; check real qualifications before marking a trainer verified.
- [ ] W151.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w152"></a>

### W152 — Shared document and page layout

**Priority:** P1 · **Source:** [base.html](../../src/main/resources/templates/base.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Source: base applies theme, bundles, chat, shortcuts, platform panel and role context; conflicting surface conventions can affect every page.

- [ ] W152.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W152.2 **Design, styling and motion:** Create the 2.0 foundations here and in external tokens/styles: deliberate light/dark surfaces, typography, spacing, layers and motion; keep page-specific assets scoped.
- [ ] W152.3 **Workflow, usability and features:** Preserve route bundles, CSRF, language/direction and server theme preference; verify visible content without JS, metadata and no theme flash.
- [ ] W152.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w153"></a>

### W153 — Primary navigation and mobile menu

**Priority:** P0 · **Source:** [universal-fragments/layout/navbar.html](../../src/main/resources/templates/universal-fragments/layout/navbar.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: signed-in logo/first link overlap at 1280px; mobile development marker, logo, profile and menu also collide despite no document overflow.

- [ ] W153.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W153.2 **Design, styling and motion:** Rebuild a compact role-aware desktop rail/header and a clear mobile header; use measured breakpoints and purposeful menu transition.
- [ ] W153.3 **Workflow, usability and features:** Keep destinations and role restrictions stable; verify long names, keyboard menu, Escape, focus return, active link and 320px reflow.
- [ ] W153.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w154"></a>

### W154 — Shared footer

**Priority:** P1 · **Source:** [universal-fragments/layout/footer.html](../../src/main/resources/templates/universal-fragments/layout/footer.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: footer provides exploration and legal links; its conversion prompt should not overwhelm signed-in work.

- [ ] W154.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W154.2 **Design, styling and motion:** Create a concise public footer and quieter authenticated variant using the same brand language.
- [ ] W154.3 **Workflow, usability and features:** Verify all legal/help routes, keyboard order, current year and readable small text; avoid repeated signup prompts inside daily tasks.
- [ ] W154.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w155"></a>

### W155 — Profile menu and account identity

**Priority:** P1 · **Source:** [universal-fragments/layout/username-logout.html](../../src/main/resources/templates/universal-fragments/layout/username-logout.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: profile popup offers identity, milestones, tour and sign out; the large account control contributes to header crowding.

- [ ] W155.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W155.2 **Design, styling and motion:** Use a compact account entry and an accessible, readable profile menu with clear private account actions.
- [ ] W155.3 **Workflow, usability and features:** Preserve POST logout/CSRF and premium status; test long names, missing images, focus restoration and clear identity when switching roles.
- [ ] W155.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w156"></a>

### W156 — Language selector

**Priority:** P1 · **Source:** [universal-fragments/layout/language-selector.html](../../src/main/resources/templates/universal-fragments/layout/language-selector.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: custom language control and region choices must fit the shared header.

- [ ] W156.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W156.2 **Design, styling and motion:** Use a compact labelled language menu with an understandable selected state and consistent focus treatment.
- [ ] W156.3 **Workflow, usability and features:** Preserve server localisation and direction; test keyboard, translated long text and screen-reader names; do not reset the user's chosen locale on navigation.
- [ ] W156.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w157"></a>

### W157 — Persistent shortcut panel

**Priority:** P1 · **Source:** [universal-fragments/layout/platform-panel.html](../../src/main/resources/templates/universal-fragments/layout/platform-panel.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15-client-dashboard](evidence/2026-10-01-v2/15-client-dashboard.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: fixed bottom shortcut strip consumes space, with another icon row visible above it on mobile.

**3 October shared repair:** training QA exposed mobile `display:flex !important` rules overriding the shared duplicate-launcher suppression. These unnecessary overrides were removed from Charlie/quick-actions CSS. In final 320/390px browser states, both floating duplicates are `display:none` while the real dock destinations remain available. Shared controls now reserve header/dock clearance when scrolled into view. This is a focused collision repair; the complete panel customisation/roles/safe-area matrix remains open. See the [current checkpoint](../qa/2026-10-03-v2-prepared-checkpoint.md).

- [ ] W157.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W157.2 **Design, styling and motion:** Choose one intentional navigation system; use a small role-appropriate mobile dock and optional desktop shortcuts with no overlap.
- [ ] W157.3 **Workflow, usability and features:** Ensure every essential destination remains discoverable; test safe-area spacing, content padding, customisation, keyboard access and active state.
- [ ] W157.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w158"></a>

### W158 — Quick action launcher

**Priority:** P1 · **Source:** [universal-fragments/layout/quick-actions.html](../../src/main/resources/templates/universal-fragments/layout/quick-actions.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: floating launcher competes with assistant and bottom navigation; source also includes action creation controls.

- [ ] W158.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W158.2 **Design, styling and motion:** Integrate useful create actions into contextual page controls or one compact launcher; use the shared overlay/layer contract.
- [ ] W158.3 **Workflow, usability and features:** Keep feature permissions and custom actions intact; verify no overlay collision, Escape, focus return and accessible labels.
- [ ] W158.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w159"></a>

### W159 — Global Charlie widget

**Priority:** P1 · **Source:** [universal-fragments/chat/chat-widget.html](../../src/main/resources/templates/universal-fragments/chat/chat-widget.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [01-home-closed](evidence/2026-10-01-v2/01-home-closed.png), [26-client-dashboard-mobile](evidence/2026-10-01-v2/26-client-dashboard-mobile.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: floating assistant competes with other fixed controls; source includes history/delete and attachment behaviour.

- [ ] W159.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W159.2 **Design, styling and motion:** Give Charlie one consistent entry point and a readable compact panel; use a single controlled transition and clear human-versus-AI identity.
- [ ] W159.3 **Workflow, usability and features:** Preserve drafts and history rules; test disabled provider, attachment failure, limits, cancellation, focus and deletion scope without sending private data.
- [ ] W159.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w160"></a>

### W160 — First-use guided tour

**Priority:** P1 · **Source:** [universal-fragments/tutorial/site-tour.html](../../src/main/resources/templates/universal-fragments/tutorial/site-tour.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **V** — inspected current state: [15a-client-tour](evidence/2026-10-01-v2/15a-client-tour.png). Shared fragments are observed within their host screen, not tested in isolation.

**Current finding/opportunity:** Live: tour auto-scrolls into the dashboard and redirects attempted calendar navigation back until skipped.

- [ ] W160.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W160.2 **Design, styling and motion:** Replace compulsory page interception with an optional contextual walkthrough and visible checklist; allow normal navigation during learning.
- [ ] W160.3 **Workflow, usability and features:** Verify Skip/Escape immediately releases navigation and restores focus; offer replay; never require a tour to access the next workout.
- [ ] W160.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w161"></a>

### W161 — Development-mode notice

**Priority:** P2 · **Source:** [universal-fragments/dev/dev-mode.html](../../src/main/resources/templates/universal-fragments/dev/dev-mode.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: development banner can affect normal layout and user interpretation.

- [ ] W161.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W161.2 **Design, styling and motion:** Use a compact unmistakable test-environment marker with a clear explanation and sensible responsive placement.
- [ ] W161.3 **Workflow, usability and features:** Verify production hides development affordances and synthetic credentials; do not let banners obscure account forms or content.
- [ ] W161.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w162"></a>

### W162 — Page development status display

**Priority:** P2 · **Source:** [universal-fragments/dev/dev-page-display.html](../../src/main/resources/templates/universal-fragments/dev/dev-page-display.html)

**Route/context:** Shared component rendered by parent templates.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: page-lock/status component is shared and can leak implementation detail to users.

- [ ] W162.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W162.2 **Design, styling and motion:** Keep implementation status in the developer experience; expose only useful availability/help text in user-facing restricted states.
- [ ] W162.3 **Workflow, usability and features:** Test unlocked/locked/available status, role permissions and translated copy; avoid dead controls and false launch promises.
- [ ] W162.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w163"></a>

### W163 — Development route hub

**Priority:** P2 · **Source:** [system-views/dev-mode/hub.html](../../src/main/resources/templates/system-views/dev-mode/hub.html)

**Route/context:** `/dev-mode`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: guest/signed-in/unavailable route lists provide a useful QA surface.

- [ ] W163.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W163.2 **Design, styling and motion:** Create a compact internal route matrix with searchable availability and role context.
- [ ] W163.3 **Workflow, usability and features:** Preserve page locks and role protection; distinguish public access from seeded-demo access; verify production exposure policy.
- [ ] W163.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w164"></a>

### W164 — Temporarily restricted feature

**Priority:** P1 · **Source:** [system-views/dev-mode/restricted.html](../../src/main/resources/templates/system-views/dev-mode/restricted.html)

**Route/context:** `/dev-mode/restricted`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: restriction is a dedicated page and needs a useful recovery route.

- [ ] W164.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W164.2 **Design, styling and motion:** Use a clear status page with reason and available alternatives; use an optional small static brand visual.
- [ ] W164.3 **Workflow, usability and features:** Explain whether the restriction is development, plan or role-related; preserve intended destination safely and never promise an unsupported unlock date.
- [ ] W164.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w165"></a>

### W165 — Login required state

**Priority:** P1 · **Source:** [system-views/dev-mode/unauthorized.html](../../src/main/resources/templates/system-views/dev-mode/unauthorized.html)

**Route/context:** `/dev-mode/unauthorized`.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: separate login-required page can duplicate ordinary authentication redirects.

- [ ] W165.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W165.2 **Design, styling and motion:** Create a concise access explanation with login/join actions and relevant return context.
- [ ] W165.3 **Workflow, usability and features:** Ensure successful sign-in returns to an allowed original destination; verify guests and users with insufficient roles receive distinct guidance.
- [ ] W165.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w166"></a>

### W166 — Access denied

**Priority:** P1 · **Source:** [system-views/error/403.html](../../src/main/resources/templates/system-views/error/403.html)

**Route/context:** `/access-denied`, `/error` with 403 dispatch and controller-denied operations; not an ordinary successful page at those operations.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: access-denied template is also used by role/operation checks; the reason must remain useful.

- [ ] W166.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W166.2 **Design, styling and motion:** Create a clear permission/plan explanation with a safe home/help route; keep the animation minimal.
- [ ] W166.3 **Workflow, usability and features:** Preserve HTTP semantics and prevent private detail leakage; verify logged-out, wrong-role and plan-restricted cases with safe recovery.
- [ ] W166.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w167"></a>

### W167 — Page not found

**Priority:** P1 · **Source:** [system-views/error/404.html](../../src/main/resources/templates/system-views/error/404.html)

**Route/context:** `/error` with 404 dispatch; also used for missing resources by controllers.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: 404 recovery exists, but direct missing-route status and visual response need runtime verification.

- [ ] W167.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W167.2 **Design, styling and motion:** Use a concise branded recovery view with correct account-home and relevant navigation; optional static sculpture fragment.
- [ ] W167.3 **Workflow, usability and features:** Keep true 404 status and useful back behaviour; test deleted resources, invalid IDs and broken links without redirecting every miss to home.
- [ ] W167.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w168"></a>

### W168 — Server error

**Priority:** P0 · **Source:** [system-views/error/500.html](../../src/main/resources/templates/system-views/error/500.html)

**Route/context:** `/error` with 500 dispatch.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Runtime: preferences parsing failure cascaded into error handling and incomplete response; robust recovery needs its own proof.

- [ ] W168.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W168.2 **Design, styling and motion:** Create a minimal reliable error surface that does not depend on failing page model attributes; provide reference/support and safe retry.
- [ ] W168.3 **Workflow, usability and features:** Check uncommitted/committed response failures and no-session states; never leak stack traces; verify the user can recover without losing work unnecessarily.
- [ ] W168.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w169"></a>

### W169 — Generic error state

**Priority:** P1 · **Source:** [system-views/error/error.html](../../src/main/resources/templates/system-views/error/error.html)

**Route/context:** `/error` for other error dispatch.

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: generic error view overlaps status-specific templates.

- [ ] W169.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W169.2 **Design, styling and motion:** Reuse a robust error component with appropriate heading, reason and recovery; keep status-specific semantics.
- [ ] W169.3 **Workflow, usability and features:** Verify all error dispatch paths, absent model attributes and correct HTTP status; avoid recursive rendering failure and misleading success UI.
- [ ] W169.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.


<a id="w170"></a>

### W170 — Birthday Mission VI special page

**Priority:** P2 · **Source:** [birthday/mission-vi.html](../../src/main/resources/templates/birthday/mission-vi.html)

**Route/context:** `/birthday/mission-vi` (controller constant).

**Coverage:** **S** — source inventoried; no accepted current screenshot of this specific surface.

**Current finding/opportunity:** Source: /birthday/mission-vi is a separate personal experience, outside the coaching product journey.

- [ ] W170.1 Verify the complete current page/state in its correct role, including the relevant embedded flows; record evidence and confirm the findings.
- [ ] W170.2 **Design, styling and motion:** Keep isolated styling/assets and a distinct non-product identity; improve only if this special experience is still required.
- [ ] W170.3 **Workflow, usability and features:** Confirm whether this personal page belongs in the production coaching deployment; test hold-to-unlock, replay, touch and reduced motion separately.
- [ ] W170.4 **Complete:** meet Q01–Q10 as applicable; record changed files, fresh screenshots, functional checks and actual test results before checking this box.

<a id="w171"></a>

### W171 — Client read-only assigned schedule

**Priority:** P1 · **Source:** [client-views/client/assigned-schedule.html](../../src/main/resources/templates/client-views/client/assigned-schedule.html)

**Route/context:** `/client/plan/schedules/{assignmentId}`; the authenticated client must own the assignment and its schedule must belong to the assigning trainer.

**Coverage:** **S** — source, ownership regression and real-template rendering checked; no accepted current screenshot.

**Current finding/opportunity:** The original assignment link pointed to a missing route. The new viewer shows coach notes and exercise prescriptions without trainer editing controls. Weekly, daily and custom cycles retain every persisted entry day; weekday labels use the request locale. The focused plan/rendering/library run passed 80 tests in 29 seconds.

- [ ] W171.1 Verify populated, empty and historical assignments in the client role, and record fresh browser evidence.
- [ ] W171.2 **Design, styling and motion:** Verify readable day cards, prescription hierarchy, focus and responsive layout in both themes.
- [ ] W171.3 **Workflow, usability and features:** Verify plan/calendar return navigation, no-script use, custom-cycle days and rejection of foreign assignments in the browser.
- [ ] W171.4 **Complete:** meet Q01–Q10 as applicable; retain changed-file, screenshot and actual verification evidence before checking this box.

<a id="w172"></a>

### W172 — Trainer private assessment history

**Priority:** P1 · **Source:** [trainer-views/trainer/assessments.html](../../src/main/resources/templates/trainer-views/trainer/assessments.html)

**Route/context:** `/trainer/assessments`; enabled, verified trainers can read their own private assessments.

**Coverage:** **S** — real-template regression passes; no accepted current screenshot.

**Current finding/opportunity:** The existing controller referenced a missing template. The new history page shows owned client identities, optional scores, escaped private notes and dates, with return/edit links and a clear empty state. The trainer client workspace now links to this history.

- [ ] W172.1 Verify populated/empty history and trainer access in the browser; record evidence.
- [ ] W172.2 **Design, styling and motion:** Verify card readability, long notes, focus, phone/tablet and both themes.
- [ ] W172.3 **Workflow, usability and features:** Verify client-list return, assessment editing and revoked-trainer denial; retain private ownership.
- [ ] W172.4 **Complete:** meet Q01–Q10 as applicable; record screenshots, changed files and actual verification.

<a id="w173"></a>

### W173 — Admin review moderation

**Priority:** P1 · **Source:** [admin-views/reviews/moderation.html](../../src/main/resources/templates/admin-views/reviews/moderation.html)

**Route/context:** `/admin/moderation`; enabled platform/super admins only.

**Coverage:** **S** — populated/empty real-template and decision regressions pass; no accepted current screenshot.

**Current finding/opportunity:** The existing route referenced a missing template and used a placeholder trainer lookup instead of loading reported reviews. The restored page shows escaped review text, reason, reporter and timestamp with labelled decision notes, shared confirmation and Hide/Keep actions. Validation retains drafts. A decision resolves all pending reports for that review; stale queue actions cannot reverse it. Focused verification passed 92 tests in 39 seconds; concurrent database and browser interaction acceptance remain open.

- [ ] W173.1 Verify populated/empty/error queue states and correct admin role in the browser.
- [ ] W173.2 **Design, styling and motion:** Verify long report text, keyboard focus, confirmation and both-theme responsive layout.
- [ ] W173.3 **Workflow, usability and features:** Verify both decisions, all-report resolution, stale actions and denied users, including concurrent requests against the actual deployment database.
- [ ] W173.4 **Complete:** meet Q01–Q10 as applicable; record fresh screenshots and actual verification evidence.

### W174 — Trainer and gym connection workspace

**Template:** [affiliations.html](../../src/main/resources/templates/gym-views/affiliations.html)

**Route/context:** `/trainer/gyms` and `/gym/admin/trainers/affiliations`; fresh enabled trainer or the gym profile's actual owner. Android Account links to the appropriate website workspace, with separate website sign-in.

**Coverage:** **V** — selected final light desktop/dark tablet/dark phone observations and actual no-script keyboard acceptance/ending. Full acceptance remains open in the implementation log.

**Current finding/opportunity:** A single legacy `users.gym_id` contradicted the multiple-gym product rule. Separate unique trainer/gym pairs now record pending, active, declined and ended states. Both sides can initiate; only the recipient accepts/declines and only the sender cancels. Existing primary links remain valid until explicitly ended. Connections use account usernames rather than sharing the gym's sign-in secret code. Active membership does not verify qualifications or change client coaching/payment relationships.

- [x] W174.1 Implement explicit two-party consent, retained invalid drafts, native confirmation, CSRF/role boundaries, unique first requests and legacy precedence.
- [ ] W174.2 **Design, styling and motion:** accept both themes, desktop/tablet/phone, long names, no-match/empty/error states, keyboard and no-script forms in the final preview.
- [ ] W174.3 **Workflow, usability and features:** accept both initiation paths and all decisions; verify team/count/verification access after ending and production migration/backfill. Complete the existing-trainer review entry point and secure qualification workflow separately from affiliation.
- [ ] W174.4 **Complete:** meet Q01–Q10 as applicable and record fresh evidence. Local database proof does not establish production migration or whole-page completion.

### W175 — Private gym connection history

**Template:** [affiliation-history.html](../../src/main/resources/templates/gym-views/affiliation-history.html)

**Route/context:** `/trainer/gyms/history` and `/gym/admin/trainers/affiliations/history`; only the participating trainer or that gym's actual owner.

**Coverage:** **V** — selected final light desktop/dark phone history and no-script keyboard navigation; private-access/template/date regressions pass. Full acceptance remains open.

**Current finding/opportunity:** Record each request and decision without losing earlier events on reopening. Legacy imports remain explicitly identified. History shows the event time and which side acted, without revealing emails or other private account fields. A nonexistent pair returns 404. Trainer/gym deletion cascades affiliation/history; a deleted actor is nulled where the retained relationship remains.

- [x] W175.1 Implement ordered, scoped, non-cacheable request/decision history and unknown-pair handling.
- [ ] W175.2 **Design, styling and motion:** accept populated/legacy/empty history, timestamps, long content, focus/back navigation and all required sizes/themes.
- [ ] W175.3 **Workflow, usability and features:** verify preserved history on decline/reopen/end, deletion/retention policies and production database behaviour.
- [ ] W175.4 **Complete:** meet Q01–Q10 as applicable and record fresh evidence.

<a id="w176"></a>

### W176 — Trainer professional-review workspace

**Template:** [overview.html](../../src/main/resources/templates/trainer-views/verification/overview.html)

**Route/context:** `/trainer/verification`; fresh enabled trainer account. The dashboard links to this direct application workspace. A gym connection does not grant professional verification.

**Coverage:** **V** — selected verified/empty light desktop/phone and dark tablet/phone observation; dark views have scripts disabled. Independent submission, retained invalid draft, owned needs-info response, private previous requests, wrong trainer/role, disabled account, locked-screen entry and CSRF have real H2/template regressions. Approval/response concurrency uses the same account-before-request lock order. Unverified/populated browser proof and complete acceptance remain open.

**Current finding/opportunity:** Existing independent trainers lacked a review entry point; the gym workflow created a new account instead. Trainers can now submit their own professional details, see previous requests and respond to an information request. New requests remain pending. Responses retain reviewer feedback and resubmit for review; they do not set verified status or send provider messages. Duplicate open requests and already-verified applications are rejected. Eleven named copy keys cover all 14 UI locales. Previous request summaries are separate from the still-required immutable review-event history and secure qualification files.

- [x] W176.1 Implement direct confirmed submission, owned needs-info responses, private previous requests and validated draft recovery; preserve existing review authority and credential contracts.
- [ ] W176.2 **Design, styling and motion:** accept empty/pending/needs-info/rejected/verified states, long notes, all themes/sizes, keyboard, no-script and translated content.
- [ ] W176.3 **Workflow, usability and features:** private qualification documents and append-only reviewer history are implemented. Finish retention/scanning and production history acceptance; verify gym-assisted applications, full rejection/reapplication browser flow and delivery/reconciliation.
- [ ] W176.4 **Complete:** meet Q01–Q10 as applicable and record final browser/device/provider evidence.

<a id="w177"></a>

### W177 — Client saved check-in and coach response

**Priority:** P1 · **Source:** [client-review.html](../../src/main/resources/templates/client-views/checkins/client-review.html)

**Route/context:** `/checkins/client-review/{id}`, the owning client account. Existing check-in service ownership rules apply; clients retain their own history after coaching is paused. The recent-history and notification links open this route.

**Coverage:** **V** — actual responded/awaiting phone states and 320 px Arabic waiting view inspected. Backend tests verify saved owner response/focus/notes, read-only markup, translated content and foreign-client rejection.

**Current finding/opportunity:** The dashboard previously previewed the latest reply but the response notification had no full historical review destination. The new read view shows that week's saved response/focus, original answers, client notes and status/time. It excludes trainer phase notes, editing controls and other clients' data. Native return link remains approximately 45 px high instead of stretching to the header height.

- [ ] W177.1 Verify every current state, including missing records, disabled/stale accounts, long answers, full consent/role and history transitions; record evidence.
- [x] W177.2 **Design, styling and motion:** Present the saved response and submitted context as readable semantic panels with week/status context, native return navigation and no decorative motion.
- [x] W177.3 **Workflow, usability and features:** Implement owner-only read access, linked account notification and recent history; distinguish awaiting response from saved content. Real H2 and local notification journey pass.
- [ ] W177.4 **Complete:** finish Q01–Q10, consistent timezone presentation and native notification deep-link acceptance; preserve local/provider evidence distinctions.

<a id="w178"></a>

### W178 — Personal workout start confirmation

**Priority:** P1 · **Source:** [confirm-start.html](../../src/main/resources/templates/trainer-views/workouts/confirm-start.html)

**Route/context:** `GET /workouts/{id}/start`; WorkoutBuilderController checks the owned template, resumes an existing open session and redirects empty workouts. Confirmation uses the existing start POST. Paired with W066/W067; this dedicated template was missing its own source entry.

**Coverage:** **S** — direct render caller traced on 3 October. Earlier studio/start implementation evidence remains with W066/W067; no new full acceptance inferred from inventory reconciliation.

- [ ] W178.1 Verify current client/trainer ownership, fresh-start, existing-session and empty-workout states; record screenshots and actual outcomes.
- [ ] W178.2 **Design, styling and motion:** Keep the workout name, saved movements and start consequence visible with a clear native Back action.
- [ ] W178.3 **Workflow, usability and features:** Confirm GET creates no session, explicit POST creates exactly one intended session and repeated navigation resumes the correct one; preserve CSRF, display preference and movement history.
- [ ] W178.4 **Complete:** meet Q01–Q10 and reconcile browser/native start behaviour with W066/W067.

## Current shared template inventory — 3 October

The runtime source contains **181 HTML templates**: **178 numbered W entries minus three deliberately retired templates, plus six current shared fragments**. W177/W178 complete the missing dedicated page entries; the owner preferences fragment is shared by trainer and gym accounts. Every current HTML source now has an exact file link in this audit; inventory coverage establishes scope, not completed runtime acceptance. The original 170-template starting inventory and dated intermediate counts remain historical evidence.

| Check | Shared source | Related acceptance |
| --- | --- | --- |
| [ ] C01 | [schedule-drawer.html](../../src/main/resources/templates/shared-views/calendar/fragments/schedule-drawer.html) | W060/W061/W076: date/selection context, keyboard, no-script, owned plan preview and application. |
| [ ] C02 | [confirmation-dialog.html](../../src/main/resources/templates/universal-fragments/layout/confirmation-dialog.html) | E20/E21: labelled consequence, one overlay, Escape/Cancel focus return, long translated content and appropriate POST protection. |
| [ ] C03 | [verification evidence.html](../../src/main/resources/templates/universal-fragments/verification/evidence.html) | W151/W176: private documents, bounded batches, authorised downloads and timeline/decision integrity. |
| [ ] C04 | [workout-builder-row.html](../../src/main/resources/templates/fragments/workout-builder-row.html) | W066/W069/W070/W078: labels, owned catalogue/custom references, preserved values and shared row commands. |
| [ ] C05 | [workout-display-preview.html](../../src/main/resources/templates/fragments/workout-display-preview.html) | W071/W072/E07: truthful preview/live placement parity, custom order/modules, preferred layout and native alignment. |
| [ ] C06 | [account-preferences.html](../../src/main/resources/templates/universal-fragments/layout/account-preferences.html) | W085/gym owner settings: native appearance/calendar/accessibility persistence, preserved unrelated preferences, translated labels and one CSRF field per form. |

## Embedded flows that need their own checks

A single template can contain several important screens or dialogs. Complete these alongside its W entry; do not consider the profile or calendar finished after reviewing only its opening tab. These are interaction surfaces, not additional HTML templates.

| ID | Surface | Design/workflow change and required verification |
| --- | --- | --- |
| E01 | Profile identity and image | Separate public preview from private details; test upload limits, cropping/failure, missing image and removal. |
| E02 | Email/phone changes | Show current, pending and verified values clearly; test incorrect/expired code, resend, back navigation and saved state. |
| E03 | Appearance/theme | Preview accessible light/dark states, preserve server preference, prevent flash and test every overlay in both themes. |
| E04 | Accessibility and motion | Give understandable choices for motion/readability; preserve preferences across devices where supported; test system reduced motion. |
| E05 | Health and training preferences | Group useful questions, explain relevance/privacy and retain invalid values; show when answers influence the plan. |
| E06 | Milestones and progress settings | Distinguish earned history from editable goals; explain criteria and avoid invented progress. |
| E07 | Exercise/workout display settings | Preview how logging/player layout changes; distinguish presentation templates from training programmes; preserve active sessions. |
| E08 | Password/security | Make current/new password rules and success clear; validate server errors, session expiry and keyboard password controls. |
| E09 | Billing/subscription/card controls | Show current plan, renewal/status and consequential action summary; verify sandbox failure, pending/cancellation and repeated requests. |
| E10 | Export/deletion | Explain exact scope and processing state; test ownership and confirmation using disposable data; preserve financial records as required by existing contracts. |
| E11 | Month schedule drawer | Preview a schedule's affected dates before application; verify conflicts, repeated application and drawer focus restoration. |
| E12 | Week schedule drawer | Use the same vocabulary/state model as month; preserve selected dates and avoid controls hiding the week on mobile. |
| E13 | Calendar task/schedule action menus | Distinguish completing, rescheduling, editing and removing; test series versus occurrence behaviour against actual services. |
| E14 | Workout/player exercise controls | Check each exercise type, unit, set, rest timer, pause and finish; retain partial work and avoid accidental completion. |
| E15 | Programme/library sharing dialogs | Show recipients, ownership and scope; preview shared content and test duplicate shares/invalid recipients. |
| E16 | Trainer relationship request/change | Explain pending/active/ended states and the single-active-trainer rule; test unavailable actions and history retention. |
| E17 | Inbox composition/attachment | Retain drafts; clearly identify human recipient; test attachment limits, failed sending and read/unread state with synthetic data. |
| E18 | Charlie composition/history/attachments | Identify the AI assistant; test unavailable provider, incomplete reply, cancellation, upload failure and scoped deletion. |
| E19 | AI-generated plan/schedule proposals | Show a preview/diff and require deliberate application; verify resulting saved records rather than treating response text as completion. |
| E20 | Navigation/account/shortcut overlays | Test one overlay at a time, Escape, outside click, keyboard, focus return, safe areas and long names. |
| E21 | Verification document/decision dialogs | Protect evidence; show reason and status; test concurrent/repeated decisions without real approvals or external notifications. |
| E22 | Gym price-change confirmation | Show before/after prices, affected subscribers, effective date and notification preview; validate using controlled delivery. |
| E23 | Guided tour and replay | Make it optional; normal navigation must keep working; test Skip/Escape, persistent completion and replay without losing work. |
| E24 | Locked/unavailable/subscription prompts | Explain why an action is unavailable and the correct next step; preserve safe return context; distinguish role, plan and development restrictions. |

- [ ] E01 Profile identity and image — completed with its parent W entry and individual evidence.
- [ ] E02 Email/phone changes — completed with its parent W entry and individual evidence.
- [ ] E03 Appearance/theme — completed with its parent W entry and individual evidence.
- [ ] E04 Accessibility and motion — completed with its parent W entry and individual evidence.
- [ ] E05 Health and training preferences — completed with its parent W entry and individual evidence.
- [ ] E06 Milestones and progress settings — completed with its parent W entry and individual evidence.
- [ ] E07 Exercise/workout display settings — completed with its parent W entry and individual evidence.
- [ ] E08 Password/security — completed with its parent W entry and individual evidence.
- [ ] E09 Billing/subscription/card controls — completed with its parent W entry and individual evidence.
- [ ] E10 Export/deletion — completed with its parent W entry and individual evidence.
- [ ] E11 Month schedule drawer — completed with its parent W entry and individual evidence.
- [ ] E12 Week schedule drawer — completed with its parent W entry and individual evidence.
- [ ] E13 Calendar task/schedule action menus — completed with its parent W entry and individual evidence.
- [ ] E14 Workout/player exercise controls — completed with its parent W entry and individual evidence.
- [ ] E15 Programme/library sharing dialogs — completed with its parent W entry and individual evidence.
- [ ] E16 Trainer relationship request/change — completed with its parent W entry and individual evidence.
- [ ] E17 Inbox composition/attachment — completed with its parent W entry and individual evidence.
- [ ] E18 Charlie composition/history/attachments — completed with its parent W entry and individual evidence.
- [ ] E19 AI-generated plan/schedule proposals — completed with its parent W entry and individual evidence.
- [ ] E20 Navigation/account/shortcut overlays — completed with its parent W entry and individual evidence.
- [ ] E21 Verification document/decision dialogs — completed with its parent W entry and individual evidence.
- [ ] E22 Gym price-change confirmation — completed with its parent W entry and individual evidence.
- [ ] E23 Guided tour and replay — completed with its parent W entry and individual evidence.
- [ ] E24 Locked/unavailable/subscription prompts — completed with its parent W entry and individual evidence.

## Android page-by-page checklist

At the initial 1 October inspection, all native entries were **S: source-only** and no Android build/emulator walkthrough had been performed. The dated follow-up below records the subsequent API 35 emulator/build evidence; physical-device acceptance remains open. The Compose shell is the entry point observed in MainActivity; older local-management files and the WebView implementation require reachability checks. Preserve useful functionality and data when deciding whether to migrate or retire a surface.

Native styling should use the same brand, hierarchy, vocabulary and accessible motion principles while respecting Android navigation, Back, system insets, text scaling and TalkBack. Avoid importing desktop panel arrangements directly into a phone.

### A001 — Launch/auth loading

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** LoadingScreen / TrainerHubApp.

- [ ] A001.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A001.2 Give startup a calm branded state and useful recoverable loading/error behaviour; check cold launch, restored/expired sessions and process recreation.
- [ ] A001.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A002 — Public navigation shell

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** PublicShell.

- [ ] A002.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A002.2 Use compact brand/navigation with an obvious next action; test Android Back, insets, font scaling and retained signup state.
- [ ] A002.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A003 — Welcome

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** WelcomeScreen.

- [ ] A003.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A003.2 Introduce the real verified coaching journey and clear join/browse paths; optional small static/motion brand visual must not delay use.
- [ ] A003.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A004 — Trainer exploration

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** ExploreScreen.

- [ ] A004.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A004.2 Present actual searchable verified listings when supported; distinguish current informational preview from a complete native directory and verify API visibility rules.
- [ ] A004.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A005 — Login

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** LoginScreen.

- [ ] A005.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A005.2 Polish role/code/password entry, validation and recovery; preserve draft fields and verify code expiry, rejection, loading and return destinations.
- [ ] A005.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A006 — Client signup

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** SignupScreen — client role.

- [ ] A006.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A006.2 Create a short readable client path; match web verification/preference continuation and test server errors with retained fields.
- [ ] A006.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A007 — Trainer signup

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** SignupScreen — trainer role.

- [ ] A007.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A007.2 Explain pending verification and professional profile steps; align verified-only visibility and reject any implied immediate approval.
- [ ] A007.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A008 — Gym signup/application

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** SignupScreen — gym role.

- [ ] A008.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A008.2 Reconcile native signup with web application/review semantics; explain whether an application or active account is created and test every pending/rejected state.
- [ ] A008.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A009 — Authenticated shell

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** SignedInShell / destinationsFor.

- [ ] A009.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A009.2 Create one role-specific bottom navigation, compact header and safe content insets; use correct Back/selected route and test state restoration.
- [ ] A009.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A010 — Client home

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** HomeScreen — client role.

- [ ] A010.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A010.2 Prioritise next session, coach action and progress; move logout into a consistent account area while retaining access; verify summary reflects real records.
- [ ] A010.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A011 — Trainer home

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** HomeScreen — trainer role.

- [ ] A011.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A011.2 Prioritise actionable client needs and verification state; show useful queues without confusing demo counts for live data.
- [ ] A011.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A012 — Gym home

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** HomeScreen — gym role.

- [ ] A012.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A012.2 Prioritise trainer/approval operations and useful counts; align permissions and copy with gym web flows.
- [ ] A012.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A013 — Day plan

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** DayScreen.

- [ ] A013.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A013.2 Make tasks/sessions and completion readable and touch-friendly; handle completion errors/repeated taps, empty days and a safe return to the selected date.
- [ ] A013.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A014 — Calendar

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** CalendarScreen.

- [ ] A014.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A014.2 Source loads current-month days and lists active dates; add understandable date navigation/empty-day discovery if services support it and preserve selection when returning from day view.
- [ ] A014.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A015 — Training log

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** TrainingScreen.

- [ ] A015.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A015.2 Source saves a fixed 45-minute duration; expose actual duration/structured logging where required and clarify notes versus a full workout session; retain notes on request failure.
- [ ] A015.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A016 — Coach/assistant chat

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** ChatScreen.

- [ ] A016.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A016.2 Source uses ASSISTANT history; identify AI clearly rather than implying a human coach. Match Charlie naming and separately define any native human inbox; retain drafts and test provider failure.
- [ ] A016.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A017 — Trainer client list

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** RoleListScreen — clients route.

- [ ] A017.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A017.2 Create useful relationship rows with next actions and detail paths; test data scope and active/pending/ended clients.
- [ ] A017.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A018 — Gym trainer list

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** RoleListScreen — trainers route.

- [ ] A018.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A018.2 Show affiliation and verification status with justified actions; test trainers with multiple gym associations.
- [ ] A018.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A019 — Gym requests list

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** RoleListScreen — requests route.

- [ ] A019.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A019.2 Verify whether repository.roleItems differentiates this route from trainers; present distinct request status and permission-controlled next actions instead of duplicated records.
- [ ] A019.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A020 — More/account

**Coverage:** V (selected trainer Account on API 35; complete acceptance remains open) · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** MoreScreen.

The updated APK was installed on 2 October. Synthetic trainer login, Account/support/professional-review/privacy sections and sign-out visibility were observed. The review link opens the exact `/trainer/verification` URI in the separate browser, which redirects to website login. Chrome first-run notification onboarding limits further emulator-browser proof. Reopening native Account retains its position. Evidence: [professional review Account](evidence/v2-native-professional-review-account-20261002.png). Build and four existing unit tests pass in 11 seconds; full role/device/theme/accessibility/offline acceptance remains open.

- [ ] A020.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A020.2 Replace technical session-storage copy with useful account/privacy actions; keep sign out accessible; align private settings and public profile preview with web.
- [ ] A020.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A021 — Shared loading/error/content states

**Coverage:** S · **Source:** [ui/TrainerHubApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt) · **Context:** ContentState.

- [ ] A021.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A021.2 Provide actionable retry and helpful empty states with preserved context; check offline/timeouts, error removal on retry, accessibility announcements and text scaling.
- [ ] A021.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A022 — Alternate WebView implementation

**Coverage:** S · **Source:** [mobile/MobileWebsiteApp.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/mobile/MobileWebsiteApp.kt) · **Context:** MobileWebsiteApp — reachability unconfirmed.

- [ ] A022.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A022.2 Decide its supported purpose after tracing callers; if retained test file upload, cookies/session, safe external links, Back, cache and keyboard. Native Compose acceptance remains separate.
- [ ] A022.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A023 — Older trainer dashboard

**Coverage:** S · **Source:** [ui/dashboard/DashboardScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/dashboard/DashboardScreen.kt) · **Context:** DashboardScreen — reachability unconfirmed.

- [ ] A023.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A023.2 Confirm whether local-management data belongs in the current connected product; migrate useful summary/reminders without duplicating live counts.
- [ ] A023.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A024 — Older client list

**Coverage:** S · **Source:** [ui/clients/ClientsScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/ClientsScreen.kt) · **Context:** ClientsScreen — reachability unconfirmed.

- [ ] A024.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A024.2 Reconcile local and remote client identity/data before retirement or migration; redesign list/search/empty state only in the supported flow.
- [ ] A024.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A025 — Add local client

**Coverage:** S · **Source:** [ui/clients/AddClientScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/AddClientScreen.kt) · **Context:** AddClientScreen — reachability unconfirmed.

- [ ] A025.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A025.2 Clarify manual local record versus platform relationship; validate input and prevent creating misleading linked client accounts.
- [ ] A025.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A026 — Local client detail

**Coverage:** S · **Source:** [ui/clients/ClientDetailScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/ClientDetailScreen.kt) · **Context:** ClientDetailScreen — reachability unconfirmed.

- [ ] A026.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A026.2 Separate plans, sessions and invoices in a focused client workspace; preserve existing records and establish migration/ownership rules.
- [ ] A026.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A027 — Local plan editor

**Coverage:** S · **Source:** [ui/clients/PlanEditorScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/PlanEditorScreen.kt) · **Context:** PlanEditorScreen — reachability unconfirmed.

- [ ] A027.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A027.2 Use readable structured editing with saved/draft states; map local plan concepts to the supported web programme/assignment model before syncing.
- [ ] A027.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A028 — Local session editor

**Coverage:** S · **Source:** [ui/clients/SessionEditorScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/SessionEditorScreen.kt) · **Context:** SessionEditorScreen — reachability unconfirmed.

- [ ] A028.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A028.2 Make date/time/duration and completion clear; test device timezone, reminders, invalid values and conflict behaviour.
- [ ] A028.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A029 — Local invoice editor

**Coverage:** S · **Source:** [ui/clients/InvoiceEditorScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/clients/InvoiceEditorScreen.kt) · **Context:** InvoiceEditorScreen — reachability unconfirmed.

- [ ] A029.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A029.2 Clarify draft/invoice/payment status and local versus platform finance; ensure this does not introduce off-platform payment messaging contrary to the product rules.
- [ ] A029.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A030 — Older trainer profile

**Coverage:** S · **Source:** [ui/profile/TrainerProfileScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/profile/TrainerProfileScreen.kt) · **Context:** TrainerProfileScreen — reachability unconfirmed.

- [ ] A030.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A030.2 Align public credentials and private details with verification; retain local data and test image/contact visibility before choosing a replacement.
- [ ] A030.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A031 — Older settings

**Coverage:** S · **Source:** [ui/settings/SettingsScreen.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/settings/SettingsScreen.kt) · **Context:** SettingsScreen — reachability unconfirmed.

- [ ] A031.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A031.2 Reconcile theme, reminders and account controls with current shell; test permission denial, settings persistence and logout.
- [ ] A031.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.

### A032 — Native shared components/theme

**Coverage:** S · **Source:** [ui/components/CommonComponents.kt](../../../Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/components/CommonComponents.kt) · **Context:** CommonComponents + ui/theme/TrainerHubTheme.kt.

- [ ] A032.1 Confirm runtime reachability and inspect the complete native screen with synthetic data.
- [ ] A032.2 Build the 2.0 tokens and readable card/form/status patterns; verify contrast, TalkBack, 200% text size, reduced animation, touch targets and system bar colours.
- [ ] A032.3 Verify Android Back/insets/keyboard, TalkBack/text scale, error/offline states and web/API rule parity; record native build/device evidence before completion.


## Verification record and release gates

### Checks actually performed in this audit

| Check/command | Result | Limit |
| --- | --- | --- |
| Repository instructions, controllers, all template structures, assets and native screen source inventory | 170 web template/component entries reconciled against disk | Source structure does not prove visual or functional correctness. |
| `npm run build:css` in Web_App | Passed | Browserslist data warning; host Node/npm differ from package pins. No dependency upgrade performed. |
| Hidden local `./gradlew.bat bootRun --console=plain --args="--spring.profiles.active=local"` | Started with local profile; port 8081 and homepage HTTP 200 verified | Startup passed; preferences subsequently raised a server exception. This is not whole-app acceptance. |
| `npm run test:3d` | Passed: 6 tests, 0 failures | Current asset/animation baseline; new 2.0 models and device performance are untested. |
| Current app-browser walkthrough | 30 accepted screenshots across guest/client desktop and selected phone/tablet states | Not every control or page was exercised. Protected trainer/gym/admin and native screens remain source-only. |
| Preferences navigation | Failed: incomplete chunked response and Thymeleaf error at select-preferences line 33 | Root form-binding problem identified; fix and full recovery test are outstanding. |
| Generated audit coverage and local-link validation | Checked when generating this document | Verification of the checklist's inventory/links, not certification of product behaviour. |

Local runtime in this run: Java 21.0.11; host Node 24.15.0/npm 11.12.1. Repository frontend pins are Node 22.22.x/npm 11.11.x. Use the pinned toolchain for release checks. No full Gradle test suite, live payment/provider test, production deployment, native build or native-device validation was run for this documentation task.

### Final 2.0 release gates

- [ ] G01 All current templates are either completed with evidence or deliberately retired with replacement/redirect/data impact documented. No unexplained missing page.
- [ ] G02 Guest, client, trainer, gym, admin and super-admin complete their real journeys with authorised local test accounts.
- [ ] G03 Signup, verification, preferences, coach relationships, plan assignment, workout execution and recovery pass meaningful backend/UI checks.
- [ ] G04 Shared navigation, light/dark themes, overlays and fixed controls pass desktop/tablet/phone, keyboard, zoom and reduced-motion reviews.
- [ ] G05 Homepage sculpture passes closed/open/selected/replay/failure/poster states, model contracts and measured representative-device performance.
- [ ] G06 Human messaging and Charlie are clearly separated; legacy histories/links work and failures retain drafts appropriately.
- [ ] G07 Terms, privacy/payment/subscription pages contain reviewed release content and are reachable from every relevant flow.
- [ ] G08 Financial changes, verification and outbound communications have sandbox/controlled-delivery evidence; no unintended real-world side effects.
- [ ] G09 Native app journeys, API parity, role/verification rules, session handling, offline/error behaviour, accessibility and device performance are verified separately.
- [ ] G10 Run the actual required backend/frontend/native build/test suites with the pinned toolchain; address real failures and record results.
- [ ] G11 Complete release metadata/cache updates and deployment/rollback preparation under the project's normal release authority.
- [ ] G12 James reviews the final desktop/mobile visual result and each completed implementation slice before Version 2.0 is labelled ready.

### Completion record for each page

When a page is completed, add a short dated note below its checklist: changed files, final screenshots, functional states exercised, exact tests/results and any remaining decision. Use fresh evidence after implementation; the screenshots below show the **starting point**, not the desired 2.0 result.

## Current screenshot evidence gallery

The following numbered steps describe the actual visited/captured state and its review health. A useful current component can still have 2.0 work outstanding. A screenshot covers the displayed viewport/state; scrolling DOM/source coverage must not be presented as a full-page visual inspection.

### Evidence step 01 — Guest homepage, closed sculpture

**Route/context:** `/` · **Record:** 01-home-closed.

Needs change: inconsistent palette and visual hierarchy; real imported logo is a useful foundation.

![Guest homepage, closed sculpture — current starting point](evidence/2026-10-01-v2/01-home-closed.png)

### Evidence step 02 — Guest homepage, opened sculpture and scrolled story

**Route/context:** `/` · **Record:** 02-home-open.

Needs change: pale story text loses contrast over bright surfaces; opening itself worked.

![Guest homepage, opened sculpture and scrolled story — current starting point](evidence/2026-10-01-v2/02-home-open.png)

### Evidence step 03 — About introduction

**Route/context:** `/about` · **Record:** 03-about.

Needs change: large first-screen heading and illustration delay the explanatory journey.

![About introduction — current starting point](evidence/2026-10-01-v2/03-about.png)

### Evidence step 04 — Pricing introduction and plan controls

**Route/context:** `/pricing` · **Record:** 04-pricing.

Needs review: keep billing choice, eligibility and checkout continuity clear; no payment attempted.

![Pricing introduction and plan controls — current starting point](evidence/2026-10-01-v2/04-pricing.png)

### Evidence step 05 — FAQ first screen

**Route/context:** `/faq` · **Record:** 05-faq.

Needs change: oversized hero delays search and useful answers.

![FAQ first screen — current starting point](evidence/2026-10-01-v2/05-faq.png)

### Evidence step 06 — Trainer discovery first screen

**Route/context:** `/explore` · **Record:** 06-explore.

Needs change: filters/listing content need earlier prominence; no request submitted.

![Trainer discovery first screen — current starting point](evidence/2026-10-01-v2/06-explore.png)

### Evidence step 07 — Role selection

**Route/context:** `/signup` · **Record:** 07-signup-choice.

Useful foundation: explicit roles; 2.0 visual treatment and next-step clarity remain.

![Role selection — current starting point](evidence/2026-10-01-v2/07-signup-choice.png)

### Evidence step 08 — Client signup form

**Route/context:** `/signup/client` · **Record:** 08-signup-client.

Useful foundation: staged information; invalid-submit and completion states not exercised.

![Client signup form — current starting point](evidence/2026-10-01-v2/08-signup-client.png)

### Evidence step 09 — Trainer signup form

**Route/context:** `/signup/trainer` · **Record:** 09-signup-trainer.

Useful foundation: role-specific form; verification and approval continuation need full testing.

![Trainer signup form — current starting point](evidence/2026-10-01-v2/09-signup-trainer.png)

### Evidence step 10 — Gym application entry

**Route/context:** `/signup/gym` · **Record:** 10-signup-gym.

Needs review: clarify application review versus immediate account creation; no application sent.

![Gym application entry — current starting point](evidence/2026-10-01-v2/10-signup-gym.png)

### Evidence step 11 — Password recovery

**Route/context:** `/forgot-password` · **Record:** 11-forgot-password.

Needs review: calm task layout, generic account response and recovery continuation; no mail sent.

![Password recovery — current starting point](evidence/2026-10-01-v2/11-forgot-password.png)

### Evidence step 12 — Terms page

**Route/context:** `/policies/terms` · **Record:** 12-terms.

Release blocker: Awaiting review content.

![Terms page — current starting point](evidence/2026-10-01-v2/12-terms.png)

### Evidence step 13 — Support entry

**Route/context:** `/support` · **Record:** 13-support.

Needs change: move the form/help task ahead of the large hero; no support message sent.

![Support entry — current starting point](evidence/2026-10-01-v2/13-support.png)

### Evidence step 14 — Client demo login entry

**Route/context:** `/login` · **Record:** 14-login.

Useful foundation: client synthetic session established; code/role/recovery paths need complete testing.

![Client demo login entry — current starting point](evidence/2026-10-01-v2/14-login.png)

### Evidence step 15 — First-use tour intercepting dashboard

**Route/context:** `/dashboard` · **Record:** 15a-client-tour.

Needs change: tour redirected calendar navigation until Skip; preserve optional learning without trapping navigation.

![First-use tour intercepting dashboard — current starting point](evidence/2026-10-01-v2/15a-client-tour.png)

### Evidence step 16 — Client dashboard after skipping tour

**Route/context:** `/dashboard` · **Record:** 15-client-dashboard.

Needs change: header overlap and competing panels obscure the primary daily task.

![Client dashboard after skipping tour — current starting point](evidence/2026-10-01-v2/15-client-dashboard.png)

### Evidence step 17 — Month calendar after tour skip

**Route/context:** `/calendar` · **Record:** 16-calendar-month.

Needs review: connect schedule application, planned items and workout execution; no schedule changes made.

![Month calendar after tour skip — current starting point](evidence/2026-10-01-v2/16-calendar-month.png)

### Evidence step 18 — Week calendar desktop

**Route/context:** `/calendar` · **Record:** 17-calendar-week.

Needs change: toolbar/filter hierarchy and visual density need consolidation.

![Week calendar desktop — current starting point](evidence/2026-10-01-v2/17-calendar-week.png)

### Evidence step 19 — Client profile, opening view

**Route/context:** `/profile` · **Record:** 18-client-profile.

Needs change: biography text washes out; private settings subflows still require individual review.

![Client profile, opening view — current starting point](evidence/2026-10-01-v2/18-client-profile.png)

### Evidence step 20 — Goals overview

**Route/context:** `/goals` · **Record:** 19-goals.

Needs change: useful progress needs stronger hierarchy and clearer next check-in; no goal created.

![Goals overview — current starting point](evidence/2026-10-01-v2/19-goals.png)

### Evidence step 21 — Workout management overview

**Route/context:** `/workout-management` · **Record:** 20-workout-management.

Needs change: clarify programme/workout/schedule concepts and the next useful action.

![Workout management overview — current starting point](evidence/2026-10-01-v2/20-workout-management.png)

### Evidence step 22 — Human coaching inbox

**Route/context:** `/inbox` · **Record:** 21-inbox.

Needs review: readable list/composer, mobile navigation, drafts and failure states; no message sent.

![Human coaching inbox — current starting point](evidence/2026-10-01-v2/21-inbox.png)

### Evidence step 23 — Day timeline, scrolled state

**Route/context:** `/calendar/day/{dateStr}` · **Record:** 22-calendar-day.

Needs review: planned items, time positioning and execution controls; screenshot shows the selected day state, not every drawer.

![Day timeline, scrolled state — current starting point](evidence/2026-10-01-v2/22-calendar-day.png)

**Additional blocked step — Preferences:** attempted `/select-preferences` after the day view. No accepted product screenshot: browser returned `ERR_INCOMPLETE_CHUNKED_ENCODING`; current server log identifies the Thymeleaf form-error context failure and subsequent error-dispatch exceptions. This step remains B and is W-specific release work.

### Evidence step 24 — Client trainer relationship

**Route/context:** `/client/trainers` · **Record:** 24-client-trainers.

Needs change: pale restriction/status copy is hard to read; make lifecycle and unavailable actions explicit.

![Client trainer relationship — current starting point](evidence/2026-10-01-v2/24-client-trainers.png)

### Evidence step 25 — Charlie assistant workspace

**Route/context:** `/chat` · **Record:** 25-charlie.

Needs review: distinguish assistant from human coaching; provider/send/failure flows not exercised.

![Charlie assistant workspace — current starting point](evidence/2026-10-01-v2/25-charlie.png)

### Evidence step 26 — Client dashboard at 390 × 844

**Route/context:** `/dashboard` · **Record:** 26-client-dashboard-mobile.

Needs change: logo/account/menu collision and competing fixed controls; overflow check alone did not identify overlap.

![Client dashboard at 390 × 844 — current starting point](evidence/2026-10-01-v2/26-client-dashboard-mobile.png)

### Evidence step 27 — Week calendar at 390 × 844

**Route/context:** `/calendar` · **Record:** 27-calendar-week-mobile.

Needs change: tool rows fill nearly the first screen before the actual week.

![Week calendar at 390 × 844 — current starting point](evidence/2026-10-01-v2/27-calendar-week-mobile.png)

### Evidence step 28 — Guest homepage at 390 × 844

**Route/context:** `/` · **Record:** 28-home-mobile.

Needs change: compact the composition so core discovery and 3D entry have deliberate mobile order.

![Guest homepage at 390 × 844 — current starting point](evidence/2026-10-01-v2/28-home-mobile.png)

### Evidence step 29 — Guest login at 390 × 844

**Route/context:** `/login` · **Record:** 29-login-mobile.

Needs review: compact identity/role/code interaction, keyboard/insets and error states.

![Guest login at 390 × 844 — current starting point](evidence/2026-10-01-v2/29-login-mobile.png)

### Evidence step 30 — Guest homepage at 768 × 1024

**Route/context:** `/` · **Record:** 30-home-tablet.

Needs review: intermediate layout and sculpture/story framing need their own visual target.

![Guest homepage at 768 × 1024 — current starting point](evidence/2026-10-01-v2/30-home-tablet.png)

### Partial implementation evidence — W150–W151

- [x] Native queue filters, focused decision form, retained review notes, approval qualification-check acknowledgement, closed-state controls and optional read-only preview implemented.
- [x] Locked review transitions and consistent trainer verification flag proven by competing H2 approval/rejection; identical final decision replay does not repeat mocked mail. Missing request returns 404.
- [x] Targeted verification/related rendering gate: **117 tests / 6 suites passed**, 50 seconds (`build/verification-review-tests.log`); source, stylesheet and localisation checks recorded in the implementation log.
- [ ] Full page acceptance, secure qualification documents/history, request creation concurrency and real qualification checks remain open. Whole-page completion boxes remain unchecked.

### Shared-surface implementation follow-up

- [x] W154: compact authenticated support/legal links; guest-only conversion and dynamic copyright year.
- [x] W155–W156: account-name contrast protected over custom covers; no-script language form preserves bounded common filters without copying private/POST inputs.
- [x] W163: native registered-route search and role availability; restricted destinations stay restricted. Development signup endpoint is disabled in production, bounded and case-normalised with duplicate handling.
- [x] W166–W169: honest uncertain-action recovery text, correct HTTP status and safe failure metadata; query strings/private errors are not displayed; history controls progressively enabled.
- [x] Shared gate **114 tests / 5 suites**, 36 seconds, and development gate **107 tests / 4 suites**, 42 seconds, passed; see implementation log for separate scope and commands.
- [x] W150–W151 follow-up: first-request concurrency, NEEDS_INFO duplication, trainer/gym eligibility and note bounds addressed; **25 tests / 4 suites passed**, 39 seconds.
- [ ] Full shared-surface role/theme/device/no-script browser acceptance, launch inbox confirmation and secure qualification documents/history remain open. The old login-demo template is currently unused by the live controller.


### Native and enhancement follow-up — 2 October 2026

- [x] A001/A005: separate initial restoration from form pending state; retain sign-in screen and errors during auth. Actual synthetic API-backed client sign-in and force-stop/session restoration observed on API 35.
- [x] A003/A009/A010: clearer welcome, theme-aware system icons and semantic cards; source adds a shared client account destination and removes duplicate home identity/logout.
- [x] A013: stable task/schedule JSON names and meaningful malformed/unavailable/foreign-item errors. Real isolated database regression proves readable records and owned completion.
- [x] A014: source adds previous/current/next month, empty-date discovery and remembered month/date; day Back returns to calendar.
- [x] A015/A016/A020/A021: restored non-secret drafts, read retry/error clearing, honest automated-helper copy, shared account/support/legal actions and guarded sign-out. Template replies are separate from human coaching and a connected generative provider.
- [x] Android debug build and **4 tests / 2 suites passed**, 12 seconds; web API/recovery **16 tests / 2 suites passed**, 13 seconds. Server-bound sessions, backup exclusions and endpoint/redirect validation implemented; actual backup transfer remains unverified.
- [x] W163: actual desktop Dev Hub workspace is visible and readable after compiled reveal/style fixes (`evidence/v2-dev-hub-v2ag-desktop.png`). W148/W155 signed-in identity/inventory contrast observed (`evidence/v2-admin-merch-v2ag-desktop.png`).
- [x] W170: progressive unlocked document, non-hold unlock, interrupted-hold cancellation, observer fallback and quiet timer implemented; browser observed non-hold activation. Related **22 tests / 3 suites passed**, 13 seconds.
- [ ] Complete native role/device/theme/font-scale/keyboard/TalkBack/offline/expired-session acceptance and the remaining native legacy reachability decisions. Latest calendar/account changes require their own fresh emulator evidence; the initial native screenshot is not proof of those changes.
- [ ] Complete all page-level acceptance and the remaining 3D, no-script, motion, workflow and product-model work. These partial results do not close the whole Version 2.0 goal.


### Native runtime follow-up and remaining alignment — 2 October 2026

- [x] Actual client invalid-login/retry, readable day records, synthetic completion and matching calendar totals; previous-month/empty-date/Back preserves the selected month.
- [x] Actual client dark theme at 130% text size, account/legal/sign-out access and return to public welcome; original emulator theme/font settings restored.
- [x] Actual trainer login/scoped client list and gym login/empty scoped trainer list/own application state. These checks use documented synthetic fixtures only.
- [x] A011/A012: source uses real operational counts separately from personal day-plan counts. A017: searchable relationship rows and native client detail/logs with a real website workspace destination. A019: own-application status wording and no gym review authority.
- [x] A001/A022–A032 alignment: current shell stops automatic sample seeding and cancels unrelated legacy sample reminders while preserving the local database/screens. Source caller search identifies dormant older management/WebView entry points; migration/live reminder reconciliation remains open.
- [x] Two-query month aggregation and actual H2 readable/owned client-detail regression: final mobile API **10 tests / 1 suite passed**, 12 seconds. Final Android build and **4 tests / 2 suites passed**, 8 seconds.
- [ ] Fresh final client-detail/search/role-count runtime proof, all-role responsive/rotation/keyboard/TalkBack/offline checks and all remaining page completion gates. Open whole-page boxes remain open.


### Prepared local checkpoint — 2 October 2026

- [x] Configured whole web regression: **837 tests / 177 suites passed**, no failures/errors/skipped, 1 minute 54 seconds. Two pre-existing excluded classes remain outside this result.
- [x] Latest Android build and **4 tests / 2 suites passed**, 9 seconds; installed local API 35 preview. Actual final trainer count, owned detail/empty history, Back, no-match/Clear, latest month totals and one landscape recreation observed; original rotation restored.
- [x] Web `2.0.0-SNAPSHOT` executable package built in 4 seconds with current assets/stable archive name; native `2.0-preview`. Docker source selects the executable archive explicitly; Docker/deployment remain untested.
- [ ] Finish all remaining role/theme/device/no-script/motion, workflow/data-model, 3D, physical-device and production acceptance. [The prepared checkpoint](../qa/2026-10-02-v2-prepared-checkpoint.md) records the current baseline; whole-product completion remains open.

### Affiliation checkpoint — 2 October 2026

- [x] W174–W175: multiple gym records, recipient consent, concurrent first requests, legacy precedence, private event history, scoped web/native reads and gym evidence access revoked on ending.
- [x] Web regression: **846 tests / 178 suites**, no failures/errors/skipped, **2m 5s**. Latest Android build and **four tests / two suites**, **42s**. Final web package **9s**; current preview `20261002v2d`.
- [x] Actual final synthetic request, trainer keyboard acceptance/ending with scripts disabled, ordered history and selected light/dark desktop/tablet/phone layouts. Original synthetic theme and preview controls restored.
- [x] 30 named affiliation translations across 14 UI locales; extraction and all 42-bundle parity checks pass. Manual H2 schema omission and undefined CSS tokens caught and fixed.
- [ ] Full W174/W175 completion, production migration/backfill, existing-trainer professional-review entry point, secure qualification evidence/history and latest native website-link runtime.
- [ ] Shared no-script shell: hide/replace inert enhancement controls and provide working narrow-screen navigation. New forms/history work without scripts; the shared platform panel remains empty. This needs separate shared-shell acceptance.

### Shared shell and professional-review checkpoint — 2 October 2026

- [x] Native role-aware navigation, protected sign-out and inactive enhancement cleanup; client/trainer/gym native complete-code sign-in works with scripts disabled. Enhanced gym/trainer sign-in still works. Guest/client/trainer/gym phone proof and six-state rendered role checks recorded.
- [x] W176 direct applications, previous requests, owned needs-info responses and locked-screen entry links. Verification remains a separate authorised reviewer decision. Approval/response lock-order defect fixed; concurrent approval/response regression passes.
- [x] Final focused gate: **137 tests / six suites**, no failures/errors/skipped, **41 seconds**, including executable packaging. All 42 localisation bundles pass. Current asset version `20261002v2h`.
- [x] Selected verified/empty review light desktop/phone and dark tablet/phone observations; dark views use script-disabled rendering. Temporary preview controls reset and synthetic theme returned to Light.
- [ ] Unverified/populated browser forms, secure qualification documents, immutable review-event history, delivery/reconciliation, remaining page-specific no-script and complete role/theme/device acceptance. These focused checks do not replace the preceding 846-test whole regression or complete Version 2.0.

### Private verification evidence and event history — 2 October checkpoint

- [x] W176/W151 private PDF/PNG/JPEG attachments, bounded atomic batches, authenticated download, open-review removal/replacement and metadata-only listings implemented. Other trainers, stale roles, disabled accounts and gym administrators cannot download these qualification files.
- [x] Submitted/replied/reviewed/file events preserve the actor where known, time, status and original notes. Duplicate decisions add no duplicate events; older requests get explicitly labelled surviving-state snapshots.
- [x] Upload quota and approval/upload races use the existing trainer-then-request lock order. Focused gate: **147 tests / eight suites**, zero failures/errors/skipped, **50 seconds**, with executable package (`build/v2-evidence-history-final-tests.log`).
- [x] Actual independent application, file attachment, script-disabled removal, native keyboard replacement upload, reviewer needs-info feedback and owned native reply. Original and revised notes retained in the tablet timeline; light phone/tablet and dark phone/tablet observations have no measured horizontal overflow.
- [x] Shared component `universal-fragments/verification/evidence.html` renders private document links and a native expandable timeline for trainer and reviewer pages. It introduces no route. Its styling is shared through the training/admin bundles, with named copy in all 14 UI locales.
- [x] Reviewer search now identifies trainers and the queue describes independent/gym-affiliated applications. Reviewer metadata uses British date order and Europe/London timestamps; evidence stays beside decision controls within the existing detail layout.
- [ ] PostgreSQL V9/staging/backfill acceptance, closed-review retention/withdrawal, scanning, actual qualification checks, delivery reconciliation, gym-assisted browser states and full Q01–Q10 acceptance remain open. This milestone does not complete any whole-page or Version 2.0 release box.

Behaviour and limits: [professional review evidence](../verification-evidence.md). Current source inventory: **179 HTML templates**, **176 unique W entries**, three added shared components. The earlier 846-test whole-app checkpoint and 137-test review checkpoint remain separate dated evidence; counts from focused runs are not cumulative.

- [x] Final configured whole-web regression/package after evidence and queue/detail refinements: **866 tests / 179 suites**, zero failures/errors/skipped, **1 minute 50 seconds**; `build/v2-whole-web-evidence-20261002.log`. Existing excluded classes remain excluded; native/device/provider/production acceptance is separate.

- [x] W151 stylesheet defect found in the final browser check and repaired: the existing detail component was confined to the calendar bundle. Admin now includes that shared component. Final bundle/package gate passes (eight seconds), with eight performance checks (five seconds). Actual desktop columns and script-disabled phone stack are verified without overflow; `v2-professional-review-reviewer-{desktop,mobile-noscript}-final-20261002.png`. This cosmetic dependency/cache refinement follows the 866-test backend/template gate and does not change its recorded scope.

### Native professional-review entry follow-up — 2 October 2026

- [x] Trainer Account exposes professional review, private documents/history and reviewer responses through the existing separate website session. Only the exact validated `/trainer/verification` path is added; no native bearer token is shared in the browser URL.
- [x] Updated debug APK build and four tests / two suites pass in 11 seconds. Installed API 35 observation confirms trainer Account, exact review URI, website-login redirect and retained native Account state. Sign out remains reachable.
- [ ] Complete the remaining native role/device/accessibility/offline and website-review acceptance. Chrome first-run notification onboarding limits further emulator-browser proof; no notification permission was granted.

### Dashboard native baseline and calendar continuity — 2 October 2026

- [x] W033/W034 primary shortcuts, date navigation and action/goal sections work before enhancement; goal scope IDs are unique. The enhanced drawers and tabs retain keyboard activation, inactive-panel isolation and focus return.
- [x] Actual 390px script-disabled action fragment navigation and calendar date navigation. Blocking only `client-dashboard-page.js` leaves native shortcuts/date links intact; Help reaches support. Selected dark phone/tablet, light phone and enhanced light desktop views have no horizontal overflow.
- [x] Reduced-motion day selection by keyboard updates its preview/destination. The enhanced current-day summary matches one task and one workout in the synthetic fixture; false default preview and countdown values stay hidden.
- [x] Calendar enum-comparison render failure repaired in the day and shared streak templates. Existing streak render/navigation/accessibility tests and real post-reload day navigation pass.
- [x] Focused dashboard/render/asset gate: 109 tests / three suites, zero failures/errors/skipped, 53 seconds. Final calendar/consistency/native-render/package gate: 11 tests / five suites, zero failures/errors/skipped, 41 seconds. These are separate, overlapping runs. CSS compilation, all 42 localisation bundle checks, JavaScript syntax and whitespace checks pass; current packaged cache is `20261002v2k`.
- [ ] Complete all W033/W034 lifecycle, theme/device/locale and accessibility acceptance. W057 still needs native section controls, empty-day completion copy and its escaped timeline fallback repaired; successful date navigation is not whole-day usability acceptance.

Evidence: `v2-dashboard-native-phone-20261002.png`, `v2-dashboard-native-actions-phone-20261002.png`, `v2-dashboard-script-failed-phone-20261002.png`, `v2-dashboard-native-dark-tablet-20261002.png`, `v2-dashboard-native-dark-actions-phone-20261002.png`, `v2-dashboard-enhanced-desktop-20261002.png`. Original synthetic Light theme, scripting, reduced-motion emulation and viewport restored. The Version 2.0 goal remains active.
