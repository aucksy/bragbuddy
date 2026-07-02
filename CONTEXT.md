# BragBuddy — Session Context · START HERE

> **New chat? Point me at this file.** Reading this file, then the files it lists, fully rehydrates
> the project — no chat history needed. This is the single, stable entrypoint. The live, changing
> state lives in **`PROGRESS.md`** (one hop from here).

---

## 0. What this is (10-second version)
BragBuddy = a voice-or-text daily work logger that keeps an always-ready, copy-paste **appraisal
document**. Native **Android (Kotlin/Compose)**, local-first (Room), a **swappable AI provider**
seam, built in **vertical slices — one phase per chat**.
**Current status:** see the one-line snapshot in §4 (details in `PROGRESS.md`).

---

## 1. Rehydration set — read in this order
1. **This file** (`CONTEXT.md`) — invariants + protocol. Always read fully.
2. **`PROGRESS.md`** — the live state: what's built, decisions/deviations, and the **exact next
   step**. Always read fully. ← this is the one that changes every phase.
3. **`../PRD/`** — `BragBuddy-PRD.md`, `BragBuddy-Build-Brief.md`, `BragBuddy-System-Prompt.md`
   (the product spec, reasoning, and the two AI prompts).
4. **`../Design System/`** — `BragBuddy Design System.dc.html` (tokens) + `BragBuddy App v2.dc.html`
   (screens) + `screenshots/`. **Authoritative for all UI — build to match, never improvise.**
5. **The code** — `app/src/main/java/com/bragbuddy/app/` (skim `ui/theme/`, `data/`, `di/`).

**Efficiency rule (so sessions stay cheap and consistent):** always read §1–4 of this file and all of
`PROGRESS.md` (small, and they hold the critical stuff so nothing is lost). For the **big** PRD /
Design files, open only the section(s) relevant to the **current phase** (use search/targeted
reads), not the whole thing — the current phase is named in `PROGRESS.md`.

---

## 2. Firm invariants — carry these into every session, never violate
- **Never lose an entry.** The raw transcript is stored before any AI step; on AI/parse failure,
  keep the transcript and route to Inbox.
- **Never block or interrupt capture.** Fire-and-forget; unclear items wait in the **Inbox** (with
  1–2 suggested projects), never a mid-capture question. Force <~0.6 confidence to Inbox.
- **Privacy:** audio stays on-device; send only minimal text to the model. **Never ask for the
  company name** — anywhere, ever.
- **Design is authoritative.** The `Design System` files are the single source of truth for UI. If
  a screen/state isn't there, flag it — don't invent a look.
- **AI is a swappable seam.** Everything depends on the `AiProvider` interface, never a concrete
  provider. "Which model / whose key / where it runs" (baked-in → BYOK → proxy) must stay a config
  change. Two models routed by task (cheap categorizer / strong summary); slugs are remote-config.
- **Build model = one phase per chat** (see §3).
- **Build environment: cloud only.** This machine has **no local Android toolchain** (it OOMs on
  builds). Builds run via **GitHub Actions**. Do not build locally or re-download the SDK/JDK.

---

## 3. The new-chat protocol  ← keeps context intact and results consistent

**Why:** the build is deliberately sliced one-phase-per-chat. A fresh chat per phase keeps the
context small and the results consistent (rehydrating from these files, not a long transcript).

**When to start a fresh chat — the assistant must PROACTIVELY SUGGEST it, not silently continue:**
- at the **end of a phase** (the natural boundary), **or**
- when a session has grown long, spans multiple phases, or context feels heavy.
The assistant suggests it plainly (e.g. *"Phase X is done and committed — start a new chat pointed
at `CONTEXT.md` for Phase Y"*) and lets the user decide. It must **never** begin a new phase's work
in a way that risks losing context without first doing the handoff below.

**No-context-loss handoff — do this BEFORE ending a chat / at every phase boundary:**
1. **Update `PROGRESS.md`:** what was built, any decisions or deviations, how it was tested, and the
   **exact next phase** plus any setup the owner must do first (keys, repo, secrets).
2. **Commit everything;** leave the repo clean and runnable.
3. **Tell the user** the phase is done and to open a new chat pointed at this file.

**Starting the new chat:** the user points the fresh session at `CONTEXT.md`; it reads the
rehydration set (§1) and continues deterministically from the "next step" in `PROGRESS.md`.

> If any of the above ever conflicts with a firm invariant in §2, the invariant wins.

---

## 4. Status snapshot (keep to ~3 lines; full detail in `PROGRESS.md`)
- **Done:** Phase 0 — Skeleton (committed on `main`, local only).
- **Owner action owed:** create + push the GitHub repo → a green `Android Debug APK` CI run verifies
  Phase 0 (nothing is compiled locally).
- **Next:** Phase 1 — Capture loop (reminder → record → on-device STT → save raw transcript → list;
  typed entry first-class; no AI yet).

---

## 5. Where durable state lives
- **Authoritative & user-owned:** this repo — `CONTEXT.md` (stable) + `PROGRESS.md` (live). Edit
  these; they are the source of truth for project state.
- The assistant may also keep a private cross-session memory as a convenience mirror, but **the repo
  files win** if they ever disagree.
