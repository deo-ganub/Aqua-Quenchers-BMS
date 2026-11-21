-- Step 1 (read-only). Paste into Supabase Dashboard -> SQL Editor and Run.
-- Expect: vault_ready = true, both *_already_applied = false.
select
  to_regclass('vault.decrypted_secrets') is not null                                    as vault_ready,
  exists(select 1 from information_schema.columns
         where table_schema='aq_private' and table_name='orders' and column_name='channel') as walk_in_already_applied,
  exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
         where n.nspname='aq_private' and p.proname='pii_key')                         as ias_already_applied,
  exists(select 1 from vault.secrets where name in ('aq_pii_key','aq_pii_index_key'))   as keys_already_exist,
  (select count(*) from aq_private.customers)                                           as customers,
  (select count(*) from aq_private.orders)                                              as orders;
