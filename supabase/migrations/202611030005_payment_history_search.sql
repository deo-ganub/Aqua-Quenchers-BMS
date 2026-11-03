-- Forward-only read query repair: history uses the same customer-name context as Payments.
-- Original migrations/data remain unchanged; CREATE OR REPLACE retains current ownership/ACLs.
begin;
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
   result:=result||jsonb_build_object('timeline',(select coalesce(jsonb_agg(jsonb_build_object('event_id',e.id,'action',e.action,'status',e.after_state->>'status','occurred_at',e.created_at,'recorded_by_name',pr.full_name) order by e.created_at,e.id),'[]') from aq_private.audit_events e left join aq_private.profiles pr on pr.user_id=e.actor_id where e.entity_id=id and e.action in ('create_order','transition_order','record_pickup','record_delivery','reschedule_pickup')));
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
  return result||jsonb_build_object('orders_count',(select count(*) from aq_private.orders where status<>'delivered'),'for_pickup_count',(select count(*) from aq_private.orders where status='for_pickup'),
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
notify pgrst, 'reload schema';
commit;
