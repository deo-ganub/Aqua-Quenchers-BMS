# Aqua Quenchers BMS

Native Java/XML Android (API29+) with Supabase Auth/PostgreSQL, exact centavo accounting and local Java expense classification. Owner tablet and Staff phone workflows share the checked operational backend. Production deployment remains deferred. Read `docs/WORK_STATE.md` before continuing: implemented code and local verification do not establish hosted/device/Figma readiness.

## Build and run

Use Android Studio/JDK21 (or AGP-compatible JDK17+) and Android SDK35/build-tools35. The Gradle8.11.1 wrapper has an official distribution checksum pin. Keep the environment proxy/CA configuration intact. In this prepared workspace:

```
export JAVA_HOME=/workspace/tooling/jdk21
export GRADLE_USER_HOME=/workspace/tooling/gradle-home
./gradlew assembleDebug testDebugUnitTest lintDebug :classifier-java:selfTest
```

`local.properties` selects the SDK and is ignored. Configure only the public `AQ_SUPABASE_URL` (HTTPS) and `AQ_SUPABASE_ANON_KEY` as Gradle properties/environment values, then rebuild. Missing configuration displays failed-save/auth feedback; no save is simulated. NEVER embed service-role credentials in Gradle/Android. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Install with `adb install -r` on Android10+; targeted native checks use the guarded controllers documented in `docs/LOCAL_REAL_SERVICES.md`. Inspect their private intent/result state first; a blanket connected-test rerun can repeat business mutations and is not the continuation workflow.

## Development backend

Use an approved **development** Supabase Free project. Apply every versioned file under `supabase/migrations/` in lexical order using reviewed administrator access; deploy the two protected development Edge handlers with Supabase tooling only when project access is supplied. No hosted deployment has been performed. `supabase/config.toml` specifies no signup/min15/TOTP and handler-managed function authentication; read admin/ACCOUNT_SECURITY.md before deployment. Hosted settings must be checked rather than assumed applied. Handlers read server-only SUPABASE_URL/ANON_KEY/SERVICE_ROLE_KEY. `username-login` resolves normalized aliases through service-only RPC, uses managed password Auth and rechecks current profile. `staff-admin` validates the user and database-enforced Owner/AAL2 authority; passwords stay in managed Auth. Initial Owner and MFA enrollment/recovery are controlled administrator tasks outside ordinary app navigation; public sign-up must be disabled and managed Auth password minimum configured to15. Do not expose the private schema through REST.

Create the initial Auth identity using the managed administrator procedure; bind its stable ID and approved profile with `admin/bootstrap.sql`. Seed opening inventory exactly once with Owner approval using the same runbook. No app inventory-adjustment or transaction-delete controls exist. Development test principals/stock must remain separate from business records.

SQL tests use explicit synthetic Auth stubs, never real JWT signature verification. The prepared local PostgreSQL17 cluster listens on `/workspace/tooling/pgtest`, port55432. UTF8 database `aq_bms_dev` has migrated empty fixture tables. Put `/workspace/tooling/sysroot/usr/lib/postgresql/17/bin` first in PATH, set LD_LIBRARY_PATH to `/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu`. Run `python3 backend/tests/run_suite.py` with those binaries in PATH. It applies all current migrations and every rollback SQL fixture in a NEW disposable UTF8 database, then removes it. The retained aq_bms_dev may have older migrations; do not reset it to run tests. `backend/tests/concurrency.py` creates/removes only its own isolated local test database. Never run fixtures against a hosted/live database.

Local official Auth/REST and actual connected Android workflows are now verified. Read [LOCAL_REAL_SERVICES.md](docs/LOCAL_REAL_SERVICES.md) to restart retained services without resetting synthetic records. Additional native expiry/retry and real Auth-schema restore evidence is under `evidence/real-auth-*.log`. These results do not establish full scenario/design/declared-device/model/hosted-recovery acceptance.

## Classifier

```
python3 -m venv .venv
.venv/bin/pip install -r classifier/requirements.txt
PYTHONPATH=classifier .venv/bin/python -m unittest discover -s classifier/tests
PYTHONPATH=classifier .venv/bin/python classifier/tests/generate_golden.py
./gradlew :classifier-java:selfTest
PYTHONPATH=classifier .venv/bin/python -m aqua_classifier --help
```

Student TF-IDF/softmax gradient training and weighted Multinomial NB live in `classifier/aqua_classifier`. Data is reviewed JSONL; grouped splits, training-only features, validation-only tuning and once-only held-out evaluation are implemented. See `classifier/README.md`. Golden fixtures are **synthetic correctness evidence**, not a release model or station benchmark. No reviewed labeled dataset/release model is supplied. Manual expense entry remains available.

A reviewed nonsynthetic model goes in `app/src/main/assets/expense-model.json`, with separately reviewed `AQ_CLASSIFIER_SHA256` pinned at build; signed APK review protects provenance. No raw training remarks enter the APK. Missing/invalid models return UNAVAILABLE; suggestion/application never persists an expense. The optional authenticated development adapter (`python -m aqua_classifier.dev_adapter`) verifies an active Owner through the real development backend; it is not a production dependency.

## Evidence and remaining work

Logs under `evidence/` record actual checks. Local functional audit: **152 Verified and 8 externally Blocked** story/acceptance records across20functional requirements; this is not full project acceptance or160 passed tests. Verification includes official local signed Auth/TOTP/REST and production handlers, native Owner/Staff workflows, exact financial/date/stock/actor/replay reconciliations, real SAF/PDF exports, recovery/restore/privacy checks,18disposable SQL fixtures, concurrent races, Android build/unit/lint and synthetic classifier mathematics/parity.

Remaining work requires accessible Figma visuals, reviewed station data/released model, declared physical devices/network, approved real opening counts/custodian and hosted recovery/privacy/backup configuration/access. See `docs/KNOWN_LIMITATIONS.md`, per-entry `docs/source-requirements.json` and `docs/WORK_STATE.md`. Production deployment remains deferred. Generated APKs/reports are ignored build artifacts; the debug APK currently targets the owned local test backend and must be rebuilt with approved public hosted configuration before station use.

Recovery/correction/privacy procedures: `admin/recovery/RUNBOOK.md`, `admin/EXCEPTION_AND_PRIVACY_RUNBOOK.md`. The schedule remains disabled until configured on a private runner; no paid service/public backup artifacts are used. Handoff sources are retained locally under `handoff/`, with `handoff/CODEX_PROMPT.md` as the actual baseline. Private academic originals are excluded from source publication.

Native runtime checks: see `docs/ANDROID_RUNTIME_CHECKS.md`. Actual connected API29 checks cover both roles, failed-save/retry states, date/quantity/payment/expense boundaries, restored previews, pickup/delivery/reservations/multiple-date carryover, settings/MFA/permissions and report export. Test credentials and classifier synthetic fixtures remain outside the app APK. Declared-device, real-model and Figma gates remain external.
