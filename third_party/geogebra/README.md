# Native GeoGebra viewport sources

The authoritative wrapper is `/home/jesush/Downloads/geographer`. Its
`src/components/GeoGebraViewport.tsx` creates a GeoGebra `appName: "3d"` applet,
not a custom Three.js renderer. The deployed engine inspected for this port was
**5.4.920.0**, corresponding to upstream commit
**dbbd9fb17f4f58f266e9b33c88fc34c6584f379b** in
[geogebra/geogebra](https://github.com/geogebra/geogebra/tree/dbbd9fb17f4f58f266e9b33c88fc34c6584f379b).

`geogebra-5.4.920.0-sources.zip` contains the original, unmodified source files
compiled by `:geogebra`. `manifest.json` records the archive SHA-256 and a SHA-256
for every included file. Gradle verifies the archive before unpacking it.
`scripts/verify-geogebra-sources.py` also checks every member and can compare
them with a checkout of the pinned revision. Updating the engine is an explicit
source update; builds do not fetch a moving GeoGebra deployment.

Included shared modules: `canvas-base`, `common`, `common-jre`, `editor-base`,
`renderer-base`, `keyboard-base`, `ggbjdk`, and `giac-jni`. Three UI-neutral
graphics/font base classes come from upstream `common-jre` test fixtures.
The original `renderer-desktop` resources provide ReTeX's math TTFs. JavaCC
generates the original three parsers during the build. The upstream JNI
wrappers and JRE JNI binding are excluded from compilation because Demos
owns its JNI declarations and runs Giac in a separate native process.

`reference-sources.zip` snapshots the reference's `src/`, `index.html`, package
metadata and the inspected `deployggb.js`, for auditing the wrapper and sizing
adapter. None of these web files are used as an application runtime.

The port's platform bindings live in `geogebra/src/main/java/dev/demos/graphing/geogebra/`:

| Native binding | Original implementation retained or adapted |
| --- | --- |
| `NativeView`, `NativeApp` | Shared `EuclidianView3D`, graphing-3D configuration and 3D kernel |
| `NativeController` | Shared `EuclidianController3D` plus the web controller's immediate picking and two-touch arbitration |
| `NativeRenderer`, `NativeShaderRenderer` | Shared `Renderer`, `RendererImplShaders`, `ManagerShaders`, packed WebGL shader source and render passes; GL calls bound to native APIs |
| `NativeAwtFactory`, `NativeTeX`, `NativeEquation` | Shared geometry/shape classes and original ReTeX math layout; native glyph/path/image rasterization |
| `NativeCasFactory` | Original `CASgiacB` translation, custom functions, cache and parsing; evaluation in Demos' isolated native Giac worker |

The Kotlin expression translator and settings adapter in `shared/.../plot/`
port `src/engine/desmosTranslator.ts` and `geogebraAdapter.ts`. The floating
controls port `GeoGebraViewport.tsx` and `GraphSettingsPopover.tsx`.

## Notices

Original per-file copyright and license headers are preserved in the archive.
GeoGebra files carry EUPL 1.2 and/or GPL notices. The overall upstream package
also specifies separate non-commercial terms at
[geogebra.org/license](https://www.geogebra.org/license). Consult those terms
and the included license texts for redistribution.

`app/src/main/assets/cas/` contains `GEOGEBRA-NOTICE.txt`, EUPL 1.2, GPL 2,
the OpenJDK Classpath exception, ReTeX's license, Plus Jakarta Sans's OFL,
and Lucide's ISC/Feather MIT notices. The viewport uses Plus Jakarta Sans's
400/500/600/700 weights and Lucide 0.546.0's original icon paths.
JOGL/GlueGen 2.6.0 include their notices in their distribution JARs.
The Windows resource task stages these notices and the source archives beside
the existing Giac sources.

See [implementation and verification](../../docs/NATIVE_3D.md) for tested
behavior, comparison results, reproduction commands and remaining differences.
