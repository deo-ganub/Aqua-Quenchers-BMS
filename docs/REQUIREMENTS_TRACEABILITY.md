# Implementation and Verification Traceability

Current scope: algo-ias database/privacy/native-session improvements, canonical classifier integration and runnable benchmark notebook. Historical reference evidence remains separately attributed. No Word/PDF downloads are uploaded to GitHub.

| ID | Requirement and implementation | Verification | Status |
|---|---|---|---|
| IAS-SESSION | 202611070001_session_privacy.sql: active identity, signed-session binding,30-minute idle/RLS, logout, status/role revocation | 14new isolated SQL tests, including retry/payment/privacy/bootstrap cases; synthetic claims are not managed signature verification | Verified locally |
| IAS-PRIVACY | Administrator-only dry-run/approved contact removal; structured customer/order/audit/retry copies; retained names/money/hashes; cutoff-aware ledger replay | Same14tests, including denied Owner/Staff/service execution, two-database replay, private files and exact target confirmation | Verified locally |
| IAS-PAYMENT | Typed-intent hashes, identical/concurrent retry once, changed-intent conflict, actual customer type/price and bounded payments | SQL suite verifies one receipt/audit, unchanged overpayment and financial history; legacy hashless keys fail closed | Verified locally |
| IAS-TRANSPORT | Api/Session: generation/identity capture, fixed JSON, one refresh,4MiB HTTPS cap/no redirects, honest uncertainty and captured-identity logout | 36 in-memory native assertions and actual Android compilation | Verified locally; real provider/device gate pending |
| IAS-EDGE | identity handler plus identity_safety:16KiB request,256KiB response, deadlines, exact fields/types, current-profile/session recheck and safe audit uncertainty | 5Deno input tests,5actual-handler mocked-provider tests, full typecheck | Verified locally; hosted behavior pending |
| IAS-DEVICE | Secure activity/MFA windows, password/OTP state exclusions and destroyed-login guard | APK/unit/lint pass; no currently connected emulator/physical device | Implemented but Unverified on device |
| ALG-SOURCE | Included offline classifier and actual-source-root provenance independent of caller CWD | 18Python tests including two provenance regressions | Verified locally |
| ALG-APP | Canonical classifier-java engine wired into expense flow; cached strict digest/size/nonsynthetic loader, stale/manual/session guards | 155Java synthetic assertions,10Android JVM unit tests, actual APK build/lint0errors72warnings | Verified compilation/engine/asset guards; UI/real-model device gate pending |
| ALG-NOTEBOOK | 16 cells/7 code cells, student explanation, opt-in synthetic/private-reviewed modes, once-only frozen test, aggregate summaries and training timing | Actual default and full synthetic cell execution, repeat rejection/result preservation and reviewed-mode guard; evidence/notebook-synthetic-execution.json | Verified synthetic only |
| EXT-RELEASE | Reviewed anonymized corpus, independently approved model, Xiaomi Pad 8 access/Android parity, hosted Auth/deployment/custody and legal/retention approval | External inputs/evidence absent; no deployment performed | Blocked |
| AUDIT-FINAL | Independent coverage critics/candidate/final verification for full skill audit | Prior 77-unit source ledger only; new implementation tests do not substitute for independent phases | Incomplete |

Exact commands and current NEXT_ACTION are in WORK_STATE.md. The source handoff remains handoff/CODEX_PROMPT.md. All test accounts, tokens, examples, payments and restored copies in new tests are synthetic and isolated.

Requester authorization and target-device declaration are recorded in [PROJECT_APPROVALS.md](PROJECT_APPROVALS.md). General development/source-sync approval is confirmed. The Xiaomi Pad 8 is declared, not physically measured; missing access/data/operational particulars remain input/evidence gates. Device-preflight fixture tests pass3cases without marking actual hardware verified.

| IAS-DOC-LAW | Explicit laws/sections, privacy and terms clause tables; Markdown presentation script and navigation | Documentation links, table structure, statutory references and diff checked; no runtime changes | Verified documentation: 39 relative file links and table structure pass; statutory texts retrieved; NPC source URLs returned HTTP403, so live NPC publications not revalidated |
