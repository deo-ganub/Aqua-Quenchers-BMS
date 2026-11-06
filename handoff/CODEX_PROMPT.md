# Build the Aqua Quenchers Android Business Management System

You are implementing our real University of Makati capstone project, the Aqua Quenchers Water Refilling Business Management System. Work in https://github.com/deo-ganub/Aqua-Quenchers-BMS.git. Inspect the current checkout before making changes; do not assume the repository is still empty.

Build the working application and its supporting backend, tests and algorithm tooling. This is an implementation request, not a request for another proposal, a generic dashboard, a website, or disconnected mockups. Read this handoff and the supplied sources first, make a short execution plan, and begin implementation in the same session. Continue through the plan until the authorized work is complete or a concrete external blocker prevents a specific part. Complete independent work when another part is blocked.

## 1. Read the authoritative sources

This handoff contains five current project files in `sources/` and searchable extracts in `source_readable/`. Extracts help navigation; retain the original documents and spreadsheet as the source of their full structure and content.

- `Project_Charter_v3.docx`: scope and milestones.
- `Requirements_Engineering_v3.docx`: functional requirements A-01 through A-20 and nonfunctional requirements B-01 through B-06.
- `User_Story_v3.xlsx`: both Owner Module and Staff Module tabs, including Given-When-Then scenarios, generalized acceptance criteria, Sample Data and test status.
- `IAS_Academic_Paper.docx`: applicable security, integrity, privacy and recovery controls. Distinguish proposed controls and archived laboratory examples from implemented product behavior.
- `ALGOCOM_Academic_Paper.docx`: the expense classifier's mathematical specification, pseudocode, interfaces, evaluation protocol and proposed acceptance gates.

Use Figma's SECOND page, `HIGH FIDELITY • AquaQuenchers`, page ID `10004:1127`:
https://www.figma.com/design/37Zw2RVMoHdhJcDa5pE78q/AquaQuenchers?node-id=10004-1127

The main design sections are Owner `10229:2790` and Staff `10229:2791`. The expense suggestion entry is `10271:3638`. `figma_screen_index.json` lists the main sections. Inspect the actual screens, component states and prototype reactions with the available Figma tools; the index is not a visual export. Use the required Figma implementation skill if that environment provides one.

Live originals, for checking newer changes when accessible:
- Charter: https://docs.google.com/document/d/1kM_KUBL0ijj-HAh_JBOPiWK2-HZdZLa8/edit
- Requirements: https://docs.google.com/document/d/1a3KIxNpw0PZQF3HiTKzbCJy87YpKx5np/edit
- User stories: https://docs.google.com/spreadsheets/d/1aasIMufuI-J97s6HAgIj5bMzUgLZSfr0DFubb3U0zac/edit
- IAS: https://docs.google.com/document/d/1irULKgUcm4PbGIXDn3hNofR9M5Ou4VDE/edit
- ALGOCOM: https://docs.google.com/document/d/1ad4xmnPsoNZSpVCHALvLoQ5zGRYaHF0hEQ5UhTwF4_o/edit

Do not use older v2 attachments or the earlier backup-encryption ALGOCOM topic as the implementation baseline. For functional behavior use the current requirements and stories; for classifier details use ALGOCOM; for security use the applicable IAS controls; for appearance and sample states use Figma; for scheduling use the charter. The explicit rules below preserve the approved decisions. Report an actual source conflict without silently changing a financial rule, permission or category. Resolve ordinary technical choices yourself. Ask only for missing decisions or access that materially block implementation, bundling questions where possible. If Figma access is unavailable, request the needed exports and continue backend/domain work; do not claim a visual match without inspecting the design.

## 2. Platform and practical architecture

- Native Android application written in JAVA, developed in Android Studio, supporting Android 10/API 29 and above.
- Owner uses an Android tablet; Staff use Android phones. Figma's iPad Pro 11 and iPhone 16 Plus frames are design references, not a request for iOS/Swift applications.
- Backend is Supabase Free: managed Auth, PostgreSQL, RLS and checked database functions/RPCs. Use a protected server-side function for privileged account administration and username identity mapping as needed.
- Python is for offline classifier training, evaluation and export. Runtime prediction is local Java on Android. Production needs no Python inference server or external AI service.
- Prefer a straightforward Java/XML Android structure using reusable UI resources, ViewModels, repositories and a clear domain layer. Keep business invariants testable outside Activities/Fragments. Reuse any appropriate existing project structure.
- Use maintained Java-compatible networking and Android dependencies. Check official documentation for current compatible versions, then pin them. Do not migrate to Kotlin, Compose, Flutter, React Native, a WebView shell or a web application.
- Use the real backend for working saves. Provide clearly separated local development/test configuration and fixtures. Missing cloud credentials must not lead to a pretend successful save.

## 3. Roles, navigation and complete functional scope

There are only TWO application roles: Owner and Staff. IAS governance titles such as CEO, CIO, CISO and DPO are course/governance assignments, not additional application roles.

Owner navigation has exactly seven options: Dashboard, Orders, Customers, Payments, Expenses, Reports and Settings.
Staff navigation has exactly five options: Home, Orders, POS, Customers and Settings. Staff payment entry is accessible through its documented operational/Settings flow; do not add an Owner-style financial navigation bar.

Implement every story and its associated screens, valid paths and error states:

| IDs | Required behavior |
| --- | --- |
| A-01 / A-12 | Owner/Staff sign-in, validation, session handling and correct destination |
| A-02 | Owner dashboard, dated financial measures, stock counts, recent orders and documented shortcuts |
| A-03 / A-14 | Customer create, search, view and edit; orders, unpaid items, payment history and balance |
| A-04 / A-15 | Create and view orders, entered business date, fixed prices and valid status progression |
| A-05 / A-16 | Actual pickup, original expected quantity, shortfall, adjusted billable quantity and rescheduling |
| A-06 / A-17 | Partial/full delivery, reservation consumption and pending/carry-over quantities |
| A-07 / A-18 | Named Walk-in POS, optional saved customer, cash/change, credit confirmation and stock deduction |
| A-08 / A-19 | Payments against a selected order or Walk-in credit reference; payment status and history |
| A-09 | Owner expense list/filter/total, manual expense entry and reviewed classifier suggestions |
| A-10 | Owner consolidated financial summary, preview and matching PDF export |
| A-11 | Owner Settings: basic inventory, Staff accounts, account security and logout |
| A-13 | Staff Home: For Pickup, Ready for Delivery and pending/carry-over operational summaries |
| A-20 | Staff Settings, own permitted account edits/password change, Payments, Orders/Pending Gallons and logout |

Both roles can perform the documented shared customer, order, pickup, delivery, POS and payment operations. Staff cannot administer other users, create expenses, open Owner financial summaries or alter protected inventory totals. Staff access to a customer's order/payment/balance context is part of the operational workflow and must not be confused with Owner-only financial reporting.

## 4. Authentication and account security

- Sign in with Username and Password. Resolve a unique normalized username through a protected backend identity mapping to a stable Supabase Auth user ID. Never implement usernames by publicly exposing the account directory.
- Username edits preserve that Auth ID and every transaction link. Derive the recording account from the authenticated identity, not a client-supplied actor ID.
- Use Supabase-managed password hashing and token authentication. Do not store plaintext passwords or implement a separate student password-hashing service.
- Passwords require at least 15 characters and support long passphrases. Require reauthentication after 30 minutes of inactivity. Handle expiry, background/resume and logout properly.
- Active/Inactive status is enforced at the backend on protected reads/writes, including requests using an existing session after deactivation. Hiding a screen is insufficient.
- Owner Staff-account creation, editing and activation/deactivation require a valid managed MFA verification. Implement real verification, not a hardcoded code or trusted client boolean. Staff role is fixed for these accounts.
- Staff-account fields follow the sources: Full name, unique Username, 11-digit Contact number, Staff role, Active/Inactive and Temporary password where appropriate.
- Staff may update only their permitted own profile fields. They cannot promote themselves or change other accounts.
- Initial Owner provisioning and verified password recovery are controlled administrator procedures outside the ordinary app. Do not add public sign-up or an invented recovery screen.
- Never put service-role keys, database passwords, recovery keys or privileged credentials in Android resources, BuildConfig, the APK, source control or logs. The public client configuration is not a privileged secret; enforce actual permissions in the backend.

## 5. Money, quantities, dates and references

- Fixed prices: Household ₱35.00/gallon; Walk-in ₱35.00/gallon; Dealer ₱30.00/gallon. No editable price feature. Server chooses the authoritative price from the permitted customer/sale type.
- Quantities are positive whole numbers; reject fractional and nonnumeric values where a gallon quantity is required.
- Use exact money arithmetic: integer centavos or BigDecimal with a compatible exact PostgreSQL representation, not floating-point accumulation. Display pesos with two decimals.
- Generate unique order, sale, payment and expense identifiers/references as appropriate. Display references consistently with the design.
- Use Asia/Manila for business dates, reporting cutoffs and date presentation. Keep immutable server encoding timestamps separately from entered business dates.
- Both roles can enter an Order date, defaulted to today. Expenses use their entered date. Follow the documented date validation; do not invent extra date restrictions.
- POS completion and payment receipts use actual server-recorded dates/times. Do not backdate cash collection to an order's business date.
- Enforce uniqueness and validation in PostgreSQL as well as the client. Customer contact numbers require exactly 11 digits and must not duplicate another customer's number.

## 6. Orders and pickup

Normal lifecycle: Ordered -> For Pickup -> Picked Up -> Refilling -> Ready for Delivery -> Delivered. Enforce the documented transition graph at the backend, including the defined Rescheduled and Undelivered branches. Do not allow arbitrary skipped or backward status edits.

At pickup:
1. Display original expected gallons, actual collected quantity, shortfall, adjusted order quantity, fixed price and adjusted total.
2. Actual collection must be a positive whole number no greater than the original expected quantity.
3. Preserve the original request and shortfall. Before refilling, reduce the billable quantity and charge to actual collection.
4. Reject a reduced total below money already paid. Refer that case to the approved administrative correction procedure instead of discarding payment or creating a negative balance.
5. Save Picked Up and add the actual collected quantity to empty stock exactly once.
6. Rescheduling requires today or a future date in Asia/Manila. Return through the documented For Pickup path when collection resumes.

## 7. Stock reservations, refilling and delivery

Track empty stock, physical filled stock and per-order active filled reservations. Filled available = physical filled stock minus active reservations. Never confuse physical filled with stock available to POS.

- Moving a collected/refilling order to Ready for Delivery atomically decreases empty stock by the adjusted order quantity, increases physical filled stock by that quantity and reserves that same quantity for that order, once.
- Check nonnegative stock and reservation invariants inside the transaction. A repeated status save must not convert or reserve the quantity again.
- Each delivery consumes ONLY that order's reservation and decreases physical filled stock by the same delivered quantity, once. It cannot borrow another order's reservation or silently replace a missing reservation with unreserved stock.
- Reject nonpositive/nonwhole delivery quantities, overdelivery, insufficient physical stock and insufficient order reservation.
- Partial delivery sets Undelivered and retains its remaining reservation. Final delivery sets Delivered and clears the remaining pending quantity.
- Pending/carry-over gallons belong to the existing order. Delivery does not create another charge or change its adjusted order total.
- POS consumes only unreserved Filled available. Recheck and lock the authoritative stock when saving.
- Use database transactions, row locks, constraints and idempotency keys to handle simultaneous Staff/Owner operations. Reusing a request key with different payload must conflict; an identical retry must return the original result without duplicate stock, charge, receipt or audit effects.
- Initial stock is loaded through a controlled Owner-approved setup procedure. There is no manual stock-adjustment screen for either role.

## 8. POS, credit, payments and exceptional corrections

- Walk-in POS requires a customer name; a saved customer profile is optional. A profile-less named credit sale remains payable using its sale reference.
- Calculate total, cash tendered, change and remaining balance. Reject negative/nonnumeric cash. Cash covering a sale can exceed its total because change is returned; recognize only the retained payment amount, not the gross bill tendered.
- If payment is insufficient, require confirmation before recording credit. Some payment means Partially Paid; no payment means Unpaid. Saving the sale records its sale and initial payment/credit consistently and deducts unreserved stock once.
- Later payment is against a selected unpaid/partially paid order or Walk-in credit reference. It must be greater than zero and no greater than the remaining balance. Derive Paid/Partially Paid/Unpaid from recorded payments.
- Preserve immutable payment references, actual receipt timestamps and recorder identity. Do not implement cross-invoice FIFO allocation unless the approved sources are explicitly revised to request it.
- There is NO in-app void, cancellation, reversal, completed-transaction delete or arbitrary balance-edit feature. Owner application access does not authorize rewriting completed history.
- Exceptional corrections need Owner approval, a designated administrator, a reason, retained originals, linked compensating entries and payment/stock/reservation reconciliation. Provide a protected administrative runbook/tooling with dry-run support, not a new app menu. Keep this distinct from the explicitly allowed short-pickup adjustment before refilling.

## 9. Financial definitions and report behavior

Calculate these on the backend from authoritative records, not hardcoded Figma values:

- Revenue: full value of saved orders and completed Walk-in sales, including unpaid amounts, dated by order business date or completed sale date. Valid short pickup adjusts that same order on its original business date; preserve its original request/change history.
- Payments received: actual cash/payments retained on their actual receipt dates.
- Expenses: saved expenses on their entered expense dates.
- Net income: Payments received minus Expenses. Do not substitute Revenue minus Expenses.
- Outstanding balances as of End date: all charges dated on/before that date minus payments actually received on/before that date, including balances originating before Start date.

Reports use Start date and End date only. Reject an inverted range and keep the previous valid report. Preview and exported PDF must show the same station, period, generation date and five financial measures/calculation. Owner dashboard/report summaries must reconcile. Staff must not be able to fetch Owner aggregate financial data directly.

## 10. Owner expenses and human-confirmed suggestions

Add Expense contains Date, Category, Amount, optional Remarks, Suggest category, Recorded by, Cancel and Save Expense. Date/Category and a valid positive amount are required. Remarks have a 500-Unicode-code-point limit. Categories are exactly, in model label order:

Fuel, Electricity, Water, Maintenance, Supplies, Salary, Others.

- Manual category selection and saving work without remarks or a prediction.
- Suggest category uses the current remarks. Show the proposed category with Apply suggestion and Choose manually. Never apply it automatically or save an expense from a classifier response.
- Apply suggestion changes only the local Category selection. Preserve Date, Amount and Remarks and display the specified review message. Owner may override using any of the seven categories and must select Save Expense to persist.
- Editing remarks invalidates the old result. Discard stale asynchronous responses. Model failure, unknown vocabulary or uncertainty must preserve ordinary manual entry.
- Save the final Owner-confirmed category, authenticated recorder, selection source and accepted model version where applicable. Merely receiving a prediction is not acceptance. Corrections do not automatically retrain the model.
- Preserve category/date filters, newest-first records and filtered total. Classifier output can never modify amounts, dates, payments or stock.
- Staff cannot open the expense/suggestion screens or authorize expense writes.

Use the exact applicable messages from the sources. The classifier messages include:
- `Enter remarks to request a suggestion.`
- `Use 500 characters or fewer.`
- `No reliable suggestion. Choose a category manually.`
- `Category suggestion unavailable. Choose a category manually.`
- `Remarks changed. Request a new suggestion or choose a category manually.`
- `Suggestion applied. Review before saving.`
- `Expense saved.`

The Figma fixture uses 15 Oct 2026, ₱750.00 and `gasolina para sa motor`, with suggested Fuel. Its baseline seven expenses total ₱6,250.00; a single successful save produces eight records totaling ₱7,000.00. This is demo/test data, not production financial data. Score 0.86 and EXP-LR-v1 are illustrative internal fixtures, not measured model results or values to hardcode into the classifier.

## 11. ALGOCOM implementation

Primary algorithm: TF-IDF plus Multiclass Logistic Regression/softmax. Comparison: Multinomial Naive Bayes using identical features and data partitions. Implement the student TF-IDF, softmax loss/gradients/training and prediction logic and verify against maintained reference libraries. A library call alone is not the intended algorithm implementation. Keep the seven-class model; do not replace it with keyword rules, binary logistic regression, a cloud LLM or a different headline algorithm.

Follow the complete companion paper, including:
- Consistent Unicode NFKC/lowercase preprocessing; letter/number unigrams and adjacent bigrams; raw and normalized input at most 500 Unicode code points; at most 128 prediction tokens. Do not silently truncate.
- Training-only vocabulary, document frequency at least 2, at most 3,000 terms and deterministic ordering/ties.
- Raw term count multiplied by smoothed IDF `ln((1+N)/(1+df))+1`, followed by L2 normalization. Zero/unknown feature vectors require manual selection.
- Numerically stable seven-class softmax; mean cross-entropy plus L2 weight regularization; no bias regularization. Implement and check the gradients.
- Full-batch gradient descent with at most 500 epochs and 20-epoch validation patience; restore the best validation checkpoint. Proposed learning rates are 0.05, 0.10 and 0.30; proposed lambdas are 0.0001, 0.001 and 0.01. Tune only on validation.
- Train the weighted TF-IDF Multinomial Naive Bayes comparison with positive smoothing selected on validation. Report what was actually implemented.
- Export vocabulary, IDF, fixed labels, weights, biases, preprocessing/model versions and decision thresholds for pure Java inference. Validate dimensions, finite values, file bounds, label order and release provenance. Keep raw training examples out of the APK. A bare checksum alone does not authenticate an attacker-replaced package.
- Java inference runs on a background executor and matches Python golden vectors, with the paper's 1e-6 numerical tolerance where applicable.
- Return SUGGESTED, MANUAL_REQUIRED or UNAVAILABLE and the appropriate safe reason/nullable fields. Start with the paper's provisional top-score 0.70 and top-two gap 0.15, tune on validation and freeze before test evaluation. Scores are uncalibrated model outputs, not guaranteed probabilities.
- Implement the documented authenticated development API exercise/adapter when required by the milestone, using the same pure prediction contract. It is not a production dependency and cannot save expenses. Follow the strict schemas/status-specific rules and reject caller-supplied actor IDs, model paths, labels, URLs or weights.

Provide offline Python data-validation/training/evaluation/export commands, deterministic fixtures, Java tests and an Android prediction integration. Do not put Python training in the app or retrain on every save.

## 12. Real data and honest evaluation

The handoff does NOT supply a verified labeled training dataset, trained release model or real benchmark results. The app and pipeline must not pretend otherwise.

- Provide a documented labeling template and ingestion checks. Planning target: at least 50 distinct Owner-reviewed remarks per category, including English, Filipino, mixed text and ambiguous/unseen examples. This is a target, not collected evidence.
- Remove unnecessary personal information. Group exact/near duplicates, receipt-related remarks and template families before splitting. Target grouped train/validation/test proportions are 60/20/20 with seed 42; report actual counts and class support. Fit vocabulary/IDF only on training; tune only on validation; evaluate frozen models once on held-out test data.
- If data cannot support a sound split/model, report insufficient data and keep manual entry available. Synthetic fixtures may test math, integration and timing infrastructure, but must not be reported as real station accuracy. Do not generate a small fake dataset and call it the finished model.
- Compare both models on the same held-out set: macro-F1, accuracy, per-class precision/recall/F1/support, confusion matrix, suggestion coverage and error among offered suggestions. Separate classifier quality from the thresholded/manual-fallback policy.
- Record dataset/version/split manifests, source commit, environment, seed, actual epochs, model size and measurement conditions.
- Time five fresh training processes after warmup, reporting median/range and peak RSS. On the declared Owner tablet, run 100 inference warmups and 1,000 timed calls; report median/p95 latency and memory growth. Repeated timings are not additional independent accuracy examples.
- Proposed gates are macro-F1 >= 0.80, every category recall >= 0.60, Java p95 inference <= 100 ms, incremental memory <= 32 MiB and package <= 2 MiB. Freeze gates before final testing and report failures honestly. Do not lower them after looking at the test set or claim Naive Bayes must lose.
- If Owner data or a real device is unavailable, finish the code/tests/benchmark harness and list those exact external blockers. Mark the affected evidence Not Run or Synthetic Only. Do not replace missing production evidence with prototype numbers.

## 13. IAS controls and scope boundaries

Implement backend active-role authorization, least-privilege grants, RLS, strict validated input, parameterized queries, checked transaction functions, protected server-derived actor/time audit records, safe error responses, HTTPS validation and secure session handling. Test direct API/database access with Staff, Owner, inactive, expired/invalid and anonymous principals. Do not trust user-editable metadata for permissions. Harden any SECURITY DEFINER functions and grants.

Protect audit evidence against ordinary application edits/deletes. Do not market ordinary logs or a local checksum as immunity from privileged database tampering. Do not build a custom cryptographic logging platform unless the approved sources explicitly require it.

Privacy work includes minimized customer data, controlled access, minimal logs, a documented rights-request/retention process, restricted approved erasure records and restore handling. Financial/history retention and any eligible contact-field removal require the approved administrative process; do not add a customer-deactivation/erase-everything screen. The IAS example consent endpoint and subject-key cryptography exercise are course/reference material, not instructions to add a customer portal, marketing program or new encryption service.

Use applicable checks from Cloudflare's security-audit skill and the referenced OWASP skills if available. Read their actual instructions; record what checks really ran. Agentic development review does not mean the BMS should contain an autonomous AI agent. Archived IAS test counts are not proof that the new application passed those tests.

## 14. Availability and backup delivery

- Successful financial/operational saves require an available backend. Show failed/offline saves honestly and preserve inputs. Local classifier availability does not make backend saves offline-capable. Do not introduce an offline financial-write queue/conflict engine.
- Provide private scheduled backup automation/configuration and a restore runbook using reviewed authenticated-encryption tooling and separately controlled recovery keys. This remains IAS recovery work; it is not the main ALGOCOM algorithm.
- Target attempts: 06:17 and 18:17 Asia/Manila every day, including Sunday. Target retention: seven verified daily copies plus an independently protected encrypted cloud recovery copy. Owner tablet and Staff phones are not backup runners.
- Keep recovery storage restricted, separate keys/digest-catalog access and avoid committing dumps, credentials or recovery material to the repository or exposing them as public artifacts.
- Record copy age, failure monitoring and a manual recovery path. Check at opening/closing and manually export before the most recent successful copy exceeds 24 hours when scheduled attempts fail.
- Target RPO: 24 hours. Target RTO: eight operating hours, Monday-Saturday, 08:00-18:00 Asia/Manila. Scheduling may be delayed; these are measured targets, not guarantees.
- Inventory schema, data, relevant Auth mappings/roles/configuration and actual Storage objects. Verify an isolated restore and reconciliation, including applying any approved erasure ledger before reopening access. An encrypted dump file alone does not establish recoverability.
- Use the Free budget. Do not assume paid PITR/backups, enable paid overages, purchase services, deploy to production or alter live client data without separate authorization. Clearly mark unconfigured secrets, cloud destinations and unexecuted recovery drills.

## 15. UI fidelity and usability

Match Figma page two's fonts (Poppins and Open Sans), colors, spacing, soft corners, navigation, forms, cards, tables, dialogs, labels and important sample states. Inspect tokens/components rather than guessing a replacement theme. Translate pointer hover states into appropriate native focus/pressed behavior. Use the business design, not the university paper headers, student-author metadata or classroom governance titles.

Build real native inputs, selection controls, date controls, buttons, lists and dialogs. Include loading, empty, error, offline/failed-save, processing, validation, confirmation and success states. Every visible action needs its documented behavior. Handle keyboard/scroll, touch targets, Back/Cancel, screen resizing and state restoration. Reuse consistent resources/components and responsive tablet/phone layouts. A screenshot placed over the app is not an implementation.

Keep prototype seed data separate from live records and derived totals. Do not edit Figma or the academic sources merely to accommodate incorrect code. Document any genuine missing design state and use an existing visual pattern while preserving the approved flow.

## 16. Explicit exclusions

Do not add route optimization/maps/TSP/K-Means, demand/weather forecasting, customer credit-risk ML, denomination-selection algorithms, Messenger integration, a separate receivables module, notifications subsystem, extra roles, editable expense categories/prices, customer deactivation, manual inventory adjustment, public sign-up, Login History UI or Activity Log UI. Do not add in-app void/cancel/reversal/delete controls. The only approved product ML feature is the assistive expense classifier. Backend accountability and exception procedures still apply.

## 17. Implementation order and repository deliverables

Read any applicable AGENTS.md and preserve existing user work. Inspect the environment and existing code, then maintain a concise plan and continue implementation.

Suggested dependency order:
1. Source/requirement traceability, Figma screen inventory and shared UI resources.
2. Database schema/migrations, invariants, RLS/grants, bootstrap and checked transactional operations.
3. Auth/active roles and a working shared customer -> order -> pickup -> refilling -> reservation -> delivery slice.
4. POS, credit/payment, concurrency/idempotency and Owner financial calculations/PDF.
5. Owner/Staff settings, managed MFA administration and expense entry.
6. Classifier training/evaluation/export tools, Java inference, fallback and expense UI integration.
7. Visual comparison, complete integration/security tests, benchmark/recovery tooling and runnable handover documentation.

Deliver within the repository:
- A complete Gradle Android Studio project and wrapper, Java source, XML layouts/resources, reusable components and documented build configuration.
- Versioned Supabase migrations, least-privilege policies/functions, seed/controlled bootstrap tooling and configuration examples without secrets.
- Python algorithm tooling, Java inference/model schema, fixture/parity tests, dataset template, documented benchmark commands and actual reports when run.
- Automated domain/database/security/integration tests and Android UI/device tests appropriate to the critical workflows.
- A readable README with local and development-cloud setup, environment variables, Owner provisioning, synthetic demo setup, build/test commands, Android installation and common failure troubleshooting.
- Source-to-code-to-test traceability for every A-01..A-20 and B-01..B-06 requirement; map each story scenario/acceptance criterion to implementation and evidence. Keep test statuses truthful.
- An ERD and data dictionary consistent with the implemented schema, recovery/administrative runbooks, API-contract documentation where applicable and a concise known-limitations/blockers file.
- A debug APK when the environment can build it; never commit signing secrets or claim a build was run when the SDK/toolchain was unavailable.

Use clear names and explain necessary design choices in simple language. Avoid unnecessary frameworks and abstractions. Do not leave core flows as TODOs, placeholder screens or successful-looking mock network calls.

## 18. Verification and completion standard

Run appropriate build, lint and tests with the actual toolchain. Cover all supplied Given-When-Then scenarios and, at minimum:
- Correct role navigation, inactive/expired sessions, username edits preserving identity, Staff isolation and invalid MFA causing no account change.
- Fixed pricing/whole-number constraints; valid short pickup; overcollection; short-pickup total below prior payment; reschedule date limits.
- Exactly-once pickup/refilling/reservation/delivery/POS/payment effects; same-key/different-payload conflict; concurrent attempts to spend the last available stock or consume another order's reservation.
- Partial/final delivery and remaining reservations/carry-over, stock never negative, and POS unable to consume reserved stock.
- Paid/Partially Paid/Unpaid; zero/negative/over-balance payment rejection; POS change versus a later payment; profile-less Walk-in credit settlement by reference.
- Revenue versus receipts, payment dates versus order dates, prior-period outstanding balances, short-pickup adjustment, expenses and the exact net-income definition; report/preview/PDF reconciliation.
- Manual expenses, apply/override/stale suggestion, blank/500/501-character boundaries, excessive tokens, unknown text, invalid/unavailable model, label/package validation, numerical checks and Python/Java parity.
- Failed-network saves that never claim success and safe retries after uncertain responses.
- Representative Owner-tablet and Staff-phone visual comparisons with Figma, including dialogs and validation. Target screen loading within three seconds under declared normal test network conditions and report actual measurements.

Before declaring completion, inspect the changes, rerun affected checks after fixes and give a concise handover: what works, how to run it, tests actually passed/failed/not run, APK location if built, and exact remaining external setup/data/device requirements. Do not describe the system as fully implemented, trained, deployed or benchmarked while the corresponding work is unverified.

## 19. Milestones and starting instruction

Approved targets: data review/preprocessing November 13, 2026; model training/Java parity November 16; app functional/security and real benchmark testing November 20; working app/classifier presentation with retained evidence November 23. Production deployment and final handover are for the second semester. These dates organize the work; they are not claims that milestones have been completed.

START NOW. Read the checkout and sources, identify only genuine blockers, create the traceability/implementation plan, and implement the first working vertical slice. Keep proceeding through the remaining scope. Do not end the session merely by returning a plan or asking whether you should start coding.
