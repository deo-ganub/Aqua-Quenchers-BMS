package com.example.aquaquencherspos;

import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.LocalDate;

/** Figma "O2 · Dashboard" (A-02): period tabs, finance cards, gallon stock, recent orders. */
public class DashboardScreen extends BaseScreen {

    private static final String[] PERIODS = {"Today", "This week", "This month", "Custom"};
    private static final int TILE_GREEN = 0xFFE6F4EA;
    private static final int TILE_BLUE = 0xFFE0F2FE;
    private static final int TILE_GREY = 0xFFE8EEF4;
    private static final int TILE_AMBER = 0xFFFEF3D6;
    private static final int AMBER_TEXT = 0xFFB45309;

    private String period = "This month";
    private LocalDate start = Fmt.monthStart();
    private LocalDate end = Fmt.today();

    private LinearLayout tabRow;
    private TextView rangeText;
    private TextView updatedChip;
    private LinearLayout financeBody;
    private LinearLayout stockBody;
    private TextView outstanding;
    private LinearLayout recentBody;

    @Override
    String section() {
        return "Dashboard";
    }

    private boolean tablet() {
        return c.getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    @Override
    void build() {
        // Title row with the "Updated" chip.
        LinearLayout titleRow = new LinearLayout(c);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(Ui.pageTitle(c, "Dashboard"), new LinearLayout.LayoutParams(0, -2, 1f));
        updatedChip = Ui.text(c, "", Ui.medium(c), 12, Ui.BODY);
        updatedChip.setBackground(Ui.box(c, 0xFFEEF2F6, 0, 13));
        updatedChip.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        updatedChip.setVisibility(View.GONE);
        titleRow.addView(updatedChip);
        page.addView(Ui.spaced(c, titleRow, 14));

        // Period tabs: Today, This week, This month, Custom + the covered range.
        HorizontalScrollView scroller = new HorizontalScrollView(c);
        scroller.setHorizontalScrollBarEnabled(false);
        tabRow = new LinearLayout(c);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        scroller.addView(tabRow);
        page.addView(Ui.spaced(c, scroller, 16));

        financeBody = new LinearLayout(c);
        financeBody.setOrientation(LinearLayout.VERTICAL);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(financeBody);
        left.addView(recentCard());

        LinearLayout right = new LinearLayout(c);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(stockCard());
        right.addView(outstandingCard());
        right.addView(Ui.spaced(c, Ui.fullWidth(c, Ui.primary(c, "+  Create order",
                v -> go(new OrderScreens.CreateOrder()))), 12));
        right.addView(Ui.spaced(c, Ui.fullWidth(c, Ui.secondary(c, "Walk-in sale",
                v -> go(new PaymentScreens.WalkInSale()))), 12));
        right.addView(Ui.spaced(c, Ui.fullWidth(c, Ui.outline(c, "Reports",
                v -> shell().openRoot(new ReportScreens.Reports()))), 0));

        if (tablet()) {
            LinearLayout cols = new LinearLayout(c);
            cols.setBaselineAligned(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.rightMargin = Ui.dp(c, 16);
            cols.addView(left, lp);
            cols.addView(right, new LinearLayout.LayoutParams(Ui.dp(c, 330), -2));
            for (LinearLayout col : new LinearLayout[]{left, right}) {
                col.setGravity(Gravity.TOP);
            }
            page.addView(cols, Ui.matchWrap());
        } else {
            page.addView(right);
            page.addView(left);
        }

        buildTabs();
        refresh();
    }

    // ------------------------------------------------------------------ tabs

    private void buildTabs() {
        tabRow.removeAllViews();
        for (String name : PERIODS) {
            boolean on = name.equals(period);
            TextView tab = Ui.text(c, name, Ui.medium(c), 14, on ? Ui.WHITE : Ui.INK);
            tab.setGravity(Gravity.CENTER);
            tab.setBackground(Ui.box(c, on ? Ui.PRIMARY : Ui.WHITE, on ? Ui.PRIMARY : Ui.INPUT_BORDER, 20));
            tab.setPadding(Ui.dp(c, 15), 0, Ui.dp(c, 15), 0);
            tab.setMinHeight(Ui.dp(c, 39));
            tab.setOnClickListener(v -> choose(name));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 39));
            lp.rightMargin = Ui.dp(c, 8);
            tabRow.addView(tab, lp);
        }
        rangeText = Ui.text(c, "", Ui.medium(c), 14, Ui.BODY);
        tabRow.addView(rangeText);
    }

    private void choose(String name) {
        LocalDate today = Fmt.today();
        switch (name) {
            case "Today":
                period = name;
                start = today;
                end = today;
                break;
            case "This week":
                period = name;
                start = today.with(DayOfWeek.MONDAY);
                end = today;
                break;
            case "This month":
                period = name;
                start = today.withDayOfMonth(1);
                end = today;
                break;
            default:
                Dialogs.period(c, "Select dashboard period",
                        "Choose the start and end dates for the dashboard figures.", start, end,
                        (from, to) -> {
                            period = "Custom";
                            start = from;
                            end = to;
                            buildTabs();
                            refresh();
                        });
                return;
        }
        buildTabs();
        refresh();
    }

    // ----------------------------------------------------------------- cards

    private LinearLayout statCard(String label, String value, String caption, boolean dark, int valueColor) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(dark ? Ui.box(c, Ui.PRIMARY, 0, 14) : Ui.box(c, Ui.WHITE, Ui.BORDER, 14));
        card.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 16));
        int sub = dark ? 0xFFE0F2FE : Ui.BODY;
        card.addView(Ui.text(c, label, Ui.medium(c), 13, sub));
        TextView v = Ui.text(c, value, Ui.semibold(c), 26, dark ? Ui.WHITE : valueColor);
        card.addView(v);
        card.addView(Ui.text(c, caption, Ui.regular(c), 12, sub));
        return card;
    }

    private LinearLayout panel() {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(Ui.box(c, Ui.WHITE, Ui.BORDER, 14));
        int pad = Ui.dp(c, 18);
        p.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.bottomMargin = Ui.dp(c, 16);
        p.setLayoutParams(lp);
        return p;
    }

    private LinearLayout recentCard() {
        LinearLayout card = panel();
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.cardTitle(c, "Recent orders"), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView all = Ui.text(c, "See all ›", Ui.medium(c), 13, Ui.PRIMARY);
        all.setOnClickListener(v -> shell().openRoot(new OrderScreens.Orders()));
        head.addView(all);
        card.addView(Ui.spaced(c, head, 6));
        recentBody = new LinearLayout(c);
        recentBody.setOrientation(LinearLayout.VERTICAL);
        card.addView(recentBody);
        return card;
    }

    private LinearLayout stockCard() {
        LinearLayout card = panel();
        card.addView(Ui.spaced(c, Ui.cardTitle(c, "Gallon stock"), 12));
        stockBody = new LinearLayout(c);
        stockBody.setOrientation(LinearLayout.VERTICAL);
        card.addView(stockBody);
        TextView link = Ui.text(c, "Inventory ›", Ui.medium(c), 13, Ui.PRIMARY);
        link.setPadding(0, Ui.dp(c, 12), 0, 0);
        link.setOnClickListener(v -> go(new SettingsScreens.Inventory()));
        card.addView(link);
        return card;
    }

    private LinearLayout outstandingCard() {
        LinearLayout card = panel();
        card.addView(Ui.text(c, "Outstanding balances", Ui.medium(c), 13, Ui.BODY));
        outstanding = Ui.text(c, Fmt.peso(0), Ui.semibold(c), 26, AMBER_TEXT);
        card.addView(outstanding);
        TextView link = Ui.text(c, "Customer balances ›", Ui.medium(c), 13, Ui.PRIMARY);
        link.setPadding(0, Ui.dp(c, 6), 0, 0);
        link.setOnClickListener(v -> shell().openRoot(new CustomerScreens.Customers()));
        card.addView(link);
        return card;
    }

    private LinearLayout tile(int fill, String value, String label, int valueColor) {
        LinearLayout t = new LinearLayout(c);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(Ui.box(c, fill, 0, 12));
        t.setPadding(Ui.dp(c, 12), Ui.dp(c, 10), Ui.dp(c, 12), Ui.dp(c, 10));
        t.addView(Ui.text(c, value, Ui.semibold(c), 22, valueColor));
        t.addView(Ui.text(c, label, Ui.medium(c), 12, Ui.INK));
        return t;
    }

    // ------------------------------------------------------------------ data

    private void refresh() {
        rangeText.setText(start.equals(end) ? Fmt.date(start) : Fmt.range(start, end));
        call("financial_summary", params("p_start", start.toString(), "p_end", end.toString()), this::showFinance);
        call("inventory_status", new JSONObject(), this::showInventory);
        load("orders_view?select=id,order_no,customer_name,status,payment_status,balance,pending_gallons"
                + "&order=created_at.desc&limit=5", this::showRecent);
    }

    private void showFinance(JSONObject f) {
        financeBody.removeAllViews();
        FlowGrid grid = new FlowGrid(c, 2, 200, 16);
        grid.addView(statCard("Revenue", Fmt.peso(d(f, "revenue")), "Orders and sales by order date", false, Ui.INK));
        grid.addView(statCard("Payments received", Fmt.peso(d(f, "payments_received")), "Cash by payment date", false, Ui.INK));
        grid.addView(statCard("Expenses", Fmt.peso(d(f, "expenses")), "By expense date", false, Ui.INK));
        grid.addView(statCard("Net income", Fmt.peso(d(f, "net_income")), "Payments received − expenses", true, Ui.WHITE));
        financeBody.addView(Ui.spaced(c, grid, 16));
        outstanding.setText(Fmt.peso(d(f, "outstanding_balances")));
    }

    private void showInventory(JSONObject inv) {
        String stamp = Fmt.event(s(inv, "updated_at"));
        updatedChip.setText(stamp.isEmpty() ? "" : "Updated " + stamp);
        updatedChip.setVisibility(stamp.isEmpty() ? View.GONE : View.VISIBLE);
        stockBody.removeAllViews();
        FlowGrid grid = new FlowGrid(c, 2, 100, 8);
        grid.addView(tile(TILE_GREEN, String.valueOf(i(inv, "filled_available")), "Filled available", Ui.SUCCESS));
        grid.addView(tile(TILE_BLUE, String.valueOf(i(inv, "reserved_filled")), "Reserved filled", Ui.PRIMARY));
        grid.addView(tile(TILE_GREY, String.valueOf(i(inv, "empty")), "Empty", Ui.INK));
        grid.addView(tile(TILE_AMBER, String.valueOf(i(inv, "pending_delivery")), "Pending delivery", AMBER_TEXT));
        stockBody.addView(grid);
    }

    private void showRecent(JSONArray rows) {
        recentBody.removeAllViews();
        if (rows.length() == 0) {
            recentBody.addView(Ui.body(c, "No orders yet."));
            return;
        }
        for (int n = 0; n < rows.length(); n++) {
            JSONObject o = rows.optJSONObject(n);
            recentBody.addView(orderRow(o));
        }
    }

    private View orderRow(JSONObject o) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 52));
        row.setOnClickListener(v -> go(withId(new OrderScreens.OrderDetails(), s(o, "id"))));

        LinearLayout who = new LinearLayout(c);
        who.setOrientation(LinearLayout.VERTICAL);
        who.addView(Ui.text(c, s(o, "customer_name"), Ui.medium(c), 15, Ui.INK));
        who.addView(Ui.text(c, s(o, "order_no"), Ui.regular(c), 12, Ui.BODY));
        row.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));

        String status = s(o, "status");
        int pending = i(o, "pending_gallons");
        boolean undelivered = "Undelivered".equals(status) && pending > 0;
        String chipText = undelivered ? pending + " left" : shortStatus(status);
        int[] colors = chipColors(status);
        TextView chip = Ui.text(c, chipText, Ui.medium(c), 12, colors[1]);
        chip.setBackground(Ui.box(c, colors[0], 0, 13));
        chip.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(-2, -2);
        chipLp.rightMargin = Ui.dp(c, 12);
        row.addView(chip, chipLp);

        double balance = d(o, "balance");
        TextView due = balance > 0
                ? Ui.text(c, Fmt.peso(balance) + " due", Ui.medium(c), 13, AMBER_TEXT)
                : Ui.text(c, "Paid", Ui.medium(c), 13, Ui.SUCCESS);
        due.setMinWidth(Ui.dp(c, 70));
        due.setGravity(Gravity.END);
        row.addView(due);
        return row;
    }

    private static String shortStatus(String status) {
        switch (status) {
            case "For Pickup":
                return "To pick up";
            case "Ready for Delivery":
                return "Ready";
            default:
                return status;
        }
    }

    private static int[] chipColors(String status) {
        switch (status) {
            case "Ready for Delivery":
            case "Delivered":
                return new int[]{TILE_GREEN, Ui.SUCCESS};
            case "Undelivered":
            case "Rescheduled":
                return new int[]{TILE_AMBER, AMBER_TEXT};
            default:
                return new int[]{TILE_BLUE, Ui.PRIMARY};
        }
    }
}
