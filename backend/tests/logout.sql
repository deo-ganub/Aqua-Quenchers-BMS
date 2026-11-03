\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
select public.aq_me();select public.aq_logout();select public.aq_logout();
do $$begin
 begin perform public.aq_me();exception when insufficient_privilege then raise notice 'PASS: revoked app session denied even with otherwise valid JWT claims';return;end;
 raise exception 'Logged-out session read protected data';end $$;
reset role;
do $$begin
 if (select count(*) from aq_private.audit_events where action='session_logout')<>1 then raise exception 'Logout retry duplicated audit';end if;
end $$;
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000002','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
select public.aq_me();
reset role;
update aq_private.profiles set active=false;
set local role authenticated;
select public.aq_logout();
reset role;
do $$begin
 if (select count(*) from aq_private.sessions where revoked_at is not null)<>2 then raise exception 'Inactive account could not end its session';end if;
 raise notice 'PASS: fresh sign-in session works, inactive caller can logout, and revocation/audit remain exactly once';
end $$;
rollback;
