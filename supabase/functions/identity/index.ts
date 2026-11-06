import { boundedText, requestObject, InputError } from "../_shared/identity_safety.ts";
// Protected identity service (Requirements A-01, A-11, B-03).
// Maps usernames to Supabase Auth accounts and performs privileged account
// changes, so the service-role key never enters the Android app.
//
// Deploy with "Verify JWT" turned OFF: login has no user token yet. Actions that
// need a signed-in user check the token themselves below.

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MFA_MAX_AGE_SECONDS = 300;

class Problem extends Error {
  constructor(public status: number, message: string, public detail = "") {
    super(message);
  }
}

const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });

const serviceHeaders = {
  apikey: SERVICE_KEY,
  Authorization: `Bearer ${SERVICE_KEY}`,
  "Content-Type": "application/json",
};

async function upstream(input: string, init: RequestInit = {}) {
  if (!SUPABASE_URL?.startsWith("https://") || !ANON_KEY || !SERVICE_KEY)
    throw new Problem(503, "Account service is not configured.");
  const response=await fetch(input,{...init,redirect:"error",signal:AbortSignal.timeout(15000)});
  const text=await boundedText(response.body,256*1024);
  return new Response(text || null,{status:response.status,headers:response.headers});
}

async function rest(path: string, init: RequestInit = {}) {
  const res = await upstream(`${SUPABASE_URL}${path}`, {
    ...init,
    headers: { ...serviceHeaders, ...(init.headers ?? {}) },
  });
  const text = await res.text();
  let body: any = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { message: text };
  }
  return { ok: res.ok, status: res.status, body };
}

async function findProfileByUsername(username: string) {
  // Usernames are stored in lowercase. An exact match avoids ilike, where "_"
  // would act as a wildcard and match other people's usernames.
  const r = await rest(
    `/rest/v1/profiles?select=id,role,username,full_name,status&username=eq.${encodeURIComponent(username.toLowerCase())}&limit=1`,
  );
  if (!r.ok) throw new Problem(500, "Unable to reach the account service.");
  return r.body[0] ?? null;
}

async function passwordGrant(email: string, password: string) {
  const res = await upstream(`${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: { apikey: ANON_KEY, "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  return res.ok ? await res.json() : null;
}

async function emailOf(userId: string): Promise<string | null> {
  const r = await rest(`/auth/v1/admin/users/${userId}`);
  return r.ok ? r.body.email ?? null : null;
}

const validUsername = (u: string) => /^[A-Za-z0-9._-]{3,40}$/.test(u);

async function login(body: any) {
  const invalid = new Problem(401, "Invalid username or password.");
  const username = String(body.username ?? "").trim();
  const password = String(body.password ?? "");
  if (!validUsername(username) || !password) throw invalid;

  let profile = await findProfileByUsername(username);
  if (!profile || profile.status !== "Active") throw invalid;
  const email = await emailOf(profile.id);
  if (!email) throw invalid;
  const session = await passwordGrant(email, password);
  if (!session || session.user?.id !== profile.id) throw invalid;
  // Account status/role may change while the managed password grant is in flight.
  const latest=await rest(`/rest/v1/profiles?select=id,role,username,full_name,status&id=eq.${profile.id}`);
  profile=latest.ok ? latest.body?.[0] : null;
  if (!profile || profile.status!=="Active") throw invalid;

  await audit(profile.id, "login", "account", profile.id, {});
  return {
    access_token: session.access_token,
    refresh_token: session.refresh_token,
    expires_in: session.expires_in,
    user_id: profile.id,
    role: profile.role,
    username: profile.username,
    full_name: profile.full_name,
  };
}

// Verifies the caller's token with Auth and re-reads their current status.
async function requireUser(req: Request) {
  const expired = new Problem(401, "Session expired. Sign in again.");
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) throw expired;
  const res = await upstream(`${SUPABASE_URL}/auth/v1/user`, {
    headers: { apikey: ANON_KEY, Authorization: `Bearer ${token}` },
  });
  if (!res.ok) throw expired;
  const user = await res.json();
  // The same database session policy applies to account-admin Edge requests.
  const session = await upstream(`${SUPABASE_URL}/rest/v1/rpc/aq_session_touch`, {
    method: "POST", headers: { apikey: ANON_KEY, Authorization: `Bearer ${token}`,
      "Content-Type": "application/json" }, body: "{}", signal: AbortSignal.timeout(15000),
  });
  if (!session.ok) throw expired;
  const r = await rest(`/rest/v1/profiles?select=id,role,status&id=eq.${user.id}`);
  const profile = r.ok ? r.body[0] : null;
  if (!profile || profile.status !== "Active") throw expired;
  return { user, profile, token };
}

function jwtClaims(token: string) {
  const part = token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/");
  return JSON.parse(atob(part.padEnd(part.length + ((4 - part.length % 4) % 4), "=")));
}

// The token must prove a TOTP check made moments ago, not just earlier in the session.
function requireRecentMfa(token: string) {
  const claims = jwtClaims(token);
  const totp = (claims.amr ?? []).find((a: any) => a.method === "totp");
  const age = totp ? Date.now() / 1000 - totp.timestamp : Infinity;
  if (claims.aal !== "aal2" || age > MFA_MAX_AGE_SECONDS) {
    throw new Problem(403, "Enter a valid verification code.", "No Staff account change was saved.");
  }
}

async function audit(actor: string, action: string, entity: string, id: string, details: unknown) {
  const recorded = await rest("/rest/v1/audit_log", {
    method: "POST",
    headers: { Prefer: "return=minimal" },
    body: JSON.stringify({ actor, action, entity, entity_id: id, details }),
  });
  if (!recorded.ok) throw new Problem(503, "The account operation needs reconciliation.",
    "The account may already have changed. Review the original operation before retrying; audit confirmation failed.");
}

async function saveStaff(req: Request, body: any) {
  const { profile: caller, token } = await requireUser(req);
  if (caller.role !== "owner") throw new Problem(403, "This action is limited to the Owner.");
  requireRecentMfa(token);

  const id = body.id ? String(body.id) : null;
  const fullName = String(body.full_name ?? "").trim();
  const username = String(body.username ?? "").trim().toLowerCase();
  const contact = String(body.contact_number ?? "").replace(/\D/g, "");
  const status = body.status === "Inactive" ? "Inactive" : "Active";
  const password = String(body.temporary_password ?? "");

  if (!fullName || !username || !body.contact_number || (!id && !password)) {
    throw new Problem(400, "Complete the required fields.",
      `Enter Full name, Username, Contact number and Temporary password. No staff account was ${id ? "saved" : "created"}.`);
  }
  if (!validUsername(username)) {
    throw new Problem(400, "Enter a valid username.", "Use 3 to 40 letters, numbers, dots, dashes or underscores.");
  }
  if (contact.length !== 11) {
    throw new Problem(400, "Please enter a valid contact number. Use 11 digits.",
      "No staff account was saved. Correct Contact number before saving again.");
  }
  if (password && [...password].length < 15) {
    throw new Problem(400, "Use at least 15 characters.",
      "The new or temporary password is too short. No password or account change was saved.");
  }
  const clash = await findProfileByUsername(username);
  if (clash && clash.id !== id) {
    throw new Problem(409, "Username already exists.",
      `${username} already belongs to ${clash.full_name ?? "another account"}. Do not create a second account with the same username.`);
  }

  let userId = id;
  if (id) {
    const existing = await rest(`/rest/v1/profiles?select=role&id=eq.${encodeURIComponent(id)}`);
    if (!existing.ok || existing.body[0]?.role !== "staff") {
      throw new Problem(404, "Staff account not found.");
    }
    if (password) {
      const u = await rest(`/auth/v1/admin/users/${id}`, { method: "PUT", body: JSON.stringify({ password }) });
      if (!u.ok) throw new Problem(400, u.body?.msg ?? "The password was not accepted.");
    }
    const p = await rest(`/rest/v1/profiles?id=eq.${id}`, {
      method: "PATCH",
      body: JSON.stringify({ full_name: fullName, username, contact_number: contact, status, updated_at: new Date().toISOString() }),
    });
    if (!p.ok) throw new Problem(503, "Unable to confirm the account update.", "A password or profile change may already have completed. Review the account before retrying.");
  } else {
    // The sign-in email is internal only: staff always use their username, so
    // username edits never touch the stable Auth user ID or linked records.
    const c = await rest("/auth/v1/admin/users", {
      method: "POST",
      body: JSON.stringify({
        email: `staff-${crypto.randomUUID()}@aq-staff.example.com`,
        password,
        email_confirm: true,
      }),
    });
    if (!c.ok) throw new Problem(400, c.body?.msg ?? "Staff account was not created.");
    userId = c.body.id;
    const p = await rest("/rest/v1/profiles?on_conflict=id", {
      method: "POST",
      headers: { Prefer: "resolution=merge-duplicates" },
      body: JSON.stringify({ id: userId, role: "staff", full_name: fullName, username, contact_number: contact, status }),
    });
    if (!p.ok) {
      if (![400,409,422].includes(p.status)) throw new Problem(503, "Account creation needs reconciliation.", "Do not create another identity until the original outcome is checked.");
      const cleanup=await rest(`/auth/v1/admin/users/${userId}`, { method: "DELETE" });
      if (!cleanup.ok) throw new Problem(503, "Account creation needs reconciliation.", "The identity cleanup was not confirmed.");
      throw new Problem(400, "Staff account was not created.");
    }
  }

  await audit(caller.id, id ? "update" : "create", "staff_account", userId!, {
    username, status, password_changed: Boolean(password),
  });
  return { id: userId, full_name: fullName, username, contact_number: contact, role: "Staff", status };
}

async function changePassword(req: Request, body: any) {
  const { user, profile } = await requireUser(req);
  const current = String(body.current_password ?? "");
  const next = String(body.new_password ?? "");
  if ([...next].length < 15) {
    throw new Problem(400, "Use at least 15 characters.",
      "The new or temporary password is too short. No password or account change was saved.");
  }
  if (!(await passwordGrant(user.email, current))) {
    throw new Problem(400, "Current password is incorrect.",
      "Your password was not changed. Enter the correct current password and try again.");
  }
  const u = await rest(`/auth/v1/admin/users/${user.id}`, { method: "PUT", body: JSON.stringify({ password: next }) });
  if (!u.ok) throw new Problem(400, u.body?.msg ?? "The password was not accepted.");
  await audit(profile.id, "change_password", "account", profile.id, {});
  return { ok: true };
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return reply(405, { message: "Method not allowed." });
  try {
    const body = await requestObject(req);
    switch (body.action) {
      case "login":
        return reply(200, await login(body));
      case "save_staff":
        return reply(200, await saveStaff(req, body));
      case "change_password":
        return reply(200, await changePassword(req, body));
      default:
        return reply(400, { message: "Unknown action." });
    }
  } catch (e) {
    if (e instanceof Problem) return reply(e.status, { message: e.message, details: e.detail });
    if (e instanceof InputError) return reply(400, { message: e.message });
    console.error("identity operation failed; private details omitted");
    return reply(503, { message: "Unable to confirm the request.", details: "For an account change, review the original operation before retrying." });
  }
});
