#!/usr/bin/env node
/**
 * BragBuddy AI eval harness (Phase AI-0).
 *
 * Runs the golden sets against the LIVE Groq API using the exact prompts, models and request shape
 * the app uses, scores the results against the thresholds in docs/IMPLEMENTATION-PLAN.md, and
 * writes eval/report.md (+ eval/report.json for machine comparison). Non-zero exit on gate failure.
 *
 * Usage:
 *   GROQ_API_KEY=gsk_… node eval/run.mjs                  # full run, gated
 *   node eval/run.mjs --dry-run                           # no API calls: validate goldens + prompt build
 *   node eval/run.mjs --only categorizer --limit 5        # smoke a subset
 *   node eval/run.mjs --baseline eval/report-baseline.json  # additionally gate on ≥ baseline (AI-1+)
 *   node eval/run.mjs --no-gate                           # write the report, always exit 0
 *
 * Node 20+, zero dependencies. Everything that must mirror the app is marked APP-MIRROR with the
 * Kotlin source it copies — if that Kotlin changes, change it here too (PromptSyncTest guards the
 * prompt TEXTS; these mirrors guard the request/context SHAPE).
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const EVAL = path.join(ROOT, 'eval');

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

const args = process.argv.slice(2);
function argValue(name, fallback = null) {
  const i = args.indexOf(name);
  if (i >= 0 && i + 1 < args.length) return args[i + 1];
  const pref = args.find((a) => a.startsWith(name + '='));
  return pref ? pref.slice(name.length + 1) : fallback;
}
const OPTS = {
  only: argValue('--only'), // categorizer | coach | summary
  out: argValue('--out', path.join(EVAL, 'report.md')),
  jsonOut: argValue('--json-out', path.join(EVAL, 'report.json')),
  baseline: argValue('--baseline'),
  limit: Number(argValue('--limit', '0')) || 0,
  dryRun: args.includes('--dry-run'),
  noGate: args.includes('--no-gate'),
};
// Fail CLOSED on operator error — this is a ship gate, a typo must never green-light.
const SUITES = ['categorizer', 'coach', 'summary'];
if (OPTS.only && !SUITES.includes(OPTS.only)) {
  console.error(`--only must be one of ${SUITES.join('|')}, got "${OPTS.only}"`);
  process.exit(1);
}
if (OPTS.baseline && !fs.existsSync(OPTS.baseline)) {
  console.error(`--baseline ${OPTS.baseline} does not exist — the ≥-baseline gate cannot silently disappear. Commit a baseline first (AI Eval workflow · commit_baseline).`);
  process.exit(1);
}

// ---------------------------------------------------------------------------
// Ship thresholds (docs/IMPLEMENTATION-PLAN.md · Phase AI-0)
// ---------------------------------------------------------------------------

const THRESHOLDS = {
  placementAccuracy: 0.85,
  inboxRecall: 0.8,
  inboxPrecision: 0.9,
  jsonValidity: 1.0, // all calls parse (fallback counts only if primary parse-failure rate < 10%)
  primaryParseFailMax: 0.1,
  routineReuse: 1.0,
  impactBand: 0.8,
  coachPass: 0.9, // and zero invented numbers = hard fail
  summaryChecks: 1.0,
};

// ---------------------------------------------------------------------------
// APP-MIRROR · models + endpoint, parsed straight out of AiConfig.kt so they can never drift.
// ---------------------------------------------------------------------------

function loadAiConfig() {
  const kt = fs.readFileSync(
    path.join(ROOT, 'app/src/main/java/com/bragbuddy/app/data/ai/AiConfig.kt'),
    'utf8',
  );
  const grab = (name) => {
    const m = kt.match(new RegExp(`${name}\\s*=\\s*"([^"]+)"`));
    if (!m) throw new Error(`AiConfig.kt: could not find ${name}`);
    return m[1];
  };
  const categorizerModel = grab('categorizerModel');
  return {
    // GROQ_BASE_URL override: local mock-server smoke tests now, the M1 managed proxy later.
    baseUrl: (process.env.GROQ_BASE_URL || '').trim() || grab('BASE_URL'),
    categorizer: [categorizerModel, grab('categorizerFallback')],
    // frameworkModel/summaryFallback alias other consts in Kotlin; resolve what the app resolves.
    summary: [grab('summaryModel'), kt.includes('summaryFallback = "') ? grab('summaryFallback') : categorizerModel],
    coach: [categorizerModel, grab('categorizerFallback')], // GroqAiProvider.suggestImpact uses the categorizer pair
  };
}

// ---------------------------------------------------------------------------
// Prompt templates (eval/prompts/*.txt — PromptSyncTest keeps them equal to AiPrompts)
// ---------------------------------------------------------------------------

function readPrompt(name) {
  return fs
    .readFileSync(path.join(EVAL, 'prompts', name), 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/\n+$/, '');
}
function promptExists(name) {
  return fs.existsSync(path.join(EVAL, 'prompts', name));
}

/** Replace a placeholder only if the template carries it — lets AI-1's restructured templates drop
 *  in (categorizer-system.txt + categorizer-user.txt, new {{ROUTINE_TYPES}}) with no harness edit. */
function fill(template, replacements) {
  let out = template;
  for (const [key, value] of Object.entries(replacements)) {
    out = out.split(`{{${key}}}`).join(value);
  }
  return out;
}

// ---------------------------------------------------------------------------
// APP-MIRROR · context builders
// ---------------------------------------------------------------------------

/** Mirrors FrameworkPrompt.categorizerBlock (data/entry/FrameworkPrompt.kt): GOAL AREAS = names only;
 *  BEHAVIOUR/DEVELOPMENT = name + blurb (AI-1); each category's sub-folder names ride along. */
function categorizerFrameworkBlock(framework, projects) {
  const byCategory = new Map();
  for (const p of projects) {
    const key = p.goalArea.trim().toLowerCase();
    if (!byCategory.has(key)) byCategory.set(key, []);
    byCategory.get(key).push(p.name);
  }
  const line = (pillar, label, withBlurb) => {
    const subs = (byCategory.get(pillar.name.trim().toLowerCase()) || []).join(', ');
    const head = withBlurb && pillar.blurb && pillar.blurb.trim() ? `- ${pillar.name}: ${pillar.blurb}` : `- ${pillar.name}`;
    return `${head}${subs ? ` · ${label}: ${subs}` : ''}`;
  };
  const pillars = framework.pillars || [];
  const kind = (k) => pillars.filter((p) => p.kind === k);
  const out = [];
  out.push('GOAL AREAS (results / projects map here):');
  for (const p of kind('GOAL_AREA')) out.push(line(p, 'projects', false));
  out.push('BEHAVIOURS / COMPETENCIES (tag work that demonstrates these):');
  for (const p of kind('BEHAVIOUR')) out.push(line(p, 'focus areas', true));
  const dev = kind('DEVELOPMENT');
  if (dev.length) {
    out.push('DEVELOPMENT (optional):');
    for (const p of dev) out.push(line(p, 'focus areas', true));
  }
  return out.join('\n').trim();
}

/** Mirrors TextCaps.cap (data/entry/TextCaps.kt): word-boundary truncate to ~300 chars + ellipsis. */
function capDescription(text, max = 300) {
  const t = String(text ?? '').trim();
  if (t.length <= max) return t;
  const hard = t.slice(0, max).replace(/\s+$/, '');
  const lastSpace = hard.lastIndexOf(' ');
  let body = lastSpace >= Math.floor(max / 2) ? hard.slice(0, lastSpace) : hard;
  body = body.replace(/\s+$/, '').replace(/[,;.:\-]+$/, '');
  return body + '…';
}

/** Mirrors EntryProcessor.prepare (data/entry/EntryProcessor.kt): the {{PROJECTS}} lines are the
 *  placement universe — sub-folders under GOAL_AREA categories only — as "- Name [Area] — desc",
 *  the description capped at 300 chars (TextCaps.cap) exactly as the app does before every call. */
function projectLines(framework, projects) {
  const goalNames = (framework.pillars || [])
    .filter((p) => p.kind === 'GOAL_AREA')
    .map((p) => p.name.toLowerCase());
  return projects
    .filter((p) => goalNames.includes(p.goalArea.trim().toLowerCase()))
    .map((p) => `- ${p.name} [${p.goalArea}]${p.description && p.description.trim() ? ` — ${capDescription(p.description)}` : ''}`);
}

/** Mirrors AiPrompts.categorizer + GroqAiProvider.categorize: one system message (the filled
 *  template) + the trimmed transcript as the user message. If a two-part template pair exists
 *  (AI-1's restructure), the volatiles move to the user message automatically. */
function buildCategorizerMessages(templates, c) {
  const ctx = c.context || {};
  const framework = ctx.framework || { pillars: [] };
  const projects = ctx.projects || [];
  const fwBlock = categorizerFrameworkBlock(framework, projects);
  const projBlock = projectLines(framework, projects);
  const replacements = {
    TODAY: ctx.today || '2026-07-11',
    ROLE: (ctx.role || '').trim() || '(not set)',
    APPRAISAL_FRAMEWORK: fwBlock || '(none set)',
    PROJECTS: projBlock.length ? projBlock.join('\n') : '(none yet)',
    PROJECT_ANCHOR: (ctx.anchor || '').trim() || 'none',
    ROUTINE_TYPES: (ctx.routineTypes || []).length ? ctx.routineTypes.map((t) => `- ${t}`).join('\n') : '(none yet)',
    TRANSCRIPT: c.transcript.trim(),
  };
  if (templates.categorizerUser) {
    // AI-1+ two-part shape: static system prompt, volatiles in the user message.
    let system = fill(templates.categorizerSystem, replacements);
    if (c.combineSingle) system = system + '\n\n' + templates.combine;
    return [
      { role: 'system', content: system },
      { role: 'user', content: fill(templates.categorizerUser, replacements) },
    ];
  }
  let system = fill(templates.categorizer, replacements);
  if (c.combineSingle) system = system + '\n\n' + templates.combine;
  return [
    { role: 'system', content: system },
    { role: 'user', content: c.transcript.trim() },
  ];
}

/** Mirrors AiPrompts.impactCoach + GroqAiProvider.suggestImpact (user message = the bullet). */
function buildCoachMessages(templates, c) {
  const system = fill(templates.coach, {
    ROLE: (c.role || '').trim() || '(not set)',
    GOAL_AREA: (c.goalArea || '').trim() || '(unset)',
    PROJECT: (c.project || '').trim() || '(none)',
    PROJECT_DETAIL: (c.projectDetail || '').trim() || '(none given)',
    BULLET: c.bullet.trim(),
  });
  return [
    { role: 'system', content: system },
    { role: 'user', content: c.bullet.trim() },
  ];
}

/** Mirrors AiPrompts.summary + GroqAiProvider.generateSummary. */
function buildSummaryMessages(templates, c) {
  const system = fill(templates.summary, {
    PERIOD: (c.period || '').trim() || 'full-year',
    LENGTH_CAP: (c.lengthCap || '').trim() || 'about one page',
    ROLE: (c.role || '').trim() || '(not set)',
    APPRAISAL_FRAMEWORK: (c.frameworkBlock || '').trim() || '(none set)',
    PINNED: (c.pinned || []).length ? c.pinned.map((p) => `- ${p}`).join('\n') : '(none)',
    ROLLUP: (c.rollup || '').trim() || '(empty)',
  });
  return [
    { role: 'system', content: system },
    { role: 'user', content: 'Generate the summary now.' },
  ];
}

// ---------------------------------------------------------------------------
// Groq transport (APP-MIRROR: temperature 0.2 + JSON mode + primary→fallback on transport OR
// parse failure — GroqAiProvider.completeAndParse. Eval-only addition: 429/5xx retries with
// backoff on the SAME model first, so a rate-limited run measures the model, not the limiter;
// transport failures are reported separately from parse failures.)
// ---------------------------------------------------------------------------

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function callChat(baseUrl, apiKey, model, messages) {
  const body = JSON.stringify({
    model,
    temperature: 0.2,
    response_format: { type: 'json_object' },
    messages,
  });
  const backoffs = [2000, 8000, 20000];
  let lastError = null;
  for (let attempt = 0; attempt <= backoffs.length; attempt++) {
    try {
      const resp = await fetch(baseUrl, {
        method: 'POST',
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body,
        signal: AbortSignal.timeout(120000),
      });
      const raw = await resp.text();
      if (resp.status === 429 || resp.status >= 500) {
        lastError = `HTTP ${resp.status}: ${raw.slice(0, 160)}`;
        if (attempt < backoffs.length) {
          const retryAfter = Number(resp.headers.get('retry-after')) * 1000 || 0;
          await sleep(Math.max(retryAfter, backoffs[attempt]));
          continue;
        }
        return { ok: false, error: lastError };
      }
      if (!resp.ok) return { ok: false, error: `HTTP ${resp.status}: ${raw.slice(0, 160)}` };
      const content = JSON.parse(raw)?.choices?.[0]?.message?.content;
      if (!content || !content.trim()) return { ok: false, error: 'Empty completion' };
      return { ok: true, content };
    } catch (e) {
      lastError = String(e?.message || e);
      if (attempt < backoffs.length) await sleep(backoffs[attempt]);
    }
  }
  return { ok: false, error: lastError || 'exhausted retries' };
}

/** APP-MIRROR: AiJson.extractObject — slice first '{' … last '}' out of the reply. */
function extractObject(text) {
  const t = text.trim().replace(/^﻿/, '').trim();
  const start = t.indexOf('{');
  const end = t.lastIndexOf('}');
  if (start < 0 || end <= start) throw new Error('No JSON object in reply');
  return t.slice(start, end + 1);
}

/** Try primary then fallback, like completeAndParse. Returns per-call bookkeeping for the
 *  JSON-validity metric (primary parse failures counted separately from transport failures). */
async function completeAndParse(baseUrl, apiKey, models, messages) {
  const record = { modelUsed: null, primaryParseFail: false, primaryTransportFail: false, parsed: null, error: null };
  for (let i = 0; i < models.length; i++) {
    const call = await callChat(baseUrl, apiKey, models[i], messages);
    if (!call.ok) {
      if (i === 0) record.primaryTransportFail = true;
      record.error = call.error;
      continue;
    }
    try {
      record.parsed = JSON.parse(extractObject(call.content));
      record.modelUsed = models[i];
      record.error = null;
      return record;
    } catch (e) {
      if (i === 0) record.primaryParseFail = true;
      record.error = `parse: ${String(e?.message || e)}`;
    }
  }
  return record;
}

/** Tiny promise pool — concurrency ≤ 2 per the spec. */
async function pool(items, worker, concurrency = 2) {
  const results = new Array(items.length);
  let next = 0;
  async function lane() {
    while (next < items.length) {
      const i = next++;
      results[i] = await worker(items[i], i);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, lane));
  return results;
}

// ---------------------------------------------------------------------------
// Golden loading
// ---------------------------------------------------------------------------

function loadJsonl(file) {
  const p = path.join(EVAL, 'golden', file);
  if (!fs.existsSync(p)) return [];
  return fs
    .readFileSync(p, 'utf8')
    .split('\n')
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('//'))
    .map((l, i) => {
      try {
        return JSON.parse(l);
      } catch (e) {
        throw new Error(`${file}:${i + 1} is not valid JSON — ${e.message}`);
      }
    });
}

function loadSummaryCases() {
  const dir = path.join(EVAL, 'golden', 'summary');
  if (!fs.existsSync(dir)) return [];
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => ({ ...JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')), _file: f }));
}

// ---------------------------------------------------------------------------
// Scoring — categorizer
// ---------------------------------------------------------------------------

const norm = (s) => String(s ?? '').trim().toLowerCase().replace(/\s+/g, ' ');

/** Scorer-side snapping, mirroring the AI-1 validator's planned rules (the spec measures placement
 *  "after the snapping rules"): case/whitespace-insensitive match to canonical names; a phantom
 *  project (no match, not Outside-project/Inbox) snaps to Inbox. Unknown goal areas stay verbatim. */
function snapProject(value, placementNames) {
  const v = norm(value);
  if (v === 'outside-project') return 'Outside-project';
  if (v === 'inbox') return 'Inbox';
  const hit = placementNames.find((n) => norm(n) === v);
  return hit ?? 'Inbox'; // phantom → Inbox (AI-1 validator rule)
}
function snapGoal(value, pillarNames) {
  const v = norm(value);
  if (v === 'inbox') return 'Inbox';
  const hit = pillarNames.find((n) => norm(n) === v);
  return hit ?? String(value ?? '').trim(); // unknown stays verbatim (Uncategorized guarantee)
}

/** APP-MIRROR: EntryProcessor.statusFor — Inbox when placement says Inbox or confidence < 0.6;
 *  an anchored capture is never parked. Applied post-snap (phantom projects park too). */
function isParked(entry, anchored, placementNames) {
  if (anchored) return false;
  const proj = snapProject(entry.project ?? 'Inbox', placementNames);
  const goalIsInbox = norm(entry.goalCategory ?? 'Inbox') === 'inbox';
  return proj === 'Inbox' || goalIsInbox || Number(entry.confidence ?? 0) < 0.6;
}

function scoreCategorizerCase(c, record) {
  const ctx = c.context || {};
  const framework = ctx.framework || { pillars: [] };
  const projects = ctx.projects || [];
  const placementNames = projectLines(framework, projects).length
    ? projects
        .filter((p) =>
          (framework.pillars || [])
            .filter((x) => x.kind === 'GOAL_AREA')
            .some((x) => norm(x.name) === norm(p.goalArea)),
        )
        .map((p) => p.name)
    : [];
  const pillarNames = (framework.pillars || []).map((p) => p.name);
  const anchorName = (ctx.anchor || '').trim();
  const anchored = Boolean(anchorName);
  // APP-MIRROR: EntryProcessor.applyCategorized — an anchored capture is force-filed to the anchor
  // project (canonical name) and that project's goal area, whatever the model output. Score it that
  // way so a model "Inbox"/development guess on an anchored row isn't under-credited (AI-1 fix).
  const anchorProj = anchored ? (projects.find((p) => norm(p.name) === norm(anchorName)) || null) : null;
  const anchorProjectName = anchorProj ? anchorProj.name : anchorName;
  const anchorGoalArea = anchorProj ? anchorProj.goalArea : null;
  const expect = c.expect || {};
  const entries = Array.isArray(record.parsed?.entries) ? record.parsed.entries : null;

  const checks = {}; // name -> {pass, detail}
  const fail = (name, detail) => (checks[name] = { pass: false, detail });
  const pass = (name) => (checks[name] = { pass: true });

  if (!record.parsed || entries === null) {
    // Unparseable / no reply — every specified expectation fails, under the SAME check keys the
    // live branch uses (so metric denominators stay identical either way).
    const why = record.error || 'no parsed result';
    if (expect.placements) fail('placements', why);
    if (expect.entryCount != null) fail('entryCount', why);
    if (expect.inboxExpected != null) fail('inbox', why);
    if (Array.isArray(expect.routineTypes)) fail(expect.routineTypes.length === 0 ? 'routineNone' : 'routine', why);
    if (Array.isArray(expect.impactBand) && expect.impactBand.length === 2) fail('impactBand', why);
    if (Array.isArray(expect.demonstrates) && expect.demonstrates.length > 0) fail('demonstrates', why);
    if (expect.metric) fail('metric', why);
    if (expect.dateMentioned) fail('dateMentioned', why);
    return { checks, matched: null };
  }

  // entryCount
  if (expect.entryCount != null) {
    entries.length === expect.entryCount
      ? pass('entryCount')
      : fail('entryCount', `expected ${expect.entryCount}, got ${entries.length}`);
  }

  // placements — greedy 1:1 matching of expected (project, goalCategory) pairs onto output entries.
  let firstMatched = entries[0] ?? null;
  if (expect.placements) {
    const remaining = entries.map((e) => ({
      e,
      project: anchored ? anchorProjectName : snapProject(e.project ?? 'Inbox', placementNames),
      goal: anchored ? (anchorGoalArea ?? snapGoal(e.goalCategory ?? 'Inbox', pillarNames)) : snapGoal(e.goalCategory ?? 'Inbox', pillarNames),
      used: false,
    }));
    const misses = [];
    expect.placements.forEach((want, idx) => {
      const hit = remaining.find(
        (r) => !r.used && norm(r.project) === norm(want.project) && norm(r.goal) === norm(want.goalCategory),
      );
      if (hit) {
        hit.used = true;
        if (idx === 0) firstMatched = hit.e;
      } else {
        misses.push(want);
      }
    });
    misses.length === 0
      ? pass('placements')
      : fail(
          'placements',
          `missing ${JSON.stringify(misses)}; got ${JSON.stringify(
            remaining.map((r) => ({ project: r.project, goalCategory: r.goal })),
          )}`,
        );
  }

  // Inbox discipline
  if (expect.inboxExpected === true) {
    entries.length > 0 && entries.every((e) => isParked(e, anchored, placementNames))
      ? pass('inbox')
      : fail('inbox', `expected parked; got ${JSON.stringify(entries.map((e) => ({ project: e.project, confidence: e.confidence })))}`);
  } else if (expect.inboxExpected === false) {
    entries.length > 0 && entries.every((e) => !isParked(e, anchored, placementNames))
      ? pass('inbox')
      : fail('inbox', `expected NOT parked; got ${JSON.stringify(entries.map((e) => ({ project: e.project, goalCategory: e.goalCategory, confidence: e.confidence })))}`);
  }

  // Routine-label reuse (exact string per the spec) / routine false-positive guard
  if (Array.isArray(expect.routineTypes)) {
    if (expect.routineTypes.length === 0) {
      entries.some((e) => e.routine === true)
        ? fail('routineNone', `expected no routine entries; got ${JSON.stringify(entries.filter((e) => e.routine).map((e) => e.routineType))}`)
        : pass('routineNone');
    } else {
      const missing = expect.routineTypes.filter(
        (label) => !entries.some((e) => e.routine === true && e.routineType === label),
      );
      missing.length === 0
        ? pass('routine')
        : fail('routine', `expected exact label(s) ${JSON.stringify(missing)}; got ${JSON.stringify(entries.map((e) => e.routineType ?? null))}`);
    }
  }

  // Impact band — on the entry matched to placements[0] (or the first entry).
  if (Array.isArray(expect.impactBand) && expect.impactBand.length === 2) {
    const impact = Number(firstMatched?.impact ?? NaN);
    impact >= expect.impactBand[0] && impact <= expect.impactBand[1]
      ? pass('impactBand')
      : fail('impactBand', `expected [${expect.impactBand}], got ${impact}`);
  }

  // demonstrates — required tags must be present on some entry (extras are a judgment call and
  // allowed; ghost-tag inflation is AI-1 validator territory). Report-only metric.
  if (Array.isArray(expect.demonstrates) && expect.demonstrates.length > 0) {
    const got = new Set(entries.flatMap((e) => e.demonstrates ?? []).map(norm));
    const missing = expect.demonstrates.filter((w) => !got.has(norm(w)));
    missing.length === 0
      ? pass('demonstrates')
      : fail('demonstrates', `missing required tag(s) ${JSON.stringify(missing)}, got ${JSON.stringify([...got])}`);
  }

  // metric — the stated number must survive into some entry's metric field (substring, case-insens).
  if (expect.metric) {
    entries.some((e) => norm(e.metric ?? '').includes(norm(expect.metric)))
      ? pass('metric')
      : fail('metric', `expected metric containing "${expect.metric}", got ${JSON.stringify(entries.map((e) => e.metric ?? null))}`);
  }

  // dateMentioned — exact ISO match on any entry (report-only metric).
  if (expect.dateMentioned) {
    entries.some((e) => (e.dateMentioned ?? '') === expect.dateMentioned)
      ? pass('dateMentioned')
      : fail('dateMentioned', `expected ${expect.dateMentioned}, got ${JSON.stringify(entries.map((e) => e.dateMentioned ?? null))}`);
  }

  return { checks, matched: firstMatched };
}

// ---------------------------------------------------------------------------
// Scoring — impact coach (rubric)
// ---------------------------------------------------------------------------

const MEASURE_KIND =
  /(percent|%|how many|how much|number|count|hours?|minutes?|days?|weeks?|months?|time\b|turnaround|faster|quicker|sooner|earlier|revenue|cost|money|budget|savings?|saved|₹|\$|€|£|users?|customers?|clients?|accounts?|teams?|people|markets?|tickets?|requests?|defects?|bugs?|incidents?|conversion|drop-?off|churn|retention|rate\b|before .* after|from .* to\b|volume|size|score)/i;

const STOPWORDS = new Set(
  'the a an and or but for with from into onto over under this that these those your you their they our we was were are is been being have has had did does do can could would should will what which when where how why put on it its by of to in at as not no more most much many'.split(
    ' ',
  ),
);
function contentWords(s) {
  return new Set(
    norm(s)
      .replace(/[^a-z0-9\s%₹$€£-]/g, ' ')
      .split(/\s+/)
      .filter((w) => w.length > 3 && !STOPWORDS.has(w)),
  );
}

function scoreCoachCase(c, record) {
  const checks = {};
  const question = String(record.parsed?.question ?? '').trim();
  if (!record.parsed || !question) {
    checks.answered = { pass: false, detail: record.error || 'no/empty question' };
    return { checks, question };
  }
  checks.answered = { pass: true };

  const words = question.split(/\s+/).filter(Boolean);
  checks.short = words.length <= 20 ? { pass: true } : { pass: false, detail: `${words.length} words (cap ~18, hard 20)` };

  checks.isQuestion = /\?\s*$/.test(question) ? { pass: true } : { pass: false, detail: 'does not end with "?"' };

  checks.measureKind = MEASURE_KIND.test(question)
    ? { pass: true }
    : { pass: false, detail: 'names no concrete measure kind' };

  // Zero invented numbers (HARD FAIL): any digit-number in the question must already appear in the
  // inputs AS A NUMBER TOKEN — set membership, not substring ("FY26" must not excuse an invented "2").
  const inputText = [c.bullet, c.projectDetail, c.project, c.goalArea, c.role].join(' ');
  const inputNumbers = new Set(inputText.match(/\d+(?:[.,]\d+)?/g) || []);
  const invented = (question.match(/\d+(?:[.,]\d+)?/g) || []).filter((n) => !inputNumbers.has(n));
  checks.noInventedNumbers = invented.length === 0 ? { pass: true } : { pass: false, detail: `invented number(s): ${invented.join(', ')}` };

  // Grounded in the project detail when one is given: shares ≥1 content word with detail+project+bullet.
  if ((c.projectDetail || '').trim()) {
    const q = contentWords(question);
    const source = contentWords(`${c.projectDetail} ${c.project} ${c.bullet}`);
    const overlap = [...q].some((w) => source.has(w));
    checks.grounded = overlap ? { pass: true } : { pass: false, detail: 'no content-word overlap with the project detail/bullet' };
  }

  return { checks, question };
}

// ---------------------------------------------------------------------------
// Scoring — summary (structural checks)
// ---------------------------------------------------------------------------

function jaccard(a, b) {
  const A = contentWords(a);
  const B = contentWords(b);
  if (!A.size || !B.size) return 0;
  let inter = 0;
  for (const w of A) if (B.has(w)) inter++;
  return inter / (A.size + B.size - inter);
}

function scoreSummaryCase(c, record) {
  const checks = {};
  const advisory = {};
  const body = record.parsed?.summary;
  if (!record.parsed || !body || !Array.isArray(body.goalAreas)) {
    checks.valid = { pass: false, detail: record.error || 'no parsable summary body' };
    return { checks, advisory };
  }
  checks.valid = { pass: true };
  const expect = c.expect || {};

  const achievements = body.goalAreas.flatMap((g) => (g.achievements || []).map((a) => a.bullet || ''));
  const rolledUp = body.goalAreas.flatMap((g) => g.rolledUp || []);
  const docText = JSON.stringify(record.parsed);

  // No duplicated accomplishment across the doc (normalized-exact or near-identical).
  if (expect.noDuplicates !== false) {
    const dupes = [];
    for (let i = 0; i < achievements.length; i++)
      for (let j = i + 1; j < achievements.length; j++) {
        if (norm(achievements[i]) === norm(achievements[j]) || jaccard(achievements[i], achievements[j]) >= 0.9)
          dupes.push([achievements[i], achievements[j]]);
      }
    checks.noDuplicates = dupes.length === 0 ? { pass: true } : { pass: false, detail: `near-identical pair(s): ${JSON.stringify(dupes.slice(0, 3))}` };
  }

  // Arcs merged: at most ONE achievement mentions each arc key.
  if (Array.isArray(expect.arcKeys)) {
    const unmerged = expect.arcKeys.filter((key) => achievements.filter((a) => norm(a).includes(norm(key))).length > 1);
    checks.arcsMerged = unmerged.length === 0 ? { pass: true } : { pass: false, detail: `arc split across bullets: ${unmerged.join('; ')}` };
  }

  // Every named input metric preserved verbatim somewhere in the doc.
  if (Array.isArray(expect.metrics)) {
    const missing = expect.metrics.filter((m) => !docText.includes(m));
    checks.metricsPreserved = missing.length === 0 ? { pass: true } : { pass: false, detail: `missing verbatim: ${missing.join(' · ')}` };
  }

  // Pinned included exactly once (matched by each pinned item's distinctive key).
  if (Array.isArray(expect.pinnedKeys)) {
    const bad = expect.pinnedKeys
      .map((key) => ({ key, hits: achievements.filter((a) => norm(a).includes(norm(key))).length }))
      .filter((r) => r.hits !== 1);
    checks.pinnedOnce = bad.length === 0 ? { pass: true } : { pass: false, detail: JSON.stringify(bad) };
  }

  // Routine rolled up with accurate counts.
  if (Array.isArray(expect.rolledUp)) {
    const missing = expect.rolledUp.filter(
      (want) => !rolledUp.some((r) => norm(r.routineType) === norm(want.routineType) && Number(r.count) === want.count),
    );
    checks.rolledUpCounts = missing.length === 0 ? { pass: true } : { pass: false, detail: `expected ${JSON.stringify(missing)}, got ${JSON.stringify(rolledUp)}` };
  }

  // setAside non-empty when material was necessarily dropped.
  if (expect.setAsideNonEmpty) {
    (record.parsed.setAside || []).length > 0
      ? (checks.setAside = { pass: true })
      : (checks.setAside = { pass: false, detail: 'setAside is empty though input had to be condensed' });
  }

  // ADVISORY until AI-2 ships its serializer/prompt fix: development content belongs in
  // "development", not "goalAreas". Reported, never gated in the AI-0 baseline.
  if (Array.isArray(expect.developmentKeys)) {
    const inDev = expect.developmentKeys.filter((k) => (body.development || []).some((d) => norm(d).includes(norm(k))));
    const leaked = expect.developmentKeys.filter((k) => achievements.some((a) => norm(a).includes(norm(k))));
    advisory.developmentPlacement =
      inDev.length === expect.developmentKeys.length && leaked.length === 0
        ? { pass: true }
        : { pass: false, detail: `in development[]: ${inDev.length}/${expect.developmentKeys.length}; leaked into goalAreas: ${leaked.join('; ') || 'none'}` };
  }

  return { checks, advisory };
}

// ---------------------------------------------------------------------------
// Metric aggregation + report
// ---------------------------------------------------------------------------

function ratio(passed, total) {
  return total === 0 ? null : passed / total;
}
function pct(v) {
  return v == null ? 'n/a' : `${(v * 100).toFixed(1)}%`;
}
function sha(file) {
  const p = path.join(EVAL, 'prompts', file);
  if (!fs.existsSync(p)) return null;
  return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex').slice(0, 12);
}

async function main() {
  const config = loadAiConfig();
  const templates = {
    categorizer: promptExists('categorizer.txt') ? readPrompt('categorizer.txt') : null,
    categorizerSystem: promptExists('categorizer-system.txt') ? readPrompt('categorizer-system.txt') : null,
    categorizerUser: promptExists('categorizer-user.txt') ? readPrompt('categorizer-user.txt') : null,
    combine: promptExists('categorizer-combine.txt') ? readPrompt('categorizer-combine.txt') : '',
    summary: readPrompt('summary.txt'),
    coach: readPrompt('impact-coach.txt'),
  };
  if (!templates.categorizer && !templates.categorizerSystem) {
    throw new Error('eval/prompts: need categorizer.txt or categorizer-system.txt(+categorizer-user.txt)');
  }

  const wants = (suite) => !OPTS.only || OPTS.only === suite;
  const cut = (arr) => (OPTS.limit > 0 ? arr.slice(0, OPTS.limit) : arr);
  const dropUnverified = (arr) => {
    const pending = arr.filter((c) => c._verify);
    if (pending.length)
      console.warn(`⚠️  skipping ${pending.length} case(s) still flagged "_verify": true — hand-verify them first (${pending.map((c) => c.id).join(', ')})`);
    return arr.filter((c) => !c._verify);
  };
  const catCases = wants('categorizer') ? cut(dropUnverified(loadJsonl('categorizer.jsonl'))) : [];
  const coachCases = wants('coach') ? cut(loadJsonl('coach.jsonl')) : [];
  const summaryCases = wants('summary') ? cut(loadSummaryCases()) : [];

  // Golden sanity (also what --dry-run verifies).
  const goldenErrors = [];
  for (const c of catCases) {
    if (!c.id || !c.transcript || !c.context) goldenErrors.push(`categorizer case missing id/transcript/context: ${JSON.stringify(c).slice(0, 80)}`);
    try {
      buildCategorizerMessages(templates, c);
    } catch (e) {
      goldenErrors.push(`categorizer ${c.id}: prompt build failed — ${e.message}`);
    }
  }
  for (const c of coachCases) {
    if (!c.id || !c.bullet) goldenErrors.push(`coach case missing id/bullet: ${JSON.stringify(c).slice(0, 80)}`);
    else buildCoachMessages(templates, c);
  }
  for (const c of summaryCases) {
    if (!c.id || !c.rollup || !c.frameworkBlock) goldenErrors.push(`summary case ${c._file} missing id/rollup/frameworkBlock`);
    else buildSummaryMessages(templates, c);
  }
  // A suite that was asked for but has zero cases = a moved/renamed golden file or an empty set —
  // fail closed, never "PASS on nothing".
  if (wants('categorizer') && catCases.length === 0) goldenErrors.push('categorizer suite has 0 cases (eval/golden/categorizer.jsonl missing/empty?)');
  if (wants('coach') && coachCases.length === 0) goldenErrors.push('coach suite has 0 cases (eval/golden/coach.jsonl missing/empty?)');
  if (wants('summary') && summaryCases.length === 0) goldenErrors.push('summary suite has 0 cases (eval/golden/summary/*.json missing?)');
  for (const c of catCases) {
    if (c.combineSingle && !templates.combine) goldenErrors.push(`categorizer ${c.id}: combineSingle set but eval/prompts/categorizer-combine.txt is missing`);
  }
  if (goldenErrors.length) {
    console.error('GOLDEN SET ERRORS:\n' + goldenErrors.map((e) => `  - ${e}`).join('\n'));
    process.exit(1);
  }

  if (OPTS.dryRun) {
    console.log(`dry-run OK — ${catCases.length} categorizer, ${coachCases.length} coach, ${summaryCases.length} summary cases; ` +
      `models: categorizer=${config.categorizer.join('→')}, summary=${config.summary.join('→')}`);
    const sample = catCases[0] ? buildCategorizerMessages(templates, catCases[0]) : null;
    if (sample) console.log(`sample system prompt (${catCases[0].id}): ${sample[0].content.length} chars; user: ${JSON.stringify(sample[1].content.slice(0, 80))}…`);
    process.exit(0);
  }

  const apiKey = (process.env.GROQ_API_KEY || '').trim();
  if (!apiKey) {
    console.error('GROQ_API_KEY is not set. Run: GROQ_API_KEY=gsk_… node eval/run.mjs   (or --dry-run)');
    process.exit(1);
  }

  console.log(`eval: ${catCases.length} categorizer + ${coachCases.length} coach + ${summaryCases.length} summary cases, concurrency 2`);

  // ---- run categorizer ----
  const catResults = await pool(catCases, async (c) => {
    const record = await completeAndParse(config.baseUrl, apiKey, config.categorizer, buildCategorizerMessages(templates, c));
    const scored = scoreCategorizerCase(c, record);
    console.log(`  [categorizer] ${c.id} — ${Object.entries(scored.checks).map(([k, v]) => `${k}:${v.pass ? '✓' : '✗'}`).join(' ') || '(no expectations)'}${record.modelUsed === config.categorizer[1] ? ' (fallback)' : ''}`);
    return { case: c, record, ...scored };
  });

  // ---- run coach ----
  const coachResults = await pool(coachCases, async (c) => {
    const record = await completeAndParse(config.baseUrl, apiKey, config.coach, buildCoachMessages(templates, c));
    const scored = scoreCoachCase(c, record);
    console.log(`  [coach] ${c.id} — ${Object.entries(scored.checks).map(([k, v]) => `${k}:${v.pass ? '✓' : '✗'}`).join(' ')}`);
    return { case: c, record, ...scored };
  });

  // ---- run summary ----
  const summaryResults = await pool(summaryCases, async (c) => {
    const record = await completeAndParse(config.baseUrl, apiKey, config.summary, buildSummaryMessages(templates, c));
    const scored = scoreSummaryCase(c, record);
    console.log(`  [summary] ${c.id} — ${Object.entries(scored.checks).map(([k, v]) => `${k}:${v.pass ? '✓' : '✗'}`).join(' ')}`);
    return { case: c, record, ...scored };
  });

  // ---- aggregate ----
  const all = [...catResults, ...coachResults, ...summaryResults];
  const calls = all.map((r) => r.record);
  const parsedAll = calls.every((r) => r.parsed != null);
  const primaryParseFails = calls.filter((r) => r.primaryParseFail).length;
  const primaryParseFailRate = ratio(primaryParseFails, calls.length) ?? 0;

  const by = (results, check) => {
    const relevant = results.filter((r) => r.checks[check] != null);
    return { pass: relevant.filter((r) => r.checks[check].pass).length, total: relevant.length };
  };

  const placement = by(catResults, 'placements');
  const inboxRecallCases = catResults.filter((r) => r.case.expect?.inboxExpected === true && r.checks.inbox != null);
  const inboxPrecisionCases = catResults.filter((r) => r.case.expect?.inboxExpected === false && r.checks.inbox != null);
  const routine = by(catResults, 'routine');
  const impactBand = by(catResults, 'impactBand');
  const entryCount = by(catResults, 'entryCount');
  const demonstrates = by(catResults, 'demonstrates');
  const metricKept = by(catResults, 'metric');
  const dateMentioned = by(catResults, 'dateMentioned');
  const routineNone = by(catResults, 'routineNone');

  const coachEvaluated = coachResults.filter((r) => Object.keys(r.checks).length > 0);
  const coachPassed = coachEvaluated.filter((r) => Object.values(r.checks).every((c) => c.pass));
  const inventedNumbers = coachResults.filter((r) => r.checks.noInventedNumbers && !r.checks.noInventedNumbers.pass);

  const summaryChecksAll = summaryResults.flatMap((r) => Object.values(r.checks));
  const summaryPass = summaryChecksAll.filter((c) => c.pass).length;

  const metrics = {
    placementAccuracy: ratio(placement.pass, placement.total),
    inboxRecall: ratio(inboxRecallCases.filter((r) => r.checks.inbox.pass).length, inboxRecallCases.length),
    inboxPrecision: ratio(inboxPrecisionCases.filter((r) => r.checks.inbox.pass).length, inboxPrecisionCases.length),
    jsonValidity: calls.length ? (parsedAll && primaryParseFailRate < THRESHOLDS.primaryParseFailMax ? 1 : 0) : null,
    primaryParseFailRate,
    routineReuse: ratio(routine.pass, routine.total),
    impactBand: ratio(impactBand.pass, impactBand.total),
    coachPass: ratio(coachPassed.length, coachEvaluated.length),
    coachNoInventedNumbers: coachEvaluated.length ? (inventedNumbers.length === 0 ? 1 : 0) : null,
    summaryChecks: ratio(summaryPass, summaryChecksAll.length),
    // report-only metrics (not gated)
    entryCountAccuracy: ratio(entryCount.pass, entryCount.total),
    demonstratesAccuracy: ratio(demonstrates.pass, demonstrates.total),
    metricPreserved: ratio(metricKept.pass, metricKept.total),
    dateMentionedAccuracy: ratio(dateMentioned.pass, dateMentioned.total),
    routineFalsePositiveFree: ratio(routineNone.pass, routineNone.total),
  };

  const gates = [];
  const gate = (name, value, threshold, extra = '') => {
    if (value == null) return; // suite not run / no cases — not gated
    gates.push({ name, value, threshold, pass: value >= threshold, extra });
  };
  gate('placementAccuracy', metrics.placementAccuracy, THRESHOLDS.placementAccuracy);
  gate('inboxRecall', metrics.inboxRecall, THRESHOLDS.inboxRecall);
  gate('inboxPrecision', metrics.inboxPrecision, THRESHOLDS.inboxPrecision);
  gate('jsonValidity', metrics.jsonValidity, THRESHOLDS.jsonValidity, `primary parse-fail rate ${pct(primaryParseFailRate)} (max ${pct(THRESHOLDS.primaryParseFailMax)})`);
  gate('routineReuse', metrics.routineReuse, THRESHOLDS.routineReuse);
  gate('impactBand', metrics.impactBand, THRESHOLDS.impactBand);
  gate('coachPass', metrics.coachPass, THRESHOLDS.coachPass);
  gate('coachNoInventedNumbers', metrics.coachNoInventedNumbers, 1); // hard fail
  gate('summaryChecks', metrics.summaryChecks, THRESHOLDS.summaryChecks);

  // Baseline comparison (AI-1+: every metric must be ≥ the committed baseline).
  let baselineRows = [];
  if (OPTS.baseline && fs.existsSync(OPTS.baseline)) {
    const base = JSON.parse(fs.readFileSync(OPTS.baseline, 'utf8'));
    for (const [name, value] of Object.entries(metrics)) {
      const prev = base.metrics?.[name];
      if (prev == null || value == null) continue;
      const lowerIsBetter = name === 'primaryParseFailRate';
      const pass = lowerIsBetter ? value <= prev + 1e-9 : value >= prev - 1e-9;
      baselineRows.push({ name, prev, value, pass });
    }
  }

  const gatesPassed = gates.every((g) => g.pass) && baselineRows.every((r) => r.pass);

  // ---- report ----
  const failures = [];
  for (const r of [...catResults, ...coachResults, ...summaryResults]) {
    const failed = Object.entries(r.checks).filter(([, v]) => !v.pass);
    const adv = Object.entries(r.advisory || {}).filter(([, v]) => !v.pass);
    if (failed.length || adv.length)
      failures.push({
        id: r.case.id,
        failed: Object.fromEntries(failed.map(([k, v]) => [k, v.detail || ''])),
        advisory: Object.fromEntries(adv.map(([k, v]) => [k, v.detail || ''])),
        model: r.record.modelUsed,
        error: r.record.error,
      });
  }

  const now = new Date().toISOString();
  const md = [];
  md.push(`# BragBuddy AI eval report`);
  md.push('');
  md.push(`- Generated: ${now}`);
  md.push(`- Models: categorizer \`${config.categorizer.join('\` → \`')}\` · summary \`${config.summary.join('\` → \`')}\` (from \`AiConfig.kt\`)`);
  md.push(`- Prompts: categorizer \`${sha('categorizer.txt') || sha('categorizer-system.txt')}\` · summary \`${sha('summary.txt')}\` · coach \`${sha('impact-coach.txt')}\` (sha256/12)`);
  md.push(`- Cases: ${catCases.length} categorizer · ${coachCases.length} coach · ${summaryCases.length} summary${OPTS.limit ? ` (LIMITED to ${OPTS.limit}/set — not a shippable run)` : ''}`);
  md.push('');
  md.push(`## Gates — ${gatesPassed ? '✅ PASS' : '❌ FAIL'}`);
  md.push('');
  md.push('| Metric | Threshold | Actual | Pass |');
  md.push('|---|---|---|---|');
  for (const g of gates) md.push(`| ${g.name}${g.extra ? ` (${g.extra})` : ''} | ≥ ${pct(g.threshold)} | ${pct(g.value)} | ${g.pass ? '✅' : '❌'} |`);
  md.push('');
  md.push('## Reported (not gated)');
  md.push('');
  md.push('| Metric | Actual |');
  md.push('|---|---|');
  for (const name of ['entryCountAccuracy', 'demonstratesAccuracy', 'metricPreserved', 'dateMentionedAccuracy', 'routineFalsePositiveFree', 'primaryParseFailRate'])
    md.push(`| ${name} | ${pct(metrics[name])} |`);
  md.push('');
  if (baselineRows.length) {
    md.push('## Baseline comparison');
    md.push('');
    md.push('| Metric | Baseline | Now | ≥ baseline |');
    md.push('|---|---|---|---|');
    for (const r of baselineRows) md.push(`| ${r.name} | ${pct(r.prev)} | ${pct(r.value)} | ${r.pass ? '✅' : '❌'} |`);
    md.push('');
  }
  if (failures.length) {
    md.push('## Failures');
    md.push('');
    for (const f of failures) {
      md.push(`### ${f.id}${f.model ? ` (\`${f.model}\`)` : ''}`);
      for (const [k, d] of Object.entries(f.failed)) md.push(`- ❌ **${k}** — ${d}`);
      for (const [k, d] of Object.entries(f.advisory)) md.push(`- ⚠️ *advisory* **${k}** — ${d}`);
      if (f.error) md.push(`- transport/parse: ${f.error}`);
      md.push('');
    }
  } else {
    md.push('No failing cases. 🎉');
    md.push('');
  }

  fs.writeFileSync(OPTS.out, md.join('\n'), 'utf8');
  fs.writeFileSync(
    OPTS.jsonOut,
    JSON.stringify(
      { generatedAt: now, models: config, thresholds: THRESHOLDS, counts: { categorizer: catCases.length, coach: coachCases.length, summary: summaryCases.length }, limited: OPTS.limit > 0, metrics, gates, gatesPassed, failures },
      null,
      2,
    ),
    'utf8',
  );
  console.log(`\nreport → ${OPTS.out}\njson   → ${OPTS.jsonOut}\ngates: ${gatesPassed ? 'PASS' : 'FAIL'}`);
  process.exit(gatesPassed || OPTS.noGate ? 0 : 1);
}

main().catch((e) => {
  console.error(e.stack || String(e));
  process.exit(1);
});
