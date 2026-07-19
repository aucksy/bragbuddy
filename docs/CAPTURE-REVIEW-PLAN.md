# Feature batch — capture-review + fixes (owner, 2026-07-19)

Five owner requests, planned as **4 phases, one signed APK each**. This doc is the durable spec so each
phase can run in its own chat (per `CONTEXT.md` §3). Live status stays in `PROGRESS.md`.

## Owner decisions — LOCKED (AskUserQuestion 2026-07-19, do not re-litigate)
1. **Item 5 review flow applies to ALL captures** — reminder AND in-app "+", voice/typed/scanned. The
   recording/typing is still never interrupted; only the POST-submit behaviour changes.
2. **The Inbox becomes an offline/failure tray ONLY.** Nothing captured online ever silently lands there
   again — you place it at capture. It survives as the safety net for offline + AI-unreachable captures
   (rename it, e.g. "To file" / "Needs network"). **"Never lose an entry" still holds.**
3. **Unsure placement: must place it, with a "skip for now" escape** → skip routes to the offline/failure
   tray so the user is never trapped.
4. **Big-paste splitting: one entry per distinct achievement** (a 4-month dump may yield 20-40 entries).

## The founding-invariant change (be explicit in every phase)
`CONTEXT.md` §2 currently says *"Never block or interrupt capture. Fire-and-forget; unclear items wait in
the Inbox, never a mid-capture question. Force <0.6 confidence to Inbox."* Item 5 **deliberately retires
the fire-and-forget + Inbox-default half of this.** The new rule: capture is still never interrupted while
recording/typing, but AFTER submit the app opens a review, shows the AI working, shows the filed placement,
and asks the user to finalise anything uncertain. **`CONTEXT.md` §2 must be rewritten in Phase 4** (keep
"never lose an entry"; replace "fire-and-forget → Inbox" with "review-at-capture; Inbox = offline/failure
fallback"). Until Phase 4 ships, the old invariant still governs the code.

---

## Phase 1 — Bottom-bar truncation, whole-app audit + fix (items 3 + 4) · LOW risk · ✅ SHIPPED v0.35.0 (2026-07-19)
> **DONE.** `versionCode 42`; compile+unit-test gate GREEN; adversarial layout pass (0 findings); UI-only → no
> eval gate. Fixed the 3 fragile surfaces below by moving the inset `Spacer` to be the **terminal child inside
> the `verticalScroll`** on `EntryDetailSheet` + `ProjectRemapSheet` (was an outer sibling after a non-weighted
> scroll → squeezed to ~0 when tall), and giving `PillarDetailScreen`'s `LazyColumn` the real nav inset. The
> whole-app audit confirmed these three were the ONLY fragile surfaces. Full detail: `PROGRESS.md` →
> `## Status: v0.35.0`. **Not device-tested (cloud-build only) — owner should eyeball the Recategorize sheet
> with the picker open.**

**Root cause (found, not guessed):** the standing rule (`ui/common/BottomBarInset.kt`) is right, but the
**Recategorize sheet** (`ui/entry/EntryDetailSheet.kt`) applies it with a FRAGILE pattern — the reserved
trailing `Spacer` is a **sibling placed AFTER a non-weighted `verticalScroll` column**. In a Compose
`Column`, the scroll child is measured first and greedily takes the remaining height, so once the content
overflows (the Recategorize picker open = the tallest content in the app) the spacer is squeezed to ~0 and
the **scrolled** Apply/Delete pills sit under the nav bar + FAB. Short sheets look fine → the bug "comes
back" whenever content is tall.

**Fragile surfaces to fix (scout inventory):**
- `ui/entry/EntryDetailSheet.kt` — the Recategorize sheet (Apply is scrolled, not pinned). **Primary.**
- `ui/common/ProjectRemapSheet.kt` — same sibling-spacer-after-scroll pattern.
- `ui/pillar/PillarDetailScreen.kt` — hardcodes `Spacing.s12` (48dp) bottom instead of
  `+ WindowInsets.navigationBars` (serves the pillar AND folder routes).

**Robust references to copy:** `ui/framework/CategoryEditSheet.kt` (uses `weight(1f)` on the scroll region
+ a terminal spacer INSIDE it), the Generate-summary sheets in `ui/summary/SummaryScreen.kt`,
`ui/home/AddImpactSheet.kt` (self-scrolling column, inset is the terminal child).

**Fix:** give the sheet a `weight(1f)` scroll region and PIN the Apply button below it (so the primary
action can never scroll under the bar), OR move the inset spacer INSIDE the scroll — match
`CategoryEditSheet`. Audit every surface in the inventory; leave the robust ones alone.

**Standing rule to reaffirm:** a bottom-anchored surface's trailing space = `<gap> + <systemNavInset> +
LocalBottomBarInset.current`, and it must live where the scroll can't eat it (inside the scroll, or below a
`weight(1f)` region). **Self-verify:** compile gate + reason against the robust references (cannot run the
app locally — cloud build only).

## Phase 2 — "Which days" reminder selector (item 1) · LOW–MED risk · ✅ SHIPPED v0.36.0 (2026-07-19)
> **DONE.** `versionCode 43`; compile+unit-test gate GREEN; independent adversarial review (compile-clean +
> logic sound; 1 MED rapid-tap lost-update race fixed atomically); no prompt/schema change → no eval gate.
> `SettingsStore.reminderDays` = a **7-bit mask** in DataStore (device-local, NOT backed up — mirrors
> `weeklyRecapEnabled`; absent key → all seven days so existing installs are unchanged). `ReminderScheduler.
> schedule(hour, minute, days)` advances the re-arm target to the next enabled weekday and **cancels +
> schedules nothing when the set is empty** (all days off = paused, never a never-firing alarm).
> `ReminderReceiver.ACTION_FIRE` gates `postReminder` on today∈days and re-arms to the next enabled day;
> boot/time-change re-arm passes days. Settings → Daily reminder gained a **7 weekday-chip row** (Mon-first,
> single-letter) under the Time row + a plain-English summary ("Reminds you on weekdays (Mon–Fri).",
> "…paused." when empty). The chip toggle is an **atomic read-modify-write** in the store (`toggleReminderDay`,
> xor inside one DataStore edit) so quick taps can't lose an update. Full detail: `PROGRESS.md` →
> `## Status: v0.36.0`. **Not device-tested (cloud-build only) — verified by reasoning + compile gate +
> review.**

Today = one daily EXACT alarm re-armed each night by `ReminderReceiver` (`reminder/ReminderScheduler.kt`,
`SettingsStore.reminderHour/Minute`). Add:
- `SettingsStore.reminderDays` (a Set<DayOfWeek> or 7-bit mask; device-local DataStore, NOT backed up —
  mirror `weeklyRecapEnabled`).
- Gate `Notifications.postReminder` + advance the re-arm target to the next ENABLED weekday in
  `ReminderReceiver.ACTION_FIRE` / `ReminderScheduler.nextTriggerMillis` (the weekly-recap `nextWeekly…`
  Sunday loop is the reference for day-of-week math).
- A weekday chip row in the "Daily reminder" card (`ui/settings/SettingsScreen.kt`, under the Time row
  inside `if (reminderEnabled)`), + `SettingsViewModel.setReminderDays` that persists and re-schedules.
- Edge: all days off = treat as reminder off (don't schedule a never-firing alarm).

## Phase 3 — Extract every achievement from a big paste (item 2) · ⚠️ EVAL-GATED · MED risk · ✅ SHIPPED v0.37.0 (2026-07-19)
> **DONE.** `versionCode 44`; compile+PromptSyncTest GREEN on the free debug gate; adversarial review (0 blocking);
> EVAL-GATED, 2 rounds. Categorizer rule 2 rewritten and SCOPED (a SHORT note = one entry, strengthened
> "task + its own detail/method/result/follow-up = same entry"; a LONG/LISTED paste = one entry PER distinct
> achievement, 20-40 normal) + calibrated **Example 5** (a numbered self-appraisal → 4 entries) + golden
> `paste-appraisal-split` (long multi-project paste, robust-minimum placements, over-split tolerated, no
> exact `entryCount`). Edited `AiPrompts.CATEGORIZER_SYSTEM` + `eval/prompts/categorizer-system.txt`
> (byte-equal) + the golden in the SAME commit. **Eval verdict: splitting PROVEN** (`SPLIT-CASE
> paste-appraisal-split placements:✓`, published via new `run.mjs` `::notice::` diagnostics);
> **placementAccuracy 97.1%, entryCountAccuracy 92.9% (UP — no over-split regression), no below-baseline
> regression**; RED only on the two PRE-EXISTING known-reds (`inboxPrecision` 22/26 = a one-case,
> within-tolerance nudge, deliberately NOT chased — out-of-scope calibration, never lower the threshold;
> `summaryChecks` = gpt-oss-120b flake, ≥ baseline, summary path untouched). **No `max_completion_tokens`
> needed** — a ~7-9-entry paste parsed cleanly (unset = model-max = no truncation). Full detail: `PROGRESS.md`
> → `## Status: v0.37.0`. **Not device-tested (cloud-build only) — the on-device proof of "big paste → many
> entries" is for the owner; the eval locks the behaviour + guards against over-splitting.**

**Confirmed:** the transcript is passed UNCAPPED end-to-end (only descriptions are capped via `TextCaps`);
there is NO app-side entry-count cap (`CategorizedNormalizer` + `processEntry` keep every entry); a plain
paste does NOT hit COMBINE mode. So the under-splitting is **purely the model's judgment** on prompt rules
1–2. This is a **prompt change** → eval-gated (edit `AiPrompts.CATEGORIZER_SYSTEM` + `eval/prompts/*.txt`
+ the `run.mjs` mirror in the same commit; budget ≥2 gate rounds).
- Strengthen splitting for a LONG multi-achievement paste (decision: one entry per distinct achievement)
  WITHOUT regressing "never split hair-thin" (a task + its detail = one). Add a calibrated example.
- Add goldens from a paste like the owner's screenshots (a structured self-appraisal → many entries).
- ⚠️ **Consider setting `max_completion_tokens`** on the Groq call (`GroqAiProvider`) — none is set today,
  so a 30-entry JSON relies on the model's default output limit and could truncate. Measure first.
- ⚠️ Inherits the KNOWN-OPEN `inboxPrecision` red gate (Example 3 vs `po-metric-30-percent`); do not
  "fix" it by lowering the threshold. See `## Status: v0.34.0`.

## Phase 4 — Capture → open → "AI is working" → review & confirm placement (item 5) · HIGH risk · LAST
The capstone; retires fire-and-forget. **Design it in two parts + a clickable mockup for owner sign-off
BEFORE building** (cannot render the real UI locally).

Current (scout map): reminder → Home "+" radial → `CaptureActivity` overlay → `EntryRepository.capture`
saves RAW + kicks `EntryProcessor.process` on the app scope (fire-and-forget) → 850ms "Saved / I'll sort
this out" → `finish()`. Processing is background, serialized by a mutex, ~seconds (≤60s worst case). No
"AI working" UI; entry later shows a passive `ProcessingCard` on Home + a "Filed ✓ → area" snackbar.

New flow:
- **4a (happy path):** after submit, keep a surface open; foreground/await the categorize with a brief
  progress animation ("BragBuddy is filing this…", min ~1.2s so it never just flashes); then show the
  filed entry(ies) with **Project ▸ Deliverable** (reuse the v0.34.0 structure) and a "Looks good" / correct
  action. Confident placements are shown + auto-accepted (correctable), not blocked.
- **4b (uncertain + Inbox rework):** when `statusFor` would be INBOX (low confidence / unplaceable), show
  the placement picker INLINE (reuse the Recategorize picker from `EntryDetailSheet`) and require a choice,
  with a **"skip for now"** → the offline/failure tray. Rework the Inbox into that tray (rename; nothing
  online lands there silently). Handle a LIST of entries (Phase 3 splitting can yield many). Handle
  offline/timeout/AI-fail: no result to show → fall back to today's "Saved for later" + the tray.
- Rewrite `CONTEXT.md` §2 (the invariant) here. Touch points: `CaptureActivity`/`CaptureViewModel` (don't
  finish on save; observe the row to a terminal status), `EntryProcessor.statusFor`, the Inbox UI, the
  retention/Home strips that reference the Inbox.

---

## Sequencing rationale
1 and 2 are quick, independent wins (build trust, immediate relief). 3 improves splitting, which 4's review
screen surfaces — so 3 before 4. 4 is the largest and changes the founding invariant, so it goes last with
its own design + mockup gate. Standing rituals per phase: debug-build compile gate → adversarial review →
(eval gate for Phase 3) → signed tag → direct APK URL → PROGRESS/CONTEXT handoff.
