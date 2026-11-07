# How to use and explain the benchmark notebook

Open [Aqua_Quenchers_Benchmark.ipynb](Aqua_Quenchers_Benchmark.ipynb), or [open the branch notebook in Colab](https://colab.research.google.com/github/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/academic/Aqua_Quenchers_Benchmark.ipynb). It now contains 16 cells, including 7 code cells and student-language explanations. The offline `classifier/` source and canonical `classifier-java/` module are included on this branch. The Android expense screen now uses the canonical engine, but an independently approved real model/digest and actual-device verification are still required.

## What each stage does

| Notebook section | What it means | What you do |
|---|---|---|
| 1. Prepare Python | Obtain the algo-ias source and its pinned packages | In Colab, use the displayed clone/install commands once. Locally use an existing environment. Never paste a secret into a notebook. |
| Settings | Choose classroom demonstration or reviewed data | Leave `MODE="synthetic"` for an immediate demo; set `RUN_EXPERIMENT=True`. |
| 2. Private helpers | Keep data/logs outside Git and run the exact classifier code | Run the helper cell; it performs no training by itself. |
| 3. Prepare/train | Create groups/splits, learn word weights and fit both algorithms | Run the training cell. Synthetic mode creates 42 clearly marked made-up examples; reviewed mode uses your private file. |
| 4. Training output | Show category counts, vocabulary/model size and validation scores | Read the actual aggregate summary. Validation scores are used to choose settings. |
| 5. Frozen evaluation | Check examples held back from model selection | After reviewing the frozen experiment, enable `RUN_HELD_OUT_ONCE`. Real-data mode also requires freeze-review acknowledgement. |
| 6. Final summaries | Compare logistic regression and Naive Bayes | Read macro-F1, per-class recall, confusion matrices and suggestion coverage. No raw remarks or private example IDs are printed. |
| 7. Training timing | Measure the full prepare/train/tune workflow | Optionally set `RUN_FIVE_PROCESS_TIMING=True`; one warmup and five measured fresh processes run. |
| 8. Tablet checks | Keep Android performance evidence separate | Use the actual Owner tablet and approved model for latency/memory/parity; Colab cannot substitute for those measurements. |

## Immediate classroom demonstration

1. Prepare the source/environment using section 1. For local Jupyter, set `PROJECT_ROOT` to the repository root. For a complete synthetic-only demonstration, you may set all three `RUN_` switches to `True` before Run All; reviewed data still requires the separate freeze review.
2. In settings, choose `MODE="synthetic"` and `RUN_EXPERIMENT=True`.
3. Run the helper, training and training-summary cells.
4. Inspect the frozen experiment. Set `RUN_HELD_OUT_ONCE=True`, then run the evaluation and final-summary cells. Do not rerun the settings cell while trying to continue the same context; that resets its in-memory context. If you intentionally restart, begin a new experiment and preserve the earlier outputs.
5. Optionally set `RUN_FIVE_PROCESS_TIMING=True` and run timing.

Run controls can be changed in a new cell, such as `RUN_HELD_OUT_ONCE=True`, so earlier settings are not rerun unnecessarily. Every generated directory is private and separate. Running a new training experiment creates a new directory rather than overwriting a result. Repeating held-out evaluation on the same frozen experiment is rejected; never remove markers to retune on test labels.

The demo examples are deliberately simple and contain category words. A high score demonstrates code execution on fixtures, not quality on real station descriptions. Say: **“These are actual results from made-up examples; we will evaluate station quality using Owner-reviewed data.”** The shared GitHub notebook has outputs cleared; local test evidence records the executed synthetic workflow.

## Reviewed-data experiment

Set `MODE="reviewed"`, the absolute private `DATASET` path, an appropriate model version and `OWNER_REVIEW_CONFIRMED=True` only after the real review. The source must be clean and committed. The loader requires the fixed schema/categories, reviewed flags and grouping metadata and screens obvious contact identifiers. These checks do not replace human review/anonymization. Optional private workspace must be outside the repository and restricted to its custodian.

Split targets remain 60/20/20 with seed 42; grouping can change actual proportions. Train-only vocabulary/IDF and validation-only tuning prevent using final-test information to choose the model. Freeze review is required before the held-out step. Raw logs and error-analysis files stay private; never commit reviewed remarks or sensitive notebook outputs.

## Explain the results simply

- **Macro-F1:** gives each category equal importance when summarizing precision and recall.
- **Recall:** how many examples belonging to a category the model finds.
- **Confusion matrix:** where the model chose the correct or wrong category; rows and columns follow the displayed labels.
- **Suggestion coverage:** how often the policy offers a category instead of leaving a manual choice.
- **Training timing:** actual preparation, learning and tuning time, including five measured fresh processes.
- **p95:** a device response-time percentile. It needs the actual tablet, not a Python training timer.

The proposed gates remain macro-F1>=0.80, every category recall>=0.60, model<=2MiB, tablet p95<=100ms and incremental memory<=32MiB. Failures must be reported at the original thresholds. Synthetic results do not satisfy real release gates. The notebook never saves an expense, adjusts stock, uploads personal data or approves a production model.

## Checks executed for this revision

`tools/test_benchmark_notebook.py` executes the actual code cells in default and opt-in synthetic modes, including full training/evaluation/timing. It verifies aggregate-only output, reviewed-mode prerequisites and rejection of repeated held-out use without changing the original results. See `evidence/notebook-tests.log` and the explicitly synthetic `evidence/notebook-synthetic-execution.json`. Classifier correctness/provenance tests are in `evidence/classifier-python-tests.log` (18 tests).

## Declared tablet and requester authorization

The requester selected **Xiaomi Pad 8** and authorized continued project work on 7 November 2026. The tablet model is now recorded in [device_profile.json](../../benchmark/device_profile.json). Read [PROJECT_APPROVALS.md](../PROJECT_APPROVALS.md) for device setup and standing authorization. `tools/device_preflight.py` captures safe observed properties on the connected trusted computer, without installing an app, collecting a serial in the report or claiming benchmark results. CPU, RAM and Android version remain unfilled until observed.
