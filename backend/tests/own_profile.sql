\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid'),('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner'),('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000002','iat',extract(epoch from clock_timestamp()),'aal','aal1')::text,true);
set local role authenticated;
do $$declare c jsonb;r jsonb;p jsonb;k text;denied boolean;begin
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic customer','customer_type','household','contact_number','09000000003','address','Synthetic'));
 p:=jsonb_build_object('operation','update_profile','request_id',gen_random_uuid(),'full_name','Updated Synthetic Staff','username','renamed.staff','contact_number','09000000002');
 r:=public.aq_command(p);
 if r->>'user_id'<>'10000000-0000-4000-8000-000000000002' or r->>'role'<>'staff' or r->>'username'<>'renamed.staff' then raise exception 'Own-profile identity/role changed';end if;
 r:=public.aq_query(jsonb_build_object('resource','customer','id',c->>'id'));
 if r->>'recorded_by'<>'10000000-0000-4000-8000-000000000002' then raise exception 'Username edit broke recorder identity';end if;
 foreach k in array array['user_id','role','active'] loop
  denied:=false;begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),k,'forged'));exception when others then denied:=true;end;
  if not denied then raise exception 'Own-profile changed forbidden field: %',k;end if;
 end loop;
 raise notice 'PASS: Staff own-profile username edit preserves Auth ID/transaction recorder; target/role/activation changes denied.';
end $$;
reset role;
do $$begin
 if (select username from aq_private.profiles where role='owner')<>'owner.test' then raise exception 'Staff modified Owner';end if;
end $$;
rollback;
