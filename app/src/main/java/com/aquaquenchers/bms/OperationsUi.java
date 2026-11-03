package com.aquaquenchers.bms;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import android.widget.AdapterView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Shared native operational screens. Every successful write comes from the authenticated RPC. */
public final class OperationsUi {
    private final MainActivity host;
    private long generation;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS;
    private static final int QTY = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final int CASH = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
    private static final String[] TYPES = {"Choose customer type", "Household", "Dealer", "Walk-in"};
    public OperationsUi(MainActivity host) { this.host = host; }

    public boolean restore(String route){switch(route){case "Customers":customers();return true;case "Orders":orders();return true;case "Pending gallons / Carry-over Details":pending();return true;case "Walk-in Sale / POS":pos();return true;case "Payments":payments();return true;case "Create Order":newOrder(null);return true;case "Add Customer":customerForm(null,null);return true;}return false;}
    private LinearLayout page(String title,String id){generation++;return host.page(title,id);}
    private LinearLayout page(String title) { generation++; return host.page(title); }
    private void query(String resource, JSONObject data, Consumer<JSONObject> action) {
        long requestedGeneration = generation;
        host.query(resource, data, result -> { if (requestedGeneration == generation) action.accept(result); });
    }
    private JSONObject json(Object... pairs) { return host.json(pairs); }
    private static String value(EditText input) { return input.getText().toString().trim(); }
    private static String title(String value){return Rules.label(value);}
    private void line(LinearLayout parent, String text) { host.text(parent, text); }
    private TextView liveLine(LinearLayout parent, String text) {
        TextView view = new TextView(host); view.setText(text); view.setTextSize(16); view.setPadding(0, 12, 0, 12); parent.addView(view); return view;
    }
    private void watch(EditText field, Runnable changed) {
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { changed.run(); }
            @Override public void afterTextChanged(Editable e) {}
        });
    }
    private int quantity(EditText input) {
        String text = value(input);
        if (!text.matches("-?[0-9]+")) throw new IllegalArgumentException("Enter a whole-number gallon quantity.");
        try {
            int q = Integer.parseInt(text);
            if (q <= 0) throw new IllegalArgumentException("Quantity must be greater than zero.");
            return q;
        } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a whole-number gallon quantity."); }
    }
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
    private long paid(JSONObject item) { return item.optLong("paid_centavos", item.optLong("amount_paid_centavos")); }
    private long balance(JSONObject item) { return item.has("balance_centavos") ? item.optLong("balance_centavos") : item.optLong("total_centavos") - paid(item); }
    private void valid(Runnable action) { try { action.run(); } catch (IllegalArgumentException e) { host.error(e.getMessage()); } }
    private void cancel(LinearLayout p, Runnable action) { host.button(p, "Cancel", action); }

    public void customers() {
        LinearLayout p = page("Customers");
        EditText search = host.input(p, "Search by name or contact number", "", InputType.TYPE_CLASS_TEXT);
        host.button(p, "Add Customer", () -> customerForm(null, null));
        LinearLayout results = new LinearLayout(host); results.setOrientation(LinearLayout.VERTICAL); p.addView(results);
        Runnable find = () -> query("customers", json("search", value(search)), result -> {
            results.removeAllViews(); JSONArray rows = items(result);
            if (rows.length() == 0) line(results, "No customers found.");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject c = rows.optJSONObject(i); if (c == null) continue;
                line(results, c.optString("name") + " · " + title(c.optString("customer_type")) + "\n" + c.optString("contact_number") + "\nBalance " + host.money(balance(c)));
                host.button(results, "View customer", () -> customer(id(c)));
            }
        });
        host.button(p, "Search", find); find.run();
    }

    public void customer(String customerId) {
        LinearLayout p = page("Customer Profile");
        query("customer", json("id", customerId), response -> {
            JSONObject c = nested(response, "customer");
            line(p, c.optString("name") + "\n" + title(c.optString("customer_type")) + "\n" + c.optString("contact_number") + "\n" + c.optString("address"));
            line(p, "Outstanding balance: " + host.money(balance(c)));
            host.button(p, "Edit Customer", () -> customerForm(c, null));
            host.button(p, "Create Order", () -> newOrder(customerId));
            host.button(p, "Record Payment", () -> payments(customerId));
            line(p, "Order history and unpaid orders");
            query("orders", json("customer_id", customerId), orders -> renderOrders(p, items(orders), false));
            line(p, "Payment history");
            query("payments", json("customer_id", customerId), payments -> renderReceipts(p, items(payments)));
            host.button(p, "Back to Customers", this::customers);
        });
    }

    private void customerForm(JSONObject existing, Consumer<String> afterSave) {
        LinearLayout p = page(existing == null ? "Add Customer" : "Edit Customer",existing==null?"":id(existing));
        EditText name = host.input(p, "Full name", existing == null ? "" : existing.optString("name"), TEXT);
        Spinner type = host.select(p, "Customer type", TYPES);
        if (existing != null) {
            String t = existing.optString("customer_type"); type.setSelection(t.equals("household") ? 1 : t.equals("dealer") ? 2 : t.equals("walk_in") ? 3 : 0);
        }
        host.restoreSelection(type);
        EditText contact = host.input(p, "Contact number", existing == null ? "" : existing.optString("contact_number"), InputType.TYPE_CLASS_PHONE);
        EditText address = host.input(p, "Address", existing == null ? "" : existing.optString("address"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        host.button(p, "Save Customer", () -> valid(() -> {
            if (value(name).isEmpty() || value(contact).isEmpty() || value(address).isEmpty() || type.getSelectedItemPosition()==0) throw new IllegalArgumentException("Complete the required fields.");
            if (type.getSelectedItemPosition() == 0) throw new IllegalArgumentException("Choose Household, Dealer, or Walk-in.");
            String number = value(contact);
            if (!number.matches("[0-9]{11}")) throw new IllegalArgumentException("Please enter a valid contact number. Use 11 digits.");
            String t = type.getSelectedItemPosition() == 1 ? "household" : type.getSelectedItemPosition() == 2 ? "dealer" : "walk_in";
            JSONObject fields = json("name", value(name), "customer_type", t, "contact_number", number, "address", value(address));
            if (existing != null) try { fields.put("id", id(existing)); } catch (Exception e) { throw new IllegalArgumentException("Unable to prepare customer."); }
            host.command(existing == null ? "create_customer" : "update_customer", fields, result -> {
                if (afterSave != null) afterSave.accept(id(nested(result,"customer"))); else if (existing != null) customer(id(existing)); else customers();
                host.info("Customer saved.");
            });
        }));
        cancel(p, ()->{if(afterSave==null)customers();else afterSave.accept(null);});
    }

    public void orders() {
        LinearLayout p = page("Orders");
        EditText search = host.input(p, "Search Order ID or customer", "", InputType.TYPE_CLASS_TEXT);
        Spinner status = host.select(p, "Status", new String[]{"All", "Ordered", "For Pickup", "Picked Up", "Refilling", "Ready for Delivery", "Undelivered", "Delivered", "Rescheduled"});
        host.button(p, "Create Order", () -> newOrder(null));
        host.button(p, "Walk-in Sale", this::pos);
        host.button(p, "Pending gallons", this::pending);
        LinearLayout rows = new LinearLayout(host); rows.setOrientation(LinearLayout.VERTICAL); p.addView(rows);
        Runnable load = () -> query("orders", json("search", value(search)), response -> {
            rows.removeAllViews(); JSONArray filtered = new JSONArray(); JSONArray all = items(response);
            String s = status.getSelectedItem().toString().toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
            for (int i = 0; i < all.length(); i++) { JSONObject o = all.optJSONObject(i); if (o != null && (s.equals("all") || s.equals(o.optString("status")))) filtered.put(o); }
            renderOrders(rows, filtered, false);
        });
        host.button(p, "Search / filter", load); load.run();
    }

    private void renderOrders(LinearLayout p, JSONArray rows, boolean pending) {
        if (rows.length() == 0) line(p, pending ? "No pending deliveries." : "No orders found.");
        for (int i = 0; i < rows.length(); i++) {
            JSONObject o = rows.optJSONObject(i); if (o == null) continue;
            line(p, o.optString("reference") + " · " + o.optString("customer_name") + "\n" + title(o.optString("status")) + " · " + title(o.optString("payment_status")) + "\n" + o.optInt("quantity") + " gallons · Total " + host.money(o.optLong("total_centavos")) + " · Balance " + host.money(balance(o)));
            if (pending) {
                String d = o.optString("business_date"); long days = 0;
                try { days = ChronoUnit.DAYS.between(LocalDate.parse(d), LocalDate.parse(host.today())); } catch (Exception ignored) {}
                line(p, "Original date: " + d + " · " + Math.max(0, days) + " days waiting\nPending gallons: " + pending(o));
            }
            host.button(p, "View Order", () -> order(id(o)));
        }
    }
    private int pending(JSONObject o) { return o.optInt("pending_quantity", o.optInt("quantity") - o.optInt("delivered_quantity")); }

    public void pending() {
        LinearLayout p = page("Pending gallons / Carry-over Details");
        query("orders", json(), result -> {
            List<JSONObject> records = new ArrayList<>(); JSONArray all = items(result);
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i); if (o != null && (o.optString("status").equals("undelivered") || o.optString("status").equals("ready_for_delivery")) && pending(o) > 0) records.add(o);
            }
            records.sort(Comparator.comparing(o -> o.optString("business_date")));
            JSONArray ordered = new JSONArray(); for (JSONObject o : records) ordered.put(o); renderOrders(p, ordered, true);
            host.button(p, "Back to Orders", this::orders);
        });
    }

    public void newOrder(String selectedCustomerId) {
        LinearLayout p = page("Create Order");
        String desiredCustomer=selectedCustomerId==null?host.choice("order_customer"):selectedCustomerId;
        Spinner customer = host.select(p, "Customer", new String[]{"Loading customers…"});
        TextView details = liveLine(p, "Select a customer to see type, address and most recent quantity.");
        EditText businessDate = date(p, "Order date", host.today());
        EditText quantity = host.input(p, "Gallon quantity", "", QTY);
        TextView total = liveLine(p, "Unit price and total will appear after selection.");
        line(p, "Initial status: Ordered. Entered Order date is separate from server-recorded encoding time.");
        List<JSONObject> list = new ArrayList<>();
        Runnable preview = () -> {
            int index = customer.getSelectedItemPosition() - 1;
            if(index<0||index>=list.size()){if(!list.isEmpty())host.rememberChoice("order_customer","");return;}
            JSONObject c = list.get(index);host.rememberChoice("order_customer",id(c)); long unit = c.optString("customer_type").equals("dealer") ? 3000 : 3500;
            details.setText(c.optString("name") + " · " + title(c.optString("customer_type")) + "\nDelivery address: " + c.optString("address") + "\nMost recent order quantity: " + (c.has("previous_order_quantity") ? c.optInt("previous_order_quantity") + " gallons" : "Not yet available"));
            try { total.setText("Unit price: " + host.money(unit) + "\nTotal: " + host.money(Math.multiplyExact((long) quantity(quantity), unit))); }
            catch (Exception e) { total.setText("Unit price: " + host.money(unit) + "\nEnter a positive whole-number quantity."); }
        };
        watch(quantity, preview);
        customer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int position, long ignored) { preview.run(); }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });
        query("customers", json(), response -> {
            JSONArray all = items(response); List<String> labels = new ArrayList<>(); labels.add("Choose customer");
            int selection = 0;
            for (int i = 0; i < all.length(); i++) {
                JSONObject c = all.optJSONObject(i); if (c == null) continue; list.add(c); labels.add(c.optString("name") + " · " + title(c.optString("customer_type")));
                if (id(c).equals(desiredCustomer)) selection = list.size();
            }
            customer.setAdapter(new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, labels));customer.setSelection(selection);
        });
        host.button(p, "Add New Customer", () -> customerForm(null, selected->newOrder(selected)));
        host.button(p, "Save Order", () -> valid(() -> {
            int position = customer.getSelectedItemPosition() - 1;
            if (position < 0 || position >= list.size()) throw new IllegalArgumentException("Choose a customer.");
            int q;try{q=quantity(quantity);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Enter a valid gallon quantity.");} String d;
            try { d = LocalDate.parse(value(businessDate)).toString(); } catch (Exception e) { throw new IllegalArgumentException("Complete the required fields."); }
            host.command("create_order", json("customer_id", id(list.get(position)), "quantity", q, "business_date", d), result -> {
                JSONObject o = nested(result, "order"); if (!id(o).isEmpty()) order(id(o)); else orders();host.info("Order saved.");
            });
        }));
        cancel(p, this::orders);
    }

    public void order(String orderId) {
        LinearLayout p = page("Order Details");
        query("order", json("id", orderId), response -> {
            JSONObject o = nested(response, "order");
            line(p, o.optString("reference") + "\n" + o.optString("customer_name") + " · " + title(o.optString("customer_type")) + "\n" + o.optString("contact_number") + "\n" + o.optString("address"));
            line(p, "Order date: " + o.optString("business_date") + "\nRecorded: " + host.timestamp(o.optString("created_at")) + "\nRecorded by: " + o.optString("recorded_by_name", o.optString("recorded_by")));
            line(p, "Status: " + title(o.optString("status")) + "\nOriginal expected gallons: " + o.optInt("expected_quantity", o.optInt("quantity")) + "\nAdjusted order gallons: " + o.optInt("quantity") + "\nPicked up: " + o.optInt("collected_quantity") + "\nShortfall: " + o.optInt("shortfall") + "\nDelivered: " + o.optInt("delivered_quantity") + "\nPending: " + pending(o) + "\nReserved for this order: " + o.optInt("reserved_quantity"));
            line(p, "Fixed unit price: " + host.money(o.optLong("unit_price_centavos")) + "\nOrder total: " + host.money(o.optLong("total_centavos")) + "\nAmount paid: " + host.money(paid(o)) + "\nBalance: " + host.money(balance(o)) + "\nPayment status: " + title(o.optString("payment_status")));
            String status = o.optString("status");
            if (status.equals("ordered")) transition(p, o, "for_pickup", "For Pickup");
            if (status.equals("rescheduled")) {
                line(p, "Rescheduled pickup: " + o.optString("rescheduled_date"));
                transition(p, o, "for_pickup", "Resume For Pickup");
            }
            if (status.equals("for_pickup")) {
                host.button(p, "Record Pickup", () -> pickup(o)); host.button(p, "Reschedule Pickup", () -> reschedule(o));
            }
            if (status.equals("picked_up")) transition(p, o, "refilling", "Start Refilling");
            if (status.equals("refilling")) transition(p, o, "ready_for_delivery", "Ready for Delivery");
            if (status.equals("ready_for_delivery") || status.equals("undelivered")) host.button(p, "Record Delivery", () -> delivery(o));
            if (balance(o) > 0) host.button(p, "Record Payment", () -> payment(o, "order"));
            JSONArray receipts=o.optJSONArray("payments");if(receipts!=null){line(p,"Payment history");renderReceipts(p,receipts);}
            JSONArray deliveries=o.optJSONArray("deliveries");if(deliveries!=null&&deliveries.length()>0){line(p,"Delivery history");for(int i=0;i<deliveries.length();i++){JSONObject d=deliveries.optJSONObject(i);if(d!=null)line(p,d.optInt("quantity")+" gallons • "+host.timestamp(d.optString("created_at"))+" • Recorded by: "+d.optString("recorded_by_name",d.optString("recorded_by")));}}
            host.button(p, "Back to Orders", this::orders);
        });
    }
    private void transition(LinearLayout p, JSONObject order, String status, String label) {
        host.button(p, label, () -> host.showDialog(new AlertDialog.Builder(host).setTitle(label).setMessage("Save this next eligible stage for " + order.optString("reference") + "?").setNegativeButton("Cancel", null).setPositiveButton("Save", (dialog, which) -> host.command("transition_order", json("order_id", id(order), "status", status), saved -> { order(id(order));host.info("Order updated."); })).create()));
    }

    private void pickup(JSONObject o) {
        LinearLayout p = page("Record Pickup",id(o)); int expected = o.optInt("expected_quantity", o.optInt("quantity")); long unit = o.optLong("unit_price_centavos");
        line(p, o.optString("reference") + " · " + o.optString("customer_name") + "\n" + o.optString("contact_number") + "\n" + o.optString("address") + "\nPickup date: " + host.today() + "\nOriginal expected gallons: " + expected + "\nFixed unit price: " + host.money(unit));
        EditText collected = host.input(p, "Actual collected gallons", "", QTY);
        TextView adjusted = liveLine(p, "Enter actual collected gallons to review the shortfall and adjusted total.");
        watch(collected, () -> {
            try { int q = quantity(collected); if (q > expected) throw new IllegalArgumentException(); adjusted.setText("Shortfall: " + (expected - q) + "\nAdjusted order gallons: " + q + "\nAdjusted total: " + host.money(Math.multiplyExact((long) q, unit))); }
            catch (Exception e) { adjusted.setText("Enter a positive whole number no greater than " + expected + "."); }
        });
        host.button(p, "Save Pickup", () -> valid(() -> {
            int q = quantity(collected);
            if (q > expected) throw new IllegalArgumentException("Collected quantity cannot exceed the expected quantity.");
            if (Math.multiplyExact((long) q, unit) < paid(o)) throw new IllegalArgumentException("Adjusted total cannot be less than the amount already paid.");
            host.command("record_pickup", json("order_id", id(o), "quantity", q), saved -> { order(id(o));host.info("Pickup recorded."); });
        }));
        host.button(p, "Reschedule Pickup", () -> reschedule(o)); cancel(p, () -> order(id(o)));
    }
    private void reschedule(JSONObject o) {
        LinearLayout p = page("Reschedule Pickup",id(o)); line(p, o.optString("reference") + " · " + o.optString("customer_name"));
        EditText next = date(p, "New pickup date", host.today());
        host.button(p, "Save Pickup Result", () -> valid(() -> {
            LocalDate d;
            try { d = LocalDate.parse(value(next)); } catch (Exception e) { throw new IllegalArgumentException("Select today or a future date."); }
            if (d.isBefore(LocalDate.parse(host.today()))) throw new IllegalArgumentException("Select today or a future date.");
            host.command("reschedule_pickup", json("order_id", id(o), "scheduled_date", d.toString()), saved -> { order(id(o));host.info("Order rescheduled."); });
        })); cancel(p, () -> order(id(o)));
    }
    private void delivery(JSONObject o) {
        LinearLayout p = page("Record Delivery",id(o)); int remaining = pending(o); int delivered = o.optInt("delivered_quantity");
        line(p, o.optString("reference") + " · " + o.optString("customer_name") + "\nPreviously delivered: " + delivered + "\nRemaining gallons: " + remaining + "\nOrder reservation: " + o.optInt("reserved_quantity") + "\nRecorded at actual server time on save.");
        EditText trip = host.input(p, "Gallons delivered this trip", "", QTY);
        TextView preview = liveLine(p, "Enter delivered gallons to review cumulative delivered and pending quantities.");
        watch(trip, () -> {
            try { int q = quantity(trip); if (q > remaining) throw new IllegalArgumentException(); preview.setText("Cumulative delivered: " + (delivered + q) + "\nPending after saving: " + (remaining - q) + "\nResult: " + (q == remaining ? "Delivered" : "Undelivered")); }
            catch (Exception e) { preview.setText("Enter a positive whole number within the " + remaining + " remaining gallons."); }
        });
        host.button(p, "Save Delivery", () -> valid(() -> {
            int q = quantity(trip);
            if (q > remaining) throw new IllegalArgumentException("Delivered quantity cannot exceed the remaining " + remaining + " gallons.");
            if (o.has("reserved_quantity") && q > o.optInt("reserved_quantity")) throw new IllegalArgumentException("Not enough reserved gallons for this order.");
            host.command("record_delivery", json("order_id", id(o), "quantity", q), saved -> { order(id(o));host.info("Delivery recorded."); });
        })); cancel(p, () -> order(id(o)));
    }

    public void pos() {
        LinearLayout p = page("Walk-in Sale / POS");
        line(p, "Purified water · fixed price ₱35.00 per gallon. A saved customer profile is optional.");
        TextView inventory = liveLine(p, "Checking unreserved filled availability…");
        final int[] available = {-1};
        query("inventory", json(), response -> { JSONObject i = nested(response, "inventory"); available[0] = i.optInt("filled_available", i.optInt("available_filled", -1)); inventory.setText(available[0] < 0 ? "Availability will be checked by the server when saving." : "Filled available: " + available[0]); });
        EditText name = host.input(p, "Customer name", "", TEXT);
        Spinner profile = host.select(p, "Saved customer (optional)", new String[]{"No saved profile"});List<JSONObject> customers = new ArrayList<>();String desiredProfile=host.choice("pos_customer");boolean[] profilesReady={false};
        query("customers", json(), response -> {
            List<String> names = new ArrayList<>(); names.add("No saved profile"); JSONArray all = items(response);
            for (int i = 0; i < all.length(); i++) { JSONObject c = all.optJSONObject(i); if (c != null) { customers.add(c); names.add(c.optString("name")); } }
            int selected=0;for(int i=0;i<customers.size();i++)if(id(customers.get(i)).equals(desiredProfile))selected=i+1;profile.setAdapter(new ArrayAdapter<>(host, android.R.layout.simple_spinner_dropdown_item, names));profile.setSelection(selected);profile.post(()->profilesReady[0]=true);
        });
        profile.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long ignored) { if(position>0&&position<=customers.size()){JSONObject c=customers.get(position-1);String prior=host.choice("pos_customer");if((profilesReady[0]&&!id(c).equals(prior))||value(name).isEmpty())name.setText(c.optString("name"));host.rememberChoice("pos_customer",id(c));}else if(profilesReady[0])host.rememberChoice("pos_customer",""); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        EditText gallons = host.input(p, "Gallon quantity", "", QTY);
        EditText cash = host.input(p, "Cash received (₱)", "", CASH);
        TextView summary = liveLine(p, "Enter gallons and cash to review total, change and credit.");
        Runnable preview = () -> {
            try {
                int q = quantity(gallons); long total = Math.multiplyExact((long) q, 3500); long tendered = cents(cash, "Enter a valid cash amount."); if (tendered < 0) throw new IllegalArgumentException();
                summary.setText("Total: " + host.money(total) + "\nCash received: " + host.money(tendered) + "\nChange: " + host.money(Math.max(0, tendered - total)) + "\nRemaining credit: " + host.money(Math.max(0, total - tendered)) + "\nPayment status: " + (tendered >= total ? "Paid" : tendered > 0 ? "Partially Paid" : "Unpaid") + (available[0] >= 0 ? "\nAvailable after sale: " + (available[0] - q) : ""));
            } catch (Exception e) { summary.setText("Enter positive whole-number gallons and valid nonnegative cash."); }
        }; watch(gallons, preview); watch(cash, preview);
        host.button(p, "Continue to payment", () -> valid(() -> {
            if (value(name).isEmpty()) throw new IllegalArgumentException("Enter the customer name.");
            int q = quantity(gallons); long tendered = cents(cash, "Enter a valid cash amount.");
            if (tendered < 0) throw new IllegalArgumentException("Cash received cannot be negative.");
            if (available[0] >= 0 && q > available[0]) throw new IllegalArgumentException("Not enough filled gallons.");
            long total = Math.multiplyExact((long) q, 3500); int index = profile.getSelectedItemPosition() - 1;
            JSONObject payload = json("customer_name", value(name), "quantity", q, "cash_centavos", tendered, "confirm_credit", tendered < total);
            if (index >= 0 && index < customers.size()) try { payload.put("customer_id", id(customers.get(index))); } catch (Exception e) { throw new IllegalArgumentException("Unable to prepare sale."); }
            String text = value(name) + "\n" + q + " gallons × ₱35.00\nTotal: " + host.money(total) + "\nCash: " + host.money(tendered) + "\nChange: " + host.money(Math.max(0, tendered - total)) + "\nRemaining credit: " + host.money(Math.max(0, total - tendered));
            host.showDialog(new AlertDialog.Builder(host).setTitle(tendered < total ? "Confirm credit" : "Walk-in payment").setMessage(text).setNegativeButton("Cancel", null).setPositiveButton(tendered < total ? "Confirm and save" : "Complete Sale", (dialog, which) -> host.command("create_sale", payload, saved -> saleResult(nested(saved, "sale")))).create());
        })); cancel(p, this::orders);
    }
    private void saleResult(JSONObject sale) {
        LinearLayout p = page("Sale saved");
        line(p, "Walk-in sale completed.\n" + sale.optString("reference") + "\nCustomer: " + sale.optString("customer_name") + "\nDate/time: " + host.timestamp(sale.optString("created_at")) + "\nTotal: " + host.money(sale.optLong("total_centavos")) + "\nPayment status: " + title(sale.optString("payment_status")) + "\nBalance: " + host.money(balance(sale)) + "\nKeep the transaction reference for later credit payments.");
        if (balance(sale) > 0) host.button(p, "Record Payment", () -> payment(sale, "sale"));
        host.button(p, "Done", this::orders);
    }

    public void payments() { payments(null); }
    private void payments(String customerId) {
        LinearLayout p = page("Payments");
        EditText search = host.input(p, "Find customer, Order ID or Walk-in credit reference", "", InputType.TYPE_CLASS_TEXT);
        line(p, "Select one unpaid or partially paid reference. Payments are recorded on the actual server receipt date.");
        LinearLayout balances = new LinearLayout(host); balances.setOrientation(LinearLayout.VERTICAL); p.addView(balances);
        Runnable load = () -> {
            balances.removeAllViews();
            JSONObject filter = json("search", value(search)); if (customerId != null) try { filter.put("customer_id", customerId); } catch (Exception ignored) {}
            query("orders", filter, response -> renderBalances(balances, items(response), "order"));
            query("sales", filter, response -> renderBalances(balances, items(response), "sale"));
        };
        host.button(p, "Search balances", load); load.run();
        host.button(p, "Payment history", () -> {
            LinearLayout history = page("Payment history");
            JSONObject filter = customerId == null ? json() : json("customer_id", customerId);
            query("payments", filter, response -> renderReceipts(history, items(response)));
            host.button(history, "Back to Payments", () -> payments(customerId));
        });
    }
    private void renderBalances(LinearLayout p, JSONArray rows, String kind) {
        int count = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject r = rows.optJSONObject(i); if (r == null || balance(r) <= 0) continue; count++;
            line(p, r.optString("reference") + " · " + r.optString("customer_name") + "\n" + title(r.optString("payment_status")) + " · Balance " + host.money(balance(r)));
            host.button(p, "Record Payment", () -> payment(r, kind));
        }
        if (count == 0) line(p, "No outstanding " + (kind.equals("order") ? "orders" : "Walk-in credits") + " found.");
    }
    private void payment(JSONObject target, String kind) {
        LinearLayout p = page("Record Payment",target.optString("reference")); long due = balance(target);
        line(p, target.optString("reference") + " · " + target.optString("customer_name") + "\nOriginal total: " + host.money(target.optLong("total_centavos")) + "\nAmount already paid: " + host.money(paid(target)) + "\nOutstanding balance: " + host.money(due) + "\nMethod: Cash\nReceipt date/time and reference are assigned by the server.");
        EditText amount = host.input(p, "Payment amount (₱)", "", CASH);
        TextView preview = liveLine(p, "Enter payment amount to review remaining balance.");
        watch(amount, () -> { try { long c = cents(amount, ""); if (c <= 0 || c > due) throw new IllegalArgumentException(); preview.setText("Remaining balance: " + host.money(due - c) + "\nResult: " + (c == due ? "Paid" : "Partially Paid")); } catch (Exception e) { preview.setText("Enter a positive amount not above " + host.money(due) + "."); } });
        host.button(p, "Save Payment", () -> valid(() -> {
            long c = cents(amount, "Enter an amount greater than zero and not above the balance.");
            if (c <= 0) throw new IllegalArgumentException("Enter an amount greater than zero and not above the balance.");
            if (c > due) throw new IllegalArgumentException("Enter an amount greater than zero and not above the balance.");
            host.command("record_payment", json("reference", target.optString("reference"), "amount_centavos", c), result -> {
                JSONObject receipt = nested(result, "payment"); LinearLayout saved = page("Payment recorded");
                line(saved, "Payment recorded.\nPayment reference: " + receipt.optString("reference") + "\nRelated reference: " + target.optString("reference") + "\nAmount: " + host.money(c) + "\nBalance: " + host.money(receipt.optLong("balance_centavos", due - c)) + "\nPayment status: " + title(receipt.optString("payment_status", due == c ? "paid" : "partially_paid")) + "\nReceipt time: " + host.timestamp(receipt.optString("received_at", receipt.optString("created_at"))));
                host.button(saved, "Done", () -> { if (kind.equals("order")) order(id(target)); else payments(); });
            });
        })); cancel(p, () -> { if (kind.equals("order")) order(id(target)); else payments(); });
    }
    private void renderReceipts(LinearLayout p, JSONArray receipts) {
        if (receipts.length() == 0) line(p, "No payments recorded.");
        for (int i = 0; i < receipts.length(); i++) {
            JSONObject r = receipts.optJSONObject(i); if (r == null) continue;
            line(p, r.optString("reference") + "\nRelated reference: " + r.optString("invoice_reference", r.optString("charge_reference", r.optString("related_reference"))) + "\n" + host.money(r.optLong("amount_centavos")) + "\nReceived: " + host.timestamp(r.optString("received_at", r.optString("created_at"))) + "\nRecorded by: " + r.optString("recorded_by_name", r.optString("recorded_by")));
        }
    }
}
