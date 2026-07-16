# BragBuddy AI eval report

- Generated: 2026-07-16T08:30:57.956Z
- Models: categorizer `meta-llama/llama-3.3-70b-instruct` → `meta-llama/llama-3.1-8b-instruct` · summary `openai/gpt-oss-120b` → `meta-llama/llama-3.3-70b-instruct` (from `AiConfig.kt`)
- Transport: OpenRouter, provider pinned to `groq` (same Groq inference the app uses; eval-only reroute)
- Consensus: each case sampled 3× — a check passes on the majority (de-noises the small golden set; thresholds unchanged)
- Prompts: categorizer `9ca2ae44cc2b` · summary `ce75a6791558` · coach `6162b0243f60` (sha256/12)
- Cases: 47 categorizer · 12 coach · 6 summary

## Gates — ❌ FAIL

| Metric | Threshold | Actual | Pass |
|---|---|---|---|
| placementAccuracy | ≥ 85.0% | 96.3% | ✅ |
| inboxRecall | ≥ 80.0% | 80.0% | ✅ |
| inboxPrecision | ≥ 90.0% | 88.5% | ❌ |
| jsonValidity (primary parse-fail rate 0.0% (max 10.0%)) | ≥ 100.0% | 100.0% | ✅ |
| routineReuse | ≥ 100.0% | 100.0% | ✅ |
| impactBand | ≥ 80.0% | 92.3% | ✅ |
| coachPass | ≥ 90.0% | 91.7% | ✅ |
| coachNoInventedNumbers | ≥ 100.0% | 100.0% | ✅ |
| summaryChecks | ≥ 100.0% | 96.3% | ❌ |

## Reported (not gated)

| Metric | Actual |
|---|---|
| entryCountAccuracy | 91.7% |
| demonstratesAccuracy | 14.3% |
| metricPreserved | 60.0% |
| dateMentionedAccuracy | 100.0% |
| routineFalsePositiveFree | 100.0% |
| primaryParseFailRate | 0.0% |

## Failures

### detailed-length (`openai/gpt-oss-120b`)
- ❌ **lengthHonoured** — too few achievements: Delivery 4<5 (passed 0/3)

### coach-eng-flaky (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet (passed 1/3)

### eng-inbox-near-duplicate-projects (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected parked; got [{"project":"Onboarding v2","confidence":0.85}] (passed 0/3)

### po-metric-30-percent (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Inbox","confidence":0.5}] (passed 0/3)

### po-extra-mentoring (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["ownership","collaboration"] (passed 0/3)

### eng-development-cert (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **impactBand** — expected [0.2,0.6], got 0.8 (passed 0/3)

### real-002 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **placements** — missing [{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"}]; got [{"project":"Inbox","goalCategory":"Performance Goals"},{"project":"Inbox","goalCategory":"Performance Goals"},{"project":"Inbox","goalCategory":"Performance Goals"}] (passed 1/3)
- ❌ **inbox** — expected NOT parked; got [{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9}] (passed 1/3)

### real-003 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 2 (passed 1/3)
- ❌ **metric** — expected metric containing "20 days", got [null] (passed 0/3)

### real-005 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **metric** — expected metric containing "76 stories", got [null,null,null,null,null,null] (passed 0/3)

### real-006 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"] (passed 0/3)

### real-007 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","do it the right way","bring others with you"] (passed 0/3)

### real-008 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7}] (passed 0/3)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"] (passed 0/3)

### real-009 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"] (passed 0/3)

### real-010 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 4 (passed 0/3)

### real-011 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 4 (passed 0/3)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","communicate frequently, candidly and clearly","lead with an external perspective","make decisions quickly and effectively","build a diverse and inclusive team","seek and provide coaching and feedback","demonstrate courageous and authentic leadership","live the blue box values"] (passed 0/3)
