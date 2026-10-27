# ColorStruction — Development Plan

Android app that helps mix paints. The user picks a target color (color wheel / HEX /
eyedropper on an image), and the app shows which paints from the user's own list to mix,
and in what proportions, to get that color.

Repository: https://github.com/IWatman/ColorStruction

## Decisions (agreed with the user)

- Personal use, not published on Google Play: APKs are distributed via GitHub Releases
- In-app language switching: Russian and English to start, more languages later
- Everything on GitHub (code, comments, docs, commits, releases) is in English
- Mostly acrylics; each palette has a type: "opaque" (acrylic/oil/gouache) or "watercolor"
- Multiple palettes, one active at a time
- Paints are added manually: custom name + color (wheel / HEX / eyedropper)
- Proportions shown as parts or percentages (user toggle)
- Up to 5 paints per recipe (hard cap), fewer is better
- Eyedropper image source: gallery + camera snapshot
- In-app "Check for updates" button that installs the latest GitHub release
- Testing on an Android emulator (Claude runs it and inspects screenshots)

## Workflow

- Routine code (scaffolding, Room, screens from a clear spec, tests) is delegated to
  **DeepSeek** (`flash`; `pro` for hard parts — solver, spectral model — or when flash fails)
- Claude writes the task with acceptance criteria, then **reviews**: reads the diff,
  builds, runs tests, checks on the emulator; fixes it or sends it back to DeepSeek
- A Python `.venv` in the repo root is used for helper scripts (not committed)

## Stack

- Kotlin, Jetpack Compose, Material 3
- minSdk 26, targetSdk 35
- Room (paints and palettes), DataStore (settings)
- Navigation Compose, ViewModel + StateFlow
- AppCompat per-app locales (`AppCompatDelegate.setApplicationLocales`) for language switching
- No DI framework: a hand-written `AppContainer` (the project is small)
- Tests: JUnit (JVM) for color math and the solver; Compose UI tests for key screens
- Build: Gradle wrapper, JDK 21, Android SDK installed locally
- CI: GitHub Actions builds and tests; tagged builds publish a signed APK to Releases

## Core idea: how mixing is computed

Averaging RGB doesn't work: blue + yellow in RGB gives gray, but real paint gives green.
We use a **subtractive spectral Kubelka–Munk model**:

1. Each sRGB color → reflectance spectrum (38 bands, 380–750 nm) via decomposition into
   7 basis spectra (the spectral.js approach, MIT license — ported to Kotlin).
2. Spectrum → K/S via Kubelka–Munk: `KS = (1-R)² / (2R)`.
3. Mix: `KS_mix = Σ wᵢ·KSᵢ` (weights adjusted by pigment tinting strength).
4. Back: `R = 1 + KS - sqrt(KS² + 2KS)` → XYZ (CIE 1931, D65) → sRGB.
5. Color difference: **CIEDE2000** in CIELAB.

Mixbox is not used: its license is CC BY-NC.

The UI states the limitation honestly: the model is approximate and real pigments differ;
each paint has a "tinting strength" parameter for fine-tuning.

## Recipe solver

Input: target color, paints from the active palette (N), component cap of 5.

1. Exhaustive search over combinations of 1..3 paints (N≤30 → ~4.5k combinations, fine).
2. 4 and 5 components only if 3 can't reach ΔE < 2: beam search — take the top 20
   combinations of the previous size and add one paint at a time (full C(30,5)=142k is too costly).
3. For each combination, optimize weights (wᵢ ≥ 0, Σwᵢ = 1) minimizing ΔE2000:
   coarse grid → refinement with Nelder–Mead / coordinate descent.
4. Prefer fewer paints: a recipe with fewer components wins if its ΔE is worse by no more
   than ~1.0. Return the top 5 diverse recipes.
5. Watercolor: water lightens instead of white paint — a virtual "water" component (paper
   white), not counted as a paint, shown as "dilute with water".
6. Proportions: "parts" (e.g. 3 : 1 : ½) or % per user toggle; after rounding, recompute
   the resulting color and ΔE.
7. Runs on `Dispatchers.Default`, cancelled when the target changes; paint spectra are cached.

Accuracy labels: ΔE < 2 "almost exact", 2–5 "close", 5–10 "similar",
> 10 "can't be mixed from these paints" + a hint about which paint is missing.

## Data model

```
Palette(id, name, type: OPAQUE|WATERCOLOR, createdAt)
Paint(id, paletteId, name, colorArgb, tintingStrength=1.0, note?, sortOrder)
Settings: activePaletteId, maxComponents (1–5, default 3), ratioFormat: PARTS|PERCENT
Language: stored by AppCompat per-app locales (system / ru / en)
```

On first launch a starter palette "Basic Acrylic" (12 colors) is created — a regular
palette that can be edited or deleted.

## In-app updates from GitHub Releases

- "Check for updates" button in Settings (plus an optional silent check on launch, at most once a day)
- Request `GET https://api.github.com/repos/IWatman/ColorStruction/releases/latest`
- Compare the tag (`vX.Y.Z`) with `BuildConfig.VERSION_NAME` (semver comparison)
- If newer: dialog with version and release notes → download the `.apk` asset with
  `DownloadManager` (or OkHttp into app cache) with progress
- Install via `PackageInstaller` session (or `ACTION_VIEW` + `FileProvider`);
  needs the `REQUEST_INSTALL_PACKAGES` permission and the "install unknown apps" prompt
- **All releases must be signed with the same key**, otherwise Android rejects the update.
  The keystore lives outside the repo; in CI it comes from GitHub Secrets
- Errors (no network, API rate limit, no APK asset) are shown as a message, never a crash

## Language switching

- Settings → Language: System / Русский / English
- `AppCompatDelegate.setApplicationLocales(...)`, `res/xml/locales_config.xml`,
  `autoStoreLocales` in the manifest for Android < 13
- All UI strings live in `values/strings.xml` (English, default) and
  `values-ru/strings.xml` (Russian); no hardcoded strings in Compose code
- Adding a language = adding `values-xx/strings.xml` + one entry in `locales_config.xml`

## Screens

1. **Home / Mix**: target color preview, color picking buttons (wheel / HEX / from image),
   recipe list: component swatches, proportions, resulting color next to the target, ΔE badge.
2. **Color picker (wheel)**: hue ring + saturation/value square inside, markers on the
   ring and in the square. Below: HEX field, RGB sliders, recent colors.
3. **Eyedropper**: Photo Picker or a camera snapshot (`TakePicture` + FileProvider, no
   CAMERA permission) → image with zoom/pan, draggable crosshair, magnifier loupe above
   the finger, 1/3×3/5×5 px averaging.
4. **My paints**: palettes list → paints list, add / edit / delete / reorder; a paint's
   color is set with the same wheel, HEX or eyedropper (photo of a swatch).
5. **Settings**: max components (1–5), parts/percent, language, check for updates,
   app version, about the mixing model.

## Stages

Each stage ends with a build (`./gradlew assembleDebug`) and a test run.

### Stage 0. Project skeleton
- [x] Python `.venv` in the repo root (in `.gitignore`)
- [x] Install SDK platform 35, build-tools, emulator, system image; create an AVD
- [x] Gradle project (Kotlin DSL, version catalog), package `com.colorstruction`
- [x] Compose + Material 3 theme, navigation with placeholder screens
- [x] `values/strings.xml` (en) + `values-ru/strings.xml` from day one
- [x] `.gitignore`, English `README.md`, git init, push to GitHub
- [x] Debug APK builds and runs on the emulator

### Stage 1. Color math (`color/`, pure Kotlin, no Android)
- [x] sRGB ↔ linear RGB ↔ XYZ ↔ CIELAB, HSV ↔ RGB, HEX parse/format
- [x] CIEDE2000 + tests against reference pairs (Sharma et al.)
- [x] Spectral model + Kubelka–Munk mixing
- [x] Tests: blue + yellow → green; color + 0% of another = itself; white lightens

### Stage 2. Solver (`mixing/`)
- [x] Weight optimization for a combination, combination search, top-N
- [x] Rounding to parts + ΔE recomputation
- [x] Tests: target = a paint from the list → 1-component recipe, ΔE≈0;
      target = a known mix of 2 paints → finds it
- [x] Beam search for 4–5 components, preference for fewer paints
- [x] Watercolor mode ("water" component)
- [x] Benchmark: 30 paints, up to 5 components — < 1–2 s on an average phone

### Stage 3. Storage
- [x] Room: Palette, Paint, DAOs, migrations; repository
- [x] Starter palette "Basic Acrylic" on first launch
- [x] DataStore for settings

### Stage 4. Color wheel (component `ui/picker/ColorWheel`)
- [x] Canvas: hue ring (sweep gradient), S/V square (two gradients)
- [x] Gestures: tap/drag on ring and square, markers, `HsvColor` state
- [x] HEX input and RGB sliders synced with the wheel
- [x] Accessibility: contentDescription, large touch targets

### Stage 5. "My paints" screen
- [x] Palettes / paints lists, CRUD, active palette selection, palette type
- [x] Add-paint dialog: custom name, color (wheel/HEX/eyedropper), tinting strength

### Stage 6. Home screen + recipes
- [x] ViewModel: target → solver (debounce, cancellation) → recipe list
- [x] Recipe cards: swatches, parts/%, "result vs target", ΔE badge
- [x] Empty states: no paints, nothing close found

### Stage 7. Eyedropper
- [x] Photo Picker (no storage permissions) + camera snapshot via FileProvider
- [x] Downsampled decoding, zoom/pan, crosshair, loupe
- [x] Area averaging, result → target or paint color

### Stage 8. Settings, language, updates
- [x] Settings screen
- [x] Language switching (System / ru / en), verify every screen in both languages
- [x] Update checker: GitHub API, version comparison, download, install
- [x] Release signing key (outside the repo)
- [x] GitHub Secrets for CI signing
- [x] GitHub Actions: build + tests on push; on tag `v*` — signed release APK attached to a GitHub Release

### Stage 9. Polish
- [x] Dark theme (system / light / dark setting)
- [x] App icon, recent colors and favorite recipes (DB v2 migration verified 0.3.0 → 0.4.0)
- [ ] First public release `v1.0.0`
- [x] `v0.1.0` released; in-app update verified on the emulator (0.0.9 → 0.1.0, cancel and back-dismiss do not leave the progress stuck)

## Possible improvements (post-MVP)
- Paint calibration from a photo of a swatch (several concentrations with white)
- Palette export/import (JSON), share a recipe as an image
- Comparison with RGB mixing (learning mode)
- Volume-based amounts: "how many ml of each for 50 ml"
- More languages

## Code structure

```
app/src/main/java/com/colorstruction/
  color/      // color math, spectra, KM, ΔE       (unit tests)
  mixing/     // recipe solver                      (unit tests)
  data/       // Room, repositories, presets, DataStore
  update/     // GitHub Releases checker + installer
  ui/
    theme/
    picker/   // ColorWheel, HEX/RGB input
    eyedropper/
    paints/
    mix/      // home screen
    settings/ // settings, language, updates
  AppContainer.kt, MainActivity.kt
```
