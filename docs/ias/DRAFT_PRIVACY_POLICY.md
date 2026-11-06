> Branch scope: this academic package is added to `algo-ias` based on `owner-module` at `bf2b43e`. Implementation links to `424c701` describe the separate `work` baseline; they do **not** prove those controls exist in `owner-module`. See [branch review](../security/OWNER_MODULE_REVIEW.md).

# DRAFT Privacy Policy — Aqua Quenchers BMS

**Version:** AQ-PRIV-DRAFT-0.1 · **Prepared:** 6 November 2026 (Asia/Manila)  
**Status:** For the Owner's, designated privacy lead's and academic review. Not an adopted policy or evidence of a production deployment.  
**Effective date:** [Owner approval required]

## 1. Who is responsible

This draft covers the Aqua Quenchers Water Refilling Business Management System used by authorized Owner and Staff accounts for the station in General Mariano Alvarez, Cavite, Philippines.

- Personal information controller/legal operator: **[registered business name and authorized representative — confirm before adoption]**.
- Business address: **[complete approved address]**.
- Privacy lead/DPO or authorized contact: **[name or office, approved email/telephone and request channel]**.

The capstone developers support the system under the operator's instructions. Academic governance titles do not create additional application roles. The operator must confirm controller/processor responsibilities and access before real customer data is used.

## 2. Information and purposes

| Information | Necessary business purpose |
|---|---|
| Customer name, customer type, contact number and service address | Identify customers, apply the fixed permitted price and arrange collection/delivery |
| Orders, original and collected quantities, delivery progress, transaction references, balances and cash payment records | Fulfil orders, reconcile gallons and retain accurate commercial records |
| Expense date, category, amount, optional remarks and recorder | Record approved expenses and produce Owner financial summaries |
| Staff/Owner name, username, contact, role, active status and stable managed identity | Provide authorized accounts and record responsibility for business actions |
| Authentication/session and protected audit records | Verify access, revoke sessions, investigate failures and preserve transaction accountability |
| Approved protected exports and recovery copies | Restore records and meet the operator's approved continuity/retention obligations |

Managed authentication processes passwords and any enrolled authentication factors. Passwords, refresh credentials, factor setup secrets and privileged service keys must not be included in public reports, screenshots, source code or training data. The BMS does not require payment-card details, government IDs, birth dates, continuous location tracking or a public customer account for ordinary station service.

Protected transaction/audit history may contain necessary customer snapshots. It is restricted business information, not a public log. Optional expense remarks should omit unnecessary personal information.

## 3. Lawful basis and notice

The operator must document the appropriate lawful basis under Republic Act No. 10173 and applicable rules for each actual purpose, including service fulfilment and applicable commercial/legal obligations. Collecting a notice acknowledgement is distinct from obtaining consent to an optional purpose.

The active release does not introduce a marketing program or customer registration portal. Optional marketing must remain disabled unless the operator separately approves the purpose, notice and genuine choice. Refusal or withdrawal of optional consent must not be presented as a request to erase records that must lawfully be retained for a different purpose. The IAS paper's sample consent endpoint is a course example, not a deployed BMS feature.

## 4. Access and sharing

Active Owner and Staff accounts share the approved operational customer/order/payment work. Only the Owner may access expenses, financial reports and Staff-account administration. Cloud/database administration and approved recovery/privacy actions require separately designated administrator or custodian access.

Supabase is the selected managed authentication/database platform. The operator must approve the actual project region, provider arrangements and any cross-border processing before launch: **[project region and processor details to confirm]**. Restricted recovery storage and support access require named custodians and separate approval. Public GitHub repositories, presentation slides and academic reports must use code, redacted evidence and synthetic examples rather than customer records, private exports or access secrets.

## 5. Automated assistance

Expense category assistance is advisory. When an approved model is available, the intended production path performs inference locally in Java on the Android device. The Owner reviews and saves the final category; prediction cannot save an expense or change payments, stock or dates.

A reviewed station dataset and released model are not currently established by this draft. Offline training data must be reviewed, minimized and kept in controlled storage. Raw remarks and customer records must not be packaged in the APK or published as benchmark evidence. The course-only development adapter is separate from the production inference path.

## 6. Retention and disposal

The Owner, privacy lead and accountant must approve the actual retention schedule before adoption. This draft does not invent a statutory retention duration or an automatic deletion commitment.

| Record class | Retention/disposal decision required |
|---|---|
| Current customer contact/address | Service necessity, correction/eligible-removal criteria and review interval |
| Orders, payments, expenses and commercial history | Applicable accounting/legal obligations and any legal hold |
| Account, authentication and audit records | Security purpose, minimum necessary evidence and authorized review/disposal |
| Training/benchmark material | Approved research purpose, minimization and experiment retention |
| Recovery copies | Approved copy age/expiry, independent protection and restore procedure |

The IAS target of seven verified daily recovery copies is a backup-rotation target, not an accounting-retention schedule. Recovery storage, scheduling, key custody and a hosted restore drill still need operational approval/verification. Eligible removals must be reapplied before a restored copy is reopened. Deleting a live record does not establish immediate erasure from every backup; immediate physical or cryptographic erasure is not promised.

## 7. Rights and requests

Customers and account holders may use the approved privacy contact to ask about their information, request access/correction, or seek blocking/removal and other applicable rights. The designated reviewer verifies authority using the minimum information necessary, records the decision and separates eligible personal-field removal from commercial records that have a continuing lawful retention basis.

No password, authenticator secret or privileged key is required in an email/chat rights request. There is no ordinary-app erase-everything control. Requests, response periods, fees if lawfully applicable, refusals and escalation must follow applicable law and the operator's approved procedure. Individuals retain the right to raise concerns with the Philippine National Privacy Commission through its official channels.

## 8. Protection and incidents

The design uses managed authentication, current-role checks, restricted database grants, checked transactions, bounded inputs, protected transport and controlled audit/recovery procedures. Local verification is not certification of the future hosted configuration or actual device operation. Device locks, administrator MFA, real backup custody and release settings require their own evidence.

Suspected incidents must be escalated to the designated response lead for containment, evidence preservation and notification assessment. Where notification conditions apply, the controller must meet applicable NPC requirements, including the applicable 72-hour requirement; an ordinary application error alone is not proof of a notifiable breach.

## 9. Adoption and changes

Before publication, complete all bracketed operator/contact/region/retention details, review the actual deployment and approve the policy version. Give people the approved notice through the operator's real service channels. Material purpose, provider, retention or access changes require review and an updated notice as appropriate. This draft does not claim that a consent or policy-acceptance event has already been captured in the app.

Source alignment: supplied IAS paper, sections on RA 10173/10175, privacy-by-design, AQ-SYSSP-01 and recovery; [source/readiness record](README.md). Operational procedures: [account security](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/admin/ACCOUNT_SECURITY.md), [privacy/corrections](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/admin/EXCEPTION_AND_PRIVACY_RUNBOOK.md), [recovery](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/admin/recovery/RUNBOOK.md).
