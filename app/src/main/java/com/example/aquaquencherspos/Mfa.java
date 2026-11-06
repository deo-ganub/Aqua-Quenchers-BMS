package com.example.aquaquencherspos;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Managed MFA check before any staff-account change (A-11.1). Uses Supabase's
 * authenticator (TOTP) factors. The first time, the Owner adds the shown key to an
 * authenticator app.
 */
final class Mfa {

    interface Done {
        void onVerified();
    }

    private Mfa() {
    }

    static void verify(Context c, Done done, Api.Failure failed) {
        Api.authGet("user", user -> {
            String factorId = verifiedTotpFactor(user);
            if (factorId != null) {
                askCode(c, factorId, null, done, failed);
            } else {
                enroll(c, done, failed);
            }
        }, failed);
    }

    private static String verifiedTotpFactor(JSONObject user) {
        JSONArray factors = user.optJSONArray("factors");
        if (factors == null) {
            return null;
        }
        for (int i = 0; i < factors.length(); i++) {
            JSONObject f = factors.optJSONObject(i);
            if ("totp".equals(f.optString("factor_type")) && "verified".equals(f.optString("status"))) {
                return f.optString("id");
            }
        }
        return null;
    }

    private static void enroll(Context c, Done done, Api.Failure failed) {
        JSONObject body = BaseScreen.params("factor_type", "totp",
                "friendly_name", "Aqua Quenchers " + System.currentTimeMillis());
        Api.authPost("factors", body, factor -> {
            JSONObject totp = factor.optJSONObject("totp");
            String secret = totp == null ? "" : totp.optString("secret");
            askCode(c, factor.optString("id"), secret, done, failed);
        }, failed);
    }

    /** Figma "Owner · Verify staff account change". A non-null secret adds first-time setup text. */
    private static void askCode(Context c, String factorId, String setupSecret, Done done, Api.Failure failed) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 24);
        p.setPadding(pad, pad, pad, pad);
        p.setBackground(Ui.box(c, Ui.WHITE, Ui.BORDER, 16));
        p.addView(Ui.spaced(c, Ui.text(c, "Verify staff account change", Ui.semibold(c), 20, Ui.INK), 8));
        if (setupSecret != null) {
            p.addView(Ui.spaced(c, Ui.body(c, "First-time setup: add this key to your authenticator app, then enter its code."), 8));
            android.widget.TextView key = Ui.text(c, setupSecret, Ui.medium(c), 14, Ui.PRIMARY);
            key.setTextIsSelectable(true);
            p.addView(Ui.spaced(c, key, 12));
        } else {
            p.addView(Ui.spaced(c, Ui.body(c, "Enter your authenticator verification code to save this account change."), 12));
        }
        Field code = Field.quantity(c, "Verification code", "123456");
        p.addView(Ui.spaced(c, code, 12));
        AlertDialog[] holder = new AlertDialog[1];
        p.addView(Ui.spaced(c, Ui.primary(c, "Verify and save", v -> {
            String value = code.value();
            if (!value.matches("[0-9]{6}")) {
                code.setError("Enter a valid verification code.");
                return;
            }
            Api.authPost("factors/" + factorId + "/challenge", new JSONObject(), challenge -> {
                JSONObject verify = BaseScreen.params("challenge_id", challenge.optString("id"), "code", value);
                Api.authPost("factors/" + factorId + "/verify", verify, session -> {
                    // The verified session carries the proof the identity service checks.
                    Session.accessToken = session.optString("access_token");
                    Session.refreshToken = session.optString("refresh_token");
                    holder[0].dismiss();
                    done.onVerified();
                }, error -> {
                    if (error.connection || error.sessionExpired) {
                        holder[0].dismiss();
                        failed.onFailure(error);
                    } else {
                        code.setError("Enter a valid verification code.");
                    }
                });
            }, error -> {
                holder[0].dismiss();
                failed.onFailure(error);
            });
        }), 8), Ui.matchWrap());
        p.addView(Ui.secondary(c, "Cancel", v -> holder[0].dismiss()), Ui.matchWrap());

        AlertDialog d = new AlertDialog.Builder(c).setView(p).setCancelable(false).create();
        holder[0] = d;
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screen = c.getResources().getDisplayMetrics().widthPixels;
            d.getWindow().setLayout(Math.min(Ui.dp(c, 520), screen - Ui.dp(c, 32)),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
