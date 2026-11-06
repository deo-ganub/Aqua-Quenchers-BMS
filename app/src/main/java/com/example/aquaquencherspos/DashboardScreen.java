package com.example.aquaquencherspos;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;

/** Figma "02 · Dashboard" (A-02). */
public class DashboardScreen extends BaseScreen {

    private LocalDate start = Fmt.monthStart();
    private LocalDate end = Fmt.today();
    private LinearLayout financeBody;
    private LinearLayout inventoryBody;
    private TextView financeTitle;
    private TextView inventoryTitle;
    private TextView periodChip;
    private Table recent;

    @Override
    String section() {
        return "Dashboard";
    }

    @Override
    void build() {
        head("Owner Dashboard", "Business and inventory overview");

        LinearLayout finance = Ui.card(c);
        LinearLayout financeHead = new LinearLayout(c);
        financeHead.setOrientation(LinearLayout.HORIZONTAL);
        financeHead.setGravity(Gravity.CENTER_VERTICAL);
        financeTitle = Ui.cardTitle(c, "");
        periodChip = Ui.text(c, "", Ui.medium(c), 13, Ui.PRIMARY);
        periodChip.setBackground(Ui.box(c, Ui.ACTIVE, Ui.ACTIVE_BORDER, 10));
        periodChip.setGravity(Gravity.CENTER_VERTICAL);
        periodChip.setMinHeight(Ui.dp(c, 36));
        periodChip.setPadding(Ui.dp(c, 13), 0, Ui.dp(c, 13), 0);
        periodChip.setOnClickListener(v -> Dialogs.period(c, "Select dashboard period",
                "Financial figures use the chosen period. Balances are shown as of the end date.",
                start, end, (from, to) -> {
                    start = from;
                    end = to;
                    refresh();
                }));
        financeHead.addView(financeTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        financeHead.addView(periodChip);
        finance.addView(Ui.spaced(c, financeHead, 12));
        financeBody = new LinearLayout(c);
        financeBody.setOrientation(LinearLayout.VERTICAL);
        finance.addView(financeBody);
        page.addView(finance);

        LinearLayout inventory = Ui.card(c);
        inventoryTitle = Ui.cardTitle(c, "Inventory");
        inventory.addView(Ui.spaced(c, inventoryTitle, 12));
        inventoryBody = new LinearLayout(c);
        inventoryBody.setOrientation(LinearLayout.VERTICAL);
        inventory.addView(inventoryBody);
        inventory.addView(WrapRow.of(c,
                Ui.secondary(c, "Inventory", v -> go(new SettingsScreens.Inventory())),
                Ui.secondary(c, "Customer balances", v -> shell().openRoot(new CustomerScreens.Customers()))));
        page.addView(inventory);

        LinearLayout actions = Ui.titledCard(c, "Quick actions");
        actions.addView(WrapRow.of(c,
                Ui.primary(c, "Create Order", v -> go(new OrderScreens.CreateOrder())),
                Ui.secondary(c, "Walk-in Sale", v -> go(new PaymentScreens.WalkInSale())),
                Ui.secondary(c, "Reports", v -> shell().openRoot(new ReportScreens.Reports()))));
        page.addView(actions);

        LinearLayout orders = Ui.titledCard(c, "Recent orders");
        recent = new Table(c, new String[]{"Order", "Customer", "Status"}, new float[]{304, 305, 295});
        orders.addView(Ui.spaced(c, recent, 12));
        orders.addView(WrapRow.of(c,
                Ui.secondary(c, "View Orders", v -> shell().openRoot(new OrderScreens.Orders()))));
        page.addView(orders);

        refresh();
    }

    private void refresh() {
        String range = Fmt.range(start, end);
        financeTitle.setText("Financial overview · " + range);
        periodChip.setText(range + "  ▾");

        call("financial_summary", params("p_start", start.toString(), "p_end", end.toString()), this::showFinance);
        call("inventory_status", new JSONObject(), this::showInventory);
        load("orders_view?select=order_no,customer_name,status&order=created_at.desc&limit=5", this::showRecent);
    }

    private void showFinance(JSONObject f) {
        receivables = d(f, "outstanding_balances");
        financeBody.removeAllViews();
        FlowGrid grid = FlowGrid.stats(c, 4);
        grid.addView(Ui.stat(c, "Revenue", Fmt.peso(d(f, "revenue"))));
        grid.addView(Ui.stat(c, "Payments received", Fmt.peso(d(f, "payments_received"))));
        grid.addView(Ui.stat(c, "Expenses", Fmt.peso(d(f, "expenses"))));
        grid.addView(Ui.stat(c, "Net income", Fmt.peso(d(f, "net_income"))));
        financeBody.addView(Ui.spaced(c, grid, 12));
        financeBody.addView(Ui.body(c, "Net income = payments received − expenses. Customer balances are shown as of "
                + Fmt.date(end) + "."));
        renderInventory();
    }

    private double receivables;
    private JSONObject inventoryData;

    private void showInventory(JSONObject inv) {
        inventoryData = inv;
        inventoryTitle.setText("Inventory · Last updated " + Fmt.stamp(s(inv, "updated_at")));
        renderInventory();
    }

    private void renderInventory() {
        if (inventoryData == null) {
            return;
        }
        inventoryBody.removeAllViews();
        FlowGrid grid = FlowGrid.stats(c, 4);
        LinearLayout filled = new LinearLayout(c);
        filled.setOrientation(LinearLayout.VERTICAL);
        filled.addView(Ui.stat(c, "Filled available", String.valueOf(i(inventoryData, "filled_available"))));
        filled.addView(Ui.hint(c, "Reserved filled: " + i(inventoryData, "reserved_filled")));
        grid.addView(filled);
        grid.addView(Ui.stat(c, "Empty gallons", String.valueOf(i(inventoryData, "empty"))));
        grid.addView(Ui.stat(c, "Pending delivery", String.valueOf(i(inventoryData, "pending_delivery"))));
        grid.addView(Ui.stat(c, "Receivables", Fmt.peso(receivables)));
        inventoryBody.addView(Ui.spaced(c, grid, 16));
    }

    private void showRecent(JSONArray rows) {
        recent.clear();
        if (rows.length() == 0) {
            recent.showEmpty("No orders yet.");
            return;
        }
        for (int n = 0; n < rows.length(); n++) {
            JSONObject o = rows.optJSONObject(n);
            recent.addRow(null,
                    new String[]{s(o, "order_no"), s(o, "customer_name"), s(o, "status")},
                    new int[]{0, 0, Ui.statusColor(s(o, "status"))});
        }
    }
}
