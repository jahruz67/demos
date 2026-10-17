# Native Giac provenance and source

The app packages the **unmodified official GeoGebra Android AAR, revision 70507**,
from [GeoGebra's Maven repository](https://repo.geogebra.net/releases/org/geogebra/giac-android/70507/giac-android-70507.aar).
It contains genuine Giac plus libc++ for `arm64-v8a`, `armeabi-v7a`, `x86`, and
`x86_64`. No network access, desktop executable, or WebView is used at runtime.
Both ARM64 libraries have 16,384-byte ELF LOAD segment alignment.

## Corresponding source supplied with this project

- `giac-70507-source.zip`: complete unmodified [Giac source at commit 19a710a](https://github.com/geogebra/giac/tree/19a710aea9d528afa272ea582f3283f242e17af3). Its Git/SVN metadata identifies revision 70507.
- `gmp-6.3.0.tar.xz`: unmodified [GNU GMP 6.3.0 source](https://ftp.gnu.org/gnu/gmp/gmp-6.3.0.tar.xz).
- `mpfr-4.2.1.tar.xz`: unmodified [GNU MPFR 4.2.1 source](https://www.mpfr.org/mpfr-4.2.1/mpfr-4.2.1.tar.xz).

The Giac source archive includes `ANDROID_README.md`, `build.gradle`,
`giac-android/build.gradle`, and `src/jni/prebuilt/android_prebuilts.sh`. The
prebuilt README records GMP 6.3.0 and MPFR 4.2.1; their sources are included here.
Use those upstream scripts for a native rebuild on a supported Unix host with
the Android NDK and Gradle. Normal Demos builds use the pinned AAR and do not need
an NDK or a native cross-compilation environment. The archive is retained outside
Android assets so it does not increase APK size.

The shipped app includes GPL-3.0, LGPL-3.0, libc++ license texts, and an attribution
notice under `assets/cas`. Distribute those notices and corresponding source with
redistributions of this GPL-integrated application.

## SHA-256

```
2e0fd53361e87f1db8d3acc353c370ea53a0688d9d0c7021a4c57c7758898433  ../../app/libs/giac-android-70507.aar
1a340c35aad4df38d987fb15e1ec126c84a4945d3b4d33a876738d3a97e8c8f6  giac-70507-source.zip
a3c2b80201b89e68616f4ad30bc66aee4927c3ce50e33929ca819d5c43538898  gmp-6.3.0.tar.xz
277807353a6726978996945af13e52829e3abd7a9a5b7fb2793894e18f1fcbb2  mpfr-4.2.1.tar.xz
```

## Adapter design

`javagiac.giacJNI` declares the subset of official SWIG native methods needed to
construct a context, parse, evaluate, print, and free a value. Its package/method
names are JNI ABI requirements; keep them when enabling obfuscation. Every value
and context is freed explicitly. Each request creates a fresh context and applies
the current list of graph definitions before evaluating, preventing deleted
variables and old assumptions from leaking across graph edits.

`CasService` runs the genuine native engine on a serialized worker in a private
`:cas` Android process. `CasEngine` sends typed messages to it and returns results
on the main thread. Replacing a keyed expression discards its pending callback
and kills its active native process; a 20-second deadline also terminates that
process. Queued requests then start a fresh service. This protects editor and
graph responsiveness even when a symbolic operation is hard or native code fails.

`CasSyntax` only normalizes UI notation, including Unicode/LaTeX integral entry.
It performs no mathematical evaluation and does not emulate a symbolic engine.
Standalone indefinite integrals display the native antiderivative plus `C`;
embedded integrals preserve Giac semantics so differentiation and substitutions
remain exact. Some functions have no closed-form antiderivative; Giac may leave
an integral unevaluated. CAS capability is provided by upstream Giac, while full
HP Prime application or command compatibility is not claimed.

## Verification

`NativeGiacTest` runs the actual Android engine and its process-backed public API:
exact fractions, symbolic and definite integrals, variable upper bounds,
differentiation of an antiderivative, factorization, solving, limits,
determinants, complex numbers, precision, definition isolation, and cancellation.
`CasSyntaxTest` checks notation adaptation and incomplete-expression handling.
The same SWIG declarations were also exercised locally against GeoGebra's
same-revision Windows native DLL before Android integration.
