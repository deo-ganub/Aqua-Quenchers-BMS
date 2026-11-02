# Offline classifier workflow

No verified station training data or trained release model is included. Synthetic golden fixtures exercise calculations/parity only; no station quality, tablet latency or memory gate is claimed.

Each UTF8 JSONL record requires: `id`, `remarks`, one fixed `category`, `reviewed` boolean, `reviewer_id` (pseudonymous authorized reviewer), `group_id`, `synthetic` boolean. Optional `receipt_id`, `template_family`, `language` support grouping. Review and redact unnecessary PII before ingestion. Target at least50 distinct Owner-reviewed examples per category, with English, Filipino/mixed, ambiguity and unseen vocabulary. That target is not a collected count. Template `labeling-template.jsonl` is deliberately unreviewed and must fail ingestion until reviewed/completed.

From repository root with requirements installed:

```
PYTHONPATH=classifier python3 -m aqua_classifier prepare --dataset /restricted/reviewed.jsonl --output /restricted/experiment-v1
PYTHONPATH=classifier python3 -m aqua_classifier train --dataset /restricted/reviewed.jsonl --output /restricted/experiment-v1 --model-version EXP-LR-v1
PYTHONPATH=classifier python3 -m aqua_classifier evaluate --dataset /restricted/reviewed.jsonl --output /restricted/experiment-v1
```

Use `--synthetic` only for synthetic experiments, never for release. Version directories are exclusive-create; interrupted/frozen evaluation must be reviewed before retry and must not be used to tune on test labels. Split seed42/grouped60/20/20 targets preserve detected exact/near duplicates, declared group, receipt and template families. Report actual class support/counts. TFIDF vocabulary/IDF fit training only; primary learning-rate/lambda and NB smoothing tune validation only. Gates/thresholds freeze before held-out evaluation. `test-results.json` includes both models, confusion matrices, macro-F1/accuracy/per-class support, coverage and errors among suggestions; results cannot authorize expense saves.

`python -m aqua_classifier.dev_adapter --help` documents the course-only adapter. Configure public AQ_SUPABASE_URL/ANON_KEY; caller must present a real active Owner token. It binds loopback, verifies through backend aq_me, uses strict request/response schemas, accepts no client model/identity fields and cannot save anything. HTTPS is required for backend validation; do not expose the local development HTTP server publicly.

The Java model loader validates finite parameters, dimensions, fixed label order, bounds, version and provenance. Approved model.json becomes the signed APK's expense-model.json, pinned by reviewed AQ_CLASSIFIER_SHA256. A checksum located beside an attacker-replaceable model provides no authenticity; signed APK/release review is the trust boundary. No raw dataset is packaged.

Proposed gates remain macro-F1≥.80, every recall≥.60, package≤2MiB, Owner-tablet p95≤100ms and incremental memory≤32MiB. Accuracy gates require reviewed held-out data. Device timing requires100 warmups/1000 timed calls on the declared tablet; training timings require a warmup and five fresh processes, median/range and peak RSS. These measurement harnesses/evidence remain unfinished in WORK_STATE until implemented and actually executed. Repeated timing calls are not independent accuracy examples.
