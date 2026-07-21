# BragBuddy — AI System Prompt Assessment (2026-07-21 refresh)

*Scope: EVERY prompt in `AiPrompts.kt` as shipped in **v0.37.0** — PART A categorizer (+ COMBINE
mode), PART B summary, PART C framework-refine, image extract, document scan, impact coach — assessed
against the PRD's intended purpose, the eval goldens, the committed baseline
(`eval/report-baseline.json`, 2026-07-16) and the v0.37.0 gate rounds. **This SUPERSEDES the
2026-07-11 assessment** (git history keeps it): that one predates v0.31.0 (length fix), v0.34.0
(deliverables) and v0.37.0 (big-paste splitting), and its recommendation list has since largely
SHIPPED (examples, rubrics, routine-label reuse, behaviour blurbs, output validator, cache-first
restructure, coach-at-capture, the eval harness itself). This is a read/analysis pass — **no eval was
run** (fixed-seed lore: a bare re-run reproduces; the committed numbers are the evidence).*

*Measured state assessed against (v0.37.0, 2 gate rounds — do not re-measure):
placementAccuracy 97.1% · entryCountAccuracy 92.9% · deliverableAccuracy 83.3% · coachPass 91.7% ·
inboxPrecision 84.6% (22/26, RED, bar 90%) · summaryChecks 96.7% (RED, bar 100%) ·
demonstratesAccuracy ~14% (1/7) · metricPreserved ~60% (3/5).*

---

## 0. Executive verdict

The system is in far better shape than the last assessment found it — the 2026-07-11 fix list
shipped and the gated metrics show it (placement 97%, routine reuse 100%, zero invented coach
numbers, JSON validity 100%). What remains is **not architecture and mostly not even prompt
quality — it is three localized defects and two stale-artifact conflicts**, each now diagnosed to a
root cause:

| # | Finding | Severity (user impact) | Root cause | Fix type |
|---|---|---|---|---|
| F1 | **Behaviour evidence is silently destroyed** for real-world frameworks (demonstratesAccuracy 14%) | **HIGH — the "how" half of the appraisal doc starves** | Prompt invites competency names; normalizer drops them | **Code-only** (no prompt edit, no cache loss) |
| F2 | **Inbox↔Outside-project boundary is mis-taught** (inboxPrecision 84.6% RED) — prompt Example 3 teaches the model to fail golden `po-metric-30-percent` | HIGH — real wins park in Inbox instead of filing | Example 3's surface pattern collides with desired behaviour; the boundary has no positive example | **Prompt edit** (dedicated calibration phase) |
| F3 | Stated numbers don't land in `metric` (metricPreserved 60%) | MED — rollup metric lines thin out; number survives only in the bullet | Rule 11 says "optional"; no long-form example shows metric extraction | Prompt edit (batch with F2) |
| F4 | Goldens `real-010`/`real-011` (entryCount=1) now contradict v0.37.0's rule 2 by construction | LOW-MED (eval hygiene; masks real entryCount signal) | Goldens written 2026-07-11 encode the pre-split policy the owner has since reversed | **Golden-only edit** |
| F5 | `summaryChecks` RED = ONE stable case (`detailed-length` wants ≥5 Delivery achievements, gpt-oss-120b reliably writes 4) | LOW-MED — cosmetic red gate every phase | Aspirational golden floor vs the model's stable behaviour (the exact v0.31.0 `lengthHonoured` lesson, recorded then repeated) | Owner decision (golden recalibration vs known-red) |
| F6 | PART C framework-refine: built, byte-maintained, **zero callers since 2026-07-07** | LOW (dead weight + drift risk) | Owner locked "no AI reshapes the framework"; the seam was never removed | **Delete** (recommended) |

**Schema drift (axis 3): CLEAN** — every prompt's promised JSON maps 1:1 onto its Kotlin serializer
(§5). No silent field drops in either direction. The one "drop" that exists is deliberate and is F1's
mechanism.

**Purpose fit (axis 1): PASS with the three defects above.** The categorizer files right and never
loses (fail-safe → Inbox verified end-to-end); the summary is manager-ready and deliverable-aware;
the coach asks and provably never invents a number (hard-gated at 1.0). The gap between "good" and
"right" is concentrated in F1–F3.

---

## 1. What changed since the 2026-07-11 assessment (context for why this doc supersedes it)

Shipped and verified through eval gates: AI-1 (examples ×5, impact/confidence rubrics, routine-label
reuse via `{{ROUTINE_TYPES}}`, cache-first two-part restructure, `CategorizedNormalizer`, description
caps, 8B-fallback confidence cap), AI-2 (coach-at-capture — the USP move the old doc called C1),
v0.30.0 (competency nesting), v0.31.0 (length-target fix F3, development placement F4), v0.34.0
(deliverables in `{{PROJECTS}}` + PART B rule 2 + `DeliverableGuess`), v0.37.0 (rule 2 split-scoping
+ Example 5 + `paste-appraisal-split`). The old doc's grades (categorizer B, no examples, no eval)
no longer describe the shipped system.

---

## 2. Contradiction (a) ADJUDICATED — prompt Example 3 vs golden `po-metric-30-percent`

**The evidence.**
- Prompt Example 3 ([AiPrompts.kt:160-169](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L160-L169)):
  *"Built that new leadership dashboard, cut reporting time by about 30 percent."* →
  `project: "Inbox", confidence: 0.5`. Its illustrative setup lists Raven Migration + SharePoint
  Request System — the **same two projects** the golden uses.
- Golden `po-metric-30-percent` (`eval/golden/categorizer.jsonl:29`): *"Built the **migration status
  dashboard** for leadership, cut the weekly reporting effort by about 30 percent."* →
  `inboxExpected: false` (must NOT park). Baseline failure: parked 0/3, `confidence 0.5` — the model
  reproduces Example 3's answer verbatim (`report-baseline.json:164-171`).
- Example 3 itself is inherited from the PRD (`PRD/BragBuddy-System-Prompt.md` §A4 Example C,
  "metric, no matching project → Inbox") — it predates the loose-mention matching rule.

**Verdict: the PROMPT EXAMPLE is the wrong one. The golden stands.** Three reasons:

1. **The golden encodes shipped product policy; the example predates it.** Rule 4
   ([AiPrompts.kt:47-54](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L47-L54), AI-1's
   loose-mention rule) says a loose mention that "clearly fits exactly one listed project" must file.
   *"The **migration** status dashboard"* carries a real cue toward the only migration project
   (Raven, whose description is literally "servicing-comms migration across markets… measured by
   markets migrated"). The example's sentence (*"that new leadership dashboard"*) carries **no** cue.
   The intended lesson differs on exactly the feature the model must learn to discriminate — but the
   two sentences share their entire surface pattern (built + dashboard + ~30% + reporting), so the
   example functions as a **memorised answer** for the golden's sentence. A few-shot example must
   never sit within pattern-matching distance of a case that requires the opposite output.
2. **The example teaches a lesson the product doesn't want.** What a 70B model generalises from it is
   "a standalone win with a metric → park at 0.5". That directly contradicts the appraisal-record
   purpose (a clear win with a stated metric is the *most* appraisal-worthy capture there is) and
   feeds the same failure in `real-008` (below). Parking is friction; with capture-review Phase 4
   coming (user confirms placement at capture), a confident Outside-project/goal-area filing is
   strictly better than an Inbox park for real work.
3. **Example 3's legitimate teaching goals survive a rewrite.** It exists to teach (i) metric
   extraction verbatim, (ii) park-when-genuinely-unplaceable, (iii) empty `suggestedProjects`. All
   three survive with a transcript that shares no vocabulary with plausible project work — e.g. a
   vendor-onboarding tracker for an ops team, in a setup whose projects are clearly unrelated.

**The fix is NOT just the example.** `real-008` (`categorizer.jsonl:44`, 9 entries all parked at 0.7)
and the v0.37.0 one-case nudge `eng-routine-tickets` (`categorizer.jsonl:10`) fail on the same
boundary with no example collision — the prompt **under-teaches Inbox vs Outside-project**. Only
Example 4 (Hinglish) shows a confident Outside-project filing; no plain-English "real work, no
project → Outside-project + best goal area, confidence ≥0.6" example exists, and rule 4's Inbox
definition ("can't place it / maybe new / unsure") reads as the safe default. So the calibration
phase = **rework Example 3 + sharpen the rule-4/rule-6 boundary ("Inbox is for work you cannot even
categorise — real work that simply belongs to no listed project is Outside-project with its best
goal area") ± one positive Outside-project example.**

**Risk (must be priced in):** `inboxRecall` sits **exactly on its 0.8 floor** (4/5 — the miss is
`eng-inbox-near-duplicate-projects`, a recall-side case). Pushing the boundary toward filing risks
tipping park-side cases; every static-block edit also reshuffles borderline cases (the 23→22 lesson)
and invalidates the Groq prefix cache for all users. This is the **dedicated, several-round, paid
calibration phase** `## Status: v0.34.0` predicted — not a side-quest. Budget 2–3 rounds (~₹25–75).

---

## 3. Contradiction (b) ADJUDICATED — goldens `real-010`/`real-011` vs v0.37.0 rule 2

**The evidence.** Both transcripts open *"3. Development Goals Progress"* followed by four
distinct em-dash bullets (requirement definition · AI-enabled knowledge · stakeholder collaboration ·
…) — a numbered/listed self-appraisal section. Both goldens expect `entryCount: 1`
(`categorizer.jsonl:46-47`, creator-verified **2026-07-11**). Rule 2 as rewritten in v0.37.0
([AiPrompts.kt:35-42](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L35-L42)) says a
pasted, listed update "must NOT be compressed: produce a SEPARATE entry for EACH distinct
achievement". The model now returns 4 (`report-baseline.json:257-269` — already 4 at baseline, 0/3,
so no gated metric moves), i.e. **the model is obeying the current prompt and the goldens are
penalising it for that.**

**Verdict: the GOLDENS are stale. Rule 2 stands.** The goldens encode the pre-2026-07-19 policy the
owner explicitly reversed in the capture-review batch (item 2: "pasting a whole self-appraisal
produced too FEW entries"). A development-goals section with four distinct progress items is
precisely the "listed update" the owner wanted split — under the old expectation, three of the four
achievements were buried inside one entry, which is the exact loss v0.37.0 was built to stop.

**Fix (golden-only, no prompt edit, no cache impact):** re-spec both cases the way
`paste-appraisal-split` was deliberately designed (`## Status: v0.37.0`): drop the exact
`entryCount: 1`, assert a **robust minimum** instead. For `real-011` keep the anchor assertion
(every split entry must still land on `Raven Migration CCP Portals` — rule 4 applies the anchor "for
every entry", so splitting doesn't weaken the anchor test; that is worth asserting explicitly).
`real-010`'s placement stays deliberately unscored as its note already records. Side benefit:
removes 2 stable entryCountAccuracy misses that currently mask real over-split regressions.

---

## 4. The two dire report-only metrics — diagnosed to root cause

### 4.1 `demonstratesAccuracy` 14% (1/7) — **a real, user-facing bug, and the top finding of this assessment (F1)**

**What the numbers say.** Six of seven expectation-carrying cases fail the same way
(`report-baseline.json:172-179, 219-274`): the golden wants the category name
`"Leadership & Behaviours"`; the model returns the **competency names inside that category's
description** — `"ownership", "collaboration"` (po-extra-mentoring) and `"set the agenda", "bring
others with you", "do it the right way"…` (real-006/007/008/009/011, whose framework blurb is the
owner's real Amex-style "Leadership Behaviors Demonstrated…" text enumerating eight pillars).

**Why the model does it — the prompt invites it.** Rule 7 says *"list the **behaviours/competencies**
from the framework"* ([AiPrompts.kt:68-71](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L68-L71))
and the CONTEXT header labels the section *"BEHAVIOURS / **COMPETENCIES**"* — while AI-1
deliberately injects each behaviour's full blurb ([FrameworkPrompt.kt:42-43](../app/src/main/java/com/bragbuddy/app/data/entry/FrameworkPrompt.kt#L42-L43))
so the model can judge against it. Given a blurb that *names* eight competencies, answering with the
competency it matched is the **more precise reading of the instruction**, not a model failure.

**Why it's not just an eval artifact — the app destroys the data.** `CategorizedNormalizer`
snaps `demonstrates` to canonical BEHAVIOUR pillar names and **drops everything else**
([CategorizedNormalizer.kt:91-94](../app/src/main/java/com/bragbuddy/app/data/entry/CategorizedNormalizer.kt#L91-L94)),
and behaviour evidence exists in the rollup ONLY where an entry's `demonstrates` equals the pillar
name ([RollupAggregator.kt:173-176](../app/src/main/java/com/bragbuddy/app/data/rollup/RollupAggregator.kt#L173-L176)).
So for any user whose framework looks like the owner's own (one behaviour category, a blurb listing
its pillars — the realistic corporate shape), **nearly every behaviour tag the model produces is
thrown away**, the rollup's behaviour-evidence section starves, and the summary's "how" half — the
v0.30.0 competency-nesting feature included — has little or nothing to work with. This is a silent
degradation of the appraisal document itself.

**Verdict: prompt ambiguity + normalizer blind spot, NOT a golden bug and NOT a defensible modelling
choice.** Two fix routes, in preference order:
1. **Code-only (recommended): teach the normalizer to map, not drop.** A returned tag that
   case-insensitively matches a competency **named in exactly one** BEHAVIOUR pillar's blurb snaps to
   that pillar's name (dedupe after). Deterministic, pure, unit-testable, mirrors into `run.mjs`'s
   `demonstrates` scorer (`run.mjs:694-700` currently compares raw model tags) — **no prompt edit, so
   no cache invalidation and no borderline-case reshuffle risk.** It also *preserves* the model's
   finer-grained signal for any future per-competency feature. Needs one eval round only because the
   scorer mirror + metric move require a fresh baseline.
2. Prompt route (fold into F2's calibration batch if preferred): rule 7 gains *"always answer with
   the CATEGORY's name (the name before the colon), never a competency named inside its
   description"*. Costs cache; touches the fragile static block.
   Route 1 first; route 2 only if route 1's mapping proves too fuzzy on real blurbs.

Matching in route 1 must be conservative: match whole competency phrases as listed in the blurb
(split on the blurb's own separators), require a unique owning pillar, and leave genuinely unknown
tags dropped as today (the ghost-tag guard must survive).

### 4.2 `metricPreserved` 60% (3/5) — prompt under-asks; the number survives in the bullet but not the field (F3)

**What the numbers say.** The two stable failures are the two REAL long-form cases:
`real-003` (*"…complete the report within the span of documented **20 days**"* → `metric: null`,
0/3) and `real-005` (6-bullet paste containing *"**18 features / 76 stories**"* → all six entries
`metric: null`, 0/3) — `report-baseline.json:200-218`. The three passes are all short transcripts
where the metric is the sentence's headline ("cut reporting time by about 30 percent").

**Root cause.** Rule 11 frames the field as an afterthought — *"**Optional**, only when explicitly
stated"* ([AiPrompts.kt:87-90](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L87-L90)) —
and **no example demonstrates extraction from long-form text**: Example 3 (the only metric example)
is a one-liner whose metric is the whole point, and Example 5 (the long-paste example) contains no
numbers at all. So on a long transcript the model treats a mid-story number as narrative detail, not
a result to lift out.

**Impact — real but bounded.** The number is not *lost* (rule 3 preserves wording, so it stays in the
bullet, and `ImpactCheck.hasMeasurable` reads the bullet, so no false coaching nudges). What thins
out is everything keyed on the **field**: highlight `(metric: …)` annotations, per-area cumulative
metrics and routine-tally metrics ([RollupAggregator.kt:156-216](../app/src/main/java/com/bragbuddy/app/data/rollup/RollupAggregator.kt#L156-L216)) —
i.e. the summary model gets a weaker signal about which highlights carry numbers, on exactly the
richest captures. MED, not HIGH.

**Fix (prompt; batch into the F2 calibration release — same static block, one cache invalidation):**
reframe rule 11's metric half as an obligation — *"when the transcript states a number or measurable
result for a piece of work, ALWAYS copy it into that entry's 'metric' (verbatim-ish), wherever in the
sentence it appears"* — and give ONE of Example 5's items a mid-sentence number that lands in
`metric`, so the long-paste example models extraction too. Note the eval's check
(`run.mjs:703-707`) reads only the field — correct as-is once the prompt asks for the right thing;
do not weaken it to accept bullet-text matches.

---

## 5. Schema drift audit (axis 3) — CLEAN

Checked every prompt's OUTPUT contract against its serializer and the parse path
(`AiJson` is lenient: `ignoreUnknownKeys`, `coerceInputValues`, object-slice extraction —
[AiJson.kt:11-31](../app/src/main/java/com/bragbuddy/app/data/ai/AiJson.kt#L11-L31)):

| Prompt | Serializer | Verdict |
|---|---|---|
| CATEGORIZER OUTPUT ([AiPrompts.kt:102-122](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L102-L122)) | `CategorizedEntry` / `CategorizeResult` ([AiModels.kt:41-74](../app/src/main/java/com/bragbuddy/app/data/ai/AiModels.kt#L41-L74)) | 1:1 — all 12 fields present both sides; defaults route an omission to Inbox, never a parse failure |
| SUMMARY OUTPUT ([AiPrompts.kt:399-411](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L399-L411)) | `SummaryResult` tree ([AiModels.kt:92-177](../app/src/main/java/com/bragbuddy/app/data/ai/AiModels.kt#L92-L177)) | 1:1 incl. `deliverable`, `count`, nested `competencies`; every addition since v0.30.0 correctly **defaulted** so old cached summaries decode |
| FRAMEWORK OUTPUT | `FrameworkRefineResult` | 1:1 (moot if F6 deletes it) |
| IMAGE_EXTRACT / DOCUMENT_SCAN | `ImageExtractResult` | 1:1 (`{"text"}`) |
| IMPACT_COACH | `ImpactSuggestion` | 1:1 (`{"question"}`) |

The only place model output is intentionally discarded is the normalizer's `demonstrates` unknown-tag
drop — which is F1, a policy gap rather than drift. The eval mirrors (`run.mjs` APP-MIRROR blocks +
`PromptSyncTest` byte-equality) were spot-checked against the Kotlin and match, including the
v0.34.0 nested-deliverable lines and the v0.31.0 `!= BEHAVIOUR` placement universe.

---

## 6. Model routing + token/cache efficiency (axis 5) — sound; two watch-items

- **Routing fits the tasks.** Categorizer/coach on `llama-3.3-70b-versatile` → `llama-3.1-8b-instant`
  with the fallback's confidence capped at 0.75 so a weak 8B placement leans Inbox
  ([GroqAiProvider.kt:55-59](../app/src/main/java/com/bragbuddy/app/data/ai/GroqAiProvider.kt#L55-L59)) —
  right models, right guard. Summary on `openai/gpt-oss-120b` → 70B: right strength for the rare,
  high-stakes call; its **nondeterminism even under the eval's fixed seed** is the sole source of the
  `summaryChecks` 96.7%-vs-100% flake (F5) — a harness reality, not a routing error.
- **The cache-first restructure shipped correctly.** Static rules + examples first, user CONTEXT
  after, volatiles (`{{TODAY}}`/anchor/transcript) in the user message, COMBINE appended at the very
  end (cache-neutral) ([AiPrompts.kt:11-17, 229-245](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L11-L17)).
  Consequence to keep honouring: **`CATEGORIZER_SYSTEM` is byte-load-bearing** — any edit invalidates
  the prefix cache for every user *and* historically reshuffles borderline goldens. So F2+F3 (the
  only prompt-text edits proposed here) must ship as **one batched edit in one eval-gated release**,
  and F1/F4 deliberately avoid the static block entirely.
- Watch-item: `visionFallback` is a deprecated preview slug (`meta-llama/llama-4-scout…`,
  [AiConfig.kt:50-53](../app/src/main/java/com/bragbuddy/app/data/ai/AiConfig.kt#L50-L53)) — fine as
  a safety net, but re-verify both vision slugs at the next phase; the vision prompts also have
  **zero eval coverage** (no golden exercises image extract / document scan — acceptable gap, noted).

---

## 7. Rule-numbering + vocabulary traps (axis 6)

- **`COMBINE_MODE` hard-references "rule 2"** ([AiPrompts.kt:231](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L231)) —
  correct today; any renumbering in the F2 calibration phase must update it (and
  `eval/prompts/categorizer-combine.txt`) in the same commit.
- **"deliverable" (the defined term) is clean inside PART A/PART B** since v0.34.0's sweep. It still
  appears loosely in IMAGE_EXTRACT ("names of deliverables/projects",
  [AiPrompts.kt:451](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L451)) and
  IMPACT_COACH ("the deliverable, system or outcome",
  [AiPrompts.kt:521](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L521)) — harmless
  today because those prompts are separate calls that never define the term, but they're the two
  places to sweep if either prompt ever learns about the deliverables axis.
- **"Outside-project" (prompt sentinel) vs "No specific project" (UI label)** — an intentional split
  since v0.31.0; fine, but any future prompt edit must keep using the sentinel spelling.
- New defined-term risk to watch: F1's fix makes **"competency"** semi-load-bearing (a thing named
  inside a BEHAVIOUR blurb). If route 2 (prompt wording) is ever taken, define it explicitly rather
  than using it loosely — the "deliverable" lesson.

---

## 8. Per-prompt purpose-fit verdicts (axis 1)

- **PART A categorizer — B+.** Files right (97.1%), never loses (fail-safe → Inbox + phantom-project
  normalizer verified), splits correctly since v0.37.0, deliverable-aware with a correctly paranoid
  decline-by-default rule 5 (83.3% ≥ its 0.8 floor). Its two real faults are F1 and F2 above.
  `impactBand` 12/13 (one over-rating: `eng-development-cert` scored 0.8 vs band [0.2,0.6]) — within
  gate, note only.
- **COMBINE mode — A.** Tight contract (exactly one entry, merge don't append), correctly
  cache-neutral, correctly referenced; both combine goldens pass.
- **PART B summary — A−.** Manager-ready by construction (bounded rollup, dedup + arc rules,
  deliverable-as-thread rule 2, pinned-verbatim rule 4, competency nesting rule 5, development
  routing rule 6, metric-verbatim rule 7, setAside honesty). Sole open item is F5's one stable
  length case; inherits F1 (thin behaviour evidence in) and F3 (thin metric annotations in) — both
  are upstream fixes, not PART B edits. Don't touch this prompt.
- **PART C framework-refine — DELETE (F6).** Zero call sites outside the seam
  (`AiProvider.kt:37`, `GroqAiProvider.kt:63`, `StubAiProvider.kt:32`); the owner's 2026-07-07
  "the user builds the framework by hand, NO AI reshapes it" decision is recorded in
  `CONTEXT.md` §4 and it has stayed unused through 15+ releases. Its prompt text also drifts from
  current product rules (e.g. "keep at least one GOAL_AREA and one BEHAVIOUR" was never re-examined
  after B2). Dead code on a seam invites accidental wiring. Recommend removing `refineFramework` from
  the interface + both providers + `FrameworkRefineRequest/Result` + the FRAMEWORK prompt
  (~150 lines; not eval-gated — nothing measured touches it; a normal compile-gated cleanup).
  Alternative if the owner wants to keep the option: leave it, cost is ~zero at runtime — but then
  mark the prompt text `// PARKED — unused by owner decision 2026-07-07` so no future session
  "fixes" it.
- **Image extract — A− (unmeasured).** Faithful-extraction contract is right (first-person,
  invent-nothing, empty-string escape); no eval coverage (noted §6).
- **Document scan — A (unmeasured).** The reference-material-not-achievement distinction is exactly
  the right guard against B2a scans polluting the record; no eval coverage.
- **Impact coach — A.** The USP holds where it matters: ONE short grounded question, **never states
  or invents a number** — hard-gated (`coachNoInventedNumbers` 1.0) and clean at baseline. 91.7%
  coachPass ≥ 90% bar; the single miss (`coach-eng-flaky`) fails the harness's own content-word
  `grounded` heuristic 1/3, not a product rule — scorer strictness, not a coach defect. No change.

---

## 9. What to change, in what order (each step eval-gated where marked; **never lower a threshold**)

| Order | Change | Files | Eval cost | Risk |
|---|---|---|---|---|
| **1. F1 + F4 together** — normalizer maps blurb-competency→category (mirror in `run.mjs` scorer) + re-spec `real-010`/`real-011` to robust-minimum splits. *(Implementation note 2026-07-21: `real-006`/`real-007` also re-specced — their `demonstrates` wanted sub-competency names, written in AI-0 before the normalizer's canonical-names-only contract existed; they now want the category name, which is what the app can store.)* | `CategorizedNormalizer.kt` + test, `run.mjs`, `categorizer.jsonl` | **1 round** (baseline must move: demonstratesAccuracy ~14%→expect ≥80%; entryCount up) | LOW — no prompt byte changes, so no cache loss, no borderline reshuffle; the mapping needs the uniqueness guard |
| **2. F2 + F3 batched** — the dedicated Inbox-boundary calibration: rework Example 3, sharpen rule 4/6 boundary (± one Outside-project example), strengthen rule 11 metric extraction + a number in Example 5 | `AiPrompts.kt` + `eval/prompts/*` + `run.mjs` if shapes move, same commit (PromptSyncTest) | **2–3 rounds** | **HIGH** — static-block edit: global cache invalidation + known reshuffle behaviour; `inboxRecall` sits on its 0.8 floor; this is the phase `## Status: v0.34.0` said to budget properly |
| **3. F5 decision** (owner) — either recalibrate `detailed-length` `minAchievements.Delivery` 5→4 (the v0.31.0 "set floors to what the model RELIABLY does" lesson — a golden calibration, argued distinct from lowering the `summaryChecks` 100% threshold, but it brushes the rule, hence owner-only) or accept the known-red | `eval/golden/summary/detailed-length.json` | rides on any round | LOW either way; the honest framing is the point |
| **4. F6** — delete framework-refine | `AiProvider.kt`, `GroqAiProvider.kt`, `StubAiProvider.kt`, `AiModels.kt`, `AiPrompts.kt` | none (not a measured prompt; compile+unit gate) | LOW |

Expected end-state if 1–2 land: demonstratesAccuracy from 14% → 80%+ (behaviour evidence actually
flows for real frameworks), metricPreserved from 60% → 80%+, `inboxPrecision` finally has a fair
shot at its 90% bar (3 of its 4 stable misses are addressed: `po-metric-30-percent` + `real-008` via
the boundary work, `real-002` partially — see the note below), and the eval stops carrying two
by-construction-failing split goldens.

**Deliberately NOT recommended:** touching PART B; adding `max_completion_tokens` (refuted
v0.37.0); re-running the eval as a remedy (fixed seed — reproduces); lowering any threshold;
re-fixing anything on the v0.33.1/v0.34.0 do-NOT-re-fix lists. **`real-002` note:** the model
answers "Raven Migration" for a project named "Raven Migration CCP Portals" — the phantom rule then
parks it. A unique-containment snap in the normalizer could fix it code-only, but it interacts with
the near-duplicate-projects park policy (`eng-inbox-near-duplicate-projects` *wants* ambiguity to
park); park this idea unless step 2's rounds leave `real-002` as the last miss, then decide with
fresh evidence.

---

## 10. Bottom line

The 2026-07-11 assessment asked for an eval harness and a magic release; both exist and the gated
numbers prove it. What this refresh found is narrower and better-defined: **one silent data-loss bug
dressed up as a bad metric (F1 — fix in code, cheap, this week), one genuine calibration debt with a
named culprit (F2 — Example 3 loses the adjudication; a real phase with a real budget), two stale
goldens (F4 — delete the contradiction v0.37.0 created), one metric the prompt never actually asked
the model to fill (F3 — batch with F2), one owner decision (F5), and one piece of dead code (F6).**
Nothing here touches the architecture, PART B, or the coach — the parts of the system that carry the
product's promises are holding them.
