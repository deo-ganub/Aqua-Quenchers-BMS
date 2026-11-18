# Classifier run: SYNTHETIC DEMO ONLY (2026-11-14)

Dataset: `classifier/demo/synthetic-demo.jsonl` produced by `classifier/demo/make_synthetic_dataset.py`.
420 generated remarks (60 per category, English/Filipino/mixed), every row `synthetic: true`. Each
subject is one group, so the test split contains only subjects never seen in training.

Commands (Python 3.13, numpy 2.3.5, scipy 1.17.0, scikit-learn 1.8.0, seed 42):

    PYTHONPATH=classifier python -m aqua_classifier prepare  --dataset classifier/demo/synthetic-demo.jsonl --output evidence/classifier-synthetic-demo --synthetic
    PYTHONPATH=classifier python -m aqua_classifier train    --dataset ... --output ... --model-version EXP-LR-v1-synthetic-demo --synthetic
    PYTHONPATH=classifier python -m aqua_classifier evaluate --dataset ... --output ... --synthetic

Held-out test (84 remarks, 19 with no known vocabulary), frozen before test read:

| Model | Accuracy | Macro-F1 | Suggestion coverage | Error among offered |
| --- | --- | --- | --- | --- |
| Multiclass Logistic Regression (primary) | 0.643 | 0.608 | 0.107 | 0.000 |
| Multinomial Naive Bayes (comparison) | 0.548 | 0.547 | 0.536 | 0.111 |

Quality gate (macro-F1 >= 0.80, every recall >= 0.60): FAILED for both. Not tuned after reading test
labels. Device latency/memory gates: NOT RUN. `release_approved: false`.

This demonstrates the working pipeline and app flow only. It is not station accuracy and must not be
presented as such (ALGOCOM Task 5). The model ships only in debug builds
(`app/src/debug/assets/expense-model.json`), where the Add Expense form shows a "Demo model trained on
synthetic examples" note. Release builds accept only an Owner-reviewed model.
