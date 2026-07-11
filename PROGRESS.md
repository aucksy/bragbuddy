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

## ▶ NEXT ROADMAP — Subscription launch + AI-magic (agreed 2026-07-11)

> **This is the live "what's next."** Direction locked with the creator 2026-07-11 (AskUserQuestion):
> **managed AI key for all at launch** (BYOK stays ONLY as the test-mode setup until launch-readiness,
> then demotes to a hidden Advanced option) · **₹149/mo · ₹999/yr India-first, single Pro tier** ·
> **paid hook = the Review Pack** · **iOS only after Android proves conversion**. Do NOT re-litigate.
>
> **The three governing docs (read before any phase):**
> - `docs/IMPLEMENTATION-PLAN.md` — **the deep spec, phase by phase, incl. the FINAL verbatim prompt
>   texts** (authored by Claude Fable 5 for execution by Claude Opus 4.8 — paste the prompts, don't
>   re-write them).
> - `docs/PRODUCT-ASSESSMENT.md` — business/UX assessment + subscription model + M-phase rationale.
> - `docs/AI-SYSTEM-ASSESSMENT.md` — AI-brain assessment (categorizer/summary/impact-coach) + efficiency.
>
> **Phase order:** **AI-0** eval harness + golden set (repo-only, no tag; owner gate: add `GROQ_API_KEY`
> repo secret) → **AI-1** v0.26.0 categorizer magic + efficiency (prompt restructure for Groq prefix
> caching ≈40-45% cheaper, rubrics, 4 baked examples, routine-label reuse, output validator, Whisper
> vocabulary prompt, description caps) → **AI-2** v0.27.0 summary fixes + **impact-coach-at-capture (the
> USP move)** → **M1** v0.28.0 managed AI proxy → **M2** first-session/polish → **M3** Play Store +
> Billing + caps → **M4** Review Pack → **M5** capture speed → **M6** retention/season → **M7** review
> cycles → **M8** iOS. One phase per chat, fresh chat per phase, same ritual as ever — **plus a new
> standing rule: any prompt/model change ships EVAL-GATED** (thresholds met and ≥ committed baseline).
>
> **Phase AI-0 is COMPLETE (2026-07-11)** — harness built AND the **baseline is committed on main**
> (`eval/report-baseline.{md,json}`, commit `3a07872`). All three owner gates are done: `GROQ_API_KEY`
> secret added; the real record imported + hand-verified (golden set **36 → 47 cases**, commit
> `b1eff14`); baseline run green. **The eval is now tag-driven like every build** (commit `f6e2d94`):
> push `eval-baseline-*` → run + commit baseline; push `eval-run-*` → gate check. No manual button.
> **Baseline gate results (the "before" AI-1 must beat): 3 gates RED — routineReuse 57.1%,
> coachPass 75.0%, summaryChecks 88.2%; plus metricPreserved 20.0% (ungated, dire).** Full numbers +
> the AI-1 target list are in `## Status: Phase AI-0` below. **Exact next step: Phase AI-1 (v0.26.0)
> in a fresh chat** — see the handoff prompt the assistant provides.

---

## ✅ COMPLETED ROADMAP — Android v2 (agreed 2026-07-07): image scan → "+" radial capture → onboarding + privacy · THEN iOS

> This is the live "what's next." Vertical-slice phases, **one per chat**, same rhythm as Phases 0–7.
> iOS is **deferred** (research parked at the end of this section). Start each phase in a fresh chat
> pointed at `CONTEXT.md`. **Exact next step:** the Android v2 batch is **COMPLETE** — **Phase C
> (onboarding wizard + Privacy/legal + audio-storage removal) shipped v0.20.0.** The remaining work is
> **iOS** (deferred; scoping parked at the end of this section) — resume in a fresh chat once the creator
> green-lights Apple enrollment. Phase A · image scan **v0.16.0**; Phase B · "+" radial capture
> **v0.17.0**; **Phase B2a · framework editing shipped v0.18.0**; **Phase B2b · project rename-remap +
> categorizer prompt + Home inline-edit Save shipped v0.19.0**; **Phase C · onboarding + privacy shipped
> v0.20.0** — all verified green.

**⚠️ SCOPE RESHAPE (creator, 2026-07-07, mid-B2):** the written B2 scope ("3-input add/update via the
`refineFramework` AI seam") was **corrected by the creator** — they do **NOT** want AI to interpret/rewrite
the framework. The framework is exactly what the user builds by hand; the AI never reshapes it. So:
- **`refineFramework` is NOT used** (the seam stays built-but-unused). "3 inputs" = how you fill a **detail
  text box**: **Type + Scan** (the per-field **mic was removed**). **Scan** = OCR a job-description /
  review-criteria **document** into the field (append, editable, deletes nothing) via the vision pipeline +
  a new doc-OCR prompt (`AiProvider.readDocumentText`).
- **Two-level model, feeding different AI steps:** a **CATEGORY** detail feeds the **summary**; a **PROJECT**
  detail feeds the **daily categorizer** (future filing). Editing either shows a **confirm-before-save** that
  names the effect. **Per-item Save** (each item its own Save; replaces the batched top-bar Save).
- **Category rename → prompt-me-first** deterministic relabel of filed records (creator's pick over auto/AI).
- **Daily categorizer uses category NAMES + project details only** (category detail blurbs dropped; behaviours
  still tagged) — **shipped v0.19.0** (pure `FrameworkPrompt.categorizerBlock`; the SUMMARY still gets the
  blurbs via `Framework.toPromptBlock()`, so editing a category detail affects only future summaries).
- **SPLIT into two releases** (creator's call): **v0.18.0** = framework/project editing (done); **v0.19.0** =
  project rename-remap (3-option) + the categorizer prompt change + Home inline-edit Save — **✅ DONE**.

**Why this batch:** the creator wants five Android changes before any iOS work. Locked via AskUserQuestion
(2026-07-07, all "recommended"):
- **Image-scan flow = extract → editable review → file** (mirrors the voice review-before-Add; you verify
  the read before anything saves).
- **Terms & Privacy = hard gate, accept once** (version-stamped; re-prompt only on a material change).
- **Onboarding = guided but skippable** (privacy → role → framework/projects via any of the 3 inputs →
  Home; Skip at any step keeps `Framework.DEFAULT`).
- **Proceed = lock roadmap, build one phase per chat** (this section is that roadmap).
- Defaults (correctable): image source = **camera + gallery** (gallery lets you scan a screenshot of
  Slack/email praise); **1 image per capture** for now.

**Groq VISION (verified live 2026-07-07):** use **`qwen/qwen3.6-27b`** — production, multimodal, 131K,
JSON mode. `meta-llama/llama-4-scout-17b-16e-instruct` is **Preview + deprecating — do NOT build on it.**
Images = base64 `data:` URL, **4 MB cap** (downscale/compress first), ≤5/req, 33 MP cap. Fits the existing
`GroqAiProvider.callChat` pattern + the `AiConfig` slug list. **RE-VERIFY the slug live before the Phase A
tag** (standing rule — slugs move). Sources: console.groq.com/docs/vision · /docs/models · /docs/deprecations.

### Phase A — Image scanning ✅ SHIPPED v0.16.0 (the 3rd input; foundation for B + onboarding)
Add a Groq **vision** path behind the existing `AiProvider` seam; camera/gallery → downscale < 4 MB →
base64 → `qwen/qwen3.6-27b` (JSON) → **editable extracted-text review** (reuse the voice REVIEW stage
pattern) → Add → the normal `EntryProcessor` categorizer.
- `AiProvider` gains a vision method (+ Groq/Stub impls); `AiConfig.visionModel`. New `CaptureMode.IMAGE`
  (`SettingsStore.kt:21`) + an image phase flow paralleling `VoicePhase`; `CaptureScreen` `when(mode)`
  branch (`:155`). `EntrySource.IMAGE` — **stored by `.name` → NO Room migration** (Room stays **v4**).
- Permissions: **CAMERA** + `PickVisualMedia`/`TakePicture` (`ActivityResultContracts`, mirror the existing
  `RequestPermission` at `CaptureActivity.kt:55` + the SAF launchers in `BackupScreen.kt:81-85`). Only
  `RECORD_AUDIO` exists today (`AndroidManifest.xml:10`).
- **Invariants hold for free** (same pipeline): fire-and-forget, Inbox-fallback on low confidence / parse
  fail, transcript/derived-text always kept. The **image is sent to Groq, NEVER stored** (privacy —
  consistent with audio).
- **Flags:** `CaptureMode` is also threaded through **backup** (`BackupCodec`/`BackupRepository`/`changeSignal`),
  the Speak/Type toggle, and the SPEAK-only auto-start `LaunchedEffect` (`CaptureActivity.kt:84-88`) — all
  assume 2 modes; extend carefully (unknown-value `runCatching{valueOf}` fallbacks already exist). The image
  review UI is **NOT in the Design System** — build from tokens, propose the look for approval.
- **Testable:** scan a photo/screenshot on-device → extract → edit → files (or Inbox); no-key → clear "add
  key / type instead" state; oversized image compresses under 4 MB; offline → fails safe (queue or Inbox).

### Phase B — "+" radial capture menu + default input method + notification routing ✅ SHIPPED v0.17.0
Replace the mic icon (4 spots: FAB `MainScaffold.kt:171`, daily-nudge `HomeScreen.kt:722`, capture toggle
`CaptureScreen.kt:231`, number-nudge `CaptureScreen.kt:530,532`) with **"+"**; tapping it opens a
**3-option radial (Voice / Text / Image) with a slick circular animation** on Home, and the appropriate
equivalent elsewhere. Introduce **ONE shared capture launcher** (kills the 5-site duplication —
`HomeScreen.kt:151-160`, `PillarDetailScreen.kt:145-154`, `MainScaffold.kt:114,121`, `Notifications.kt:30`)
taking a new **`EXTRA_START_MODE`**.
- Settings **"Default capture method"** = Voice / Text / Image / **Ask each time** (new DataStore pref;
  reuse the `CaptureMode` `runCatching{valueOf}.getOrDefault` pattern). Notification tap → starts that
  method (auto-record / keyboard / camera); **if "Ask" → opens the radial** (new `CaptureActivity` entry
  state). Single notification intent to change (`Notifications.kt:30`, `FLAG_UPDATE_CURRENT` already set).
- **Flags:** the radial is **NOT in the Design System** — propose the look for approval; **preserve FAB
  symmetry/alignment** and every existing mic-launch surface. In-context "Add entry to <project>" AddRows
  become "+" and open the same 3-choice, anchored. If the default should survive restore, add
  `defaultCaptureMethod` to the backup mirror (`BackupCodec`/`BackupRepository`/`changeSignal` — copy how
  `lastCaptureMode` is threaded).
- **Testable:** each default routes correctly from BOTH the FAB and the notification; "Ask" opens the
  radial; animation is smooth; every old mic-launch surface still works (regression).

### Phase B2 — Framework editing: 3-input add/update + Reset + rename-remap ✅ SHIPPED (v0.18.0 + v0.19.0)
> Requested by the creator right after Phase B (understanding-questions about how the AI uses the framework
> led here). Build it in a **fresh chat BEFORE Phase C** — it's independently valuable and, unlike Phase C,
> **NOT blocked** on the pending privacy attachment; **Phase C's onboarding step 3 then reuses this exact
> framework-input capability.** Ships as **v0.18.0**.

**Confirmed to the creator (from the code — carry these facts):** the framework is presented **first** in
both prompts (categorizer `CONTEXT` before the transcript; summary `CONTEXT` before the rollup —
`AiPrompts.kt`) and is **re-injected on EVERY categorize + summary call** (`EntryProcessor.kt:297` reads
`frameworkStore.framework.first()` per entry; the model is stateless). **Updating the framework affects only
FUTURE filing** — already-filed entries are NOT re-run; any whose `goalCategory`/`demonstrates` no longer
match a current pillar surface in the **"Uncategorized"** catch-all (`HomeDoc.kt:126-133`), never lost.

**Scope (locked via AskUserQuestion 2026-07-07):**
- **3-input framework add/update** in the Framework editor — today it's **type + per-field VOICE only**
  (`FrameworkViewModel.fieldVoice`/`fieldTranscript`, `CategoryEditSheet`). Add a natural-language **Text**
  refine and a **Scan** refine, both via the **built-but-unused `refineFramework` seam**
  (`AiProvider.refineFramework` + `AiPrompts.framework` PART C already work end-to-end; **NO UI calls them
  yet** — grep-confirmed). **Scan** = reuse the Phase A vision path (`AiProvider.extractFromImage` +
  `data/image/ImageInput`) on a job-description / review-criteria doc → feed the extracted text into
  `refineFramework` → apply to the current framework via `FrameworkStore.save`. Build the input so **Phase
  C onboarding step 3 reuses it verbatim.**
- **Reset framework** in Settings — **`FrameworkStore.reset()` ALREADY EXISTS** (→ `Framework.DEFAULT`) but
  is wired to **no UI** (grep-confirmed). Add a Settings action + confirm dialog (warn: filed entries keep
  their labels → they surface under Uncategorized; reset doesn't delete entries).
- **Deterministic rename-remap** (creator's pick over keep-Uncategorized-only / AI-bulk-refile): when a
  category is **renamed** in the editor, offer to remap **old-name → new-name** across filed entries — **NO
  AI, instant, reversible**: `UPDATE entries SET goalCategory = :new WHERE goalCategory = :old` (new DAO
  query) **AND** rewrite `demonstrates` lists that contain the old name (Kotlin load→rewrite→save, since
  it's a JSON list column — SQL can't touch it), **all under the `EntryProcessor` mutex**, then
  `reconcileRollup()` so the summary follows. Category rename **already cascades to sub-folders**
  (`ProjectEntity.goalArea`, v0.9.0 `renameCategory`) — this extends the cascade to **entries + rollup**.
  Removes/adds still fall to Uncategorized (safe).
- **Testable:** type/scan a framework description → applies (or fails safe, keeping the old framework);
  Reset → back to default and old entries appear under Uncategorized; rename a category → prompt → filed
  entries + the rollup/summary follow the new name; no-key → the AI-backed Text/Scan paths degrade to the
  existing manual editing.

### Phase C — Onboarding wizard + Privacy/legal (+ remove the audio-storage option) ✅ SHIPPED v0.20.0
> **DONE — full detail in the `## Status: v0.20.0` section below.** Reshape-corrected: onboarding step 3
> reuses the **B2a Type + Scan** framework editor (`FrameworkScreen`), **NOT** `refineFramework` (which
> stays unused). Decisions locked via AskUserQuestion (2026-07-08, all recommended): **one release**;
> encryption **phrased honestly** (no SQLCipher); **India / simpleapps108@gmail.com**; onboarding =
> **Welcome → Privacy → Role → Framework**. The plan text below is kept as the historical spec.

First-run **guided-but-skippable** wizard (branch `BragNavHost` start destination `:20` on a new
`SettingsStore.onboardingComplete` flag): **(1) Privacy & Terms — hard gate, version-stamped acceptance**
→ **(2) role** → **(3) framework/projects setup via Voice/Text/Image** (reuse the **built-but-unused**
`refineFramework` seam [`AiProvider.kt:37`] + `FrameworkStore.save`; image = scan a job-description /
review-criteria doc) → Home. Skip keeps `Framework.DEFAULT`. *(SHIPPED as Type+Scan per the reshape — see
below.)*
- **Privacy screen** also in Settings (nav Card like Drive/Reliability, `SettingsScreen.kt:313-328`
  pattern). Research-backed content: local-only storage / no account; **explicit third-party AI — name
  Groq** (entry text + scanned images are sent there); **no audio/image retention** (transcribed/read then
  discarded); AI-accuracy / no-warranty; limitation of liability; deletion control; on-device BYOK key;
  age; governing law + updates + contact (default **India / simpleapps108@gmail.com** — confirm); and the
  **emphasised closing point: the user is solely responsible for what they disclose — strongly recommend
  never entering company name, client names, or confidential/employer info.** Draft a hosted-ready privacy
  `.md` in the repo (sibling-app pattern). **NOT legal advice — recommend a lawyer review before public
  launch.** **Creator's privacy reference RECEIVED (2026-07-07)** — a **"Core Privacy Principles"** card layout
  (rounded grey cards, bold title + plain body): *Your Audio Is Never Stored · You Control Everything ·
  Secured Encryption, Throughout · No Ads, No Tracking, No Selling.* **Adopt the card STYLE + tone, but
  the reference is from a server/account/always-listening app — several claims are FALSE for BragBuddy and
  MUST be rewritten, not copied (a false privacy claim is a legal risk):** (1) *Audio never stored* — KEEP,
  adapt: audio + scanned **images are sent to Groq to process**, then not retained by us / never backed up;
  offline clips sit briefly on-device then delete after transcription. (2) *You control everything* —
  rewrite: **no account, no servers, no "our backups"** (data = your device + optional **your own Google
  Drive**); **DROP "pause recording"** (tap-to-capture, not always-listening). (3) *Encryption at rest,
  highest grade* — 🚩 **can't truthfully claim app-level at-rest encryption today** (Android OS/sandbox only,
  no SQLCipher); **Phase C decision: add on-device encryption (SQLCipher/encrypted DataStore) vs. phrase
  honestly** (in-transit to Groq = HTTPS, true). (4) *No ads/tracking/selling* — KEEP (true). **ADD what
  the reference omits (BragBuddy-critical):** the **third-party AI (Groq) disclosure** (entry text + images
  sent to Groq — the #1 disclosure) and the **closing "you are responsible for what you disclose; never
  enter company/client/confidential info."** Screenshot lives in the creator's chat 2026-07-07.
- **Remove audio-storage everywhere:** delete the disabled **"+ Voice notes"** `OptionRow`
  (`BackupScreen.kt:125-133`) + its KDoc (`:55-60`); correct the **inaccurate manifest comment**
  (`AndroidManifest.xml:5-9`) claiming audio stays on-device (it goes to Groq Whisper). **LEAVE** the
  genuine offline-queue temp-clip path (`EntryEntity.audioPath`/`PENDING_AUDIO`/`OfflineRecovery`) — that's
  the never-lose-a-take safety net, not stored notes.
- **Testable:** fresh install → must accept terms before Home → guided setup via all 3 inputs → Skip works
  → re-accept only on a version bump; Settings → Privacy readable; no "voice notes" language anywhere.

**Per-phase ritual (unchanged):** clarify via AskUserQuestion → build → **compile review + adversarial
logic review** → fix → **tag the signed release** → thorough on-device test (edge / negative / regression —
act like an expert tester) → update PROGRESS + commit → suggest a fresh chat for the next phase. Cloud-only
build; **the CI compiler is the only gate.** Guard UI symmetry/alignment. Reuse the app's custom-scrim
sheet pattern (never a Material `ModalBottomSheet` — the veto-freeze trap).

### iOS — DEFERRED (2026-07-07); scoping parked so it isn't re-done
Creator: "not building iOS yet — Android changes first"; Apple enrollment "not yet". **No stack decision
was made.** When it resumes, this is the pre-done research:
- **Stack options** (my lean was **Native Swift/SwiftUI**, *not chosen*): port the reviewed Kotlin as the
  executable spec (prompts verbatim, unit tests → XCTest); vs **Kotlin Multiplatform** (share the brain,
  but refactor the shipped Android app incl. **Room v4** — regression risk for zero Android gain + heavier
  Kotlin/Native CI). Compose-MP and Flutter both **advised against** (non-native feel / the JS-CI pain the
  PRD explicitly rejected).
- **Portability audit:** the whole brain is pure Kotlin or **DI-only-coupled** — `AiProvider` + prompts +
  `AiJson`, `RollupAggregator` + `ReviewPeriod`, `RetentionPolicy`, `ImpactCheck`, `BackupCodec`,
  `Framework`, and **`EntryProcessor`** (coupled only by `javax.inject`). Android-only = UI (Compose), Room,
  DataStore, audio, connectivity, alarms/notifications, Drive sign-in, Hilt. Transcription is already a
  plain Groq REST call (portable). iOS reliability is *lighter* (no OEM battery wizard).
- **Windows→iOS CI reality (researched):** Xcode is Mac-only → **cloud macOS CI is the ONLY path** (fits
  "CI is the only gate"). GitHub-hosted **standard macOS runners are FREE on public repos** (bragbuddy is
  public). Signing = App Store Connect **API key (.p8)** + fastlane match, secrets-as-base64 (same model as
  the Android keystore) — set up entirely from Windows. Distribution = **TestFlight** (no Mac/cable).
- **Owner gates (iOS):** Apple Developer Program **$99/yr**, an App Store Connect API key + app record, and
  an **iOS OAuth client** on `gmailapi-491903` for Drive (parallel to the pending Android one).
- **Capture parity:** no iOS overlay (Apple forbids) — the notification opens the app straight into a
  minimal auto-recording screen + App Intents (Siri/Shortcuts/Action Button/Lock-Screen). Blessed in the
  PRD/Brief.

---

## Status: Phase AI-0 — Eval harness + golden set ✅ COMPLETE (repo-only · NO app change · NO release tag; harness mock-tested end-to-end + adversarially reviewed; BASELINE COMMITTED on main `3a07872`)

> **The first phase of the subscription-launch roadmap** (`docs/IMPLEMENTATION-PLAN.md` · Phase AI-0).
> From now on **any prompt/model change ships eval-gated** the way code ships compile-gated. No app
> code changed (the only app-module addition is a unit test); versionCode untouched; no tag.

### What was built
1. **`eval/prompts/*.txt`** — the CURRENT `AiPrompts` templates dumped verbatim (categorizer +
   combine-mode appendix + summary + impact-coach), extracted mechanically from the Kotlin source so
   they are byte-accurate. These are what the eval measures — the AI-1 baseline is the *before*.
2. **`PromptSyncTest`** (`app/src/test/…/PromptSyncTest.kt`) — asserts each `AiPrompts` builder output
   equals the corresponding txt file (sentinel-placeholder trick maps each template onto itself;
   CRLF/trailing-newline normalized). Runs in BOTH Android workflows' `testDebugUnitTest` step →
   **prompt drift = red CI**. Change a prompt → change the Kotlin AND the txt in one commit.
3. **`eval/run.mjs`** — Node 20, zero-dep runner. Mirrors the app exactly (marked `APP-MIRROR`):
   models + endpoint parsed live out of `AiConfig.kt`; context blocks rebuilt the way
   `FrameworkPrompt.categorizerBlock` / `EntryProcessor.prepare` / `Framework.toPromptBlock` build
   them; `temperature 0.2`, JSON mode, system+user message shape, primary→fallback on transport OR
   parse failure (`completeAndParse`), first-`{`-to-last-`}` extraction (`AiJson`), Inbox parking =
   `project/goalCategory=="Inbox" || confidence<0.6`, never when anchored (`statusFor`). Eval-only
   deviation (documented): 429/5xx retries with backoff on the same model first, so a rate-limited
   run measures the model, not the limiter. Scores every IMPLEMENTATION-PLAN metric, applies the
   AI-1 validator's snapping rules scorer-side, writes `eval/report.md` + `report.json` (machine-
   comparable), supports `--baseline` (AI-1+ must be ≥ baseline everywhere), `--dry-run`, `--only`,
   `--limit`, `--no-gate`; non-zero exit on gate failure. `GROQ_BASE_URL` override doubles as the
   M1-proxy test hook.
4. **Golden seed set** — `eval/golden/categorizer.jsonl` (**36 synthetic-edge cases** across 3
   personas: exact/loose/shorthand mentions, multi-item splits, routine bursts + label stability,
   Hinglish, no-work, Inbox-discipline incl. near-duplicate project names, anchored captures,
   praise-screenshot text, relative dates with fixed `today`, metric preservation, isExtra/behaviour
   tags, COMBINE-mode merges, and a development-cert case whose placement is deliberately unscored —
   the CURRENT prompt restricts `goalCategory` to goal areas, so AI-1 must decide the
   development-placement policy first), `coach.jsonl` (12 rubric cases), `summary/*.json` (the 4
   spec'd rollups: dense-year w/ arc+dupe+pinned, sparse-8, routine-heavy w/ exact tallies,
   development-heavy w/ ADVISORY placement check until AI-2). Target remains 60–80 with the
   creator's REAL transcripts as the majority — that import is owner-gated (below).
5. **`eval/tools/from-backup.mjs`** — turns a device backup export into skeleton cases: split
   siblings grouped by shared transcript (placements + entryCount together), Inbox/FAILED rows →
   `inboxExpected`, routine labels + demonstrates + anchor + capture-day `today` carried; every line
   flagged `"_verify": true` (run.mjs REFUSES unverified cases) and written to a gitignored pending
   file. The backup itself and the pending file are gitignored (raw record — never committed).
6. **`.github/workflows/eval.yml`** — manual-only (`workflow_dispatch`; it spends tokens). Needs the
   **`GROQ_API_KEY` repo secret (OWNER GATE)**. Uploads the report artifact + prints it into the job
   summary; `commit_baseline` input commits `eval/report-baseline.{md,json}` (baseline runs never
   fail the job — they are the "before"; normal runs fail on any red gate = the ship gate).
7. `eval/README.md` — layout, thresholds table, case schema, real-record import steps, baseline
   discipline. `.gitignore` grew the eval/privacy entries.

### How it was tested (no local Android toolchain, no Groq key on this machine)
- `node eval/run.mjs --dry-run` — golden validation + prompt build green (34/12/4 cases).
- **Full pipeline against a local mock Groq server** (`GROQ_BASE_URL` override): all 3 suites ran
  end-to-end — transport→parse→every scorer→aggregation→report+json+gates. Verified scorer
  behaviours: anchored case never parks, coach rubric sub-checks fire (incl. grounded-in-detail),
  summary checks catch a missing metric / pinned / tally, generic parked entry fails placements but
  passes Inbox-expected cases.
- **Deterministic golden-set validator**: every expected placement/goalCategory/routine-label/
  band/anchor/demonstrates cross-checked against its own case context; summary expectations
  cross-checked against the rollup text — ALL CONSISTENT.
- `from-backup.mjs` round-tripped against a synthetic backup (split-sibling grouping, INBOX→
  `inboxExpected`, PENDING_AUDIO skipped — verified output).
- **Adversarial review** (independent agent over run.mjs/PromptSyncTest/goldens/workflow/tool) —
  **2 HIGH + 4 MED + 5 LOW, ALL FIXED pre-commit:** (H) a zero-case run / typo'd `--only` / moved
  golden file exited 0 with "gates PASS" → fails closed now; (H) a named-but-missing `--baseline`
  silently dropped the ≥-baseline gate → exit 1; (M) invented-number check used substring
  containment ("FY26" excused an invented "2") → number-token set membership; (M) the workflow
  never actually passed `--baseline` → non-baseline runs now gate on the committed baseline when it
  exists; (M) one golden case expected development-pillar placement the current prompt forbids →
  unscored + flagged for AI-1; (M) from-backup pre-filled contradictory `inboxExpected` on
  mixed-status captures → unanimous-signal only, FAILED rows carry no label signal, grouping keys
  on createdAt so repeat captures never merge, `today` uses local date not UTC. Also verified
  clean: prompt dumps byte-match the Kotlin constants (LF, no BOM), `PromptSyncTest` compiles
  (JUnit 4.13.2) + every sentinel rides the builders unchanged, eval.yml expressions correct, a
  crashed run can't commit an empty baseline. Plus COMBINE-mode golden coverage added (was zero) +
  a stray NUL byte found and scrubbed. **CI VERIFIED GREEN** (`testDebugUnitTest` + debug APK,
  run 29142570423) after one compile fix — **GOTCHA: Kotlin block comments NEST**, so the literal
  glob `eval/prompts/` + `*.txt` written together inside a KDoc opened a nested comment that
  swallowed the file ("Unclosed comment"); never put a `/`-`*` sequence in a Kotlin comment.
  Bonus: `android-debug.yml` now surfaces test/compile failures as **public ::error:: annotations**
  (this machine can't read auth-gated run logs — annotations made this failure diagnosable).

### Owner gates — ✅ ALL DONE (2026-07-11)
1. ✅ Repo secret **`GROQ_API_KEY`** added (verified by the baseline run getting past the "Check
   secret" step).
2. ✅ Real record imported. The backup was pulled from the owner's **Google Drive**
   (`BragBuddy/bragbuddy-backup.json`, auto-backup dated 2026-07-10 — incidental proof the Drive
   OAuth gate now works) into the session scratchpad (never committed), run through
   `eval/tools/from-backup.mjs` → **11 skeleton cases**, hand-verified case-by-case with the owner
   via AskUserQuestion, and merged into `categorizer.jsonl` (**36 → 47**, commit `b1eff14`).
   Owner's redaction call: **no scrub** (the codenames are meaningless without the employer anchor).
   - The record is small (11 captures), so the merged set is **47, not the 60–80 target** — that
     target assumed a bigger record. Grow it as the record grows (before AI-2); 47 is a solid AI-1
     baseline.
3. ✅ Baseline committed. **The eval was made tag-driven** (`f6e2d94`; `.github/workflows/eval.yml`
   now also fires on `eval-baseline-*` / `eval-run-*` tags — no manual button, matching every other
   build here). Pushed `eval-baseline-ai0-*` → run `29152288074` green → the job committed
   `eval/report-baseline.{md,json}` to main (`3a07872`).

### Baseline results (the "before" — `eval/report-baseline.md`, prompts categorizer `16a4f8cc2817` · summary `dbaa716aae8e` · coach `59ae3e19bc59`)

| Gate | Threshold | Baseline | |
|---|---|---|---|
| placementAccuracy | ≥ 85% | **88.9%** | ✅ |
| inboxRecall | ≥ 80% | 80.0% | ✅ |
| inboxPrecision | ≥ 90% | 92.3% | ✅ |
| jsonValidity | 100% | 100% | ✅ |
| routineReuse | 100% | **57.1%** | ❌ |
| impactBand | ≥ 80% | 92.3% | ✅ |
| coachPass | ≥ 90% | **75.0%** | ❌ |
| coachNoInventedNumbers | 100% | 100% | ✅ |
| summaryChecks | 100% | **88.2%** | ❌ |

Ungated: entryCountAccuracy 88.9% · demonstratesAccuracy 71.4% · **metricPreserved 20.0%** · dateMentioned 100% · routineFalsePositiveFree 100%.

### AI-1 target list (what the red metrics say — all in `docs/IMPLEMENTATION-PLAN.md` · Phase AI-1)
- **routineReuse 57.1% (RED, gate 100%)** — the model invents label variants instead of reusing the
  provided one: `"access requests"` → `"access approvals"` / `"SharePoint access requests"` /
  `"servicing requests"`. AI-1's routine-label-reuse rule + the output validator (snap a near-match
  routine label to the existing one) is aimed exactly here.
- **metricPreserved 20.0% (ungated but dire)** — stated numbers vanish from the `metric` field:
  "30%", "20 days", "76 stories" all dropped (real-003/005, po-metric-30-percent, po-combine-number-
  followup). Biggest single AI-1 win; the metric-preservation prompt rule + validator target it.
- **coachPass 75.0% (RED, gate 90%)** — 3 coach cases fail **grounded** (the nudge shares no content
  word with the project detail/bullet → generic questions). AI-1/AI-2 coach grounding.
- **summaryChecks 88.2% (RED, gate 100%)** — `dense-year` misses **pinnedOnce** (PCI-DSS not pinned)
  and **rolledUpCounts** (label singular/plural drift: `"code review"` vs `"code reviews"`). Summary
  prompt + the same routine-label normalization.
- **demonstrates 71.4%** — behaviour-tag granularity: the model returns sub-behaviours
  (`"set the agenda"…`) where the case wants the parent pillar `"Leadership & Behaviours"`
  (real-007), or a wrong tag (eng-collab-docs). AI-1 rubric + validator.
- **development placement (advisory until AI-2)** — `eng-development-cert` + the real
  `real-009/010/011` development-goals docs land in Inbox / Learning & Growth. The CURRENT prompt
  can't emit a development pillar, so these are UNSCORED-on-placement by design; **AI-1 must decide
  the development-placement policy** (prompt or validator), then strengthen these cases.

### ⚠️ Harness finding for AI-1 (surfaced by the real anchored cases — a genuine APP-MIRROR gap)
The placement scorer **`snapProject` (`eval/run.mjs:369`) does NOT apply the deterministic anchor
override** that the app's `EntryProcessor` (and the harness's own `isParked`, `run.mjs:386`) both
honor. So an **anchored** capture whose model output says "Inbox"/a development pillar is scored as a
placement MISS — even though the real app force-files it to the anchor project. This is why
**real-009 / real-011** (both anchored to a real project, development-flavoured content) failed
placement: the expectations are **correct for the app**, but the harness under-credits them. The
synthetic anchored cases didn't catch it (there the model happened to output the anchor project
itself). **AI-1 fix (validator scope):** when `ctx.anchor` is set, snap every entry's project to the
anchor before matching placements — mirroring `isParked`'s anchor-awareness — then these cases score
truthfully. Left as-is in the baseline on purpose (the scorer's own comment defers snapping policy to
"the AI-1 validator's planned rules"; don't rewrite a committed "before").

### Fresh chat → Phase AI-1
Fresh chat pointed at `CONTEXT.md` → **Phase AI-1 (v0.26.0)**. Baseline discipline (`eval/README.md`):
change the prompt (Kotlin **and** the `eval/prompts/*.txt` together — `PromptSyncTest` enforces it),
then **push an `eval-run-*` tag** to gate the change (must pass every threshold AND be ≥ this
baseline on every metric) before tagging the release. Commit a fresh baseline after shipping.

---

## Status: v0.25.0 — P4 · AI project-aware "Add impact" list on Home ✅ DONE (signed · tag-driven CI; compile+logic+UI+test adversarially reviewed) — **9-FEATURE BATCH COMPLETE**

> **Phase 4 — the FINAL item of the 9-feature batch** (creator's 2026-07-10 request; locked via AskUserQuestion:
> AI impact loop = an "Add impact" list on Home). Design locked via AskUserQuestion 2026-07-11: **collapsible
> Home card** → **AI suggests what to quantify, then merges** → **type-only** input. **Room stays v4** (no
> schema change). Firm invariant preserved: **the AI never invents a number — it only asks; the number comes
> from the user.**

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.25.0/BragBuddy-v0.25.0.apk` (signed; `.aab` alongside).

### v0.25.0 — what was built (`versionCode 29`)
1. **The "Add impact" card (Home).** A **collapsible** card (`ui/home/ImpactCard.kt`, built in the existing
   reliability/nudge-card style) lists filed **wins that lack a number**. Backed by pure
   `data/impact/ImpactCandidates.kt` (`from()` = **PROCESSED** + **non-routine** + has a **bullet** + `lacksMeasurable`
   [no `metric` AND no number in the bullet via the existing `ImpactCheck`], newest-first), unit-tested
   (`ImpactCandidatesTest`, 12). Collapsed = a count + "Show"; expands inline to ≤6 rows (+ "N more") each opening
   the add-impact sheet; session-dismissible (in-memory). **Gated on `aiEnabled`** (a Groq key) in the VM: without
   AI the card hides (the merge re-runs the categorizer, which needs a key — and there'd be no PROCESSED entries
   to list anyway), so there's never a broken add.
2. **Project-aware AI suggestion + type-only merge.** Tapping a row opens `ui/home/AddImpactSheet.kt` (custom-scrim,
   never a Material sheet; `rememberDiscardGuard` protects a half-typed number). It shows the win + an **AI question**
   about WHAT to quantify — new seam `AiProvider.suggestImpact` (`ImpactSuggestRequest`/`ImpactSuggestion` +
   `AiPrompts.impactCoach`; Groq impl on the cheap categorizer model + fallback; Stub returns the generic question;
   `HomeViewModel.loadImpactSuggestion` uses the entry's **project detail** [`ProjectEntity.description`] + goal area
   + role; job-cancelled on close/re-open so a slow result can't flash into the wrong sheet; falls back to a generic
   "what changed — can you put a number on it?" on no-key/failure). The prompt **only ASKS** — rule 3 forbids stating/
   inventing a number; the question is shown, never stored. The user types the number and taps **Add impact**.
3. **Non-destructive merge (`EntryProcessor.addImpact`).** The number is folded into the bullet via the existing
   **COMBINE mode** (`categorizer(combineSingle=true)` — one merged bullet, never a split). **Unlike `replace`, the
   row is NOT reset to RAW:** it stays **PROCESSED** with its placement, behaviours and ★/pin intact, and only the
   **bullet + `metric` + impact score** change; on ANY AI failure / empty result the win is left **exactly as it
   was** (retryable). This was the review fix (see below) — it means adding a number can never move a win, drop its
   goal area, or demote a good record to a bullet-less Inbox row. `EntryRepository.addImpact` delegates to it
   (fire-and-forget on the app scope).
- **Tests:** `ImpactCandidatesTest` (12) — every exclusion reason + the number-word boundary ("five" counts) + the
  occurredAt-over-createdAt ordering + the `lacksMeasurable` helper.
- **REVIEW (4-dimension adversarial — compile / logic / UI / test):** compile = **CLEAN**; tests = **CORRECT** (all
  12 hand-evaluated, no existing test broken — both `AiProvider` impls override the new method); UI = **no HIGH/MED**
  (faithful card + custom-scrim sheet, bounded field, discard-guarded, no nested-clickable swallow); logic = **HOLD**,
  **1 MED FIXED** — the original path routed through the destructive `replace()` (resets to RAW first), so a *transient*
  AI blip demoted an already-good win to a bullet-less Inbox row → **rewritten as the non-destructive `addImpact`**
  above (which also fixed 2 LOWs for free: no Outside-project goal-area re-guess, no permanent anchor pin). 2 other
  LOWs fixed: the stale-suggestion race (job-cancel) and the toast copy ("your impact"). 1 LOW left by-design (the card
  title counts all candidates while showing 6 with a "+N more" tail).
- **On-device test (the creator's step):** (a) log a qualitative win ("shipped the checkout redesign", no number) →
  it appears in the **Add impact** card → tap → the AI asks a project-aware question → type "cut drop-off 18%" → Add →
  the bullet updates to fold in the number and the row leaves the card, **staying in the same project**; (b) confirm a
  win with a number never appears in the card, and routine/BAU entries don't either; (c) turn OFF airplane-less…
  actually: with a bad/removed key the card **hides** (no broken flow); (d) start typing a number, hit Back/scrim → the
  **discard confirmation** appears; (e) generate a Summary and confirm the newly-quantified win reads with its number.
  **NEXT = the 9-feature batch is COMPLETE — await the creator's next direction (iOS is still DEFERRED).**

---

## Status: v0.24.0 — P3 · Notification-rationale popup + shorter/de-keyed onboarding privacy ✅ DONE (signed · tag-driven CI; compile+logic+UI+test adversarially reviewed)

> **Phase 3 of the 9-feature batch** (creator's 2026-07-10 request; one signed APK per group). Two changes,
> both device-local, no schema change (**Room stays v4**; the new flag is DataStore). No `PrivacyPolicy.VERSION`
> bump (the concise onboarding copy is a *summary*, not a material terms change — so no re-accept for existing
> users; acceptance still binds the full policy in Settings → Privacy).

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.24.0/BragBuddy-v0.24.0.apk` (signed; `.aab` alongside).

### v0.24.0 — what was built (`versionCode 28`)
1. **First-run notification-rationale popup (feature (a)).** The **naked OS `POST_NOTIFICATIONS` dialog** that
   `MainActivity.onCreate` fired on every cold start — which raced the **Welcome** onboarding screen on a fresh
   install — is **removed**. Instead, a **custom-scrim popup** (`ui/main/NotificationPrimerSheet.kt`, built in the
   `CatchupSheet` style — never a Material `ModalBottomSheet`) is shown **once on first Home**, explaining WHY
   BragBuddy wants to notify ("one quiet reminder a day … change or turn it off anytime") **before** the OS
   dialog; its **"Allow notifications"** button is what launches the real `RequestPermission`. Gated by a new
   device-local `SettingsStore.notifPrimerHandled` flag. Pure decision helper `notification/NotificationPrimer.kt`
   (`decide(sdkInt, alreadyGranted, handled)` → **SHOW / MARK_HANDLED / NONE**): pre-Android-13 or already-granted
   (an upgrader who granted under the old dialog) → **auto-satisfied silently, no popup**; only a fresh 13+/not-granted
   state SHOWs. Hosted in `MainScaffold` (a `StateFlow<Boolean?>` null-loading gate avoids a flash; catch-up is
   gated `!primerVisible` so two scrims never stack; the primer renders last so it covers the bar + FAB).
2. **Suppress the reliability-card double-nag.** The Home "keep your reminder alive" card
   (`HomeViewModel.showReliabilityCard`) now **also requires `notifPrimerHandled`** — so the primer and the card can
   **never nag about notifications at the same time** (structural mutual-exclusion on one flag). **Intent-based
   asymmetry on how the primer resolves:** **"Maybe later" / scrim** → `markNotifPrimerDeclined` **atomically** marks
   handled AND records the current `ReminderHealth.riskSignature` as acknowledged (one `store.edit{}`, no flash
   frame) → the card stays quiet; **"Allow" → OS-granted** → just `markNotifPrimerHandled` (a battery risk on an
   aggressive OEM can still surface the card — different concern, not a re-nag); **"Allow" → OS-denied** (incl. a
   permanently-denied upgrader where `launch()` returns denied with no dialog) → also just `markNotifPrimerHandled`,
   deliberately leaving the risk **un-acknowledged** so the card **can** surface and deep-link them to notification
   settings — **no dead-end** (this was a review finding, fixed). A genuinely NEW/changed risk later still resurfaces
   the card (existing `reliabilityDismissedRisks` semantics).
3. **Shorter, de-keyed onboarding privacy (feature (b)).** The onboarding hard-gate now renders a **concise
   summary** (`PrivacyContent(concise = true)` → new `PrivacyPolicy.onboardingPrinciples` — **6 short cards** vs. the
   full **9** — + `ONBOARDING_INTRO` + a "Read the full privacy policy anytime in Settings → Privacy" pointer). It
   **de-keys** the wording: the BYOK **key-instruction verbiage** ("AI runs on Groq — *with your own key* … *using the
   Groq API key you add yourself*") is dropped from onboarding (premature there), retitled **"AI runs on Groq"** —
   while KEEPING the material Groq disclosure, the no-audio/image-retention, control, no-warranty cards, and the
   emphasised **"You decide what you write"** closing + Groq link (rendered unconditionally). **BYOK itself is
   unchanged**; **Settings → Privacy keeps the FULL, authoritative policy** (`concise = false`, the default) that
   acceptance binds. `docs/privacy.md` (mirrors the full policy) is untouched.
- **Tests:** `NotificationPrimerTest` (11 assertions) — the full SDK × granted × handled matrix incl. the 32↔33
  boundary and both auto-satisfy paths. `PrivacyPolicy`/`SettingsStore` changes are purely additive (no existing
  test references them; all defaults preserved).
- **REVIEW (4-dimension adversarial — compile / logic / UI / test, independent agents, ~94 tool-uses):** compile =
  **CLEAN** (all symbols/imports/`when`-exhaustiveness/icon-in-extended verified); tests = **CORRECT** (all 11
  assertions hand-evaluated true, no existing test broken); UI = solid, **no HIGH/MED** (faithful `CatchupSheet`
  clone, correct z-order/insets/theme, concise privacy renders + Accept bar stays pinned); logic = **HOLD (ship)**,
  all 10 behavioural claims verified, **1 LOW dead-end FIXED** (the permanently-denied-upgrader recovery path above),
  2 LOW documented (composite-signature acknowledgment is by-design & resurfaces on change; a sub-frame DataStore-load
  race is cosmetic/unreachable). Also fixed pre-tag: added `verticalScroll` to the primer sheet (no clip at large
  font scale), tightened the body copy ("once a day"), and corrected a now-stale comment in `Notifications.kt`.
- **On-device test (the creator's step):** (a) **fresh install** → onboarding (Welcome no longer has an OS dialog
  racing it) → land on Home → the rationale popup appears **once** → **Allow** → OS dialog → grant → reminder works
  (Settings → Reliable reminders → "Send a test reminder"); (b) fresh install → **Maybe later** → **no** reliability
  card nag on Home; (c) tap **Allow** then **Don't allow** in the OS dialog → the Home "keep your reminder alive" card
  appears offering **Review settings** (recovery path, not a dead-end); (d) kill+relaunch → the popup does **not**
  re-appear (handled); (e) an **upgrader** who already granted → **no** popup; (f) onboarding **Privacy** step now
  shows the **shorter** cards with **no "add your Groq key" instructions**, and Settings → **Privacy** still shows the
  **full** policy; existing users are **not** re-prompted to re-accept. **NEXT = P4 v0.25.0** (AI project-aware
  "Add impact" list on Home — the last item in the 9-feature batch).

---

## Status: v0.23.0 — P2 · Recategorize (fix-a-wrong-category) + Theme (System/Light/Dark/Auto) ✅ DONE (signed · tag-driven CI; compile+logic+UI+test adversarially reviewed)

> **Phase 2 of the 9-feature batch.** Feature (a) as originally written (Inbox → tag a framework category)
> was **dropped by the creator mid-phase** (2026-07-10) in favour of a broader need: *"ensure anything filed
> under the wrong category can be corrected."* Locked via AskUserQuestion → **Option B · Full recategorize**
> (both the goal-area/Development placement AND Leadership & Behaviours evidence, correctable in one no-AI
> action). Theme = **device-local** (not backed up), also locked via AskUserQuestion. **Room stays v4** (theme
> is DataStore; no schema change).

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.23.0/BragBuddy-v0.23.0.apk` (signed; `.aab` alongside).

### v0.23.0 — what was built (`versionCode 27`)
1. **Recategorize — fix a wrongly-filed entry (feature (a), reshaped).** The entry-detail sheet's old **Move**
   (folder-only reassign) is replaced by **Recategorize** (`ui/entry/EntryDetailSheet.kt`): an inline two-axis
   picker — **FILE UNDER** (single-select a placement **category** = a GOAL_AREA/DEVELOPMENT pillar, then an
   optional **folder** within it, or "No specific project") + **ALSO EVIDENCE FOR** (multi-select the
   **BEHAVIOUR** pillars this entry demonstrates) → **Apply**. Deterministic, **no AI**:
   `EntryProcessor.recategorize(id, goalArea, project, demonstrates)` (replaced `reassign`) sets goalCategory /
   project / anchorProject / demonstrates / status=PROCESSED / confidence=1.0 under the processing **mutex**,
   then `syncRollup`; skips RAW + PENDING_AUDIO rows. **Closes the two gaps** that made "correct the category"
   impossible before: a **folder-less category is now reachable** (you pick the category itself, not just a
   folder under it), and **behaviour evidence is finally editable** (add a missed tag / drop a wrong one). Pure
   preselection logic in `data/entry/Recategorize.kt` (placement/behaviour partition, default category —
   canonicalised, default folder — category-scoped, default behaviours — canonical & stale-dropping), unit-tested
   (`RecategorizeTest`, 11). **Rollup-safe by design:** `toRollupItem()` drops a blank/"Inbox" goal area, so the
   picker **always requires a real placement category** (Apply disabled if the framework has none) — an entry's
   evidence therefore always reaches the generated summary. The old entry-reassign chain
   (`EntryProcessor.reassign`, `EntryRepository.reassign`, VM `reassignToProject`/`reassignOutside`/`bestGoalArea`,
   sheet `onMoveToProject`/`onMoveOutside`) was removed (the folder-cascade `ProjectRepository.reassignCategory`
   is unrelated and stays). Home + PillarDetail VMs gained a `framework` StateFlow feeding the picker.
2. **Theme — System / Light / Dark / Auto (feature (b)).** New `ThemeMode` in `SettingsStore` + two device-local
   AUTO switch times (dark-start default 8:00 PM, light-start 7:00 AM). Pure schedule logic in
   `data/theme/ThemeSchedule.kt` (`resolveDark`, `inDarkWindow` wrapping midnight [inclusive dark-start /
   exclusive light-start], `minutesUntilNextSwitch` — never 0, nearest upcoming boundary), unit-tested
   (`ThemeScheduleTest`, 10). Applied via a shared `ui/theme/ThemedApp.kt` (`BragBuddyThemedApp` composable +
   `ThemeViewModel`) that wraps **both** `MainActivity` and the translucent `CaptureActivity` in
   `BragBuddyTheme(darkTheme = resolved)`; AUTO **flips live while open** via a boundary-timed
   `delay`+`tick` recomposition (no alarms — theme only matters while a surface is visible). **No cold-start
   flash:** MainActivity holds the splash (`setKeepOnScreenCondition` until `onReady`); the translucent
   CaptureActivity can't use a splash, so `BragBuddyThemedApp(holdUntilLoaded = true)` shows nothing (transparent
   window) until prefs load. A `SideEffect` syncs system-bar icon contrast to the **resolved** theme (forced Dark
   on a light device keeps readable icons). Settings → **Appearance** card = a 4-segment System/Light/Dark/Auto
   control + two standard `TimePickerDialog` rows (only shown for Auto). **Device-local — NOT in `BackupCodec`**
   (a per-device visual preference; a restored phone keeps its own theme).
- **Tests:** `RecategorizeTest` (11) + `ThemeScheduleTest` (10) — pure-logic coverage of the preselection helpers
  and the theme/AUTO-schedule math.
- **REVIEW (4-dimension adversarial workflow — compile / logic / UI / test, each an independent agent that read
  the code + grepped symbols; 70 tool-uses total):** compile = 0, logic = 0 (rollup invariant HOLDS; mutex/CAS
  matches resolve; AUTO tick has no busy-loop/leak), test = 0 (all assertions verified correct). **UI = 2 LOW:**
  (1) **FIXED** — the translucent CaptureActivity had no splash to mask a forced-theme cold-start flash → added
  `holdUntilLoaded`; (2) **pre-existing / out-of-scope** — an in-progress inline *edit* is lost on device rotation
  (a Phase-4 `remember` pattern, already noted as an accepted limitation in the v0.20.1 audit; the recat picker's
  selections re-derive harmlessly on reopen, so this diff doesn't worsen it).
- **On-device test (the creator's step):** (a) file an entry, open it → Recategorize → move it to a **folder-less
  category** and confirm it lands there; (b) tick/untick a behaviour and confirm the "Evidences …" line + the
  behaviour section update, and that the change survives a Regenerate of the summary; (c) an Uncategorized entry
  can be re-homed; (d) Settings → Appearance → Dark forces dark everywhere incl. the capture overlay + status
  bar icons; (e) Auto → set dark-start a minute ahead and watch it flip live; (f) kill+relaunch in a forced
  theme → no light flash. **NEXT = P3 v0.24.0** (notification-rationale popup + shorten/de-key onboarding privacy).

---

## Status: v0.22.0 — Summary phase (edit/delete·collapse·restore·de-dup) ✅ DONE (signed · tag-driven CI; compile+logic+UI+test reviewed)

> **Phase 1 of the 9-feature batch** (creator's 2026-07-10 request; delivered PHASED, one signed APK per group;
> decisions locked via AskUserQuestion). Four summary-screen features, all summary-cache-only — **the record
> is never touched** (invariant holds). **Room stays v4** (no schema change; everything is DataStore JSON).

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.22.0/BragBuddy-v0.22.0.apk` (signed; `.aab` alongside).

### v0.22.0 — what was built (`versionCode 26`)
1. **Edit / delete a summary pointer, REMEMBERED (feature #1).** Long-press any pointer → a custom-scrim
   `PointerActionSheet` (Edit line / Delete line; "your record on Home isn't affected"). Edit opens
   `EditPointerDialog` (bounded AlertDialog). Persistence is a new **`SummaryOverrides`** layer on
   `CachedSummary` (`data/summary/SummaryOverrides.kt`: `deleted`/`edits`/`restored` + `summaryKey()` normalizer
   + pure **`applyOverrides()`**), re-applied over EVERY fresh generation in `SummaryViewModel.generate()` — so
   **deletes stay gone and edits stay, across a Regenerate** (edits are best-effort if the model rephrases;
   deletes/restores are reliable). Text-keyed (no fragile summary-line→entry id), covers achievements + rolled-up
   + behaviour evidence + development. Chained edits (A→B→C) resolve to a fixpoint; a final per-area
   `distinctBy(summaryKey)` safety net.
2. **Collapsible framework categories (feature #3).** Chevron on each `SectionHeader` (the toggle is a SEPARATE
   clickable from "Copy" so the Copy tap is never swallowed); **expanded by default** (read-and-copy screen);
   mirrors the Home/Pillar `mutableStateListOf` idiom + `AnimatedVisibility(expandVertically)` accordion.
3. **Restore from Set-aside (feature #5).** A "Restore" action per note → `RestorePickerSheet` (pick which goal
   area) → re-injected as an achievement and dropped from Set-aside; sticky across a Regenerate via `restored`.
4. **De-dup repeats (feature #9).** Deterministic pre-merge in `RollupAggregator.mergeNotable()` collapses
   exact/normalized-identical non-routine bullets in the same project into ONE highlight with a **count**, run
   BEFORE the highlight cap (accurate `×N` even past the cap); progressive **arcs are NOT merged**; the model
   sees `(logged N×)` and a new SUMMARY prompt rule ("never list the same accomplishment twice; keep arcs as one
   bullet; prefer the fewest pointers"); an emphasized `×N` chip on the achievement row. `SummaryAchievement`
   gained `count:Int=1`, `AggHighlight` `count:Int=1`, `SummaryRolledUp.count` now defaults `=0` (decode
   hardening). Serialize change flips existing cached summaries `isStale` ONCE → a one-time optional Regenerate.
- **Tests:** `SummaryOverridesTest` (13) + `RollupDedupTest` (7) — pure-logic coverage of overrides + de-dup.
- **REVIEW (4-dimension adversarial + a re-run real logic pass):** compile = WILL COMPILE; tests = all pass;
  UI = 1 MED (collapse animation used the non-ColumnScope AnimatedVisibility overload → diagonal; fixed to
  vertical accordion) + 1 LOW (×N chip corner matched to the Pinned chip) fixed; logic = invariants HOLD, 2 MED
  (rolled-up **Edit** was a dead button — applyOverrides now edits rolled-up lines; generate() onSuccess `put`
  raced a concurrent `mutateCached` — now under `editMutex` + fresh overrides read; overlay also blocks touch
  during "Curating…") + 3 LOW (restored-list `distinct()`, `SummaryRolledUp.count` default, KDoc honesty) fixed.
  **GOTCHA (recorded):** a workflow review-agent returned a schema-satisfying JUNK stub for the logic dimension
  → always sanity-check each agent's result; re-ran logic as a single synchronous agent.

---

## Status: v0.21.2 — text-field growth fix (long note no longer pushes the Save row off-screen) ✅ DONE (signed · tag-driven CI)

> **UI hotfix.** A long typed/pasted note grew the capture text box without bound, inflating the
> bottom-anchored sheet until the **Save/Add row scrolled off the bottom** (owner-reported on the Type
> screen). Root cause: the editable `Box`es used `heightIn(min = …)` with **no max**, and the fields had
> **no internal scroll** — so content grew the field, the field grew the sheet, and the actions below it
> left the viewport.

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.21.2/BragBuddy-v0.21.2.apk` (signed;
`.aab` alongside). **Fix (`versionCode 25`):** cap every **multi-line** editable field with `maxLines` so
long text scrolls **inside** the field (bounding the field → bounding the sheet), keeping the action row on
screen. Uniform, no new imports, no logic change, no data loss (`maxLines` bounds only what's visible; the
full value is still submitted). Applied to all 6 growing fields — **Type capture** & **voice/image Review**
(`ui/capture/CaptureScreen.kt`, maxLines 6), **framework detail** (`ui/framework/CategoryEditSheet.kt`
`ScanField`, 5), **inline entry edit** (`ui/entry/EntryDetailSheet.kt`, 6), and the **Home/Pillar "Edit
entry" dialogs** (`OutlinedTextField` minLines 3 → maxLines 8). Single-line fields (Groq key, folder/role/
number/remap names) were already bounded — untouched. **Room stays v4.** No behaviour change; pure layout bound.

---

## Status: v0.21.1 — Google Drive connect + recovery (onboarding recovery step + connect-time restore CHOICE) ✅ DONE (signed · tag-driven CI; compile = WILL COMPILE, logic = invariant HOLDS/0 HIGH-MED)

> **Shipped as `v0.21.1`** — the identical `v0.21.0` tag build was **cancelled by GitHub's runner queue
> before any step ran** (infrastructure backlog, NOT a code failure; the job had zero executed steps).
> Re-tagged v0.21.1 (versionCode 24) at the same code to re-trigger; `v0.21.0` tag stays but never
> published a release.

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.21.1/BragBuddy-v0.21.1.apk` (signed;
`.aab` alongside). A recovery flow so reinstalling users get their record back, and connecting Drive is
always an **explicit restore choice** that **never auto-backs-up an empty state or clobbers an existing
backup**. **Room stays v4** (no new pref — reuses `driveAutoBackup` as the preserve-the-backup lever).
Decisions locked via AskUserQuestion (2026-07-08, all recommended): recovery step **after Privacy** and a
successful restore **jumps to Home**; "Not now" **preserves the old backup** (pauses auto-backup); build now
/ ship v0.21.0. **⚠️ Owner gate:** Drive sign-in still FAILS at runtime until the `com.bragbuddy.app` Android
OAuth client + release SHA-1 (`B8:B2:F2:…:A4:D3`) is added to `gmailapi-491903` — the flow degrades
gracefully (shows the error, lets you Skip), but **can't be end-to-end tested until that gate is done**.

### v0.21.1 — what was built (`versionCode 24`; feature was v0.21.0, re-tagged after the cancelled run)
1. **Onboarding recovery step** (`ui/onboarding/OnboardingScreen.kt` `RecoverStep` + `OnboardingViewModel`
   Drive methods). New flow: **Welcome → Privacy → Recover from Drive → Role → Framework** (`TOTAL_STEPS=5`).
   "Reinstalling? Recover your record." → **Connect Google Drive** → after connect, if a backup exists →
   **Restore this backup** / **Not now**; if none → Continue; not connected → Skip. A **successful restore
   finishes onboarding straight to Home** (entries + role + framework all come back, so Role/Framework are
   skipped) via the existing `finished` StateFlow (restore → `setDriveAutoBackup(true)` → `completeOnboarding`
   → navigate — durable-before-nav). Fresh installs are the only ones that reach onboarding (upgraders keep
   `onboardingComplete=true`), so local is empty here → a restore is always safe. New UI (not in the Design
   System) — built in the onboarding style + the Backup screen's health-card look; flagged.
2. **Connect-time restore CHOICE, unified** (`ui/backup/BackupViewModel` + `BackupScreen`). Connecting on an
   **empty** device with an **existing** backup now shows a **"Restore your record?" dialog** (Restore / Not
   now) instead of the old **silent auto-restore**. **Restore** → pull + keep auto-backup on. **Not now** →
   `setDriveAutoBackup(false)` to **preserve the previous backup** (a fresh capture can't overwrite it) + a
   clear note; the user can restore later, re-enable auto-backup, or "Back up now". (`exists && !empty` is
   unchanged — local is the truth, buttons remain; `!exists && !empty` still seeds.)
3. **Never auto-backup an empty state / never clobber** — already guarded (`isLocalEmpty` in the observer +
   `backupNow`), now reinforced by the decline→pause lever. The **launch-time silent auto-restore**
   (`DriveBackupManager.restoreIfEmpty` + its `BragBuddyApp` call) was **removed** — recovery is exclusively
   an explicit choice now (onboarding or Settings), so a "Not now" decision is never silently overridden on
   the next launch. `BragBuddyApp` now just starts the guarded auto-backup observer.

### Adversarial review before tagging (compile + logic; per protocol)
- **Compile:** WILL COMPILE — all imports/signatures/nested `DriveStepState` access/launcher/icons verified;
  no leftover `connected`/`restoreIfEmpty`/`launch` references.
- **Logic:** the **"never clobber a backup / never lose an entry" invariant HOLDS — 0 HIGH/MED.** Every
  `restoreFromDrive`/`setDriveAutoBackup(true)` is an explicit user tap; the decline→`setDriveAutoBackup(false)`
  write is race-free (the onboarding VM scope survives a local step change; the observer reads the flag fresh
  each run and also guards `isLocalEmpty`); restore-and-finish awaits the durable writes before the nav pop;
  `importJson` never restores Drive/onboarding prefs (hence the explicit post-restore `setDriveAutoBackup(true)`
  + `completeOnboarding`); removing the launch auto-restore leaves no lost-recovery path. **1 LOW fixed** —
  `_driveState` no longer seeds `connectedEmail` from `currentEmail()` (init `null`), so a "connected-but-not-
  yet-backup-checked" state can never render the "no backup / start fresh" copy by mistake. **1 LOW accepted**
  (pre-existing, by-design): `exists && !empty` connect → the observer later syncs local→cloud (correct for
  same-lineage data; only a genuinely divergent second-device backup could be replaced — unchanged by this
  feature).

### Flags / on-device test (the creator's step — GATED on the owner OAuth step)
Add the `com.bragbuddy.app` Android OAuth client + release SHA-1 to `gmailapi-491903` first, then: (1) fresh
install → onboarding → Recover → Connect → (with a prior backup) Restore → lands on Home with your record
back, Role/Framework skipped; (2) same, but **Not now** → continue → log an entry → confirm the **old backup
is NOT overwritten** (Settings shows auto-backup off + the previous backup still restorable); (3) skip Drive
in onboarding, later connect in Settings on an empty device → the **restore dialog** appears (not a silent
restore); (4) an already-connected user is undisturbed. **Next: iOS (DEFERRED).**

---

## Status: v0.20.1 — End-to-end audit patch (fixes + hardening from a whole-app deep audit) ✅ DONE (signed · tag-driven CI; compile = WILL COMPILE, adversarial logic = HOLD/no-HIGH, 2 review-fixes applied)

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.20.1/BragBuddy-v0.20.1.apk` (signed;
`.aab` alongside). Not a phase — a **5-dimension end-to-end audit** of the whole Android build (data-integrity/
concurrency · AI seam · UI/Compose/nav · build/release · privacy/security/dead-code), each dimension run by an
independent agent, findings verified against the code, fixed, and reviewed. **Room stays v4.** Decisions locked
via AskUserQuestion (2026-07-08): exclude the DB from OS cloud-backup (keep device-transfer); apply all four MED
fixes; ship as v0.20.1. The creator also required a **discard-confirmation on every text editor**.

### v0.20.1 — what changed (`versionCode 22`)
1. **Privacy-truthfulness (was a live false claim): OS auto-backup no longer uploads entries.** `allowBackup`
   stayed `true` but `res/xml/data_extraction_rules.xml` + `backup_rules.xml` **`<include database bragbuddy.db>`**
   meant Android silently cloud-backed every raw transcript — contradicting the shipped "the only copy that leaves
   your device is one you choose to make." Now the **`<cloud-backup>` excludes all domains** (also keeps the
   DataStore Groq-key file out) and **`<device-transfer>` keeps db+prefs** (local phone-to-phone only). The app's
   own opt-in Drive backup is unaffected.
2. **Live-capture crash fixed (`data/speech/Transcriber.kt`).** The Groq Whisper request/`Authorization` header was
   built OUTSIDE `runCatching`; a malformed key character threw `IllegalArgumentException` → uncaught in the
   fire-and-forget caller → **crash + lost take**. Moved the request build inside `runCatching` (mirrors
   `GroqAiProvider`) → a bad key now fails safe.
3. **Offline-queue duplicate-on-crash fixed (never-lose core; `OfflineRecovery.kt` + 3 new `EntryDao` queries).**
   A process-kill between the transcript commit and the clip `file.delete()` left an unreferenced clip that the
   orphan sweep **re-adopted into a DUPLICATE entry**. Redesigned the clip lifecycle: the drain now commits the
   transcript **keeping** `audioPath` and never deletes inline; a new `cleanupDrainedClips()` (run after the drain)
   owns deletion for rows that have durably left the queue (`settledWithAudio(PENDING_AUDIO)` → `File.delete` +
   `clearDrainedClipPath` **under the EntryProcessor mutex**); and `adoptOrphanClips` now references **all** rows'
   clips (`allAudioPaths()`), so a drained-but-not-deleted clip is never re-adopted. Crash-safe + self-healing:
   no loss, no dup, no leak (adversarially verified).
4. **Atomic transcript split (`EntryProcessor.processEntry`).** Injected `BragBuddyDatabase`; the first-row update
   + all sibling inserts now commit in one `db.withTransaction { }` (rollup synced after) so a crash mid-split can't
   leave row 1 PROCESSED (skipped forever) with the extra split items lost.
5. **Bullet-less entries reach the summary (`resolve`/`reassign`).** Resolving/reassigning a FAILED/empty row (no
   cleaned bullet) filed it to a folder but the rollup skipped it → silently absent from the generated appraisal
   doc. Now it falls back to the raw transcript as the bullet so it's included.
6. **Discard-confirmation on every text editor (`ui/common/DiscardGuard.kt`, wired into `CategoryEditSheet`,
   `EntryDetailSheet`, `CaptureScreen`).** Per the creator: unsaved typed/scanned text must not be lost by Back
   gesture or any other ambient exit. `rememberDiscardGuard(dirty, onDismiss)` installs a `BackHandler` + a
   "Discard changes?" dialog and returns a `requestDismiss` wired to the scrim + close ✕. When there are unsaved
   edits it confirms; otherwise it dismisses. **Also fixes the app-wide gap where system Back on an open overlay
   exited the app** (or discarded an edit) instead of closing the overlay. Explicit "Cancel" buttons stay direct.
7. **Cleanups:** deleted the dead `reminder/ReminderWorker.kt` (reminders are AlarmManager now); `SummaryResult.summary`
   defaulted so a reply omitting it degrades instead of throwing; onboarding finish-bar now hides for the project-
   remap sheet too; stale "OpenRouter" dep comment → Groq; `exportJson`/`changeSignal` strip the transient
   `audioPath` (no device-local path in the backup JSON; no double-upload churn).

### Audit review (per protocol)
Compile agent (Room-focused, given the prior `new`-keyword trap): **WILL COMPILE** — the 3 new DAO params
(`pending`, `path`, none) are valid Java identifiers, the enum bind reuses the proven `EntryStatus` converter.
Logic agent: **HOLD — 0 HIGH, firm invariants intact**; the offline redesign verified crash-safe across every
interleaving. 2 review findings applied: (MED) `clearAudioPath` moved under the processor mutex; (LOW) `audioPath`
stripped from the backup/change surface. **Noted, not fixed:** a resolved un-transcribable *placeholder* row can
show its placeholder text as a summary bullet (user-initiated + editable); the remaining non-text overlays (the "+"
radial, catch-up, remap sheets) still lack a BackHandler (Back there falls through) — a small follow-up; rotation
still drops in-progress framework/entry **editor** text (config-change; capture is already VM-safe) — the discard
guard covers dismissal, not rotation. **On-device testing (the creator's step)** should exercise: an offline voice
note recovering (no dup) across a force-stop; a mid-edit Back on each editor → Discard dialog; a fresh install NOT
appearing in the phone's Google cloud backup list.

---

## Status: v0.20.0 — Android v2 · Phase C · Onboarding wizard + Privacy/legal + audio-storage removal ✅ DONE (signed · tag-driven CI; compile review = WILL COMPILE, adversarial logic review + fix-diff re-review held → SHIP)

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.20.0/BragBuddy-v0.20.0.apk` (signed;
`.aab` alongside). The final Android-v2 slice: a first-run **guided-but-skippable onboarding wizard**, a
**Privacy & terms** screen (hard gate + Settings), and **removal of the audio-storage remnants**. **No AI
reshapes the framework** (the reshape holds — step 3 is the real Type+Scan editor). **Room stays v4** (the
two new flags are DataStore, no schema change). Decisions locked via AskUserQuestion (2026-07-08, all
"recommended"): **one release**; at-rest encryption **phrased honestly** (no SQLCipher — see below); law +
contact = **India / simpleapps108@gmail.com**; onboarding flow = **Welcome → Privacy → Role → Framework**.

### v0.20.0 — what was built (`versionCode 21`)
1. **First-run onboarding wizard** (`ui/onboarding/OnboardingScreen.kt` + `OnboardingViewModel.kt`):
   **Welcome → Privacy (hard gate) → Role → Framework → Home**, with dot progress + pill CTAs in the Design
   System §2 tone (warm intro card, the amber "no company name" reassurance). **Privacy is required**; **role
   & framework are skippable** (framework skip keeps `Framework.DEFAULT`). **Step 3 reuses the real B2a
   Type + Scan editor** — it *embeds* `FrameworkScreen` (not `refineFramework`, which stays unused), so
   Add-category / edit-detail / Scan-a-doc all work verbatim; the onboarding **finish bar hides while an
   editor sheet is open** (so it can't be tapped through the custom-scrim sheet — FrameworkScreen gained a
   defaulted `reportEditing: (Boolean)->Unit` hook, a no-op for the tab). The role step **seeds from an
   existing saved role** (for a re-onboard) without clobbering typing.
2. **Gate + routing** (`ui/navigation/RootGateViewModel.kt`, `BragNavHost.kt`, `Destinations.kt`): a tiny VM
   resolves the start destination from two new device-local flags before the NavHost is built (null → a
   brief bg-coloured frame, no flicker). `showOnboarding = !onboardingComplete || acceptedPrivacyVersion <
   PrivacyPolicy.VERSION`; `reacceptOnly` (already-onboarded + stale version) collapses the wizard to **just
   the privacy card**. Finish navigates `HOME` with `popUpTo(ONBOARDING){inclusive}`; start destination +
   reacceptOnly are `remember`-snapshotted so the flag flip can't rebuild the graph.
3. **Two device-local flags** (`SettingsStore`): `onboardingComplete` (default false) + `acceptedPrivacyVersion`
   (default 0). **Finish is ONE atomic `store.edit{}`** (`completeOnboarding(privacyVersion)`) and the screen
   **awaits it before navigating** — the earlier two-write/launch-then-nav approach was cancelled by the nav
   pop and could loop re-onboarding (HIGH, caught + fixed pre-tag). **Deliberately NOT backed up** (accepting
   terms is per-install; also avoids a mid-session restore fighting the gate) — verified safe against the
   v0.14 restore-on-reinstall path.
4. **Privacy & terms** — one source of truth `data/legal/PrivacyPolicy.kt` (`VERSION = 1`), rendered by the
   shared `ui/legal/PrivacyContent.kt` (rounded grey cards, bold title + plain body — the creator's "Core
   Privacy Principles" style) and reused by **both** the onboarding gate and a read-only Settings screen
   (`ui/legal/PrivacyScreen.kt`, nav Card like Drive/Reliability). The claims are **rewritten TRUE for
   BragBuddy** (the reference was a server/account/always-listening app): local-only/no-account; **the #1
   disclosure — text + scanned images + audio all go to Groq using the user's own key**; audio/images not
   retained (acknowledges the Phase-7 offline temp clip); **encryption phrased honestly** (HTTPS in transit +
   Android sandbox/device encryption at rest; **no** false app-level at-rest claim — no SQLCipher);
   no ads/tracking/selling; delete-anytime; AI-can-be-wrong / no-warranty / limitation of liability; 18+; law
   = India, contact = simpleapps108@gmail.com; and the **emphasised closing: you are solely responsible for
   what you disclose — never enter company/client/confidential info.** Mirrored to a hosted-ready
   **`docs/privacy.md`** (sibling-app pattern; owner enables Pages later). *(NOT legal advice — a lawyer
   should review before any public/Play launch.)*
5. **Audio-storage removal:** deleted the disabled **"+ Voice notes"** `OptionRow` + its KDoc in
   `BackupScreen.kt` (+ removed the now-unused `Mic` import); corrected the **inaccurate manifest comment**
   (`AndroidManifest.xml`) that claimed on-device STT keeps audio on the phone — it now states audio goes to
   Groq Whisper and isn't retained. **LEFT** the genuine offline-queue temp-clip path
   (`EntryEntity.audioPath` / `PENDING_AUDIO` / `OfflineRecovery`) untouched — the never-lose-a-take net.

### Adversarial review before tagging (compile + logic + fix-diff re-review; per protocol)
- **Compile pass (agent, "you are the compiler"):** **WILL COMPILE** across all 11 changed/new files — every
  import/symbol/signature/theme-token/Compose-scope verified, `FrameworkScreen` new defaulted param leaves
  the tab call site valid, Hilt VMs inject cleanly, the elvis-`return` gate idiom typechecks. No Room/DAO
  touched → the `new` Java-keyword class of failure ([[room-java-keyword-params]]) doesn't apply.
- **Adversarial logic pass (agent):** **1 HIGH + 1 MED + 1 LOW, all fixed pre-tag; firm invariants intact.**
  - **[HIGH · fixed]** `completeOnboarding()` did two sequential `viewModelScope` writes then navigated →
    the nav pop cancelled the scope mid-write → `onboardingComplete` could never persist → **re-onboarding
    loop**. Fixed: one atomic `SettingsStore.completeOnboarding()` edit + **await-before-navigate** via a
    `finished` StateFlow the screen observes (`LaunchedEffect(finished){ onFinished() }`); reacceptOnly path
    hardened the same way.
  - **[MED · fixed]** the onboarding "Start logging" bar sat beside the embedded editor → tappable *under* an
    open `CategoryEditSheet` → a mistap finished onboarding and dropped unsaved editor text. Fixed: hide the
    finish bar while a framework sheet is open (`reportEditing` hook).
  - **[LOW · fixed]** the role step started blank on a forced re-onboard (existing role ignored) → seed from
    the saved role without clobbering typing.
  - Verified SAFE: start-destination race/flicker, bail-out durability, reacceptOnly, Home role double-ask,
    backup/restore vs. the gate, framework-embed leaks/scroll/scan, and **every privacy claim cross-checked
    against the actual code** (Groq text+image+audio, no analytics SDK, no SQLCipher).
- **Fix-diff re-review (agent):** **SHIP** — the 4 changed files compile clean, the durable-before-nav and
  finish-bar fixes are correct, no regressions; one INFO (no try/catch on the finish write — matches every
  other setter, and it's safer than the old bug: on failure the user simply isn't navigated, no false
  "complete").

### Flags / on-device test (the creator's step)
New UI (onboarding wizard, privacy cards, Settings privacy card) is **not in the Design System's exact form**
— the §2 onboarding predates the reshape (it shows AI voice-refine + no privacy/role step), so I adopted its
**tone** (warm intro, dot progress, pill CTA, amber reassurance) and reconciled the flow via AskUserQuestion;
the privacy cards follow the creator's attached "Core Privacy Principles" reference. **To verify on-device:**
(1) **fresh install** → must accept **Privacy** before reaching Home → Role (type/skip) → Framework (edit via
Type+Scan / add a category / **Scan a doc** / just "Start logging") → Home; (2) **Skip** works at role &
framework (framework keeps the default 3); (3) reopen the app → **no onboarding** (flag persisted — the HIGH
fix; confirm it doesn't loop); (4) Settings → **Privacy & terms** is readable and matches; (5) open the
category editor **during** onboarding → the "Start logging" bar is **hidden** (no tap-through); (6) Backup
screen shows **no "Voice notes"** row; (7) existing-user upgrade path (install v0.19.0 then this) → sees the
wizard once, role pre-filled, framework intact, nothing lost. **Version bump to re-prompt terms:** bump
`PrivacyPolicy.VERSION` for a material change → already-onboarded users get **only** the privacy re-accept.
**Owner (optional):** enable GitHub Pages (main `/docs`) to host `docs/privacy.md` before any public launch;
a lawyer should review the copy. **Next: iOS** (deferred — see the ▶ NEXT ROADMAP). Android v2 is complete.

---

## Status: v0.19.0 — Android v2 · Phase B2b · Project rename-remap + categorizer prompt change + Home inline-edit Save ✅ DONE (verified green · signed · first-try CI — run 28923683720; the compile + adversarial logic review held)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.19.0/BragBuddy-v0.19.0.apk` (signed;
`.aab` alongside). The deferred half of the reshaped Phase B2 — three pieces (all agreed 2026-07-07). **No
AI anywhere here** (rename-remap is deterministic; the prompt change only drops text). **Room stays v4** (no
schema change — the remap edits existing columns; no new pref). Decisions locked via AskUserQuestion: entry
edit = **inline in the detail sheet**; remap prompt = **custom-scrim bottom sheet**; emptied folder in
reassign/new = **kept**.

### v0.19.0 — what was built (`versionCode 20`)
1. **Daily-categorizer prompt change (core pipeline).** The categorizer's `{{APPRAISAL_FRAMEWORK}}` block now
   lists category **NAMES + each category's sub-folder names but NOT the detail blurbs**. Extracted the pure,
   unit-tested `data/entry/FrameworkPrompt.categorizerBlock(fw, projects)` (replacing `EntryProcessor`'s
   private `frameworkBlockWithFolders`). The **summary is untouched** — it still builds its framework block
   from `Framework.toPromptBlock()` (`SummaryViewModel:139`, blurbs kept), so a **category detail now feeds the
   summary only**: editing it changes future summaries without disturbing daily filing. Behaviours keep their
   names (still tagged); full project details still ride in `{{PROJECTS}}`. `FrameworkPromptTest` locks the
   split (names present, blurbs absent). `AiPromptsTest` unaffected (it passes a framework string in).
2. **Project rename-remap (3-option, deterministic, no AI)** — the project-level analogue of v0.18.0's category
   rename-remap. When a project (folder) with filed records is **renamed**, a shared custom-scrim
   `ui/common/ProjectRemapSheet` offers: **(a) Carry** to the new name · **(b) Reassign** to an existing
   goal-area project · **(c) New project** created inline. `EntryProcessor.remapProjectEverywhere(old, oldArea,
   target, targetArea, createTargetFolder)` runs under the processing **mutex**, updates `project` +
   `goalCategory` + `anchorProject`, then `reconcileLocked()` so the rollup/summary follow. **Category-scoped**
   (`EntryDao.remapProjectScoped`/`remapAnchorScoped`/`countProjectReferences` all filter by goal area) because
   a folder is unique by `(name, goalArea)` — a same-named folder under another goal area is never touched.
   Hooked at **both** rename sites: the Framework editor's project rows (`FrameworkViewModel.saveProject`, via
   `row.baseName` as the old name) and Settings' folder dialog (`SettingsViewModel.updateProject`). Dismissing
   leaves records under their goal area's "Outside project" bucket (never lost — verified against `HomeDoc`).
3. **Home inline-edit Save.** Tap an entry → the detail sheet now edits the bullet **in place** with its own
   **"Save & re-file"** button (per-item Save model, matching v0.18.0), replacing the pop-up dialog for that
   path (`EntryDetailSheet` `onEdit` → `onSaveEdit: (String) -> Unit`; edit state keyed on `entry.id`). The row
   ⋮-menu Edit keeps its existing dialog (already has a Save button). Hosts: Home + pillar deep view.
4. **Incidental fix:** Settings folder-edit no longer wipes the project **description** (`updateProject` passed
   `description = null`; now preserves `existing.description`).

### Adversarial review before tagging (compile + logic; per protocol)
- **Compile pass (agent, "you are the compiler"):** **WILL COMPILE** — clean across all 16 files. Confirmed the
  Room Java-keyword codegen trap is avoided (DAO params `old`/`oldArea`/`newName`/`newArea`/`name`/`area` — no
  reserved words), all imports resolve (incl. the new `ProjectRemapSheet`/`EntryDetailSheet` sets), both
  `EntryDetailSheet` call sites match the new `onSaveEdit` with no stale `onEdit`, and every `FlowRow` carries
  `@OptIn(ExperimentalLayoutApi::class)`. A focused second compile pass re-verified the category-scoping fix.
- **Adversarial logic pass (agent):** **0 HIGH** (no firm-invariant break — no record lost/stranded; all
  mutations under the mutex + reconciled). **3 MED + 3 LOW; the 3 MED + 1 LOW fixed pre-tag:**
  - **[MED · fixed]** project match was **name-only**, so a same-named folder under another goal area got its
    records mis-counted/mis-relabeled → **scoped every count/update by goal area**.
  - **[MED · fixed]** Settings "rename + recategorise in one edit" then Carry left records under the OLD area →
    **Carry now follows the folder's new area** (`newArea`).
  - **[MED · fixed]** the reassign picker offered **behaviour/growth folders** (records would drop into
    Uncategorized) → **filtered to goal-area folders only**.
  - **[LOW · fixed]** `applyProjectCreateNew` created the folder on `viewModelScope` (could orphan on navigate-
    away) → **create moved into the processor** (durable app scope, under the mutex, ordered before the remap).
  - **[LOW · fixed]** remap sheet copy said dismissed records "wait under Uncategorized" → corrected to
    **"Outside project"** (a project rename leaves `goalCategory` valid, so they stay in their goal area).
  - **[LOW · accepted, pre-existing]** `reassign`-to-Outside keeps a stale `anchorProject`; non-destructive,
    unrelated to B2b (a Phase-4 behaviour). Noted for a later cleanup.

### Flags / on-device test (the creator's step)
New UI (`ProjectRemapSheet` 3-option custom-scrim sheet, the inline edit field + Save in the detail sheet) is
**not in the Design System** — built from tokens; the look + the 3 behaviour choices were confirmed via
AskUserQuestion. **To verify on-device:** (1) rename a project that has records → the 3-option sheet → **Carry**
(records follow to the new name; check Home + a regenerated summary) → **Reassign** to another project (records +
goal area follow) → **New project** (folder created, records moved) → **Leave** (records sit under "Outside
project"); rename from BOTH the Framework editor and Settings → Edit folder. (2) Two goal-area folders with the
**same name** under different goal areas → rename one → only its records move (category-scoping). (3) Tap an
entry → **Edit inline** → change the text → **Save & re-file** (re-files; the ⋮-menu Edit dialog still works).
(4) Edit a **category detail** → it changes the next **summary** but NOT where new entries file; a **project
detail** still steers daily filing. **Next: Phase C** (onboarding wizard + Privacy/legal — blocked on the pending
privacy attachment). Start in a fresh chat pointed at `CONTEXT.md`.

---

## Status: v0.18.0 — Android v2 · Phase B2a · Framework editing (Type+Scan, per-item Save, Reset, category rename-remap) ✅ DONE (verified green · signed; fixed via 2 CI round-trips — the Room `new` Java-keyword codegen break)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.18.0/BragBuddy-v0.18.0.apk` (signed;
`.aab` alongside). Part 1 of the reshaped Phase B2 (see the ⚠️ SCOPE RESHAPE note in ▶ NEXT ROADMAP). **No
AI reshapes the framework** — the user builds it by hand; Scan is just OCR into a field. **Room stays v4**
(no schema change — the rename-remap edits existing columns; no new pref).

### v0.18.0 — what was built (`versionCode 19`)
1. **Framework editor reworked to Type + Scan, per-item Save** (`ui/framework/CategoryEditSheet.kt` rewritten,
   `FrameworkViewModel.kt` rewritten, `FrameworkScreen.kt`): the per-field **mic/voice was removed**
   (`AudioRecorder`/`GroqTranscriber`/`fieldVoice` machinery gone from the framework VM). Each **detail box**
   is now **type or Scan** — a `ScanField` with a **Scan** button that OCRs a document into the field
   (append, editable, deletes nothing). **Add a category** now opens the **same full-screen sheet** in
   add-mode (`pillar = null`; the old `AddCategoryDialog`/`SegToggle` deleted). Editing is **per-item**: the
   category (name/axis/detail) has its own **Save** with a confirm ("your next summary will use this updated
   detail" + rename note); each **project** row (name/detail) has its own **Save** with a confirm ("future
   entries will use this; existing records unchanged"). A create that hits the `(name, goalArea)` unique index
   returns `<= 0` → the row stays dirty + a clear toast (never a false "saved").
2. **Document scan behind the seam** (`data/ai/`): `AiProvider.readDocumentText` (+ `GroqAiProvider` impl —
   reuses the Phase A vision pipeline `callVision` + `AiConfig.visionModel`/`visionFallback`, but with a NEW
   **doc-OCR prompt** `AiPrompts.documentScan` — the scanned image is *reference material* (a job description /
   review criteria), not "the work you did"; `StubAiProvider` returns empty). `FrameworkViewModel.onScanImage`
   downscales via `data/image/ImageInput`, reads with vision, and emits the text to append; camera
   (FileProvider, reuses `capture_images`) + gallery (`PickVisualMedia`) launchers live in the sheet. Fail-safe
   throughout (no key / offline / unreadable / empty → field unchanged, calm toast, never a stuck "Reading…").
   **All Save actions are disabled while a scan is in flight** (a save/close mid-OCR would drop the result —
   it's delivered on a replay=0 SharedFlow only while the sheet is collecting).
3. **Deterministic category rename-remap (NO AI, prompt-first)** (`EntryProcessor.renameCategoryEverywhere` +
   `countCategoryReferences`, `EntryDao.updateGoalCategory`, `EntryRepository` passthroughs): when a category is
   renamed + saved, its folders cascade (existing `projects.renameCategory`) and — if filed records still carry
   the old label — the user is **prompted** (`FrameworkScreen`) to relabel them. Confirm → under the processing
   **mutex**: SQL `UPDATE goalCategory old → new COLLATE NOCASE` + a Kotlin rewrite of the `demonstrates`
   JSON-list (behaviour tags, `.distinct()`), then `reconcileLocked()` so the summary follows. Declining leaves
   records under "Uncategorized" (the prompt warns; creator's chosen behaviour).
4. **Reset framework in Settings** (`SettingsViewModel.resetFramework` → `FrameworkStore.reset()` + a card +
   confirm dialog): back to the default 3 categories; **project folders and every filed record are KEPT**
   (records under changed categories surface under "Uncategorized" until re-homed).

### Adversarial review before tagging (compile + logic; per protocol)
Compile pass (agent, "you are the compiler"): passed Kotlin type-checking, **but MISSED a Room codegen
break** — the first two tags failed CI at `testDebugUnitTest` with `EntryDao_Impl.java:382: error:
<identifier> expected`. Root cause: the new DAO param was named **`new`** — a valid Kotlin identifier but a
**Java reserved word**, so Room's generated Java `EntryDao_Impl` was invalid. Fixed by renaming the param to
`newName` (and the `:new` bind). **LESSON (added to memory):** Room generates Java from Kotlin `@Dao`s — never
name a `@Query`/`@Insert` param after a Java keyword (`new`, `default`, `class`, `int`, …); Kotlin/agents won't
catch it, only the Java compile of the generated impl does. (Also switched `COLLATE NOCASE` → `LOWER()=LOWER()`
for the case-insensitive match — a red herring for the failure, but the cleaner form, kept.)
Logic pass: **0 firm-invariant breaks** (no entry loss, capture never blocked, rollup reconciled, mutex sound).
**3 findings fixed pre-tag:** (MED) a scan completing after the sheet saved/closed dropped the OCR result →
**all Save actions gated on scan-in-flight**; (MED) `rememberSaveable` scan-targets vs. plain-`remember` editor
state mismatched → a restored scan could land in a vanished row → **scan-targets moved to plain `remember`** so
the editor resets together (matches the pre-existing rotation behaviour); (LOW-MED) a new project hitting the
unique index returned `-1` but the row showed "saved" → **`doSaveProject` now reflects the real persist outcome**
(+ `ProjectRepository.create` KDoc corrected). Accepted (LOW): declining the relabel prompt splits the view
(empty renamed folders + records in Uncategorized) — the prompt copy warns; it's the creator's "prompt-first" pick.

### Flags / on-device test (the creator's step)
New UI (per-item Save layout, `ScanField` with the Scan button, camera/gallery chooser, Reset card, rename-remap
prompt) is **not in the Design System** — built from tokens; the per-item layout + Scan approach were confirmed
with the creator via AskUserQuestion. **To verify on-device:** (1) edit a category detail → Save → confirm →
"next summary will use this"; (2) Scan a job-description photo/screenshot into a detail box (no key → toast; offline
→ calm toast; nothing lost); (3) add/edit a project detail → its own Save + confirm; (4) rename a category with
filed records → the relabel prompt → Relabel moves them (check Home + a regenerated summary follow the new name),
Leave → they sit in Uncategorized; (5) Reset framework in Settings → default 3 categories, folders + records kept.
**Next: Phase B2b — v0.19.0** (project rename-remap 3-option + categorizer prompt change [names + projects, drop
category blurbs] + Home daily-record Save buttons). Start in a fresh chat pointed at `CONTEXT.md`.

---

## Status: v0.17.0 — Android v2 · Phase B · "+" radial capture menu ✅ DONE (compile + adversarial review clean; awaiting CI)

**APK (on green):** `github.com/aucksy/bragbuddy/releases/download/v0.17.0/BragBuddy-v0.17.0.apk` (signed;
`.aab` alongside). The second Android-v2 slice: the mic FAB becomes a **"+"** that opens a 3-option
**radial (Speak / Type / Scan)**, a new **Default capture method** setting, and **one shared capture
launcher**. Scope locked via AskUserQuestion (2026-07-07): **FAB always opens the radial** (it's the
deliberate chooser; the default governs only the notification/nudge); **default = Voice** (preserves
today's open-to-voice feel); **labels = Speak / Type / Scan** (match the existing in-sheet toggle). The
radial look was proposed as a token-built mockup and approved before coding. **Room stays v4** (no schema
change — the new pref is DataStore).

### v0.17.0 — what was built (`versionCode 18`)
1. **The "+" radial** (`ui/main/MainScaffold.kt`): the raised FAB moved from inside `BottomBar` to the
   **scaffold level** (so it sits above the radial scrim and stays tappable to close), its geometry
   reproduced exactly (`padding(bottom = navInset + 23.dp)` == the old `align(TopCenter).offset(y=-20)`
   in a `navInset+55dp` bar — symmetry intact). The "+" rotates 45°→"×" (`graphicsLayer{rotationZ}`);
   three option circles fan up from the FAB (Speak `-84,-66` / Type `0,-112` / Scan `+84,-66` dp) with a
   staggered scale+fade+travel enter (`animateFloatAsState` per item, `delayMillis = index*45`); the app's
   own capture scrim dims the screen (tap to dismiss); **close is by unmount** so the full-screen scrim
   never lingers to swallow taps. Each pick → `CaptureLauncher.openMode(mode)`.
2. **One shared launcher** (`ui/capture/CaptureLauncher.kt`, new): kills the 5-site
   `Intent(…CaptureActivity…)` duplication (Home, pillar view, FAB, catch-up, notification). Carries a new
   **`CaptureActivity.EXTRA_START_MODE`**: a `CaptureMode` name (explicit radial pick), `START_ASK` (the
   3-choice chooser — in-context "+"), `START_DEFAULT` (the notification/nudge → the user's default,
   resolved in the VM), or absent (Redo → last-used mode). `intentForMode/Chooser/Default/Redo` + `open*`.
3. **Default capture method** (`data/prefs/SettingsStore.kt` `enum DefaultCaptureMethod {ASK,SPEAK,TYPE,
   IMAGE}` + `defaultCaptureMethod` pref, default `SPEAK`; reader reuses the `runCatching{valueOf}
   .getOrDefault` pattern). New **Settings card** (`ui/settings` `DefaultCaptureCard`, segmented
   Ask/Voice/Type/Scan). The **notification** (`notification/Notifications.kt`) + **daily nudge**
   (`HomeScreen`) + **catch-up** (`MainScaffold`) now open via `openDefault` → the VM resolves the pref
   (ASK → the chooser). In-context **"Add entry to …" rows** (Home folders, pillar view) open `openChooser`
   (the 3-choice, **anchored** to that folder). The daily-nudge button icon went mic → "+".
4. **The in-sheet chooser** (`ui/capture/CaptureScreen.kt` `StartChooser`, `CaptureViewModel.awaitingChoice`
   + `pickStartMode`): when a launch is `START_ASK` (or `START_DEFAULT` with default=Ask) the sheet shows
   three big Speak/Type/Scan cards (reusing the image mode's `SourceButton` look); picking opens straight
   into it (Speak auto-records via the activity's mic-permission flow — the auto-voice `LaunchedEffect` now
   keys on `awaitingChoice` so clearing it re-fires; Type focuses the keyboard; Scan shows pick-a-source).
   The ✕ / scrim / handle are rendered **before** the chooser branch, so it's always cancellable, and the
   anchor banner shows above it. `EXTRA_START_MODE` is applied synchronously in `onCreate` (`vm.applyStart`)
   — always before the VM's init coroutine resumes past its first `settings.first()` suspension, so the
   opening mode is deterministic.
5. **Backup mirror**: `defaultCaptureMethod` threaded through `BackupCodec` (field w/ default +
   `toJson`/`toSettings`), `BackupRepository` (export / import / `changeSignal`), and `BackupCodecTest`
   (round-trips `IMAGE`) — it survives a Drive restore, exactly like `lastCaptureMode`.

### Adversarial review before tagging (compile + logic; per protocol)
- **Compile pass (agent, "you are the compiler"):** **clean** across all 14 files + the new launcher —
  every import/symbol/signature/scoped-modifier (`Modifier.weight`/`align` in the right scope, `return@Column`,
  `by`/getValue/setValue, `when(enum)` exhaustiveness, the `SourceButton`/`StartChooser` call) verified;
  removed imports (`Intent`, `CaptureActivity`, `Mic`) grep-confirmed unreferenced.
- **Adversarial logic pass (agent):** **0 HIGH, 0 MED.** Both flagged risks resolved: (a) the **radial
  options are tappable** though drawn outside their 52dp parent — Compose doesn't clip hit-testing on
  non-clipping ancestors (`NodeCoordinator.hitTest` forwards the pointer), and every option sits well inside
  the screen; (b) **`requestedStart` timing is safe** — `onCreate` sets it before the retained VM's init
  coroutine resumes past `settings.first()`. Routing verified correct across all surfaces; anchor survives
  the chooser; no Material `ModalBottomSheet` (veto-freeze) introduced. **3 LOW, all accepted** (documented,
  consistent with prior phases): L1 double-`recorder.start()` only if you rotate in the sub-second
  pre-LISTENING window (VM is retained across rotation → tiny; worst case a leaked temp file, never a
  lost/dup entry — fixing it risks the never-lose-a-take path); L2 mic-permanently-denied → Speak toggle
  bounces to Type (pre-existing); L3 two-finger simultaneous pick (singleTop collapses it).

### Flags / on-device test (the creator's step)
New UI (radial, in-sheet 3-card chooser, Default-capture segmented control) is **not in the Design System** —
built from tokens; look approved via the Phase B mockup. **#1 to verify on-device:** tap each of the three
radial options actually launches (confirms the offset hit-testing). Then: each default routes correctly from
the FAB (always radial) AND the notification/daily-nudge (goes straight to Voice; "Ask" → chooser); the "+"
in-context rows open the anchored 3-choice and file into the right folder; Redo still opens last-used; the
FAB sits exactly where the mic did (symmetry); Drive backup/restore carries the default. **Next: Phase C —
onboarding wizard + Privacy/legal** (see ▶ NEXT ROADMAP). Start in a fresh chat pointed at `CONTEXT.md`.

---

## Status: v0.16.0 — Android v2 · Phase A · Image scanning ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.16.0/BragBuddy-v0.16.0.apk` (signed; `.aab`
alongside; Android Release run `28871752443`, **first-try green** — the compile pass + adversarial review
+ fix-diff held, no CI round-trips). The first phase of the Android v2 batch: a **3rd capture input**.
Scope locked via AskUserQuestion: image flow = **extract → editable review → file** (verify the read before
it saves); review shows a **thumbnail**; source = **camera + gallery**; 1 image/capture. **Room stays v4**
(`EntrySource.IMAGE` is stored by name — no migration).

### v0.16.0 — what was built (`versionCode 17`)
1. **Groq vision behind the seam** (`data/ai/`): `AiProvider.extractFromImage` (+ `GroqAiProvider.callVision`
   — one multimodal user turn: a `content` array of `{text}` + base64 `{image_url}` with `json_object`;
   `StubAiProvider` fails safe). `AiConfig.visionModel = qwen/qwen3.6-27b` (**production, re-verified live
   2026-07-07** on console.groq.com/docs) + `visionFallback = meta-llama/llama-4-scout-…` (tried if the
   primary is retired). `AiPrompts.imageExtract` — a first-person, **faithful** extraction that **invents
   nothing**; an empty string = "no work content" (distinct from an error). `ImageExtractRequest/Result`.
2. **`data/image/ImageInput`** (pure Android framework, **no image-loading dependency**): bounds-decode →
   downscale (≤ 1600 px longest side) → JPEG (< 2.6 MB → < 4 MB base64) → `data:` URL; null on an
   undecodable/corrupt/0-byte image; bitmaps recycled on every path.
3. **Capture flow** (`ui/capture/`): `CaptureMode.IMAGE` + a 3-way **Speak / Type / Scan** toggle;
   `ImageContent` (pick source → "Reading your image…" → the **shared** editable `ReviewContent` with a
   decoded **thumbnail**; calm error states — no-key / unreadable / offline / empty). `CaptureViewModel.
   onImageChosen` runs off the sheet under an `imageJob` (double-pick + mode-switch race guards; `impactAdded`
   reset per image so a multi-item scan never wrongly merges into one bullet), fails safe (the **source image
   persists** → retry, no queue needed), and files via `EntryRepository.capture(text, EntrySource.IMAGE)`.
   The image is **sent to Groq and never stored**.
4. **Image acquisition** (`CaptureActivity`): gallery = **`PickVisualMedia`** (no permission); camera =
   **`TakePicture`** to a **FileProvider** Uri (no CAMERA permission — the system camera handles it).
   `pendingPhotoUri` is **persisted across config-change / process-death** (`onSaveInstanceState` +
   `BundleCompat` restore) so a returning photo is never dropped; temp scans are **swept** on a fresh launch
   and on finish (privacy + bounded cache). Manifest **FileProvider** (`${applicationId}.fileprovider`) +
   `res/xml/file_paths.xml`.

### Adversarial review before tagging (compile + logic; per protocol)
Compile pass: **clean** (every import/symbol/signature cross-checked; the `AiProvider` change is overridden
in both impls with no test fake; `when(CaptureMode)` exhaustive; activity-compose 1.9.3 has PickVisualMedia).
Logic pass: **2 HIGH + 1 MED + LOWs — all fixed pre-tag, fixes re-checked:**
- **[HIGH]** the camera photo was **dropped on process-death / rotation** mid-capture (`pendingPhotoUri` was
  a plain Activity field) → **persisted** via `onSaveInstanceState`/`BundleCompat` + an orphan sweep.
- **[HIGH]** the review agent doubted the vision slug → **re-verified `qwen/qwen3.6-27b` live** on Groq's docs
  (it postdates the agent's training) + added the `llama-4-scout` fallback for resilience.
- **[MED]** stale `impactAdded` after an image **Re-scan** could force a multi-item image into one merged
  bullet → reset in `onImageChosen`.
- **[LOW]** base64 headroom tightened (2.6 MB JPEG target); the decode-peak OOM is already caught → `imageFail`.

### Flags / next
New UI (3-way toggle, image pick screen, thumbnail, error states) is **not in the Design System** — built
from tokens; look confirmed via AskUserQuestion (thumbnail in review). On-device test: scan a
screenshot/whiteboard → extract → edit → files (or Inbox); no-key → "add key / type instead"; offline → calm
retry; nothing lost. **Next: Phase B — the "+" radial capture menu** + default input method + notification
routing (see the ▶ NEXT ROADMAP at the top). Start in a fresh chat pointed at `CONTEXT.md`.

---

## Status: v0.15.0 — Phase 7 · Reliability + retention polish ✅ DONE (verified green · signed · first-try CI) — the LAST Android phase

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.15.0/BragBuddy-v0.15.0.apk` (signed; `.aab`
alongside; Android Release run `28712476499`, **first-try green** — no CI round-trips; the 5-dimension
adversarially-verified review + the fix-diff re-review held). Build-Brief Phase 7: *"OEM alarm / battery-optimization wizard (ColorOS etc.), on-open 'you
haven't logged' fallback, offline queue, weekly catch-up prompt, an early preview summary, error
states."* Scope locked via AskUserQuestion: offline voice = **auto-queue the audio**; catch-up = **on
open, Fri 17:00→Sun, once/week**; preview banner = **5+ filed entries, CTA auto-generates**; wizard =
**at-risk Home card + Settings screen**; daily nudge = **after the reminder time only**. **Room v3→v4**
(`MIGRATION_3_4`, additive `entries.audioPath`).

### v0.15.0 — what was built (`versionCode 16`)
1. **Offline voice queue — the never-lose-a-take spine** (`data/net/ConnectivityMonitor` +
   `data/entry/OfflineRecovery` + `EntryStatus.PENDING_AUDIO` + `EntryEntity.audioPath`):
   - A voice capture whose transcription fails **offline auto-queues silently** (clip moved
     `cacheDir → filesDir/voice_queue`, a PENDING_AUDIO row inserted, "Saved for later" confirmation);
     an online transport failure offers **Try again / Type instead / Save for later**; **dismissing the
     sheet mid-transcription or after a transport failure queues instead of deleting** (onCleared
     backstop). `AudioRecorder.stop()` now transfers file ownership (cancel() can never delete a
     finished take — was the root of two silent-loss paths).
   - `OfflineRecovery` (started in `BragBuddyApp`; also kicked on launch-online, reconnect, key
     add/replace [debounced], and post-queue): **adopts orphaned clips** (crash-window sweep, 2-min
     grace, mtime refreshed on move; 0-byte husks deleted), **drains the queue** (transcribe → RAW →
     categorizer), and **auto-retries FAILED entries** (same idempotent path as Inbox "Try again").
   - **Every drain outcome commits via `EntryProcessor.commitPendingAudio` — a compare-and-swap under
     the processing mutex** (row still exists, still PENDING_AUDIO, same clip) and **the audio is
     deleted only after a successful commit** — so a concurrent Drive restore can never be clobbered by
     a stale-id write and words are never deleted before their text is durable.
   - Failure policy: transient (offline/408/429/5xx) + auth (401/403 — fixable key) stay queued;
     other 4xx (unreadable/oversized clip — `TranscriptionHttpException`) park **visibly in the Inbox**
     (INBOX not FAILED, so the placeholder is never fed to the AI). Home shows a **waiting-voice strip**
     with connectivity-aware copy. Backup: PENDING_AUDIO rows **excluded from export**, **preserved
     across restore** (re-inserted with fresh ids), ignored by `isLocalEmpty` (the v0.14 anti-clobber
     guard still holds), and filtered out of `changeSignal` (no byte-identical re-uploads).
2. **Retention (Design §7)** — pure, unit-tested `data/retention/RetentionPolicy`:
   - **Daily nudge**: quiet Home card when nothing is logged today AND the reminder time passed
     (dismissible for the day; future-dated entries can't nag; hidden for a never-logged user).
   - **Weekly catch-up**: the §7 sheet ("Anything bigger this week you didn't log?" · Add something /
     Not this week) on first open in the Fri-17:00→Sunday window, once per ISO week (stamped with the
     week it was SHOWN for), opt-out toggle in Settings, skipped until the user has ever logged.
   - **Early preview banner**: the §7 indigo-gradient card ("Your summary's taking shape · From just N
     entries") from 5 filed entries until the first summary ever generates (or dismissed); **"See the
     preview" jumps to the Summary tab and auto-generates** (one metered call — the tap is the consent;
     all of generate()'s staleness/offline/key guards still apply).
3. **Reliable reminders (OEM wizard)** — `reminder/ReliabilityCheck` + `ui/reliability/` + a Home
   at-risk card. Live-✓ steps: notifications (**including a blocked "Daily reminder" CHANNEL**, which
   deep-links to the channel page), exact alarms (S+), battery-optimization exemption (direct system
   dialog via new manifest `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — Play-restricted, fine for direct
   APK), best-effort **OEM auto-start deep links** (ColorOS/MIUI/Vivo/Huawei… + app-details fallback)
   with a user-confirmed switch (no public API), and **"Send a test reminder"**. Every step returns via
   an activity-result launcher → re-probe + **re-arm the alarm immediately**. The Home card shows only
   on real risk (battery-optimization alone counts **only on aggressive OEMs** — a stock Pixel isn't
   nagged) and dismissal is **per-risk-signature**, so a NEW risk later resurfaces it.
4. **Calm error states**: Inbox FAILED pill goes offline-aware ("Offline — will retry when you're
   connected"); Summary generate has an **advisory** offline pre-check (gated on the connectivity
   callback having registered — never hard-blocks on a stuck signal); capture error copy distinguishes
   offline/redo. *(Wizard, nudge, at-risk and waiting cards are not in the design files — flagged,
   built from tokens; the catch-up sheet + preview banner match the §7 mockups.)*

### Adversarial review before tagging (per protocol; ultra pass)
A 5-dimension review workflow (compile / offline-queue / backup-restore / retention-logic /
reliability-wizard), **every finding adversarially verified** by an independent skeptic: 16 raw →
**15 confirmed (2 HIGH, 5 MED, 8 LOW), 1 refuted; compile dimension clean**. Highlights fixed:
- **[HIGH]** dismissing the sheet **during TRANSCRIBING** deleted the take (scope cancellation meant
  onFailure never ran; backstop only covered ERROR) → backstop broadened + recorder ownership fix.
- **[HIGH]** the recovery drain **raced a Drive restore**: a stale-id full-row `@Update` could overwrite
  a restored entry (ids preserved on restore!) or store the transcript nowhere, then delete the clip →
  the CAS `commitPendingAudio` + delete-only-after-commit.
- **[MED×5]** "Type instead" deleted the retained clip (recorder ownership); permanently-failing clips
  retried forever with no user recourse (typed HTTP errors + Inbox parking); at-risk card would show for
  ~every fresh install and its dismissal was forever (OEM gating + risk signatures); wizard was blind to
  a blocked reminder channel (channel probe + deep link); drain/restore race (above).
- **[LOW×8]** incl. orphaned-clip crash window (adoption sweep), kick-before-insert (sequenced
  callback), number-clip double-tap guard, catch-up week stamped at show-time, future-dated-entry nudge,
  advisory offline gate, changeSignal churn, lost-clip placeholder fed to the AI (INBOX parking).
Then the **fix diff itself was re-reviewed** (compile + logic agents — the v0.11.0 lesson): compile
clean; 4 edge defects found + fixed (0-byte clip adopt→park loop; fixed-key never re-kicking recovery
[debounced]; Speak→Type→Speak round-trip destroying a held take; renameTo keeping recording-time mtime).

### Tests
`RetentionPolicyTest` (day/week keys incl. ISO padding, nudge edges [before/at/after reminder time,
logged today, disabled, never-logged, dismissed-today, future-dated], catch-up window boundaries +
once-per-week + opt-out, preview banner gates) + `HomeDocTest` gains the PENDING_AUDIO waiting-strip
case (visible, not processing/inbox, Home not empty).

### Owner setup still pending (Phase 6, unchanged)
Drive sign-in stays gated until a `com.bragbuddy.app` Android OAuth client + release SHA-1
(`B8:B2:F2:86:05:BF:C8:44:94:98:E9:58:02:EA:55:74:9E:58:A4:D3`) is added to project `gmailapi-491903`.
Local Export/Import works regardless.

### Next
**Android is feature-complete** (Build Brief Phases 0–7 all shipped). **Next = the Android v2 batch**
(image scan → "+" radial capture → onboarding + privacy) — see **▶ NEXT ROADMAP** at the top of this
file; **iOS is deferred** (research parked there). Everything under "Out of scope" stays parked. On-device verification for this phase: reminders fire
reliably on the Find X9s (walk the Reliable-reminders screen once), an offline voice/typed entry
recovers when the network returns, nothing is lost.

---

## Status: v0.14.0 — Phase 6 · Google Drive backup + restore ✅ DONE (signed · tag-driven CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.14.0/BragBuddy-v0.14.0.apk` (signed; `.aab`
alongside). Build-Brief Phase 6: *"Google Drive backup (data + readable doc), restore on reinstall, a
visible backup-health indicator, manual export."* Scope locked via AskUserQuestion: **reuse the shared
Google project** (owner adds an Android OAuth client — see setup below); **structured/text data only**
(no audio is retained on-device, so the design's "+ voice notes" toggle is shown disabled + flagged);
**readable doc → visible Drive folder + Export to device**. Room stays **v3** (backup is read-only over
the existing schema; no migration).

### ⚠️ OWNER SETUP REQUIRED (one-time — Drive sign-in fails until this is done)
Add an **Android OAuth client** to the shared Google Cloud project **`gmailapi-491903`** (the one
ColorCloset/NotDigest use — the Web client id is already wired in `DriveConfig.WEB_CLIENT_ID`):
- **Package name:** `com.bragbuddy.app`
- **Release SHA-1:** `B8:B2:F2:86:05:BF:C8:44:94:98:E9:58:02:EA:55:74:9E:58:A4:D3`
  (from `bragbuddy-release.keystore`, alias `bragbuddy`; SHA-256
  `D6:7E:61:6E:E7:41:C8:10:4C:FC:B3:2D:1A:A5:40:4D:06:4D:23:2A:94:C0:95:00:C1:4E:95:1D:BB:C2:DD:FE`).
Until it's added, the app builds/installs fine and **local Export/Import still works** — only Google
sign-in returns an error. (If BragBuddy ever moves to Play App Signing, add Play's app-signing SHA-1 too.)

### v0.14.0 — what was built (`versionCode 15`)
1. **Drive backup/restore** (`data/drive/`): `DriveConfig` (shared web client id, `drive.file` scope,
   "BragBuddy" folder), `DriveBackupManager` — Google Sign-In + **Drive v3 REST hit directly over the
   OAuth token** (no Drive SDK), mirroring the sibling apps' proven pattern. Uploads **two** files with
   **create-before-delete** safety: `bragbuddy-backup.json` (restore data) + `BragBuddy record.txt` (the
   human-readable appraisal doc, refreshed each backup — openable from any computer). Silent, debounced
   **auto-backup** observer (on when connected; `SettingsStore.driveAutoBackup`, default on).
2. **The backup payload** (`data/backup/`): `BackupCodec` (pure, unit-tested org.json (de)serialise of
   entries + folders + framework + settings + cached summaries) + `BackupRepository` (gathers/applies +
   readable-doc + local SAF export/import + size). **The Groq key and audio are NEVER backed up**
   (privacy); the **rollup is derived** so it isn't stored — it's rebuilt (`reconcileRollup`) from the
   restored entries. `EntryEntity`/`ProjectEntity` DAOs gained `getAllOnce`/`deleteAll`/`insertAll`
   (ids preserved on restore); `SummaryStore` gained raw export/import.
3. **The Backup screen** (`ui/backup/`, Design System §6, reached from Settings): a **health card**
   (Backed up · <time> / Connected / Not backed up), the **"what gets backed up"** options
   (Transcriptions & data + a **disabled "+ Voice notes"** with the "not stored on this device" note),
   an **auto-backup** toggle, **Back up now**, **Restore from Drive**, **Export a copy to my device**
   (SAF) + **Restore from a file** fallback, and Disconnect.
4. **Restore-on-reinstall**: `BragBuddyApp` restores-if-empty then starts the auto-backup observer;
   connecting Drive with an empty local + an existing backup **auto-restores** the cloud history.

### Adversarial review before tagging (compile + logic; fixed pre-tag)
Compile pass (new + changed files, incl. play-services-auth API, org.json, SAF launchers, Hilt app
field injection): **clean**. Logic pass found **2 HIGH + 2 MED — all fixed, fixes re-compile-checked:**
- **[HIGH · backup-destruction]** the auto-backup observer could **overwrite a rich Drive backup with a
  near-empty local state** (reinstall → connect → log one entry → clobber, since connect didn't restore).
  → Fixed: **never back up an empty state** (`backupNow` throws + the observer skips when
  `isLocalEmpty`), and **connecting with empty local + an existing backup now auto-restores** instead of
  seeding — closing the window.
- **[HIGH/MED · data loss]** `importJson` wasn't atomic and ran **outside the processing mutex** — a
  mid-restore throw could half-wipe the log, and a concurrent categorization could leak a pre-restore
  capture into the restored data. → Fixed: the whole restore runs under **`EntryProcessor.runRestore`
  (the mutex)** with the log/folder replace in a single **Room `withTransaction`**, then reconciles the
  rollup inline (non-reentrant).
- **[MED · data loss]** `decode()`'s accept gate was too permissive (a foreign JSON could trigger a
  wipe). → Fixed: restore requires **our `version` marker** + the structural keys before any destructive
  write (decode already ran before any delete). Accepted LOWs: orphan-file cleanup on failed delete;
  first-change-after-start timing (now `drop(1)`); reinstall vs. processPending launch ordering (rare,
  harmless).

### Tests
`BackupCodecTest` (round-trip of entries/folders/framework/settings/summaries + null on non-backup +
enum-fallback) — uses a real `org.json` test dependency (Android stubs org.json in unit tests).

---

## Status: v0.13.0 — Phase 5 · Running rollup + summary ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.13.0/BragBuddy-v0.13.0.apk` (signed; `.aab`
alongside; Android Release run `28704890580`, **first-try green** — no CI round-trips, the careful
pre-commit compile pass + the 5-dimension adversarial review held). Build-Brief Phase 5: *"maintain the rollup on each entry (impact/routine → deterministic
update); the summary-generator prompt → a curated, length-capped summary + 'set aside' list, with pin /
promote / demote."* The actual point of the app. Scope locked via AskUserQuestion: **period =
configurable review-year start** (windowed mid-year / year-end); **per-line reword = deferred** (whole-
summary Regenerate only); **set-aside = calm explanatory panel** (no per-item restore — the AI returns
categorical notes, not restorable items; mockup's "tap to add back" flagged as a deviation). Summary
model slug `openai/gpt-oss-120b` **re-verified live on Groq** (Production tier) + fallback
`llama-3.3-70b-versatile`. Room stays **v3** (rollup + cached summaries persist via DataStore, no migration).

### v0.13.0 — what was built (`versionCode 14`)
1. **The running rollup** (`data/rollup/`) — a maintained, per-entry compact **projection** of the
   filed record, kept in sync **incrementally** so the summary never re-scans the raw log:
   - `RollupModels.kt` — `RollupItem` (id · timestamp · goalArea · project · bullet · metric · impact ·
     routine/type · isExtra · demonstrates) + the aggregate view types.
   - `RollupStore.kt` — DataStore-JSON store; `put`/`remove` (upsert/reverse **by id**) / `replaceAll`.
   - `RollupAggregator.kt` (pure, unit-tested) — `EntryEntity.toRollupItem()` (the single "is this a
     filed contribution?" gate), `aggregate(items, window, framework, cap)` → per-area ranked
     highlights + routine tallies + cumulative metrics + behaviour evidence, `serialize()` → the
     bounded `{{ROLLUP}}` block, `signature()` (stable `String.hashCode` over the whole model input).
   - `ReviewPeriod.kt` (pure, unit-tested) — `ReviewPeriods.windowFor(period, startMonth)` (mid-year =
     first 6 months, year-end = full review year, from a configurable start month) + `SummaryLength`
     (Brief/One page/Detailed → `{{LENGTH_CAP}}` text **and** a length-dependent `highlightCap`).
   - **Wired into `EntryProcessor` under its existing mutex** at every mutation: process (first + each
     sibling by captured insert id), refileSingle, replace (reset removes → refile re-adds → ★ re-sync),
     resolve, reassign, setExtra (syncs); **setPinned does NOT** (pins are fed to the summary live).
     **Delete/deleteMany now route through the processor** so a delete drops the rollup contribution.
   - **Launch self-heal:** `reconcileRollup()` (MainActivity, after `processPending`) rebuilds the
     rollup from PROCESSED entries — **seeds it for this upgrade** (existing entries predate the rollup)
     and repairs any drift. Reads the bounded log ONCE at launch (off the summary path); the summary
     itself only ever reads the rollup.
2. **Summary generation + cache** (`data/summary/SummaryStore.kt`) — a generated summary is a **cached
   artefact** keyed per (period + length), tagged with the **input signature** it was made from.
   Viewing is free; **Regenerate calls the model only when the input changed** (signature differs);
   each **fresh** generation is metered via the existing `UsageMeter` (built Phase 0, first used here).
   Promote/demote reorders the cached draft **locally** (no model call), persisted with the same
   signature so it doesn't self-mark stale.
3. **The Summary screen** (`ui/summary/`, replacing the `MainScaffold` placeholder) — Design System §5:
   a **Generate sheet** (custom scrim, per the veto-freeze rule — never a Material `ModalBottomSheet`)
   with the Period segmented control + computed date range + entry count + the Length picker; the
   generated **document** (pillar-coloured sections, achievement bullets, routine work as muted
   `×N` rolled-up insets, behaviour evidence, growth); a **Pinned chip** on lines from pinned entries;
   per-line **promote/demote**; a **Regenerate** that only fires when stale ("Up to date" otherwise);
   a calm collapsible **Set-aside** panel; **Copy all / Copy section** (pure `ui/summary/SummaryExport.kt`,
   unit-tested) → clipboard + toast. States: no-key → open Settings; empty period; not-generated →
   Generate CTA; generating → shimmer overlay.
4. **Review-year start setting** (`SettingsStore.reviewYearStartMonth` + a "Review year" card in
   Settings) — windows the summary's mid-year/year-end periods (the creator's chosen period model).

### Adversarial review before tagging (compile + logic; 5-dimension fan-out, each finding verified)
Ran a parallel compile + rollup + summary-VM + UI + integrity review, then adversarially verified every
finding. **Compile & integrity dimensions clean.** 2 MED + 1 NOTE survived verification — all fixed,
and the fixes re-compile-checked (the v0.11.0 lesson):
- **[MED · fixed]** the Generate **sheet's Regenerate button called the model unconditionally** — an
  up-to-date cached summary could be regenerated (wasted metered call). Fixed: the staleness guard now
  lives in `generate()` itself (`cached && !isStale → "Already up to date", no call`), protecting BOTH
  the sheet and the status-chip paths; the sheet button reads "Up to date" when fresh.
- **[MED · fixed]** `_generating` **re-entrancy race** — the guard was checked before `launch` but set
  after a suspend point, so a fast double-tap launched two generations. Fixed: the flag is set
  **synchronously before `launch`** (+ `try/finally` reset).
- **[NOTE · fixed]** "Detailed" length was silently bounded by the default cap 15. Fixed: `SummaryLength`
  now carries a length-dependent `highlightCap` (Brief 8 / One page 15 / Detailed 60), passed to
  `aggregate()`, so the knob changes the real model input, not just the prompt wording.
- **I also caught pre-review:** a `s?.phase == READY` smart-cast that wouldn't compile (`s.period` on a
  nullable) → restructured the phase dispatch to null-check first.

### Tests
`RollupAggregatorTest` (projection gate, windowing, ranking + cap, routine tally, behaviour evidence,
serialize, signature stability), `ReviewPeriodTest` (configurable-start windowing, mid vs year-end),
`SummaryExportTest` (plain-text copy incl. set-aside excluded from the pasted doc).

---

## Status: v0.12.0 — Phase 4 · Edit, reassign, copy-out ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.12.0/BragBuddy-v0.12.0.apk` (signed; `.aab`
alongside; run `28702671318`, first-try green). The Build-Brief Phase 4: *"tap an entry → raw + cleaned; edit / move /
toggle Extra / pin / delete; copy a section or the whole doc as clean text for Word/Docs."* Scope
locked via AskUserQuestion (tap→detail sheet · clean plain text · per-section + whole-doc copy). Room
stays **v3** (no schema change — `isExtra`/`isPinned` columns already existed).

### v0.12.0 — what was built (`versionCode 13`)
1. **Tap an entry → detail sheet** (`ui/entry/EntryDetailSheet.kt`, a custom scrim+Column bottom sheet
   like the capture sheet — NOT Material `ModalBottomSheet`, per the veto-freeze rule). Shows the
   **cleaned bullet + the raw transcript** it came from + chips (project · goal · ★ · Pinned · metric ·
   date), and gathers every per-entry action: **Edit** (opens the existing edit dialog), **Move**
   (reveals a folder-chip picker → reassign, no AI re-call), **★ Standout** toggle, **Pin** toggle,
   **Delete**. Not in the design files (flagged; built from tokens). Reached from all three surfaces —
   Home inline, the deep pillar view, and the single-folder screen — via a new `EntryBulletRow.onTap`.
2. **Move / reassign a FILED entry** (`EntryProcessor.reassign`, `EntryRepository.reassign`) — mirrors
   the Inbox `resolve` but works on a PROCESSED row too (guards only against a still-processing RAW),
   under the processing mutex, keeping the cleaned bullet/behaviours (no AI re-call); a named target
   becomes the `anchorProject`. The picker lists **goal-area folders only** (a behaviour-area folder
   isn't a placement slot).
3. **★ Standout + Pin toggles** — `EntryDao.setExtra/setPinned` (targeted column updates, routed
   through the processor mutex so they can't lose to a concurrent re-file). Pin is stored now and
   consumed by the Phase 5 summary. Optimistic snapshot so the sheet reflects the toggle instantly.
4. **Copy-out** (`ui/home/DocExport.kt`, pure + unit-tested `DocExportTest`) → **clean plain text**
   (UPPERCASE pillar heading → project sub-heading → `  • bullet` + `[Standout]`; behaviours list
   their evidence bullets). A **"Copy"** action in the Home header copies the **whole document**; a
   **"Copy"** in each pillar deep-view / single-folder header copies **that section** — to the
   clipboard with a "paste into Word or Docs" toast. Matches the design's "Copy for review".

### Adversarial review before tagging (compile + logic; fixed pre-tag)
Compile: **clean** (all call sites / imports / the new shared sheet + `onOpenDetail` threading
cross-checked). Logic: **0 HIGH, 3 MED — all fixed:**
- **[MED]** lost-update race — the targeted `setExtra`/`setPinned` writes could be clobbered by a
  concurrent full-row `reassign`/`resolve`/`replace` carrying the pre-toggle flag → **routed the
  toggles through the same processing mutex**.
- **[MED]** a user's manual **★ Standout was dropped on Edit** (the re-file reset it; Pin survived —
  inconsistent) → **`replace` now preserves a set ★** across the re-file (re-applies it after refile).
- **[MED]** the Move picker offered **behaviour-area folders**, which would strand the entry in the
  Uncategorized catch-all instead of the picked folder → **picker filtered to goal-area folders**.

---

## Status: v0.11.0 — v0.10.0 feedback batch (5 items) ✅ DONE (verified green · signed)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.11.0/BragBuddy-v0.11.0.apk` (signed; `.aab`
alongside; run `28694241418`). Third on-device-testing pass (creator, 5 items; UI choices locked via
AskUserQuestion). Room stays **v3** (no schema change).

**CI note (1 round-trip):** the first tag failed at "Run unit tests" — the pre-tag *logic* review had
me add `super.onReceive()` to a Hilt `@AndroidEntryPoint` receiver to fix a runtime injection gap, but
that **doesn't compile** (Kotlin rejects the super-call to the abstract framework method; Hilt's
superclass swap is a post-compile bytecode transform), and the *compile* review had run before that
edit. Fixed by switching to a **plain `BroadcastReceiver` + `EntryPointAccessors`** (the sibling Spends
app's proven pattern). **Lesson: any code added in response to a review must itself be re-compiled/
re-reviewed — a fix can introduce a fresh compile break the earlier pass never saw.**

### v0.11.0 — what changed (`versionCode 12`)
1. **Reliable 9 PM reminder (was firing at random times).** Root cause: the daily reminder used
   **WorkManager periodic work**, which the OS batches into Doze windows and which drops the
   time-of-day anchor after the first run → drift. Rewritten to an **exact `AlarmManager` alarm**
   (`setExactAndAllowWhileIdle`, inexact `setAndAllowWhileIdle` fallback if exact-alarm access is
   unavailable) that a new **`ReminderReceiver`** re-arms every day (on fire) and after boot / clock /
   timezone changes. `MainActivity` still re-syncs on launch; the legacy WorkManager `daily_reminder`
   work is **cancelled** in `schedule()`/`cancel()` so an upgraded install can't double-fire. Manifest
   gains `USE_EXACT_ALARM` (API 33+, auto-granted) + `SCHEDULE_EXACT_ALARM` (API 31-32) + the receiver.
2. **"Add impact/number" now yields ONE merged, deduped bullet** (was appending the follow-up as a
   separate thing). New **combine mode**: `CategorizeRequest.combineSingle` → `AiPrompts` appends a
   `COMBINE_MODE` directive ("return EXACTLY ONE entry; merge the follow-up, remove repetition, keep
   every distinct fact & number"). `EntryProcessor.process/replace` route to the single-output
   `refileSingle` when set (never split, never drop the number). `CaptureViewModel` tracks
   `impactAdded` (set when a number is typed or spoken and folded in) and passes `combineSingle`
   through `save → capture/replaceText`; the typed post-save `confirmNumber` also uses it. A plain
   multi-item note (no number added) still splits normally.
3. **Consistent expand/collapse animation.** The deep pillar view previously popped items in with no
   animation while Home/Framework used `AnimatedVisibility`. Restructured the pillar view so each
   project/evidence group's entries live inside `AnimatedVisibility` — identical fade/expand
   everywhere.
4. **Home folders expand INLINE** (were opening a screen that re-listed the same folders). Tapping a
   folder now expands it in place to its recent entries (**fully actionable**: ⋮ edit/redo/delete +
   "Add entry"), capped at **10** (`MAX_INLINE_ENTRIES`); more than 10 → a **"See all N"** row opens a
   **dedicated single-folder screen** (`pillar/{id}?folder=…`, `PillarDetailViewModel.singleFolder`)
   showing just that folder with full edit/delete/multi-select/add, back to Home. Entry rows are a new
   shared `ui/common/EntryBulletRow` used by BOTH Home and the deep view (identical look). *(Deviation
   from the Design System's "Home stays an overview; depth one tap in" — the creator explicitly chose
   inline expansion; confirmed via AskUserQuestion.)* Behaviour sections keep their sample + tap-to-open
   (creator's choice — only the animation was unified).
5. **Performance Goals section expanded by default** on Home (all sections were collapsed since
   v0.10.0). Seeded once via `LaunchedEffect(doc.goals)` + a `seededExpand` guard, so the user can
   collapse it and it stays collapsed. Only the "Performance Goals" section (creator's choice); folders
   inside stay collapsed.

### Adversarial review before tagging (compile + logic; per protocol)
Compile pass: **clean** (0 findings — every call site / import / signature cross-checked, incl. the
new shared `EntryBulletRow` and the `onOpenFolder` threading). Logic pass found **1 HIGH + 4 LOW**:
- **[HIGH · fixed]** `ReminderReceiver` (a Hilt `@AndroidEntryPoint` receiver) didn't call
  `super.onReceive()`, so field injection never ran → `settingsStore`/`scheduler` uninitialized →
  **crash on every alarm fire and boot**, killing the reminder entirely. Fixed: added
  `super.onReceive(context, intent)` first, plus a `catch` so a reminder can never crash the app.
- **[LOW · accepted]** the `combineSingle` flag lives only in the fire-and-forget lambda; if the
  process dies between the capture insert and processing, the launch-time drain re-files in split mode
  (the note may split instead of merging). Rare; entry never lost. Persisting it would need a Room
  migration — deferred.
- **[LOW · accepted]** `impactAdded` latches until the next take; heavily rewriting the review text
  after adding a number could still force one bullet. Narrow edge.
- **[LOW · accepted]** the single-folder "See all" screen shows an empty stale view if the folder is
  renamed/deleted while open (graceful — no crash/loss; mirrors how a vanished pillar is handled).
- **[NOTE]** `USE_EXACT_ALARM` is Play-restricted (alarm/calendar/reminder apps). BragBuddy currently
  ships as a direct signed APK (not Play), so it's fine now — revisit before any Play submission
  (could drop to `SCHEDULE_EXACT_ALARM` + a settings redirect on API 33+).

---

## Status: v0.10.0 — UX batch (6 items) ✅ DONE (verified green · signed)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.10.0/BragBuddy-v0.10.0.apk` (signed; `.aab`
alongside). Second on-device-testing pass (creator, 6 items; scope locked via AskUserQuestion).
CI note: took 3 CI round-trips — the "Run unit tests" step (compiles main+test) caught **two**
missing-import errors in brand-new code the static reviews missed: `height`/`width` in
`CategoryEditSheet`, then `mutableStateOf`/`setValue` in `CaptureScreen`'s "See an example" state.
Reinforces: **the compiler is the only real gate**; grep/agent import checks are not sufficient.

### v0.10.0 — what changed (`versionCode 11`)
1. **Collapsible sections everywhere, default collapsed** (less scrolling on long pages). Home
   goal/behaviour sections, the pillar deep-view's projects + evidence, and Framework categories all
   collapse/expand via a chevron; expanded ids kept in a remembered set (start empty). Selection mode
   in the pillar view **force-expands** so bullets stay selectable; Home's "Filing…" strip + Inbox
   peek stay outside the collapsible sections so a fresh capture is always visible.
2. **Removed "Refine by voice"** from the Framework page. Editing is now direct + per-field (voice/text
   given inside the edit sheet). `AiProvider.refineFramework` stays in the seam (unused).
3. **Notification** text → "Log today's work wins now / Before you forget them."
4. **Framework category editor rebuilt** (creator: sub-folders were wrong). Category rows are compact
   + collapsible; **Edit opens a full sheet** with a **category summary** (voice or text) and its
   **projects**, each with a **name + its own summary** (voice or text; greyed "Add your performance
   metrics for this project…"). The old always-visible project pills are gone (they live inside now).
   Project summaries persist to `ProjectEntity.description` and feed the AI (`frameworkBlockWithFolders`
   already carries folder names; descriptions ride in `{{PROJECTS}}`). Voice = per-field cloud dictation
   (`FrameworkViewModel.fieldVoice`/`fieldTranscript`); type-only without a Groq key.
5. **"Extra" → "★ Standout"** (clearer), tappable to a one-line explanation ("work beyond your normal
   role — evidences leadership"). Internal `isExtra` + colour tokens unchanged.
6. **Impact nudge much stronger**: always shown; **pulses** (subtle scale) + primary styling when the
   note has no measurable value yet (calms once a number is present); richer copy ("Add numbers &
   impact — %, time saved, ₹, people, before → after"); an inline **"See an example"** expands a
   weak→strong before/after to coach users new to self-assessment.

### Adversarial review before tagging (compile + logic; fixed pre-tag)
Compile: 1 breaker (missing `Text` import in the new `CategoryEditSheet`) → fixed. Logic found real
crash/data-loss risks in the new editor — all fixed:
- **[HIGH]** renaming a project onto a sibling's name, or a category onto another category's name,
  hit the `(name, goalArea)` unique index and **crashed** an unguarded coroutine mid-save (partial
  writes). Fixed: **unique category/project names are validated in the sheet** (Save disabled + a
  hint), the category keeps its old name on any residual collision, `@Update`/`reassignCategory` are
  now `IGNORE`/`OR IGNORE`, and the whole project-diff is wrapped in `runCatching`. No crash path left.
- **[MED]** duplicate category names would merge folder namespaces (and a remove could wipe another
  category's folders) → duplicate category names are now blocked on add + edit.
- **[LOW]** a new project duplicating a sibling was silently dropped → now blocked with a hint;
  fast double-tap could start two field recordings → `startFieldVoice` flips state synchronously.
No Room schema change (only `@Update` conflict strategy) — DB stays **v3**.

---

## v0.9.0 — Cleanup batch (5 items) ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.9.0/BragBuddy-v0.9.0.apk` (signed; `.aab`
alongside). Built green in CI first try (run `28665167434`). Pre-Phase-4 cleanup from on-device
testing (creator, 5 items; scope locked via AskUserQuestion).

### v0.9.0 — what changed (`versionCode 10`)
1. **Multi-select + bulk delete** in the **deep pillar view** and the **Inbox** (long-press → checkboxes
   → "N selected" bar → Delete). `EntryDao.deleteByIds` / `EntryRepository.deleteMany`; both screens
   guard against stale selection (`retainAll(presentIds)`). Resolve chips / ⋮ menus still work (child
   taps aren't swallowed by the selection `combinedClickable`).
2. **On-device transcription removed** (creator: "it's crap"). `SpeechToText` deleted; voice is
   **cloud Whisper (Groq) only**. No key → a clear **"add your key / type instead"** state (never a
   dead mic); typing always works. Settings engine toggle gone (one cloud-only note). Framework
   refine-by-voice and the role field also drop on-device (role field is now **type-only**).
3. **Sub-folders under ANY framework category** (creator: flexible folders whose names feed the AI).
   `ProjectEntity.goalArea` now means *category (any pillar)*, not just goal areas. The **Framework
   editor** manages each category's sub-folders (add/rename/delete chips); **Home ⇄ Framework stay in
   sync** (one `projects` table). Renaming a category **cascades** to its folders
   (`renameCategory`); deleting a category deletes its folders (`deleteByCategory`). **Room v2→v3**:
   `projects` unique index is now composite **`(name, goalArea)`** so the same folder name can live
   under different categories (`MIGRATION_2_3` drops `index_projects_name`, creates
   `index_projects_name_goalArea`).
4. **Home ⇄ Framework project sync** — automatic (both read `ProjectDao`): a folder added on Home
   under a goal area shows in the Framework editor and vice-versa.
   **AI context:** the categorizer's framework block is enriched with **every category's sub-folder
   names** (`EntryProcessor.frameworkBlockWithFolders`); the `{{PROJECTS}}` *placement* list stays
   goal-area folders only (behaviour/growth folders are context, not a new placement slot).
5. **Number nudge rebuilt at the transcript** (the old post-save nudge silently skipped when a spoken
   number-word made `hasMeasurable` true). Now, on the **review screen**, an always-available
   **"Add a number?"** → **record a short second clip** (Groq) *or* type → appended to the
   transcript → **Add** files the combined text so the AI cleans it into one bullet. Never blocks;
   Add is disabled only while a number clip is recording/transcribing; a typed-but-uncommitted number
   is folded in on Add. Typed capture keeps its post-save nudge.

### Adversarial review before tagging (compile + logic; fixed pre-tag)
Compile: **clean**. Logic: 1 MED + 2 LOW, all fixed:
- **[MED]** a duplicate sub-folder name silently no-op'd (global-unique index) → fixed by the composite
  `(name, goalArea)` unique index + migration (same name allowed across categories).
- **[LOW]** pillar multi-select lacked the Inbox's stale-selection guard → added `retainAll`.
- **[LOW]** tapping Add with a typed-but-uncommitted number dropped it → `confirmAdd` now folds it in.
- Verified fine: no-key voice path (no crash/loop), recorder reuse + temp-file cleanup across
  main/number takes, refine-replace folder-orphaning is cosmetic (entries survive via the
  Uncategorized catch-all), `combinedClickable` doesn't swallow child taps.

---

## v0.8.0 — Phase 3 · Living document + Inbox resolve ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.8.0/BragBuddy-v0.8.0.apk` (signed; `.aab`
alongside). Built green in CI first try (run `28660160397`) — unit tests incl. the new `HomeDocTest`,
then signed `assembleRelease`/`bundleRelease`.

**Home is now the structured document** (Design System §1 · "Home — your living document"): goal /
growth **pillars** hold **project cards** (name · N entries · updated); behaviour pillars gather the
entries that **evidence** them; the **Inbox** peek waits at the end. Tapping any pillar/project opens
the **deep pillar view** (blurb → projects → dated bullets with "also evidences" cross-links + Extra
chips; add-entry-to-project = anchored capture; per-entry edit/redo/delete live here now). The
**Inbox resolves in a tap** — a suggested project, any folder, or "Outside project" — no AI re-call.

### v0.8.0 — what was built (`versionCode 9`)
Creator answers via AskUserQuestion: Home depth = **full deep pillar screen**; Inbox resolve targets =
**suggested + folders + Outside project**.
- **`ui/home/HomeDoc.kt`** — pure, unit-tested shaping (`buildHomeDoc` + `goalProjectGroups` /
  `behaviourEvidence` / `uncategorizedEntries`). Groups PROCESSED entries under goal pillars by
  project (folder names canonicalised; blank/`Outside-project`/`Inbox`/unknown → one **Outside
  project** bucket; empty folders still shown). Behaviour pillars gather entries via `demonstrates`.
  RAW → a **"Filing…" processing** strip; INBOX+FAILED → the **Inbox peek**. **One entry, two places**
  is a computed view, never a duplicated row. Pillar dot colours = `pillarColor(indexInFramework)`.
- **`HomeScreen` rewritten** to the overview (sections → cards; role prompt hoisted above so a new
  user still sees it; empty-state gains a "New project"). The v0.6.0 horizontal folder row is **gone**
  — projects now live in the document; anchored capture moved to the deep view's "Add entry to
  <project>" (mechanism unchanged: `EXTRA_PROJECT`). **Flag:** one-tap folder capture is now two taps
  (open pillar → Add entry); say the word to add a shortcut back.
- **`ui/pillar/PillarDetailScreen` + `PillarDetailViewModel`** (NEW, nav route `pillar/{pillarId}`,
  `SavedStateHandle`) — the depth "one tap in": About-this-pillar blurb, projects with dated bullets
  (or behaviour evidence), add-entry (anchored) / add-project / add-detail, and **edit / redo /
  delete per entry** (reuses `EntryRepository.replaceText` → single-row re-file; the v0.6.0 split-
  sibling data-loss trap is **not** reintroduced).
- **Inbox tap-to-resolve** (`InboxScreen` + `InboxViewModel`): suggested-project chips + an "Other
  folder ▾" picker + "Outside project", each files in one tap via **`EntryProcessor.resolve` /
  `EntryRepository.resolve`** — sets project + goal area, keeps the model's bullet/behaviours, marks
  PROCESSED, **no AI re-call**, under the same processing `Mutex` (INBOX/FAILED-only guard, can't race
  `process()`). A suggestion that isn't a folder yet is **created** so the entry lands where tapped.
  FAILED entries keep **Try again**. **No Room schema change** — DB stays v2.
- **Nav:** `Routes.pillar()` + a pillar `composable` in `BragNavHost`; `MainScaffold` threads
  `onOpenPillar` and wires Home's "Review →" to switch to the Inbox tab. Summary tab still a Phase-5
  placeholder; the design's header "Summarise" button stays deferred (Settings gear remains).

### Adversarial review before tagging (compile + logic; fixed pre-tag)
Ran both passes per the protocol. Compile: **1** breaker (missing `lazy.items` import in
`PillarDetailScreen`) → fixed. Logic:
- **[HIGH · invariant]** a PROCESSED entry whose `goalCategory` matched no pillar and evidenced no
  behaviour (framework renamed/refined after filing, or model drift) vanished from every Home
  surface. **Fix:** `uncategorizedEntries` catch-all → a synthetic, navigable **"Uncategorized"**
  section (triage-only deep view: re-home via edit/redo). Nothing filed can disappear. Unit-tested.
- **[MED]** resolving via a suggested chip that wasn't an existing folder collapsed the entry into
  "Outside project" (contradicting the tap) → now creates the folder first (idempotent).
- **[LOW]** Home's `stateIn` initial no longer asserts `isEmpty=true` (was flashing the empty CTA on
  cold start). Two Outside-bucket edge collisions noted as acceptable/cosmetic.

---

## v0.7.0 — Static impact coaching (no AI) ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.7.0/BragBuddy-v0.7.0.apk` (signed;
`.aab` alongside). New: a **free, local** nudge to add numbers to entries — a persistent capture hint
plus, after saving an entry with no number, a **skippable "Add a measurable result?"** sheet.

### v0.7.0 — impact coaching (100% local, no LLM call)
Creator answer via AskUserQuestion: nudge appears **in the capture sheet, before dismiss**. Design
files have no such component (flagged) — built from existing tokens (sheet, pills, greyed hints).
- **`data/impact/ImpactCheck.hasMeasurable(text)`** — local regex only: any **digit**, currency
  (`% ₹ $ € £`), or a spelled-out **number word** (`one`–`twelve`, `dozen`, `couple`, `hundred`…),
  word-boundaried so "someone"/"tension" don't false-positive. No network, instant, free, all tiers.
  Unit-tested (`ImpactCheckTest`).
- **Persistent greyed hint** in the capture field: "Tip: add a number if you can — %, time, ₹, count,
  people." (replaced the v0.6.0 voice project-tip to avoid two "Tip:" lines; the type placeholder
  keeps the project example).
- **Post-save nudge (in-sheet):** `save()` runs the local check. Has a number → dismiss **instantly**
  (unchanged `_saved` → toast → finish). No number → the sheet shows **"Saved ✓ · Add a measurable
  result?"** with **[Add number] [Skip]** (`CaptureUiState.savedNudge`, `SavedNudgeSheet`). The entry
  is **already saved** before the nudge — it never blocks; **Skip / tap-outside / back → dismiss**.
  **Add number** opens a **TYPED** field (never records), appends the metric, and re-files through the
  **existing** categorizer (`replaceText` — the nudge itself adds no LLM call). Redo/replace supported.
- **Skipped** the optional "nudge less often for consistent users" (would add persisted streak state).
- Categorizer / Inbox / raw-transcript safety nets untouched. Adversarial review (compile + logic)
  came back **clean** — 0 findings.

---

## v0.6.0 — Job role + project folders/anchoring ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.6.0/BragBuddy-v0.6.0.apk`. Home Projects
row (folder → capture into it), first-run role prompt, Settings **Your role** + **Project folders**.
Room DB → **v2** (`anchorProject` migration).

### v0.6.0 — two additive features (creator request)
Answers via AskUserQuestion: role = **Settings + a gentle first-run prompt**; folders = **a Projects
section on Home**. Both flagged as UI not in the design files before building.

**Feature 1 — Job role (AI context; never the company name):**
- `SettingsStore.jobRole` (device-local) + `rolePromptDismissed`. Editable in Settings ("Your role"
  card — `ui/role/RoleInput` type-or-speak via on-device STT + example chips) and a dismissible
  first-run prompt on Home (`HomeViewModel.showRolePrompt = jobRole blank && !dismissed`).
- Injected into **both** prompts (categorizer + summary) **and** framework-refine. Sharpens core-duty
  vs. beyond-scope/leadership + impact; **informs, never dictates** (doesn't move normal work out of
  its goal area). `CategorizeRequest.role` / `SummaryRequest.role` / `FrameworkRefineRequest.role`.

**Feature 2 — Project folders + low-friction anchoring:**
- Projects (folders) on Home as cards (`FoldersRow`): create (`ProjectRepository` over the existing
  `ProjectDao`; goal area defaults to the framework's first goal category) and **tap → capture
  straight into that project** (`CaptureActivity.EXTRA_PROJECT`). Full management in Settings.
- **Explicit anchor** stored on the entry: `EntryEntity.anchorProject` (**Room v1→v2** additive
  `ALTER TABLE` migration — existing data preserved; `DatabaseModule.addMigrations`). The categorizer
  is told the anchor and **`EntryProcessor` also honours it deterministically**: folder-tap fixes the
  project (+ its goal area) on the row **and its split siblings** and marks them PROCESSED — the
  folder wins even if the model drifts; behaviour/leadership tagging stays an AI call (role-helped).
  A non-anchored capture behaves exactly as before (confidence < 0.6 / "Inbox" → Inbox).
- Capture sheet: an **"In <project>" banner** when anchored; a **greyed optional** project hint when
  not (never required — free talk/type still works, an entry may span projects or none).
- Prompts: categorizer gains ROLE + an optional PROJECT ANCHOR it must honour (skip guessing);
  summary + framework gain ROLE. `AiPromptsTest` extended.

### Adversarial review before tagging (compile clean; 2 logic fixes pre-tag, commit `6adb12a`)
- **Edit/Redo data-loss trap on multi-item captures:** split siblings all carry the full transcript,
  the Edit dialog prefilled that full text on a card showing one bullet, and `replace()` ran
  `deleteSiblings` — so trimming it dropped the peers. Fix: **`replace()` re-files as a SINGLE entry**
  (no re-split, no sibling deletion; `deleteSiblings` retired along with the fragile
  createdAt-as-group key), and **Edit prefills the bullet** for a filed entry. Editing one entry can
  no longer disturb the others. (Trade-off: redo of a multi-item note becomes one entry — fine for a
  fix-this-entry action.)
- **Settings role blank-Done wiped the saved role** → guarded `onImeDone` with `isNotBlank`.

---

## v0.5.0 — Phase 2 + cleanup batch ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.5.0/BragBuddy-v0.5.0.apk`. Voice capture
review-before-Add; Home ⋮ Edit/Redo/Delete; Framework "categories" + Refine by voice.

### v0.5.0 — post-testing cleanup batch (creator feedback, 9 items)
Answers taken via AskUserQuestion: word = **"category"** (not "pillar"); Home = **Edit (retype+save)**
+ **Redo (re-record from scratch)** + Delete; category **details = an editable description**; voice
capture gets a **review-before-Add** step (voice only; typed stays instant).
- **UI fixes:** status-bar inset on Home/Framework/Inbox (`statusBarsPadding`); bottom-bar
  "Framework" no longer wraps (smaller label + `maxLines=1`).
- **Capture rework:** after Stop, the transcript shows **editable** with **Add / Re-record / Cancel**
  — nothing saves until Add; a close ✕ cancels anytime (`VoicePhase.REVIEW`, `enterReview`,
  `confirmAdd`, `reRecord`). Typed unchanged. **Redo** re-records over the entry via
  `CaptureActivity.EXTRA_REPLACE_ID` → `replaceText` (replace mode; cancel keeps the original).
- **Home entry actions:** ⋮ menu → Edit text (dialog → `replaceText` → re-file), Redo, Delete
  (confirm). `EntryDao.deleteById`/`deleteSiblings`; `EntryRepository.delete`/`replaceText`.
- **Categories:** "category" wording everywhere user-facing (internal `Pillar`/`PillarKind`
  unchanged); each has an **editable description** (feeds the categorizer); add/edit dialogs include it.
- **Refine-by-voice:** now uses the **same Groq cloud transcription** as capture (was on-device STT —
  the "not connected" feeling; gated on `groqApiKey` present, not the capture engine); the prompt is
  **instruction-aware** (apply add/rename/remove/re-describe, keep the rest); distinct **"REFINING
  YOUR FRAMEWORK"** sheet with a transcribing state.

### Adversarial review before tagging (8 bugs caught + fixed pre-release)
Compile pass clean; logic pass found 8, all fixed in commit `4a80bba` **before** the tag:
1. **[HIGH]** on-device voice review was torn down ~1.5s after transcription (the STT force-submit
   fallback only checked `!didSave`; review saves later) → now also gated on `submitting`.
2–3. **[MED]** refine sheet could resurrect after Cancel during "Thinking", and a late cloud-transcribe
   callback could bleed into a re-opened refine → in-flight coroutine tracked in `refineJob`, cancelled
   on dismiss/reopen/start-over + a `Thinking`-state guard.
4–5. **[MED]** editing/Redoing a **split** entry duplicated its sibling rows, and an edit could be
   clobbered by an in-flight categorization → `replace()` moved into `EntryProcessor` under the same
   `Mutex` and deletes siblings (shared `createdAt`) before re-filing.
6. **[MED]** first-capture race auto-started voice under the default SPEAK before the saved mode loaded
   → auto-start waits on an `initialized` flag (`LaunchedEffect(mode, initialized)`).
7–8. **[LOW]** audio temp-file leak after a failed take + typed save; unguarded cloud Stop double-tap.

---

## v0.4.1 — Phase 2 AI categorization ✅ DONE (verified green · signed · first-try CI)

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.4.1/BragBuddy-v0.4.1.apk`. Settings →
**AI brain (Groq)** → paste your **Groq key** (the same single key that powers Cloud Whisper),
stored on-device only.

### v0.4.1 — AI runs on Groq (single key), not OpenRouter
Creator's call (asked directly): reuse the one Groq key instead of a second OpenRouter signup. Groq
runs LLMs too (OpenAI-compatible), so the categorizer + framework refine now use the **same on-device
Groq key** as cloud Whisper — faster, and Groq's API doesn't train on the data. `OpenRouterAiProvider`
→ **`GroqAiProvider`** (reads `groqApiKey`); `AiConfig` repointed to `api.groq.com/openai/v1` with
`llama-3.3-70b-versatile` (categorizer) + `llama-3.1-8b-instant` (fallback) + `openai/gpt-oss-120b`
(summary) — **verified live** against console.groq.com/docs/models (the OpenRouter `deepseek-v3:free`
slug I'd first picked had already been pulled — exactly why the slug lives in one file). The
`AiProvider` seam is unchanged, so a public launch can still route the **summary** to a paid provider
model. Settings collapsed to **one** canonical "AI brain (Groq)" key card (the Transcription card
points at it — no duplicate field). `openRouterApiKey` removed; `aiEnabled = groqApiKey set`.

### What was built (Phase 2, `versionCode 4`)
- **AI brain wired behind the seam.** `data/ai/GroqAiProvider` replaces `StubAiProvider` in
  `di/AiModule` (one-line bind). OpenAI-compatible **Groq**, reusing the on-device `groqApiKey` (read
  fresh per call). **Two models by task + fallback** (`data/ai/AiConfig` — the single place slugs
  live, remote-config-ready): categorizer `llama-3.3-70b-versatile`, fallback `llama-3.1-8b-instant`;
  summary `openai/gpt-oss-120b` (Phase 5, not exercised yet). On any transient/HTTP/parse failure the
  provider tries the fallback model, then fails safe. (v0.4.0 shipped this on OpenRouter; v0.4.1
  switched to Groq per the creator — see the top section.)
- **Baked prompts** (`data/ai/AiPrompts`) — Part A daily categorizer + Part C framework refine + Part
  B summary, verbatim from `../PRD/BragBuddy-System-Prompt.md`, with runtime injection (today /
  framework / projects). `data/ai/AiJson` does lenient JSON extraction (strips ```-fences/prose,
  first `{`…last `}`) — **unit-tested** (`CategorizeParsingTest`).
- **Categorization pipeline** (`data/entry/EntryProcessor`, kicked from `EntryRepository.capture` on
  an app-lifetime `CoroutineScope` — `di/CoroutinesModule` `@ApplicationScope`). Off the capture
  path (never blocks). Splits a multi-item transcript into sibling rows (each keeps the transcript),
  fills every field, and sets status: **confidence < 0.6 or an "Inbox" placement → INBOX**;
  AI/parse/network failure → **FAILED (transcript kept, shown in Inbox)**; empty result → INBOX.
  **Never loses an entry.** Drained on launch (`MainActivity`) for interrupted runs.
- **Framework refine-by-voice** (`ui/framework/`) — `FrameworkStore` (DataStore JSON) persists the
  editable framework, defaulting to the shipped `Framework.DEFAULT`. Framework tab: pillars grouped
  by axis (*What you did* / *How you did it*) with the ramp colours, rename/remove/add; **Refine by
  voice** → speak (on-device STT) or type how you're judged → AI builds pillars → shown as editable
  cards for a **one-tap confirm**. **Company name never asked** (prompt forbids it; no field exists).
- **Settings** — new **AI brain (OpenRouter)** key card (mirrors the Groq card; on-device only; the
  screen is now scrollable). **Home** cards now show the cleaned bullet + a placement/status chip
  (Processing… / goal area / Inbox / Extra). **Inbox** tab: read-only list of INBOX+FAILED entries
  with a bottom-bar badge and a **Try again** on failures. `MainScaffold` routes the Framework +
  Inbox tabs (were placeholders).

### Decisions / deviations / flags (Phase 2)
- **Scope line held to the Build Brief phase list:** the *structured pillar Home document* and the
  *Inbox tap-to-resolve quick-confirm* (assign a `suggestedProject`) are **Phase 3** — not built
  here. Home stays a flat list (now AI-enriched); Inbox is read + retry only.
- **Refine-by-voice uses on-device STT** (not cloud Whisper) — smallest reliable surface for a short
  description; typing is a peer. Cloud STT for refine can come later.
- **Drag-to-reorder pillars deferred** (rename/add/remove + the AI refine are in). Reorder is a
  later polish.
- **FAILED entries don't auto-reprocess when the key is *finished* typing.** `setOpenRouterApiKey`
  best-effort re-runs failed on the blank→non-blank edge (fires on the 1st char, so mostly a no-op).
  The reliable path: **Inbox → Try again**, or just add the key *before* capturing. Made idempotent
  (see below) so it can't duplicate.
- **generateSummary is implemented but unexercised** (no caller until Phase 5); kept complete so the
  seam is real, not a stub.

### Adversarial review before tagging (per the release protocol)
Ran a compile pass (clean — 0 findings) **and** a logic pass **before** tagging. The logic pass
caught three real bugs, all fixed in commit `638bfd0` before the release:
1. **Crash-loop / fail-safe hole:** a throw while *building* the request (OkHttp rejects a malformed
   key char in a header) or reading DataStore escaped `runCatching` and the fire-and-forget scope →
   crash, row stuck RAW → re-throw + crash-loop next launch. Fix: whole `callChat` body (incl.
   request/header build) inside `runCatching`, **plus** an `EntryProcessor.process` catch-all that
   lands any throw in FAILED (visible). Fail-safe restored.
2. **Duplicate sibling rows:** capture's kick + a launch-time drain during a config change could both
   see a row RAW and both insert its split siblings. Fix: `process()` serializes on a `Mutex` and
   **re-reads status inside the lock**, processing only RAW/FAILED (PROCESSED/INBOX skipped) — never
   split twice; also makes a double `reprocessFailed()` idempotent.
3. Removed the unused `force` param; Inbox retry routes through the same guarded path.

### How it was tested
- **Built green in CI, first try** (Android Release workflow, run `28608099310`): `testDebugUnitTest`
  (incl. the new `CategorizeParsingTest`) then a **signed** `assembleRelease`/`bundleRelease` →
  published `BragBuddy-v0.4.0.apk` + `.aab`. No CI round-trips were needed (careful pre-commit
  compile review + the adversarial pass).
- On-device smoke test is the creator's step: add the OpenRouter key, capture a real update, confirm
  it's cleaned + filed (or lands in Inbox), and try Refine-by-voice.

---

## v0.3.0 — cloud transcription ✅ DONE (verified green · signed) · v0.2.0 = Phase 1 · v0.1.0 = Phase 0

**APK:** `github.com/aucksy/bragbuddy/releases/download/v0.3.0/BragBuddy-v0.3.0.apk`. To use it:
Settings → Transcription → Cloud Whisper → paste a Groq key (`gsk_…` from console.groq.com). CI
caught 1 compile error (missing `import androidx.compose.foundation.layout.Box` in SettingsScreen —
its 4 "@Composable invocations" errors were cascade from the unresolved `Box`).

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

## Next — Phase 7: Reliability + retention polish (Build Brief phase list) — the LAST Android phase
Phases 0–6 are the full core loop (capture → AI categorize → living doc → edit/copy → summary → Drive
backup). Phase 7 is the reliability/retention layer that makes it dependable on a real device:
- **OEM alarm / battery-optimization wizard** (ColorOS / Find X9s etc.) so the daily reminder survives
  aggressive OEM battery management — a guided "allow background / exact alarms" flow.
- **On-open "you haven't logged" fallback** + a gentle **weekly catch-up** prompt (Design System §7 ·
  "Anything bigger this week?") and an **early preview summary** in week 1 (Design System §7 · a real
  preview from a handful of entries, to make the payoff felt before review season).
- **Offline queue / error states**: a capture made offline recovers cleanly; AI-failed entries retry;
  clear, calm error surfaces. (Much of the never-lose-an-entry spine already exists — this hardens it.)
- *Testable: reminders fire reliably on the creator's Find X9s; an offline entry recovers; nothing is
  lost.* After Phase 7, **iOS** is the next frontier (everything under "Out of scope" stays parked).

**Note:** `USE_EXACT_ALARM` is Play-restricted — a pre-Play item (BragBuddy ships as a direct signed
APK, not Play). No Play wiring until the creator asks.

**Owner setup still pending for Phase 6 (Drive):** add a `com.bragbuddy.app` **Android OAuth client** +
the release SHA-1 (`B8:B2:F2:86:05:BF:C8:44:94:98:E9:58:02:EA:55:74:9E:58:A4:D3`) to project
`gmailapi-491903` — Drive sign-in fails until then; local Export/Import works regardless.

**Groundwork already in place (shipped):** rollup + summary (`data/rollup/`, `data/summary/`,
`ui/summary/`), Drive backup (`data/drive/`, `data/backup/`, `ui/backup/`). The **Groq key** (Settings →
AI brain) powers categorizer + Cloud Whisper + summary — one key, on-device only.

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
