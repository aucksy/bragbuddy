#!/usr/bin/env node
/**
 * Bootstrap golden categorizer cases from a real BragBuddy backup (Phase AI-0).
 *
 * Reads a `bragbuddy-backup.json` (Settings → Backup → "Export to device"), and emits SKELETON
 * cases to `eval/golden/from-backup.pending.jsonl` — one per captured transcript, with the entry's
 * CURRENTLY FILED placement pre-filled as the tentative expectation. The creator's own record plus
 * their Recategorize corrections are the seed dataset (docs/IMPLEMENTATION-PLAN.md · AI-0 §7).
 *
 * ⚠️ The output is NOT a golden set yet. Every line must be HAND-VERIFIED (is the filed placement
 * actually right? is the pre-filled entryCount/inbox flag right?), then moved into
 * `eval/golden/categorizer.jsonl`. Each pending line carries `"_verify": true` so run.mjs can
 * never mistakenly score it.
 * ⚠️ The backup contains the real record — transcripts may be sensitive. Review/redact before
 * committing anything derived from it; never commit the backup file itself (both are gitignored).
 *
 * Usage: node eval/tools/from-backup.mjs <path-to-bragbuddy-backup.json> [--out <file>]
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const EVAL = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const input = process.argv[2];
if (!input || !fs.existsSync(input)) {
  console.error('Usage: node eval/tools/from-backup.mjs <path-to-bragbuddy-backup.json> [--out <file>]');
  process.exit(1);
}
const outIdx = process.argv.indexOf('--out');
const outFile = outIdx > 0 ? process.argv[outIdx + 1] : path.join(EVAL, 'golden', 'from-backup.pending.jsonl');

const backup = JSON.parse(fs.readFileSync(input, 'utf8'));
if (!backup.version || !Array.isArray(backup.entries) || !Array.isArray(backup.pillars)) {
  console.error('Not a recognisable BragBuddy backup (missing version/entries/pillars).');
  process.exit(1);
}

// ---- context, shared by every case: the user's real setup (mirrors BackupCodec fields) ----
const framework = {
  pillars: backup.pillars.map((p) => ({ name: p.name, kind: p.kind || 'GOAL_AREA', blurb: p.blurb || '' })),
};
const projects = (backup.projects || [])
  .filter((p) => !p.archived)
  .map((p) => ({ name: p.name, goalArea: p.goalArea, description: p.description || '' }));
const role = backup.settings?.jobRole || '';
const routineTypes = [
  ...new Set(backup.entries.map((e) => (e.routineType || '').trim()).filter(Boolean)),
];

// ---- group split siblings: rows sharing one rawTranscript came from ONE capture. The key also
// includes createdAt (split siblings inherit the captured row's createdAt via entry.copy), so two
// SEPARATE captures of identical text (routine chores repeat) never merge into a bogus entryCount.
const SKIP_STATUS = new Set(['RAW', 'PENDING_AUDIO']);
const groups = new Map();
for (const e of backup.entries) {
  const t = (e.rawTranscript || '').trim();
  if (!t || SKIP_STATUS.has(e.status)) continue;
  const key = [e.createdAt || 0, e.anchorProject || '', t].join('|');
  if (!groups.has(key)) groups.set(key, []);
  groups.get(key).push(e);
}

// LOCAL date, not UTC — an IST capture at 00:30 must not get yesterday as `today`.
const iso = (millis) => {
  if (!millis) return null;
  const d = new Date(millis);
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return d.getFullYear() + '-' + mm + '-' + dd;
};

let n = 0;
const lines = [];
for (const [, rows] of groups) {
  n++;
  const first = rows[0];
  // FAILED rows carry NO trustworthy label (the AI never answered — that says nothing about the
  // transcript's ambiguity), so they contribute no pre-filled signal; the transcript itself is
  // still worth keeping for fully-manual labelling.
  const labelled = rows.filter((e) => e.status !== 'FAILED');
  const allInbox = labelled.length > 0 && labelled.every((e) => e.status === 'INBOX');
  const placements = labelled
    .filter((e) => e.status === 'PROCESSED' && e.project && e.goalCategory)
    .map((e) => ({ project: e.project, goalCategory: e.goalCategory }));
  const routineLabels = [...new Set(labelled.filter((e) => e.routine && e.routineType).map((e) => e.routineType))];
  const demonstrates = [...new Set(labelled.flatMap((e) => e.demonstrates || []))];

  const expect = {};
  if (placements.length) expect.placements = placements;
  if (labelled.length) expect.entryCount = labelled.length;
  // inboxExpected only when the signal is UNANIMOUS — run.mjs scores it against EVERY output
  // entry, so a mixed parked+placed capture must leave it unset (the hand-verifier decides).
  if (allInbox) expect.inboxExpected = true;
  else if (placements.length > 0 && placements.length === labelled.length) expect.inboxExpected = false;
  if (routineLabels.length) expect.routineTypes = routineLabels;
  if (demonstrates.length) expect.demonstrates = demonstrates;
  const metric = labelled.map((e) => e.metric).find(Boolean);
  if (metric) expect.metric = metric;

  const statuses = rows.map((e) => e.status).join('+');
  lines.push(
    JSON.stringify({
      _verify: true, // remove after hand-verification, then move the line into categorizer.jsonl
      id: 'real-' + String(n).padStart(3, '0'),
      transcript: first.rawTranscript.trim(),
      context: {
        role,
        today: iso(first.createdAt) || '2026-07-11', // capture day (local) so relative dates stay truthful
        anchor: first.anchorProject || null,
        routineTypes,
        framework,
        projects,
      },
      expect,
      note: 'from backup · status=' + statuses + ' · pre-filled labels are TENTATIVE — verify by hand' + (labelled.length === 0 ? ' (FAILED capture: label entirely by hand)' : ''),
    }),
  );
}

fs.writeFileSync(outFile, lines.join('\n') + (lines.length ? '\n' : ''), 'utf8');
console.log(lines.length + ' skeleton case(s) → ' + outFile);
console.log('Next: hand-verify each line (fix wrong placements, trim sensitive text, drop weak cases),');
console.log('remove the "_verify" flag, and append the good ones to eval/golden/categorizer.jsonl.');
