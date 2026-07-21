# BragBuddy — AI Cost Model & Free-Quota Analysis

*Date: 2026-07-21 · Against: v0.40.1 (versionCode 49) · Prompt sizes MEASURED from the real
templates + the real rollup serializer at synthetic year-scale records; prices fetched live from
[groq.com/pricing](https://groq.com/pricing) on this date. Supersedes the smaller cost sketch in
`PRODUCT-ASSESSMENT.md` Appendix A (2026-07-11), which predates Detailed summaries becoming real
(v0.31.0 F3) and the deliverable index in the prompt (v0.34.0). Exchange rate assumed ₹88/USD.*

*Owner's question: "as the record grows through the year, what do Detailed summaries cost, and how
much free quota can we give before charging?"*

---

## 1. TL;DR

1. **Summary cost does NOT grow with the record.** The summary never reads the raw log — it reads
   the rollup digest, and every part of that digest is **capped in code**. Input grows for the
   first few months, then **plateaus at the caps**. A user with 5,000 entries pays the same per
   generation as one with 500.
2. **Even the absolute worst-case Detailed summary costs under ₹1** (~₹0.77) on the managed key.
   The realistic mid-year heavy case is ~₹0.34. The existing 50-generations/month soft cap
   therefore tops out at ~₹38/user/month — safe under the ₹149 price.
3. **The real cost driver is the DAILY categorizer, not the summary** (~₹0.15–0.24 per filed
   entry, ~₹5–8/month for a daily logger). Quota design should cap *AI filings per month*, and be
   generous with summaries — summaries are the conversion hook and cost pennies.
4. **A free user can be held to ~₹1.5–3/month** with caps; at a 3% free→paid conversion each
   annual payer (₹63/mo contribution) carries ~33 free users at a budget of ~₹1.9 each — the
   free tier is affordable, with the caps + per-device quotas the product assessment already
   mandates.
5. **One forward-looking trap:** if the Pro tier later buys a *premium writer* for summaries
   (PRODUCT-ASSESSMENT GAP 7), a Claude-class model makes the worst Detailed ≈ ₹18/generation —
   the 50/month cap would then exceed the whole subscription. A premium writer needs its own
   tighter cap (~10/mo). With gpt-oss-120b there is no problem.

---

## 2. Why the cost is bounded by design (the answer to "grows through the year")

The Summary tab reads `RollupAggregator.serialize(...)` — a digest with **hard caps at every
level** (all in `RollupAggregator.kt` / `ReviewPeriod.kt` at v0.40.1):

| Input block | Cap | Where |
|---|---|---|
| Highlights per goal area | **8 (Brief) / 15 (One page) / 60 (Detailed)** | `SummaryLength.highlightCap` |
| Deliverables listed per area | 12 | `DELIVERABLE_CAP` |
| Cumulative metrics per area | 12 | `METRIC_CAP` |
| Evidence bullets per behaviour pillar | 6 | `EVIDENCE_CAP` |
| Category/project detail text | 300 chars each | `TextCaps.DESCRIPTION_MAX` |
| Repeat logs of the same win | merged to ONE line with a count | `mergeNotable` |
| Routine work | one tally line per routine type | rollup |

**Measured prompt sizes** (real `summary.txt` template + real serializer format, synthetic records):

| Record scale | Detailed input | One page input | Brief input |
|---|---|---|---|
| Owner today (37 windowed entries) | ~5.0–5.4k tokens | — | — |
| Mid-year heavy (200 entries, 4 areas) | ~10.5–12.2k | — | — |
| **Year-end, EVERY cap maxed** (6 areas × 60 highlights, 12 deliv, 12 metrics, 4×6 evidence, 20 pinned) | **~24–28k** | ~11–13k | ~9–11k |

The worst case fits gpt-oss-120b's 131k context with room to spare. Not capped (watchlist, none
break the model): **pinned** entries (every pinned-in-window line rides in — user-controlled; a
future prompt cap of ~25 would close it), **pillar count** (details are capped, the number of
pillars isn't), and the **categorizer's** project list (grows with active projects; 300-char cap
per description, Done deliverables already drop out).

---

## 3. Unit costs (live Groq prices, 2026-07-21)

Prices per million tokens: gpt-oss-120b **$0.15 in / $0.60 out** (cached input −50%);
llama-3.3-70b **$0.59 / $0.79**; llama-3.1-8b **$0.05 / $0.08**; Whisper large-v3 **$0.111/hr**
(turbo **$0.04/hr**).

**Per summary generation** (gpt-oss-120b; output estimated 1.2k Brief → 8k worst Detailed):

| Scenario | In / Out tokens | Cost |
|---|---|---|
| Owner today, Detailed | ~5k / ~2k | **~₹0.17** |
| Mid-year heavy, Detailed | ~11.5k / ~3.5k | **~₹0.34** |
| Year-end absolute worst, Detailed | ~26.5k / ~8k | **~₹0.77** |
| Year-end worst, One page | ~12k / ~2k | ~₹0.26 |
| Year-end worst, Brief | ~10k / ~1.2k | ~₹0.20 |

50/mo soft cap × worst case = **~₹38/mo** (realistic season use, 4–8 gens: **₹1–4/mo**).

**Everything else, per action:** categorize a note (llama-3.3-70b, ~2.7k in typical → ~4.2k with
30 projects, ~200 out) **₹0.15–0.24**; transcribe a 25s clip **~₹0.07** (turbo: ₹0.02); impact
coach **~₹0.04**; image scan (qwen vision) est. **₹0.2–0.5** *(vision pricing not published on
the main table — verify at M1)*.

---

## 4. Monthly cost per user (managed key)

| Profile | Assumptions | AI cost/month |
|---|---|---|
| **Capped free user** | ~15–40 AI filings, 3 voice/day cap, turbo Whisper, 1 Brief/period | **₹1.5–3** (with the 8B categorizer lever: under ₹1) |
| **Typical daily logger** | ~33 voice entries, a few scans, 4–6 summaries in season | **₹8–15 in season, ₹6–11 off** |
| **Heavy power user** | 60 entries, 30 projects, 10 scans, 50 worst-case Detailed gens | **~₹55 absolute ceiling** (soft cap doing its job) |

**Margin at the locked prices:** Pro monthly ₹149 → ~₹127 after the 15% Play cut → **~88–92%
gross margin**. Pro annual ₹999 → ~₹71/mo → **~₹56–63 contribution after AI (~80–88%)**. Even the
absolute-ceiling power user is profitable on monthly and roughly break-even on annual.

---

## 5. Free quota — how much can we give away?

Budget anchor: at **3% conversion**, one annual payer (₹63/mo contribution) carries ~33 free
users → **~₹1.9/free-user/month budget**. At 5%: ~₹3.3.

**Recipe A (recommended — matches PRODUCT-ASSESSMENT §free-tier):** ~3 voice notes/day (turbo
Whisper), ~10 scans/mo, **AI filing capped ~40 entries/mo** (beyond the cap a note is still SAVED
— it goes to Inbox for manual tap-filing; "never lose an entry" holds, only the AI guess is
metered), **1–3 Brief generations per review period**. Cost ≈ **₹1.5–2.5/mo** on the 70B
categorizer, under ₹1 if the 8B lever validates ≥85% placement (AI-SYSTEM-ASSESSMENT; don't trade
filing trust for ₹1 without the eval).

**Recipe B (quality-first):** keep 70B everywhere, tighter caps (~15 AI filings/mo). ≈ ₹2.5–3/mo.

**What NOT to meter hard: summaries.** The owner's instinct ("detailed summaries will cost a lot")
is inverted — a whole review season of summaries costs less than one day of heavy filing. Giving a
free user a real taste of the payoff costs ~₹0.2–0.6/period and is the single best conversion
lever. Meter *entries*, gift *summaries* (Brief), sell *lengths + premium writer + Review Pack*.

Abuse control stays mandatory (per-device quotas + Play Integrity attestation on the proxy) — an
uncapped scripted client could otherwise burn ~₹0.2/request.

---

## 6. Sensitivities & risks

- **Premium-writer upgrade (GAP 7):** Claude-Sonnet-class via OpenRouter (~$3/$15 per M): worst
  Detailed ≈ **₹18/gen** → the 50/mo cap = ₹880/mo, exceeding the subscription. Mini-class
  (~$0.25/$2): ~₹2/gen worst — fine. **Action: if/when the premium writer lands, give it its own
  cap (~10/mo) or trigger it only on final export; keep gpt-oss for drafts.**
- **BYOK free keys (today's reality, pre-launch):** Groq's FREE tier enforces per-minute token
  limits (of the order of ~8k TPM for gpt-oss-120b — verify at console.groq.com, limits change).
  A mid-year Detailed prompt (~11k tokens) will start being REJECTED as too large on free keys —
  not a cost problem but a hard wall. v0.40.1's honest error copy ("too much in one go — try One
  page", "(Groq 413)") is the mitigation; the managed key (paid tier limits) removes the wall.
  **This is another reason M1 must land before heavy users reach mid-year.**
- **Rate/price drift:** all numbers re-verifiable in ~15 min: rerun the measurement script
  (session scratch: `cost-measure.mjs` — rebuild from §2's caps if lost) against
  groq.com/pricing + the day's INR rate.
- **Whisper is negligible** either way (~₹1–2/mo heavy); switch to turbo (−64%) at M1 for the
  free tier without a quality worry for short notes.

*Method note: token counts = measured characters ÷ 3.6–4.2 (BPE range for English+structured
text); outputs estimated from eval-observed behaviour (gpt-oss-120b writes conservatively short).
All figures are managed-key marginal costs; Play's 15% cut applied on revenue; ₹88/USD.*

---

## 7. RECOMMENDED LAUNCH QUOTA CARD (owner-aligned 2026-07-21 — the M3 implementation target)

**Principle (owner's requirement made concrete): the HABIT is free, the HARVEST is paid.** The
main purpose — a polished, paste-ready review document — must not be fulfillable free. The daily
loop must be free so the user's whole year accumulates inside the app; the record itself is what
converts them at review season.

| | FREE — "Build your record" | PRO — ₹149/mo · ₹999/yr (locked) |
|---|---|---|
| Typed capture | **Unlimited, always saved** (trust floor) | Unlimited |
| AI filing | **~40 notes/mo** (≈2/workday; overflow → Inbox manual tap-filing, never lost) | Unlimited (fair-use) |
| Voice notes | 3/day (turbo Whisper) | Unlimited |
| Photo scans | 5/mo | Unlimited |
| Summary | **1 Brief per review season**, ending in a locked teaser ("+ N more wins in your record — unlock your full document") | **One page + Detailed (Pro-only)**, unlimited gens (50/mo soft cap stays), regenerate freely |
| Review Pack (M4) | — | The season hero: gap-filling interview, first-person prose per goal area, tone control |
| Backup / restore / copy RAW record | **Always free** (never hostage the user's own words — sell the craftsmanship, not the data) | Same |

**Conversion mechanics:** review-season countdown campaign (M6) + every free Brief ends in the
locked teaser + optional **7-day Pro trial granted ONCE, triggered at the user's first review
season** (not at install — pre-habit, the payoff means nothing). **Push annual over monthly**
(reviews are twice a year; monthly churns post-season; ₹999 matches the product's rhythm).

**Why the free tier can't fulfil the main purpose:** Brief = 3 bullets/category — a trailer, not
a submittable self-assessment; real forms need the per-goal detail that lives in One page/
Detailed/Review Pack. Free copy-out exports the user's OWN raw words unpolished — the paid
artifact is the AI-written document. Economics per §5: free user ₹1.5–2.5/mo; one annual payer
carries ~33 free users; break-even ≈ 3% conversion.
