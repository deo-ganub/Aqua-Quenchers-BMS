package com.aquaquenchers.bms;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Shared native operational screens (Figma Staff 1–10 and Owner 3–9). Every successful write comes from the authenticated RPC. */
public final class OperationsUi {
    private final MainActivity host;
    private final Ui ui;
    private long generation;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int CASH = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final String[] TYPE_NAMES = {"Household", "Dealer", "Walk-in"};
    private static final String[] TYPE_KEYS = {"household", "dealer", "walk_in"};
    public OperationsUi(MainActivity host) { this.host = host; this.ui = host.ui(); }

    public boolean restore(String route,String entity){
        if(Rules.uuid(entity)){
            switch(route){
                case "Customer Profile":customer(entity);return true;
                case "Order Details":order(entity);return true;
                case "Payments":payments(entity);return true;
                case "Edit Customer":page(route,entity);query("customer",json("id",entity),r->customerForm(nested(r,"customer"),null));return true;
                case "Record Pickup":page(route,entity);query("order",json("id",entity),r->pickup(nested(r,"order")));return true;
                case "Reschedule Pickup":page(route,entity);query("order",json("id",entity),r->reschedule(nested(r,"order")));return true;
                case "Record Delivery":page(route,entity);query("order",json("id",entity),r->delivery(nested(r,"order")));return true;
                case "Sale saved":page(route,entity);query("sale",json("id",entity),r->saleResult(nested(r,"sale")));return true;
            }
        }
        if("Record Payment".equals(route)&&(entity.startsWith("ORD-")||entity.startsWith("SALE-"))){
            String kind=entity.startsWith("ORD-")?"order":"sale";
            page(route,entity);query(kind,json("reference",entity),r->payment(nested(r,kind),kind));return true;
        }
        switch(route){case "Customers":customers();return true;case "Orders":orders();return true;case "Pending gallons / Carry-over Details":pending();return true;case "Walk-in Sale / POS":pos();return true;case "Payments":payments();return true;case "Create Order":newOrder(null);return true;case "Add Customer":customerForm(null,null);return true;}return false;}

    // ---------------------------------------------------------------- helpers
    private LinearLayout page(String title,String id){generation++;return host.page(title,id);}
    private LinearLayout page(String title) { generation++; return host.page(title); }
    private void query(String resource, JSONObject data, Consumer<JSONObject> action) {
        long requestedGeneration = generation;
        host.query(resource, data, result -> { if (requestedGeneration == generation) action.accept(result); });
    }
    private JSONObject json(Object... pairs) { return host.json(pairs); }
    private static String value(EditText input) { return input.getText().toString().trim(); }
    private static String title(String value){return Rules.label(value);}
    private void watch(EditText field, Runnable changed) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
            @Override public void afterTextChanged(Editable e) {}
        });
    }
    private int quantity(EditText input) { return Rules.quantity(value(input)); }
    private long cents(EditText input, String invalid) {
        try {
            String raw = value(input);
            if (!raw.matches("-?[0-9]+(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException(invalid);
            return new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (ArithmeticException e) { throw new IllegalArgumentException(invalid); }
    }
    private EditText date(LinearLayout parent, String label, String initial) {
        EditText field = host.input(parent, label, initial, InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE);
        field.setFocusable(false); field.setContentDescription(label + ", select date");
        field.setOnClickListener(v -> {
            LocalDate d;
            try { d = LocalDate.parse(value(field)); } catch (Exception ignored) { d = LocalDate.parse(host.today()); }
            host.showDialog(new DatePickerDialog(host, (picker, y, m, day) -> field.setText(LocalDate.of(y, m + 1, day).toString()), d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()));
        });
        return field;
    }
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
            case "refilling": return "Mark ready";
            case "ready_for_delivery": case "undelivered": return "Record delivery";
            default: return "View order";
        }
    }
    private static String shortDate(String iso) {
        try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)); } catch (Exception e) { return iso; }
    }
    private String relativeDay(String iso) {
        try { return shortDate(iso) + " · " + Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(iso), LocalDate.parse(host.today()))) + " day(s)"; } catch (Exception e) { return iso; }
    }
    private void valid(Runnable action) { try { action.run(); } catch (IllegalArgumentException e) { host.error(e.getMessage()); } }
    private EditText search(LinearLayout parent, String label, String hint) {
        EditText e = host.inputPlain(parent, label, "", InputType.TYPE_CLASS_TEXT); e.setHint(hint); return e;
    }
    private LinearLayout stack(LinearLayout parent) { LinearLayout c = new LinearLayout(host); c.setOrientation(LinearLayout.VERTICAL); parent.addView(c, new LinearLayout.LayoutParams(-1, -2)); return c; }
    private TextView line(LinearLayout parent, String text) { return host.text(parent, text); }
    private Button small(LinearLayout parent, String label, int style, Runnable action) {
        Button b = new Button(host); b.setText(label); ui.styled(b, style); b.setMinHeight(ui.dp(44)); b.setPadding(ui.dp(16), 0, ui.dp(16), 0);
        b.setOnClickListener(v -> { try { action.run(); } catch (IllegalArgumentException e) { host.error(e.getMessage()); } });
        parent.addView(b, ui.lp(-2, ui.dp(44), 0, 0, 0, 0)); return b;
    }
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
        page("Order Details", id(summary));
        query("order", json("id", id(summary)), r -> {
            JSONObject o = nested(r, "order");
            if ("for_pickup".equals(status)) pickup(o); else delivery(o);
        });
    }

    // ---------------------------------------------------------------- customer sheet
    private void chooseCustomer(Consumer<JSONObject> chosen, Runnable createNew) {
        Dialog sheet = new Dialog(host);
        sheet.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(host); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(ui.shape(Ui.WHITE, 0, 22)); root.setPadding(ui.dp(18), ui.dp(12), ui.dp(18), ui.dp(12));
        View handle = new View(host); handle.setBackground(ui.shape(Ui.BORDER, 0, 3)); root.addView(handle, ui.lp(ui.dp(40), ui.dp(4), 0, 0, 0, 10));
        ((LinearLayout.LayoutParams) handle.getLayoutParams()).gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(ui.tv("Choose customer", 19, Ui.INK, ui.headBold), ui.lp(-1, -2, 0, 0, 0, 8));
        EditText find = new EditText(host); ui.styleInput(find); find.setHint("Search name or contact number"); find.setContentDescription("Search name or contact number"); find.setSingleLine(true);
        root.addView(find, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout newRow = ui.row(root); newRow.setBackground(ui.shape(Ui.TONAL, 0, 12)); newRow.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12)); newRow.setClickable(true);
        TextView plus = ui.tv("+", 18, Ui.WHITE, ui.headBold); plus.setGravity(Gravity.CENTER); plus.setBackground(ui.shape(Ui.PRIMARY, 0, 40)); newRow.addView(plus, new LinearLayout.LayoutParams(ui.dp(32), ui.dp(32)));
        newRow.addView(ui.tv("  New customer", 15, Ui.PRIMARY, ui.head));
        ((LinearLayout.LayoutParams) newRow.getLayoutParams()).topMargin = ui.dp(10);
        newRow.setOnClickListener(v -> { sheet.dismiss(); createNew.run(); });
        ScrollView scroller = new ScrollView(host); LinearLayout list = new LinearLayout(host); list.setOrientation(LinearLayout.VERTICAL); scroller.addView(list);
        root.addView(scroller, new LinearLayout.LayoutParams(-1, ui.dp(300)));
        List<JSONObject> all = new ArrayList<>();
        Runnable render = () -> {
            list.removeAllViews(); String needle = value(find).toLowerCase(Locale.ROOT); int shown = 0;
            for (JSONObject c : all) {
                if (!needle.isEmpty() && !(c.optString("name").toLowerCase(Locale.ROOT).contains(needle) || c.optString("contact_number").contains(needle))) continue;
                shown++;
                LinearLayout r = ui.row(list); r.setPadding(0, ui.dp(10), 0, ui.dp(10)); r.setClickable(true);
                r.addView(ui.avatar(c.optString("name")), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
                LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(10), 0, 0, 0);
                names.addView(ui.tv(c.optString("name"), 15, Ui.INK, ui.headBold));
                names.addView(ui.tv(title(c.optString("customer_type")) + " · " + c.optString("contact_number"), 12, Ui.MUTED, ui.body));
                r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                long due = balance(c);
                r.addView(ui.tv(due > 0 ? host.money(due) + " due" : "–", 13, due > 0 ? Ui.AMBER : Ui.MUTED, ui.bodySemi));
                r.setContentDescription(c.optString("name"));
                r.setOnClickListener(v -> { sheet.dismiss(); chosen.accept(c); });
            }
            if (shown == 0) list.addView(ui.tv("No customers found.", 14, Ui.MUTED, ui.body), ui.lp(-1, -2, 0, 12, 0, 0));
        };
        watch(find, render);
        host.query("customers", json(), response -> { JSONArray rows = items(response); for (int i = 0; i < rows.length(); i++) { JSONObject c = rows.optJSONObject(i); if (c != null) all.add(c); } render.run(); });
        sheet.setContentView(root);
        host.showDialog(sheet);
        Window w = sheet.getWindow();
        if (w != null) { w.setBackgroundDrawableResource(android.R.color.transparent); w.setGravity(Gravity.BOTTOM); w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT); }
    }

    // ---------------------------------------------------------------- customers
    public void customers() {
        LinearLayout p = page("Customers");
        EditText search = search(p, "Search name or contact number", "Search name or contact number");
        String[] groups = {"All", "Household", "Dealer", "Owes ₱"};
        int[] filter = {0};
        LinearLayout results = new LinearLayout(host); results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout holder = p;
        LinearLayout detailPane = null;
        boolean split = host.isOwner() && host.tablet();
        LinearLayout listSide = p;
        if (split) {
            LinearLayout h = new LinearLayout(host); h.setOrientation(LinearLayout.HORIZONTAL); p.addView(h, new LinearLayout.LayoutParams(-1, -2));
            listSide = new LinearLayout(host); listSide.setOrientation(LinearLayout.VERTICAL); h.addView(listSide, new LinearLayout.LayoutParams(0, -2, 1));
            detailPane = new LinearLayout(host); detailPane.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, -2, 1.3f); dl.leftMargin = ui.dp(16); h.addView(detailPane, dl);
            p.removeView(search); listSide.addView(search, 0);
        }
        final LinearLayout pane = detailPane;
        long[] searchRequest = {0};
        Ui.Chips[] chips = new Ui.Chips[1];
        Runnable[] reload = new Runnable[1];
        chips[0] = ui.chips(listSide, groups, 0, i -> { filter[0] = i; reload[0].run(); });
        listSide.addView(results, new LinearLayout.LayoutParams(-1, -2));
        reload[0] = () -> {
            long requested = ++searchRequest[0];
            query("customers", json("search", value(search)), result -> {
                if (requested != searchRequest[0]) return;
                results.removeAllViews(); JSONArray rows = items(result); int shown = 0;
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject c = rows.optJSONObject(i); if (c == null) continue;
                    String type = c.optString("customer_type");
                    if (filter[0] == 1 && !"household".equals(type)) continue;
                    if (filter[0] == 2 && !"dealer".equals(type)) continue;
                    if (filter[0] == 3 && balance(c) <= 0) continue;
                    shown++;
                    LinearLayout card = ui.card(results); card.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
                    LinearLayout r = ui.row(card); card.setClickable(true);
                    r.addView(ui.avatar(c.optString("name")), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
                    LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(10), 0, 0, 0);
                    names.addView(ui.tv(c.optString("name"), 15, Ui.INK, ui.headBold));
                    names.addView(ui.tv(title(type) + " · " + c.optString("contact_number") + (host.isOwner() ? " · Active" : ""), 12, Ui.MUTED, ui.body));
                    r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                    long due = balance(c);
                    r.addView(due > 0 ? ui.pill(host.money(due) + " due", Ui.AMBER_BG, Ui.AMBER) : ui.pill("Settled", Ui.GREEN_BG, Ui.GREEN));
                    card.setContentDescription(c.optString("name") + " " + (due > 0 ? host.money(due) + " due" : "Settled"));
                    card.setOnClickListener(v -> { if (pane != null) { pane.removeAllViews(); renderCustomer(pane, id(c)); } else customer(id(c)); });
                }
                if (shown == 0) line(results, "No customers found.");
            });
        };
        watch(search, reload[0]);
        reload[0].run();
        host.mainAction("+ Add customer", Ui.FILLED, () -> customerForm(null, null));
    }

    public void customer(String customerId) {
        LinearLayout p = page("Customer Profile", customerId);
        renderCustomer(p, customerId);
    }
    private void renderCustomer(LinearLayout p, String customerId) {
        query("customer", json("id", customerId), response -> {
            JSONObject c = nested(response, "customer");
            long due = balance(c);
            LinearLayout head = ui.card(p);
            LinearLayout r = ui.row(head);
            TextView av = ui.avatar(c.optString("name")); av.setTextSize(16); r.addView(av, new LinearLayout.LayoutParams(ui.dp(52), ui.dp(52)));
            LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(12), 0, 0, 0);
            names.addView(ui.tv(c.optString("name"), 19, Ui.INK, ui.headBold));
            names.addView(ui.tv(c.optString("contact_number") + " · " + c.optString("address"), 12, Ui.MUTED, ui.body));
            LinearLayout pills = ui.row(names); pills.setPadding(0, ui.dp(6), 0, 0);
            pills.addView(ui.pill(title(c.optString("customer_type")), Ui.TONAL, Ui.PRIMARY), ui.lp(-2, -2, 0, 0, 6, 0));
            if (host.isOwner()) pills.addView(ui.pill("Active", Ui.GREEN_BG, Ui.GREEN), ui.lp(-2, -2, 0, 0, 6, 0));
            if (due > 0) pills.addView(ui.pill(host.money(due) + " due", Ui.AMBER_BG, Ui.AMBER));
            r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            line(head, "Outstanding balance: " + host.money(due)).setTextSize(12);
            LinearLayout actions = ui.row(p);
            Button callBtn = host.button(actions, "Call", Ui.TONAL_BTN, () -> dial(c.optString("contact_number")));
            callBtn.setLayoutParams(ui.lp(0, -2, 0, 0, 6, 0)); ((LinearLayout.LayoutParams) callBtn.getLayoutParams()).weight = 1;
            Button newOrder = host.button(actions, "New order", Ui.FILLED, () -> newOrder(customerId));
            LinearLayout.LayoutParams nl = ui.lp(0, -2, 0, 0, 6, 0); nl.weight = 1; newOrder.setLayoutParams(nl);
            if (due > 0) { Button collect = host.button(actions, "Collect", Ui.AMBER_BTN, () -> payments(customerId)); LinearLayout.LayoutParams cl = ui.lp(0, -2, 0, 0, 0, 0); cl.weight = 1; collect.setLayoutParams(cl); }
            EditText start = host.isOwner() ? date(p, "History start date", "") : null;
            EditText end = host.isOwner() ? date(p, "History end date", "") : null;
            LinearLayout orderRows = new LinearLayout(host), paymentRows = new LinearLayout(host);
            orderRows.setOrientation(LinearLayout.VERTICAL); paymentRows.setOrientation(LinearLayout.VERTICAL);
            ui.segmented(p, new String[]{"Orders", "Payments"}, 0, i -> { orderRows.setVisibility(i == 0 ? View.VISIBLE : View.GONE); paymentRows.setVisibility(i == 1 ? View.VISIBLE : View.GONE); });
            p.addView(orderRows, new LinearLayout.LayoutParams(-1, -2)); p.addView(paymentRows, new LinearLayout.LayoutParams(-1, -2));
            paymentRows.setVisibility(View.GONE);
            long[] historyRequest = {0};
            Runnable history = () -> valid(() -> {
                String from = start == null ? "" : value(start), to = end == null ? "" : value(end);
                if (!from.isEmpty()) Rules.date(from); if (!to.isEmpty()) Rules.date(to);
                if (!from.isEmpty() && !to.isEmpty()) Rules.period(from, to);
                long requested = ++historyRequest[0];
                query("orders", json("customer_id", customerId, "start_date", from, "end_date", to), orders -> { if (requested == historyRequest[0]) { orderRows.removeAllViews(); renderOrders(orderRows, items(orders), false); } });
                query("payments", json("customer_id", customerId, "start_date", from, "end_date", to), payments -> { if (requested == historyRequest[0]) { paymentRows.removeAllViews(); renderReceipts(paymentRows, items(payments)); } });
            });
            if (host.isOwner()) {
                ui.muted(p, "Orders use business date; payments use receipt date in Asia/Manila. Blank dates show all history.");
                host.button(p, "Apply history dates", Ui.TONAL_BTN, history);
                host.button(p, "Clear history dates", Ui.OUTLINE, () -> { start.setText(""); end.setText(""); history.run(); });
            }
            history.run();
            TextView edit = ui.tv("Edit customer", 14, Ui.PRIMARY, ui.head); edit.setGravity(Gravity.CENTER); edit.setPadding(0, ui.dp(12), 0, ui.dp(12)); edit.setClickable(true); edit.setOnClickListener(v -> customerForm(c, null));
            p.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        });
    }

    private void customerForm(JSONObject existing, Consumer<String> afterSave) {
        LinearLayout p = page(existing == null ? "Add Customer" : "Edit Customer", existing == null ? "" : id(existing));
        LinearLayout card = ui.card(p);
        EditText name = host.input(card, "Full name", existing == null ? "" : existing.optString("name"), TEXT);
        EditText contact = host.input(card, "Contact number", existing == null ? "" : existing.optString("contact_number"), InputType.TYPE_CLASS_PHONE);
        contact.setHint("11 digits"); ui.muted(card, "11 digits").setTextSize(11);
        EditText address = host.input(card, "Address", existing == null ? "" : existing.optString("address"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        ui.label(card, "Customer type");
        String saved = host.choice("customer_type");
        int[] type = {-1};
        if (!saved.isEmpty()) type[0] = Integer.parseInt(saved);
        else if (existing != null) { String t = existing.optString("customer_type"); type[0] = t.equals("household") ? 0 : t.equals("dealer") ? 1 : t.equals("walk_in") ? 2 : -1; }
        TextView typeError = ui.smallError(card);
        Ui.Chips seg = ui.segmented(card, TYPE_NAMES, type[0], i -> { type[0] = i; host.rememberChoice("customer_type", Integer.toString(i)); typeError.setVisibility(View.GONE); });
        card.removeView(typeError); card.addView(typeError);
        host.mainAction("Save customer", Ui.FILLED, () -> valid(() -> {
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
            host.command(existing == null ? "create_customer" : "update_customer", fields, result -> {
                JSONObject saved2 = nested(result, "customer");
                if (afterSave != null) afterSave.accept(id(saved2)); else if (existing != null) customer(id(existing)); else customers();
                host.info("Customer saved.", value(name) + " · " + number);
            });
        }));
    }

    // ---------------------------------------------------------------- orders
    public void orders() { ordersFiltered(null); }
    public void ordersFiltered(String initialGroup) {
        LinearLayout p = page("Orders");
        boolean split = host.isOwner() && host.tablet();
        LinearLayout top = host.headerRow() == null ? ui.row(p) : host.headerRow(); top.setGravity(Gravity.CENTER_VERTICAL);
        if (host.isOwner()) { small(top, "Walk-in sale", Ui.TONAL_BTN, this::pos); top.addView(new View(host), new LinearLayout.LayoutParams(ui.dp(8), 1)); }
        small(top, "+ New order", Ui.FILLED, () -> newOrder(null));
        LinearLayout listSide = p, pane = null;
        if (split) {
            LinearLayout h = new LinearLayout(host); h.setOrientation(LinearLayout.HORIZONTAL); p.addView(h, new LinearLayout.LayoutParams(-1, -2));
            listSide = new LinearLayout(host); listSide.setOrientation(LinearLayout.VERTICAL); h.addView(listSide, new LinearLayout.LayoutParams(0, -2, 1));
            pane = new LinearLayout(host); pane.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, -2, 1.5f); dl.leftMargin = ui.dp(16); h.addView(pane, dl);
        }
        final LinearLayout detail = pane;
        EditText search = search(listSide, host.isOwner() ? "Search order or customer" : "Search name or order ID", host.isOwner() ? "Search order or customer" : "Search name or order ID");
        String[] names = host.isOwner() ? new String[]{"All", "Pickup", "Refill", "Deliver"} : new String[]{"Pickup", "Refilling", "Deliver", "All"};
        int[] map = host.isOwner() ? new int[]{-1, 1, 2, 3} : new int[]{1, 2, 3, -1};
        String saved = host.choice("orders_filter");
        int start = 0;
        for (int i = 0; i < map.length; i++) if (initialGroup != null && initialGroup.equals(groupName(map[i]))) start = i;
        if (initialGroup == null && !saved.isEmpty()) try { start = Math.max(0, Math.min(3, Integer.parseInt(saved))); } catch (NumberFormatException ignored) { }
        int[] selected = {start};
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL);
        List<JSONObject> loaded = new ArrayList<>();
        String[] openId = {""};
        Runnable[] render = new Runnable[1];
        Ui.Chips chips = ui.chips(listSide, names, start, i -> { selected[0] = i; host.rememberChoice("orders_filter", Integer.toString(i)); render[0].run(); });
        listSide.addView(rows, new LinearLayout.LayoutParams(-1, -2));
        render[0] = () -> {
            rows.removeAllViews();
            int[] counts = new int[4];
            for (JSONObject o : loaded) { int g = group(o.optString("status")); if (g > 0) counts[g]++; }
            for (int i = 0; i < map.length; i++) chips.label(i, names[i] + " " + (map[i] < 0 ? loaded.size() : counts[map[i]]));
            int shown = 0; JSONObject firstShown = null;
            for (JSONObject o : loaded) {
                if (map[selected[0]] >= 0 && group(o.optString("status")) != map[selected[0]]) continue;
                shown++; if (firstShown == null) firstShown = o;
                orderCard(rows, o, !host.isOwner(), id(o).equals(openId[0]), () -> { if (detail != null) { openId[0] = id(o); render[0].run(); detail.removeAllViews(); renderOrderById(detail, id(o)); } else order(id(o)); });
            }
            if (shown == 0) line(rows, "No orders found.");
            if (detail != null && openId[0].isEmpty() && firstShown != null) { openId[0] = id(firstShown); render[0].run(); detail.removeAllViews(); renderOrderById(detail, id(firstShown)); }
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
    }
    private static String groupName(int g) { return g == 1 ? "Pickup" : g == 2 ? "Refilling" : g == 3 ? "Deliver" : "All"; }
    private void orderCard(LinearLayout parent, JSONObject o, boolean showNext, boolean selected, Runnable open) {
        LinearLayout card = ui.card(parent); card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); card.setClickable(true);
        if (selected) card.setBackground(ui.shape(Ui.TONAL, Ui.PRIMARY, 14));
        LinearLayout r = ui.row(card);
        LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL);
        names.addView(ui.tv(o.optString("customer_name"), 15, Ui.INK, ui.headBold));
        names.addView(ui.tv(o.optString("reference") + " · " + o.optInt("quantity") + " gallons", 12, Ui.MUTED, ui.body));
        if (showNext && group(o.optString("status")) > 0) names.addView(ui.tv("Next: " + nextLabel(o.optString("status")) + " ›", 13, Ui.PRIMARY, ui.bodySemi), ui.lp(-2, -2, 0, 6, 0, 0));
        r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout right = new LinearLayout(host); right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.END);
        right.addView(ui.statusPill(o.optString("status"), pending(o)));
        long due = balance(o);
        TextView amount = ui.tv(due > 0 ? host.money(due) + " due" : "Paid", 13, due > 0 ? Ui.AMBER : Ui.GREEN, ui.bodySemi);
        right.addView(amount, ui.lp(-2, -2, 0, 6, 0, 0));
        r.addView(right);
        card.setContentDescription(o.optString("reference") + " " + o.optString("customer_name") + " " + Rules.label(o.optString("status")));
        card.setOnClickListener(v -> open.run());
    }
    private void renderOrders(LinearLayout p, JSONArray rows, boolean pending) {
        if (rows.length() == 0) line(p, pending ? "No pending deliveries." : "No orders found.");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            if (pending) {
                long days = 0;
                try { days = Math.max(0, ChronoUnit.DAYS.between(LocalDate.parse(o.optString("business_date")), LocalDate.parse(host.today()))); } catch (Exception ignored) { }
                LinearLayout card = ui.card(p); card.setClickable(true); card.setOnClickListener(v -> order(id(o)));
                LinearLayout top = ui.row(card);
                LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL);
                names.addView(ui.tv(o.optString("customer_name"), 15, Ui.INK, ui.headBold));
                names.addView(ui.tv(o.optString("reference") + " · Since " + shortDate(o.optString("business_date")), 12, Ui.MUTED, ui.body));
                top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                top.addView(days == 0 ? ui.pill("Today", Ui.TONAL, Ui.PRIMARY) : ui.pill(days + (days == 1 ? " day waiting" : " days waiting"), Ui.AMBER_BG, Ui.AMBER));
                LinearLayout bottom = ui.row(card); bottom.setPadding(0, ui.dp(8), 0, 0);
                int left = pending(o);
                bottom.addView(ui.tv(left + (left == 1 ? " gallon" : " gallons"), 17, Ui.INK, ui.headBold), new LinearLayout.LayoutParams(0, -2, 1));
                small(bottom, "Deliver", Ui.FILLED, () -> nextStep(o));
                card.setContentDescription(o.optString("reference") + " " + o.optString("customer_name") + " " + left + " gallons pending");
                ui.muted(p, "Original date: " + o.optString("business_date") + " · " + days + " days waiting · Pending gallons: " + left).setTextSize(11);
                continue;
            }
            orderCard(p, o, false, false, () -> order(id(o)));
            if (pending) {
                String d = o.optString("business_date"); long days = 0;
                try { days = ChronoUnit.DAYS.between(LocalDate.parse(d), LocalDate.parse(host.today())); } catch (Exception ignored) {}
                ui.muted(p, "Original date: " + d + " · " + Math.max(0, days) + " days waiting\nPending gallons: " + pending(o)).setPadding(ui.dp(4), 0, 0, ui.dp(10));
            }
        }
    }

    public void pending() {
        LinearLayout p = page("Pending gallons / Carry-over Details");
        ui.muted(p, "Gallons still owed to customers · oldest first");
        query("orders", json(), result -> {
            List<JSONObject> records = new ArrayList<>(); JSONArray all = items(result);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i); if (o != null && (o.optString("status").equals("undelivered") || o.optString("status").equals("ready_for_delivery")) && pending(o) > 0) records.add(o);
            }
            records.sort(Comparator.comparing(o -> o.optString("business_date")));
            JSONArray ordered = new JSONArray(); for (JSONObject o : records) ordered.put(o); renderOrders(p, ordered, true);
        });
    }

    // ---------------------------------------------------------------- new order
    public void newOrder(String selectedCustomerId) {
        LinearLayout p = page("Create Order");
        String desiredCustomer = selectedCustomerId == null ? host.choice("order_customer") : selectedCustomerId;
        JSONObject[] picked = {null};
        List<JSONObject> list = new ArrayList<>();
        LinearLayout customerCard = ui.card(p); customerCard.setClickable(true);
        LinearLayout qtyCard = ui.card(p);
        ui.muted(qtyCard, "How many gallons?").setTypeface(ui.bodySemi);
        EditText quantity = host.quantity(qtyCard, "Gallon quantity", "", new int[]{5, 10, 15, 20}, "gallons");
        quantity.setHint("0");
        TextView last = ui.pill("", Ui.TONAL, Ui.PRIMARY); last.setVisibility(View.GONE); qtyCard.addView(last, ui.lp(-2, -2, 0, 4, 0, 0));
        LinearLayout priceCard = ui.card(p);
        TextView[] unitRow = ui.pair(priceCard, "Price per gallon", "—", Ui.INK, false);
        String[] orderDate = {host.today()};
        TextView[] dateRow = ui.pair(priceCard, "Order date", "Today · " + shortDate(orderDate[0]), Ui.INK, false);
        dateRow[1].setClickable(true); dateRow[1].setTextColor(Ui.PRIMARY);
        dateRow[1].setOnClickListener(v -> {
            LocalDate d; try { d = LocalDate.parse(orderDate[0]); } catch (Exception e) { d = LocalDate.parse(host.today()); }
            host.showDialog(new DatePickerDialog(host, (picker, y, m, day) -> { orderDate[0] = LocalDate.of(y, m + 1, day).toString(); dateRow[1].setText(orderDate[0].equals(host.today()) ? "Today · " + shortDate(orderDate[0]) : shortDate(orderDate[0]) + " " + orderDate[0].substring(0, 4)); }, d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth()));
        });
        TextView[] totalRow = ui.pair(priceCard, "Total", host.money(0), Ui.PRIMARY, true);
        ui.muted(p, "Initial status: Ordered. Entered Order date is separate from server-recorded encoding time.").setTextSize(11);
        Runnable preview = () -> {
            if (picked[0] == null) { unitRow[0].setText("Price per gallon"); unitRow[1].setText("—"); totalRow[1].setText(host.money(0)); return; }
            long unit = picked[0].optString("customer_type").equals("dealer") ? 3000 : 3500;
            unitRow[0].setText("Price per gallon · " + title(picked[0].optString("customer_type"))); unitRow[1].setText(host.money(unit));
            try { totalRow[1].setText(host.money(Math.multiplyExact((long) Integer.parseInt(value(quantity)), unit))); }
            catch (Exception e) {
                try { double d = Double.parseDouble(value(quantity)); totalRow[1].setText(host.money(Math.round(d * unit))); } catch (Exception ignored) { totalRow[1].setText(host.money(0)); }
            }
        };
        watch(quantity, preview);
        Runnable[] showCustomer = new Runnable[1];
        Runnable pick = () -> chooseCustomer(c -> { picked[0] = c; host.rememberChoice("order_customer", id(c)); showCustomer[0].run(); },
            () -> customerForm(null, selected -> newOrder(selected)));
        showCustomer[0] = () -> {
            customerCard.removeAllViews();
            if (picked[0] == null) {
                LinearLayout r = ui.row(customerCard);
                r.addView(ui.tv("Choose customer", 16, Ui.INK, ui.headBold), new LinearLayout.LayoutParams(0, -2, 1));
                r.addView(ui.tv("Select ›", 14, Ui.PRIMARY, ui.head));
                customerCard.setOnClickListener(v -> pick.run());
            } else {
                JSONObject c = picked[0];
                LinearLayout r = ui.row(customerCard);
                r.addView(ui.avatar(c.optString("name")), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
                LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(10), 0, 0, 0);
                names.addView(ui.tv(c.optString("name"), 16, Ui.INK, ui.headBold));
                names.addView(ui.tv(title(c.optString("customer_type")) + " · " + c.optString("address"), 12, Ui.MUTED, ui.body));
                r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
                TextView change = ui.tv("Change", 14, Ui.PRIMARY, ui.head); change.setClickable(true); change.setOnClickListener(v -> pick.run()); r.addView(change);
                int previous = c.optInt("previous_order_quantity", 0);
                if (previous > 0) { last.setText("Last order: " + previous + " gallons"); last.setVisibility(View.VISIBLE); last.setClickable(true); last.setOnClickListener(v -> quantity.setText(Integer.toString(previous))); if (value(quantity).isEmpty()) quantity.setText(Integer.toString(previous)); }
                else last.setVisibility(View.GONE);
                customerCard.setOnClickListener(null);
            }
            preview.run();
        };
        showCustomer[0].run();
        query("customers", json(), response -> {
            JSONArray all = items(response);
            for (int i = 0; i < all.length(); i++) { JSONObject c = all.optJSONObject(i); if (c == null) continue; list.add(c); if (id(c).equals(desiredCustomer)) picked[0] = c; }
            showCustomer[0].run();
            if (picked[0] == null && selectedCustomerId == null && value(quantity).isEmpty()) pick.run();
        });
        host.mainAction("Save order", Ui.FILLED, () -> valid(() -> {
            if (picked[0] == null) throw new IllegalArgumentException("Choose a customer.");
            int q;
            try { q = quantity(quantity); } catch (IllegalArgumentException e) { host.fieldError(quantity, "Enter a valid gallon quantity."); return; }
            String d;
            try { d = LocalDate.parse(orderDate[0]).toString(); } catch (Exception e) { throw new IllegalArgumentException("Complete the required fields."); }
            JSONObject c = picked[0]; long unit = c.optString("customer_type").equals("dealer") ? 3000 : 3500;
            host.command("create_order", json("customer_id", id(c), "quantity", q, "business_date", d), result -> {
                JSONObject o = nested(result, "order");
                if (!id(o).isEmpty()) order(id(o)); else orders();
                host.info("Order saved.", o.optString("reference") + " · " + c.optString("name") + " · " + q + " gallons · " + host.money(Math.multiplyExact((long) q, unit)));
            });
        }));
        host.actionNote("Saved with today’s time; the order date can be changed above.");
    }

    // ---------------------------------------------------------------- order detail
    public void order(String orderId) {
        LinearLayout p = page("Order Details", orderId);
        renderOrderById(p, orderId, host.isOwner() && host.tablet());
    }
    private void renderOrderById(LinearLayout p, String orderId) { renderOrderById(p, orderId, true); }
    private void renderOrderById(LinearLayout p, String orderId, boolean pane) {
        query("order", json("id", orderId), response -> renderOrder(p, nested(response, "order"), pane));
    }
    private void renderOrder(LinearLayout p, JSONObject o, boolean pane) {
        String status = o.optString("status");
        long balance = balance(o), unit = o.optLong("unit_price_centavos");
        int delivered = o.optInt("delivered_quantity"), quantity = o.optInt("quantity");
        // Header
        LinearLayout head = ui.card(p);
        LinearLayout r = ui.row(head);
        r.addView(ui.avatar(o.optString("customer_name")), new LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)));
        LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(12), 0, 0, 0);
        names.addView(ui.tv(o.optString("customer_name"), 19, Ui.INK, ui.headBold));
        names.addView(ui.tv(o.optString("reference") + " · " + title(o.optString("customer_type")), 12, Ui.MUTED, ui.body));
        r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        Runnable main = mainStep(o);
        String mainLabel = mainLabel(o);
        if (pane && main != null) { Button b = small(r, mainLabel, "delivered".equals(status) ? Ui.AMBER_BTN : Ui.FILLED, main); b.setMinHeight(ui.dp(48)); }
        else if (!pane) small(r, "Call", Ui.TONAL_BTN, () -> dial(o.optString("contact_number")));
        if (!pane && group(status) == 1) {
            line(head, o.optString("contact_number")).setTypeface(ui.bodySemi);
            ui.muted(head, o.optString("address"));
        }
        // Progress
        LinearLayout prog = ui.card(p);
        boolean labelled = status.equals("ordered") || status.equals("undelivered") || status.equals("delivered") || status.equals("rescheduled");
        ui.label(prog, labelled ? "Progress · " + title(status) : "Progress");
        ui.progress(prog, status, pane ? Ui.STAGES_FULL : Ui.STAGES_COMPACT);
        // Gallons and payment
        LinearLayout lower = p;
        LinearLayout gallons, pay;
        if (pane) {
            LinearLayout both = new LinearLayout(host); both.setOrientation(LinearLayout.HORIZONTAL); p.addView(both, new LinearLayout.LayoutParams(-1, -2));
            gallons = new LinearLayout(host); gallons.setOrientation(LinearLayout.VERTICAL); gallons.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 14)); gallons.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(0, -2, 1); gl.rightMargin = ui.dp(6); both.addView(gallons, gl);
            pay = new LinearLayout(host); pay.setOrientation(LinearLayout.VERTICAL); pay.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 14)); pay.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, -2, 1); pl.leftMargin = ui.dp(6); both.addView(pay, pl);
            ((LinearLayout.LayoutParams) both.getLayoutParams()).bottomMargin = ui.dp(12);
        } else { gallons = ui.card(p); pay = ui.card(p); }
        boolean progressStyle = delivered > 0 || status.equals("undelivered") || status.equals("delivered");
        if (progressStyle || pane) {
            ui.heading(gallons, "Gallons");
            if (progressStyle) {
                LinearLayout bar = new LinearLayout(host); bar.setOrientation(LinearLayout.HORIZONTAL);
                View done = new View(host); done.setBackground(ui.shape(Ui.PRIMARY, 0, 4)); View left = new View(host); left.setBackground(ui.shape(Ui.AMBER, 0, 4));
                bar.addView(done, new LinearLayout.LayoutParams(0, ui.dp(8), Math.max(0.01f, delivered))); bar.addView(left, new LinearLayout.LayoutParams(0, ui.dp(8), Math.max(0.01f, pending(o))));
                gallons.addView(bar, ui.lp(-1, -2, 0, 0, 0, 6));
                ui.pair(gallons, "Delivered", delivered + " of " + quantity, Ui.INK, false);
                ui.pair(gallons, "Still to deliver", pending(o) + (pending(o) == 1 ? " gallon" : " gallons"), pending(o) > 0 ? Ui.AMBER : Ui.INK, false);
                if (pending(o) > 0) ui.pair(gallons, "Waiting since", relativeDay(o.optString("business_date")), Ui.INK, false);
            } else {
                ui.pair(gallons, "Ordered", Integer.toString(quantity), Ui.INK, false);
                ui.pair(gallons, "Picked up", Integer.toString(o.optInt("collected_quantity")), Ui.INK, false);
                ui.pair(gallons, "Delivered", Integer.toString(delivered), Ui.INK, false);
                ui.pair(gallons, "Still to deliver", Integer.toString(pending(o)), Ui.AMBER, false);
            }
        } else {
            LinearLayout stats = ui.row(gallons);
            String[][] cols = {{Integer.toString(quantity), "Ordered"}, {Integer.toString(o.optInt("collected_quantity")), "Picked up"}, {Integer.toString(delivered), "Delivered"}, {Integer.toString(pending(o)), "Pending"}};
            for (String[] c : cols) { LinearLayout cell = new LinearLayout(host); cell.setOrientation(LinearLayout.VERTICAL); cell.addView(ui.tv(c[0], 20, Ui.INK, ui.headBold)); cell.addView(ui.tv(c[1], 12, Ui.MUTED, ui.body)); stats.addView(cell, new LinearLayout.LayoutParams(0, -2, 1)); }
        }
        LinearLayout payHead = ui.row(pay);
        payHead.addView(ui.tv("Payment", 17, Ui.INK, ui.headBold), new LinearLayout.LayoutParams(0, -2, 1));
        payHead.addView(ui.paymentPill(o.optString("payment_status")));
        ui.pair(pay, "Total · " + quantity + " × " + host.money(unit), host.money(o.optLong("total_centavos")), Ui.INK, false);
        ui.pair(pay, "Paid", host.money(paid(o)), Ui.INK, false);
        ui.pair(pay, "Balance", host.money(balance), balance > 0 ? Ui.AMBER : Ui.INK, true);
        if (balance > 0 && !"delivered".equals(status) || (balance > 0 && pane)) host.button(pay, "Collect payment", Ui.OUTLINE, () -> payment(o, "order"));
        if (status.equals("for_pickup")) {
            TextView re = ui.tv("Customer not home? Reschedule pickup", 14, Ui.PRIMARY, ui.body); re.setGravity(Gravity.CENTER); re.setPadding(0, ui.dp(10), 0, ui.dp(10)); re.setClickable(true); re.setOnClickListener(v -> reschedule(o));
            p.addView(re, new LinearLayout.LayoutParams(-1, -2));
        }
        if (status.equals("rescheduled")) {
            ui.muted(p, "Rescheduled pickup: " + o.optString("rescheduled_date"));
            if (!o.optString("reschedule_notes").isEmpty()) ui.muted(p, "Pickup notes: " + o.optString("reschedule_notes"));
        }
        // Full record
        LinearLayout details = ui.card(p);
        ui.heading(details, "Order details");
        ui.pair(details, "Customer", o.optString("customer_name") + " · " + title(o.optString("customer_type")), Ui.INK, false);
        ui.pair(details, "Contact", o.optString("contact_number"), Ui.INK, false);
        ui.pair(details, "Address", o.optString("address"), Ui.INK, false);
        ui.pair(details, "Order date", o.optString("business_date"), Ui.INK, false);
        ui.pair(details, "Recorded", host.timestamp(o.optString("created_at")), Ui.INK, false);
        ui.pair(details, "Recorded by", o.optString("recorded_by_name", o.optString("recorded_by")), Ui.INK, false);
        ui.pair(details, "Order status", title(status), Ui.INK, false);
        ui.pair(details, "Original expected gallons", Integer.toString(o.optInt("expected_quantity", quantity)), Ui.INK, false);
        ui.pair(details, "Adjusted order gallons", Integer.toString(quantity), Ui.INK, false);
        ui.pair(details, "Shortfall", Integer.toString(o.optInt("shortfall")), Ui.INK, false);
        ui.pair(details, "Reserved for this order", Integer.toString(o.optInt("reserved_quantity")), Ui.INK, false);
        ui.pair(details, "Fixed unit price", host.money(unit), Ui.INK, false);
        if (pending(o) > 0) ui.pair(details, "Waiting", Math.max(0, ChronoUnit.DAYS.between(Rules.date(o.optString("business_date")), Rules.date(host.today()))) + " days since original order date", Ui.INK, false);
        JSONArray receipts = o.optJSONArray("payments");
        if (receipts != null) { LinearLayout c = ui.card(p); ui.heading(c, "Payment history"); renderReceipts(c, receipts); }
        JSONArray deliveries = o.optJSONArray("deliveries");
        if (deliveries != null && deliveries.length() > 0) {
            LinearLayout c = ui.card(p); ui.heading(c, "Delivery history");
            for (int i = 0; i < deliveries.length(); i++) { JSONObject d = deliveries.optJSONObject(i); if (d != null) line(c, d.optInt("quantity") + " gallons • " + host.timestamp(d.optString("created_at")) + " • Recorded by: " + d.optString("recorded_by_name", d.optString("recorded_by"))); }
        }
        if (host.isOwner()) {
            LinearLayout c = ui.card(p); ui.heading(c, "Timeline");
            JSONArray timeline = o.optJSONArray("timeline");
            if (timeline == null || timeline.length() == 0) line(c, "No timeline events available.");
            else for (int i = 0; i < timeline.length(); i++) { JSONObject event = timeline.optJSONObject(i); if (event != null) line(c, title(event.optString("action")) + " • " + title(event.optString("status")) + "\n" + host.timestamp(event.optString("occurred_at")) + "\nRecorded by: " + event.optString("recorded_by_name")); }
        }
        if (!pane && main != null) {
            host.mainAction(mainLabel, "delivered".equals(status) ? Ui.AMBER_BTN : Ui.FILLED, main);
            String note = "ordered".equals(status) ? "Moves the order to the pickup list" : "delivered".equals(status) ? "Last step for this order" : "refilling".equals(status) ? "Reserves the gallons for this order" : "";
            if (!note.isEmpty()) host.actionNote(note);
        }
    }
    private String mainLabel(JSONObject o) {
        String status = o.optString("status");
        if ("delivered".equals(status)) return "Collect " + host.money(balance(o));
        return nextLabel(status);
    }
    /** The single next-step action of an order; null when nothing is left to do. */
    private Runnable mainStep(JSONObject o) {
        switch (o.optString("status")) {
            case "ordered": return () -> transition(o, "for_pickup");
            case "rescheduled": return () -> transition(o, "for_pickup");
            case "for_pickup": return () -> pickup(o);
            case "picked_up": return () -> transition(o, "refilling");
            case "refilling": return () -> transition(o, "ready_for_delivery");
            case "ready_for_delivery": case "undelivered": return () -> delivery(o);
            case "delivered": return balance(o) > 0 ? () -> payment(o, "order") : null;
            default: return null;
        }
    }
    private void transition(JSONObject order, String status) {
        host.command("transition_order", json("order_id", id(order), "status", status), saved -> { order(id(order)); host.info("Order updated."); });
    }

    // ---------------------------------------------------------------- pickup, reschedule, delivery
    private void customerHeader(LinearLayout p, JSONObject o, String extra) {
        LinearLayout card = ui.card(p);
        LinearLayout r = ui.row(card);
        r.addView(ui.avatar(o.optString("customer_name")), new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL); names.setPadding(ui.dp(10), 0, 0, 0);
        names.addView(ui.tv(o.optString("customer_name"), 16, Ui.INK, ui.headBold));
        names.addView(ui.tv(o.optString("reference") + (extra.isEmpty() ? "" : " · " + extra), 12, Ui.MUTED, ui.body));
        r.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        if (!o.optString("contact_number").isEmpty() || !o.optString("address").isEmpty()) ui.muted(card, (o.optString("contact_number") + "\n" + o.optString("address")).trim()).setPadding(0, ui.dp(8), 0, 0);
    }
    private void pickup(JSONObject o) {
        LinearLayout p = page("Record Pickup", id(o)); int expected = o.optInt("expected_quantity", o.optInt("quantity")); long unit = o.optLong("unit_price_centavos");
        customerHeader(p, o, "Pickup date: " + host.today());
        LinearLayout card = ui.card(p);
        ui.pair(card, "Original expected gallons", Integer.toString(expected), Ui.INK, true);
        ui.label(card, "Actual collected gallons");
        EditText collected = host.quantity(card, "Actual collected gallons", "", new int[]{Math.max(1, expected / 2), Math.max(1, expected - 1), expected}, "gallons");
        collected.setHint("0");
        LinearLayout calc = ui.card(p);
        TextView[] shortfall = ui.pair(calc, "Shortfall", "—", Ui.INK, false), adjusted = ui.pair(calc, "Adjusted order gallons", "—", Ui.INK, false);
        ui.pair(calc, "Fixed unit price", host.money(unit), Ui.INK, false);
        TextView[] total = ui.pair(calc, "Adjusted total", "—", Ui.PRIMARY, true);
        Runnable refreshPreview = () -> {
            try { int q = quantity(collected); if (q > expected) throw new IllegalArgumentException(); shortfall[1].setText(Integer.toString(expected - q)); adjusted[1].setText(Integer.toString(q)); total[1].setText(host.money(Math.multiplyExact((long) q, unit))); }
            catch (Exception e) { shortfall[1].setText("—"); adjusted[1].setText("—"); total[1].setText("—"); }
        }; watch(collected, refreshPreview); if (!value(collected).isEmpty()) refreshPreview.run();
        TextView re = ui.tv("Customer not home? Reschedule pickup", 14, Ui.PRIMARY, ui.body); re.setGravity(Gravity.CENTER); re.setPadding(0, ui.dp(10), 0, ui.dp(10)); re.setClickable(true); re.setOnClickListener(v -> reschedule(o));
        p.addView(re, new LinearLayout.LayoutParams(-1, -2));
        host.mainAction("Save pickup", Ui.FILLED, () -> valid(() -> {
            int q;
            try { q = quantity(collected); } catch (IllegalArgumentException e) { host.fieldError(collected, e.getMessage()); return; }
            if (q > expected) { host.fieldError(collected, "Collected quantity cannot exceed the expected quantity."); return; }
            if (Math.multiplyExact((long) q, unit) < paid(o)) { host.fieldError(collected, "Adjusted total cannot be less than the amount already paid."); return; }
            host.command("record_pickup", json("order_id", id(o), "quantity", q), saved -> { order(id(o)); host.info("Pickup recorded.", q + " gallons collected · adjusted total " + host.money(Math.multiplyExact((long) q, unit))); });
        }));
    }
    private void reschedule(JSONObject o) {
        LinearLayout p = page("Reschedule Pickup", id(o)); customerHeader(p, o, "");
        LinearLayout card = ui.card(p);
        EditText next = date(card, "New pickup date", host.today());
        EditText notes = host.input(card, "Notes (optional)", o.optString("reschedule_notes"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        host.mainAction(host.isOwner() ? "Save Pickup Result" : "Save result", Ui.FILLED, () -> valid(() -> {
            LocalDate d;
            try { d = LocalDate.parse(value(next)); } catch (Exception e) { host.fieldError(next, "Select today or a future date."); return; }
            if (d.isBefore(LocalDate.parse(host.today()))) { host.fieldError(next, "Select today or a future date."); return; }
            Rules.remarks(value(notes));
            host.command("reschedule_pickup", json("order_id", id(o), "scheduled_date", d.toString(), "notes", value(notes)), saved -> { order(id(o)); host.info("Order rescheduled.", "Pickup moved to " + shortDate(d.toString()) + "."); });
        }));
    }
    private void delivery(JSONObject o) {
        LinearLayout p = page("Record Delivery", id(o)); int remaining = pending(o); int delivered = o.optInt("delivered_quantity");
        customerHeader(p, o, delivered + " of " + o.optInt("quantity") + " delivered");
        LinearLayout card = ui.card(p);
        ui.muted(card, "Gallons delivered now").setTypeface(ui.bodySemi);
        int[] picks = new int[Math.min(3, remaining)]; String[] pickNames = new String[picks.length];
        for (int i = 0; i < picks.length; i++) { picks[i] = i == 0 ? remaining : remaining - i; pickNames[i] = i == 0 ? "All " + remaining : Integer.toString(picks[i]); }
        EditText trip = host.quantity(card, "Gallons delivered this trip", "", picks, pickNames, "of " + remaining + " remaining");
        trip.setHint("0");
        TextView status = ui.tv("", 13, Ui.INK, ui.body); status.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10)); status.setVisibility(View.GONE);
        p.addView(status, ui.lp(-1, -2, 0, 0, 0, 12));
        ui.muted(p, "Previously delivered: " + delivered + " · Remaining gallons: " + remaining + " · Order reservation: " + o.optInt("reserved_quantity") + "\nRecorded at actual server time on save." + (host.isOwner() ? "\nRecording account: " + host.recorder() + " · Today: " + host.today() + " (Asia/Manila)" : "")).setTextSize(11);
        Runnable refreshPreview = () -> {
            try {
                int q = quantity(trip); if (q > remaining) throw new IllegalArgumentException();
                boolean all = q == remaining; status.setVisibility(View.VISIBLE);
                status.setBackground(ui.shape(all ? Ui.GREEN_BG : Ui.AMBER_BG, 0, 12)); status.setTextColor(all ? Ui.GREEN : Ui.AMBER);
                status.setText(all ? "Order will be Delivered\n" + (remaining - q) + " gallons pending after saving. Reserved stock for this order: " + o.optInt("reserved_quantity") + "."
                    : (remaining - q) + (remaining - q == 1 ? " gallon stays pending" : " gallons stay pending") + "\nOrder becomes Undelivered. The remaining reservation is kept.");
            } catch (Exception e) { status.setVisibility(View.GONE); }
        }; watch(trip, refreshPreview); if (!value(trip).isEmpty()) refreshPreview.run();
        host.mainAction("Save delivery", Ui.FILLED, () -> valid(() -> {
            int q;
            try { q = quantity(trip); } catch (IllegalArgumentException e) { host.fieldError(trip, e.getMessage()); return; }
            if (q > remaining) { host.fieldError(trip, "Delivered quantity cannot exceed the remaining " + remaining + " gallons."); return; }
            if (o.has("reserved_quantity") && q > o.optInt("reserved_quantity")) { host.error("Not enough reserved gallons for this order."); return; }
            host.command("record_delivery", json("order_id", id(o), "quantity", q), saved -> { order(id(o)); host.info("Delivery recorded.", q == remaining ? "All " + (delivered + q) + " gallons delivered." : q + " gallons delivered · " + (remaining - q) + " still to deliver"); });
        }));
    }

    // ---------------------------------------------------------------- walk-in sale
    public void pos() {
        LinearLayout p = page("Walk-in Sale / POS");
        LinearLayout top = ui.row(p);
        top.addView(ui.tv("Counter sale · no profile needed", 12, Ui.MUTED, ui.body), new LinearLayout.LayoutParams(0, -2, 1));
        TextView stock = ui.pill("Checking stock…", Ui.TONAL, Ui.PRIMARY); top.addView(stock);
        ui.muted(p, "Purified water · fixed price ₱35.00 per gallon. A saved customer profile is optional.").setTextSize(11);
        if (host.isOwner()) ui.muted(p, "Recording account: " + host.recorder()).setTextSize(11);
        final long[] available = {-1};
        query("inventory", json(), response -> { JSONObject i = nested(response, "inventory"); available[0] = i.optLong("filled_available", i.optLong("available_filled", -1)); stock.setText(available[0] < 0 ? "Stock checked on save" : available[0] + " gallons available"); });
        EditText name = host.input(p, "Customer name", "", TEXT);
        String[] profileId = {host.choice("pos_customer")};
        TextView profile = ui.tv(profileId[0].isEmpty() ? "Use a saved customer profile (optional)" : "Saved profile selected · change", 13, Ui.PRIMARY, ui.head); profile.setClickable(true); profile.setPadding(0, ui.dp(4), 0, ui.dp(8));
        profile.setOnClickListener(v -> chooseCustomer(c -> { profileId[0] = id(c); host.rememberChoice("pos_customer", id(c)); name.setText(c.optString("name")); profile.setText("Saved profile: " + c.optString("name") + " · change"); }, () -> customerForm(null, selected -> pos())));
        p.addView(profile, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout gCard = ui.card(p);
        ui.muted(gCard, "Gallons").setTypeface(ui.bodySemi);
        EditText gallons = host.quantity(gCard, "Gallon quantity", "", new int[]{1, 2, 3, 5, 10}, "× ₱35.00 each");
        gallons.setHint("0");
        LinearLayout cCard = ui.card(p);
        ui.muted(cCard, "Cash received").setTypeface(ui.bodySemi);
        EditText cash = host.input(cCard, "Cash received (₱)", "", CASH); cash.setHint("₱0.00");
        host.quickAmounts(cCard, cash, new String[]{"Exact", "₱100", "₱200", "₱500"}, new String[]{"", "100", "200", "500"});
        // the "Exact" pick is resolved against the current total below
        LinearLayout bar = host.actionBar();
        LinearLayout totals = ui.row(bar);
        LinearLayout tl = new LinearLayout(host); tl.setOrientation(LinearLayout.VERTICAL);
        tl.addView(ui.tv("Total", 12, Ui.MUTED, ui.body)); TextView totalView = ui.tv(host.money(0), 22, Ui.INK, ui.headBold); tl.addView(totalView);
        totals.addView(tl, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout tr = new LinearLayout(host); tr.setOrientation(LinearLayout.VERTICAL); tr.setGravity(Gravity.END);
        TextView changeLabel = ui.tv("Change", 12, Ui.MUTED, ui.body); tr.addView(changeLabel); TextView changeView = ui.tv("–", 22, Ui.GREEN, ui.headBold); tr.addView(changeView);
        totals.addView(tr);
        Button complete = host.button(bar, "Complete sale", Ui.FILLED, () -> { });
        ui.setEnabledLook(complete, false, Ui.FILLED);
        TextView creditNote = ui.tv("", 12, Ui.MUTED, ui.body); creditNote.setGravity(Gravity.CENTER); creditNote.setVisibility(View.GONE); bar.addView(creditNote);
        Runnable[] recompute = new Runnable[1];
        recompute[0] = () -> {
            long total = 0, tendered = -1; boolean ok = true;
            try { int q = quantity(gallons); total = Math.multiplyExact((long) q, 3500); totalView.setText(host.money(total)); host.clearFieldError(gallons); if (available[0] >= 0 && q > available[0]) { host.fieldError(gallons, "Not enough filled gallons."); ok = false; } }
            catch (IllegalArgumentException e) { totalView.setText(host.money(0)); ok = false; if (!value(gallons).isEmpty()) host.fieldError(gallons, e.getMessage()); }
            try { tendered = cents(cash, "Enter a valid cash amount."); host.clearFieldError(cash); if (tendered < 0) { host.fieldError(cash, "Cash received cannot be negative."); ok = false; } }
            catch (IllegalArgumentException e) { ok = false; if (!value(cash).isEmpty()) host.fieldError(cash, e.getMessage()); }
            boolean credit = ok && tendered < total;
            changeLabel.setText(credit ? "Credit (unpaid)" : "Change");
            if (ok && !credit) { changeView.setText(host.money(tendered - total)); changeView.setTextColor(Ui.GREEN); }
            else if (credit) { changeView.setText(host.money(total - tendered)); changeView.setTextColor(Ui.AMBER); }
            else { changeView.setText("–"); changeView.setTextColor(Ui.GREEN); }
            complete.setText(credit ? "Save as credit · " + host.money(total - tendered) : "Complete sale");
            ui.setEnabledLook(complete, ok && total > 0, credit ? Ui.AMBER_BTN : Ui.FILLED);
            creditNote.setVisibility(credit ? View.VISIBLE : View.GONE);
            if (credit) creditNote.setText("Paid " + host.money(Math.max(0, tendered)) + " now · " + host.money(total - tendered) + " stays as a balance");
        };
        watch(gallons, recompute[0]); watch(cash, recompute[0]);
        // Exact = the current total
        for (TextView chip : new TextView[]{}) { }
        complete.setOnClickListener(v -> valid(() -> {
            if (value(name).isEmpty()) { host.fieldError(name, "Enter the customer name."); return; }
            int q = quantity(gallons); long tendered = cents(cash, "Enter a valid cash amount.");
            if (tendered < 0) throw new IllegalArgumentException("Cash received cannot be negative.");
            if (available[0] >= 0 && q > available[0]) throw new IllegalArgumentException("Not enough filled gallons.");
            long total = Math.multiplyExact((long) q, 3500);
            JSONObject payload = json("customer_name", value(name), "quantity", q, "cash_centavos", tendered, "confirm_credit", tendered < total);
            if (!profileId[0].isEmpty()) try { payload.put("customer_id", profileId[0]); } catch (Exception e) { throw new IllegalArgumentException("Unable to prepare sale."); }
            host.command("create_sale", payload, saved -> {
                JSONObject sale = nested(saved, "sale");
                long balance = balance(sale);
                String status = balance > 0 ? (tendered > 0 ? "Partially paid" : "Unpaid") : "Paid";
                String detail = value(name) + " · " + q + " gallons · " + status + (balance > 0 ? " · Credit " + host.money(balance) : " · Change " + host.money(Math.max(0, tendered - total)))
                    + (available[0] >= 0 ? " · Stock " + available[0] + " → " + (available[0] - q) : "");
                host.rememberChoice("pos_customer", "");
                pos();
                host.info("Sale " + sale.optString("reference") + " saved", detail);
            });
        }));
        cashExact(cCard, cash, totalView, gallons);
        recompute[0].run();
    }
    /** "Exact" quick amount: cash equals the current total. */
    private void cashExact(LinearLayout cashCard, EditText cash, TextView totalView, EditText gallons) {
        for (int i = 0; i < cashCard.getChildCount(); i++) {
            View child = cashCard.getChildAt(i);
            if (child instanceof LinearLayout && ((LinearLayout) child).getChildCount() == 4) {
                View exact = ((LinearLayout) child).getChildAt(0);
                exact.setOnClickListener(v -> { try { long total = Math.multiplyExact((long) quantity(gallons), 3500); cash.setText(BigDecimal.valueOf(total, 2).setScale(2).toPlainString()); } catch (IllegalArgumentException e) { host.fieldError(gallons, e.getMessage()); } });
            }
        }
    }
    private void saleResult(JSONObject sale) {
        LinearLayout p = page("Sale saved", id(sale));
        host.info("Walk-in sale completed.", sale.optString("reference"));
        LinearLayout c = ui.card(p);
        ui.pair(c, "Transaction", sale.optString("reference"), Ui.INK, true);
        ui.pair(c, "Customer", sale.optString("customer_name"), Ui.INK, false);
        ui.pair(c, "Date/time", host.timestamp(sale.optString("created_at")), Ui.INK, false);
        ui.pair(c, "Recorded by", sale.optString("recorded_by_name", sale.optString("recorded_by")), Ui.INK, false);
        ui.pair(c, "Total", host.money(sale.optLong("total_centavos")), Ui.INK, false);
        ui.pair(c, "Payment status", title(sale.optString("payment_status")), Ui.INK, false);
        ui.pair(c, "Balance", host.money(balance(sale)), balance(sale) > 0 ? Ui.AMBER : Ui.INK, true);
        ui.muted(c, "Keep the transaction reference for later credit payments.").setTextSize(11);
        if (balance(sale) > 0) host.button(p, "Collect payment", Ui.AMBER_BTN, () -> payment(sale, "sale"));
        host.mainAction("Done", Ui.FILLED, this::pos);
    }

    // ---------------------------------------------------------------- payments
    public void payments() { payments(null); }
    private void payments(String customerId) {
        LinearLayout p = page("Payments", customerId == null ? "" : customerId);
        boolean split = host.isOwner() && host.tablet();
        LinearLayout listSide = p, pane = null;
        if (split) {
            LinearLayout h = new LinearLayout(host); h.setOrientation(LinearLayout.HORIZONTAL); p.addView(h, new LinearLayout.LayoutParams(-1, -2));
            listSide = new LinearLayout(host); listSide.setOrientation(LinearLayout.VERTICAL); h.addView(listSide, new LinearLayout.LayoutParams(0, -2, 1));
            pane = new LinearLayout(host); pane.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(0, -2, 1.2f); dl.leftMargin = ui.dp(16); h.addView(pane, dl);
        }
        final LinearLayout form = pane;
        EditText search = search(listSide, "Find customer, Order ID or Walk-in credit reference", "Find customer, Order ID or Walk-in credit reference");
        ui.muted(listSide, "Select one unpaid or partially paid reference. Payments are recorded on the actual server receipt date.").setTextSize(11);
        ui.heading(listSide, "Unpaid and Partially Paid Orders"); LinearLayout orderBalances = stack(listSide);
        ui.heading(listSide, "Named Walk-in Credits"); LinearLayout saleBalances = stack(listSide);
        long[] searchRequest = {0};
        Runnable load = () -> {
            long requested = ++searchRequest[0];
            JSONObject filter = json("search", value(search)); if (customerId != null) try { filter.put("customer_id", customerId); } catch (Exception ignored) {}
            query("orders", filter, response -> { if (requested == searchRequest[0]) { orderBalances.removeAllViews(); renderBalances(orderBalances, items(response), "order", form); } });
            query("sales", filter, response -> { if (requested == searchRequest[0]) { saleBalances.removeAllViews(); renderBalances(saleBalances, items(response), "sale", form); } });
        };
        watch(search, load); load.run();
        host.button(listSide, "Payment history", Ui.OUTLINE, () -> {
            String selectedSearch = value(search);
            LinearLayout history = page("Payment history");
            JSONObject filter = json("search", selectedSearch);
            if (customerId != null) try { filter.put("customer_id", customerId); } catch (Exception ignored) {}
            query("payments", filter, response -> renderReceipts(history, items(response)));
        });
    }
    private void renderBalances(LinearLayout p, JSONArray rows, String kind, LinearLayout form) {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i); if (r == null || balance(r) <= 0) continue; count++;
            LinearLayout card = ui.card(p); card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12)); card.setClickable(true);
            LinearLayout row = ui.row(card);
            LinearLayout names = new LinearLayout(host); names.setOrientation(LinearLayout.VERTICAL);
            names.addView(ui.tv(r.optString("customer_name"), 15, Ui.INK, ui.headBold));
            names.addView(ui.tv(r.optString("reference"), 12, Ui.MUTED, ui.body));
            row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout right = new LinearLayout(host); right.setOrientation(LinearLayout.VERTICAL); right.setGravity(Gravity.END);
            right.addView(ui.paymentPill(r.optString("payment_status")));
            right.addView(ui.tv(host.money(balance(r)), 14, Ui.AMBER, ui.headBold), ui.lp(-2, -2, 0, 4, 0, 0));
            row.addView(right);
            card.setContentDescription(r.optString("reference") + " " + r.optString("customer_name") + " Balance " + host.money(balance(r)));
            card.setOnClickListener(v -> { if (form != null) { form.removeAllViews(); paymentForm(form, r, kind, null); } else payment(r, kind); });
        }
        if (count == 0) line(p, "No outstanding " + (kind.equals("order") ? "orders" : "Walk-in credits") + " found.");
    }
    private void payment(JSONObject target, String kind) {
        LinearLayout p = page("Record Payment", target.optString("reference"));
        paymentForm(p, target, kind, null);
    }
    private void paymentForm(LinearLayout p, JSONObject target, String kind, Runnable ignored) {
        long due = balance(target);
        LinearLayout head = ui.card(p);
        LinearLayout headRow = ui.row(head);
        LinearLayout headNames = new LinearLayout(host); headNames.setOrientation(LinearLayout.VERTICAL);
        headNames.addView(ui.tv("Collect payment · " + target.optString("customer_name"), 18, Ui.INK, ui.headBold));
        headNames.addView(ui.tv(target.optString("reference") + " · Total " + host.money(target.optLong("total_centavos")) + " · Paid " + host.money(paid(target)), 12, Ui.MUTED, ui.body));
        headRow.addView(headNames, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout balanceBox = new LinearLayout(host); balanceBox.setOrientation(LinearLayout.VERTICAL); balanceBox.setGravity(Gravity.END);
        balanceBox.addView(ui.tv("Balance", 12, Ui.MUTED, ui.body)); balanceBox.addView(ui.tv(host.money(due), 24, Ui.AMBER, ui.headBold));
        headRow.addView(balanceBox);
        head.setContentDescription("Outstanding balance " + host.money(due));
        LinearLayout both = new LinearLayout(host); both.setOrientation(host.tablet() ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL); p.addView(both, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout card = new LinearLayout(host); card.setOrientation(LinearLayout.VERTICAL); card.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 14)); card.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        LinearLayout.LayoutParams cl = host.tablet() ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2); cl.bottomMargin = ui.dp(12); if (host.tablet()) cl.rightMargin = ui.dp(6); both.addView(card, cl);
        LinearLayout after = new LinearLayout(host); after.setOrientation(LinearLayout.VERTICAL); after.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 14)); after.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
        LinearLayout.LayoutParams al = host.tablet() ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2); al.bottomMargin = ui.dp(12); if (host.tablet()) al.leftMargin = ui.dp(6); both.addView(after, al);
        ui.muted(card, "Amount received").setTypeface(ui.bodySemi);
        String full = BigDecimal.valueOf(due, 2).setScale(2).toPlainString();
        LinearLayout quickHost = new LinearLayout(host); quickHost.setOrientation(LinearLayout.VERTICAL); card.addView(quickHost, new LinearLayout.LayoutParams(-1, -2));
        EditText amount = host.input(card, "Payment amount (₱)", "", CASH); amount.setHint("₱0.00");
        host.quickAmounts(quickHost, amount, new String[]{"Full " + host.money(due), "₱100", "₱200", "₱500"}, new String[]{full, "100", "200", "500"});
        ui.pair(card, "Method", "Cash", Ui.INK, false);
        ui.pair(card, "Payment date", "Today · " + shortDate(host.today()), Ui.INK, false);
        ui.pair(card, "Recorded by", host.recorder(), Ui.INK, false);
        ui.muted(card, "Receipt date/time and reference are assigned by the server.").setTextSize(11);
        if (host.isOwner()) ui.muted(card, "Today: " + host.today() + " (Asia/Manila)").setTextSize(11);
        ui.muted(after, "After saving").setTypeface(ui.bodySemi);
        TextView[] remaining = ui.pair(after, "Balance", "—", Ui.AMBER, true), result = ui.pair(after, "Status", "—", Ui.INK, false);
        TextView receiptNote = ui.tv("Receipt shown after you save.", 13, Ui.PRIMARY, ui.body); receiptNote.setBackground(ui.shape(Ui.TONAL, 0, 12)); receiptNote.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10)); after.addView(receiptNote, ui.lp(-1, -2, 0, 10, 0, 0));
        watch(amount, () -> { try { long c = cents(amount, ""); if (c <= 0 || c > due) throw new IllegalArgumentException(); remaining[1].setText(host.money(due - c)); result[1].setText(c == due ? "Paid" : "Partially paid"); } catch (Exception e) { remaining[1].setText("—"); result[1].setText("—"); } });
        Runnable save = () -> valid(() -> {
            long c;
            try { c = cents(amount, "Enter an amount greater than zero and not above the balance."); } catch (IllegalArgumentException e) { host.fieldError(amount, e.getMessage()); return; }
            if (c <= 0 || c > due) { host.fieldError(amount, "Enter an amount greater than zero and not above the balance."); return; }
            host.command("record_payment", json("reference", target.optString("reference"), "amount_centavos", c), result2 -> {
                JSONObject receipt = nested(result2, "payment"); LinearLayout saved = page("Payment recorded");
                host.info("Payment recorded.", target.optString("reference") + " · " + host.money(c));
                LinearLayout sc = ui.card(saved);
                ui.pair(sc, "Payment reference", receipt.optString("reference"), Ui.INK, true);
                ui.pair(sc, "Related reference", target.optString("reference"), Ui.INK, false);
                ui.pair(sc, "Amount", host.money(c), Ui.INK, false);
                ui.pair(sc, "Balance", host.money(receipt.optLong("balance_centavos", due - c)), Ui.AMBER, true);
                ui.pair(sc, "Payment status", title(receipt.optString("payment_status", due == c ? "paid" : "partially_paid")), Ui.INK, false);
                ui.pair(sc, "Receipt time", host.timestamp(receipt.optString("received_at", receipt.optString("created_at"))), Ui.INK, false);
                ui.pair(sc, "Recorded by", receipt.optString("recorded_by_name", receipt.optString("recorded_by")), Ui.INK, false);
                host.mainAction("Done", Ui.FILLED, () -> { if (kind.equals("order")) order(id(target)); else payments(); });
            });
        });
        if (p == host.currentContent()) host.mainAction("Save payment", Ui.FILLED, save);
        else {
            LinearLayout buttons = ui.row(p); buttons.setGravity(Gravity.END);
            Button cancel = host.button(buttons, "Cancel", Ui.OUTLINE, () -> { p.removeAllViews(); });
            cancel.setLayoutParams(ui.lp(-2, -2, 0, 0, 8, 0));
            Button saveButton = host.button(buttons, "Save payment", Ui.FILLED, save); saveButton.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        }
    }
    private void renderReceipts(LinearLayout p, JSONArray receipts) {
        if (receipts.length() == 0) line(p, "No payments recorded.");
        for (int i = 0; i < receipts.length(); i++) {
            JSONObject r = receipts.optJSONObject(i); if (r == null) continue;
            LinearLayout row = ui.column(p); row.setPadding(0, ui.dp(6), 0, ui.dp(6));
            LinearLayout top = ui.row(row);
            top.addView(ui.tv(r.optString("reference"), 14, Ui.INK, ui.headBold), new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(ui.tv(host.money(r.optLong("amount_centavos")), 14, Ui.GREEN, ui.headBold));
            ui.muted(row, "Related reference: " + r.optString("invoice_reference", r.optString("charge_reference", r.optString("related_reference"))));
            ui.muted(row, "Received: " + host.timestamp(r.optString("received_at", r.optString("created_at"))) + " · Recorded by: " + r.optString("recorded_by_name", r.optString("recorded_by"))).setTextSize(11);
        }
    }
}
