\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid'),('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner'),('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal1')::text,true);
set local role authenticated;
do $$declare denied boolean:=false;begin
 begin perform public.aq_admin_update_staff(jsonb_build_object('operation','set_active','request_id',gen_random_uuid(),'payload_hash',repeat('a',64),'user_id','10000000-0000-4000-8000-000000000002','active',false));exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'AAL1 Owner changed Staff without managed MFA';end if;
 raise notice 'PASS: Owner AAL1 denied account mutation';
end $$;
reset role;
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
do $$declare r jsonb;begin
 r:=public.aq_admin_update_staff(jsonb_build_object('operation','update','request_id',gen_random_uuid(),'payload_hash',repeat('b',64),'user_id','10000000-0000-4000-8000-000000000002','username','renamed.staff','full_name','Renamed Synthetic','contact_number','09000000002','active',true));
 if r->>'user_id'<>'10000000-0000-4000-8000-000000000002' or r->>'username'<>'renamed.staff' or r->>'role'<>'staff' then raise exception 'Identity/role not preserved';end if;
 raise notice 'PASS: AAL2 account rename preserves stable Auth identity and Staff role';
end $$;
reset role;
set local role anon;
do $$declare denied boolean:=false;begin
 begin perform public.aq_query('{"resource":"customers"}');exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Anonymous protected read allowed';end if;
 raise notice 'PASS: anonymous protected read denied by grant';
end $$;
reset role;
update aq_private.sessions set last_seen_at=clock_timestamp()-interval '31 minutes';
set local role authenticated;
do $$begin
 begin perform public.aq_me();exception when insufficient_privilege then raise notice 'PASS: expired session protected read denied';return;end;
 raise exception 'Expired session accepted';
end $$;
rollback;
