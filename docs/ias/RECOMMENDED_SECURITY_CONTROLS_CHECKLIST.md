# Checklist of Recommended Security Controls

**Document:** AQ-IAS-05 · **Reviewed:** 7 November 2026 · **Scope:** current algo-ias implementation

Checked boxes mean the stated local implementation check has passed; they do not mean production acceptance. Pending operational checks remain unchecked. Source links and evidence are in [Implementation of Security Controls](IMPLEMENTATION_OF_SECURITY_CONTROLS.md).

## Authentication and authorization

- [x] Bind protected operations to the current active identity and server-side role; verify denial and role/status revocation in isolated database tests.
- [x] Enforce the server-side 30-minute session idle rule and prevent expired/revoked sessions from renewing in local tests.
- [x] Keep retries bound to the original session/identity; reject stale native completions in host tests.
- [ ] Verify deployed managed Auth password policy, MFA, account recovery and actual provider session revocation using approved development accounts.
- [ ] Verify Owner/Staff boundaries and lifecycle behavior on the physical Xiaomi Pad 8 and supported Staff device.

## Transaction and inventory integrity

- [x] Verify current-customer pricing, bounded payments, concurrent/identical retries and changed-intent rejection in disposable database tests.
- [x] Verify approved once-only opening-stock initialization and retain protected transaction/audit history.
- [x] Verify truthful unknown-save handling and immutable retry requests in native transport tests.
- [ ] Complete stronger multi-service account-creation reconciliation and independently review the remaining security register; current uncertain outcomes are reported, not claimed successful.

## Privacy, transport and device controls

- [x] Provide draft Privacy Policy and Terms with explicit legal references and stated adoption gaps.
- [x] Verify administrator-only approved contact minimization, retained financial records and erasure-decision replay into a second disposable database.
- [x] Verify HTTPS request/response bounds and strict identity-handler input boundaries with host/mocked-provider tests.
- [x] Compile explicit Android backup/device-transfer exclusions, cleartext denial and secure-window/lifecycle controls; inspect the final APK for excluded private artifacts.
- [ ] Verify physical-device screenshot, lifecycle, lock and data-transfer behavior; compile checks alone are insufficient.
- [ ] Complete operator identity, privacy contact, purposes/bases, record-specific retention, provider arrangements and effective date before organizational adoption.

## Algorithm and public-artifact safeguards

- [x] Verify the classifier's strict model size/digest/approval gates and manual fallback using synthetic Java/Python tests and Android compilation.
- [x] Verify the notebook's synthetic workflow, guarded reviewed mode and once-only held-out evaluation; publish aggregate evidence instead of private remarks.
- [ ] Obtain Owner-reviewed anonymized data and an approved real model; measure actual quality, Java parity and Xiaomi Pad 8 performance.

## Recovery, incidents and independent assurance

- [x] Verify restricted local privacy-ledger export/dry-run/replay with protected files and exact target selection.
- [ ] Establish and monitor seven verified daily recovery copies and independent protection; verify key/copy custody and restore privacy decisions before reopening.
- [ ] Measure RPO 24 hours and RTO eight operating hours in an authorized hosted restore drill; these remain targets.
- [ ] Finalize incident contacts, breach-notification assessment procedure, monitoring and response rehearsal.
- [ ] Complete the independent Cloudflare skill audit phases. Current local implementation tests are not a completed full audit.
- [ ] Close applicable gaps from the manual OWASP Agentic Skills Top 10 assessment of AI-assisted development; do not describe it as automated certification.

## Evidence and governing references

[Implementation links and executed tests](IMPLEMENTATION_OF_SECURITY_CONTROLS.md) · [Privacy Policy and laws](DRAFT_PRIVACY_POLICY.md) · [Terms](DRAFT_TERMS_AND_CONDITIONS.md) · [full assessment](SECURITY_CONTROLS.md) · [traceability](../REQUIREMENTS_TRACEABILITY.md)
