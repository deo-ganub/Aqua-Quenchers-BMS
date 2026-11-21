-- Walk-ins become counter orders: the customer brings their OWN gallons, the station refills
-- them, then the customer claims them at the counter or has them delivered. Station gallon
-- stock (empty/filled counts and reservations) is retired; the app now shows the customer
-- gallons currently at the station instead. Old walk-in sales stay readable for history.
begin;
alter table aq_private.orders alter column customer_id drop not null;
alter table aq_private.orders add column channel text not null default 'messenger' check(channel in ('messenger','counter'));
alter table aq_private.orders add column fulfillment text not null default 'deliver' check(fulfillment in ('deliver','claim'));
do $$ declare r record; begin
 for r in select conname from pg_constraint where conrelid='aq_private.orders'::regclass and contype='c' and pg_get_constraintdef(oid) like '%status%' loop
  execute format('alter table aq_private.orders drop constraint %I',r.conname);
 end loop;
end $$;
alter table aq_private.orders add constraint orders_status_check check(status in ('ordered','for_pickup','rescheduled','picked_up','received_at_counter','refilling','ready_for_delivery','undelivered','delivered','claimed'));
alter table aq_private.orders add constraint orders_quantity_stage_check check((status in ('ordered','for_pickup','rescheduled') and collected_quantity=0 and delivered_quantity=0) or
 (status in ('picked_up','received_at_counter','refilling','ready_for_delivery','undelivered','delivered','claimed') and collected_quantity=quantity));
alter table aq_private.orders add constraint orders_finished_check check(status not in ('delivered','claimed') or delivered_quantity=quantity);
alter table aq_private.orders add constraint orders_counter_rules_check check(
 (channel='messenger' and customer_id is not null and fulfillment='deliver' and status not in ('received_at_counter','claimed')) or
 (channel='counter' and customer_type='walk_in' and status not in ('ordered','for_pickup','rescheduled','picked_up') and (fulfillment='claim' or length(btrim(address))>0) and (status<>'claimed' or fulfillment='claim')));

-- "Gallons at the station": customer gallons the station is holding right now.
create or replace function aq_private.stock_json() returns jsonb language sql stable set search_path='' as $$
 select jsonb_build_object(
  'at_station',coalesce(sum(o.quantity-o.delivered_quantity),0),
  'refilling',coalesce(sum(o.quantity) filter(where o.status in ('picked_up','received_at_counter','refilling')),0),
  'ready',coalesce(sum(o.quantity-o.delivered_quantity) filter(where o.status in ('ready_for_delivery','undelivered')),0),
  'pending_quantity',coalesce(sum(o.quantity-o.delivered_quantity) filter(where o.status in ('ready_for_delivery','undelivered') and o.fulfillment='deliver'),0),
  'to_claim',coalesce(sum(o.quantity) filter(where o.status='ready_for_delivery' and o.fulfillment='claim'),0),
  'updated_at',max(o.updated_at),
  'gallons',coalesce(jsonb_agg(aq_private.order_json(o.id) order by o.business_date,o.created_at),'[]'))
 from aq_private.orders o where o.status in ('picked_up','received_at_counter','refilling','ready_for_delivery','undelivered')
$$;

create or replace function public.aq_command(p_request jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_variable
<<aq_command>>
declare
 a uuid:=aq_private.require_actor(); op text:=p_request->>'operation'; rid uuid; previous aq_private.requests; result jsonb;
 c aq_private.customers; o aq_private.orders; s aq_private.sales; inv aq_private.inventory; q integer; cash bigint; total bigint; paid bigint;
 id uuid; status text; d date; before_state jsonb; reference text; reserved bigint; remarks text; source text; model text;
begin
 if p_request is null or jsonb_typeof(p_request)<>'object' then raise exception 'Invalid request.'; end if;
 begin rid:=(p_request->>'request_id')::uuid; exception when others then raise exception 'A valid request_id is required.'; end;
 if rid is null then raise exception 'A valid request_id is required.'; end if;
 insert into aq_private.requests(actor_id,request_id,payload,payload_hash) values(a,rid,p_request,sha256(convert_to(p_request::text,'UTF8'))) on conflict do nothing;
 select * into previous from aq_private.requests where actor_id=a and request_id=rid for update;
 if previous.payload_hash<>sha256(convert_to(p_request::text,'UTF8')) then raise exception 'Request key was already used with different data.'; end if;
 if previous.result is not null then return previous.result; end if;
 -- A small station benefits from a deterministic global write lock; explicit rows below
 -- also protect invariants. This serializes application mutations, never external reads.
 perform pg_advisory_xact_lock(71420261103);
 if op in ('create_customer','update_customer') then
  perform aq_private.require_keys(p_request,array['operation','request_id','id','name','customer_type','contact_number','address']);
  if coalesce(length(btrim(p_request->>'name')),0)=0 or coalesce(length(btrim(p_request->>'address')),0)=0 or coalesce(length(btrim(p_request->>'contact_number')),0)=0 then raise exception 'Complete the required fields.'; end if;
  if coalesce(p_request->>'customer_type','') not in ('household','dealer','walk_in') then raise exception 'Choose Household, Dealer, or Walk-in.'; end if;
  if replace(p_request->>'contact_number',' ','') !~ '^[0-9]{11}$' then raise exception 'Please enter a valid contact number. Use 11 digits.'; end if;
  id:=case when op='update_customer' then (p_request->>'id')::uuid else gen_random_uuid() end;
  if exists(select 1 from aq_private.customers where contact_number=replace(p_request->>'contact_number',' ','') and customers.id<>id) then raise exception 'Customer already exists.'; end if;
  if op='update_customer' then
   select to_jsonb(x) into before_state from aq_private.customers x where x.id=id for update;
   if not found then raise exception 'Customer not found.'; end if;
   update aq_private.customers set name=btrim(p_request->>'name'),customer_type=p_request->>'customer_type',contact_number=replace(p_request->>'contact_number',' ',''),address=btrim(p_request->>'address'),updated_at=clock_timestamp() where customers.id=id;
  else
   insert into aq_private.customers(id,name,customer_type,contact_number,address,recorded_by) values(id,btrim(p_request->>'name'),p_request->>'customer_type',replace(p_request->>'contact_number',' ',''),btrim(p_request->>'address'),a);
  end if;
  result:=aq_private.customer_json(id);
 elsif op='create_order' then
  perform aq_private.require_keys(p_request,array['operation','request_id','customer_id','quantity','business_date']);
  q:=aq_private.positive_quantity(p_request->'quantity');
  select * into c from aq_private.customers where customers.id=(p_request->>'customer_id')::uuid;
  if not found then raise exception 'Choose a customer.'; end if;
  if c.contact_number is null or c.address is null then raise exception 'Complete the required fields.';end if;
  d:=coalesce(nullif(p_request->>'business_date','')::date,aq_private.today());
  insert into aq_private.orders(reference,customer_id,customer_type,customer_name,address,contact_number,business_date,expected_quantity,quantity,unit_price_centavos,recorded_by)
  values(aq_private.new_reference('ORD'),c.id,c.customer_type,c.name,c.address,c.contact_number,d,q,q,case when c.customer_type='dealer' then 3000 else 3500 end,a) returning orders.id into id;
  result:=aq_private.order_json(id);
 elsif op in ('transition_order','record_pickup','reschedule_pickup','record_delivery') then
  perform aq_private.require_keys(p_request,array['operation','request_id','order_id','status','quantity','scheduled_date']||case when op='reschedule_pickup' then array['notes'] else array[]::text[] end);
  select * into o from aq_private.orders where orders.id=(p_request->>'order_id')::uuid for update;
  if not found then raise exception 'Order not found.'; end if;
  id:=o.id; before_state:=aq_private.order_json(id);
  if op='transition_order' then
   status:=p_request->>'status';
   if status=o.status then result:=before_state; end if;
   if result is null then
    -- Gallons belong to customers, so stages move gallons through the station without stock counts.
    if not ((o.status='ordered' and status='for_pickup') or (o.status='rescheduled' and status='for_pickup') or (o.status in ('picked_up','received_at_counter') and status='refilling') or (o.status='refilling' and status='ready_for_delivery')
     or (o.status='ready_for_delivery' and status='claimed' and o.channel='counter' and o.fulfillment='claim')) then raise exception 'Follow the next eligible order stage.'; end if;
    update aq_private.orders set status=p_request->>'status',delivered_quantity=case when status='claimed' then quantity else delivered_quantity end,updated_at=clock_timestamp() where orders.id=id;
   end if;
  elsif op='record_pickup' then
   if o.status<>'for_pickup' then raise exception 'Follow the next eligible order stage.'; end if;
   q:=aq_private.positive_quantity(p_request->'quantity');
   if q>o.expected_quantity then raise exception 'Collected quantity cannot exceed the expected quantity.'; end if;
   if q::bigint*o.unit_price_centavos<aq_private.paid(id,null) then raise exception 'Adjusted total cannot be less than the amount already paid.'; end if;
   update aq_private.orders set quantity=q,collected_quantity=q,status='picked_up',updated_at=clock_timestamp() where orders.id=id;
  elsif op='reschedule_pickup' then
   if o.status not in ('for_pickup','rescheduled') then raise exception 'Follow the next eligible order stage.'; end if;
   d:=nullif(p_request->>'scheduled_date','')::date;
   if d is null or d<aq_private.today() then raise exception 'Select today or a future date.'; end if;
   if p_request ? 'notes' and jsonb_typeof(p_request->'notes') not in ('string','null') then raise exception 'Enter valid pickup notes.';end if;
   if char_length(coalesce(p_request->>'notes',''))>500 then raise exception 'Use 500 characters or fewer.';end if;
   update aq_private.orders set status='rescheduled',rescheduled_date=d,reschedule_notes=btrim(coalesce(p_request->>'notes','')),updated_at=clock_timestamp() where orders.id=id;
  else
   if o.status not in ('ready_for_delivery','undelivered') or o.fulfillment<>'deliver' then raise exception 'Follow the next eligible order stage.'; end if;
   q:=aq_private.positive_quantity(p_request->'quantity');
   if q>o.quantity-o.delivered_quantity then raise exception 'Delivered quantity cannot exceed the pending quantity.'; end if;
   update aq_private.orders set delivered_quantity=delivered_quantity+q,status=case when delivered_quantity+q=quantity then 'delivered' else 'undelivered' end,updated_at=clock_timestamp() where orders.id=id;
   insert into aq_private.deliveries(order_id,quantity,recorded_by) values(id,q,a);
  end if;
  result:=aq_private.order_json(id);
 elsif op='create_sale' then
  raise exception 'Walk-ins are now recorded as counter orders.';
 elsif op='create_counter_order' then
  -- Walk-in: the customer brings their own gallons. Paid at drop-off (cash now, rest as credit).
  perform aq_private.require_keys(p_request,array['operation','request_id','customer_id','customer_name','contact_number','address','quantity','cash_centavos','confirm_credit']);
  if coalesce(length(btrim(p_request->>'customer_name')),0)=0 then raise exception 'Enter the customer name.'; end if;
  if char_length(coalesce(p_request->>'customer_name',''))>160 then raise exception 'Use 160 characters or fewer.'; end if;
  if char_length(coalesce(p_request->>'address',''))>1000 then raise exception 'Use 1000 characters or fewer.'; end if;
  if coalesce(replace(p_request->>'contact_number',' ',''),'')!~'^([0-9]{11})?$' then raise exception 'Please enter a valid contact number. Use 11 digits.'; end if;
  q:=aq_private.positive_quantity(p_request->'quantity'); cash:=aq_private.money(coalesce(p_request->'cash_centavos','0'::jsonb),false); total:=q::bigint*3500;
  if nullif(p_request->>'customer_id','') is not null then
   select * into c from aq_private.customers where customers.id=(p_request->>'customer_id')::uuid;
   if not found then raise exception 'Customer not found.'; end if;
  end if;
  if cash<total and p_request->'confirm_credit' is distinct from 'true'::jsonb then raise exception 'Confirm recording the remaining balance as credit.'; end if;
  insert into aq_private.orders(reference,customer_id,customer_type,customer_name,address,contact_number,business_date,expected_quantity,quantity,collected_quantity,unit_price_centavos,status,channel,fulfillment,recorded_by)
  values(aq_private.new_reference('WI'),c.id,'walk_in',btrim(p_request->>'customer_name'),btrim(coalesce(p_request->>'address','')),replace(coalesce(p_request->>'contact_number',''),' ',''),aq_private.today(),q,q,q,3500,'received_at_counter','counter',
   case when length(btrim(coalesce(p_request->>'address','')))>0 then 'deliver' else 'claim' end,a) returning orders.id into id;
  if least(cash,total)>0 then insert into aq_private.payments(reference,order_id,amount_centavos,recorded_by) values(aq_private.new_reference('PAY'),id,least(cash,total),a); end if;
  result:=aq_private.order_json(id)||jsonb_build_object('cash_tendered_centavos',cash,'change_centavos',greatest(cash-total,0));
 elsif op='record_payment' then
  perform aq_private.require_keys(p_request,array['operation','request_id','reference','amount_centavos']);
  begin cash:=aq_private.money(p_request->'amount_centavos');exception when others then raise exception 'Enter an amount greater than zero and not above the balance.';end; reference:=p_request->>'reference';
  select * into o from aq_private.orders where orders.reference=reference for update;
  if found then total:=o.quantity::bigint*o.unit_price_centavos; paid:=aq_private.paid(o.id,null);
  else
   select * into s from aq_private.sales where sales.reference=reference for update;
   if not found then raise exception 'Order or sale reference not found.'; end if;
   total:=s.quantity::bigint*s.unit_price_centavos; paid:=aq_private.paid(null,s.id);
  end if;
  if cash>total-paid then raise exception 'Payment cannot exceed the ₱% balance.',to_char((total-paid)::numeric/100,'FM999999999999990.00'); end if;
  insert into aq_private.payments(reference,order_id,sale_id,amount_centavos,recorded_by) values(aq_private.new_reference('PAY'),o.id,s.id,cash,a) returning payments.id into id;
  select to_jsonb(p)||jsonb_build_object('invoice_reference',reference,'balance_centavos',total-paid-cash,'payment_status',aq_private.payment_status(total,paid+cash),'recorded_by_name',(select full_name from aq_private.profiles where user_id=a)) into result from aq_private.payments p where p.id=id;
 elsif op='create_expense' then
  perform aq_private.require_actor(true);
  perform aq_private.require_keys(p_request,array['operation','request_id','business_date','category','amount_centavos','remarks','selection_source','accepted_model_version']);
  d:=nullif(p_request->>'business_date','')::date;
  if d is null or coalesce(p_request->>'category','')='' then raise exception 'Complete the required fields.'; end if;
  if p_request->>'category' not in ('Fuel','Electricity','Water','Maintenance','Supplies','Salary','Others') then raise exception 'Choose a valid expense category.'; end if;
  begin cash:=aq_private.money(p_request->'amount_centavos'); exception when others then raise exception 'Enter a valid expense amount.'; end;
  remarks:=coalesce(p_request->>'remarks',''); if char_length(remarks)>500 then raise exception 'Use 500 characters or fewer.'; end if;
  source:=coalesce(p_request->>'selection_source','manual'); model:=nullif(p_request->>'accepted_model_version','');
  if source not in ('manual','suggestion','manual_override') or ((source='suggestion')<>(model is not null)) then raise exception 'Invalid category selection source.'; end if;
  insert into aq_private.expenses(reference,business_date,category,amount_centavos,remarks,selection_source,accepted_model_version,recorded_by)
  values(aq_private.new_reference('EXP'),d,p_request->>'category',cash,remarks,source,model,a) returning expenses.id into id;
  select to_jsonb(e) into result from aq_private.expenses e where e.id=id;
 elsif op='update_profile' then
  perform aq_private.require_keys(p_request,array['operation','request_id','full_name','contact_number','username']);
  if coalesce(length(btrim(p_request->>'full_name')),0)=0 then raise exception 'Complete the required fields.'; end if;
  if coalesce(p_request->>'contact_number','') !~ '^[0-9]{11}$' then raise exception 'Please enter a valid contact number. Use 11 digits.'; end if;
  if lower(btrim(coalesce(p_request->>'username',''))) !~ '^[a-z0-9][a-z0-9_.-]{2,39}$' then raise exception 'Enter a valid username.'; end if;
  perform aq_private.require_available_username(lower(btrim(p_request->>'username')),a);
  id:=a; select to_jsonb(p)-'created_at'-'updated_at' into before_state from aq_private.profiles p where user_id=a;
  update aq_private.profiles set full_name=btrim(p_request->>'full_name'),contact_number=replace(p_request->>'contact_number',' ',''),username=lower(btrim(p_request->>'username')),updated_at=clock_timestamp() where user_id=a;
  result:=public.aq_me();
 else raise exception 'Unknown operation.';
 end if;
 if before_state is distinct from result then
  insert into aq_private.audit_events(actor_id,action,entity_id,request_id,before_state,after_state) values(a,op,id,rid,before_state,result);
 end if;
 update aq_private.requests set result=aq_command.result where actor_id=a and request_id=rid;
 return result;
end $$;

create or replace function public.aq_query(p_request jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
#variable_conflict use_variable
declare a uuid:=aq_private.require_actor(); resource text:=p_request->>'resource'; items jsonb; result jsonb; id uuid; search text:=coalesce(p_request->>'search',''); start_date date; end_date date;
begin
 perform aq_private.require_keys(p_request,array['resource','id','reference','search','customer_id','status','start_date','end_date','category','unpaid_only']);
 start_date:=nullif(p_request->>'start_date','')::date;end_date:=nullif(p_request->>'end_date','')::date;
 if end_date<start_date then raise exception 'The end date cannot be earlier than the start date.';end if;
 if resource='me' then return public.aq_me();
 elsif resource='customers' then
  select coalesce(jsonb_agg(aq_private.customer_json(c.id) order by c.name),'[]') into items from aq_private.customers c where c.name ilike '%'||search||'%' or c.contact_number like '%'||replace(search,' ','')||'%';
 elsif resource='customer' then
  id:=(p_request->>'id')::uuid; result:=aq_private.customer_json(id);
  if result is null then raise exception 'Customer not found.'; end if;
  return result||jsonb_build_object('orders',(select coalesce(jsonb_agg(aq_private.order_json(o.id) order by created_at desc),'[]') from aq_private.orders o where customer_id=id),
   'sales',(select coalesce(jsonb_agg(aq_private.sale_json(s.id) order by created_at desc),'[]') from aq_private.sales s where customer_id=id),
   'payments',(select coalesce(jsonb_agg(to_jsonb(p)||jsonb_build_object('invoice_reference',coalesce(o.reference,s.reference),'recorded_by_name',pr.full_name) order by received_at desc),'[]') from aq_private.payments p left join aq_private.orders o on o.id=p.order_id left join aq_private.sales s on s.id=p.sale_id left join aq_private.profiles pr on pr.user_id=p.recorded_by where o.customer_id=id or s.customer_id=id));
 elsif resource='orders' then
  select coalesce(jsonb_agg(aq_private.order_json(o.id) order by created_at desc),'[]') into items from aq_private.orders o
  where (nullif(p_request->>'customer_id','') is null or customer_id=(p_request->>'customer_id')::uuid)
  and (start_date is null or o.business_date>=start_date) and (end_date is null or o.business_date<=end_date)
  and (nullif(p_request->>'status','') is null or status=p_request->>'status')
  and (o.reference ilike '%'||search||'%' or o.customer_name ilike '%'||search||'%')
  and (coalesce(p_request->>'unpaid_only','false')<>'true' or aq_private.paid(o.id,null)<o.quantity::bigint*o.unit_price_centavos);
 elsif resource='order' then
  select o.id into id from aq_private.orders o where o.id=nullif(p_request->>'id','')::uuid or o.reference=p_request->>'reference';
  result:=aq_private.order_json(id); if result is null then raise exception 'Order not found.'; end if;
  if exists(select 1 from aq_private.profiles where user_id=a and role='owner') then
   result:=result||jsonb_build_object('timeline',(select coalesce(jsonb_agg(jsonb_build_object('event_id',e.id,'action',e.action,'status',e.after_state->>'status','occurred_at',e.created_at,'recorded_by_name',pr.full_name) order by e.created_at,e.id),'[]') from aq_private.audit_events e left join aq_private.profiles pr on pr.user_id=e.actor_id where e.entity_id=id and e.action in ('create_order','create_counter_order','transition_order','record_pickup','record_delivery','reschedule_pickup')));
  end if;
  return result||jsonb_build_object('payments',(select coalesce(jsonb_agg(to_jsonb(p)||jsonb_build_object('recorded_by_name',pr.full_name) order by received_at desc),'[]') from aq_private.payments p left join aq_private.profiles pr on pr.user_id=p.recorded_by where p.order_id=id),
   'deliveries',(select coalesce(jsonb_agg(to_jsonb(d) order by created_at desc),'[]') from aq_private.deliveries d where order_id=id));
 elsif resource in ('sales','sale') then
  if resource='sale' then
   select s.id into id from aq_private.sales s where s.id=nullif(p_request->>'id','')::uuid or s.reference=p_request->>'reference';
   result:=aq_private.sale_json(id); if result is null then raise exception 'Sale not found.'; end if;
   return result||jsonb_build_object('payments',(select coalesce(jsonb_agg(to_jsonb(p)||jsonb_build_object('recorded_by_name',pr.full_name) order by received_at desc),'[]') from aq_private.payments p left join aq_private.profiles pr on pr.user_id=p.recorded_by where p.sale_id=id));
  end if;
  select coalesce(jsonb_agg(aq_private.sale_json(s.id) order by created_at desc),'[]') into items from aq_private.sales s
  where (s.reference ilike '%'||search||'%' or s.customer_name ilike '%'||search||'%') and (nullif(p_request->>'customer_id','') is null or s.customer_id=(p_request->>'customer_id')::uuid)
  and (coalesce(p_request->>'unpaid_only','false')<>'true' or aq_private.paid(null,s.id)<s.quantity::bigint*s.unit_price_centavos);
 elsif resource='payments' then
  select coalesce(jsonb_agg(to_jsonb(p)||jsonb_build_object('invoice_reference',coalesce(o.reference,s.reference),'customer_name',coalesce(o.customer_name,s.customer_name),'recorded_by_name',pr.full_name) order by received_at desc),'[]') into items
  from aq_private.payments p left join aq_private.orders o on o.id=p.order_id left join aq_private.sales s on s.id=p.sale_id left join aq_private.profiles pr on pr.user_id=p.recorded_by
  where (coalesce(o.reference,s.reference) ilike '%'||search||'%' or coalesce(o.customer_name,s.customer_name) ilike '%'||search||'%') and (nullif(p_request->>'customer_id','') is null or o.customer_id=(p_request->>'customer_id')::uuid or s.customer_id=(p_request->>'customer_id')::uuid)
  and (start_date is null or (p.received_at at time zone 'Asia/Manila')::date>=start_date) and (end_date is null or (p.received_at at time zone 'Asia/Manila')::date<=end_date);
 elsif resource in ('inventory','home') then
  result:=aq_private.stock_json();
  return result||jsonb_build_object('orders_count',(select count(*) from aq_private.orders where status not in ('delivered','claimed')),'for_pickup_count',(select count(*) from aq_private.orders where status='for_pickup'),
   'ready_for_delivery_count',(select count(*) from aq_private.orders where status='ready_for_delivery'),
   'pending_orders',(select coalesce(jsonb_agg(aq_private.order_json(o.id)||jsonb_build_object('days_waiting',greatest(aq_private.today()-business_date,0)) order by business_date),'[]') from aq_private.orders o where status in ('ready_for_delivery','undelivered')));
 elsif resource='expenses' then
  perform aq_private.require_actor(true);
 elsif resource in ('report','dashboard') then
  perform aq_private.require_actor(true);
  start_date:=coalesce(nullif(p_request->>'start_date','')::date,aq_private.today()); end_date:=coalesce(nullif(p_request->>'end_date','')::date,aq_private.today());
  result:=aq_private.financial_summary(start_date,end_date);
  if resource='dashboard' then return result||jsonb_build_object('inventory',aq_private.stock_json(),'recent_orders',(select coalesce(jsonb_agg(x.item),'[]') from (select aq_private.order_json(o.id) item from aq_private.orders o order by created_at desc limit 10) x)); end if;
  return result;
 elsif resource='staff' then
  perform aq_private.require_actor(true);
  select coalesce(jsonb_agg(to_jsonb(p)-'created_at'-'updated_at' order by full_name),'[]') into items from aq_private.profiles p where role='staff';
 else raise exception 'Unknown resource.';
 end if;
 if resource='expenses' then
  select coalesce(jsonb_agg(to_jsonb(e)||jsonb_build_object('recorded_by_name',pr.full_name) order by e.business_date desc,e.created_at desc),'[]') into items
  from aq_private.expenses e left join aq_private.profiles pr on pr.user_id=e.recorded_by
  where (nullif(p_request->>'start_date','') is null or business_date>=(p_request->>'start_date')::date)
  and (nullif(p_request->>'end_date','') is null or business_date<=(p_request->>'end_date')::date)
  and (nullif(p_request->>'category','') is null or category=p_request->>'category');
  return jsonb_build_object('items',items,'total_centavos',(select coalesce(sum((x->>'amount_centavos')::bigint),0) from jsonb_array_elements(items) x));
 end if;
 return jsonb_build_object('items',items);
end $$;

revoke all on all functions in schema aq_private from public,anon,authenticated;
revoke all on function public.aq_command(jsonb),public.aq_query(jsonb) from public,anon;
grant execute on function public.aq_command(jsonb),public.aq_query(jsonb) to authenticated;
commit;
