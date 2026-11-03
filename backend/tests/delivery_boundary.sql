-- Disposable rollback fixture only. JWT stubs verify domain authorization, not signatures.
\set ON_ERROR_STOP on
begin;
insert into auth.users values
 ('13000000-0000-4000-8000-000000000001','delivery-owner@test.invalid'),
 ('13000000-0000-4000-8000-000000000002','delivery-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values
 ('13000000-0000-4000-8000-000000000001','delivery.owner','Delivery Owner','09000000301','owner'),
 ('13000000-0000-4000-8000-000000000002','delivery.staff','Delivery Staff','09000000302','staff');
update aq_private.inventory set initialized=true,physical_filled=20;
create function pg_temp.delivery_snapshot() returns jsonb language plpgsql as $$
declare result jsonb:='{}'; name text; rows jsonb;
begin
 foreach name in array array['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses','requests','audit_events'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),''[]''::jsonb) from aq_private.%I x',name) into rows;
  result:=result||jsonb_build_object(name,rows);
 end loop;
 return result;
end $$;
do $$
declare actor uuid; i int; c jsonb; o jsonb; request jsonb; saved jsonb; replay jsonb; before_state jsonb; rejected boolean; row record; first_delivery timestamptz;
begin
 for i in 1..2 loop
  actor:=case when i=1 then '13000000-0000-4000-8000-000000000001'::uuid else '13000000-0000-4000-8000-000000000002'::uuid end;
  perform set_config('request.jwt.claim.sub',actor::text,true);
  perform set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
  execute 'set local role authenticated';
  c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','DELIVERY BOUNDARY '||i,'customer_type','dealer','contact_number','0900000040'||i,'address','DISPOSABLE FIXTURE'));
  o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',3));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
  perform public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',3));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','refilling'));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','ready_for_delivery'));
  execute 'reset role';
  for row in select * from (values
   ('0'::jsonb,'Quantity must be greater than zero.'),
   ('-1'::jsonb,'Quantity must be greater than zero.'),
   ('1.5'::jsonb,'Enter a whole-number gallon quantity.'),
   ('"abc"'::jsonb,'Enter a whole-number gallon quantity.'),
   ('null'::jsonb,'Enter a whole-number gallon quantity.'),
   ('4'::jsonb,'Delivered quantity cannot exceed the pending quantity.')
  ) x(value,message) loop
   before_state:=pg_temp.delivery_snapshot();rejected:=false;
   execute 'set local role authenticated';
   begin perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',row.value));
   exception when others then if sqlerrm=row.message then rejected:=true;else raise;end if;end;
   execute 'reset role';
   if not rejected or pg_temp.delivery_snapshot() is distinct from before_state then raise exception 'Delivery invalid quantity changed protected state: %',row.value;end if;
  end loop;
  -- Test-admin injection models missing own reservation despite abundant physical stock.
  -- No product adjustment endpoint or production inventory is used.
  update aq_private.reservations set quantity=0 where order_id=(o->>'id')::uuid;
  before_state:=pg_temp.delivery_snapshot();request:=jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',1);rejected:=false;
  execute 'set local role authenticated';
  begin perform public.aq_command(request);exception when others then if sqlerrm='Not enough reserved gallons for this order.' then rejected:=true;else raise;end if;end;
  execute 'reset role';
  if not rejected or pg_temp.delivery_snapshot() is distinct from before_state then raise exception 'Missing reservation changed protected state';end if;
  update aq_private.reservations set quantity=3 where order_id=(o->>'id')::uuid;
  execute 'set local role authenticated';saved:=public.aq_command(request);execute 'reset role';
  if saved->>'status'<>'undelivered' or (saved->>'pending_quantity')::int<>2 or (saved->>'reserved_quantity')::int<>2 or (saved->>'total_centavos')::int<>9000 then raise exception 'Partial delivery did not preserve charge/reservation';end if;
  select created_at into first_delivery from aq_private.deliveries where order_id=(o->>'id')::uuid;
  if first_delivery is null or abs(extract(epoch from clock_timestamp()-first_delivery))>5 or not exists(select 1 from aq_private.deliveries where order_id=(o->>'id')::uuid and recorded_by=actor and quantity=1) then raise exception 'Delivery actual timestamp/actor missing';end if;
  before_state:=pg_temp.delivery_snapshot();execute 'set local role authenticated';replay:=public.aq_command(request);execute 'reset role';
  if replay is distinct from saved or pg_temp.delivery_snapshot() is distinct from before_state then raise exception 'Delivery replay changed protected state';end if;
  execute 'set local role authenticated';
  saved:=public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',2));
  execute 'reset role';
  if saved->>'status'<>'delivered' or (saved->>'pending_quantity')::int<>0 or (saved->>'reserved_quantity')::int<>0 or (saved->>'total_centavos')::int<>9000 then raise exception 'Final delivery reconciliation failed';end if;
 end loop;
 raise notice 'PASS: both roles exact invalid quantities and own-reservation denial/full protected rollback; restored same request succeeds once, replay immutable, partial/final charge-reservation-time-actor preserved.';
end $$;
rollback;
