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
**Android is feature-complete** (Build Brief Phases 0–7 all shipped). Next frontier: **iOS port**
(everything under "Out of scope" stays parked). On-device verification for this phase: reminders fire
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
