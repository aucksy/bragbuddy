# BragBuddy AI eval harness (Phase AI-0)

Every prompt/model change ships **eval-gated**, the way code ships compile-gated
(`docs/IMPLEMENTATION-PLAN.md` · standing rule since 2026-07-11). This directory is that gate:
canonical prompt texts, golden test sets, a zero-dependency runner, and the committed baseline
every AI phase must meet or beat.

## Layout

```
eval/
├── prompts/                  # canonical prompt texts — the SAME words the app ships
│   ├── categorizer.txt       #   PART A daily categorizer (current single-message shape)
│   ├── categorizer-combine.txt  # COMBINE MODE appendix (joined as base + "\n\n" + appendix)
│   ├── summary.txt           #   PART B summary generator
│   └── impact-coach.txt      #   impact coach (one nudge question)
├── golden/
│   ├── categorizer.jsonl     # one case per line (schema below)
│   ├── coach.jsonl           # rubric-scored coach cases
│   └── summary/*.json        # 4 synthetic rollups with structural expectations
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
copy — keep them in step when that Kotlin changes. (One deliberate eval-only difference: 429/5xx
retries with backoff on the same model first, so a rate-limited run measures the model, not the
limiter.)

## Running

```bash
GROQ_API_KEY=gsk_…  node eval/run.mjs            # full run, gated (non-zero exit on failure)
node eval/run.mjs --dry-run                       # no API calls: validate goldens + prompt build
node eval/run.mjs --only categorizer --limit 5    # cheap smoke (LIMITED runs are never shippable)
node eval/run.mjs --baseline eval/report-baseline.json   # AI-1+: also gate on ≥ baseline everywhere
node eval/run.mjs --no-gate                       # write the report, always exit 0
```

In CI: **Actions → AI Eval → Run workflow** (needs the `GROQ_API_KEY` repo secret — owner gate).
Tick **commit_baseline** to commit the run as `eval/report-baseline.{md,json}` — do that once per
prompt generation; baseline runs never fail the job (they are the "before" measurement), normal
runs fail on any red gate.

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
| Summary structural checks (no dupes · arcs merged · metrics verbatim · pinned once · counts exact · setAside honest) | 100% |

Reported but not gated: entry-count accuracy, demonstrates (required tags present), metric field
preservation, dateMentioned accuracy, routine false-positive rate. The development-placement check
is **advisory until AI-2** (the current serializer labels development areas as goal areas).
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
string; scored by the rubric. `summary/*.json`: see the four files — `expect` supports
`noDuplicates`, `arcKeys`, `metrics`, `pinnedKeys`, `rolledUp`, `setAsideNonEmpty`,
`developmentKeys` (advisory).

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
