# Fixed pivot and responsive logo - 9 October 2026

The user clarified that Explode must stay in the same position and dragging should change its angles. This supersedes the earlier translation checkpoint.

Explode now supports primary pointer dragging around the stationary world pivot. Release preserves the angle; pointer hovering does not move or reset it. Removed pan state, translation gesture code, Shift-arrow movement and the Drag/Rotate buttons. Arrow keys (including modified arrow keys) rotate only. Mode changes still reset the angle, and selecting a connection switches back to the network. Cancellation, pointer capture and touch-action handling remain.

Responsive improvements move the view switcher below the canvas, with a 4px gap. Phone connection tiles use two columns, larger targets, compact headings and padding. The stage has sufficient height to show the wordmark. Instructions use the existing English/Welsh bundles. No inline scripts/styles or new dependency.

## Verification

- Browser bundle built; six existing animation-clock tests passed with no failures/skips. These are transition regressions, not new gesture tests. Executable package built successfully in 14 seconds; final asset version `20261009v7f`.
- Actual desktop pointer drag settled at yaw 0.450 / tilt 0.180. Pivot remained X 0.000 / Y 0.220 before and after release. Shift + Right changed rotation while retaining the same pivot.
- Actual 320px phone drag settled at yaw 0.270 / tilt 0.135 with the same fixed pivot. No movement-tool buttons exist. Selecting Trainer switched to network and hid the pivot instructions; Sculpture switching also works.
- At 320px, stage height is 300px, modes height 54px and gap 4px; no document overflow. At 768px, gap remains 4px and no document overflow. Desktop initial rendering was inspected at normal 1280px sizing. Temporary viewport override reset.
- [Phone fixed-pivot capture](../audits/evidence/v2-logo-fixed-pivot-phone-20261009.jpg). Earlier 390px responsive sculpture capture is retained as historical evidence.

Local site remains running on loopback port 8081, PID 34472. The full V2 checklist goal remains active; touchscreen hardware and broader release acceptance are still open.
