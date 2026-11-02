# Backend work state

Sources: `handoff/CODEX_PROMPT.md`, requirements A-01..A-20/B-02/B-03/B-06, Owner/Staff story extracts, applicable IAS controls. Source documents remain unchanged. Backend owns `supabase/migrations/`, configuration, fixtures and `backend/`; account Edge Functions and administrator tooling are delegated separately.

Plan: implement private PostgreSQL schema, hardened authenticated RPCs, exact monetary/stock rules, RLS/grants; document API; validate migration/security/financial flows in an isolated PostgreSQL-compatible runner and concurrency where available. Never deploy or alter live data.

Status: In Progress. No production credentials supplied. Local PostgreSQL binaries absent; preparing PGlite test harness. Hosted Supabase Auth/MFA and real multi-connection PostgreSQL tests are separate integration evidence.

NEXT_ACTION: write schema and checked transaction RPC, then execute adversarial stock/payment/report tests.
