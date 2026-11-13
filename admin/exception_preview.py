"""Restricted, read-only financial correction evidence; never executes corrections."""
import argparse
import json
import os
import pathlib
import re
import subprocess
import uuid


def preview(service, kind, target):
    if not re.fullmatch(r'[A-Za-z0-9_.-]{1,100}', service):
        raise ValueError('Use an approved libpq service name.')
    if kind not in ('order', 'sale'):
        raise ValueError('Select order or sale.')
    target = str(uuid.UUID(target))
    table, link = ('orders', 'order_id') if kind == 'order' else ('sales', 'sale_id')
    # Table/column names come only from the fixed enum; UUID uses psql binding.
    query = f"""begin isolation level repeatable read read only;
select jsonb_build_object(
 'kind','{kind}', 'database',current_database(), 'administrator',current_user,
 'snapshot_at',transaction_timestamp(), 'original',to_jsonb(t),
 'charge_centavos',t.quantity::bigint*t.unit_price_centavos,
 'receipts_centavos',coalesce((select sum(amount_centavos) from aq_private.payments where {link}=t.id),0),
 'balance_centavos',t.quantity::bigint*t.unit_price_centavos-coalesce((select sum(amount_centavos) from aq_private.payments where {link}=t.id),0),
 'payments',coalesce((select jsonb_agg(to_jsonb(p) order by p.received_at,p.id) from aq_private.payments p where {link}=t.id),'[]'::jsonb),
 'deliveries',coalesce((select jsonb_agg(to_jsonb(d) order by d.created_at,d.id) from aq_private.deliveries d where d.order_id=t.id and '{kind}'='order'),'[]'::jsonb),
 'reservation',coalesce((select to_jsonb(r) from aq_private.reservations r where r.order_id=t.id and '{kind}'='order'),'null'::jsonb),
 'inventory',(select to_jsonb(i) from aq_private.inventory i),
 'reserved_total',coalesce((select sum(quantity) from aq_private.reservations),0),
 'audit',coalesce((select jsonb_agg(to_jsonb(a) order by a.id) from aq_private.audit_events a where a.entity_id=t.id or a.entity_id in (select id from aq_private.payments where {link}=t.id) or (a.entity_id in (select id from aq_private.deliveries where order_id=t.id) and '{kind}'='order')),'[]'::jsonb))
from aq_private.{table} t where t.id=:'target'::uuid
and current_user not in ('anon','authenticated','service_role');
rollback;
"""
    result = subprocess.run(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-v', 'target='+target],
                            env={**os.environ, 'PGSERVICE': service}, input=query, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError('Read-only preview failed; check administrator service/access. No correction executed.')
    if not result.stdout.strip():
        raise ValueError('Reference unavailable to the designated administrator.')
    report = json.loads(result.stdout)
    report.update(mode='READ_ONLY_DRY_RUN', correction_executed=False,
                  next_step='Owner-approved case specification and isolated reviewed compensating migration required.')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--service', required=True, help='Separate administrator libpq service; no connection URL.')
    parser.add_argument('--kind', choices=['order', 'sale'], required=True)
    parser.add_argument('--id', required=True)
    parser.add_argument('--output', type=pathlib.Path, required=True)
    args = parser.parse_args()
    parent = args.output.parent
    if parent.stat().st_mode & 0o077:
        raise ValueError('Output parent must be restricted (0700).')
    report = preview(args.service, args.kind, args.id)
    fd = os.open(args.output, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    with os.fdopen(fd, 'w') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')
    print('Read-only preview saved to restricted output; no correction executed.')


if __name__ == '__main__':
    main()
