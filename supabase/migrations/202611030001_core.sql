-- Aqua Quenchers: private tables; authenticated, checked transactional API only.
begin;
create schema if not exists aq_private;
revoke all on schema aq_private from public, anon, authenticated;
alter default privileges in schema aq_private revoke all on tables from public, anon, authenticated;
alter default privileges in schema aq_private revoke execute on functions from public, anon, authenticated;

create table aq_private.profiles (
 user_id uuid primary key references auth.users(id),
 username text not null unique check(username = lower(btrim(username)) and username ~ '^[a-z0-9][a-z0-9_.-]{2,39}$'),
 full_name text not null check(length(btrim(full_name)) between 1 and 160),
 contact_number text not null check(contact_number ~ '^[0-9]{11}$'),
 role text not null check(role in ('owner','staff')), active boolean not null default true,
 created_at timestamptz not null default clock_timestamp(), updated_at timestamptz not null default clock_timestamp()
);
create table aq_private.sessions (
 session_id uuid primary key, user_id uuid not null references aq_private.profiles,
 last_seen_at timestamptz not null default clock_timestamp()
);
create table aq_private.customers (
 id uuid primary key default gen_random_uuid(), name text not null check(length(btrim(name)) between 1 and 160),
 customer_type text not null check(customer_type in ('household','dealer','walk_in')),
 contact_number text not null unique check(contact_number ~ '^[0-9]{11}$'),
 address text not null check(length(btrim(address)) between 1 and 1000),
 created_at timestamptz not null default clock_timestamp(), updated_at timestamptz not null default clock_timestamp(),
 recorded_by uuid not null references aq_private.profiles
);
create table aq_private.inventory (
 singleton boolean primary key default true check(singleton), empty_quantity bigint not null default 0 check(empty_quantity>=0),
 physical_filled bigint not null default 0 check(physical_filled>=0),
 initialized boolean not null default false, updated_at timestamptz not null default clock_timestamp()
);
insert into aq_private.inventory(singleton) values(true);
create sequence aq_private.reference_seq;
create table aq_private.orders (
 id uuid primary key default gen_random_uuid(), reference text not null unique,
 customer_id uuid not null references aq_private.customers,
 customer_type text not null check(customer_type in ('household','dealer','walk_in')),
 customer_name text not null, address text not null, contact_number text not null,
 business_date date not null, expected_quantity integer not null check(expected_quantity>0),
 quantity integer not null check(quantity>0 and quantity<=expected_quantity),
 collected_quantity integer not null default 0 check(collected_quantity>=0 and collected_quantity<=expected_quantity),
 delivered_quantity integer not null default 0 check(delivered_quantity>=0 and delivered_quantity<=quantity),
 unit_price_centavos bigint not null check((customer_type='dealer' and unit_price_centavos=3000) or (customer_type<>'dealer' and unit_price_centavos=3500)),
 status text not null default 'ordered' check(status in ('ordered','for_pickup','picked_up','refilling','ready_for_delivery','undelivered','delivered','rescheduled')),
 rescheduled_date date, reschedule_notes text not null default '' check(char_length(reschedule_notes)<=500), created_at timestamptz not null default clock_timestamp(),
 updated_at timestamptz not null default clock_timestamp(), recorded_by uuid not null references aq_private.profiles,
 check((status in ('ordered','for_pickup','rescheduled') and collected_quantity=0 and delivered_quantity=0) or
       (status in ('picked_up','refilling','ready_for_delivery','undelivered','delivered') and collected_quantity=quantity)),
 check(status<>'delivered' or delivered_quantity=quantity)
);
create table aq_private.reservations (
 order_id uuid primary key references aq_private.orders, quantity integer not null check(quantity>=0),
 created_at timestamptz not null default clock_timestamp(), updated_at timestamptz not null default clock_timestamp()
);
create table aq_private.sales (
 id uuid primary key default gen_random_uuid(), reference text not null unique,
 customer_id uuid references aq_private.customers, customer_name text not null check(length(btrim(customer_name)) between 1 and 160),
 quantity integer not null check(quantity>0), unit_price_centavos bigint not null default 3500 check(unit_price_centavos=3500),
 cash_tendered_centavos bigint not null check(cash_tendered_centavos>=0),
 change_centavos bigint not null check(change_centavos>=0),
 created_at timestamptz not null default clock_timestamp(), recorded_by uuid not null references aq_private.profiles
);
create table aq_private.payments (
 id uuid primary key default gen_random_uuid(), reference text not null unique,
 order_id uuid references aq_private.orders, sale_id uuid references aq_private.sales,
 amount_centavos bigint not null check(amount_centavos>0), received_at timestamptz not null default clock_timestamp(),
 recorded_by uuid not null references aq_private.profiles,
 check((order_id is not null)::integer+(sale_id is not null)::integer=1)
);
create table aq_private.deliveries (
 id uuid primary key default gen_random_uuid(), order_id uuid not null references aq_private.orders,
 quantity integer not null check(quantity>0), created_at timestamptz not null default clock_timestamp(),
 recorded_by uuid not null references aq_private.profiles
);
create table aq_private.expenses (
 id uuid primary key default gen_random_uuid(), reference text not null unique, business_date date not null,
 category text not null check(category in ('Fuel','Electricity','Water','Maintenance','Supplies','Salary','Others')),
 amount_centavos bigint not null check(amount_centavos>0), remarks text not null default '' check(char_length(remarks)<=500),
 selection_source text not null check(selection_source in ('manual','suggestion','manual_override')),
 accepted_model_version text, created_at timestamptz not null default clock_timestamp(),
 recorded_by uuid not null references aq_private.profiles,
 check((selection_source='suggestion' and accepted_model_version is not null and length(btrim(accepted_model_version)) between 1 and 100) or
       (selection_source<>'suggestion' and accepted_model_version is null))
);
create table aq_private.requests (
 actor_id uuid not null references aq_private.profiles, request_id uuid not null, payload jsonb not null, payload_hash bytea not null,
 result jsonb, created_at timestamptz not null default clock_timestamp(), primary key(actor_id,request_id)
);
create table aq_private.audit_events (
 id bigint generated always as identity primary key, actor_id uuid references aq_private.profiles,
 action text not null, entity_id uuid, request_id uuid, before_state jsonb, after_state jsonb,
 reason text, created_at timestamptz not null default clock_timestamp()
);
create index orders_business_date_idx on aq_private.orders(business_date);
create index orders_customer_idx on aq_private.orders(customer_id,created_at desc);
create index orders_status_idx on aq_private.orders(status,business_date);
create index payments_order_idx on aq_private.payments(order_id);
create index payments_sale_idx on aq_private.payments(sale_id);
create index payments_received_idx on aq_private.payments(received_at);
create index sales_customer_idx on aq_private.sales(customer_id,created_at desc);
create index expenses_date_idx on aq_private.expenses(business_date,category);
-- Default-deny RLS is deliberate: only audited SECURITY DEFINER APIs may use tables.
do $$ declare t record; begin
 for t in select tablename from pg_tables where schemaname='aq_private' loop
  execute format('alter table aq_private.%I enable row level security',t.tablename);
  execute format('revoke all on aq_private.%I from public,anon,authenticated',t.tablename);
 end loop;
end $$;

create function aq_private.today() returns date language sql stable set search_path='' as $$ select (statement_timestamp() at time zone 'Asia/Manila')::date $$;
create function aq_private.new_reference(p_prefix text) returns text language sql volatile set search_path='' as $$
 with value as (select nextval('aq_private.reference_seq')::text n)
 select p_prefix||'-'||to_char(aq_private.today(),'YYYY')||'-'||lpad(n,greatest(6,length(n)),'0') from value
$$;
create function aq_private.require_actor(p_owner boolean default false) returns uuid language plpgsql security definer set search_path='' as $$
declare a uuid:=auth.uid(); p aq_private.profiles; sid uuid; seen timestamptz; claims jsonb:=auth.jwt();
begin
 select * into p from aq_private.profiles where user_id=a;
 if a is null or p.user_id is null or not p.active or (p_owner and p.role<>'owner') then
  raise exception using errcode='42501',message='Access denied.';
 end if;
 begin sid:=(claims->>'session_id')::uuid; exception when others then sid:=null; end;
 if sid is null then raise exception using errcode='42501',message='Session expired. Sign in again.'; end if;
 perform pg_advisory_xact_lock(714,hashtext(sid::text));
 select last_seen_at into seen from aq_private.sessions where session_id=sid and user_id=a for update;
 if found then
  if seen <= clock_timestamp()-interval '30 minutes' then raise exception using errcode='42501',message='Session expired. Sign in again.'; end if;
  update aq_private.sessions set last_seen_at=clock_timestamp() where session_id=sid;
 else
  if (claims->>'iat') is null or to_timestamp((claims->>'iat')::double precision) <= clock_timestamp()-interval '30 minutes' then
   raise exception using errcode='42501',message='Session expired. Sign in again.';
  end if;
  insert into aq_private.sessions(session_id,user_id) values(sid,a);
 end if;
 return a;
end $$;
-- Identity administration extends this check with pending aliases in migration3.
create function aq_private.require_available_username(p_username text,p_user_id uuid) returns void language plpgsql set search_path='' as $$
begin
 perform pg_advisory_xact_lock(3841952);
 if exists(select 1 from aq_private.profiles where username=p_username and user_id<>p_user_id) then raise exception 'Username already exists.';end if;
end $$;
create function aq_private.require_keys(p jsonb,p_allowed text[]) returns void language plpgsql set search_path='' as $$
declare k text; begin
 if p is null or jsonb_typeof(p)<>'object' then raise exception 'Invalid request.'; end if;
 for k in select jsonb_object_keys(p) loop
  if not(k=any(p_allowed)) then raise exception 'Unknown field: %',k; end if;
 end loop;
end $$;
create function aq_private.positive_quantity(p jsonb) returns integer language plpgsql immutable set search_path='' as $$
declare n numeric; begin
 if p is null or jsonb_typeof(p)<>'number' or (p#>>'{}') !~ '^-?[0-9]+$' then raise exception 'Enter a whole-number gallon quantity.'; end if;
 n:=(p#>>'{}')::numeric;
 if n<=0 then raise exception 'Quantity must be greater than zero.'; end if;
 if n>2147483647 then raise exception 'Enter a valid gallon quantity.'; end if;
 return n::integer;
end $$;
create function aq_private.money(p jsonb,p_positive boolean default true) returns bigint language plpgsql immutable set search_path='' as $$
declare n numeric; begin
 if p is null or jsonb_typeof(p)<>'number' or (p#>>'{}') !~ '^-?[0-9]+$' then raise exception 'Enter a valid amount in whole centavos.'; end if;
 n:=(p#>>'{}')::numeric;
 if n<0 or (p_positive and n=0) or n>9007199254740991 then raise exception 'Enter a valid amount.'; end if;
 return n::bigint;
end $$;
create function aq_private.paid(p_order uuid,p_sale uuid) returns bigint language sql stable set search_path='' as $$
 select coalesce(sum(amount_centavos),0)::bigint from aq_private.payments where (p_order is not null and order_id=p_order) or (p_sale is not null and sale_id=p_sale)
$$;
create function aq_private.payment_status(p_total bigint,p_paid bigint) returns text language sql immutable set search_path='' as $$
 select case when p_paid=0 then 'unpaid' when p_paid<p_total then 'partially_paid' else 'paid' end
$$;
create function aq_private.order_json(p_id uuid) returns jsonb language sql stable set search_path='' as $$
 select to_jsonb(o)||jsonb_build_object('shortfall',o.expected_quantity-o.collected_quantity,
 'pending_quantity',case when o.collected_quantity>0 then o.quantity-o.delivered_quantity else 0 end,
 'total_centavos',o.quantity::bigint*o.unit_price_centavos,'paid_centavos',p.paid,
 'balance_centavos',o.quantity::bigint*o.unit_price_centavos-p.paid,
 'payment_status',aq_private.payment_status(o.quantity::bigint*o.unit_price_centavos,p.paid),
 'reserved_quantity',coalesce(r.quantity,0),'recorded_by_name',a.full_name)
 from aq_private.orders o cross join lateral (select aq_private.paid(o.id,null) as paid) p
 left join aq_private.reservations r on r.order_id=o.id left join aq_private.profiles a on a.user_id=o.recorded_by where o.id=p_id
$$;
create function aq_private.sale_json(p_id uuid) returns jsonb language sql stable set search_path='' as $$
 select to_jsonb(s)||jsonb_build_object('total_centavos',s.quantity::bigint*s.unit_price_centavos,'paid_centavos',p.paid,
 'balance_centavos',s.quantity::bigint*s.unit_price_centavos-p.paid,'payment_status',aq_private.payment_status(s.quantity::bigint*s.unit_price_centavos,p.paid),
 'business_date',(s.created_at at time zone 'Asia/Manila')::date,'recorded_by_name',a.full_name)
 from aq_private.sales s cross join lateral (select aq_private.paid(null,s.id) as paid) p left join aq_private.profiles a on a.user_id=s.recorded_by where s.id=p_id
$$;
create function aq_private.customer_json(p_id uuid) returns jsonb language sql stable set search_path='' as $$
 select to_jsonb(c)||jsonb_build_object('balance_centavos',
 coalesce((select sum(o.quantity::bigint*o.unit_price_centavos-aq_private.paid(o.id,null)) from aq_private.orders o where o.customer_id=c.id),0)+
 coalesce((select sum(s.quantity::bigint*s.unit_price_centavos-aq_private.paid(null,s.id)) from aq_private.sales s where s.customer_id=c.id),0),
 'previous_order_quantity',(select o.quantity from aq_private.orders o where o.customer_id=c.id order by o.created_at desc limit 1))
 from aq_private.customers c where c.id=p_id
$$;
create function aq_private.stock_json() returns jsonb language sql stable set search_path='' as $$
 select to_jsonb(i)||jsonb_build_object('reserved_filled',r.n,'filled_available',i.physical_filled-r.n,
 'pending_quantity',coalesce((select sum(quantity-delivered_quantity) from aq_private.orders where status in ('ready_for_delivery','undelivered')),0))
 from aq_private.inventory i cross join lateral (select coalesce(sum(quantity),0)::bigint n from aq_private.reservations) r
$$;
create function public.aq_me() returns jsonb language plpgsql security definer set search_path='' as $$
declare a uuid:=aq_private.require_actor(); begin
 return (select to_jsonb(p)-'created_at'-'updated_at' from aq_private.profiles p where user_id=a);
end $$;

create function public.aq_command(p_request jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
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
  select * into inv from aq_private.inventory where singleton for update;
  select * into o from aq_private.orders where orders.id=(p_request->>'order_id')::uuid for update;
  if not found then raise exception 'Order not found.'; end if;
  id:=o.id; before_state:=aq_private.order_json(id);
  if op='transition_order' then
   status:=p_request->>'status';
   if status=o.status then result:=before_state; end if;
   if result is null then
    if not ((o.status='ordered' and status='for_pickup') or (o.status='rescheduled' and status='for_pickup') or (o.status='picked_up' and status='refilling') or (o.status='refilling' and status='ready_for_delivery')) then raise exception 'Follow the next eligible order stage.'; end if;
    if status='ready_for_delivery' then
     if inv.empty_quantity<o.quantity then raise exception 'Not enough empty gallons.'; end if;
     if exists(select 1 from aq_private.reservations where order_id=id) then raise exception 'Order already reserved.'; end if;
     update aq_private.inventory set empty_quantity=empty_quantity-o.quantity,physical_filled=physical_filled+o.quantity,updated_at=clock_timestamp() where singleton;
     insert into aq_private.reservations(order_id,quantity) values(id,o.quantity);
    end if;
    update aq_private.orders set status=p_request->>'status',updated_at=clock_timestamp() where orders.id=id;
   end if;
  elsif op='record_pickup' then
   if o.status<>'for_pickup' then raise exception 'Follow the next eligible order stage.'; end if;
   q:=aq_private.positive_quantity(p_request->'quantity');
   if q>o.expected_quantity then raise exception 'Collected quantity cannot exceed the expected quantity.'; end if;
   if q::bigint*o.unit_price_centavos<aq_private.paid(id,null) then raise exception 'Adjusted total cannot be less than the amount already paid.'; end if;
   update aq_private.orders set quantity=q,collected_quantity=q,status='picked_up',updated_at=clock_timestamp() where orders.id=id;
   update aq_private.inventory set empty_quantity=empty_quantity+q,updated_at=clock_timestamp() where singleton;
  elsif op='reschedule_pickup' then
   if o.status not in ('for_pickup','rescheduled') then raise exception 'Follow the next eligible order stage.'; end if;
   d:=nullif(p_request->>'scheduled_date','')::date;
   if d is null or d<aq_private.today() then raise exception 'Select today or a future date.'; end if;
   if p_request ? 'notes' and jsonb_typeof(p_request->'notes') not in ('string','null') then raise exception 'Enter valid pickup notes.';end if;
   if char_length(coalesce(p_request->>'notes',''))>500 then raise exception 'Use 500 characters or fewer.';end if;
   update aq_private.orders set status='rescheduled',rescheduled_date=d,reschedule_notes=btrim(coalesce(p_request->>'notes','')),updated_at=clock_timestamp() where orders.id=id;
  else
   if o.status not in ('ready_for_delivery','undelivered') then raise exception 'Follow the next eligible order stage.'; end if;
   q:=aq_private.positive_quantity(p_request->'quantity');
   if q>o.quantity-o.delivered_quantity then raise exception 'Delivered quantity cannot exceed the pending quantity.'; end if;
   select quantity into reserved from aq_private.reservations where order_id=id for update;
   if coalesce(reserved,0)<q then raise exception 'Not enough reserved gallons for this order.'; end if;
   if inv.physical_filled<q then raise exception 'Not enough filled gallons.'; end if;
   update aq_private.reservations set quantity=quantity-q,updated_at=clock_timestamp() where order_id=id;
   update aq_private.inventory set physical_filled=physical_filled-q,updated_at=clock_timestamp() where singleton;
   update aq_private.orders set delivered_quantity=delivered_quantity+q,status=case when delivered_quantity+q=quantity then 'delivered' else 'undelivered' end,updated_at=clock_timestamp() where orders.id=id;
   insert into aq_private.deliveries(order_id,quantity,recorded_by) values(id,q,a);
  end if;
  result:=aq_private.order_json(id);
 elsif op='create_sale' then
  perform aq_private.require_keys(p_request,array['operation','request_id','customer_id','customer_name','quantity','cash_centavos','confirm_credit']);
  if coalesce(length(btrim(p_request->>'customer_name')),0)=0 then raise exception 'Enter the customer name.'; end if;
  q:=aq_private.positive_quantity(p_request->'quantity'); cash:=aq_private.money(coalesce(p_request->'cash_centavos','0'::jsonb),false); total:=q::bigint*3500;
  if cash<total and p_request->'confirm_credit' is distinct from 'true'::jsonb then raise exception 'Confirm recording the remaining balance as credit.'; end if;
  select * into inv from aq_private.inventory where singleton for update;
  select coalesce(sum(quantity),0) into reserved from aq_private.reservations;
  if inv.physical_filled-reserved<q then raise exception 'Not enough filled gallons.'; end if;
  insert into aq_private.sales(reference,customer_id,customer_name,quantity,cash_tendered_centavos,change_centavos,recorded_by)
  values(aq_private.new_reference('SALE'),nullif(p_request->>'customer_id','')::uuid,btrim(p_request->>'customer_name'),q,cash,greatest(cash-total,0),a) returning sales.id into id;
  if least(cash,total)>0 then insert into aq_private.payments(reference,sale_id,amount_centavos,recorded_by) values(aq_private.new_reference('PAY'),id,least(cash,total),a); end if;
  update aq_private.inventory set physical_filled=physical_filled-q,updated_at=clock_timestamp() where singleton;
  result:=aq_private.sale_json(id);
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
 if exists(select 1 from aq_private.inventory where physical_filled<(select coalesce(sum(quantity),0) from aq_private.reservations)) then raise exception 'Stock reservation invariant violated.'; end if;
 update aq_private.requests set result=aq_command.result where actor_id=a and request_id=rid;
 return result;
end $$;

create function aq_private.financial_summary(p_start date,p_end date) returns jsonb language plpgsql stable set search_path='' as $$
declare revenue bigint; receipts bigint; expense bigint; outstanding bigint;
begin
 if p_start is null or p_end is null then raise exception 'Complete the required fields.'; end if;
 if p_end<p_start then raise exception 'The end date cannot be earlier than the start date.'; end if;
 select coalesce(sum(v),0) into revenue from (
 select quantity::bigint*unit_price_centavos v from aq_private.orders where business_date between p_start and p_end
 union all select quantity::bigint*unit_price_centavos from aq_private.sales where (created_at at time zone 'Asia/Manila')::date between p_start and p_end) x;
 select coalesce(sum(amount_centavos),0) into receipts from aq_private.payments where (received_at at time zone 'Asia/Manila')::date between p_start and p_end;
 select coalesce(sum(amount_centavos),0) into expense from aq_private.expenses where business_date between p_start and p_end;
 select coalesce(sum(v),0) into outstanding from (
  select o.quantity::bigint*o.unit_price_centavos-coalesce((select sum(p.amount_centavos) from aq_private.payments p where p.order_id=o.id and (p.received_at at time zone 'Asia/Manila')::date<=p_end),0) v from aq_private.orders o where business_date<=p_end
  union all select s.quantity::bigint*s.unit_price_centavos-coalesce((select sum(p.amount_centavos) from aq_private.payments p where p.sale_id=s.id and (p.received_at at time zone 'Asia/Manila')::date<=p_end),0) from aq_private.sales s where (created_at at time zone 'Asia/Manila')::date<=p_end) x;
 return jsonb_build_object('station','Aqua Quenchers','start_date',p_start,'end_date',p_end,'generated_at',clock_timestamp(),
 'revenue_centavos',revenue,'payments_received_centavos',receipts,'expenses_centavos',expense,
 'net_income_centavos',receipts-expense,'outstanding_centavos',outstanding,'calculation','Payments received - Expenses','timezone','Asia/Manila');
end $$;

create function public.aq_query(p_request jsonb) returns jsonb language plpgsql security definer set search_path='' as $$
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
  where (coalesce(o.reference,s.reference) ilike '%'||search||'%') and (nullif(p_request->>'customer_id','') is null or o.customer_id=(p_request->>'customer_id')::uuid or s.customer_id=(p_request->>'customer_id')::uuid)
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

-- Supabase creates PUBLIC execute grants by default; explicitly close every helper.
revoke all on all functions in schema aq_private from public,anon,authenticated;
revoke all on function public.aq_me(),public.aq_command(jsonb),public.aq_query(jsonb) from public,anon;
grant execute on function public.aq_me(),public.aq_command(jsonb),public.aq_query(jsonb) to authenticated;
commit;
