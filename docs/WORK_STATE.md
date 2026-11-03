# Aqua Quenchers work state

## Objective / authorized scope
Complete Java/XML Android29+ BMS, Supabase Auth/PostgreSQL/RLS and offline Python classifier/local Java inference defined by `/workspace/Aqua-Quenchers-BMS/handoff/CODEX_PROMPT.md` and five local originals/extracts. Continuous implementation/testing authorized. Production deployment, paid services and live-data changes remain deferred. Preserve unrelated/user changes. AGENTS.md points future sessions here and to REQUIREMENTS_TRACEABILITY.md.

Decisions: only Owner/Staff; fixed35/30 pesos and integer centavos/gallons; Manila business dates separate from immutable actual receipts; backend active role/session/MFA authority; atomic stock/reservations/payments and payload-hash idempotency. Classifier suggests only, manual entry available; no fake saves/metrics. Case-specific approved corrections require reviewed compensating migration, never general in-app reversals.

## Git / persistence
Branch `work`; HEAD at checkpoint write `a157805` (parent baseline3561b67). Current coherent checkpoint commit being prepared; inspect git log/status for its final ID. Modified/untracked work is our ongoing native session/draft/contract fixes; core+identity+logout migrations; Auth handler/config/security docs/tests; classifier validation-policy/provenance tests; correction-preview/recovery tooling; source trace/docs and evidence. No unrelated initial changes existed. Private handoff/builds/local.properties/keys/dumps/venvs ignored; CODEX_PROMPT tracked. No push/deploy.

## Verified (component subsets, not full product acceptance)
- Android combined `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug :classifier-java:selfTest`: PASS,6 domain tests,155 Java assertions (8 synthetic cross-language cases), lint0 errors/29 warnings. `evidence/android-final-build.log`; app/test APKs under app/build/outputs/apk. No device execution/Figma match.
- Python16 numerical/reference/strict-adapter/grouped-split/validation-policy/export/synthetic pipeline tests: PASS (`evidence/classifier-python.log`). Threshold grid selected only on validation; unknown vectors remain in quality denominator but cannot be offered. Real data/model/quality/device benchmarks absent.
- Four migrations applied in order to NEW disposable UTF8 PostgreSQL database, then11 fixtures: smoke/lifecycle/admin_security/unicode/edge_cases/expense_fixture/reservation_guards/client_contract/account_recovery/own_profile/logout all PASS (`evidence/postgres-utf8.log`,2026-11-03). This covers stock/short pickup/reservations, exact money/date/as-of receipts, ACL/active/expired/MFA stubs, stable own-profile links, definitive failed account preparation versus uncertain pending, and exact-once session logout. SQL JWT stubs do not prove managed signatures/TOTP.
- Actual two-connection last-gallon, identical retry and conflicting same-key payload cases PASS (`evidence/postgres-concurrency.log`). Opening bootstrap/retry denial/reference beyond999999 PASS. Node7 mocked Auth cases, Deno wrappers typecheck +3 native negative tests and public-client-key guard rejection PASS (auth-node/edge-deno-check/edge-deno-tests/client-key-guard logs).
- age file/tamper/independent-copy checks and2 synthetic retention-policy checks PASS. Populated encrypted dump→new UTF8 database restore with all4 migrations, ACL/RLS, independent privacy-ledger replay and retry non-resurrection PASS (`evidence/recovery-database.log`,2026-11-03). Exact synthetic order+sale charges/receipts31500/expenses75000 reconciled. Read-only administrator correction-preview order/sale totals, unchanged financial/audit state,0600 exclusive output and ordinary-role rejection PASS in same drill. Earlier order-only receipt assertion was updated for the new paid POS fixture; full reconciliation passes. All disposable databases cleaned by finally clauses.

## Implemented but unverified / current task
Native operational/Owner/settings/MFA/PDF forms, dynamic selection by stable IDs, per-account/entity draft+request-key restoration, stale callback/dialog/executor cleanup, refresh/logout/revocation, canonical save acknowledgements, manual/suggestion Apply/override, report/PDF snapshots and Manila presentation compile/pass host checks. Full native UI/rotation/network/actual Auth flows and all160 source criteria remain In Progress; compilation is not runtime proof.
Python/Java strict release provenance/trusted model loading and benchmark harness implemented. Prior synthetic timing report predates validation-policy changes; preserve as historical, generate a new fixture-only report after clean code checkpoint. Actual model/data/tablet gates Blocked.
Private backup schedule/export/age/monitor/conservative7-verified-day retention, privacy ledger and protected correction preview implemented. Actual hosted custody/restore/alerts/approved correction execution Blocked on concrete approvals/access. Restored-session invalidation runbook added; hosted managed invalidation NOT RUN.
Exact task: finalize coherent code/evidence checkpoint; then refresh synthetic timing provenance and exercise concurrent deliveries against reservation/account invariants.

## Commands / runtime / external operations
Latest: isolated Python psql orchestration applied sorted supabase/migrations/*.sql +11 fixtures (PASS); `python3 admin/tests/database_restore.py` with PG/age PATH (PASS after new fixture reconciliation). Outputs above. Reuse valid evidence; rerun only affected checks.
JAVA_HOME=/workspace/tooling/jdk21; GRADLE_USER_HOME=/workspace/tooling/gradle-home; official SDK /workspace/tooling/android-sdk via ignored local.properties; checksum-pinned Gradle8.11.1. Python /workspace/tooling/aq-python/bin/python, PYTHONPATH=classifier; pinned numpy2.3.5/scipy1.17/sklearn1.8. Deno /workspace/tooling/deno-cli/node_modules/.bin/deno, DENO_DIR=/workspace/tooling/deno-cache.
PG17 socket /workspace/tooling/pgtest port55432, UTF8 aq_bms_dev empty rollback fixtures; check existing process before restart. PATH prefix /workspace/tooling/sysroot/usr/lib/postgresql/17/bin then /workspace/tooling/sysroot/usr/bin (age); LD_LIBRARY_PATH=/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu; PGCLIENTENCODING=UTF8. No fixtures against hosted/live systems. Old SQL_ASCII postgres DB is not fixture target.
No uncertain external write/process pending. Docker healthy earlier; SupabaseCLI3 installed with SUPABASE_HOME=/workspace/tooling/supabase-home. Isolated TLS config outside Git. Start1128/PID14189 stopped after confirmed proxy CONNECT403 to public.ecr.aws; no containers created. Additive public.ecr.aws rule and reusable install_script/start_skill saved in environment draft, requires environment-settings save/publication to apply. Runtime2026-11-03 still reports no applied custom hosts/network state unknown; do not blindly retry or bypass TLS/policy. No cloud credentials configured. Do not print service/config/log secrets.

## Blockers
- Applied registry policy (public.ecr.aws) for real local Supabase Auth/REST integration; approved development project access/config for hosted checks. Saved draft alone does not activate access.
- Figma SECOND-page/component/dialog exports/access; no visual fidelity claim.
- Owner-reviewed seven-category dataset/approved release model; no station accuracy claim.
- Declared Owner tablet/Staff phone + normal test network (no /dev/kvm); no latency/memory/screen/PDF/UI runtime result.
- Independent restricted recovery destinations/keys/custodian/controller retention/erasure decisions and hosted isolated drill; no RPO/RTO claim.
- Concrete Owner-approved exceptional correction specification (reference/reason/amounts/date/stock/reservations) before any compensating execution; read-only tooling complete.

## Ordered remaining work
1. Commit current coherent source/evidence; refresh five-process synthetic timing on the clean code commit (new output, never station benchmark).
2. Add/exercise targeted concurrent partial/final-delivery and reservation/retry variants; repair failures and update trace/checkpoint.
3. Continue source-criterion review of independent UI/state/API/error gaps;160 entries map partial evidence, none declared fully Verified from compilation. Update mappings as new checks pass.
4. Reconcile reusable setup/start draft with final commands; preserve existing additive config. Hosted/device/Figma/real-data/custody work proceeds when prerequisites exist.

NEXT_ACTION: commit verified current source/docs/evidence, then run fixture-only classifier benchmark to new evidence/classifier-synthetic-timing-v2.json with /workspace/tooling/aq-python/bin/python and PYTHONPATH=classifier.
