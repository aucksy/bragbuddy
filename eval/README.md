# BragBuddy AI eval harness (Phase AI-0)

Every prompt/model change ships **eval-gated**, the way code ships compile-gated
(`docs/IMPLEMENTATION-PLAN.md` · standing rule since 2026-07-11). This directory is that gate:
canonical prompt texts, golden test sets, a zero-dependency runner, and the committed baseline
every AI phase must meet or beat.

## Layout

```
eval/
├── prompts/                  # canonical prompt texts — the SAME words the app ships
│   ├── categorizer-system.txt   # PART A daily categorizer — cache-first SYSTEM message (AI-1 two-part)
│   ├── categorizer-user.txt     #   the per-call USER message (today / anchor / transcript)
│   ├── categorizer-combine.txt  # COMBINE MODE appendix (joined as system + "\n\n" + appendix)
│   ├── summary.txt           #   PART B summary generator
│   └── impact-coach.txt      #   impact coach (one nudge question)
├── golden/
│   ├── categorizer.jsonl     # one case per line (schema below)
│   ├── coach.jsonl           # rubric-scored coach cases
│   └── summary/*.json        # 5 synthetic rollups with structural expectations
├── tools/from-backup.mjs     # bootstrap real-record cases from a device backup export
├── run.mjs                   # the runner (Node 20+, no deps)
├── report.md / report.json   # last run's output (gitignored-by-convention: commit only baselines)
└── report-baseline.{md,json} # the committed "before" — AI-1+ must be ≥ this on every metric
```

**Prompt sync:** `app/src/test/.../PromptSyncTest.kt` asserts each `AiPrompts` template equals the
corresponding `eval/prompts/*.txt` (line endings normalized). It runs in both Android workflows'
unit-test step, so editing a prompt in one place without the other is **red CI**. Change a prompt →
change BOTH the Kotlin constant and the txt file in the same commit.

**Request fidelity:** `run.mjs` reads the model slugs and endpoint straight out of `AiConfig.kt`,
rebuilds the categorizer context blocks the way `EntryProcessor.prepare` /
`FrameworkPrompt.categorizerBlock` do, and mirrors `GroqAiProvider` exactly: system+user messages,
`temperature 0.2`, JSON mode, primary → fallback on transport OR parse failure. (The summary
cases carry their `frameworkBlock` verbatim in the golden files, authored in the
`Framework.toPromptBlock` format.) Sections marked `APP-MIRROR` in `run.mjs` name the Kotlin they
copy — keep them in step when that Kotlin changes. (Two deliberate eval-only differences: 429/5xx
retries with backoff on the same model first, so a rate-limited run measures the model, not the
limiter; and a fixed `seed` on every call so identical prompts give stable outputs run-to-run —
the app itself sends no seed.)

**Consensus sampling (since AI-2, `EVAL_SAMPLES`, CI uses 3):** each case is called N times and a
check passes on the **majority** of samples. The golden sets are small and the models are
nondeterministic at temperature 0.2 — a *different* single check flaked run-to-run on byte-identical
prompts (AI-2 gate rounds r3–r6), and `summaryChecks` needs 100%, so a single sample almost never
clears the ~18-check AND-gate. Consensus measures each check's central tendency, which is the honest
capability. N is clamped odd (no tie); the absolute thresholds and all scoring are unchanged; every
sample's call still counts toward the call-level `jsonValidity`/parse metrics. Set `EVAL_SAMPLES=1`
(the default) for cheap local runs.

**Baseline noise tolerance (since AI-2):** on top of consensus, the ≥-baseline comparison passes
when a metric is within **one golden case** of its baseline value (per-metric denominators ride in
`report.json`). A one-case drop is indistinguishable from noise; two or more still fails. The
committed AI-1 baseline was single-sample; a consensus gate run compares conservatively against it
(consensus reduces variance toward the mean, and AI-2 left the categorizer prompt byte-identical, so
its metrics move only by noise). The absolute thresholds below are untouched and remain the hard
quality bar. Booleans (jsonValidity, coachNoInventedNumbers) get no tolerance.

## Running

```bash
OPENROUTER_API_KEY=sk-or-… node eval/run.mjs      # full run via OpenRouter PINNED TO GROQ (preferred)
GROQ_API_KEY=gsk_…  node eval/run.mjs             # full run, direct Groq (free tier throttles — see below)
node eval/run.mjs --dry-run                       # no API calls: validate goldens + prompt build
node eval/run.mjs --only categorizer --limit 5    # cheap smoke (LIMITED runs are never shippable)
node eval/run.mjs --baseline eval/report-baseline.json   # AI-1+: also gate on ≥ baseline everywhere
node eval/run.mjs --no-gate                       # write the report, always exit 0
```

**Transport (decided 2026-07-12, after the AI-1 gate was quota-blocked):** the full run's ~63 live
calls do not fit Groq's FREE-tier daily token budget, and Groq's paid tier is closed to new
sign-ups — so the harness can reroute through **OpenRouter with the provider hard-pinned to Groq**
(`provider: { only: ["groq"], allow_fallbacks: false }`): the *same models on the same Groq
inference the app's users hit*, billed via OpenRouter credits (~₹9 per full run). Slugs map via
`OPENROUTER_SLUGS` in `run.mjs` (fail-closed on an unmapped slug — keep it in step with
`AiConfig.kt`). Precedence: an explicit `GROQ_BASE_URL` wins (mock server / M1 proxy hook), then
`OPENROUTER_API_KEY`, then direct `GROQ_API_KEY`. The app itself is untouched — this exists only in
the harness.

In CI (needs the `OPENROUTER_API_KEY` repo secret — owner gate; `GROQ_API_KEY` still works as the
direct fallback). Two ways, same as every build here it is tag-driven; a manual fallback also
exists:

- **Push a tag `eval-baseline-*`** → runs suite `all` and commits the result as
  `eval/report-baseline.{md,json}` (do that once per prompt generation — the "before").
- **Push a tag `eval-run-*`** → runs suite `all` as a **gate check** (must meet/beat the baseline;
  fails the job on any red gate). This is the pre-tag ship gate for AI-1/AI-2.
- **Manual:** Actions → AI Eval → Run workflow → pick the suite + `commit_baseline` checkbox.

Baseline runs never fail the job (they are the measurement); gate runs fail on any red gate.

## Ship thresholds (gates)

| Metric | Gate |
|---|---|
| Placement accuracy (project + goalCategory, after snapping) | ≥ 85% |
| Inbox recall (ambiguous → parked) | ≥ 80% |
| Inbox precision (clear → NOT parked) | ≥ 90% |
| JSON validity (all calls parse; primary parse-fail rate < 10%) | 100% |
| Routine-label reuse (exact label when context provides one) | 100% |
| Impact inside the case's band | ≥ 80% |
| Coach rubric pass (short · concrete measure kind · grounded · **zero invented numbers = hard fail**) | ≥ 90% |
| Summary structural checks (no dupes · arcs merged · metrics verbatim · pinned once · counts exact · setAside honest · competencies nested under their category) | 100% |

Reported but not gated: entry-count accuracy, demonstrates (required tags present), metric field
preservation, dateMentioned accuracy, routine false-positive rate. The development-placement check
is **gated since AI-2** (the serializer heads development pillars "DEVELOPMENT AREA:" and summary
rule 5 routes them into `development[]`); it was advisory in the AI-0/AI-1 baselines.
"Parked in Inbox" is judged **after the scorer's snapping**: a phantom project name snaps to Inbox
first (the AI-1 validator's planned rule — the shipping app does not snap yet), then the app's own
`statusFor` logic applies: `project/goalCategory == "Inbox"` or `confidence < 0.6`, never for
anchored captures. Coach "≤ ~18 words" is enforced at a hard 20. A run with zero cases in a
requested suite, an unknown `--only` value, or a named-but-missing `--baseline` file **fails
closed** (exit 1).

## Golden case schema (`categorizer.jsonl`, one JSON object per line)

```jsonc
{
  "id": "po-exact-project",
  "transcript": "what the user said",
  "context": {
    "role": "Product Owner",
    "today": "2026-07-11",              // fixed date → relative-date cases stay deterministic
    "anchor": null,                       // or a project name (folder-tap capture)
    "routineTypes": ["access requests"], // existing labels (only injected once the template has {{ROUTINE_TYPES}})
    "framework": { "pillars": [ { "name": "…", "kind": "GOAL_AREA|BEHAVIOUR|DEVELOPMENT", "blurb": "…" } ] },
    "projects": [ { "name": "…", "goalArea": "…", "description": "…" } ]
  },
  "expect": {                            // every field optional — score only what a case specifies
    "placements": [ { "project": "…", "goalCategory": "…" } ],
    "entryCount": 1,
    "inboxExpected": false,
    "routineTypes": ["access requests"], // [] asserts NO routine entry
    "impactBand": [0.5, 0.85],           // checked on the entry matched to placements[0]
    "demonstrates": ["Collaboration"],   // required tags (extras allowed)
    "metric": "30",                       // substring that must land in some entry's metric
    "dateMentioned": "2026-07-07"
  },
  "note": "why this case exists"
}
```

A case may also set top-level `"combineSingle": true` — the transcript is then sent in COMBINE
mode (the add-impact / add-detail merge path: exactly one entry, follow-up folded in), mirroring
`CategorizeRequest.combineSingle`.

`coach.jsonl`: `{ "id", "bullet", "project", "projectDetail", "goalArea", "role" }` — no expected
string; scored by the rubric. `summary/*.json`: see the five files — `expect` supports
`noDuplicates`, `arcKeys`, `metrics`, `pinnedKeys`, `rolledUp`, `setAsideNonEmpty`,
`developmentKeys` (gated since AI-2), and `competencyGrouping` (gated since the Summary phase:
`{ "category": "Leadership", "competencies": [...] }` — a BEHAVIOUR category whose framework
description names competencies must group its evidence UNDER the category, nested, not surface each
competency as its own top-level header).

## Growing the set with the real record

The current ~34 categorizer cases are the synthetic-edge seed (Hinglish, splits, shorthand
mentions, routine bursts, relative dates, no-work, anchors, near-duplicate project names). The
target is **60–80 cases with the creator's real transcripts as the majority**:

1. On the phone: Settings → Backup → **Export to device** → move `bragbuddy-backup.json` to this
   machine (do **NOT** commit it — it is the raw record).
2. `node eval/tools/from-backup.mjs path/to/bragbuddy-backup.json`
   → writes skeleton cases to `eval/golden/from-backup.pending.jsonl`, one per capture (split
   siblings grouped), with the currently filed placement as the tentative expectation.
3. **Hand-verify every line** (the filed placement may itself be an AI mistake — the user's
   Recategorize corrections are exactly the gold worth keeping), redact anything sensitive, drop
   weak cases, remove the `"_verify": true` flag, and append to `categorizer.jsonl`.
   `run.mjs` skips any case still flagged `_verify`.

## Baseline discipline (AI-1 and later)

1. Before changing a prompt: make sure `eval/report-baseline.{md,json}` exists for the CURRENT
   prompts (run the workflow with **commit_baseline** once).
2. Change the prompt (Kotlin + txt together; PromptSyncTest keeps you honest).
3. Run the eval with `--baseline eval/report-baseline.json`: gates must pass AND no metric may drop
   below the baseline. Only then tag the release.
4. After shipping, commit a fresh baseline for the next phase.
