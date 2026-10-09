# Interactive sculpture verification — 8 September 2026

> Historical verification of the first version. The subsequent inline pointer-following refinement and its current checks are recorded in [the inline refinement report](2026-09-08-inline-sculpture.md). Modal, drag and focus-loop observations below describe the superseded version.

## Delivered

The public homepage now loads a solid Blender-made logo with enamel faces, illuminated channels, titanium backing and separate cores. The entry interaction turns the sculpture sideways and opens its two halves around an animated trainer/gym/workout/activity network. Construction mode separates the real mesh parts, with an explanation and reassemble action. Palettes, custom colours, light intensity, separation, distance, speed, wireframe, pause and reset are included.

The source scene is `design/brand/one-to-one-sculpture.blend`; regeneration and file ownership are documented in [the sculpture guide](../brand/INTERACTIVE_SCULPTURE.md). The previous stacked-image JavaScript builder was removed. Homepage navigation, registration destinations and the other three homepage chapters remain in place.

## Build and automated checks

| Check | Result |
| --- | --- |
| Blender 4.5 background model/export/Cycles render | Passed, `ONE_TO_ONE_BLENDER_EXPORT_PASS` |
| `npm run build:frontend` | Passed, CSS and 3D bundle generated |
| `npm run test:3d` | 2 passed |
| Focused Gradle tests below | 17 passed, 0 failures, 0 errors |
| `gradlew.bat bootJar` | Passed |
| JAR asset comparison | Model, poster, CSS, script, licence notice and messages match source assets byte for byte |
| `git diff --check` | Passed |

```powershell
.\gradlew.bat test --tests '*LogoExperienceContractTest' --tests '*PublicPageConsistencyContractTest' --tests '*HomeLocalisationContractTest' --tests '*HtmlDoctypeResponseFilterTest' --tests '*UserSettingsLocaleInterceptorTest' bootJar
```

The Java checks cover the binary GLB structure, independently named solid layers, non-zero geometry bounds on all three axes, English/Welsh key parity, message resolution for every supported site language, existing homepage contracts and locale handling. The animation regression reproduces an early RAF timestamp against a real Three.js curve. A long background-tab gap is also capped to avoid a jump.

## Browser evidence

Verified against the running local Spring `local` profile on `http://localhost:8081/` using the in-app Chromium browser:

- Live model loaded, reached `data-ready=true` and settled. Model, poster, stylesheet and script returned HTTP 200. The final renderer check contained no JavaScript exceptions or console warnings/errors.
- Opened the logo, watched the side turn and book opening, then selected trainer, gym, workout and activity nodes.
- Completed all six sample reps and observed `6 / 6 reps complete` with Activity selected.
- Switched to Explode and visually confirmed separate faces, light channels, backing and cores. Tested maximum separation.
- Selected Aurora and observed violet/mint materials. Enabled wireframe and confirmed visible triangles, adjusted distance, paused, and reset to the signature palette, assembled model and zero reps.
- Dragged the model directly and visually confirmed a changed 3D viewing angle.
- Opened with Enter under reduced-motion emulation: motion started paused and the scene settled without the side-turn animation. Escape closed the dialog and returned focus to the entry button.
- Confirmed Shift+Tab from Close cycles to Reset, and Tab from Reset cycles to Close.
- Checked 1440×900 desktop, 390×844 phone and 820×1180 tablet layouts. The phone workspace measured 362px wide / 362px scroll width; the tablet workspace measured 792px / 792px. Both dialogs had no horizontal overflow. Phone node selection worked.
- Deliberately blocked the GLB request. The 1100px Blender poster loaded, the canvas was hidden, the fallback explanation appeared, and the guided workout still accepted a demo rep. Removed the request block afterwards.
- Restored the viewport and reduced-motion emulation after testing.

Screenshots are in the ignored local folder `output/brand-experience/`:

- `home-final.png`
- `network-final.png`
- `exploded.png`
- `custom-wireframe.png`
- `mobile-final.png`
- `tablet-final.png`
- `drag-rotation.png`
- `fallback-demo.png`

## Fixes found during verification

The portable Blender assembly initially failed because Windows app storage redirected its directory. Launching Blender from the resolved package directory fixed this. No system-wide runtime change was needed.

The initial scene exposed an early-frame timing error, an unregistered custom message bundle, excessive bloom, a mobile pseudo-element overflow and a keyboard focus-loop edge case. Each was corrected and checked again. A Gradle invocation initially attached `--tests` to `bootJar`; the corrected command above completed successfully.

## Scope and remaining notes

This is a local implementation and verification, with a generated release JAR. No deployment or push was performed. The workout is a browser-only sample and does not save account data. Physical mobile hardware, Safari and Firefox were not tested.

New experience copy is English and Welsh; the site's other offered languages resolve to English for this bundle. Existing page translations are unchanged.

`npm audit` reports four findings in the existing CSS toolchain: two high, one moderate and one low. The affected packages (`browserslist`, `nanoid`, `postcss`, `postcss-selector-parser`) have the same versions before and after this change. Neither added package, Three.js nor esbuild, was listed in those findings. Details are recorded in `output/brand-experience/npm-audit.json`; no unrelated dependency upgrade was applied.

The earlier site/workflow audit remains in [2026-09-08-workflow-audit.md](2026-09-08-workflow-audit.md); this sculpture change does not claim to resolve its unrelated findings.
