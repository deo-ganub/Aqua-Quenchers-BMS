# DRAFT Privacy Policy — Aqua Quenchers BMS

**Version:** AQ-PRIV-DRAFT-0.2 · **Prepared:** 7 November 2026 (Asia/Manila)

**Status:** Draft for review by the operator, designated privacy lead and authorized academic reviewers; organizational adoption is pending.

**Effective date:** [Owner approval required]

## 1. Personal Information Controller and Scope

This draft covers the Aqua Quenchers Water Refilling Business Management System used by authorized Owner and Staff accounts for the station in General Mariano Alvarez, Cavite, Philippines.

- Personal information controller/legal operator: **[registered business name and authorized representative — confirm before adoption]**.
- Business address: **[complete approved address]**.
- Privacy lead/DPO or authorized contact: **[name or office, approved email/telephone and request channel]**.

The capstone developers support the system under the operator's instructions. Academic governance titles do not create additional application roles. The operator must confirm controller/processor responsibilities and access before real customer data is used.

## 2. Categories of Information and Processing Purposes

| Information | Necessary business purpose |
|---|---|
| Customer name, customer type, contact number and service address | Identify customers, apply the fixed permitted price and arrange collection/delivery |
| Orders, original and collected quantities, delivery progress, transaction references, balances and cash payment records | Fulfil orders, reconcile gallons and retain accurate commercial records |
| Expense date, category, amount, optional remarks and recorder | Record approved expenses and produce Owner financial summaries |
| Staff/Owner name, username, contact, role, active status and stable managed identity | Provide authorized accounts and record responsibility for business actions |
| Authentication/session and protected audit records | Verify access, revoke sessions, investigate failures and preserve transaction accountability |
| Approved protected exports and recovery copies | Restore records and meet the operator's approved continuity/retention obligations |

Managed authentication processes passwords and any enrolled authentication factors. Passwords, refresh credentials, factor setup secrets and privileged service keys must not be included in public reports, public images, source code or training data. The BMS does not require payment-card details, government IDs, birth dates, continuous location tracking or a public customer account for ordinary station service.

Protected transaction/audit history may contain necessary customer snapshots. It is restricted business information, not a public log. Optional expense remarks should omit unnecessary personal information.

## 3. Lawful Basis and Transparency

The operator must document the appropriate lawful basis under Republic Act No. 10173 and applicable rules for each actual purpose, including service fulfilment and applicable commercial/legal obligations. Collecting a notice acknowledgement is distinct from obtaining consent to an optional purpose.

The active release does not introduce a marketing program or customer registration portal. Optional marketing must remain disabled unless the operator separately approves the purpose, notice and genuine choice. Refusal or withdrawal of optional consent must not be presented as a request to erase records that must lawfully be retained for a different purpose. Optional-purpose processing requires a separately approved implementation and appropriate records of the applicable legal basis.

## 4. Authorized Access and Data Sharing

Active Owner and Staff accounts share the approved operational customer/order/payment work. Only the Owner may access expenses, financial reports and Staff-account administration. Cloud/database administration and approved recovery/privacy actions require separately designated administrator or custodian access.

Supabase is the selected managed authentication/database platform. The operator must approve the actual project region, provider arrangements and any cross-border processing before launch: **[project region and processor details to confirm]**. Restricted recovery storage and support access require named custodians and separate approval. Public GitHub repositories, presentation slides and academic reports must use code, redacted evidence and synthetic examples rather than customer records, private exports or access secrets.

## 5. Automated Classification Assistance

Expense category assistance is advisory. When an approved model is available, the intended production path performs inference locally in Java on the Android device. The Owner reviews and saves the final category; prediction cannot save an expense or change payments, stock or dates.

A reviewed station dataset and released model are not currently established by this draft. Offline training data must be reviewed, minimized and kept in controlled storage. Raw remarks and customer records must not be packaged in the APK or published as benchmark evidence. The research demonstration adapter is outside the intended production inference path.

## 6. Retention and Disposal

The Owner, privacy lead and accountant must approve the actual retention schedule before adoption. This draft does not invent a statutory retention duration or an automatic deletion commitment.

| Record class | Retention/disposal decision required |
|---|---|
| Current customer contact/address | Service necessity, correction/eligible-removal criteria and review interval |
| Orders, payments, expenses and commercial history | Applicable accounting/legal obligations and any legal hold |
| Account, authentication and audit records | Security purpose, minimum necessary evidence and authorized review/disposal |
| Training/benchmark material | Approved research purpose, minimization and experiment retention |
| Recovery copies | Approved copy age/expiry, independent protection and restore procedure |

The IAS target of seven verified daily recovery copies is a backup-rotation target, not an accounting-retention schedule. Recovery storage, scheduling, key custody and a hosted restore drill still need operational approval/verification. Eligible removals must be reapplied before a restored copy is reopened. Deleting a live record does not establish immediate erasure from every backup; immediate physical or cryptographic erasure is not promised.

## 7. Data Subject Rights and Request Procedures

Customers and account holders may use the approved privacy contact to ask about their information, request access/correction, or seek blocking/removal and other applicable rights. The designated reviewer verifies authority using the minimum information necessary, records the decision and separates eligible personal-field removal from commercial records that have a continuing lawful retention basis.

No password, authenticator secret or privileged key is required in an email/chat rights request. There is no ordinary-app erase-everything control. Requests, response periods, fees if lawfully applicable, refusals and escalation must follow applicable law and the operator's approved procedure. Individuals retain the right to raise concerns with the Philippine National Privacy Commission through its official channels.

## 8. Security Safeguards and Incident Management

The design uses managed authentication, current-role checks, restricted database grants, checked transactions, bounded inputs, protected transport and controlled audit/recovery procedures. Local verification is not certification of the future hosted configuration or actual device operation. Device locks, administrator MFA, real backup custody and release settings require their own evidence.

Suspected incidents must be escalated to the designated response lead for containment, evidence preservation and notification assessment. Where notification conditions apply, the controller must meet applicable NPC requirements, including the applicable 72-hour requirement; an ordinary application error alone is not proof of a notifiable breach.

## 9. Approval, Effectivity and Revision

Before publication, complete all bracketed operator/contact/region/retention details, review the actual deployment and approve the policy version. Give people the approved notice through the operator's real service channels. Material purpose, provider, retention or access changes require review and an updated notice as appropriate. This draft does not claim that a consent or policy-acceptance event has already been captured in the app.

## 10. Approval Record

| Approval responsibility | Required action | Status |
|---|---|---|
| Authorized operator | Confirm controller identity, purposes and service channels | Pending |
| Designated privacy lead | Review lawful bases, rights procedures and incident obligations | Pending |
| Authorized records/accounting reviewer | Approve retention and applicable holds | Pending |
| Authorized approving representative | Approve version and effective date | Pending |

Implementation and deployment evidence is assessed separately in the Security-Control Assessment. This policy establishes proposed governance obligations; adoption does not itself verify technical compliance.

Implementation reference: the approved-contact process is documented in [Database, Privacy and Recovery Operations](../security/DATABASE_PRIVACY_OPERATIONS.md). Its local synthetic verification does not adopt this policy or select the operator's retention periods.

Project acceptance: the requester approved the prepared project draft and continued implementation on 7 November 2026. See [authorization record](../PROJECT_APPROVALS.md). Actual controller/contact/retention/effective-date particulars and organizational-role attestations are not invented by this project acceptance.
