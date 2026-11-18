# Known limitations and release blockers

Local functional verification is complete for152 of160 indexed story/acceptance entries; the remaining8 are externally Blocked in source-requirements.json. This is not full project acceptance or a production deployment. Passing evidence uses synthetic business data, official local GoTrue/PostgREST, production Edge handlers and API29 software emulation. No Figma fidelity, station classifier accuracy, physical-device performance or hosted recovery target is claimed.

## External prerequisites

- A01AC1/A12AC1: verified production administrator recovery channel, named custodian/identity-verification approval and provider-admin access. The outside-app procedure is documented; local identity/password/session controls pass. No production recovery performed.
- A09AC2/AC4/SCA/SCC: Owner-reviewed labeled station corpus, frozen grouped split and an approved trained/released model. Training/evaluation/export and local Java inference/controller tooling are implemented; synthetic mathematical/parity tests pass. Actual released-model apply/uncertainty/override/stale-result acceptance and station quality/performance remain unverified. Manual expenses, unavailable-model fallback and exact financial immutability pass.
- A11AC4/SCC: actual opening filled/empty counts and documented Owner approval/designated administrator custody.47/80 are illustrative. Controlled local initialization/inventory/MFA-account controls pass; no manual adjustment app screen or production seed is authorized by a fixture.
- Figma SECOND-page accessible authenticated screens/components/dialogs/prototype export. Supplied URL previously failed CONNECT403 and no connector is available. Implemented native controls/fonts do not prove a visual match; final icon/layout acceptance remains blocked.
- Actual Owner tablet/Staff phones and declared normal network for performance/usability/compatibility acceptance, including real model p95/heap/process memory and screen-loading targets. Software emulation is functional evidence only.
- Approved hosted development configuration/access, verified MFA/recovery custody, independent private backup destination/key custody, controller/accountant retention/privacy approval and hosted isolated restore/monitoring drill. Local signed Auth/TOTP/session invalidation/restore/retention/privacy tests pass; production RPO/RTO/legal or hosted equivalence are not established. Schedule and production deployment remain deferred.

## Runtime limits

Financial writes require the backend; there is no offline write queue. Tokens remain in memory and process death requires sign-in. Request identities survive saved-state recreation and retries. The APK built here uses public configuration for the owned local test endpoint, not a deployed station backend; service credentials/training remarks/test passwords never enter the app APK. Synthetic API-key fixtures expire after24hours; guarded renewal preserves users/signing secret/roles and requires expired login windows, then a public-config rebuild.

App/test build, unit tests and lint pass. Remaining nonfatal lint warnings concern programmatic text/localization, final design icon, overdraw and unused resources; they are not hidden by suppressions. Explicit allowBackup=false/explicit fullBackupContent exclusions and Android12 cloud/device extraction exclusions (including device-protected storage) protect backup policy across supported versions.

Completed mutating native controllers must not be rerun blindly. Their private intent/result files and public evidence distinguish initial failures, reviewed same-intent repair, verified completion and guarded restoration. See WORK_STATE.md for exact outcomes; correction execution still requires a concrete Owner-approved case and reviewed migration outside the app.

## 2026-11-14 paper re-audit (IAS and ALGOCOM, Nov 4 submissions)

- Resolved 2026-11-14 at the owner's request: the earlier prototype schema in `public` (13 tables, 3 views, 32 legacy functions including anonymous-executable `is_current_user_owner`) was dropped from the hosted project by `supabase/migrations/202611140001_close_legacy_public.sql`. Only the 11 reviewed aq_* RPCs remain in `public`. A full row and definition export was saved first under the git-ignored `handoff/legacy_public_backup_2026-11-14/`. After removal, legacy endpoints return 404, the app's access checks are unchanged and the Owner dashboard loads on the tablet.
- Verified live on 2026-11-14 with the real Staff account: anonymous `aq_query` 401; Staff `report`, `dashboard`, `expenses` and `staff` 403; Staff customers allowed; legacy `public.expenses` returns no rows to Staff.
- Fixed: the fixed-canvas inset code used Android 11 APIs while minSdk is 29 (Android 10, required by both papers); an API 29 fallback was added and lint is clean. Not run on an Android 10 device or emulator (none installed).
- Checked: debug APK contains no secret, service-role, management or Figma tokens. Classifier Python tests (16) pass on Python 3.13 with the pinned numpy/scipy/scikit-learn; app unit tests (7) pass. Python 3.15 has no wheel for the pinned numpy.

## 2026-11-14 paper alignment work

- ALGOCOM classifier: full pipeline run on a 420-row SYNTHETIC dataset (`evidence/classifier-synthetic-demo/`). LR macro-F1 0.608 vs NB 0.547 on held-out unseen subjects; both fail the 0.80 gate. The synthetic model ships only in debug builds with a visible demo note; release builds still require an Owner-reviewed model. Verified on the tablet: "gasolina para sa motor" -> Suggested: Fuel -> Apply suggestion selects Fuel; nothing saved. Real benchmark evidence still needs Owner-reviewed station remarks (target 50 per category) and the tablet latency/memory run.
- IAS Task 4 guardrails added: `.gitleaks.toml`, `.pre-commit-config.yaml`, `.githooks/pre-commit` (fails closed), `policies/gitleaks-release.json`, `policies/branch-rules.json`, `.github/workflows/security.yml` (secret-scan, security-evidence with APK inspection, model-and-risk). NOT RUN locally: downloading the Gitleaks binary was not permitted in this session; the hook is not enabled (`git config core.hooksPath .githooks`) because it blocks commits until gitleaks 8.30.1 is installed. GitHub branch ruleset not applied (needs repository admin).
