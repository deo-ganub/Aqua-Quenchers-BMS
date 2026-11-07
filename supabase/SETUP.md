# Connecting the app to Supabase

The Android app is already pointed at your project (`ypwzwiljtjrvfppqyjgl`). These steps create what it needs there. Do them once, in order, in the [Supabase Dashboard](https://supabase.com/dashboard). Sign-in will not work until step 4 is done.

## 1. Set the password rules

**Authentication → Providers → Email** (or **Authentication → Policies**, depending on your dashboard version):

- Set **Minimum password length** to `15`.
- Turn **off** public sign-ups ("Allow new users to sign up"). Staff accounts are created by the Owner only.

## 2. Create the tables and rules

1. Open **SQL Editor → New query**.
2. Paste all of [migrations/20261105000000_owner_module.sql](migrations/20261105000000_owner_module.sql) and select **Run**.
3. In a new query, paste all of [migrations/20261107000000_session_privacy.sql](migrations/20261107000000_session_privacy.sql) and select **Run**. This adds the session guard, logout and the contact-removal procedure.

Both only add to the database and are safe to run again, in that order. Your existing `profiles` rows are kept.

## 3. Deploy the identity function

1. Open **Edge Functions → Deploy a new function → Via Editor**.
2. Name it exactly `identity`.
3. Replace the sample code with all of [functions/identity/index.ts](functions/identity/index.ts) and deploy.
4. Open the function's **Details** tab, turn **Verify JWT off**, and save. Sign-in has no user token yet, so the function checks tokens itself.

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
update public.inventory
set physical_filled = 50, reserved_filled = 0, empty_gallons = 80, updated_at = now()
where id = 1;
```

## 6. Password recovery

The app has no forgot-password screen. Recovery is verified by the designated administrator outside the app, which is what requirements A-01 and A-11 ask for. An Owner who forgets their password contacts the administrator, who issues a new one as follows.

### Issuing a new Owner password

1. Open **Authentication → Users** and find the account.
2. Open the row menu (the three dots at the end) and choose **Reset password**, or set one directly with the SQL below.
3. Hand the temporary password over in person, not by message.
4. They sign in, then set their own under **Settings → Change password**.

```sql
update auth.users
set encrypted_password = extensions.crypt('a-temporary-password-here', extensions.gen_salt('bf')),
    updated_at = now()
where id = (select id from public.profiles where username = 'owner.elena');
```

The temporary password must be at least 15 characters, the same as every other password here.

### Issuing a new Staff password

The Owner does this in the app, under **Settings → Staff accounts → Edit Staff**, using the Temporary password field. No dashboard access is needed.

## 7. Sign in

Open the app and sign in with the username from step 4.

If the Owner forgets the password, they contact the administrator, who follows step 6 to issue a new one.

Signing in opens a server-side session. It ends after 30 minutes without activity, on **Log out**, or the moment the account is set Inactive; an old token is refused after any of those.

The first time you save a staff account, the app shows a setup key. Add it to an authenticator app such as Google Authenticator, then enter the 6-digit code. Later changes only ask for the code.

## 8. Removing a customer's contact details (privacy)

Contact number and address can be removed from a customer's record; the name and all financial history stay. This runs only in the **SQL Editor**, never from the app, and only after the Owner has approved the request in writing.

1. Find the customer id: `select id, name, contact_number from public.customers where name ilike '%maria%';`
2. Dry run first. Nothing changes; the answer shows what would be removed:

```sql
select public.erase_customer_contact(
  'customer-id-here'::uuid,
  gen_random_uuid(),          -- decision id: keep this value for step 3
  'APR-2026-001',             -- the Owner's approval reference
  'Customer asked for removal on 07 Nov 2026',
  false);                     -- dry run
```

3. Run the same statement again with `true` and the **same decision id** to execute it.

It is refused while the customer has an order still in progress or an unpaid balance. A contact number entered after the decision's cutoff is left alone (`protected_by_later_update`). The decision, who ran it and the result are kept in `public.privacy_decisions` and the audit history.

## Keep these private

- Never put the `service_role` key in the Android app or in chat. Only the Edge Function uses it, and Supabase provides it automatically.
- Turn on MFA for your Supabase and GitHub logins (B-03).
- Only the designated administrator resets an Owner password, under **Authentication → Users**, after confirming who is asking.
