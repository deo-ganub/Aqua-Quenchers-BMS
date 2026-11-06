package com.example.aquaquencherspos;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Orders list, Create Order and Order Details with status updates (A-04). */
public final class OrderScreens {

    static final String[] STATUSES = {
            "Ordered", "For Pickup", "Picked Up", "Refilling", "Ready for Delivery",
            "Delivered", "Undelivered", "Rescheduled"
    };

    private OrderScreens() {
    }

    static double priceFor(String type) {
        return "Dealer".equals(type) ? 30.0 : 35.0;
    }

    /** Figma "01 · Orders". */
    public static class Orders extends BaseScreen {
        private JSONArray all = new JSONArray();
        private LocalDate from;
        private LocalDate to;
        private Field search;
        private Field range;
        private Field status;
        private Field payment;
        private Table table;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Orders", "All orders recorded by the Owner and Staff");
            add(WrapRow.of(c,
                    Ui.primary(c, "Create Order", v -> go(new CreateOrder())),
                    Ui.secondary(c, "Carry-over Gallons", v -> go(new FulfillmentScreens.CarryOver()))));

            LinearLayout find = Ui.titledCard(c, "Find orders");
            FlowGrid form = FlowGrid.form(c);
            search = Field.search(c, "Search", "Order ID or customer name");
            search.setOnChange(this::render);
            range = Field.dropdown(c, "Date range", "All dates", this::chooseRange);
            String[] statusChoices = new String[STATUSES.length + 1];
            statusChoices[0] = "All statuses";
            System.arraycopy(STATUSES, 0, statusChoices, 1, STATUSES.length);
            status = Field.dropdown(c, "Order status", "All statuses",
                    () -> Dialogs.options(c, "Order status", statusChoices, pick -> {
                        status.setValue(pick);
                        render();
                    }));
            payment = Field.dropdown(c, "Payment status", "All payments", () -> Dialogs.options(c, "Payment status",
                    new String[]{"All payments", "Paid", "Partially Paid", "Unpaid"}, pick -> {
                        payment.setValue(pick);
                        render();
                    }));
            form.addView(search);
            form.addView(range);
            form.addView(status);
            form.addView(payment);
            find.addView(Ui.spaced(c, form, 12));
            find.addView(Ui.body(c, "Ordered · For Pickup · Picked Up · Refilling · Ready for Delivery · "
                    + "Delivered · Undelivered · Rescheduled"));
            page.addView(find);

            LinearLayout list = Ui.titledCard(c, "Orders");
            table = new Table(c, new String[]{"Order ID / Date", "Customer / Type", "Gallons", "Order status", "Payment status"},
                    new float[]{232, 216, 97, 200, 159});
            list.addView(Ui.spaced(c, table, 12));
            list.addView(Ui.spaced(c, WrapRow.of(c,
                    Ui.secondary(c, "View Order Details", v -> {
                        if (table.selected() == null) {
                            Dialogs.correct(c, "Select an order.", "Tap an order in the list first.");
                        } else {
                            go(withId(new OrderDetails(), s(table.selected(), "id")));
                        }
                    }),
                    Ui.secondary(c, "Pickups", v -> go(new FulfillmentScreens.Pickups())),
                    Ui.secondary(c, "Deliveries", v -> go(new FulfillmentScreens.Deliveries())),
                    Ui.secondary(c, "Carry-over", v -> go(new FulfillmentScreens.CarryOver()))), 16));
            list.addView(Ui.spaced(c, Ui.label(c, "Walk-in sales"), 8));
            list.addView(WrapRow.of(c, Ui.primary(c, "New counter sale", v -> go(new PaymentScreens.WalkInSale()))));
            page.addView(list);

            refresh();
        }

        private void chooseRange() {
            Dialogs.options(c, "Date range", new String[]{"All dates", "Choose dates"}, pick -> {
                if ("All dates".equals(pick)) {
                    from = null;
                    to = null;
                    range.setValue("All dates");
                    refresh();
                } else {
                    Dialogs.period(c, "Select order dates", "Orders are filtered by their order date.",
                            from != null ? from : Fmt.monthStart(), to != null ? to : Fmt.today(), (a, b) -> {
                                from = a;
                                to = b;
                                range.setValue(Fmt.range(a, b));
                                refresh();
                            });
                }
            });
        }

        private void refresh() {
            String path = "orders_view?select=*&order=created_at.desc";
            if (from != null) {
                path += "&order_date=gte." + from + "&order_date=lte." + to;
            }
            load(path, rows -> {
                all = rows;
                render();
            });
        }

        private void render() {
            String q = search.value().toLowerCase(Locale.ROOT);
            table.clear();
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject o = all.optJSONObject(n);
                boolean match = q.isEmpty()
                        || s(o, "order_no").toLowerCase(Locale.ROOT).contains(q)
                        || s(o, "customer_name").toLowerCase(Locale.ROOT).contains(q);
                boolean statusOk = "All statuses".equals(status.value()) || status.value().equals(s(o, "status"));
                boolean paymentOk = "All payments".equals(payment.value()) || payment.value().equals(s(o, "payment_status"));
                if (match && statusOk && paymentOk) {
                    table.addRow(o, new String[]{
                            s(o, "order_no") + " / " + Fmt.shortDate(s(o, "order_date")),
                            s(o, "customer_name") + " / " + s(o, "customer_type"),
                            String.valueOf(i(o, "gallons")), s(o, "status"), s(o, "payment_status")
                    }, null);
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No orders found.");
            } else {
                table.selectFirst();
            }
        }
    }

    /** Figma "02 · Create Order". */
    public static class CreateOrder extends BaseScreen {
        private final List<JSONObject> customers = new ArrayList<>();
        private JSONObject customer;
        private Field customerField;
        private Field typeField;
        private Field gallons;
        private Field orderDate;
        private TextView lastOrder;
        private TextView addressValue;
        private final TextView[] quantityOut = new TextView[1];
        private final TextView[] priceOut = new TextView[1];
        private final TextView[] totalOut = new TextView[1];

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Create Order", "Record a customer request");

            LinearLayout info = Ui.titledCard(c, "Order information");
            FlowGrid form = FlowGrid.form(c);
            customerField = Field.dropdown(c, "Customer", "Select customer  ▾", this::chooseCustomer);
            typeField = Field.dropdown(c, "Customer type", "Household  ▾", () -> Dialogs.options(c, "Customer type",
                    new String[]{"Household · ₱35.00 / gallon", "Dealer · ₱30.00 / gallon", "Walk-in · ₱35.00 / gallon"},
                    pick -> {
                        selectedType = pick.substring(0, pick.indexOf(" ·"));
                        typeField.setValue(selectedType + "  ▾");
                        recalc();
                    }));
            gallons = Field.quantity(c, "Gallons ordered", null);
            gallons.setOnChange(this::recalc);
            orderDate = Field.date(c, "Order date", Fmt.today());
            form.addView(customerField);
            form.addView(typeField);
            form.addView(gallons);
            form.addView(orderDate);
            info.addView(Ui.spaced(c, form, 8));
            lastOrder = Ui.body(c, "");
            info.addView(Ui.spaced(c, lastOrder, 12));
            info.addView(Ui.spaced(c, WrapRow.of(c, Ui.secondary(c, "Add New Customer",
                    v -> go(new CustomerScreens.CustomerForm()))), 12));
            info.addView(Ui.spaced(c, Ui.text(c, "Delivery address", Ui.regular(c), 13, Ui.PRIMARY), 8));
            addressValue = Ui.text(c, "Select a customer", Ui.medium(c), 14, Ui.INK);
            addressValue.setBackground(Ui.box(c, Ui.ACTIVE, Ui.INPUT_BORDER, 10));
            addressValue.setGravity(android.view.Gravity.CENTER_VERTICAL);
            addressValue.setMinHeight(Ui.dp(c, 48));
            addressValue.setPadding(Ui.dp(c, 13), 0, Ui.dp(c, 13), 0);
            info.addView(addressValue, Ui.matchWrap());
            page.addView(info);

            LinearLayout summary = Ui.titledCard(c, "Order summary");
            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Quantity", "0 gallons", Ui.BODY, quantityOut));
            stats.addView(Ui.stat(c, "Unit price", Fmt.peso(35), Ui.BODY, priceOut));
            stats.addView(Ui.stat(c, "Total", Fmt.peso(0), Ui.BODY, totalOut));
            summary.addView(Ui.spaced(c, stats, 12));
            summary.addView(Ui.spaced(c, Ui.body(c,
                    "Initial order status: Ordered · Order ID and recording account are assigned when saved."), 12));
            summary.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Order", v -> submit())));
            page.addView(summary);

            getParentFragmentManager().setFragmentResultListener(CustomerScreens.RESULT_KEY, getViewLifecycleOwner(),
                    (key, bundle) -> pendingCustomerId = bundle.getString("id"));
            load("customers?select=*&order=name.asc", rows -> {
                customers.clear();
                for (int n = 0; n < rows.length(); n++) {
                    customers.add(rows.optJSONObject(n));
                }
                if (pendingCustomerId != null) {
                    for (JSONObject row : customers) {
                        if (pendingCustomerId.equals(s(row, "id"))) {
                            choose(row);
                        }
                    }
                    pendingCustomerId = null;
                } else if (customer != null) {
                    choose(customer);
                }
            });
        }

        private String selectedType = "Household";
        private String pendingCustomerId;

        private void chooseCustomer() {
            if (customers.isEmpty()) {
                Dialogs.correct(c, "No customers yet.", "Select Add New Customer to create the first one.");
                return;
            }
            CustomerPicker.show(c, customers, this::choose, () -> go(new CustomerScreens.CustomerForm()));
        }

        private void choose(JSONObject row) {
            customer = row;
            selectedType = s(row, "customer_type");
            customerField.setValue(s(row, "name") + " · " + selectedType + "  ▾");
            customerField.setError(null);
            typeField.setValue(selectedType + "  ▾");
            addressValue.setText(s(row, "address"));
            lastOrder.setText("");
            load("orders?select=original_gallons,order_date&customer_id=eq." + s(row, "id")
                    + "&order=created_at.desc&limit=1", rows -> {
                if (rows.length() > 0) {
                    JSONObject o = rows.optJSONObject(0);
                    lastOrder.setText("Most recent order: " + Fmt.gallons(i(o, "original_gallons"))
                            + " · " + Fmt.date(s(o, "order_date")));
                }
            });
            recalc();
        }

        private void recalc() {
            Integer qty = whole(gallons.value());
            int count = qty != null && qty > 0 ? qty : 0;
            double price = priceFor(selectedType);
            quantityOut[0].setText(Fmt.gallons(count));
            priceOut[0].setText(Fmt.peso(price));
            totalOut[0].setText(Fmt.peso(count * price));
        }

        private void submit() {
            if (customer == null) {
                customerField.setError("Select a customer.");
                return;
            }
            Integer qty = whole(gallons.value());
            if (qty == null || qty <= 0) {
                gallons.setError("Enter a valid gallon quantity.");
                return;
            }
            save("Saving order…", "Your order is being recorded. Please wait.", "create_order",
                    params("p_customer_id", s(customer, "id"), "p_customer_type", selectedType,
                            "p_gallons", qty, "p_order_date", orderDate.isoDate()),
                    row -> Dialogs.done(c, "Order saved.",
                            s(row, "order_no") + " · " + s(row, "customer_name") + "\n"
                                    + Fmt.gallons(i(row, "gallons")) + " × " + Fmt.peso(d(row, "unit_price"))
                                    + " = " + Fmt.peso(d(row, "total")) + "\nOrder status: " + s(row, "status"),
                            this::back));
        }
    }

    /** Figma "03 · Order Details". */
    public static class OrderDetails extends BaseScreen {
        private JSONObject order;
        private LinearLayout body;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Order Details", "Review quantities, payments and fulfillment");
            body = new LinearLayout(c);
            body.setOrientation(LinearLayout.VERTICAL);
            page.addView(body);
            reload();
        }

        private void reload() {
            loadOne("orders_view?select=*&id=eq." + argId(), row -> {
                order = row;
                load("payments?select=*&order_id=eq." + argId() + "&order=created_at.desc", payments ->
                        load("order_events?select=*&order_id=eq." + argId() + "&order=id.asc",
                                events -> render(payments, events)));
            });
        }

        private void render(JSONArray payments, JSONArray events) {
            body.removeAllViews();
            String status = s(order, "status");
            int pending = i(order, "pending_gallons");

            LinearLayout who = Ui.titledCard(c, "Customer and order");
            FlowGrid top = FlowGrid.stats(c, 3);
            top.addView(Ui.stat(c, "Customer", s(order, "customer_name")));
            top.addView(Ui.stat(c, "Type", s(order, "customer_type")));
            top.addView(Ui.stat(c, "Order total", Fmt.peso(d(order, "total"))));
            who.addView(Ui.spaced(c, top, 12));
            who.addView(Ui.spaced(c, Ui.ink(c, s(order, "order_no") + " · " + Fmt.stamp(s(order, "created_at"))
                    + " · Recorded by " + s(order, "recorded_by_name")), 8));
            who.addView(Ui.ink(c, Fmt.contact(s(order, "customer_contact")) + " · " + s(order, "delivery_address")));
            body.addView(who);

            LinearLayout tracking = Ui.titledCard(c, "Gallon Tracking");
            FlowGrid gallons = FlowGrid.stats(c, 4);
            gallons.addView(Ui.stat(c, "Ordered", String.valueOf(i(order, "original_gallons"))));
            gallons.addView(Ui.stat(c, "Picked up", String.valueOf(i(order, "collected_gallons"))));
            gallons.addView(Ui.stat(c, "Delivered", String.valueOf(i(order, "delivered_gallons")), Ui.SUCCESS, null));
            gallons.addView(Ui.stat(c, "Pending / variance", String.valueOf(pending)));
            tracking.addView(Ui.spaced(c, gallons, 12));

            Button statusButton = Ui.secondary(c, "Order status: " + status + "  ▾", v -> showStatusChoices());
            statusButton.setTextColor(Ui.statusColor(status));
            tracking.addView(Ui.spaced(c, WrapRow.of(c, statusButton), 12));
            tracking.addView(Ui.spaced(c, Ui.body(c, stageHint(status, pending)), 12));
            WrapRow stageActions = new WrapRow(c);
            if ("For Pickup".equals(status)) {
                stageActions.addView(Ui.primary(c, "Record Pickup",
                        v -> go(withId(new FulfillmentScreens.RecordPickup(), argId()))));
            }
            if ("Ready for Delivery".equals(status) || "Undelivered".equals(status)) {
                stageActions.addView(Ui.primary(c, "Record Delivery",
                        v -> go(withId(new FulfillmentScreens.RecordDelivery(), argId()))));
            }
            tracking.addView(stageActions);
            body.addView(tracking);

            LinearLayout pay = Ui.titledCard(c, "Payment");
            FlowGrid money = FlowGrid.stats(c, 4);
            money.addView(Ui.stat(c, "Unit price", Fmt.peso(d(order, "unit_price"))));
            money.addView(Ui.stat(c, "Total", Fmt.peso(d(order, "total"))));
            money.addView(Ui.stat(c, "Paid", Fmt.peso(d(order, "paid_amount")), Ui.SUCCESS, null));
            money.addView(Ui.stat(c, "Balance", Fmt.peso(d(order, "balance"))));
            pay.addView(Ui.spaced(c, money, 12));
            String paymentLine = s(order, "payment_status") + (payments.length() > 0
                    ? " · Reference " + s(payments.optJSONObject(0), "payment_no") + " · Cash"
                    : " · No payment recorded");
            pay.addView(Ui.spaced(c, Ui.body(c, paymentLine), 12));
            if (d(order, "balance") > 0) {
                pay.addView(WrapRow.of(c, Ui.primary(c, "Record Payment",
                        v -> go(PaymentScreens.RecordPayment.forOrder(argId())))));
            }
            body.addView(pay);

            LinearLayout timeline = Ui.titledCard(c, "Timeline and carry-over");
            Table t = new Table(c, new String[]{"Date / Time", "Status / Action", "Recorded by"},
                    new float[]{300, 354, 250});
            for (int n = 0; n < events.length(); n++) {
                JSONObject e = events.optJSONObject(n);
                t.addRow(null, new String[]{Fmt.event(s(e, "created_at")), s(e, "action"), s(e, "recorded_by_name")}, null);
            }
            timeline.addView(Ui.spaced(c, t, 12));
            if (i(order, "original_gallons") != i(order, "gallons")) {
                timeline.addView(Ui.spaced(c, Ui.body(c, "Original request: " + i(order, "original_gallons")
                        + " gallons · Shortfall: " + (i(order, "original_gallons") - i(order, "gallons")) + " gallons"), 8));
            }
            if ("Undelivered".equals(status)) {
                timeline.addView(Ui.body(c, "Original date: " + Fmt.date(s(order, "order_date"))
                        + " · Days outstanding: " + i(order, "days_waiting") + " · Pending gallons: " + pending));
            }
            body.addView(timeline);
        }

        private String stageHint(String status, int pending) {
            switch (status) {
                case "Undelivered":
                    return "The remaining " + pending + " gallons must be delivered before this order can be marked Delivered.";
                case "Ready for Delivery":
                    return "Ready for delivery. Record the actual delivered quantity.";
                case "Delivered":
                    return "All gallons have been delivered.";
                default:
                    return "Follow the eligible next order stage.";
            }
        }

        /** Figma "02 · Order status dropdown": current stage filled, next eligible stage outlined. */
        private void showStatusChoices() {
            String status = s(order, "status");
            String[] choices = new String[STATUSES.length + 1];
            for (int n = 0; n < STATUSES.length; n++) {
                choices[n] = STATUSES[n] + (STATUSES[n].equals(status) ? "  (current)" : "");
            }
            choices[STATUSES.length] = "Back to order";
            Dialogs.options(c, "Update order status", choices, pick -> {
                if (!"Back to order".equals(pick)) {
                    applyStatus(pick.replace("  (current)", ""));
                }
            });
        }

        private void applyStatus(String next) {
            String status = s(order, "status");
            if (next.equals(status)) {
                return;
            }
            boolean toPickupForm = "For Pickup".equals(status) && "Picked Up".equals(next);
            boolean toReschedule = "Rescheduled".equals(next)
                    && ("For Pickup".equals(status) || "Ordered".equals(status) || "Rescheduled".equals(status));
            boolean toDeliveryForm = ("Ready for Delivery".equals(status) || "Undelivered".equals(status))
                    && ("Delivered".equals(next) || "Undelivered".equals(next));
            if (toPickupForm) {
                go(withId(new FulfillmentScreens.RecordPickup(), argId()));
            } else if (toReschedule) {
                go(withId(new FulfillmentScreens.Reschedule(), argId()));
            } else if (toDeliveryForm) {
                go(withId(new FulfillmentScreens.RecordDelivery(), argId()));
            } else {
                save("Updating status…", "Saving the next order stage.", "set_order_status",
                        params("p_order_id", argId(), "p_status", next), result -> {
                            if (result.has("empty_after")) {
                                Dialogs.done(c, "Ready for Delivery",
                                        s(result, "order_no") + " · " + Fmt.gallons(i(result, "gallons")) + "\n"
                                                + "Empty: " + i(result, "empty_before") + " → " + i(result, "empty_after")
                                                + " · Physical filled: " + i(result, "filled_before") + " → " + i(result, "filled_after") + "\n"
                                                + "Reserved: " + i(result, "reserved_before") + " → " + i(result, "reserved_after")
                                                + " · Available stays " + i(result, "available") + "\n"
                                                + "Converted and reserved once.", this::reload);
                            } else {
                                reload();
                            }
                        });
            }
        }
    }
}
