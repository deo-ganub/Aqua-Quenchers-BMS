-- Synthetic fixture accounting only. No trained-model/probability claim; never production seed.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare x record;r jsonb;new_expense jsonb;p jsonb;begin
 for x in select * from (values('Fuel',50000),('Electricity',150000),('Water',100000),('Maintenance',120000),('Supplies',75000),('Salary',100000),('Others',30000)) t(category,cents) loop
  perform public.aq_command(jsonb_build_object('operation','create_expense','request_id',gen_random_uuid(),'business_date','2026-10-15','category',x.category,'amount_centavos',x.cents,'selection_source','manual'));
 end loop;
 r:=public.aq_query('{"resource":"expenses","start_date":"2026-10-15","end_date":"2026-10-15"}');
 if jsonb_array_length(r->'items')<>7 or (r->>'total_centavos')::bigint<>625000 then raise exception 'Baseline7/6250 mismatch';end if;
 p:=jsonb_build_object('operation','create_expense','request_id',gen_random_uuid(),'business_date','2026-10-15','category','Fuel','amount_centavos',75000,'remarks','gasolina para sa motor','selection_source','manual');
 new_expense:=public.aq_command(p);perform public.aq_command(p);
 r:=public.aq_query('{"resource":"expenses","start_date":"2026-10-15","end_date":"2026-10-15"}');
 if jsonb_array_length(r->'items')<>8 or (r->>'total_centavos')::bigint<>700000 or r->'items'->0->>'id'<>new_expense->>'id' then raise exception '8/7000/newest-first mismatch';end if;
 r:=public.aq_query('{"resource":"expenses","start_date":"2026-10-15","end_date":"2026-10-15","category":"Fuel"}');
 if jsonb_array_length(r->'items')<>2 or (r->>'total_centavos')::bigint<>125000 then raise exception 'Filtered sum mismatch';end if;
 raise notice 'PASS: manual no-remarks fixture7/6250 becomes8/7000 once; newest-first and filtered total reconcile. No model result asserted.';
end $$;
rollback;
