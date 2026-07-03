# BragBuddy — Session Context · START HERE

> **New chat? Point me at this file.** Reading this file, then the files it lists, fully rehydrates
> the project — no chat history needed. This is the single, stable entrypoint. The live, changing
> state lives in **`PROGRESS.md`** (one hop from here).

---

## 0. What this is (10-second version)
BragBuddy = a voice-or-text daily work logger that keeps an always-ready, copy-paste **appraisal
document**. Native **Android (Kotlin/Compose)**, local-first (Room), a **swappable AI provider**
seam, built in **vertical slices — one phase per chat**.
**Current status:** see the one-line snapshot in §4 (details in `PROGRESS.md`).

---

## 1. Rehydration set — read in this order
1. **This file** (`CONTEXT.md`) — invariants + protocol. Always read fully.
2. **`PROGRESS.md`** — the live state: what's built, decisions/deviations, and the **exact next
   step**. Always read fully. ← this is the one that changes every phase.
3. **`../PRD/`** — `BragBuddy-PRD.md`, `BragBuddy-Build-Brief.md`, `BragBuddy-System-Prompt.md`
   (the product spec, reasoning, and the two AI prompts).
4. **`../Design System/`** — `BragBuddy Design System.dc.html` (tokens) + `BragBuddy App v2.dc.html`
   (screens) + `screenshots/`. **Authoritative for all UI — build to match, never improvise.**
5. **The code** — `app/src/main/java/com/bragbuddy/app/` (skim `ui/theme/`, `data/`, `di/`).

**Efficiency rule (so sessions stay cheap and consistent):** always read §1–4 of this file and all of
`PROGRESS.md` (small, and they hold the critical stuff so nothing is lost). For the **big** PRD /
Design files, open only the section(s) relevant to the **current phase** (use search/targeted
reads), not the whole thing — the current phase is named in `PROGRESS.md`.

---

## 2. Firm invariants — carry these into every session, never violate
- **Never lose an entry.** The raw transcript is stored before any AI step; on AI/parse failure,
  keep the transcript and route to Inbox.
- **Never block or interrupt capture.** Fire-and-forget; unclear items wait in the **Inbox** (with
  1–2 suggested projects), never a mid-capture question. Force <~0.6 confidence to Inbox.
- **Privacy:** audio stays on-device; send only minimal text to the model. **Never ask for the
  company name** — anywhere, ever.
- **Design is authoritative.** The `Design System` files are the single source of truth for UI. If
  a screen/state isn't there, flag it — don't invent a look.
- **AI is a swappable seam.** Everything depends on the `AiProvider` interface, never a concrete
  provider. "Which model / whose key / where it runs" (baked-in → BYOK → proxy) must stay a config
  change. Two models routed by task (cheap categorizer / strong summary); slugs are remote-config.
- **Build model = one phase per chat** (see §3).
- **Build environment: cloud only.** This machine has **no local Android toolchain** (it OOMs on
  builds). Builds run via **GitHub Actions**. Do not build locally or re-download the SDK/JDK.

---

## 3. The new-chat protocol  ← keeps context intact and results consistent

**Why:** the build is deliberately sliced one-phase-per-chat. A fresh chat per phase keeps the
context small and the results consistent (rehydrating from these files, not a long transcript).

**When to start a fresh chat — the assistant must PROACTIVELY SUGGEST it, not silently continue:**
- at the **end of a phase** (the natural boundary), **or**
- when a session has grown long, spans multiple phases, or context feels heavy.
The assistant suggests it plainly (e.g. *"Phase X is done and committed — start a new chat pointed
at `CONTEXT.md` for Phase Y"*) and lets the user decide. It must **never** begin a new phase's work
in a way that risks losing context without first doing the handoff below.

**No-context-loss handoff — do this BEFORE ending a chat / at every phase boundary:**
1. **Update `PROGRESS.md`:** what was built, any decisions or deviations, how it was tested, and the
   **exact next phase** plus any setup the owner must do first (keys, repo, secrets).
2. **Commit everything;** leave the repo clean and runnable.
3. **Tell the user** the phase is done and to open a new chat pointed at this file.

**Starting the new chat:** the user points the fresh session at `CONTEXT.md`; it reads the
rehydration set (§1) and continues deterministically from the "next step" in `PROGRESS.md`.

> If any of the above ever conflicts with a firm invariant in §2, the invariant wins.

---

## 4. Status snapshot (keep short; full detail in `PROGRESS.md`)
- **Repo:** github.com/aucksy/bragbuddy — live, signed tag-driven releases (push `vX.Y.Z` → APK at
  `releases/download/<tag>/BragBuddy-<tag>.apk`). Git identity: `simpleapps108@gmail.com`.
- **Shipped & verified green:**
  - `v0.1.0` — Phase 0 skeleton.
  - `v0.2.0` — Phase 1 capture loop: speak/type → save raw transcript → Home list + daily reminder.
  - `v0.3.0` — cloud Whisper transcription via **Groq** (Settings → Transcription; key on-device
    only), with on-device STT as the fallback. Room DB still v1.
  - `v0.4.0`→`v0.4.1` — Phase 2 **AI categorization** behind the `AiProvider` seam (`StubAiProvider`
    retired). Daily categorizer runs on each RAW entry → cleaned bullet filed to a goal area, or Inbox
    (conf < 0.6 / "Inbox" placement / AI-fail → Inbox, transcript kept — never lost); two models +
    fallback, slugs in `data/ai/AiConfig`. **AI runs on Groq (`GroqAiProvider`, `llama-3.3-70b-
    versatile`) reusing the SINGLE on-device Groq key** — one key for AI + Cloud Whisper (Settings →
    AI brain (Groq)); no OpenRouter needed. Seam stays swappable (summary → paid provider at launch).
  - `v0.5.0` — post-testing **cleanup batch**: "pillar"→**"category"** (user-facing); **voice capture
    review-before-Add** (Add/Re-record/Cancel; typed instant); Home entry **⋮ Edit/Redo/Delete**;
    **editable category descriptions**; **refine-by-voice via Groq cloud** + instruction-aware prompt +
    distinct sheet; status-bar insets + tab-label wrap fixed. 8 review bugs fixed pre-tag. Room DB v1.
  - `v0.6.0` — **Job role** (`SettingsStore.jobRole`, device-local, never company name; Settings card +
    first-run Home prompt; `ui/role/RoleInput` type-or-speak) injected into **both prompts** +
    framework-refine (core vs. beyond-scope). **Project folders** on Home (create + tap → capture into
    it; `ProjectRepository`; managed in Settings) + **deterministic anchoring** (`EntryEntity.anchor
    Project`, honoured in `EntryProcessor` for the row + siblings). **Room DB now v2** (additive
    `ALTER TABLE entries ADD COLUMN anchorProject` migration in `BragBuddyDatabase.MIGRATION_1_2`;
    exportSchema still off). Edit/redo now re-files a SINGLE entry (no split; fixed a data-loss trap).
  - `v0.7.0` — **Static impact coaching (NO AI)**: `data/impact/ImpactCheck.hasMeasurable()` (local
    regex — digits / `% ₹ $ € £` / number words) gates a persistent greyed capture hint + a post-save,
    **in-sheet** "Add a measurable result?" nudge (`SavedNudgeSheet`; entry saved first, never blocks;
    Add number = typed-only, appends + re-files via the existing categorizer). Has-a-number captures
    dismiss instantly. Free/offline for all tiers; no LLM call added.
  - `v0.8.0` — **Phase 3 · Living document + Inbox resolve.** Home is now the **structured document**
    (`ui/home/HomeDoc.kt` pure builder → goal/growth pillars with **project cards**, behaviour pillars
    gathering **evidence**, RAW "Filing…" strip, Inbox peek). **Deep pillar view** one tap in
    (`ui/pillar/`, nav `pillar/{pillarId}`): blurb → projects → dated bullets ("also evidences" +
    Extra) + add-entry(anchored)/add-project/add-detail + per-entry **edit/redo/delete** (moved off
    Home). **Inbox tap-to-resolve** (`EntryProcessor.resolve`, no AI re-call): suggested chip / any
    folder / Outside project → PROCESSED. A **catch-all "Uncategorized"** section guarantees a
    renamed/refined framework can never hide filed entries (invariant). v0.6.0 horizontal folder row
    removed (projects live in the doc). **Room stays v2**; no schema change.
  - `v0.9.0` — **Cleanup batch (5 items).** **Multi-select + bulk delete** in the deep pillar view +
    Inbox (`deleteByIds`/`deleteMany`). **On-device transcription removed** (voice = Groq cloud only;
    no key → "add key / type instead"; role field now type-only; `SpeechToText` deleted). **Sub-folders
    under ANY framework category** (`ProjectEntity.goalArea` = category name) — managed in the Framework
    editor + Home, **kept in sync** (one `projects` table); category rename/delete cascades; **Room
    v2→v3** composite unique index `(name, goalArea)` (`MIGRATION_2_3`). Categorizer framework block
    enriched with every category's **sub-folder names as AI context** (placement stays goal-area only).
    **Number nudge rebuilt at the transcript** — record a 2nd clip (or type) → appended → AI cleans the
    combined text (replaced the post-save nudge that silently skipped on spoken number-words).
- **Next: Phase 4 — Edit, reassign, copy-out.** Tap an entry → raw + cleaned; edit/move/toggle
  Extra/pin/delete; **copy a section or the whole doc as clean text** for Word/Docs. Per-entry
  edit/redo/delete + Inbox resolve already exist; `isPinned`/`isExtra` fields exist (no toggle UI
  yet); `buildHomeDoc`'s goal-area shaping makes a copy-out serializer straightforward.
- **Build reality:** cloud-only (no local Android toolchain). Nothing compiles locally → budget ~2 CI
  round-trips/phase; **the compiler is the only gate** (a static review agent has missed real
  errors). Fix from the CI log via the **public** GitHub API (unauthenticated is enough for run status
  + release assets; `git credential fill` for token reuse is blocked by the sandbox classifier).

---

## 5. Where durable state lives
- **Authoritative & user-owned:** this repo — `CONTEXT.md` (stable) + `PROGRESS.md` (live). Edit
  these; they are the source of truth for project state.
- The assistant may also keep a private cross-session memory as a convenience mirror, but **the repo
  files win** if they ever disagree.
