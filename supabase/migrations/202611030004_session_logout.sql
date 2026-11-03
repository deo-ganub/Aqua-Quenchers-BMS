-- App-session revocation is checked even while an old managed access JWT has not expired.
begin;
alter table aq_private.sessions add column if not exists revoked_at timestamptz;
create or replace function aq_private.require_actor(p_owner boolean default false) returns uuid language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); p aq_private.profiles; sid uuid; seen timestamptz; revoked timestamptz; claims jsonb:=auth.jwt();
begin
 select * into p from aq_private.profiles where user_id=a;
 if a is null or p.user_id is null or not p.active or (p_owner and p.role<>'owner') then
  raise exception using errcode='42501',message='Access denied.';
 end if;
 begin sid:=(claims->>'session_id')::uuid; exception when others then sid:=null; end;
 if sid is null then raise exception using errcode='42501',message='Session expired. Sign in again.'; end if;
 perform pg_advisory_xact_lock(714,hashtext(sid::text));
 select last_seen_at,revoked_at into seen,revoked from aq_private.sessions where session_id=sid and user_id=a for update;
 if found then
  if revoked is not null or seen <= clock_timestamp()-interval '30 minutes' then raise exception using errcode='42501',message='Session expired. Sign in again.'; end if;
  update aq_private.sessions set last_seen_at=clock_timestamp() where session_id=sid;
 else
  if (claims->>'iat') is null or to_timestamp((claims->>'iat')::double precision) <= clock_timestamp()-interval '30 minutes' then
   raise exception using errcode='42501',message='Session expired. Sign in again.';
  end if;
  insert into aq_private.sessions(session_id,user_id) values(sid,a);
 end if;
 return a;
end $$;

create or replace function public.aq_logout() returns jsonb
language plpgsql security definer set search_path='' as $$
declare actor uuid:=auth.uid(); sid uuid; changed integer;
begin
 begin sid:=(auth.jwt()->>'session_id')::uuid;exception when others then sid:=null;end;
 if actor is null or sid is null then raise exception using errcode='42501',message='Access denied.';end if;
 if not exists(select 1 from aq_private.profiles where user_id=actor) then return jsonb_build_object('logged_out',true);end if;
 perform pg_advisory_xact_lock(714,hashtext(sid::text));
 insert into aq_private.sessions(session_id,user_id,revoked_at) values(sid,actor,clock_timestamp())
 on conflict(session_id) do update set revoked_at=clock_timestamp() where aq_private.sessions.user_id=excluded.user_id and aq_private.sessions.revoked_at is null;
 get diagnostics changed=row_count;
 if not exists(select 1 from aq_private.sessions where session_id=sid and user_id=actor) then raise exception using errcode='42501',message='Access denied.';end if;
 if changed>0 then insert into aq_private.audit_events(actor_id,action,entity_id) values(actor,'session_logout',sid);end if;
 return jsonb_build_object('logged_out',true);
end $$;
revoke all on function public.aq_logout() from public,anon;
grant execute on function public.aq_logout() to authenticated;
revoke all on function aq_private.require_actor(boolean) from public,anon,authenticated;
commit;
