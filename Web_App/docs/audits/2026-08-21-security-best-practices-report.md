# One To One security best-practices audit

Audit date: 21 August 2026  
Scope: Web_App source, configuration, dependencies, local runtime behaviour, authentication journeys, mobile API, uploads, payments, and representative privileged routes  
Runtime audited: Spring Boot 3.5.7, Java 21, local profile, development mode, H2, port 8081  
Assessment type: source-assisted security review and bounded local verification; this is not an external penetration test

## Executive summary

The application has a solid security foundation in several important areas: Spring Security is active, normal web requests are CSRF-protected, passwords use BCrypt, SQL in the mobile controller is parameterised, Stripe webhooks verify signatures, most object ownership checks are explicit, private uploads are served through authenticated owner checks, and production configuration uses Flyway plus schema validation.

The release should nevertheless be treated as a security **no-go** until the mobile gym account path is corrected. The public mobile endpoint creates a fully privileged GYM_ADMIN account immediately, bypassing the reviewed web application process. That account can then list and approve all gym applications through the mobile API. This is a direct vertical privilege-escalation and administrative-function exposure.

Three further areas should be remediated before production use:

1. Add shared abuse controls to mobile login, password recovery, and the public AI endpoint.
2. Revoke active web and mobile sessions when a password is reset, and store only hashes of password-reset tokens.
3. Add a production fail-closed guard for development mode and deploy a measured Content Security Policy.

Finding count:

- High: 4
- Medium: 6
- Low: 3
- Informational/positive controls: 10

## High-severity findings

### SEC-01 — Public mobile gym sign-up bypasses the approval process

- Rule ID: AUTHZ-ROLE-001
- Severity: High
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileApiController.java:63-65
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileAuthService.java:70-98
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileApiController.java:376-414
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/SecurityConfig.java:175-190
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/GymApplications/GymApplicationController.java:118-133,167-169
- Evidence:
  - POST /api/mobile/auth/signup/gym is public.
  - The service assigns Role.GYM_ADMIN directly and immediately returns a 30-day bearer session.
  - Any GYM_ADMIN bearer token may list all gym applications and approve any application ID.
  - The equivalent web approval action is restricted to PLATFORM_ADMIN or SUPER_ADMIN and creates the approved account only after review.
- Impact: An unauthenticated attacker can self-provision an administrative gym role, read applicant information, and change the status of arbitrary gym applications. The mobile path contradicts the server’s reviewed onboarding model and exposes an administrative function to an unreviewed account.
- Fix:
  1. Remove public GYM_ADMIN account creation from MobileAuthService.
  2. Make mobile gym registration call the same GymApplicationService used by /signup/gym.
  3. Provision the account only from the existing platform-admin approval service.
  4. Restrict application listing and approval to PLATFORM_ADMIN or SUPER_ADMIN in one shared service/method-security policy.
  5. Add negative integration tests for unauthenticated, CLIENT, TRAINER, GYM_ADMIN, PLATFORM_ADMIN, and SUPER_ADMIN callers.
- Mitigation: Disable the mobile gym sign-up and mobile gym application-management routes until the shared workflow is implemented.
- False-positive notes: None. The web controller and SecurityConfig explicitly demonstrate that gym application approval is intended to be a platform-admin operation.

OWASP recommends deny-by-default authorisation and validation of permissions for every request and every target object. See the [OWASP Authorization Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html).

### SEC-02 — Password reset does not revoke active web sessions or 30-day mobile bearer tokens

- Rule ID: AUTH-RECOVERY-002
- Severity: High
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Users/PasswordResetService.java:74-96
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileAuthService.java:24-25,101-123,135-150
- Evidence: resetPassword changes the password and marks the reset token used, but it does not invalidate Spring Session records or update mobile_auth_tokens. Mobile tokens remain valid for 30 days unless explicitly logged out.
- Impact: A user who resets their password because an account or device is compromised may remain exposed to an attacker holding an existing mobile bearer token or web session.
- Fix:
  1. Add a single account-credential-revocation service.
  2. On successful reset, revoke every mobile token for the user and invalidate all server-side sessions except an explicitly approved current recovery session.
  3. Record the revocation as a privileged audit event.
  4. Offer the user a clear “signed out everywhere” confirmation.
- Mitigation: Reduce mobile token lifetime and provide a user-facing active-device/session revocation screen.
- False-positive notes: No revocation call is present in PasswordResetService, and MobileAuthService validates only expiry and revoked_at.

### SEC-03 — Mobile login bypasses the existing login throttle

- Rule ID: AUTH-BRUTEFORCE-003
- Severity: High
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileApiController.java:37-45
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileAuthService.java:52-67
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/LoginThrottleFilter.java
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/SecurityConfig.java:141,177,245-246
- Evidence: LoginThrottleFilter protects the form-login route, while /api/mobile/auth/login is globally permitted and calls password verification directly. No per-IP, per-account, device, or global rate limiter is applied in MobileAuthService or MobileApiController.
- Impact: Attackers can perform credential stuffing and password guessing against the mobile endpoint without the protection users receive on the web login.
- Fix:
  1. Move failure accounting and throttling into a shared authentication-abuse service used by web, OAuth failure handling where relevant, and mobile.
  2. Rate-limit by normalised account identifier and network source, with bounded backoff and audit events.
  3. Keep responses generic and avoid permanent lockout that can be weaponised against a victim.
  4. Add integration tests that prove both web and mobile paths throttle consistently.
- Mitigation: Apply an edge rate limit to /api/mobile/auth/login while the shared service is built.
- False-positive notes: No generic API rate-limiting filter or dependency was found in the repository.

The [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html) identifies login throttling as a core defence against password guessing.

### SEC-04 — The unauthenticated AI endpoint has no endpoint-specific quota

- Rule ID: ABUSE-AI-004
- Severity: High
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/SecurityConfig.java:142,178
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Chat/ChatController.java:179-209
  - src/main/resources/application.properties:67-72
- Evidence:
  - POST /chat/ask is explicitly permitAll.
  - When the configured chat service is available, each request can call the AI provider.
  - app.ai.enabled defaults to true.
  - No quota, request-size budget, anonymous session allowance, CAPTCHA, or per-origin/per-IP limiter is visible on this route.
- Impact: Automated callers can consume provider spend and application resources, degrade service, and use the application as an unrestricted prompt relay. The exact financial impact depends on whether a production API key is enabled.
- Fix:
  1. Require an authenticated session for the full assistant, or create a tightly limited public demo mode.
  2. Add input-size limits, per-session and per-network quotas, concurrency limits, timeouts, and spend telemetry.
  3. Fail closed when the public quota is exhausted.
  4. Separate the deterministic public responder from provider-backed responses unless the visitor is authenticated or passes the intended conversion gate.
- Mitigation: Set APP_AI_ENABLED=false until endpoint abuse controls are deployed.
- False-positive notes: If production intentionally runs without an AI key, provider cost is absent, but the unauthenticated resource-abuse path remains.

## Medium-severity findings

### SEC-05 — Development mode is fail-open and not prohibited by the Render profile

- Rule ID: CONFIG-FAILSAFE-005
- Severity: Medium
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Security/SecurityConfig.java:132-171
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Config/DevModeProperties.java:7-17
  - src/main/resources/application.properties:30
  - src/main/resources/application-render.properties:1-30
- Evidence: When DEV_MODE=true, unmatched routes end in anyRequest().permitAll(). The Render profile does not override app.dev-mode to false and no startup guard rejects DEV_MODE in a production profile.
- Impact: A deployment-variable mistake can silently weaken authorisation for routes not explicitly protected in the development branch. New routes are especially exposed because the branch is allow-by-default.
- Fix:
  1. Change the development branch to deny-by-default/authenticated-by-default.
  2. Add an ApplicationRunner/startup assertion that refuses to start when the render or production profile and development mode are both active.
  3. Keep the development hub restricted to local/test profiles.
  4. Add a profile-level security contract test.
- Mitigation: Pin DEV_MODE=false in deployment configuration and alert on any rendered development-mode marker.
- False-positive notes: The audited local instance intentionally uses development mode; the finding concerns production misconfiguration resilience.

### SEC-06 — Password-recovery requests lack abuse controls and store raw reset tokens

- Rule ID: AUTH-RECOVERY-006
- Severity: Medium
- Location:
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Users/PasswordResetService.java:35-62
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Users/PasswordResetToken.java:9-36
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Users/PasswordResetController.java:32-46
- Evidence:
  - Unknown emails return before token creation and email work, so response time can differ from a known account.
  - No per-account or per-network request limit is applied.
  - The UUID reset token is stored directly in the database and queried by its raw value.
- Impact: Attackers can flood a user’s email, probe timing differences, and use a database leak to redeem unexpired reset tokens directly.
- Fix:
  1. Store a SHA-256 or HMAC hash of each high-entropy reset token, returning the raw token only through email.
  2. Add per-account and per-network recovery limits.
  3. Make the public response path uniform and queue delivery asynchronously.
  4. Retain the existing 45-minute expiry and single-use behaviour.
- Mitigation: Add edge limits to /forgot-password and monitor repeated recovery requests.
- False-positive notes: The visible success message is already generic, UUID v4 supplies adequate entropy for this use, and prior tokens are invalidated. The remaining weaknesses are storage, timing, and abuse control.

The [OWASP Forgot Password Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) recommends consistent responses and timing, secure single-use expiring tokens, secure storage, and protection from excessive automated requests.

### SEC-07 — No Content Security Policy protects numerous dynamic HTML sinks

- Rule ID: JS-CSP-001 / JS-XSS-001
- Severity: Medium
- Location:
  - Live response from GET / on 21 August 2026: no Content-Security-Policy header
  - src/main/resources/static/js/chat/coach-chat.js:201-220
  - src/main/java/uk/ac/cf/_5/group14/One_To_One/Chat/CoachConversationService.java:49-63
- Evidence: A conversation title is derived from the user’s first message and later inserted with innerHTML. The live application sends X-Content-Type-Options and X-Frame-Options but no CSP.
- Impact: A user-controlled string reaches an HTML parsing sink. In the confirmed conversation-title path, exploitation is mainly self-XSS because conversations are owner-scoped, but the pattern increases the impact of future source/sink mistakes across a frontend with many innerHTML uses.
- Fix:
  1. Replace the conversation title assignment with createElement plus textContent.
  2. Audit each remaining innerHTML use and reserve HTML parsing for trusted constants or sanitised rich content.
  3. Introduce Content-Security-Policy-Report-Only, resolve violations, then enforce a nonce/hash or external-script policy.
  4. Add Trusted Types after the existing sinks are reduced.
- Mitigation: Add a report-only policy immediately and monitor violations without breaking current pages.
- False-positive notes: Constant SVG/template markup is not itself a vulnerability. The cited title is demonstrably user-derived, although owner scoping reduces practical impact.

Spring Security does not invent a CSP automatically; the application must define it. Its documentation describes CSP as defence-in-depth for content injection and recommends validation/encoding as the first line of defence: [Spring Security response headers](https://docs.spring.io/spring-security/reference/features/exploits/headers.html).

### SEC-08 — Third-party scripts execute without Subresource Integrity

- Rule ID: JS-SUPPLY-001 / JS-SRI-001
- Severity: Medium
- Location:
  - src/main/resources/templates/shared-views/notes/index.html:5,123
  - src/main/resources/templates/trainer-views/schedule/workout.html:188
- Evidence: Quill 1.3.7 is loaded from jsDelivr and Sortable 1.15.0 from cdnjs without integrity or crossorigin attributes. No CSP restricts their execution.
- Impact: A compromised CDN response or dependency distribution path executes with the authenticated application origin’s privileges.
- Fix: Prefer self-hosting reviewed, pinned assets. Otherwise add verified SHA-384 integrity metadata, crossorigin, a narrow CSP allowlist, and an update process.
- Mitigation: Restrict script-src in a report-only CSP while assets are migrated.
- False-positive notes: Versions are pinned and HTTPS is used, which reduces accidental drift but does not provide content integrity.

### SEC-09 — Mobile bearer-token schema is created at application runtime

- Rule ID: DATA-MIGRATION-009
- Severity: Medium
- Location: src/main/java/uk/ac/cf/_5/group14/One_To_One/MobileApi/MobileAuthService.java:38-50
- Evidence: A PostConstruct method issues CREATE TABLE IF NOT EXISTS mobile_auth_tokens instead of using the PostgreSQL Flyway migration set.
- Impact: Production startup requires schema-changing database permissions, migration history does not fully describe the security schema, and rollback/validation cannot prove the mobile token table’s exact state.
- Fix: Move the table, indexes, foreign key, and expiry/revocation indexes into a versioned PostgreSQL Flyway migration; remove runtime DDL; validate with the Render profile.
- Mitigation: Verify current production schema and restrict the application database user after migration.
- False-positive notes: The statement is parameter-free and not an SQL-injection issue; this is schema governance and least privilege.

### SEC-10 — Security-critical mobile routes have no dedicated tests

- Rule ID: TEST-AUTHZ-010
- Severity: Medium
- Location:
  - src/test: no MobileApi or MobileAuth tests found
  - build.gradle:72-77
- Evidence: The full suite contains 617 passing tests, but repository search found no mobile API/authentication security tests. Two repository/container tests are explicitly excluded from the default test task.
- Impact: The gym-role escalation, cross-role access, token expiry/revocation, and mobile login throttling can regress without a failing build.
- Fix:
  1. Add MockMvc/integration tests for each role and each mobile route.
  2. Include token expiry, revoked token, object ownership, trainer/client link, and admin-function matrices.
  3. Replace broad default exclusions with tagged suites and run the full database-backed set in CI.
- Mitigation: Disable the risky mobile routes until the matrix exists.
- False-positive notes: Existing controller/service tests still provide broad application coverage; this finding is specific to the new security boundary.

## Low-severity findings

### SEC-11 — Two npm build-tool advisories are present

- Rule ID: SUPPLY-NPM-011
- Severity: Low in this deployment context
- Location: package-lock.json / node_modules as reported by npm audit on 21 August 2026
- Evidence:
  - postcss <=8.5.22: GHSA-fxqj-rqcc-2cmp, one moderate advisory, fix available in 8.5.23.
  - nanoid <3.3.18: GHSA-2v37-7h3g-55p8, one high advisory, transitive, fix available.
  - npm reports 0 production vulnerabilities, 170 development dependencies, and 2 total advisories.
- Impact: These packages execute during asset builds. The current project builds trusted repository CSS and does not expose the affected nanoid custom size parameter, so runtime exploitability is low, but a compromised or untrusted build input could affect confidentiality/availability.
- Fix: Update PostCSS and the transitive nanoid chain, regenerate the lockfile, then rerun CSS build and npm audit.
- Mitigation: Keep build inputs trusted and isolate CI credentials from frontend asset builds.
- False-positive notes: The advisory severities are upstream severity, not the application-specific severity. The app-specific rating is reduced because the packages are build-time dependencies.

Primary advisory records: [PostCSS GHSA-fxqj-rqcc-2cmp](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp) and [nanoid GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8).

### SEC-12 — Referrer and browser capability policies are absent

- Rule ID: HEADERS-012
- Severity: Low
- Location: live GET / response on 21 August 2026
- Evidence:
  - Present: Cache-Control no-store/no-cache, X-Content-Type-Options nosniff, X-Frame-Options DENY.
  - Absent locally: Referrer-Policy, Permissions-Policy, Cross-Origin-Opener-Policy.
  - HSTS was absent over HTTP, which is expected locally and must be verified over deployed HTTPS.
- Impact: Browsers receive fewer explicit boundaries around referrer disclosure and optional device/platform capabilities.
- Fix: Add a deliberate Referrer-Policy and a minimum Permissions-Policy; assess COOP/COEP only if compatible with OAuth/payment flows. Verify HSTS on the deployed HTTPS response.
- Mitigation: Configure these at the reverse proxy if application-level delivery is not preferred.
- False-positive notes: These are defence-in-depth headers; absence is not proof of an exploitable vulnerability.

### SEC-13 — Dependency freshness and framework migration need a controlled lane

- Rule ID: SUPPLY-JAVA-013
- Severity: Low
- Location: build.gradle:3-6,46-69
- Evidence: dependencyUpdates passed and reports several later releases, including Spring Boot 3.5.16 within the current line, PostgreSQL 42.7.13, jsoup 1.23.1, H2 2.4.240, and a much newer OpenPDF major line. It also reports major upgrades that should not be applied blindly.
- Impact: Remaining on older patch lines increases maintenance and security response lag. Conversely, bulk major upgrades could break authentication, migrations, or PDF generation.
- Fix: Patch within the current compatible release line first, use a separate branch for Spring Boot 4/Gradle 9/OpenPDF 3 migrations, and verify all 617 tests plus production-profile startup.
- Mitigation: Enable automated dependency PRs with grouped patch/minor updates and a security-only fast lane.
- False-positive notes: “Outdated” does not mean “vulnerable”; this finding is lifecycle hygiene, not a CVE claim.

## Confirmed positive controls

1. BCryptPasswordEncoder is configured for stored passwords.
2. Form endpoints include CSRF tokens; anonymous POSTs without a token were rejected with 401 by the custom access-denied handling.
3. CSRF exclusions are limited to the bearer-token mobile API and Stripe webhook; the Stripe service performs HMAC signature and timestamp validation.
4. An unknown cross-origin preflight request to the mobile API was rejected with 403 and no Access-Control-Allow-Origin.
5. Anonymous GET /api/mobile/me returned 401; anonymous admin access returned 401.
6. Mobile bearer tokens use 48 random bytes, store SHA-256 hashes, support revocation, and expire after 30 days.
7. Mobile SQL uses bound parameters rather than concatenating request data into SQL.
8. Chat/profile image uploads enforce byte limits, decode and re-encode image content, generate server filenames, normalise paths, and prevent traversal. Workout video uploads inspect magic bytes, use owner/session paths, and enforce size limits.
9. Private chat and workout media are served by an authenticated owner-checking controller with no-store caching.
10. Render configuration enables Flyway, Hibernate schema validation, persistent JDBC sessions, forwarded headers, and a required persistent card-encryption key.

These controls align with the [OWASP File Upload Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html), which recommends authenticated uploads, server-generated names, size/type/content validation, storage outside the webroot, and controlled retrieval.

## Live verification record

- GET /: 200
- GET /login: 200
- GET /actuator/health/liveness: 200
- GET /actuator/health/readiness: 200
- GET /api/mobile/me without bearer token: 401
- GET /admin/gym-applications anonymously: 401
- GET /uploads/chat/not-real.png anonymously: 401
- OPTIONS /api/mobile/me from https://evil.example: 403, no allowed origin
- POST /login without CSRF: 401
- POST /support/feedback without CSRF: 401
- GET /h2-console/: 200 only in the intentionally local development profile

The local root health aggregate returned 503 while both configured liveness and readiness probes returned 200. The aggregate response did not expose details. Production operations should rely on the explicitly configured probes and separately diagnose why the aggregate includes a failing contributor.

## Prioritised remediation plan

### P0 — Release blockers

1. Remove direct mobile GYM_ADMIN provisioning and reuse the gym application/approval service.
2. Restrict mobile gym application operations to platform administrators.
3. Add a shared mobile/web authentication throttle.
4. Revoke all sessions and mobile tokens after password reset.
5. Disable or tightly quota provider-backed unauthenticated AI calls.
6. Add mobile auth/authorisation integration tests before re-enabling the routes.

Acceptance evidence:

- A public caller cannot obtain GYM_ADMIN.
- GYM_ADMIN cannot list or approve platform gym applications.
- Only PLATFORM_ADMIN/SUPER_ADMIN can approve.
- Repeated mobile login and recovery attempts are throttled.
- A pre-reset bearer token returns 401 after reset.
- The full role matrix is green in CI.

### P1 — Browser and recovery hardening

1. Hash reset tokens at rest and make recovery timing uniform.
2. Add recovery and AI rate limits plus operational telemetry.
3. Replace the confirmed user-derived innerHTML sink.
4. Deploy CSP in report-only mode, then enforce it.
5. Self-host or add SRI to Quill and Sortable.
6. Add Referrer-Policy and Permissions-Policy; verify HSTS on production HTTPS.

### P2 — Platform and supply-chain hardening

1. Move mobile token DDL into Flyway.
2. Patch PostCSS/nanoid and compatible Java dependencies.
3. Restore excluded database tests through tagged CI jobs.
4. Add automated security dependency scans and a production-profile smoke test.

## Verification commands run

- .\gradlew.bat bootRun --args=--spring.profiles.active=local --console=plain
- .\gradlew.bat test --rerun-tasks --console=plain
- .\gradlew.bat dependencyUpdates --console=plain
- npm run build:css
- npm audit --audit-level=low --json
- npm run i18n:check
- git diff --check
- Bounded Invoke-WebRequest/curl checks for routes, headers, CSRF, CORS, and upload privacy

Results:

- Java: 617 tests, 0 failures, 0 errors, 0 skipped; full rerun passed in 2 minutes 39 seconds.
- CSS: production build passed.
- dependencyUpdates: passed.
- npm audit: failed its security threshold with 1 high and 1 moderate build-time advisory.
- i18n check: failed because 65 unlocalised strings remain across four templates.
- git diff --check: passed; it emitted existing line-ending warnings only.

## Release recommendation

Security status: **No-go until SEC-01 through SEC-04 are resolved and covered by integration tests.**

The remaining medium and low findings should be scheduled immediately after the release blockers, with CSP introduced through a report-only rollout to avoid breaking the existing rich frontend.
