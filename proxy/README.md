# BragBuddy managed AI relay (Phase M1)

A tiny [Cloudflare Worker](https://developers.cloudflare.com/workers/) that sits between the app and
Groq so BragBuddy users get AI **without bringing their own API key**. It holds the one real Groq key
server-side and forwards two OpenAI-compatible endpoints:

| App calls (`PROXY_BASE_URL` + path) | Relay forwards to |
|---|---|
| `POST …/v1/chat/completions` | `https://api.groq.com/openai/v1/chat/completions` |
| `POST …/v1/audio/transcriptions` | `https://api.groq.com/openai/v1/audio/transcriptions` |

**It is stateless** — request/response bodies are never stored and never logged. The only state is
per-install request *counts* in KV, for abuse quotas. The app's privacy policy promises this, so keep
it true (see `src/index.js`).

---

## How the app decides direct-vs-relay

`data/ai/AiEndpoint.kt`:

- **A BYOK key is set** (Settings → AI engine) → the app goes **straight to Groq** with that key. The
  relay is bypassed entirely.
- **No key** → the app calls **this relay** at `BuildConfig.PROXY_BASE_URL`, sending `X-Install-Id`
  (a random per-install token for quotas) and `X-App-Key` (`BuildConfig.PROXY_APP_SECRET`, the gate).
- **No key and no `PROXY_BASE_URL` baked in** → today's behaviour (prompt to add a key). So until you
  finish the steps below, the shipped v0.28.0 is BYOK-only with zero regression.

---

## One-time deploy (from Windows — no toolchain needed beyond Node + npm)

```bash
cd proxy
npm install
npx wrangler login          # opens the browser to authorise your Cloudflare account

# 1) Create the KV namespace for quota counters, then paste its id into wrangler.toml.
#    REQUIRED: without the QUOTA binding the relay fails closed (503 on every call) so an accidental
#    deploy can't run an unbounded Groq bill. (Local `wrangler dev` sets ALLOW_NO_QUOTA=true instead.)
npx wrangler kv namespace create QUOTA
#   → prints:  id = "abc123..."   → uncomment the [[kv_namespaces]] block in wrangler.toml and paste it

# 2) Set the two secrets (never stored in the repo)
npx wrangler secret put GROQ_API_KEY     # your real Groq key
npx wrangler secret put APP_SECRET       # a long random string you generate (see below)

# 3) Deploy
npx wrangler deploy
#   → prints your Worker URL, e.g.  https://bragbuddy-proxy.<your-subdomain>.workers.dev
```

**Generate the app secret** (any long random string works; keep it private):

```bash
# e.g. 32 random bytes hex:
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Use the **same value** for `wrangler secret put APP_SECRET` and the app's `PROXY_APP_SECRET` GitHub
secret (next section).

---

## Point the app at the relay (bakes managed mode into the next release)

Add two **repository secrets** to the app repo (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `PROXY_BASE_URL` | your Worker URL **plus `/v1`**, e.g. `https://bragbuddy-proxy.<subdomain>.workers.dev/v1` |
| `PROXY_APP_SECRET` | the same random string you gave the Worker's `APP_SECRET` |

`android-release.yml` already forwards both into the build. Then **re-push the `v0.28.0` tag** (or cut
the next version): the new APK bakes them in, and keyless installs light up on the managed path. No app
code changes.

> Note: `PROXY_BASE_URL` must end in `/v1` — the app appends `/chat/completions` and
> `/audio/transcriptions` to it.

---

## Test it

```bash
# chat — expect a normal Groq JSON completion
curl -s https://bragbuddy-proxy.<subdomain>.workers.dev/v1/chat/completions \
  -H "X-App-Key: <APP_SECRET>" -H "X-Install-Id: test-device-1" \
  -H "Content-Type: application/json" \
  -d '{"model":"llama-3.1-8b-instant","messages":[{"role":"user","content":"say hi as JSON {\"hi\":true}"}],"response_format":{"type":"json_object"}}'

# wrong/missing app key — expect 401
curl -s -o /dev/null -w "%{http_code}\n" https://bragbuddy-proxy.<subdomain>.workers.dev/v1/chat/completions \
  -H "X-Install-Id: test-device-1" -H "Content-Type: application/json" -d '{}'
```

Then on-device: remove your BYOK key (Settings → AI engine → Remove key), and confirm voice + filing
still work — that traffic is now going through the relay.

---

## Tunables (no app update needed)

Set as plain vars in `wrangler.toml` `[vars]` or the dashboard:

- `DAILY_LIMIT` / `MONTHLY_LIMIT` — per-install request ceilings (defaults 200 / 3000).
- `GLOBAL_DAILY_LIMIT` — safety cap across **all** installs per day (default 5000) — bounds your Groq bill.
- `MODEL_OVERRIDES` — optional JSON map to repoint model slugs server-side, e.g.
  `{"openai/gpt-oss-120b":"llama-3.3-70b-versatile"}`. Lets you swap models without an app release.

## Cost / abuse notes (M1 → M3)

- The `X-App-Key` gate + per-install quotas + the global ceiling keep the relay from being an open Groq
  faucet, but the app secret is extractable from an APK — it's a **soft** gate.
- **M3** hardens this: Play Integrity attestation on the token, plus real per-tier metering (free vs
  Pro) enforced here and in-app. Until then, keep `GLOBAL_DAILY_LIMIT` conservative.
- Quota counting is best-effort (KV is eventually consistent) and counts **on attempt, not on success**
  — fine for abuse ceilings, not billing. So a run of upstream failures still burns quota, and the
  relay's own over-quota `429` looks like a Groq rate-limit `429` to the app (which may retry a queued
  voice note against an exhausted **monthly** cap until it resets or the user adds a BYOK key). All
  acceptable for M1; **M3** replaces this with exact, success-based metering and a distinct cap signal.
- If the `QUOTA` KV binding is missing the relay **fails closed** (503) rather than serve unbounded; a
  transient KV error **fails open** (serves) so a KV blip never blocks legitimate traffic.
