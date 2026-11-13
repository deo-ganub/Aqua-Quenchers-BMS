-- Real checked POS/payment data; customer-scoped credit lists must never mix subjects.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
update aq_private.inventory set physical_filled=5,initialized=true;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare a jsonb;b jsonb;sale jsonb;p jsonb;r jsonb;denied boolean;begin
 a:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic A','customer_type','household','contact_number','09000000003','address','Synthetic'));
 b:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic B','customer_type','dealer','contact_number','09000000004','address','Synthetic'));
 p:=jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_id',a->>'id','customer_name','Synthetic A','quantity',1,'cash_centavos',0);
 denied:=false;begin perform public.aq_command(p);exception when others then denied:=true;end;
 if not denied then raise exception 'Unconfirmed zero-cash credit saved';end if;
 sale:=public.aq_command(p||'{"confirm_credit":true}'::jsonb);
 if sale->>'payment_status'<>'unpaid' or (sale->>'paid_centavos')::bigint<>0 or sale->>'recorded_by_name'<>'Synthetic Owner' then raise exception 'Zero-cash sale/recorder mismatch';end if;
 perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'customer_id',b->>'id','customer_name','Synthetic B','cash_centavos',500,'confirm_credit',true));
 perform public.aq_command((p-'customer_id')||jsonb_build_object('request_id',gen_random_uuid(),'customer_name','Synthetic profileless','cash_centavos',1000,'confirm_credit',true));
 r:=public.aq_query(jsonb_build_object('resource','sales','customer_id',a->>'id','unpaid_only',true));
 if jsonb_array_length(r->'items')<>1 or r->'items'->0->>'id'<>sale->>'id' then raise exception 'Other customer/profileless credit leaked into selected customer context';end if;
 r:=public.aq_query('{"resource":"sales","unpaid_only":true}');
 if jsonb_array_length(r->'items')<>3 then raise exception 'Global named credit lookup lost records';end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',sale->>'reference','amount_centavos',3501));exception when others then if sqlerrm='Payment cannot exceed the ₱35.00 balance.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Overpayment/message incorrect';end if;
 p:=jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',sale->>'reference','amount_centavos',3500);
 r:=public.aq_command(p);if public.aq_command(p) is distinct from r then raise exception 'Receipt retry changed result';end if;
 if r->>'payment_status'<>'paid' or (r->>'balance_centavos')::bigint<>0 or r->>'recorded_by_name'<>'Synthetic Owner' then raise exception 'Selected-reference settlement/recorder failed';end if;
 r:=public.aq_query(jsonb_build_object('resource','sales','customer_id',a->>'id','unpaid_only',true));
 if jsonb_array_length(r->'items')<>0 then raise exception 'Settled reference still appears unpaid';end if;
 raise notice 'PASS: explicit zero-cash credit, own-customer/global named credit lookup, exact overpayment feedback, recorder and exact-once selected receipt.';
end $$;
reset role;
do $$begin
 if (select physical_filled from aq_private.inventory)<>2 or (select count(*) from aq_private.sales)<>3 or (select count(*) from aq_private.payments)<>3 or (select sum(amount_centavos) from aq_private.payments)<>5000 then raise exception 'Rejected/retried credit/payment changed stock or receipt totals';end if;
end $$;
rollback;
