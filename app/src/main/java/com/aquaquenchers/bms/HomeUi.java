package com.aquaquenchers.bms;

import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
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

/** Staff "Today" (Figma S03) and Owner "Dashboard" (Figma O2, O2b, O2c). */
final class HomeUi {
    private final MainActivity a;
    private final Ui ui;
    HomeUi(MainActivity activity) { a = activity; ui = activity.ui(); }

    void show() { if (a.wide() && a.isOwner()) dashboard(); else if (a.isOwner()) phoneDashboard(); else today(); }
    private static JSONArray items(JSONObject r) { JSONArray x = r.optJSONArray("items"); return x == null ? new JSONArray() : x; }

    // ------------------------------------------------------------------ Staff: Today (S03)
    private void today() {
        LinearLayout p = a.page("Home");
        a.query("orders", a.json(), r -> renderToday(p, items(r)));
    }
    private void renderToday(LinearLayout p, JSONArray all) {
        List<JSONObject> open = new ArrayList<>();
        int pickup = 0, refilling = 0, deliver = 0, todays = 0; long collect = 0;
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i); if (o == null) continue;
            String s = o.optString("status");
            if (a.today().equals(o.optString("business_date"))) todays++;
            int g = OperationsUi.group(s);
            if (g == 1) pickup++; else if (g == 2) refilling++; else if (g == 3) deliver++;
            if (!OperationsUi.done(s)) open.add(o);
            collect += Math.max(0, OperationsUi.balance(o));
        }
        String first = a.recorder().trim().isEmpty() ? "" : a.recorder().trim().split("\\s+")[0];
        int hour = LocalTime.now(Rules.MANILA).getHour();
        String hello = hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
        LinearLayout greet = ui.V(p, 2);
        ui.text(greet, first.isEmpty() ? hello : hello + ", " + first, 24, 36, Ui.INK, ui.p6);
        ui.text(greet, LocalDate.now(Rules.MANILA).format(DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.ENGLISH)) + " · " + todays + (todays == 1 ? " order today" : " orders today"), 14, 19, Ui.MUTED, ui.o4);
        open.sort(Comparator.comparing((JSONObject o) -> o.optString("business_date")).thenComparing(o -> o.optString("reference")));

        // Next up
        LinearLayout next = ui.V(p, 10); ui.bg(next, Ui.PRIMARY, 0, 16); ui.pad(next, 18, 18);
        if (open.isEmpty()) {
            ui.text(next, "ALL CLEAR", 12, 18, 0xFFC7E5FF, ui.p5);
            ui.text(next, "Nothing waiting right now", 22, 33, Ui.WHITE, ui.p6);
            ui.text(next, "Create an order or record a walk-in.", 14, 19, 0xFFE0F2FF, ui.o4);
        } else {
            JSONObject job = open.get(0);
            String status = job.optString("status");
            int g = OperationsUi.group(status);
            ui.text(next, "NEXT UP · " + (g == 3 ? "DELIVERY" : g == 2 ? "REFILLING" : "PICKUP"), 12, 18, 0xFFC7E5FF, ui.p5);
            ui.text(next, job.optString("customer_name"), 22, 33, Ui.WHITE, ui.p6);
            int gallons = g == 3 ? OperationsUi.pending(job) : job.optInt("quantity");
            String what = g == 3 ? gallons + " gallons to deliver" : g == 2 ? gallons + " gallons · " + Rules.label(status) : gallons + " gallons to collect";
            String address = job.optString("address");
            ui.text(next, address.isEmpty() ? what : what + " · " + address, 14, 19, 0xFFE0F2FF, ui.o4);
            LinearLayout buttons = ui.H(next, 10);
            TextView call = ui.button("Call", Ui.FILLED, 48, 14, 16, () -> a.operations().call(job));
            call.setBackground(ui.shape(Ui.WHITE, 0, 14)); call.setTextColor(Ui.PRIMARY); ui.hug(buttons, call);
            TextView act = ui.button(OperationsUi.nextLabel(job), Ui.FILLED, 48, 14, 16, () -> a.operations().nextStep(job));
            act.setBackground(ui.shape(Ui.WHITE, 0, 14)); act.setTextColor(Ui.PRIMARY); ui.grow(buttons, act);
        }

        ui.text(p, "Today’s work", 17, 26, Ui.INK, ui.p6);
        LinearLayout grid = ui.V(p, 12);
        LinearLayout row1 = ui.H(grid, 12), row2 = ui.H(grid, 12);
        tile(row1, Integer.toString(pickup), "To pick up", Ui.AMBER_BG, Ui.AMBER, () -> a.operations().ordersFiltered("Pickup"));
        tile(row1, Integer.toString(refilling), "Refilling", Ui.TONAL, Ui.PRIMARY, () -> a.operations().ordersFiltered("Refilling"));
        tile(row2, Integer.toString(deliver), "To deliver", Ui.GREEN_BG, Ui.GREEN, () -> a.operations().ordersFiltered("Deliver"));
        tile(row2, peso(collect), "To collect", Ui.GREY_BG, Ui.INK, () -> a.operations().payments());

        JSONObject longest = null;
        for (JSONObject o : open) {
            String s = o.optString("status");
            if (("undelivered".equals(s) || "ready_for_delivery".equals(s)) && !OperationsUi.claimAtCounter(o) && OperationsUi.pending(o) > 0) { longest = o; break; }
        }
        LinearLayout wait = ui.card(p, 6, 16);
        wait.setClickable(true); wait.setOnClickListener(v -> a.operations().pending()); wait.setContentDescription("Pending gallons");
        ui.label(wait, "Waiting longest");
        LinearLayout r = ui.H(wait, 8);
        if (longest == null) ui.grow(r, ui.tv("No pending gallons", 15, 22, Ui.INK, ui.p5));
        else {
            ui.grow(r, ui.single(ui.tv(longest.optString("customer_name") + " · " + OperationsUi.pending(longest) + " gallons left", 15, 22, Ui.INK, ui.p5)));
            long days = 0;
            try { days = ChronoUnit.DAYS.between(Rules.date(longest.optString("business_date")), Rules.date(a.today())); } catch (Exception ignored) { }
            days = Math.max(0, days);
            ui.hug(r, ui.chip(days == 0 ? "Today" : days == 1 ? "1 day" : days + " days", Ui.AMBER_BG, Ui.AMBER));
        }
        LinearLayout actions = ui.H(p, 12);
        ui.grow(actions, ui.dockButton("New order", Ui.FILLED, () -> a.operations().newOrder(null)));
        ui.grow(actions, ui.dockButton("Walk-in", Ui.TONAL_BTN, () -> a.operations().pos()));
    }
    /** Whole pesos for the "To collect" tile (Figma "₱150"), otherwise the full amount. */
    private String peso(long cents) { return cents % 100 == 0 ? "₱" + String.format(Locale.ENGLISH, "%,d", cents / 100) : a.money(cents); }
    private void tile(LinearLayout row, String value, String label, int bg, int fg, Runnable open) {
        LinearLayout t = ui.vbox(2); ui.bg(t, bg, 0, 16); ui.pad(t, 16, 16);
        t.setClickable(true); t.setContentDescription(label + " " + value); t.setOnClickListener(v -> open.run());
        ui.text(t, value, 28, 42, fg, ui.p6);
        ui.text(t, label, 13, 18, Ui.INK, ui.o6);
        ui.grow(row, t);
    }

    // ------------------------------------------------------------------ Owner: Dashboard (O2)
    private static final String[] PERIODS = {"Today", "This week", "This month", "Custom"};
    private String customFrom, customTo;

    private void dashboard() {
        LinearLayout p = a.page("Dashboard");
        String saved = a.choice("period");
        int[] selected = {saved.isEmpty() ? 1 : Math.max(0, Math.min(3, Integer.parseInt(saved)))};
        TextView updated = ui.chip("", Ui.GREY_BG, Ui.MUTED); updated.setVisibility(View.GONE);
        ui.hug(a.titleActions(), updated);
        LinearLayout periodRow = ui.H(p, 8);
        TextView range = ui.tv("", 14, 21, Ui.MUTED, ui.p5);
        LinearLayout bodyHost = ui.V(p, 0);
        Runnable[] load = new Runnable[1];
        Ui.Tabs[] tabs = new Ui.Tabs[1];
        tabs[0] = ui.tabs(periodRow, PERIODS, selected[0], false, true, 8, i -> {
            if (i == 3) { tabs[0].select(selected[0], false); customDialog(() -> { selected[0] = 3; a.rememberChoice("period", "3"); tabs[0].select(3, false); load[0].run(); }); return; }
            selected[0] = i; a.rememberChoice("period", Integer.toString(i)); load[0].run();
        });
        ui.hug(periodRow, range);
        load[0] = () -> {
            LocalDate now = LocalDate.parse(a.today());
            String from, to;
            if (selected[0] == 0) { from = to = now.toString(); }
            else if (selected[0] == 1) { from = now.minusDays(now.getDayOfWeek().getValue() - 1L).toString(); to = now.toString(); }
            else if (selected[0] == 2) { from = now.withDayOfMonth(1).toString(); to = now.toString(); }
            else { from = customFrom == null ? now.toString() : customFrom; to = customTo == null ? now.toString() : customTo; }
            a.query("dashboard", a.json("start_date", from, "end_date", to), r -> {
                range.setText(rangeLabel(from, to));
                bodyHost.removeAllViews();
                JSONObject stock = r.optJSONObject("inventory");
                String stamp = stock == null ? "" : stock.optString("updated_at");
                if (!stamp.isEmpty()) { updated.setText("Updated " + Rules.shortStamp(stamp)); updated.setVisibility(View.VISIBLE); }
                renderDashboard(bodyHost, r);
            });
        };
        load[0].run();
    }
    /** Figma O2b/O2c: the custom period modal. An invalid range keeps the previous figures. */
    private void customDialog(Runnable applied) {
        Dialog[] d = new Dialog[1];
        LinearLayout card = a.modal(460, d);
        ui.text(card, "Select dashboard period", 22, 33, Ui.INK, ui.p6);
        ui.text(card, "Choose the start and end dates for the dashboard figures.", 14, 19, Ui.MUTED, ui.o4);
        EditText start = a.operations().date(card, "Start date", customFrom == null ? a.today() : customFrom);
        EditText end = a.operations().date(card, "End date", customTo == null ? a.today() : customTo);
        LinearLayout row = ui.H(card, 10); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        TextView cancel = ui.button("Cancel", Ui.OUTLINE, 46, 12, 15, () -> d[0].dismiss()); ui.hug(row, cancel);
        TextView apply = ui.button("Apply period", Ui.FILLED, 46, 12, 15, () -> {
            String from = start.getText().toString().trim(), to = end.getText().toString().trim();
            try { Rules.period(from, to); } catch (IllegalArgumentException e) { a.fieldError(end, e.getMessage()); return; }
            customFrom = from; customTo = to; d[0].dismiss(); applied.run();
        });
        ui.hug(row, apply);
        a.showDialog(d[0]);
    }
    // ------------------------------------------------------------------ Owner on a phone: Dashboard in the Staff phone style
    private void phoneDashboard() {
        LinearLayout p = a.page("Dashboard");
        String saved = a.choice("period");
        int[] selected = {saved.isEmpty() ? 1 : Math.max(0, Math.min(3, Integer.parseInt(saved)))};
        LinearLayout head = ui.V(p, 2);
        TextView range = ui.text(head, "", 14, 19, Ui.MUTED, ui.o4);
        TextView updated = ui.text(head, "", 12, 16, Ui.MUTED, ui.o4); updated.setVisibility(View.GONE);
        Runnable[] load = new Runnable[1];
        Ui.Tabs[] tabs = new Ui.Tabs[1];
        tabs[0] = ui.tabs(p, PERIODS, selected[0], false, false, 8, i -> {
            if (i == 3) { tabs[0].select(selected[0], false); customDialog(() -> { selected[0] = 3; a.rememberChoice("period", "3"); tabs[0].select(3, false); load[0].run(); }); return; }
            selected[0] = i; a.rememberChoice("period", Integer.toString(i)); load[0].run();
        });
        LinearLayout host = ui.V(p, 16);
        load[0] = () -> {
            LocalDate now = LocalDate.parse(a.today());
            String from, to;
            if (selected[0] == 0) { from = to = now.toString(); }
            else if (selected[0] == 1) { from = now.minusDays(now.getDayOfWeek().getValue() - 1L).toString(); to = now.toString(); }
            else if (selected[0] == 2) { from = now.withDayOfMonth(1).toString(); to = now.toString(); }
            else { from = customFrom == null ? now.toString() : customFrom; to = customTo == null ? now.toString() : customTo; }
            a.query("dashboard", a.json("start_date", from, "end_date", to), r -> {
                range.setText(rangeLabel(from, to));
                JSONObject stock = r.optJSONObject("inventory");
                String stamp = stock == null ? "" : stock.optString("updated_at");
                if (!stamp.isEmpty()) { updated.setText("Updated " + Rules.shortStamp(stamp)); updated.setVisibility(View.VISIBLE); }
                host.removeAllViews();
                LinearLayout grid = ui.V(host, 12);
                LinearLayout r1 = ui.H(grid, 12), r2 = ui.H(grid, 12);
                phoneKpi(r1, "Revenue", r.optLong("revenue_centavos"), "By order date", false);
                phoneKpi(r1, "Payments received", r.optLong("payments_received_centavos"), "By payment date", false);
                phoneKpi(r2, "Expenses", r.optLong("expenses_centavos"), "By expense date", false);
                phoneKpi(r2, "Net income", r.optLong("net_income_centavos"), "Payments − expenses", true);
                LinearLayout gallons = ui.card(host, 12, 16);
                LinearLayout gh = ui.H(gallons, 8);
                ui.grow(gh, ui.tv("Gallons at the station", 17, 26, Ui.INK, ui.p6));
                ui.hug(gh, ui.link("Gallons at station ›", 14, () -> a.ownerUi().inventory()));
                LinearLayout s1 = ui.H(gallons, 10), s2 = ui.H(gallons, 10);
                stockTile(s1, stock == null ? 0 : stock.optLong("at_station"), "At the station", Ui.TONAL, Ui.PRIMARY);
                stockTile(s1, stock == null ? 0 : stock.optLong("refilling"), "Refilling", Ui.GREY_BG, Ui.INK);
                stockTile(s2, stock == null ? 0 : stock.optLong("pending_quantity"), "To deliver", Ui.AMBER_BG, Ui.AMBER);
                stockTile(s2, stock == null ? 0 : stock.optLong("to_claim"), "To claim", Ui.GREEN_BG, Ui.GREEN);
                LinearLayout owed = ui.card(host, 6, 16); owed.setClickable(true); owed.setOnClickListener(v -> a.operations().payments());
                ui.label(owed, "Outstanding balances");
                LinearLayout or = ui.H(owed, 8);
                ui.grow(or, ui.tv(a.money(r.optLong("outstanding_centavos")), 26, 39, Ui.AMBER, ui.p6));
                ui.hug(or, ui.link("Customer balances ›", 14, () -> a.operations().payments()));
                LinearLayout recent = ui.card(host, 10, 16);
                LinearLayout rh = ui.H(recent, 8);
                ui.grow(rh, ui.tv("Recent orders", 17, 26, Ui.INK, ui.p6));
                ui.hug(rh, ui.link("See all ›", 14, () -> a.operations().orders()));
                LinearLayout rows = ui.V(recent, 4);
                a.query("orders", a.json(), orders -> {
                    JSONArray list = items(orders);
                    if (list.length() == 0) ui.text(rows, "No orders found.", 14, 19, Ui.MUTED, ui.o4);
                    for (int i = 0; i < Math.min(5, list.length()); i++) {
                        JSONObject o = list.optJSONObject(i); if (o == null) continue;
                        LinearLayout line = ui.H(rows, 8); ui.pad(line, 0, 8); line.setClickable(true);
                        line.setOnClickListener(v -> a.operations().order(o.optString("id")));
                        LinearLayout names = ui.vbox(1); ui.grow(line, names);
                        ui.text(names, o.optString("customer_name"), 15, 22, Ui.INK, ui.p5);
                        ui.text(names, o.optString("reference"), 12, 16, Ui.MUTED, ui.o4);
                        LinearLayout end = ui.vbox(4); end.setGravity(Gravity.END); ui.hug(line, end);
                        ui.hug(end, ui.statusChip(o.optString("status"), OperationsUi.pending(o)));
                        long due = OperationsUi.balance(o);
                        ui.hug(end, ui.tv(due > 0 ? a.money(due) + " due" : "Paid", 12, 18, due > 0 ? Ui.AMBER : Ui.GREEN, ui.p5));
                    }
                });
                LinearLayout actions = ui.H(host, 12);
                ui.grow(actions, ui.dockButton("New order", Ui.FILLED, () -> a.operations().newOrder(null)));
                ui.grow(actions, ui.dockButton("Walk-in", Ui.TONAL_BTN, () -> a.operations().pos()));
                ui.fill(host, ui.dockButton("Reports", Ui.OUTLINE, () -> a.ownerUi().reports()));
            });
        };
        load[0].run();
    }
    private void phoneKpi(LinearLayout row, String label, long cents, String caption, boolean dark) {
        LinearLayout c = ui.vbox(2);
        ui.bg(c, dark ? Ui.PRIMARY : Ui.WHITE, dark ? 0 : Ui.BORDER, 16); ui.pad(c, 16, 16);
        ui.text(c, label, 13, 18, dark ? 0xFFD9EDFF : Ui.MUTED, ui.o6);
        ui.single(ui.text(c, a.money(cents), 22, 33, dark ? Ui.WHITE : Ui.INK, ui.p6));
        ui.text(c, caption, 12, 16, dark ? 0xFFD9EDFF : Ui.MUTED, ui.o4);
        c.setContentDescription(label + " " + a.money(cents));
        ui.grow(row, c);
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

    private void renderDashboard(LinearLayout host, JSONObject r) {
        LinearLayout cols = ui.H(host, 16); cols.setGravity(Gravity.TOP);
        LinearLayout left = ui.vbox(14), right = ui.vbox(14);
        cols.addView(left, new LinearLayout.LayoutParams(0, -2, 606));
        cols.addView(right, new LinearLayout.LayoutParams(0, -2, 330));

        LinearLayout kpis = ui.V(left, 12);
        LinearLayout k1 = ui.H(kpis, 12), k2 = ui.H(kpis, 12);
        kpi(k1, "Revenue", r.optLong("revenue_centavos"), "Orders and sales by order date", false);
        kpi(k1, "Payments received", r.optLong("payments_received_centavos"), "Cash by payment date", false);
        kpi(k2, "Expenses", r.optLong("expenses_centavos"), "By expense date", false);
        kpi(k2, "Net income", r.optLong("net_income_centavos"), "Payments received − expenses", true);

        LinearLayout recent = ui.card(left, 10, 18);
        LinearLayout head = ui.H(recent, 0);
        ui.grow(head, ui.tv("Recent orders", 17, 26, Ui.INK, ui.p6));
        ui.hug(head, ui.link("See all ›", 14, () -> a.operations().orders()));
        LinearLayout rows = ui.V(recent, 10);
        a.query("orders", a.json(), orders -> {
            JSONArray list = items(orders);
            if (list.length() == 0) ui.text(rows, "No orders found.", 14, 19, Ui.MUTED, ui.o4);
            for (int i = 0; i < Math.min(5, list.length()); i++) {
                JSONObject o = list.optJSONObject(i); if (o == null) continue;
                LinearLayout line = ui.H(rows, 0); ui.pad(line, 0, 8);
                line.setClickable(true); line.setOnClickListener(v -> a.operations().order(o.optString("id")));
                line.setContentDescription(o.optString("reference") + " " + o.optString("customer_name"));
                LinearLayout names = ui.hbox(14);
                ui.hug(names, ui.tv(o.optString("customer_name"), 15, 22, Ui.INK, ui.p5));
                ui.hug(names, ui.tv(o.optString("reference"), 13, 18, Ui.MUTED, ui.o4));
                ui.grow(line, names);
                LinearLayout end = ui.hbox(14); ui.hug(line, end);
                ui.hug(end, ui.statusChip(o.optString("status"), OperationsUi.pending(o)));
                long due = OperationsUi.balance(o);
                ui.hug(end, ui.tv(due > 0 ? a.money(due) + " due" : "Paid", 13, 20, due > 0 ? Ui.AMBER : Ui.GREEN, ui.p5));
            }
        });

        JSONObject stock = r.optJSONObject("inventory");
        LinearLayout gallons = ui.card(right, 12, 18);
        ui.text(gallons, "Customer gallons at the station", 17, 26, Ui.INK, ui.p6);
        LinearLayout s1 = ui.H(gallons, 10), s2 = ui.H(gallons, 10);
        stockTile(s1, stock == null ? 0 : stock.optLong("at_station"), "At the station", Ui.TONAL, Ui.PRIMARY);
        stockTile(s1, stock == null ? 0 : stock.optLong("refilling"), "Refilling", Ui.GREY_BG, Ui.INK);
        stockTile(s2, stock == null ? 0 : stock.optLong("pending_quantity"), "To deliver", Ui.AMBER_BG, Ui.AMBER);
        stockTile(s2, stock == null ? 0 : stock.optLong("to_claim"), "To claim", Ui.GREEN_BG, Ui.GREEN);
        ui.hug(gallons, ui.link("Gallons at station ›", 14, () -> a.ownerUi().inventory()));

        LinearLayout owed = ui.card(right, 6, 18);
        ui.label(owed, "Outstanding balances");
        ui.text(owed, a.money(r.optLong("outstanding_centavos")), 26, 39, Ui.AMBER, ui.p6);
        ui.hug(owed, ui.link("Customer balances ›", 14, () -> a.operations().payments()));

        ui.fill(right, ui.button("+  Create order", Ui.FILLED, 48, 12, 15, () -> a.operations().newOrder(null)));
        ui.fill(right, ui.button("Walk-in", Ui.TONAL_BTN, 48, 12, 15, () -> a.operations().pos()));
        ui.fill(right, ui.button("Reports", Ui.OUTLINE, 48, 12, 15, () -> a.ownerUi().reports()));
    }
    private void kpi(LinearLayout row, String label, long cents, String caption, boolean dark) {
        LinearLayout c = ui.vbox(4);
        ui.bg(c, dark ? Ui.PRIMARY : Ui.WHITE, dark ? 0 : Ui.BORDER, 16); ui.pad(c, 18, 18);
        ui.text(c, label, 13, 18, dark ? 0xFFD9EDFF : Ui.MUTED, ui.o6);
        ui.text(c, a.money(cents), 28, 42, dark ? Ui.WHITE : Ui.INK, ui.p6);
        ui.text(c, caption, 12, 16, dark ? 0xFFD9EDFF : Ui.MUTED, ui.o4);
        c.setContentDescription(label + " " + a.money(cents));
        ui.grow(row, c);
    }
    private void stockTile(LinearLayout row, long value, String label, int bg, int fg) {
        LinearLayout t = ui.vbox(0); ui.bg(t, bg, 0, 12); ui.pad(t, 12, 12);
        ui.text(t, Long.toString(value), 24, 36, fg, ui.p6);
        ui.text(t, label, 12, 16, Ui.INK, ui.o6);
        t.setContentDescription(label + " " + value);
        ui.grow(row, t);
    }
}
