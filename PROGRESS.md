# BragBuddy — Build Progress

The rolling handoff log for the phased build. **Start of each chat:** read the PRD
(`../PRD/BragBuddy-Build-Brief.md`, `../PRD/BragBuddy-System-Prompt.md`), the Design System
(`../Design System/`), this file, and the current code — that is the context, not chat history.
**End of each phase:** update this file, commit, leave the repo clean and runnable.

---

## Status: Phase 0 — Skeleton ✅ (built, not yet cloud-verified)

**Goal:** an Android Kotlin/Compose project that builds and runs to an empty Home screen, with the
data layer and the swappable AI seam in place.

### What was built
- **Project & toolchain** — single-module Gradle project (`:app`), Kotlin 2.0.21, AGP 8.7.3,
  Gradle 8.10.2, Compose BOM 2024.10.01, Hilt 2.52, KSP. Versions are the exact proven set used to
  ship the author's other native apps (Pause/Spends), so the cloud build is reproducible.
  `minSdk 26`, `targetSdk/compileSdk 35`. `applicationId`/`namespace` = `com.bragbuddy.app`.
- **Design system → code** — full token set transcribed into the Compose theme
  (`ui/theme/`): colours (light + dark, `Tokens`/`BragPalette`), the variable **pillar ramp**
  (`PillarRamp`, with Extra/Inbox reserved), typography scale (`BragTypography`), radii, spacing.
  Dynamic colour is OFF so the palette is identical everywhere. The full palette beyond Material's
  `ColorScheme` is provided via `LocalBragPalette` / `BragBuddyTheme.palette`.
- **Fonts** — the three design fonts are **bundled** as OFL variable fonts from google/fonts
  (`res/font/bricolage_grotesque.ttf`, `plus_jakarta_sans.ttf`, `jetbrains_mono.ttf`), wired with
  `FontVariation` per weight (needs API 26+, = our minSdk). No runtime download, works offline.
- **Local database (Room, v1)** — the durable **raw log**: `EntryEntity` (fields mirror the
  categorizer JSON + always-stored `rawTranscript` + status/source enums) and `ProjectEntity`,
  with DAOs, `Converters` (enums + `List<String>` as JSON), and `BragBuddyDatabase`. Provided via
  Hilt (`di/DatabaseModule`).
- **Swappable AI seam** — `AiProvider` interface (`categorize`, `generateSummary`, both return
  `Result<>` and must fail safe to Inbox) + typed request/result models matching the two system
  prompts. `StubAiProvider` (no network) echoes a transcript into the Inbox at confidence 0. Bound
  in `di/AiModule` — swapping to OpenRouter later is a one-line change here.
- **Default framework** — ships as static data (`data/framework/Framework.DEFAULT`): Performance
  Goals / Leadership & Behaviours / Learning & Growth, with `toPromptBlock()` for the prompts. The
  company name is never asked.
- **UI shell** — `MainActivity` (Hilt, splash, edge-to-edge) → `BragNavHost` (Home ↔ Settings).
  Home renders the header ("Your record · always ready / This year") + an empty state, and observes
  the entry count (exercises Room+Hilt+Flow end-to-end). Settings shows the active AI-provider
  label + app version.
- **Icon** — adaptive launcher icon built from the design-system briefcase (white on indigo),
  vector-only (fine because minSdk 26).
- **CI** — `.github/workflows/android-debug.yml`: on push to main / manual, runs
  `testDebugUnitTest` then `assembleDebug` and uploads `BragBuddy-debug.apk`. One unit test
  (`FrameworkTest`) establishes the test sourceset.

### Decisions & deviations (things a reviewer should know)
- **Stack = native Kotlin/Compose** (not Flutter, which the brief floated) — confirmed with the
  creator: matches his experience + all sibling apps, and this machine builds Android only in CI.
- **Build path = GitHub Actions only.** The local Android toolchain is intentionally absent on this
  machine (it OOMs on builds). Nothing here has been compiled locally — Phase 0 must be verified by
  a green cloud run before it's "done" (see Next steps).
- **Dark tokens for Extra/Inbox/positive softs** are pragmatic dark tints; the design files only
  specify light values for the reserved fills. Flagged for confirmation if a dark spec appears.
- **Room `exportSchema = false`** at v1 (no migrations yet). Turn on + commit a `schemas/` dir at
  the first migration so migration tests get a baseline (a known gap in a sibling app).
- **No dev-tooling plugins adopted** (OpenWolf/Ponytail/Superpowers). Kept Phase 0 lean; can pilot
  later and measure, per the brief.

### How it was tested
- Not yet run through the cloud build. Config mirrors the known-green Pause project 1:1 (same
  wrapper, same version catalog shape, same workflow), and sources were self-reviewed for imports.
- **Verification step owed:** push to GitHub and confirm the `Android Debug APK` workflow is green,
  then install `BragBuddy-debug.apk` and confirm it opens to the empty Home and Settings shows the
  stub provider label. Record the result here.

---

## Next — Phase 1: Capture loop
Daily reminder → tap → recording screen → on-device transcription (Android `SpeechRecognizer`) →
save the raw transcript as an `EntryEntity` (status RAW) → show it in a list. **Typed entry is
first-class**, not voice-only. No AI yet (the stub already routes to Inbox if we want to exercise
it). *Testable: speak or type on the phone; the entry saves and shows.*

**Creator setup needed before/within Phase 1:** none for capture itself (STT + mic are on-device).
The OpenRouter key walk-through comes in **Phase 2**, not now — don't wire the LLM early.

### Repo / hosting setup still owed (owner action)
- Create the GitHub repo (e.g. `github.com/aucksy/BragBuddy`) and push `main`. Confirm the debug
  workflow runs green and produces the APK artifact.
- Release signing (keystore + secrets) is only needed when we cut a signed Release later; the debug
  workflow needs no secrets.
