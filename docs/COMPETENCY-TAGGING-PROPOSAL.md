# Competency tagging — proposal (2026-07-21) · ✅ APPROVED same day · ⏸ SEQUENCING now subject to the VISION-FIT ASSESSMENT (owner redirect, later 2026-07-21 — see PROGRESS.md ▶ WHERE WE ARE; the locked decisions below still stand)

> **⭐ OWNER DECISIONS LOCKED 2026-07-21 (carry into every phase, never violate):**
> 1. **The AI never assumes, invents, or fills in a competency ("pillar") or its details — anywhere,
>    ever.** Competencies exist ONLY when the user defines them (name + detail), by hand, in the
>    framework editor. This extends the standing "no AI reshapes the framework" rule (2026-07-07) to
>    the new level. The AI may only *tag against* the user's defined list; unknown names still drop.
> 2. **The app NUDGES the user to define competencies + details** — that is the adoption mechanism:
>    editor/onboarding copy that sells the detail box ("this is how daily filing tags each pillar
>    correctly"), plus a gentle, dismissible hint when behaviour evidence accumulates against a
>    category with no defined competencies (reuse the M2 one-slot Home nudge queue — never a nag,
>    never blocking capture).
> 3. Everything in §5's conditions stands: optional/default-empty · tagging-only, never placement ·
>    "Competencies" is the one name everywhere.

*The owner's question: Amex has named leadership pillars ("Set the Agenda", "Bring Others With You"…)
and employees are expected to tag work under specific ones. If that's true of most companies, should
BragBuddy let users define these pillars — with details — so logged work can be assessed and tagged
per pillar? Is it a good idea, and what's the right implementation?*

**Verdict: YES — a good idea, genuinely aligned with how big-company appraisals work, and cheaper
than it looks because half the machinery already exists. Build it as the behaviour-axis replay of
the deliverables arc (structure first, AI second), keep it strictly optional, and call the items
"Competencies".**

---

## 1. Research — is the Amex pattern common?

Yes, at large employers it is close to universal; at small ones it is rare. The pattern — a "how"
axis with NAMED behaviours/competencies that employees must evidence individually — shows up as:

- **Amazon** formally embedded its 16 Leadership Principles into the revamped performance-review
  model — LPs are now a core metric scored per employee (three-tier: Role Model / Solid Strength /
  Development Needed) feeding the Overall Value score that drives raises and promotion
  ([People Matters](https://me.peoplemattersglobal.com/news/performance-management/amazon-makes-leadership-principles-a-key-metric-of-its-revamped-performance-appraisal-model-46235),
  [PerformYard](https://www.performyard.com/articles/how-does-amazon-do-performance-management)).
  Promotion docs at Amazon have long required evidence mapped LP-by-LP.
- **Competency-based performance appraisal** is a named, standard HR model: predefined competencies
  with behavioural indicators per competency, assessed via BARS / 360 / assessment centres. The
  literature itself notes it is **resource-intensive and therefore concentrated in larger
  organisations** ([Deel](https://www.deel.com/glossary/competency-based-performance-appraisal/),
  [TCW Global](https://www.tcwglobal.com/payrolling-terms/competency-based-performance-appraisal)).
- Well-known named frameworks employees are appraised against: **Amex** Leadership Behaviors (the
  owner's own review form says *"using specific examples for each pillar"* — it is literally in the
  `real-*` eval goldens), **Microsoft** leadership principles (Create Clarity · Generate Energy ·
  Deliver Success) + culture attributes, **UK Civil Service Behaviours** (nine named behaviours,
  evidence required per behaviour in applications and appraisals), **Unilever** Standards of
  Leadership, big-4 professional frameworks (e.g. the PwC Professional's five dimensions).
- **Small companies** mostly have no such structure — a flat "teamwork/communication" line or
  nothing. **Conclusion: the feature must be optional and default-empty**, enriching users whose
  companies have it without burdening those whose don't.

**Product significance:** appraisal forms with named pillars ask for *an example per pillar*. A
record pre-sorted by pillar means BragBuddy can emit exactly the shape the form demands — and can
tell the user *which pillar has no evidence yet* (a coverage nudge no generic logger can offer).

---

## 2. Why this is cheaper than it looks — what already exists

1. **The data + editor already support it.** Since v0.9.0, sub-folders with descriptions can be
   created under ANY framework category, including BEHAVIOUR ones (`ProjectEntity.goalArea` =
   category name; unique `(name, goalArea)` index; rename/delete cascades; B2a editor UI). The
   categorizer prompt already receives them as `focus areas: X, Y` (names only). They exist — they
   are just unlabelled for this purpose and carry no tagging semantics.
2. **The model already volunteers competency names.** That was the root cause of the
   demonstratesAccuracy-14% bug (assessment F1): told to judge against the category description, the
   model answers with the competency it matched ("set the agenda"). We now map that answer UP to the
   category (v0.38.0) — **the fine-grained half of the signal is parsed and then deliberately
   discarded**. This feature = stop discarding it.
3. **The summary schema already nests competencies.** `SummaryBehaviour.competencies[]` +
   PART B rule 5 (v0.30.0 item 4) already group evidence under competency names — but by the
   summary model's *judgment* from the blurb text. With real per-entry tags the grouping becomes
   deterministic — exactly the v0.34.0 deliverables lesson: *"wherever a tag exists, trust it over
   your own reading of the wording."*

## 3. What is genuinely new (the real cost)

- **`EntryEntity` must carry the per-competency tag** (Room v8→v9, additive; `BackupCodec` must
  carry it — the v0.31.0 `anchorGoalArea` lesson: an un-serialised column is silently dropped by a
  Drive restore).
- **Normalizer**: retain validated `(category, competency)` pairs instead of collapsing — validated
  against the user's DEFINED competency list first, the blurb as fallback; unknown still drops.
  Mirror in `run.mjs`.
- **Rollup**: per-competency evidence grouping in `AggBehaviour` + serialization for PART B.
- **PART B rule 5 rewrite** (eval-gated): trust the provided grouping over blurb-reading;
  `competencyGrouping` golden updated. NOTE: this edits the SUMMARY prompt, not the categorizer's
  cache-critical static block — no prefix-cache impact, and **no categorizer prompt change is
  needed at all** (the model already emits the names).
- **Editor surface**: present a BEHAVIOUR category's sub-folders as **"Competencies"** with copy
  like *"the named behaviours / leadership pillars your review form expects — e.g. 'Set the
  Agenda'"*, each with a detail box (Type + Scan, like everything else).
- **Manual correction**: the entry-detail behaviour picker gains the competency level (mirror the
  Recategorize third-level pattern from v0.33.0).
- **Eval**: per-competency goldens — deliberately re-instating the sub-competency expectations that
  `real-006`/`real-007` carried in AI-0 and that v0.38.0 removed *as correct for the current
  contract*; under the new contract they become the right wants again, now against defined names.

## 4. Naming — call them "Competencies"

The system already uses exactly this word for exactly this concept: PART B rule 5, the
`SummaryCompetency` schema, the `competencyGrouping` gate. One word across UI, prompt, schema and
docs — the "deliverable" vocabulary lesson (v0.34.0) says never let a defined term live loosely.
Do NOT call them "pillars" in code or prompts: `Pillar` is already the internal name for framework
*categories*, and the collision would be a standing trap. (UI helper text may *mention* "leadership
pillars" as a synonym, since that's what users will recognise.)

## 5. Recommended shape — the deliverables-arc replay, 3 phases

| Phase | Content | Gate |
|---|---|---|
| **K1 · structure + manual** | Editor relabel/copy for behaviour-category competencies · Room v9 entry column + backup · manual tagging in entry detail · Home/pillar view shows competency chips | compile + unit tests only (no prompt change) |
| **K2 · AI + summary** | Normalizer retains validated pairs (+ `run.mjs` mirror) · rollup per-competency grouping · PART B rule 5 trust-the-tag rewrite · per-competency goldens | **EVAL-GATED**, budget 1–2 rounds |
| **K3 · coverage insight** | The payoff USP: per-competency evidence coverage ("no evidence yet for 'Bring Others With You' this cycle") as a Home/summary nudge, feeding the impact-coach surface | compile + unit tests |

**Conditions (owner-locked principles this must respect):**
- **Optional, default-empty** — a framework without defined competencies behaves exactly as today
  (v0.38.0 blurb-mapping + summary-model judgment keep working; nothing regresses for simple users).
- **Competencies are tagging targets, never placement slots** — work is never *filed into* a
  behaviour; the v0.33.0 leak lesson stands.
- **The user's own list wins** — the AI never invents a competency name (validated against the
  defined list; the framework stays hand-built per the 2026-07-07 no-AI-reshapes rule).

**Ordering vs. open work:** independent of the F2/F3 Inbox-boundary calibration (different prompt,
different risk); recommended order stays **F2/F3 first** (already scoped, closes a red gate), then
this arc, with capture-review Phase 4 sequenced wherever the owner wants it. Overall size:
comparable to the deliverables arc — 2–3 releases, one eval-gated.

---

*Sources: [People Matters — Amazon makes Leadership Principles a key appraisal metric](https://me.peoplemattersglobal.com/news/performance-management/amazon-makes-leadership-principles-a-key-metric-of-its-revamped-performance-appraisal-model-46235) ·
[PerformYard — How does Amazon do performance management](https://www.performyard.com/articles/how-does-amazon-do-performance-management) ·
[Deel — Competency-based performance appraisal](https://www.deel.com/glossary/competency-based-performance-appraisal/) ·
[TCW Global — Competency-based performance appraisal](https://www.tcwglobal.com/payrolling-terms/competency-based-performance-appraisal)*
