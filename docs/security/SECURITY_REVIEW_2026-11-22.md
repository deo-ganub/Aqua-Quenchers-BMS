# Security Review of the IAS Hardening Changes

**Document identifier:** AQ-SEC-04 · **Date:** 22 November 2026 · **Scope:** commits dca2955, ce4eab8, c20af6c, 062b1bc on `main` and the fixes that followed.

## 1. Method

The method follows the source-first approach of the earlier Cloudflare-method assessment ([README](README.md)): map trust boundaries, trace every candidate issue through the source with file/line evidence, and keep confirmed issues, plausible risks and missing best practices apart. A separate reviewer instance that had not written the code did the review, read-only. The author then fixed the findings and re-ran the tests. This is an internal independent review, not a third-party certification, and no deployed service was probed.

**Coverage (units reviewed):** `202611220001_ias_hardening.sql` in full, compared against the previous `aq_command`/`aq_query` (no authorization check removed); grants and default privileges of the earlier migrations; `OfflineQueue`, `OfflineStore` and the `MainActivity` queue paths; `Rules.password`, `auth.mjs` (`checkPassword`, `clientSource`, `loginHandler`); the self-service password change in `OwnerUi`; `restore_drill.py`, `backup.py`, `export-and-package` and the runbook.

**Checked and not a vulnerability:** all new SECURITY DEFINER functions use `search_path=''` and fully qualified names, and the only dynamic SQL is parameterized. `aq_private` stays closed to anon/authenticated. Request keys are bound to `(actor_id, request_id)`, so one account cannot replay another's request. The audit trigger serializes on an advisory lock, and timestamps are hashed in UTC. Offline entries are filtered by account, authenticated with AES-GCM, and only reported as sent after the server returns an id. The restore drill has no command injection (argument lists, generated database name).

## 2. Findings and resolution

| ID | Category | Finding | Resolution |
|---|---|---|---|
| F1 | Confirmed, High | The encryption key silently fell back to a copy in the same database, even on a server with Vault. A dump would then contain both key and data. | **Fixed.** Where Vault exists, a missing Vault key stops the migration and every encryption call (fail closed). Local keys are generated only where no Vault exists (test databases). Backups exclude `local_secrets` data. Tested in `ias_hardening.sql`. Key-id/rotation support is future work. |
| F2 | Confirmed, Medium | The stored request hash was an unkeyed SHA-256 of the full request. Someone with a dump could guess an 11-digit contact number by brute force. Erasure kept it. | **Fixed.** The digest is now an HMAC with a separate Vault key (`aq_pii_index_key`); existing digests were re-keyed. Erasure replaces it with random bytes. Tested. |
| F3 | Confirmed, Medium | The backup drill trusted `verify_audit_chain()` from the dump it was checking, and the anchor hash in the catalog was not compared. | **Fixed.** The drill recomputes every hash itself from rows exported with Postgres built-ins and compares the previous backup's anchor (`chain_seq`, hash). A rewritten and fully re-chained log plus a replaced verify function: PASSED without an anchor, **FAILED (anchor MISMATCH)** with the previous anchor. The first backup has no earlier anchor; that is a stated limit. |
| F4 | Confirmed, Medium | The full password policy is checked on the server only for staff temporary passwords. A user's own password change goes directly to Supabase Auth, which only enforces its configured minimum length (15). | **Documented.** The app enforces the full rule. The direct Auth path is limited to the hosted Auth policy, which must be set and recorded when deploying (minimum 15; enable leaked-password protection if the plan allows it). A modified client can only weaken its own user's password. |
| F5 | Plausible, Medium | Without a trusted proxy header a caller could forge `cf-connecting-ip`. A shared "unknown" bucket could let one attacker block everyone. | **Fixed.** Only the header named in `AQ_CLIENT_IP_HEADER` is trusted, and it must be confirmed on the hosted project. Without it the source limit follows the username, so there is no shared bucket. The 10-attempt per-username limit always applies. Tested. |
| F6 | Plausible, Medium | An offline save older than 3 days was dropped with "never saved", though a lost reply can look the same as no connection. | **Fixed.** The app asks the server (`request_status`) before deciding, and reports "Sent earlier" or "Not saved" accordingly. Tested in SQL and unit tests. The device clock is still trusted for the age limit. |
| F7 | Best practice | `concat_ws`/`|` hashing input was ambiguous with NULLs. | **Fixed.** Canonical `jsonb_build_array(...)` encoding. |
| F8 | Best practice | The blind index used the encryption key. | **Fixed.** A separate `aq_pii_index_key`. |
| F9 | Best practice | Keystore key usable while the device is locked; old entries of another account could stay forever; one account could fill the queue; session-expired 403 dropped saves. | **Fixed.** `setUnlockedDeviceRequired`, a 14-day purge for any account, a 30-entry cap per account, and session-expired 403 kept. The queue is kept on sign-out on purpose (unsent saves must not be lost); it is encrypted and purged by age. |
| F10 | Best practice | Drill: missing result passed silently, stderr could echo rows, no timeouts, cleanup could hide errors, key restored to scratch. | **Fixed.** Missing drill recorded explicitly as `NOT RUN`, only the first error line is shown, timeouts added, cleanup errors do not mask the original, scratch dropped `WITH (FORCE)`, key data excluded from dumps. |
| F11 | Best practice | Staff contact numbers in `profiles` are not encrypted. Free-text fields (notes, remarks, reasons) could still contain personal data in the audit log. | **Accepted and documented.** Staff contacts are internal account data under RLS. Free-text fields have length limits and a guidance note in the Privacy Policy. |

## 3. Status of the earlier Cloudflare-method audit

The open item from AQ-SEC-01 (§4, source key taken from the first `x-forwarded-for` value) is resolved by F5. This review is the independent pass over the changed code. It was done by a separate reviewer instance, not by an outside auditor, so it does not complete a third-party audit. Runtime checks of F5 (which header the hosted proxy sets) and of device behaviour remain open.

## 4. Verification after fixes

SQL suite: 8 migrations and 18 rollback fixtures passed, including `ias_hardening.sql`. Concurrency tests passed. JUnit: 25/25. Node auth tests: 9/9. Restore drill: PASSED on a seeded disposable database; FAILED on a tampered log; FAILED with the anchor on a fully re-chained log. Android build and device tests were not run (no Android SDK in this environment).
