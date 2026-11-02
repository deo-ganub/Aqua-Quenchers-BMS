# Aqua Quenchers work state

## Objective, scope, decisions
Implement the complete approved native Java/XML Android29+ BMS + Supabase Auth/PostgreSQL/RLS + offline Python training/local Java classifier. User authorizes continuous implementation/testing without repeated permission requests. Production deployment, paid services and live-data changes remain deferred. Baseline `/workspace/Aqua-Quenchers-BMS/handoff/CODEX_PROMPT.md`; five originals `handoff/sources/` and extracts `handoff/source_readable/` preserved locally, private sources excluded from publication. Only Owner/Staff, fixed pricing, integer centavos, Manila business dates, server actor/time, checked transactions/idempotency and manual classifier fallback. No invented metrics, saves or MFA.

## Repository
Branch `work`, currently unborn; coherent initial implementation commit pending. Initially empty checkout, no unrelated user changes. Relevant untracked trees: app/, classifier/, classifier-java/, supabase/, backend/, admin/, docs/, evidence/, .github/, Gradle wrapper/config and README/AGENTS. Local properties/builds/private handoff ignored; CODEX_PROMPT included. No push/deploy/external data mutation.

## Verified evidence
- Original5 source SHA256 manifest checks; SDK35/build-tools35 official manifest sizes/checksums and Gradle8.11.1 official SHA256 verified.
- `evidence/android-final-build.log`: wrapper assembleDebug/testDebugUnitTest/lintDebug/:classifier-java:selfTest BUILD SUCCESSFUL (52 tasks, 26 new). Android4 unit tests pass; lint0 errors with warnings (mostly untranslated programmatic labels). APK `app/build/outputs/apk/debug/app-debug.apk`. APK built before newest SignInDeviceTest (instrumentation build pending).
- Java154 assertions pass including8 synthetic Python/Java token/TFIDF/softmax/policy cases, `evidence/classifier-java.log`. Python8 tests pass incl gradients/extreme logits/sklearn TFIDF/MNB/loss/dev adapter schema, `evidence/classifier-python.log`. No station accuracy or real device gate evidence.
- PostgreSQL17 actual UTF8 DB `aq_bms_dev`: core+Auth migrations, smoke/lifecycle/admin_security/unicode pass (`evidence/postgres-utf8.log`): idempotency/conflict, exact contact, inactive/expired/anonymous/Staff denial, Owner AAL1 denial/AAL2 identity-preserving rename, pickup charge/shortfall, reservation conversion/retry/POS protection, partial/final delivery, expense list and receipt/business-date/as-of reports,500/501 supplementary Unicode boundary.
- Actual concurrent Owner/Staff last-gallon test passes: one sale/receipt, one denial, zero stock (`evidence/postgres-concurrency.log`). Test creates/removes its own database.
- `evidence/recovery-test.log`: actual age1.2.1 encryption/files roundtrip, copies/hash checks and authenticated tamper rejection pass with temporary synthetic bundle/keys. Database/hosted restore NOT RUN. Auth node runner completed (`evidence/auth-node.log`); hosted Auth/MFA not run.

## Implemented but not fully verified
Native shared operational forms, Owner expenses/async suggestions/manual fallback, report preview/PDF, inventory/settings/profile/password/managed MFA Staff form; real HTTPS transport. Session activity guard and stale RPC callback guard; request digests/keys retained in saved state. Hash-pinned reviewed model APK loader rejects missing/synthetic release. No trained release supplied. Need full UI/state/hosted checks.
Backend checked RPCs/RLS/default deny, service-only alias mapping/rate limit, managed Auth staff provisioning reconciliation. Session advisory lock fixes concurrent first access. All required scenarios not yet covered.
Python reviewed/grouped data/training/eval/export plus CLI/dev adapter; math tests pass. Training/evaluation end-to-end and timing harness unfinished.
Recovery export/age packaging/private schedule/files restore and privacy/correction runbooks written. Automatic7-day verified retention policy, actual independent cloud custody/monitoring and database restore drill not complete. Bootstrap SQL written but not yet exercised.
Source inventory indexes160 acceptance/scenario records across20 A requirements (`docs/source-requirements.json`). Detailed per-criterion implementation/test mapping remains unfinished; full requirements NOT Verified. No Figma visuals inspected.

## Commands/runtime and pending outcomes
Toolchains: JAVA_HOME=/workspace/tooling/jdk21; GRADLE_USER_HOME=/workspace/tooling/gradle-home (proxy configured); SDK /workspace/tooling/android-sdk in ignored local.properties. Wrapper checksum pinned. Python global numeric dependencies match requirements; use PYTHONPATH=classifier.
Postgres cluster /workspace/tooling/pgtest, socket same, port55432, OS agent; it is running (check before restart). UTF8 `aq_bms_dev` is the current isolated migrated fixture DB; old postgres database was SQL_ASCII, superseded for Unicode evidence. Binaries /workspace/tooling/sysroot/usr/lib/postgresql/17/bin FIRST in PATH; LD_LIBRARY_PATH=/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu. Socket commands need environment sandbox escalation, already authorized development verification. SQL fixtures rollback; never target cloud/live databases.
Gradle final build session29285 may need completion polling; log confirms success. No uncertain external write. Temporary recovery identities removed with test temp directory. Keep credentials/dumps/models/training data outside Git.

## Blockers (external)
Figma second-page/component/dialog exports/access; reviewed seven-category training dataset; declared Owner tablet/Staff phone/test network; approved development Supabase project/config/MFA access; restricted independent recovery destinations/keys/custodian and controller-adopted retention/erasure approval; hosted isolated restore drill. Independent internal work below remains, so scope is NOT externally blocked in full.

## Ordered remaining work / current task
1. Commit coherent current baseline; compile instrumentation test APK, rerun affected checks on fixes. Inspect actual lint warnings; no suppressed correctness checks.
2. Finish Python training/eval end-to-end/data/benchmark harness and Java/device benchmark harness; keep synthetic evidence distinct.
3. Extend POS/credit/change/payment/short-pickup-underpaid/reschedule/reservation-theft/error/security tests; exercise controlled bootstrap.
4. Complete Android state restoration/selectors/loading/error/native UI tests and backend/Edge contract integration; full visual/hosted/device checks await access.
5. Complete recovery retention/age monitoring/isolated database restore and approved compensation tooling (no arbitrary edits); maintain honest external blockers.
6. Map every source criterion to code/tests/gaps and finish reproducible setup/config docs; checkpoint after each meaningful step.

NEXT_ACTION: run `JAVA_HOME=/workspace/tooling/jdk21 GRADLE_USER_HOME=/workspace/tooling/gradle-home ./gradlew --no-daemon --max-workers=2 assembleDebugAndroidTest lintDebug` and inspect logs; then add full classifier pipeline/data and benchmark harness tests. Do not stop at this checkpoint while executable independent work remains.
