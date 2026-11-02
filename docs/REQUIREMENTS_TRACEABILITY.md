# Requirements traceability

Baseline: `handoff/CODEX_PROMPT.md`, five originals under `handoff/sources/`; extracted criteria under `handoff/source_readable/`. Status vocabulary: Planned, In Progress, Implemented but Unverified, Verified, Blocked. Verification requires applicable executed passing checks. Detailed mapping is in progress; no requirement is yet Verified.

| Requirement | Status | Implementation | Verification / gap |
|---|---|---|---|
| A-01 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-02 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-03 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-04 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-05 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-06 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-07 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-08 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-09 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-10 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-11 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-12 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-13 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-14 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-15 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-16 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-17 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-18 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-19 | In Progress | See component code / source inventory | Full criterion checks pending |
| A-20 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-01 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-02 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-03 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-04 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-05 | In Progress | See component code / source inventory | Full criterion checks pending |
| B-06 | In Progress | See component code / source inventory | Full criterion checks pending |

## Targeted verification (current instance)

B-03/B-06 subset: `backend/tests/smoke.sql`, evidence `evidence/postgres-migrations.log`: passed on PostgreSQL17.11 with explicit Auth stubs. Covers identical retry, payload conflict, exact phone format, Staff expense/admin denial and inactive existing session. These passing subsets do not verify the full requirements. Core migration and `supabase/functions/contract.sql` execute successfully; hosted Auth/MFA not run.

Classifier Java compile passed; self-test failed missing Python golden fixture (`evidence/classifier-java.log`). Android incomplete: missing OwnerUi and wrapper; no APK/build evidence. All A/B overall statuses remain Planned/In Progress until detailed coverage exists.

Classifier verification update: `classifier/tests/generate_golden.py` generated 8 clearly synthetic cases; `ClassifierSelfTest` passed 154 assertions including Python/Java tokens/features/scores/policy parity. `classifier/tests/test_gradients.py` passed 2 tests covering all central finite-difference weight/bias gradients and extreme-logit softmax stability. Logs: `evidence/classifier-{java,python,fixtures}.log`. Supersedes the missing-fixture failure above; real-data/model/device gates remain Blocked and full A-09/B-01/B-06 remain unverified.

A-04/A-05/A-06/A-07/A-08/A-09/A-10 and Staff shared counterparts: `backend/tests/lifecycle.sql` verified targeted backend lifecycle/date/financial/reservation paths (see final PostgreSQL log). Full requirements remain In Progress: hosted integration/UI/story variants not all checked. `OwnerUi.java` adds expenses/report/PDF/settings/MFA flows, host API35 compilation passed; device/UI verification not run.

Latest evidence: UTF8 backend migration/smoke/lifecycle/admin/security/Unicode tests pass (`evidence/postgres-utf8.log`); real concurrent stock test passes. Wrapper final build/unit/lint/Java selfTest passed (`evidence/android-final-build.log`),4 Android unit tests,154 Java assertions; Python8 checks passed. age file/tamper checks pass (`evidence/recovery-test.log`), hosted/database restore not run. `docs/source-requirements.json` contains160 individually indexed acceptance/scenario records; detailed code/test/gap mapping remains in progress. No full A/B requirement is declared Verified based solely on these subsets.
