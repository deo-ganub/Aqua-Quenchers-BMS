-- White-box domain tests: corrupt only an isolated fixture's reservation to test denial.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
update aq_private.inventory set physical_filled=10,initialized=true;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()))::text,true);
do $$declare c jsonb;o jsonb;first_id uuid;second_id uuid;r jsonb;denied boolean;before_stock bigint;begin
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'name','Synthetic reservation','customer_type','household','contact_number','09000000003','address','Synthetic'));
 for i in 1..2 loop
  o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',case when i=1 then 4 else 3 end));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
  perform public.aq_command(jsonb_build_object('operation','record_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','quantity',case when i=1 then 4 else 3 end));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','refilling'));
  perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','ready_for_delivery'));
  if i=1 then first_id:=(o->>'id')::uuid;else second_id:=(o->>'id')::uuid;end if;
 end loop;
 select physical_filled into before_stock from aq_private.inventory;
 update aq_private.reservations set quantity=0 where order_id=first_id;
 denied:=false;
 begin perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',first_id,'quantity',1));exception when others then
  if sqlerrm='Not enough reserved gallons for this order.' then denied:=true;else raise;end if;
 end;
 if not denied then raise exception 'Delivery borrowed unreserved or another order stock';end if;
 if (select quantity from aq_private.reservations where order_id=second_id)<>3 or (select physical_filled from aq_private.inventory)<>before_stock then raise exception 'Rejected delivery changed another reservation or physical stock';end if;
 update aq_private.reservations set quantity=4 where order_id=first_id;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','record_delivery','request_id',gen_random_uuid(),'order_id',first_id,'quantity',5));exception when others then denied:=true;end;
 if not denied then raise exception 'Overdelivery allowed';end if;
 raise notice 'PASS: missing order reservation rejects despite ample physical/unreserved/other-order stock; failed save preserves stock and other reservation; overdelivery denied.';
end $$;
rollback;
