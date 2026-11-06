# Aqua Quenchers Business Management System
## Timed Presentation Script: ALGOCOM and IAS

**Target duration:** 13 minutes, including navigation; retain 2 minutes before the 15-minute limit.

**Delivery:** Read the quoted passages. Bracketed directions and location tables are presenter instructions and are not spoken. Rehearse once with a timer and open the required tabs beforehand. The script presents the evidence currently available; independent audit completion and application-branch classifier integration remain outstanding.

### Presentation locations

| Tab | Material | Exact location |
|---|---|---|
| 1 | Latest ALGOCOM PBL milestone | [ALGOCOM Google Doc](https://docs.google.com/document/d/1ad4xmnPsoNZSpVCHALvLoQ5zGRYaHF0hEQ5UhTwF4_o/edit) |
| 2 | Latest IAS milestone | [IAS Google Doc](https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit) |
| 3 | Benchmark analysis and notebook | [Analysis](BENCHMARK_ANALYSIS.md); [notebook](Aqua_Quenchers_Benchmark.ipynb) |
| 4 | Algorithm reference implementation | [Python models](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier/aqua_classifier/math_models.py); [Java inference](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/424c70112d22e37dccdef41e5471e283587c0726/classifier-java/src/main/java/com/aquaquenchers/classifier/ExpenseCategoryClassifier.java) |
| 5 | Security assessment | [Cloudflare methodology/status](../security/README.md); [OWASP assessment](../security/OWASP_AGENTIC_SKILLS_TOP_10.md) |
| 6 | Draft policies | [Privacy Policy](../ias/DRAFT_PRIVACY_POLICY.md); [Terms and Conditions](../ias/DRAFT_TERMS_AND_CONDITIONS.md) |
| 7 | Security checklist and implementation evidence | [Control matrix](../ias/SECURITY_CONTROLS.md); [actual application-branch assessment](../security/OWNER_MODULE_REVIEW.md) |
| 8 | Application security code | [Owner-module SQL](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/supabase/migrations/20261105000000_owner_module.sql); [identity Edge handler](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/supabase/functions/identity/index.ts) |
| 9 | Consolidated formal submission | [Report source](AQUA_QUENCHERS_TECHNICAL_AND_COMPLIANCE_REPORT.md); Word/PDF copies supplied separately |

All relative links above resolve within the `algo-ias` documentation branch. Algorithm reference links deliberately identify revision `424c701`; that reference implementation has not been integrated into the owner-module application base. Keep the document tabs open before presenting. Download the PDF and Word report beforehand for offline reference; opening the live milestones still requires access to those documents.

### 0:00–0:30 — Introduction

[OPEN Tab 9: title page.]

> Good day. This presentation covers the Aqua Quenchers Business Management System for Algorithms and Complexity and Information Assurance and Security. I will discuss our latest milestones, algorithm methodology, benchmark procedure, security assessments, draft policies and implementation evidence. I will distinguish source implementation from results that still require testing or approval.

### 0:30–1:30 — Latest PBL milestones

[OPEN Tabs 1 and 2. Display each document title and its objectives or methodology section.]

> These are our latest ALGOCOM and IAS milestone documents. The system supports a water-refilling station's customers, orders, inventory, payments, expenses and financial reporting through Owner and Staff roles.
>
> ALGOCOM focuses on classifying expense remarks using TF-IDF and multiclass logistic regression, compared with weighted Multinomial Naive Bayes. IAS focuses on authentication, authorization, data protection, transaction integrity and recovery. Our formal portfolio and supporting documents are maintained on the algo-ias branch of the project repository.

### 1:30–3:00 — Benchmarking analysis

[OPEN Tab 3. Show the analysis's experimental controls, acceptance table and notebook execution switches.]

> The benchmark protocol separates predictive quality from computational performance. Reviewed examples are grouped to reduce leakage between training, validation and test partitions, with target proportions of sixty, twenty and twenty percent. Vocabulary and inverse-document-frequency values are fitted only on training data. Hyperparameters and suggestion thresholds are selected on validation data before the held-out evaluation is frozen.
>
> We report macro-F1, per-category recall, confusion matrices and suggestion coverage. The proposed quality gates are macro-F1 of at least zero point eight and recall of at least zero point six for every category.
>
> Training timing uses one warm-up process and five measured fresh processes, reporting the median, range and peak resident memory. Device evaluation requires one hundred warm-up inferences and one thousand measured inferences on the declared Owner tablet. Its proposed gates include a model no larger than two mebibytes, a ninety-fifth-percentile latency no greater than one hundred milliseconds and incremental memory no greater than thirty-two mebibytes.
>
> The notebook contains the reproducible procedure. It is unexecuted; station-quality and actual-tablet results remain pending reviewed data, an approved model and the physical device.

### 3:00–4:45 — Algorithm implementation demonstration

[OPEN Tab 4. In `math_models.py`, search for `class LogisticRegression`, then `class MultinomialNaiveBayes`. Open the Java inference class. Limit navigation to these three points.]

> This is the reference algorithm implementation in the same GitHub repository. Logistic regression learns the relationship between TF-IDF features and the seven expense categories. Softmax produces normalized class scores. Weighted Multinomial Naive Bayes provides a comparison using the same experimental controls.
>
> The Java implementation supplies local inference for the intended Android integration. Its result is advisory: the Owner must review the category, and category selection is separate from saving an expense. The intended controls include rejecting invalid model artifacts, handling unavailable or uncertain predictions and preventing stale suggestions from overriding a later manual choice.
>
> This demonstration is a source-code walkthrough. The algorithm reference exists in a separate baseline and is not yet integrated into the owner-module application underlying algo-ias. I therefore cannot present a completed live in-app classifier demonstration on this branch. The remaining work is integration, approved-model loading and verification with reviewed data on the target device.

[If an app is already prepared for a safe demonstration, show only its actual available behavior. The branch's `ExpenseClassifier.java` explicitly represents unavailability. Do not describe manual entry or an unavailable state as successful classification. Avoid installing, training or creating financial records during this short presentation.]

### 4:45–6:00 — Cloudflare security-audit skill

[OPEN Tab 5: Security Assessment Methodology and Status, sections 2–4.]

> We applied Cloudflare's security-audit workflow to the separate reference source using a pinned GitHub revision. The process included architecture reconnaissance, trust-boundary mapping, a coverage ledger and six subsystem source reviews.
>
> Seventy-seven coverage units were reviewed. Seventy-five were closed through source review without a retained candidate, while two were linked to one unresolved forwarded-header throttling concern. These numbers describe coverage; they are not seventy-seven passed penetration tests.
>
> The independent coverage and candidate-verification stages remain incomplete following an execution-resource limit. No confirmed exploit or comprehensive security certification is claimed. The unresolved item concerns whether the deployed gateway provides a trustworthy source address for aggregate login throttling; independent per-username limits remain present in the reference source.

[UPSTREAM SOURCE: https://github.com/cloudflare/security-audit-skill/tree/c1c8a8c1471069fb0e188eeaff69b8e8db6564a8. Show the link if asked; the project assessment is in `docs/security/README.md`.]

### 6:00–7:15 — OWASP Agentic Skills Top 10

[OPEN Tab 5: OWASP assessment matrix.]

> We used the official OWASP Agentic Skills Top 10 checklist for a manual applicability assessment of our AI-assisted development workflow. The ten areas cover malicious skills, supply-chain compromise, excessive privileges, insecure metadata, untrusted external instructions, weak isolation, update drift, poor scanning, governance and cross-platform reuse.
>
> Our assessment records safeguards such as pinned source revisions and documented scope, together with gaps in signed dependency verification, enforceable isolation and independent testing. This is a manual checklist assessment; it is not an automated OWASP scanner result or certification. The BMS does not contain an autonomous agent, so the agentic framework is applied to development practices rather than treating the expense classifier as a tool-executing agent.

[UPSTREAM SOURCE: https://github.com/OWASP/www-project-agentic-skills-top-10/blob/d6f7d7d0de314f52a83a85d1828e06ab096e595c/checklist.md.]

### 7:15–8:30 — Draft Privacy Policy

[OPEN Tab 6: Privacy Policy. Show sections 2, 6, 7 and the approval record.]

> Our draft Privacy Policy identifies the categories of information processed by the system, including customer contact details, transaction records, account information and protected audit history. It explains processing purposes, authorized access, sharing, retention, data subject rights and incident handling.
>
> Customer contact information and commercial records require different retention decisions. Removing unnecessary contact details does not automatically authorize deletion of financial history. Restored backups must also follow approved privacy decisions before access is reopened.
>
> The document remains a draft because the legal operator, privacy contact, lawful bases, retention schedule and effective date require organizational approval. It is a proposed governance document, not evidence that consent or legal adoption has already occurred.

### 8:30–9:30 — Draft Terms and Conditions

[OPEN Tab 6: Terms and Conditions. Show account security, financial records and connectivity/corrections.]

> The draft Terms and Conditions define authorized Owner and Staff use, individual account responsibilities, acceptable conduct and the handling of business records. They address approved pricing, whole-gallon quantities, monetary calculations and the need for an actual backend confirmation before a save is treated as completed.
>
> The terms also address uncertain network outcomes, controlled corrections, report handling and advisory expense suggestions. They retain draft status pending approval of the operator details, support channel, acceptance process and effective date. The required thirty-minute inactivity policy still needs verified enforcement on the assessed application branch.

### 9:30–10:45 — Recommended security-control checklist

[OPEN Tab 7: Control matrix. Point to the seven IAS ticket rows.]

> The security-control checklist links requirements to source locations, evidence and remaining verification. Its seven main areas are privileged-key protection; authorization and injection prevention; encrypted backups; independent recovery and monitoring; session and device protection; financial and inventory integrity; and classifier boundaries.
>
> We distinguish source-reviewed controls, locally verified evidence and blocked external checks. Several detailed links refer to the separate reference implementation. Their presence does not establish that the same controls are integrated or verified in owner-module. The application-branch assessment records those differences and the remaining engineering work.

### 10:45–12:30 — Implementation of security controls

[OPEN Tab 8: SQL migration. Search `require_active`, `require_owner` and `record_payment`. Then open the identity Edge handler and search `aal2`.]

> These links show security code in the application branch itself. The SQL functions check current account activity and Owner authority before protected operations. Financial operations use checked database functions with transaction logic and audit records. The identity handler verifies managed authentication, and account administration includes Owner and MFA-assurance checks.
>
> The application also keeps session credentials in memory and disables application backup. These source controls still require verification under the actual hosted configuration and device conditions.
>
> The branch assessment identifies outstanding work in asynchronous session binding, remote logout and revocation, server-side inactivity, account-operation reconciliation and retry-intent handling. We do not claim those items are already resolved. Our implementation evidence therefore consists of identified source controls and a clear validation register, rather than an unsupported statement that every recommended control has passed.

### 12:30–13:00 — Closing

[RETURN to Tab 9 or the academic portfolio index.]

> The repository contains our milestone references, benchmark protocol and notebook, algorithm source links, draft privacy policy, draft terms, security-control matrix and assessment records. Our remaining priorities are classifier integration, reviewed-data and device measurements, independent audit completion and organizational approval. Thank you.

### Questions: concise responses

- **Is the full Cloudflare audit finished?** No. Source review was performed; independent coverage and finding verification remain incomplete.
- **Did OWASP certify the application?** No. The official Agentic Skills checklist was applied manually to development-workflow risks.
- **Where are the benchmark results?** The protocol, implementation and unexecuted notebook are available. Reviewed-data and declared-device results have not been produced.
- **Can the classifier be demonstrated live on algo-ias?** Not yet. This branch's application has not integrated the separate classifier reference implementation.
- **Are the policies effective?** No. They are formal drafts pending authorized organizational adoption.

### Timing contingency

If navigation takes longer than expected, omit secondary code examples and summarize the matrix by its seven control areas. Preserve all required topics and the explicit evidence limitations. End the prepared material at 13 minutes to retain time before the 15-minute limit.
