# BragBuddy — Build Progress

> **Entrypoint is `CONTEXT.md`** — read it first (it carries the invariants, the reading order, and
> the new-chat protocol). This file is the **live state** it points to: what's built and the exact
> next step. Keep this file current; it's what makes a fresh chat pick up without losing context.

The rolling handoff log for the phased build. **Start of each chat:** read `CONTEXT.md`, then the
PRD folder (`../PRD/BragBuddy-PRD.md`, `../PRD/BragBuddy-Build-Brief.md`,
`../PRD/BragBuddy-System-Prompt.md`), the Design System (`../Design System/`), this file, and the
current code — that is the context, not chat history.
**End of each phase:** update this file, commit, leave the repo clean and runnable.

---

## Status: v0.3.0 — cloud transcription ✅ built (pending CI) · v0.2.0 = Phase 1 done · v0.1.0 = Phase 0

### Capture-quality upgrade — cloud Whisper (v0.3.0, `versionCode 3`)
On-device STT quality was poor, so — per PRD **P0-11** (cloud transcription is a swappable upgrade) —
added a **swappable transcription engine**:
- **On-device** (free/offline Android STT, unchanged) OR **Cloud Whisper via Groq** (free-tier
  `whisper-large-v3`, OpenAI-compatible endpoint).
- Cloud path: `data/speech/AudioRecorder` (MediaRecorder → m4a) records with the live waveform → on
  stop, `data/speech/GroqTranscriber` (OkHttp multipart) transcribes (brief "Transcribing…" state)
  → save. Retry re-transcribes the same recording (no re-speaking on a transient network fail).
- **Key handling (per the brief's rule):** the Groq key is entered in **Settings → Transcription**
  and stored **on-device only** — never committed, never in the APK. On-device is the no-key/offline
  fallback (`AppSettings.cloudTranscription` = engine==CLOUD && key set).
- `di/NetworkModule` provides OkHttp; `TranscriptionEngine` + `groqApiKey` in `SettingsStore`.
- Provider chosen with the user (AskUserQuestion): **Groq** (free, no card) over OpenAI (paid).
- Compile-reviewed by an agent (1 real bug caught + fixed: a `LaunchedEffect` still calling the
  now-private `startListening()` — must be `startVoice()`).

---

## Phase 1 — Capture loop ✅ (v0.2.0, verified green)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.2.0/BragBuddy-v0.2.0.apk`. Two compile
errors were caught by CI + fixed (a static review agent missed both): `by
androidx.activity.viewModels()` FQN-call doesn't resolve as a delegate (import + `by viewModels()`);
`Modifier.padding(horizontal=, top=, bottom=)` mixes incompatible overloads (use start/end/top/bottom).

**Goal:** tap → **speak or type** → save the raw transcript → see it in a list. Fire-and-forget; no
AI yet. `versionCode 2` / `versionName 0.2.0`.

### What was built (Phase 1)
- **Capture surface** (`ui/capture/`: `CaptureActivity` [translucent] + `CaptureScreen` +
  `CaptureViewModel`): a bottom sheet over a scrim with a **Speak/Type toggle** that opens to the
  last-used mode. Voice = on-device `SpeechRecognizer` (`data/speech/SpeechToText`, prefers offline)
  with live partials, a timer, an RMS-driven waveform, and **stop = submit**; Type = a text field +
  submit arrow. Both **fire-and-forget**: save → brief "Saved" confirmation → dismiss. No-speech /
  STT-unavailable → retry or "Type instead".
- **Save path** (`data/entry/EntryRepository.capture`): stores the raw transcript immediately as an
  `EntryEntity` (status RAW, source VOICE/TEXT). Nothing is lost; AI fills the rest in Phase 2.
- **Home = entry list** (`ui/home/`): newest-first cards (transcript + relative time + source icon)
  or the empty state, hosted in **`ui/main/MainScaffold`** = the design's bottom tab bar with the
  raised **mic FAB** (the capture trigger). Summary / Framework / Inbox are placeholder tabs.
- **Daily reminder** (`reminder/ReminderScheduler` + `ReminderWorker`, `notification/Notifications`):
  a user-set daily notification (WorkManager 24h periodic, initial-delay to the chosen time) → tap
  opens capture. Channel created in `BragBuddyApp`. Enable toggle + time picker in **Settings**.
- **Permissions:** RECORD_AUDIO requested from the capture sheet (deny → Type fallback);
  POST_NOTIFICATIONS requested at launch (Android 13+).

### Deviations / flags (Phase 1)
- **Settings** is reached via a temporary **gear in the Home header** — the design shows "Summarise"
  there (a Phase 5 feature). Relocate when the summary surface lands.
- **Placeholder Summary/Framework/Inbox tabs** aren't in the design as "coming soon" screens —
  flagged; their phases replace them.
- STT forces `EXTRA_PREFER_OFFLINE`; a device without an offline pack will error → the sheet offers
  Type. Tune against the creator's real device (cloud STT is P0-11, later). No audio is retained yet,
  so a failed STT falls back only to retry/type.
- A review agent statically checked all 16 files for compile-breakers (none found); real
  verification is the green CI run below.

---

## Phase 0 — Skeleton ✅ (shipped v0.1.0, verified green in CI)

**Goal:** an Android Kotlin/Compose project that builds and runs to an empty Home screen, with the
data layer and the swappable AI seam in place.

**Verified:** repo live at **github.com/aucksy/bragbuddy**; signed release APK built + published by
CI at `releases/download/v0.1.0/BragBuddy-v0.1.0.apk`. Two build-time issues were found by the
cloud build and fixed (see "How it was tested"): an XML-comment `--`, and a variable-font
`ExperimentalTextApi` opt-in.

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
  in `di/AiModule` — swapping to OpenRouter later is a one-line change here. The two methods are
  deliberately separate so the two-models-by-task routing (free categorizer + fallback / stronger
  summary, remote-config slugs — PRD P0-12) drops in behind the interface in Phase 2.
- **Usage-metering hook** (`data/usage/UsageMeter` + `DataStoreUsageMeter`, bound in `di/UsageModule`)
  — the one forward-hook the PRD says to build "from the start" (P0-12 / §11 monetization): counts
  fresh **summary generations** (per-month + lifetime) and **cloud-transcription seconds**. No
  billing/tiers/UI — just the counts. Nothing increments it yet (summary = Phase 5, cloud STT =
  opt-in Phase 1/2); the seam exists so those phases call it instead of a later rewrite.
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

### Requirement update absorbed (2026-07-02 — formal `BragBuddy-PRD.md` added, Build Brief revised)
The full PRD and a revised brief landed after the initial scaffold. Reviewed against Phase 0:
- **Consistent already, no code change:** default framework (Perf Goals / Leadership & Behaviours /
  Learning & Growth), text-entry first-class (`EntrySource.TEXT`), ~0.6-confidence→Inbox, immutable
  raw log, swappable provider, on-device STT default.
- **Two models routed by task + remote-config slugs + fallback-on-429** (PRD P0-12): the seam
  already supports it (separate `categorize`/`generateSummary`); wiring is Phase 2. Documented on
  `AiProvider`.
- **Summary-generation guardrails** (view cached free; regenerate only when the rollup changed;
  cache per period+length; soft cap): Phase 5 repository concern — noted for that phase.
- **Metering "from the start"**: implemented now as the small `UsageMeter` hook (see above) — the
  only net-new architectural change this update required.
- **Monetization (PRD §11)** is post-MVP and explicitly not built; the metering hook is its only
  MVP footprint.

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
- **Built green in the cloud (GitHub Actions).** The tag-driven release workflow produced a signed
  APK: `github.com/aucksy/bragbuddy/releases/download/v0.1.0/BragBuddy-v0.1.0.apk`. Install and
  confirm it opens to the empty Home + Settings shows the stub AI-engine label.
- **Two issues the cloud build caught (nothing is compiled locally — this is the gate):**
  1. `values-night/colors.xml` had `--ab-*` inside an XML comment → `mergeDebugResources` failed
     (XML forbids `--` inside comments). **Lesson:** never put `--` (e.g. the design `--ab-*` token
     names) in Android res-XML comments; Kotlin `//` comments are fine.
  2. `Fonts.kt` used the variable-font `FontVariation` / `Font(variationSettings=)` API, which is
     an error-level `@RequiresOptIn` (`ExperimentalTextApi`) → `compileDebugKotlin` failed. Fixed
     with `@file:OptIn(ExperimentalTextApi::class)`.

---

## Next — Phase 2: AI categorization
Wire OpenRouter (free model) behind the existing `AiProvider` seam (replace `StubAiProvider`); run
the daily categorizer prompt on each RAW entry; parse the JSON and file each into project / goal
area / Inbox with all its fields (confidence < ~0.6 → Inbox; parse-fail → keep transcript, route to
Inbox). The default framework already ships as static data. Add **voice-refinement of the framework**
(speak how you're judged → editable pillars, no company name) + a light confirm. Meter nothing extra
here beyond the existing `UsageMeter` hook (summary metering is Phase 5). *Testable: an update gets
cleaned + categorized or lands in Inbox; framework refine-by-voice works.*

**Creator setup needed in Phase 2:** walk the creator through creating an **OpenRouter key** (stored
in local config, never committed/shipped); pick a free JSON-reliable model + a fallback. Two models
by task: free categorizer now / stronger summary in Phase 5.

### Repo / hosting (DONE)
- Repo live: **github.com/aucksy/bragbuddy** (`main` + tag `v0.1.0`). Two workflows: `android-debug`
  (push→main, artifact) and `android-release` (tag `v*` → signed APK + Release; debug fallback if
  secrets absent).
- Signing set up: keystore at `D:\Apps\BragBuddy\bragbuddy-release.keystore` (alias `bragbuddy`),
  secret values in `D:\Apps\BragBuddy\_signing_backup\GITHUB_SECRETS.txt`; 4 repo secrets added
  (`KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` are the ones Gradle actually uses).
- Ship a new build: bump `versionCode`/`versionName` in `app/build.gradle.kts`, commit, push a tag
  `vX.Y.Z` → the signed APK Release publishes at `releases/download/<tag>/BragBuddy-<tag>.apk`.
- Git identity is `simpleapps108@gmail.com`.
