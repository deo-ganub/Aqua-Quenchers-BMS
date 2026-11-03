# Aqua Quenchers work state

## Objective / scope / decisions
Complete native Java/XML Android29+ BMS, Supabase Auth/PostgreSQL/RLS and offline Python/local Java classifier defined by `/workspace/Aqua-Quenchers-BMS/handoff/CODEX_PROMPT.md` and five local originals/extracts. Continuous implementation/testing authorized. Production deployment, purchases and live-data changes remain deferred. Read AGENTS.md and REQUIREMENTS_TRACEABILITY.md; preserve user changes.
Only Owner/Staff; fixed35/30 pesos, whole gallons/integer centavos; Manila business dates separate from actual immutable receipts; backend active role/session/MFA and actor authority; atomic stock/reservations/payment/payload-hash idempotency. Classifier assists reviewed selection, never saves automatically. No fake saves/metrics/design fidelity. Financial exceptions need a concrete approved case/reviewed compensating migration; read-only preview exists.

## Current state
Branch `work`; implementation checkpoint HEAD81bf0c1. Current changes: real_services_gateway.ts, retry_and_expiry.py, FailureRecoveryDeviceTest.java, start_retained_local.py and this checkpoint. No unrelated source changes found; inspect git status before continuing. Production deferred; no push.

## Verified evidence (reuse)
- Local official GoTruev2.197.0 + PostgREST16.5 + real production Edge handlers over trusted HTTPS: real-auth-rest-http.log and real-services-provenance.json. Real JWT/forgery, MFA422/AAL2, Staff lifecycle/deactivation, permissions, exact financial/stock workflow PASS. Source-backed route works without blocked ECR registry.
- Connected native Owner/Staff workflows PASS run7f863325: login, manual expense/rotation, actual SAF PDF, MFA account creation, customers/orders/pickup/refill/partial-final delivery/payment/POS, own alias/password/logout. Independent ledger/stock/account/PDF reconciliation: android-connected-ui.log, pdf-connected-text.log. Prior failed viewer-return attempt retained its real synthetic expense; no history rewriting.
- Android recovery build PASS (assembleDebug/assembleDebugAndroidTest/testDebugUnitTest/lintDebug/:classifier-java:selfTest), private android-recovery-build.log. Prior7 unit/155Java assertions/lint0errors29warnings valid. API29 baseline3tests PASS; 16Python tests;4SQL migrations/14fixtures;7concurrency races; Node7/Deno3; age/tamper/retention/disposable restore all PASS in existing evidence. Restore so far used Auth stub; real Auth-schema recovery remains actionable.

## In Progress / unverified
Recovery test implementation compiles: one scoped real post-commit503; rotation retries same request/expense ID, real2-second Auth token/refresh, backend30-minute idle enforcement after controlled31-minute fixture timestamp. Not yet executed. First runner exited at missing emulator BEFORE controls armed or financial mutation.
Environment restart cleared ALL processes. start_retained_local.py restored retained PostgreSQL/Auth/shortAuth/REST/gateway; health PASS and second run PASS without resetting fixture. Software emulator booting session79985; private log /workspace/tooling/android-emulator-resume.log. No instrument run pending. Current code source unchanged after build except startup helper.

## Retained state / operations
Private synthetic config0600 /workspace/tooling/aq-auth-rest-local/private-config.json; never print credentials/tokens. Local database aq_http_fe979804, PostgreSQL55432, Auth55434, REST55435, HTTPS55436, shortAuth55437. Normal token expiry3600sec and idle30minutes unchanged. Local CA separate from server leaf; user trust only on owned debug AVD. No hosted operations pending. Controls directory currently empty; inspect before retries. HTTP fixture state completed; broad native password updated in private config. Do not blindly repeat these mutating suites.
Commands this continuation: Gradle recovery build PASS; retry_and_expiry.py exited before test (missing emulator); start_retained_local.py twice PASS. Relevant logs under private local directory; public completed evidence under evidence/.

## Concrete external blockers
Figma SECOND-page visual/component access; reviewed seven-category data/release model; declared Owner tablet/Staff phone/network for performance/full device acceptance; independent recovery custody/destinations/retention approvals and hosted drill. No invented design/model/benchmark/hosted results. Local Auth/REST no longer blocked. Full product acceptance not yet Verified.

## Ordered remaining work
1. Complete native recovery/expiry check; repair real failures, reconcile exact-once ledger, update traceability/evidence.
2. Verify real GoTrue-schema backup/isolated restore, permissions and session invalidation; preserve original local fixture.
3. Save tested retained service startup instructions in cloud draft, update stale docs/trace statuses and coherent local commit.
4. Complete independent remaining acceptance cases; record externally blocked subsets precisely.

Recovery attempt1 d0235821: native OK1test/completed, actual post-commit expense1234 saved exactly once and idle resume denial passed; host correctly FAILED refresh-count assertion0. Three-second wait insufficient to establish rejection by REST verifier. Preserved public android-failure-recovery-attempt1.log and private *.attempt1 logs; consumed fault/result retained. No pending controls. Wait extended35seconds (normal server policies unchanged), build72547 pending. Must use fresh baseline; keep first expense history.

Recovery attempt2 run4ee8f811 VERIFIED: native OK1test/completed; refresh_count2 to3600seconds; exact upstream committed/retry same ID/count1/ledger+1234; idle resume denial/fresh login; independent signed2-second token accepted then denied401 after35sec. Public android-failure-recovery.log and real-auth-expiry-retry.log. First attempt expense retained. Phone private fixture removed after PASS. Native runner79321 completed successfully. Only test timing changed, no server policy weakened.
Cloud start_skill draft saved revision successor to6; startup helper tested twice. Requires environment-settings review/save/publication to activate future snapshot; no network/membership edits or publication claimed. Current task: real Auth-schema isolated restore harness implemented, syntax PASS, runtime pending.

Real Auth restore36400 VERIFIED: actual Auth schema/users/factors + exact financial/stock snapshot, ownership/ACL/RLS; clone-only app-session revoke/Auth session cascade, old access403/refresh400; fresh real login/TOTP/AAL2 and protected expense totals. Source business snapshot unchanged; clone/services cleaned. evidence/real-auth-restore.log. No hosted/custody/RPO/RTO claim. Traceability updated with executed subsets; remaining scenarios In Progress rather than obsolete registry Blocked.

NEXT_ACTION: review/commit recovery tests and startup/docs/evidence; select next unexercised operational source variant (reschedule/short pickup/cancel/date/history), add targeted connected verification and continue.
