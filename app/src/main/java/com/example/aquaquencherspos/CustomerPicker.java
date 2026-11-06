package com.example.aquaquencherspos;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Figma "07 · Select Customer": searchable list with an Add new customer action. */
final class CustomerPicker {

    private CustomerPicker() {
    }

    static void show(Context c, List<JSONObject> customers, Consumer<JSONObject> onPick, Runnable onAddNew) {
        LinearLayout panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 24);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(Ui.box(c, Ui.WHITE, Ui.BORDER, 16));
        panel.addView(Ui.spaced(c, Ui.text(c, "Select customer", Ui.semibold(c), 20, Ui.INK), 12));

        Field search = Field.search(c, null, "Search name or contact number");
        panel.addView(Ui.spaced(c, search, 12));

        LinearLayout list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView listScroll = new ScrollView(c);
        listScroll.addView(list);
        panel.addView(listScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 220)));

        AlertDialog dialog = new AlertDialog.Builder(c).setView(panel).create();
        Runnable render = () -> {
            list.removeAllViews();
            String q = search.value().toLowerCase(Locale.ROOT);
            String digits = q.replaceAll("[^0-9]", "");
            for (JSONObject row : customers) {
                String name = BaseScreen.s(row, "name");
                boolean match = q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)
                        || (!digits.isEmpty() && BaseScreen.s(row, "contact_number").contains(digits));
                if (match) {
                    list.addView(Ui.spaced(c, Ui.listButton(c, name + " · " + BaseScreen.s(row, "customer_type"), v -> {
                        dialog.dismiss();
                        onPick.accept(row);
                    }), 8));
                }
            }
        };
        search.setOnChange(render);
        render.run();

        LinearLayout.LayoutParams addLp = Ui.matchWrap();
        addLp.topMargin = Ui.dp(c, 8);
        panel.addView(Ui.secondary(c, "Add new customer", v -> {
            dialog.dismiss();
            onAddNew.run();
        }), addLp);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screen = c.getResources().getDisplayMetrics().widthPixels;
            dialog.getWindow().setLayout(Math.min(Ui.dp(c, 500), screen - Ui.dp(c, 32)),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }
}
