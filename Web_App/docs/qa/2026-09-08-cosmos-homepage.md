# Planetary homepage and connected journey — 8 September 2026

## Delivered design

The homepage now shares the sculpture’s cyan/orange light with a deep navy atmosphere, restrained violet and mint accents, planetary horizons and sparse star texture. The direction uses planetary forms without spacecraft controls or dashboard decoration. Decorative artwork is CSS-only and introduces no image download or animation dependency.

The expanded sculpture story spans the entire homepage grid below the hero. Four planetary destinations select trainer, gym, workout and activity; they share state with the 3D nodes. The story area pairs the existing coaching/gym imagery with orbital framing, a live six-rep dial, and an illustrated momentum world. Settings and motion controls are grouped in a quieter footer. The first-opening animation and immediate subsequent mode transitions are preserved.

The programme, trust, roles and final invitation sections continue the same palette. Existing preview handlers, coaching rules and signup/trainer destinations are retained. Visible copy and the new journey labels have English/Welsh messages; other site languages retain the existing English fallback for this bundle.

## Implementation

- `home-cosmos.css`: scoped homepage art direction, CSS planets, full-width desktop layout and responsive story variants.
- `public.html`: homepage scope class, stylesheet import and decorative planetary elements.
- `fragments/logo-experience.html`: four-destination navigation, richer story visuals, rep dial and compact settings area.
- `logo-experience.js`: selected-node and rep state shared with the new visual elements; existing renderer/motion ownership retained.
- `messages-logo*.properties`: new and revised journey copy.
- Asset version: `20260908cosmos2`.

The scene wrappers use `display: contents` to participate in the homepage grid. The inspector spans both desktop columns; phone/tablet layouts use one hero column. Grid content is aligned to the start to prevent the sculpture moving when the full-width story opens.

## Verification

- Frontend build passed; six animation tests passed.
- Focused Java suite passed (17 tests) during implementation; final packaging check recorded below.
- New stylesheet parsed with PostCSS; fragment parsed with parse5 without errors.
- Desktop at 1440 × 1000: inspector expanded to the full 1312px content width. No horizontal overflow.
- The four journey buttons update the selected story and the 3D node together.
- Three workout reps produced counter 3, three completed markers and a 180-degree ring. Six reps selected Activity with the completed sample message.
- Phone at 390 × 844: inspector width 348px, four destinations fit in one row, story artwork and copy stack, no horizontal overflow.
- Existing Week programme tab and Gym owner role tab worked after restyling.
- Existing low-contrast green role-heading text was adjusted for the navy background; the old green coaching strip was replaced with open rows.

Screenshots are under `output/brand-experience/`: `cosmos-journey-desktop.png`, `cosmos-workout-desktop.png`, `cosmos-lower-desktop.png` and `cosmos-mobile.png`.

The imagery and rep journey are illustrative. No account data is saved by the sculpture demo. Local Chromium visual checks only; no physical phone, Safari or Firefox validation and no deployment.

### Final verification

- `npm run build:frontend`, `npm run test:3d` and the final CSS build completed successfully.
- `gradlew.bat test --tests '*LogoExperienceContractTest' --tests '*PublicPageConsistencyContractTest' --tests '*HomeLocalisationContractTest' --tests '*HtmlDoctypeResponseFilterTest' --tests '*UserSettingsLocaleInterceptorTest' bootJar` passed: 17 tests, zero failures/errors; executable JAR built.
- SHA-256 comparisons confirmed that the packaged cosmos stylesheet, homepage, sculpture fragment, JavaScript bundle and both message bundles match their source files.
- Final live checks confirmed the `20260908cosmos2` assets, stable sculpture position during opening, keyboard Enter selecting Workout, reduced-motion story animation set to `none`, three rep markers for three reps, and no captured JavaScript exceptions.
- Final phone check confirmed the four destinations fit with their full orbital accents and no horizontal overflow. Tablet was checked at 820 × 1180.
- The coaching strip is transparent and the role emphasis uses the corrected pale-blue colour on the navy background.
- A development reload after compilation temporarily omitted a page-model setting and caused a Thymeleaf render error. A clean restart of the same local profile restored normal rendering without a source workaround. The restarted homepage rendered successfully and the server remains running at localhost:8081.
- Final captures include `cosmos-hero.png`, `cosmos-journey-desktop.png`, `cosmos-workout-desktop.png`, `cosmos-lower-desktop.png` and `cosmos-mobile.png`.
