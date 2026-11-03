\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
select public.aq_admin_prepare_staff(jsonb_build_object('operation','create','request_id','40000000-0000-4000-8000-000000000001','payload_hash',repeat('a',64),'username','synthetic.staff','full_name','Synthetic Staff','contact_number','09000000002'));
do $$begin
 begin perform public.aq_admin_fail_staff('40000000-0000-4000-8000-000000000001',repeat('a',64));exception when insufficient_privilege then raise notice 'PASS: ordinary Owner cannot mark a preparation failed';return;end;
 raise exception 'Owner bypassed service-only failure control';end $$;
reset role;
set local role service_role;
select public.aq_admin_fail_staff('40000000-0000-4000-8000-000000000001',repeat('a',64));
select public.aq_admin_fail_staff('40000000-0000-4000-8000-000000000001',repeat('a',64));
reset role;
do $$begin
 if (select state from aq_private.account_operations where request_id='40000000-0000-4000-8000-000000000001')<>'failed' or (select count(*) from aq_private.audit_events where action='staff_creation_rejected')<>1 then raise exception 'Failure marking duplicated or did not close job';end if;
end $$;
set local role authenticated;
select public.aq_admin_prepare_staff(jsonb_build_object('operation','create','request_id','40000000-0000-4000-8000-000000000002','payload_hash',repeat('b',64),'username','synthetic.staff','full_name','Synthetic Staff','contact_number','09000000002'));
reset role;
insert into auth.users values ('10000000-0000-4000-8000-000000000002','40000000-0000-4000-8000-000000000002@identity.aquaquenchers.invalid');
set local role service_role;
do $$begin
 begin perform public.aq_admin_fail_staff('40000000-0000-4000-8000-000000000002',repeat('b',64));exception when others then if sqlerrm='Account creation requires reconciliation.' then raise notice 'PASS: existing Auth identity cannot be falsely marked failed';return;else raise;end if;end;
 raise exception 'Existing identity incorrectly marked failed';end $$;
reset role;
set local role authenticated;
select public.aq_admin_finish_staff('40000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000002',repeat('b',64));
reset role;
do $$begin
 if (select state from aq_private.account_operations where request_id='40000000-0000-4000-8000-000000000002')<>'completed' or (select count(*) from aq_private.profiles where role='staff')<>1 then raise exception 'Prepared identity did not reconcile once';end if;
 raise notice 'PASS: corrected request can reuse released alias and managed identity completes once';end $$;
rollback;
