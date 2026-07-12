# BragBuddy AI eval report

- Generated: 2026-07-12T05:24:57.852Z
- Models: categorizer `meta-llama/llama-3.3-70b-instruct` → `meta-llama/llama-3.1-8b-instruct` · summary `openai/gpt-oss-120b` → `meta-llama/llama-3.3-70b-instruct` (from `AiConfig.kt`)
- Transport: OpenRouter, provider pinned to `groq` (same Groq inference the app uses; eval-only reroute)
- Prompts: categorizer `9ca2ae44cc2b` · summary `dbaa716aae8e` · coach `59ae3e19bc59` (sha256/12)
- Cases: 47 categorizer · 12 coach · 4 summary

## Gates — ❌ FAIL

| Metric | Threshold | Actual | Pass |
|---|---|---|---|
| placementAccuracy | ≥ 85.0% | 100.0% | ✅ |
| inboxRecall | ≥ 80.0% | 80.0% | ✅ |
| inboxPrecision | ≥ 90.0% | 92.3% | ✅ |
| jsonValidity (primary parse-fail rate 0.0% (max 10.0%)) | ≥ 100.0% | 100.0% | ✅ |
| routineReuse | ≥ 100.0% | 100.0% | ✅ |
| impactBand | ≥ 80.0% | 92.3% | ✅ |
| coachPass | ≥ 90.0% | 83.3% | ❌ |
| coachNoInventedNumbers | ≥ 100.0% | 100.0% | ✅ |
| summaryChecks | ≥ 100.0% | 88.2% | ❌ |

## Reported (not gated)

| Metric | Actual |
|---|---|
| entryCountAccuracy | 91.7% |
| demonstratesAccuracy | 28.6% |
| metricPreserved | 60.0% |
| dateMentionedAccuracy | 100.0% |
| routineFalsePositiveFree | 100.0% |
| primaryParseFailRate | 0.0% |

## Failures

### eng-inbox-near-duplicate-projects (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected parked; got [{"project":"Onboarding v2","confidence":0.85}]

### po-metric-30-percent (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Inbox","confidence":0.5}]

### po-extra-mentoring (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["ownership","collaboration"]

### eng-development-cert (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **impactBand** — expected [0.2,0.6], got 0.8

### real-003 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 2
- ❌ **metric** — expected metric containing "20 days", got [null,null]

### real-005 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **metric** — expected metric containing "76 stories", got [null,null,null,null,null,null]

### real-006 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Set the Agenda","Bring Others With You","Do It the Right Way"], got ["leadership & behaviours"]

### real-007 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"]

### real-008 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7},{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.7}]
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"]

### real-009 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda: define what winning looks like","bring others with you: build a diverse and inclusive team","do it the right way: communicate frequently, candidly and clearly","bring others with you: seek and provide coaching and feedback","do it the right way: demonstrate courageous and authentic leadership","bring others with you: make collaboration essential","do it the right way: live the blue box values"]

### real-010 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 4

### real-011 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 4

### coach-po-raven (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet

### coach-eng-selector (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet

### dense-year (`openai/gpt-oss-120b`)
- ❌ **pinnedOnce** — [{"key":"PCI-DSS","hits":0}]

### routine-heavy (`openai/gpt-oss-120b`)
- ❌ **rolledUpCounts** — expected [{"routineType":"access requests","count":62},{"routineType":"servicing requests","count":38}], got [{"bullet":"Processed 62 access requests with average turnaround under 1 day","routineType":"access request","count":62},{"bullet":"Handled 38 servicing requests","routineType":"servicing request","count":38}]
