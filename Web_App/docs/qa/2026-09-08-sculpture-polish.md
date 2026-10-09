# Sculpture lighting and interaction polish — 8 September 2026

## Changes

- Preserved the existing 2.65-second cinematic first opening and both hinged halves.
- Changed enamel to a satin finish, softened studio lighting, bounded exposure and reduced bloom so upward tilt retains blue/orange colour. Kept stronger environment reflection on the dark backing for visible depth.
- Replaced visible instruction sentences, the caption and text entry button with an animated mouse-click symbol. It appears on hover/focus and is visible by default for touch input. Accessible instructions and status remain available to screen readers.
- Moved Sculpture / Connections / Explode into the artwork viewport, with a sliding active indicator. Their subsequent transitions now respond on the first frame and finish in 0.85 seconds.
- Fixed the offscreen animation stall: deliberate finite transitions finish even outside the viewport; ambient animation still suspends off screen. Background tabs remain paused.
- Added smoothly interpolated node emphasis and hover/focus feedback on the connections.
- Suppressed selection, copying, image dragging and the artwork context menu inside the experience. Form controls keep normal editing. These are interaction restrictions, not protection against downloading public assets.
- Shortened the inline introduction and updated English/Welsh labels. Shipped asset version: `20260908logo5`.

## Evidence

- `npm run build:frontend`: passed.
- `npm run test:3d`: six passed. The added regressions verify immediate mode response and completion while offscreen without pointer events, followed by suspension of ambient rendering.
- Focused Gradle suite: 17 passed, zero failures/errors. Includes sculpture, homepage, localisation, HTML response filtering and locale handling contracts.
- `gradlew.bat bootJar`: passed; five updated script, stylesheet, markup and localisation assets matched the packaged JAR byte for byte.
- `git diff --check`: passed.
- Browser at the top of the pointer area: tilt -0.174 radians, blue/orange colour retained, no broad white bloom. Screenshot: `output/brand-experience/polish-top-angle.png`.
- Single Sculpture click: `phase=changing`, opening fraction already falling (0.714 observed), then settled at 0. Single Connections click: opening fraction 0.059 observed, then settled at 1 without another canvas click.
- Clicked Sculpture and immediately scrolled its viewport completely off screen (bottom -706.7px). It continued from `changing` to `settled`, opening fraction 0, without further interaction.
- 390 × 844 layout: no horizontal overflow; all three mode buttons fit at 86px width. Screenshot: `output/brand-experience/polish-mobile.png`.
- Reduced motion: Connections settled immediately at 1; the mouse cue animation was `none`.
- Double-clicked the experience label: selection stayed empty. Computed `user-select` is `none`; the poster has `draggable=false`.

Local Chromium verification only; no physical phone, Safari or Firefox verification. No publishing or deployment. Earlier QA reports record superseded interaction layouts.
