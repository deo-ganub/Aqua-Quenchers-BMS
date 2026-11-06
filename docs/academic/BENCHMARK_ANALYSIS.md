# Algorithm Specification and Benchmark Analysis

**Document identifier:** AQ-ALG-01

**Edition:** 1.0

**Issue date:** 7 November 2026

## 1. Research Objective and Evidence Scope

The algorithm component investigates advisory classification of station expense remarks using TF-IDF features and multiclass logistic regression, with weighted Multinomial Naive Bayes as a comparison model. The evaluation protocol follows the current Algorithms and Complexity milestone and preserves separate measures of predictive quality, computational cost and application integration.

The offline training source and canonical Java module are now included on `algo-ias`. The Android expense flow calls the Java module through a cached, size/digest/provenance-checked asset loader. A reviewed release model is not included; manual entry remains available. Historical reference links retain their stated revision. Existing synthetic evidence supports numerical and implementation checks within its recorded scope; it does not establish station accuracy or physical-device performance. Reviewed station data, an approved release model and the declared Owner tablet are required for the outstanding empirical evaluation. The companion notebook contains no execution outputs.

## 2. Algorithm and Experimental Controls

| Research requirement | Implementation and evidence |
|---|---|
| NFKC/lowercase Unicode text, bounded unigrams/bigrams, sparse TF-IDF | [text.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/text.py), [features.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/text.py); training-only vocabulary/IDF; maximum500 raw and normalized codepoints,128 tokens and3000 features |
| Student multiclass logistic regression with stable softmax/gradient learning | [models.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/math_models.py), [training/tuning](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/pipeline.py) |
| Weighted Multinomial Naive Bayes comparison | Same models/pipeline; same frozen splits/features and reported comparison. No requirement or claim that NB must lose |
| Owner-reviewed labels, seven fixed categories, leakage control | [data.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/data.py), [label guide/template](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/README.md); grouped60/20/20 targets, seed42, exact/near/receipt/template grouping. Actual class counts must be reported, not assumed equal to targets |
| Validation-only hyperparameters and suggestion policy | Pipeline tunes LR/lambda/NB smoothing and confidence/gap using validation only; freezes policy before one held-out evaluation. Unknown/zero-feature examples stay in quality metrics; suggestions remain advisory |
| Once-only held-out evaluation and provenance | Frozen digests/manifests, exclusive output files and evaluation-start reservation in pipeline. A started/failed evaluation requires review; do not delete markers to retune on test labels |
| Pure Java/Android integration | [Strict model](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/Model.java), [inference](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseCategoryClassifier.java), [manual-confirmation lifecycle](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseSuggestionController.java) |

## 3. Evaluation Measures and Acceptance Criteria

For both models report the frozen dataset/split/source/model hashes, pseudonymous review provenance, actual per-class train/validation/test support, confusion matrix, accuracy, macro precision/recall/F1, per-class precision/recall/F1, offered-suggestion coverage/error, model bytes and environment. Distinguish accepted examples from abstention/manual fallback. Use actual held-out support; a zero-support category cannot demonstrate its recall gate. Keep error examples private/redacted. Timing repetitions are not additional independent accuracy observations.

Training timing uses [benchmark.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/benchmark.py): one fresh-process warmup, then five fresh-process prepare/train runs. Report five durations, median/range and peak RSS with CPU/OS/runtime/library versions. It never runs held-out evaluation. The reported duration includes preparation plus training/tuning; label that scope rather than presenting it as an isolated optimizer-only duration.

Device timing uses [InferenceBenchmark](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/InferenceBenchmark.java) and [ClassifierDeviceBenchmark](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/app/src/androidTest/java/com/aquaquenchers/bms/ClassifierDeviceBenchmark.java): actual declared Owner tablet, approved model,100 warm-up then1,000 timed inferences. Report median/p95, incremental Java heap including load and separate process RSS/PSS observation. Java heap does not establish process-memory growth. Record model/device/Android/thermal/background/network conditions and Android parity evidence; do not substitute host or emulator timing for tablet results.

| Frozen proposed release gate | Current evidence |
|---|---|
| Held-out macro-F1 >=0.80 | **Blocked:** Owner-reviewed held-out corpus/model absent |
| Every category recall >=0.60 | **Blocked:** actual reviewed per-category held-out support/results absent |
| Model package <=2 MiB | Loader/export bound implemented; actual approved release artifact **Not Run** |
| Owner-tablet Java p95 <=100ms | Harness implemented; declared physical tablet/model **Not Run** |
| Incremental memory <=32 MiB | Java heap harness implemented; actual tablet/model and separate process memory **Not Run** |
| Python/Java/Android model agreement | Synthetic Python/Java correctness evidence retained; real frozen model and target Android Unicode/runtime parity **Not Run** |

Freeze these gates before final testing. Report failures honestly; do not lower thresholds after seeing held-out results. Historical prototype values, synthetic timing and a code-complete harness cannot fill these result cells.

## 4. Reproducibility Procedure

Use an approved isolated offline environment with dependencies already installed from [pinned requirements](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/requirements.txt). Start in this repository root, use private absolute dataset/output paths, a clean reviewed source commit and a **new** output directory. See [classifier instructions](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/README.md). The notebook validates inputs and invokes the same CLI with argv lists. Its optional Colab setup commands are explicit; they do not run automatically. Synthetic demonstration mode generates only labelled fictional examples. The default execution switches remain off, and no private data or release artifacts are uploaded. Do not run it automatically on repository open. `reviewed:true` in JSON is an ingestion assertion, not proof the Owner actually reviewed/redacted the record.

For a synthetic-only demonstration use the explicit synthetic mode in a separate experiment and label every result **SYNTHETIC ONLY**. It must never satisfy release accuracy, model approval, independent review or physical-device gates. No synthetic experiment is automatically generated or executed by this notebook.

## Current runnable notebook guide

See [BENCHMARK_NOTEBOOK_GUIDE.md](BENCHMARK_NOTEBOOK_GUIDE.md) for the 16-cell workflow, actual synthetic execution evidence and instructions for reviewed-data evaluation. Source correctness tests and synthetic results remain distinct from station accuracy and actual-device acceptance.
