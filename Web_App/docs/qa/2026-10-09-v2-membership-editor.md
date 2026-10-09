# Version 2.0 membership editor checkpoint — 9 October 2026

Work resumed from the laptop handoff on `main` at `a13374b0`. This milestone advances W139; the full web/native Version 2.0 release remains unfinished.

The metadata service now locks and refreshes the stored product before copying only name, description and availability from the separate validated draft. It rechecks gym ownership after refresh. Ordinary editing preserves the latest stored price, billing period, gym ownership and existing subscriptions, including when the request persistence context holds an older price. The controller no longer mutates/saves a previously loaded entity.

Catalogue create/edit links carry search, status, page and page size into native forms. Back, Cancel, successful create/edit and rejected drafts retain bounded catalogue context. Return URLs are constructed from known local parameters rather than accepting a caller URL. The editor uses the existing semantic surface/button tokens, associated field errors and an error summary. Creation exposes USD price and monthly billing; editing shows authoritative current price, billing and active subscriber count with a separate Change Price entry. Delivery copy explicitly says attempts are not tracked. No new JavaScript or dependency is required.

## Fresh verification

- Final focused test and package run: **14 tests / two suites**, zero failures, errors or skipped, **1 minute 53 seconds**. `GymAdminMembershipControllerTest` has nine tests; `GymMembershipCatalogueIntegrationTest` has five. `build/v2-membership-editor-final-20261009.log` records this run. The earlier cold run passed the same fourteen tests in 3 minutes 48 seconds; these counts are not cumulative.
- Includes **28 actual create/edit renders in fourteen locales**, stale-managed-price preservation, forged price/ownership fields, one preserved active subscription, exact-cent creation, invalid retained draft/ARIA errors, owner denial and encoded filter/page return. Existing controlled price-change duplicate/email checks remain in the controller suite; email is mocked.
- CSS compilation and **42 localisation bundle key/placeholder parity** pass. `git diff --check` passes. Asset cache: **20261009v7a**. Packaged editor, catalogue and training CSS bytes match current source/generated resources.
- Fresh final executable startup: **11:53:53.303 Europe/London**, 31.498 seconds, local H2, port 8081. Email provider `none`, SMS `console`, AI disabled. No production data, deployment, migration, real subscription, money or notice was used.
- Actual synthetic gym login, native creation at **$12.35 USD**, filtered editor navigation, description/availability save and retained search were observed. The metadata save preserved $12.35/monthly and returned the product as inactive. After the final wording/package restart, the disposable fixture was recreated and the final editor inspected again.
- Final light English desktop and **390 × 844** phone views were inspected; phone document overflow is false and Save/Cancel height is **47.72 px**. Final captures: [desktop](../audits/evidence/v2-membership-editor-desktop-20261009.jpg), [phone](../audits/evidence/v2-membership-editor-phone-20261009.jpg). Viewport override was reset. The labelled H2 product is disposable and disappears on restart.

## Laptop build note

The installed Java 21 runtime initially failed before compilation with `Unable to establish loopback connection` / Windows Unix-domain `Invalid argument: connect`. A command-local short socket directory resolved it; no global Java or security setting changed:

```powershell
New-Item -ItemType Directory -Force -Path 'C:/Temp/one-to-one-java' | Out-Null
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-21'
$env:JAVA_TOOL_OPTIONS = '-Djdk.net.unixdomain.tmpdir=C:/Temp/one-to-one-java'
.\gradlew.bat test --tests '*GymMembershipCatalogueIntegrationTest' --tests '*GymAdminMembershipControllerTest' bootJar --console=plain
```

Frontend dependencies were restored with `npm ci`. This laptop has Node **25.6.1 / npm 11.15.0**, while the project pins **22.22.x / 11.11.x**. CSS compilation succeeded here; this is not a pinned-toolchain release gate.

## Remaining acceptance and next work

W139.3 is accepted for the tested metadata/validation/subscriber workflow. W139.1/.2/.4 and complete Q01–Q10 remain open: full themes, RTL browser, zoom, scripts disabled, failed enhancement, keyboard/screen-reader/device, creation replay, back/refresh and broader state acceptance are not inferred from these checks. PostgreSQL concurrency acceptance and the existing USD-to-launch-currency decision remain open.

Continue **W140/W141** price-change/history workflows: refresh locked price state, explicit effective-date/timezone and scheduled-state guidance, controlled duplicate/no-subscriber/delivery checks, stable bounded chronology and readable immutable history. The shared tablet quick-actions collision, W107 authored media/model, W114/W115 notification/context, W133 hosted-provider and every earlier web/native release gap remain open.

Changed files: membership service/controller, editor/catalogue templates, scoped/generated CSS and cache advice, existing catalogue integration tests, audit/checkpoint/implementation records and two captures. No schema change or new production dependency.
