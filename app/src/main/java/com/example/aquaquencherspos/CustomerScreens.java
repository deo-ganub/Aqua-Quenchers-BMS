package com.example.aquaquencherspos;

import android.os.Bundle;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Customer records (A-03). */
public final class CustomerScreens {

    static final String[] TYPES = {"Household", "Dealer", "Walk-in"};
    /** Fragment result sent back to Create Order after adding a customer. */
    static final String RESULT_KEY = "customer_saved";

    private CustomerScreens() {
    }

    /** Figma "01 · Customers". */
    public static class Customers extends BaseScreen {
        private JSONArray all = new JSONArray();
        private Field search;
        private Field type;
        private Table table;

        @Override
        String section() {
            return "Customers";
        }

        @Override
        void build() {
            head("Customers", "Add, search and update customer records");
            add(WrapRow.of(c, Ui.primary(c, "Add Customer", v -> go(new CustomerForm()))));

            LinearLayout card = Ui.titledCard(c, "Customer records");
            FlowGrid filters = FlowGrid.form(c);
            search = Field.search(c, "Search", "Name or 11-digit contact number");
            search.setOnChange(this::render);
            type = Field.dropdown(c, "Customer type", "All", () -> Dialogs.options(c, "Customer type",
                    new String[]{"All", "Household", "Dealer", "Walk-in"}, pick -> {
                        type.setValue(pick);
                        render();
                    }));
            filters.addView(search);
            filters.addView(type);
            card.addView(Ui.spaced(c, filters, 16));

            table = new Table(c, new String[]{"Customer / Contact", "Type", "Balance", "Status"},
                    new float[]{338, 192, 192, 182});
            card.addView(Ui.spaced(c, table, 12));
            card.addView(WrapRow.of(c, Ui.secondary(c, "Open Customer Profile", v -> {
                if (table.selected() == null) {
                    Dialogs.correct(c, "Select a customer.", "Tap a customer in the list first.");
                } else {
                    go(withId(new CustomerProfile(), s(table.selected(), "id")));
                }
            })));
            page.addView(card);

            load("customer_balances?select=*&order=name.asc", rows -> {
                all = rows;
                render();
            });
        }

        private void render() {
            String q = search.value().toLowerCase(Locale.ROOT);
            String digits = q.replaceAll("[^0-9]", "");
            table.clear();
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject row = all.optJSONObject(n);
                boolean match = q.isEmpty()
                        || s(row, "name").toLowerCase(Locale.ROOT).contains(q)
                        || (!digits.isEmpty() && s(row, "contact_number").contains(digits));
                boolean typeOk = "All".equals(type.value()) || type.value().equals(s(row, "customer_type"));
                if (match && typeOk) {
                    table.addRow(row, new String[]{
                            s(row, "name") + " / " + Fmt.contact(s(row, "contact_number")),
                            s(row, "customer_type"), Fmt.peso(d(row, "balance")), s(row, "status")
                    }, new int[]{0, 0, 0, Ui.statusColor(s(row, "status"))});
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No customers found.");
            } else {
                table.selectFirst();
            }
        }
    }

    /** Figma "03 · Add Customer" and "04 · Edit Customer". */
    public static class CustomerForm extends BaseScreen {
        private Field name;
        private Field type;
        private Field contact;
        private Field address;

        @Override
        String section() {
            return "Customers";
        }

        @Override
        void build() {
            boolean editing = argId() != null;
            head(editing ? "Edit Customer" : "Add Customer",
                    editing ? "Update a customer profile" : "Register a customer profile");

            LinearLayout card = Ui.titledCard(c, editing ? "Customer details" : "New customer");
            FlowGrid form = FlowGrid.form(c);
            name = Field.text(c, "Full name", null);
            type = Field.dropdown(c, "Customer type", "", () -> Dialogs.options(c, "Customer type", TYPES, pick -> {
                type.setValue(pick);
                type.setError(null);
            }));
            contact = Field.phone(c, "Contact number", "0917 555 0125");
            address = Field.text(c, "Address", null);
            form.addView(name);
            form.addView(type);
            form.addView(contact);
            form.addView(address);
            card.addView(Ui.spaced(c, form, 4));
            card.addView(Ui.spaced(c, Ui.body(c, "Enter exactly 11 digits."), 12));
            card.addView(Ui.spaced(c, Ui.body(c, "Use a unique 11-digit contact number when adding a customer. "
                    + "When editing, the customer can keep their current number."), 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Customer", v -> submit())));
            page.addView(card);

            if (editing) {
                loadOne("customers?select=*&id=eq." + argId(), row -> {
                    name.setValue(s(row, "name"));
                    type.setValue(s(row, "customer_type"));
                    contact.setValue(Fmt.contact(s(row, "contact_number")));
                    address.setValue(s(row, "address"));
                });
            }
        }

        private void submit() {
            String digits = contact.value().replaceAll("[^0-9]", "");
            if (name.value().isEmpty() || type.value().isEmpty()
                    || contact.value().isEmpty() || address.value().isEmpty()) {
                Dialogs.correct(c, "Complete the required fields.",
                        "Enter Full name, Customer type, Contact number and Address. No customer record was saved.");
                return;
            }
            if (digits.length() != 11) {
                contact.setError("Please enter a valid contact number. Use 11 digits.");
                return;
            }
            save("Saving customer…", "Please wait while the customer record is saved.", "save_customer",
                    params("p_id", argId(), "p_name", name.value(), "p_type", type.value(),
                            "p_contact", digits, "p_address", address.value()),
                    row -> Dialogs.done(c, "Customer saved.",
                            s(row, "name") + " · " + s(row, "customer_type") + "\n"
                                    + Fmt.contact(s(row, "contact_number")) + "\n" + s(row, "address"),
                            () -> {
                                Bundle result = new Bundle();
                                result.putString("id", s(row, "id"));
                                getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
                                back();
                            }));
        }
    }

    /** Figma "02 · Customer Profile": details, orders, unpaid balance and payments. */
    public static class CustomerProfile extends BaseScreen {
        private final List<JSONObject> unpaid = new ArrayList<>();
        private LinearLayout infoBody;
        private Table orders;
        private Table payments;
        private LinearLayout paymentStats;
        private JSONObject selectedOrder;

        @Override
        String section() {
            return "Customers";
        }

        @Override
        void build() {
            head("Customer Profile", "Order history, payments and outstanding balance");

            LinearLayout info = Ui.titledCard(c, "Customer information");
            infoBody = new LinearLayout(c);
            infoBody.setOrientation(LinearLayout.VERTICAL);
            info.addView(Ui.spaced(c, infoBody, 12));
            info.addView(WrapRow.of(c, Ui.secondary(c, "Edit Customer",
                    v -> go(withId(new CustomerForm(), argId())))));
            page.addView(info);

            LinearLayout history = Ui.titledCard(c, "Order history");
            orders = new Table(c, new String[]{"Order / Date", "Gallons", "Total", "Order status", "Payment status"},
                    new float[]{261, 105, 151, 214, 173});
            history.addView(Ui.spaced(c, orders, 12));
            history.addView(WrapRow.of(c, Ui.secondary(c, "View Order", v -> {
                if (orders.selected() == null) {
                    Dialogs.correct(c, "Select an order.", "Tap an order in the list first.");
                } else {
                    go(withId(new OrderScreens.OrderDetails(), s(orders.selected(), "id")));
                }
            })));
            page.addView(history);

            LinearLayout pay = Ui.titledCard(c, "Payment history");
            paymentStats = new LinearLayout(c);
            paymentStats.setOrientation(LinearLayout.VERTICAL);
            pay.addView(Ui.spaced(c, paymentStats, 12));
            payments = new Table(c, new String[]{"Date / Reference", "Order ID", "Amount", "Recorded by"},
                    new float[]{300, 220, 170, 214});
            pay.addView(Ui.spaced(c, payments, 12));
            pay.addView(WrapRow.of(c, Ui.primary(c, "Record Payment", v -> recordPayment())));
            page.addView(pay);

            String id = argId();
            loadOne("customer_balances?select=*&id=eq." + id, this::showInfo);
            load("orders_view?select=*&customer_id=eq." + id + "&order=created_at.desc", this::showOrders);
            load("payments?select=*&customer_id=eq." + id + "&order=created_at.desc", this::showPayments);
        }

        private JSONObject customer;

        private void showInfo(JSONObject row) {
            customer = row;
            infoBody.removeAllViews();
            Table t = new Table(c, new String[]{"Field", "Value"}, new float[]{457, 447});
            t.addRow(null, new String[]{"Name / Type", s(row, "name") + " / " + s(row, "customer_type")}, null);
            t.addRow(null, new String[]{"Contact", Fmt.contact(s(row, "contact_number"))}, null);
            t.addRow(null, new String[]{"Address", s(row, "address")}, null);
            infoBody.addView(t);
            renderStats();
        }

        private void showOrders(JSONArray rows) {
            orders.clear();
            unpaid.clear();
            if (rows.length() == 0) {
                orders.showEmpty("No orders yet.");
                return;
            }
            for (int n = 0; n < rows.length(); n++) {
                JSONObject o = rows.optJSONObject(n);
                if (d(o, "balance") > 0) {
                    unpaid.add(o);
                }
                orders.addRow(o, new String[]{
                        s(o, "order_no") + " / " + Fmt.shortDate(s(o, "order_date")),
                        String.valueOf(i(o, "gallons")), Fmt.peso(d(o, "total")),
                        s(o, "status"), s(o, "payment_status")
                }, new int[]{0, 0, 0, Ui.statusColor(s(o, "status")), Ui.statusColor(s(o, "payment_status"))});
            }
            orders.selectFirst();
        }

        private double collected;

        private void showPayments(JSONArray rows) {
            payments.clear();
            collected = 0;
            if (rows.length() == 0) {
                payments.showEmpty("No payments recorded.");
            }
            for (int n = 0; n < rows.length(); n++) {
                JSONObject p = rows.optJSONObject(n);
                collected += d(p, "amount");
                payments.addRow(null, new String[]{
                        Fmt.shortDate(s(p, "payment_date")) + " / " + s(p, "payment_no"),
                        s(p, "source_no"), Fmt.peso(d(p, "amount")), s(p, "recorded_by_name")
                }, null);
            }
            renderStats();
        }

        private void renderStats() {
            paymentStats.removeAllViews();
            FlowGrid grid = FlowGrid.stats(c, 2);
            grid.addView(Ui.stat(c, "Payments collected", Fmt.peso(collected)));
            grid.addView(Ui.stat(c, "Remaining balance", Fmt.peso(d(customer, "balance"))));
            paymentStats.addView(grid);
        }

        private void recordPayment() {
            if (unpaid.isEmpty()) {
                Dialogs.correct(c, "No unpaid orders.", "This customer has no unpaid order to record a payment against.");
                return;
            }
            String[] choices = new String[unpaid.size()];
            for (int n = 0; n < choices.length; n++) {
                JSONObject o = unpaid.get(n);
                choices[n] = s(o, "order_no") + " · " + Fmt.peso(d(o, "balance"));
            }
            Dialogs.options(c, "Select an unpaid order", choices, pick -> {
                for (int n = 0; n < choices.length; n++) {
                    if (choices[n].equals(pick)) {
                        selectedOrder = unpaid.get(n);
                        go(PaymentScreens.RecordPayment.forOrder(s(selectedOrder, "id")));
                    }
                }
            });
        }
    }
}
