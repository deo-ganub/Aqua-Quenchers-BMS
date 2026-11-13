package com.aquaquenchers.bms;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Design system of the Figma file "HIGH FIDELITY · Final": colours, Poppins/Open Sans type,
 * cards, pills, chips, segmented controls, steppers and the order progress bar.
 * Presentation only: no business rule or backend call lives here.
 */
final class Ui {
    static final int PRIMARY = 0xFF0369A1, INK = 0xFF0F172A, MUTED = 0xFF475569, BG = 0xFFF8FAFC, BORDER = 0xFFE2E8F0,
        TONAL = 0xFFE0F2FE, AMBER = 0xFFB26608, AMBER_BG = 0xFFFFF3DA, GREEN = 0xFF178C45, GREEN_BG = 0xFFE7F7EA,
        GREY_BG = 0xFFEDF2F7, DANGER = 0xFFCC2929, DANGER_BG = 0xFFFCE5E5, WHITE = 0xFFFFFFFF, DISABLED_TEXT = 0xFF64748B,
        INFO_TEXT = 0xFF0369A1;
    static final int FILLED = 0, TONAL_BTN = 1, OUTLINE = 2, AMBER_BTN = 3, DANGER_OUTLINE = 4, LINK = 5;

    final Activity a;
    final Typeface head, headBold, body, bodySemi;

    Ui(Activity activity) {
        a = activity;
        // Figma uses Poppins Medium for labels, buttons and pills, Poppins SemiBold for titles and numbers.
        Typeface openSans = a.getResources().getFont(R.font.open_sans);
        head = a.getResources().getFont(R.font.poppins_medium);
        headBold = a.getResources().getFont(R.font.poppins_semibold);
        body = openSans;
        bodySemi = Typeface.create(openSans, 600, false);
    }

    int dp(float value) { return Math.round(value * a.getResources().getDisplayMetrics().density); }
    boolean tablet() { return a.getResources().getConfiguration().smallestScreenWidthDp >= 600; }

    GradientDrawable shape(int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill); d.setCornerRadius(dp(radiusDp));
        if (stroke != 0) d.setStroke(Math.max(1, dp(1)), stroke);
        return d;
    }

    LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    // ---------- text ----------
    TextView tv(String value, float sp, int color, Typeface face) {
        TextView t = new TextView(a);
        t.setText(value); t.setTextSize(sp); t.setTextColor(color); t.setTypeface(face);
        return t;
    }
    TextView text(ViewGroup parent, String value, float sp, int color, Typeface face) {
        TextView t = tv(value, sp, color, face);
        parent.addView(t, lp(-1, -2, 0, 0, 0, 0));
        return t;
    }
    TextView title(ViewGroup parent, String value) {
        TextView t = tv(value, 22, INK, headBold);
        parent.addView(t, lp(-1, -2, 0, 4, 0, 8));
        return t;
    }
    TextView heading(ViewGroup parent, String value) {
        TextView t = tv(value, 17, INK, headBold);
        parent.addView(t, lp(-1, -2, 0, 0, 0, 6));
        return t;
    }
    TextView label(ViewGroup parent, String value) {
        TextView t = tv(value, 13, INK, bodySemi);
        parent.addView(t, lp(-1, -2, 0, 8, 0, 4));
        return t;
    }
    TextView muted(ViewGroup parent, String value) {
        TextView t = tv(value, 13, MUTED, body);
        parent.addView(t, lp(-1, -2, 0, 0, 0, 2));
        return t;
    }
    TextView smallError(ViewGroup parent) {
        TextView t = tv("", 12, DANGER, body);
        t.setVisibility(View.GONE);
        t.setContentDescription("Field error");
        parent.addView(t, lp(-1, -2, 0, 4, 0, 4));
        return t;
    }

    // ---------- containers ----------
    LinearLayout card(ViewGroup parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(shape(WHITE, BORDER, 14));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        parent.addView(c, lp(-1, -2, 0, 0, 0, 12));
        return c;
    }
    LinearLayout row(ViewGroup parent) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(r, lp(-1, -2, 0, 0, 0, 0));
        return r;
    }
    LinearLayout column(ViewGroup parent) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        parent.addView(c, lp(-1, -2, 0, 0, 0, 0));
        return c;
    }
    LinearLayout weighted(LinearLayout parent, float weight) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        parent.addView(c, new LinearLayout.LayoutParams(0, -2, weight));
        return c;
    }
    View divider(ViewGroup parent) {
        View v = new View(a);
        v.setBackgroundColor(BORDER);
        parent.addView(v, lp(-1, 1, 0, 10, 0, 10));
        return v;
    }
    /** Label on the left and value on the right (price, balance and similar rows). */
    TextView[] pair(ViewGroup parent, String key, String value, int valueColor, boolean strong) {
        LinearLayout r = row(parent);
        r.setPadding(0, dp(4), 0, dp(4));
        TextView k = tv(key, 13, MUTED, body);
        r.addView(k, new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = tv(value, strong ? 17 : 14, valueColor, strong ? headBold : bodySemi);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(-2, -2));
        return new TextView[]{k, v};
    }

    // ---------- pills, avatars, icons ----------
    TextView pill(String value, int bg, int fg) {
        TextView t = tv(value, 11, fg, head);
        t.setPadding(dp(10), dp(3), dp(10), dp(3));
        t.setBackground(shape(bg, 0, 20));
        t.setGravity(Gravity.CENTER);
        return t;
    }
    TextView avatar(String name) {
        String initials = initials(name);
        TextView t = tv(initials, 13, PRIMARY, headBold);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(TONAL, 0, 40));
        t.setContentDescription(name);
        return t;
    }
    static String initials(String name) {
        if (name == null) return "";
        String[] parts = name.trim().split("\\s+");
        StringBuilder s = new StringBuilder();
        for (String p : parts) if (!p.isEmpty() && s.length() < 2) s.append(Character.toUpperCase(p.charAt(0)));
        return s.toString();
    }
    ImageView icon(int drawable, int color, int sizeDp) {
        ImageView v = new ImageView(a);
        v.setImageResource(drawable);
        v.setColorFilter(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    // ---------- buttons ----------
    Button styled(Button b, int style) {
        b.setAllCaps(false);
        b.setTypeface(style == LINK ? head : head);
        b.setTextSize(style == LINK ? 14 : 15);
        b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setMinWidth(0); b.setMinimumWidth(0);
        b.setStateListAnimator(null);
        b.setElevation(0);
        int fill, stroke = 0, text;
        switch (style) {
            case TONAL_BTN: fill = TONAL; text = PRIMARY; break;
            case OUTLINE: fill = WHITE; stroke = BORDER; text = INK; break;
            case AMBER_BTN: fill = AMBER; text = WHITE; break;
            case DANGER_OUTLINE: fill = WHITE; stroke = DANGER; text = DANGER; break;
            case LINK: fill = Color.TRANSPARENT; text = PRIMARY; break;
            default: fill = PRIMARY; text = WHITE; break;
        }
        b.setBackground(shape(fill, stroke, 14));
        b.setTextColor(text);
        b.setPadding(dp(14), dp(8), dp(14), dp(8));
        return b;
    }
    void setEnabledLook(Button b, boolean enabled, int style) {
        b.setEnabled(enabled);
        if (!enabled) { b.setBackground(shape(BORDER, 0, 14)); b.setTextColor(DISABLED_TEXT); }
        else styled(b, style);
    }

    // ---------- inputs ----------
    EditText styleInput(EditText e) {
        e.setTypeface(body); e.setTextSize(15); e.setTextColor(INK); e.setHintTextColor(0xFF94A3B8);
        e.setBackground(shape(WHITE, 0xFFCCD6E0, 12));
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setMinHeight(dp(48));
        return e;
    }
    void flagInvalid(EditText e, boolean invalid) { e.setBackground(shape(WHITE, invalid ? DANGER : 0xFFCCD6E0, 12)); }

    // ---------- chips and segmented controls ----------
    class Chips {
        final LinearLayout view; final List<TextView> items = new ArrayList<>(); int selected;
        private final IntConsumer onSelect;
        Chips(LinearLayout v, int selected, IntConsumer onSelect) { view = v; this.selected = selected; this.onSelect = onSelect; }
        void select(int index, boolean notify) {
            selected = index;
            for (int i = 0; i < items.size(); i++) paint(items.get(i), i == selected);
            if (notify && onSelect != null) onSelect.accept(index);
        }
        void label(int index, String value) { if (index >= 0 && index < items.size()) items.get(index).setText(value); }
    }
    private void paint(TextView chip, boolean on) {
        chip.setBackground(shape(on ? PRIMARY : WHITE, on ? PRIMARY : BORDER, 40));
        chip.setTextColor(on ? WHITE : INK);
        chip.setSelected(on);
    }
    Chips chips(ViewGroup parent, String[] labels, int selected, IntConsumer onSelect) {
        HorizontalScrollView scroller = new HorizontalScrollView(a);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        scroller.addView(line);
        parent.addView(scroller, lp(-1, -2, 0, 0, 0, 8));
        Chips c = new Chips(line, selected, onSelect);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = tv(labels[i], 13, INK, head);
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(40));
            chip.setClickable(true);
            chip.setOnClickListener(v -> c.select(index, true));
            line.addView(chip, lp(-2, -2, 0, 0, 8, 0));
            c.items.add(chip);
        }
        c.select(selected, false);
        return c;
    }
    /** Chips that fill the width evenly (quick picks such as 5 / 10 / 15 / 20). */
    Chips quickPicks(ViewGroup parent, String[] labels, int selected, IntConsumer onSelect) {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(line, lp(-1, -2, 0, 8, 0, 4));
        Chips c = new Chips(line, selected, onSelect);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = tv(labels[i], 14, INK, head);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setClickable(true);
            chip.setOnClickListener(v -> c.select(index, true));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            p.setMargins(dp(i == 0 ? 0 : 4), 0, dp(i == labels.length - 1 ? 0 : 4), 0);
            line.addView(chip, p);
            c.items.add(chip);
        }
        c.select(selected, false);
        return c;
    }
    Chips segmented(ViewGroup parent, String[] labels, int selected, IntConsumer onSelect) {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setBackground(shape(GREY_BG, 0, 12));
        line.setPadding(dp(3), dp(3), dp(3), dp(3));
        parent.addView(line, lp(-1, -2, 0, 0, 0, 8));
        Chips c = new Chips(line, selected, onSelect) {
            @Override void select(int index, boolean notify) {
                selected = index;
                for (int i = 0; i < items.size(); i++) {
                    TextView t = items.get(i); boolean on = i == index;
                    t.setBackground(on ? shape(WHITE, BORDER, 10) : shape(Color.TRANSPARENT, 0, 10));
                    t.setTextColor(on ? PRIMARY : MUTED); t.setSelected(on);
                }
                if (notify && onSelect != null) onSelect.accept(index);
            }
        };
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView t = tv(labels[i], 13, MUTED, head);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(dp(40));
            t.setClickable(true);
            t.setOnClickListener(v -> c.select(index, true));
            line.addView(t, new LinearLayout.LayoutParams(0, dp(40), 1));
            c.items.add(t);
        }
        c.select(selected, false);
        return c;
    }

    // ---------- stepper ----------
    /** Large quantity with minus/plus controls. The EditText stays the single source of truth. */
    void stepper(ViewGroup parent, EditText value, String unit) {
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER);
        TextView minus = roundButton("−", "Decrease");
        TextView plus = roundButton("+", "Increase");
        value.setBackground(null);
        value.setGravity(Gravity.CENTER);
        value.setTypeface(headBold);
        value.setTextSize(36);
        value.setTextColor(INK);
        value.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        value.setPadding(0, 0, 0, 0);
        line.addView(minus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        line.addView(value, new LinearLayout.LayoutParams(dp(110), -2));
        line.addView(plus, new LinearLayout.LayoutParams(dp(48), dp(48)));
        parent.addView(line, lp(-1, -2, 0, 4, 0, 0));
        TextView u = tv(unit, 12, MUTED, body);
        u.setGravity(Gravity.CENTER);
        parent.addView(u, lp(-1, -2, 0, 0, 0, 4));
        minus.setOnClickListener(v -> value.setText(Integer.toString(Math.max(0, wholePart(value, false) - 1))));
        plus.setOnClickListener(v -> value.setText(Integer.toString(wholePart(value, true) + 1)));
    }
    private int wholePart(EditText value, boolean up) {
        try {
            double d = Double.parseDouble(value.getText().toString().trim());
            return (int) (up ? Math.floor(d) : Math.ceil(d));
        } catch (NumberFormatException e) { return 0; }
    }
    private TextView roundButton(String symbol, String description) {
        TextView t = tv(symbol, 22, PRIMARY, headBold);
        t.setGravity(Gravity.CENTER);
        t.setBackground(shape(TONAL, 0, 40));
        t.setClickable(true);
        t.setContentDescription(description);
        return t;
    }

    // ---------- order progress ----------
    static final String[] STAGES_COMPACT = {"Ordered", "Pickup", "Picked up", "Refill", "Ready", "Delivered"};
    static final String[] STAGES_FULL = {"Ordered", "For pickup", "Picked up", "Refilling", "Ready", "Delivered"};
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
    void progress(ViewGroup parent, String status, String[] labels) {
        int stage = stageIndex(status);
        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(line, lp(-1, -2, 0, 4, 0, 4));
        for (int i = 0; i < labels.length; i++) {
            LinearLayout cell = new LinearLayout(a);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            View bar = new View(a);
            boolean reached = stage == 5 || i <= stage;
            bar.setBackground(shape(reached ? PRIMARY : GREY_BG, 0, 3));
            cell.addView(bar, lp(-1, 5, 0, 0, 0, 0));
            TextView t = tv(labels[i], 10, i == stage ? PRIMARY : MUTED, i == stage ? bodySemi : body);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(t, lp(-1, -2, 0, 4, 0, 0));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.setMargins(dp(i == 0 ? 0 : 2), 0, dp(i == labels.length - 1 ? 0 : 2), 0);
            line.addView(cell, p);
        }
        line.setContentDescription("Progress: " + Rules.label(status));
    }

    // ---------- status presentation shared by lists ----------
    /** Pill for the order status; undelivered orders show how many gallons are left. */
    TextView statusPill(String status, int pendingGallons) {
        switch (status == null ? "" : status) {
            case "undelivered": return pill(pendingGallons > 0 ? pendingGallons + " left" : "Undelivered", AMBER_BG, AMBER);
            case "ready_for_delivery": return pill("Ready", GREEN_BG, GREEN);
            case "for_pickup": return pill("To pick up", TONAL, PRIMARY);
            case "delivered": return pill("Delivered", GREEN_BG, GREEN);
            case "picked_up": return pill("Picked up", TONAL, PRIMARY);
            case "refilling": return pill("Refilling", TONAL, PRIMARY);
            case "rescheduled": return pill("Rescheduled", AMBER_BG, AMBER);
            default: return pill(Rules.label(status), TONAL, PRIMARY);
        }
    }
    TextView paymentPill(String paymentStatus) {
        switch (paymentStatus == null ? "" : paymentStatus) {
            case "paid": return pill("Paid", GREEN_BG, GREEN);
            case "partially_paid": return pill("Partially paid", AMBER_BG, AMBER);
            default: return pill("Unpaid", DANGER_BG, DANGER);
        }
    }
}
