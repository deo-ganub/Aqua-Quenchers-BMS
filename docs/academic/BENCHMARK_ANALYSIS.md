> Branch scope: this academic package is added to `algo-ias` based on `owner-module` at `bf2b43e`. Implementation links to `424c701` describe the separate `work` baseline; they do **not** prove those controls exist in `owner-module`. See [branch review](../security/OWNER_MODULE_REVIEW.md).

# Expense classifier benchmark analysis

This is the analysis protocol and implementation map for the user-supplied ALGOCOM paper, reviewed 6 November 2026. **No station accuracy, physical-device latency or release-memory result is asserted.** The repository has synthetic correctness/timing evidence; reviewed station data and the actual Owner tablet are absent. The [notebook](Aqua_Quenchers_Benchmark.ipynb) has no stored outputs and requires explicit private inputs.

## Algorithm and experimental controls

| Paper requirement | Implementation and evidence |
|---|---|
| NFKC/lowercase Unicode text, bounded unigrams/bigrams, sparse TF-IDF | [text.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/text.py), [features.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/text.py); training-only vocabulary/IDF; maximum500 raw and normalized codepoints,128tokens and3000features |
| Student multiclass logistic regression with stable softmax/gradient learning | [models.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/math_models.py), [training/tuning](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/pipeline.py) |
| Weighted Multinomial Naive Bayes comparison | Same models/pipeline; same frozen splits/features and reported comparison. No requirement or claim that NB must lose |
| Owner-reviewed labels, seven fixed categories, leakage control | [data.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/data.py), [label guide/template](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/README.md); grouped60/20/20 targets, seed42, exact/near/receipt/template grouping. Actual class counts must be reported, not assumed equal to targets |
| Validation-only hyperparameters and suggestion policy | Pipeline tunes LR/lambda/NB smoothing and confidence/gap using validation only; freezes policy before one held-out evaluation. Unknown/zero-feature examples stay in quality metrics; suggestions remain advisory |
| Once-only held-out evaluation and provenance | Frozen digests/manifests, exclusive output files and evaluation-start reservation in pipeline. A started/failed evaluation requires review; do not delete markers to retune on test labels |
| Pure Java/Android integration | [Strict model](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/Model.java), [inference](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseCategoryClassifier.java), [manual-confirmation lifecycle](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseSuggestionController.java) |

## Measurements to produce

For both models report the frozen dataset/split/source/model hashes, pseudonymous review provenance, actual per-class train/validation/test support, confusion matrix, accuracy, macro precision/recall/F1, per-class precision/recall/F1, offered-suggestion coverage/error, model bytes and environment. Distinguish accepted examples from abstention/manual fallback. Use actual held-out support; a zero-support category cannot demonstrate its recall gate. Keep error examples private/redacted. Timing repetitions are not additional independent accuracy observations.

Training timing uses [benchmark.py](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/benchmark.py): one fresh-process warmup, then five fresh-process prepare/train runs. Report five durations, median/range and peak RSS with CPU/OS/runtime/library versions. It never runs held-out evaluation. The reported duration includes preparation plus training/tuning; label that scope rather than presenting it as an isolated optimizer-only duration.

Device timing uses [InferenceBenchmark](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/InferenceBenchmark.java) and [ClassifierDeviceBenchmark](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/app/src/androidTest/java/com/aquaquenchers/bms/ClassifierDeviceBenchmark.java): actual declared Owner tablet, approved model,100warmups then1000timed inferences. Report median/p95, incremental Java heap including load and separate process RSS/PSS observation. Java heap does not establish process-memory growth. Record model/device/Android/thermal/background/network conditions and Android parity evidence; do not substitute host or emulator timing for tablet results.

| Frozen proposed release gate | Current evidence |
|---|---|
| Held-out macro-F1 >=0.80 | **Blocked:** Owner-reviewed held-out corpus/model absent |
| Every category recall >=0.60 | **Blocked:** actual reviewed per-category held-out support/results absent |
| Model package <=2MiB | Loader/export bound implemented; actual approved release artifact **Not Run** |
| Owner-tablet Java p95 <=100ms | Harness implemented; declared physical tablet/model **Not Run** |
| Incremental memory <=32MiB | Java heap harness implemented; actual tablet/model and separate process memory **Not Run** |
| Python/Java/Android model agreement | Synthetic Python/Java correctness evidence retained; real frozen model and target Android Unicode/runtime parity **Not Run** |

Freeze these gates before final testing. Report failures honestly; do not lower thresholds after seeing held-out results. Historical prototype values, synthetic timing and a code-complete harness cannot fill these result cells.

## Running the workflow

Use an approved isolated offline environment with dependencies already installed from [pinned requirements](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/requirements.txt). Start in this repository root, use private absolute dataset/output paths, a clean reviewed source commit and a **new** output directory. See [classifier instructions](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/README.md). The notebook validates inputs and invokes the same CLI with argv lists; it does not upload files, install packages, download a repository, publish artifacts or generate a release model on its own. Do not run it automatically on repository open. `reviewed:true` in JSON is an ingestion assertion, not proof the Owner actually reviewed/redacted the record.

For a synthetic-only demonstration use the explicit synthetic mode in a separate experiment and label every result **SYNTHETIC ONLY**. It must never satisfy release accuracy, model approval, independent review or physical-device gates. No synthetic experiment is automatically generated or executed by this notebook.
