# One To One — prepared baseline before Version 2.0

**Owner:** James · **Date:** 1 October 2026 · **Environment:** local web application, H2 demo data and Android debug build.

This records the stabilisation work following the [page-by-page 2.0 audit](../audits/one-to-one-v2-design-workflow-usability-audit-2026-10-01.md). The aim is a dependable starting point for the redesign. Existing uncommitted work was preserved. The complete futuristic 2.0 design, additional features and new 3D production remain in the main audit checklist.

## Completed fixes

### Key issues

- [x] **Preferences rendering:** moved the validation summary inside the bound form. The full editor and invalid submissions now render with their form and errors intact.
- [x] **Partial error responses:** the HTML response filter only copies a successfully rendered body. A template failure no longer commits partial HTML before the container can handle the error.
- [x] **Standards mode:** removed the shop template's UTF-8 byte-order mark and made document detection tolerate leading marks. Normal HTML form POST responses receive the same doctype protection as GET pages; API and AJAX responses retain their existing behaviour.
- [x] **Login destination:** first login no longer replaces a requested destination with a compulsory tutorial. Existing role routing, saved requests and valid next destinations are retained.
- [x] **Tour navigation:** the tour resumes only through explicit start/continue navigation. Ordinary navigation cancels stale tour state instead of sending the user back. Replay remains available, with a fallback when session storage is unavailable.
- [x] **Gym application privacy:** a native gym account can read its own applications rather than every gym's contact/application records. Its operational trainer list excludes other gyms and unassociated trainers.
- [x] **Gym approval integrity:** gym accounts cannot approve applications. Authorised platform staff use the existing complete application service, including its account creation workflow, rather than a direct status update.
- [x] **Gym signup integrity:** the mobile API rejects direct creation of gym administrator accounts. The native gym signup screen opens the existing website application process, which requires review.
- [x] **Disabled accounts:** existing mobile tokens no longer authenticate disabled users.
- [x] **Mobile API input errors:** malformed calendar dates/months return a client error; training duration outside 1–1440 minutes cannot write a log.

### Smaller defects and usability repairs

- [x] **Header layout:** corrected the desktop grid and switched to the menu before navigation collides with the account controls. CSS and JavaScript use the same breakpoint. The narrow layout uses compact branding and hides the duplicate profile name while retaining account access.
- [x] **Asset caching:** shared navigation JavaScript uses the same asset version as the layout. The final CSS/cache identifier is `20261001baseline5`.
- [x] **Mobile dashboard:** moved the local dashboard shortcut row into the page, removing a second persistent row from above the main navigation. Removed its redundant bottom reservation. Coaching metrics stack on phones instead of breaking words across three tiny columns.
- [x] **Calendar:** month/week jump tools use a native collapsed disclosure, leaving more room for the calendar. Existing fields and navigation remain available when expanded.
- [x] **Contrast:** gave the open 3D inspector an opaque dark surface, strengthened light-theme trainer/status labels and mixed custom profile biography colours with readable theme text.
- [x] **Broken characters:** repaired incorrectly decoded punctuation in assistant context, navigation parsing, replies and shop copy, plus related comments.
- [x] **Native write failures:** task completion, training saves and Charlie messages catch failures, retain useful data/drafts and prevent repeated taps while a write is pending. Fields are disabled during submission so a completed request cannot clear newly edited text.
- [x] **Native duration:** training records use the duration entered by the user rather than always submitting 45 minutes.
- [x] **Native identity and tabs:** Charlie is labelled as an AI assistant; the gym Requests screen calls its application endpoint rather than displaying the trainer list again.
- [x] **Native network recovery:** network/server failures preserve a cached session; rejected credentials clear it. Connections are disconnected in a finally block, UTF-8 is explicit and non-JSON server errors produce a useful retry message. Coroutine cancellation is preserved in the changed authentication/write paths.

## Files changed by this stabilisation pass

Paths below are relative to this repository. Generated outputs were rebuilt from source. Some files already contained earlier work; those changes were retained.

| Area | Files |
| --- | --- |
| HTML response handling | `Web_App/src/main/java/uk/ac/cf/_5/group14/One_To_One/Web/HtmlDoctypeResponseFilter.java` |
| Login and caching | `Web_App/src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/CustomAuthenticationSuccessHandler.java`; `Config/UiStyleBundleAdvice.java` under the same Java package |
| Mobile server | `Web_App/src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileApiController.java`; `MobileAuthService.java` |
| Shared web layout | `Web_App/src/main/resources/templates/base.html`; `universal-fragments/layout/navbar.html`; `universal-fragments/dev/dev-mode.html` |
| Web page templates | `Web_App/src/main/resources/templates/client-views/conditions-preference/select-preferences.html`; `client-views/client/trainers.html`; `shared-views/calendar/month.html`; `shared-views/calendar/week.html`; `shared-views/merch/shop.html` |
| Web styles | `Web_App/src/main/resources/static/css/components/core/navbar.css`; `core/dev-mode.css`; `core/shell-layout.css`; `dashboard/client-dashboard-refresh.css`; `account/profile.css`; `calendar/calendar-redesign.css`; `misc/home-cosmos.css` |
| Web behaviour | `Web_App/src/main/resources/static/js/core/navbar-page.js`; `public/site-tour.js` |
| Punctuation repairs | `Chat/ChatContextBuilder.java`, `ChatContextService.java`, `ChatNavParser.java`, `ChatRuleBasedResponder.java`; `Merch/MerchController.java`; `MerchOrders/MerchOrderRepository.java`; `PaymentCards/SavedPaymentMethod.java`, `SavedPaymentMethodService.java`; comments in `Security/SecurityConfig.java`, under the main Java package |
| Android | `Phone-App/application/app/src/main/java/uk/ac/cardiff/trainerhub/ui/TrainerHubApp.kt`; `data/remote/OneToOneMobileRepository.kt`; `data/remote/OneToOneApiClient.kt` |
| Regression coverage | `Web_App/src/test/java/uk/ac/cf/_5/group14/One_To_One/Web/PreparedBaselinePageRenderingTest.java`; `Web/HtmlDoctypeResponseFilterTest.java`; `MobileApi/MobileApiSafetyTest.java`; `SecurityTests/LoginIntegrationTest.java`; `SecurityTests/ShellPerformanceContractTest.java` |
| Generated CSS | `Web_App/src/main/resources/static/css/app.css`; changed route bundles in `static/css/bundles/` |
| Documentation/evidence | This report, the main audit status note, `Web_App/docs/README.md` and the browser captures linked below |

## Verification

Testing used the existing suite plus focused regression coverage for the actual failures. There was no new test framework or dependency installation.

| Check | Command / evidence | Result |
| --- | --- | --- |
| Web test suite | `Web_App`: `.\gradlew.bat test --console=plain` | Final complete run: 693 tests passed, zero failures/errors/skips in 1 minute 33 seconds. The final small header-only follow-ups were subsequently built and checked in the browser. |
| Responsive source contracts | `Web_App`: `.\gradlew.bat test --tests '*DashboardConsistencyContractTest' --tests '*ShellPerformanceContractTest' --console=plain` | 15 tests passed after the responsive fixes; this focused run replaces the current Gradle HTML/XML report with those 15 cases. |
| Final Java/resources | `Web_App`: `.\gradlew.bat classes --console=plain` | Passed after the final asset-version and header changes. |
| Real template rendering | `PreparedBaselinePageRenderingTest` | 61 public/client/trainer/gym/admin/super-admin route cases render or redirect without server errors, plus full preferences and invalid-submit cases. Redirects are allowed; this does not prove every destination or control. |
| API regressions | `MobileApiSafetyTest` | Seven tests cover cross-gym isolation, review permissions/workflow, signup bypass, invalid duration and malformed month. Isolation tests execute the actual queries against small H2 fixtures. |
| Frontend output | `Web_App`: `npm run build:frontend`; final `npm run build:css` after responsive changes | CSS bundles and 3D browser bundle built successfully. |
| 3D timing regressions | `Web_App`: `npm run test:3d` | All six tests passed. |
| Changed JavaScript syntax | `node --check src/main/resources/static/js/core/navbar-page.js`; equivalent check for `public/site-tour.js` | Passed. |
| Android packaging | `Phone-App/application`: `.\gradlew.bat :app:assembleDebug --offline --console=plain` | Passed after the final native field changes; debug APK produced. No device/emulator runtime claim. |
| Diff whitespace | Repository root: `git diff --check` | Passed; Git reports line-ending conversion notices on Windows. |

The first broad web run exposed the shop byte-order mark; this was repaired. The first compile of a new filter regression needed an explicit checked-exception declaration; that was corrected. A focused responsive rerun exposed a source contract still requiring the removed fixed dashboard dock; the contract now checks the remaining flyout reservation. Final results, rather than these intermediate failures, determine the prepared state.

Browserslist reports that its cached browser data is six months old. Builds pass; dependency updates were kept outside this repair pass. The host Node/npm versions differ from the package's declared toolchain, so CI should continue using the declared versions.

### Browser verification and captures

The in-app browser uses `http://localhost:8081`, synthetic local accounts and the final built assets. The preview was started afresh for the final pass because compiling Java during a development hot reload temporarily left controller layout advice missing. That transient state was not accepted as runtime proof.

The browser record below is completed as each affected journey is checked. Captures are in [prepared-baseline evidence](../audits/evidence/2026-10-01-prepared-baseline/).

- [x] Homepage sculpture opens to its connection view, the inspector has a readable opaque surface and Escape restores the opening control.
- [x] Client login preserves the saved preference destination without forcing the tour; a fresh local session also reaches the dashboard normally.
- [x] Full preferences render in the browser. Submitting zero default sets shows the validation summary and keeps the form in standards mode (`CSS1Compat`). Restoring the original value of four saves successfully and shows confirmation; no intentional preference change remains in the local demo account.
- [x] Header geometry and captures checked at 320, 390, 640, 768, 1024, 1280 and 1440 pixels: no brand/account/navigation overlap or document overflow in the checked client state. The 640-pixel brand label now yields space to the controls. All-role/translated visual acceptance still belongs to the 2.0 checklist.
- [x] Phone dashboard Goals panel opens from the relocated shortcut row, closes with Escape and retains access to its content. Mobile navigation also closes with Escape and returns focus to its opening button.
- [x] Optional tour replay opens; ordinary calendar navigation leaves the user on the calendar without replaying the stale tour.
- [x] Week jump disclosure opens with a click and closes with Enter; its date/task controls remain available. Month disclosure and tablet layout also checked.
- [x] The light-theme profile biography is readable in the actual page; its visible sidebar colour is `color(srgb 0.354902 0.436274 0.47451)`, rather than the pale custom colour used by the hidden dark profile menu. This is component evidence, not a full contrast certification.
- [x] Final complete-suite repeat after the dashboard responsive adjustments, plus focused dashboard/shell checks for the subsequent header fixes.

Selected captures: [phone dashboard](../audits/evidence/2026-10-01-prepared-baseline/dashboard-320.png), [Goals panel](../audits/evidence/2026-10-01-prepared-baseline/dashboard-320-goals-panel.png), [phone week](../audits/evidence/2026-10-01-prepared-baseline/calendar-week-390.png), [tablet month](../audits/evidence/2026-10-01-prepared-baseline/calendar-month-768.png), [640-pixel header](../audits/evidence/2026-10-01-prepared-baseline/calendar-640.png), [desktop header](../audits/evidence/2026-10-01-prepared-baseline/calendar-1440.png), [profile](../audits/evidence/2026-10-01-prepared-baseline/profile-1280.png), [invalid preferences](../audits/evidence/2026-10-01-prepared-baseline/preferences-invalid-1280.png), [saved preferences](../audits/evidence/2026-10-01-prepared-baseline/preferences-saved-1280.png) and [3D inspector](../audits/evidence/2026-10-01-prepared-baseline/home-1280-inspector.png). Header measurements are recorded in [navigation-breakpoints.json](../audits/evidence/2026-10-01-prepared-baseline/navigation-breakpoints.json).

The final preview produced no server error/template-exception log entries during these checked flows, and the browser's final error log was empty. The temporary viewport override was reset after verification.

## Remaining work before release

These remain explicit release/design tasks rather than being marked complete by a successful build:

- [ ] Reviewed terms/subscription terms must replace the current awaiting-review content.
- [ ] Complete native Android device/emulator testing, including API outages and the gym application browser handoff.
- [ ] Run the production-provider checks for payments, mail, storage and deployment using authorised test environments and credentials.
- [ ] Complete the full 2.0 design system and page-by-page rebuild, including role-specific responsive visual review, every important state and translated/long content.
- [ ] Resolve the existing primary-gym-only mobile representation against the product rule allowing trainers to belong to multiple gyms; preserve privacy when implementing the full affiliation model.
- [ ] Consolidate the remaining duplicate/legacy surfaces through the main audit, preserving records and route compatibility.

**Prepared point:** working local baseline and built Android package for the next 2.0 implementation slice. The first design slice should establish the shared shell and homepage opening experience, then carry it into the client dashboard/calendar before expanding across the remaining pages. Build/test success is not a guarantee that every undiscovered bug or production integration has been exercised.
