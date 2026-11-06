package com.example.aquaquencherspos;

import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Walk-in sales and the payment ledger (A-07, A-08). */
public final class PaymentScreens {

    private static final double WALK_IN_PRICE = 35.0;

    private PaymentScreens() {
    }

    /** Figma "02 · POS Walk-in". */
    public static class WalkInSale extends BaseScreen {
        private Field name;
        private Field gallons;
        private final TextView[] quantityOut = new TextView[1];
        private final TextView[] totalOut = new TextView[1];

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            head("Walk-in Sale", "Record a counter sale");

            LinearLayout info = Ui.titledCard(c, "Order information");
            FlowGrid form = FlowGrid.form(c);
            name = Field.text(c, "Customer", "Customer name");
            form.addView(name);
            form.addView(Field.readOnly(c, "Customer type", "Walk-in"));
            gallons = Field.quantity(c, "Gallons ordered", null);
            gallons.setOnChange(this::recalc);
            form.addView(gallons);
            form.addView(Field.readOnly(c, "Order date", Fmt.date(Fmt.today())));
            info.addView(Ui.spaced(c, form, 12));
            info.addView(Ui.spaced(c, WrapRow.of(c, Ui.secondary(c, "Add New Customer",
                    v -> go(new CustomerScreens.CustomerForm()))), 12));
            info.addView(Ui.spaced(c, Ui.text(c, "Counter sale", Ui.regular(c), 13, Ui.PRIMARY), 8));
            TextView noAddress = Ui.text(c, "No delivery address required", Ui.medium(c), 14, Ui.INK);
            noAddress.setBackground(Ui.box(c, Ui.ACTIVE, Ui.INPUT_BORDER, 10));
            noAddress.setGravity(android.view.Gravity.CENTER_VERTICAL);
            noAddress.setMinHeight(Ui.dp(c, 48));
            noAddress.setPadding(Ui.dp(c, 13), 0, Ui.dp(c, 13), 0);
            info.addView(Ui.spaced(c, noAddress, 12));
            info.addView(Ui.text(c, "A saved profile is optional. The customer name is required.",
                    Ui.regular(c), 13, Ui.PRIMARY));
            page.addView(info);

            LinearLayout summary = Ui.titledCard(c, "Order summary");
            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Quantity", "0 gallons", Ui.BODY, quantityOut));
            stats.addView(Ui.stat(c, "Unit price", Fmt.peso(WALK_IN_PRICE)));
            stats.addView(Ui.stat(c, "Total", Fmt.peso(0), Ui.BODY, totalOut));
            summary.addView(Ui.spaced(c, stats, 12));
            summary.addView(Ui.spaced(c, Ui.body(c, "Counter sale · Stock updates after saving payment"), 12));
            summary.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Continue to payment", v -> next())));
            page.addView(summary);
        }

        private void recalc() {
            Integer qty = whole(gallons.value());
            int count = qty != null && qty > 0 ? qty : 0;
            quantityOut[0].setText(Fmt.gallons(count));
            totalOut[0].setText(Fmt.peso(count * WALK_IN_PRICE));
        }

        private void next() {
            if (name.value().isEmpty()) {
                name.setError("Enter the customer name.");
                return;
            }
            Integer qty = whole(gallons.value());
            if (qty == null || qty <= 0) {
                gallons.setError("Enter a valid gallon quantity.");
                return;
            }
            Bundle b = new Bundle();
            b.putString("name", name.value());
            b.putInt("gallons", qty);
            WalkInPayment screen = new WalkInPayment();
            screen.setArguments(b);
            go(screen);
        }
    }

    /** Figma "03 · Walk-in Payment" and "04 · Credit". */
    public static class WalkInPayment extends BaseScreen {
        private String customerName;
        private int count;
        private int available;
        private double total;
        private Field cash;
        private LinearLayout statsHolder;
        private TextView note;
        private Button complete;

        @Override
        String section() {
            return "Orders";
        }

        @Override
        void build() {
            customerName = requireArguments().getString("name");
            count = requireArguments().getInt("gallons");
            total = count * WALK_IN_PRICE;
            head("Walk-in payment", "Record a counter sale and its payment or credit balance");

            LinearLayout sale = Ui.titledCard(c, "Counter sale");
            sale.addView(Ui.spaced(c, Ui.ink(c, "Customer type: Walk-in · A saved customer profile is not required."), 12));
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Customer name", customerName));
            form.addView(Field.readOnly(c, "Product", "Purified water"));
            form.addView(Field.readOnly(c, "Unit price", Fmt.peso(WALK_IN_PRICE)));
            Field stockField = Field.readOnly(c, "Available filled stock", "…");
            form.addView(stockField);
            form.addView(Field.readOnly(c, "Gallons purchased", String.valueOf(count)));
            cash = Field.money(c, "Cash tendered", "₱0.00");
            cash.setOnChange(this::recalc);
            form.addView(cash);
            form.addView(Field.readOnly(c, "Recorded by", recordedBy()));
            sale.addView(form);
            page.addView(sale);

            LinearLayout summary = Ui.titledCard(c, "Sale summary");
            statsHolder = new LinearLayout(c);
            statsHolder.setOrientation(LinearLayout.VERTICAL);
            summary.addView(Ui.spaced(c, statsHolder, 12));
            note = Ui.body(c, "");
            summary.addView(Ui.spaced(c, note, 12));
            complete = Ui.primary(c, "Complete Sale", v -> submit());
            summary.addView(WrapRow.of(c, Ui.secondary(c, "Cancel", v -> back()), complete));
            page.addView(summary);

            call("inventory_status", new JSONObject(), inv -> {
                available = i(inv, "filled_available");
                stockField.setValue(Fmt.gallons(available));
                recalc();
            });
            recalc();
        }

        private void recalc() {
            BigDecimal tendered = money(cash.value());
            double given = tendered != null && tendered.signum() >= 0 ? tendered.doubleValue() : 0;
            boolean credit = given < total;
            statsHolder.removeAllViews();
            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Total", Fmt.peso(total)));
            stats.addView(credit ? Ui.stat(c, "Remaining credit", Fmt.peso(total - given))
                    : Ui.stat(c, "Change", Fmt.peso(given - total)));
            stats.addView(Ui.stat(c, "Stock after saving", Fmt.gallons(Math.max(available - count, 0))));
            statsHolder.addView(stats);
            note.setText(credit ? "Cash below total. Confirm " + Fmt.peso(total - given) + " as credit before saving."
                    : "Record the customer name. Any unpaid remainder is recorded as credit.");
            complete.setText(credit ? "Confirm credit" : "Complete Sale");
        }

        private void submit() {
            String raw = cash.value();
            BigDecimal tendered = raw.isEmpty() ? BigDecimal.ZERO : money(raw);
            if (tendered == null || tendered.signum() < 0) {
                cash.setError("Enter a valid cash amount.");
                return;
            }
            if (count > available) {
                Dialogs.correct(c, "Not enough filled gallons.", customerName + " · Attempted purchase " + count
                        + " gallons\nAvailable filled stock: " + available
                        + " gallons\nNo sale or stock deduction was saved.");
                return;
            }
            double given = tendered.doubleValue();
            if (given < total) {
                Dialogs.confirm(c, "Confirm credit balance?", customerName + " · Walk-in\nSale total: " + Fmt.peso(total)
                                + "\nCash received: " + Fmt.peso(given) + "\nUnpaid balance: " + Fmt.peso(total - given)
                                + "\nConfirm to record " + Fmt.peso(total - given) + " as credit.",
                        "Confirm and save", () -> send(tendered, true));
            } else {
                send(tendered, false);
            }
        }

        private void send(BigDecimal tendered, boolean credit) {
            save("Completing sale…", "Recording the walk-in sale and received cash.", "complete_walkin_sale",
                    params("p_customer_name", customerName, "p_customer_id", null, "p_gallons", count,
                            "p_cash", tendered.doubleValue(), "p_confirm_credit", credit),
                    r -> {
                        boolean owes = d(r, "credit") > 0;
                        Dialogs.done(c, "Walk-in sale recorded",
                                s(r, "sale_no") + " · " + s(r, "customer_name") + " · Walk-in sale\n"
                                        + Fmt.gallons(i(r, "gallons")) + " × " + Fmt.peso(d(r, "unit_price"))
                                        + " = " + Fmt.peso(d(r, "total")) + "\n"
                                        + (owes ? "Cash received: " + Fmt.peso(d(r, "cash")) + " · Credit: " + Fmt.peso(d(r, "credit"))
                                        : "Cash: " + Fmt.peso(d(r, "cash")) + " · Change: " + Fmt.peso(d(r, "change"))) + "\n"
                                        + "Payment status: " + s(r, "payment_status") + "\n"
                                        + "Filled stock after sale: " + Fmt.gallons(i(r, "filled_after")),
                                () -> shell().openRoot(new OrderScreens.Orders()));
                    });
        }
    }

    /** Figma "05 · Payments". */
    public static class Payments extends BaseScreen {
        private JSONArray all = new JSONArray();
        private LocalDate from = Fmt.monthStart();
        private LocalDate to = Fmt.today();
        private String customer = "All customers";
        private Field customerField;
        private Field rangeField;
        private Field statusField;
        private Field search;
        private LinearLayout statsHolder;
        private Table table;

        @Override
        String section() {
            return "Payments";
        }

        @Override
        void build() {
            head("Payments", "Amounts received against recorded orders");
            LinearLayout card = Ui.titledCard(c, "Payment ledger");
            FlowGrid form = FlowGrid.form(c);
            customerField = Field.dropdown(c, "Customer", customer, this::chooseCustomer);
            rangeField = Field.dropdown(c, "Date range", Fmt.range(from, to), () -> Dialogs.period(c, "Select payment dates",
                    "Payments are filtered by the date the cash was received.", from, to, (a, b) -> {
                        from = a;
                        to = b;
                        rangeField.setValue(Fmt.range(a, b));
                        refresh();
                    }));
            statusField = Field.dropdown(c, "Payment status", "All statuses", () -> Dialogs.options(c, "Payment status",
                    new String[]{"All statuses", "Paid", "Partially Paid", "Unpaid"}, pick -> {
                        statusField.setValue(pick);
                        render();
                    }));
            search = Field.search(c, "Search", "Payment, Order or Walk-in reference");
            search.setOnChange(this::render);
            form.addView(customerField);
            form.addView(rangeField);
            form.addView(statusField);
            form.addView(search);
            card.addView(Ui.spaced(c, form, 16));
            statsHolder = new LinearLayout(c);
            statsHolder.setOrientation(LinearLayout.VERTICAL);
            card.addView(Ui.spaced(c, statsHolder, 16));
            table = new Table(c, new String[]{"Reference / Date", "Customer / Order", "Amount", "Recorded by"},
                    new float[]{253, 301, 156, 194});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(WrapRow.of(c,
                    Ui.primary(c, "Record Payment", v -> choosePayable(true)),
                    Ui.primary(c, "Walk-in credit", v -> choosePayable(false))));
            page.addView(card);
            refresh();
        }

        private void refresh() {
            load("payments_view?select=*&payment_date=gte." + from + "&payment_date=lte." + to
                    + "&order=created_at.desc", rows -> {
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
            String q = search.value().toLowerCase(Locale.ROOT);
            table.clear();
            double sum = 0;
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject p = all.optJSONObject(n);
                boolean match = q.isEmpty()
                        || s(p, "payment_no").toLowerCase(Locale.ROOT).contains(q)
                        || s(p, "source_no").toLowerCase(Locale.ROOT).contains(q);
                boolean customerOk = "All customers".equals(customer) || customer.equals(s(p, "customer_name"));
                boolean statusOk = "All statuses".equals(statusField.value())
                        || statusField.value().equals(s(p, "source_payment_status"));
                if (match && customerOk && statusOk) {
                    table.addRow(null, new String[]{
                            s(p, "payment_no") + " / " + Fmt.shortDate(s(p, "payment_date")),
                            s(p, "customer_name") + " / " + s(p, "source_no"),
                            Fmt.peso(d(p, "amount")), s(p, "recorded_by_name")
                    }, null);
                    sum += d(p, "amount");
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No payments found for this period.");
            }
            statsHolder.removeAllViews();
            FlowGrid stats = FlowGrid.stats(c, 2);
            stats.addView(Ui.stat(c, "Payments received", Fmt.peso(sum)));
            stats.addView(Ui.stat(c, "Payment count", String.valueOf(shown)));
            statsHolder.addView(stats);
        }

        /** Lists unpaid orders or named Walk-in credit by reference (A-08.1). */
        private void choosePayable(boolean orders) {
            if (orders) {
                load("orders_view?select=id,order_no,customer_name,balance&balance=gt.0&order=created_at.asc", rows ->
                        offer(rows, "order_no", true, "Select an unpaid order"));
            } else {
                load("walkin_sales?select=id,sale_no,customer_name,total,paid_amount&payment_status=neq.Paid&order=created_at.asc",
                        rows -> offer(rows, "sale_no", false, "Select a Walk-in credit"));
            }
        }

        private void offer(JSONArray rows, String refKey, boolean orders, String title) {
            if (rows.length() == 0) {
                Dialogs.correct(c, "Nothing to collect.", orders ? "There are no unpaid orders."
                        : "There are no unpaid Walk-in sales.");
                return;
            }
            List<String> choices = new ArrayList<>();
            for (int n = 0; n < rows.length(); n++) {
                JSONObject r = rows.optJSONObject(n);
                double balance = orders ? d(r, "balance") : d(r, "total") - d(r, "paid_amount");
                choices.add(s(r, refKey) + " · " + s(r, "customer_name") + " · " + Fmt.peso(balance));
            }
            Dialogs.options(c, title, choices.toArray(new String[0]), pick -> {
                JSONObject chosen = rows.optJSONObject(choices.indexOf(pick));
                go(orders ? RecordPayment.forOrder(s(chosen, "id")) : RecordPayment.forSale(s(chosen, "id")));
            });
        }
    }

    /** Figma "06 · Record Payment". */
    public static class RecordPayment extends BaseScreen {
        private double balance;
        private Field amount;
        private TextView note;
        private final TextView[] paymentOut = new TextView[1];
        private final TextView[] afterOut = new TextView[1];

        static RecordPayment forOrder(String id) {
            RecordPayment screen = new RecordPayment();
            Bundle b = new Bundle();
            b.putString("order", id);
            screen.setArguments(b);
            return screen;
        }

        static RecordPayment forSale(String id) {
            RecordPayment screen = new RecordPayment();
            Bundle b = new Bundle();
            b.putString("sale", id);
            screen.setArguments(b);
            return screen;
        }

        @Override
        String section() {
            return "Payments";
        }

        @Override
        void build() {
            String orderId = requireArguments().getString("order");
            String saleId = requireArguments().getString("sale");
            if (orderId != null) {
                loadOne("orders_view?select=*&id=eq." + orderId, o ->
                        render(s(o, "customer_name"), "Order ID", s(o, "order_no"), d(o, "total"), d(o, "paid_amount"),
                                "Record payment against an order"));
            } else {
                loadOne("walkin_sales?select=*&id=eq." + saleId, o ->
                        render(s(o, "customer_name"), "Walk-in reference", s(o, "sale_no"), d(o, "total"), d(o, "paid_amount"),
                                "Record payment against a Walk-in credit"));
            }
        }

        private void render(String customer, String refLabel, String ref, double total, double paid, String title) {
            balance = total - paid;
            head("Record Payment", "Total " + Fmt.peso(total) + " · Already paid " + Fmt.peso(paid));
            LinearLayout card = Ui.titledCard(c, title);
            FlowGrid form = FlowGrid.form(c);
            form.addView(Field.readOnly(c, "Customer", customer));
            form.addView(Field.readOnly(c, refLabel, ref));
            form.addView(Field.readOnly(c, "Outstanding amount", Fmt.peso(balance)));
            amount = Field.money(c, "Payment amount", "₱0.00");
            amount.setOnChange(this::recalc);
            form.addView(amount);
            // Cash collection cannot be backdated (A-08.7), so this is not editable.
            form.addView(Field.readOnly(c, "Payment date", Fmt.date(Fmt.today())));
            form.addView(Field.readOnly(c, "Payment method", "Cash"));
            form.addView(Field.readOnly(c, "Reference", "Generated when saved"));
            form.addView(Field.readOnly(c, "Recorded by", recordedBy()));
            card.addView(Ui.spaced(c, form, 16));

            FlowGrid stats = FlowGrid.stats(c, 3);
            stats.addView(Ui.stat(c, "Balance before", Fmt.peso(balance)));
            stats.addView(Ui.stat(c, "Payment", Fmt.peso(0), Ui.BODY, paymentOut));
            stats.addView(Ui.stat(c, "Balance after", Fmt.peso(balance), Ui.BODY, afterOut));
            card.addView(Ui.spaced(c, stats, 12));
            note = Ui.body(c, "");
            card.addView(Ui.spaced(c, note, 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Payment", v -> submit())));
            page.addView(card);
            recalc();
        }

        private void recalc() {
            BigDecimal value = money(amount.value());
            double pay = value != null && value.signum() > 0 && value.doubleValue() <= balance ? value.doubleValue() : 0;
            paymentOut[0].setText(Fmt.peso(pay));
            afterOut[0].setText(Fmt.peso(balance - pay));
            String after = pay >= balance ? "Paid" : pay > 0 ? "Partially Paid" : "No change";
            note.setText("Status after saving: " + after + ". Payment cannot exceed the outstanding amount.");
        }

        private void submit() {
            BigDecimal value = money(amount.value());
            if (value == null || value.signum() <= 0 || value.doubleValue() > balance) {
                amount.setError("Enter an amount greater than zero and not above the balance.");
                return;
            }
            save("Recording payment…", "Updating the payment and outstanding balance.", "record_payment",
                    params("p_order_id", requireArguments().getString("order"),
                            "p_sale_id", requireArguments().getString("sale"),
                            "p_amount", value.doubleValue()),
                    r -> Dialogs.done(c, "Payment recorded.",
                            s(r, "payment_no") + " · " + s(r, "source_no") + "\n"
                                    + "Amount received: " + Fmt.peso(d(r, "amount")) + "\n"
                                    + "Payment status: " + s(r, "payment_status") + "\n"
                                    + "Remaining balance: " + Fmt.peso(d(r, "balance")),
                            this::back));
        }
    }
}
