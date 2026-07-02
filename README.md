# BragBuddy

A voice-first daily work logger that quietly keeps an always-ready, copy-paste **appraisal
document**. Speak (or type) ~20 seconds a day; BragBuddy cleans it into structured, categorized
bullets organised around your own appraisal framework, and — at review time — generates a crisp,
curated summary you can paste straight into your review.

- **Anonymous** — never asks for your company name.
- **Works instantly** — ships with a sensible default framework; no blank setup.
- **Local-first** — your record lives on your device; audio stays on the phone.

## Tech
Native **Android** (Kotlin + Jetpack Compose, Material 3), **Room** for the local record,
**Hilt** for DI, a **swappable AI provider** seam (stubbed now; OpenRouter free model later).
Design is driven strictly by the files in `../Design System/`.

## Building
This project is built in the **cloud via GitHub Actions** (see `.github/workflows/`). Push to
`main` (or run the *Android Debug APK* workflow manually) and download the `BragBuddy-debug.apk`
artifact from the run.

```
# Once a local Android SDK/JDK 17 is available (not on the primary dev machine):
./gradlew assembleDebug
```

## Where things are
- `app/src/main/java/com/bragbuddy/app/`
  - `ui/theme/` — the design system in code (colours, type, pillar ramp, spacing, radii)
  - `ui/` — screens (Home, Settings; more per phase)
  - `data/local/` — Room: the durable raw log
  - `data/ai/` — the swappable AI provider seam + typed prompt models
  - `data/framework/` — the default appraisal framework (static)
  - `di/` — Hilt modules

See **`PROGRESS.md`** for the phase-by-phase build log and what's next.
