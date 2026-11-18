package com.aquaquenchers.bms;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Operational screens for both roles. Staff (phone): Figma S04–S16, P1–P7, K1–K4. Owner (tablet): Figma O3–O8.
 * Every successful write comes from the authenticated RPC; rules and messages follow the requirements.
 */
public final class OperationsUi {
    private final MainActivity host;
    private final Ui ui;
    private long generation;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int CASH = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final String[] TYPE_NAMES = {"Household", "Dealer", "Walk-in"};
    private static final String[] TYPE_KEYS = {"household", "dealer", "walk_in"};
    public OperationsUi(MainActivity host) { this.host = host; this.ui = host.ui(); }

    public boolean restore(String route, String entity) {
        if (Rules.uuid(entity)) {
            switch (route) {
                case "Customer Profile": customer(entity); return true;
                case "Order Details": order(entity); return true;
                case "Payments": payments(entity); return true;
                case "Record Pickup": loadOrder(entity, this::pickup); return true;
                case "Reschedule Pickup": loadOrder(entity, this::reschedule); return true;
                case "Record Delivery": loadOrder(entity, this::delivery); return true;
            }
        }
        if ("Record Payment".equals(route) && (entity.startsWith("ORD-") || entity.startsWith("SALE-") || entity.startsWith("WI-"))) {
            String kind = entity.startsWith("ORD-") ? "order" : "sale";
            query(kind, json("reference", entity), r -> payment(nested(r, kind), kind)); return true;
        }
        switch (route) { case "Customers": customers(); return true; case "Orders": orders(); return true; case "Pending gallons / Carry-over Details": pending(); return true; case "Walk-in Sale / POS": case "Sale saved": pos(); return true; case "Payments": payments(); return true; case "Create Order": newOrder(null); return true; case "Add Customer": customerForm(null, null); return true; }
        return false;
    }

    // ================================================================================================ helpers
    /** Owner tablet layout; Owners on a phone get the phone screens. */
    private boolean owner() { return host.wide(); }
    private LinearLayout page(String title, String id) { generation++; return host.page(title, id); }
    private LinearLayout page(String title) { return page(title, ""); }
    private void query(String resource, JSONObject data, Consumer<JSONObject> action) {
        long requested = generation;
        host.query(resource, data, result -> { if (requested == generation) action.accept(result); });
    }
    private void loadOrder(String id, Consumer<JSONObject> then) { host.query("order", json("id", id), r -> then.accept(nested(r, "order"))); }
    private JSONObject json(Object... pairs) { return host.json(pairs); }
    private static String value(EditText input) { return input.getText().toString().trim(); }
    private static String title(String value) { return Rules.label(value); }
    private void watch(EditText field, Runnable changed) { field.addTextChangedListener(MainActivity.watcher(changed)); }
    private int quantity(EditText input) { return Rules.quantity(value(input)); }
    private static String cleanMoney(String raw) { return raw.replace("₱", "").replace(",", "").trim(); }
    private long cents(EditText input, String invalid) {
        try {
            String raw = cleanMoney(value(input));
            if (!raw.matches("-?[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException(invalid);
            return new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) { throw new IllegalArgumentException(invalid); }
    }
    static String pesoText(long cents) { return "₱" + BigDecimal.valueOf(cents, 2).setScale(2).toPlainString(); }
    private static String wholePeso(long cents) { return cents % 100 == 0 ? "₱" + String.format(Locale.ENGLISH, "%,d", cents / 100) : Rules.money(cents); }
    /** Date field: shows "Fri, 16 Oct 2026" (Staff) or "12 Oct 2026" (Owner); the ISO date is kept on the view. */
    EditText date(LinearLayout parent, String label, String iso) { return date(parent, label, iso, false); }
    EditText date(LinearLayout parent, String label, String iso, boolean todayPrefix) {
        EditText field = host.input(parent, label, "", InputType.TYPE_NULL);
        field.setTag(null); field.setFocusable(false); field.setContentDescription(label + ", select date");
        setIso(field, iso, todayPrefix);
        field.setOnClickListener(v -> {
            LocalDate d;
            try { d = LocalDate.parse(iso(field)); } catch (Exception ignored) { d = LocalDate.parse(host.today()); }
            host.showDialog(new DatePickerDialog(host, (picker, y, m, day) -> { setIso(field, LocalDate.of(y, m + 1, day).toString(), todayPrefix); host.clearFieldError(field); }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()));
        });
        return field;
    }
    void setIso(EditText field, String iso, boolean todayPrefix) {
        field.setTag(R.id.iso_date, iso);
        try {
            LocalDate d = LocalDate.parse(iso);
            String shown = owner() ? d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)) : d.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH));
            if (todayPrefix && iso.equals(host.today())) shown = "Today · " + d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
            field.setText(shown);
        } catch (Exception e) { field.setText(iso == null ? "" : iso); }
    }
    static String iso(EditText field) { Object v = field.getTag(R.id.iso_date); return v == null ? field.getText().toString().trim() : v.toString(); }
    private static JSONArray items(JSONObject result) { JSONArray a = result.optJSONArray("items"); return a == null ? new JSONArray() : a; }
    private static JSONObject nested(JSONObject result, String key) { JSONObject object = result.optJSONObject(key); return object == null ? result : object; }
    private static String id(JSONObject item) { return item.optString("id"); }
    static long paid(JSONObject item) { return item.optLong("paid_centavos", item.optLong("amount_paid_centavos")); }
    static long balance(JSONObject item) { return item.has("balance_centavos") ? item.optLong("balance_centavos") : item.optLong("total_centavos") - paid(item); }
    static int pending(JSONObject o) { return o.optInt("pending_quantity", o.optInt("quantity") - o.optInt("delivered_quantity")); }
    /** 1 = pickup, 2 = refilling, 3 = delivery; 0 = finished. */
    static int group(String status) {
        switch (status == null ? "" : status) {
            case "ordered": case "for_pickup": case "rescheduled": return 1;
            case "picked_up": case "refilling": return 2;
            case "ready_for_delivery": case "undelivered": return 3;
            default: return 0;
        }
    }
    static String nextLabel(String status) {
        switch (status == null ? "" : status) {
            case "ordered": return "Send for pickup";
            case "rescheduled": return "Resume for pickup";
            case "for_pickup": return "Record pickup";
            case "picked_up": return "Start refilling";
            case "refilling": return "Mark ready for delivery";
            case "ready_for_delivery": case "undelivered": return "Record delivery";
            default: return "View order";
        }
    }
    private static String shortDate(String iso) { try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)); } catch (Exception e) { return iso; } }
    private static String longDate(String iso) { try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)); } catch (Exception e) { return iso; } }
    private static String stamp(String encoded) {
        try { return OffsetDateTime.parse(encoded).atZoneSameInstant(Rules.MANILA).format(DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.ENGLISH)); } catch (Exception e) { return encoded == null ? "" : encoded; }
    }
    private long daysSince(String iso) { try { return Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.parse(host.today()))); } catch (Exception e) { return 0; } }
    private void valid(Runnable action) { try { action.run(); } catch (IllegalArgumentException e) { host.error(e.getMessage()); } }
    private EditText search(LinearLayout parent, String hint) { EditText e = host.inputPlain(parent, hint, "", InputType.TYPE_CLASS_TEXT); e.setHint(hint); return e; }
    private TextView empty(LinearLayout p, String text) { return ui.text(p, text, 14, 19, Ui.MUTED, ui.o4); }

    /** Opens the dialler for the customer of an order or customer record. */
    void call(JSONObject record) {
        String number = record.optString("contact_number");
        if (!number.isEmpty()) { dial(number); return; }
        query("order", json("id", id(record)), r -> dial(nested(r, "order").optString("contact_number")));
    }
    private void dial(String number) {
        if (number == null || number.isEmpty()) { host.error("No contact number saved for this customer."); return; }
        try { host.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); } catch (Exception e) { host.error("Calling is not available on this device."); }
    }
    /** Home shortcut: the form for the order's next eligible step. */
    void nextStep(JSONObject summary) {
        String status = summary.optString("status");
        if (!"for_pickup".equals(status) && !"ready_for_delivery".equals(status) && !"undelivered".equals(status)) { order(id(summary)); return; }
        loadOrder(id(summary), o -> { if ("for_pickup".equals(status)) pickup(o); else delivery(o); });
    }
    static String displayContact(String n) { return n != null && n.matches("[0-9]{11}") ? n.substring(0, 4) + " " + n.substring(4, 7) + " " + n.substring(7) : n == null ? "" : n; }

    // Staff customer header card (Figma S05/S11): avatar 44, name 19/28, subtitle 13/18.
    private LinearLayout staffHeader(LinearLayout p, String name, String sub) {
        LinearLayout card = ui.card(p, 10, 16);
        LinearLayout r = ui.H(card, 12);
        ui.sized(r, ui.avatar(name, 44, 15.84f), 44, 44);
        LinearLayout names = ui.vbox(2); ui.grow(r, names);
        ui.text(names, name, 19, 28, Ui.INK, ui.p6);
        ui.text(names, sub, 13, 18, Ui.MUTED, ui.o4);
        return card;
    }

    // ================================================================================================ customer chooser
    private void chooseCustomer(Consumer<JSONObject> chosen, Runnable createNew) {
        Dialog[] d = new Dialog[1];
        LinearLayout root = owner() ? host.modal(560, d) : host.sheet(d);
        if (!owner()) {
            View grab = new View(host); grab.setBackground(ui.shape(Ui.BORDER, 0, 2));
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(ui.px(40), ui.px(4)); gl.gravity = Gravity.CENTER_HORIZONTAL; root.addView(grab, gl);
        }
        ui.text(root, "Choose customer", owner() ? 22 : 19, owner() ? 33 : 28, Ui.INK, ui.p6);
        EditText find = ui.input(owner(), "Search name or contact number", InputType.TYPE_CLASS_TEXT);
        find.setContentDescription("Search name or contact number"); ui.fill(root, find);
        LinearLayout newRow = ui.H(root, 12); ui.bg(newRow, Ui.TONAL, 0, 12); ui.pad(newRow, 12, 12); newRow.setClickable(true);
        TextView plus = ui.tv("+", 18, 27, Ui.WHITE, ui.p6); plus.setGravity(Gravity.CENTER); plus.setBackground(ui.shape(Ui.PRIMARY, 0, 18));
        ui.sized(newRow, plus, 36, 36);
        ui.hug(newRow, ui.tv("New customer", 15, 22, Ui.PRIMARY, ui.p5));
        newRow.setOnClickListener(v -> { d[0].dismiss(); createNew.run(); });
        ScrollView scroller = new ScrollView(host); LinearLayout list = ui.vbox(4); scroller.addView(list);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, ui.px(owner() ? 320 : 280)));
        List<JSONObject> all = new ArrayList<>();
        Runnable render = () -> {
            list.removeAllViews(); String needle = value(find).toLowerCase(Locale.ROOT).replace(" ", ""); int shown = 0;
            for (JSONObject c : all) {
                if (!needle.isEmpty() && !(c.optString("name").toLowerCase(Locale.ROOT).replace(" ", "").contains(needle) || c.optString("contact_number").contains(needle))) continue;
                shown++;
                LinearLayout r = ui.H(list, 12); ui.pad(r, 0, 8); r.setClickable(true);
                ui.sized(r, ui.avatar(c.optString("name"), 36, 13), 36, 36);
                LinearLayout names = ui.vbox(1); ui.grow(r, names);
                ui.text(names, c.optString("name"), 15, 22, Ui.INK, ui.p5);
                ui.text(names, title(c.optString("customer_type")) + " · " + displayContact(c.optString("contact_number")), 12, 16, Ui.MUTED, ui.o4);
                long due = balance(c);
                ui.hug(r, ui.tv(due > 0 ? wholePeso(due) + " due" : "–", 13, 20, due > 0 ? Ui.AMBER : Ui.MUTED, ui.p5));
                r.setContentDescription(c.optString("name"));
                r.setOnClickListener(v -> { d[0].dismiss(); chosen.accept(c); });
            }
            if (shown == 0) empty(list, "No customers found.");
        };
        watch(find, render);
        host.query("customers", json(), response -> { JSONArray rows = items(response); for (int i = 0; i < rows.length(); i++) { JSONObject c = rows.optJSONObject(i); if (c != null) all.add(c); } render.run(); });
        host.showDialog(d[0]);
    }

    // ================================================================================================ customers
    private static final String[] CUSTOMER_TABS = {"All", "Household", "Dealer", "Owes ₱"};
    public void customers() { customers(null); }
    private void customers(String selectedId) {
        LinearLayout p = page("Customers", selectedId == null ? "" : selectedId);
        if (owner()) {
            ui.hug(host.titleActions(), ui.button("+  Add customer", Ui.FILLED, 44, 12, 15, () -> customerForm(null, null)));
            LinearLayout[] panes = host.panes(380);
            host.bannerAt(panes[1], 0);
            customerList(panes[0], selectedId, ui.V(panes[1], 14));
        } else {
            customerList(p, null, null);
            host.floatingAction("+  Add customer", () -> customerForm(null, null));
        }
    }
    private void customerList(LinearLayout side, String selectedId, LinearLayout pane) {
        boolean o = owner();
        EditText search = search(side, "Search name or contact number");
        int[] filter = {0};
        LinearLayout results = ui.vbox(o ? 14 : 16);
        Runnable[] reload = new Runnable[1];
        ui.tabs(side, CUSTOMER_TABS, 0, false, o, o ? 6 : 8, i -> { filter[0] = i; reload[0].run(); });
        ui.fill(side, results);
        long[] searchRequest = {0};
        String[] open = {selectedId == null ? "" : selectedId};
        String[] shownId = {""};
        Runnable showPane = () -> { if (pane == null || open[0].isEmpty() || open[0].equals(shownId[0])) return; shownId[0] = open[0]; pane.removeAllViews(); renderCustomer(pane, open[0]); };
        reload[0] = () -> {
            long requested = ++searchRequest[0];
            query("customers", json("search", value(search).replace(" ", "")), result -> {
                if (requested != searchRequest[0]) return;
                results.removeAllViews(); JSONArray rows = items(result); int shown = 0; JSONObject first = null;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject c = rows.optJSONObject(i); if (c == null) continue;
                    String type = c.optString("customer_type");
                    if (filter[0] == 1 && !"household".equals(type)) continue;
                    if (filter[0] == 2 && !"dealer".equals(type)) continue;
                    if (filter[0] == 3 && balance(c) <= 0) continue;
                    shown++; if (first == null) first = c;
                    long due = balance(c);
                    if (o) {
                        boolean on = id(c).equals(open[0]);
                        LinearLayout item = ui.H(results, 8); ui.bg(item, on ? Ui.TONAL : Ui.WHITE, on ? Ui.PRIMARY : Ui.BORDER, 12); ui.pad(item, 12, 12); item.setClickable(true);
                        LinearLayout names = ui.vbox(2); ui.grow(item, names);
                        ui.text(names, c.optString("name"), 15, 22, Ui.INK, ui.p6);
                        ui.text(names, title(type) + " · " + displayContact(c.optString("contact_number")), 12, 16, Ui.MUTED, ui.o4);
                        LinearLayout end = ui.vbox(4); end.setGravity(Gravity.END); ui.hug(item, end);
                        ui.hug(end, ui.chip("Active", Ui.GREEN_BG, Ui.GREEN));
                        ui.hug(end, ui.tv(due > 0 ? host.money(due) + " due" : "Settled", 12, 18, due > 0 ? Ui.AMBER : Ui.GREEN, ui.p5));
                        item.setContentDescription(c.optString("name"));
                        item.setOnClickListener(v -> { open[0] = id(c); reload[0].run(); showPane.run(); });
                    } else {
                        LinearLayout card = ui.card(results, 0, 14); card.setClickable(true);
                        LinearLayout r = ui.H(card, 12);
                        ui.sized(r, ui.avatar(c.optString("name"), 44, 15.84f), 44, 44);
                        LinearLayout names = ui.vbox(1); ui.grow(r, names);
                        ui.text(names, c.optString("name"), 16, 24, Ui.INK, ui.p6);
                        ui.text(names, title(type) + " · " + displayContact(c.optString("contact_number")), 12, 16, Ui.MUTED, ui.o4);
                        ui.hug(r, due > 0 ? ui.chip(host.money(due) + " due", Ui.AMBER_BG, Ui.AMBER) : ui.chip(paid(c) > 0 || c.optLong("total_centavos") > 0 ? "Settled" : host.money(0), Ui.GREEN_BG, Ui.GREEN));
                        card.setContentDescription(c.optString("name") + " " + (due > 0 ? host.money(due) + " due" : "Settled"));
                        card.setOnClickListener(v -> customer(id(c)));
                    }
                }
                if (shown == 0) empty(results, "No customers found.");
                if (o && open[0].isEmpty() && first != null) { open[0] = id(first); reload[0].run(); }
                if (o) showPane.run();
            });
        };
        watch(search, reload[0]);
        reload[0].run();
    }

    public void customer(String customerId) {
        if (owner()) { customers(customerId); return; }
        LinearLayout p = page("Customer Profile", customerId);
        renderCustomer(p, customerId);
    }
    private void renderCustomer(LinearLayout p, String customerId) {
        boolean o = owner();
        query("customer", json("id", customerId), response -> {
            JSONObject c = nested(response, "customer");
            long due = balance(c);
            LinearLayout head = ui.card(p, o ? 14 : 12, o ? 18 : 16);
            LinearLayout r = ui.H(head, o ? 14 : 12);
            ui.sized(r, ui.avatar(c.optString("name"), 56, 20.16f), 56, 56);
            LinearLayout names = ui.vbox(o ? 4 : 3); ui.grow(r, names);
            ui.text(names, c.optString("name"), o ? 22 : 20, o ? 33 : 30, Ui.INK, ui.p6);
            ui.text(names, displayContact(c.optString("contact_number")) + " · " + c.optString("address"), o ? 13 : 12, o ? 18 : 16, Ui.MUTED, ui.o4);
            LinearLayout chips = ui.hbox(8); ui.hug(names, chips);
            ui.hug(chips, ui.chip(title(c.optString("customer_type")), Ui.TONAL, Ui.PRIMARY));
            if (o) ui.hug(chips, ui.chip("Active", Ui.GREEN_BG, Ui.GREEN));
            else if (due > 0) ui.hug(chips, ui.chip(host.money(due) + " due", Ui.AMBER_BG, Ui.AMBER));
            if (o) {
                LinearLayout acts = ui.hbox(8); ui.hug(r, acts);
                ui.hug(acts, ui.button("Edit", Ui.OUTLINE, 44, 12, 15, () -> customerForm(c, null)));
                ui.hug(acts, ui.button("New order", Ui.TONAL_BTN, 44, 12, 15, () -> newOrder(customerId)));
                if (due > 0) ui.hug(acts, ui.button("Collect " + wholePeso(due), Ui.AMBER_BTN, 44, 12, 15, () -> payments(customerId)));
            } else {
                LinearLayout actions = ui.H(p, 10);
                ui.grow(actions, ui.button("Call", Ui.TONAL_BTN, 48, 14, 14, () -> dial(c.optString("contact_number"))));
                ui.grow(actions, ui.button("New order", Ui.FILLED, 48, 14, 14, () -> newOrder(customerId)));
                if (due > 0) ui.grow(actions, ui.button("Collect", Ui.AMBER_BTN, 48, 14, 14, () -> payments(customerId)));
            }
            TextView[] stats = new TextView[4];
            if (o) {
                LinearLayout row = ui.H(p, 12);
                String[] labels = {"Orders", "Total billed", "Paid", "Outstanding"};
                for (int i = 0; i < 4; i++) {
                    LinearLayout s = ui.vbox(2); ui.bg(s, i == 3 ? Ui.AMBER_BG : Ui.WHITE, i == 3 ? 0 : Ui.BORDER, 16); ui.pad(s, 14, 14);
                    ui.text(s, labels[i], 12, 16, Ui.MUTED, ui.o4);
                    stats[i] = ui.text(s, i == 3 ? host.money(due) : "–", 20, 30, i == 3 ? Ui.AMBER : Ui.INK, ui.p6);
                    ui.grow(row, s);
                }
            }
            LinearLayout historyHost = o ? ui.card(p, 10, 18) : p;
            LinearLayout tabRow = o ? ui.H(historyHost, 8) : null;
            LinearLayout segHost = o ? ui.vbox(0) : p;
            if (o) ui.hug(tabRow, segHost);
            LinearLayout orderRows = ui.V(historyHost, o ? 0 : 16), paymentRows = ui.V(historyHost, o ? 0 : 16);
            paymentRows.setVisibility(View.GONE);
            Ui.Tabs seg = ui.segmented(segHost, new String[]{"Orders", "Payments"}, 0, i -> { orderRows.setVisibility(i == 0 ? View.VISIBLE : View.GONE); paymentRows.setVisibility(i == 1 ? View.VISIBLE : View.GONE); });
            if (o) { ((LinearLayout.LayoutParams) seg.view.getLayoutParams()).width = ui.px(220); historyHost.removeView(orderRows); historyHost.removeView(paymentRows); historyHost.addView(orderRows); historyHost.addView(paymentRows); }
            String[] range = {"", ""};
            Runnable history = () -> {
                query("orders", json("customer_id", customerId, "start_date", range[0], "end_date", range[1]), orders -> {
                    orderRows.removeAllViews(); JSONArray list = items(orders);
                    long billed = 0, paidSum = 0;
                    for (int i = 0; i < list.length(); i++) { JSONObject x = list.optJSONObject(i); if (x != null) { billed += x.optLong("total_centavos"); paidSum += paid(x); } }
                    if (o) { stats[0].setText(Integer.toString(list.length())); stats[1].setText(host.money(billed)); stats[2].setText(host.money(paidSum)); }
                    renderHistoryOrders(orderRows, list);
                });
                query("payments", json("customer_id", customerId, "start_date", range[0], "end_date", range[1]), pays -> { paymentRows.removeAllViews(); renderReceipts(paymentRows, items(pays), !o); });
            };
            if (o) {
                ui.spacer(tabRow);
                String[] periods = {"All time ▾", "This month ▾", "Custom ▾"};
                int[] which = {0};
                TextView period = ui.tv(periods[0], 14, 21, Ui.INK, ui.p5); ui.pad(period, 14, 8); ui.bg(period, Ui.WHITE, Ui.FIELD, 20); period.setClickable(true);
                ui.hug(tabRow, period);
                period.setOnClickListener(v -> {
                    which[0] = (which[0] + 1) % 2; period.setText(periods[which[0]]);
                    LocalDate now = LocalDate.parse(host.today());
                    range[0] = which[0] == 1 ? now.withDayOfMonth(1).toString() : ""; range[1] = which[0] == 1 ? now.toString() : "";
                    history.run();
                });
            }
            history.run();
            if (!o) ui.fill(p, linkButton("Edit customer", () -> customerForm(c, null)));
        });
    }
    private TextView linkButton(String label, Runnable action) {
        TextView t = ui.tv(label, 14, 21, Ui.PRIMARY, ui.p5); t.setGravity(Gravity.CENTER); t.setMinHeight(ui.px(40)); t.setClickable(true);
        t.setOnClickListener(v -> action.run()); return t;
    }
    private void renderHistoryOrders(LinearLayout p, JSONArray list) {
        boolean o = owner();
        if (list.length() == 0) { empty(p, "No orders found."); return; }
        for (int i = 0; i < list.length(); i++) {
            JSONObject x = list.optJSONObject(i); if (x == null) continue;
            String meta = shortDate(x.optString("business_date")) + " · " + x.optInt("quantity") + " gallons · " + host.money(x.optLong("total_centavos"));
            String status = x.optString("status");
            TextView chip = "undelivered".equals(status) ? ui.chip("Undelivered", Ui.AMBER_BG, Ui.AMBER) : ui.statusChip(status, pending(x));
            if (o) {
                LinearLayout line = ui.H(p, 0); ui.pad(line, 0, 8); line.setMinimumHeight(ui.px(42)); line.setClickable(true);
                LinearLayout names = ui.hbox(16); ui.grow(line, names);
                ui.hug(names, ui.tv(x.optString("reference"), 14, 21, Ui.INK, ui.p5));
                ui.hug(names, ui.tv(meta, 13, 18, Ui.MUTED, ui.o4));
                ui.hug(line, chip);
                line.setOnClickListener(v -> order(id(x)));
            } else {
                LinearLayout card = ui.card(p, 6, 14); card.setClickable(true);
                LinearLayout top = ui.H(card, 8);
                ui.grow(top, ui.tv(x.optString("reference"), 15, 22, Ui.INK, ui.p5));
                ui.hug(top, chip);
                ui.text(card, meta, 13, 18, Ui.MUTED, ui.o4);
                card.setOnClickListener(v -> order(id(x)));
            }
        }
    }

    /** Add/edit customer: Staff page (K3, K4), Owner modal (O7b–O7e). */
    private void customerForm(JSONObject existing, Consumer<String> afterSave) {
        boolean o = owner();
        Dialog[] d = new Dialog[1];
        LinearLayout form;
        if (o) {
            form = host.modal(460, d);
            ui.text(form, existing == null ? "Add customer" : "Edit customer", 22, 33, Ui.INK, ui.p6);
            ui.text(form, existing == null ? "Enter the customer details." : "Update the customer details.", 14, 19, Ui.MUTED, ui.o4);
        } else {
            LinearLayout p = page(existing == null ? "Add Customer" : "Edit Customer", existing == null ? "" : id(existing));
            form = ui.card(p, 16, 16);
        }
        EditText name = host.input(form, "Full name", existing == null ? "" : existing.optString("name"), TEXT);
        EditText contact = host.input(form, "Contact number", existing == null ? "" : displayContact(existing.optString("contact_number")), InputType.TYPE_CLASS_PHONE);
        if (!o) { LinearLayout f = (LinearLayout) contact.getParent(); TextView help = ui.tv("11 digits", 12, 16, Ui.MUTED, ui.o4); f.addView(help, f.indexOfChild(contact) + 1, new LinearLayout.LayoutParams(-1, -2)); }
        EditText address = host.input(form, "Address", existing == null ? "" : existing.optString("address"), TEXT);
        LinearLayout typeBox = ui.V(form, o ? 6 : 16);
        ui.label(typeBox, "Customer type");
        int[] type = {-1};
        if (existing != null) { String t = existing.optString("customer_type"); type[0] = t.equals("household") ? 0 : t.equals("dealer") ? 1 : t.equals("walk_in") ? 2 : -1; }
        TextView typeError = ui.fieldError(typeBox);
        ui.segmented(typeBox, TYPE_NAMES, type[0], i -> { type[0] = i; typeError.setVisibility(View.GONE); });
        typeBox.removeView(typeError); typeBox.addView(typeError);
        LinearLayout bannerSlot = o ? ui.V(form, 0) : null;
        Runnable save = () -> valid(() -> {
            boolean bad = false;
            if (value(name).isEmpty()) { host.fieldError(name, "Complete the required fields."); bad = true; }
            if (value(contact).isEmpty()) { host.fieldError(contact, "Complete the required fields."); bad = true; }
            if (value(address).isEmpty()) { host.fieldError(address, "Complete the required fields."); bad = true; }
            if (type[0] < 0) { typeError.setText("Choose Household, Dealer, or Walk-in."); typeError.setVisibility(View.VISIBLE); bad = true; }
            if (bad) return;
            String number;
            try { number = Rules.customerContact(value(contact)); } catch (IllegalArgumentException e) { host.fieldError(contact, e.getMessage()); return; }
            JSONObject fields = json("name", value(name), "customer_type", TYPE_KEYS[type[0]], "contact_number", number, "address", value(address));
            if (existing != null) try { fields.put("id", id(existing)); } catch (Exception e) { throw new IllegalArgumentException("Unable to prepare customer."); }
            String savedName = value(name), savedType = TYPE_NAMES[type[0]];
            host.command(existing == null ? "create_customer" : "update_customer", fields, result -> {
                if (d[0] != null) d[0].dismiss();
                JSONObject saved = nested(result, "customer");
                if (afterSave != null) afterSave.accept(id(saved)); else customer(existing != null ? id(existing) : id(saved));
                host.info("Customer saved.", savedName + " · " + savedType + " · " + displayContact(number));
            });
        });
        if (o) {
            LinearLayout row = ui.H(form, 10); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            ui.hug(row, ui.ownerButton("Cancel", Ui.OUTLINE, () -> d[0].dismiss()));
            ui.hug(row, ui.ownerButton(existing == null ? "Save customer" : "Save changes", Ui.FILLED, save));
            host.showDialog(d[0]);
            host.bannerAt(bannerSlot, 0);
        } else host.mainAction(existing == null ? "Save customer" : "Save changes", Ui.FILLED, save);
    }

    // ================================================================================================ orders
    public void orders() { ordersFiltered(null); }
    public void ordersFiltered(String initialGroup) {
        if (owner()) { ownerOrders("Orders", "", null, initialGroup == null ? 0 : tabFor(initialGroup), null); return; }
        LinearLayout p = page("Orders");
        EditText search = search(p, "Search name or order ID");
        String[] names = {"Pickup", "Refilling", "Deliver", "All"};
        int[] map = {1, 2, 3, -1};
        String saved = host.choice("orders_filter");
        int start = 0;
        for (int i = 0; i < map.length; i++) if (initialGroup != null && initialGroup.equals(groupName(map[i]))) start = i;
        if (initialGroup == null && !saved.isEmpty()) try { start = Math.max(0, Math.min(3, Integer.parseInt(saved))); } catch (NumberFormatException ignored) { }
        int[] selected = {start};
        LinearLayout rows = ui.vbox(16);
        List<JSONObject> loaded = new ArrayList<>();
        Runnable[] render = new Runnable[1];
        Ui.Tabs tabs = ui.tabs(p, names, start, false, false, 8, i -> { selected[0] = i; host.rememberChoice("orders_filter", Integer.toString(i)); render[0].run(); });
        ui.fill(p, rows);
        render[0] = () -> {
            rows.removeAllViews();
            int[] counts = new int[4];
            for (JSONObject o : loaded) { int g = group(o.optString("status")); if (g > 0) counts[g]++; }
            for (int i = 0; i < map.length; i++) tabs.label(i, names[i] + " " + (map[i] < 0 ? loaded.size() : counts[map[i]]));
            int shown = 0;
            for (JSONObject o : loaded) {
                if (map[selected[0]] >= 0 && group(o.optString("status")) != map[selected[0]]) continue;
                shown++;
                staffOrderCard(rows, o);
            }
            if (shown == 0) empty(rows, "No orders found.");
        };
        long[] searchRequest = {0};
        Runnable load = () -> {
            long requested = ++searchRequest[0];
            query("orders", json("search", value(search)), response -> {
                if (requested != searchRequest[0]) return;
                loaded.clear(); JSONArray all = items(response);
                for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null) loaded.add(o); }
                render[0].run();
            });
        };
        watch(search, load);
        load.run();
        host.floatingAction("+  New order", () -> newOrder(null));
    }
    private static int tabFor(String group) { switch (group) { case "Pickup": return 1; case "Refilling": return 2; case "Deliver": return 3; default: return 0; } }
    private static String groupName(int g) { return g == 1 ? "Pickup" : g == 2 ? "Refilling" : g == 3 ? "Deliver" : "All"; }
    /** Figma S04 order card. */
    private void staffOrderCard(LinearLayout parent, JSONObject o) {
        LinearLayout card = ui.card(parent, 10, 16); card.setClickable(true);
        LinearLayout top = ui.H(card, 8);
        LinearLayout names = ui.vbox(2); ui.grow(top, names);
        ui.text(names, o.optString("customer_name"), 16, 24, Ui.INK, ui.p6);
        ui.text(names, o.optString("reference") + " · " + o.optInt("quantity") + " gallons", 13, 18, Ui.MUTED, ui.o4);
        ui.hug(top, ui.statusChip(o.optString("status"), pending(o)));
        LinearLayout bottom = ui.H(card, 0);
        String status = o.optString("status");
        TextView next = ui.tv(group(status) > 0 ? "Next: " + nextLabel(status) + "  ›" : "Delivered", 14, 21, group(status) > 0 ? Ui.PRIMARY : Ui.MUTED, ui.p5);
        ui.grow(bottom, next);
        long due = balance(o);
        ui.hug(bottom, ui.tv(due > 0 ? host.money(due) + " due" : "Paid", 14, 21, due > 0 ? Ui.AMBER : Ui.GREEN, ui.p5));
        card.setContentDescription(o.optString("reference") + " " + o.optString("customer_name") + " " + Rules.label(status));
        card.setOnClickListener(v -> order(id(o)));
    }

    /**
     * Owner Orders page (O3–O6): list on the left (380), and on the right either the selected order,
     * a pickup/reschedule/delivery form, the new order form or the walk-in sale.
     */
    private void ownerOrders(String route, String entity, String selectedId, int tab, Consumer<LinearLayout> right) {
        page(route, entity);
        int mode = "Create Order".equals(route) ? 1 : "Walk-in Sale / POS".equals(route) ? 2 : 0;
        LinearLayout acts = host.titleActions();
        TextView walk = ui.button("Walk-in sale", mode == 2 ? Ui.DISABLED : Ui.TONAL_BTN, 44, 12, 15, this::pos);
        TextView neu = ui.button("+  New order", mode == 1 ? Ui.DISABLED : mode == 2 ? Ui.TONAL_BTN : Ui.FILLED, 44, 12, 15, () -> newOrder(null));
        if (mode == 2) walk.setEnabled(true); if (mode == 1) neu.setEnabled(true);
        ui.hug(acts, walk); ui.hug(acts, neu);
        LinearLayout[] panes = host.panes(380);
        LinearLayout list = panes[0], pane = ui.V(panes[1], 14);
        host.bannerAt(panes[1], 0);
        EditText search = search(list, "Search order or customer");
        String[] names = {"All", "Pickup", "Refill", "Deliver"};
        int[] map = {-1, 1, 2, 3};
        int[] selected = {tab};
        LinearLayout rows = ui.vbox(14);
        List<JSONObject> loaded = new ArrayList<>();
        String[] openId = {selectedId == null ? "" : selectedId};
        Runnable[] render = new Runnable[1];
        Ui.Tabs tabs = ui.tabs(list, names, mode == 0 ? tab : -1, false, true, 6, i -> { selected[0] = i; render[0].run(); });
        ui.fill(list, rows);
        render[0] = () -> {
            rows.removeAllViews();
            int[] counts = new int[4];
            for (JSONObject o : loaded) { int g = group(o.optString("status")); if (g > 0) counts[g]++; }
            for (int i = 0; i < map.length; i++) tabs.label(i, names[i] + " " + (map[i] < 0 ? loaded.size() : counts[map[i]]));
            int shown = 0; JSONObject first = null;
            for (JSONObject o : loaded) {
                if (selected[0] > 0 && group(o.optString("status")) != map[selected[0]]) continue;
                shown++; if (first == null) first = o;
                ownerOrderItem(rows, o, id(o).equals(openId[0]));
            }
            if (shown == 0) empty(rows, "No orders found.");
            if (right == null && openId[0].isEmpty() && first != null) { openId[0] = id(first); render[0].run(); loadOrder(openId[0], x -> { pane.removeAllViews(); ownerDetail(pane, x); }); }
        };
        long[] searchRequest = {0};
        Runnable load = () -> {
            long requested = ++searchRequest[0];
            query("orders", json("search", value(search)), response -> {
                if (requested != searchRequest[0]) return;
                loaded.clear(); JSONArray all = items(response);
                for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null) loaded.add(o); }
                render[0].run();
            });
        };
        watch(search, load);
        load.run();
        if (right != null) right.accept(pane);
        else if (!openId[0].isEmpty()) loadOrder(openId[0], x -> { pane.removeAllViews(); ownerDetail(pane, x); });
    }
    /** Figma O3 list item. */
    private void ownerOrderItem(LinearLayout parent, JSONObject o, boolean on) {
        LinearLayout item = ui.H(parent, 8); ui.bg(item, on ? Ui.TONAL : Ui.WHITE, on ? Ui.PRIMARY : Ui.BORDER, 12); ui.pad(item, 12, 12); item.setClickable(true);
        LinearLayout names = ui.vbox(2); ui.grow(item, names);
        ui.text(names, o.optString("customer_name"), 15, 22, Ui.INK, ui.p6);
        ui.text(names, o.optString("reference") + " · " + o.optInt("quantity") + " gal", 12, 16, Ui.MUTED, ui.o4);
        LinearLayout end = ui.vbox(4); end.setGravity(Gravity.END); ui.hug(item, end);
        ui.hug(end, ui.statusChip(o.optString("status"), pending(o)));
        long due = balance(o);
        ui.hug(end, ui.tv(due > 0 ? host.money(due) + " due" : "Paid", 12, 18, due > 0 ? Ui.AMBER : Ui.GREEN, ui.p5));
        item.setContentDescription(o.optString("reference") + " " + o.optString("customer_name") + " " + Rules.label(o.optString("status")));
        item.setOnClickListener(v -> order(id(o)));
    }

    public void pending() {
        LinearLayout p = page("Pending gallons / Carry-over Details");
        ui.text(p, "Gallons still owed to customers", 14, 19, Ui.MUTED, ui.o4);
        LinearLayout list = ui.V(p, 16);
        query("orders", json(), result -> {
            List<JSONObject> records = new ArrayList<>(); JSONArray all = items(result);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o != null && ("undelivered".equals(o.optString("status")) || "ready_for_delivery".equals(o.optString("status"))) && pending(o) > 0) records.add(o);
            }
            records.sort(Comparator.comparing(o -> o.optString("business_date")));
            if (records.isEmpty()) empty(list, "No pending deliveries.");
            for (JSONObject o : records) {
                long days = daysSince(o.optString("business_date"));
                LinearLayout card = ui.card(list, 10, 16); card.setClickable(true); card.setOnClickListener(v -> order(id(o)));
                LinearLayout top = ui.H(card, 8);
                LinearLayout names = ui.vbox(2); ui.grow(top, names);
                ui.text(names, o.optString("customer_name"), 16, 24, Ui.INK, ui.p6);
                ui.text(names, o.optString("reference") + " · Since " + shortDate(o.optString("business_date")), 13, 18, Ui.MUTED, ui.o4);
                ui.hug(top, days == 0 ? ui.chip("Today", Ui.TONAL, Ui.PRIMARY) : ui.chip(days + (days == 1 ? " day waiting" : " days waiting"), Ui.AMBER_BG, Ui.AMBER));
                LinearLayout bottom = ui.H(card, 8);
                int left = pending(o);
                ui.grow(bottom, ui.tv(left + (left == 1 ? " gallon" : " gallons"), 20, 30, Ui.INK, ui.p6));
                ui.hug(bottom, ui.button("Deliver", Ui.FILLED, 40, 12, 15, () -> nextStep(o)));
                card.setContentDescription(o.optString("reference") + " " + o.optString("customer_name") + " " + left + " gallons pending, original date " + o.optString("business_date") + ", " + days + " days waiting");
            }
        });
    }

    // ================================================================================================ new order
    public void newOrder(String selectedCustomerId) {
        if (owner()) { ownerOrders("Create Order", "", null, -1, pane -> newOrderForm(pane, selectedCustomerId)); return; }
        newOrderForm(page("Create Order"), selectedCustomerId);
    }
    private void newOrderForm(LinearLayout p, String selectedCustomerId) {
        boolean o = owner();
        String desired = selectedCustomerId == null ? host.choice("order_customer") : selectedCustomerId;
        JSONObject[] picked = {null};
        LinearLayout customerCard = ui.card(p, 10, o ? 18 : 16);
        LinearLayout qtyCard = ui.card(p, o ? 14 : 16, o ? 18 : 16);
        LinearLayout qHead = ui.H(qtyCard, 8);
        ui.grow(qHead, ui.tv("How many gallons?", 13, 18, Ui.MUTED, ui.o6));
        TextView last = ui.chip("", Ui.TONAL, Ui.PRIMARY); last.setVisibility(View.GONE);
        if (o) ui.hug(qHead, last);
        EditText quantity = host.quantity(qtyCard, "Gallon quantity", "", o ? new int[]{5, 10, 15, 20, -1} : new int[]{5, 10, 15, 20}, o ? new String[]{"5", "10", "15", "20", "Other"} : new String[]{"5", "10", "15", "20"}, "gallons", o, 8, null);
        if (o) ui.size(quantity, 48, 72);
        quantity.setHint("0");
        if (!o) { LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); qtyCard.addView(last, lp); }
        LinearLayout priceCard = ui.card(p, 8, o ? 18 : 16);
        TextView[] unitRow = ui.pair(priceCard, "Price per gallon", "—");
        String[] orderDate = {host.today()};
        TextView[] dateRow = ui.pair(priceCard, "Order date", dateLabel(orderDate[0]));
        dateRow[1].setClickable(true);
        dateRow[1].setOnClickListener(v -> {
            LocalDate d; try { d = LocalDate.parse(orderDate[0]); } catch (Exception e) { d = LocalDate.parse(host.today()); }
            host.showDialog(new DatePickerDialog(host, (picker, y, m, day) -> { orderDate[0] = LocalDate.of(y, m + 1, day).toString(); dateRow[1].setText(dateLabel(orderDate[0])); }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()));
        });
        TextView[] totalRow = ui.pair(priceCard, "Total", host.money(0), Ui.PRIMARY, o ? 24 : 22, ui.p6);
        Runnable preview = () -> {
            if (picked[0] == null) { unitRow[0].setText("Price per gallon"); unitRow[1].setText("—"); totalRow[1].setText(host.money(0)); return; }
            long unit = Rules.fixedPrice(picked[0].optString("customer_type"));
            unitRow[0].setText("Price per gallon · " + title(picked[0].optString("customer_type"))); unitRow[1].setText(host.money(unit));
            try { totalRow[1].setText(host.money(Math.multiplyExact((long) Integer.parseInt(value(quantity)), unit))); }
            catch (Exception e) { totalRow[1].setText(host.money(0)); }
        };
        watch(quantity, preview);
        Runnable[] showCustomer = new Runnable[1];
        Runnable pick = () -> chooseCustomer(c -> { picked[0] = c; host.rememberChoice("order_customer", id(c)); showCustomer[0].run(); },
            () -> customerForm(null, selected -> newOrder(selected)));
        showCustomer[0] = () -> {
            customerCard.removeAllViews();
            if (o) ui.text(customerCard, "New order", 22, 33, Ui.INK, ui.p6);
            if (picked[0] == null) {
                LinearLayout r = ui.H(customerCard, 12); r.setClickable(true); r.setOnClickListener(v -> pick.run());
                if (o) { ui.bg(r, Ui.TONAL, 0, 12); ui.pad(r, 12, 12); }
                ui.grow(r, ui.tv("Choose customer", 16, 24, Ui.INK, ui.p6));
                ui.hug(r, ui.link("Select", 14, pick));
            } else {
                JSONObject c = picked[0];
                LinearLayout r = ui.H(customerCard, 12);
                if (o) { ui.bg(r, Ui.TONAL, 0, 12); ui.pad(r, 12, 12); }
                TextView av = ui.avatar(c.optString("name"), o ? 40 : 44, o ? 14.4f : 15.84f);
                if (o) av.setBackground(ui.shape(Ui.WHITE, 0, 20));
                ui.sized(r, av, o ? 40 : 44, o ? 40 : 44);
                LinearLayout names = ui.vbox(o ? 1 : 2); ui.grow(r, names);
                ui.text(names, c.optString("name"), o ? 16 : 17, o ? 24 : 26, Ui.INK, ui.p6);
                String sub = o ? title(c.optString("customer_type")) + " · " + displayContact(c.optString("contact_number")) + " · Delivers to " + c.optString("address") : title(c.optString("customer_type")) + " · " + c.optString("address");
                ui.text(names, sub, 12, 16, Ui.MUTED, ui.o4).setSingleLine(true);
                ui.hug(r, ui.link("Change", 14, pick));
                int previous = c.optInt("previous_order_quantity", 0);
                if (previous > 0) { last.setText("Last order: " + previous + " gallons"); last.setVisibility(View.VISIBLE); last.setClickable(true); last.setOnClickListener(v -> quantity.setText(Integer.toString(previous))); if (value(quantity).isEmpty()) quantity.setText(Integer.toString(previous)); }
                else last.setVisibility(View.GONE);
            }
            if (o) ui.text(customerCard, "Not in the list? Search the customer box and choose “+ New customer”.", 12, 16, Ui.MUTED, ui.o4);
            preview.run();
        };
        showCustomer[0].run();
        query("customers", json(), response -> {
            JSONArray all = items(response);
            for (int i = 0; i < all.length(); i++) { JSONObject c = all.optJSONObject(i); if (c != null && id(c).equals(desired)) picked[0] = c; }
            showCustomer[0].run();
            if (picked[0] == null && selectedCustomerId == null && value(quantity).isEmpty()) pick.run();
        });
        Runnable save = () -> valid(() -> {
            if (picked[0] == null) { pick.run(); return; }
            int q;
            try { q = quantity(quantity); } catch (IllegalArgumentException e) { host.fieldError(quantity, e.getMessage()); return; }
            String d = orderDate[0];
            JSONObject c = picked[0]; long unit = Rules.fixedPrice(c.optString("customer_type"));
            host.command("create_order", json("customer_id", id(c), "quantity", q, "business_date", d), result -> {
                JSONObject saved = nested(result, "order");
                if (!id(saved).isEmpty()) order(id(saved)); else orders();
                host.info("Order saved", saved.optString("reference") + " · " + c.optString("name") + " · " + q + " gallons · " + host.money(Math.multiplyExact((long) q, unit)));
            });
        });
        if (o) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, this::orders); host.ownerAction(row, "Save order", Ui.FILLED, save); }
        else host.mainAction("Save order", Ui.FILLED, save);
    }
    private String dateLabel(String iso) {
        boolean today = iso.equals(host.today());
        String d = owner() ? longDate(iso) : shortDate(iso);
        return today ? "Today · " + d : d;
    }

    // ================================================================================================ order detail
    public void order(String orderId) {
        if (owner()) { ownerOrders("Order Details", orderId, orderId, 0, null); return; }
        LinearLayout p = page("Order Details", orderId);
        loadOrder(orderId, o -> staffDetail(p, o));
    }
    /** Figma S05/S06/S07/S10*: Staff order detail. */
    private void staffDetail(LinearLayout p, JSONObject o) {
        String status = o.optString("status");
        long balance = balance(o), unit = o.optLong("unit_price_centavos");
        int delivered = o.optInt("delivered_quantity"), quantity = o.optInt("quantity");
        LinearLayout head = staffHeader(p, o.optString("customer_name"), o.optString("reference") + " · " + title(o.optString("customer_type")));
        if (group(status) == 1 && !"ordered".equals(status)) {
            LinearLayout r = ui.H(head, 8);
            LinearLayout contact = ui.vbox(2); ui.grow(r, contact);
            ui.text(contact, displayContact(o.optString("contact_number")), 15, 22, Ui.INK, ui.p5);
            ui.text(contact, o.optString("address"), 13, 18, Ui.MUTED, ui.o4);
            ui.hug(r, ui.button("Call", Ui.TONAL_BTN, 40, 14, 16, () -> dial(o.optString("contact_number"))));
        }
        LinearLayout prog = ui.card(p, 14, 16);
        boolean labelled = !"for_pickup".equals(status);
        ui.label(prog, labelled ? "Progress · " + title(status) : "Progress");
        ui.progress(prog, status, false);
        boolean progressStyle = delivered > 0 || "undelivered".equals(status);
        if (progressStyle && !"delivered".equals(status)) {
            LinearLayout g = ui.card(p, 10, 16);
            ui.label(g, "Gallons");
            LinearLayout bar = ui.H(g, 0);
            View done = new View(host), left = new View(host);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, ui.px(10), Math.max(0.01f, delivered)), ll = new LinearLayout.LayoutParams(0, ui.px(10), Math.max(0.01f, pending(o)));
            done.setBackground(corner(Ui.PRIMARY, true, pending(o) == 0)); left.setBackground(corner(Ui.AMBER, delivered == 0, true));
            bar.addView(done, dl); bar.addView(left, ll);
            ui.pair(g, "Delivered", delivered + " of " + quantity, Ui.INK, 17, ui.p6);
            ui.pair(g, "Still to deliver", pending(o) + (pending(o) == 1 ? " gallon" : " gallons"), Ui.AMBER, 17, ui.p6);
            long days = daysSince(o.optString("business_date"));
            ui.pair(g, "Waiting since", shortDate(o.optString("business_date")) + " · " + days + (days == 1 ? " day" : " days"));
        } else if (!"ordered".equals(status) && !"delivered".equals(status)) {
            LinearLayout g = ui.card(p, 10, 16);
            LinearLayout stats = ui.H(g, 8); stats.setGravity(Gravity.TOP);
            String[][] cols = {{Integer.toString(quantity), "Ordered"}, {Integer.toString(o.optInt("collected_quantity")), "Picked up"}, {Integer.toString(delivered), "Delivered"}, {Integer.toString(pending(o)), "Pending"}};
            for (String[] c : cols) { LinearLayout cell = ui.vbox(2); ui.text(cell, c[0], 22, 33, Ui.INK, ui.p6); ui.text(cell, c[1], 12, 16, Ui.MUTED, ui.o4); ui.grow(stats, cell); }
        }
        LinearLayout pay = ui.card(p, 8, 16);
        if (!"ordered".equals(status)) {
            LinearLayout ph = ui.H(pay, 8);
            ui.grow(ph, ui.tv("Payment", 13, 18, Ui.MUTED, ui.o6));
            ui.hug(ph, ui.paymentChip(o.optString("payment_status"), false));
        }
        ui.pair(pay, "Total · " + quantity + " × " + host.money(unit), host.money(o.optLong("total_centavos")));
        if (paid(o) > 0 || "ordered".equals(status) || "delivered".equals(status) || progressStyle) ui.pair(pay, "Paid", host.money(paid(o)));
        ui.pair(pay, "Balance", host.money(balance), balance > 0 ? Ui.AMBER : Ui.INK, 17, ui.p6);
        if (balance > 0 && !"delivered".equals(status) && !"ordered".equals(status)) ui.fill(pay, ui.button("Collect payment", Ui.OUTLINE, 44, 14, 16, () -> payment(o, "order")));
        if ("for_pickup".equals(status)) ui.fill(p, linkButton("Customer not home? Reschedule pickup", () -> reschedule(o)));
        if ("rescheduled".equals(status)) {
            ui.text(p, "Rescheduled pickup: " + longDate(o.optString("rescheduled_date")), 13, 18, Ui.MUTED, ui.o4);
            if (!o.optString("reschedule_notes").isEmpty()) ui.text(p, "Note: " + o.optString("reschedule_notes"), 13, 18, Ui.MUTED, ui.o4);
        }
        Runnable main = mainStep(o);
        if (main != null) {
            host.mainAction(mainLabel(o), "delivered".equals(status) ? Ui.AMBER_BTN : Ui.FILLED, main);
            String note = stepNote(o);
            if (!note.isEmpty()) host.actionNote(note);
        }
    }
    private android.graphics.drawable.GradientDrawable corner(int color, boolean left, boolean right) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color);
        float r = ui.px(6), l = left ? r : 0, rr = right ? r : 0;
        d.setCornerRadii(new float[]{l, l, rr, rr, rr, rr, l, l}); return d;
    }
    private String stepNote(JSONObject o) {
        switch (o.optString("status")) {
            case "ordered": case "rescheduled": return "Moves the order to the pickup list";
            case "picked_up": return "Moves the order to refilling";
            case "refilling": return "Moves " + o.optInt("quantity") + " gallons from empty to filled stock and reserves them for this order";
            case "ready_for_delivery": return "Opens Record delivery";
            case "delivered": return "Last step for this order";
            default: return "";
        }
    }
    /** Figma O3: Owner order detail pane. */
    private void ownerDetail(LinearLayout pane, JSONObject o) {
        String status = o.optString("status");
        long balance = balance(o), unit = o.optLong("unit_price_centavos");
        int delivered = o.optInt("delivered_quantity"), quantity = o.optInt("quantity");
        LinearLayout head = ui.card(pane, 14, 18);
        LinearLayout r = ui.H(head, 14);
        ui.sized(r, ui.avatar(o.optString("customer_name"), 52, 18.72f), 52, 52);
        LinearLayout names = ui.vbox(2); ui.grow(r, names);
        ui.text(names, o.optString("customer_name"), 22, 33, Ui.INK, ui.p6);
        ui.text(names, o.optString("reference") + " · " + title(o.optString("customer_type")), 13, 18, Ui.MUTED, ui.o4);
        Runnable main = mainStep(o);
        if (main != null) ui.hug(r, ui.button(mainLabel(o), "delivered".equals(status) ? Ui.AMBER_BTN : Ui.FILLED, 48, 12, 16, () -> host.guarded(main)));
        ui.progress(head, status, true);
        LinearLayout both = ui.H(pane, 14); both.setGravity(Gravity.TOP);
        LinearLayout gallons = ui.vbox(10); ui.bg(gallons, Ui.WHITE, Ui.BORDER, 16); ui.pad(gallons, 18, 18); ui.grow(both, gallons);
        LinearLayout pay = ui.vbox(10); ui.bg(pay, Ui.WHITE, Ui.BORDER, 16); ui.pad(pay, 18, 18); ui.grow(both, pay);
        ui.text(gallons, "Gallons", 16, 24, Ui.INK, ui.p6);
        ui.pair(gallons, "Ordered", Integer.toString(o.optInt("expected_quantity", quantity)));
        ui.pair(gallons, "Picked up", Integer.toString(o.optInt("collected_quantity")));
        ui.pair(gallons, "Delivered", Integer.toString(delivered));
        ui.pair(gallons, "Still to deliver", Integer.toString(pending(o)), pending(o) > 0 ? Ui.AMBER : Ui.INK, 17, ui.p6);
        LinearLayout ph = ui.H(pay, 8);
        ui.grow(ph, ui.tv("Payment", 16, 24, Ui.INK, ui.p6));
        ui.hug(ph, ui.paymentChip(o.optString("payment_status"), false));
        ui.pair(pay, "Total · " + quantity + " × " + host.money(unit), host.money(o.optLong("total_centavos")));
        ui.pair(pay, "Paid", host.money(paid(o)));
        ui.pair(pay, "Balance", host.money(balance), balance > 0 ? Ui.AMBER : Ui.INK, 17, ui.p6);
        if (balance > 0) ui.fill(pay, ui.button("Collect payment", Ui.OUTLINE, 40, 12, 15, () -> payment(o, "order")));
        if ("rescheduled".equals(status) || pending(o) > 0) {
            LinearLayout note = ui.card(pane, 6, 18);
            if ("rescheduled".equals(status)) {
                ui.text(note, "Rescheduled pickup · " + longDate(o.optString("rescheduled_date")), 16, 24, Ui.INK, ui.p6);
                if (!o.optString("reschedule_notes").isEmpty()) ui.text(note, o.optString("reschedule_notes"), 14, 19, Ui.MUTED, ui.o4);
            } else {
                long days = daysSince(o.optString("business_date"));
                ui.text(note, "Pending delivery", 16, 24, Ui.INK, ui.p6);
                ui.text(note, "Original date " + longDate(o.optString("business_date")) + " · " + days + (days == 1 ? " day" : " days") + " waiting · " + pending(o) + " gallons left · " + o.optInt("reserved_quantity") + " reserved", 14, 19, Ui.MUTED, ui.o4);
            }
        }
        LinearLayout timeline = ui.card(pane, 8, 18);
        ui.text(timeline, "Timeline", 16, 24, Ui.INK, ui.p6);
        JSONArray events = o.optJSONArray("timeline");
        if (events == null || events.length() == 0) ui.text(timeline, "Order created · " + host.timestamp(o.optString("created_at")) + " · " + o.optString("recorded_by_name", ""), 14, 19, Ui.INK, ui.o4);
        else for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); if (e == null) continue;
            LinearLayout line = ui.H(timeline, 16); line.setGravity(Gravity.TOP);
            TextView when = ui.tv(stamp(e.optString("occurred_at")), 13, 20, Ui.MUTED, ui.p5);
            when.setMinWidth(ui.px(82)); when.setSingleLine(true); ui.hug(line, when);
            String who = e.optString("recorded_by_name");
            ui.grow(line, ui.tv(eventText(e) + (who.isEmpty() ? "" : " · " + who), 14, 19, Ui.INK, ui.o4));
        }
    }
    private static String eventText(JSONObject e) {
        String action = e.optString("action"), status = e.optString("status");
        switch (action) {
            case "create_order": return "Order created";
            case "record_pickup": return "Picked up" + (e.has("quantity") ? " " + e.optInt("quantity") + " gallons" : "");
            case "record_delivery": return "Delivered" + (e.has("quantity") ? " " + e.optInt("quantity") + " gallons" : "");
            case "record_payment": return "Payment recorded";
            case "reschedule_pickup": return "Pickup rescheduled";
            case "transition_order": return Rules.label(status);
            default: return Rules.label(action) + (status.isEmpty() ? "" : " · " + Rules.label(status));
        }
    }
    private String mainLabel(JSONObject o) {
        if ("delivered".equals(o.optString("status"))) return "Collect " + host.money(balance(o));
        return nextLabel(o.optString("status"));
    }
    /** The single next-step action of an order; null when nothing is left to do. */
    private Runnable mainStep(JSONObject o) {
        switch (o.optString("status")) {
            case "ordered": case "rescheduled": return () -> transition(o, "for_pickup");
            case "for_pickup": return () -> pickup(o);
            case "picked_up": return () -> transition(o, "refilling");
            case "refilling": return () -> transition(o, "ready_for_delivery");
            case "ready_for_delivery": case "undelivered": return () -> delivery(o);
            case "delivered": return balance(o) > 0 ? () -> payment(o, "order") : null;
            default: return null;
        }
    }
    private void transition(JSONObject order, String status) {
        host.command("transition_order", json("order_id", id(order), "status", status), saved -> { order(id(order)); host.info("Order updated", order.optString("reference") + " · " + Rules.label(status)); });
    }

    // ================================================================================================ pickup, reschedule, delivery
    private void pickup(JSONObject o) {
        if (owner()) { ownerOrders("Record Pickup", id(o), id(o), 1, pane -> pickupForm(pane, o)); return; }
        pickupForm(page("Record Pickup", id(o)), o);
    }
    /** Figma S11 / O4: actual collected gallons, shortfall preview and adjusted charge. */
    private void pickupForm(LinearLayout p, JSONObject o) {
        boolean ow = owner();
        int expected = o.optInt("expected_quantity", o.optInt("quantity")); long unit = o.optLong("unit_price_centavos");
        if (ow) ownerFormHeader(p, "Record pickup · " + o.optString("customer_name"), o.optString("reference") + " · " + title(o.optString("customer_type")) + " · Expected " + expected + " gallons", o.optString("status"), "Reschedule", () -> reschedule(o));
        else staffHeader(p, o.optString("customer_name"), o.optString("reference") + " · Expected " + expected + " gallons");
        LinearLayout card = ui.card(p, 16, ow ? 18 : 16);
        ui.label(card, "Gallons collected");
        int[] picks = new int[Math.min(4, expected)]; String[] names = new String[picks.length];
        for (int i = 0; i < picks.length; i++) { picks[i] = expected - i; names[i] = i == 0 ? "All " + expected : Integer.toString(picks[i]); }
        EditText collected = host.quantity(card, "Actual collected gallons", "", picks, names, "of " + expected + " expected", !ow, 8, null);
        collected.setHint("0");
        LinearLayout shortBanner = ui.banner(p, Ui.WARNING, "", "", ow); shortBanner.setVisibility(View.GONE);
        Runnable refresh = () -> {
            try {
                int q = quantity(collected);
                if (q > expected) { collected.setTextColor(Ui.DANGER); ui.setBanner(shortBanner, Ui.ERROR, "Collected quantity cannot exceed the expected quantity.", null, ow); shortBanner.setVisibility(View.VISIBLE); return; }
                collected.setTextColor(Ui.INK);
                if (q < expected) {
                    long total = Math.multiplyExact((long) q, unit);
                    ui.setBanner(shortBanner, Ui.WARNING, (expected - q) + (expected - q == 1 ? " gallon short" : " gallons short"), "The order becomes " + q + " × " + host.money(unit) + " = " + host.money(total) + ". The original request of " + expected + " stays on record.", ow);
                    shortBanner.setVisibility(View.VISIBLE);
                } else shortBanner.setVisibility(View.GONE);
            } catch (Exception e) { shortBanner.setVisibility(View.GONE); }
        };
        watch(collected, refresh);
        if (!ow) ui.fill(p, linkButton("Not collected? Reschedule pickup", () -> reschedule(o)));
        TextView[] note = {null};
        Runnable save = () -> valid(() -> {
            int q;
            try { q = quantity(collected); } catch (IllegalArgumentException e) { host.fieldError(collected, e.getMessage()); return; }
            if (q > expected) { host.fieldError(collected, "Collected quantity cannot exceed the expected quantity."); return; }
            if (Math.multiplyExact((long) q, unit) < paid(o)) { host.fieldError(collected, "Adjusted total cannot be less than the amount already paid."); return; }
            host.command("record_pickup", json("order_id", id(o), "quantity", q), saved -> {
                String detail = o.optString("reference") + " · " + q + " gallons collected · order now " + q + " × " + host.money(unit) + " = " + host.money(Math.multiplyExact((long) q, unit)) + " · " + q + " empty gallons added to stock";
                // A saved pickup goes straight into refilling (no separate "Start refilling" tap).
                host.command("transition_order", json("order_id", id(o), "status", "refilling"), moved -> {
                    order(id(o));
                    host.info("Pickup recorded · now Refilling", detail);
                });
            });
        });
        if (ow) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, () -> order(id(o))); host.ownerAction(row, "Save pickup", Ui.FILLED, save); }
        else {
            LinearLayout dock = (LinearLayout) host.mainAction("Save pickup", Ui.FILLED, save).getParent();
            note[0] = ui.tv("", 13, 18, Ui.MUTED, ui.o4); note[0].setGravity(Gravity.CENTER); ui.fill(dock, note[0]);
            Runnable updateNote = () -> { try { int q = quantity(collected); note[0].setText("Adds " + q + (q == 1 ? " empty gallon" : " empty gallons") + " to stock"); note[0].setVisibility(View.VISIBLE); } catch (Exception e) { note[0].setVisibility(View.GONE); } };
            watch(collected, updateNote); updateNote.run();
        }
    }
    /** Owner form header card: title, subtitle, optional secondary action and the stage stepper (O4). */
    private void ownerFormHeader(LinearLayout p, String titleText, String sub, String status, String action, Runnable run) {
        LinearLayout head = ui.card(p, 10, 18);
        LinearLayout r = ui.H(head, 8);
        LinearLayout names = ui.vbox(2); ui.grow(r, names);
        ui.text(names, titleText, 22, 33, Ui.INK, ui.p6);
        ui.text(names, sub, 13, 18, Ui.MUTED, ui.o4);
        if (action != null) ui.hug(r, ui.button(action, Ui.OUTLINE, 44, 12, 15, run));
        if (status != null) ui.progress(head, status, true);
    }
    private void reschedule(JSONObject o) {
        if (owner()) { ownerOrders("Reschedule Pickup", id(o), id(o), 1, pane -> rescheduleForm(pane, o)); return; }
        rescheduleForm(page("Reschedule Pickup", id(o)), o);
    }
    /** Figma S12 / O4f: new pickup date (quick picks), optional note. */
    private void rescheduleForm(LinearLayout p, JSONObject o) {
        boolean ow = owner();
        if (ow) ownerFormHeader(p, "Reschedule pickup · " + o.optString("customer_name"), o.optString("reference") + " · Pickup not done", o.optString("status"), null, null);
        else staffHeader(p, o.optString("customer_name"), o.optString("reference") + " · Pickup not done");
        LinearLayout card = ui.card(p, 14, ow ? 18 : 16);
        ui.label(card, "New pickup date");
        LocalDate today = LocalDate.parse(host.today());
        EditText[] next = new EditText[1];
        Ui.Tabs[] quick = new Ui.Tabs[1];
        quick[0] = ui.tabs(card, new String[]{"Today", "Tomorrow", "In 3 days", "Pick date"}, 1, true, ow, 8, i -> {
            if (i == 3) { next[0].performClick(); return; }
            setIso(next[0], today.plusDays(i == 0 ? 0 : i == 1 ? 1 : 3).toString(), false); host.clearFieldError(next[0]);
        });
        next[0] = date(card, "Date", today.plusDays(1).toString());
        EditText notes = host.input(card, "Note (optional)", o.optString("reschedule_notes"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        Runnable save = () -> valid(() -> {
            LocalDate d;
            try { d = LocalDate.parse(iso(next[0])); } catch (Exception e) { host.fieldError(next[0], "Select today or a future date."); return; }
            if (d.isBefore(today)) { host.fieldError(next[0], "Select today or a future date."); return; }
            Rules.remarks(value(notes));
            host.command("reschedule_pickup", json("order_id", id(o), "scheduled_date", d.toString(), "notes", value(notes)), saved -> { order(id(o)); host.info("Order rescheduled", o.optString("reference") + " · pickup moved to " + longDate(d.toString())); });
        });
        if (ow) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, () -> order(id(o))); host.ownerAction(row, "Save pickup result", Ui.FILLED, save); }
        else { host.mainAction("Save new date", Ui.FILLED, save); host.actionNote("The order stays in the pickup list with the new date"); }
    }
    private void delivery(JSONObject o) {
        if (owner()) { ownerOrders("Record Delivery", id(o), id(o), 3, pane -> deliveryForm(pane, o)); return; }
        deliveryForm(page("Record Delivery", id(o)), o);
    }
    /** Figma S13 / O4h: gallons delivered this trip, partial/complete preview, reservation check. */
    private void deliveryForm(LinearLayout p, JSONObject o) {
        boolean ow = owner();
        int remaining = pending(o), delivered = o.optInt("delivered_quantity"), reserved = o.optInt("reserved_quantity", remaining);
        if (ow) ownerFormHeader(p, "Record delivery · " + o.optString("customer_name"), o.optString("reference") + " · " + title(o.optString("customer_type")) + " · " + delivered + " of " + o.optInt("quantity") + " delivered", o.optString("status"), null, null);
        else staffHeader(p, o.optString("customer_name"), o.optString("reference") + " · " + delivered + " of " + o.optInt("quantity") + " delivered");
        LinearLayout card = ui.card(p, 16, ow ? 18 : 16);
        ui.label(card, "Gallons delivered now");
        int[] picks = new int[Math.min(3, remaining)]; String[] pickNames = new String[picks.length];
        for (int i = 0; i < picks.length; i++) { picks[i] = remaining - i; pickNames[i] = i == 0 ? "All " + remaining : Integer.toString(picks[i]); }
        EditText trip = host.quantity(card, "Gallons delivered this trip", "", picks, pickNames, "of " + remaining + " remaining", !ow, 8, null);
        trip.setHint("0");
        LinearLayout status = ui.banner(p, Ui.SUCCESS, "", "", ow); status.setVisibility(View.GONE);
        Runnable refresh = () -> {
            try {
                int q = quantity(trip); if (q > remaining) throw new IllegalArgumentException();
                boolean all = q == remaining;
                int left = remaining - q, total = o.optInt("quantity");
                String who = host.recorder() + " (" + (host.isOwner() ? "Owner" : "Staff") + ")";
                if (all) ui.setBanner(status, Ui.SUCCESS, "Order will be Delivered", ow ? "0 gallons pending after saving · reserved stock for this order: " + reserved + " · cumulative delivered " + (delivered + q) + " of " + total + " · recorded by " + who + ", " + longDate(host.today()) : "0 gallons pending after saving. Reserved stock for this order: " + reserved + ".", ow);
                else if (ow) ui.setBanner(status, Ui.WARNING, "Order becomes Undelivered", left + (left == 1 ? " gallon stays pending" : " gallons stay pending") + " · the remaining reservation is kept · cumulative delivered " + (delivered + q) + " of " + total + " · recorded by " + who, true);
                else ui.setBanner(status, Ui.WARNING, left + (left == 1 ? " gallon stays pending" : " gallons stay pending"), "Order becomes Undelivered. The remaining reservation is kept.", false);
                status.setVisibility(View.VISIBLE);
            } catch (Exception e) { status.setVisibility(View.GONE); }
        };
        watch(trip, refresh);
        Runnable save = () -> valid(() -> {
            int q;
            try { q = quantity(trip); } catch (IllegalArgumentException e) { host.fieldError(trip, e.getMessage()); return; }
            if (q > remaining) { host.fieldError(trip, "Delivered quantity cannot exceed the remaining " + remaining + " gallons."); return; }
            if (o.has("reserved_quantity") && q > o.optInt("reserved_quantity")) { ui.setBanner(status, Ui.ERROR, "Not enough reserved gallons for this order.", ow ? "Reserved stock for this order: " + o.optInt("reserved_quantity") + " · nothing was saved" : "Reserved stock for this order: " + o.optInt("reserved_quantity") + ". Nothing was saved.", ow); status.setVisibility(View.VISIBLE); return; }
            host.command("record_delivery", json("order_id", id(o), "quantity", q), saved -> {
                order(id(o));
                host.info("Delivery recorded", o.optString("reference") + " · " + q + " gallons delivered · " + (q == remaining ? "all " + (delivered + q) + " delivered" : (remaining - q) + " still to deliver"));
            });
        });
        if (ow) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, () -> order(id(o))); host.ownerAction(row, "Save delivery", Ui.FILLED, save); }
        else host.mainAction("Save delivery", Ui.FILLED, save);
    }

    // ================================================================================================ walk-in sale
    public void pos() {
        if (owner()) { ownerOrders("Walk-in Sale / POS", "", null, -1, this::posForm); return; }
        posForm(page("Walk-in Sale / POS"));
    }
    /** Figma P1–P7 (Staff) and O6–O6g (Owner). */
    private void posForm(LinearLayout p) {
        boolean ow = owner();
        final long[] available = {-1};
        TextView stock = ui.chip("Checking stock…", Ui.TONAL, Ui.PRIMARY);
        EditText name;
        if (ow) {
            LinearLayout head = ui.card(p, 12, 18);
            LinearLayout r = ui.H(head, 8);
            ui.grow(r, ui.tv("Walk-in sale", 22, 33, Ui.INK, ui.p6));
            ui.hug(r, stock);
            name = host.input(head, "Customer name", "", TEXT);
        } else {
            LinearLayout r = ui.H(p, 8);
            ui.grow(r, ui.tv("Counter sale · no profile needed", 13, 18, Ui.MUTED, ui.o4));
            ui.hug(r, stock);
            name = host.input(p, "Customer name", "", TEXT);
        }
        query("inventory", json(), response -> { JSONObject i = nested(response, "inventory"); available[0] = i.optLong("filled_available", i.optLong("available_filled", -1)); stock.setText(available[0] < 0 ? "Stock checked on save" : available[0] + " gallons available"); });
        LinearLayout pair = ow ? ui.H(p, 14) : p;
        if (ow) pair.setGravity(Gravity.TOP);
        LinearLayout gCard = ow ? ui.vbox(12) : ui.card(p, 14, 16);
        LinearLayout cCard = ow ? ui.vbox(12) : null;
        if (ow) { ui.bg(gCard, Ui.WHITE, Ui.BORDER, 16); ui.pad(gCard, 18, 18); ui.grow(pair, gCard); }
        ui.label(gCard, ow ? "Gallons · ₱35.00 each" : "Gallons");
        EditText gallons = host.quantity(gCard, "Gallon quantity", "", new int[]{1, 2, 3, 5, 10}, new String[]{"1", "2", "3", "5", "10"}, ow ? "gallons" : "× ₱35.00 each", true, 8, null);
        if (ow) ui.size(gallons, 48, 72);
        gallons.setHint("0");
        if (ow) { cCard = ui.vbox(12); ui.bg(cCard, Ui.WHITE, Ui.BORDER, 16); ui.pad(cCard, 18, 18); ui.grow(pair, cCard); }
        else cCard = ui.card(p, 14, 16);
        ui.label(cCard, "Cash received");
        EditText cash = host.inputPlain(cCard, "Cash received", "", InputType.TYPE_CLASS_TEXT); cash.setHint("₱0.00");
        LinearLayout cashField = (LinearLayout) cash.getParent();
        String[] cashLabels = {"Exact", "₱100", "₱200", "₱500"};
        long[] cashValues = {-1, 10000, 20000, 50000};
        Ui.Tabs cashPicks = ui.tabs(cCard, cashLabels, -1, true, ow, 8, i -> {
            if (i == 0) { try { cash.setText(pesoText(Math.multiplyExact((long) quantity(gallons), 3500))); } catch (IllegalArgumentException e) { host.fieldError(gallons, e.getMessage()); } }
            else cash.setText(pesoText(cashValues[i]));
        });
        cCard.removeView(cashField); cCard.addView(cashField, 1);
        if (ow) { TextView[] rec = ui.pair(cCard, "Recorded by", host.recorder()); rec[1].setSingleLine(true); rec[1].setEllipsize(android.text.TextUtils.TruncateAt.END); }
        // Totals: Owner green card under the inputs, Staff in the dock above the button
        LinearLayout totalsHost;
        TextView[] stockAfter = {null};
        if (ow) { totalsHost = ui.card(p, 8, 18); ui.bg(totalsHost, Ui.GREEN_BG, 0, 16); }
        else totalsHost = host.dockBox();
        LinearLayout totals = ui.H(totalsHost, 0); totals.setGravity(Gravity.TOP);
        LinearLayout tl = ui.vbox(0); ui.hug(totals, tl);
        ui.hug(tl, ui.tv("Total", 13, 18, Ui.MUTED, ui.o4));
        TextView totalView = ui.hugText(tl, host.money(0), ow ? 28 : 24, Ui.INK, ui.p6);
        ui.spacer(totals);
        LinearLayout tr = ui.vbox(0); tr.setGravity(ow ? Gravity.CENTER_HORIZONTAL : Gravity.END); ui.hug(totals, tr);
        TextView changeLabel = ui.hugText(tr, "Change", 13, Ui.MUTED, ui.o4);
        TextView changeView = ui.hugText(tr, "–", ow ? 28 : 24, Ui.GREEN, ui.p6);
        if (ow) {
            ui.spacer(totals);
            LinearLayout st = ui.vbox(0); st.setGravity(Gravity.END); ui.hug(totals, st);
            ui.hug(st, ui.tv("Stock after saving", 13, 18, Ui.MUTED, ui.o4));
            stockAfter[0] = ui.hugText(st, "–", 28, Ui.INK, ui.p6);
        }
        TextView complete; TextView creditNote = null;
        Runnable[] submit = new Runnable[1];
        if (ow) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, this::orders); complete = host.ownerAction(row, "Complete sale", Ui.FILLED, () -> submit[0].run()); }
        else {
            complete = ui.dockButton("Complete sale", Ui.FILLED, () -> host.guarded(submit[0])); ui.fill(totalsHost, complete);
            creditNote = ui.tv("", 13, 18, Ui.MUTED, ui.o4); creditNote.setGravity(Gravity.CENTER); creditNote.setVisibility(View.GONE); ui.fill(totalsHost, creditNote);
        }
        final TextView note = creditNote;
        Runnable recompute = () -> {
            long total = 0, tendered = -1; boolean ok = true; int q = 0;
            try { q = quantity(gallons); total = Math.multiplyExact((long) q, 3500); totalView.setText(host.money(total)); host.clearFieldError(gallons); if (available[0] >= 0 && q > available[0]) { host.fieldError(gallons, "Not enough filled gallons."); ok = false; } }
            catch (IllegalArgumentException e) { totalView.setText(host.money(0)); ok = false; if (!value(gallons).isEmpty()) host.fieldError(gallons, e.getMessage()); }
            String raw = cleanMoney(value(cash));
            try { tendered = cents(cash, "Enter a valid cash amount."); host.clearFieldError(cash); if (tendered < 0) { host.fieldError(cash, "Cash received cannot be negative."); ok = false; } }
            catch (IllegalArgumentException e) { ok = false; if (!raw.isEmpty()) host.fieldError(cash, e.getMessage()); }
            int pick = -1; for (int i = 1; i < cashValues.length; i++) if (tendered == cashValues[i]) pick = i; if (tendered >= 0 && tendered == total && total > 0) pick = 0;
            cashPicks.select(pick, false);
            boolean credit = ok && tendered < total;
            changeLabel.setText(credit ? "Credit (unpaid)" : "Change");
            if (ok && !credit) { changeView.setText(host.money(tendered - total)); changeView.setTextColor(Ui.GREEN); }
            else if (credit) { changeView.setText(host.money(total - tendered)); changeView.setTextColor(Ui.AMBER); }
            else { changeView.setText("–"); changeView.setTextColor(Ui.GREEN); }
            if (stockAfter[0] != null) stockAfter[0].setText(available[0] >= 0 && ok ? Long.toString(available[0] - q) : "–");
            complete.setText(credit ? "Save as credit · " + host.money(total - tendered) : "Complete sale");
            ui.style(complete, ok && total > 0 ? (credit ? Ui.AMBER_BTN : Ui.FILLED) : Ui.DISABLED);
            if (note != null) { note.setVisibility(credit ? View.VISIBLE : View.GONE); if (credit) note.setText("Paid " + host.money(Math.max(0, tendered)) + " now · " + host.money(total - tendered) + " stays as a balance"); }
        };
        watch(gallons, recompute); watch(cash, recompute);
        submit[0] = () -> valid(() -> {
            if (value(name).isEmpty()) { host.fieldError(name, "Enter the customer name."); return; }
            int q; try { q = quantity(gallons); } catch (IllegalArgumentException e) { host.fieldError(gallons, e.getMessage()); return; }
            long tendered; try { tendered = cents(cash, "Enter a valid cash amount."); } catch (IllegalArgumentException e) { host.fieldError(cash, e.getMessage()); return; }
            if (tendered < 0) { host.fieldError(cash, "Cash received cannot be negative."); return; }
            if (available[0] >= 0 && q > available[0]) { host.fieldError(gallons, "Not enough filled gallons."); return; }
            long total = Math.multiplyExact((long) q, 3500);
            JSONObject payload = json("customer_name", value(name), "quantity", q, "cash_centavos", tendered, "confirm_credit", tendered < total);
            String customer = value(name);
            host.command("create_sale", payload, saved -> {
                JSONObject sale = nested(saved, "sale");
                long bal = balance(sale);
                String st = bal > 0 ? (tendered > 0 ? "Partially paid" : "Unpaid") : "Paid";
                pos();
                host.info("Sale " + sale.optString("reference") + " saved", customer + " · " + q + " gallons · " + st + (bal > 0 ? " · Credit " + host.money(bal) : " · Change " + host.money(Math.max(0, tendered - total))) + (available[0] >= 0 ? " · Stock " + available[0] + " → " + (available[0] - q) : ""));
            });
        });
        recompute.run();
    }

    // ================================================================================================ payments
    public void payments() { payments(null, null); }
    private void payments(String customerId) { payments(customerId, null); }
    private void payments(String customerId, String openReference) {
        boolean ow = owner();
        LinearLayout p = page("Payments", customerId == null ? "" : customerId);
        TextView outstanding = ui.chip("", Ui.AMBER_BG, Ui.AMBER); outstanding.setVisibility(View.GONE);
        if (ow) ui.hug(host.titleActions(), outstanding);
        LinearLayout list, pane = null;
        if (ow) { LinearLayout[] panes = host.panes(380); list = panes[0]; pane = ui.V(panes[1], 14); host.bannerAt(panes[1], 0); }
        else list = p;
        final LinearLayout form = pane;
        EditText search = search(list, ow ? "Search customer or reference" : "Search customer, order or sale reference");
        int[] filter = {0};
        LinearLayout rows = ui.vbox(14);
        Runnable[] load = new Runnable[1];
        ui.tabs(list, new String[]{"Owes ₱", "All payments"}, 0, false, ow, 6, i -> { filter[0] = i; load[0].run(); });
        ui.fill(list, rows);
        long[] request = {0};
        String[] openRef = {openReference == null ? "" : openReference};
        load[0] = () -> {
            long requested = ++request[0];
            JSONObject f = json("search", value(search)); if (customerId != null) try { f.put("customer_id", customerId); } catch (Exception ignored) { }
            query("orders", f, orders -> query("sales", f, sales -> query("payments", f, receipts -> {
                if (requested != request[0]) return;
                rows.removeAllViews();
                List<JSONObject[]> due = new ArrayList<>();
                long sum = 0;
                JSONArray oa = items(orders), sa = items(sales);
                for (int i = 0; i < oa.length(); i++) { JSONObject x = oa.optJSONObject(i); if (x != null && balance(x) > 0) { due.add(new JSONObject[]{x, json("kind", "order")}); sum += balance(x); } }
                for (int i = 0; i < sa.length(); i++) { JSONObject x = sa.optJSONObject(i); if (x != null && balance(x) > 0) { due.add(new JSONObject[]{x, json("kind", "sale")}); sum += balance(x); } }
                outstanding.setText("Outstanding " + host.money(sum)); outstanding.setVisibility(View.VISIBLE);
                if (filter[0] == 1) { renderReceipts(rows, items(receipts), true); return; }
                if (due.isEmpty()) empty(rows, "No outstanding balances.");
                JSONObject[] first = null;
                for (JSONObject[] d : due) {
                    JSONObject x = d[0]; String kind = d[1].optString("kind");
                    if (first == null) first = d;
                    boolean on = x.optString("reference").equals(openRef[0]);
                    LinearLayout item = ui.H(rows, 8); ui.bg(item, on ? Ui.TONAL : Ui.WHITE, on ? Ui.PRIMARY : Ui.BORDER, ow ? 12 : 16); ui.pad(item, ow ? 12 : 16, ow ? 12 : 14); item.setClickable(true);
                    LinearLayout names = ui.vbox(2); ui.grow(item, names);
                    ui.text(names, x.optString("customer_name"), 15, 22, Ui.INK, ui.p6);
                    ui.text(names, x.optString("reference") + ("sale".equals(kind) ? " · walk-in" : ""), 12, 16, Ui.MUTED, ui.o4);
                    LinearLayout end = ui.vbox(4); end.setGravity(Gravity.END); ui.hug(item, end);
                    ui.hug(end, ui.paymentChip(x.optString("payment_status"), true));
                    ui.hug(end, ui.tv(host.money(balance(x)) + " due", 12, 18, Ui.AMBER, ui.p5));
                    item.setContentDescription(x.optString("reference") + " " + x.optString("customer_name") + " Balance " + host.money(balance(x)));
                    item.setOnClickListener(v -> { if (form != null) { openRef[0] = x.optString("reference"); load[0].run(); form.removeAllViews(); paymentForm(form, x, kind); } else payment(x, kind); });
                }
                if (form != null && !openRef[0].isEmpty() && form.getChildCount() == 0) for (JSONObject[] d : due) if (d[0].optString("reference").equals(openRef[0])) { paymentForm(form, d[0], d[1].optString("kind")); break; }
                if (form != null && openRef[0].isEmpty() && first != null) { openRef[0] = first[0].optString("reference"); JSONObject[] fx = first; load[0].run(); form.removeAllViews(); paymentForm(form, fx[0], fx[1].optString("kind")); }
            })));
        };
        watch(search, load[0]); load[0].run();
    }
    private void payment(JSONObject target, String kind) {
        if (owner()) { payments(null, target.optString("reference")); return; }
        LinearLayout p = page("Record Payment", target.optString("reference"));
        paymentForm(p, target, kind);
    }
    /** Figma S15 (Staff) and O8 (Owner): collect a payment against one reference. */
    private void paymentForm(LinearLayout p, JSONObject target, String kind) {
        boolean ow = owner();
        long due = balance(target);
        LinearLayout amountCard, afterCard = null;
        if (ow) {
            LinearLayout head = ui.card(p, 12, 18);
            LinearLayout r = ui.H(head, 8);
            LinearLayout names = ui.vbox(2); ui.grow(r, names);
            ui.text(names, "Collect payment · " + target.optString("customer_name"), 22, 33, Ui.INK, ui.p6);
            ui.text(names, target.optString("reference") + " · Total " + host.money(target.optLong("total_centavos")) + " · Paid " + host.money(paid(target)), 13, 18, Ui.MUTED, ui.o4);
            LinearLayout bal = ui.hbox(0); ui.hug(r, bal);
            ui.hug(bal, ui.tv("Balance", 14, 19, Ui.MUTED, ui.o4));
            ui.hug(bal, ui.tv(host.money(due), 26, 39, Ui.AMBER, ui.p6));
            LinearLayout both = ui.H(p, 14); both.setGravity(Gravity.TOP);
            amountCard = ui.vbox(12); ui.bg(amountCard, Ui.WHITE, Ui.BORDER, 16); ui.pad(amountCard, 18, 18); ui.grow(both, amountCard);
            afterCard = ui.vbox(8); ui.bg(afterCard, Ui.WHITE, Ui.BORDER, 16); ui.pad(afterCard, 18, 18); ui.grow(both, afterCard);
        } else {
            staffHeader(p, target.optString("customer_name"), target.optString("reference") + " · Total " + host.money(target.optLong("total_centavos")) + " · Paid " + host.money(paid(target)));
            amountCard = ui.card(p, 12, 16);
            ui.pair(amountCard, "Balance due", host.money(due), Ui.AMBER, 22, ui.p6);
        }
        ui.label(amountCard, "Amount received");
        List<Long> amounts = new ArrayList<>(); amounts.add(due);
        for (long c : new long[]{50000, 20000, 10000, 5000, 2000}) if (c < due && amounts.size() < 3) amounts.add(c);
        String[] labels = new String[amounts.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = i == 0 ? "Full " + wholePeso(due) : wholePeso(amounts.get(i));
        EditText[] amountRef = new EditText[1];
        Ui.Tabs picks = ui.tabs(amountCard, labels, -1, true, ow, 8, i -> amountRef[0].setText(pesoText(amounts.get(i))));
        EditText amount = host.inputPlain(amountCard, "Payment amount", "", InputType.TYPE_CLASS_TEXT); amount.setHint("₱0.00");
        amountRef[0] = amount;
        ui.pair(amountCard, "Method", "Cash");
        if (ow) {
            ui.pair(amountCard, "Payment date", "Today · " + longDate(host.today()));
            TextView[] rec = ui.pair(amountCard, "Recorded by", host.recorder()); rec[1].setSingleLine(true); rec[1].setEllipsize(android.text.TextUtils.TruncateAt.END);
        }
        TextView[] remaining = null, result = null;
        LinearLayout receipt;
        if (ow) {
            ui.label(afterCard, "After saving");
            remaining = ui.pair(afterCard, "Balance", "—", Ui.AMBER, 22, ui.p6);
            result = ui.pair(afterCard, "Status", "—");
            receipt = ui.banner(afterCard, Ui.INFO, "Receipt reference", "Shown after you save.", true);
        } else {
            receipt = ui.banner(p, Ui.INFO, "Balance after payment: —", "Enter the amount received.", false);
        }
        final TextView[] rem = remaining, res = result;
        watch(amount, () -> {
            int sel = -1;
            try {
                long c = cents(amount, ""); for (int i = 0; i < amounts.size(); i++) if (amounts.get(i) == c) sel = i;
                if (c <= 0 || c > due) throw new IllegalArgumentException();
                String status = c == due ? "Paid" : "Partially paid";
                if (rem != null) { rem[1].setText(host.money(due - c)); res[1].setText(status); }
                else ui.setBanner(receipt, Ui.INFO, "Balance after payment: " + host.money(due - c), "Status becomes " + status + ".", false);
            } catch (Exception e) {
                if (rem != null) { rem[1].setText("—"); res[1].setText("—"); }
                else ui.setBanner(receipt, Ui.INFO, "Balance after payment: —", "Enter the amount received.", false);
            }
            picks.select(sel, false);
        });
        Runnable save = () -> valid(() -> {
            long c;
            try { c = cents(amount, "Enter an amount greater than zero and not above the balance."); } catch (IllegalArgumentException e) { host.fieldError(amount, e.getMessage()); return; }
            if (c > due) { host.fieldError(amount, "Payment cannot exceed the " + host.money(due) + " balance."); return; }
            if (c <= 0) { host.fieldError(amount, "Enter an amount greater than zero and not above the balance."); return; }
            host.command("record_payment", json("reference", target.optString("reference"), "amount_centavos", c), done -> {
                JSONObject r = nested(done, "payment");
                long left = r.optLong("balance_centavos", due - c);
                String status = title(r.optString("payment_status", left == 0 ? "paid" : "partially_paid"));
                paymentRecorded(target, kind, r, c, left, status);
            });
        });
        if (ow) { LinearLayout row = host.actionRow(p); host.ownerAction(row, "Cancel", Ui.OUTLINE, () -> p.removeAllViews()); host.ownerAction(row, "Save payment", Ui.FILLED, save); }
        else host.mainAction("Save payment", Ui.FILLED, save);
        if (ow) {
            LinearLayout history = ui.card(p, 6, 18);
            ui.text(history, "Payment history", 16, 24, Ui.INK, ui.p6);
            query("payments", json("search", target.optString("reference")), r -> renderReceipts(history, items(r), false));
        }
    }
    /** Figma S16 / O8d. */
    private void paymentRecorded(JSONObject target, String kind, JSONObject receipt, long amount, long left, String status) {
        String detail = host.money(amount) + " cash · Balance " + host.money(left) + " · " + status;
        if (owner()) { payments(); host.info("Payment recorded. · " + receipt.optString("reference"), target.optString("reference") + " · " + detail); return; }
        if ("order".equals(kind)) {
            LinearLayout p = page("Order Details", id(target));
            loadOrder(id(target), o -> { staffDetail(p, o); host.info("Payment recorded. · " + receipt.optString("reference"), detail); });
        } else {
            LinearLayout p = page("Payment recorded", target.optString("reference"));
            staffHeader(p, target.optString("customer_name"), target.optString("reference") + " · Walk-in");
            LinearLayout c = ui.card(p, 8, 16);
            ui.pair(c, "Total", host.money(target.optLong("total_centavos")));
            ui.pair(c, "Paid", host.money(target.optLong("total_centavos") - left));
            ui.pair(c, "Balance", host.money(left), left > 0 ? Ui.AMBER : Ui.INK, 17, ui.p6);
            host.info("Payment recorded. · " + receipt.optString("reference"), detail);
            host.mainAction("Done", Ui.FILLED, this::payments);
        }
    }
    private void renderReceipts(LinearLayout p, JSONArray receipts, boolean cards) {
        if (receipts.length() == 0) { empty(p, "No payments recorded."); return; }
        for (int i = 0; i < receipts.length(); i++) {
            JSONObject r = receipts.optJSONObject(i); if (r == null) continue;
            String when = shortDate(r.optString("received_at", r.optString("created_at")).length() >= 10 ? r.optString("received_at", r.optString("created_at")).substring(0, 10) : "");
            try { when = OffsetDateTime.parse(r.optString("received_at", r.optString("created_at"))).atZoneSameInstant(Rules.MANILA).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)); } catch (Exception ignored) { }
            String who = r.optString("recorded_by_name", r.optString("recorded_by"));
            String related = r.optString("invoice_reference", r.optString("charge_reference", r.optString("related_reference")));
            if (cards) {
                LinearLayout card = ui.card(p, 6, 14);
                LinearLayout top = ui.H(card, 8);
                ui.grow(top, ui.tv(r.optString("reference"), 15, 22, Ui.INK, ui.p5));
                ui.hug(top, ui.tv(host.money(r.optLong("amount_centavos")), 15, 22, Ui.GREEN, ui.p6));
                ui.text(card, when + " · " + related + " · " + who, 13, 18, Ui.MUTED, ui.o4);
            } else {
                LinearLayout line = ui.H(p, 16);
                ui.hug(line, ui.tv(r.optString("reference"), 13, 20, Ui.INK, ui.p5));
                ui.hug(line, ui.tv(when, 13, 18, Ui.MUTED, ui.o4));
                ui.hug(line, ui.tv(host.money(r.optLong("amount_centavos")), 13, 20, Ui.INK, ui.p5));
                ui.hug(line, ui.tv(who, 13, 18, Ui.MUTED, ui.o4));
            }
        }
    }
}
