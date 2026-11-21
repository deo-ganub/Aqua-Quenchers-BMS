-- Step 7 (read-only). Expect: audit chain ok = true, unencrypted_contacts = 0, unencrypted_addresses = 0.
select aq_private.verify_audit_chain() as audit_chain;
select
  (select count(*) from aq_private.customers where contact_number is not null and contact_number<>'' and contact_number not like 'enc:v1:%') as unencrypted_contacts,
  (select count(*) from aq_private.customers where address is not null and address<>'' and address not like 'enc:v1:%')               as unencrypted_addresses,
  (select count(*) from aq_private.orders where channel='counter')                                                                      as walk_in_orders;
