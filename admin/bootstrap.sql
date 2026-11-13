-- Controlled administrator procedure only, never an app RPC.
-- psql -v auth_id=... -v username=... -v full_name=... -v contact=... -v empty_count=... -v filled_count=... -v approval=... -f admin/bootstrap.sql
-- Auth identity must already be provisioned by managed Auth. Values are psql SQL literals.
\set ON_ERROR_STOP on
begin;
select pg_advisory_xact_lock(71420261103);
create temp table bootstrap_input as select :'auth_id'::uuid id,:'username'::text username,:'full_name'::text full_name,:'contact'::text contact,:'empty_count'::bigint empty_count,:'filled_count'::bigint filled_count,:'approval'::text approval;
do $$declare x record;begin
 select * into x from bootstrap_input;
 if length(btrim(x.approval))=0 or x.empty_count<0 or x.filled_count<0 then raise exception 'Owner approval and nonnegative opening counts required';end if;
 if not exists(select 1 from auth.users where id=x.id) then raise exception 'Provision managed Owner identity first';end if;
 if exists(select 1 from aq_private.inventory where initialized) or exists(select 1 from aq_private.orders) or exists(select 1 from aq_private.sales) then raise exception 'Opening stock already initialized or operations exist';end if;
 insert into aq_private.profiles(user_id,username,full_name,contact_number,role) values(x.id,x.username,x.full_name,x.contact,'owner');
 update aq_private.inventory set empty_quantity=x.empty_count,physical_filled=x.filled_count,initialized=true,updated_at=clock_timestamp();
 insert into aq_private.audit_events(actor_id,action,reason,after_state) values(x.id,'controlled_initial_setup',x.approval,aq_private.stock_json());
end $$;
commit;
