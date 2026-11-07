-- Session guard, logout and approved contact removal.
-- Run after 20261105000000_owner_module.sql. It only adds and replaces, so it
-- is safe to run again. Run the base migration first if it was changed.
--
-- * aq_session_touch is the session guard. Every protected write passes through
--   it. A session idle for 30 minutes, or one that was revoked, cannot renew
--   itself; the person signs in again (A-01, A-11).
-- * Deactivating an account revokes its application sessions at once, so an
--   access token that has not expired yet is still refused.
-- * aq_logout ends the exact session named in the token, so leaving the app
--   is recorded against the right session rather than only on the screen.
-- * erase_customer_contact is the administrator's privacy procedure. It needs
--   an approved decision, supports a dry run, is blocked by active operations
--   or unsettled balances, and never removes contact details that were
--   legitimately updated after the decision's cutoff.

-- ---------------------------------------------------------------- sessions --
create table if not exists public.app_sessions (
  session_id uuid primary key,
  user_id uuid not null references public.profiles (id) on delete cascade,
  started_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now(),
  revoked_at timestamptz,
  revoked_reason text
);
create index if not exists app_sessions_user_idx on public.app_sessions (user_id);
alter table public.app_sessions enable row level security;
revoke all on public.app_sessions from public, anon, authenticated;

-- Supabase places the Auth session id and the issue time in every access token.
create or replace function public.aq_jwt_claim(p_name text) returns text
language plpgsql stable set search_path = '' as $$
begin
  return nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> p_name;
exception when others then
  return null;
end $$;

create or replace function public.aq_jwt_session() returns uuid
language plpgsql stable set search_path = '' as $$
begin
  return public.aq_jwt_claim('session_id')::uuid;
exception when others then
  return null;
end $$;

create or replace function public.aq_jwt_issued_at() returns timestamptz
language plpgsql stable set search_path = '' as $$
begin
  return to_timestamp(public.aq_jwt_claim('iat')::double precision);
exception when others then
  return null;
end $$;

-- The session guard.
create or replace function public.aq_session_touch() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_sid uuid := public.aq_jwt_session();
  v_iat timestamptz := public.aq_jwt_issued_at();
  v_limit constant interval := interval '30 minutes';
  v_row public.app_sessions;
begin
  if v_user is null or v_sid is null then
    raise exception 'Session expired. Sign in again.'
      using detail = 'No application session is attached to this request. Protected requests are denied.';
  end if;
  perform pg_advisory_xact_lock(hashtext('aq_session'), hashtext(v_sid::text));
  select * into v_row from public.app_sessions where session_id = v_sid and user_id = v_user for update;
  if found then
    if v_row.revoked_at is not null then
      raise exception 'Session expired. Sign in again.'
        using detail = 'This session was ended (' || coalesce(v_row.revoked_reason, 'revoked') || '). Protected requests are denied.';
    end if;
    if v_row.last_seen_at <= clock_timestamp() - v_limit then
      raise exception 'Session expired. Sign in again.'
        using detail = 'The account was idle for 30 minutes. An idle session cannot renew itself; protected requests are denied.';
    end if;
    update public.app_sessions set last_seen_at = clock_timestamp() where session_id = v_sid;
  else
    if v_iat is null or v_iat <= clock_timestamp() - v_limit then
      raise exception 'Session expired. Sign in again.'
        using detail = 'The sign-in is older than 30 minutes and was never used. Protected requests are denied.';
    end if;
    insert into public.app_sessions (session_id, user_id) values (v_sid, v_user);
  end if;
  return jsonb_build_object('session_id', v_sid, 'last_seen_at', clock_timestamp());
end $$;

-- Read-only check used by the row policies: the same rule without renewing.
create or replace function public.aq_session_ok() returns boolean
language plpgsql stable security definer set search_path = '' as $$
declare
  v_sid uuid := public.aq_jwt_session();
  v_iat timestamptz := public.aq_jwt_issued_at();
  v_row public.app_sessions;
begin
  if v_sid is null then return false; end if;
  select * into v_row from public.app_sessions where session_id = v_sid and user_id = auth.uid();
  if found then
    return v_row.revoked_at is null and v_row.last_seen_at > clock_timestamp() - interval '30 minutes';
  end if;
  return v_iat is not null and v_iat > clock_timestamp() - interval '30 minutes';
end $$;

create or replace function public.is_active_user() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(public.app_role() in ('owner', 'staff'), false) and public.aq_session_ok()
$$;

-- Every protected request re-reads the account status, then passes the guard.
create or replace function public.require_active(out actor uuid, out actor_name text)
language plpgsql security definer set search_path = '' as $$
begin
  select p.id, coalesce(p.full_name, p.username, 'Account') into actor, actor_name
  from public.profiles p
  where p.id = auth.uid() and p.status = 'Active' and p.role::text in ('owner', 'staff');
  if actor is null then
    raise exception 'Session expired. Sign in again.'
      using detail = 'The account was idle for 30 minutes or its active access was removed. Protected requests are denied.';
  end if;
  perform public.aq_session_touch();
end $$;

-- The guard writes, so everything that calls it must be allowed to write.
alter function public.require_owner() volatile;
alter function public.whoami() volatile;
alter function public.financial_summary(date, date) volatile;
alter function public.inventory_status() volatile;

create or replace function public.aq_logout() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user uuid := auth.uid();
  v_sid uuid := public.aq_jwt_session();
  v_changed integer := 0;
begin
  if v_user is null or v_sid is null then
    raise exception 'Access denied.';
  end if;
  if not exists (select 1 from public.profiles where id = v_user) then
    return jsonb_build_object('logged_out', true);
  end if;
  perform pg_advisory_xact_lock(hashtext('aq_session'), hashtext(v_sid::text));
  insert into public.app_sessions (session_id, user_id, revoked_at, revoked_reason)
  values (v_sid, v_user, clock_timestamp(), 'logout')
  on conflict (session_id) do update
    set revoked_at = clock_timestamp(), revoked_reason = 'logout'
    where public.app_sessions.user_id = excluded.user_id and public.app_sessions.revoked_at is null;
  get diagnostics v_changed = row_count;
  if v_changed > 0 then
    perform public.audit(v_user, 'logout', 'account', v_user::text, jsonb_build_object('session_id', v_sid));
  end if;
  return jsonb_build_object('logged_out', true, 'session_id', v_sid);
end $$;

-- Deactivation revokes the account's sessions immediately.
create or replace function public.aq_revoke_sessions_on_deactivate() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if new.status = 'Inactive' and old.status is distinct from 'Inactive' then
    update public.app_sessions
    set revoked_at = clock_timestamp(), revoked_reason = 'deactivated'
    where user_id = new.id and revoked_at is null;
  end if;
  return new;
end $$;
drop trigger if exists aq_profiles_deactivate on public.profiles;
create trigger aq_profiles_deactivate after update of status on public.profiles
for each row execute function public.aq_revoke_sessions_on_deactivate();

revoke all on function public.aq_jwt_claim(text), public.aq_jwt_session(), public.aq_jwt_issued_at(),
  public.aq_session_ok(), public.aq_revoke_sessions_on_deactivate()
  from public, anon, authenticated;
revoke all on function public.aq_session_touch(), public.aq_logout() from public, anon;
grant execute on function public.aq_session_touch(), public.aq_logout() to authenticated;

-- ----------------------------------------------------------------- privacy --
-- Contact details can be removed; names and financial history are retained.
alter table public.customers alter column contact_number drop not null;
alter table public.customers alter column address drop not null;
alter table public.orders alter column delivery_address drop not null;

create table if not exists public.privacy_decisions (
  decision_id uuid primary key,
  customer_id uuid not null references public.customers (id),
  payload jsonb not null,
  result jsonb not null,
  administrator text not null,
  effective_cutoff timestamptz not null,
  recorded_at timestamptz not null default now()
);
alter table public.privacy_decisions enable row level security;
revoke all on public.privacy_decisions from public, anon, authenticated, service_role;

-- Administrator-only. Not callable from the app or the edge function: it runs
-- only with direct database authority (the SQL editor), after the Owner has
-- approved the decision. p_execute=false is a dry run that changes nothing.
create or replace function public.erase_customer_contact(
  p_customer_id uuid, p_decision_id uuid, p_owner_approval_ref text, p_reason text,
  p_execute boolean default false, p_cutoff timestamptz default null
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare
  v_cutoff timestamptz := coalesce(p_cutoff, clock_timestamp());
  v_payload jsonb;
  v_prior public.privacy_decisions;
  v_customer public.customers;
  v_blockers text[] := '{}';
  v_customer_rows integer;
  v_order_rows integer;
  v_result jsonb;
begin
  if p_customer_id is null or p_decision_id is null or p_execute is null
     or coalesce(length(btrim(p_owner_approval_ref)), 0) not between 1 and 160
     or coalesce(length(btrim(p_reason)), 0) not between 1 and 500
     or v_cutoff > clock_timestamp() then
    raise exception 'An approved decision needs the customer, a decision ID, the Owner approval reference, a reason and a cutoff that is not in the future.';
  end if;
  v_payload := jsonb_build_object('customer_id', p_customer_id, 'owner_approval_ref', btrim(p_owner_approval_ref),
                                  'reason', btrim(p_reason), 'cutoff', p_cutoff);
  perform pg_advisory_xact_lock(hashtext('aq_privacy'), hashtext(p_customer_id::text));

  select * into v_prior from public.privacy_decisions where decision_id = p_decision_id;
  if found then
    if v_prior.payload <> v_payload then
      raise exception 'Decision ID conflicts with the retained decision.';
    end if;
    return v_prior.result;
  end if;

  select * into v_customer from public.customers where id = p_customer_id for update;
  if not found then raise exception 'Customer not found.'; end if;

  if exists (select 1 from public.orders o where o.customer_id = p_customer_id and o.status <> 'Delivered') then
    v_blockers := array_append(v_blockers, 'Orders are still in progress.');
  end if;
  if exists (select 1 from public.orders o where o.customer_id = p_customer_id and o.paid_amount < o.total)
     or exists (select 1 from public.walkin_sales s where s.customer_id = p_customer_id and s.paid_amount < s.total) then
    v_blockers := array_append(v_blockers, 'Unsettled balances remain.');
  end if;
  if array_length(v_blockers, 1) > 0 then
    raise exception 'Active operations or unsettled balances block contact removal.'
      using detail = array_to_string(v_blockers, ' ') || ' Nothing was changed.';
  end if;

  select count(*) into v_customer_rows from public.customers
  where id = p_customer_id and updated_at <= v_cutoff and (contact_number is not null or address is not null);
  select count(*) into v_order_rows from public.orders
  where customer_id = p_customer_id and created_at <= v_cutoff and delivery_address is not null;

  v_result := jsonb_build_object(
    'mode', case when p_execute then 'EXECUTED' else 'DRY RUN' end,
    'customer_id', p_customer_id, 'decision_id', p_decision_id, 'effective_cutoff', v_cutoff,
    'customer_contact_rows', v_customer_rows, 'order_address_rows', v_order_rows,
    'protected_by_later_update', v_customer.updated_at > v_cutoff,
    'customer_name', 'RETAINED', 'financial_history', 'RETAINED');
  if not p_execute then return v_result; end if;

  update public.customers set contact_number = null, address = null, updated_at = clock_timestamp()
  where id = p_customer_id and updated_at <= v_cutoff;
  update public.orders set delivery_address = null
  where customer_id = p_customer_id and created_at <= v_cutoff;
  update public.audit_log
  set details = details - array['contact_number', 'address', 'contact', 'delivery_address', 'customer_contact']
  where created_at <= v_cutoff and (
    (entity = 'customer' and entity_id = p_customer_id::text)
    or (entity = 'order' and entity_id in (select o.id::text from public.orders o where o.customer_id = p_customer_id)));
  update public.idempotency_keys
  set result = result - array['contact_number', 'address', 'delivery_address', 'customer_contact']
  where created_at <= v_cutoff and result ->> 'id' in (
    select p_customer_id::text
    union select o.id::text from public.orders o where o.customer_id = p_customer_id);

  insert into public.privacy_decisions (decision_id, customer_id, payload, result, administrator, effective_cutoff)
  values (p_decision_id, p_customer_id, v_payload, v_result, session_user, v_cutoff);
  insert into public.audit_log (actor, action, entity, entity_id, details)
  values (null, 'approved_contact_removal', 'customer', p_customer_id::text,
          v_result || jsonb_build_object('owner_approval_ref', btrim(p_owner_approval_ref), 'reason', btrim(p_reason),
                                         'administrator', session_user));
  return v_result;
end $$;
revoke all on function public.erase_customer_contact(uuid, uuid, text, text, boolean, timestamptz)
  from public, anon, authenticated, service_role;
