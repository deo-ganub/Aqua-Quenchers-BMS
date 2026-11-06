-- Additive hardening for the owner-module schema. Apply after 20261105000000.
-- Does not deploy itself, change prices/stock, or erase any customer on migration.
begin;
create schema if not exists aq_private;
revoke all on schema aq_private from public, anon, authenticated, service_role;
revoke create on schema public from public, anon, authenticated;
alter default privileges in schema aq_private revoke execute on functions from public;
create table if not exists aq_private.sessions (
  session_id uuid primary key,
  actor uuid not null references public.profiles(id),
  last_seen timestamptz not null,
  revoked_at timestamptz
);
alter table aq_private.sessions enable row level security;
revoke all on aq_private.sessions from public, anon, authenticated, service_role;

create or replace function aq_private.session_id() returns uuid
language plpgsql stable security definer set search_path='' as $$
begin
  return (auth.jwt()->>'session_id')::uuid;
exception when invalid_text_representation then return null;
end $$;

-- Read-only predicate: browsing cannot renew its own inactivity deadline.
create or replace function aq_private.session_allowed() returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from aq_private.sessions s
   where s.session_id=aq_private.session_id() and s.actor=auth.uid()
   and s.revoked_at is null and s.last_seen>statement_timestamp()-interval '30 minutes')
$$;

-- Called for an explicit protected request, never on an automatic background timer.
create or replace function public.aq_session_touch() returns jsonb
language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); sid uuid:=aq_private.session_id(); s aq_private.sessions;
  issued timestamptz; stamp timestamptz:=clock_timestamp();
begin
  if a is null or sid is null or not exists(select 1 from public.profiles p
      where p.id=a and p.status='Active' and p.role::text in ('owner','staff')) then
    raise exception 'Session expired. Sign in again.' using errcode='28000';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(sid::text,0));
  select * into s from aq_private.sessions where session_id=sid for update;
  if found then
    if s.actor<>a or s.revoked_at is not null or s.last_seen<=stamp-interval '30 minutes' then
      raise exception 'Session expired. Sign in again.' using errcode='28000';
    end if;
    update aq_private.sessions set last_seen=stamp where session_id=sid;
  else
    begin issued:=to_timestamp((auth.jwt()->>'iat')::double precision);
    exception when invalid_text_representation or numeric_value_out_of_range then
      raise exception 'Session expired. Sign in again.' using errcode='28000';
    end;
    if issued is null or issued<=stamp-interval '30 minutes' or issued>stamp+interval '1 minute' then
      raise exception 'Session expired. Sign in again.' using errcode='28000';
    end if;
    insert into aq_private.sessions(session_id,actor,last_seen) values(sid,a,stamp);
  end if;
  return jsonb_build_object('active',true);
end $$;

create or replace function public.app_role() returns text
language sql stable security definer set search_path='' as $$
 select p.role::text from public.profiles p where p.id=auth.uid() and p.status='Active'
 and aq_private.session_allowed()
$$;
create or replace function public.require_active(out actor uuid,out actor_name text)
language plpgsql stable security definer set search_path='' as $$
begin
 select p.id,coalesce(p.full_name,p.username,'Account') into actor,actor_name
 from public.profiles p where p.id=auth.uid() and p.status='Active'
 and p.role::text in ('owner','staff') and aq_private.session_allowed();
 if actor is null then raise exception 'Session expired. Sign in again.' using errcode='28000'; end if;
end $$;
-- Also guard the existing own-profile SELECT exception with current session state.
drop policy if exists aq_profiles_select on public.profiles;
create policy aq_profiles_select on public.profiles for select to authenticated
 using(public.is_active_user() and (id=auth.uid() or public.is_owner()));

create or replace function public.aq_logout() returns jsonb
language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); sid uuid:=aq_private.session_id(); changed integer;
begin
 if a is null or sid is null or not exists(select 1 from public.profiles where id=a) then
   raise exception 'Session expired. Sign in again.' using errcode='28000';
 end if;
 perform pg_advisory_xact_lock(hashtextextended(sid::text,0));
 if exists(select 1 from aq_private.sessions where session_id=sid and actor<>a) then
   raise exception 'Session expired. Sign in again.' using errcode='28000';
 end if;
 insert into aq_private.sessions(session_id,actor,last_seen,revoked_at)
 values(sid,a,clock_timestamp(),clock_timestamp())
 on conflict(session_id) do update set revoked_at=excluded.revoked_at
 where aq_private.sessions.revoked_at is null;
 get diagnostics changed=row_count;
 if changed>0 then perform public.audit(a,'logout','session',sid::text,'{}'::jsonb); end if;
 return jsonb_build_object('signed_out',true);
end $$;

create or replace function aq_private.revoke_removed_access() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 if new.status is distinct from old.status or new.role is distinct from old.role then
   update aq_private.sessions set revoked_at=coalesce(revoked_at,clock_timestamp()) where actor=new.id;
 end if;
 return new;
end $$;
drop trigger if exists aq_revoke_removed_access on public.profiles;
create trigger aq_revoke_removed_access after update of status,role on public.profiles
 for each row execute function aq_private.revoke_removed_access();

-- Bind new retry keys to the complete typed intent; never store its raw fields.
alter table public.idempotency_keys add column if not exists payload_hash text;
create or replace function public.idem_claim(p_actor uuid,p_key uuid,p_action text,p_payload jsonb)
returns jsonb language plpgsql security definer set search_path='' as $$
declare v public.idempotency_keys; h text;
begin
 if p_actor is distinct from auth.uid() or p_key is null or p_payload is null
    or octet_length(p_payload::text)>16384 then raise exception 'Invalid request.'; end if;
 h:=encode(sha256(convert_to(p_payload::text,'UTF8')),'hex');
 insert into public.idempotency_keys(actor,key,action,payload_hash)
 values(p_actor,p_key,p_action,h) on conflict do nothing;
 if found then return null; end if;
 select * into v from public.idempotency_keys where actor=p_actor and key=p_key for update;
 if v.action<>p_action or v.payload_hash is distinct from h then
   raise exception 'Request key conflicts with its original intent.' using errcode='23505';
 end if;
 if v.result is null then raise exception 'Request requires reconciliation.'; end if;
 return v.result;
end $$;
-- Patch only the known maintained writer call sites, keeping their financial logic.
do $$
declare f record; definition text; old_call text; new_call text; fields text;
begin
 for f in select p.oid,p.proname,p.proargnames,p.pronargs from pg_proc p
 join pg_namespace n on n.oid=p.pronamespace where n.nspname='public'
 and p.proname in ('save_customer','create_order','set_order_status','record_pickup',
 'reschedule_pickup','record_delivery','complete_walkin_sale','record_payment','save_expense')
 loop
   definition:=pg_get_functiondef(f.oid);
   old_call:=format('public.idem_claim(a.actor, p_key, %L)',f.proname);
   select string_agg(format('%L,%I',name,name),',' order by ord) into fields
   from unnest(f.proargnames[1:f.pronargs]) with ordinality x(name,ord) where name<>'p_key';
   new_call:=format('public.idem_claim(a.actor, p_key, %L, jsonb_build_object(%s))',f.proname,fields);
   if position(old_call in definition)>0 then execute replace(definition,old_call,new_call);
   elsif position(new_call in definition)=0 then raise exception 'Unexpected writer definition: %',f.proname;
   end if;
 end loop;
end $$;

-- Customer contact may become NULL only through the separately authorized procedure.
-- Ordinary save_customer still requires every contact field.
alter table public.customers alter column contact_number drop not null;
alter table public.customers alter column address drop not null;
alter table public.orders alter column delivery_address drop not null;
create or replace function aq_private.require_complete_order_contact() returns trigger
language plpgsql security definer set search_path='' as $$
declare c public.customers;
begin
 select * into c from public.customers where id=new.customer_id for share;
 if new.customer_type is distinct from c.customer_type then
   raise exception 'Customer type changed. Reload the customer before ordering.';
 end if;
 new.unit_price:=public.unit_price_for(c.customer_type);
 if c.contact_number is null or coalesce(trim(c.address),'')='' then
   raise exception 'Complete the customer contact details before creating a new order.';
 end if;
 return new;
end $$;
drop trigger if exists aq_order_contact on public.orders;
create trigger aq_order_contact before insert on public.orders for each row
 execute function aq_private.require_complete_order_contact();

create table if not exists aq_private.privacy_decisions (
 decision_id uuid primary key, customer_id uuid not null references public.customers(id),
 approval_ref text not null, reason text not null, cutoff timestamptz not null,
 executed_at timestamptz not null default clock_timestamp(), receipt jsonb not null
);
alter table aq_private.privacy_decisions enable row level security;
revoke all on aq_private.privacy_decisions from public,anon,authenticated,service_role;

-- Redact known structured contact fields only in this customer's copied objects.
create or replace function aq_private.redact_contact(v jsonb,target uuid,owned boolean default false)
returns jsonb language plpgsql immutable set search_path='' as $$
declare result jsonb; k text; child jsonb; scoped boolean;
begin
 if v is null then return null; end if;
 if jsonb_typeof(v)='array' then
   select coalesce(jsonb_agg(aq_private.redact_contact(x,target,owned)),'[]'::jsonb) into result
   from jsonb_array_elements(v) x; return result;
 elsif jsonb_typeof(v)='object' then
   scoped:=owned or coalesce(v->>'id'=target::text,false) or coalesce(v->>'customer_id'=target::text,false);
   result:='{}'::jsonb;
   for k,child in select * from jsonb_each(v) loop
     result:=result||jsonb_build_object(k,case when scoped and k in ('contact_number','address','delivery_address')
       then 'null'::jsonb else aq_private.redact_contact(child,target,scoped) end);
   end loop;
   return result;
 end if;
 return v;
end $$;

create or replace function aq_private.erase_customer_contact(
 p_customer uuid,p_decision uuid,p_approval text,p_reason text,
 p_execute boolean default false,p_cutoff timestamptz default null)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare c public.customers; prior aq_private.privacy_decisions; cutoff timestamptz;
 receipt jsonb; c_count integer:=0; o_count integer:=0; a_count integer:=0; r_count integer:=0;
begin
 if p_customer is null or p_decision is null or coalesce(trim(p_approval),'')=''
 or char_length(p_approval)>160 or coalesce(trim(p_reason),'')='' or char_length(p_reason)>500
 or p_execute is null then raise exception 'Customer, decision, approval reference and reason are required.'; end if;
 perform pg_advisory_xact_lock(hashtextextended(p_decision::text,1));
 select * into prior from aq_private.privacy_decisions where decision_id=p_decision;
 if found then
   if prior.customer_id<>p_customer or prior.approval_ref<>p_approval or prior.reason<>p_reason
   or (p_cutoff is not null and prior.cutoff<>p_cutoff) then raise exception 'Privacy decision conflicts.'; end if;
   return prior.receipt;
 end if;
 cutoff:=coalesce(p_cutoff,clock_timestamp());
 if cutoff>clock_timestamp() then raise exception 'A privacy cutoff cannot be in the future.'; end if;
 select * into c from public.customers where id=p_customer for update;
 if not found then raise exception 'Customer not found.'; end if;
 if exists(select 1 from public.orders where customer_id=p_customer and (status<>'Delivered' or paid_amount<total))
 or exists(select 1 from public.walkin_sales where customer_id=p_customer and paid_amount<total) then
   raise exception 'Active operations or unsettled balances require review before removal.';
 end if;
 if not p_execute then
   return jsonb_build_object('status','DRY_RUN','customer_id',p_customer,'cutoff',cutoff,
     'retained','Names, financial history, references and retry hashes');
 end if;
 update public.customers set contact_number=null,address=null
 where id=p_customer and updated_at<=cutoff;
 get diagnostics c_count=row_count;
 update public.orders set delivery_address=null where customer_id=p_customer and created_at<=cutoff;
 get diagnostics o_count=row_count;
 update public.audit_log set details=aq_private.redact_contact(details,p_customer)
 where created_at<=cutoff and details is distinct from aq_private.redact_contact(details,p_customer);
 get diagnostics a_count=row_count;
 update public.idempotency_keys set result=aq_private.redact_contact(result,p_customer)
 where created_at<=cutoff and result is distinct from aq_private.redact_contact(result,p_customer);
 get diagnostics r_count=row_count;
 receipt:=jsonb_build_object('status','EXECUTED','decision_id',p_decision,'customer_id',p_customer,
   'cutoff',cutoff,'customers',c_count,'orders',o_count,'audit_copies',a_count,'retry_copies',r_count);
 insert into aq_private.privacy_decisions(decision_id,customer_id,approval_ref,reason,cutoff,receipt)
 values(p_decision,p_customer,p_approval,p_reason,cutoff,receipt);
 perform public.audit(null,'privacy_contact_erasure','customer',p_customer::text,
   jsonb_build_object('decision_id',p_decision,'cutoff',cutoff));
 return receipt;
end $$;

-- Opening counts and account recovery stay outside ordinary app navigation.
create or replace function aq_private.initialize_inventory(p_filled integer,p_empty integer,p_approval text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare inv public.inventory;
begin
 if p_filled is null or p_empty is null or p_filled<0 or p_empty<0
 or coalesce(trim(p_approval),'')='' or char_length(p_approval)>160 then
   raise exception 'Approved nonnegative opening counts are required.';
 end if;
 select * into inv from public.inventory where id=1 for update;
 if not found or inv.physical_filled<>0 or inv.reserved_filled<>0 or inv.empty_gallons<>0
 or exists(select 1 from public.orders) or exists(select 1 from public.walkin_sales)
 or exists(select 1 from public.payments) or exists(select 1 from public.audit_log where action='initialize_inventory') then
   raise exception 'Opening stock is already initialized or operations have started.';
 end if;
 update public.inventory set physical_filled=p_filled,empty_gallons=p_empty,updated_at=clock_timestamp() where id=1;
 perform public.audit(null,'initialize_inventory','inventory','1',jsonb_build_object(
   'filled',p_filled,'empty',p_empty,'approval_ref',p_approval));
 return jsonb_build_object('initialized',true,'filled',p_filled,'empty',p_empty);
end $$;
create or replace function aq_private.revoke_actor_sessions(p_actor uuid,p_approval text)
returns jsonb language plpgsql security invoker set search_path='' as $$
declare changed integer;
begin
 if p_actor is null or not exists(select 1 from public.profiles where id=p_actor)
 or coalesce(trim(p_approval),'')='' or char_length(p_approval)>160 then
   raise exception 'An existing account and approved recovery reference are required.';
 end if;
 update aq_private.sessions set revoked_at=clock_timestamp() where actor=p_actor and revoked_at is null;
 get diagnostics changed=row_count;
 perform public.audit(null,'revoke_sessions','account',p_actor::text,
   jsonb_build_object('sessions',changed,'approval_ref',p_approval));
 return jsonb_build_object('revoked_sessions',changed,'managed_auth_revocation','REQUIRES_PROVIDER_ADMINISTRATOR');
end $$;

revoke all on all functions in schema aq_private from public,anon,authenticated,service_role;
revoke all on function public.idem_claim(uuid,uuid,text,jsonb) from public,anon,authenticated,service_role;
revoke all on function public.aq_session_touch(),public.aq_logout() from public,anon,service_role;
grant execute on function public.aq_session_touch(),public.aq_logout() to authenticated;
commit;
