# Philippine Laws and Sections: Aqua Quenchers BMS

**Document:** AQ-IAS-04 · **Reviewed:** 7 November 2026 · **Branch:** algo-ias

This table makes the IAS milestone and presentation references explicit. Explanations are summaries, not statutory quotations. A safeguard supports an obligation; its presence does not establish complete legal compliance or prove that a criminal offense occurred. Read the linked law for its full conditions.

## 1. Data Privacy Act of 2012 — Republic Act No. 10173

| Exact section | Subject | Student explanation and BMS application | Policy clause / implementation and evidence |
|---|---|---|---|
| §11 | General data privacy principles | Be transparent, have a legitimate purpose, and collect proportionate, accurate information. Customer contact details support actual station service; unnecessary personal details should be excluded. | Privacy Policy §§2–3, 6; [privacy operations](../security/DATABASE_PRIVACY_OPERATIONS.md). Field minimization and retention decisions are distinct. |
| §12, particularly §12(b) where applicable | Criteria for lawful processing | Identify a valid basis for each purpose. Necessary order processing may use the contract basis when its conditions apply; optional consent is not a compulsory substitute for every other basis. | Privacy Policy §3; operator must document actual bases. No marketing service/customer portal added. |
| §16(a)–(d) | Information, access and correction rights | Explain processing and provide an authorized process for information, access and correction requests. | Privacy Policy §7; approved privacy contact and complete operating procedure remain to be supplied. |
| §16(e) | Blocking, removal or destruction | Review whether the legal conditions apply. An eligible request can remove unnecessary contact details while a lawful subset of commercial records is retained. | Privacy Policy §§6–7; administrator-only contact removal and restore replay; [14 local database/privacy tests](../../evidence/database-privacy-tests.log). This is not an erase-everything screen. |
| §16(f) | Indemnification | Qualifying harm from specified unlawful handling may give rise to indemnification. A draft term cannot waive applicable statutory rights. | Terms §7; complaints and remedies depend on the facts and applicable process. |
| §20(a)–(e) | Security of personal information | Apply reasonable organizational, physical and technical safeguards, including appropriate protection when a provider processes data. | Privacy Policy §§4, 8; current-role/session checks, restricted database operations and audit records. [Security assessment](SECURITY_CONTROLS.md); hosted/device safeguards still require verification. |
| §20(f); NPC Circular 16-03 | Personal data breach notification | Assess the actual incident and statutory notification conditions. Where mandatory notification applies, follow the applicable 72-hour rule and procedural requirements for notifying the NPC and affected people. Not every error automatically triggers notification. | Privacy Policy §8; privacy lead must document assessment and response. A local test is not a completed incident-response drill. |
| §21 | Accountability principle | The controller remains accountable for personal information, including when processing is subcontracted or transferred. Supabase does not take over all of the station's responsibilities. | Privacy Policy §§1, 4; confirm controller, provider arrangements and applicable transfer safeguards before adoption. |
| §26(a) | Access due to negligence: personal information | The specified negligent-access offense has its own elements and legal process; a software defect alone does not establish guilt. The milestone discusses this as conditional legal exposure. | Privacy Policy §8; incident review and legal assessment. No automatic penalty or finding of guilt is asserted. |

## 2. Cybercrime Prevention Act of 2012 — Republic Act No. 10175

| Exact section | Offense | Student explanation / project scenario | Terms clause and supporting response |
|---|---|---|---|
| §4(a)(1) | Illegal access | Accessing a computer system without right, such as trying to enter another person's authorized account. | Terms §§1–2, 5; managed login, active-role checks and restricted grants. |
| §4(a)(3) | Data interference | Intentionally or recklessly damaging, deleting or altering computer data without right, including maliciously changing order/payment records. | Terms §§3–5; checked transactions, bounded quantities/payments, protected audit history and recovery planning. |
| §4(a)(4) | System interference | Intentional interference without right that hinders or interferes with system functioning, such as malicious disruption of the order service. | Terms §§5, 7; bounded requests, deadlines, provider controls and continuity procedures. Hosted monitoring and recovery evidence remain outstanding. |
| §4(b)(2) | Computer-related fraud | Unauthorized data manipulation or interference causing damage with fraudulent intent, such as dishonest manipulation of payment records. All legal elements must be established. | Terms §§3–5; server-controlled actors/prices, payment limits and idempotent requests. Audit records support review but do not automatically prove fraud. |

## 3. Accounting retention and regulatory references

| Reference | Meaning for the project | Required distinction |
|---|---|---|
| NIRC §235, as amended by RA 11976 §33 | Covered books and accounting records generally have a five-year preservation rule, calculated according to the statutory filing rules. | The accountant must identify the covered BMS records and applicable holds. This does not mean retaining every customer contact field for five years. |
| NPC Circular 2022-01 | Provides a framework for administrative fines for covered infringements. | Administrative fines, criminal penalties and civil damages are separate; none is automatically established by a test failure. |
| IAS recovery targets | Seven verified daily copies plus an independently protected copy; RPO 24 hours; RTO eight operating hours, Monday–Saturday 08:00–18:00 Asia/Manila. | These are project recovery targets, not statutory retention periods or completed hosted recovery measurements. |

## 4. Primary legal and project sources

- [RA 10173 — Lawphil statutory text](https://lawphil.net/statutes/repacts/ra2012/ra_10173_2012.html); [NPC publication](https://privacy.gov.ph/data-privacy-act/)
- [RA 10175 — Lawphil statutory text](https://lawphil.net/statutes/repacts/ra2012/ra_10175_2012.html)
- [RA 11976, including §33 amending NIRC §235 — Lawphil](https://lawphil.net/statutes/repacts/ra2024/ra_11976_2024.html)
- [NPC Circular 16-03 — personal data breach management](https://privacy.gov.ph/wp-content/uploads/2016/12/NPC-Circular-16-03-Personal-Data-Breach-Management.pdf)
- [NPC Circular 2022-01 — administrative fines](https://privacy.gov.ph/wp-content/uploads/2022/08/NPC-Circular-2022-01.pdf)
- [IAS milestone](https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit)
- [Presentation script](../academic/PRESENTATION_SCRIPT.md), [Privacy Policy](DRAFT_PRIVACY_POLICY.md), [Terms and Conditions](DRAFT_TERMS_AND_CONDITIONS.md), [current traceability](../REQUIREMENTS_TRACEABILITY.md)

**Acceptance boundary:** source/local checks are recorded separately from hosted verification, organizational adoption, real-device results and independent security audit completion. No legal certification or executed agreement is claimed.
