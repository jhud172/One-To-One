# Exploded logo manipulation — 9 October 2026

Historical checkpoint: superseded by the user clarification in [fixed-pivot update](2026-10-09-logo-fixed-pivot.md). Drag/Rotate controls and translation described below have been removed.
Requested behaviour: the exploded logo can move around its environment and pivot in place instead of following pointer hover. This moves the whole sculpture; it does not detach or independently edit individual model layers.

Explode now preserves position and angle after release. Drag translates the sculpture using bounded, viewport-relative offsets; Rotate pivots around its own centre while preserving those offsets. Shift-drag or right-drag also pivots. Pointer capture supports release outside the canvas; cancellation, blur, scroll, resizing and view changes terminate the active gesture. Background atmosphere remains separate from sculpture manipulation. Idle bobbing and pointer-following are disabled in Explode. Other views restore their centred position and ordinary interaction, with hidden manipulation controls.

Arrow keys pivot; Shift + arrow keys move. Explicit Drag/Rotate buttons have 44px targets and work without modifier keys. Touch scrolling is suppressed only on the exploded canvas. Instructions use the existing English/Welsh logo bundle fallback. Controls occupy their own row below the canvas so phone controls do not obscure the model. There is no inline script or style, new dependency, provider call or deployment.

## Verification

- Browser bundle built; all six existing animation-clock regression tests pass, with no failures/skips. These are clock/transition regression checks, not six new drag tests. Localisation key/placeholder parity passes for all 42 checked bundles. CSS and executable package build successfully. Fresh asset version: `20261009v7d`.
- Actual browser pointer drag at the original 568px panel width changed normalised position from 0/0 to **0.299/0.269**. Rotate drag preserved that position and settled at **0.450 yaw / 0.180 tilt**; subsequent pointer movement to the view controls did not reset the angle. Sculpture reset offsets to 0/0 and hid the tools; Connections selected its normal view. Keyboard Shift + Right moved X to 0.080; Left pivoted the angle. Desktop 1280px and phone 390px checks showed no document overflow; phone tool targets measured 44px.
- The initial phone capture exposed controls over the sculpture. Markup and home grid were repaired to place controls below the canvas, and final captures are recorded after the final-package check. Do not treat the superseded overlay capture as final acceptance.

Real touchscreen hardware, screen-reader output and the full release/role/theme/RTL matrix remain open. The broader One To One 2.0 completion goal remains active.

Final package built in **13 seconds** after successful CSS/bundle generation; local startup was **13:13:25.125 Europe/London**, 28.762 seconds, PID 6652 on loopback port 8081. Browser loaded the final `20261009v7d` bundle. At 390px, a real pointer drag moved X/Y to **0.201/0.154**; a Rotate drag retained those offsets. Selecting the Trainer connection returned to network mode with 0/0 offsets and hidden tools. At both 390px and 1280px, the toolbar starts exactly at the canvas bottom, with no document overflow. Final inspected captures: [desktop](../audits/evidence/v2-logo-explode-manipulation-desktop-20261009.jpg), [phone](../audits/evidence/v2-logo-explode-manipulation-phone-20261009.jpg). Browser viewport override was reset and the user's homepage left in Explode with Drag selected.
