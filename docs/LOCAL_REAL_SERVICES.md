# Retained local integration services

This is a synthetic development fixture, using official GoTrue and PostgREST with
the production Edge handlers through a test-only HTTPS gateway. It is not hosted
Supabase or production deployment. Versions/digests are recorded in
`evidence/real-services-provenance.json`. The public registry is unnecessary for
this source-backed route. Existing private configuration and databases are retained
under `/workspace/tooling`; live processes must restart after environment restore.

From `/workspace/Aqua-Quenchers-BMS`, run:

```sh
python3 admin/tests/start_retained_local.py
```

The helper validates the local-only configuration, starts stopped services and
checks both official Auth instances through trusted HTTPS. A second invocation
preserves running services and fixture history. It does not provision/reset users,
run migrations or overwrite credentials. PostgreSQL port55432/socket
`/workspace/tooling/pgtest`; normal Auth55434, REST55435, HTTPS55436 and short-lived
test Auth55437. Inspect private logs if readiness fails. Never print private config,
session files, credential fixtures or logs containing sensitive download URLs.

Builds use Java21, Gradle8.11.1 and the retained Android SDK as described in README.
Supply only the local public URL/anon key from private config to build environment
variables `AQ_SUPABASE_URL`/`AQ_SUPABASE_ANON_KEY`; never service keys/passwords.
Default unconfigured builds must fail honestly at connection attempts.

For the owned API29 emulator, follow `ANDROID_RUNTIME_CHECKS.md`, require
`sys.boot_completed=1`, and reverse tcp55436. The fixture's public CA is installed
in its user CA store; only debug builds trust user CAs. Release trust stays system
only. Do not disable TLS checks or modify unrelated devices.

`real_services_test.py` and `connected_android.py` mutate synthetic data and keep
stage/outcome files. Inspect those files before retrying; the completed suites are
not startup checks. `retry_and_expiry.py` uses nonce-scoped one-shot controls to
hide a real committed response and issue one actual two-second token. Normal Auth
remains3600seconds and application inactivity30minutes. Preserve failed attempts
and committed rows; never reset financial history for a passing run.

`real_auth_restore.py` creates a NEW disposable local database, retains actual
Auth ownership/ACLs, revokes sessions only in that clone and verifies real login,
TOTP and protected reads before dropping the clone. Run it after other mutating
harnesses finish. It does not establish hosted recovery or independent custody.

Missing retained private configuration is a setup prerequisite: restore the
authorized synthetic fixture securely; never replace it with invented production
credentials. Real Figma assets, reviewed classifier data/model, declared devices
and independent recovery custody/hosted drill remain separate acceptance gates.

Additional native operational runner: `python3 admin/tests/operational_edges.py` creates synthetic paid dealer orders for Owner/Staff invalid collection and reschedule/cancel/rotation checks. It persists baselines and exact request bodies/results before each external call; use `--resume` only after inspecting its incomplete stage. Completed roles are skipped. Preserve duplicate-contact rejections and actual rate-limit intervals; never reset limits to run tests. `LoginRateLimitDeviceTest` is a separate gated negative check for an already-exhausted local fixture and must not be treated as a general startup test.

`financial_history.py` is read-only apart from real authentication/session bookkeeping. It verifies native report/expense periods and customer order-business-date versus payment-receipt-date histories and compares persisted financial/stock/customer state. Report generation time is intentionally excluded as response metadata. `named_credit.py` is a mutating native synthetic suite; inspect credit-state.json and actual matching sales before --resume, never blindly replay a credit save.

`backend_outage.py` is a mutating synthetic native suite: it validates PID ownership/argv before stopping only the local gateway, confirms no offline commit, restarts retained services, then verifies one recovered expense1299. It records outage-state.json before stopping and restores the gateway on failure. Inspect that state and actual expense before retrying. `pdf_cancel.py` is read-only and verifies actual DocumentsUI cancellation/rotation/no false acknowledgement with exact financial/inventory reconciliation.

Expired retained synthetic API fixtures: `admin/tests/renew_retained_test_keys.py` renews only already-expired24hour anon/service test keys with their same local signing secret and roles. It requires the owned marker/endpoints/database, valid old signatures, no active login quota windows, a private config archive and matching owned gateway PID. It never resets users, passwords, quota rows or databases. After a successful renewal, rebuild debug app/test APKs using only the renewed public anon configuration; old installed APKs have the old public key. Production key rotation is outside this helper. Inspect private expired-key-renewal-state.json before retrying an uncertain restart.

Operational completion: `operational_preview_cycle.py` and `reservation_shortage_native.py` use separate persisted intents and are now completed. The former verifies exact native form/preview/rotation and two original-date carryovers for both roles, then reconciles real partial/final actors/times and every preexisting row. Its narrow --resume-preview-reviewed is only for the documented original Ready/no-delivery failure, not general replay. The latter temporarily reduces ONLY the new owned fixture order reservation under test-admin SQL guards, proves native denial without protected changes, restores it before rotated retry and reconciles one final delivery/replay. No production count, adjustment endpoint or general reset is introduced. Inspect completed/private states before considering any future distinct fixture.
