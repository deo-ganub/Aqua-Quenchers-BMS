package com.example.aquaquencherspos;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

/**
 * The Figma design system in code: type styles, cards, buttons and statistics.
 * Sizes are the design's own values (4/8/12/16/24/32 spacing, 44dp controls,
 * 10dp control corners, 14dp card corners).
 */
public final class Ui {

    static final int PRIMARY = 0xFF0369A1;
    static final int PRIMARY_DARK = 0xFF075985;
    static final int ACTIVE = 0xFFE0F2FE;
    static final int ACTIVE_BORDER = 0xFFBAE6FD;
    static final int PAGE = 0xFFF8FAFC;
    static final int INK = 0xFF0F172A;
    static final int BODY = 0xFF475569;
    static final int MUTED = 0xFF64748B;
    static final int BORDER = 0xFFE2E8F0;
    static final int INPUT_BORDER = 0xFFCBD5E1;
    static final int ERROR = 0xFFB91C1C;
    static final int SUCCESS = 0xFF1A7338;
    static final int WARNING = 0xFF92400E;
    static final int WHITE = 0xFFFFFFFF;

    private Ui() {
    }

    static int dp(Context c, float value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, c.getResources().getDisplayMetrics()));
    }

    static Typeface semibold(Context c) {
        return ResourcesCompat.getFont(c, R.font.poppins_semibold);
    }

    static Typeface medium(Context c) {
        return ResourcesCompat.getFont(c, R.font.poppins_medium);
    }

    static Typeface regular(Context c) {
        return ResourcesCompat.getFont(c, R.font.open_sans);
    }

    static TextView text(Context c, String value, Typeface face, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(value);
        t.setTypeface(face);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(true);
        return t;
    }

    static TextView pageTitle(Context c, String value) {
        return text(c, value, semibold(c), 26, INK);
    }

    static TextView cardTitle(Context c, String value) {
        return text(c, value, semibold(c), 17, INK);
    }

    static TextView label(Context c, String value) {
        return text(c, value, medium(c), 14, INK);
    }

    static TextView body(Context c, String value) {
        return text(c, value, regular(c), 14, BODY);
    }

    static TextView ink(Context c, String value) {
        return text(c, value, regular(c), 14, INK);
    }

    static TextView hint(Context c, String value) {
        return text(c, value, regular(c), 12, BODY);
    }

    static GradientDrawable box(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, radiusDp));
        if (stroke != 0) {
            d.setStroke(dp(c, 1), stroke);
        }
        return d;
    }

    /** White content card with the design's 14dp corners and soft shadow. */
    static LinearLayout card(Context c) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(box(c, WHITE, BORDER, 14));
        card.setElevation(dp(c, 2));
        int pad = dp(c, 16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(c, 20);
        card.setLayoutParams(lp);
        return card;
    }

    static LinearLayout titledCard(Context c, String title) {
        LinearLayout card = card(c);
        card.addView(spaced(c, cardTitle(c, title), 12));
        return card;
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** Adds a bottom margin to a view that goes in a vertical layout. */
    static <T extends View> T spaced(Context c, T view, float bottomDp) {
        LinearLayout.LayoutParams lp = matchWrap();
        lp.bottomMargin = dp(c, bottomDp);
        view.setLayoutParams(lp);
        return view;
    }

    private static Button button(Context c, String label) {
        Button b = new Button(c, null, 0);
        b.setText(label);
        b.setAllCaps(false);
        b.setTypeface(medium(c));
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(c, 44));
        b.setMinimumHeight(dp(c, 44));
        b.setMinWidth(dp(c, 140));
        b.setMinimumWidth(dp(c, 140));
        int pad = dp(c, 20);
        b.setPadding(pad, 0, pad, 0);
        b.setStateListAnimator(null);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        return b;
    }

    static Button primary(Context c, String label, View.OnClickListener click) {
        Button b = button(c, label);
        b.setTextColor(WHITE);
        b.setBackground(box(c, PRIMARY, 0, 10));
        b.setOnClickListener(click);
        return b;
    }

    static Button secondary(Context c, String label, View.OnClickListener click) {
        Button b = button(c, label);
        b.setTextColor(INK);
        b.setBackground(box(c, ACTIVE, ACTIVE_BORDER, 10));
        b.setOnClickListener(click);
        return b;
    }

    /** A full-width pale row button, as on the Settings screen. */
    static Button listButton(Context c, String label, View.OnClickListener click) {
        Button b = button(c, label);
        b.setTextColor(INK);
        b.setBackground(box(c, ACTIVE, INPUT_BORDER, 10));
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        int pad = dp(c, 13);
        b.setPadding(pad, 0, pad, 0);
        b.setOnClickListener(click);
        return b;
    }

    static void setEnabled(Button b, boolean enabled) {
        b.setEnabled(enabled);
        b.setAlpha(enabled ? 1f : 0.5f);
    }

    /** Label over a bold value. Returns the value so screens can update it live. */
    static View stat(Context c, String label, String value, int labelColor, TextView[] valueOut) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(text(c, label, regular(c), 14, labelColor));
        TextView v = text(c, value, semibold(c), 17, INK);
        box.addView(v);
        if (valueOut != null) {
            valueOut[0] = v;
        }
        return box;
    }

    static View stat(Context c, String label, String value) {
        return stat(c, label, value, BODY, null);
    }

    /** Colors statuses the way the design does. */
    static int statusColor(String status) {
        if (status == null) {
            return INK;
        }
        switch (status) {
            case "Undelivered":
            case "Inactive":
                return ERROR;
            case "Active":
            case "Delivered":
            case "Paid":
                return SUCCESS;
            case "Partially Paid":
            case "For Pickup":
            case "Rescheduled":
                return WARNING;
            case "Ready for Delivery":
                return PRIMARY;
            default:
                return INK;
        }
    }

    static ColorStateList tint(int color) {
        return ColorStateList.valueOf(color);
    }
}
