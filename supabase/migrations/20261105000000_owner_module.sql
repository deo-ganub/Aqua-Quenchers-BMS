-- Aqua Quenchers: Owner module database (Requirements v3.30.10.2026).
-- Paste into Supabase Dashboard > SQL Editor and run once. Safe to re-run.
-- It only adds to the database: no table, column or row is dropped.
--
-- Rules enforced here rather than in the app:
--   * Clients can only READ tables. Every write goes through a function below,
--     so prices, stock, reservations and history cannot be changed directly.
--   * Inactive accounts lose access immediately, even with a valid session,
--     because every policy and function re-reads profiles.status.
--   * Stock changes use row locks inside one transaction, and each save carries
--     an idempotency key so a retry cannot apply twice.

-- ---------------------------------------------------------------- profiles --
create table if not exists public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  role text not null default 'staff'
);
alter table public.profiles add column if not exists username text;
alter table public.profiles add column if not exists full_name text;
alter table public.profiles add column if not exists contact_number text;
alter table public.profiles add column if not exists status text not null default 'Active';
alter table public.profiles add column if not exists created_at timestamptz not null default now();
alter table public.profiles add column if not exists updated_at timestamptz not null default now();
create unique index if not exists profiles_username_unique on public.profiles (lower(username));
do $$ begin
  alter table public.profiles add constraint profiles_status_check check (status in ('Active', 'Inactive'));
exception when duplicate_object then null; end $$;

-- ------------------------------------------------------------------ tables --
create table if not exists public.customers (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) > 0),
  customer_type text not null check (customer_type in ('Household', 'Dealer', 'Walk-in')),
  contact_number text not null unique check (contact_number ~ '^[0-9]{11}$'),
  address text not null check (length(trim(address)) > 0),
  status text not null default 'Active' check (status in ('Active', 'Inactive')),
  recorded_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  order_no text not null unique,
  customer_id uuid not null references public.customers (id),
  customer_name text not null,
  customer_type text not null check (customer_type in ('Household', 'Dealer', 'Walk-in')),
  delivery_address text not null,
  unit_price numeric(10, 2) not null check (unit_price > 0),
  original_gallons integer not null check (original_gallons > 0),
  gallons integer not null check (gallons > 0),
  collected_gallons integer check (collected_gallons > 0),
  delivered_gallons integer not null default 0 check (delivered_gallons >= 0),
  reserved_gallons integer not null default 0 check (reserved_gallons >= 0),
  total numeric(12, 2) generated always as (gallons * unit_price) stored,
  paid_amount numeric(12, 2) not null default 0 check (paid_amount >= 0),
  payment_status text generated always as (
    case when paid_amount <= 0 then 'Unpaid'
         when paid_amount >= gallons * unit_price then 'Paid'
         else 'Partially Paid' end
  ) stored,
  status text not null default 'Ordered' check (status in (
    'Ordered', 'For Pickup', 'Picked Up', 'Refilling', 'Ready for Delivery',
    'Delivered', 'Undelivered', 'Rescheduled')),
  order_date date not null,
  pickup_date date not null,
  notes text,
  recorded_by uuid not null references public.profiles (id),
  recorded_by_name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (delivered_gallons <= gallons),
  check (paid_amount <= gallons * unit_price)
);

create table if not exists public.order_events (
  id bigint generated always as identity primary key,
  order_id uuid not null references public.orders (id),
  action text not null,
  recorded_by_name text not null,
  created_at timestamptz not null default now()
);

create table if not exists public.walkin_sales (
  id uuid primary key default gen_random_uuid(),
  sale_no text not null unique,
  customer_id uuid references public.customers (id),
  customer_name text not null check (length(trim(customer_name)) > 0),
  gallons integer not null check (gallons > 0),
  unit_price numeric(10, 2) not null check (unit_price > 0),
  total numeric(12, 2) generated always as (gallons * unit_price) stored,
  paid_amount numeric(12, 2) not null default 0 check (paid_amount >= 0),
  payment_status text generated always as (
    case when paid_amount <= 0 then 'Unpaid'
         when paid_amount >= gallons * unit_price then 'Paid'
         else 'Partially Paid' end
  ) stored,
  cash_tendered numeric(12, 2) not null check (cash_tendered >= 0),
  change_amount numeric(12, 2) not null check (change_amount >= 0),
  sale_date date not null,
  recorded_by uuid not null references public.profiles (id),
  recorded_by_name text not null,
  created_at timestamptz not null default now(),
  check (paid_amount <= gallons * unit_price)
);

create table if not exists public.payments (
  id uuid primary key default gen_random_uuid(),
  payment_no text not null unique,
  order_id uuid references public.orders (id),
  sale_id uuid references public.walkin_sales (id),
  customer_id uuid references public.customers (id),
  customer_name text not null,
  source_no text not null,
  amount numeric(12, 2) not null check (amount > 0),
  method text not null default 'Cash',
  payment_date date not null,
  recorded_by uuid not null references public.profiles (id),
  recorded_by_name text not null,
  created_at timestamptz not null default now(),
  check ((order_id is null) <> (sale_id is null))
);

create table if not exists public.expenses (
  id uuid primary key default gen_random_uuid(),
  expense_date date not null,
  category text not null check (category in (
    'Fuel', 'Electricity', 'Water', 'Maintenance', 'Supplies', 'Salary', 'Others')),
  amount numeric(12, 2) not null check (amount > 0),
  remarks text check (char_length(remarks) <= 500),
  selection_source text not null default 'manual' check (selection_source in ('manual', 'suggestion')),
  model_version text,
  recorded_by uuid not null references public.profiles (id),
  recorded_by_name text not null,
  created_at timestamptz not null default now()
);

-- One row only. Opening counts are set by the controlled setup step in SETUP.md.
create table if not exists public.inventory (
  id smallint primary key default 1 check (id = 1),
  physical_filled integer not null default 0,
  reserved_filled integer not null default 0,
  empty_gallons integer not null default 0,
  updated_at timestamptz not null default now(),
  check (empty_gallons >= 0),
  check (reserved_filled >= 0),
  check (physical_filled >= reserved_filled)
);
insert into public.inventory (id) values (1) on conflict (id) do nothing;

create table if not exists public.reference_counters (
  prefix text not null,
  year integer not null,
  last_value integer not null default 0,
  primary key (prefix, year)
);

create table if not exists public.idempotency_keys (
  actor uuid not null,
  key uuid not null,
  action text not null,
  payload_hash bytea,
  result jsonb,
  created_at timestamptz not null default now(),
  primary key (actor, key)
);
alter table public.idempotency_keys add column if not exists payload_hash bytea;

-- Protected audit history: no client can read it and there is no screen for it.
create table if not exists public.audit_log (
  id bigint generated always as identity primary key,
  actor uuid,
  action text not null,
  entity text not null,
  entity_id text,
  details jsonb,
  created_at timestamptz not null default now()
);

create index if not exists orders_status_idx on public.orders (status);
create index if not exists orders_order_date_idx on public.orders (order_date);
create index if not exists orders_customer_idx on public.orders (customer_id);
create index if not exists order_events_order_idx on public.order_events (order_id);
create index if not exists payments_date_idx on public.payments (payment_date);
create index if not exists payments_customer_idx on public.payments (customer_id);
create index if not exists sales_date_idx on public.walkin_sales (sale_date);
create index if not exists expenses_date_idx on public.expenses (expense_date);

-- ----------------------------------------------------------------- helpers --
create or replace function public.manila_today() returns date
language sql stable set search_path = '' as $$
  select (now() at time zone 'Asia/Manila')::date
$$;

create or replace function public.app_role() returns text
language sql stable security definer set search_path = '' as $$
  select p.role::text from public.profiles p
  where p.id = auth.uid() and p.status = 'Active'
$$;

create or replace function public.is_active_user() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(public.app_role() in ('owner', 'staff'), false)
$$;

create or replace function public.is_owner() returns boolean
language sql stable security definer set search_path = '' as $$
  select coalesce(public.app_role() = 'owner', false)
$$;

-- Returns the caller's id and name, or stops the request if access was removed.
create or replace function public.require_active(out actor uuid, out actor_name text)
language plpgsql stable security definer set search_path = '' as $$
begin
  select p.id, coalesce(p.full_name, p.username, 'Account') into actor, actor_name
  from public.profiles p
  where p.id = auth.uid() and p.status = 'Active' and p.role::text in ('owner', 'staff');
  if actor is null then
    raise exception 'Session expired. Sign in again.'
      using detail = 'The account was idle for 30 minutes or its active access was removed. Protected requests are denied.';
  end if;
end $$;

create or replace function public.require_owner(out actor uuid, out actor_name text)
language plpgsql stable security definer set search_path = '' as $$
begin
  select r.actor, r.actor_name into actor, actor_name from public.require_active() r;
  if not public.is_owner() then
    raise exception 'This action is limited to the Owner.';
  end if;
end $$;

create or replace function public.next_reference(p_prefix text) returns text
language plpgsql security definer set search_path = '' as $$
declare
  v_year integer := extract(year from public.manila_today());
  v_next integer;
begin
  insert into public.reference_counters as c (prefix, year, last_value)
  values (p_prefix, v_year, 1)
  on conflict (prefix, year) do update set last_value = c.last_value + 1
  returning last_value into v_next;
  return p_prefix || '-' || v_year || '-' || lpad(v_next::text, 4, '0');
end $$;

-- Claims a key for this request. A non-null result means the save already
-- happened, so the caller returns it unchanged. A concurrent duplicate waits
-- here until the first request commits.
-- A retry with the same key returns the stored result. The key is bound to a
-- SHA-256 fingerprint of the request, so the same key sent with different data
-- is a conflict rather than a second, different save.
drop function if exists public.idem_claim(uuid, uuid, text);
create or replace function public.idem_claim(p_actor uuid, p_key uuid, p_action text, p_payload jsonb) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_hash bytea := sha256(convert_to(coalesce(p_payload::text, ''), 'UTF8'));
  v_row public.idempotency_keys;
begin
  if p_key is null then
    raise exception 'Missing idempotency key.';
  end if;
  insert into public.idempotency_keys (actor, key, action, payload_hash)
  values (p_actor, p_key, p_action, v_hash) on conflict do nothing;
  if found then return null; end if;
  select * into v_row from public.idempotency_keys i
  where i.actor = p_actor and i.key = p_key;
  if v_row.action <> p_action or v_row.payload_hash is distinct from v_hash then
    raise exception 'Request key was already used with different data.'
      using detail = 'This retry does not match the original request, so nothing was saved. Start the action again to get a new request key.';
  end if;
  return v_row.result;
end $$;

create or replace function public.idem_finish(p_actor uuid, p_key uuid, p_result jsonb) returns jsonb
language sql security definer set search_path = '' as $$
  update public.idempotency_keys set result = p_result where actor = p_actor and key = p_key;
  select p_result;
$$;

create or replace function public.audit(p_actor uuid, p_action text, p_entity text, p_entity_id text, p_details jsonb)
returns void language sql security definer set search_path = '' as $$
  insert into public.audit_log (actor, action, entity, entity_id, details)
  values (p_actor, p_action, p_entity, p_entity_id, p_details);
$$;

create or replace function public.unit_price_for(p_type text) returns numeric
language sql immutable set search_path = '' as $$
  select case p_type when 'Dealer' then 30.00 when 'Household' then 35.00 when 'Walk-in' then 35.00 end
$$;

create or replace function public.peso(p numeric) returns text
language sql immutable set search_path = '' as $$
  select '₱' || to_char(p, 'FM999,999,990.00')
$$;

-- --------------------------------------------------------------- customers --
create or replace function public.save_customer(
  p_id uuid, p_name text, p_type text, p_contact text, p_address text, p_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_other text; v_row public.customers;
  v_contact text := regexp_replace(coalesce(p_contact, ''), '[^0-9]', '', 'g');
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'save_customer',
    jsonb_build_object('id', p_id, 'name', p_name, 'type', p_type, 'contact', p_contact, 'address', p_address));
  if v_stored is not null then return v_stored; end if;

  if coalesce(trim(p_name), '') = '' or coalesce(p_type, '') = ''
     or coalesce(trim(p_contact), '') = '' or coalesce(trim(p_address), '') = '' then
    raise exception 'Complete the required fields.'
      using detail = 'Enter Full name, Customer type, Contact number and Address. No customer record was saved.';
  end if;
  if p_type not in ('Household', 'Dealer', 'Walk-in') then
    raise exception 'Complete the required fields.' using detail = 'Choose a valid customer type.';
  end if;
  if length(v_contact) <> 11 then
    raise exception 'Please enter a valid contact number. Use 11 digits.'
      using detail = 'No customer record was saved.';
  end if;

  perform pg_advisory_xact_lock(hashtext('customer_contact:' || v_contact));
  select c.name into v_other from public.customers c
  where c.contact_number = v_contact and c.id is distinct from p_id;
  if found then
    raise exception 'Customer already exists.'
      using detail = v_contact || ' already belongs to ' || v_other || '.';
  end if;

  if p_id is null then
    insert into public.customers (name, customer_type, contact_number, address, recorded_by)
    values (trim(p_name), p_type, v_contact, trim(p_address), a.actor)
    returning * into v_row;
  else
    update public.customers
    set name = trim(p_name), customer_type = p_type, contact_number = v_contact,
        address = trim(p_address), updated_at = now()
    where id = p_id returning * into v_row;
    if not found then raise exception 'Customer not found.'; end if;
  end if;
  perform public.audit(a.actor, case when p_id is null then 'create' else 'update' end,
                       'customer', v_row.id::text, to_jsonb(v_row));
  return public.idem_finish(a.actor, p_key, to_jsonb(v_row));
end $$;

-- ------------------------------------------------------------------ orders --
create or replace function public.create_order(
  p_customer_id uuid, p_customer_type text, p_gallons integer, p_order_date date, p_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_customer public.customers; v_row public.orders;
  v_price numeric := public.unit_price_for(p_customer_type);
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'create_order',
    jsonb_build_object('customer_id', p_customer_id, 'customer_type', p_customer_type, 'gallons', p_gallons, 'order_date', p_order_date));
  if v_stored is not null then return v_stored; end if;

  select * into v_customer from public.customers where id = p_customer_id;
  if not found or v_price is null then
    raise exception 'Complete the required fields.' using detail = 'Select a customer and customer type.';
  end if;
  if p_gallons is null or p_gallons <= 0 then
    raise exception 'Enter a valid gallon quantity.' using detail = 'No order was saved.';
  end if;

  insert into public.orders (
    order_no, customer_id, customer_name, customer_type, delivery_address, unit_price,
    original_gallons, gallons, order_date, pickup_date, recorded_by, recorded_by_name)
  values (
    public.next_reference('ORD'), v_customer.id, v_customer.name, p_customer_type, v_customer.address, v_price,
    p_gallons, p_gallons, coalesce(p_order_date, public.manila_today()),
    greatest(coalesce(p_order_date, public.manila_today()), public.manila_today()), a.actor, a.actor_name)
  returning * into v_row;

  insert into public.order_events (order_id, action, recorded_by_name) values (v_row.id, 'Ordered', a.actor_name);
  perform public.audit(a.actor, 'create', 'order', v_row.id::text, to_jsonb(v_row));
  return public.idem_finish(a.actor, p_key, to_jsonb(v_row));
end $$;

-- Handles only the stages that need no extra input. Pickup, reschedule and
-- delivery have their own functions because they collect quantities or dates.
create or replace function public.set_order_status(p_order_id uuid, p_status text, p_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_order public.orders; v_inv public.inventory; v_result jsonb;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'set_order_status',
    jsonb_build_object('order_id', p_order_id, 'status', p_status));
  if v_stored is not null then return v_stored; end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then raise exception 'Order not found.'; end if;

  if not ((v_order.status = 'Ordered' and p_status = 'For Pickup')
       or (v_order.status = 'Rescheduled' and p_status = 'For Pickup')
       or (v_order.status = 'Picked Up' and p_status = 'Refilling')
       or (v_order.status = 'Refilling' and p_status = 'Ready for Delivery')) then
    raise exception 'Follow the next eligible order stage.'
      using detail = v_order.status || ' cannot change directly to ' || p_status
        || '. Complete pickup, refilling and delivery recording in their eligible sequence. The saved status is unchanged.';
  end if;

  v_result := jsonb_build_object('order_no', v_order.order_no, 'gallons', v_order.gallons, 'status', p_status);

  if p_status = 'Ready for Delivery' then
    select * into v_inv from public.inventory where id = 1 for update;
    if v_inv.empty_gallons < v_order.gallons then
      raise exception 'Not enough empty gallons.'
        using detail = 'Refilling order requires ' || v_order.gallons || ' gallons. Empty stock: ' || v_inv.empty_gallons
          || E'\nReady for Delivery was not saved. Order status and stock remain unchanged.';
    end if;
    update public.inventory
    set empty_gallons = empty_gallons - v_order.gallons,
        physical_filled = physical_filled + v_order.gallons,
        reserved_filled = reserved_filled + v_order.gallons,
        updated_at = now()
    where id = 1;
    update public.orders set reserved_gallons = v_order.gallons where id = p_order_id;
    v_result := v_result || jsonb_build_object(
      'empty_before', v_inv.empty_gallons, 'empty_after', v_inv.empty_gallons - v_order.gallons,
      'filled_before', v_inv.physical_filled, 'filled_after', v_inv.physical_filled + v_order.gallons,
      'reserved_before', v_inv.reserved_filled, 'reserved_after', v_inv.reserved_filled + v_order.gallons,
      'available', v_inv.physical_filled - v_inv.reserved_filled);
  end if;

  update public.orders set status = p_status, updated_at = now() where id = p_order_id;
  insert into public.order_events (order_id, action, recorded_by_name) values (p_order_id, p_status, a.actor_name);
  perform public.audit(a.actor, 'status', 'order', p_order_id::text,
                       jsonb_build_object('from', v_order.status, 'to', p_status));
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

create or replace function public.record_pickup(p_order_id uuid, p_collected integer, p_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_order public.orders; v_inv public.inventory; v_new_total numeric; v_result jsonb;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'record_pickup',
    jsonb_build_object('order_id', p_order_id, 'collected', p_collected));
  if v_stored is not null then return v_stored; end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then raise exception 'Order not found.'; end if;
  if v_order.status <> 'For Pickup' then
    raise exception 'Follow the next eligible order stage.'
      using detail = 'Only a For Pickup order can record a pickup. Current status: ' || v_order.status || '.';
  end if;
  if p_collected is null or p_collected <= 0 then
    raise exception 'Quantity must be greater than zero.' using detail = 'Order, charge and stock remain unchanged.';
  end if;
  if p_collected > v_order.original_gallons then
    raise exception 'Collected quantity cannot exceed the expected quantity.'
      using detail = 'Expected: ' || v_order.original_gallons || ' gallons · Attempted collection: ' || p_collected
        || '. Order, charge and stock remain unchanged.';
  end if;

  v_new_total := p_collected * v_order.unit_price;
  if v_new_total < v_order.paid_amount then
    raise exception 'Adjusted total cannot be less than the amount already paid.'
      using detail = 'Paid: ' || public.peso(v_order.paid_amount) || ' · Actual: ' || p_collected || ' × '
        || public.peso(v_order.unit_price) || ' = ' || public.peso(v_new_total)
        || E'.\nNo change saved; refer for Owner-approved admin correction.';
  end if;

  select * into v_inv from public.inventory where id = 1 for update;
  update public.inventory set empty_gallons = empty_gallons + p_collected, updated_at = now() where id = 1;
  update public.orders
  set gallons = p_collected, collected_gallons = p_collected, status = 'Picked Up', updated_at = now()
  where id = p_order_id;
  insert into public.order_events (order_id, action, recorded_by_name)
  values (p_order_id, 'Picked Up · ' || p_collected || ' gallons', a.actor_name);

  v_result := jsonb_build_object(
    'order_no', v_order.order_no, 'customer_name', v_order.customer_name,
    'expected', v_order.original_gallons, 'collected', p_collected,
    'shortfall', v_order.original_gallons - p_collected,
    'unit_price', v_order.unit_price, 'total', v_new_total,
    'empty_before', v_inv.empty_gallons, 'empty_after', v_inv.empty_gallons + p_collected);
  perform public.audit(a.actor, 'pickup', 'order', p_order_id::text, v_result);
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

create or replace function public.reschedule_pickup(p_order_id uuid, p_new_date date, p_reason text, p_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare a record; v_stored jsonb; v_order public.orders; v_result jsonb;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'reschedule_pickup',
    jsonb_build_object('order_id', p_order_id, 'new_date', p_new_date, 'reason', p_reason));
  if v_stored is not null then return v_stored; end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then raise exception 'Order not found.'; end if;
  if v_order.status not in ('Ordered', 'For Pickup', 'Rescheduled') then
    raise exception 'Follow the next eligible order stage.'
      using detail = 'A ' || v_order.status || ' order cannot be rescheduled.';
  end if;
  if p_new_date is null or p_new_date < public.manila_today() then
    raise exception 'Select today or a future date.' using detail = 'The pickup date was not changed.';
  end if;

  update public.orders
  set status = 'Rescheduled', pickup_date = p_new_date,
      notes = nullif(trim(coalesce(p_reason, '')), ''), updated_at = now()
  where id = p_order_id;
  insert into public.order_events (order_id, action, recorded_by_name)
  values (p_order_id, 'Rescheduled · ' || to_char(p_new_date, 'DD Mon YYYY'), a.actor_name);

  v_result := jsonb_build_object('order_no', v_order.order_no, 'customer_name', v_order.customer_name,
    'new_date', p_new_date, 'reason', nullif(trim(coalesce(p_reason, '')), ''));
  perform public.audit(a.actor, 'reschedule', 'order', p_order_id::text, v_result);
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

create or replace function public.record_delivery(p_order_id uuid, p_quantity integer, p_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_order public.orders; v_inv public.inventory;
  v_pending integer; v_status text; v_result jsonb;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'record_delivery',
    jsonb_build_object('order_id', p_order_id, 'quantity', p_quantity));
  if v_stored is not null then return v_stored; end if;

  select * into v_order from public.orders where id = p_order_id for update;
  if not found then raise exception 'Order not found.'; end if;
  if v_order.status not in ('Ready for Delivery', 'Undelivered') then
    raise exception 'Follow the next eligible order stage.'
      using detail = 'Only a Ready for Delivery or Undelivered order can record a delivery.';
  end if;

  v_pending := v_order.gallons - v_order.delivered_gallons;
  if p_quantity is null or p_quantity <= 0 then
    raise exception 'Quantity must be greater than zero.'
      using detail = 'Previously delivered: ' || v_order.delivered_gallons || ' · Remaining: ' || v_pending
        || E'\nOrder and stock are unchanged.';
  end if;
  if p_quantity > v_pending then
    raise exception 'Delivered quantity cannot exceed the pending quantity.'
      using detail = 'Remaining: ' || v_pending || ' · Attempted delivery: ' || p_quantity
        || E'.\nNo delivery or stock change was saved.';
  end if;
  if p_quantity > v_order.reserved_gallons then
    raise exception 'Not enough reserved gallons for this order.'
      using detail = 'Remaining: ' || v_pending || ' · Reserved for this order: ' || v_order.reserved_gallons
        || E'\nAttempted delivery: ' || p_quantity || '. No delivery or stock change was saved.';
  end if;

  select * into v_inv from public.inventory where id = 1 for update;
  if p_quantity > v_inv.physical_filled then
    raise exception 'Not enough filled gallons.'
      using detail = 'Physical filled stock: ' || v_inv.physical_filled || E'.\nNo delivery or stock change was saved.';
  end if;

  update public.inventory
  set physical_filled = physical_filled - p_quantity,
      reserved_filled = reserved_filled - p_quantity,
      updated_at = now()
  where id = 1;

  v_status := case when p_quantity = v_pending then 'Delivered' else 'Undelivered' end;
  update public.orders
  set delivered_gallons = delivered_gallons + p_quantity,
      reserved_gallons = reserved_gallons - p_quantity,
      status = v_status, updated_at = now()
  where id = p_order_id;
  insert into public.order_events (order_id, action, recorded_by_name)
  values (p_order_id, v_status || ' · ' || p_quantity || ' gallons', a.actor_name);

  v_result := jsonb_build_object(
    'order_no', v_order.order_no, 'customer_name', v_order.customer_name,
    'delivered_now', p_quantity, 'total_delivered', v_order.delivered_gallons + p_quantity,
    'gallons', v_order.gallons, 'pending', v_pending - p_quantity, 'status', v_status,
    'filled_before', v_inv.physical_filled, 'filled_after', v_inv.physical_filled - p_quantity,
    'reserved_before', v_inv.reserved_filled, 'reserved_after', v_inv.reserved_filled - p_quantity,
    'available', v_inv.physical_filled - v_inv.reserved_filled);
  perform public.audit(a.actor, 'delivery', 'order', p_order_id::text, v_result);
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

-- ---------------------------------------------------------- walk-in & cash --
create or replace function public.complete_walkin_sale(
  p_customer_name text, p_customer_id uuid, p_gallons integer, p_cash numeric, p_confirm_credit boolean, p_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_inv public.inventory; v_price numeric := public.unit_price_for('Walk-in');
  v_total numeric; v_paid numeric; v_change numeric; v_available integer; v_today date := public.manila_today();
  v_sale public.walkin_sales; v_payment_no text; v_result jsonb;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'complete_walkin_sale',
    jsonb_build_object('customer_name', p_customer_name, 'customer_id', p_customer_id, 'gallons', p_gallons, 'cash', p_cash, 'confirm_credit', p_confirm_credit));
  if v_stored is not null then return v_stored; end if;

  if coalesce(trim(p_customer_name), '') = '' then
    raise exception 'Enter the customer name.'
      using detail = 'Customer name is required for a Walk-in sale. No saved customer profile or delivery address is required. No sale or stock deduction was saved.';
  end if;
  if p_gallons is null or p_gallons <= 0 then
    raise exception 'Enter a valid gallon quantity.' using detail = 'No sale or stock deduction was saved.';
  end if;
  if p_cash is null or p_cash < 0 then
    raise exception 'Enter a valid cash amount.' using detail = 'Enter a numeric amount of zero or greater.';
  end if;

  select * into v_inv from public.inventory where id = 1 for update;
  v_available := v_inv.physical_filled - v_inv.reserved_filled;
  if p_gallons > v_available then
    raise exception 'Not enough filled gallons.'
      using detail = trim(p_customer_name) || ' · Attempted purchase ' || p_gallons || E' gallons\nAvailable filled stock: '
        || v_available || E' gallons\nNo sale or stock deduction was saved.';
  end if;

  v_total := p_gallons * v_price;
  v_paid := least(p_cash, v_total);
  v_change := greatest(p_cash - v_total, 0);
  if v_paid < v_total and not coalesce(p_confirm_credit, false) then
    raise exception 'Confirm credit balance?'
      using detail = 'Confirm to record ' || public.peso(v_total - v_paid) || ' as credit.';
  end if;

  update public.inventory set physical_filled = physical_filled - p_gallons, updated_at = now() where id = 1;
  insert into public.walkin_sales (sale_no, customer_id, customer_name, gallons, unit_price, paid_amount,
    cash_tendered, change_amount, sale_date, recorded_by, recorded_by_name)
  values (public.next_reference('WI'), p_customer_id, trim(p_customer_name), p_gallons, v_price, v_paid,
    p_cash, v_change, v_today, a.actor, a.actor_name)
  returning * into v_sale;

  if v_paid > 0 then
    v_payment_no := public.next_reference('PAY');
    insert into public.payments (payment_no, sale_id, customer_id, customer_name, source_no, amount,
      payment_date, recorded_by, recorded_by_name)
    values (v_payment_no, v_sale.id, p_customer_id, v_sale.customer_name, v_sale.sale_no, v_paid,
      v_today, a.actor, a.actor_name);
  end if;

  v_result := jsonb_build_object(
    'sale_no', v_sale.sale_no, 'customer_name', v_sale.customer_name, 'gallons', p_gallons,
    'unit_price', v_price, 'total', v_total, 'cash', p_cash, 'change', v_change,
    'credit', v_total - v_paid, 'payment_status', v_sale.payment_status,
    'payment_no', v_payment_no, 'filled_after', v_available - p_gallons);
  perform public.audit(a.actor, 'create', 'walkin_sale', v_sale.id::text, v_result);
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

-- The payment date is always today in Manila: cash cannot be backdated.
create or replace function public.record_payment(p_order_id uuid, p_sale_id uuid, p_amount numeric, p_key uuid)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  a record; v_stored jsonb; v_order public.orders; v_sale public.walkin_sales; v_balance numeric;
  v_no text; v_source text; v_status text; v_result jsonb;
  v_customer uuid; v_name text; v_total numeric; v_paid numeric;
begin
  select * into a from public.require_active();
  v_stored := public.idem_claim(a.actor, p_key, 'record_payment',
    jsonb_build_object('order_id', p_order_id, 'sale_id', p_sale_id, 'amount', p_amount));
  if v_stored is not null then return v_stored; end if;

  if (p_order_id is null) = (p_sale_id is null) then
    raise exception 'Select one order or Walk-in reference.';
  end if;

  if p_order_id is not null then
    select * into v_order from public.orders where id = p_order_id for update;
    if not found then raise exception 'Order not found.'; end if;
    v_source := v_order.order_no; v_customer := v_order.customer_id; v_name := v_order.customer_name;
    v_total := v_order.total; v_paid := v_order.paid_amount;
  else
    select * into v_sale from public.walkin_sales where id = p_sale_id for update;
    if not found then raise exception 'Walk-in sale not found.'; end if;
    v_source := v_sale.sale_no; v_customer := v_sale.customer_id; v_name := v_sale.customer_name;
    v_total := v_sale.total; v_paid := v_sale.paid_amount;
  end if;

  v_balance := v_total - v_paid;
  if p_amount is null or p_amount <= 0 or p_amount > v_balance or round(p_amount, 2) <> p_amount then
    raise exception 'Enter an amount greater than zero and not above the balance.'
      using detail = v_source || ' · ' || v_name || E'\nOutstanding: ' || public.peso(v_balance) || E'\nNo receipt was saved.';
  end if;

  v_no := public.next_reference('PAY');
  insert into public.payments (payment_no, order_id, sale_id, customer_id, customer_name, source_no, amount,
    payment_date, recorded_by, recorded_by_name)
  values (v_no, p_order_id, p_sale_id, v_customer, v_name, v_source, p_amount,
    public.manila_today(), a.actor, a.actor_name);

  if p_order_id is not null then
    update public.orders set paid_amount = paid_amount + p_amount, updated_at = now()
    where id = p_order_id returning payment_status into v_status;
  else
    update public.walkin_sales set paid_amount = paid_amount + p_amount
    where id = p_sale_id returning payment_status into v_status;
  end if;

  v_result := jsonb_build_object('payment_no', v_no, 'source_no', v_source, 'customer_name', v_name,
    'amount', p_amount, 'total_paid', v_paid + p_amount, 'payment_status', v_status,
    'balance', v_balance - p_amount);
  perform public.audit(a.actor, 'create', 'payment', v_no, v_result);
  return public.idem_finish(a.actor, p_key, v_result);
end $$;

-- ---------------------------------------------------------------- expenses --
create or replace function public.save_expense(
  p_date date, p_category text, p_amount numeric, p_remarks text, p_source text, p_model_version text, p_key uuid
) returns jsonb language plpgsql security definer set search_path = '' as $$
declare a record; v_stored jsonb; v_row public.expenses;
begin
  select * into a from public.require_owner();
  v_stored := public.idem_claim(a.actor, p_key, 'save_expense',
    jsonb_build_object('date', p_date, 'category', p_category, 'amount', p_amount, 'remarks', p_remarks, 'source', p_source, 'model_version', p_model_version));
  if v_stored is not null then return v_stored; end if;

  if p_date is null or coalesce(p_category, '') = '' then
    raise exception 'Complete the required fields.'
      using detail = 'Expense date or category is missing. Enter the required values before saving. The ledger is unchanged.';
  end if;
  if p_category not in ('Fuel', 'Electricity', 'Water', 'Maintenance', 'Supplies', 'Salary', 'Others') then
    raise exception 'Complete the required fields.' using detail = 'Choose one of the fixed expense categories.';
  end if;
  if p_amount is null or p_amount <= 0 or round(p_amount, 2) <> p_amount then
    raise exception 'Enter a valid expense amount.' using detail = 'The amount must be greater than zero.';
  end if;
  if char_length(coalesce(p_remarks, '')) > 500 then
    raise exception 'Use 500 characters or fewer.' using detail = 'Shorten the remarks before saving.';
  end if;

  insert into public.expenses (expense_date, category, amount, remarks, selection_source, model_version,
    recorded_by, recorded_by_name)
  values (p_date, p_category, p_amount, nullif(trim(coalesce(p_remarks, '')), ''),
    case when p_source = 'suggestion' then 'suggestion' else 'manual' end,
    case when p_source = 'suggestion' then p_model_version end, a.actor, a.actor_name)
  returning * into v_row;

  perform public.audit(a.actor, 'create', 'expense', v_row.id::text, to_jsonb(v_row));
  return public.idem_finish(a.actor, p_key, to_jsonb(v_row));
end $$;

-- ----------------------------------------------------------------- reports --
create or replace function public.financial_summary(p_start date, p_end date)
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_revenue numeric; v_payments numeric; v_expenses numeric; v_outstanding numeric;
begin
  perform public.require_owner();
  if p_start is null or p_end is null then
    raise exception 'Complete the required fields.' using detail = 'Select a start date and an end date.';
  end if;
  if p_end < p_start then
    raise exception 'The end date cannot be earlier than the start date.'
      using detail = 'Correct the period. The previous valid financial summary is retained.';
  end if;

  select coalesce((select sum(total) from public.orders where order_date between p_start and p_end), 0)
       + coalesce((select sum(total) from public.walkin_sales where sale_date between p_start and p_end), 0)
    into v_revenue;
  select coalesce(sum(amount), 0) into v_payments from public.payments where payment_date between p_start and p_end;
  select coalesce(sum(amount), 0) into v_expenses from public.expenses where expense_date between p_start and p_end;
  select coalesce((select sum(total) from public.orders where order_date <= p_end), 0)
       + coalesce((select sum(total) from public.walkin_sales where sale_date <= p_end), 0)
       - coalesce((select sum(amount) from public.payments where payment_date <= p_end), 0)
    into v_outstanding;

  return jsonb_build_object(
    'start_date', p_start, 'end_date', p_end, 'generated_on', public.manila_today(),
    'revenue', v_revenue, 'payments_received', v_payments, 'expenses', v_expenses,
    'net_income', v_payments - v_expenses, 'outstanding_balances', v_outstanding);
end $$;

create or replace function public.inventory_status()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare v_inv public.inventory; v_pending integer;
begin
  perform public.require_active();
  select * into v_inv from public.inventory where id = 1;
  select coalesce(sum(gallons - delivered_gallons), 0) into v_pending
  from public.orders where status in ('Ready for Delivery', 'Undelivered');
  return jsonb_build_object(
    'filled_available', v_inv.physical_filled - v_inv.reserved_filled,
    'physical_filled', v_inv.physical_filled, 'reserved_filled', v_inv.reserved_filled,
    'empty', v_inv.empty_gallons, 'pending_delivery', v_pending, 'updated_at', v_inv.updated_at);
end $$;

create or replace function public.whoami()
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare a record;
begin
  select * into a from public.require_active();
  return jsonb_build_object('id', a.actor, 'name', a.actor_name, 'role', public.app_role());
end $$;

-- ------------------------------------------------------------------- views --
create or replace view public.orders_view with (security_invoker = true) as
select o.*, c.contact_number as customer_contact,
       o.gallons - o.delivered_gallons as pending_gallons,
       o.total - o.paid_amount as balance,
       public.manila_today() - o.order_date as days_waiting
from public.orders o join public.customers c on c.id = o.customer_id;

create or replace view public.customer_balances with (security_invoker = true) as
select c.*,
       coalesce((select sum(o.total - o.paid_amount) from public.orders o where o.customer_id = c.id), 0)
     + coalesce((select sum(s.total - s.paid_amount) from public.walkin_sales s where s.customer_id = c.id), 0) as balance
from public.customers c;

create or replace view public.payments_view with (security_invoker = true) as
select p.*, coalesce(o.payment_status, s.payment_status) as source_payment_status
from public.payments p
left join public.orders o on o.id = p.order_id
left join public.walkin_sales s on s.id = p.sale_id;

-- ---------------------------------------------------------- access control --
alter table public.profiles enable row level security;
alter table public.customers enable row level security;
alter table public.orders enable row level security;
alter table public.order_events enable row level security;
alter table public.walkin_sales enable row level security;
alter table public.payments enable row level security;
alter table public.expenses enable row level security;
alter table public.inventory enable row level security;
alter table public.reference_counters enable row level security;
alter table public.idempotency_keys enable row level security;
alter table public.audit_log enable row level security;

-- Read-only for active accounts. Revoking first also defeats any older, broader
-- policy, because a role with no table privilege cannot use it.
revoke all on public.profiles, public.customers, public.orders, public.order_events,
  public.walkin_sales, public.payments, public.expenses, public.inventory,
  public.reference_counters, public.idempotency_keys, public.audit_log,
  public.orders_view, public.customer_balances, public.payments_view
  from anon, authenticated;

grant select (id, role, username, full_name, contact_number, status, created_at) on public.profiles to authenticated;
grant select on public.customers, public.orders, public.order_events, public.walkin_sales,
  public.payments, public.expenses, public.inventory,
  public.orders_view, public.customer_balances, public.payments_view
  to authenticated;

drop policy if exists aq_profiles_select on public.profiles;
create policy aq_profiles_select on public.profiles for select to authenticated
  using (id = auth.uid() or public.is_owner());
drop policy if exists aq_customers_select on public.customers;
create policy aq_customers_select on public.customers for select to authenticated using (public.is_active_user());
drop policy if exists aq_orders_select on public.orders;
create policy aq_orders_select on public.orders for select to authenticated using (public.is_active_user());
drop policy if exists aq_order_events_select on public.order_events;
create policy aq_order_events_select on public.order_events for select to authenticated using (public.is_active_user());
drop policy if exists aq_sales_select on public.walkin_sales;
create policy aq_sales_select on public.walkin_sales for select to authenticated using (public.is_active_user());
drop policy if exists aq_payments_select on public.payments;
create policy aq_payments_select on public.payments for select to authenticated using (public.is_active_user());
drop policy if exists aq_inventory_select on public.inventory;
create policy aq_inventory_select on public.inventory for select to authenticated using (public.is_active_user());
drop policy if exists aq_expenses_select on public.expenses;
create policy aq_expenses_select on public.expenses for select to authenticated using (public.is_owner());

-- Internal helpers cannot be called from the API.
revoke all on function public.require_active(), public.require_owner(), public.next_reference(text),
  public.idem_claim(uuid, uuid, text, jsonb), public.idem_finish(uuid, uuid, jsonb),
  public.audit(uuid, text, text, text, jsonb), public.unit_price_for(text), public.peso(numeric),
  public.manila_today()
  from public, anon, authenticated;
-- The views call this one as the signed-in user.
grant execute on function public.manila_today() to authenticated;

revoke all on function public.app_role(), public.is_active_user(), public.is_owner() from public, anon;
grant execute on function public.app_role(), public.is_active_user(), public.is_owner() to authenticated;

revoke all on function public.save_customer(uuid, text, text, text, text, uuid),
  public.create_order(uuid, text, integer, date, uuid),
  public.set_order_status(uuid, text, uuid),
  public.record_pickup(uuid, integer, uuid),
  public.reschedule_pickup(uuid, date, text, uuid),
  public.record_delivery(uuid, integer, uuid),
  public.complete_walkin_sale(text, uuid, integer, numeric, boolean, uuid),
  public.record_payment(uuid, uuid, numeric, uuid),
  public.save_expense(date, text, numeric, text, text, text, uuid),
  public.financial_summary(date, date),
  public.inventory_status(),
  public.whoami()
  from public, anon;
grant execute on function public.save_customer(uuid, text, text, text, text, uuid),
  public.create_order(uuid, text, integer, date, uuid),
  public.set_order_status(uuid, text, uuid),
  public.record_pickup(uuid, integer, uuid),
  public.reschedule_pickup(uuid, date, text, uuid),
  public.record_delivery(uuid, integer, uuid),
  public.complete_walkin_sale(text, uuid, integer, numeric, boolean, uuid),
  public.record_payment(uuid, uuid, numeric, uuid),
  public.save_expense(date, text, numeric, text, text, text, uuid),
  public.financial_summary(date, date),
  public.inventory_status(),
  public.whoami()
  to authenticated;
