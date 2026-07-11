# BragBuddy — AI System Assessment: making the categorizer, summary, and impact coach actually magical

*Date: 2026-07-11 · Scope: the three AI jobs that ARE the product — daily categorization ("inputs land in the right folders"), summary generation ("the write-up just works"), and the impact-coaching loop (the USP) — plus token efficiency ("users never consume more than they pay for"). Assessed against the SHIPPED prompts (`AiPrompts.kt`, v0.25.0) and the exact runtime context they receive, not the PRD spec. Companion doc: `PRODUCT-ASSESSMENT.md` (strategy). BYOK remains the test-mode setup until launch; managed key at launch — confirmed by the creator 2026-07-11.*

---

## 0. Executive verdict

The architecture is right and the prompts are *good* — grounded, invent-nothing, JSON-disciplined, with the anchor/Inbox/COMBINE mechanics correctly specified. But "good" is not "magical," and the gap is closable with **cheap, surgical changes**: the shipped categorizer has **no worked examples** (the PRD wrote three; they were never baked in), its **impact/confidence numbers are unanchored** (and those numbers silently decide what the summary is even allowed to see), the model **never sees the routine labels it already created** (so tallies fragment), behaviour pillars lost their definitions in the B2b change, **nothing validates the model's returned names** against the real project/category lists, and Whisper is never told the user's project vocabulary. On efficiency: the prompt's mutable CONTEXT block sits at the **top**, which defeats Groq's 50%-off prompt caching — restructuring is the single biggest cost lever and it's free.

**The one meta-gap above all others: there is no evaluation harness.** Magic isn't written, it's *measured into existence*. Until placement accuracy, routine-grouping stability, and question quality are scored against a golden set on every prompt/model change, every tweak is a guess. Build the eval first; everything else follows.

| AI job | Today | Verdict |
|---|---|---|
| Daily categorizer (PART A) | B | Right skeleton; unanchored scores, no examples, label drift, no output validation |
| Summary generator (PART B) | B+ | Strong dedup/arc rules; DEVELOPMENT routing ambiguity; inherits categorizer's score noise |
| Impact coach (USP) | B− for what it is, **D for where it is** | The prompt is well-constrained — but the AI question never appears during/after capture, only later on a Home card |
| Token efficiency | C+ | Sound two-model routing; caching defeated by prompt layout; unbounded project details ride every call |
| Evaluation / feedback loop | **F (absent)** | No golden set, no scoring, no regression gate |

---

## 1. What each prompt actually receives at runtime (the reality map)

This is what ships — it differs from the PRD's prompt doc in ways that matter:

- **Categorizer** (`AiPrompts.categorizer`, built in `EntryProcessor.prepare`, `EntryProcessor.kt:456-490`):
  - `{{APPRAISAL_FRAMEWORK}}` = **category NAMES + sub-folder names only — no blurbs** (`FrameworkPrompt.categorizerBlock`; the B2b change routed category details to the summary only).
  - `{{PROJECTS}}` = only GOAL_AREA placement folders, as `- Name [GoalArea] — description` — **descriptions ride in full, unbounded**.
  - `{{ROLE}}`, `{{TODAY}}` (changes daily), `{{PROJECT_ANCHOR}}`, transcript as the user message; `COMBINE_MODE` appended for merges.
  - Model: `llama-3.3-70b-versatile` → fallback `llama-3.1-8b-instant`, temp 0.2, JSON mode.
- **Summary** (`AiPrompts.summary`): framework **with blurbs** (`Framework.toPromptBlock`), pinned list, role, period, length cap, and the serialized rollup — impact-ranked highlights (`[impact 0.71] bullet (project:) (metric:) (logged N×) (standout) (evidences:)`), routine tallies, cumulative metrics, behaviour evidence (`RollupAggregator.serialize`, caps: 15 highlights/area — 8 Brief / 60 Detailed, 6 evidence/behaviour, 12 metrics). Model: `openai/gpt-oss-120b` → fallback 70B.
- **Impact coach** (`AiPrompts.impactCoach`): role, goal area, project, **the project's user-written detail**, and the bullet. Model: the cheap categorizer chain.
- **Whisper** (`GroqTranscriber`): audio + model + `response_format=text`. **Nothing else — no vocabulary prompt.**

Two structural truths to keep front-of-mind:
1. **The categorizer's numbers are load-bearing.** `impact` ranks the highlight shortlist that gets *trimmed at the cap* — a miscalibrated 0.5 vs 0.7 decides whether a win **ever reaches the summary model at all**. `confidence` gates the 0.6 Inbox floor (`EntryProcessor.kt:575-586`). These aren't cosmetic scores; they're the routing fabric.
2. **Nothing checks the model's homework.** `applyCategorized` (`EntryProcessor.kt:557-562`) stores `c.project` and `c.goalCategory` **verbatim**. The prompt says "exact name," but LLMs drift ("Raven migration" for "Raven Migration", a plausible-but-unlisted name). A drifted goal area is caught by the Uncategorized catch-all; a drifted project name files the entry under its goal area but **outside any real folder** — silently mis-shelved, PROCESSED, never flagged.

---

## 2. Daily categorizer — failure modes, ranked, each with the fix

### A1 · HIGH — No few-shot examples shipped
The PRD's system-prompt doc (§A4) contains three worked examples that anchor split granularity, routine detection, behaviour restraint, and impact levels. The shipped `CATEGORIZER` const has **none**. For a 70B model doing subjective judgments (what's routine? what's 0.7 impact? when to tag Leadership?), examples are the highest-leverage consistency lever that exists.
**Fix:** bake 3–4 examples into the static prompt (the PRD's three, plus one Hinglish + one anchored capture). ~450 tokens — and after the E1 cache restructure they live in the cached prefix, so their marginal cost per call rounds to ~zero.

### A2 · HIGH — `impact` and `confidence` are unanchored scales
"a number 0.0-1.0 estimating how appraisal-worthy" gives the model no bands; scores will cluster and wobble across days/sessions — and (see §1.1) they decide the shortlist and the Inbox floor. The 0.6 floor is only meaningful if the model's 0.6 means something stable.
**Fix — add rubric bands to the prompt:**
```
"impact" anchors: 0.2 = routine task done well · 0.4 = useful contribution, limited reach
· 0.6 = solid deliverable milestone · 0.75 = shipped outcome with a visible result or metric
· 0.9+ = major outcome — metric moved, cross-team/leadership visibility. Most days are 0.3-0.6;
reserve 0.8+ for genuinely standout work.
"confidence" anchors: 0.9+ = the transcript names a listed project (or clearly matches its
description) · 0.7 = strong contextual match · below 0.6 = do not guess — use "Inbox".
```
Reinforce both in the A1 examples. Verify against the eval set (§5).

### A3 · HIGH — `routineType` label drift fragments the tallies
The model invents a label each time ("access requests" / "SharePoint requests" / "servicing requests") and `RollupAggregator` groups by exact string (`:102`) — so one year of the same chore becomes three weak tallies instead of one strong "Resolved 200+ requests" line. The model **never sees the labels it already created.**
**Fix:** inject the existing labels — add `{{ROUTINE_TYPES}}` (the current tally labels for that user, a dozen tokens) with the rule *"When this work matches an existing routine type below, reuse that exact label; only coin a new label for a genuinely new kind of routine work."* Cheap, deterministic payoff at summary time. (Belt-and-braces: case-insensitive tally grouping in `RollupAggregator`.)

### A4 · MED-HIGH — Behaviour pillars lost their definitions (B2b overshoot)
`categorizerBlock` sends **names only**. "Leadership & Behaviours" self-describes; a real company pillar like "Enterprise Mindset" or "Courageous Authenticity" does not — the model is guessing what to tag from a 2-word name, which is exactly where over/under-tagging comes from. The B2b rationale (category detail = summary-only, so edits don't disturb filing) is right for **goal areas**; for **behaviours** the blurb *is the tagging definition*.
**Fix:** reinstate blurbs for BEHAVIOUR (and DEVELOPMENT) pillars in `categorizerBlock`; keep goal areas names-only. One-line change in `FrameworkPrompt.kt`; the B2b principle survives where it matters.

### A5 · MED — No output validation / snap-to-known-names
See §1.2. The cheapest robustness win in the whole pipeline is a deterministic post-parse validator in `EntryProcessor`:
- `project`: case-insensitive exact match against the placement list → snap to canonical casing; no match → try trimmed/whitespace-normalized; still no match → treat as Inbox **with the model's string preserved in `suggestedProjects`** (the Inbox chip then offers it — nothing lost, nothing phantom).
- `goalCategory`: same against pillar names (Uncategorized already backstops, but snapping keeps Home clean).
- `demonstrates`: drop names not in the framework (prevents ghost behaviours in the rollup).
No AI, no tokens, kills a whole class of silent mis-filings.

### A6 · MED — `dateMentioned` date math shifts review windows
"last Tuesday" → the model computes an ISO date from `{{TODAY}}`; models get relative-date math wrong routinely, and the result writes `occurredAt` (`applyCategorized:559`) which drives **period windowing** — a botched year boundary silently drops the entry from the review period.
**Fix:** prompt-side, restrict it: *"only when the transcript states an explicit date or an unambiguous relative day (yesterday, last Friday); if in doubt, omit."* Code-side: reject `dateMentioned` more than ~370 days from today or in the future.

### A7 · MED — Loose/alias project mentions aren't addressed
Users say "the migration," "raven," "that dashboard thing." Nothing tells the model shorthand is expected and the folder descriptions are there to resolve it.
**Fix:** one rule line: *"Users refer to projects loosely — by shorthand, partial names, or what the project does. Match against each project's name AND description; when a loose mention clearly fits exactly one listed project, use it (confidence ≥0.7). If it could fit more than one, prefer Inbox with suggestedProjects."*

### A8 · MED — Unbounded project descriptions ride on every call
A user who **Scans a job description into a project detail** (the B2a feature!) can push a 2,000-token blob into `{{PROJECTS}}` — on *every* categorize call, forever, while also drowning the signal.
**Fix:** truncate per-project description to ~300 chars in `prepare()` (first sentence(s) — descriptions front-load what a project is), and cap the field length in the editor UI with a "keep it to a line or two — this rides on every filing" hint. Same cap for `{{PROJECT_DETAIL}}` in the impact coach.

### A9 · MED-HIGH (quality, not prompt) — Whisper gets no vocabulary
`GroqTranscriber` sends audio only. Whisper's `prompt` parameter (~224 tokens, supported on Groq's OpenAI-compatible endpoint) biases recognition toward given spellings — the difference between "Raven migration" and "raven my gration", between colleagues' names spelled right and mangled. Transcription errors poison everything downstream and *the user sees them in the review step* — it's the first "is this thing smart?" impression.
**Fix:** pass `prompt` = project names + role + a few framework terms (already in memory at call time). Zero extra cost, direct accuracy gain, biggest on the Hinglish/jargon reality the PRD itself flags.

---

## 3. Summary generator — failure modes, ranked

### B1 · MED — DEVELOPMENT content routing is ambiguous
`RollupAggregator.aggregate` treats every non-BEHAVIOUR pillar as a goal area (`:88`), so Learning & Growth arrives in `{{ROLLUP}}` as `GOAL AREA: Learning & Growth` — but the output schema wants development material in a separate `development: []` array, and rule 5 just says "fill DEVELOPMENT if there is real material." The model must *infer* that one of the "goal areas" it was handed is actually the development pillar and re-route it. Sometimes it will; sometimes it'll appear under `goalAreas` (or both), and the rendered doc changes shape between regenerations.
**Fix:** serialize development pillars under their own header (`DEVELOPMENT AREA: …`) in `RollupAggregator.serialize`, and make rule 5 explicit: *"Items under a DEVELOPMENT AREA belong in `development`, never in `goalAreas`."*

### B2 · HIGH (inherited) — Score noise decides what the model can see
The Brief preset shows the model only the **top 8 highlights per area by `impact`**. With unanchored daily scores (A2), a genuinely strong win scored 0.55 on a stingy day is *invisible* to the summary — the user reads the output, notices their best work missing, and trust dies exactly at the payoff moment. Fixing A2 is therefore a **summary-quality** fix. (The ★ Standout flag and Pin are the manual overrides — they already bypass ranking; good.)

### B3 · LOW-MED — Pinned items can duplicate shortlist highlights
Pinned bullets ride in `{{PINNED}}` *and* usually also appear in the rollup highlights. Rule 1's dedup is about repeated logging, rule 3 says "always include every pinned item" — nothing says *include it once*.
**Fix:** append to rule 3: *"A pinned item may also appear among the highlights — include it once, in its strongest phrasing."*

### B4 · LOW — Metric preservation isn't explicit
"Never invent numbers" is there; "never *drop* the user's numbers" isn't. Models trimming for a length cap sometimes sacrifice the metric clause — the single most valuable part of a bullet.
**Fix:** add: *"When a selected achievement carries a metric, keep the number verbatim in the bullet — a line with its number beats two lines without."*

### B5 · LOW — Cross-area arc duplication
Rule 1's arc-merging ("started X" → "shipped X") operates within a goal area; if daily filing ever placed stages of one deliverable under two areas, both survive. Rare, and Recategorize is the user fix. Note only.

### What's already strong (don't touch)
The bounded-rollup design; deterministic pre-dedup with counts *before* the cap; the `(logged N×)`/count contract; `setAside` transparency; behaviour-evidence-or-omit; pinned always-include; role-shapes-emphasis-not-facts; overrides persisting across regenerations. This is a genuinely well-engineered summarization system — the fixes above are calibration and routing, not redesign.

---

## 4. The impact coach — the USP is built, but standing in the wrong room

The prompt itself (`IMPACT_COACH`) is well-constrained: ONE ≤18-word question, names the *kind* of measure, grounded in the project's own notes, hard "never state/guess/invent a number" rule, graceful generic fallback. Verified wiring: project-aware via `ProjectEntity.description`, stale-suggestion cancellation, non-destructive COMBINE merge that can never demote a win (`EntryProcessor.addImpact`). Good.

**C1 · HIGH — The AI question never appears during or after capture.** The USP as stated — *"AI intelligently understands the work input and, considering the existing project details, asks easy-to-understand correct questions during/after the daily record"* — is not what ships. At capture time the user gets the **static regex nudge** ("Add numbers & impact / %, time saved, ₹, people, before → after") — the same canned text for shipping a redesign and unblocking a team. The intelligent, project-aware question exists **only** on the Home "Add impact" card, which the user meets later, *if* they scroll, *if* they expand it. The magic moment is misplaced: the highest-yield instant to ask "how much did drop-off fall?" is **the moment the work is still in their head — right after Add.**
**Fix — move the brain to the moment (without violating "never block capture"):**
1. On Add (voice/image review or typed save), fire `suggestImpact` on the **raw transcript** (the prompt takes "the achievement" — it doesn't need the filed bullet) *in parallel with* filing.
2. The existing nudge surface (`NumberNudge` / `SavedNudgeSheet`) shows the static text immediately, then **swaps in the AI question when it lands** (~1s on Groq). No new UI, no blocking, static fallback intact on no-key/offline/slow.
3. The anchored project's detail rides along when the capture came from a folder; otherwise role + goal area still beat the generic line.
4. Keep the Home card as the *catch-up* surface (its real job: wins that slipped through).
This one change is the difference between "an app with a tips label" and "it asked me exactly the right question." It's also cheap: ~500 tokens on the 70B chain per no-number capture (~10-15/mo/user ≈ **₹0.4/mo**).

**C2 · MED — The coach degrades silently when project details are empty** — and most users won't write them. Rule 2 already falls back, but the *magic* is the grounding. Mitigations in order of leverage: (a) onboarding/framework-editor copy that sells the detail field as "this is how the AI asks you smarter questions" (it's currently pitched as metadata); (b) when the coach fires ungrounded, occasionally append a one-tap hint — "Add a line about how this project is measured and I'll ask sharper questions"; (c) *never* auto-write the detail (the creator's no-AI-reshapes-framework rule stands).

**C3 · LOW-MED — No answer-quality loop.** The user answers "made it much faster" — still no number; the COMBINE merge happens; the win re-qualifies as a candidate later (correct but slow). A local `ImpactCheck.hasMeasurable` on the reply could catch it *before* merging with one gentle re-ask ("Can you put a rough number on 'much faster' — even ~20% helps?"), max once, never blocking. Ship after C1.

---

## 5. The missing engine: an evaluation harness (build this FIRST)

Every fix above is a hypothesis until scored. The PRD set the bar (≥85% correct placement); nothing measures it. Before touching the prompts, build the measuring stick — the same discipline as the app's unit tests, applied to the AI:

1. **Golden set.** Export the creator's real history (rawTranscript + human-verified placement/routine/impact-band per entry — the Recategorize log is literally a labeled-corrections dataset). Target ~60-80 cases; add synthetic edge cases: Hinglish, multi-item, shorthand project mentions, praise screenshots, routine bursts, relative dates, empty/no-work notes.
2. **Harness.** A small Node script (the ForgeAI harness pattern) that imports the exact prompt text (or a JSON dump of `AiPrompts` builders), calls Groq with the production model chain + JSON mode, and scores: placement accuracy, Inbox precision/recall (things that *should* park vs shouldn't), routineType stability across paraphrases, impact-band agreement, JSON validity, split granularity. Cost per full run: ~$0.02.
3. **Summary eval.** Feed 3-4 synthetic year-rollups (dense / sparse / routine-heavy / dev-heavy) and check: no duplicate accomplishments, arcs merged, metrics preserved verbatim, development routed correctly, pinned included once, setAside speaks in categories.
4. **Coach eval.** 20 bullets × grade each question: ≤18 words, names a measure kind, grounded when detail exists, zero invented numbers.
5. **Gate.** Run on every prompt/slug change (locally or a manual-dispatch GitHub Action with a secret key). Prompt changes get the same rule code changes have: **no ship without green.**

This also future-proofs the launch decisions: when the managed proxy arrives, the same harness answers "can the free tier run the 8B categorizer?" with data instead of vibes.

---

## 6. Token efficiency — "never consume more than they pay for"

### E1 · The big one: the prompt layout defeats Groq's prompt caching (50% off cached input)
Caching discounts the **longest unchanged prefix**. The shipped `CATEGORIZER` puts the CONTEXT block — `{{TODAY}}` (changes daily), role, framework, projects, anchor — at the **top**, before all the static rules. Effective cached prefix: ~100 tokens out of ~1,400+. The file's own comment says "static instruction text stays first so prompt caching can discount it" — the template just doesn't do it.
**Fix:** restructure to *static-first, volatile-last*: role-line + all rules + output schema + few-shot examples first (fully static, ~1,600 tokens with examples), then the CONTEXT block at the end of the system message (framework/projects/role — changes only when the user edits them), with `{{TODAY}}` + anchor + transcript in the **user message** (always fresh). Result: on a typical day every call after the first hits a ~1,600-token cached prefix → **roughly 40-45% off categorize input cost**, which is the dominant spend. This also makes the A1 examples effectively free.

### E2-E7 · The rest of the levers
- **E2 — Cap what rides along** (= A8): truncated project descriptions bound `{{PROJECTS}}`; framework blocks are already tight post-B2b.
- **E3 — Whisper tiering:** `whisper-large-v3` today (~$0.111/hr); `v3-turbo` is ~64% cheaper. **Don't switch blind** — large-v3 is stronger multilingual, and Hinglish is the stated reality. Eval both on real clips (§5 harness); if turbo holds, take the saving; if not, transcription is the *last* place to cheap out (it's the user-visible first impression).
- **E4 — Keep the summary model strong.** It runs a handful of times a year on a bounded input; its cost is noise (~½¢/generation). This is the "spend where the stakes are" spot — at launch, per the PRD, consider an even stronger paid writer here.
- **E5 — Finish the metering** (`recordTranscriptionSeconds` has zero callers; categorize calls aren't counted at all). The managed proxy needs per-device budgets: count categorize calls + transcription seconds + summary generations on-device *and* enforce server-side quotas at the proxy. The fair-use caps from `PRODUCT-ASSESSMENT.md` §4 then have real teeth.
- **E6 — Silent-fallback quality flag:** when the 8B fallback files an entry (rate-limit days), quality drops invisibly. Cheap guard: cap fallback-filed confidence at 0.75 so borderline 8B placements lean Inbox rather than silently mis-filing. Optional polish.
- **E7 — What NOT to do:** don't route "short" transcripts to the 8B to save money (placement trust is the product; the eval can revisit); don't batch (latency is the UX); don't trim the review-before-Add step to save a call (it's a trust feature, and it costs nothing — filing happens once either way).

### The per-user budget after E1/E2 (managed-key math)
| Item | Today | After fixes |
|---|---|---|
| Categorize (30/mo) | ~51k tok ≈ ₹2.6 | ~33k effective (cached) ≈ **₹1.6** — *with* examples added |
| Whisper (~10 min) | ₹1.6 (large-v3) | ₹1.6 (or ~₹0.6 on turbo if eval passes) |
| Impact coach at capture (new, ~12/mo) | — | **₹0.4** |
| Summary (seasonal avg) | ~₹0.4 | ~₹0.4 |
| **Total / active user / month** | **~₹4.6** | **~₹4.0 (₹3.0 w/ turbo)** — *more* AI features for *less* money |

The USP upgrade (C1) is paid for twice over by the cache restructure alone. A capped free tier stays under ~₹2/user/mo; the ₹149 Pro price carries a >95% AI-cost margin.

---

## 7. Recommended prompt-change set (concrete, in ship order)

Each is small; together they're the "magic" release. All gated on §5's eval showing green (build the eval first — Phase AI-0).

1. **Restructure both prompts static-first** (E1) + move `{{TODAY}}`/anchor/transcript to the user message. *(Efficiency + enables #2 free.)*
2. **Bake in 4 few-shot examples** (A1) — the PRD's three + one Hinglish/shorthand case, each showing calibrated impact/confidence and routine labeling.
3. **Add the impact & confidence rubrics** (A2) — the band definitions from §2.
4. **Inject `{{ROUTINE_TYPES}}` + reuse rule** (A3).
5. **Behaviour blurbs back in the categorizer block** (A4) — one line in `FrameworkPrompt.kt`.
6. **Alias-matching rule** (A7) + **date restraint rule** (A6).
7. **Code, not prompt:** post-parse validator (A5), description caps (A8), Whisper `prompt` vocabulary (A9), date sanity bound (A6).
8. **Summary prompt:** development routing (B1, with the serializer header change), pinned-once (B3), metric-verbatim (B4).
9. **Impact coach at capture** (C1) — the USP move; then the empty-detail nudge (C2) and the one-shot re-ask (C3).

Suggested phasing in the house rhythm: **Phase AI-0** = eval harness + golden set (no app change, no release) → **Phase AI-1** = categorizer + efficiency set (#1-7, one release, eval-gated) → **Phase AI-2** = summary set + coach-at-capture (#8-9, one release, eval-gated). AI-1/AI-2 slot naturally before or alongside the managed-proxy phase (M1 in `PRODUCT-ASSESSMENT.md`) — same seam, and the proxy inherits an already-cheap, already-measured brain.

---

## 8. Bottom line

Nothing here requires new architecture — the two-prompt design, the rollup, the seam, and the invariants all hold. The distance to "it just works, magically" is: **anchor the numbers, show the model examples, let it see its own labels, validate its answers, teach Whisper the user's words, ask the smart question at the moment it matters — and measure all of it before shipping.** Every one of those is days, not weeks, and the efficiency restructure pays for the entire upgrade.
