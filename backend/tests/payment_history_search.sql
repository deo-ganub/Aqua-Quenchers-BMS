\set ON_ERROR_STOP on
begin;
-- Disposable rollback fixture only; no live station or controlled opening seed.
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@history.invalid'),('10000000-0000-4000-8000-000000000002','staff@history.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values
('10000000-0000-4000-8000-000000000001','owner.history','SYNTHETIC History Owner','09000000001','owner'),
('10000000-0000-4000-8000-000000000002','staff.history','SYNTHETIC History Staff','09000000002','staff');
update aq_private.inventory set physical_filled=1;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
set local role authenticated;
do $$
declare c jsonb;other_customer jsonb;o jsonb;other_order jsonb;sale jsonb;all_receipts jsonb;stock jsonb;r jsonb;blocked boolean:=false;day text:=(clock_timestamp() at time zone 'Asia/Manila')::date::text;
begin
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','SYNTHETIC Needle Name','customer_type','household','contact_number','09000000011','address','SYNTHETIC HISTORY'));
 other_customer:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','SYNTHETIC Other','customer_type','household','contact_number','09000000012','address','SYNTHETIC HISTORY'));
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',2,'business_date',day));
 other_order:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',other_customer->>'id','quantity',2,'business_date',day));
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',1000));
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',o->>'reference','amount_centavos',500));
 perform public.aq_command(jsonb_build_object('operation','record_payment','request_id',gen_random_uuid(),'reference',other_order->>'reference','amount_centavos',250));
 sale:=public.aq_command(jsonb_build_object('operation','create_sale','request_id',gen_random_uuid(),'customer_name','SYNTHETIC Needle Walk-in','quantity',1,'cash_centavos',1000,'confirm_credit',true));
 all_receipts:=public.aq_query('{"resource":"payments"}');stock:=public.aq_query('{"resource":"inventory"}');
 if jsonb_array_length(all_receipts->'items')<>4 then raise exception 'Missing fixture receipts';end if;
 r:=public.aq_query('{"resource":"payments","search":"nEeDlE"}');if jsonb_array_length(r->'items')<>3 then raise exception 'Customer-name history omits order or unlinked sale receipts';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search','needle','customer_id',c->>'id'));if jsonb_array_length(r->'items')<>2 then raise exception 'Customer scope widened by name search';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search','needle','customer_id',other_customer->>'id'));if jsonb_array_length(r->'items')<>0 then raise exception 'Wrong-customer receipt leaked';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search',o->>'reference'));if jsonb_array_length(r->'items')<>2 then raise exception 'Order reference history changed';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search',sale->>'reference'));if jsonb_array_length(r->'items')<>1 then raise exception 'Walk-in reference history changed';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search','needle','start_date',day,'end_date',day));if jsonb_array_length(r->'items')<>3 then raise exception 'Actual receipt-date filter lost matches';end if;
 r:=public.aq_query(jsonb_build_object('resource','payments','search','needle','start_date',(day::date+1)::text,'end_date',(day::date+1)::text));if jsonb_array_length(r->'items')<>0 then raise exception 'Name search bypasses date cutoff';end if;
 r:=public.aq_query('{"resource":"payments","search":"No Such Customer"}');if jsonb_array_length(r->'items')<>0 then raise exception 'Unknown name returned receipts';end if;
 perform set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
 perform set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000002','iat',extract(epoch from clock_timestamp()),'aal','aal1')::text,true);
 r:=public.aq_query('{"resource":"payments","search":"Needle"}');if jsonb_array_length(r->'items')<>3 then raise exception 'Active Staff history search failed';end if;
 begin perform public.aq_query('{"resource":"expenses"}');exception when insufficient_privilege then blocked:=true;end;
 if not blocked then raise exception 'Query replacement widened Owner permission';end if;
 if public.aq_query('{"resource":"payments"}')<>all_receipts or public.aq_query('{"resource":"inventory"}')<>stock then raise exception 'Search mutated receipt or stock data';end if;
end $$;
reset role;
select not has_function_privilege('anon','public.aq_query(jsonb)','execute') and has_function_privilege('authenticated','public.aq_query(jsonb)','execute') and not has_table_privilege('authenticated','aq_private.payments','select') as query_acl_preserved \gset
\if :query_acl_preserved
\else
 \quit 1
\endif
rollback;
