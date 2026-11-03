\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
do $$declare r jsonb;denied boolean;begin
 r:=public.aq_admin_prepare_staff(jsonb_build_object('operation','create','request_id','30000000-0000-4000-8000-000000000001','payload_hash',repeat('a',64),'username','reserved.staff','full_name','Synthetic Staff','contact_number','09000000002','active',true));
 if r->>'state'<>'pending' then raise exception 'Alias not reserved';end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','update_profile','request_id',gen_random_uuid(),'username','reserved.staff','full_name','Synthetic Owner','contact_number','09000000001'));exception when others then if sqlerrm='Username already exists.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Self-profile claimed a pending Staff alias';end if;
 r:=public.aq_me();if r->>'username'<>'owner.test' then raise exception 'Rejected edit modified profile';end if;
end $$;
reset role;
set local role service_role;
select public.aq_admin_fail_staff('30000000-0000-4000-8000-000000000001',repeat('a',64));
reset role;
set local role authenticated;
do $$declare r jsonb;begin
 r:=public.aq_command(jsonb_build_object('operation','update_profile','request_id',gen_random_uuid(),'username','reserved.staff','full_name','Synthetic Owner','contact_number','09000000001'));
 if r->>'user_id'<>'10000000-0000-4000-8000-000000000001' or r->>'username'<>'reserved.staff' then raise exception 'Definitively released alias unavailable or identity changed';end if;
 raise notice 'PASS: checked self-profile cannot take pending Staff alias; only protected definitive failure releases it, stable Auth identity preserved.';
end $$;
rollback;
