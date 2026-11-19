# Native 3D viewport: implementation and verification

The reference at `/home/jesush/Downloads/geographer` embeds GeoGebra's graphing
3D applet. Demos now compiles that engine's original Java sources and runs its
rendering pipeline through native OpenGL ES/JOGL. The former Kotlin mesh sampler,
software depth rasterizer, trackball camera and 3D surface composable have been
removed. Demos' 2D graph implementation remains separate and unchanged by this
replacement.

## Source of truth

The inspected deployment is GeoGebra **5.4.920.0**, upstream commit
`dbbd9fb17f4f58f266e9b33c88fc34c6584f379b`. The source archive includes 4,851
byte-identical upstream files, with individual SHA-256 hashes. The build checks
the archive hash before compiling. See [source provenance](../third_party/geogebra/README.md).

The relevant reference files are `GeoGebraViewport.tsx`,
`GraphSettingsPopover.tsx`, `geogebraAdapter.ts`, `desmosTranslator.ts`,
`presets.ts`, `calculator.ts`, the viewport CSS, and `deployggb.js`. They are
snapshotted in `third_party/geogebra/reference-sources.zip`. The rendering
implementation is GeoGebra's shared `geogebra3D/euclidian3D` tree, including its
drawables, geometry managers, camera animations, controller, hit testing,
packed vertex/fragment shaders and ReTeX label layout.

## Native integration

| Layer | Implementation |
| --- | --- |
| Geometry, projection, lighting, transparency, grid, axes, picking | Original GeoGebra shared Java implementation |
| GL entry points | `NativeRenderer` / `NativeShaderRenderer`; GLES 2 on Android, JOGL 2.6.0 on desktop |
| Math labels | Original ReTeX layout and fonts, native Android Canvas / Java2D rasterization |
| Expressions and styles | Kotlin port of the reference translator and incremental adapter |
| Floating controls and settings | Native Compose controls, original Lucide paths and Plus Jakarta Sans fonts |
| Symbolic commands | Original GeoGebra `CASgiacB` protocol, evaluated by Demos' existing isolated native Giac worker |
| Documents | Camera XML plus native construction/adapter state; existing rows remain authoritative after edits |

One worker owns each kernel, input controller, GL context and render resources.
The worker presents its live native framebuffer to Compose. No browser,
WebView, JavaScript runtime, web bundle, downloaded graph image or remote engine
is used to render the application. Font and shader resources are packaged
locally. Demos' existing PNG/PDF export captures the same rendered graph.

The 3D component mounts lazily and remains alive, paused, while viewing 2D.
Changing an expression updates the construction incrementally. Slider updates
use native `setValue`, retaining dependents and camera state. Native selection
and object movement use the original controller. Saving retains moved objects;
editing their source row subsequently redefines them. Loading another document
creates a new viewport state. Expression undo/redo preserves the current camera.

Native pointer timestamps preserve the original 100 ms drag threshold and
rotation-speed sampling across the worker queue. A final pending camera step
is applied before release, so a batch of moves cannot be lost when spin starts
or stops. Continuous rotation remains an upstream animation and does not block
graph export. Idle views stop rendering after pending updates settle.

## Reference details deliberately retained

- Orthographic projection, initial azimuth −60° and elevation 20°, with default
  bounds [−6, 6] on all three axes.
- Primary drag orbits; Shift-primary and middle drag pan; right drag rotates
  even though the reference disables the right-click menu.
- Two-finger center movement competes with pinch distance using the original
  web-controller arbitration. Moving both fingers together rotates the view.
- Zoom buttons apply 0.75 and 1.333 to the configured bounds. Repeated presses
  use those configured bounds again. Home restores their scale and origin while
  retaining orientation.
- The deployment's internal two-pixel border deduction, minimum size, right
  margin, CSS scale and rounded input coordinates are reproduced. At a
  1080 × 760 reference window, its 698 × 712 host produces a 696 × 710 logical
  view scaled to approximately 666.086 × 679.484 pixels.
- Demos retains its original wrench button and graph-settings sheet. Its axis
  numbers, label visibility and custom axis labels update the native engine;
  this intentionally supports the existing Demos settings beyond the reference
  wrapper's unused 3D label fields.
- Lowercase internal labels make bare triples such as `(0,0,2)` GeoGebra vectors.
  Uppercase named triples such as `A=(1,2,3)` are points.
- Surface/quadric opacity uses the engine's defaults. The wrapper's filling
  override applies only to inequality, integral and polygon objects.
- The standalone viewport settings use the reference's 320-pixel card, spacing, border dimensions,
  font sizes/weights, blue colors, icon paths and control placement. Its initial
  loading indicator is native; the failure message gives an offline retry
  instruction appropriate to the native renderer.

Demos-specific table rows, extra columns, regressions, residuals, scalar
parameters and existing u/v parametric surface inputs are adapted into original
GeoGebra objects. The 2D editor, keypad, layouts and native file services retain
their existing behavior.

## Repeatable comparisons

`desktop/src/test/resources/geogebra-reference/` contains actual reference PNGs,
construction XML, settings, camera transforms, point interaction captures and
59 translator cases. The desktop tests exercise both XML restoration and the
native expression adapter, then render through a real native GL context.

The six scene comparisons produced these mean absolute RGB channel differences
on the 0–255 scale:

| Scene | Original XML → native | Expressions → native |
| --- | ---: | ---: |
| Empty axes/grid | 0.610 | 0.615 |
| Wave surface | 0.934 | 0.938 |
| Sphere | 0.505 | 0.510 |
| Wave, parameter and plane | 1.040 | 1.044 |
| Bowl, helix and vector | 0.859 | 0.863 |
| Cylinder and curve | 0.535 | 0.538 |

Tests require mean channel difference below 1.5 and fewer than 0.8% of pixels
with total RGB difference greater than 90. These are measured tolerances, not
a claim of identical pixels. Mouse orbit, Shift/middle pan, right rotation,
cursor-centered wheel zoom, zoom buttons and Home are compared to captured
reference camera transforms within `1e-9`. Drag endpoints are checked after a
pause before release to avoid making the assertion depend on host scheduling.
Continuous-spin timing has not been compared numerically across platforms.

Point hover and selected frames are also compared with the reference. Arrow
keys, Shift-arrow increments, the disabled Delete shortcut and Ctrl-plus zoom
are compared with the reference's point coordinates and camera transforms.
Additional tests cover coalesced touch input, camera restoration, construction
restoration, dependency updates, tables and native ReTeX captions.

Run the native tests on a desktop with OpenGL support and a connected Android
device/emulator:

```sh
./gradlew :desktop:test :app:connectedDebugAndroidTest
./gradlew :app:assembleDebug
python3 scripts/verify-geogebra-sources.py
```

Images, difference frames and numeric reports are written under
`desktop/build/reports/geogebra-native/`. Android reports are under
`app/build/reports/androidTests/connected/debug/`. Android instrumentation
includes the full calculator activity, dimension switching, saving/reopening,
native surfaces, contrast/settings/gestures, and native CAS operations.

To refresh captures, run the unmodified reference's Vite server, then:

```sh
PLAYWRIGHT_MODULE=/absolute/path/to/playwright/index.mjs \
BROWSER_PATH=/absolute/path/to/chromium \
REFERENCE_URL=http://127.0.0.1:4380 \
node scripts/capture-geogebra-reference.mjs
node scripts/capture-geogebra-translator.mjs /path/to/geographer
```

The capture tools are development-only. They are not included in either app's
runtime. Keep the engine version pinned while refreshing fixtures; the
reference's live `deployggb.js` may advance to a different version.

## Verification limits and existing failures

The desktop renderer has been tested on this Linux host through JOGL; Android
has been tested on an API 35 x86_64 emulator using native GLES/SwiftShader.
The full desktop application also opens and displays the native viewport on
Linux. Its existing Windows-only CAS launcher reports a missing `java.exe` on
Linux; desktop CAS execution must be verified on Windows. Android native CAS
integration passed its device tests.
The Windows executable/installer and physical ARM/Android GPUs cannot be run
on this host and remain unverified. Existing Windows DLL staging, collected
resource verification and installer hash guards remain intact.

Native text rasterization and GL multisample coverage differ from browser
rendering. The small residual differences above include glyph edges, line
antialiasing and some transparency edges. The renderer requests four-sample
antialiasing; Android falls back to an available non-MSAA configuration if the
device cannot provide it. Real touch hardware, pen events, high-DPI Windows
font rendering and every possible expression combination have not been
exhaustively compared. AR/VR and GeoGebra's hidden authoring toolbars are not
exposed by the reference wrapper.

The full Android JVM/Robolectric suite has five existing failures, reproduced
in the pre-replacement baseline: two `MathEditingTest` cases, the cursor-handle
case in `MathTouchTest`, and two `ModeKeypadTest` cases. The replacement run
completed 82 tests with those same five failures. Their unrelated production
code was left unchanged. The instrumentation undo test separates its edits by
more than the existing 750 ms typing group so it tests distinct undo actions.

The final native validation completed **11 desktop tests and 13 Android device
tests with no failures**. The Android calculator activity was launched separately
and its actual 3D frame was inspected. The desktop application was launched with
isolated recovery/preferences data and its own Compose rendering layer was
captured and inspected. Desktop OS screen capture was unavailable, so no other
desktop content was captured.
