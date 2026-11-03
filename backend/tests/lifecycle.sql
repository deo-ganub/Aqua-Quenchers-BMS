\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
do $$
declare c jsonb;o jsonb;r jsonb; sale jsonb; key uuid; denied boolean; params jsonb;stages text[];event jsonb;
begin
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic','customer_type','household','contact_number','09000000003','address','Synthetic'));
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',10,'business_date','2026-10-15'));
 if (o->>'total_centavos')::bigint<>35000 then raise exception 'Incorrect initial price';end if;
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
 r:=public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',8));
 if (r->>'total_centavos')::bigint<>28000 or (r->>'shortfall')::int<>2 then raise exception 'Short pickup adjustment failed';end if;
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','refilling'));
 params:=jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','ready_for_delivery');
 perform public.aq_command(params);perform public.aq_command(params);
 r:=public.aq_query('{"resource":"inventory"}');
 if (r->>'reserved_filled')::int<>8 or (r->>'physical_filled')::int<>8 or (r->>'filled_available')::int<>0 then raise exception 'Reservation/conversion duplicated or incorrect';end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_name','Synthetic counter','quantity',1,'cash_centavos',3500));exception when others then denied:=true;end;
 if not denied then raise exception 'POS spent reserved stock';end if;
 params:=jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',3);
 r:=public.aq_command(params);perform public.aq_command(params);
 if r->>'status'<>'undelivered' or (r->>'reserved_quantity')::int<>5 or (r->>'total_centavos')::bigint<>28000 then raise exception 'Partial delivery failed';end if;
 r:=public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',5));
 if r->>'status'<>'delivered' or (r->>'reserved_quantity')::int<>0 then raise exception 'Final delivery failed';end if;
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',10000));
 perform public.aq_command(jsonb_build_object('operation','create_expense','request_id',gen_random_uuid(),'business_date','2026-10-15','category','Fuel','amount_centavos',75000,'selection_source','manual'));
 r:=public.aq_query('{"resource":"expenses","start_date":"2026-10-15","end_date":"2026-10-15"}');
 if (r->>'total_centavos')::bigint<>75000 or jsonb_array_length(r->'items')<>1 then raise exception 'Expense list/total failed';end if;
 r:=public.aq_query('{"resource":"report","start_date":"2026-10-15","end_date":"2026-10-15"}');
 if (r->>'revenue_centavos')::bigint<>28000 or (r->>'payments_received_centavos')::bigint<>0 or (r->>'net_income_centavos')::bigint<>-75000 or (r->>'outstanding_centavos')::bigint<>28000 then raise exception 'Business date/receipts/as-of definitions failed: %',r;end if;
 r:=public.aq_query(jsonb_build_object('resource','report','start_date',(clock_timestamp() at time zone 'Asia/Manila')::date,'end_date',(clock_timestamp() at time zone 'Asia/Manila')::date));
 if (r->>'payments_received_centavos')::bigint<>10000 or (r->>'outstanding_centavos')::bigint<>18000 then raise exception 'Receipt/current as-of calculation failed';end if;
 r:=public.aq_query(jsonb_build_object('resource','order','id',o->>'id'));
 select array_agg(value->>'status') into stages from jsonb_array_elements(r->'timeline');
 if stages is distinct from array['ordered','for_pickup','picked_up','refilling','ready_for_delivery','undelivered','delivered'] then raise exception 'Timeline missing/reordered/duplicated stages: %',stages;end if;
 for event in select value from jsonb_array_elements(r->'timeline') loop
  if (select count(*) from jsonb_object_keys(event))<>5 or event->>'occurred_at' is null or event->>'recorded_by_name'<>'Synthetic Owner' then raise exception 'Timeline fields not minimized/server-derived';end if;
 end loop;
 raise notice 'PASS: adjusted charge, conversion/reservation retry, reserved POS protection, partial/final delivery, Owner expenses, report date/receipt/as-of definitions and minimal exact-once ordered Owner timeline';
end $$;
reset role;
insert into auth.users values ('10000000-0000-4000-8000-000000000002','staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000002','staff.test','Synthetic Staff','09000000002','staff');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare orders jsonb;r jsonb;begin
 orders:=public.aq_query('{"resource":"orders"}');
 r:=public.aq_query(jsonb_build_object('resource','order','id',orders->'items'->0->>'id'));
 if r ? 'timeline' or r->>'reference' is null then raise exception 'Staff operational details/timeline scope incorrect';end if;
 raise notice 'PASS: Staff retains operational order details without Owner timeline evidence';
end $$;
rollback;
