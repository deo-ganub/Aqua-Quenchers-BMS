# Production apply checklist — walk-in counter orders + IAS hardening (2026-11-22)

Project: `ypwzwiljtjrvfppqyjgl`. Run by the Owner/administrator. Nothing here was run by Claude
against the hosted project (no access was granted); every step below is untested on hosted Supabase.

| # | Where | What | Expected |
|---|---|---|---|
| 1 | SQL Editor | Run `00_preflight.sql` | `vault_ready` true; both `*_already_applied` false. Stop if the migrations already show as applied. |
| 2 | Your computer | Back up first: `npx supabase login`, then `npx supabase db dump --db-url "<connection string from Project Settings → Database>" -f before_2026-11-22.sql` and again with `--data-only -f before_2026-11-22_data.sql`. Keep the files private. | Two dump files. |
| 3 | SQL Editor | Run the `do $$ … $$` block of `01_create_vault_keys.sql` | "Success. No rows returned". |
| 4 | SQL Editor | Run the last `select` of `01_create_vault_keys.sql`; copy both keys into a password manager **outside Supabase** | Two 64-character hex values. |
| 5 | SQL Editor | Paste and run the whole of `supabase/migrations/202611210001_walk_in_counter_orders.sql` | Success. It is one transaction: an error changes nothing. |
| 6 | SQL Editor | Paste and run the whole of `supabase/migrations/202611220001_ias_hardening.sql` | Success. It refuses to run if the Vault keys are missing. |
| 7 | SQL Editor | Run `04_verify.sql` | `audit_chain.ok` true; 0 unencrypted contacts/addresses. |
| 8 | Your computer (repo root) | `npx supabase functions deploy username-login --project-ref ypwzwiljtjrvfppqyjgl` and the same for `staff-admin` | Both "Deployed". |
| 9 | Optional | Only if a proxy such as Cloudflare sits in front: `npx supabase secrets set AQ_CLIENT_IP_HEADER=cf-connecting-ip --project-ref ypwzwiljtjrvfppqyjgl` | Without it, sign-in throttling keys on the username. |
| 10 | App | Sign in as Owner, open a customer (contact/address still readable), save a test walk-in, check Settings → Gallons at station | Works; then retest A-07/A-18 by hand. |

If step 5 or 6 fails: nothing was changed by that step (transaction). Copy the error message (not data) and stop.
If something is wrong after step 6: restore from the step-2 dump; the Vault keys must be kept either way.
