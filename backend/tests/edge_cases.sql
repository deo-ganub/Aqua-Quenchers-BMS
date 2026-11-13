\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
update aq_private.inventory set physical_filled=4,initialized=true;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
do $$declare sale jsonb;r jsonb;c jsonb;o jsonb;p jsonb;bad jsonb;denied boolean;n jsonb;expected text;begin
 p:=jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_name','Profileless credit','quantity',2,'cash_centavos',1000,'confirm_credit',true);
 sale:=public.aq_command(p);perform public.aq_command(p);
 if sale->>'payment_status'<>'partially_paid' or (sale->>'balance_centavos')::bigint<>6000 or (sale->>'change_centavos')::bigint<>0 then raise exception 'Profileless credit mismatch %',sale;end if;
 foreach bad in array array['0'::jsonb,'-1'::jsonb,'6001'::jsonb,'"nonnumeric"'::jsonb] loop
  expected:=case when bad='6001'::jsonb then 'Payment cannot exceed the ₱60.00 balance.' else 'Enter an amount greater than zero and not above the balance.' end;
  denied:=false;begin perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',sale->>'reference','amount_centavos',bad));exception when others then if sqlerrm=expected then denied:=true;else raise;end if;end;
  if not denied then raise exception 'Invalid payment accepted';end if;
 end loop;
 p:=jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',sale->>'reference','amount_centavos',6000);
 r:=public.aq_command(p);perform public.aq_command(p);
 if r->>'payment_status'<>'paid' or (r->>'balance_centavos')::bigint<>0 or r->>'recorded_by_name'<>'Synthetic Owner' then raise exception 'Reference settlement/recorder failed';end if;
 p:=jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_name','Cash change','quantity',1,'cash_centavos',10000);
 r:=public.aq_command(p);perform public.aq_command(p);
 if (r->>'change_centavos')::bigint<>6500 or (r->>'paid_centavos')::bigint<>3500 then raise exception 'Gross tender recognized as receipts';end if;
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic prepaid','customer_type','household','contact_number','09000000003','address','Synthetic'));
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',10));
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','delivered'));exception when others then denied:=true;end;
 if not denied then raise exception 'Skipped order lifecycle accepted';end if;
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',35000));
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','reschedule_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','scheduled_date',((clock_timestamp() at time zone 'Asia/Manila')::date-1)));exception when others then denied:=true;end;
 if not denied then raise exception 'Past reschedule accepted';end if;
 perform public.aq_command(jsonb_build_object('operation','reschedule_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','scheduled_date',(clock_timestamp() at time zone 'Asia/Manila')::date));
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
 foreach n in array array['0'::jsonb,'"abc"'::jsonb,'1.5'::jsonb,'11'::jsonb,'8'::jsonb] loop
  denied:=false;begin perform public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',n));exception when others then denied:=true;end;
  if not denied then raise exception 'Invalid/overcollected/below-paid pickup accepted: %',n;end if;
 end loop;
 p:=jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',10);perform public.aq_command(p);perform public.aq_command(p);
 r:=public.aq_query('{"resource":"inventory"}');if (r->>'empty_quantity')::bigint<>10 then raise exception 'Pickup double counted';end if;
 raise notice 'PASS: profileless credit/selected-reference settlement, invalid payments, cash/change receipts, skipped lifecycle, reschedule boundaries, overcollection and paid-total protection, pickup retries';
end $$;
reset role;
do $$begin
 if (select sum(amount_centavos) from aq_private.payments)<>45500 then raise exception 'Duplicate or incorrect receipts';end if;
 if (select count(*) from aq_private.sales)<>2 then raise exception 'Duplicate POS sale';end if;
 raise notice 'PASS: exact total retained receipts and no duplicate POS records';
end $$;
rollback;
