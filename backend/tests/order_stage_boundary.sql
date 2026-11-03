\set ON_ERROR_STOP on
begin;
-- Disposable real SQL state-machine checks. JWT claims are explicit test stubs.
insert into auth.users values
 ('12000000-0000-4000-8000-000000000001','stage-owner@test.invalid'),
 ('12000000-0000-4000-8000-000000000002','stage-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values
 ('12000000-0000-4000-8000-000000000001','stage.owner','Stage Owner','09000000301','owner'),
 ('12000000-0000-4000-8000-000000000002','stage.staff','Stage Staff','09000000302','staff');
create function pg_temp.stage_snapshot() returns jsonb language plpgsql as $$
declare result jsonb:='{}'; name text; rows jsonb;
begin
 foreach name in array array['customers','profiles','orders','inventory','reservations','deliveries','sales','payments','expenses','requests','audit_events'] loop
  execute format('select coalesce(jsonb_agg(to_jsonb(x) order by to_jsonb(x)::text),''[]''::jsonb) from aq_private.%I x',name) into rows;
  result:=result||jsonb_build_object(name,rows);
 end loop;
 return result;
end $$;
create function pg_temp.reject_unavailable_stages(order_id uuid) returns void language plpgsql as $$
declare current_stage text; target text; before_state jsonb; denied boolean;
begin
 select o.status into current_stage from aq_private.orders o where o.id=order_id;
 foreach target in array array['ordered','for_pickup','picked_up','refilling','ready_for_delivery','undelivered','delivered','rescheduled','bogus'] loop
  -- Same-state no-op/replays are a separate contract; real transitions only.
  if target=current_stage or (current_stage in ('ordered','rescheduled') and target='for_pickup') or (current_stage='picked_up' and target='refilling') or (current_stage='refilling' and target='ready_for_delivery') then continue; end if;
  before_state:=pg_temp.stage_snapshot();
  execute 'set local role authenticated';
  denied:=false;
  begin
   perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',order_id,'status',target));
  exception when others then
   if sqlerrm<>'Follow the next eligible order stage.' then raise;end if;
   denied:=true;
  end;
  execute 'reset role';
  if not denied or pg_temp.stage_snapshot()<>before_state then raise exception 'Unavailable transition % -> % accepted or changed protected records',current_stage,target;end if;
 end loop;
end $$;
do $$
declare actor uuid;i int;c jsonb;o jsonb;
begin
 for i in 1..2 loop
  actor:=case when i=1 then '12000000-0000-4000-8000-000000000001'::uuid else '12000000-0000-4000-8000-000000000002'::uuid end;
  perform set_config('request.jwt.claim.sub',actor::text,true);
  perform set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
  execute 'set local role authenticated';
  c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','STAGE FIXTURE '||i,'customer_type','household','contact_number','0900000040'||i,'address','DISPOSABLE FIXTURE'));
  o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',2,'business_date',(clock_timestamp() at time zone 'Asia/Manila')::date));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','reschedule_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','scheduled_date',(clock_timestamp() at time zone 'Asia/Manila')::date+1,'notes','DISPOSABLE STAGE CHECK'));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
  perform public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',2));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','refilling'));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','ready_for_delivery'));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',1));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  execute 'set local role authenticated';
  perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',1));
  execute 'reset role';perform pg_temp.reject_unavailable_stages((o->>'id')::uuid);
  if (select status<>'delivered' or quantity<>2 or delivered_quantity<>2 from aq_private.orders where id=(o->>'id')::uuid) or (select physical_filled<>0 or empty_quantity<>0 from aq_private.inventory where singleton) or exists(select 1 from aq_private.reservations where quantity<>0) then raise exception 'Valid continuation failed after rejected stage';end if;
 end loop;
 raise notice 'PASS: Owner+Staff skipped/backward/unsupported direct transitions at every lifecycle stage reject exact message with full rollback; corrected valid path/reschedule/pickup/readiness/partial/final delivery succeeds. JWT stubs only.';
end $$;
rollback;
