# Inline sculpture refinement — 8 September 2026

## Behaviour

The homepage sculpture now follows pointer position within its own area, using one animation loop and cached bounds. Dragging is no longer required. Pointer leave, cancellation, blur, scrolling and resizing reset the desired pose. Touch scrolling is preserved; keyboard arrows remain available.

Clicking or pressing Enter keeps the experience inside the homepage. The 2.65-second sequence first recentres the existing pose, then turns the closed sculpture, then opens the cyan and orange halves. The network grows into the opening and the controls expand below it near the end. No dialog, navigation, body scroll lock or focus trap is involved. The original headline stays aligned at the top as the details expand.

Removed the rectangular page meshes and perimeter connections. Individual connections now follow curved Bezier paths; the low orbit is irregular and subdued. Canvas and ambient illumination fade into the background on all edges. The surrounding straight background grid is removed and atmospheric rings are softened.

The entry action focuses the canvas, or the fold button in the fallback. Fold/Escape hides and disables the inline details and restores entry focus. Reduced motion starts paused and skips the transition. Rendering pauses when the sculpture viewport leaves the screen.

## Verification

- `npm run build:frontend`: passed, CSS and browser bundle generated.
- `npm run test:3d`: four tests passed, covering first-frame timing, capped resume delta, recenter/turn before opening, and bounded pointer coordinates.
- `gradlew.bat bootJar`: passed; seven updated CSS, JavaScript, localisation and template assets matched the packaged JAR byte for byte.
- `git diff --check`: passed.
- Focused Gradle suite: 17 tests passed, zero failures/errors, with the command below.
- Browser, desktop and 390 × 844 viewport: no horizontal overflow; readable node labels; both halves and flowing connections visible without page inserts.
- Moved the pointer with no buttons pressed: yaw changed from zero to approximately 0.31 radians. Clicking captured `phase=aligning` with `openAmount=0`; the scene subsequently settled at `openAmount=1` on the same URL with zero open dialogs.
- After the layout correction, the headline stayed at approximately 139px in document coordinates while the inline details expanded (rather than moving down to approximately 423px).
- Completed six sample reps and observed Activity selected with the completed progress message.
- Keyboard arrows, Enter and Escape worked under reduced-motion emulation; opening and folding settled immediately.
- Deliberately blocked the GLB: poster/fallback remained visible, the inline controls opened, focus moved to Fold the logo, and a workout sample rep was accepted. Removed the request block afterwards.
- Browser cache initially retained earlier assets. Verified refreshed assets with caching disabled and advanced the shipped version to `20260908logo3`.
- A contract check initially failed because writing the existing stylesheet on Windows introduced CRLF where its assertions expect LF. Restored repository LF endings and reran successfully.

```powershell
.\gradlew.bat test --tests '*LogoExperienceContractTest' --tests '*PublicPageConsistencyContractTest' --tests '*HomeLocalisationContractTest' --tests '*HtmlDoctypeResponseFilterTest' --tests '*UserSettingsLocaleInterceptorTest' bootJar
```

Local screenshots are stored in `output/brand-experience/`: `inline-aligning.png`, `inline-open-desktop.png`, `inline-open-mobile.png` and `inline-fallback.png`. These supplement the historical first-version report; its modal/drag checks no longer describe the current interaction.

Local Chromium verification only; physical phones, Safari and Firefox have not been tested. No deployment or remote publishing was performed. The earlier site/workflow audit remains separate.
