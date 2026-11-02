# Controlled exceptional corrections and privacy

No application role can void, reverse, delete or edit completed financial history. Short pickup before refilling is the specified exception with prior-payment protection. Keep ordinary Owner/Staff app credentials away from administrative SQL access.

For any exceptional correction, obtain documented Owner approval naming the designated administrator, affected reference, reason and intended compensating amounts/stock/reservations. Preserve originals and the approval separately. Begin with a read-only dry run: export the selected order/sale, all payments/deliveries/reservations and protected audit rows; calculate original charge, retained receipts, balance and inventory effects. Lock affected records for any approved execution, record linked compensating entries in a reviewed migration, and verify reconciliation in an isolated database first. Never directly overwrite amounts or discard receipts to hide an error. An approved business-specific correction specification is required before execution; there is no general-purpose arbitrary-balance tool.

Dry-run queries (administrator only; use parameter binding for invoice IDs):

```
select aq_private.order_json(:'order_id'::uuid);
select * from aq_private.payments where order_id=:'order_id'::uuid order by received_at;
select * from aq_private.deliveries where order_id=:'order_id'::uuid order by created_at;
select * from aq_private.reservations where order_id=:'order_id'::uuid;
select * from aq_private.audit_events where entity_id=:'order_id'::uuid order by created_at;
```

The controller verifies rights-request identity with minimum necessary evidence. Document the purpose/legal basis and retention assessment with the controller/accountant; financial retention and disputes can prevent erasing history. No customer portal, deactivation menu or erase-everything UI is added. Remove eligible unnecessary contact/address fields only through a reviewed administrative migration that preserves required financial history and notes the affected historical snapshots. Production retention periods require controller adoption; academic proposals are not operational approval.

Retain a restricted erasure ledger separately from restore sets, naming a minimal subject reference, approved fields, approval, reason and time. Reapply it to restored rows and historical contact snapshots before reopening access. Dispose of unnecessary exports under the custody policy; seven-day backup rotation is distinct from statutory accounting retention. Do not promise cryptographic/physical erasure merely because a file or live row was deleted.

Incident handling: restrict exposed routes/credentials, preserve minimized evidence, assess scope and notify the controller/DPO promptly. The DPO evaluates actual applicable NPC notification deadlines and submissions; archived laboratory incident scenarios are not real incident evidence. Do not expose personal data in logs, screenshots, source history or defense demonstrations.
