# Classifier run v2: SYNTHETIC DEMO ONLY (2026-11-14)

Same protocol as `../classifier-synthetic-demo/README.md` (seed 42, grouped 60/20/20, tuned on validation,
frozen before one test read). v2 adds everyday supply items (tape, markers, cleaning gear) to four
Supplies groups after the Owner tried "scotch tape" and the v1 model returned "No reliable suggestion".
444 synthetic rows; 90 held-out test remarks, 26 with no known vocabulary.

| Model | Accuracy | Macro-F1 | Suggestion coverage | Error among offered |
| --- | --- | --- | --- | --- |
| Multiclass Logistic Regression (primary) | 0.678 | 0.629 | 0.111 | 0.000 |
| Multinomial Naive Bayes (comparison) | 0.678 | 0.641 | 0.489 | 0.091 |

Quality gate FAILED for both (macro-F1 >= 0.80). Device gates NOT RUN. `release_approved: false`.

Shipped in debug builds as `EXP-LR-v2-synthetic-demo`. Spot checks with the exported model:
"scotch tape" and "packing tape" -> Supplies; "water bill" -> Water; "gasolina para sa motor",
"bayad meralco" and "repair ng pump" -> no reliable suggestion (their groups fell in the held-out
split, so the shipped model never trained on them). The split was not re-drawn to change this.
Real coverage needs Owner-reviewed station remarks where common words recur across many examples.
