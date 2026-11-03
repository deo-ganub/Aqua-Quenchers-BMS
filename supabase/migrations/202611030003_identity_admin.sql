-- Apply after the main BMS migration. Hosted Auth remains the only password store.
-- The Edge Functions service key is server-only; grants here are intentionally narrow.
create table if not exists aq_private.login_windows (
 bucket text primary key, started_at timestamptz not null, attempts integer not null check(attempts>0)
);
create table if not exists aq_private.account_operations (
 request_id uuid primary key, actor_id uuid not null references auth.users(id),
 operation text not null check(operation in ('create','update','set_active')),
 payload_hash text not null check(payload_hash ~ '^[0-9a-f]{64}$'),
 payload jsonb not null, state text not null default 'pending' check(state in ('pending','completed','failed')),
 result jsonb, created_at timestamptz not null default clock_timestamp(),completed_at timestamptz
);
alter table aq_private.account_operations drop constraint if exists account_operations_state_check;
alter table aq_private.account_operations add constraint account_operations_state_check check(state in ('pending','completed','failed'));
create table if not exists aq_private.account_audit (
 id bigint generated always as identity primary key, actor_id uuid not null,
 target_id uuid not null, request_id uuid not null unique, action text not null,
 before_value jsonb, after_value jsonb not null, recorded_at timestamptz not null default clock_timestamp()
);
revoke all on aq_private.login_windows,aq_private.account_operations,aq_private.account_audit from public,anon,authenticated;

create or replace function public.aq_auth_login_attempt(p_username_key text,p_source_key text) returns boolean
language plpgsql security definer set search_path='' as $$
declare k text; n integer; started timestamptz; allowed boolean:=true;
begin
 if p_username_key !~ '^[0-9a-f]{64}$' or p_source_key !~ '^[0-9a-f]{64}$' then raise exception 'Invalid request.';end if;
 -- Lock both independent buckets in stable key order. Inactive/unknown accounts also count.
 for k in select v from (values ('u:'||p_username_key),('s:'||p_source_key)) t(v) order by v loop
  insert into aq_private.login_windows values(k,clock_timestamp(),1)
  on conflict(bucket) do update set
    attempts=case when aq_private.login_windows.started_at < clock_timestamp()-interval '15 minutes' then 1 else aq_private.login_windows.attempts+1 end,
    started_at=case when aq_private.login_windows.started_at < clock_timestamp()-interval '15 minutes' then clock_timestamp() else aq_private.login_windows.started_at end
  returning attempts into n;
  if n > (case when left(k,2)='u:' then 10 else 60 end) then allowed:=false;end if;
 end loop;
 -- Bounded cleanup; no source IP or raw username is stored.
 delete from aq_private.login_windows where bucket in(select bucket from aq_private.login_windows where started_at<clock_timestamp()-interval '1 day' limit 200);
 return allowed;
end $$;
revoke all on function public.aq_auth_login_attempt(text,text) from public,anon,authenticated;
grant execute on function public.aq_auth_login_attempt(text,text) to service_role;

create or replace function public.aq_admin_resolve_username(p_username text) returns jsonb
language sql security definer stable set search_path='' as $$
 select jsonb_build_object('user_id',p.user_id,'email',u.email)
 from aq_private.profiles p join auth.users u on u.id=p.user_id
 where p.username=lower(btrim(p_username)) and p.active limit 1;
$$;
revoke all on function public.aq_admin_resolve_username(text) from public,anon,authenticated;
grant execute on function public.aq_admin_resolve_username(text) to service_role;

create or replace function aq_private.admin_actor() returns uuid
language plpgsql security definer set search_path='' as $$
declare actor uuid;
begin
 actor:=aq_private.require_actor(true);
 if coalesce(auth.jwt()->>'aal','')<>'aal2' then raise exception using errcode='42501',message='Enter a valid verification code.';end if;
 return actor;
end $$;
revoke all on function aq_private.admin_actor() from public,anon,authenticated;

create or replace function aq_private.validate_staff_payload(p_request jsonb) returns void
language plpgsql set search_path='' as $$
begin
 if jsonb_typeof(p_request)<>'object' or exists(select 1 from jsonb_object_keys(p_request) k where k not in ('request_id','operation','payload_hash','user_id','full_name','username','contact_number','active')) then raise exception 'Invalid request.';end if;
 if (p_request->>'payload_hash') !~ '^[0-9a-f]{64}$' or p_request->>'request_id' is null then raise exception 'Invalid request.';end if;
 if p_request->>'operation' not in ('create','update','set_active') then raise exception 'Invalid request.';end if;
 if p_request->>'operation'<>'set_active' then
  if coalesce(length(btrim(p_request->>'full_name')),0)=0 or length(p_request->>'full_name')>160 or coalesce(p_request->>'username','') !~ '^[a-z0-9][a-z0-9_.-]{2,39}$' or coalesce(p_request->>'contact_number','') !~ '^[0-9]{11}$' then raise exception 'Invalid staff details.';end if;
 end if;
 if p_request ? 'active' and jsonb_typeof(p_request->'active')<>'boolean' then raise exception 'Invalid request.';end if;
 if p_request->>'operation'='set_active' and not(p_request?'active') then raise exception 'Invalid request.';end if;
end $$;
revoke all on function aq_private.validate_staff_payload(jsonb) from public,anon,authenticated;

create or replace function public.aq_admin_prepare_staff(p_request jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid; job aq_private.account_operations; rid uuid;
begin
 actor:=aq_private.admin_actor(); perform aq_private.validate_staff_payload(p_request);
 if p_request->>'operation'<>'create' then raise exception 'Invalid request.';end if;
 rid:=(p_request->>'request_id')::uuid;
 -- Serialize account/profile writes to eliminate the username ownership race.
 perform pg_advisory_xact_lock(3841952);
 select * into job from aq_private.account_operations where request_id=rid for update;
 if found then
  if job.actor_id<>actor or job.payload_hash<>p_request->>'payload_hash' then raise exception using errcode='23505',message='Request ID conflicts with another request.';end if;
  return jsonb_build_object('state',job.state,'email',rid::text||'@identity.aquaquenchers.invalid','result',job.result);
 end if;
 if exists(select 1 from aq_private.profiles where username=p_request->>'username') or exists(select 1 from aq_private.account_operations where state='pending' and payload->>'username'=p_request->>'username') then raise exception using errcode='23505',message='Username already exists.';end if;
 insert into aq_private.account_operations(request_id,actor_id,operation,payload_hash,payload) values(rid,actor,'create',p_request->>'payload_hash',p_request);
 return jsonb_build_object('state','pending','email',rid::text||'@identity.aquaquenchers.invalid');
end $$;
revoke all on function public.aq_admin_prepare_staff(jsonb) from public,anon;
grant execute on function public.aq_admin_prepare_staff(jsonb) to authenticated;

create or replace function public.aq_admin_auth_identity(p_request_id uuid) returns jsonb
language sql security definer stable set search_path='' as $$
 select jsonb_build_object('user_id',u.id) from auth.users u
 join aq_private.account_operations o on u.email=o.request_id::text||'@identity.aquaquenchers.invalid'
 where o.request_id=p_request_id and o.operation='create';
$$;
revoke all on function public.aq_admin_auth_identity(uuid) from public,anon,authenticated;
grant execute on function public.aq_admin_auth_identity(uuid) to service_role;

create or replace function public.aq_admin_finish_staff(p_request_id uuid,p_user_id uuid,p_payload_hash text) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid; job aq_private.account_operations; v_result jsonb;
begin
 actor:=aq_private.admin_actor(); perform pg_advisory_xact_lock(3841952);
 select * into job from aq_private.account_operations where request_id=p_request_id for update;
 if not found or job.actor_id<>actor or job.payload_hash<>p_payload_hash or job.operation<>'create' then raise exception 'Invalid request.';end if;
 if job.state='completed' then return job.result;end if;
 if not exists(select 1 from auth.users where id=p_user_id and email=p_request_id::text||'@identity.aquaquenchers.invalid') then raise exception 'Identity does not match prepared account.';end if;
 insert into aq_private.profiles(user_id,username,full_name,contact_number,role,active)
 values(p_user_id,job.payload->>'username',job.payload->>'full_name',job.payload->>'contact_number','staff',coalesce((job.payload->>'active')::boolean,true)) returning to_jsonb(profiles) into v_result;
 insert into aq_private.account_audit(actor_id,target_id,request_id,action,after_value) values(actor,p_user_id,p_request_id,'staff_created',v_result);
 update aq_private.account_operations set state='completed',result=v_result,completed_at=clock_timestamp() where request_id=p_request_id;
 return v_result;
end $$;
revoke all on function public.aq_admin_finish_staff(uuid,uuid,text) from public,anon;
grant execute on function public.aq_admin_finish_staff(uuid,uuid,text) to authenticated;

create or replace function public.aq_admin_update_staff(p_request jsonb) returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid; target uuid; rid uuid; old_value jsonb; v_result jsonb; job aq_private.account_operations;
begin
 actor:=aq_private.admin_actor();perform aq_private.validate_staff_payload(p_request);
 if p_request->>'operation' not in ('update','set_active') then raise exception 'Invalid request.';end if;
 target:=(p_request->>'user_id')::uuid;rid:=(p_request->>'request_id')::uuid;perform pg_advisory_xact_lock(3841952);
 select * into job from aq_private.account_operations where request_id=rid for update;
 if found then
  if job.actor_id<>actor or job.payload_hash<>p_request->>'payload_hash' then raise exception using errcode='23505',message='Request ID conflicts with another request.';end if;
  return job.result;
 end if;
 select to_jsonb(p) into old_value from aq_private.profiles p where user_id=target and role='staff' for update;
 if not found then raise exception using errcode='42501',message='Staff account unavailable.';end if;
 if p_request->>'operation'='update' then
  if exists(select 1 from aq_private.account_operations where state='pending' and payload->>'username'=p_request->>'username') then raise exception using errcode='23505',message='Username already exists.';end if;
  update aq_private.profiles set username=p_request->>'username',full_name=p_request->>'full_name',contact_number=p_request->>'contact_number',active=coalesce((p_request->>'active')::boolean,active),updated_at=clock_timestamp() where user_id=target returning to_jsonb(profiles) into v_result;
 else
  update aq_private.profiles set active=(p_request->>'active')::boolean,updated_at=clock_timestamp() where user_id=target returning to_jsonb(profiles) into v_result;
 end if;
 insert into aq_private.account_operations(request_id,actor_id,operation,payload_hash,payload,state,result,completed_at) values(rid,actor,p_request->>'operation',p_request->>'payload_hash',p_request,'completed',v_result,clock_timestamp());
 insert into aq_private.account_audit(actor_id,target_id,request_id,action,before_value,after_value) values(actor,target,rid,'staff_'||(p_request->>'operation'),old_value,v_result);
 return v_result;
end $$;
revoke all on function public.aq_admin_update_staff(jsonb) from public,anon;
grant execute on function public.aq_admin_update_staff(jsonb) to authenticated;

-- Newly added private tables also receive explicit default-deny RLS.
alter table aq_private.login_windows enable row level security;
alter table aq_private.account_operations enable row level security;
alter table aq_private.account_audit enable row level security;

-- Only the privileged handler may close a definitively rejected Auth preparation.
-- An existing Auth identity always requires reconciliation, never a false failed marker.
create or replace function public.aq_admin_fail_staff(p_request_id uuid,p_payload_hash text) returns jsonb
language plpgsql security definer set search_path='' as $$
<<aq_admin_fail_staff>>
declare job aq_private.account_operations; result jsonb;
begin
 perform pg_advisory_xact_lock(3841952);
 select * into job from aq_private.account_operations where request_id=p_request_id for update;
 if not found or job.payload_hash is distinct from p_payload_hash or job.operation<>'create' then raise exception 'Invalid request.';end if;
 if job.state='completed' or exists(select 1 from auth.users where email=p_request_id::text||'@identity.aquaquenchers.invalid') then raise exception 'Account creation requires reconciliation.';end if;
 if job.state='failed' then return job.result;end if;
 result:=jsonb_build_object('state','failed','reason','AUTH_REJECTED');
 update aq_private.account_operations set state='failed',result=aq_admin_fail_staff.result,completed_at=clock_timestamp() where request_id=p_request_id;
 insert into aq_private.audit_events(actor_id,action,request_id,after_state,reason) values(job.actor_id,'staff_creation_rejected',p_request_id,result,'Managed Auth definitively rejected account details; no identity exists.');
 return result;
end $$;
revoke all on function public.aq_admin_fail_staff(uuid,text) from public,anon,authenticated;
grant execute on function public.aq_admin_fail_staff(uuid,text) to service_role;
