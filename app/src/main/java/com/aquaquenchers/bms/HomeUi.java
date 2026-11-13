package com.aquaquenchers.bms;

import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Staff "Today" and Owner "Dashboard" (Figma S03 and O2). Data and rules come from the backend queries. */
final class HomeUi {
    private final MainActivity a;
    private final Ui ui;
    HomeUi(MainActivity activity) { a = activity; ui = activity.ui(); }

    void show() { if (a.isOwner()) dashboard(); else today(); }

    // ------------------------------------------------------------------ Staff: Today
    private void today() {
        LinearLayout p = a.page("Home");
        a.query("orders", a.json(), r -> renderToday(p, items(r)));
    }
    private static JSONArray items(JSONObject r) { JSONArray x = r.optJSONArray("items"); return x == null ? new JSONArray() : x; }

    private void renderToday(LinearLayout p, JSONArray all) {
        List<JSONObject> open = new ArrayList<>();
        int pickup = 0, refilling = 0, deliver = 0, todays = 0; long collect = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i); if (o == null) continue;
            String s = o.optString("status");
            if (a.today().equals(o.optString("business_date"))) todays++;
            if (OperationsUi.group(s) == 1) pickup++; else if (OperationsUi.group(s) == 2) refilling++; else if (OperationsUi.group(s) == 3) deliver++;
            if (!"delivered".equals(s)) open.add(o);
            collect += Math.max(0, OperationsUi.balance(o));
        }
        String first = a.recorder().trim().isEmpty() ? "" : a.recorder().trim().split("\\s+")[0];
        int hour = LocalTime.now(Rules.MANILA).getHour();
        String hello = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        TextView greet = ui.tv(first.isEmpty() ? hello : hello + ", " + first, 20, Ui.INK, ui.headBold);
        p.addView(greet, ui.lp(-1, -2, 0, 0, 0, 2));
        ui.muted(p, LocalDate.now(Rules.MANILA).format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)) + " · " + todays + (todays == 1 ? " order today" : " orders today"));
        open.sort(Comparator.comparing((JSONObject o) -> o.optString("business_date")).thenComparing(o -> o.optString("reference")));

        // Next job with its action
        LinearLayout next = new LinearLayout(a);
        next.setOrientation(LinearLayout.VERTICAL);
        next.setBackground(ui.shape(Ui.PRIMARY, 0, 16));
        next.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(16));
        p.addView(next, ui.lp(-1, -2, 0, 14, 0, 14));
        if (open.isEmpty()) {
            next.addView(ui.tv("ALL CLEAR", 11, 0xCCFFFFFF, ui.head));
            next.addView(ui.tv("Nothing waiting right now", 20, Ui.WHITE, ui.headBold), ui.lp(-1, -2, 0, 4, 0, 4));
            next.addView(ui.tv("Create an order or record a walk-in sale.", 13, 0xE6FFFFFF, ui.body));
        } else {
            JSONObject job = open.get(0);
            String status = job.optString("status");
            int g = OperationsUi.group(status);
            TextView kicker = ui.tv("NEXT UP · " + (g == 3 ? "DELIVERY" : g == 2 ? "REFILLING" : "PICKUP"), 11, 0xCCFFFFFF, ui.head);
            kicker.setLetterSpacing(0.05f);
            next.addView(kicker);
            next.addView(ui.tv(job.optString("customer_name"), 22, Ui.WHITE, ui.headBold), ui.lp(-1, -2, 0, 4, 0, 4));
            int gallons = g == 3 ? OperationsUi.pending(job) : job.optInt("quantity");
            String what = g == 3 ? gallons + " gallons to deliver" : g == 2 ? gallons + " gallons · " + Rules.label(status) : gallons + " gallons to collect";
            String address = job.optString("address");
            next.addView(ui.tv(address.isEmpty() ? what : what + " · " + address, 13, 0xE6FFFFFF, ui.body), ui.lp(-1, -2, 0, 0, 0, 12));
            LinearLayout buttons = ui.row(next);
            Button call = a.button(buttons, "Call", Ui.TONAL_BTN, () -> a.operations().call(job));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ui.dp(92), -2); cp.rightMargin = ui.dp(8); call.setLayoutParams(cp);
            call.setBackground(ui.shape(Ui.WHITE, 0, 14));
            Button act = a.button(buttons, OperationsUi.nextLabel(status), Ui.TONAL_BTN, () -> a.operations().nextStep(job));
            act.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1)); act.setBackground(ui.shape(Ui.WHITE, 0, 14));
        }

        ui.heading(p, "Today’s work");
        LinearLayout row1 = tileRow(p), row2 = tileRow(p);
        tile(row1, Integer.toString(pickup), "To pick up", Ui.AMBER_BG, Ui.AMBER, () -> a.operations().ordersFiltered("Pickup"));
        tile(row1, Integer.toString(refilling), "Refilling", Ui.TONAL, Ui.PRIMARY, () -> a.operations().ordersFiltered("Refilling"));
        tile(row2, Integer.toString(deliver), "To deliver", Ui.GREEN_BG, Ui.GREEN, () -> a.operations().ordersFiltered("Deliver"));
        tile(row2, a.money(collect), "To collect", Ui.GREY_BG, Ui.INK, () -> a.operations().payments());

        // Longest-waiting pending gallons
        JSONObject longest = null;
        for (JSONObject o : open) {
            String s = o.optString("status");
            if (("undelivered".equals(s) || "ready_for_delivery".equals(s)) && OperationsUi.pending(o) > 0) { longest = o; break; }
        }
        LinearLayout wait = ui.card(p);
        wait.setClickable(true);
        wait.setOnClickListener(v -> a.operations().pending());
        wait.setContentDescription("Pending gallons");
        ui.muted(wait, "Waiting longest");
        if (longest == null) {
            ui.text(wait, "No pending gallons", 15, Ui.INK, ui.bodySemi);
        } else {
            LinearLayout r = ui.row(wait);
            TextView who = ui.tv(longest.optString("customer_name") + " · " + OperationsUi.pending(longest) + " gallons left", 15, Ui.INK, ui.bodySemi);
            r.addView(who, new LinearLayout.LayoutParams(0, -2, 1));
            long days = 0;
            try { days = ChronoUnit.DAYS.between(Rules.date(longest.optString("business_date")), Rules.date(a.today())); } catch (Exception ignored) { }
            r.addView(ui.pill(Math.max(0, days) == 1 ? "1 day" : Math.max(0, days) + " days", Ui.AMBER_BG, Ui.AMBER));
        }
        LinearLayout actions = ui.row(p);
        Button newOrder = a.button(actions, "New order", Ui.FILLED, () -> a.operations().newOrder(null));
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, -2, 1); l1.rightMargin = ui.dp(6); newOrder.setLayoutParams(l1);
        Button walkIn = a.button(actions, "Walk-in sale", Ui.TONAL_BTN, () -> a.operations().pos());
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, -2, 1); l2.leftMargin = ui.dp(6); walkIn.setLayoutParams(l2);
    }
    private LinearLayout tileRow(LinearLayout p) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        p.addView(r, ui.lp(-1, -2, 0, 0, 0, 10));
        return r;
    }
    private void tile(LinearLayout row, String value, String label, int bg, int fg, Runnable open) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(ui.shape(bg, 0, 16));
        t.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        t.setClickable(true);
        t.setContentDescription(label + " " + value);
        t.setOnClickListener(v -> open.run());
        t.addView(ui.tv(value, 24, fg, ui.headBold));
        t.addView(ui.tv(label, 13, Ui.INK, ui.bodySemi));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(row.getChildCount() == 0 ? 0 : ui.dp(5), 0, row.getChildCount() == 0 ? ui.dp(5) : 0, 0);
        row.addView(t, lp);
    }

    // ------------------------------------------------------------------ Owner: Dashboard
    private static final String[] PERIODS = {"Today", "This week", "This month", "Custom"};
    private JSONObject lastSummary;

    private void dashboard() {
        LinearLayout p = a.page("Dashboard");
        String saved = a.choice("period");
        int[] selected = {saved.isEmpty() ? 0 : Math.max(0, Math.min(3, Integer.parseInt(saved)))};
        TextView range = ui.tv("", 13, Ui.MUTED, ui.body);
        TextView updated = ui.pill("", Ui.GREY_BG, Ui.MUTED);
        LinearLayout body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout customBox = new LinearLayout(a);
        customBox.setOrientation(LinearLayout.VERTICAL);
        EditText start = a.input(customBox, "Start date", a.today(), InputType.TYPE_CLASS_DATETIME);
        EditText end = a.input(customBox, "End date", a.today(), InputType.TYPE_CLASS_DATETIME);
        Runnable[] loadRef = new Runnable[1];

        Ui.Chips chips = ui.chips(p, PERIODS, selected[0], i -> {
            selected[0] = i; a.rememberChoice("period", Integer.toString(i));
            customBox.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
            if (i != 3) loadRef[0].run();
        });
        LinearLayout rangeRow = ui.row(p);
        rangeRow.addView(range, new LinearLayout.LayoutParams(0, -2, 1));
        rangeRow.addView(updated);
        updated.setVisibility(View.GONE);
        a.button(customBox, "Apply period", Ui.TONAL_BTN, () -> loadRef[0].run());
        customBox.setVisibility(selected[0] == 3 ? View.VISIBLE : View.GONE);
        p.addView(customBox, new LinearLayout.LayoutParams(-1, -2));
        p.addView(body, ui.lp(-1, -2, 0, 10, 0, 0));

        loadRef[0] = () -> {
            LocalDate now = LocalDate.parse(a.today());
            String from, to;
            if (selected[0] == 0) { from = to = now.toString(); }
            else if (selected[0] == 1) { from = now.minusDays(now.getDayOfWeek().getValue() - 1L).toString(); to = now.toString(); }
            else if (selected[0] == 2) { from = now.withDayOfMonth(1).toString(); to = now.toString(); }
            else { from = start.getText().toString().trim(); to = end.getText().toString().trim(); }
            try { Rules.period(from, to); } catch (IllegalArgumentException e) { a.error(e.getMessage()); return; }
            a.clearMessage();
            range.setText(rangeLabel(from, to));
            a.query("dashboard", a.json("start_date", from, "end_date", to), r -> {
                lastSummary = r;
                body.removeAllViews();
                JSONObject stock = r.optJSONObject("inventory");
                String stamp = stock == null ? "" : stock.optString("updated_at");
                if (!stamp.isEmpty()) { updated.setText("Updated " + Rules.shortStamp(stamp)); updated.setVisibility(View.VISIBLE); }
                renderDashboard(body, r);
            });
        };
        loadRef[0].run();
    }
    static String rangeLabel(String from, String to) {
        DateTimeFormatter day = DateTimeFormatter.ofPattern("d", Locale.ENGLISH), full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH), month = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
        try {
            LocalDate f = LocalDate.parse(from), t = LocalDate.parse(to);
            if (f.equals(t)) return t.format(full);
            if (f.getYear() == t.getYear() && f.getMonth() == t.getMonth()) return f.format(day) + "–" + t.format(full);
            return f.format(month) + " – " + t.format(full);
        } catch (Exception e) { return from + " to " + to; }
    }

    private void renderDashboard(LinearLayout body, JSONObject r) {
        boolean wide = a.tablet();
        LinearLayout holder = new LinearLayout(a);
        holder.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.addView(holder, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout left = new LinearLayout(a), right = new LinearLayout(a);
        left.setOrientation(LinearLayout.VERTICAL); right.setOrientation(LinearLayout.VERTICAL);
        if (wide) {
            holder.addView(left, new LinearLayout.LayoutParams(0, -2, 1.7f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, -2, 1f); rp.leftMargin = ui.dp(16);
            holder.addView(right, rp);
        } else { holder.addView(left, new LinearLayout.LayoutParams(-1, -2)); holder.addView(right, new LinearLayout.LayoutParams(-1, -2)); }

        LinearLayout k1 = new LinearLayout(a), k2 = new LinearLayout(a);
        left.addView(k1, ui.lp(-1, -2, 0, 0, 0, 10)); left.addView(k2, ui.lp(-1, -2, 0, 0, 0, 10));
        kpi(k1, "Revenue", r.optLong("revenue_centavos"), "Orders and sales by order date", false, true);
        kpi(k1, "Payments received", r.optLong("payments_received_centavos"), "Cash by payment date", false, false);
        kpi(k2, "Expenses", r.optLong("expenses_centavos"), "By expense date", false, true);
        kpi(k2, "Net income", r.optLong("net_income_centavos"), "Payments received − expenses", true, false);

        LinearLayout recent = ui.card(left);
        LinearLayout head = ui.row(recent);
        head.addView(ui.tv("Recent orders", 17, Ui.INK, ui.headBold), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = ui.tv("See all ›", 13, Ui.PRIMARY, ui.head);
        all.setClickable(true); all.setOnClickListener(v -> a.operations().orders());
        head.addView(all);
        LinearLayout rows = new LinearLayout(a); rows.setOrientation(LinearLayout.VERTICAL);
        recent.addView(rows, ui.lp(-1, -2, 0, 6, 0, 0));
        a.query("orders", a.json(), orders -> {
            JSONArray list = items(orders);
            if (list.length() == 0) ui.muted(rows, "No orders found.");
            for (int i = 0; i < Math.min(5, list.length()); i++) {
                JSONObject o = list.optJSONObject(i); if (o == null) continue;
                LinearLayout line = ui.row(rows);
                line.setPadding(0, ui.dp(9), 0, ui.dp(9));
                line.setClickable(true); line.setOnClickListener(v -> a.operations().order(o.optString("id")));
                line.setContentDescription(o.optString("reference") + " " + o.optString("customer_name"));
                LinearLayout names = new LinearLayout(a); names.setOrientation(LinearLayout.VERTICAL);
                names.addView(ui.tv(o.optString("customer_name"), 15, Ui.INK, ui.headBold));
                names.addView(ui.tv(o.optString("reference"), 12, Ui.MUTED, ui.body));
                line.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                line.addView(ui.statusPill(o.optString("status"), OperationsUi.pending(o)), ui.lp(-2, -2, 0, 0, 8, 0));
                long due = OperationsUi.balance(o);
                TextView amount = ui.tv(due > 0 ? a.money(due) + " due" : "Paid", 13, due > 0 ? Ui.AMBER : Ui.GREEN, ui.bodySemi);
                line.addView(amount);
            }
        });

        JSONObject stock = r.optJSONObject("inventory");
        LinearLayout gallons = ui.card(right);
        gallons.addView(ui.tv("Gallon stock", 17, Ui.INK, ui.headBold), ui.lp(-1, -2, 0, 0, 0, 8));
        LinearLayout s1 = new LinearLayout(a), s2 = new LinearLayout(a);
        gallons.addView(s1, ui.lp(-1, -2, 0, 0, 0, 8)); gallons.addView(s2, ui.lp(-1, -2, 0, 0, 0, 8));
        stockTile(s1, stock == null ? 0 : stock.optLong("filled_available"), "Filled available", Ui.GREEN_BG, Ui.GREEN, true);
        stockTile(s1, stock == null ? 0 : stock.optLong("reserved_filled"), "Reserved filled", Ui.TONAL, Ui.PRIMARY, false);
        stockTile(s2, stock == null ? 0 : stock.optLong("empty_quantity"), "Empty", Ui.GREY_BG, Ui.INK, true);
        stockTile(s2, stock == null ? 0 : stock.optLong("pending_quantity"), "Pending delivery", Ui.AMBER_BG, Ui.AMBER, false);
        TextView inv = ui.tv("Inventory ›", 13, Ui.PRIMARY, ui.head);
        inv.setClickable(true); inv.setOnClickListener(v -> a.ownerUi().inventory());
        gallons.addView(inv);

        LinearLayout owed = ui.card(right);
        ui.muted(owed, "Outstanding balances");
        owed.addView(ui.tv(a.money(r.optLong("outstanding_centavos")), 26, Ui.AMBER, ui.headBold));
        TextView balances = ui.tv("Customer balances ›", 13, Ui.PRIMARY, ui.head);
        balances.setClickable(true); balances.setOnClickListener(v -> a.operations().customers());
        owed.addView(balances, ui.lp(-2, -2, 0, 4, 0, 0));

        a.button(right, "Create order", Ui.FILLED, () -> a.operations().newOrder(null));
        a.button(right, "Walk-in sale", Ui.TONAL_BTN, () -> a.operations().pos());
        a.button(right, "Reports", Ui.OUTLINE, () -> a.ownerUi().reports());
    }
    private void kpi(LinearLayout row, String label, long cents, String caption, boolean dark, boolean first) {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(ui.shape(dark ? Ui.PRIMARY : Ui.WHITE, dark ? 0 : Ui.BORDER, 14));
        c.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        c.addView(ui.tv(label, 13, dark ? Ui.WHITE : Ui.MUTED, ui.body));
        c.addView(ui.tv(a.money(cents), 24, dark ? Ui.WHITE : Ui.INK, ui.headBold), ui.lp(-1, -2, 0, 2, 0, 2));
        c.addView(ui.tv(caption, 12, dark ? 0xDDFFFFFF : Ui.MUTED, ui.body));
        c.setContentDescription(label + " " + a.money(cents));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(first ? 0 : ui.dp(5), 0, first ? ui.dp(5) : 0, 0);
        row.addView(c, lp);
    }
    private void stockTile(LinearLayout row, long value, String label, int bg, int fg, boolean first) {
        LinearLayout t = new LinearLayout(a);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(ui.shape(bg, 0, 12));
        t.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        t.addView(ui.tv(Long.toString(value), 22, fg, ui.headBold));
        t.addView(ui.tv(label, 12, Ui.INK, ui.bodySemi));
        t.setContentDescription(label + " " + value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(first ? 0 : ui.dp(4), 0, first ? ui.dp(4) : 0, 0);
        row.addView(t, lp);
    }
}
