# Checklist of Recommended Security Controls

> Copied to `main` from branch `algo-ias` (revision 3e1b75a) on 21 November 2026 and updated for the controls added that day. Links to `evidence/` logs and to `com/example/aquaquencherspos` source files refer to the `algo-ias` branch.


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

## Update — 22 November 2026 (main branch)

- [x] Encrypt customer contact numbers and addresses in the database (AES-256 field encryption; keys in Supabase Vault, fail closed if missing; separate blind-index key). Locally verified (`backend/tests/ias_hardening.sql`); hosted Vault keys must be created before the migration runs.
- [x] Make the audit log append-only and tamper-evident (SHA-256 hash chain, no contact data in audit rows, Owner-only integrity check). Locally verified, including detection of a superuser edit.
- [x] Enforce the password policy (15+ characters, no common words or own name) in the app and for staff accounts on the server. The hosted Auth policy still has to be set for self-service changes (see AQ-SEC-04 F4).
- [x] Keep unsent saves encrypted on the device and send them later with the same request ID; never report them as saved early. Unit-tested; not yet tried on a device.
- [x] Restore every backup into a scratch database before keeping it, with an independent hash-chain check and an anchor from the previous backup. Locally verified; the private runner schedule is still to be configured by the custodian.
- [x] Resolve the sign-in source-key item from the Cloudflare-method audit and complete an independent review of the changed code ([AQ-SEC-04](../security/SECURITY_REVIEW_2026-11-22.md)).
- [x] Add weekly dependency advisory/update pull requests (Dependabot).
- [ ] Fill the operator's name, email, phone, street address and effective date in the Privacy Policy and Terms.
- [ ] Configure the private backup runner, recovery custodians and (if purchased) Supabase PITR; measure RPO/RTO in a hosted drill.
- [ ] Run the Android build and test the offline queue, Keystore and screens on the real tablet and phone.
- [ ] Third-party (outside) security audit; this project's reviews are internal.
