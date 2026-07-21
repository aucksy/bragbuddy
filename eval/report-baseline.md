# BragBuddy AI eval report

- Generated: 2026-07-21T05:05:50.396Z
- Models: categorizer `meta-llama/llama-3.3-70b-instruct` → `meta-llama/llama-3.1-8b-instruct` · summary `openai/gpt-oss-120b` → `meta-llama/llama-3.3-70b-instruct` (from `AiConfig.kt`)
- Transport: OpenRouter, provider pinned to `groq` (same Groq inference the app uses; eval-only reroute)
- Consensus: each case sampled 3× — a check passes on the majority (de-noises the small golden set; thresholds unchanged)
- Prompts: categorizer `4b436dbfb3e1` · summary `5477f35a7a4a` · coach `6162b0243f60` (sha256/12)
- Cases: 54 categorizer · 12 coach · 7 summary

## Gates — ❌ FAIL

| Metric | Threshold | Actual | Pass |
|---|---|---|---|
| placementAccuracy | ≥ 85.0% | 97.1% | ✅ |
| inboxRecall | ≥ 80.0% | 80.0% | ✅ |
| inboxPrecision | ≥ 90.0% | 88.5% | ❌ |
| jsonValidity (primary parse-fail rate 0.0% (max 10.0%)) | ≥ 100.0% | 100.0% | ✅ |
| routineReuse | ≥ 100.0% | 100.0% | ✅ |
| impactBand | ≥ 80.0% | 100.0% | ✅ |
| coachPass | ≥ 90.0% | 91.7% | ✅ |
| coachNoInventedNumbers | ≥ 100.0% | 100.0% | ✅ |
| deliverableAccuracy | ≥ 80.0% | 83.3% | ✅ |
| summaryChecks | ≥ 100.0% | 96.7% | ❌ |

## Reported (not gated)

| Metric | Actual |
|---|---|
| entryCountAccuracy | 97.5% |
| demonstratesAccuracy | 100.0% |
| metricPreserved | 60.0% |
| dateMentionedAccuracy | 100.0% |
| routineFalsePositiveFree | 100.0% |
| primaryParseFailRate | 0.0% |

## Failures

### detailed-length (`openai/gpt-oss-120b`)
- ❌ **lengthHonoured** — too few achievements: Delivery 3<5; Quality & Craft 1<3 (passed 0/3)

### coach-eng-flaky (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet (passed 0/3)

### eng-inbox-near-duplicate-projects (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected parked; got [{"project":"Onboarding v2","confidence":0.85}] (passed 0/3)

### po-metric-30-percent (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Performance Goals","confidence":0.5}] (passed 0/3)

### real-002 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **placements** — missing [{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"}]; got [{"project":"Inbox","goalCategory":"Performance Goals"},{"project":"Inbox","goalCategory":"Performance Goals"},{"project":"Inbox","goalCategory":"Performance Goals"},{"project":"Inbox","goalCategory":"Performance Goals"}] (passed 0/3)
- ❌ **inbox** — expected NOT parked; got [{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration","goalCategory":"Performance Goals","confidence":0.9}] (passed 0/3)

### real-003 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **entryCount** — expected 1, got 2 (passed 0/3)
- ❌ **metric** — expected metric containing "20 days", got [null,null] (passed 0/3)

### real-005 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **metric** — expected metric containing "76 stories", got [null,null,null,null,null,null,null] (passed 0/3)

### real-008 (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Inbox","goalCategory":"Inbox","confidence":0.7},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Platform Health","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Inbox","goalCategory":"Inbox","confidence":0.7},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9}] (passed 0/3)

### del-anchored-project-no-ai-guess (`meta-llama/llama-3.3-70b-instruct`)
- ❌ **deliverables** — [{"expected":null,"got":"Market rollout"}] (passed 0/3)
