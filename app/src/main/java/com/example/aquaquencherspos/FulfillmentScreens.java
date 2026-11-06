package com.example.aquaquencherspos;

import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/** Pickups, rescheduling, deliveries and carry-over gallons (A-05, A-06). */
public final class FulfillmentScreens {

    private FulfillmentScreens() {
    }

    private static boolean matches(JSONObject o, String query) {
        String q = query.toLowerCase(Locale.ROOT);
        return q.isEmpty()
                || BaseScreen.s(o, "order_no").toLowerCase(Locale.ROOT).contains(q)
                || BaseScreen.s(o, "customer_name").toLowerCase(Locale.ROOT).contains(q);
    }

    /** Figma "01 · Pickups". */
    public static class Pickups extends BaseScreen {
        private JSONArray all = new JSONArray();
        private Field date;
        private Field search;
        private Table table;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Pickups", "Scheduled collections of empty gallons");
            LinearLayout card = Ui.titledCard(c, "Scheduled pickups");
            FlowGrid form = FlowGrid.form(c);
            date = Field.date(c, "Pickup date", Fmt.today());
            date.setOnChange(this::render);
            search = Field.search(c, "Search", "Customer or Order ID");
            search.setOnChange(this::render);
            form.addView(date);
            form.addView(search);
            card.addView(Ui.spaced(c, form, 16));
            table = new Table(c, new String[]{"Order / Customer", "Address / Contact", "Expected", "Status"},
                    new float[]{289, 326, 140, 149});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(WrapRow.of(c,
                    Ui.primary(c, "Record Pickup", v -> openSelected(new RecordPickup())),
                    Ui.secondary(c, "Reschedule", v -> openSelected(new Reschedule()))));
            page.addView(card);
            load("orders_view?select=*&status=in.(Ordered,%22For%20Pickup%22,Rescheduled)&order=pickup_date.asc", rows -> {
                all = rows;
                render();
            });
        }

        private void openSelected(BaseScreen screen) {
            if (table.selected() == null) {
                Dialogs.correct(c, "Select an order.", "Tap an order in the list first.");
            } else {
                go(withId(screen, s(table.selected(), "id")));
            }
        }

        private void render() {
            table.clear();
            int shown = 0;
            String day = date.isoDate();
            for (int n = 0; n < all.length(); n++) {
                JSONObject o = all.optJSONObject(n);
                // Unfinished earlier pickups stay visible until they are collected.
                boolean due = s(o, "pickup_date").compareTo(day) <= 0;
                if (due && matches(o, search.value())) {
                    table.addRow(o, new String[]{
                            s(o, "order_no") + " · " + s(o, "customer_name"),
                            s(o, "delivery_address") + " · " + Fmt.contact(s(o, "customer_contact")),
                            Fmt.gallons(i(o, "original_gallons")), s(o, "status")
                    }, new int[]{0, 0, 0, Ui.statusColor(s(o, "status"))});
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No pickups scheduled for this date.");
            } else {
                table.selectFirst();
            }
        }
    }

    /** Figma "02 · Record Pickup". */
    public static class RecordPickup extends BaseScreen {
        private JSONObject order;
        private Field collected;
        private TextView note;
        private final TextView[] collectedOut = new TextView[1];
        private final TextView[] varianceOut = new TextView[1];

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Record Pickup", null);
            loadOne("orders_view?select=*&id=eq." + argId(), this::render);
        }

        private void render(JSONObject o) {
            order = o;
            page.addView(Ui.spaced(c, Ui.body(c, s(o, "customer_name") + " · " + Fmt.contact(s(o, "customer_contact"))
                    + " · " + s(o, "delivery_address")), 16));
            LinearLayout card = Ui.titledCard(c, "Pickup details");
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Order ID", s(o, "order_no") + " · " + s(o, "customer_name")));
            form.addView(Field.readOnly(c, "Pickup date", Fmt.date(Fmt.today())));
            form.addView(Field.readOnly(c, "Expected gallons", String.valueOf(i(o, "original_gallons"))));
            collected = Field.quantity(c, "Actual collected gallons", null);
            collected.setOnChange(this::recalc);
            form.addView(collected);
            card.addView(Ui.spaced(c, form, 16));

            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Expected", String.valueOf(i(o, "original_gallons"))));
            stats.addView(Ui.stat(c, "Collected", "0", Ui.BODY, collectedOut));
            stats.addView(Ui.stat(c, "Variance", String.valueOf(i(o, "original_gallons")), Ui.BODY, varianceOut));
            card.addView(Ui.spaced(c, stats, 12));
            note = Ui.body(c, "");
            card.addView(Ui.spaced(c, note, 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Pickup", v -> submit())));
            page.addView(card);
            recalc();
        }

        private void recalc() {
            int expected = i(order, "original_gallons");
            Integer qty = whole(collected.value());
            boolean valid = qty != null && qty > 0 && qty <= expected;
            collectedOut[0].setText(valid ? String.valueOf(qty) : "0");
            varianceOut[0].setText(String.valueOf(valid ? expected - qty : expected));
            if (valid) {
                double price = d(order, "unit_price");
                note.setText("Save adjusts this " + s(order, "customer_type") + " order to " + qty + " gallons × "
                        + Fmt.peso(price) + " = " + Fmt.peso(qty * price) + ", retains the original " + expected
                        + " and shortfall " + (expected - qty) + ", adds " + qty + " empties and moves the order to Refilling.");
            } else {
                note.setText("Enter the gallons actually collected. It cannot be more than " + expected + ".");
            }
        }

        private void submit() {
            String raw = collected.value();
            Integer qty = whole(raw);
            java.math.BigDecimal number = money(raw);
            if (number != null && number.signum() <= 0) {
                collected.setError("Quantity must be greater than zero.");
                return;
            }
            if (qty == null) {
                collected.setError("Enter a whole-number gallon quantity.");
                return;
            }
            if (qty > i(order, "original_gallons")) {
                collected.setError("Collected quantity cannot exceed the expected quantity.");
                return;
            }
            save("Recording pickup…", "Saving the collected empty-gallon quantity.", "record_pickup",
                    params("p_order_id", argId(), "p_collected", qty), this::startRefilling);
        }

        /** A saved pickup moves straight on to Refilling; the order history still logs Picked Up first. */
        private void startRefilling(JSONObject pickup) {
            android.app.AlertDialog busy = Dialogs.saving(c, "Starting refill…", "Moving the order to Refilling.");
            Api.rpc("set_order_status", params("p_order_id", argId(), "p_status", "Refilling",
                    "p_key", java.util.UUID.randomUUID().toString()), r -> {
                busy.dismiss();
                if (isAdded()) {
                    pickupDone(pickup, "Refilling");
                }
            }, error -> {
                busy.dismiss();
                if (error.sessionExpired) {
                    failed(error);
                } else if (isAdded()) {
                    // The pickup itself is saved, so report it and leave the stage for the order screen.
                    pickupDone(pickup, "Picked Up (not yet moved to Refilling; update it from the order)");
                }
            });
        }

        private void pickupDone(JSONObject r, String status) {
            Dialogs.done(c, "Pickup recorded",
                    s(r, "order_no") + " · " + s(r, "customer_name") + "\n"
                            + "Collected: " + i(r, "collected") + " of " + i(r, "expected")
                            + " · Shortfall: " + i(r, "shortfall") + "\n"
                            + "Adjusted order: " + i(r, "collected") + " × " + Fmt.peso(d(r, "unit_price"))
                            + " = " + Fmt.peso(d(r, "total")) + "\n"
                            + "Status: " + status + " · Empty: " + i(r, "empty_before") + " → " + i(r, "empty_after"),
                    this::back);
        }
    }

    /** Figma "03 · Reschedule Pickup". */
    public static class Reschedule extends BaseScreen {
        private Field notes;
        private Field newDate;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Pickup Result", "Set a new date and mark the order Rescheduled");
            loadOne("orders_view?select=*&id=eq." + argId(), o -> {
                LinearLayout card = Ui.titledCard(c, "Reschedule order");
                FlowGrid form = FlowGrid.form(c);
                form.addView(Field.readOnly(c, "Order", s(o, "order_no") + " · " + s(o, "customer_name")));
                Field result = Field.readOnly(c, "Pickup result", "Rescheduled");
                result.setValueColor(Ui.WARNING);
                form.addView(result);
                notes = Field.text(c, "Notes optional", null);
                form.addView(notes);
                newDate = Field.date(c, "New pickup date", null);
                form.addView(newDate);
                card.addView(Ui.spaced(c, form, 12));
                card.addView(Ui.spaced(c, Ui.body(c, "Select a new date and mark the order Rescheduled. A reason is optional."), 16));
                card.addView(WrapRow.of(c,
                        Ui.secondary(c, "Cancel", v -> back()),
                        Ui.primary(c, "Save Pickup Result", v -> submit())));
                page.addView(card);
            });
        }

        private void submit() {
            if (newDate.dateValue() == null || newDate.dateValue().isBefore(Fmt.today())) {
                newDate.setError("Select today or a future date.");
                return;
            }
            save("Saving reschedule…", "Updating the pickup date and reason.", "reschedule_pickup",
                    params("p_order_id", argId(), "p_new_date", newDate.isoDate(), "p_reason", notes.value()),
                    r -> Dialogs.done(c, "Order rescheduled",
                            s(r, "order_no") + " · " + s(r, "customer_name") + "\n"
                                    + "New pickup date: " + Fmt.date(s(r, "new_date")) + "\n"
                                    + "Order status: Rescheduled"
                                    + (s(r, "reason").isEmpty() ? "" : "\nReason: " + s(r, "reason")),
                            this::back));
        }
    }

    /** Figma "04 · Deliveries". */
    public static class Deliveries extends BaseScreen {
        private JSONArray all = new JSONArray();
        private Field search;
        private Table table;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Deliveries", "Ready orders and remaining gallons from earlier trips");
            LinearLayout card = Ui.titledCard(c, "Ready orders and carry-over");
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Delivery date", Fmt.date(Fmt.today())));
            search = Field.search(c, "Search", "Customer or Order ID");
            search.setOnChange(this::render);
            form.addView(search);
            card.addView(Ui.spaced(c, form, 16));
            table = new Table(c, new String[]{"Order / Customer", "To deliver", "Original date", "Days waiting", "Status"},
                    new float[]{282, 138, 154, 122, 208});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(WrapRow.of(c,
                    Ui.primary(c, "Record Delivery", v -> {
                        if (table.selected() == null) {
                            Dialogs.correct(c, "Select an order.", "Tap an order in the list first.");
                        } else {
                            go(withId(new RecordDelivery(), s(table.selected(), "id")));
                        }
                    }),
                    Ui.secondary(c, "Carry-over Gallons", v -> go(new CarryOver()))));
            page.addView(card);
            load("orders_view?select=*&status=in.(%22Ready%20for%20Delivery%22,Undelivered)&order=order_date.asc", rows -> {
                all = rows;
                render();
            });
        }

        private void render() {
            table.clear();
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject o = all.optJSONObject(n);
                if (matches(o, search.value())) {
                    table.addRow(o, new String[]{
                            s(o, "order_no") + " · " + s(o, "customer_name"),
                            Fmt.gallons(i(o, "pending_gallons")), Fmt.shortDate(s(o, "order_date")),
                            String.valueOf(i(o, "days_waiting")), s(o, "status")
                    }, new int[]{0, 0, 0, 0, Ui.statusColor(s(o, "status"))});
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No orders are waiting for delivery.");
            } else {
                table.selectFirst();
            }
        }
    }

    /** Figma "05 · Record Delivery". */
    public static class RecordDelivery extends BaseScreen {
        private JSONObject order;
        private Field quantity;
        private TextView note;
        private final TextView[] totalOut = new TextView[1];
        private final TextView[] pendingOut = new TextView[1];

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Record Delivery", "Record actual delivered gallons in the order");
            loadOne("orders_view?select=*&id=eq." + argId(), this::render);
        }

        private void render(JSONObject o) {
            order = o;
            LinearLayout card = Ui.titledCard(c, "Delivery details");
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Order", s(o, "order_no") + " · " + s(o, "customer_name")));
            form.addView(Field.readOnly(c, "Remaining expected quantity", Fmt.gallons(i(o, "pending_gallons"))));
            quantity = Field.quantity(c, "Gallons delivered this trip", null);
            quantity.setOnChange(this::recalc);
            form.addView(quantity);
            form.addView(Field.readOnly(c, "Delivery date / time", "Recorded when saved"));
            form.addView(Field.readOnly(c, "Recorded by", recordedBy()));
            card.addView(Ui.spaced(c, form, 16));

            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Previously delivered", String.valueOf(i(o, "delivered_gallons"))));
            stats.addView(Ui.stat(c, "Total delivered", String.valueOf(i(o, "delivered_gallons")), Ui.BODY, totalOut));
            stats.addView(Ui.stat(c, "Pending after saving", String.valueOf(i(o, "pending_gallons")), Ui.BODY, pendingOut));
            card.addView(Ui.spaced(c, stats, 12));
            note = Ui.body(c, "");
            card.addView(Ui.spaced(c, note, 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Delivery", v -> submit())));
            page.addView(card);
            recalc();
        }

        private void recalc() {
            int pending = i(order, "pending_gallons");
            int previous = i(order, "delivered_gallons");
            Integer qty = whole(quantity.value());
            boolean valid = qty != null && qty > 0 && qty <= pending;
            int now = valid ? qty : 0;
            totalOut[0].setText(String.valueOf(previous + now));
            pendingOut[0].setText(String.valueOf(pending - now));
            String after = valid && qty == pending ? "Delivered" : "Undelivered";
            note.setText("Status after saving: " + after + ". A shortfall stays Undelivered and carries over to the next trip.");
        }

        private void submit() {
            Integer qty = whole(quantity.value());
            if (qty == null) {
                quantity.setError("Enter a whole-number gallon quantity.");
                return;
            }
            if (qty <= 0) {
                quantity.setError("Quantity must be greater than zero.");
                return;
            }
            if (qty > i(order, "pending_gallons")) {
                quantity.setError("Delivered quantity cannot exceed the pending quantity.");
                return;
            }
            save("Recording delivery…", "Saving the delivery and remaining gallons.", "record_delivery",
                    params("p_order_id", argId(), "p_quantity", qty),
                    r -> Dialogs.done(c, "Delivery recorded",
                            s(r, "order_no") + " · " + s(r, "customer_name") + "\n"
                                    + "Delivered now: " + Fmt.gallons(i(r, "delivered_now")) + "\n"
                                    + "Total delivered: " + i(r, "total_delivered") + " of " + Fmt.gallons(i(r, "gallons")) + "\n"
                                    + "Pending: " + Fmt.gallons(i(r, "pending")) + "\n"
                                    + "Order status: " + s(r, "status") + "\n"
                                    + "Physical filled: " + i(r, "filled_before") + " → " + i(r, "filled_after")
                                    + " · Reserved: " + i(r, "reserved_before") + " → " + i(r, "reserved_after") + "\n"
                                    + "Available stays " + Fmt.gallons(i(r, "available")),
                            this::back));
        }
    }

    /** Figma "06 · Carry-over": undelivered balances, oldest first. */
    public static class CarryOver extends BaseScreen {
        private JSONArray all = new JSONArray();
        private String customer = "All customers";
        private Field customerField;
        private Table table;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Carry-over Gallons", "Prioritize undelivered balances");
            LinearLayout card = Ui.titledCard(c, "Carry-over gallons · Oldest first");
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Current delivery date", Fmt.date(Fmt.today())));
            customerField = Field.dropdown(c, "Customer", customer, this::chooseCustomer);
            form.addView(customerField);
            card.addView(Ui.spaced(c, form, 16));
            table = new Table(c, new String[]{"Customer / Order", "Original date", "Days outstanding", "Pending gallons"},
                    new float[]{338, 192, 192, 182});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(WrapRow.of(c, Ui.primary(c, "Record Delivery", v -> {
                if (table.selected() == null) {
                    Dialogs.correct(c, "Select an order.", "Tap an order in the list first.");
                } else {
                    go(withId(new RecordDelivery(), s(table.selected(), "id")));
                }
            })));
            page.addView(card);
            load("orders_view?select=*&status=eq.Undelivered&order=order_date.asc", rows -> {
                all = rows;
                render();
            });
        }

        private void chooseCustomer() {
            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
            names.add("All customers");
            for (int n = 0; n < all.length(); n++) {
                names.add(s(all.optJSONObject(n), "customer_name"));
            }
            Dialogs.options(c, "Customer", names.toArray(new String[0]), pick -> {
                customer = pick;
                customerField.setValue(pick);
                render();
            });
        }

        private void render() {
            table.clear();
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject o = all.optJSONObject(n);
                if ("All customers".equals(customer) || customer.equals(s(o, "customer_name"))) {
                    table.addRow(o, new String[]{
                            s(o, "customer_name") + " / " + s(o, "order_no"), Fmt.date(s(o, "order_date")),
                            String.valueOf(i(o, "days_waiting")), String.valueOf(i(o, "pending_gallons"))
                    }, null);
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No carry-over gallons.");
            } else {
                table.selectFirst();
            }
        }
    }
}
