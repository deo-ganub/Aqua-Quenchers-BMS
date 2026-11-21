-- Walk-in counter orders: customer-owned gallons, no station stock, pay at drop-off.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('12000000-0000-4000-8000-000000000001','walkin-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('12000000-0000-4000-8000-000000000001','walkin.staff','Fixture Staff','09000000301','staff');
select set_config('request.jwt.claim.sub','12000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare w jsonb;d jsonb;r jsonb;home jsonb;denied boolean;req jsonb;begin
 -- Claim at the counter, paid in full with change. Zero station stock is fine.
 req:=jsonb_build_object('operation','create_counter_order','request_id',gen_random_uuid(),'customer_id','','customer_name','Walk-in Ana','contact_number','','address','','quantity',2,'cash_centavos',10000,'confirm_credit',false);
 w:=public.aq_command(req);
 if w->>'status'<>'received_at_counter' or w->>'fulfillment'<>'claim' or w->>'reference' not like 'WI-%' or (w->>'change_centavos')::bigint<>3000 or w->>'payment_status'<>'paid' then raise exception 'Bad counter order %',w;end if;
 if public.aq_command(req)<>w then raise exception 'Retry not idempotent';end if;
 if jsonb_array_length(public.aq_query(jsonb_build_object('resource','orders','search','WI-'))->'items')<>1 then raise exception 'Retry duplicated the order';end if;
 home:=public.aq_query(jsonb_build_object('resource','home'));
 if (home->>'at_station')::int<>2 or (home->>'refilling')::int<>2 then raise exception 'Gallons at station wrong %',home;end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',w->>'id','status','claimed','quantity',null,'scheduled_date',null));exception when others then denied:=sqlerrm='Follow the next eligible order stage.';end;
 if not denied then raise exception 'Claimed before refilling';end if;
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',w->>'id','status','refilling','quantity',null,'scheduled_date',null));
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',w->>'id','status','ready_for_delivery','quantity',null,'scheduled_date',null));
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',w->>'id','status','','quantity',2,'scheduled_date',null));exception when others then denied:=sqlerrm='Follow the next eligible order stage.';end;
 if not denied then raise exception 'Claim order was delivered';end if;
 r:=public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',w->>'id','status','claimed','quantity',null,'scheduled_date',null));
 if r->>'status'<>'claimed' or (r->>'delivered_quantity')::int<>2 then raise exception 'Claim failed %',r;end if;
 -- Delivery walk-in: partial cash at drop-off; the rest is credit only when confirmed.
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','create_counter_order','request_id',gen_random_uuid(),'customer_id',null,'customer_name','Walk-in Ben','contact_number','09000000302','address','Blk 1 Lot 2','quantity',3,'cash_centavos',5000,'confirm_credit',false));exception when others then denied:=sqlerrm='Confirm recording the remaining balance as credit.';end;
 if not denied then raise exception 'Credit without confirmation';end if;
 d:=public.aq_command(jsonb_build_object('operation','create_counter_order','request_id',gen_random_uuid(),'customer_id',null,'customer_name','Walk-in Ben','contact_number','09000000302','address','Blk 1 Lot 2','quantity',3,'cash_centavos',5000,'confirm_credit',true));
 if d->>'fulfillment'<>'deliver' or (d->>'balance_centavos')::bigint<>5500 or d->>'payment_status'<>'partially_paid' then raise exception 'Bad delivery walk-in %',d;end if;
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',d->>'id','status','refilling','quantity',null,'scheduled_date',null));
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',d->>'id','status','ready_for_delivery','quantity',null,'scheduled_date',null));
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',d->>'id','status','claimed','quantity',null,'scheduled_date',null));exception when others then denied:=true;end;
 if not denied then raise exception 'Delivery order was claimed';end if;
 r:=public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',d->>'id','status','','quantity',3,'scheduled_date',null));
 if r->>'status'<>'delivered' then raise exception 'Delivery failed %',r;end if;
 r:=public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',d->>'reference','amount_centavos',5500));
 if r->>'payment_status'<>'paid' then raise exception 'Balance payment failed';end if;
 if (public.aq_query(jsonb_build_object('resource','home'))->>'at_station')::int<>0 then raise exception 'Finished orders still at station';end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_id','','customer_name','X','quantity',1,'cash_centavos',3500,'confirm_credit',false));exception when others then denied:=true;end;
 if not denied then raise exception 'Retired stock sale still accepted';end if;
 raise notice 'PASS: walk-in counter orders claim/deliver paths, drop-off payment and credit, idempotent retry, gallons-at-station counts, retired stock sale.';
end $$;
rollback;
