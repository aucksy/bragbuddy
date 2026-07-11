# BragBuddy — Implementation Plan (subscription launch + AI-magic)

*Authored by Claude Fable 5 on 2026-07-11, designed to be executed by Claude Opus 4.8 (or any future session) one phase per chat. This document is the deep spec; the live phase pointer stays in `PROGRESS.md`; invariants and protocol stay in `CONTEXT.md` (both win over this doc if they ever disagree). Strategy rationale: `docs/PRODUCT-ASSESSMENT.md` (business) and `docs/AI-SYSTEM-ASSESSMENT.md` (AI brain) — read the relevant sections of both before starting any phase here.*

**Locked decisions (creator, 2026-07-11, via AskUserQuestion — do NOT re-litigate):**
managed AI key for all users at launch (BYOK stays ONLY as the test-mode setup until launch-readiness, then demotes to a hidden Advanced option) · pricing ₹149/mo · ₹999/yr India-first, single Pro tier · paid hook = the Review Pack · iOS only after Android proves conversion.

**Standing rituals for every phase (unchanged house rules):** clarify genuine ambiguity via AskUserQuestion → build → compile review + adversarial logic review (+ **eval-harness green for any AI-prompt phase**) → fix → tag the signed release → on-device test list for the creator → update `PROGRESS.md` + commit → suggest a fresh chat. Cloud-only builds; the CI compiler is the only gate; custom-scrim sheets only; re-verify Groq model slugs live before every tag; never violate a `CONTEXT.md` §2 invariant.

---

## Phase order

| Phase | Ships | Tag |
|---|---|---|
| **AI-0** | Eval harness + golden set (repo-only; no app change) | none (commit only) |
| **AI-1** | Categorizer magic + efficiency set (prompt restructure, rubrics, examples, routine labels, validator, Whisper vocab, caps) | v0.26.0 |
| **AI-2** | Summary fixes + impact-coach-at-capture (the USP move) | v0.27.0 |
| **M1** | Managed AI proxy (backend + injectable endpoint/auth; BYOK → Advanced) | v0.28.0 |
| **M2** | First-session & polish batch (onboarding aha, Home card diet, haptics/snackbars, weekly recap, quick wins) | v0.29.0 |
| **M3** | Play Store + Billing + paywall/trial + metering enforcement | v0.30.0 |
| **M4** | The Review Pack (docx/PDF/share export, prose mode, tone, gap-fill interview, premium writer) | v0.31.0 |
| **M5** | Capture speed pack (widget, QS tile, shortcuts, share-target, exported capture entry) | v0.32.0 |
| **M6** | Retention & season pack (streaks, monthly recap, review-season countdown/campaign) | v0.33.0 |
| **M7** | Review cycles (archive year, role-based framework presets) | v0.34.0 |
| **M8** | iOS (per parked research in `PROGRESS.md`) | — |

AI phases first: the brain is the product, the restructure cuts AI cost ~40-45%, and M1's proxy then inherits an already-measured, already-cheap brain. M2's quick wins may be pulled earlier if a phase comes in light — flag, don't silently do.

---

# Phase AI-0 — Evaluation harness + golden set (BUILD THIS FIRST)

**Goal:** every prompt/model change from now on ships eval-gated, the way code ships compile-gated. No app code changes; no release tag.

**Deliverables (all under repo `eval/`):**
1. `eval/prompts/` — canonical prompt text files: `categorizer-system.txt`, `categorizer-user.txt`, `summary.txt`, `impact-coach.txt` (introduced in AI-1; for AI-0, dump the CURRENT `AiPrompts` texts so the baseline is measured before any change).
2. **`PromptSyncTest`** (JVM unit test in the app module): asserts each `AiPrompts` constant equals the corresponding `eval/prompts/*.txt` (path `../eval/prompts/…` from the app module working dir; normalize line endings). Drift = red CI. This makes the eval and the app provably test the same words.
3. `eval/run.mjs` (Node 20, no heavy deps): loads golden cases, builds requests exactly like the app (same models `AiConfig` uses, `temperature 0.2`, JSON mode, primary + fallback reported separately), calls Groq with `GROQ_API_KEY` from env, concurrency ≤2, writes `eval/report.md` + non-zero exit on threshold failure.
4. `eval/golden/categorizer.jsonl` — one case per line: `{ "id", "transcript", "context": { "role", "framework", "projects", "routineTypes", "anchor" }, "expect": { "placements": [{ "project", "goalCategory" }], "entryCount", "routineTypes": [], "impactBand": [lo, hi], "inboxExpected": bool, "demonstrates": [] } }`. Fields in `expect` are individually optional — score only what a case specifies.
5. `eval/golden/coach.jsonl` — `{ "id", "bullet", "project", "projectDetail", "goalArea", "role" }`; scored by rubric (below), no expected string.
6. `eval/golden/summary/*.json` — 4 synthetic rollups (dense year / sparse 8-entry / routine-heavy / development-heavy) with expected structural properties.
7. `eval/tools/from-backup.mjs` — converts a `bragbuddy-backup.json` (the app's device export) into skeleton categorizer cases (transcript + the *currently filed* placement pre-filled as the tentative label) for hand-verification. **The creator's own record + their Recategorize corrections are the seed dataset.**
8. `.github/workflows/eval.yml` — `workflow_dispatch` only, needs repo secret `GROQ_API_KEY` (**owner gate — ask the creator to add it**), runs `node eval/run.mjs`, uploads `report.md` as an artifact.

**Metrics & ship thresholds (report all; gate on these):**
- Placement accuracy (project + goalCategory both right, after the AI-1 validator's snapping rules): **≥85%** (the PRD bar).
- Inbox discipline: ambiguous cases parked in Inbox (recall) **≥80%**; clear cases NOT parked (precision) **≥90%**.
- JSON validity across all calls: **100%** (fallback chain counts as pass only if primary-parse failure rate <10%).
- Routine-label reuse where the golden context provides a fitting label: **100%**.
- Impact within the case's band: **≥80%** of banded cases.
- Coach rubric (per question): ≤ ~18 words · names a concrete measure kind · grounded in `projectDetail` when given · **zero invented numbers** (hard fail) — pass **≥90%**.
- Summary structural checks: no duplicated accomplishment across the doc · arcs merged · every input metric preserved verbatim · development content in `development[]` not `goalAreas[]` (post-AI-2) · pinned included exactly once · `setAside` non-empty when items were dropped.

**Golden-set composition target (~60-80 categorizer cases):** the creator's real transcripts (majority) + synthetic edges: Hinglish/mixed-language, multi-item splits, shorthand/alias project mentions, praise-screenshot-derived text, routine bursts (same chore, varied phrasing — label-stability cases), relative dates, no-work content (expected `entries: []`), anchored captures, near-duplicate project names.

**Acceptance:** baseline report generated with the CURRENT prompts and committed (`eval/report-baseline.md`) — this is the "before" that AI-1/AI-2 must beat. README documents local usage (`GROQ_API_KEY=… node eval/run.mjs`).

---

# Phase AI-1 — Categorizer magic + efficiency (v0.26.0)

**Goal:** placement/scoring consistency up, cost down ~40-45%, no behaviour regressions. Eval-gated: AI-0 thresholds met AND ≥ baseline on every metric.

### 1a. Prompt restructure (cache-first) + new categorizer text
Replace `AiPrompts.CATEGORIZER` and its builder with a **two-part** prompt: a system message ordered *static-first → user-context-last*, and a user message carrying the per-call volatiles. Groq prefix-caching then covers the entire static block (~1,900 tokens incl. examples) on every call, and the context tail (framework/projects/role/labels) only changes when the user edits their setup. `{{TODAY}}` (changes daily) and the anchor/transcript move to the **user message**. `COMBINE_MODE` stays appended to the very end of the system message when `combineSingle` (cache-neutral).

Builder contract: `AiPrompts.categorizerSystem(framework, projects, role, routineTypes, combineSingle): String` and `AiPrompts.categorizerUser(today, projectAnchor, transcript): String`. `CategorizeRequest` gains `routineTypes: List<String>`. `GroqAiProvider.categorize` sends the two messages accordingly (`StubAiProvider` updated to match).

**The new system prompt — use this text verbatim** (`{{…}}` = builder replacements; keep exact wording, it is calibrated with the examples):

```
You are the processing engine inside "BragBuddy", a mobile app that helps an
employee keep a record of their work contributions for performance appraisals,
organised the way their company's appraisal form is structured.

Each request gives you: this standing instruction, then the user's CONTEXT (their
role, appraisal framework, project folders and known routine-work labels), and a
MESSAGE containing today's date, an optional project anchor, and ONE transcript
(spoken or typed) describing what the user did. Turn the transcript into clean,
structured, appraisal-ready entries.

WHAT TO DO
1. Read the transcript. It may describe one thing or several.
2. Split it into separate entries — one per distinct piece of work. Never split
   hair-thin: a task and its immediate detail are ONE entry.
3. For each entry write ONE concise bullet: professional, factual, past-tense,
   action-led; preserve meaning, names and technical terms; clear English even if
   the transcript mixes languages; invent nothing — no impact or numbers the user
   did not say; one sentence where possible.
4. "project": if the MESSAGE gives a project anchor, use it verbatim for every
   entry. Otherwise: the exact name of one of the user's projects, or
   "Outside-project", or "Inbox" (can't place it / maybe new / unsure). Users refer
   to projects loosely — by shorthand, a partial name, or what the project does;
   match against each project's name AND its description, and when a loose mention
   clearly fits exactly one listed project, use that project. Never invent a
   project; if it could fit more than one, prefer "Inbox" and fill
   "suggestedProjects" with the 1-2 best matches.
5. "goalCategory": the goal area this counts toward. If it belongs to a known
   project, use that project's goal area. For Outside-project work pick the
   best-fitting goal area. If unsure, "Inbox".
6. "demonstrates": list the behaviours/competencies from the framework this work
   GENUINELY evidences, judged against each behaviour's description (this stays
   your decision even when the project is anchored). Tag a behaviour only when the
   work clearly shows it — never inflate. Empty list if none.
7. "isExtra": true only if clearly beyond this person's normal duties FOR THEIR
   ROLE (mentoring, helping another team, an initiative they started, fixing
   something not theirs). A core deliverable of their role is NOT extra. Else false.
8. "impact": how appraisal-worthy this is, anchored to these bands:
   0.2 = routine task done well · 0.4 = useful contribution, limited reach ·
   0.6 = solid deliverable milestone · 0.75 = shipped outcome with a visible
   result or metric · 0.9+ = major outcome (metric moved, cross-team or
   leadership visibility). Most everyday work is 0.3-0.6; reserve 0.8+ for
   genuinely standout work. Weigh outcome, scale, difficulty, visibility, goal
   alignment, and extra/leadership work. Provisional; the summary step re-judges.
9. "routine": true if this is repetitive/business-as-usual work better counted in
   bulk than listed on its own (e.g. one of many support tickets). When true, add
   "routineType": if one of the user's existing routine labels in CONTEXT fits,
   reuse that EXACT label; only coin a new short label for a genuinely new kind of
   routine work. Notable one-offs are routine=false.
10. Optional, only when explicitly stated: "metric" (a number/result the user
    mentioned) and "dateMentioned" (ISO date) — set dateMentioned ONLY for an
    explicit date or an unambiguous relative day ("yesterday", "last Friday",
    computed from the date in the MESSAGE); if in doubt, omit it.
11. "confidence", anchored: 0.9+ = the transcript names a listed project, or the
    anchor fixes it · 0.7-0.8 = a loose mention that clearly fits one project, or
    a solid goal-area fit for Outside-project work · below 0.6 = you are not
    sure — use "Inbox" placement instead of guessing.

WHAT NOT TO DO
- Don't include non-work content (greetings, filler, personal chat).
- Don't merge unrelated tasks. Don't add outcomes the user didn't state. Don't
  tag behaviours not clearly shown.
- Output only the JSON below — no prose, no markdown, no code fences.

OUTPUT
{
  "entries": [
    {
      "bullet": "string",
      "project": "exact project name | Outside-project | Inbox",
      "goalCategory": "exact goal-area name | Inbox",
      "demonstrates": ["behaviour name"],
      "isExtra": false,
      "impact": 0.0,
      "routine": false,
      "routineType": "string (only when routine is true)",
      "metric": "string (optional - omit if none)",
      "dateMentioned": "YYYY-MM-DD (optional - omit if none)",
      "confidence": 0.0,
      "suggestedProjects": ["string (optional)"]
    }
  ]
}
If there is no usable work contribution, return exactly: { "entries": [] }

EXAMPLES (illustrative setup — Role: Product Owner. GOAL AREAS = Performance
Goals. BEHAVIOURS = Leadership & Behaviours: ownership, collaboration, courageous
decisions. Projects = Raven Migration [Performance Goals] — servicing-comms
migration across markets; SharePoint Request System [Performance Goals] —
access-request tooling. Existing routine labels: "access requests".)

Example 1 — notable project work, loose project mention:
Transcript: "Finished testing the raven thing for three more markets and signed
them off."
{ "entries": [
  { "bullet": "Completed and signed off Raven Migration testing for three additional markets.",
    "project": "Raven Migration", "goalCategory": "Performance Goals",
    "demonstrates": [], "isExtra": false, "impact": 0.7, "routine": false,
    "confidence": 0.85 }
] }

Example 2 — two items; one routine (existing label reused), one extra + leadership:
Transcript: "Cleared about a dozen SharePoint access requests. Also ran a
cross-team session to unblock three teams stuck on the migration, even though it
wasn't my job."
{ "entries": [
  { "bullet": "Cleared a dozen SharePoint access requests.",
    "project": "SharePoint Request System", "goalCategory": "Performance Goals",
    "demonstrates": [], "isExtra": false, "impact": 0.3,
    "routine": true, "routineType": "access requests", "confidence": 0.9 },
  { "bullet": "Led a cross-team session that unblocked three teams on the Raven Migration.",
    "project": "Raven Migration", "goalCategory": "Performance Goals",
    "demonstrates": ["Leadership & Behaviours"], "isExtra": true, "impact": 0.85,
    "routine": false, "confidence": 0.85 }
] }

Example 3 — metric, no matching project → Inbox:
Transcript: "Built that new leadership dashboard, cut reporting time by about 30
percent."
{ "entries": [
  { "bullet": "Built a new leadership reporting dashboard.",
    "project": "Inbox", "goalCategory": "Inbox", "demonstrates": [],
    "isExtra": false, "impact": 0.75, "routine": false,
    "metric": "reduced reporting time by ~30%", "confidence": 0.5,
    "suggestedProjects": [] }
] }

Example 4 — mixed-language, clear work, no project signal:
Transcript: "Aaj client call me pricing issue sort kar diya, unko naya quote
bhej diya same day."
{ "entries": [
  { "bullet": "Resolved a client pricing issue on a call and sent the revised quote the same day.",
    "project": "Outside-project", "goalCategory": "Performance Goals",
    "demonstrates": [], "isExtra": false, "impact": 0.5, "routine": false,
    "confidence": 0.7 }
] }

CONTEXT (the user's own setup — changes rarely)
- The user's job role: {{ROLE}}
  Use the role to judge what is CORE duty versus BEYOND scope / leadership. It
  informs "isExtra", "impact" and behaviour tags; it never moves normal work out
  of its goal area.
- The company's appraisal framework:
{{APPRAISAL_FRAMEWORK}}
  GOAL AREAS = the "what" (results and project work map here).
  BEHAVIOURS/COMPETENCIES = the "how" (tag work that genuinely demonstrates
  these, judged by their descriptions). Optional DEVELOPMENT areas. If this
  framework is empty, use only project / "Outside-project" / "Inbox".
- The user's current projects (each tagged with the goal area it rolls up to):
{{PROJECTS}}
  If empty, treat all work as "Outside-project" or "Inbox".
- The user's existing routine-work labels (reuse when one fits):
{{ROUTINE_TYPES}}
```

**User message template** (`categorizerUser`):
```
Today's date: {{TODAY}}
Project anchor for this note: {{PROJECT_ANCHOR}}
Transcript:
{{TRANSCRIPT}}
```
(`{{PROJECT_ANCHOR}}` = the folder name or `none`; when set, rule 4's anchor clause governs.)

### 1b. Context enrichment & bounding (code)
- **Behaviour blurbs back:** `FrameworkPrompt.categorizerBlock` — BEHAVIOUR and DEVELOPMENT pillars render `- Name: blurb` again (goal areas stay names-only, preserving the B2b property that editing a goal-area detail affects only summaries).
- **Routine labels:** new `EntryDao` query `SELECT routineType FROM entries WHERE routineType IS NOT NULL AND routineType != '' GROUP BY routineType ORDER BY COUNT(*) DESC LIMIT 20`; `prepare()` injects as `{{ROUTINE_TYPES}}` (`(none yet)` when empty).
- **Description caps:** in `prepare()`, truncate each project description to **300 chars** (word boundary + `…`); same cap for `projectDetail` in `HomeViewModel.loadImpactSuggestion`. Add a one-line hint under the project-detail field in `CategoryEditSheet`: *"A line or two is plenty — this rides along every time the AI files an entry."*

### 1c. Output validation (code — pure, unit-tested)
New `data/entry/CategorizedNormalizer.kt`: `normalize(result, placementProjects, pillars): CategorizeResult`, applied in `processEntry`/`refileSingle` between `categorize` success and `applyCategorized`:
- `project`: case-insensitive/whitespace-normalized match against placement names → snap to canonical casing; no match and not `Outside-project`/`Inbox` → set `Inbox`, prepend the model's string to `suggestedProjects` (distinct), and cap `confidence` at 0.5 (Inbox floor routing does the rest — nothing lost, nothing phantom).
- `goalCategory`: same snap against pillar names; unknown → leave verbatim (the Uncategorized catch-all must keep its guarantee — never hide).
- `demonstrates`: snap case-insensitively to canonical BEHAVIOUR pillar names; drop unknowns.
- `dateMentioned`: reject if > 370 days past or ≥ 1 day future (leave `occurredAt` as capture time).
Unit tests: snapping, phantom-project→Inbox+suggestion, unknown-goal passthrough, ghost-behaviour drop, date bounds.

### 1d. Whisper vocabulary (code)
`GroqTranscriber.transcribe` adds a multipart `prompt` part: `"Work log for a {role}. Projects: {names, comma-joined}."` capped ~200 tokens (truncate project list, not the role). Source: `SettingsStore` + `ProjectDao.observeActive().first()` (inject `ProjectDao`; keep the call resilient — a DB read failure must never block transcription; `runCatching` to empty prompt).

### 1e. Fallback confidence guard (code, small)
When `completeAndParse` succeeded via the **fallback** model, cap each entry's `confidence` at 0.75 (surface the flag through `CategorizeResult` or a provider-level wrapper) — borderline 8B placements lean Inbox instead of silently mis-filing.

**Also in this phase:** update `eval/prompts/*.txt` to the new texts (PromptSyncTest keeps them honest); re-run the eval — **gate the tag on thresholds AND ≥ baseline everywhere**; adversarial review per ritual; on-device test list for the creator (shorthand mention files right; routine label reused; scanned-long-description project no longer bloats; phantom name lands in Inbox with its chip; transcription spells project names right).

---

# Phase AI-2 — Summary fixes + impact-coach-at-capture (v0.27.0)

### 2a. Summary prompt + serializer (code + prompt)
- `RollupAggregator.serialize`: DEVELOPMENT-kind pillars render under `DEVELOPMENT AREA: <name>` instead of `GOAL AREA:` (aggregate() already keeps them; only the header changes — match by framework pillar kind, case-insensitive).
- `AiPrompts.SUMMARY` — three edits (verbatim):
  - Rule 3 → `3. Always include every pinned item, in the right goal area. A pinned item may also appear among the highlights — include it ONCE, in its strongest phrasing.`
  - Rule 5 → `5. Items listed under a DEVELOPMENT AREA belong in "development", never in "goalAreas". Fill "development" only if the framework has such an area and there is real material.`
  - Rule 6 gains: `When a selected achievement carries a metric, keep the number verbatim in the bullet — a line with its number beats two lines without.`
- Summary eval cases re-run; structural checks must pass.

### 2b. Impact coach at capture — the USP move (code; prompt tweak)
Trigger: a capture whose text has no measurable value (`ImpactCheck.hasMeasurable == false`) and `aiEnabled`.
- **Voice/image REVIEW phase:** when the transcript lands, fire `suggestImpact` **in parallel** (VM scope, cancel-on-dismiss/add — reuse the `loadImpactSuggestion` job pattern). `NumberNudge` gains an optional `aiQuestion: String?`; static copy shows immediately, the AI question **swaps in when it arrives**. Anchored captures pass the folder's (capped) detail; otherwise role + goal area.
- **Typed post-save:** `SavedNudgeSheet` same pattern on save.
- Prompt: relabel the `{{BULLET}}` context line to `The achievement (the user's own words — may be a raw transcript):` so transcript-mode is in-distribution.
- Invariants: never delays or blocks save; no-key/offline/slow → static text stands; the question is shown, never stored. Cost note: ~₹0.4/user/mo at ~12 no-number captures.
- Optional if the phase runs light (else defer to M2): local re-ask when the user's reply still has no measurable — one gentle retry max (*"Can you put a rough number on that — even ~20% helps?"*), never blocking.

**Ritual:** eval green (coach rubric ≥90%, zero invented numbers) + adversarial review + tag v0.27.0. On-device: speak a no-number win → the nudge upgrades to a project-aware question within ~1s → answering folds the number in with placement intact.

---

# Phases M1–M8 — monetization track (spec pointers)

Full rationale and scope: `PRODUCT-ASSESSMENT.md` §5–6. Per-phase notes a fresh session must know:

- **M1 · Managed proxy:** thin OpenAI-compatible pass-through (Cloudflare Worker or Supabase Edge — creator has Supabase experience from ForgeAI); endpoints mirror `chat/completions` + `audio/transcriptions`; client auth = per-install anonymous token (UUID minted on first run; Play Integrity attestation added in M3); per-device daily/monthly quotas; **stateless, no request-body logging** (the privacy claim depends on it). App side: `AiConfig.BASE_URL`/Whisper `ENDPOINT` become an injectable `AiEndpoint(baseUrl, authHeaderProvider)` (Hilt); `GroqAiProvider`/`GroqTranscriber` consume it; **BYOK path kept fully working behind a Settings → Advanced item** (key present → direct-Groq mode; absent → proxy). Privacy policy cards updated honestly (new disclosure: entries pass through our relay, never stored) — bump `PrivacyPolicy.VERSION` (re-accept is correct here: material change). Owner gates: host account + proxy secret; keep slugs remote-config-able via the proxy.
- **M2 · First-session & polish:** onboarding "aha rehearsal" (log a real win at the end of onboarding, watch it file into the just-built framework, land on the made-real "YOUR RECORD · READY" card); Home one-slot nudge queue (priority: filing strip > waiting-voice > primer-gated reliability > daily nudge > impact > preview; max ONE dismissible card + strips); haptics per the house on-finger-down KEYBOARD_TAP rule; snackbars replace toasts; weekly recap notification (local data only); quick-fix set from `PRODUCT-ASSESSMENT.md` §5 items 4–5.
- **M3 · Play + Billing:** listing per sibling playbooks (Spends/NotDigest checklists); Play Billing v7+ single `pro` sub (monthly + annual offers, 7-day trial); local entitlement cache + graceful offline; **rework Play-restricted permissions** (`USE_EXACT_ALARM` → `SCHEDULE_EXACT_ALARM` request-flow, drop `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — reliability wizard deep-links instead); wire `recordTranscriptionSeconds` (currently zero callers) + categorize-call counting; enforce free-tier caps from `PRODUCT-ASSESSMENT.md` §4 at the proxy AND in-app (soft UI before hard wall).
- **M4 · Review Pack:** docx (empty-dependency approach: OOXML via zip writer, or a maintained lib — decide in-phase) + PDF (`android.graphics.pdf`) + `ACTION_SEND` share; first-person **prose mode** (new summary output variant — extend PART B with a `proseParagraphs` alternative schema behind a length/format picker); tone presets (confident / measured / concise — a one-line style directive injected into the prompt, eval-checked); gap-fill interview (pre-generation: for each thin pillar, ONE optional question via the coach pattern — answers become pinned context, never invented facts); premium writer slug via proxy config.
- **M5 · Capture speed:** Glance widget (one-tap voice + type buttons), QS tile, static app shortcuts, `ACTION_SEND`/`SEND_MULTIPLE` share-target (text → typed flow; image → scan flow) — all through a new **exported** trampoline activity that forwards to `CaptureLauncher` (CaptureActivity itself stays unexported); optional "Quick add" setting (skip review on confident transcriptions; default OFF — review is a trust feature).
- **M6 · Retention/season:** gentle streaks (persisted count + forgiving "momentum" framing), monthly recap, review-season countdown card + the campaign notification driven by the existing review-year setting.
- **M7 · Review cycles:** archive-year flow (snapshot record + rollup; fresh period) + role-based framework presets at onboarding.
- **M8 · iOS:** per the parked research at the end of `PROGRESS.md`'s roadmap section.

---

## Notes for the executing model (Opus 4.8)

1. **Read order for any phase:** `CONTEXT.md` (all) → `PROGRESS.md` top block → this doc's phase section → the two assessment docs' relevant sections only. Don't re-derive strategy; the decisions above are locked.
2. **Prompt texts in this doc are final** — paste them, don't re-write them. If an eval failure genuinely requires wording changes, change the minimum, re-run the eval, and record the delta in `PROGRESS.md`.
3. **The eval is a ship gate, not advice.** For AI-touching phases: thresholds met AND no metric below the committed baseline.
4. Groq slugs move — re-verify live before every tag (standing rule). Prompt caching on Groq is automatic prefix-matching; keep the static block byte-stable (any edit invalidates the cache for all users — batch prompt edits into releases, never A/B in patch releases).
5. AskUserQuestion at real forks only; defaults are in this doc. Deviations (creator reshapes, dropped scope) get recorded in `PROGRESS.md` the same way B2's reshape was.
6. Never violate `CONTEXT.md` §2. Two additions earned by this plan: **the AI never invents a number** (coach + summary), and **prompt changes ship eval-gated**.
