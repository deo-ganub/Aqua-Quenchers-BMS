"""Automated isolated restore drill for a fresh database dump.

Restores the business schemas of a pg_dump custom-format file into a NEW scratch database on a
separate (non-production) PostgreSQL server, runs integrity checks and drops the scratch database.
It never connects to the source database. The result is evidence for the administrator; it does
not by itself mark a backup copy as verified in the catalog (see RUNBOOK.md).

Usage:
  python3 restore_drill.py --dump database.dump --admin-dsn "service=aq_drill" [--out drill.json]
"""
import argparse, datetime, json, subprocess, sys, uuid

PREP = """
create schema if not exists extensions;
create extension if not exists pgcrypto with schema extensions;
create schema if not exists auth;
create schema if not exists aq_private;
create table if not exists auth.users(id uuid primary key, email text);
create or replace function auth.uid() returns uuid language sql stable as $$ select null::uuid $$;
create or replace function auth.jwt() returns jsonb language sql stable as $$ select '{}'::jsonb $$;
do $$ begin
 if not exists(select 1 from pg_roles where rolname='anon') then create role anon nologin; end if;
 if not exists(select 1 from pg_roles where rolname='authenticated') then create role authenticated nologin; end if;
 if not exists(select 1 from pg_roles where rolname='service_role') then create role service_role nologin; end if;
end $$;
"""

CHECKS = """
select jsonb_build_object(
 'audit_chain', aq_private.verify_audit_chain(),
 'rows', jsonb_build_object(
   'customers',(select count(*) from aq_private.customers),
   'orders',(select count(*) from aq_private.orders),
   'payments',(select count(*) from aq_private.payments),
   'expenses',(select count(*) from aq_private.expenses),
   'audit_events',(select count(*) from aq_private.audit_events)),
 'unsealed_customer_contacts',(select count(*) from aq_private.customers where contact_number is not null and contact_number not like 'enc:v1:%'),
 'overpaid_orders',(select count(*) from aq_private.orders o where (select coalesce(sum(p.amount_centavos),0) from aq_private.payments p where p.order_id=o.id) > o.quantity::bigint*o.unit_price_centavos),
 'orphan_payments',(select count(*) from aq_private.payments p where (p.order_id is null)=(p.sale_id is null))
)::text;
"""


def run(cmd, **kw):
    r = subprocess.run(cmd, text=True, capture_output=True, **kw)
    if r.returncode != 0:
        raise SystemExit(f"Restore drill step failed: {cmd[0]}: {r.stderr.strip()[:2000]}")
    return r


def dsn_for(admin_dsn, database):
    return f"{admin_dsn} dbname={database}"


def drill(dump, admin_dsn):
    started = datetime.datetime.now(datetime.timezone.utc)
    run(["pg_restore", "--list", dump])  # structurally valid archive
    name = "aq_drill_" + uuid.uuid4().hex[:12]
    run(["psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", dsn_for(admin_dsn, "postgres"), "-c", f"create database {name} encoding 'UTF8' template template0"])
    try:
        target = dsn_for(admin_dsn, name)
        run(["psql", "-X", "-q", "-v", "ON_ERROR_STOP=1", "-d", target], input=PREP)
        # Tables, functions and data only: constraints, triggers and grants (post-data) are not
        # needed to check the contents, and the scratch server has no Supabase Auth schema.
        run(["pg_restore", "--no-owner", "--no-privileges", "--exit-on-error", "--section=pre-data", "--section=data",
             "--schema=aq_private", "--schema=public", "--dbname", target, dump])
        out = run(["psql", "-X", "-At", "-v", "ON_ERROR_STOP=1", "-d", target], input=CHECKS).stdout.strip()
        checks = json.loads(out.splitlines()[-1])
    finally:
        run(["psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", dsn_for(admin_dsn, "postgres"), "-c", f"drop database if exists {name}"])
    ok = (checks["audit_chain"].get("ok") is True and checks["unsealed_customer_contacts"] == 0
          and checks["overpaid_orders"] == 0 and checks["orphan_payments"] == 0)
    finished = datetime.datetime.now(datetime.timezone.utc)
    return {"status": "PASSED" if ok else "FAILED", "started_at": started.isoformat(), "finished_at": finished.isoformat(),
            "duration_seconds": round((finished - started).total_seconds(), 1), "scratch_database_dropped": True,
            "latest_audit_hash": checks["audit_chain"].get("latest_hash"), "checks": checks,
            "note": "Automated restore drill evidence. The administrator still records the isolated_restore decision."}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump", required=True)
    ap.add_argument("--admin-dsn", required=True, help="libpq connection for the scratch (non-production) server, without dbname")
    ap.add_argument("--out")
    a = ap.parse_args()
    result = drill(a.dump, a.admin_dsn)
    text = json.dumps(result, indent=2)
    if a.out:
        with open(a.out, "x") as f:
            f.write(text)
    print(text)
    sys.exit(0 if result["status"] == "PASSED" else 2)


if __name__ == "__main__":
    main()
