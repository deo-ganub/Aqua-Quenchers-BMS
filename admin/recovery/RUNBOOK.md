# Recovery operations (development only until separately approved)

Use Supabase Free. No production deployment, paid backups/PITR or live-data changes are authorized. The recovery tooling uses the reviewed `age` CLI and independently controlled recipient identities; keys and dumps never belong in source, APKs, Actions artifacts or chat.

A designated custodian exports a complete development bundle into a restricted directory (0700). Use libpq service/credential files, never put database URLs/passwords in commands or logs:

```
pg_dump --format=custom --file=/restricted/bundle/database.dump
pg_dumpall --roles-only --no-role-passwords --file=/restricted/bundle/roles.sql
```

Inventory roles, Auth users/identity mappings/factors/configuration, schema/data, Supabase project configuration and actual Storage object bytes/metadata. `pg_dump` must include `auth`, `storage` metadata and `aq_private` as authorized; inspect its table list and privileges rather than assuming the connection can export them. Preserve separately controlled credentials needed to recreate managed services. Never restore managed Auth/roles directly over a live Supabase project.

The bundle requires `project-config.json`, `auth-config.json` (nonsecret configuration or secure recovery selectors), `storage-inventory.json` with explicit boolean `uses_storage`; if true include actual `storage-objects/`. Inventory every bucket/object checksum. A database dump alone is insufficient. The custodian reviews completeness and excludes unnecessary exports/PII.

```
python3 admin/recovery/backup.py package --bundle /restricted/bundle --recipients /restricted/public-recipients.txt --primary /restricted/primary --recovery /independent/recovery --catalog /separate/catalog
```

All destination directories must be 0700 and disjoint. Mount restricted independently protected cloud storage for recovery; a second local directory does not establish independent cloud protection. Digest catalog access and recovery private keys belong to separate principals/permissions. `age` recipient files contain public recipient keys; private identities are not available to the scheduled backup runner. Packaging verifies archive structure with pg_restore and both encrypted copy digests; it does NOT claim an isolated restore. Recoverable source exports are in protected temporary storage and require platform storage disposal policy; file deletion is not a physical sanitization guarantee.

Schedule private runner attempts at `17 22,10 * * *` UTC (06:17/18:17 Asia/Manila including Sunday). Configure the private workflow only after custodian review of credentials, storage, budgets, capacity, key custody and failure monitoring. Do not use public Actions or public artifacts. Keep seven verified daily copies: after isolated validation, retain the newest verified copy for each of seven business dates; delete older copies only after checking the independent recovery copy and catalog. Automated deletion is intentionally not enabled until that custody/verification policy is configured.

Check latest successful catalog entry at opening and closing; alert when any attempt fails or age approaches 24h. If attempts fail, manually export before the most recent successful copy exceeds 24h. Scheduling can be delayed. Target RPO 24h; RTO eight operating hours, Monday–Saturday 08:00–18:00 Manila; measure actual drill duration and do not claim the targets achieved.

For a drill, suspend app access and use an isolated test project/database. Validate catalog independently before decryption:

```
python3 admin/recovery/backup.py restore-files --archive /independent/recovery/COPY.tar.age --entry /separate/catalog/COPY.tar.age.json --identity /offline/recovery.identity --destination /new/isolated-directory
```

File verification does not establish database recovery. Review roles/config; restore with pg_restore --exit-on-error to a NEW isolated database. Recreate required managed Auth configuration/mappings and restore actual Storage objects. Apply the separately retained approved erasure ledger before opening access. Run backend lifecycle/security/payment/reservation tests; reconcile charge/receipt/expense totals, reservations <= physical stock, references and identity links; compare inventory checksums. Record timings, errors, copy age, exact inventory and reviewer decision. Reopen only after Owner/custodian review. Never bypass failed authenticated decryption or mismatch checks.

Current external blockers: development project credentials, restricted independent cloud destinations, recovery identities/custodian, approved retention/erasure decisions and a hosted isolated restore drill. Local file/tooling tests do not remove these blockers.

Age/retention commands: `python3 admin/recovery/retention.py monitor --primary ... --recovery ... --catalog ...` verifies copies and exits2 when stale/missing/corrupt; wire that status to the private runner's failure monitoring. Run at opening/closing. `prune` defaults to dry-run and refuses deletion until7 distinct daily copies have administrator-recorded `isolated_restore: {"status":"VERIFIED","evidence_ref":"restricted drill evidence ID"}` decisions in the independently controlled catalog. Do not add such decisions based solely on file round trips. Review the dry-run before --execute; retired ciphertexts are deleted from both destinations while catalog/retirement history is retained. Actual cloud destination permissions and externally delivered alert monitoring remain unconfigured.
