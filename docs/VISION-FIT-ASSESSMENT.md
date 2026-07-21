# BragBuddy — Vision-Fit Assessment (2026-07-21)

*A product-level research + assessment pass, NO build (owner-directed, PROGRESS.md ▶ WHERE WE ARE).
The whole app — framework model, setup, daily capture + guidance, output — measured against the
owner's vision, restated verbatim below. Research from real, cited sources. This doc folds in every
open item (competency arc K1–K3 · F2/F3/F5/F6 from `AI-SYSTEM-ASSESSMENT.md` · capture-review
Phase 4) into ONE prioritised roadmap (§7). Nothing here was built, no prompt or model was touched,
and no eval was run (per owner instruction; the fresh committed baseline of 2026-07-21 is the
measured evidence).*

> **THE VISION (the yardstick, verbatim):** *"A flexible framework in this app that can work for
> ANY company / employee for their high-quality mid-year and year-end assessments. The employee
> only needs to add what was done in a day, and is guided to add it in better quality — and the
> high-quality documentation is ready as per their company's framework whenever it's needed. Easy
> to use, but effective in achieving its purpose."*

---

## 0. Executive verdict

The app is **strong on the second half of the vision and weak on the first half.** "Add what was
done in a day, guided to better quality, documentation ready" — that machinery is built, measured,
and genuinely differentiated (capture-time coaching that never invents a number; a bounded rollup
that keeps the summary cheap and honest; per-section copy-out). What's **not yet true** is "works
for ANY company": the app can *represent* most companies' frameworks (§3), but a non-expert user
has almost no help getting THEIR company's framework in (§4) — no presets, no guided translation,
setup is skippable, and the realistic outcome is that most users would stay on the generic default,
which quietly breaks "documentation ready **as per their company's framework**."

| Axis | Verdict | The one-line reason |
|---|---|---|
| **A · Framework flexibility** | **GOOD — fits the dominant real-world shapes; 3 named gaps** | Two-axis model + projects/deliverables + (approved) competencies covers the hybrid enterprise form, named-pillar frameworks, and KRA/OKR shops; poor fits: narrative Q&A forms, targets/weightages, multi-year promo windows (§3) |
| **B · Setup ease** | **WEAK — the biggest vision gap** | Getting YOUR framework in = a hand-build translation task with zero help; no presets (the PRD promised them), skippable at onboarding → default-framework output for most users (§4) |
| **C · Daily capture + guided quality** | **STRONG core, guidance is one-note** | Capture is genuinely ~20s, never loses, coaches at the right moment — but the ONLY question it ever asks is "add a number"; research says the perishable details are number + baseline + who-for + how-hard (§5) |
| **D · Output fit** | **GOOD architecture, one real defect + 2 gaps** | Pillar-structured, per-section copy — pastable when the framework mirrors the form; but the Year-end window summarises the WRONG YEAR for the standard India cycle (write in April for the year that closed in March), and Q&A-style forms get material, not answers (§6) |
| **E · Roadmap coherence** | One prioritised roadmap in §7 | Sequencing re-ordered vision-first; every open item folded in; 3 owner decisions gate it |

**Bottom line:** the vision's bottleneck is not the AI, not capture, not the summary — it is
**framework setup ease (axis B)**, and secondarily the **breadth of quality guidance (axis C)**.
Both are addressable without touching the app's proven architecture.

---

## 1. Research I — how real companies' review formats actually vary

*(Full source list in §8. Prevalence figures are the best available surveys; where no reliable
number exists it is marked UNVERIFIED rather than guessed.)*

| # | Format | What the employee must produce | Named examples | How common |
|---|---|---|---|---|
| 1 | **Named-competency / behaviour framework** | Evidence written PER named pillar | Amazon LPs (Forte: 3–5 written "accomplishments" tied to LPs), Amex Leadership Behaviors (G + L rating), UK Civil Service Behaviours (~250-word STAR statement per behaviour) | Big-enterprise heavy; UK public sector universal |
| 2 | **OKR / goal / KRA-based** | Self-assessment per goal; India: KRA sheet with **weightages + targets + self-rating per KRA** | Google OKRs (0.0–1.0 grading, kept separate from pay), Infosys/TCS/Wipro KRA sheets | 83% of companies set individual goals (Mercer 2019) |
| 3 | **Rating-scale form** | 1–5 self-rating per criterion + a comment box each | SHRM sample forms, most HRIS defaults | ~60% of review questions are 5-point scales |
| 4 | **Narrative self-assessment** | Prose answers to 4–8 open questions ("key accomplishments", "what could have gone better", "development areas") | Google GRAD self-review; the generic annual form | Near-universal as a component; the dominant form at small/mid companies |
| 5 | **Promotion packet** | Multi-page evidence doc, LP/competency-tagged bullets, endorsements, often multi-year | Google promo packet, Amazon promo doc | Big-tech norm, rare elsewhere |
| 6 | **No formal framework** | Nothing, or a freeform 1:1/email | Typical small business | Dominant in SMEs (share UNVERIFIED) |
| 7 | **India KRA / increment culture** | April–March cycle: KRA setting (April, weighted) → mid-year check (Sep–Oct) → **year-end self-rating per KRA, written in April** → normalisation → increment letter | TCS/Infosys/Wipro (bell curve scrapped 2016, normalisation persists) | Standard in Indian enterprise |
| 8 | **Continuous / check-in** | Light recurring form or none | Adobe Check-in (quarterly, no prescribed form), Deloitte snapshots (employee writes nothing), Microsoft Connect (semi-annual/quarterly doc) | Growing; 71% of companies still run an annual review |

**The three findings that matter most for BragBuddy:**
1. **The canonical enterprise form is a HYBRID**: a "what" section (goals/KRAs) + a "how" section
   (competencies/values) + narrative questions layered on top. That is *exactly* BragBuddy's
   two-axis model — the core bet is validated by the field.
2. The two capture shapes a flexible tool must fit are **evidence-per-named-pillar** and
   **evidence-per-goal-with-weightage/target**, plus a **narrative fallback**. BragBuddy fits №1
   well (fully once the competency arc lands), №2 partially (no targets/weights), №3 as raw
   material rather than per-question answers (§3).
3. **Mid-year is consistently a lighter, forward-looking check-in** (progress vs existing goals, often
   no rating); year-end is the full backward-looking form. BragBuddy's two-period model matches this
   split well — but the year-end window is computed wrongly for the April-cycle case (§6.2).

## 2. Research II — what "high-quality" evidence is, and how employees actually work today

**Quality canon.** The prescribed shape everywhere is a narrative arc from context to **measurable
result**: STAR/CAR/PAR (UK Civil Service formally prescribes STAR with 250-word caps; assessors
score the *Action* part hardest), and the XYZ formula ("Accomplished X as measured by Y by doing
Z", ex-Google People Ops). What separates a strong self-assessment, per the practitioner consensus:
specific measurable accomplishments · alignment to team/company goals · honest development areas ·
length discipline (~300–500 words is common advice). A Harvard Kennedy School study (Dec 2025)
found manager ratings closely track submitted self-ratings — **underselling directly lowers the
score**, and NBER research (Exley & Kessler) shows women systematically self-describe equal
performance less favourably, so evidence-anchored accuracy is a fairness feature, not cosmetics.

**Capture-time guidance is where the leverage is.** Memory-decay and ecological-momentary-assessment
research shows retrospective recall systematically loses exactly the things that make evidence
high-quality — **the numbers, baselines and specifics fade first, the gist survives**. Rater-side
recency bias is equally documented. The practical implication, well supported though not tested as
a controlled experiment: **prompt for the perishable elements — the number, the before/after, who
it was for — at the moment of logging.** That is precisely what BragBuddy's impact coach does
(§5), and the research says it asks for only one of the three-or-four perishable things.

**How employees actually fill forms today.** The dominant workflow is **last-minute forensic
reconstruction** — calendars, sent mail, Jira/git mining, "kudos" folders — with experts advising
2–3 hours for the self-assessment itself and the surrounding process widely described as stressful.
Brag docs are evangelised but a minority practice, and even keepers batch (Julia Evans: works best
biweekly; she herself does one marathon session per cycle; Google institutionalised **weekly**
snippets, not daily). The fast-growing alternative is **paste-your-bullets-into-ChatGPT**, which is
now common and produces cliché-ridden, example-free prose from whatever the employee could recall —
it polishes but cannot recover lost evidence, and it violates many companies' data policies (27% of
orgs had banned GenAI outright, 63% restrict inputs — Cisco 2024).

**The honest competitive read for BragBuddy's daily loop:**
- **Where it wins:** it attacks the real pain — memory loss + hours of stress + generic AI prose
  with no evidence trail. Nothing found in the market does "structured to *your company's* form
  from accumulated, guided evidence" as a personal tool (Lattice does it, but employer-side).
  Local-first + no-training is a real answer to the confidentiality objection.
- **Where it doesn't (design implications, not flaws to fix today):**
  (1) **The habit is the hardest problem, not a given** — habit-app data says ~43% of users lapse
  within 30 days; the app must stay excellent at *sparse-log gap-filling* (it already is: scan +
  big-paste splitting v0.37.0 turn a December reconstruction into a filed record — keep treating
  that as a first-class path, not a fallback).
  (2) **Evidence favours weekly over daily** as the sustainable floor — the which-days reminder
  (v0.36.0) + weekly recap already support this; framing ("a few times a week is enough") matters
  more than features here.
  (3) Voice is an India-friendly bonus (7B WhatsApp voice notes/day; India leads voice-input
  adoption) but fails in open-plan offices — the app's equal-first-class Type path is the right
  call, already true.

---

## 3. Axis A — Framework flexibility · **GOOD, with 3 named gaps**

**The model:** `Framework` = a flat list of pillars, each `GOAL_AREA | BEHAVIOUR | DEVELOPMENT`
with a name + free-text blurb ([Framework.kt:12-19](../app/src/main/java/com/bragbuddy/app/data/framework/Framework.kt#L12));
projects nest under goal areas, deliverables under projects (Room v8), descriptions on both feed
the AI; the approved competency arc (K1–K3, `COMPETENCY-TAGGING-PROPOSAL.md`) will add named
competencies under BEHAVIOUR categories. Fit against each researched format:

| Format | Fit | Evidence / gap |
|---|---|---|
| Hybrid enterprise form (what + how + narrative) | **Strong** | The two-axis model IS this shape; validated by the owner's own Amex setup in the eval goldens |
| Named-pillar (Amazon/Amex/Civil Service) | **Strong once K1–K3 lands** | Today: one BEHAVIOUR category with pillars in the blurb (works, v0.38.0 maps competency answers up); K-arc makes it per-pillar evidence + coverage — the exact artifact these forms demand (an example per pillar) |
| KRA/goal sheets with weightages + targets | **Partial** | Goal areas model the KRAs and blurbs *can* hold targets informally ("measured by markets migrated" is already the pattern) — but nothing structured: the summary can never write "achieved X **vs target Y**", and weights can't order emphasis. Gap is real but cheap to narrow with editor copy that *asks* for the target in the detail box (no schema change) |
| OKR shops | **Acceptable** | Objectives = goal areas; deliverables' Active/Done lifecycle maps well to key results; 0.0–1.0 grading is out of scope (the form's job, not the record's) |
| Rating-scale forms | **Fits by design** | The rating is the employee's judgment; what they need is the evidence per criterion — pillars/competencies deliver that. Ratings themselves are correctly out of scope |
| Narrative Q&A forms | **Weakest fit** | Output sections = framework categories, not the form's *questions*. "Key accomplishments" ≈ the goal-area block (fine); "what could have gone better" has no home — the record captures wins, not struggles, and DEVELOPMENT holds learning, not honest reflection. The user gets excellent raw material, not per-question answers |
| Promo packets | **Partial, non-core** | Per-competency evidence fits (K-arc); missing: multi-year/custom windows (§6.2) and links/artifacts on entries (no URL field). Fine to leave — not the target user |
| No-framework SMEs | **Good** | The shipped default ([Framework.kt:40-61](../app/src/main/java/com/bragbuddy/app/data/framework/Framework.kt#L40)) is exactly the generic hybrid; copy-out works freeform |

**Verdict:** the data model does NOT need reshaping. The misses that matter are (i) narrative
Q&A forms (common at small/mid companies) get material rather than answers — a *future output
feature* (per-question mapping), not a model change; (ii) targets/weights — narrow via guidance
copy now, structured fields only if demand proves; (iii) custom periods (§6.2). **Do not add
model flexibility speculatively** — every researched format maps to the existing two axes plus
the approved competency level.

---

## 4. Axis B — Setup ease · **WEAK — the biggest vision gap** ⚠️ owner decision required

**What a new user actually faces:** onboarding step 5-of-6 embeds the real framework editor with
the 3-category default, is fully skippable, and says "Optional — refine this now or anytime… The
AI never rewrites it" ([OnboardingScreen.kt:303-334](../app/src/main/java/com/bragbuddy/app/ui/onboarding/OnboardingScreen.kt#L303)).
To make the app match THEIR company, the user must, by hand: invent categories, decide for each
whether it is a goal area / behaviour / development (a taxonomy the app never teaches in *their
form's* terms), name them to mirror their form's sections, and write detail blurbs. The only
assists are per-field: type, or **Scan** (OCR of a document into a single description field —
[AiPrompts.kt:471-497](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L471), wired at
[FrameworkViewModel.kt:279](../app/src/main/java/com/bragbuddy/app/ui/framework/FrameworkViewModel.kt#L279)).
**There are no presets anywhere in the app** (verified: zero source matches), although the PRD's
own PART C promised "Pick a **preset** and edit it" (`PRD/BragBuddy-System-Prompt.md` PART C).

**Why this breaks the vision:** the realistic funnel for a non-expert is skip → stay on default →
every summary comes out in "Performance Goals / Leadership & Behaviours / Learning & Growth" →
at review time the output must be re-shaped into the company's real sections — which is exactly
the reconstruction work the vision promises to eliminate. "Works for ANY company" currently means
"can represent any company, if the user does unaided translation work most users won't do."

**The tension, laid out honestly (the call is the owner's — locked rule: NO AI reshapes the
framework, 2026-07-07; the unused `refineFramework` seam is F6's recommended delete):**

| Option | What it is | Ease win | Risks | Rule status |
|---|---|---|---|---|
| **B1 · Guided hand-build** (status quo + copy) | Better editor/onboarding copy: "name categories after your form's sections"; ask for targets in detail boxes; a worked example | Small | Effort stays on the user; likely low completion | ✅ No conflict |
| **B2 · Preset library** (recommended regardless of B3) | 5–8 **static, hand-authored** templates the user picks then edits: Generic hybrid (default) · Named-pillar/leadership-behaviours · KRA sheet with weightages · OKR · Values+competencies · UK-Civil-Service-style · Narrative-form starter. Pure data, no AI call | **Large** — one tap gets a recognisable skeleton; research §1 says these few shapes cover most companies | Templates must be maintained; picking wrong still possible (they're editable) | ✅ **No conflict** — the rule bans AI reshaping; a static template the user picks and edits is the PRD's own promised design |
| **B3 · Scan-your-form → AI-DRAFTED framework, user reviews every item before anything saves** | Photograph the review form / paste its text → AI proposes categories+kinds+details into the editor as an unsaved draft → user confirms/edits/deletes item-by-item → only then saved | **Largest** — the true "any company in one minute" | OCR/model errors put wrong structure in front of a trusting user; axis-assignment mistakes (goal vs behaviour) are exactly what the model gets wrong; erodes the bright-line rule — even review-gated, the AI *is* shaping the starting point; trust cost if it's bad | ⚠️ **Conflicts with the locked rule as stated.** Ship only if the owner explicitly re-scopes the rule to "the AI never *silently* reshapes the framework; an explicit, user-invoked, review-gated draft is allowed" |

**Recommendation:** do **B2 now** (large win, zero rule conflict, small build — static data + a
picker step in onboarding/editor) plus B1's copy. Hold **B3** as a separate, explicit owner
decision — and **defer F6 (delete `refineFramework`) until that decision**: deleting the seam and
then rebuilding it for B3 would be churn; if the owner rules B3 out, delete it then (as
`AI-SYSTEM-ASSESSMENT.md` recommends). Note B3's honest counter-argument for the owner: the
research shows form-translation is precisely where non-expert users give up, and a review-gated
draft is how every competitor will do it — but the locked rule exists because trust in "MY
framework, MY words" is the product's spine. Both readings are legitimate; that's why it's an
owner call.

---

## 5. Axis C — Daily capture + guided quality · **STRONG core, one-note guidance**

**What's built and working (measured):** 3-input capture (voice/type/scan) with review-before-add;
raw transcript saved before any AI step, never lost; big-paste splitting proven (v0.37.0);
placement 97.1%, JSON validity 100% (baseline 2026-07-21). The guidance loop: a free local
has-a-number check ([ImpactCheck.kt:10-21](../app/src/main/java/com/bragbuddy/app/data/impact/ImpactCheck.kt#L10))
gates a capture-surface hint + weak→strong example; the AI **impact coach** asks ONE project-aware
question at capture, fetched in parallel while the user reads their transcript
([CaptureViewModel.kt:482-514](../app/src/main/java/com/bragbuddy/app/ui/capture/CaptureViewModel.kt#L482)),
and provably never invents a number (`coachNoInventedNumbers` 1.0, hard-gated; prompt at
[AiPrompts.kt:503-532](../app/src/main/java/com/bragbuddy/app/data/ai/AiPrompts.kt#L503)); a Home
"Add impact" list catches unquantified wins later. Research §2 validates the design point exactly:
capture-time prompting for perishable detail is where a tool can genuinely change output quality.

**Where guidance misses (against the research's definition of quality):**
1. **It only ever asks for a number.** STAR/XYZ quality has 3–4 perishable elements: the metric,
   the **baseline/before-state**, **who it was for / scale** (users, teams, markets), and
   **difficulty/context** (the "challenge" in CAR). A win that already contains a digit gets *zero*
   guidance even if it's "fixed 3 bugs" — vague, task-shaped, no outcome. The coach seam is built
   for exactly one more question-kind; broadening it (still ONE question per capture, chosen by
   what's most missing) is a small, eval-gated prompt phase.
2. **Nothing guides the "how" axis at capture.** No nudge ever asks "which behaviour did this
   show?" — behaviour evidence arrives only when the model tags it. K3's coverage insight ("no
   evidence yet for 'Bring Others With You' this cycle") is the designed fix; it also answers the
   named-pillar forms' real demand (an example per pillar) — this is the strongest product reason
   to sequence the K-arc.
3. **Placement friction is the loop's live wound**: `inboxPrecision` 88.5% (bar 90) — real wins
   still park in the Inbox (F2's Example-3 root cause, adjudicated). Capture-review **Phase 4**
   (spec'd, mockup-first) restructures this from the user's seat: placement shown and confirmed at
   capture, Inbox demoted to an offline tray. After Phase 4, F2's *user-facing* cost shrinks (a
   wrong first guess becomes a one-tap correction in the review sheet) — F2/F3 calibration is still
   worth doing, but it stops being the burning item.
4. **Habit honesty (research §2):** the sustainable floor is a-few-times-a-week, and sparse-log
   users are the majority case, not the failure case. What exists already supports this
   (which-days reminders, weekly recap, scan + paste-splitting as reconstruction paths); the copy
   should stop implying daily is the contract.

**Verdict:** the loop is real and differentiated — "guided to add it in better quality" is
~one-third delivered (numbers), with the widest headroom in question breadth (C-fix) and behaviour
coverage (K3).

---

## 6. Axis D — Output fit · **GOOD architecture, one real defect, two gaps**

**What's built:** Summary tab → Generate (period + length) reads the bounded windowed rollup
([RollupModels.kt](../app/src/main/java/com/bragbuddy/app/data/rollup/RollupModels.kt)), returns a
pillar-structured document with per-project grouping, deliverable arcs, ×N routine rollups, nested
competencies, pinned/promote/demote, derived set-aside, ⋮ retag that corrects the record; cached
until the rollup changes. Copy-out is **per-section and whole-doc**, clean plain text with
UPPERCASE pillar headings ([SummaryExport.kt:45-106](../app/src/main/java/com/bragbuddy/app/ui/summary/SummaryExport.kt#L45)).
**When the user's pillars mirror their form's sections, this IS paste-per-section into the form**
— axis D's fate is therefore coupled to axis B: output fit inherits setup quality.

**6.1 Mid-year vs year-end:** the two-period model with a configurable review-year start matches
the researched mid-year-lighter/year-end-full split well; `promptLabel` already frames the mid-year
tone ("mid-year check-in", [ReviewPeriod.kt:13-16](../app/src/main/java/com/bragbuddy/app/data/rollup/ReviewPeriod.kt#L13)).

**6.2 ⚠️ THE CONCRETE DEFECT — the Year-end window summarises the wrong year for the standard
India cycle.** `windowFor` always anchors at the review year the user is *currently in*
([ReviewPeriod.kt:45-61](../app/src/main/java/com/bragbuddy/app/data/rollup/ReviewPeriod.kt#L45)):
the most recent start-month occurrence at-or-before today, window = that start + 1 year. Research
§1: the Indian cycle is April–March with the year-end self-assessment **written in April, after
the close**. On 10 April 2026 with an April start, "Year-end" resolves to 1 Apr 2026 – 31 Mar 2027
— the brand-new, nearly-empty year — not Apr 2025 – Mar 2026, the year being reviewed. The user's
highest-stakes generation of the year silently produces a near-empty document. (Mid-year escapes:
H1 written in Sep–Oct still falls inside the same review year.) **Fix is small and code-only:** a
"Previous year" period option (or auto-offering the prior window when the current one is thin) in
`ReviewPeriods` + the Generate sheet. This should ship at the next opportunity regardless of
everything else.

**6.3 Gaps (real, not defects):** (i) narrative Q&A forms get a mineable document, not
per-question answers — a possible later feature is a light "my form's questions → which sections
feed each" mapping, design-first (§3); (ii) no target-vs-actual framing since targets are never
captured (§3); (iii) plain-text-only export is *right* for paste-into-web-form (research: most
forms are filled in HRIS text boxes) — .docx/rich export is not worth building now; a per-section
word-target ("tighten to ~250 words", the Civil-Service-style cap) is a cheap future knob the
length picker doesn't currently offer.

---

## 7. Axis E — The ONE prioritised roadmap

*Folds in: competency arc K1–K3 (approved, ⭐locked rules stand — the AI never assumes/invents a
competency or its details, user-defined only, "Competencies" naming; this assessment re-sequences,
never overturns) · F2/F3 prompt calibration · F5 · F6 · capture-review Phase 4. M3 (Play/Billing)
stays DEFERRED TO THE VERY END (owner-locked). Standing rules unchanged: any prompt change ships
eval-gated, `run.mjs` mirror in the same commit, never lower a threshold.*

**Sequencing logic (vision-first):** fix the one thing that breaks at the exact moment of payoff
(V1); close the biggest vision gap (V2 = axis B); ship the guided-quality capstone that also
de-fangs the Inbox pain (V3 = Phase 4); then calibrate the AI boundary with its pressure reduced
(V4); then the per-pillar USP (V5); then broaden coaching (V6). An alternative order the owner may
prefer — F2/F3 before Phase 4, as `AI-SYSTEM-ASSESSMENT.md` §9 suggested — is defensible (it
closes a red gate sooner); it is not recommended here because Phase 4 changes what the Inbox *is*,
and calibrating the boundary before the product redefines it risks paying the HIGH-risk
static-block edit twice.

| # | Phase | What | Size / gate | Risk | Why this position |
|---|---|---|---|---|---|
| **V1** | **Review-window fix** | "Previous year" (and previous-half) period option in `ReviewPeriods` + Generate sheet | Small; code-only; unit tests | LOW | §6.2 — the year-end generation is silently wrong for the April cycle; the single highest value-per-line change available |
| **V2** | **Setup ease** ← owner decision B2/B3 (§4) | B2 preset library + B1 guidance copy (recommended now); B3 scan-to-draft only if the owner re-scopes the locked rule | Medium; no AI change for B1/B2 (static data + UI) | LOW (B2) / MED (B3) | The biggest vision gap; every later phase's output quality inherits it |
| **V3** | **Capture-review Phase 4** (spec: `CAPTURE-REVIEW-PLAN.md`) | Review & confirm placement at capture; Inbox → offline tray; rewrites `CONTEXT.md` §2 (keep "never lose an entry"). Mockup for owner sign-off BEFORE build, per the spec | Large; HIGH-risk; design-first | HIGH | The guided-quality capstone; makes every mis-file a one-tap correction, shrinking F2's user cost before F2 is attempted |
| **V4** | **F2 + F3 calibration** (batched, per `AI-SYSTEM-ASSESSMENT.md` §9 step 2) | Rework Example 3, sharpen Inbox↔Outside-project boundary, rule-11 metric obligation + Example-5 number | Prompt phase; **EVAL-GATED 2–3 rounds (~₹25–75)**; static-block edit = cache invalidation | HIGH (known reshuffle; `inboxRecall` on its 0.8 floor) | Still worth doing after V3 (first-guess quality shows in the review sheet); metricPreserved 60% also feeds summary quality |
| **V5** | **Competency arc K1 → K2 → K3** (proposal doc; ⭐locked rules stand) | K1 structure+manual (Room v9) → K2 AI+summary (eval-gated 1–2 rounds) → K3 coverage nudge | 2–3 releases, one eval-gated | MED | The per-pillar-evidence USP for exactly the named-framework companies research §1 identifies; K3 is also axis C's behaviour-guidance fix |
| **V6** | **Coach breadth** | The coach picks ONE question from a small kind-set (number / before-after / who-for-scale) by what's most missing; still one question, still never invents | Small prompt phase; **EVAL-GATED** (coach prompt only — not the categorizer's cache-critical block) | LOW-MED | Turns "guided" from one-note to the researched quality shape without adding capture friction |
| — | **F5** (owner call, rides on any eval round) | Recalibrate `detailed-length` Delivery floor 5→4 vs keep the known-red | Golden-only | LOW | Either way, the honest framing from `AI-SYSTEM-ASSESSMENT.md` §9 stands |
| — | **F6** (⏸ HOLD until the V2/B3 decision) | Delete `refineFramework` if B3 is ruled out; repurpose the seam if B3 is chosen | Cleanup / compile-gated | LOW | Deleting then rebuilding for B3 would be churn; decide once |
| **LAST** | **M3** Play Store + Billing + metering | unchanged scope | — | — | Owner-locked position |

**Deliberately NOT building (with reasons):**
- **Tool integrations (Jira/git/calendar auto-mining)** — the strongest competitive counter-move
  (§2: "integrations reconstruct without habit"), but it contradicts local-first privacy, explodes
  scope, and the PRD parks it (P2). Revisit only after real users churn on the habit.
- **Ratings / scores in the output** — the rating is the form's job; evidence is BragBuddy's.
- **Multiple/switchable frameworks** — PRD non-goal 8 stands; B2 presets cover the setup need
  without the data-model cost. Revisit if real users demand promo-packet + form simultaneously.
- **Structured target/weightage fields** — narrow it with detail-box copy (V2/B1) first;
  schema only if demand proves.
- **.docx/rich export** — plain text into HRIS boxes is the researched reality.
- **Re-litigating the deliverables/anchor architecture, PART B, or anything on the
  v0.33.1/v0.34.0 do-NOT-re-fix lists.**

**The three owner decisions this roadmap needs:**
1. **B2 vs B2+B3** (§4) — presets only, or also the review-gated scan-to-draft (which requires
   explicitly re-scoping the 2026-07-07 rule). Gates V2's shape and F6's fate.
2. **Sequencing sign-off** — V3-before-V4 (recommended) vs the AI-assessment's F2-first order.
3. **F5** — golden recalibration vs keep the known-red (unchanged from `AI-SYSTEM-ASSESSMENT.md`).

---

## 8. Sources

**Formats (§1):** WorldatWork/WTW Compensation Programs & Practices survey ([PDF](https://worldatwork.org/media/CDN/dist/CDN2/documents/pdf/resources/research/Compensation%20Programs%20and%20Practices.pdf)) · Mercer 2019 Global PM Study ([summary](https://managebetter.com/blog/takeaways-from-mercers-2019-global-performance-management-study)) · Amazon Forte 2026 ([Fortune](https://fortune.com/2025/07/03/amazons-new-performance-review-system/), [AgilityPortal](https://agilityportal.io/blog/understanding-amazon-forte-olr-performance-review)) · UK Civil Service Behaviours ([GOV.UK](https://www.gov.uk/government/publications/success-profiles/success-profiles-civil-service-behaviours), [Acas](https://www.acas.org.uk/about-us/finding-and-applying-for-acas-jobs/how-we-assess-job-applications)) · Amex ([PeopleMatters](https://www.peoplematters.in/article/leadership-development/developing-people-managers-american-express-11592)) · Google OKR/GRAD/promo ([re:Work](https://rework.withgoogle.com/intl/en/guides/set-goals-with-okrs), [GRAD](https://buildyourfuture.withgoogle.com/programs/grad), [staffeng.com](https://staffeng.com/guides/promo-packets/)) · India KRA/increment cycle ([IndianHRM](https://www.indianhrm.com/guides/salary-increment-appraisal-india/), [Business Standard — TCS scraps bell curve](https://www.business-standard.com/article/companies/tcs-does-away-with-bell-curve-model-116041900657_1.html)) · Adobe Check-in ([CMI](https://www.managers.org.uk/knowledge-and-insights/case-study/why-adobe-killed-off-the-annual-performance-review/)) · Deloitte snapshots ([ATD](https://www.td.org/content/atd-blog/reinventing-performance-management-at-deloitte)) · Microsoft Connect ([Deel](https://www.deel.com/blog/employee-performance-reviews-at-microsoft/)) · rating-scale prevalence ([SSR](https://www.selectsoftwarereviews.com/blog/performance-management-statistics), [Culture Amp](https://www.cultureamp.com/blog/best-rating-scale-performance-reviews)) · form structure ([SHRM form](https://www.shrm.org/topics-tools/tools/forms/performance-appraisal-self-assessment), [Smartsheet](https://www.smartsheet.com/free-employee-performance-review-templates), [Management Center](https://www.managementcenter.org/resources/sample-performance-evaluation-form/)).

**Quality evidence (§2):** STAR / Success Profiles ([GOV.UK](https://www.gov.uk/government/publications/success-profiles/success-profiles-civil-service-behaviours), [Quarterdeck](https://quarterdeck.co.uk/articles/leadership-example-civil-service/), [InterviewGold](https://www.interviewgold.com/advice/personal-suitability-behaviour-statements/)) · XYZ formula ([Inc. via UT Law PDF](https://law.utexas.edu/wp-content/uploads/sites/44/2020/09/Google-Recruiters-Say-Using-the-X-Y-Z-Formula-on-Your-Resume-Will-Improve-Your-Odds-of-Getting-Hired-at-Google-_-Inc.com_.pdf)) · CAR/PAR ([Teal](https://www.tealhq.com/post/car-method-resume)) · self-assessment consensus ([Culture Amp](https://www.cultureamp.com/blog/self-performance-review-examples), [Deel](https://www.deel.com/blog/self-evaluation-examples/), [Indeed](https://www.indeed.com/career-advice/career-development/self-performance-review), [Pin](https://www.pin.com/blog/self-evaluation-examples/)) · self-rating anchoring ([Harvard Kennedy School](https://www.hks.harvard.edu/faculty-research/policy-topics/gender-race-identity/self-ratings-and-bias-performance-reviews)) · gender self-promotion gap ([NBER w26345](https://www.nber.org/papers/w26345)) · brag docs ([Julia Evans](https://jvns.ca/blog/brag-documents/), [Gergely Orosz](https://blog.pragmaticengineer.com/work-log-template-for-software-engineers/)) · recall/EMA ([Shiffman et al., PubMed](https://pubmed.ncbi.nlm.nih.gov/18509902/), [Catalog of Bias](https://catalogofbias.org/biases/recall-bias/), [episodic-detail forgetting](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9944610/)) · recency bias ([Culture Amp](https://www.cultureamp.com/blog/performance-review-bias), [Paycor](https://www.paycor.com/resource-center/articles/the-top-10-performance-review-biases/)) · daily-diary feasibility ([HBR — Power of Small Wins](https://hbr.org/2011/05/the-power-of-small-wins)).

**Behaviour today (§2):** reconstruction norm ([Ask a Manager](https://www.askamanager.org/2016/11/everything-you-need-to-know-about-your-year-end-performance-review.html), [git-standup](https://github.com/WickyNilliams/git-standup)) · time spent ([CIO](https://www.cio.com/article/289180/careers-staffing-10-tips-for-making-self-evaluations-meaningful.html), [Adobe study](https://www.marketscreener.com/quote/stock/ADOBE-INC-4844/news/Performance-Review-Peril-Adobe-Study-Shows-Office-Workers-Waste-Time-and-Tears-23677513/)) · Google weekly snippets ([I Done This](https://blog.idonethis.com/google-snippets-internal-tool/)) · ChatGPT reviews ([Axios](https://www.axios.com/2024/02/12/chatgpt-human-resources-performance-reviews), [Textio](https://textio.com/blog/chatgpt-writes-performance-feedback), [Resume Builder via Reworked](https://www.reworked.co/employee-experience/how-companies-use-ai-in-employee-performance-reviews/)) · tools & abandonment ([HypeDocs](https://www.fastcompany.com/90630966/hypedocs-promotion-imposter-syndrome-help), [I Done This](https://en.wikipedia.org/wiki/I_Done_This), habit-app churn [Dataintelo](https://dataintelo.com/report/habit-tracking-apps-market), D30 retention [Lovable](https://lovable.dev/guides/what-is-a-good-retention-rate-for-an-app), [Lattice AI drafts](https://lattice.com/blog/lattice-spring-summer-2026-product-release)) · habit formation ([UCL/Lally](https://www.ucl.ac.uk/news/2009/aug/how-long-does-it-take-form-habit)) · diary compliance ([Reynolds et al.](https://repettilab.psych.ucla.edu/wp-content/uploads/sites/302/2023/03/Reynolds-Robles-Repetti_2016_DP_Measurement-Reactivity.pdf)) · push-notification fatigue ([Sci-Tech-Today](https://www.sci-tech-today.com/stats/push-notification-statistics/)) · voice in India ([TechCrunch — WhatsApp voice notes](https://techcrunch.com/2022/03/30/people-are-sending-7-billion-voice-messages-on-whatsapp-every-day), [Think with Google](https://www.thinkwithgoogle.com/intl/en-apac/future-of-marketing/emerging-technology/ok-google-how-is-voice-making-technology-more-accessible-in-india/)) · GenAI bans ([Cisco 2024](https://newsroom.cisco.com/c/r/newsroom/en/us/a/y2024/m01/organizations-ban-use-of-generative-ai-over-data-privacy-security-cisco-study.html), [Forbes — Samsung](https://www.forbes.com/sites/siladityaray/2023/05/02/samsung-bans-chatgpt-and-other-chatbots-for-employees-after-sensitive-code-leak/)).

*Unverified items are flagged inline in §1–§2 (how far ahead people start; brag-doc prevalence;
exact hybrid %; SME no-review share; voice-logging of work specifically; the capture-time-prompting
controlled experiment). Prevalence claims marked with survey names come from those surveys'
published summaries.*

---

*Assessed 2026-07-21 against v0.38.0 (`versionCode 45`), baseline `eval/report-baseline.json`
(2026-07-21: placement 97.1% · demonstratesAccuracy 100% · inboxPrecision 88.5% RED ·
metricPreserved 60% · summaryChecks 96.7%). Companion docs: `AI-SYSTEM-ASSESSMENT.md` (prompt
level) · `COMPETENCY-TAGGING-PROPOSAL.md` (K-arc) · `CAPTURE-REVIEW-PLAN.md` (Phase 4) ·
`PRODUCT-ASSESSMENT.md` (2026-07-11 business/UX). This doc is the product-level view; where it
re-sequences those docs' plans, this doc governs once the owner picks.*
