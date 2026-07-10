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
  - `v0.14.0` — **Phase 6 · Google Drive backup + restore.** `data/drive/` (`DriveConfig` +
    `DriveBackupManager`): Google Sign-In (`drive.file`) + **Drive v3 REST over the OAuth token** (no
    SDK) → one restore `bragbuddy-backup.json` + the readable `BragBuddy record.txt` in a visible
    "BragBuddy" folder, **create-before-delete**, silent debounced auto-backup. `data/backup/`
    (`BackupCodec` pure org.json (de)serialise, unit-tested; `BackupRepository`): backs up entries +
    folders + framework + settings + cached summaries; **Groq key & audio NEVER backed up**; rollup is
    derived → rebuilt on restore. `ui/backup/` (Design §6, from Settings): health card, disabled
    "+ voice notes" (no audio retained — flagged), auto-backup toggle, Back up now / Restore from Drive
    / Export to device (SAF) / Restore from a file. **Restore-on-reinstall** (BragBuddyApp restore-if-
    empty → start observer; connect-with-empty-local auto-restores). Room stays **v3**. **OWNER GATE:**
    add a `com.bragbuddy.app` Android OAuth client + release SHA-1
    (`B8:B2:F2:86:05:BF:C8:44:94:98:E9:58:02:EA:55:74:9E:58:A4:D3`) to shared project `gmailapi-491903`
    — sign-in fails until then; local Export/Import works regardless. Pre-tag review: compile clean; 2
    HIGH (auto-backup could clobber a rich backup with near-empty local → never back up empty +
    auto-restore on connect; `importJson` non-atomic/off-mutex → run under `EntryProcessor.runRestore`
    mutex + Room `withTransaction`) + 1 MED (decode gate too loose → require the `version` marker) fixed
    + re-compile-checked.
  - `v0.13.0` — **Phase 5 · Running rollup + summary** (the point of the app). A maintained per-entry
    **rollup projection** (`data/rollup/`) kept in step **incrementally** under the `EntryProcessor`
    mutex on every mutation (file/edit/move/resolve/★/**delete**), + a launch **reconcile** self-heal
    that seeds it for the upgrade — so the summary reads a bounded rollup, never re-scanning the log.
    On-demand **summary** via the baked PART B prompt / `generateSummary` (strong slug
    `openai/gpt-oss-120b`, re-verified live) → the design's **Summary screen** (`ui/summary/`, replacing
    the placeholder): Generate sheet (configurable review-year windowed period + length picker),
    pillar-coloured doc, routine `×N` rolled-up insets, Pinned chip, promote/demote, **Regenerate only
    when the rollup changed** (cached per period+length; each fresh gen metered via `UsageMeter`), calm
    set-aside panel, Copy all/section. Review-year-start setting added. Room stays **v3** (rollup +
    cached summaries persist via DataStore). Pre-tag 5-dimension review: compile+integrity clean; 2 MED
    (sheet Regenerate bypassed the staleness guard → guard moved into `generate()`; `_generating`
    re-entrancy race → flag set synchronously before launch) + 1 NOTE (length-dependent highlight cap)
    fixed; fixes re-compile-checked.
  - `v0.12.0` — **Phase 4 · Edit, reassign, copy-out.** **Tap an entry → detail sheet**
    (`ui/entry/EntryDetailSheet.kt`, custom scrim sheet): raw transcript + cleaned bullet + chips, with
    **Edit / Move (reassign, no AI re-call) / ★ Standout toggle / Pin toggle / Delete** — from Home
    inline, the pillar view, and the single-folder screen (new `EntryBulletRow.onTap`). **Move** =
    `EntryProcessor.reassign` (works on a PROCESSED row; picker = goal-area folders only). **★/Pin** =
    `EntryDao.setExtra/setPinned` routed through the processor mutex; a manual ★ now survives an Edit.
    **Copy-out** = pure `ui/home/DocExport.kt` (clean plain text; unit-tested) → "Copy" in the Home
    header (whole doc) + each pillar/folder header (that section) → clipboard + toast. Room stays **v3**.
    Pre-tag review: 0 HIGH, 3 MED fixed (toggle/reassign lost-update race, ★-dropped-on-edit, Move
    picker filtered to goal-area folders).
  - `v0.11.0` — **v0.10.0 feedback batch (5).** **Reliable 9 PM reminder** (was drifting on WorkManager
    periodic work → exact `AlarmManager` alarm re-armed daily by a new `ReminderReceiver`, boot/clock
    reschedule, legacy work cancelled, `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`). **Add-impact merges
    into ONE deduped bullet** (`CategorizeRequest.combineSingle` → `COMBINE_MODE` prompt; add-number
    flows re-file single-output, never split/drop). **Consistent expand animation** (pillar view now
    uses `AnimatedVisibility` like Home). **Home folders expand INLINE** — fully actionable
    (edit/redo/delete + Add entry), last 10 + "See all N" → a **single-folder screen**
    (`pillar/{id}?folder=…`); shared `ui/common/EntryBulletRow` for Home + deep view *(deliberate
    deviation from "Home is an overview" — creator's call)*. **Performance Goals expanded by default**
    (seeded once). Room stays **v3**. Pre-tag review fixed a HIGH: `ReminderReceiver` needed
    `super.onReceive()` (Hilt injection) or it crashed on every fire.
  - `v0.10.0` — **UX batch (6).** **Collapsible sections** (default collapsed) on Home / pillar
    deep-view / Framework (selection mode force-expands the pillar view). **Framework editor rebuilt**:
    "Refine by voice" removed; compact collapsible category rows → **Edit opens a full sheet** with a
    **category summary** (voice or text) and **projects each with their own summary** (voice/text,
    "Add your performance metrics…"); pills moved inside; project summaries → `ProjectEntity.description`
    (+ AI context). Per-field cloud dictation (`fieldVoice`/`fieldTranscript`); type-only without a key.
    **"Extra" → "★ Standout"** (tap = explanation). **Impact nudge** always-on + **pulses**/stronger when
    no number + richer copy + inline **"See an example"** (weak→strong). Unique category/project names
    validated in the editor (Save gated) + DAO `IGNORE`/`runCatching` backstop against unique-index
    crashes. **Room stays v3**; no schema change.
  - `v0.15.0` — **Phase 7 · Reliability + retention polish (the LAST Android phase — Android is now
    feature-complete).** **Offline voice queue** (`EntryStatus.PENDING_AUDIO` + `entries.audioPath`,
    **Room v3→v4** additive `MIGRATION_3_4`): an offline capture auto-queues its clip
    (filesDir/`voice_queue`), online transport failures offer Save-for-later, and dismissing the sheet
    mid-transcription/post-failure **queues instead of deleting** (`AudioRecorder.stop()` now transfers
    file ownership). `data/net/ConnectivityMonitor` (validated-network StateFlow, advisory
    `callbackRegistered`) + `data/entry/OfflineRecovery` (launch/reconnect/key-change triggers): orphan
    sweep → drain (transcribe→RAW→categorizer) → auto-retry FAILED; **every outcome commits via
    `EntryProcessor.commitPendingAudio` (CAS under the processor mutex) and the audio is deleted only
    after the commit** (restore-race-proof); un-transcribable 4xx clips park visibly in the Inbox.
    Backup export excludes / restore preserves pending rows; `isLocalEmpty` ignores them. **Retention
    (Design §7,** pure `data/retention/RetentionPolicy`**)**: daily "nothing logged today" Home card
    (post-reminder-time), weekly catch-up sheet (Fri 17:00→Sun, once per ISO week, Settings opt-out),
    early-preview banner (5+ filed entries → Summary tab auto-generate). **Reliable reminders** (Home
    at-risk card + Settings screen, `reminder/ReliabilityCheck` + `ui/reliability/`): live-✓ steps for
    notifications (incl. a blocked reminder CHANNEL), exact alarms, battery exemption (direct dialog;
    manifest `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Play-restricted like USE_EXACT_ALARM), OEM
    auto-start deep links + user-confirmed switch, test-reminder; card gated to real risk
    (battery-only counts just on aggressive OEMs) with per-risk-signature dismissal. Calm offline copy
    in Inbox/Summary/capture. Pre-tag: 5-dimension adversarially-verified review (15 findings incl. 2
    HIGH data-loss races — all fixed) + a fix-diff re-review (4 edge defects fixed).
- **Next: iOS (deferred)** — the **Android v2 batch is COMPLETE**: **Phase A (image scan) v0.16.0**, **Phase B
  ("+" radial capture) v0.17.0**, **Phase B2a (framework editing) v0.18.0**, **Phase B2b (project rename-remap
  + categorizer prompt change + Home inline-edit Save) v0.19.0**, and **Phase C (onboarding + privacy/legal +
  audio-storage removal) v0.20.0** all shipped & verified green. **v0.20.1 = a whole-app end-to-end audit patch**
  (5 dimensions, adversarially reviewed): OS auto-backup no longer uploads entries to Google cloud (privacy claim
  made TRUE — cloud-backup excludes all domains, device-transfer kept); a live-capture crash on a malformed Groq
  key fixed; the offline-queue duplicate-on-crash + non-atomic transcript split + bullet-less-entries-missing-from-
  summary all fixed; and a **Discard-changes? confirmation on every text editor** (Back/scrim/✕) that also makes
  system Back close an open overlay instead of exiting the app. **v0.21.1 = Google Drive connect + recovery** (shipped v0.21.1; the v0.21.0 tag build was cancelled in GitHub's runner queue): a
  new onboarding **Recover from Drive** step (Welcome → Privacy → **Recover** → Role → Framework; a successful
  restore jumps straight to Home) + connecting Drive is now an **explicit restore CHOICE** (not silent
  auto-restore) that **never auto-backs-up an empty state or clobbers an existing backup** — "Not now" pauses
  auto-backup to preserve the previous backup; the launch-time silent auto-restore was removed. **Owner gate:
  Drive sign-in still fails until the com.bragbuddy.app Android OAuth client + release SHA-1 is added to
  gmailapi-491903** (degrades gracefully; untestable e2e until then). **B2 was RESHAPED mid-phase (creator,
  2026-07-07): NO AI reshapes the framework** — the user builds it by hand; `refineFramework` stays unused.
  "3 inputs" = **Type + Scan** on a detail box (the per-field **mic was removed**); **Scan** = OCR a
  job-description/review doc into the field (`AiProvider.readDocumentText`). Two-level model: a **category**
  detail feeds the **summary** ONLY, a **project** detail feeds the **daily categorizer**; **per-item Save**
  with confirm-before-save; **category & project rename → prompt-first** deterministic relabel. **v0.20.0
  (Phase C) shipped:** first-run **guided-but-skippable onboarding** (Welcome → Privacy hard-gate → Role →
  Framework), gated on new device-local `SettingsStore.onboardingComplete` + version-stamped
  `acceptedPrivacyVersion` (a stale version re-prompts privacy-only) via `RootGateViewModel`/`BragNavHost`;
  **step 3 reuses the real B2a Type+Scan editor** (embeds `FrameworkScreen` — NOT `refineFramework`); a shared
  `data/legal/PrivacyPolicy.kt` (VERSION=1) rendered as "Core Privacy Principles" cards
  (`ui/legal/PrivacyContent`) in **both** onboarding & a read-only Settings screen, claims **rewritten TRUE**
  for BragBuddy (Groq = #1 disclosure; encryption **phrased honestly**, no SQLCipher; the emphasised
  never-enter-company-info closing) + hosted-ready `docs/privacy.md`; **audio-storage remnants removed**
  ("+ Voice notes" backup row + inaccurate manifest comment; the offline-queue temp-clip path LEFT intact).
  Decisions locked via AskUserQuestion 2026-07-08 (all recommended). Pre-tag: compile=WILL COMPILE, logic
  review 1 HIGH (finish write cancelled by nav pop → re-onboard loop) + 1 MED (finish bar tappable through the
  editor sheet) + 1 LOW all fixed, fix-diff re-review=SHIP. **Room stays v4** (flags are DataStore; not
  backed up — terms acceptance is per-install). One phase per chat (full **▶ NEXT ROADMAP** at the top of
  `PROGRESS.md`). **iOS is DEFERRED** (creator's call — Android changes first; the
  stack/CI/signing research is parked in `PROGRESS.md`). Android Build-Brief Phases 0–7 are complete;
  everything under "Out of scope" stays parked. (`USE_EXACT_ALARM`/`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` are Play-restricted —
  pre-Play items; BragBuddy ships as a direct signed APK.)
  **Phase 6 owner gate still pending:** add a `com.bragbuddy.app` Android OAuth client + the release
  SHA-1 (`B8:B2:F2:86:05:BF:C8:44:94:98:E9:58:02:EA:55:74:9E:58:A4:D3`) to project `gmailapi-491903`.
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
