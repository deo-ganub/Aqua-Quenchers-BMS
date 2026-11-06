# Current Application Security and Integration Status

The original source assessment below concerns owner-module bf2b43e. The following implementation update applies to the subsequent algo-ias source; source-review observations are not represented as production certification.

| Area | Implemented update | Verification and remaining gate |
|---|---|---|
| Database idle/roles/revocation | New current-session/RLS checks, 30-minute expiry, bound logout and role/status revocation | 14isolated SQL tests; hosted signed-Auth/configuration still required |
| Retry/payment/price integrity | Full typed-intent SHA-256, exact replay/conflict handling, protected customer price, once-only opening counts | SQL positive/negative/concurrent/receipt tests pass; legacy retry keys require reconciliation |
| Privacy and restore | Administrator-only dry-run/approved contact removal, structured copy cleanup, independent ledger and cutoff-aware replay | SQL/CLI tests pass; real approval/retention/custody remains external |
| Native transport/lifecycle | Session generation and identity binding, single refresh, bounded HTTPS, exact intent, local cleanup and captured-identity remote logout | 36 in-memory assertions plus actual Android build; provider/device behavior separate |
| Native privacy | Secure activity/MFA windows, password/OTP saved-state protection and destroyed-login callback guard | Build/lint pass; physical-device behavior not newly observed |
| Identity handler | Bounded/deadlined strict requests/upstream responses, live session/current-profile recheck, safe errors and audit-failure reconciliation | 5input and 5 actual-handler fixture tests/typecheck pass; managed Auth/profile/audit creation is still multi-service, not an atomic distributed transaction |
| Classifier integration | Canonical Java engine wired into expense form; cached strict 2 MiB/digest/nonsynthetic asset gate and stale/manual/session guards | 155Java assertions,10app unit tests, APK/lint pass; actual approved model/tablet/real-data parity remain pending |

See [DATABASE_PRIVACY_OPERATIONS.md](DATABASE_PRIVACY_OPERATIONS.md), [benchmark guide](../academic/BENCHMARK_NOTEBOOK_GUIDE.md) and docs/REQUIREMENTS_TRACEABILITY.md for evidence. Independent Cloudflare audit completion, actual hosted deployment and legal adoption remain separate. Account-creation uncertainty still requires reviewed reconciliation; do not blindly create another identity.

---

## Historical source assessment (bf2b43e; superseded where updated above)

# Application Branch Security and Integration Assessment

**Document identifier:** AQ-SEC-02

**Edition:** 1.0 — Source Assessment

**Issue date:** 7 November 2026

**Assessed revision:** `bf2b43ea2ce0731ff145f5aad421979f9d3b6a7a`

## 1. Scope

The assessment concerns the owner-module application source inherited by `algo-ias`. The separate reference baseline `424c701` is not integrated by the documentation changes. Source observations below require independent behavioral verification before security acceptance.

## 2. Source-Reviewed Controls

The identity Edge handler verifies managed bearer identity and current active profile status. Account administration includes Owner and managed AAL2 checks. Protected SQL business operations use authorization checks, direct-write restrictions, row-level security and transactional financial audit records. Native session credentials are held in process memory, and the application manifest disables backup. A Supabase endpoint and publishable key constitute public client configuration rather than a privileged service credential.

## 3. Validation and Remediation Register

| Area | Source observation | Required verification or action |
|---|---|---|
| Asynchronous identity binding | Refresh/replay and MFA completion paths lack originating-session generation guards | Validate logout/account-switch sequences with delayed responses; bind completion to the originating identity and session |
| Logout and revocation | Local cleanup does not demonstrate provider session revocation | Verify remote invalidation, failed-network behavior and rejection of prior credentials |
| Inactivity | Active-profile SQL guard does not enforce the specified server idle period | Implement and verify server-side inactivity enforcement consistent with approved policy |
| Account administration audit | Audit recording is separate from managed identity mutation | Validate partial failures and establish accountable reconciliation without false success |
| Retry intent | Retry handling does not compare the complete changed payload intent | Verify identical retry behavior and conflict rejection for changed intent |
| Failure reporting | Ambiguous network failures may be described as no save | Use outcome-accurate feedback and deterministic reconciliation of unknown outcomes |
| Classifier integration | The application explicitly reports classifier unavailability | Integrate the reviewed advisory classifier, strict artifact checks and explicit Owner confirmation; preserve manual entry |

The register identifies source observations and engineering work. It does not assign exploit severity or claim a confirmed cross-principal attack without the applicable validation.

## 4. Algorithm and Operational Acceptance

The application branch does not currently provide the completed in-app classifier demonstration. Empirical evaluation additionally requires an Owner-reviewed anonymized corpus, an approved model and the declared physical tablet. Hosted Auth/grants, independent recovery custody, restoration and device controls remain unverified. Privacy and authorized-use documents require organizational adoption.

## 5. Source References

- [Application Java source](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/bf2b43ea2ce0731ff145f5aad421979f9d3b6a7a/app/src/main/java/com/example/aquaquencherspos)
- [Assessed source revision](https://github.com/deo-ganub/Aqua-Quenchers-BMS/tree/bf2b43ea2ce0731ff145f5aad421979f9d3b6a7a)
- [Reference implementation revision](https://github.com/deo-ganub/Aqua-Quenchers-BMS/tree/424c70112d22e37dccdef41e5471e283587c0726)
