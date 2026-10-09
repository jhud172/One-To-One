# Connection detail previews — 9 October 2026

The Connections scene no longer opens the large introduction and four-item panel automatically. Hovering a scene item or focusing its label opens its description. Clicking a sphere or label pins that description; clicking another item switches the pinned description. Clicking the same item, the close control, the blank canvas, or elsewhere on the page dismisses it. Escape dismisses details before folding the scene.

The detail card retains the existing guided demo actions and customization controls. Its entrance and story changes animate, with reduced-motion preferences respected. Explode retains its construction controls and fixed pivot. The non-WebGL fallback retains its guided navigation.

Validation:

- `npm run build:3d`: browser bundle rebuilt successfully.
- `npm run test:3d`: 6 passed, no failures or skips (animation-clock regression coverage).
- Java 21 `gradlew.bat bootJar`: successful; rebuilt local preview served on loopback port 8081.
- Actual in-app browser interactions: initial Connections details hidden; pointer preview without pinning; keyboard focus preview; click pinning; pin survives focus on another item; repeat click, close button, Escape and outside click dismiss; mode switch clears details; Explode construction controls remain available.
- Visually inspected at desktop 1440×1000, tablet 768×1024, and phone 390×844 and 320×740. No horizontal overflow at the tablet and phone widths. Scene labels have 44px minimum touch targets.
- `git diff --check`: passed.

Evidence: `docs/audits/evidence/v2-logo-connection-preview-desktop-20261009.jpg` and `docs/audits/evidence/v2-logo-connection-preview-phone-20261009.jpg` (local evidence files).

These checks cover this interaction change, not the full release checklist. Node 25 was available locally rather than the pinned Node 22 runtime. Production Render deployment remains constrained by the previously observed 512MB service memory limit; a paid service upgrade has not been approved.
