\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid'),('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner'),('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal1')::text,true);
set local role authenticated;
do $$
declare r jsonb; retry jsonb; payload jsonb; denied boolean;
begin
 payload:='{"operation":"create_customer","request_id":"30000000-0000-4000-8000-000000000001","name":"Synthetic Customer","customer_type":"household","contact_number":"09000000003","address":"Synthetic address"}';
 r:=public.aq_command(payload); retry:=public.aq_command(payload);
 if r<>retry then raise exception 'Idempotent retry differed';end if;
 denied:=false;
 begin perform public.aq_command(payload||'{"name":"Different"}'::jsonb); exception when others then denied:=true;end;
 if not denied then raise exception 'Changed payload accepted';end if;
 denied:=false;
 begin perform public.aq_command(payload||'{"request_id":"30000000-0000-4000-8000-000000000002","contact_number":"090 00000003"}'::jsonb);exception when others then denied:=true;end;
 if not denied then raise exception 'Whitespace phone accepted';end if;
 denied:=false;
 begin perform public.aq_query('{"resource":"expenses"}');exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Staff expense read allowed';end if;
 denied:=false;
 begin perform public.aq_admin_prepare_staff('{"operation":"create"}');exception when insufficient_privilege then denied:=true;end;
 if not denied then raise exception 'Staff administration allowed';end if;
 raise notice 'PASS: retry equality, payload conflict, exact phone validation, Staff expense and administration denial';
end $$;
reset role;
update aq_private.profiles set active=false where role='staff';
set local role authenticated;
do $$ begin
 begin perform public.aq_me();exception when insufficient_privilege then raise notice 'PASS: existing inactive session rejected';return;end;
 raise exception 'Inactive session accepted';
end $$;
rollback;
