/**
 * BragBuddy managed AI relay — Phase M1.
 *
 * A thin, OpenAI-compatible pass-through in front of Groq so BragBuddy users get the AI without
 * bringing their own API key. It holds the ONE real Groq key server-side and forwards two endpoints:
 *
 *   POST /v1/chat/completions      →  https://api.groq.com/openai/v1/chat/completions
 *   POST /v1/audio/transcriptions  →  https://api.groq.com/openai/v1/audio/transcriptions
 *
 * PRIVACY (the app's privacy policy depends on this being TRUE — do not change it lightly):
 *   • Stateless. Request/response BODIES are never stored and never logged. The audio/chat payload is
 *     streamed straight through to Groq; we do not read it (except to optionally remap the `model`
 *     slug for chat, and even then it is never logged).
 *   • The ONLY state is per-install request COUNTS in KV (for abuse quotas), keyed by a random install
 *     token the app mints — never tied to a person, never the content.
 *
 * AUTH (M1 — hardened further by Play Integrity in M3):
 *   • X-App-Key  must equal the APP_SECRET (baked into the app build) — keeps the open internet off
 *     the owner's Groq key. Extractable from an APK, so it's a soft gate; quotas + M3 attestation back it.
 *   • X-Install-Id  a per-install UUID, used only as the quota key.
 *
 * Secrets (wrangler secret put …):  GROQ_API_KEY, APP_SECRET
 * Vars (wrangler.toml [vars] or dashboard):  DAILY_LIMIT, MONTHLY_LIMIT, GLOBAL_DAILY_LIMIT,
 *   MODEL_OVERRIDES (optional JSON slug remap — lets you repoint models with no app update).
 * Binding:  QUOTA (a KV namespace) — REQUIRED. If unbound the relay fails closed (503) so an
 *   accidental deploy can't run unbounded Groq spend; set ALLOW_NO_QUOTA="true" only for local dev.
 */

const GROQ_ORIGIN = 'https://api.groq.com';

// Our path → the Groq upstream path. Add here if new endpoints are ever proxied.
const ROUTES = {
  '/v1/chat/completions': '/openai/v1/chat/completions',
  '/v1/audio/transcriptions': '/openai/v1/audio/transcriptions',
};

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, '') || '/';
    const upstreamPath = ROUTES[path];

    if (!upstreamPath) return json(404, { error: 'not_found' });
    if (request.method !== 'POST') return json(405, { error: 'method_not_allowed' });

    // Server misconfiguration guard — fail closed rather than proxy without a key.
    if (!env.GROQ_API_KEY) return json(500, { error: 'server_not_configured' });

    // 1) App-gate: reject anything not carrying the baked app secret.
    const appKey = request.headers.get('X-App-Key') || '';
    if (!env.APP_SECRET || !safeEqual(appKey, env.APP_SECRET)) {
      return json(401, { error: 'unauthorized' });
    }

    // 2) Install token (quota key). Required, bounded length.
    const installId = (request.headers.get('X-Install-Id') || '').slice(0, 128);
    if (!installId) return json(400, { error: 'missing_install_id' });

    // 3) Per-install + global soft quotas (abuse prevention; M3 does exact per-tier metering).
    const quota = await enforceQuota(env, installId);
    if (!quota.ok) {
      // quota_unavailable = KV misconfigured, so we fail CLOSED to protect Groq spend (a real server
      // problem → 503, transient to the app). A real cap hit → 429.
      const unavailable = quota.scope === 'quota_unavailable';
      return json(unavailable ? 503 : 429, {
        error: unavailable ? 'quota_unavailable' : 'quota_exceeded',
        scope: quota.scope,
      });
    }

    // 4) Build the upstream request: strip client auth/identity headers, inject the real Groq key.
    const headers = new Headers(request.headers);
    headers.delete('X-App-Key');
    headers.delete('X-Install-Id');
    headers.delete('Authorization');
    headers.delete('Host');
    headers.set('Authorization', `Bearer ${env.GROQ_API_KEY}`);

    const isChat = path === '/v1/chat/completions';
    let body;
    if (isChat && env.MODEL_OVERRIDES) {
      // Only chat, only when a remap is configured: buffer the small JSON to swap the `model` slug.
      // (Never logged.) Audio is NEVER buffered here — it streams straight through.
      const text = await request.text();
      body = remapModel(text, env.MODEL_OVERRIDES);
      headers.set('Content-Type', 'application/json');
      headers.delete('Content-Length'); // let fetch recompute for the (possibly) rewritten body
    } else {
      body = request.body; // stream through untouched (multipart audio, or chat with no remap)
    }

    const init = { method: 'POST', headers, body };
    if (body === request.body) init.duplex = 'half'; // required when the body is a ReadableStream

    let upstream;
    try {
      upstream = await fetch(GROQ_ORIGIN + upstreamPath, init);
    } catch (_) {
      return json(502, { error: 'upstream_unreachable' });
    }

    // Stream Groq's response straight back (status + body untouched; no buffering, no logging).
    const respHeaders = new Headers(upstream.headers);
    return new Response(upstream.body, { status: upstream.status, headers: respHeaders });
  },
};

// ---------------- helpers ----------------

function json(status, obj) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

/** Constant-time-ish string compare (length is not secret here). */
function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length || a.length === 0) {
    return false;
  }
  let out = 0;
  for (let i = 0; i < a.length; i++) out |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return out === 0;
}

function intEnv(value, fallback) {
  const n = parseInt(value ?? '', 10);
  return Number.isInteger(n) && n >= 0 ? n : fallback;
}

/**
 * Soft per-install + global daily/monthly caps via KV. Check-then-bump; the KV get→put race is
 * acceptable for abuse ceilings (M3 replaces this with exact metering).
 *
 * Two deliberate failure postures:
 *  - **KV unbound** (a config mistake — the global spend ceiling can't be enforced at all) → fail
 *    CLOSED so nobody accidentally runs an unbounded Groq bill. `ALLOW_NO_QUOTA="true"` opts out
 *    (local `wrangler dev`, or a deliberate no-quota deploy).
 *  - **KV bound but a call throws** (a transient KV blip) → fail OPEN; quotas are soft and must not
 *    block legitimate relay traffic on an infra hiccup.
 */
async function enforceQuota(env, installId) {
  if (!env.QUOTA) {
    return env.ALLOW_NO_QUOTA === 'true' ? { ok: true } : { ok: false, scope: 'quota_unavailable' };
  }

  try {
    const iso = new Date().toISOString();
    const day = iso.slice(0, 10); // YYYY-MM-DD (UTC)
    const month = iso.slice(0, 7); // YYYY-MM

    const dailyLimit = intEnv(env.DAILY_LIMIT, 200);
    const monthlyLimit = intEnv(env.MONTHLY_LIMIT, 3000);
    const globalLimit = intEnv(env.GLOBAL_DAILY_LIMIT, 5000);

    // Namespaced (`q:i:` install, `q:g:` global) so a crafted X-Install-Id such as "global" can't
    // collide with the global counter's key.
    const dayKey = `q:i:${installId}:${day}`;
    const monthKey = `q:i:${installId}:${month}`;
    const globalKey = `q:g:${day}`;

    const [d, m, g] = await Promise.all([
      env.QUOTA.get(dayKey),
      env.QUOTA.get(monthKey),
      env.QUOTA.get(globalKey),
    ]);
    const dc = parseInt(d || '0', 10) || 0;
    const mc = parseInt(m || '0', 10) || 0;
    const gc = parseInt(g || '0', 10) || 0;

    if (dc >= dailyLimit) return { ok: false, scope: 'device_daily' };
    if (mc >= monthlyLimit) return { ok: false, scope: 'device_monthly' };
    if (gc >= globalLimit) return { ok: false, scope: 'global_daily' };

    await Promise.all([
      env.QUOTA.put(dayKey, String(dc + 1), { expirationTtl: 172800 }), // ~2 days
      env.QUOTA.put(monthKey, String(mc + 1), { expirationTtl: 3456000 }), // ~40 days
      env.QUOTA.put(globalKey, String(gc + 1), { expirationTtl: 172800 }),
    ]);
    return { ok: true };
  } catch (_) {
    return { ok: true }; // transient KV error → fail open (soft quota; never block on a KV blip)
  }
}

/** Optionally rewrite the chat `model` slug from a JSON map in MODEL_OVERRIDES. Never logs the body. */
function remapModel(bodyText, overridesJson) {
  try {
    const overrides = JSON.parse(overridesJson);
    const body = JSON.parse(bodyText);
    if (body && typeof body.model === 'string' && typeof overrides[body.model] === 'string') {
      body.model = overrides[body.model];
      return JSON.stringify(body);
    }
  } catch (_) {
    // Malformed override map or body — forward the original bytes unchanged.
  }
  return bodyText;
}
