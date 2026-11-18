-- Remove the earlier prototype schema from `public` (owner request, 2026-11-14).
-- IAS ACL-01/ACL-10 (default deny) and "separate operational versus administrative functions":
-- the hosted project still held a pre-rebuild prototype in `public` (its own tables, views and
-- SECURITY DEFINER functions such as record_payment, save_expense and set_order_status, plus
-- anonymous EXECUTE on is_current_user_owner). The app and Edge Functions use only the reviewed
-- aq_* RPCs below over aq_private. Nothing outside `public` depends on the legacy objects.
-- A full row/definition export was taken before this ran (kept outside git).
-- No CASCADE: an unexpected dependency aborts the whole migration instead of removing it.
begin;

drop view if exists public.customer_balances, public.orders_view, public.payments_view;

drop table if exists
  public.payments, public.walkin_sales, public.order_events, public.orders, public.privacy_decisions,
  public.customers, public.expenses, public.app_sessions, public.inventory, public.audit_log,
  public.idempotency_keys, public.reference_counters, public.profiles;

do $$
declare r record;
begin
  for r in
    select p.oid::regprocedure as fn
    from pg_proc p
    where p.pronamespace = 'public'::regnamespace and p.prokind = 'f'
      and p.proname not in (
        'aq_admin_auth_identity', 'aq_admin_fail_staff', 'aq_admin_finish_staff', 'aq_admin_prepare_staff',
        'aq_admin_resolve_username', 'aq_admin_update_staff', 'aq_auth_login_attempt',
        'aq_command', 'aq_logout', 'aq_me', 'aq_query')
  loop
    execute format('drop function %s', r.fn);
  end loop;
end $$;

-- New objects in public stay closed unless a migration grants them explicitly.
alter default privileges in schema public revoke all on tables from public, anon, authenticated;
alter default privileges in schema public revoke all on functions from public, anon, authenticated;
alter default privileges in schema public revoke all on sequences from public, anon, authenticated;

commit;
