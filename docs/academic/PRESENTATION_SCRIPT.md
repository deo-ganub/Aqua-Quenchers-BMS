# Aqua Quenchers Business Management System
## Final Student Presentation Script

**Prepared:** 7 November 2026. **Target duration:** 12–14 minutes, including switching files.

Read only the quoted paragraphs. The **Open** instructions are not spoken. Keep the tabs ready before starting. Use the recorded synthetic results below or finish the notebook's synthetic run beforehand; do not spend presentation time installing packages or waiting for training.

### Files to prepare

| Topic | Open this file |
|---|---|
| Latest ALGOCOM milestone | [ALGOCOM Google Doc](https://docs.google.com/document/d/1ad4xmnPsoNZSpVCHALvLoQ5zGRYaHF0hEQ5UhTwF4_o/edit) |
| Latest IAS milestone | [IAS Google Doc](https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit) |
| Benchmark procedure | [Benchmark Analysis](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/academic/BENCHMARK_ANALYSIS.md) |
| Notebook | [Open in Colab](https://colab.research.google.com/github/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/academic/Aqua_Quenchers_Benchmark.ipynb) or [view notebook](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/academic/Aqua_Quenchers_Benchmark.ipynb) |
| Actual recorded classroom results | [Synthetic execution evidence](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/evidence/notebook-synthetic-execution.json) |
| Algorithm code | [TF-IDF](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/classifier/aqua_classifier/text.py), [learning methods](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/classifier/aqua_classifier/math_models.py) and [Android expense flow](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/app/src/main/java/com/example/aquaquencherspos/ExpenseScreens.java) |
| Cloudflare review | [Assessment and status](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/security/README.md) |
| OWASP review | [Agentic Skills Top 10 assessment](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/security/OWASP_AGENTIC_SKILLS_TOP_10.md) |
| Privacy Policy | [Draft Privacy Policy](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_PRIVACY_POLICY.md) |
| Terms and Conditions | [Draft Terms](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/DRAFT_TERMS_AND_CONDITIONS.md) |
| Security checklist | [Security-Control Assessment](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/docs/ias/SECURITY_CONTROLS.md) |
| Current database/privacy code | [New migration](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/supabase/migrations/202611070001_session_privacy.sql) and [payment function in base migration](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/supabase/migrations/20261105000000_owner_module.sql) |
| Proof of database checks | [14 database/privacy tests](https://github.com/deo-ganub/Aqua-Quenchers-BMS/blob/algo-ias/evidence/database-privacy-tests.log) |

## 0:00–0:40 — Introduction

**Open:** the project repository or milestone title page.

> Good day. Our project is Aqua Quenchers, a business management system for a water-refilling station. It helps organize customers, orders, inventory, payments and expenses.
>
> Today, I will explain our ALGOCOM and IAS outputs, show the methods and code we use, and give examples of how they help the station. Our selected Owner tablet is the Xiaomi Pad 8.

## 0:40–1:20 — Latest PBL milestones

**Open:** the two milestone Google Docs. Show each title and its objectives.

> These are our latest milestone documents. For ALGOCOM, we focus on suggesting an expense category from the description entered by the Owner. For IAS, we focus on protecting accounts, customer information and business records.
>
> The documents set the plan; this presentation adds our latest implementation progress. The milestones guide our implementation. The supporting code, notebook and security documents are on the algo-ias branch of our GitHub repository.

## 1:20–3:10 — Algorithm implementation and demonstration

**Open:** `text.py` and find `class Tfidf`. Then open `math_models.py` and find `class LogisticRegression` and `class MultinomialNaiveBayes`. Show the Android expense-flow link briefly.

> Our expense classifier uses TF-IDF, multiclass logistic regression and a Naive Bayes comparison.
>
> TF-IDF turns words into numbers. It helps distinguish useful words in one expense description from words appearing in nearly every description.
>
> Our primary method is L2-regularized multiclass logistic regression, also called softmax regression. It learns patterns from these numbers and chooses among seven categories: Fuel, Electricity, Water, Maintenance, Supplies, Salary and Others. During learning, full-batch gradient descent adjusts the weights to reduce mistakes. L2 regularization discourages overly large weights. Softmax then turns category scores into values that can be compared.
>
> Weighted Multinomial Naive Bayes gives us another method to compare using the same data.
>
> For example, the intended use is to suggest Fuel for a description such as “fuel for delivery.” That illustrates the goal. The Owner still checks the suggested category before saving.
>
> The canonical Java engine is now connected to the Android expense form. Its loader checks the model file, and the form protects against applying an outdated suggestion after the remarks or manual choice changes. Manual category selection remains available; the loader accepts a separately approved station model.

**Demonstration direction:** show the code and actual synthetic notebook results. This is a source-and-notebook demonstration, not a claim that a reviewed model has already produced predictions on the Xiaomi Pad 8.

## 3:10–4:50 — Benchmarking analysis and Colab

**Open:** the notebook and saved synthetic execution evidence. Find `training_summary`, `quality_summary` and `timing_summary` in the evidence file.

> The benchmark checks three things: category quality, speed and memory use. We use grouped splits targeting sixty, twenty and twenty percent, with seed forty-two, for learning, choosing settings and final testing. Keeping related examples together can change the actual counts. It is similar to practicing questions and then taking a different exam.
>
> Our recorded classroom demonstration used forty-two made-up examples: twenty-eight for training, seven for validation and seven for final testing. Both methods correctly classified all seven test examples in that demonstration. The examples are deliberately easy, so this shows that the workflow runs; it is not a claim of perfect accuracy on station records.
>
> We also ran one warm-up and five measured preparation-and-training processes. Their recorded median time was about two point eight three seconds on the development computer. That is training time, not the tablet's response time.
>
> The notebook explains each step and displays the actual summaries. Final testing is protected from being repeated on the same experiment. Our next real-data evaluation uses reviewed expense descriptions, and the device measurements will be taken on the Xiaomi Pad 8.

## 4:50–5:50 — Cloudflare security-audit skill

**Open:** the Cloudflare assessment, sections 2 and 3.

> We used Cloudflare's security-audit skill to organize a source review. It helped us examine login, permissions, transactions, classifier inputs, recovery and build settings.
>
> For example, we looked for a path where a Staff account might reach an Owner-only action, rather than checking only whether an Owner button is hidden.
>
> The reference review covered seventy-seven items. That is a coverage count, not seventy-seven passed security tests. The independent verification stages remain open. Our newer implementation checks provide separate evidence of the controls we added.

## 5:50–6:40 — OWASP Agentic Skills Top 10

**Open:** the OWASP assessment table.

> We also used the official OWASP Agentic Skills Top 10 checklist to review risks when using AI tools during development. These include unsafe skills, untrusted instructions and giving a tool too much access.
>
> If an external document tells a tool to reveal a secret key, the document should not be treated as permission to do that. If a tool only needs to read code, it should not automatically be allowed to deploy the system.
>
> We applied the checklist manually and recorded the safeguards and remaining checks. Our expense classifier suggests categories; it does not independently operate the business or make financial decisions.

## 6:40–8:30 — Draft Privacy Policy and Philippine laws

**Open:** the Privacy Policy, sections 2, 3, 7 and 8. Keep the IAS milestone available for the detailed legal mapping.

> Our Privacy Policy is guided by Republic Act 10173, the Data Privacy Act of 2012.
>
> Section 11 covers general privacy principles. If the station collects a phone number and address for delivery, there should be a clear purpose, and we should avoid unrelated information.
>
> Section 12 concerns having a lawful reason for processing information. Using an address to fulfill an order and using it for optional marketing are different purposes that need separate consideration.
>
> Section 16 concerns the person's rights. For example, a customer can request a correction when their phone number is wrong. Section 20 connects to protecting information through appropriate safeguards. Section 21 means the business remains accountable even when it uses a service such as Supabase.
>
> We implemented an administrator-only process for approved contact removal. It can remove eligible contact details from the customer record and relevant copies while preserving required financial history. Privacy decisions can also be reapplied when an older database copy is restored.
>
> Our IAS discussion also considers RA 10175, the Cybercrime Prevention Act of 2012, particularly unauthorized access and interference with data. Permission checks and protected financial operations help address those risks. The policy remains a project-accepted draft with the business's operating particulars to be completed.

## 8:30–9:20 — Draft Terms and Conditions

**Open:** the Terms, sections 2–5.

> Our Terms and Conditions explain the responsibilities of the Owner and Staff. Users should protect their accounts, enter accurate information and work within their assigned permissions.
>
> For example, if a save times out, the user should check the original transaction before creating another one. A timeout does not automatically mean nothing was saved.
>
> The terms also explain controlled corrections, appropriate handling of reports and the Owner's responsibility to review expense suggestions. These rules support responsible use alongside the technical controls.

## 9:20–10:10 — Recommended security-control checklist

**Open:** the top current-implementation matrix in the Security-Control Assessment.

> Our checklist connects each security requirement to its implementation and evidence. It covers accounts, personal information, financial records, recovery and classifier safeguards. The milestone also requires encrypted backups, seven daily copies and an independent copy, targeting at most twenty-four hours of data loss and recovery within eight operating hours. Those operational targets still need verification.
>
> We distinguish current code from older reference material. We also distinguish a local test from a result observed on the hosted system or actual tablet. This helps us show exactly what each piece of evidence supports.

## 10:10–12:10 — Database and privacy controls in the code

**Open:** the new migration. Find `aq_session_touch`, `aq_logout` and `erase_customer_contact`. In the base migration, find `record_payment`.

> These are examples of controls implemented in the database.
>
> If a session has been idle for thirty minutes, it cannot renew itself through the session guard. If an account is deactivated, its existing application sessions are revoked. Logging out also identifies the correct session rather than relying only on returning to the login screen.
>
> If a payment is zero, negative or above the remaining balance, record_payment raises an error before recording it. Retrying the same request reuses its result, while changing the request under the same key creates a conflict. This helps prevent duplicate payments.
>
> For privacy, the contact-removal procedure requires separate administrator authority and an approved decision. A dry run allows the decision to be checked before execution. Active operations and unsettled balances block removal, and a later legitimate contact update is protected from an older removal cutoff.
>
> The password policy requires at least fifteen characters, and sensitive Staff-account changes use managed authenticator verification. We use SHA-256 fingerprints to check model bytes and bind new retry requests to their original content. A fingerprint checks a match; it is different from encryption.
>
> Fourteen local database and privacy tests passed. The Android build, unit tests, classifier checks and synthetic notebook workflow also passed. These checks used isolated or made-up data, and the hosted development rollout remains a separate step.

## 12:10–12:40 — Closing

**Open:** the notebook guide or repository index.

> Our current outputs include the algorithm code, runnable benchmark notebook, draft policies, security checklist and tested database and privacy controls. The Xiaomi Pad 8 is our selected tablet. Our next steps are the reviewed station-data evaluation, actual-device measurements and the approved hosted development rollout. Thank you.

---

## Quick reference — do not read this section aloud

| Term | Student meaning | Find it here |
|---|---|---|
| TF-IDF | Turning words into weighted numbers | `classifier/aqua_classifier/text.py`, `Tfidf` |
| Logistic regression | Learning weights to select a category | `classifier/aqua_classifier/math_models.py`, `LogisticRegression` |
| Full-batch gradient descent | Uses the whole training batch to calculate each weight update | Same file, `fit` and `loss_gradient` |
| L2 regularization | Discourages excessively large weights | Same file, `loss_gradient` |
| Softmax | Turning category scores into comparable values that add up to one | Same file, `softmax` |
| Naive Bayes | A comparison method using feature/category patterns | Same file, `MultinomialNaiveBayes` |
| Macro-F1 | A quality summary giving each category equal importance | Notebook final-results section |
| Recall | How many examples belonging to a category were found | Notebook per-category results |
| p95 | A time that approximately 95% of measured response times do not exceed | Notebook tablet section; actual measurement still required |
| Authentication | Checking identity | `supabase/functions/identity/index.ts` |
| Authorization | Checking allowed actions | Base SQL, `require_active` and `require_owner` |
| SHA-256 | A fingerprint of bytes or content; not encryption | Java `Model.java`; new SQL `idem_claim` |
| RLS | Database rules controlling which rows an account can access | Base and new SQL migrations |
| RA 10173 | Data Privacy Act of 2012 | Privacy Policy; IAS milestone legal mapping |
| RA 10175 | Cybercrime Prevention Act of 2012 | IAS milestone legal mapping |

Legal texts: [RA 10173](https://lawphil.net/statutes/repacts/ra2012/ra_10173_2012.html) and [RA 10175](https://lawphil.net/statutes/repacts/ra2012/ra_10175_2012.html). Relevant RA 10173 sections are 11, 12, 16, 20 and 21; relevant RA 10175 examples include section 4(a)(1), illegal access, and 4(a)(3), data interference. Safeguards address risks; an ordinary bug is not automatically a cybercrime or proof of noncompliance.

### Short answers if asked

- **What has been demonstrated?** The algorithm source path and a recorded, actually executed synthetic training/evaluation/timing workflow. The Android engine is connected, with approved-model safeguards.
- **Why does the classroom example score highly?** The small made-up descriptions are deliberately simple and contain category words. Real station quality needs different reviewed examples.
- **Has the Xiaomi Pad 8 been benchmarked?** It is selected and its preparation is recorded. Actual measurements need the connected tablet and approved model.
- **What remains in Cloudflare's audit?** The independent coverage and finding-verification stages. Local implementation tests are separate evidence.
- **Are the policies final operating rules?** They are accepted project drafts. Operator/contact/retention particulars and organizational adoption remain identified in the documents.

### Milestone details for questions

- Primary model: L2-regularized Multiclass Logistic Regression / Softmax Regression. TF-IDF is feature weighting; full-batch Gradient Descent is training optimization; Multinomial Naive Bayes is the comparison.
- Dataset split target: grouped 60/20/20, seed 42. Report actual counts after grouping, not invented exact proportions. The 42-row demonstration actually uses 28/7/7. At least 50 distinct Owner-reviewed examples per category is a planning target; the demonstration does not fulfill that target.
- Frozen acceptance targets: macro-F1>=0.80, each category recall>=0.60, model<=2MiB, Xiaomi Pad 8 p95<=100ms and incremental memory<=32MiB. Actual Android/process-memory evidence is separate from host training timing.
- Password/session policy: minimum 15 characters, 30-minute inactivity and managed MFA for Staff-account administration. Effective hosted configuration still needs its own observation.
- Recovery targets: seven verified daily copies plus independent protected recovery copy; RPO 24 hours and RTO eight operating hours (Monday–Saturday 08:00–18:00 Asia/Manila). These are targets, not measured production outcomes.
- Consent-endpoint and subject-key cryptography exercises in IAS are reference/course material; the active BMS does not require a new customer portal or marketing service.

### Presenter reminders

- Keep each code view to the named function/class; do not read every line.
- Show saved notebook output/evidence to keep the talk under 15 minutes.
- Describe approved-model and real-data/device gates once, without turning every explanation into a negative answer.
- Code and notebook links are on GitHub; this script's Word/PDF files are delivered directly in the conversation.


## Philippine law and draft-terms reference

For the exact law names, section numbers, project examples and verified-versus-pending controls, open [Laws and Sections](../ias/LAWS_AND_SECTIONS.md). The [Privacy Policy table](../ias/DRAFT_PRIVACY_POLICY.md#policy-and-legal-reference-table) and [Terms table](../ias/DRAFT_TERMS_AND_CONDITIONS.md#terms-and-conditions-at-a-glance) map each draft clause to its meaning and legal/project basis. Clause numbers in the Terms are not statutory section numbers.
