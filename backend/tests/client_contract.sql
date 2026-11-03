-- Strict DTO protocol used by native customer edit/reschedule/Home; no extra actor fields.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('10000000-0000-4000-8000-000000000001','owner@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('10000000-0000-4000-8000-000000000001','owner.test','Synthetic Owner','09000000001','owner');
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id','20000000-0000-4000-8000-000000000001','iat',extract(epoch from clock_timestamp()))::text,true);
set local role authenticated;
do $$declare p jsonb;c jsonb;r jsonb;o jsonb;denied boolean;invalid jsonb;expected text;begin
 p:=jsonb_build_object('name','Synthetic customer','customer_type','household','contact_number','09000000003','address','Synthetic');
 c:=public.aq_command(p||jsonb_build_object('operation','create_customer','request_id',gen_random_uuid()));
 r:=public.aq_command(p||jsonb_build_object('operation','update_customer','request_id',gen_random_uuid(),'id',c->>'id','name','Edited synthetic'));
 if r->>'id'<>c->>'id' or r->>'name'<>'Edited synthetic' then raise exception 'Native edit ID contract failed';end if;
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('operation','update_customer','request_id',gen_random_uuid(),'customer_id',c->>'id'));exception when others then denied:=true;end;
 if not denied then raise exception 'Obsolete customer_id edit key accepted';end if;
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('operation','create_customer','request_id',gen_random_uuid()));exception when others then if sqlerrm='Customer already exists.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Duplicate contact accepted';end if;
 for invalid in select value from jsonb_array_elements('[0,-1,1.5,"abc"]') loop
  expected:=case when invalid in ('0'::jsonb,'-1'::jsonb) then 'Quantity must be greater than zero.' else 'Enter a whole-number gallon quantity.' end;
  denied:=false;begin perform public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',invalid));exception when others then if sqlerrm=expected then denied:=true;else raise;end if;end;
  if not denied then raise exception 'Invalid order quantity/message accepted: %',invalid;end if;
 end loop;
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',2));
 perform public.aq_command(jsonb_build_object('operation','transition_order','request_id',gen_random_uuid(),'order_id',o->>'id','status','for_pickup'));
 p:=jsonb_build_object('operation','reschedule_pickup','request_id',gen_random_uuid(),'order_id',o->>'id','scheduled_date',(clock_timestamp() at time zone 'Asia/Manila')::date+1,'notes','Synthetic access unavailable');
 r:=public.aq_command(p);
 if r->>'status'<>'rescheduled' or r->>'reschedule_notes'<>'Synthetic access unavailable' then raise exception 'Native reschedule/date/optional notes contract failed';end if;
 if public.aq_command(p) is distinct from r then raise exception 'Notes retry changed saved result';end if;
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'notes',repeat('😀',501)));exception when others then if sqlerrm='Use 500 characters or fewer.' then denied:=true;else raise;end if;end;
 if not denied then raise exception 'Unbounded notes accepted';end if;
 denied:=false;begin perform public.aq_command(p||jsonb_build_object('request_id',gen_random_uuid(),'client_actor_id','unsupported'));exception when others then denied:=true;end;
 if not denied then raise exception 'Unsupported caller actor accepted';end if;
 r:=public.aq_query('{"resource":"home"}');
 if (r->>'orders_count')::int<>1 or (r->>'ready_for_delivery_count')::int<>0 then raise exception 'Native Home count contract failed';end if;
 raise notice 'PASS: stable customer edit id, duplicate contact denial, strict reschedule/date payload and active Home order counts.';
end $$;
rollback;
