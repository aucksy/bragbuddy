# BragBuddy — Product & UX Assessment: from shipped v0.25.0 to a paid, best-in-class subscription app

*Date: 2026-07-11 · Assessed against: v0.25.0 (versionCode 29, Android feature-complete through the 9-feature batch) · Method: full code walkthrough of every shipped flow (capture → categorize → living doc → inbox → summary → backup → retention), the PRD/prompts, the Design System boards, competitive research, and managed-key cost modelling.*

*Strategic forks locked with the creator (AskUserQuestion, 2026-07-11): **managed AI key for all users** (BYOK demoted to an advanced option) · **₹149/mo · ₹999/yr, India-first** · **paid hook = the Review Pack** · **iOS only after Android proves conversion**.*

---

## 0. Executive verdict

BragBuddy's engineering and product *bones* are genuinely best-in-class for its niche: the never-lose-an-entry pipeline, the bounded rollup→summary architecture, the honest privacy posture, and a design token system (Bricolage Grotesque, pillar colour ramp, full dark theme) that reads like a polished indie consumer app. **No direct competitor is mobile-first, voice-first, framework-structured, and private** — BragBook/BragJournal/Bragdocs/Cruit are all web tools, mostly dev-oriented; Teal is a resume/job-search suite. The positioning slot is open.

But as a *product someone pays for monthly*, v0.25.0 has one fatal flaw and three critical ones:

1. **FATAL — the app does nothing intelligent until the user creates a Groq developer account and pastes an API key into a plaintext Settings field the onboarding never mentions.** Voice doesn't transcribe, entries pile silently into the Inbox, the summary refuses to generate. A non-technical corporate employee — the target user — churns in session one, before ever seeing the magic. This kills activation *and* the subscription story (you can't charge for AI the user pays for themselves).
2. **CRITICAL — the payoff is delivered by clipboard only.** For an app whose whole thesis is "a review-ready document," there is no Word/PDF export, no share sheet, no formatted document — just plain-text copy and a developer-shaped JSON backup.
3. **CRITICAL — there is no way to pay.** No Play Billing, no entitlements, no paywall, no Play Store listing; distribution is a sideloaded GitHub APK, and two shipped permissions are Play-restricted.
4. **CRITICAL — capture is slower than the promise.** "Log in seconds" is really ~3 taps + a mandatory review step, and every fast-capture surface the category leader would have — widget, quick-settings tile, app shortcuts, share-target, Wear — is absent (`CaptureActivity` is even `exported=false`, so nothing external *can* reach it).

The gap between "shipped" and "worth paying for" is not more features — it's **removing the key wall, packaging the payoff, building the payment rail, and hardening the habit loop**. The AI economics make this easy: a daily active user costs **~₹4–7/month** in managed AI at current Groq pricing (~3–5% of a ₹149 subscription), and a capped free user can be driven under ~₹2/month.

**Scorecard (today, against "best-in-class paid app"):**

| Dimension | Grade | One-line reason |
|---|---|---|
| Core value / JTBD (summary + filing trust) | **B+** | Architecture and prompts are excellent; output ceiling and delivery format hold it back |
| Activation / time-to-first-value | **F** | BYOK wall + no aha moment inside onboarding |
| Ease of use / capture friction | **C+** | Solid flows, but 3-tap capture and zero fast-entry surfaces |
| Habit & retention | **C** | Reminder + nudges exist; no streaks, recap, or seasonal loop |
| Willingness to pay / monetization | **F (nothing exists)** | No billing, no tiers, no store presence — but the metering seam is ready |
| Trust, privacy & positioning | **A−** | Genuinely differentiated and honestly written; not yet *sold* |
| Polish / delight | **B−** | Premium materials (type, colour, motion); utilitarian mechanics (toasts, zero haptics) |

---

## 1. What is genuinely excellent — protect these while changing everything else

These are the assets a competitor cannot cheaply copy. Every recommendation below builds on them; none should be traded away.

- **The never-lose-an-entry pipeline.** Raw transcript stored before any AI step; offline voice queue with CAS commits under the processor mutex (`EntryProcessor.commitPendingAudio`); dismiss-mid-transcription queues instead of deleting; un-transcribable clips park *visibly* in the Inbox (`OfflineRecovery.kt:160-205`). This is subscription-grade reliability engineering already.
- **The rollup architecture** (`data/rollup/`). The summary reads a bounded projection, never the raw log — which is simultaneously the cost model, the latency story, and a real privacy claim ("the AI never reads your history in bulk"). It also makes managed-key economics trivially safe.
- **Prompt design** (`AiPrompts.kt`). Grounded ("invent nothing"), dedup-aware ("never list the same accomplishment twice"), rollup-aware, with the `setAside` transparency note. The impact coach that *asks but never invents a number* is exactly the right trust line.
- **Trust mechanics.** Confidence floor 0.6 → Inbox, 1-tap resolve chips (`InboxScreen.kt:245`), the no-AI Recategorize two-axis fixer (`EntryDetailSheet.kt:220-276`), the Uncategorized catch-all so a framework change can never hide entries, summary edits persisting as overrides across regenerations (`SummaryViewModel.kt:277-309`).
- **The honest privacy story.** Local-first, no account, never asks the company name, Groq named as the #1 disclosure, OS auto-backup excluded (v0.20.1), claims rewritten TRUE rather than copied. This is rare and marketable.
- **The design system as shipped.** Token-faithful colour/typography (`ui/theme/Color.kt`, `Fonts.kt`), pillar ramp with reserved semantic hues, full scheduled dark theme, warm empty states ("Got 20 seconds? Tell me about your day and I'll keep the record.").
- **The reliability wizard** (`ui/reliability/`) — the OEM battery/auto-start walkthrough with live ✓ steps and a test-reminder button is something even big apps don't ship.

---

## 2. The gap assessment — prioritized, with severity and why it matters for conversion/retention

### S0 — Fatal for a paid product

**GAP 1 · The BYOK activation wall.**
*Where:* Onboarding never asks for a key (`OnboardingScreen.kt:93-109`: Welcome → Privacy → Recover → Role → Framework). Without one: voice errors ("Add your free Groq key in Settings → Voice transcription. Or type it below." `CaptureScreen.kt:401-413`), every typed entry lands FAILED→Inbox (`EntryProcessor.kt:520-524`), the Home hint is buried ("If this persists, check your Groq key in Settings." `HomeScreen.kt:707`), the Summary tab dead-ends at "Add your Groq key to generate" (`SummaryScreen.kt:253-262`), and the Add-impact card simply hides. The key field itself is a plaintext, unvalidated `BasicTextField` with a non-tappable "console.groq.com" reference (`SettingsScreen.kt:300-319`) — and the no-key copy calls the same field two different names ("Voice transcription" vs "AI brain").
*User impact:* a brand-new non-technical user experiences a beautiful app that silently doesn't work. They will not create a developer account. They churn before the aha.
*Why it matters for subscription:* activation is the top of the funnel — nobody converts who never felt the magic. And with BYOK, the user is already paying (Groq) for the very thing you want to sell. **Locked decision: managed key for all, BYOK becomes a hidden advanced option.** Cost: ~₹4–7/mo per daily active user; free tier drivable under ~₹2/mo (Appendix A).

### S1 — Critical to "worth paying for"

**GAP 2 · The payoff has no delivery vehicle.**
*Where:* `DocExport.kt` and `SummaryExport.kt` produce clipboard plain text; grep confirms zero `ACTION_SEND`/share-sheet/PDF/DOCX in the app; the device "export" is `bragbuddy-backup.json`.
*User impact:* at the highest-stakes moment (review week), the user manually pastes plain text into Word and reformats. The product's crescendo is a toast that says "Copied — paste into Word or Docs."
*Why it matters:* the Review Pack is the locked paid hook. People pay at moments of concentrated need; a polished, formatted, one-tap-exportable document (.docx/PDF/share sheet, headings and bullets styled) *is* the perceived product. Without it, the paid tier has nothing tangible to protect.

**GAP 3 · No payment rail, no store presence.**
*Where:* No `BillingClient`, no entitlement model, no paywall UI anywhere (grep-confirmed). Distribution is a sideloaded APK from GitHub releases. `USE_EXACT_ALARM` and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` are Play-restricted (already flagged in CONTEXT.md).
*Why it matters:* subscriptions effectively require Play Billing + a Play listing (which also solves discovery and trust — nobody mass-market sideloads). The `UsageMeter` seam exists (summary generations counted; note `recordTranscriptionSeconds` has **zero callers** — the wiring was never finished), so gating is a config change *after* billing exists.

**GAP 4 · Capture is slower than the promise, and the fast lanes don't exist.**
*Where:* Notification → auto-record → Stop → mandatory review → Add ≈ **3 taps + wait** (`CaptureViewModel.kt:387-418`); FAB path is 4. No widget, no quick-settings tile, no app shortcuts, no share-target, no Wear, no lock-screen surface (manifest has only MainActivity/CaptureActivity/ReminderReceiver/FileProvider; `AndroidManifest.xml:53-99`), and `CaptureActivity` is `exported=false` so no future shortcut can reach it without a new entry point.
*User impact:* every added second of friction compounds daily. The habit — the whole retention engine — rests on capture being effectively free.
*Why it matters:* "capture in seconds" is the marketing claim; competitors' micro-logging (and the real competitor: doing nothing) punish any friction. A share-target is also a *capture superpower*: forwarding a praise email/Slack screenshot into BragBuddy is exactly how wins arrive in real life — the image-scan pipeline already exists to receive them.

**GAP 5 · The retention machinery is below subscription grade.**
*Where:* One fixed daily reminder (default 18:00 — note the docstrings say "9 PM"; `SettingsStore.kt:42`), a daily "nothing logged" card, a Fri-17:00→Sun weekly catch-up, a one-shot preview banner at 5 entries. No streaks (deliberately dropped — worth revisiting now that the goal is subscription retention), no weekly recap ("your week in wins"), no monthly moment, no review-season countdown, no widget (also a retention surface), no adaptive reminder timing.
*Why it matters:* monthly churn is the subscription killer for a product whose payoff is twice a year. The retention loop must *manufacture* interim payoffs: a weekly recap notification is the cheapest, highest-leverage one (it re-surfaces the user's own wins — motivating and zero-AI). Annual pricing (locked) hedges this, but the habit still decides renewal.

### S2 — High-value gaps for "best in class"

**GAP 6 · First-session cognitive load / Home card pile-up.** Up to **7 stacked cards** can precede the actual record (role prompt, reliability, impact, daily nudge, preview banner, waiting-voice, filing strip — `HomeScreen.kt:204-298`), and the moment several fire is precisely the fragile early-user window. The living document — the calm centerpiece the design promises — is below the fold. Needs a one-slot nudge queue (max 1–2 cards, priority-ordered, rotate).

**GAP 7 · Summary output ceiling.** The prompt is strong, but: (a) the writer model is `openai/gpt-oss-120b` — good, not the best prose available; the paid tier should buy a premium writer (the seam + remote-config slugs make this a config change); (b) output is bullets-only — many real review forms want **first-person prose paragraphs** per goal area ("Write my self-assessment") — an unserved, high-desperation format; (c) no tone control (confident/measured/concise); (d) no gap-filling: the AI never interviews the user about a thin pillar before writing ("Learning & Growth has 1 entry — want to tell me about any training/certs before I write this?"). These four are the paid tier's quality moat.

**GAP 8 · Premium materials, utilitarian mechanics.** Zero haptics anywhere (grep-confirmed — and the house rule for Gboard-crisp on-down haptics already exists in other apps); feedback is Android system toasts ("Category saved", "Copied…") next to bespoke typography; capture review adds a confirmation step but no delight; no celebratory moment when an entry files or a summary generates (the design board explicitly wanted "generation is a moment of delight").

**GAP 9 · The framework model's cognitive load.** The two-axis what/how model is right, but the editor asks a corporate user to reason about axes, per-field Type-vs-Scan, **per-item Save** with differing confirm dialogs (`CategoryEditSheet.kt:240-383`), and rename-relabel prompts. Works for user #1 (the creator); will intimidate mass-market users. Onboarding should offer **role-based framework presets** (from the role they already typed) so most users never open the editor at all.

**GAP 10 · One framework, one year, forever.** No review-cycle archive ("year ended — archive and start fresh"), no multiple frameworks (PRD non-goal, correctly deferred — but a *paid* second-year user hits this wall exactly at renewal time). A "New review year" flow is both a retention feature and a renewal moment.

**GAP 11 · Positioning is built but not told.** The privacy story (local-first, anonymous, no company name, bounded AI reads) is the most defensible differentiator and appears only inside Settings→Privacy. No landing page, no store-listing narrative, no "why not just Notion/ChatGPT" answer anywhere a prospect would see it.

### S3 — Smaller frictions (fold into other phases)

- Image capture has no offline queue — errors out, unlike voice (`CaptureViewModel.kt:232-235`).
- Serialized entry processing (one-at-a-time mutex) makes a burst of captures file slowly with only the "Filing N…" strip as feedback.
- The 6 PM/"9 PM" default-reminder drift; single fixed reminder time.
- Settings sprawl: 13+ cards mixing consumer prefs with power-user plumbing (key field, OEM wizard) — needs an "Advanced" split once managed AI lands.
- The two-names-for-one-key copy inconsistency (moot once the key field is demoted).
- 3 of 4 design-system screenshots are byte-identical placeholders — the design boards under-document the shipped Scan mode and Recover step (doc drift, not product).

---

## 3. Benchmark — what "best in class" means here

| Alternative | What it is | Price | Where BragBuddy wins | Where it loses today |
|---|---|---|---|---|
| **Do nothing + ChatGPT at review time** | The real competitor | free-ish | The record exists (you can't prompt your way to forgotten wins); framework-structured; zero-effort filing | Nothing — *if* the habit sticks; that's the whole game |
| **BragBook.io** | Web brag-doc + AI career content, dev-skewed | $10–20/mo | Mobile+voice capture; privacy (BragBook stores your work data server-side); framework mirrors *your* appraisal | Integrations (GitHub/Jira auto-import), web access, sharing |
| **BragJournal.ai / Bragdocs / Cruit** | Web journals with AI summaries, email nudges | ~$5–15/mo | Same as above: capture speed, privacy, structure | Email-nudge cadence, web/desktop presence |
| **Teal** | Career/job-search suite | ~$29/mo | Focus (appraisal, not job hunt), price, capture | Brand, breadth, resume outputs |
| **Notion / Docs / Julia-Evans template** | Manual brag doc | free | Zero manual organizing; voice; summary generation; reminders | Free; already installed; zero trust required |

**The winning claim, verbatim-ready:** *"Speak 20 seconds a day. BragBuddy files it under how your company actually judges you, and at review time hands you a polished self-assessment — without your work diary ever touching anyone's server but your own."* No competitor can say the second half; none of them try to say the first.

Best-in-class checklist BragBuddy must meet (and mostly can, cheaply): one-tap capture from anywhere (widget/tile/share-target) · works instantly with zero setup (managed key) · a exportable, formatted deliverable (.docx/PDF/prose mode) · a weekly moment of felt progress (recap) · a paywall at the moment of need, not in the way of the habit.

---

## 4. The subscription model (locked direction, detailed)

**One paid tier.** Two paid tiers (PRD §11's Plus/Pro) add choice friction without revenue at this scale — collapse to a single **BragBuddy Pro** until real usage says otherwise.

| | **Free — "build the record"** | **Pro — ₹149/mo · ₹999/yr (~45% annual discount)** |
|---|---|---|
| Capture (voice/type/scan) | Unlimited typed; fair-use voice (~3 notes/day) & scan (~10/mo) — managed AI, zero setup | Unlimited, priority processing |
| Auto-filing + living doc + Inbox + Recategorize | ✔ full | ✔ full |
| Filing model | Cheap tier (cached prompts; optionally 8B categorizer) | Best categorizer (70B) — "smarter filing" |
| Summary | **1 fresh generation per review period, Brief length** — enough to feel the payoff, not enough to carry review season | Unlimited (existing 50/mo soft cap), premium writer model, all lengths |
| **The Review Pack** | — | .docx/PDF/share-sheet export · first-person **prose mode** · tone control · gap-fill interview · pinned/promote (already built) |
| Drive backup + restore | ✔ (it's a trust feature, not a hook) | ✔ |
| Widget/tile/shortcuts, reminders, streaks/recap | ✔ (habit surfaces stay free — they create converters) | ✔ |
| BYOK (advanced) | ✔ hidden in Advanced — unlocks unmetered AI on your own key (privacy purists; also the escape valve that keeps "local-first" credible) | ✔ |

**Trial & paywall placement.** No upfront paywall, no card. The wall appears exactly where desperation does: (1) the second summary generation in a period, (2) any export/prose/tone action, (3) optionally length=Detailed. First touch of any wall offers a **7-day full-Pro trial**. Run **review-season campaigns** (the app knows the user's review-year setting!) — a notification 3 weeks before period-end: "Review season — your record has 87 wins. Get the Review Pack ready." That is the single highest-intent conversion moment this category has, and no competitor exploits it.

**Why this split converts:** the free tier keeps the *habit* whole (capture, filing, living doc — all unlimited enough), so the record grows; the record growing is what makes review week desperate; desperation meets the paywall exactly at the Review Pack. Capture is never taxed — per the PRD's own correct instinct: "meter the expensive thing, not the habit."

**Unit economics (Appendix A for detail):** Pro annual nets ~₹71/mo after the 15% Play cut; managed AI for a daily user ≈ ₹4–7/mo → **90%+ gross margin on paid**. A capped free user costs ~₹1.5–2.5/mo with prompt caching + turbo Whisper; at a conservative 3% free→paid conversion, each payer carries ~33 free users (~₹50–80/mo) — margin-positive, and the caps keep the tail bounded. Abuse control (per-device quotas + Play Integrity attestation on the proxy) is mandatory, not optional.

**Positioning line for the store listing:** privacy *as the reason to pay*, not a footnote — "No account. No server holding your career. The AI reads one entry at a time and forgets it." (The managed proxy must be a stateless, no-logging pass-through, and the privacy policy updated to disclose it honestly — same discipline as v0.20.0.)

---

## 5. Recommendations, ranked impact × effort

**Quick wins (days each, some are copy/config):**
1. Onboarding aha rehearsal — during onboarding, have the user log one real win and *watch it file live* into the framework they just saw; end on the mock "YOUR RECORD · READY" card made real. (Huge activation lift; reuses everything.)
2. Home card diet — a one-slot priority queue for nudge cards (max 1 visible + the filing strip); the record starts above the fold.
3. Haptics pass (finger-down KEYBOARD_TAP on capture/save/toggle — the house pattern) + replace system toasts with themed snackbars; add a small "filed ✓ → Performance Goals" confirmation moment.
4. Fix the key-copy inconsistency, make console.groq.com tappable, mask the key field — *interim* polish while BYOK is still primary.
5. Image-offline queue parity with voice; reminder-default copy drift (6 PM vs "9 PM").
6. Weekly recap notification ("This week: 6 wins, 2 with numbers — tap to review") — pure local data, zero AI cost, biggest cheap retention lever.

**The bigger bets (each a phase, §6):**
7. **Managed AI proxy** — the single biggest unlock (activation + monetization + Settings simplification at once).
8. **Play Store + Billing + paywall/trial** — the revenue rail; includes shedding/reworking the two Play-restricted permissions.
9. **The Review Pack** — .docx/PDF/share export, prose mode, tone control, gap-fill interview, premium writer model.
10. **Capture speed pack** — widget, QS tile, app shortcuts, **share-target** (text + screenshot → existing scan pipeline), optional "Quick add" (skip review for confident transcriptions; keep review default — it's a trust feature).
11. **Retention pack** — streaks/momentum done gently, monthly recap, review-season countdown, smart reminder time suggestions.
12. **Review cycles** — archive year → start fresh (renewal-moment feature).
13. **iOS** — after a full conversion read on Android.

---

## 6. Proposed phased roadmap (one phase per chat, existing rhythm)

| Phase | Ships | Why this order |
|---|---|---|
| **M1 · Managed AI proxy** | Proxy backend (thin OpenAI-compatible pass-through; per-device anon token, quotas, Play Integrity; no logging) · injectable BASE_URL/auth in `AiConfig`/`GroqAiProvider`/`Transcriber` · BYOK → Advanced · onboarding/Settings de-keyed · prompt caching + turbo Whisper for cost | Everything else (activation, free tier, paywall) depends on it; the seam was built for exactly this |
| **M2 · First-session & polish batch** | Onboarding aha rehearsal · Home card diet · haptics/snackbars · quick-win fixes (#4–6 above) · weekly recap | Make the free product feel best-in-class *before* charging; cheap while M1's backend settles |
| **M3 · Play Store + Billing** | Play listing (sibling playbooks exist) · Play Billing + entitlement gate · paywall screens + 7-day trial · permission rework (`USE_EXACT_ALARM`/battery-exemption are Play-restricted) · free-tier caps wired to `UsageMeter` (finish the unwired transcription metering) | The revenue rail; caps only make sense once managed AI exists |
| **M4 · The Review Pack** (hero) | .docx + PDF + share-sheet export · first-person prose mode · tone presets · gap-fill interview · premium writer model (config) | The paid tier's tangible product; lands with the paywall live |
| **M5 · Capture speed pack** | Glance widget · QS tile · app shortcuts · share-target into the scan/text pipeline · exported capture entry point · optional Quick-add | Habit velocity → retention → renewal |
| **M6 · Retention & season pack** | Streaks/momentum (gentle) · monthly recap · review-season countdown + campaign notification · smart reminder suggestion | Converts the twice-a-year value rhythm into monthly stickiness |
| **M7 · Review cycles** | Archive year, start fresh; framework presets by role | Second-year retention + renewal moment |
| **M8 · iOS** | Per parked research (SwiftUI lean, cloud macOS CI, TestFlight) | Only after M3–M4 produce a conversion read |

---

## 7. Risks & honest caveats

- **Privacy vs proxy tension.** Managed key routes entry text through *your* server. Mitigate: stateless no-logging pass-through, updated honest privacy cards (the v0.20.0 discipline), BYOK kept as the credible escape valve. This is a *positioning strength* if told properly ("we built the paid tier so it still can't read your history").
- **Free-tier cost tail.** Unlimited-feeling free AI needs real quotas + attestation from day one; the rollup design already bounds the worst case.
- **India-first price ceiling.** ₹999/yr is healthy for India but modest globally — regional Play pricing (e.g. $34.99/yr US) later; don't anchor global at Indian rates.
- **Seasonality churn.** Monthly subscribers will hit-and-run around review season; the annual push (45% discount), review-season campaigns, and M6 retention pack are the counters. Watch the monthly/annual mix.
- **Backend = new operational surface.** The app is proudly backend-less today; M1 adds uptime, key-rotation, and abuse duty. Keep it a single thin worker (Cloudflare/Supabase edge), remote-config the slugs (already the intent in `AiConfig`).
- **Categorization quality on a cheaper free tier** (if the 8B lever is pulled): validate against the ≥85% PRD bar first; prompt caching alone may be enough — don't trade filing trust for ₹1.
- **Not legal/tax advice:** pricing, subscription terms, and the updated privacy policy need a human review before public launch.

---

## Appendix A — Managed-key cost model (July 2026 Groq pricing)

Measured from the shipped prompts (`AiPrompts.kt`; ~4 chars/token):

| Call | Tokens/call (in+out) | Model | Frequency (daily user) |
|---|---|---|---|
| categorize | ~1,600–2,100 | llama-3.3-70b ($0.59/$0.79 per M) | ~30/mo |
| transcribe | ~20s audio | whisper-large-v3 (~$0.111/hr; turbo $0.04/hr) | ~30/mo ≈ 10 min |
| summary | ~3,000–8,000 | gpt-oss-120b (~$0.15/$0.75 per M) | ~2/mo in season |
| impact/vision/framework | ~450–2,000 | mixed | occasional |

- **Daily active user, current models:** ≈ **$0.06–0.08/mo (₹5–7)**.
- **Free user with caps + prompt caching (50% off the ~1,200-token static categorizer prompt) + turbo Whisper:** ≈ **₹1.5–2.5/mo**.
- **Pro annual:** ₹999/yr → ~₹83/mo gross → ~₹71 after 15% Play cut → **~₹64–66 contribution after AI** (>90% margin).
- Break-even free→paid conversion at 3%: ~33 free users/payer × ₹2 ≈ ₹66 — covered by one annual payer's monthly contribution; anything above 3% is profit. (Typical freemium converts 2–5%; the review-season campaign is the lever to beat that.)

*Cost sources: [groq.com/pricing](https://groq.com/pricing), [CloudZero Groq pricing survey](https://www.cloudzero.com/blog/groq-pricing/). Competitor anchors: [BragBook pricing/features](https://bragbook.io/features), [BragJournal](https://bragjournal.ai/), [Bragdocs](https://www.bragdocs.com/), [Cruit](https://www.askcruit.com/features/journaling), [Julia Evans' brag-document essay](https://jvns.ca/blog/brag-documents/) (the canonical free template).*
