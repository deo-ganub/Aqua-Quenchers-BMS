> Branch scope: this academic package is added to `algo-ias` based on `owner-module` at `bf2b43e`. Implementation links to `424c701` describe the separate `work` baseline; they do **not** prove those controls exist in `owner-module`. See [branch review](../security/OWNER_MODULE_REVIEW.md).

# Professor presentation readiness

Prepared 6 November 2026. Use links on the reviewed branch/commit when presenting; a local file is not on GitHub until its source push is verified. Production remains deferred. Public demonstrations use synthetic/redacted examples, never customer records, account passwords or backup keys.

| Professor request | Repository output | Status / precise missing input |
|---|---|---|
| ALGOCOM: latest PBL Milestone1 | User identifies the linked [ALGOCOM and IAS documents](../ias/README.md) as the latest milestones | **Provided by user:** latest milestone links recorded; live documents remain authoritative |
| Benchmarking analysis notebook or code | [Benchmark analysis](BENCHMARK_ANALYSIS.md), [notebook](Aqua_Quenchers_Benchmark.ipynb), [five-process timing implementation](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/benchmark.py) | Harness/code and methodology available. Notebook intentionally has no execution outputs. Actual reviewed-data/device results **Not Run** |
| Algorithm implementation/demo | [Classifier workflow](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/README.md), [student training pipeline](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/pipeline.py), [logistic regression](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/math_models.py), [Java inference](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseCategoryClassifier.java) | Synthetic mathematics/parity evidence exists; no approved station model packaged. Manual expense entry remains operational |
| IAS: draft privacy policy and terms | [IAS package](../ias/README.md) | Drafts available; operator/privacy adoption fields explicitly pending |
| IAS: recommended and implemented controls | [Security checklist with code/tests](../ias/SECURITY_CONTROLS.md) | Source/local evidence available; hosted/physical/custody controls remain external |
| Whole-codebase Cloudflare/OWASP reports | [Security review index](../security/README.md) | Report scopes, independent checks, unresolved items and methodology provenance included |

## Reproducible demonstration order

1. Open the repository branch and record its commit. Show the native Java/XML [login design source (local unpublished commit de7a03b)](../security/README.md) and existing role-specific app flow using the approved isolated development setup in [local services](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/docs/LOCAL_REAL_SERVICES.md). Do not blindly rerun closed financial/account fixture controllers.
2. Show the [synthetic golden cases](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/fixtures/golden.json), Python/Java implementation and retained [Python evidence](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/evidence/classifier-python.log). Explain that fixture consistency is not station accuracy. A missing/unapproved model visibly offers manual entry; no successful classifier result is fabricated.
3. Open the unexecuted notebook/analysis to explain grouped splits, validation-only tuning, frozen test use, NB comparison and timing gates. Use private reviewed data and an isolated new experiment only when available. Keep raw remarks/results containing private errors outside public commits.
4. Show the IAS drafts, each control's implementation and actual retained test evidence. Explain the hosted, legal/adoption, backup-custody and device checks still required. No draft or archive result constitutes the professor's/Owner's acceptance.

## Remaining presentation/release dependencies

Owner-reviewed anonymized labeled corpus and approved frozen model; declared physical Owner tablet and Staff phone/network; legal operator/privacy channel/lawful-basis/retention decisions; effective hosted Auth/schema/security settings; named independent backup/key/ledger custody and monitored restore/RPO/RTO evidence. The paper's deadlines are planning targets, not promises that missing dependencies have been delivered.
