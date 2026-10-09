# Interactive homepage sculpture

The homepage logo is a solid model made in Blender. Three.js loads its GLB, lights it in the browser and animates the two hinged halves. There are no stacked logo images in the interactive model.

## Visitor experience

- Move the pointer within the sculpture area to gently rotate it, or use the arrow keys. Click, Enter or the animated mouse button unfolds it in the homepage. The mouse cue appears on hover or keyboard focus, and stays visible on touch devices. Instructions remain available to screen readers. No dialog, navigation, scroll lock or focus trap is used.
- The sculpture first returns to its resting pose, turns while still closed, then opens its cyan and orange halves like book covers. There are no rectangular page inserts. Curved connections reveal the trainer, gym, workout and activity network; the story expands across both homepage columns below the scene near the end of the opening. The three mode controls sit inside the artwork viewport. A second, four-destination journey navigates trainer, gym, workout and activity stories; it shares the same selected node as the 3D scene. Select a labelled node, or follow the guided connection.
- The initial opening uses a 1.15-second staged sequence. Subsequent mode selections begin immediately and settle in 0.45 seconds. Reduced motion and pause still settle immediately. The launch action is a persistently visible named button, and story copy remains selectable.
- Satin enamel and bounded studio lighting retain the cyan/orange colours at steep viewing angles. Selected and hovered connections interpolate their emphasis smoothly.
- Text selection, copying and the browser context menu remain available. Keyboard controls and normal form editing use their standard browser behaviour.
- The story area pairs trainer and gym photography with orbital framing, a live workout rep ring and an illustrated activity world. These are illustrative demo visuals, not a live coach listing or progress feed.
- The workout offers a six-rep sample set. Its counter, six markers and ring update from the same demo state. Completing it moves to activity; no account data or network writes are involved.
- Explode separates the enamel, illuminated channel, supporting shell and central cores. The construction description identifies the parts; Reassemble returns them to the logo.
- Make it yours contains three colour palettes, individual colours, separation, lighting, animation speed, distance and wireframe display. Reset restores the defaults and clears sample progress.
- Escape or Fold the logo closes the inline details and restores focus to the entry point. Reduced motion starts paused and skips the turning transition. Ambient rendering suspends off screen and in background tabs; deliberate mode changes finish even when scrolling moves the artwork off screen.
- If WebGL or the model cannot load, the rendered poster and text-based network demo remain available.

## Source and generated files

| Purpose | Path |
| --- | --- |
| Reproducible Blender model and render | `tools/brand/build-logo.py` |
| Editable Version 2.0 Blender scene | `design/brand/one-to-one-sculpture-v2.blend` |
| Preserved original Blender scene | `design/brand/one-to-one-sculpture.blend` |
| Shipped mesh and poster | `src/main/resources/static/models/one-to-one/` |
| Browser scene and interactions | `src/main/frontend/logo-experience.js` |
| Frame clock, opening sequence and pointer mapping | `src/main/frontend/animation-clock.mjs` |
| Bundle build | `tools/brand/build-web.mjs` |
| Generated script and licence notice | `src/main/resources/static/js/public/logo-experience.bundle.js*` |
| Markup | `src/main/resources/templates/public-views/home/fragments/logo-experience.html` |
| Sculpture styling | `src/main/resources/static/css/components/misc/logo-experience.css` |
| Homepage atmosphere and full-width journey | `src/main/resources/static/css/components/misc/home-cosmos.css` |
| English and Welsh labels | `src/main/resources/messages-logo*.properties` |

The dedicated message bundle is registered in both `LocalisationAdvice` and Spring configuration. Other supported site languages use English fallback for this experience. Existing page translations remain in their existing bundles.

## Rebuild

From `Web_App`, use the project's pinned Node/npm versions:

```powershell
npm ci
npm run build:frontend
npm run test:3d
```

To change the geometry, use Blender 4.5 or later:

```powershell
& 'C:\path\to\blender.exe' --background --python tools/brand/build-logo.py
```

The script writes the GLB, saves the editable scene and renders the transparent poster using Cycles. Blender is only an authoring dependency; Java deployment uses the generated assets. Keep the generated model, poster, browser bundle and licence notice with the source changes.

The two root groups `Wing_Left` and `Wing_Right` are the opening hinges. `Face_*`, `Circuit_*`, `Shell_*`, `Core_*` and `Wordmark` names are the browser animation contract. GLB axes are Y-up, with its front facing +Z. Preserve those names when editing the scene.

Portable Blender used from a packaged Windows application may be redirected under the package's `LocalCache/Local` directory. If its bundled assembly cannot be found, run the executable from its resolved directory; the model does not require a system-wide Blender installation.

## Validation

```powershell
.\gradlew.bat test --tests '*LogoExperienceContractTest' --tests '*PublicPageConsistencyContractTest' --tests '*HomeLocalisationContractTest'
```

The model contract checks the actual shipped binary header, independent layers and three-dimensional mesh bounds. Localisation checks compare English/Welsh keys with the fragment. Motion regressions exercise a Three.js path with an early first frame, alignment-before-opening and bounded pointer mapping. Browser verification is still required for lighting, animation, responsive layout, keyboard operation and fallback behaviour.

Only `/models/one-to-one/**` is publicly allowed. The model path is excluded from HTML wrapping and saved navigation destinations, just like the site's other static assets.

## Homepage composition

The Version 2.0 `home-v2.css` layer follows `home-cosmos.css`. It aligns the journey with the shared Connected Performance light/dark tokens, places One To One prominently above the coaching promise and keeps joining actions available immediately. The automatic page-covering splash is removed. The sculpture opens only on deliberate activation.

The Version 2.0 model adds solid luminous core collars and rear structural ribs, softer machined bevels, restrained satin materials and a matching ONE TO ONE wordmark. The reproducible generator produces the model, transparent poster and a new editable scene while retaining the original scene. Blender 5.2.1 LTS was used for this export; the browser still uses the established hinge and mesh-name contract.

The `home-cosmos` class scopes the planetary homepage direction. Its stylesheet is imported after the existing homepage and sculpture styles. Decorative planets and sparse star textures are CSS-only, non-interactive and hidden from assistive technology. Existing programme previews, trust tabs, role selection, registration and trainer links retain their existing handlers and destinations.

The sculpture wrappers use `display: contents` within the homepage grid so the canvas stays beside the hero text while the inspector spans both columns. Below 900px these elements become one column. The scene still has a single JavaScript state owner and observes the canvas viewport for rendering suspension. No extra animation loop or dependency is added.
