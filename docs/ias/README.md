# Information Assurance and Security Documentation

## IAS submission — the four required deliverables

These files are on **algo-ias**. If GitHub opens another branch, select `algo-ias` before browsing. The links below open the requested files directly.

| Professor's requirement | Open the deliverable | What it contains |
|---|---|---|
| DRAFT Privacy Policy | [DRAFT Privacy Policy](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_PRIVACY_POLICY.md) | Information collected, purposes, rights, retention, security and exact Philippine laws/sections inside the policy |
| DRAFT Terms and Conditions | [DRAFT Terms and Conditions](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_TERMS_AND_CONDITIONS.md) | Account responsibilities, authorized use, records, privacy, provider services and adoption |
| Checklist of Recommended Security Controls | [Security Controls Checklist](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/RECOMMENDED_SECURITY_CONTROLS_CHECKLIST.md) | Explicit checkboxes distinguishing locally verified work and outstanding acceptance requirements |
| Implementation of Security Controls | [Security Implementation and Code Links](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/IMPLEMENTATION_OF_SECURITY_CONTROLS.md) | Direct app/database/Edge source links, executed test evidence and remaining verification |

**Professor-recommended generator:** [nisrulz/app-privacy-policy-generator](https://github.com/nisrulz/app-privacy-policy-generator). [Drafting method and adaptations](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/POLICY_GENERATOR_METHOD.md) records the reviewed source templates and app-specific revisions. Policies remain drafts; checked local tests do not establish production/legal certification.


**Document identifier:** AQ-IAS-REG-01

**Edition:** 1.0

**Issue date:** 7 November 2026

**System:** Aqua Quenchers Business Management System

## 1. Purpose

This document registers the privacy, authorized-use and security-control materials supporting the Information Assurance and Security component of the project. The documentation addresses the approved native Java/XML Android and Supabase architecture and the privacy, access-control, transaction-integrity and continuity requirements specified in the academic milestones.

## 2. Document register

| Identifier | Document | Classification |
|---|---|---|
| AQ-IAS-01 | [Privacy Policy](DRAFT_PRIVACY_POLICY.md) | Draft policy for organizational review and adoption |
| AQ-IAS-02 | [Terms and Conditions](DRAFT_TERMS_AND_CONDITIONS.md) | Draft terms for organizational review and adoption |
| AQ-IAS-03 | [Security-Control Assessment](SECURITY_CONTROLS.md) | Technical assessment with revision-specific evidence |
| AQ-SEC-01 | [Security Assessment Methodology and Status](../security/README.md) | Interim assessment; independent verification outstanding |
| AQ-SEC-02 | [Application Branch Assessment](../security/OWNER_MODULE_REVIEW.md) | Source assessment of the owner-module application revision |
| AQ-SEC-03 | [OWASP Agentic Skills Assessment](../security/OWASP_AGENTIC_SKILLS_TOP_10.md) | Manual applicability and gap assessment |

## 3. Source authority and provenance

| Academic source | Reviewed snapshot SHA-256 | Application |
|---|---|---|
| [IAS milestone](https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit) | `fc9102539e518cf3dad726fe2a120d2f502ff4a647fe7ec43c265eb1d571a088` | Privacy, least privilege, authentication, device sessions, transaction integrity and recovery |
| [ALGOCOM milestone](https://docs.google.com/document/d/1ad4xmnPsoNZSpVCHALvLoQ5zGRYaHF0hEQ5UhTwF4_o/edit) | `4d976caa8b54eb1a1b2bec6ea5a89e9eec7f4328321615c50591a9f188c8c14f` | Expense-classification algorithm, experimental methodology and benchmark criteria |

The identifiers correspond to read-only text snapshots retrieved on 6 November 2026. Subsequent amendments to the live documents require reconciliation. Private originals and personal academic information are excluded from public repository artifacts. Proposed controls, historical laboratory observations and illustrative examples are distinguished from current implementation evidence.

## 4. Technical scope and revision control

The application under review is the `owner-module` revision `bf2b43ea2ce0731ff145f5aad421979f9d3b6a7a`, from which the `algo-ias` documentation branch was derived. A separate reference implementation exists at revision `424c70112d22e37dccdef41e5471e283587c0726`. The extended source review included local revision `de7a03b`, comprising that reference baseline and an unpublished login-layout change. Controls and tests from these revisions are attributed explicitly; their presence in a reference implementation does not establish integration into the assessed application branch.

The architecture uses native Android components, managed Supabase authentication and checked PostgreSQL operations. Persistence of authentication tokens would require an approved Android Keystore design. Protected transactional audit records do not establish cryptographic protection against a privileged database administrator. The expense classifier supplies advisory categories and does not exercise autonomous tool or financial authority.

Current `algo-ias` implementation has advanced beyond that historical assessment baseline: local database/privacy, native transport and classifier integration evidence is recorded in [current traceability](../REQUIREMENTS_TRACEABILITY.md). Historical assessment findings are not automatically current hosted verification.

## 5. Approval requirements

Organizational adoption requires confirmation of the legal operator, privacy contact, lawful processing bases, retention schedule and provider arrangements. Operational acceptance additionally requires effective hosted security configuration, independent recovery-copy and key custody, an isolated restoration exercise and device-based verification. Algorithm acceptance requires reviewed anonymized data, an approved model and measurements on the declared physical device. Production deployment remains deferred.

## Presentation and legal-reference tables

- [Laws and exact sections](LAWS_AND_SECTIONS.md): RA 10173, RA 10175, notification and retention references.
- [Privacy Policy clause table](DRAFT_PRIVACY_POLICY.md#policy-and-legal-reference-table).
- [Terms and Conditions clause table](DRAFT_TERMS_AND_CONDITIONS.md#terms-and-conditions-at-a-glance).
- [Student presentation script](../academic/PRESENTATION_SCRIPT.md), aligned with milestone methods and current evidence.
