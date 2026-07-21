# Structured Summary arc ("S-arc") — durable spec (owner-directed 2026-07-21)

> **The owner's ask, near-verbatim:** the final summary must be laid out the way the USER organised
> their record. *Performance Goals → Project → Deliverable → all work under that deliverable
> summarised in the minimum possible impactful pointers, repeats carrying a logical count. Leadership
> keeps the same project/deliverable structure but holds the LEADERSHIP ASPECT of the work, filed
> under specific leadership pillars if available. Learning & Growth is mostly user-added, but the AI
> may detect learning-relevant work from daily logs against the user's development plan — same
> project ▸ deliverable tagging unless an item sits under no project. If the user manually creates
> organised folders, those must survive into the final summary — that's what a manager digests.*
>
> **This arc ABSORBS the approved competency arc** (`COMPETENCY-TAGGING-PROPOSAL.md` K1–K3 — its
> ⭐locked rules stand unchanged: competencies exist ONLY when the user defines them; the AI never
> assumes/invents one or its details; the name is "Competencies"; tagging-only, never placement).

## ⭐ Owner decisions LOCKED (AskUserQuestion 2026-07-21 — do not re-litigate)

1. **Leadership section = PILLARS FIRST.** Top headers are the user's leadership pillars
   (competencies); each evidence line carries its `[Project ▸ Deliverable]` tag. (Chosen over
   projects-first and over pillar→project 3-level nesting.)
2. **Double listing = YES, TWO ANGLES.** The same win may appear in Performance Goals as the
   outcome bullet AND in Leadership rephrased to its leadership aspect. The two sections are read
   independently by managers.
3. **L&G detection = SUGGEST FIRST, USER CONFIRMS.** The AI proposes a Learning & Growth cite
   (based on the DEVELOPMENT pillar's detail — the user's development plan); it appears in the
   summary ONLY after the user approves it. Nothing is ever re-filed; the entry stays under its
   project. (Chosen over auto-evidence and over manual-only.)
4. **Priority = NEXT ARC**, merged with K1–K3. V6 coach breadth and capture-review Phase 4 come
   AFTER this arc. (F2/F3 calibration, F5, F6/B3 remain separate open items.)

## Target output shape (screen AND copied text — same hierarchy)

```
PERFORMANCE GOALS
  Intake Hub for CommXHub
    Tech Questions
      • Resolved 145+ tech questions across the phase, unblocking delivery   (from 4 logs)
    • Translated the vision into a build-ready product — 18 features / 76 stories
  Raven Migration CCP Portals
    truncated forms ORE   (DONE)
      • Owned the ORE end-to-end: RCA → process gap → print/test/ops fixes   (from 7 logs)

LEADERSHIP & BEHAVIOURS
  Set the Agenda
    • Defined a bounded MVP target and a multi-year plan  [Intake Hub]
  Do It the Right Way
    • Took accountability for an ORE outside the role's remit  [Raven Migration ▸ truncated forms ORE]
  (a pillar with no genuine evidence is omitted, never padded — PART B rule 4 stands)

LEARNING & GROWTH
  • Selected for the ECMX AI Studio cohort   (no project)
  • Learned the platform-health-report process end-to-end  [Platform Health]   ← approved cite
```

Rules of the shape:
- A deliverable's whole windowed history condenses to **1–3 pointers max**, chosen by impact;
  repeats collapse into ONE pointer with a logical count — prefer the CUMULATIVE stated metric
  ("145 questions") over a naive ×N when the numbers themselves accumulate.
- Loose (no-deliverable) wins list under their project after the deliverable groups; no-project
  wins list plainly under the goal area (mirrors Home, v0.33.0 owner calls).
- A **Done** deliverable reads as a completed story (v0.34.0 rule 2 stands) and shows its state.
- Leadership evidence = the leadership ANGLE rewritten, not the delivery bullet repeated (decision 2).
- Users WITHOUT deliverables/competencies see today's output — every level degrades gracefully
  (the K-proposal's optional/default-empty condition).

## Phases (one per chat, standing rituals apply: debug-gate → adversarial review → tag → handoff)

**S1 · Deterministic layout + counts (NO prompt change, NO eval).**
Screen + export render `goalArea → project → deliverable → pointers` from the tags the summary
achievements already carry (`SummaryGrouping` project folders exist since v0.30.0; add the
deliverable sub-level + REAL indented headers in `SummaryExport` replacing the `[Project ▸ Del]`
line-tags; show `(from N logs)` when `AggHighlight.count > 1`; deliverable Done state from
`AggDeliverable`). Reorder/collapse stays scoped within a folder. Pure render/export work +
`DocExport`-style unit tests.

**S2 · K1 — competency structure, manual (Room v9, NO eval).**
Per the approved proposal: BEHAVIOUR-category sub-folders presented as "Competencies" in the
editor (+ detail boxes), `EntryEntity` per-competency tag (additive migration; `BackupCodec`
carries it — the v0.31.0 restore lesson), manual tagging in entry detail (3rd-level picker
pattern), competency chips in views.

**S3 · K2 + PART B rewrite — the structured-summary prompt phase (⚠️ EVAL-GATED, 2–3 rounds).**
- Rollup: per-competency evidence grouping (`AggBehaviour` gains competencies + each evidence
  item's project/deliverable — the rollup items already carry both), serialization for PART B.
- PART B: schema `projects[] → deliverables[] → pointers[]` under each goal area; behaviours →
  pillars-first with tagged evidence; **leadership-aspect rewrite rule** (decision 2); deliverable
  condensation rule (1–3 pointers, cumulative-metric-over-×N); trust-the-tag over blurb-reading
  (the v0.34.0 lesson). Update/extend goldens: `deliverableGrouping`, `competencyGrouping`
  (trust-the-tag rewrite), + a leadership-angle check and a two-angle check. `run.mjs` mirrors +
  `PromptSyncTest` in the SAME commit. Cached-summary schema additions ALL defaulted (old blobs
  must decode — the standing `SummaryStore` wipe hazard).
- ⚠️ PART B was rated A− "don't touch" by `AI-SYSTEM-ASSESSMENT.md` — this is a deliberate,
  owner-directed evolution; budget accordingly and expect `summaryChecks`' known F5 flake.

**S4 · L&G suggest-and-confirm (⚠️ EVAL-GATED — categorizer change) + K3 coverage.**
- Categorizer: when a DEVELOPMENT pillar has a non-empty detail (the dev plan), the model may add a
  `devSuggestion` against it (suggestion field, NOT placement; entry stays under its project).
  Gated on the user having written a plan; new golden(s); inherits the KNOWN-OPEN `inboxPrecision`
  red — never lower thresholds. ⚠️ This touches the cache-critical static block — consider
  batching with the F2/F3 calibration phase to pay the cache invalidation ONCE (owner call at S4
  kickoff).
- Approval surface: a collapsed "Suggested for Learning & Growth" list in the Summary L&G section
  (+ entry-detail chip) with Approve/Dismiss; approved → a durable dev-evidence tag feeding the
  rollup. When capture-review Phase 4 ships, approval merges into its review sheet.
- K3: per-competency coverage nudge ("no evidence yet for 'Bring Others With You' this cycle") via
  the M2 one-slot nudge queue.

**After the arc:** V6 coach breadth → capture-review Phase 4 (spec unchanged in
`CAPTURE-REVIEW-PLAN.md`) → F2/F3 (if not batched into S4) → M3 last (locked).

## Standing constraints carried in
- No company names anywhere in the app (owner, 2026-07-21).
- Any prompt change ships EVAL-GATED with the `eval/prompts/*` + `run.mjs` mirror in the same
  commit; never lower a threshold; a bare re-run is not a remedy.
- The AI never invents a competency or reshapes the framework; deliverable/competency levels are
  optional and default-empty; anchors are made of names scoped by anchor columns (v0.33.1).
- Every silent curation act must say WHAT it kept, WHY, and WHERE the rest lives (v0.39.1 lesson —
  the set-aside panel patterns apply to the new layout too).
