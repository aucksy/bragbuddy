# BragBuddy AI eval report

- Generated: 2026-07-11T12:19:40.529Z
- Models: categorizer `llama-3.3-70b-versatile` → `llama-3.1-8b-instant` · summary `openai/gpt-oss-120b` → `llama-3.3-70b-versatile` (from `AiConfig.kt`)
- Prompts: categorizer `16a4f8cc2817` · summary `dbaa716aae8e` · coach `59ae3e19bc59` (sha256/12)
- Cases: 47 categorizer · 12 coach · 4 summary

## Gates — ❌ FAIL

| Metric | Threshold | Actual | Pass |
|---|---|---|---|
| placementAccuracy | ≥ 85.0% | 88.9% | ✅ |
| inboxRecall | ≥ 80.0% | 80.0% | ✅ |
| inboxPrecision | ≥ 90.0% | 92.3% | ✅ |
| jsonValidity (primary parse-fail rate 0.0% (max 10.0%)) | ≥ 100.0% | 100.0% | ✅ |
| routineReuse | ≥ 100.0% | 57.1% | ❌ |
| impactBand | ≥ 80.0% | 92.3% | ✅ |
| coachPass | ≥ 90.0% | 75.0% | ❌ |
| coachNoInventedNumbers | ≥ 100.0% | 100.0% | ✅ |
| summaryChecks | ≥ 100.0% | 88.2% | ❌ |

## Reported (not gated)

| Metric | Actual |
|---|---|
| entryCountAccuracy | 88.9% |
| demonstratesAccuracy | 71.4% |
| metricPreserved | 20.0% |
| dateMentionedAccuracy | 100.0% |
| routineFalsePositiveFree | 100.0% |
| primaryParseFailRate | 0.0% |

## Failures

### po-split-two (`llama-3.3-70b-versatile`)
- ❌ **placements** — missing [{"project":"Raven Migration","goalCategory":"Performance Goals"}]; got [{"project":"SharePoint Request System","goalCategory":"Performance Goals"},{"project":"Outside-project","goalCategory":"Performance Goals"}]
- ❌ **routine** — expected exact label(s) ["access requests"]; got ["servicing requests",null]

### po-routine-reuse (`llama-3.3-70b-versatile`)
- ❌ **routine** — expected exact label(s) ["access requests"]; got ["SharePoint access requests"]

### po-routine-variant-phrasing (`llama-3.3-70b-versatile`)
- ❌ **routine** — expected exact label(s) ["access requests"]; got ["access approvals"]

### eng-routine-tickets (`llama-3.3-70b-versatile`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Inbox","goalCategory":"Quality & Craft","confidence":0.8}]

### eng-inbox-near-duplicate-projects (`llama-3.3-70b-versatile`)
- ❌ **inbox** — expected parked; got [{"project":"Onboarding v2","confidence":0.8}]

### eng-anchored-conflicting-mention (`llama-3.3-70b-versatile`)
- ❌ **entryCount** — expected 1, got 2

### po-metric-30-percent (`llama-3.3-70b-versatile`)
- ❌ **metric** — expected metric containing "30", got [null]

### eng-collab-docs (`llama-3.1-8b-instant`)
- ❌ **demonstrates** — missing required tag(s) ["Collaboration"], got ["ownership"]

### eng-development-cert (`llama-3.3-70b-versatile`)
- ❌ **impactBand** — expected [0.2,0.6], got 0.8

### po-combine-number-followup (`llama-3.3-70b-versatile`)
- ❌ **metric** — expected metric containing "30", got [null]

### real-003 (`llama-3.3-70b-versatile`)
- ❌ **entryCount** — expected 1, got 3
- ❌ **metric** — expected metric containing "20 days", got [null,null,null]

### real-005 (`llama-3.3-70b-versatile`)
- ❌ **metric** — expected metric containing "76 stories", got [null,null,null,null,null,null,null]

### real-007 (`llama-3.1-8b-instant`)
- ❌ **demonstrates** — missing required tag(s) ["Leadership & Behaviours"], got ["set the agenda","bring others with you","do it the right way"]

### real-008 (`llama-3.3-70b-versatile`)
- ❌ **inbox** — expected NOT parked; got [{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.9},{"project":"Outside-project","goalCategory":"Performance Goals","confidence":0.8},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.8},{"project":"Outside-project","goalCategory":"Inbox","confidence":0.7},{"project":"Inbox","goalCategory":"Inbox","confidence":0.6},{"project":"Platform Health","goalCategory":"Performance Goals","confidence":0.7},{"project":"Outside-project","goalCategory":"Performance Goals","confidence":0.8},{"project":"Outside-project","goalCategory":"Performance Goals","confidence":0.8},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.8},{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals","confidence":0.8}]

### real-009 (`llama-3.3-70b-versatile`)
- ❌ **placements** — missing [{"project":"Intake Hub for CommXHub","goalCategory":"Performance Goals"},{"project":"Intake Hub for CommXHub","goalCategory":"Performance Goals"}]; got [{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"}]

### real-010 (`llama-3.3-70b-versatile`)
- ❌ **entryCount** — expected 1, got 4

### real-011 (`llama-3.3-70b-versatile`)
- ❌ **entryCount** — expected 1, got 4
- ❌ **placements** — missing [{"project":"Raven Migration CCP Portals","goalCategory":"Performance Goals"}]; got [{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"},{"project":"Inbox","goalCategory":"Learning & Growth"}]

### coach-po-raven (`llama-3.3-70b-versatile`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet

### coach-eng-selector (`llama-3.3-70b-versatile`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet

### coach-eng-retro (`llama-3.3-70b-versatile`)
- ❌ **grounded** — no content-word overlap with the project detail/bullet

### dense-year (`openai/gpt-oss-120b`)
- ❌ **pinnedOnce** — [{"key":"PCI-DSS","hits":0}]
- ❌ **rolledUpCounts** — expected [{"routineType":"code reviews","count":84},{"routineType":"support tickets","count":57}], got [{"bullet":"Performed code reviews","routineType":"code review","count":84},{"bullet":"Handled support tickets","routineType":"support ticket","count":57}]

### development-heavy (`llama-3.3-70b-versatile`)
- ⚠️ *advisory* **developmentPlacement** — in development[]: 2/2; leaked into goalAreas: Kubernetes; SQL course
