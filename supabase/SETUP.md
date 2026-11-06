# Connecting the app to Supabase

The Android app is already pointed at your project (`ypwzwiljtjrvfppqyjgl`). These steps create what it needs there. Do them once, in order, in the [Supabase Dashboard](https://supabase.com/dashboard). Sign-in will not work until step 4 is done.

## 1. Set the password rules

**Authentication → Providers → Email** (or **Authentication → Policies**, depending on your dashboard version):

- Set **Minimum password length** to `15`.
- Turn **off** public sign-ups ("Allow new users to sign up"). Staff accounts are created by the Owner only.

## 2. Create the tables and rules

1. Open **SQL Editor → New query**.
2. Paste all of [migrations/20261105000000_owner_module.sql](migrations/20261105000000_owner_module.sql) and select **Run**.

After the base schema, review and apply [202611070001_session_privacy.sql](migrations/202611070001_session_privacy.sql). The new migration adds server session checks, payload-bound retries and administrator-only privacy procedures; it performs no customer erasure or stock initialization by itself. Existing financial records remain. Deploy the matching updated Android client and identity handler together in an approved development rollout; older clients do not register the new session guard. This repository change has not applied anything to your hosted project.

## 3. Deploy the complete identity function

Use the authenticated Supabase CLI for the approved **development** project from this repository root:

```sh
supabase functions deploy identity --project-ref YOUR_APPROVED_DEVELOPMENT_PROJECT_REF --no-verify-jwt
```

Deploy both `functions/identity/index.ts` and its imported `functions/_shared/identity_safety.ts` dependency together. Copying only index.ts into a single-file Dashboard editor will leave a missing import. The gateway JWT flag is intentionally off because username sign-in has no token yet; protected actions verify managed bearer identity and current database session in the handler. Keep service credentials server-side. Apply the ordered migrations and matching updated Android client as one reviewed development rollout. No deployment has been performed from this workspace.

## 4. Create the Owner account

1. Open **Authentication → Users → Add user → Create new user**.
2. Enter an email and a password of at least 15 characters, and tick **Auto Confirm User**. You sign in to the app with the username below, not this email.
3. In the **SQL Editor**, run this with your own values. Keep the username in lowercase:

```sql
insert into public.profiles (id, role, username, full_name, contact_number, status)
select id, 'owner', 'owner.elena', 'Elena Mendoza', '09175550100', 'Active'
from auth.users where email = 'the-email-you-used@example.com'
on conflict (id) do update
  set role = 'owner', username = excluded.username, full_name = excluded.full_name,
      contact_number = excluded.contact_number, status = 'Active';
```

If it reports `1 row`, the account is ready.

## 5. Set the opening stock

There is deliberately no stock-adjustment screen (A-11.2). Run this once with the Owner-approved counts. `reserved_filled` stays `0` because no orders exist yet:

```sql
-- Designated database administrator only; substitute approved real counts/reference.
select aq_private.initialize_inventory(50, 80, 'APPROVED-OPENING-COUNT-REFERENCE');
```

The procedure refuses a second initialization or initialization after operational orders/sales/payments. Do not use a direct UPDATE to reset stock. The example 50/80 counts are illustrative, not approved business counts. See [database/privacy operations](../docs/security/DATABASE_PRIVACY_OPERATIONS.md).

## 6. Password recovery

The app has no forgot-password screen. Recovery is verified by the designated administrator outside the app, which is what requirements A-01 and A-11 ask for. An Owner who forgets their password contacts the administrator, who issues a new one as follows.

### Issuing a new Owner password

1. Verify the requester's identity and authority through the approved administrator process. Record an approval reference without passwords or factor secrets.
2. Use the managed provider's supported administrator reset/recovery procedure for the existing Auth user ID. Do not write `auth.users.encrypted_password` directly or replace the account identity.
3. Revoke application sessions with designated database authority:

```sql
select aq_private.revoke_actor_sessions('EXISTING-AUTH-USER-UUID', 'APPROVED-RECOVERY-REFERENCE');
```

4. Revoke the managed Auth access/refresh sessions using the supported administrator procedure. The SQL helper explicitly reports that provider revocation is a separate requirement.
5. Verify old credentials fail against the approved development stack and the correct active account can reauthenticate. Never send passwords/service keys/recovery secrets through public messages. Recover MFA only through the provider's reviewed identity-verified process.

The password minimum remains15characters. Review secure password-change/recovery settings in the actual hosted provider; client prompts alone do not establish those controls.

### Issuing a new Staff password

The Owner does this in the app, under **Settings → Staff accounts → Edit Staff**, using the Temporary password field. No dashboard access is needed.

## 7. Sign in

Open the app and sign in with the username from step 4.

If the Owner forgets the password, they contact the administrator, who follows step 6 to issue a new one.

The first time you save a staff account, the app shows a setup key. Add it to an authenticator app such as Google Authenticator, then enter the 6-digit code. Later changes only ask for the code.

## Keep these private

- Never put the `service_role` key in the Android app or in chat. Only the Edge Function uses it, and Supabase provides it automatically.
- Turn on MFA for your Supabase and GitHub logins (B-03).
- Only the designated administrator resets an Owner password, under **Authentication → Users**, after confirming who is asking.

## Database sessions and privacy

The updated client registers/renews a current server session for protected requests. Expired or revoked sessions cannot renew themselves; protected RLS reads also require a current session. Deactivation or role changes revoke existing sessions, including before later reactivation. Local sign-out always clears memory and attempts both application/provider revocation; failed remote connectivity cannot be treated as confirmed provider invalidation.

Read [DATABASE_PRIVACY_OPERATIONS.md](../docs/security/DATABASE_PRIVACY_OPERATIONS.md) for dry-run/approval/cutoff procedures, independently protected erasure-ledger export and restore replay. Ordinary Owner/Staff and service-role access cannot invoke these private administrative procedures. Financial records, names, references and retry hashes are retained; commercial retention and actual legal/operational approval remain the operator's responsibility.
