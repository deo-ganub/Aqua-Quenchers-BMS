-- IAS hardening: encrypted contact data, blind-index uniqueness, sealed retry results,
-- PII-free append-only SHA-256 audit chain and tamper detection. Rollback fixture.
\set ON_ERROR_STOP on
begin;
insert into auth.users values ('15000000-0000-4000-8000-000000000001','ias-owner@test.invalid'),('15000000-0000-4000-8000-000000000002','ias-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values
 ('15000000-0000-4000-8000-000000000001','ias.owner','IAS Owner','09000000501','owner'),
 ('15000000-0000-4000-8000-000000000002','ias.staff','IAS Staff','09000000502','staff');
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000001',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()),'aal','aal2')::text,true);
create temp table ias_ids(k text primary key, v text);
grant all on ias_ids to authenticated;
set local role authenticated;
do $$declare c jsonb;o jsonb;w jsonb;r jsonb;req jsonb;denied boolean;begin
 req:=jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'id',null,'name','IAS Maria','customer_type','household','contact_number','0917 555 0999','address','24 Mabini St.');
 c:=public.aq_command(req);
 if c->>'contact_number'<>'09175550999' or c->>'address'<>'24 Mabini St.' or c ? 'contact_index' then raise exception 'customer_json must return decrypted values only %',c;end if;
 if public.aq_command(req)<>c then raise exception 'Retry did not return the same decrypted result';end if;
 denied:=false;begin perform public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'id',null,'name','Other','customer_type','dealer','contact_number','09175550999','address','x'));exception when others then denied:=sqlerrm='Customer already exists.';end;
 if not denied then raise exception 'Duplicate contact accepted';end if;
 r:=public.aq_query(jsonb_build_object('resource','customers','search','5550999'));
 if jsonb_array_length(r->'items')<>1 then raise exception 'Search by contact digits failed';end if;
 o:=public.aq_command(jsonb_build_object('operation','create_order','request_id',gen_random_uuid(),'customer_id',c->>'id','quantity',2));
 if o->>'contact_number'<>'09175550999' or o->>'address'<>'24 Mabini St.' then raise exception 'order_json not decrypted';end if;
 w:=public.aq_command(jsonb_build_object('operation','create_counter_order','request_id',gen_random_uuid(),'customer_name','IAS Walk-in','contact_number','09175550888','address','5 Rizal St.','quantity',1,'cash_centavos',3500,'confirm_credit',false));
 if w->>'address'<>'5 Rizal St.' or w->>'fulfillment'<>'deliver' then raise exception 'walk-in decrypt %',w;end if;
 insert into ias_ids values('customer',c->>'id'),('order',o->>'id'),('walkin',w->>'id');
 r:=public.aq_query('{"resource":"audit_integrity"}');
 if (r->>'ok')::boolean is not true or (r->>'rows_checked')::int<3 then raise exception 'Audit chain not valid %',r;end if;
end $$;
reset role;
do $$declare cid uuid:=(select v::uuid from ias_ids where k='customer');oid uuid:=(select v::uuid from ias_ids where k='order');wid uuid:=(select v::uuid from ias_ids where k='walkin');blocked boolean;v jsonb;begin
 -- stored values are ciphertext, never plain text
 if exists(select 1 from aq_private.customers where id=cid and (contact_number not like 'enc:v1:%' or address not like 'enc:v1:%' or contact_number like '%0999%' or contact_index is null)) then raise exception 'Customer PII stored in plain text';end if;
 if exists(select 1 from aq_private.orders where id in (oid,wid) and (contact_number not like 'enc:v1:%' or address not like 'enc:v1:%')) then raise exception 'Order PII stored in plain text';end if;
 if exists(select 1 from aq_private.requests where payload::text like '%0917%' or result::text like '%5550999%' or result::text like '%Mabini%') then raise exception 'Retry store keeps plain PII';end if;
 if exists(select 1 from aq_private.audit_events where before_state::text like '%enc:v1:%' or after_state::text like '%enc:v1:%' or after_state::text like '%0917%' or after_state::text like '%Mabini%' or after_state ? 'contact_number') then raise exception 'Audit log holds contact data';end if;
 -- append-only
 blocked:=false;begin update aq_private.audit_events set reason='x' where chain_seq=1;exception when others then blocked:=sqlerrm='The audit log is append-only.';end;
 if not blocked then raise exception 'Audit update allowed';end if;
 blocked:=false;begin delete from aq_private.audit_events where chain_seq=1;exception when others then blocked:=sqlerrm='The audit log is append-only.';end;
 if not blocked then raise exception 'Audit delete allowed';end if;
 blocked:=false;begin truncate aq_private.audit_events cascade;exception when others then blocked:=sqlerrm='The audit log is append-only.';end;
 if not blocked then raise exception 'Audit truncate allowed';end if;
 -- a superuser who bypasses the triggers is still detected by the hash chain
 alter table aq_private.audit_events disable trigger audit_events_no_update;
 update aq_private.audit_events set after_state=after_state||'{"status":"delivered"}' where chain_seq=(select min(chain_seq)+1 from aq_private.audit_events);
 alter table aq_private.audit_events enable trigger audit_events_no_update;
 v:=aq_private.verify_audit_chain();
 if (v->>'ok')::boolean or (v->>'first_broken_seq')::bigint<>(select min(chain_seq)+1 from aq_private.audit_events) then raise exception 'Tampering not detected %',v;end if;
end $$;
rollback;
begin;
-- Staff cannot read the integrity report; erasure still works with encrypted data.
insert into auth.users values ('15000000-0000-4000-8000-000000000002','ias-staff@test.invalid');
insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values ('15000000-0000-4000-8000-000000000002','ias.staff','IAS Staff','09000000502','staff');
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000002',true);
select set_config('request.jwt.claims',jsonb_build_object('session_id',gen_random_uuid(),'iat',extract(epoch from clock_timestamp()))::text,true);
create temp table ias_c(v text);grant all on ias_c to authenticated;
set local role authenticated;
do $$declare denied boolean:=false;c jsonb;begin
 begin perform public.aq_query('{"resource":"audit_integrity"}');exception when others then denied:=true;end;
 if not denied then raise exception 'Staff read audit integrity';end if;
 c:=public.aq_command(jsonb_build_object('operation','create_customer','request_id',gen_random_uuid(),'id',null,'name','IAS Erase','customer_type','household','contact_number','09175550777','address','9 Luna St.'));
 insert into ias_c values(c->>'id');
end $$;
reset role;
do $$declare cid uuid:=(select v::uuid from ias_c);r jsonb;begin
 r:=aq_private.erase_eligible_contact(cid,gen_random_uuid(),'OWNER-APPROVAL-1','Customer asked to remove contact details',true);
 if r->>'mode'<>'EXECUTED' or exists(select 1 from aq_private.customers where id=cid and (contact_number is not null or address is not null or contact_index is not null)) then raise exception 'Erasure failed %',r;end if;
 if (aq_private.verify_audit_chain()->>'ok')::boolean is not true then raise exception 'Erasure broke the audit chain';end if;
end $$;
rollback;
\echo PASS: IAS hardening - encrypted contact data, blind-index uniqueness and search, sealed retries, PII-free append-only SHA-256 audit chain with tamper detection, staff denied integrity report, erasure keeps the chain valid.
