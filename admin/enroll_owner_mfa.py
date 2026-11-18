#!/usr/bin/env python3
"""Enroll the Owner's authenticator (TOTP) for managed MFA.

Run this yourself on a trusted computer; nothing is written to disk. It signs in through the
deployed username-login handler, enrolls a TOTP factor, shows the otpauth URI/secret to add to
an authenticator app, then verifies one code so staff-account changes can use MFA.

    python admin/enroll_owner_mfa.py --url https://<ref>.supabase.co --anon-key <public anon key>
"""
import argparse, getpass, json, sys, urllib.error, urllib.request


def call(base, anon, method, path, body=None, token=None):
    headers = {"apikey": anon, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        sys.exit(f"{path} failed ({e.code}): {e.read()[:300].decode(errors='replace')}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--anon-key", required=True)
    a = ap.parse_args()
    base = a.url.rstrip("/")
    user = input("Owner username: ").strip()
    password = getpass.getpass("Password: ")
    session = call(base, a.anon_key, "POST", "/functions/v1/username-login", {"username": user, "password": password})
    token = (session.get("session") or session).get("access_token")
    if not token:
        sys.exit("Sign-in did not return a session.")
    me = call(base, a.anon_key, "GET", "/auth/v1/user", token=token)
    if any(f.get("status") == "verified" and f.get("factor_type") == "totp" for f in me.get("factors") or []):
        sys.exit("A verified authenticator is already enrolled for this account.")
    factor = call(base, a.anon_key, "POST", "/auth/v1/factors", {"factor_type": "totp", "friendly_name": "Aqua Quenchers Owner"}, token)
    print("\nAdd this account to your authenticator app (Google Authenticator, Authy, ...):")
    print("  Secret key:", factor["totp"]["secret"])
    print("  Or open this URI:", factor["totp"]["uri"])
    code = input("\nEnter the 6-digit code now shown in the app: ").strip()
    challenge = call(base, a.anon_key, "POST", f"/auth/v1/factors/{factor['id']}/challenge", {}, token)
    call(base, a.anon_key, "POST", f"/auth/v1/factors/{factor['id']}/verify", {"challenge_id": challenge["id"], "code": code}, token)
    print("Authenticator verified. Staff-account changes will now ask for this code.")


if __name__ == "__main__":
    main()
