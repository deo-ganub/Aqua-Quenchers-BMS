\set ON_ERROR_STOP on
begin;
-- Explicit disposable-fixture boundary: a verified physical shortage is injected
-- by the test administrator, not by any BMS/manual-adjustment product endpoint.
-- This transaction always rolls back; it never targets the retained HTTP DB.
insert into auth.users values
 ('11000000-0000-4000-8000-000000000001','empty-owner@test.invalid'),
 ('11000000-0000-4000-8000-000000000002','empty-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values
 ('11000000-0000-4000-8000-000000000001','empty.owner','Fixture Owner','09000000101','owner'),
 ('11000000-0000-4000-8000-000000000002','empty.staff','Fixture Staff','09000000102','staff');
create function pg_temp.stock_boundary_snapshot() returns jsonb language plpgsql as $$
declare result jsonb:='{}'; name text; rows jsonb;
begin
 foreach name in array array['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses','requests','audit_events'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),''[]''::jsonb) from aq_private.%I x',name) into rows;
  result:=result||jsonb_build_object(name,rows);
 end loop;
 return result;
end $$;
do $$
declare actor uuid; i int; c jsonb; o jsonb; request jsonb; first_result jsonb; retry_result jsonb; before_state jsonb; after_state jsonb; rejected boolean;
begin
 for i in 1..2 loop
  actor:=case when i=1 then '11000000-0000-4000-8000-000000000001'::uuid else '11000000-0000-4000-8000-000000000002'::uuid end;
  perform set_config('request.jwt.claim.sub',actor::text,true);
  perform set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
  execute 'set local role authenticated';
  c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','EMPTY BOUNDARY '||i,'customer_type','dealer','contact_number','0900000020'||i,'address','DISPOSABLE FIXTURE'));
  o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',2,'business_date',current_date));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
  perform public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',2));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','refilling'));
  execute 'reset role';
  -- Administrator-only fixture setup models the shortage; no application grant.
  update aq_private.inventory set empty_quantity=1 where singleton;
  before_state:=pg_temp.stock_boundary_snapshot();
  request:=jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','ready_for_delivery');
  execute 'set local role authenticated';
  rejected:=false;
  begin
   perform public.aq_command(request);
  exception when others then
   if sqlerrm <> 'Not enough empty gallons.' then raise; end if;
   rejected:=true;
  end;
  execute 'reset role';
  if not rejected then raise exception 'Insufficient empties accepted for actor %',i; end if;
  after_state:=pg_temp.stock_boundary_snapshot();
  if after_state<>before_state then raise exception 'Rejected refill changed protected records for actor %',i; end if;
  -- Restore only the controlled fixture shortage, then reuse the rejected key.
  update aq_private.inventory set empty_quantity=2 where singleton;
  execute 'set local role authenticated';
  first_result:=public.aq_command(request);
  execute 'reset role';
  before_state:=pg_temp.stock_boundary_snapshot();
  execute 'set local role authenticated';
  retry_result:=public.aq_command(request);
  execute 'reset role';
  if first_result<>retry_result or pg_temp.stock_boundary_snapshot()<>before_state then raise exception 'Ready retry duplicated a conversion/reservation/audit effect'; end if;
  if (select empty_quantity<>0 or physical_filled<>2 from aq_private.inventory where singleton) or (select coalesce(sum(quantity),0)<>2 from aq_private.reservations) then raise exception 'Restored refill stock/reservation incorrect'; end if;
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',2));
  execute 'reset role';
  if (select physical_filled<>0 or empty_quantity<>0 from aq_private.inventory where singleton) or exists(select 1 from aq_private.reservations where quantity<>0) then raise exception 'Fixture final delivery did not settle stock'; end if;
 end loop;
 raise notice 'PASS: Owner/Staff exact insufficient-empty error, full rejected-command rollback, restored same-key retry and exactly-once conversion/reservation/audit; disposable administrator boundary, JWT stubs only.';
end $$;
rollback;
