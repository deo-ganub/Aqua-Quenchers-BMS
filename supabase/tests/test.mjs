import { PGlite } from '@electric-sql/pglite';
import { readFileSync } from 'node:fs';
import { randomUUID as k } from 'node:crypto';

const files = process.argv.slice(2);
const sql = files.map((f) => readFileSync(f, 'utf8')).join('\n');
const db = new PGlite();
let pass = 0, fail = 0;
const ok = (name, cond, extra = '') => { cond ? pass++ : (fail++, console.log('FAIL', name, extra)); };

await db.exec(`
  create role anon nologin; create role authenticated nologin; create role service_role nologin;
  create schema auth;
  create table auth.users (id uuid primary key, email text);
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema auth to anon, authenticated;
  grant usage on schema public to anon, authenticated;
  grant execute on function auth.uid() to anon, authenticated;
  -- the state your project is already in: a minimal profiles table with a row
  create table public.profiles (id uuid primary key, role text not null);
`);
const OWNER = '00000000-0000-0000-0000-000000000001';
const STAFF = '00000000-0000-0000-0000-000000000002';
await db.exec(`insert into auth.users values ('${OWNER}','o@x.y'),('${STAFF}','s@x.y');
  insert into public.profiles values ('${OWNER}','owner');`);

await db.exec(sql);
await db.exec(sql); // re-running must be safe
ok('existing profile kept', (await db.query(`select role from profiles where id='${OWNER}'`)).rows[0].role === 'owner');

await db.exec(`
  update profiles set username='owner.elena', full_name='Elena Mendoza', status='Active' where id='${OWNER}';
  insert into profiles (id, role, username, full_name) values ('${STAFF}','staff','ana.mendoza','Ana Mendoza');
  update inventory set physical_filled=50, reserved_filled=3, empty_gallons=80 where id=1;
`);

// Each account gets an Auth-style session id; tests may swap it or back-date iat.
const sessions = {}; const issued = {};
const sessionOf = (uid) => (sessions[uid] ??= k());
const newSession = (uid, iatSecondsAgo = 0) => { sessions[uid] = k(); issued[uid] = Math.floor(Date.now() / 1000) - iatSecondsAgo; return sessions[uid]; };
async function as(uid, text, params = []) {
  return db.transaction(async (tx) => {
    await tx.query(`select set_config('request.jwt.claim.sub', $1, true)`, [uid || '']);
    const claims = uid ? { sub: uid, session_id: sessionOf(uid), iat: issued[uid] ?? Math.floor(Date.now() / 1000) } : {};
    await tx.query(`select set_config('request.jwt.claims', $1, true)`, [uid ? JSON.stringify(claims) : '']);
    await tx.exec(`set local role ${uid ? 'authenticated' : 'anon'}`);
    return (await tx.query(text, params)).rows;
  });
}
const rpc = async (uid, fn, ...args) => {
  const ph = args.map((_, i) => `$${i + 1}`).join(',');
  const r = await as(uid, `select public.${fn}(${ph}) as r`, args);
  return r[0].r;
};
const err = async (p) => { try { await p; return null; } catch (e) { return e.message; } };
const inv = async () => (await db.query('select * from inventory')).rows[0];

// ----- customers (A-03)
const maria = await rpc(OWNER, 'save_customer', null, 'Maria Santos', 'Household', '0917 555 0125', '24 Mabini St.', k());
ok('contact normalized', maria.contact_number === '09175550125');
const cruz = await rpc(OWNER, 'save_customer', null, 'Cruz Store', 'Dealer', '09195550123', 'Rizal St.', k());
ok('missing fields', (await err(rpc(OWNER, 'save_customer', null, '', 'Dealer', '09195550999', 'x', k()))) === 'Complete the required fields.');
ok('bad contact', (await err(rpc(OWNER, 'save_customer', null, 'A', 'Dealer', '123', 'x', k()))) === 'Please enter a valid contact number. Use 11 digits.');
ok('duplicate contact', (await err(rpc(OWNER, 'save_customer', null, 'A', 'Dealer', '09175550125', 'x', k()))) === 'Customer already exists.');
const edited = await rpc(OWNER, 'save_customer', maria.id, 'Maria S.', 'Household', '09175550125', '24 Mabini St.', k());
ok('edit keeps own number', edited.name === 'Maria S.');

// ----- pricing and create order (A-04)
const key1 = k();
const o1 = await rpc(OWNER, 'create_order', maria.id, 'Household', 10, '2026-10-14', key1);
ok('household price', Number(o1.unit_price) === 35 && Number(o1.total) === 350);
ok('order id format', /^ORD-\d{4}-0001$/.test(o1.order_no), o1.order_no);
ok('status ordered', o1.status === 'Ordered');
const o1again = await rpc(OWNER, 'create_order', maria.id, 'Household', 10, '2026-10-14', key1);
ok('idempotent order', o1again.id === o1.id && (await db.query('select count(*)::int c from orders')).rows[0].c === 1);
const o2 = await rpc(OWNER, 'create_order', cruz.id, 'Dealer', 10, '2026-10-15', k());
ok('dealer price', Number(o2.total) === 300);
ok('invalid qty', (await err(rpc(OWNER, 'create_order', maria.id, 'Household', 0, '2026-10-14', k()))) === 'Enter a valid gallon quantity.');

// ----- no skipped or backward stages (A-04.6)
ok('skip blocked', (await err(rpc(OWNER, 'set_order_status', o1.id, 'Delivered', k()))) === 'Follow the next eligible order stage.');
ok('skip to refilling blocked', (await err(rpc(OWNER, 'set_order_status', o1.id, 'Refilling', k()))) === 'Follow the next eligible order stage.');
ok('pickup before For Pickup blocked', (await err(rpc(OWNER, 'record_pickup', o1.id, 5, k()))) === 'Follow the next eligible order stage.');
await rpc(OWNER, 'set_order_status', o2.id, 'For Pickup', k());
ok('backward blocked', (await err(rpc(OWNER, 'set_order_status', o2.id, 'Ordered', k()))) === 'Follow the next eligible order stage.');

// ----- pickup (A-05), Figma: Dealer 10 expected, 8 collected => 240, empties 80 -> 88
ok('zero pickup', (await err(rpc(OWNER, 'record_pickup', o2.id, 0, k()))) === 'Quantity must be greater than zero.');
ok('over pickup', (await err(rpc(OWNER, 'record_pickup', o2.id, 11, k()))) === 'Collected quantity cannot exceed the expected quantity.');
// paid 270 then a pickup of 8 (240) must be rejected
const payKey = k();
const p270 = await rpc(OWNER, 'record_payment', o2.id, null, 270, payKey);
ok('below paid rejected', (await err(rpc(OWNER, 'record_pickup', o2.id, 8, k()))) === 'Adjusted total cannot be less than the amount already paid.');
ok('rejected pickup left stock', (await inv()).empty_gallons === 80);
// use another order for the valid short pickup
const o3 = await rpc(OWNER, 'create_order', cruz.id, 'Dealer', 10, '2026-10-15', k());
await rpc(OWNER, 'set_order_status', o3.id, 'For Pickup', k());
const pkKey = k();
const pk = await rpc(OWNER, 'record_pickup', o3.id, 8, pkKey);
ok('short pickup values', pk.shortfall === 2 && Number(pk.total) === 240 && pk.empty_before === 80 && pk.empty_after === 88, JSON.stringify(pk));
await rpc(OWNER, 'record_pickup', o3.id, 8, pkKey);
ok('pickup adds stock once', (await inv()).empty_gallons === 88);
const o3row = (await db.query('select * from orders where id=$1', [o3.id])).rows[0];
ok('original request retained', o3row.original_gallons === 10 && o3row.gallons === 8 && Number(o3row.total) === 240);
ok('order date unchanged', new Date(o3row.order_date).toISOString().startsWith('2026-10-15') || String(o3row.order_date).includes('2026'));

// ----- reschedule (A-05.4)
const o4 = await rpc(OWNER, 'create_order', cruz.id, 'Dealer', 2, '2026-10-15', k());
await rpc(OWNER, 'set_order_status', o4.id, 'For Pickup', k());
ok('past date rejected', (await err(rpc(OWNER, 'reschedule_pickup', o4.id, '2020-01-01', 'x', k()))) === 'Select today or a future date.');
const today = (await db.query(`select (now() at time zone 'Asia/Manila')::date::text d`)).rows[0].d;
await rpc(OWNER, 'reschedule_pickup', o4.id, today, 'Customer unavailable', k());
ok('rescheduled', (await db.query('select status from orders where id=$1', [o4.id])).rows[0].status === 'Rescheduled');
await rpc(OWNER, 'set_order_status', o4.id, 'For Pickup', k());
ok('returns to For Pickup', (await db.query('select status from orders where id=$1', [o4.id])).rows[0].status === 'For Pickup');

// ----- ready for delivery: conversion happens once, available unchanged (Figma: 80->70, 47->57, 0->10)
await rpc(OWNER, 'set_order_status', o3.id, 'Refilling', k());
let i0 = await inv();
const rKey = k();
const ready = await rpc(OWNER, 'set_order_status', o3.id, 'Ready for Delivery', rKey);
await rpc(OWNER, 'set_order_status', o3.id, 'Ready for Delivery', rKey); // retry
let i1 = await inv();
ok('converted once', i1.empty_gallons === i0.empty_gallons - 8 && i1.physical_filled === i0.physical_filled + 8 && i1.reserved_filled === i0.reserved_filled + 8, JSON.stringify([i0, i1]));
ok('available unchanged', i1.physical_filled - i1.reserved_filled === i0.physical_filled - i0.reserved_filled);

// insufficient empties
const o5 = await rpc(OWNER, 'create_order', cruz.id, 'Dealer', 1, '2026-10-15', k());
await rpc(OWNER, 'set_order_status', o5.id, 'For Pickup', k());
await rpc(OWNER, 'record_pickup', o5.id, 1, k());
await rpc(OWNER, 'set_order_status', o5.id, 'Refilling', k());
await db.exec('update inventory set empty_gallons=0');
ok('no empties', (await err(rpc(OWNER, 'set_order_status', o5.id, 'Ready for Delivery', k()))) === 'Not enough empty gallons.');
ok('no empties unchanged', (await db.query('select status from orders where id=$1', [o5.id])).rows[0].status === 'Refilling');
await db.exec('update inventory set empty_gallons=80');

// ----- delivery (A-06): partial, reservation kept, once only
ok('zero delivery', (await err(rpc(OWNER, 'record_delivery', o3.id, 0, k()))) === 'Quantity must be greater than zero.');
ok('over pending', (await err(rpc(OWNER, 'record_delivery', o3.id, 9, k()))) === 'Delivered quantity cannot exceed the pending quantity.');
i0 = await inv();
const dKey = k();
const d1 = await rpc(OWNER, 'record_delivery', o3.id, 5, dKey);
await rpc(OWNER, 'record_delivery', o3.id, 5, dKey);
i1 = await inv();
ok('partial status', d1.status === 'Undelivered' && d1.pending === 3);
ok('delivery once', i1.physical_filled === i0.physical_filled - 5 && i1.reserved_filled === i0.reserved_filled - 5);
ok('available stable on delivery', i1.physical_filled - i1.reserved_filled === i0.physical_filled - i0.reserved_filled);
const beforeCharge = Number((await db.query('select total from orders where id=$1', [o3.id])).rows[0].total);
const d2 = await rpc(OWNER, 'record_delivery', o3.id, 3, k());
ok('final delivery', d2.status === 'Delivered' && d2.pending === 0);
ok('delivery leaves charge', Number((await db.query('select total from orders where id=$1', [o3.id])).rows[0].total) === beforeCharge);
const rs = (await db.query('select reserved_gallons from orders where id=$1', [o3.id])).rows[0];
ok('reservation consumed', rs.reserved_gallons === 0);

// reservation shortage cannot use unreserved stock
const o6 = await rpc(OWNER, 'create_order', cruz.id, 'Dealer', 3, '2026-10-15', k());
await rpc(OWNER, 'set_order_status', o6.id, 'For Pickup', k());
await rpc(OWNER, 'record_pickup', o6.id, 3, k());
await rpc(OWNER, 'set_order_status', o6.id, 'Refilling', k());
await rpc(OWNER, 'set_order_status', o6.id, 'Ready for Delivery', k());
await db.query('update orders set reserved_gallons=2 where id=$1', [o6.id]);
ok('reservation shortage', (await err(rpc(OWNER, 'record_delivery', o6.id, 3, k()))) === 'Not enough reserved gallons for this order.');
await db.query('update orders set reserved_gallons=3 where id=$1', [o6.id]);

// ----- walk-in (A-07)
ok('walkin blank name', (await err(rpc(OWNER, 'complete_walkin_sale', ' ', null, 10, 500, false, k()))) === 'Enter the customer name.');
ok('walkin negative cash', (await err(rpc(OWNER, 'complete_walkin_sale', 'Pedro', null, 1, -1, false, k()))) === 'Enter a valid cash amount.');
i0 = await inv();
const avail = i0.physical_filled - i0.reserved_filled;
ok('walkin cannot use reserved', (await err(rpc(OWNER, 'complete_walkin_sale', 'Pedro', null, avail + 1, 99999, false, k()))) === 'Not enough filled gallons.');
ok('credit needs confirmation', (await err(rpc(OWNER, 'complete_walkin_sale', 'Pedro', null, 10, 100, false, k()))) === 'Confirm credit balance?');
const wKey = k();
const w1 = await rpc(OWNER, 'complete_walkin_sale', 'Pedro Garcia', null, 10, 500, false, wKey);
await rpc(OWNER, 'complete_walkin_sale', 'Pedro Garcia', null, 10, 500, false, wKey);
ok('walkin paid + change', w1.payment_status === 'Paid' && Number(w1.change) === 150 && Number(w1.total) === 350);
ok('walkin deducts once', (await inv()).physical_filled === i0.physical_filled - 10);
ok('payment only for tendered total', Number((await db.query(`select amount from payments where source_no=$1`, [w1.sale_no])).rows[0].amount) === 350);
const w2 = await rpc(OWNER, 'complete_walkin_sale', 'Pedro Garcia', null, 10, 100, true, k());
ok('partial credit', w2.payment_status === 'Partially Paid' && Number(w2.credit) === 250);
const w3 = await rpc(OWNER, 'complete_walkin_sale', 'Pedro Garcia', null, 10, 0, true, k());
ok('zero cash unpaid', w3.payment_status === 'Unpaid' && w3.payment_no === null);

// ----- payments (A-08)
const pay = 'Enter an amount greater than zero and not above the balance.';
ok('zero pay', (await err(rpc(OWNER, 'record_payment', o1.id, null, 0, k()))) === pay);
ok('negative pay', (await err(rpc(OWNER, 'record_payment', o1.id, null, -5, k()))) === pay);
ok('over pay', (await err(rpc(OWNER, 'record_payment', o1.id, null, 351, k()))) === pay);
const pKey = k();
const p1 = await rpc(OWNER, 'record_payment', o1.id, null, 200, pKey);
await rpc(OWNER, 'record_payment', o1.id, null, 200, pKey);
ok('partial pay', p1.payment_status === 'Partially Paid' && Number(p1.balance) === 150);
ok('payment once', (await db.query('select count(*)::int c from payments where order_id=$1', [o1.id])).rows[0].c === 1);
const p2 = await rpc(OWNER, 'record_payment', o1.id, null, 150, k());
ok('paid in full', p2.payment_status === 'Paid' && Number(p2.balance) === 0);
const wp = await rpc(OWNER, 'record_payment', null, w3.sale_id ?? (await db.query('select id from walkin_sales where sale_no=$1', [w3.sale_no])).rows[0].id, 100, k());
ok('walkin credit payable by reference', wp.source_no === w3.sale_no && wp.payment_status === 'Partially Paid');
ok('payment dated today', (await db.query(`select payment_date::text d from payments where payment_no=$1`, [p1.payment_no])).rows[0].d === today);

// ----- expenses (A-09)
ok('expense missing cat', (await err(rpc(OWNER, 'save_expense', '2026-10-15', '', 10, null, 'manual', null, k()))) === 'Complete the required fields.');
ok('expense bad amount', (await err(rpc(OWNER, 'save_expense', '2026-10-15', 'Fuel', -20, null, 'manual', null, k()))) === 'Enter a valid expense amount.');
ok('expense remark limit', (await err(rpc(OWNER, 'save_expense', '2026-10-15', 'Fuel', 1, 'x'.repeat(501), 'manual', null, k()))) === 'Use 500 characters or fewer.');
const ex = await rpc(OWNER, 'save_expense', '2026-10-15', 'Fuel', 750, 'gasolina', 'suggestion', 'v1', k());
ok('expense source kept', ex.selection_source === 'suggestion' && ex.model_version === 'v1');
ok('staff cannot save expense', (await err(rpc(STAFF, 'save_expense', '2026-10-15', 'Fuel', 1, null, 'manual', null, k()))) === 'This action is limited to the Owner.');
ok('staff cannot read expenses', (await as(STAFF, 'select * from expenses')).length === 0);
ok('owner reads expenses', (await as(OWNER, 'select * from expenses')).length === 1);

// ----- reports (A-10): recompute independently and compare
ok('bad range', (await err(rpc(OWNER, 'financial_summary', '2026-10-15', '2026-10-01'))) === 'The end date cannot be earlier than the start date.');
ok('staff cannot report', (await err(rpc(STAFF, 'financial_summary', '2026-10-01', '2026-10-15'))) === 'This action is limited to the Owner.');
const octOnly = await rpc(OWNER, 'financial_summary', '2026-10-01', '2026-10-31');
const orderTotals = (await db.query(`select coalesce(sum(gallons*unit_price),0)::numeric t from orders where order_date between '2026-10-01' and '2026-10-31'`)).rows[0].t;
ok('revenue is orders by business date, short pickup reduced', Number(octOnly.revenue) === Number(orderTotals), JSON.stringify(octOnly));
ok('oct has no cash because cash uses actual date', Number(octOnly.payments_received) === 0);
ok('net = payments - expenses', Number(octOnly.net_income) === Number(octOnly.payments_received) - Number(octOnly.expenses));
ok('expense by entered date', Number(octOnly.expenses) === 750);
ok('as-of ignores later cash', Number(octOnly.outstanding_balances) === Number(orderTotals));
const nowS = await rpc(OWNER, 'financial_summary', '2026-01-01', today);
const chk = (await db.query(`select
   (select coalesce(sum(total-paid_amount),0) from orders)+(select coalesce(sum(total-paid_amount),0) from walkin_sales) b`)).rows[0].b;
ok('outstanding equals open balances today', Number(nowS.outstanding_balances) === Number(chk), JSON.stringify([nowS, chk]));

// ----- access control
const denied = async (uid, text) => (await err(as(uid, text))) !== null;
ok('anon no rpc', (await err(rpc(null, 'whoami'))) !== null);
ok('anon no customers', await denied(null, 'select * from customers'));
ok('auth no direct insert', await denied(OWNER, `insert into inventory(id) values (1)`));
ok('auth no direct update stock', await denied(OWNER, `update inventory set physical_filled=999`));
ok('auth no direct order update', await denied(OWNER, `update orders set paid_amount=0`));
ok('auth no delete payments', await denied(OWNER, `delete from payments`));
ok('auth no audit read', await denied(OWNER, `select * from audit_log`));
ok('auth no profile role change', await denied(STAFF, `update profiles set role='owner' where id='${STAFF}'`));
ok('helper hidden: next_reference', await denied(OWNER, `select public.next_reference('ORD')`));
ok('helper hidden: audit', await denied(OWNER, `select public.audit(null,'x','y',null,null)`));
ok('helper hidden: idem', await denied(OWNER, `select public.idem_finish(gen_random_uuid(),gen_random_uuid(),'{}')`));
ok('staff sees only self', (await as(STAFF, 'select id from profiles')).length === 1);
ok('owner sees all profiles', (await as(OWNER, 'select id from profiles')).length === 2);
ok('views work for staff', (await as(STAFF, 'select * from orders_view')).length > 0);
ok('balance view works', (await as(OWNER, 'select * from customer_balances')).length === 2);
ok('payments view works', (await as(OWNER, 'select * from payments_view')).length > 0);
ok('audit recorded', (await db.query('select count(*)::int c from audit_log')).rows[0].c > 10);
ok('order timeline recorded', (await db.query('select count(*)::int c from order_events where order_id=$1', [o3.id])).rows[0].c >= 5);

// ----- inactive loses access at once, same session
await db.query(`update profiles set status='Inactive' where id=$1`, [STAFF]);
ok('inactive rpc denied', (await err(rpc(STAFF, 'whoami'))) === 'Session expired. Sign in again.');
ok('inactive read empty', (await as(STAFF, 'select * from customers')).length === 0);
ok('inactive write denied', (await err(rpc(STAFF, 'create_order', maria.id, 'Household', 1, today, k()))) === 'Session expired. Sign in again.');
await db.query(`update profiles set status='Active' where id=$1`, [STAFF]);
ok('staff recording stamps name', (await rpc(STAFF, 'create_order', maria.id, 'Household', 1, today, k())).recorded_by_name === 'Ana Mendoza');

const inv2 = await rpc(OWNER, 'inventory_status');
ok('inventory formula', inv2.filled_available === inv2.physical_filled - inv2.reserved_filled, JSON.stringify(inv2));
{
// ===================== session guard, logout, request binding, privacy =====================
const sessionRow = async (sid) => (await db.query('select * from app_sessions where session_id=$1', [sid])).rows[0];
let t0 = pass + fail;

// ----- session guard
const ownerSid = sessionOf(OWNER);
await rpc(OWNER, 'whoami');
ok('S1 first protected request opens the session row', (await sessionRow(ownerSid))?.revoked_at === null);
await db.query(`update app_sessions set last_seen_at = clock_timestamp() - interval '31 minutes' where session_id=$1`, [ownerSid]);
ok('S2 idle 30 minutes: protected write denied', (await err(rpc(OWNER, 'whoami'))) === 'Session expired. Sign in again.');
ok('S3 idle session cannot renew itself', (Date.now() - new Date((await sessionRow(ownerSid)).last_seen_at).getTime()) > 30 * 60e3);
ok('S4 idle session reads nothing', (await as(OWNER, 'select * from customers')).length === 0);
newSession(OWNER);
ok('S5 fresh sign-in works again', (await rpc(OWNER, 'whoami')).name === 'Elena Mendoza');
newSession(OWNER, 31 * 60);
ok('S6 unused sign-in older than 30 minutes is refused', (await err(rpc(OWNER, 'whoami'))) === 'Session expired. Sign in again.');
newSession(OWNER);
await rpc(OWNER, 'whoami');

// ----- logout names the session
const sidBefore = sessionOf(OWNER);
const out = await rpc(OWNER, 'aq_logout');
ok('S7 logout reports the session it ended', out.logged_out === true && out.session_id === sidBefore);
ok('S8 logged-out session is revoked', (await sessionRow(sidBefore)).revoked_reason === 'logout');
ok('S9 logged-out session denied', (await err(rpc(OWNER, 'whoami'))) === 'Session expired. Sign in again.');
ok('S10 logout is audited', (await db.query(`select count(*)::int c from audit_log where action='logout' and details->>'session_id'=$1`, [sidBefore])).rows[0].c === 1);
newSession(OWNER);

// ----- deactivation revokes sessions
newSession(STAFF);
await rpc(STAFF, 'whoami');
const staffSid = sessionOf(STAFF);
await db.query(`update profiles set status='Inactive' where id=$1`, [STAFF]);
ok('S11 deactivation revokes open sessions', (await sessionRow(staffSid)).revoked_reason === 'deactivated');
await db.query(`update profiles set status='Active' where id=$1`, [STAFF]);
ok('S12 reactivated account still needs a new sign-in', (await err(rpc(STAFF, 'whoami'))) === 'Session expired. Sign in again.');
newSession(STAFF);
ok('S13 new sign-in after reactivation works', (await rpc(STAFF, 'whoami')).name === 'Ana Mendoza');

// ----- request key bound to its content
const key1 = k();
const first = await rpc(OWNER, 'save_customer', null, 'Retry Person', 'Household', '09170000001', '1 Retry St.', key1);
const again = await rpc(OWNER, 'save_customer', null, 'Retry Person', 'Household', '09170000001', '1 Retry St.', key1);
ok('R1 same key, same data: stored result reused', again.id === first.id);
const before = (await db.query('select count(*)::int c from customers')).rows[0].c;
ok('R2 same key, different data: conflict', (await err(rpc(OWNER, 'save_customer', null, 'Retry Person', 'Household', '09170000002', '1 Retry St.', key1))) === 'Request key was already used with different data.');
ok('R3 conflict saved nothing', (await db.query('select count(*)::int c from customers')).rows[0].c === before);
ok('R4 same key, different action: conflict', (await err(rpc(OWNER, 'create_order', first.id, 'Household', 1, today, key1))) === 'Request key was already used with different data.');
ok('R5 payment retry under its original key is reused once', await (async () => {
  const c = await rpc(OWNER, 'save_customer', null, 'Pay Twice', 'Household', '09170000003', '2 Retry St.', k());
  const o = await rpc(OWNER, 'create_order', c.id, 'Household', 1, today, k());
  const pk = k();
  const p1 = await rpc(OWNER, 'record_payment', o.id, null, 35, pk);
  const p2 = await rpc(OWNER, 'record_payment', o.id, null, 35, pk);
  const paid = (await db.query('select paid_amount from orders where id=$1', [o.id])).rows[0].paid_amount;
  return p1.payment_no === p2.payment_no && Number(paid) === 35;
})());

// ----- approved contact removal
const erase = (args) => db.query(`select public.erase_customer_contact($1,$2,$3,$4,$5,$6) as r`, args);
const victim = await rpc(OWNER, 'save_customer', null, 'Erase Me', 'Household', '09170000009', '9 Privacy St.', k());
const vo = await rpc(OWNER, 'create_order', victim.id, 'Household', 2, today, k());
const dec = k();
ok('P1 not callable by an app account', /permission denied/.test(await err(rpc(OWNER, 'erase_customer_contact', victim.id, dec, 'APR-1', 'Customer request', false, null))));
const eP2 = await err(erase([victim.id, dec, 'APR-1', 'Customer request', false, null]));
ok('P2 blocked while an order is in progress', /block contact removal/.test(eP2), String(eP2));
await rpc(OWNER, 'set_order_status', vo.id, 'For Pickup', k());
await rpc(OWNER, 'record_pickup', vo.id, 2, k());
await rpc(OWNER, 'set_order_status', vo.id, 'Refilling', k());
await rpc(OWNER, 'set_order_status', vo.id, 'Ready for Delivery', k());
await rpc(OWNER, 'record_delivery', vo.id, 2, k());
const eP3 = await err(erase([victim.id, dec, 'APR-1', 'Customer request', false, null]));
ok('P3 blocked while a balance is unsettled', /block contact removal/.test(eP3), String(eP3));
await rpc(OWNER, 'record_payment', vo.id, null, 70, k());
ok('P4 needs an approval reference and reason', /approved decision/.test(await err(erase([victim.id, dec, '', 'Customer request', false, null]))));
const dry = (await erase([victim.id, dec, 'APR-1', 'Customer request', false, null])).rows[0].r;
ok('P5 dry run reports the scope', dry.mode === 'DRY RUN' && dry.customer_contact_rows === 1 && dry.order_address_rows === 1, JSON.stringify(dry));
ok('P6 dry run changes nothing', (await db.query('select contact_number from customers where id=$1', [victim.id])).rows[0].contact_number === '09170000009'
  && (await db.query('select count(*)::int c from privacy_decisions')).rows[0].c === 0);
const done = (await erase([victim.id, dec, 'APR-1', 'Customer request', true, null])).rows[0].r;
const after = (await db.query('select name, contact_number, address from customers where id=$1', [victim.id])).rows[0];
ok('P7 execute removes contact and address, keeps the name', done.mode === 'EXECUTED' && after.contact_number === null && after.address === null && after.name === 'Erase Me');
ok('P8 order address removed, money kept', await (async () => {
  const o = (await db.query('select delivery_address, total, paid_amount from orders where id=$1', [vo.id])).rows[0];
  return o.delivery_address === null && Number(o.total) === 70 && Number(o.paid_amount) === 70;
})());
ok('P9 audit snapshots scrubbed', (await db.query(`select count(*)::int c from audit_log where entity='customer' and entity_id=$1 and (details ? 'contact_number' or details ? 'address')`, [victim.id])).rows[0].c === 0);
ok('P10 retry snapshots scrubbed', (await db.query(`select count(*)::int c from idempotency_keys where result->>'id'=$1 and (result ? 'contact_number' or result ? 'address')`, [victim.id])).rows[0].c === 0);
ok('P11 decision retained with the administrator', (await db.query('select administrator from privacy_decisions where decision_id=$1', [dec])).rows[0].administrator === 'postgres');
ok('P12 removal is audited', (await db.query(`select count(*)::int c from audit_log where action='approved_contact_removal' and entity_id=$1`, [victim.id])).rows[0].c === 1);
ok('P13 same decision replayed returns the retained result', (await erase([victim.id, dec, 'APR-1', 'Customer request', true, null])).rows[0].r.mode === 'EXECUTED');
ok('P14 same decision id with different data conflicts', /conflicts with the retained decision/.test(await err(erase([victim.id, dec, 'APR-2', 'Customer request', true, null]))));
ok('P15 later legitimate update is protected from an older cutoff', await (async () => {
  const c = await rpc(OWNER, 'save_customer', null, 'Updated Later', 'Dealer', '09170000010', '10 Old St.', k());
  const cutoff = (await db.query(`select clock_timestamp() t`)).rows[0].t;
  await new Promise((r) => setTimeout(r, 20));
  await rpc(OWNER, 'save_customer', c.id, 'Updated Later', 'Dealer', '09170000011', '11 New St.', k());
  const r = (await erase([c.id, k(), 'APR-3', 'Old request', true, cutoff])).rows[0].r;
  const row = (await db.query('select contact_number from customers where id=$1', [c.id])).rows[0];
  return r.protected_by_later_update === true && r.customer_contact_rows === 0 && row.contact_number === '09170000011';
})());
console.log(`session/privacy tests: ${pass + fail - t0} run`);
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail ? 1 : 0);
