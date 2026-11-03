-- Customer identity normalization and date-filtered history use different date axes.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,true);
do $$declare c jsonb;o jsonb;p jsonb;day integer;denied boolean;begin
 p:=jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic history','customer_type','household','contact_number','0900 000 0003','address','Synthetic');
 c:=public.aq_command(p);
 if c->>'contact_number'<>'09000000003' then raise exception 'Display spaces not normalized';end if;
 perform set_config('aq_test.customer_id',c->>'id',true);
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'contact_number','09000000003'));exception when others then if sqlerrm='Customer already exists.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Formatted duplicate contact accepted';end if;
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'customer_type',''));exception when others then if sqlerrm='Choose Household, Dealer, or Walk-in.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Unselected type message missing';end if;
 for day in 14..16 loop
  o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',1,'business_date',make_date(2026,10,day)));
  perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',500));
 end loop;
 c:=public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'contact_number','09000000004'));
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',1,'business_date','2026-10-15'));
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',500));
end $$;
-- Deliberately controlled receipt timestamps in this rollback-only fixture.
update aq_private.payments p set received_at=case o.business_date when '2026-10-14'::date then '2026-10-14T15:59:59Z'::timestamptz when '2026-10-15'::date then '2026-10-14T16:00:00Z'::timestamptz else '2026-10-15T15:59:59Z'::timestamptz end from aq_private.orders o where o.id=p.order_id;
set local role authenticated;
do $$declare filters jsonb;r jsonb;denied boolean;begin
 filters:=jsonb_build_object('customer_id',current_setting('aq_test.customer_id'),'start_date','2026-10-15','end_date','2026-10-15');
 r:=public.aq_query(filters||'{"resource":"orders"}'::jsonb);
 if jsonb_array_length(r->'items')<>1 or r->'items'->0->>'business_date'<>'2026-10-15' then raise exception 'Business-date/customer filter failed';end if;
 r:=public.aq_query(filters||'{"resource":"payments"}'::jsonb);
 if jsonb_array_length(r->'items')<>2 then raise exception 'Inclusive Manila receipt-date/customer filter failed';end if;
 r:=public.aq_query(jsonb_build_object('resource','orders','customer_id',current_setting('aq_test.customer_id'),'start_date','','end_date',''));
 if jsonb_array_length(r->'items')<>3 then raise exception 'Cleared history did not restore all customer orders';end if;
 denied:=false;begin perform public.aq_query(filters||'{"resource":"orders","start_date":"2026-10-16"}'::jsonb);exception when others then if sqlerrm='The end date cannot be earlier than the start date.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Invalid history date range accepted';end if;
 raise notice 'PASS: normalized customer contact/duplicates/type validation; scoped business-date orders and inclusive Manila receipt history; clear/invalid date ranges.';
end $$;
rollback;
