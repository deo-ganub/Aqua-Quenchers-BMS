package com.aquaquenchers.bms;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Design toolkit for the Figma page "HIGH FIDELITY · Final". Every size passed to these helpers is a
 * Figma design unit; MainActivity scales one design unit to px so that the Owner frame (1194 wide) and
 * the Staff frame (440 wide) fill the screen width. Presentation only: no business rule lives here.
 */
final class Ui {
    // Colours exactly as sampled from the Figma frames.
    static final int PRIMARY = 0xFF0369A1, INK = 0xFF0F172A, MUTED = 0xFF475469, NAV_MUTED = 0xFF475569, BG = 0xFFF8FAFC,
        BORDER = 0xFFE2E8F0, FIELD = 0xFFCCD6E0, TONAL = 0xFFE0F2FF, NAV_TONAL = 0xFFE0F2FE, NAV_TONAL_STROKE = 0xFFBAE6FD,
        AMBER = 0xFFB26608, AMBER_BG = 0xFFFFF3DA, GREEN = 0xFF178C45, GREEN_BG = 0xFFE7F7EA, GREY_BG = 0xFFEDF2F7,
        DANGER = 0xFFCC2929, DANGER_BG = 0xFFFCE5E5, OWNER_DANGER_BG = 0xFFFDECEA, WHITE = 0xFFFFFFFF, DIVIDER = 0xFFE2E8F0;
    static final int FILLED = 0, TONAL_BTN = 1, OUTLINE = 2, AMBER_BTN = 3, DANGER_OUTLINE = 4, DISABLED = 5;
    static final int INFO = 0, SUCCESS = 1, WARNING = 2, ERROR = 3;

    final Activity a;
    final Typeface p4, p5, p6, o4, o6;

    Ui(Activity activity) {
        a = activity;
        p4 = a.getResources().getFont(R.font.poppins_regular);
        p5 = a.getResources().getFont(R.font.poppins_medium);
        p6 = a.getResources().getFont(R.font.poppins_semibold);
        Typeface openSans = a.getResources().getFont(R.font.open_sans);
        o4 = Typeface.create(openSans, 400, false);
        o6 = Typeface.create(openSans, 600, false);
    }

    /** Design units to px. */
    int px(float v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }
    int dp(float v) { return px(v); }

    // ------------------------------------------------------------------ shapes and boxes
    GradientDrawable shape(int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(px(radius));
        if (stroke != 0) d.setStroke(Math.max(1, px(1)), stroke);
        return d;
    }
    <T extends View> T bg(T v, int fill, int stroke, float radius) { v.setBackground(shape(fill, stroke, radius)); return v; }
    <T extends View> T pad(T v, float l, float t, float r, float b) { v.setPadding(px(l), px(t), px(r), px(b)); return v; }
    <T extends View> T pad(T v, float h, float vert) { return pad(v, h, vert, h, vert); }

    LinearLayout box(boolean vertical, float gap) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        if (!vertical) l.setGravity(Gravity.CENTER_VERTICAL);
        gap(l, gap);
        return l;
    }
    /** Figma auto-layout spacing: a transparent divider between visible children. */
    void gap(LinearLayout l, float gap) {
        if (gap <= 0) { l.setShowDividers(LinearLayout.SHOW_DIVIDER_NONE); return; }
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.TRANSPARENT);
        d.setSize(px(gap), px(gap));
        l.setDividerDrawable(d);
        l.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
    }
    LinearLayout vbox(float gap) { return box(true, gap); }
    LinearLayout hbox(float gap) { return box(false, gap); }
    /** Vertical box added to a parent at full width. */
    LinearLayout V(ViewGroup parent, float gap) { return fill(parent, vbox(gap)); }
    /** Horizontal box added to a parent at full width. */
    LinearLayout H(ViewGroup parent, float gap) { return fill(parent, hbox(gap)); }

    // ------------------------------------------------------------------ adding children
    <T extends View> T fill(ViewGroup p, T v) { p.addView(v, params(p, -1, -2)); return v; }
    <T extends View> T hug(ViewGroup p, T v) { p.addView(v, params(p, -2, -2)); return v; }
    <T extends View> T grow(ViewGroup p, T v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        p.addView(v, lp); return v;
    }
    <T extends View> T growV(ViewGroup p, T v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, 1);
        p.addView(v, lp); return v;
    }
    <T extends View> T sized(ViewGroup p, T v, float w, float h) { p.addView(v, params(p, w < 0 ? (int) w : px(w), h < 0 ? (int) h : px(h))); return v; }
    View spacer(LinearLayout row) { View v = new View(a); row.addView(v, new LinearLayout.LayoutParams(0, 1, 1)); return v; }
    View space(ViewGroup p, float h) { View v = new View(a); p.addView(v, params(p, -1, px(h))); return v; }
    private ViewGroup.LayoutParams params(ViewGroup p, int w, int h) {
        if (p instanceof LinearLayout) return new LinearLayout.LayoutParams(w, h);
        if (p instanceof FrameLayout) return new FrameLayout.LayoutParams(w, h);
        return new ViewGroup.MarginLayoutParams(w, h);
    }
    static void margins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams) lp).setMargins(l, t, r, b);
    }

    // ------------------------------------------------------------------ text
    static float defaultLh(float size, boolean poppins) {
        if (poppins) return Math.round(size * 1.5f);
        if (size <= 10) return 16; if (size <= 11) return 15; if (size <= 12) return 16; if (size <= 13) return 18;
        if (size <= 14) return 19; if (size <= 15) return 20; if (size <= 16) return 22; return Math.round(size * 1.36f);
    }
    TextView tv(String value, float size, int color, Typeface face) {
        boolean poppins = face == p4 || face == p5 || face == p6;
        return tv(value, size, defaultLh(size, poppins), color, face);
    }
    TextView tv(String value, float size, float lh, int color, Typeface face) {
        TextView t = new TextView(a);
        t.setText(value);
        t.setTypeface(face);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        size(t, size, lh);
        return t;
    }
    void size(TextView t, float size, float lh) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        t.setLineHeight(px(lh));
    }
    /** Adds a text at full width. */
    TextView text(ViewGroup p, String value, float size, int color, Typeface face) { return fill(p, tv(value, size, color, face)); }
    TextView text(ViewGroup p, String value, float size, float lh, int color, Typeface face) { return fill(p, tv(value, size, lh, color, face)); }
    /** Adds a text that hugs its content. */
    TextView hugText(ViewGroup p, String value, float size, int color, Typeface face) { return hug(p, tv(value, size, color, face)); }
    TextView single(TextView t) { t.setSingleLine(true); t.setEllipsize(TextUtils.TruncateAt.END); return t; }
    /** Figma field label: Open Sans 600 13/18 muted. */
    TextView label(ViewGroup p, String value) { return text(p, value, 13, MUTED, o6); }

    // ------------------------------------------------------------------ cards and banners
    LinearLayout card(ViewGroup p, float gap, float padding) {
        LinearLayout c = V(p, gap);
        bg(c, WHITE, BORDER, 16);
        pad(c, padding, padding);
        return c;
    }
    LinearLayout banner(ViewGroup p, int kind, String title, String detail, boolean owner) {
        int bgc, fg;
        switch (kind) {
            case SUCCESS: bgc = GREEN_BG; fg = GREEN; break;
            case WARNING: bgc = AMBER_BG; fg = AMBER; break;
            case ERROR: bgc = owner ? OWNER_DANGER_BG : DANGER_BG; fg = DANGER; break;
            default: bgc = TONAL; fg = PRIMARY; break;
        }
        LinearLayout b = vbox(2);
        bg(b, bgc, 0, 12);
        pad(b, 14, 14);
        TextView t = text(b, title, 14, fg, p5);
        t.setTag("banner-title");
        if (detail != null && !detail.isEmpty()) text(b, detail, 13, INK, o4).setTag("banner-detail");
        b.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        b.setContentDescription(title + (detail == null || detail.isEmpty() ? "" : ". " + detail));
        if (p != null) fill(p, b);
        return b;
    }
    void setBanner(LinearLayout b, int kind, String title, String detail, boolean owner) {
        int bgc, fg;
        switch (kind) {
            case SUCCESS: bgc = GREEN_BG; fg = GREEN; break;
            case WARNING: bgc = AMBER_BG; fg = AMBER; break;
            case ERROR: bgc = owner ? OWNER_DANGER_BG : DANGER_BG; fg = DANGER; break;
            default: bgc = TONAL; fg = PRIMARY; break;
        }
        bg(b, bgc, 0, 12);
        TextView t = (TextView) b.getChildAt(0);
        t.setText(title); t.setTextColor(fg);
        TextView d = b.getChildCount() > 1 ? (TextView) b.getChildAt(1) : null;
        if (detail != null && !detail.isEmpty()) {
            if (d == null) d = text(b, "", 13, INK, o4);
            d.setText(detail); d.setVisibility(View.VISIBLE);
        } else if (d != null) d.setVisibility(View.GONE);
    }
    View divider(ViewGroup p) { View v = new View(a); v.setBackgroundColor(DIVIDER); p.addView(v, params(p, -1, Math.max(1, px(1)))); return v; }

    // ------------------------------------------------------------------ chips, avatars, icons
    TextView chip(String value, int bgc, int fg) {
        TextView t = tv(value, 12, 18, fg, p5);
        pad(t, 10, 4);
        bg(t, bgc, 0, 20);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        return t;
    }
    TextView avatar(String name, float size, float font) {
        TextView t = tv(initials(name), font, Math.round(font * 1.5f), PRIMARY, p5);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(TONAL, 0, size / 2f));
        t.setContentDescription(name);
        return t;
    }
    static String initials(String name) {
        if (name == null) return "";
        StringBuilder s = new StringBuilder();
        for (String part : name.trim().split("\\s+")) if (!part.isEmpty() && s.length() < 2) s.append(Character.toUpperCase(part.charAt(0)));
        return s.toString();
    }
    ImageView icon(int drawable, int color) {
        ImageView v = new ImageView(a);
        v.setImageResource(drawable);
        v.setColorFilter(color);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    // ------------------------------------------------------------------ buttons
    /** A Figma button: fixed height, radius, Poppins 500 label, 20 horizontal padding. */
    TextView button(String label, int style, float height, float radius, float font, Runnable action) {
        TextView b = tv(label, font, Math.round(font * 1.5f), INK, p5);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        pad(b, 20, 0);
        b.setMinHeight(px(height));
        b.setTag(R.id.button_shape, new float[]{radius});
        b.setClickable(true); b.setFocusable(true);
        style(b, style);
        if (action != null) b.setOnClickListener(v -> { if (b.isEnabled()) action.run(); });
        return b;
    }
    void style(TextView b, int style) {
        float radius = b.getTag(R.id.button_shape) instanceof float[] ? ((float[]) b.getTag(R.id.button_shape))[0] : 12;
        int fill, stroke = 0, text;
        switch (style) {
            case TONAL_BTN: fill = TONAL; text = PRIMARY; break;
            case OUTLINE: fill = WHITE; stroke = FIELD; text = INK; break;
            case AMBER_BTN: fill = AMBER; text = WHITE; break;
            case DANGER_OUTLINE: fill = WHITE; stroke = DANGER; text = DANGER; break;
            case DISABLED: fill = BORDER; text = MUTED; break;
            default: fill = PRIMARY; text = WHITE; break;
        }
        b.setBackground(shape(fill, stroke, radius));
        b.setTextColor(text);
        b.setEnabled(style != DISABLED);
        b.setTag(R.id.button_style, style);
    }
    /** Staff dock button: 52 high, radius 14, 16/24. */
    TextView dockButton(String label, int style, Runnable action) { return button(label, style, 52, 14, 16, action); }
    /** Owner form button: 48 high, radius 12; primary actions 16/24, secondary 15/22. */
    TextView ownerButton(String label, int style, Runnable action) {
        return button(label, style, 48, 12, style == OUTLINE || style == TONAL_BTN ? 15 : 16, action);
    }
    /** Text link in Poppins 500 primary. */
    TextView link(String label, float size, Runnable action) {
        TextView t = tv(label, size, PRIMARY, p5);
        t.setClickable(true);
        if (action != null) t.setOnClickListener(v -> action.run());
        return t;
    }

    // ------------------------------------------------------------------ tabs and quick picks
    class Tabs {
        final LinearLayout view; final List<TextView> items = new ArrayList<>(); int selected;
        IntConsumer onSelect;
        Tabs(LinearLayout v) { view = v; }
        void select(int index, boolean notify) {
            selected = index;
            for (int i = 0; i < items.size(); i++) paintTab(items.get(i), i == index);
            if (notify && onSelect != null) onSelect.accept(index);
        }
        void label(int i, String value) { if (i >= 0 && i < items.size()) items.get(i).setText(value); }
    }
    void paintTab(TextView t, boolean on) {
        float r = t.getTag(R.id.button_shape) instanceof float[] ? ((float[]) t.getTag(R.id.button_shape))[0] : 20;
        t.setBackground(shape(on ? PRIMARY : WHITE, on ? 0 : FIELD, r));
        t.setTextColor(on ? WHITE : INK);
        t.setSelected(on);
    }
    /**
     * Filter tabs or quick picks. Staff: radius 22, vertical padding 9. Owner: radius 20, vertical padding 8.
     * When {@code equal} is true every tab takes the same width (quick picks).
     */
    Tabs tabs(ViewGroup parent, String[] labels, int selected, boolean equal, boolean owner, float gap, IntConsumer onSelect) {
        LinearLayout line = hbox(gap);
        if (equal) fill(parent, line); else hug(parent, line);
        Tabs tabs = new Tabs(line);
        tabs.onSelect = onSelect;
        float r = owner ? 20 : 22, v = owner ? 8 : 9;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = tv(labels[i], 14, 21, INK, p5);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            pad(t, 14, v);
            t.setTag(R.id.button_shape, new float[]{r});
            t.setClickable(true);
            t.setOnClickListener(x -> tabs.select(index, true));
            if (equal) grow(line, t); else hug(line, t);
            tabs.items.add(t);
        }
        tabs.select(selected, false);
        return tabs;
    }

    /** Segmented control: grey track radius 14, padding 4, selected segment white with border. */
    Tabs segmented(ViewGroup parent, String[] labels, int selected, IntConsumer onSelect) {
        LinearLayout line = hbox(4);
        bg(line, GREY_BG, 0, 14);
        pad(line, 4, 4);
        fill(parent, line);
        Tabs tabs = new Tabs(line) {
            @Override void select(int index, boolean notify) {
                selected = index;
                for (int i = 0; i < items.size(); i++) {
                    TextView t = items.get(i); boolean on = i == index;
                    t.setBackground(on ? shape(WHITE, FIELD, 10) : shape(Color.TRANSPARENT, 0, 10));
                    t.setTextColor(on ? PRIMARY : MUTED); t.setSelected(on);
                }
                if (notify && onSelect != null) onSelect.accept(index);
            }
        };
        tabs.onSelect = onSelect;
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = tv(labels[i], 14, 21, MUTED, p5);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setClickable(true);
            t.setOnClickListener(x -> tabs.select(index, true));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, px(43), 1);
            line.addView(t, lp);
            tabs.items.add(t);
        }
        tabs.select(selected, false);
        return tabs;
    }

    // ------------------------------------------------------------------ inputs
    /** Text input box. Staff: 52 high, radius 12, 16/22. Owner: 46 high, radius 10, 15/20. */
    EditText input(boolean owner, String hint, int inputType) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(inputType);
        e.setTypeface(o4);
        e.setTextColor(INK);
        e.setHintTextColor(MUTED);
        e.setIncludeFontPadding(false);
        size(e, owner ? 15 : 16, owner ? 20 : 22);
        e.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        pad(e, owner ? 14 : 16, 0);
        if ((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) == 0) e.setSingleLine(true);
        e.setMinHeight(px(owner ? 46 : 52));
        e.setBackground(shape(WHITE, FIELD, owner ? 10 : 12));
        e.setTag(R.id.field_owner, owner);
        return e;
    }
    void flagInvalid(EditText e, boolean invalid) {
        boolean owner = Boolean.TRUE.equals(e.getTag(R.id.field_owner));
        e.setBackground(shape(WHITE, invalid ? DANGER : FIELD, owner ? 10 : 12));
    }
    TextView fieldError(ViewGroup p) {
        TextView t = text(p, "", 13, DANGER, o4);
        t.setVisibility(View.GONE);
        return t;
    }

    // ------------------------------------------------------------------ stepper
    /**
     * Quantity stepper: tonal circles with − and +, a large value and a unit line underneath.
     * Staff: circles 60, value 64/96. Owner: circles 52, value 52/78.
     */
    TextView stepper(ViewGroup parent, EditText value, String unit, boolean owner, boolean centered) {
        float circle = owner ? 52 : 60, sign = owner ? 26 : 30, size = owner ? 52 : 64;
        LinearLayout line = hbox(24);
        line.setGravity(centered ? Gravity.CENTER : Gravity.CENTER_VERTICAL | Gravity.START);
        fill(parent, line);
        TextView minus = round("−", circle, sign, "Decrease");
        sized(line, minus, circle, circle);
        LinearLayout middle = vbox(0);
        middle.setGravity(Gravity.CENTER_HORIZONTAL);
        hug(line, middle);
        value.setBackground(null);
        value.setGravity(Gravity.CENTER);
        value.setTypeface(p6);
        value.setTextColor(INK);
        value.setHintTextColor(INK);
        value.setIncludeFontPadding(false);
        size(value, size, Math.round(size * 1.5f));
        value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        value.setSingleLine(true);
        value.setPadding(0, 0, 0, 0);
        value.setMinWidth(px(owner ? 80 : 65));
        hug(middle, value);
        TextView u = tv(unit, 14, 19, MUTED, o4);
        u.setGravity(Gravity.CENTER);
        hug(middle, u);
        TextView plus = round("+", circle, sign, "Increase");
        sized(line, plus, circle, circle);
        minus.setOnClickListener(v -> value.setText(Integer.toString(Math.max(0, whole(value, false) - 1))));
        plus.setOnClickListener(v -> value.setText(Integer.toString(whole(value, true) + 1)));
        return u;
    }
    private static int whole(EditText value, boolean up) {
        try {
            double d = Double.parseDouble(value.getText().toString().trim());
            return (int) (up ? Math.floor(d) : Math.ceil(d));
        } catch (NumberFormatException e) { return 0; }
    }
    private TextView round(String symbol, float circle, float sign, String description) {
        TextView t = tv(symbol, sign, Math.round(sign * 1.5f), PRIMARY, p5);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(TONAL, 0, circle / 2f));
        t.setClickable(true);
        t.setContentDescription(description);
        return t;
    }

    // ------------------------------------------------------------------ order progress
    static final String[] STAGES_STAFF = {"Ordered", "Pickup", "Picked up", "Refill", "Ready", "Delivered"};
    static final String[] STAGES_OWNER = {"Ordered", "For pickup", "Picked up", "Refilling", "Ready", "Delivered"};
    static int stageIndex(String status) {
        switch (status == null ? "" : status) {
            case "ordered": return 0;
            case "for_pickup": case "rescheduled": return 1;
            case "picked_up": return 2;
            case "refilling": return 3;
            case "ready_for_delivery": case "undelivered": return 4;
            case "delivered": return 5;
            default: return 0;
        }
    }
    /** Six-segment stage stepper: bars 6 high, radius 3, label below with 6 spacing. */
    void progress(ViewGroup parent, String status, boolean owner) {
        String[] labels = owner ? STAGES_OWNER : STAGES_STAFF;
        int stage = stageIndex(status);
        float font = owner ? 12 : 11;
        LinearLayout line = H(parent, owner ? 6 : 4);
        line.setGravity(Gravity.TOP);
        for (int i = 0; i < labels.length; i++) {
            LinearLayout cell = vbox(6);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View bar = new View(a);
            bar.setBackground(shape(i <= stage ? PRIMARY : BORDER, 0, 3));
            cell.addView(bar, new LinearLayout.LayoutParams(-1, px(6)));
            boolean current = i == stage;
            TextView t = current ? tv(labels[i], font, owner ? 18 : 16, PRIMARY, p5) : tv(labels[i], font, owner ? 16 : 15, MUTED, o4);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            hug(cell, t);
            grow(line, cell);
        }
        line.setContentDescription("Progress: " + Rules.label(status));
    }

    // ------------------------------------------------------------------ rows
    /** Label on the left, value on the right: Open Sans 14/19 muted and Poppins 500 15/22. */
    TextView[] pair(ViewGroup parent, String key, String value) { return pair(parent, key, value, INK, 15, p5); }
    TextView[] pair(ViewGroup parent, String key, String value, int color, float size, Typeface face) {
        LinearLayout r = H(parent, 8);
        TextView k = tv(key, 14, 19, MUTED, o4);
        grow(r, k);
        TextView v = tv(value, size, Math.round(size * 1.5f), color, face);
        v.setGravity(Gravity.END);
        hug(r, v);
        return new TextView[]{k, v};
    }

    // ------------------------------------------------------------------ status chips
    TextView statusChip(String status, int pendingGallons) {
        switch (status == null ? "" : status) {
            case "undelivered": return chip(pendingGallons > 0 ? pendingGallons + " left" : "Undelivered", AMBER_BG, AMBER);
            case "ready_for_delivery": return chip("Ready", GREEN_BG, GREEN);
            case "for_pickup": return chip("To pick up", TONAL, PRIMARY);
            case "delivered": return chip("Delivered", GREEN_BG, GREEN);
            case "picked_up": return chip("Picked up", TONAL, PRIMARY);
            case "refilling": return chip("Refilling", TONAL, PRIMARY);
            case "rescheduled": return chip("Rescheduled", AMBER_BG, AMBER);
            default: return chip(Rules.label(status), TONAL, PRIMARY);
        }
    }
    TextView paymentChip(String paymentStatus, boolean shortLabel) {
        switch (paymentStatus == null ? "" : paymentStatus) {
            case "paid": return chip("Paid", GREEN_BG, GREEN);
            case "partially_paid": return chip(shortLabel ? "Partial" : "Partially paid", AMBER_BG, AMBER);
            default: return chip("Unpaid", DANGER_BG, DANGER);
        }
    }
}
