# Database sessions, privacy and recovery operations

This is the operating guide for the additive `202611070001_session_privacy.sql` migration. Changes are implemented and locally checked against synthetic fixtures. No hosted database migration, real customer erasure, stock initialization or account reset was performed by these checks.

## What the database now enforces

- Current active identity and a signed session identifier are required. A protected request explicitly registers/renews the session; ordinary browsing cannot silently extend its own deadline. A first-use token must be recent, and a session idle for 30 minutes cannot renew itself.
- Protected raw reads use RLS and the current session predicate. Financial RPCs retain their active/Owner checks. Role/status changes revoke existing sessions, so old sessions stay revoked after reactivation.
- `aq_logout` binds the session to its actor and records one protected logout event. The client separately attempts managed Auth logout while always clearing local memory. Remote failure is not proof of managed refresh revocation.
- New financial/operational retry keys bind to the complete typed request using SHA-256. Matching retries reuse the result; changed intent conflicts. Pre-migration keys lacking a hash fail closed and require reconciliation rather than a new duplicate.
- New order type/price is checked against the protected current customer. Historical prices/receipts remain unchanged. New orders cannot copy contact data that has been removed.

The client and identity handler must be deployed with the migration in a reviewed **development** rollout. Older clients do not call `aq_session_touch`. Apply migrations in lexical order; never rerun the base schema alone after hardening, because its older CREATE OR REPLACE functions/policies would replace the newer definitions.

## Approved contact removal

There is no customer-deactivation or erase-everything app screen. The designated administrator uses separate database authority after the operator/privacy lead has verified the request, approved eligible fields and considered accounting retention, holds and service obligations. Ordinary Owner/Staff and `service_role` cannot invoke private procedures.

1. Record a minimal customer UUID, new decision UUID, approval reference, reason and original cutoff. Keep names, phone/address and secret values out of the reason where unnecessary.
2. Review a dry run. Example placeholders must be replaced with approved identifiers:

```sql
select aq_private.erase_customer_contact(
 'CUSTOMER-UUID', 'DECISION-UUID', 'APPROVAL-REFERENCE', 'APPROVED-MINIMAL-REASON',
 false, 'ORIGINAL-TIMEZONE-AWARE-CUTOFF'::timestamptz);
```

3. Review active operations/unsettled balances. The procedure refuses removal while they remain. An approved cutoff is not permission to evade those guards.
4. After authorized review, use the **same identifiers and original cutoff** with `p_execute=true`.
5. Verify the receipt and independently export the decision ledger. A repeat matching decision returns its original receipt; changed approval/subject/reason/cutoff conflicts.

Eligible structured contact/address fields are set to NULL in customer/order copies and targeted audit/retry snapshots up to the cutoff. Names, financial history, references and original retry hashes remain. Later legitimately updated customer data and later copies are not removed by an older cutoff. New service requires complete contact reacquisition through the ordinary validated customer form and an appropriate processing basis.

The procedure does not erase arbitrary free text, exported PDFs, every backup, or physical media; these remain part of the authorized privacy process. Do not claim anonymity or physical/cryptographic erasure. Administrators control a sensitive procedure, not a new permission for the app.

## Independent ledger and restored copies

Install PostgreSQL client tools and configure approved private libpq service entries. Do not put connection strings or passwords in source/commands. Use a custodian-owned 0700 directory outside the repository. Export files are exclusive-created 0600; symlinks/hard links and oversized/invalid ledgers are rejected.

```sh
python3 tools/privacy_ledger.py export --service APPROVED_SOURCE_SERVICE --output /restricted/decisions.json
python3 tools/privacy_ledger.py replay --service APPROVED_RESTORE_SERVICE --ledger /restricted/decisions.json
```

Replay defaults to a dry run. Execute only after verifying the isolated restored target and its original-cutoff decisions:

```sh
python3 tools/privacy_ledger.py replay --service APPROVED_RESTORE_SERVICE --ledger /restricted/decisions.json --execute --confirm-database EXACT_ISOLATED_RESTORED_DATABASE
```

Replay uses one transaction and bound SQL parameters. A conflicting decision/unsettled restored record aborts the batch. Keep the ledger independently protected and newer than ordinary restore sets; do not rely only on a ledger inside the older backup being restored. Include current schema/migrations, data, required Auth/configuration/storage inventory and private decisions in the custody plan. Keep restored access closed until decisions are reapplied, sessions revoked, provider refresh credentials invalidated and required financial/identity reconciliation passes. The tool explicitly does not reopen access.

## Opening counts and account recovery

`aq_private.initialize_inventory(filled, empty, approval_ref)` permits nonnegative opening counts once, before orders/sales/payments and with a protected audit receipt. It does not add stock adjustment to the app. Only actual Owner-approved counts may be used; synthetic test counts are not business opening inventory.

`aq_private.revoke_actor_sessions(actor_uuid, approval_ref)` revokes application sessions during approved account recovery. Use the provider's supported managed recovery/revocation procedure as well, preserving the stable Auth ID and linked business records. Direct SQL password-hash replacement is not the recovery process.

## Executed local evidence

- `evidence/database-privacy-tests.log`: 14 tests in a new database, including RLS/roles/idle/revocation, changed/identical/concurrent retry, payment/price bounds, privacy copies/cutoffs/rights guards, independent restore replay and once-only inventory.
- `evidence/native-transport-tests.log`: 36 in-memory native assertions; delayed callbacks/refresh, exact retry intent, session denial, bounds and captured-identity logout. No account/network access.
- `evidence/identity-safety-tests.log`: 5 Deno input/byte/deadline/schema checks. `identity-typecheck.log` records handler type checking.
- `evidence/android-build.log`:actual APK/unit/lint build; physical-device and hosted behavior remain separate.

SQL fixture claims do not validate managed JWT signatures. Legal adoption, real retention, operational custody/monitoring and actual hosted/device verification remain external requirements. Independent Cloudflare audit phases are not closed by these implementation tests.

## Reproducing the isolated local tests in the prepared workspace

The dedicated fixture cluster is `/workspace/tooling/aq-algoias-isolated/data`, with private socket directory `/workspace/tooling/aq-algoias-isolated`, port 55472 and no TCP listener. It is separate from all earlier retained Auth/financial fixtures. On this prepared cloud, use PostgreSQL 17 binaries under `/workspace/tooling/sysroot/usr/lib/postgresql/17/bin` and library path `/workspace/tooling/sysroot/usr/lib/x86_64-linux-gnu`. Start only that fixture data directory with `pg_ctl -D ... -o '-k /workspace/tooling/aq-algoias-isolated -p 55472 -c listen_addresses=' start`; use its private server log. Do not point the test at another cluster. On a fresh environment, initialize a new private UTF8 fixture cluster first; do not reset an existing database.

Commands from the repository root:

```sh
python3 supabase/tests/test_database_privacy.py
python3 tools/test_native_transport.py
PYTHONPATH=classifier /workspace/tooling/aq-python/bin/python -B -m unittest discover -s classifier/tests
PYTHONPATH=classifier /workspace/tooling/aq-python/bin/python -B tools/test_benchmark_notebook.py
```

The native host runner uses the branch's declared JSON test JAR from `/workspace/tooling/aq-algoias-java/json.jar`. Use the prepared Deno CLI for the two identity test files. The actual-handler fixture permits only the three named synthetic Supabase environment variables and has no network permission. Full Android build instructions are in the repository README. Exact local commands/results are retained in WORK_STATE.md; no production credentials are part of these tests.
