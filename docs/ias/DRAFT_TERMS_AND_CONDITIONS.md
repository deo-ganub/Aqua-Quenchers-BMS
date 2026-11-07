# DRAFT Terms and Conditions — Aqua Quenchers BMS

**Version:** AQ-TERMS-DRAFT-0.3 · **Prepared:** 7 November 2026 (Asia/Manila)

**Drafting method:** Reviewed and tailored using the professor-recommended [App Privacy Policy Generator](https://github.com/nisrulz/app-privacy-policy-generator) source templates; see the [method and adaptation record](POLICY_GENERATOR_METHOD.md). Philippine-law and project-specific requirements are retained.

**Status:** Draft for organizational and academic review; adoption and effective date pending.

**Operator:** [registered business name, approved address and contact]

**Effective date:** [to be approved]

## Terms and Conditions at a glance

Clause numbers below refer to **this draft**, not to sections of a law. Legal references explain relevant obligations and risks; operational rules such as prices and timeout length are project decisions, not statutory requirements.

| Draft clause | User obligation in plain language | Relevant law / section or project basis | Implementation / remaining obligation |
|---|---|---|---|
| §1 Authorized users | Use only your own authorized account for assigned work. | RA 10175 §4(a)(1); approved Owner/Staff roles | Current active-role/session checks; operator assigns authority. |
| §2 Account security | Protect passwords and factors, lock devices and report loss. | RA 10173 §20; project minimum 15-character passphrase and 30-minute idle rule | Session controls tested locally; hosted Auth/MFA settings and actual tablet behavior need verification. |
| §3 Correct records | Enter accurate quantities/payments; do not falsify records or bypass prices. | RA 10173 §11(c); RA 10175 §§4(a)(3), 4(b)(2), where offense elements apply; approved financial rules | Checked transactions, backend prices and payment limits; [local database evidence](../../evidence/database-privacy-tests.log). |
| §4 Confirmation and corrections | A timeout is not a confirmed save; use the same request and controlled reconciliation. | Project integrity rule supporting RA 10173 §20 | Immutable intent/idempotency and honest unknown outcomes; hosted verification pending. |
| §5 Acceptable use | Respect Owner-only functions; do not publish records, attack services or run unauthorized tests. | RA 10173 §20; RA 10175 §§4(a)(1), 4(a)(3), 4(a)(4) | Permission checks and bounded transport; no authorization to probe unrelated systems. |
| §6 Privacy and classifier | Protect reports; use synthetic public examples; review category suggestions before saving. | RA 10173 §§11–12, 16, 20; human-review requirement | Local inference integration; approved real model/data and device results remain missing. |
| §7 Availability and custody | Follow outage procedures and protect backups; verify physical/accounting records. | RA 10173 §§20–21; project continuity targets | Seven daily copies plus independent protection, RPO 24h and RTO eight operating hours are targets; hosted drill/custody not verified. |
| §7 Preserved rights | These terms do not waive applicable legal rights or excuse negligent handling. | RA 10173 §16(f), where applicable | Draft wording; no executed acceptance agreement or legal certification claimed. |
| §8 Suspension and inquiries | Report problems through the approved channel; access can end when authority ends. | RA 10173 §§16, 20–21; operator access policy | Role/status revocation tested locally; supply actual support/privacy contact. |
| §9 Adoption | Complete operator details, effective date and acceptance process before operation under these terms. | Organizational governance; applicable Philippine law | Requester project acceptance is recorded; missing operational facts and role attestations remain explicit. |

See [Laws and Sections](LAWS_AND_SECTIONS.md) for full legal-reference tables and [the presentation script](../academic/PRESENTATION_SCRIPT.md) for the spoken explanation.

## 1. Purpose and authorized users

The BMS supports the station's customer, order, pickup/delivery, inventory, cash-sale, payment, expense and financial-report work. Access is for individually authorized Owner and Staff accounts. There is no public customer registration, public password-reset workflow or customer marketing program in the approved release.

The Owner approves business access. Shared account credentials, impersonation, access after authorization is withdrawn and use outside assigned business duties are prohibited. Application permissions are limited to the approved Owner and Staff roles.

## 2. Account security

Use the approved managed-authentication account, a passphrase of at least 15 characters and required authenticator verification. Keep passwords and factor/recovery secrets private; do not place them in GitHub, public images or ordinary support messages. Use an appropriate device lock and report lost devices or suspected account misuse through the approved contact.

The approved security policy requires reauthentication after 30 minutes of inactivity. Compliance must be verified for the deployed application revision. Backend current-role/active-status checks remain authoritative. Account recovery and lost-factor handling require the designated administrator's identity-verification procedure; knowing a username is insufficient. The operator must verify hosted Auth and recovery settings before production use.

## 3. Correct records and authority

Enter accurate, necessary information and check the selected customer/reference, business date, quantity, amount and final result before confirming work. Quantities are whole gallons; monetary calculations use centavos. The approved fixed rates are PHP 35 per gallon for Household/Walk-in and PHP 30 for Dealer orders; counter Walk-in POS remains PHP 35. The backend controls applicable prices, actors, receipt times, stock reservations and transaction references.

Revenue by order business date, cash received by actual receipt date, expenses by entered expense date and balances as of period end are different measures. Net income in this BMS is payments received minus expenses. A short pickup retains the original request/audit history and adjusts billable quantity under the permitted rules. Delivery progress and payment status are independent.

Do not fabricate collections/deliveries, alter prices or actor fields through a modified client, bypass reservations, overcollect, overpay or delete financial history. Opening inventory requires actual approved counts and designated administrator control. There is no ordinary-app manual-stock-adjustment or transaction-delete screen.

## 4. Connectivity, confirmation and corrections

Financial writes require a reachable backend. A preview, spinner, prediction or network timeout is not confirmation that a record was saved. When an outcome is unknown, preserve the original request/reference and follow the safe retry/reconciliation procedure; do not create a fresh duplicate transaction to guess the result.

An authorized correction must follow the controlled review process with the affected reference, reason, quantities/amounts/dates and approval. Completed receipts and protected audit evidence are retained as required. Report discrepancies to the designated operator instead of editing the database or concealing an error.

## 5. Permissions and acceptable use

Owner and Staff may perform the approved shared operational work. Expenses, Owner financial reports and Staff-account administration remain Owner-only; account administration requires managed MFA. Staff may edit permitted own-profile details and use the documented order/Walk-in credit payment flows.

Do not distribute credentials, publish personal/financial data, introduce malicious files or models, attempt unauthorized access, deliberately disrupt service or use administrative export/recovery tools without authority. Security tests use isolated synthetic fixtures and approved scope; the academic exercise does not authorize probing other people's or production systems.

## 6. Privacy, reports and assisted categories

Handle customer/account/transaction information according to the approved [Privacy Policy](DRAFT_PRIVACY_POLICY.md) and retention instructions. Export PDFs only to authorized destinations and protect downloaded/shared files. Academic presentations and public repositories must use synthetic or appropriately redacted examples.

Category suggestions, when an approved release model is supplied, assist the Owner and require review. An uncertain/unavailable suggestion does not authorize a category or a save. Manual expense entry remains available. No accuracy, device-speed or production-recovery target is asserted merely because code or a synthetic test exists.

## 7. Availability, custody and business responsibilities

The operator approves device/network readiness, administrator/recovery contacts, initial stock, retention decisions and independent backup/key custody. Backup and restoration targets require actual monitored exports and a verified drill. Follow the approved continuity record and later reconciliation procedure during outages; do not simulate a successful backend save.

The system's records and reports do not replace physical stock checks, accounting review, water-quality testing or applicable business obligations. Maintenance, limitations and changes must be communicated through the operator's approved channels. This draft does not waive non-waivable rights or excuse unauthorized or negligent handling of records.

## 8. Suspension, inquiries and adoption

The authorized operator may deactivate an account or restrict work when authority ends, a device is lost, misuse is suspected or containment is necessary, subject to applicable obligations. Session revocation and business-history retention follow the documented procedures.

Support/privacy/security contact: **[approved channel required]**. Applicable Philippine law governs to the extent appropriate to the approved operator arrangement; any dispute/complaint process and contract wording require review before adoption.

Complete operator/contact/effective-date fields and obtain the designated approval before using these terms. Acceptance procedures shall be approved and implemented before these terms take effect; this draft records no executed acceptance agreement.

## 9. Approval Record

| Approval responsibility | Required action | Status |
|---|---|---|
| Authorized operator | Confirm service scope, account responsibilities and support channel | Pending |
| Designated privacy or legal reviewer | Review applicable obligations and policy consistency | Pending |
| Authorized approving representative | Approve version, acceptance process and effective date | Pending |

Project acceptance: the requester approved the prepared project draft and continued implementation on 7 November 2026. See [authorization record](../PROJECT_APPROVALS.md). Actual controller/contact/retention/effective-date particulars and organizational-role attestations are not invented by this project acceptance.


## 10. Third-party services, updates and document changes

The application uses Supabase for managed authentication and database services. Review the provider's [Privacy Policy](https://supabase.com/privacy) and [Terms of Service](https://supabase.com/terms); the operator must confirm the arrangements applicable to the actual project. Using a provider does not remove the operator's accountability under RA 10173, Section 21.

The operator shall communicate supported application versions, required security updates and material changes to these terms through approved staff channels. Record the approved version and effective date, explain material changes and complete the approved acceptance procedure before the revised terms take effect. Updates do not authorize deletion of required business records or waiver of applicable rights. Operator/support contact remains the approved channel specified in Clause 8.
