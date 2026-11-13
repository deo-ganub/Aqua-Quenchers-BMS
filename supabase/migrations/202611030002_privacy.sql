-- Administrator-only eligible contact removal; normal app create/edit validation remains required.
begin;
alter table aq_private.requests add column if not exists payload_hash bytea;
update aq_private.requests set payload_hash=sha256(convert_to(payload::text,'UTF8')) where payload_hash is null;
alter table aq_private.requests alter column payload_hash set not null;
alter table aq_private.customers alter column contact_number drop not null;
alter table aq_private.customers alter column address drop not null;
alter table aq_private.orders alter column contact_number drop not null;
alter table aq_private.orders alter column address drop not null;
create table aq_private.privacy_decisions (
 decision_id uuid primary key, customer_id uuid not null references aq_private.customers,
 payload jsonb not null, result jsonb not null, administrator text not null,
 effective_cutoff timestamptz not null, recorded_at timestamptz not null default clock_timestamp()
);
alter table aq_private.privacy_decisions enable row level security;
revoke all on aq_private.privacy_decisions from public,anon,authenticated,service_role;
create function aq_private.erase_eligible_contact(p_customer_id uuid,p_decision_id uuid,p_owner_approval_ref text,p_reason text,p_execute boolean default false,p_restore_cutoff timestamptz default null)
returns jsonb language plpgsql security invoker set search_path='' as $$
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
 if exists(select 1 from aq_private.orders o where o.customer_id=p_customer_id and (o.status<>'delivered' or o.quantity::bigint*o.unit_price_centavos<>aq_private.paid(o.id,null)))
 or exists(select 1 from aq_private.sales s where s.customer_id=p_customer_id and s.quantity::bigint*s.unit_price_centavos<>aq_private.paid(null,s.id)) then raise exception 'Active operations or unsettled financial records require controller review before contact removal.';end if;
 select count(*) into customer_rows from aq_private.customers where id=p_customer_id and updated_at<=cutoff and (contact_number is not null or address is not null);
 select count(*) into order_rows from aq_private.orders where customer_id=p_customer_id and created_at<=cutoff and (contact_number is not null or address is not null);
 result:=jsonb_build_object('mode',case when p_execute then 'EXECUTED' else 'DRY RUN' end,'customer_id',p_customer_id,'decision_id',p_decision_id,'effective_cutoff',cutoff,'customer_contact_rows',customer_rows,'order_contact_snapshots',order_rows,'financial_history','RETAINED','customer_name','RETAINED under controller retention decision');
 if not p_execute then return result;end if;
 update aq_private.customers set contact_number=null,address=null,updated_at=clock_timestamp() where id=p_customer_id and updated_at<=cutoff;
 update aq_private.orders set contact_number=null,address=null where customer_id=p_customer_id and created_at<=cutoff;
 -- Approved PII removal includes historical audit/retry snapshots; preserve original hashes,
 -- financial fields, references and exactly-once effects. Hashes remain restricted personal data.
 update aq_private.audit_events set before_state=before_state-array['contact_number','address'],after_state=after_state-array['contact_number','address']
 where created_at<=cutoff and (entity_id=p_customer_id or entity_id in(select id from aq_private.orders where customer_id=p_customer_id and created_at<=cutoff));
 update aq_private.requests as req set payload=req.payload-array['contact_number','address'],result=req.result-array['contact_number','address']
 where req.created_at<=cutoff and (req.result->>'id'=p_customer_id::text or req.result->>'id' in(select id::text from aq_private.orders where customer_id=p_customer_id and created_at<=cutoff));
 insert into aq_private.privacy_decisions(decision_id,customer_id,payload,result,administrator,effective_cutoff) values(p_decision_id,p_customer_id,payload,result,session_user,cutoff);
 insert into aq_private.audit_events(action,entity_id,request_id,after_state,reason) values('approved_contact_removal',p_customer_id,p_decision_id,result,p_reason);
 return result;
end $$;
revoke all on function aq_private.erase_eligible_contact(uuid,uuid,text,text,boolean,timestamptz) from public,anon,authenticated,service_role;
commit;
