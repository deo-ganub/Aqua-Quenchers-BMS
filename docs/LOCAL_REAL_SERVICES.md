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
