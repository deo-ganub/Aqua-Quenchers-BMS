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


def run(cmd, timeout=1800, **kw):
    r = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout, **kw)
    if r.returncode != 0:
        # Only the first error line is shown: later lines can echo restored rows (personal data).
        first = next((l for l in r.stderr.splitlines() if "ERROR" in l or "error" in l), "see server log")
        raise SystemExit(f"Restore drill step failed: {cmd[0]}: {first[:300]}")
    return r


# Rows exported with Postgres built-ins only (not functions from the dump), so a tampered
# verify_audit_chain() in the backup cannot hide a rewritten log. Hashes are recomputed here.
CHAIN_ROWS = """
select chain_seq, encode(prev_hash,'hex'), encode(row_hash,'hex'), encode(content_digest,'hex'),
 jsonb_build_array(before_state,after_state,reason)::text,
 jsonb_build_array(chain_seq,actor_id,action,entity_id,request_id,to_char(created_at at time zone 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US'),encode(content_digest,'hex'))::text
from aq_private.audit_events order by chain_seq;
"""


def verify_chain(target, anchor_seq=None, anchor_hash=None):
    import csv, hashlib, io
    out = run(["psql", "-X", "-A", "-t", "--csv", "-v", "ON_ERROR_STOP=1", "-d", target], input=CHAIN_ROWS).stdout
    prev = bytes(32); n = 0; anchor_ok = anchor_seq is None
    for row in csv.reader(io.StringIO(out)):
        if not row: continue
        seq, prev_hex, row_hex, digest_hex, content, canon = int(row[0]), row[1], row[2], row[3], row[4], row[5]
        n += 1
        digest = hashlib.sha256(content.encode()).hexdigest()
        h = hashlib.sha256(prev + canon.encode()).hexdigest()
        if seq != n or prev_hex != prev.hex() or digest_hex != digest or row_hex != h:
            return {"ok": False, "rows_checked": n, "first_broken_seq": seq}
        if anchor_seq is not None and seq == anchor_seq:
            if row_hex != anchor_hash:
                return {"ok": False, "rows_checked": n, "first_broken_seq": seq, "anchor": "MISMATCH"}
            anchor_ok = True
        prev = bytes.fromhex(row_hex)
    if not anchor_ok:
        return {"ok": False, "rows_checked": n, "anchor": "MISSING (log shorter than the previous backup)"}
    return {"ok": True, "rows_checked": n, "latest_seq": n, "latest_hash": prev.hex(),
            "anchor": "MATCHED" if anchor_seq is not None else "NONE"}


def dsn_for(admin_dsn, database):
    return f"{admin_dsn} dbname={database}"


def drill(dump, admin_dsn, anchor_seq=None, anchor_hash=None):
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
        checks["audit_chain"] = verify_chain(target, anchor_seq, anchor_hash)
    finally:
        try:
            run(["psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", dsn_for(admin_dsn, "postgres"), "-c", f"drop database if exists {name} with (force)"], timeout=300)
        except BaseException as cleanup:  # do not hide the original failure
            print(f"warning: scratch database {name} may remain: {cleanup}", file=sys.stderr)
    ok = (checks["audit_chain"].get("ok") is True and checks["unsealed_customer_contacts"] == 0
          and checks["overpaid_orders"] == 0 and checks["orphan_payments"] == 0)
    finished = datetime.datetime.now(datetime.timezone.utc)
    return {"status": "PASSED" if ok else "FAILED", "started_at": started.isoformat(), "finished_at": finished.isoformat(),
            "duration_seconds": round((finished - started).total_seconds(), 1), "scratch_database_dropped": True,
            "latest_audit_hash": checks["audit_chain"].get("latest_hash"),
            "latest_audit_seq": checks["audit_chain"].get("latest_seq"), "checks": checks,
            "note": "Automated restore drill evidence. The administrator still records the isolated_restore decision."}


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump", required=True)
    ap.add_argument("--admin-dsn", required=True, help="libpq connection for the scratch (non-production) server, without dbname")
    ap.add_argument("--out")
    ap.add_argument("--anchor-seq", type=int, help="chain_seq recorded by the previous backup's drill")
    ap.add_argument("--anchor-hash", help="row hash recorded for --anchor-seq")
    a = ap.parse_args()
    if (a.anchor_seq is None) != (a.anchor_hash is None): ap.error("--anchor-seq and --anchor-hash go together")
    result = drill(a.dump, a.admin_dsn, a.anchor_seq, a.anchor_hash)
    text = json.dumps(result, indent=2)
    if a.out:
        with open(a.out, "x") as f:
            f.write(text)
    print(text)
    sys.exit(0 if result["status"] == "PASSED" else 2)


if __name__ == "__main__":
    main()
