# ColorStruction

An Android paint mixing helper: pick a target color and ColorStruction tells you which
of *your own* paints to mix, and in what proportions, to get it.

Status: early development (v0.1.0) — project skeleton only; nothing below is usable yet.

## About

Mixing paints is guesswork: you look at a reference color and try to guess how much of
each tube to squeeze out. ColorStruction solves that problem in reverse. You give it a
target color (from a color wheel, a HEX code, or a pixel of a photo), and it searches
mixes built from the paints you actually own, then shows the winning combinations with
proportions and an accuracy estimate.

Everything stays on your own list of paints: you add your paints once (name, color,
tinting strength) and the app only ever suggests mixes of those.

## Features

Planned / in development:

- Color wheel picker — hue ring plus a saturation/value square, with markers
- HEX and RGB input, kept in sync with the wheel
- Eyedropper on a photo from the gallery or a fresh camera snapshot, with zoom/pan and a
  magnifier loupe
- Multiple palettes, each marked as opaque (acrylic, oil, gouache) or watercolor
- Recipes of up to 5 paints, preferring fewer components when accuracy allows
- Proportions shown either as parts ("3 : 1 : ½") or as percentages
- Russian and English UI with in-app language switching
- In-app update check that installs the latest GitHub release

## How it works

Averaging RGB values is wrong for paint. Blue and yellow averaged in RGB gives gray, but
real paint gives green, because pigments subtract light rather than add it.

ColorStruction therefore mixes subtractively:

1. Each sRGB color is turned into a reflectance spectrum (38 bands, 380–750 nm) by
   decomposing it into 7 basis spectra — the spectral.js approach.
2. Spectra are converted to absorption/scattering (K/S) values with the Kubelka–Munk
   equations: `KS = (1 - R)² / (2R)`.
3. A mix is the weighted sum of the K/S values: `KS_mix = Σ wᵢ · KSᵢ`.
4. The result is converted back to reflectance, then to CIE XYZ (D65) and sRGB.
5. Candidate mixes are ranked with CIEDE2000 color difference against the target.

The model is an approximation: real pigments differ from the reconstructed spectra, so
every paint carries a "tinting strength" parameter you can tune to match what you see on
the palette. There is no claim of perfect color reproduction.

## Installation

1. Download the latest APK from
   [Releases](https://github.com/IWatman/ColorStruction/releases).
2. Allow installing from unknown sources when Android asks.
3. Open the APK to install.

Later versions can be installed from inside the app via Settings → Check for updates.
All releases are signed with the same key, so updates install over the existing app.

## Building from source

Requirements:

- JDK 17 or newer
- Android SDK with platform 35 and build-tools 35.0.0

Create `local.properties` with the SDK location (this file is not committed):

```properties
sdk.dir=/path/to/Android/Sdk
```

Build and test:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Tech stack

- Kotlin, coroutines and StateFlow
- Jetpack Compose with Material 3
- Navigation Compose
- ViewModel with a hand-written `AppContainer` (no DI framework)
- Room for paints and palettes
- DataStore for settings
- AppCompat per-app locales for language switching
- JUnit (JVM) for color math and the solver; Compose UI tests for key screens

## Project structure

```
app/src/main/java/com/colorstruction/
  color/       color math, spectra, Kubelka-Munk, color difference
  mixing/      recipe solver
  data/        Room, repositories, presets, DataStore
  update/      GitHub Releases checker and installer
  ui/
    theme/
    picker/      color wheel, HEX/RGB input
    eyedropper/
    paints/      palettes and paints
    mix/         home screen and recipes
    settings/
  AppContainer.kt, ColorStructionApp.kt, MainActivity.kt
```

## Roadmap

The full plan — color model, solver design, data model, screen specs and per-stage tasks —
is in [PLAN.md](PLAN.md).

Stages:

1. Color math
2. Solver
3. Storage
4. Color wheel
5. My paints screen
6. Home screen and recipes
7. Eyedropper
8. Settings, language, updates
9. Polish

## Contributing / Translations

The UI ships in English (default) and Russian. Adding a language takes two steps:

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-<code>/strings.xml`
   and translate the values.
2. Add a `<locale android:name="<code>" />` entry to
   `app/src/main/res/xml/locales_config.xml`.

Never hardcode user-visible text in Compose code — always use string resources.

## License

License: TBD
