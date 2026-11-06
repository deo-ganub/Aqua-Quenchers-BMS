package com.example.aquaquencherspos;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

/** Shared screen scaffold: page padding, navigation, loading and saving. */
public abstract class BaseScreen extends Fragment {

    protected Context c;
    protected LinearLayout page;

    /**
     * Stays the same across retries of one form, so a retry after a dropped
     * connection cannot save twice. Replaced only after a successful save.
     */
    private String idempotencyKey = UUID.randomUUID().toString();

    /** Which navigation option stays highlighted on this screen. */
    abstract String section();

    /** Builds the page content. Runs again each time the screen is returned to. */
    abstract void build();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        c = requireContext();
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(true);
        page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(c, c.getResources().getConfiguration().smallestScreenWidthDp >= 600 ? 32 : 16);
        page.setPadding(side, Ui.dp(c, 28), side, Ui.dp(c, 16));
        scroll.addView(page);
        shell().highlight(section());
        build();
        return scroll;
    }

    protected OwnerDashboardActivity shell() {
        return (OwnerDashboardActivity) requireActivity();
    }

    protected void go(Fragment screen) {
        shell().open(screen);
    }

    protected void back() {
        shell().back();
    }

    protected void head(String title, String subtitle) {
        page.addView(Ui.spaced(c, Ui.pageTitle(c, title), 8));
        if (subtitle != null) {
            page.addView(Ui.spaced(c, Ui.body(c, subtitle), 16));
        }
    }

    protected <T extends View> T add(T view) {
        if (view.getLayoutParams() == null) {
            page.addView(Ui.spaced(c, view, 16));
        } else {
            page.addView(view);
        }
        return view;
    }

    // ------------------------------------------------------------------ data

    protected void load(String path, Consumer<JSONArray> ok) {
        Api.select(path, rows -> {
            if (isAdded()) {
                ok.accept(rows);
            }
        }, this::failed);
    }

    protected void loadOne(String path, Consumer<JSONObject> ok) {
        load(path, rows -> {
            if (rows.length() > 0) {
                ok.accept(rows.optJSONObject(0));
            } else {
                Dialogs.message(c, "Record not found.", "It may no longer be available.", "Back", this::back);
            }
        });
    }

    protected void call(String function, JSONObject params, Consumer<JSONObject> ok) {
        Api.rpc(function, params, result -> {
            if (isAdded()) {
                ok.accept(result);
            }
        }, this::failed);
    }

    /** Runs a save with the design's "Saving…" dialog and its idempotency key. */
    protected void save(String title, String detail, String function, JSONObject params, Consumer<JSONObject> ok) {
        AlertDialog busy = Dialogs.saving(c, title, detail);
        try {
            params.put("p_key", idempotencyKey);
        } catch (org.json.JSONException e) {
            throw new IllegalStateException(e);
        }
        Api.rpc(function, params, result -> {
            busy.dismiss();
            idempotencyKey = UUID.randomUUID().toString();
            if (isAdded()) {
                ok.accept(result);
            }
        }, error -> {
            busy.dismiss();
            failed(error);
        });
    }

    protected void failed(Api.ApiError error) {
        if (!isAdded()) {
            return;
        }
        if (error.sessionExpired) {
            shell().expireSession();
        } else {
            Dialogs.correct(c, error.getMessage(), error.detail);
        }
    }

    // --------------------------------------------------------------- helpers

    protected String argId() {
        return getArguments() == null ? null : getArguments().getString("id");
    }

    static <F extends Fragment> F withId(F fragment, String id) {
        Bundle b = new Bundle();
        b.putString("id", id);
        fragment.setArguments(b);
        return fragment;
    }

    /** JSON string or "" (org.json would otherwise return the text "null"). */
    static String s(JSONObject o, String key) {
        return o == null || o.isNull(key) ? "" : o.optString(key);
    }

    static double d(JSONObject o, String key) {
        return o == null || o.isNull(key) ? 0 : o.optDouble(key, 0);
    }

    static int i(JSONObject o, String key) {
        return o == null || o.isNull(key) ? 0 : o.optInt(key, 0);
    }

    /** Builds function parameters. A null value is sent as JSON null, as Postgres expects. */
    static JSONObject params(Object... pairs) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i < pairs.length; i += 2) {
                o.put((String) pairs[i], pairs[i + 1] == null ? JSONObject.NULL : pairs[i + 1]);
            }
        } catch (org.json.JSONException e) {
            throw new IllegalArgumentException(e);
        }
        return o;
    }

    /** A positive or zero money value with at most two decimals, or null if it is not one. */
    static BigDecimal money(String raw) {
        try {
            String clean = raw.replace("₱", "").replace(",", "").trim();
            if (clean.isEmpty()) {
                return null;
            }
            BigDecimal value = new BigDecimal(clean);
            return value.stripTrailingZeros().scale() > 2 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The typed text as a whole number, or null when it is fractional or not a number. */
    static Integer whole(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    protected static String recordedBy() {
        return Session.displayName();
    }
}
