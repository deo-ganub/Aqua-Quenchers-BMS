# Aqua Quenchers BMS — algo-ias

## IAS submission — the four required deliverables

These files are on **algo-ias**. If GitHub opens another branch, select `algo-ias` before browsing. The links below open the requested files directly.

| Professor's requirement | Open the deliverable | What it contains |
|---|---|---|
| DRAFT Privacy Policy | [DRAFT Privacy Policy](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_PRIVACY_POLICY.md) | Information collected, purposes, rights, retention, security and exact Philippine laws/sections inside the policy |
| DRAFT Terms and Conditions | [DRAFT Terms and Conditions](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_TERMS_AND_CONDITIONS.md) | Account responsibilities, authorized use, records, privacy, provider services and adoption |
| Checklist of Recommended Security Controls | [Security Controls Checklist](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/RECOMMENDED_SECURITY_CONTROLS_CHECKLIST.md) | Explicit checkboxes distinguishing locally verified work and outstanding acceptance requirements |
| Implementation of Security Controls | [Security Implementation and Code Links](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/IMPLEMENTATION_OF_SECURITY_CONTROLS.md) | Direct app/database/Edge source links, executed test evidence and remaining verification |

**Professor-recommended generator:** [nisrulz/app-privacy-policy-generator](https://github.com/nisrulz/app-privacy-policy-generator). [Drafting method and adaptations](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/POLICY_GENERATOR_METHOD.md) records the reviewed source templates and app-specific revisions. Policies remain drafts; checked local tests do not establish production/legal certification.


Native Java/XML Android Owner application with Supabase authentication and checked database operations. Production deployment remains deferred. Read [WORK_STATE](docs/WORK_STATE.md), [traceability](docs/REQUIREMENTS_TRACEABILITY.md) and AGENTS.md before changes.

- **Database and privacy:** [setup](supabase/SETUP.md), the ordered [migrations](supabase/migrations) and [administrator operations](docs/security/DATABASE_PRIVACY_OPERATIONS.md). The additive migration does not execute real erasures or opening counts by itself. Review/deploy matching client/Edge/database changes in approved development only.
- **Benchmark:** open [the notebook](docs/academic/Aqua_Quenchers_Benchmark.ipynb) and [student guide](docs/academic/BENCHMARK_NOTEBOOK_GUIDE.md). Included offline classifier source supports explicit synthetic demonstration and private reviewed-data experiments. Synthetic execution is not station accuracy.
- **Android classifier:** canonical Java module is connected to the expense flow. Manual entry remains available. A reviewed nonsynthetic model must be supplied as `app/src/main/assets/expense_model.json`, with its independently reviewed SHA-256 passed as `-PAQ_CLASSIFIER_SHA256=...`. No model/data is packaged by default; release/signing/data/device approval is separate.
- **Verification:** local evidence is under `evidence/`. Database tests use a dedicated new no-TCP PostgreSQL fixture cluster and disposable databases; host transport tests use in-memory connections. Do not run them against hosted data or replay earlier retained business fixtures.

## Build

Use Android Studio with the declared Gradle9.6/AGP9.4.1/JDK25 toolchain and SDK37 (minimum device API29). The wrapper pins the distribution checksum. Build with `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :classifier-java:selfTest`. Maintain configured proxy/CA trust when using the managed cloud; a downloaded JDK may require the system CA truststore. Never disable TLS verification.

## Privacy and artifact handling

Privileged service/recovery keys, private customer/training data and independent ledgers stay outside source control. Word/PDF documents are delivered directly through the conversation, not GitHub. Policy adoption, lawful bases/retention, actual independent recovery custody, reviewed model/data and physical-device/hosted verification require the corresponding authorized evidence. Local tests do not constitute an independent full security audit or production/legal certification.

## Presentation and IAS legal references

Read the [presentation script](docs/academic/PRESENTATION_SCRIPT.md), [laws and sections table](docs/ias/LAWS_AND_SECTIONS.md), [draft Privacy Policy](docs/ias/DRAFT_PRIVACY_POLICY.md) and [draft Terms and Conditions with clause table](docs/ias/DRAFT_TERMS_AND_CONDITIONS.md). These distinguish legal references, project rules, local verification and remaining operational requirements.
