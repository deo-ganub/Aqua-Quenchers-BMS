-- IAS hardening (21 Nov 2026)
-- 1. Field-level encryption of customer contact numbers and addresses (customers and order
--    snapshots) with pgcrypto AES-256 (OpenPGP symmetric). The key is read from Supabase Vault
--    secret "aq_pii_key" when Vault exists; otherwise from aq_private.local_secrets, which is
--    meant for local/test databases only. A keyed HMAC-SHA256 "blind index" keeps contact
--    numbers unique without storing them in plain text.
-- 2. Tamper-evident audit log: every audit row is chained with SHA-256 to the previous row and
--    the table is append-only (UPDATE/DELETE/TRUNCATE are refused). Personal contact fields are
--    never written into the audit log, so privacy erasure no longer needs to edit audit rows.
-- 3. Stored request payloads keep no contact/address, and stored results keep them encrypted.
begin;
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;

-- ---------------------------------------------------------------- keys and crypto helpers
create table if not exists aq_private.local_secrets(name text primary key, value text not null);
alter table aq_private.local_secrets enable row level security;
revoke all on aq_private.local_secrets from public,anon,authenticated;
-- Local/test databases (no Vault) get generated keys. Where Vault exists (Supabase) nothing is
-- seeded here: create the Vault secrets "aq_pii_key" and "aq_pii_index_key" first, otherwise this
-- migration stops with "Encryption key ... is not configured in Vault." (fail closed).
do $$ begin
 if to_regclass('vault.decrypted_secrets') is null then
  insert into aq_private.local_secrets(name,value) values
   ('aq_pii_key',encode(extensions.gen_random_bytes(32),'hex')),
   ('aq_pii_index_key',encode(extensions.gen_random_bytes(32),'hex')) on conflict do nothing;
 end if;
end $$;

create function aq_private.pii_key(p_name text default 'aq_pii_key') returns text language plpgsql stable security definer set search_path='' as $$
declare k text;
begin
 if p_name not in ('aq_pii_key','aq_pii_index_key') then raise exception 'Unknown key.'; end if;
 if to_regclass('vault.decrypted_secrets') is not null then
  execute 'select decrypted_secret from vault.decrypted_secrets where name=$1 limit 1' into k using p_name;
  if k is null or length(k)<32 then raise exception 'Encryption key % is not configured in Vault.',p_name; end if;
  return k;
 end if;
 select value into k from aq_private.local_secrets where name=p_name;
 if k is null or length(k)<32 then raise exception 'Encryption key % is not configured.',p_name; end if;
 return k;
end $$;
create function aq_private.enc(p text) returns text language sql volatile security definer set search_path='' as $$
 select case when p is null then null when p='' then '' when p like 'enc:v1:%' then p
  else 'enc:v1:'||translate(encode(extensions.pgp_sym_encrypt(p,aq_private.pii_key(),'cipher-algo=aes256, compress-algo=0'),'base64'),E'\n','') end
$$;
create function aq_private.dec(p text) returns text language sql stable security definer set search_path='' as $$
 select case when p is null then null when p not like 'enc:v1:%' then p
  else extensions.pgp_sym_decrypt(decode(substr(p,8),'base64'),aq_private.pii_key()) end
$$;
create function aq_private.blind(p text) returns bytea language sql stable security definer set search_path='' as $$
 select case when p is null or p='' then null else extensions.hmac(convert_to(p,'UTF8'),convert_to(aq_private.pii_key('aq_pii_index_key'),'UTF8'),'sha256') end
$$;
-- Keyed digest of a whole request, used only to detect a reused request_id with different data.
-- Keyed so that a database dump cannot be used to guess the contact number inside a request.
create function aq_private.request_digest(p jsonb) returns bytea language sql stable security definer set search_path='' as $$
 select extensions.hmac(sha256(convert_to(p::text,'UTF8')),convert_to(aq_private.pii_key('aq_pii_index_key'),'UTF8'),'sha256')
$$;
create function aq_private.strip_pii(p jsonb) returns jsonb language sql immutable set search_path='' as $$
 select case when p is null then null when jsonb_typeof(p)='object' then p-array['contact_number','address','contact_index','temporary_password','password'] else p end
$$;
create function aq_private.seal_result(p jsonb) returns jsonb language sql volatile security definer set search_path='' as $$
 select case when p is null or jsonb_typeof(p)<>'object' or not (p ? 'contact_number' or p ? 'address') then p
  else (p-array['contact_number','address'])||jsonb_build_object('pii_enc',aq_private.enc(jsonb_build_object('contact_number',p->'contact_number','address',p->'address')::text)) end
$$;
create function aq_private.open_result(p jsonb) returns jsonb language sql stable security definer set search_path='' as $$
 select case when p is null or jsonb_typeof(p)<>'object' or not (p ? 'pii_enc') then p
  else (p-'pii_enc')||aq_private.dec(p->>'pii_enc')::jsonb end
$$;

-- ---------------------------------------------------------------- encrypt existing data
do $$ declare r record; begin
 for r in select conname from pg_constraint where conrelid='aq_private.customers'::regclass and contype in ('c','u')
   and (pg_get_constraintdef(oid) like '%contact_number%' or pg_get_constraintdef(oid) like '%address%') loop
  execute format('alter table aq_private.customers drop constraint %I',r.conname);
 end loop;
end $$;
alter table aq_private.customers add column contact_index bytea;
update aq_private.customers set contact_index=aq_private.blind(contact_number),contact_number=aq_private.enc(contact_number),address=aq_private.enc(address);
alter table aq_private.customers add constraint customers_contact_index_key unique(contact_index);
alter table aq_private.customers add constraint customers_contact_sealed_check check(contact_number is null or contact_number like 'enc:v1:%');
alter table aq_private.customers add constraint customers_address_sealed_check check(address is null or (address like 'enc:v1:%' and length(address)<=4000));
update aq_private.orders set contact_number=aq_private.enc(contact_number),address=aq_private.enc(address);
alter table aq_private.orders add constraint orders_pii_sealed_check check((contact_number is null or contact_number='' or contact_number like 'enc:v1:%') and (address is null or address='' or address like 'enc:v1:%'));
-- Re-key stored request digests (old value = sha256 of the request text) and drop PII.
update aq_private.requests set payload=aq_private.strip_pii(payload),result=aq_private.seal_result(result),
 payload_hash=extensions.hmac(payload_hash,convert_to(aq_private.pii_key('aq_pii_index_key'),'UTF8'),'sha256');

-- Readers return decrypted values to authorised callers only.
create or replace function aq_private.customer_json(p_id uuid) returns jsonb language sql stable set search_path='' as $$
 select (to_jsonb(c)-'contact_index')||jsonb_build_object('contact_number',aq_private.dec(c.contact_number),'address',aq_private.dec(c.address),'balance_centavos',
 coalesce((select sum(o.quantity::bigint*o.unit_price_centavos-aq_private.paid(o.id,null)) from aq_private.orders o where o.customer_id=c.id),0)+
 coalesce((select sum(s.quantity::bigint*s.unit_price_centavos-aq_private.paid(null,s.id)) from aq_private.sales s where s.customer_id=c.id),0),
 'previous_order_quantity',(select o.quantity from aq_private.orders o where o.customer_id=c.id order by o.created_at desc limit 1))
 from aq_private.customers c where c.id=p_id
$$;
create or replace function aq_private.order_json(p_id uuid) returns jsonb language sql stable set search_path='' as $$
 select to_jsonb(o)||jsonb_build_object('contact_number',aq_private.dec(o.contact_number),'address',aq_private.dec(o.address),'shortfall',o.expected_quantity-o.collected_quantity,
 'pending_quantity',case when o.collected_quantity>0 then o.quantity-o.delivered_quantity else 0 end,
 'total_centavos',o.quantity::bigint*o.unit_price_centavos,'paid_centavos',p.paid,
 'balance_centavos',o.quantity::bigint*o.unit_price_centavos-p.paid,
 'payment_status',aq_private.payment_status(o.quantity::bigint*o.unit_price_centavos,p.paid),
 'reserved_quantity',coalesce(r.quantity,0),'recorded_by_name',a.full_name)
 from aq_private.orders o cross join lateral (select aq_private.paid(o.id,null) as paid) p
 left join aq_private.reservations r on r.order_id=o.id left join aq_private.profiles a on a.user_id=o.recorded_by where o.id=p_id
$$;

-- ---------------------------------------------------------------- tamper-evident audit log
alter table aq_private.audit_events add column chain_seq bigint unique, add column content_digest bytea, add column prev_hash bytea, add column row_hash bytea;
create function aq_private.audit_row_hash(p_prev bytea,p_seq bigint,p_actor uuid,p_action text,p_entity uuid,p_request uuid,p_created timestamptz,p_digest bytea) returns bytea language sql immutable set search_path='' as $$
 select sha256(p_prev||convert_to(jsonb_build_array(p_seq,p_actor,p_action,p_entity,p_request,to_char(p_created at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US'),encode(p_digest,'hex'))::text,'UTF8'))
$$;
create function aq_private.audit_content_digest(p_before jsonb,p_after jsonb,p_reason text) returns bytea language sql immutable set search_path='' as $$
 select sha256(convert_to(jsonb_build_array(p_before,p_after,p_reason)::text,'UTF8'))
$$;
-- Remove personal contact fields from historical audit rows, then build the chain in id order.
update aq_private.audit_events set before_state=aq_private.strip_pii(before_state),after_state=aq_private.strip_pii(after_state);
do $$ declare r record; prev bytea:=decode(repeat('00',32),'hex'); n bigint:=0; d bytea; h bytea; begin
 for r in select * from aq_private.audit_events order by id loop
  n:=n+1; d:=aq_private.audit_content_digest(r.before_state,r.after_state,r.reason);
  h:=aq_private.audit_row_hash(prev,n,r.actor_id,r.action,r.entity_id,r.request_id,r.created_at,d);
  update aq_private.audit_events set chain_seq=n,content_digest=d,prev_hash=prev,row_hash=h where id=r.id;
  prev:=h;
 end loop;
end $$;
alter table aq_private.audit_events alter column chain_seq set not null, alter column content_digest set not null, alter column prev_hash set not null, alter column row_hash set not null;

create function aq_private.audit_chain_insert() returns trigger language plpgsql security definer set search_path='' as $$
declare last aq_private.audit_events;
begin
 perform pg_advisory_xact_lock(71420261122);
 new.before_state:=aq_private.strip_pii(new.before_state); new.after_state:=aq_private.strip_pii(new.after_state);
 select * into last from aq_private.audit_events order by chain_seq desc limit 1;
 new.chain_seq:=coalesce(last.chain_seq,0)+1;
 new.prev_hash:=coalesce(last.row_hash,decode(repeat('00',32),'hex'));
 new.content_digest:=aq_private.audit_content_digest(new.before_state,new.after_state,new.reason);
 new.row_hash:=aq_private.audit_row_hash(new.prev_hash,new.chain_seq,new.actor_id,new.action,new.entity_id,new.request_id,new.created_at,new.content_digest);
 return new;
end $$;
create function aq_private.audit_append_only() returns trigger language plpgsql set search_path='' as $$
begin raise exception 'The audit log is append-only.'; end $$;
create trigger audit_events_chain before insert on aq_private.audit_events for each row execute function aq_private.audit_chain_insert();
create trigger audit_events_no_update before update or delete on aq_private.audit_events for each row execute function aq_private.audit_append_only();
create trigger audit_events_no_truncate before truncate on aq_private.audit_events for each statement execute function aq_private.audit_append_only();

create function aq_private.verify_audit_chain() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare r record; prev bytea:=decode(repeat('00',32),'hex'); n bigint:=0;
begin
 for r in select * from aq_private.audit_events order by chain_seq loop
  n:=n+1;
  if r.chain_seq<>n or r.prev_hash<>prev
   or r.content_digest<>aq_private.audit_content_digest(r.before_state,r.after_state,r.reason)
   or r.row_hash<>aq_private.audit_row_hash(prev,r.chain_seq,r.actor_id,r.action,r.entity_id,r.request_id,r.created_at,r.content_digest) then
   return jsonb_build_object('ok',false,'rows_checked',n,'first_broken_seq',r.chain_seq,'checked_at',clock_timestamp());
  end if;
  prev:=r.row_hash;
 end loop;
 return jsonb_build_object('ok',true,'rows_checked',n,'latest_hash',encode(prev,'hex'),'checked_at',clock_timestamp());
end $$;

-- Privacy erasure: audit rows hold no contact fields any more, so only live records and
-- stored retry payloads/results are cleared. The decision itself is appended to the audit log.
create or replace function aq_private.erase_eligible_contact(p_customer_id uuid,p_decision_id uuid,p_owner_approval_ref text,p_reason text,p_execute boolean default false,p_restore_cutoff timestamptz default null)
returns jsonb language plpgsql security invoker set search_path='' as $f$
declare payload jsonb; prior aq_private.privacy_decisions; cutoff timestamptz:=coalesce(p_restore_cutoff,clock_timestamp()); result jsonb; customer_rows integer; order_rows integer;
begin
 if p_execute is null or p_customer_id is null or p_decision_id is null or coalesce(length(btrim(p_owner_approval_ref)),0) not between 1 and 160 or coalesce(length(btrim(p_reason)),0) not between 1 and 500 or cutoff>clock_timestamp() then raise exception 'Reviewed Owner approval, reason, decision and valid cutoff required.';end if;
 payload:=jsonb_build_object('customer_id',p_customer_id,'owner_approval_ref',p_owner_approval_ref,'reason',p_reason,'restore_cutoff',p_restore_cutoff);
 perform pg_advisory_xact_lock(71420261103);
 select * into prior from aq_private.privacy_decisions where decision_id=p_decision_id;
 if found then
  if prior.payload<>payload then raise exception 'Decision ID conflicts with retained decision.';end if;
  return prior.result;
 end if;
 perform 1 from aq_private.customers where id=p_customer_id for update;
 if not found then raise exception 'Customer not found.';end if;
 if exists(select 1 from aq_private.orders o where o.customer_id=p_customer_id and (o.status not in ('delivered','claimed') or o.quantity::bigint*o.unit_price_centavos<>aq_private.paid(o.id,null)))
 or exists(select 1 from aq_private.sales s where s.customer_id=p_customer_id and s.quantity::bigint*s.unit_price_centavos<>aq_private.paid(null,s.id)) then raise exception 'Active operations or unsettled financial records require controller review before contact removal.';end if;
 select count(*) into customer_rows from aq_private.customers where id=p_customer_id and updated_at<=cutoff and (contact_number is not null or address is not null);
 select count(*) into order_rows from aq_private.orders where customer_id=p_customer_id and created_at<=cutoff and (contact_number is not null or address is not null);
 result:=jsonb_build_object('mode',case when p_execute then 'EXECUTED' else 'DRY RUN' end,'customer_id',p_customer_id,'decision_id',p_decision_id,'effective_cutoff',cutoff,'customer_contact_rows',customer_rows,'order_contact_snapshots',order_rows,'financial_history','RETAINED','customer_name','RETAINED under controller retention decision');
 if not p_execute then return result;end if;
 update aq_private.customers set contact_number=null,contact_index=null,address=null,updated_at=clock_timestamp() where id=p_customer_id and updated_at<=cutoff;
 update aq_private.orders set contact_number=null,address=null where customer_id=p_customer_id and created_at<=cutoff;
 update aq_private.requests as req set payload=aq_private.strip_pii(req.payload),result=aq_private.strip_pii(req.result)-'pii_enc',payload_hash=extensions.gen_random_bytes(32)
 where req.created_at<=cutoff and (req.result->>'id'=p_customer_id::text or req.result->>'id' in(select id::text from aq_private.orders where customer_id=p_customer_id and created_at<=cutoff));
 insert into aq_private.privacy_decisions(decision_id,customer_id,payload,result,administrator,effective_cutoff) values(p_decision_id,p_customer_id,payload,result,session_user,cutoff);
 insert into aq_private.audit_events(action,entity_id,request_id,after_state,reason) values('approved_contact_removal',p_customer_id,p_decision_id,result,p_reason);
 return result;
end $f$;

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
 insert into aq_private.requests(actor_id,request_id,payload,payload_hash) values(a,rid,aq_private.strip_pii(p_request),aq_private.request_digest(p_request)) on conflict do nothing;
 select * into previous from aq_private.requests where actor_id=a and request_id=rid for update;
 if previous.payload_hash<>aq_private.request_digest(p_request) then raise exception 'Request key was already used with different data.'; end if;
 if previous.result is not null then return aq_private.open_result(previous.result); end if;
 -- A small station benefits from a deterministic global write lock; explicit rows below
 -- also protect invariants. This serializes application mutations, never external reads.
 perform pg_advisory_xact_lock(71420261103);
 if op in ('create_customer','update_customer') then
  perform aq_private.require_keys(p_request,array['operation','request_id','id','name','customer_type','contact_number','address']);
  if coalesce(length(btrim(p_request->>'name')),0)=0 or coalesce(length(btrim(p_request->>'address')),0)=0 or coalesce(length(btrim(p_request->>'contact_number')),0)=0 then raise exception 'Complete the required fields.'; end if;
  if coalesce(p_request->>'customer_type','') not in ('household','dealer','walk_in') then raise exception 'Choose Household, Dealer, or Walk-in.'; end if;
  if replace(p_request->>'contact_number',' ','') !~ '^[0-9]{11}$' then raise exception 'Please enter a valid contact number. Use 11 digits.'; end if;
  id:=case when op='update_customer' then (p_request->>'id')::uuid else gen_random_uuid() end;
  if exists(select 1 from aq_private.customers where contact_index=aq_private.blind(replace(p_request->>'contact_number',' ','')) and customers.id<>id) then raise exception 'Customer already exists.'; end if;
  if op='update_customer' then
   select to_jsonb(x) into before_state from aq_private.customers x where x.id=id for update;
   if not found then raise exception 'Customer not found.'; end if;
   update aq_private.customers set name=btrim(p_request->>'name'),customer_type=p_request->>'customer_type',contact_number=aq_private.enc(replace(p_request->>'contact_number',' ','')),contact_index=aq_private.blind(replace(p_request->>'contact_number',' ','')),address=aq_private.enc(btrim(p_request->>'address')),updated_at=clock_timestamp() where customers.id=id;
  else
   insert into aq_private.customers(id,name,customer_type,contact_number,contact_index,address,recorded_by) values(id,btrim(p_request->>'name'),p_request->>'customer_type',aq_private.enc(replace(p_request->>'contact_number',' ','')),aq_private.blind(replace(p_request->>'contact_number',' ','')),aq_private.enc(btrim(p_request->>'address')),a);
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
  values(aq_private.new_reference('WI'),c.id,'walk_in',btrim(p_request->>'customer_name'),aq_private.enc(btrim(coalesce(p_request->>'address',''))),aq_private.enc(replace(coalesce(p_request->>'contact_number',''),' ','')),aq_private.today(),q,q,q,3500,'received_at_counter','counter',
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
 update aq_private.requests set result=aq_private.seal_result(aq_command.result) where actor_id=a and request_id=rid;
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
  select coalesce(jsonb_agg(aq_private.customer_json(c.id) order by c.name),'[]') into items from aq_private.customers c where c.name ilike '%'||search||'%' or aq_private.dec(c.contact_number) like '%'||replace(search,' ','')||'%';
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
 elsif resource='request_status' then
  -- Whether this account's request_id reached the server (used before giving up on an offline save).
  if nullif(p_request->>'id','') is null then raise exception 'Invalid request.'; end if;
  return (select case when r.request_id is null then jsonb_build_object('known',false) else jsonb_build_object('known',true,'completed',r.result is not null,'result',aq_private.open_result(r.result)) end
   from (select 1) one left join aq_private.requests r on r.actor_id=a and r.request_id=(p_request->>'id')::uuid);
 elsif resource='audit_integrity' then
  perform aq_private.require_actor(true);
  return aq_private.verify_audit_chain();
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
