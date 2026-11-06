package com.example.aquaquencherspos;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * The design's table. On a wide screen it shows weighted columns. When narrower
 * than {@link #STACK_BELOW_DP} (phones) each row becomes a stacked card that
 * labels its own values, so no column is ever cut off.
 */
public final class Table extends LinearLayout {

    private static final int STACK_BELOW_DP = 520;

    public interface OnSelect {
        void onSelect(JSONObject row);
    }

    private final String[] headers;
    private final float[] weights;
    private final LinearLayout headerRow;
    private final LinearLayout rows;
    private OnSelect onSelect;
    private View selectedRow;
    private JSONObject selected;
    private boolean stacked;

    public Table(Context c, String[] headers, float[] weights) {
        super(c);
        this.headers = headers;
        this.weights = weights;
        setOrientation(VERTICAL);

        headerRow = new LinearLayout(c);
        headerRow.setOrientation(HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setBackgroundColor(Ui.PAGE);
        headerRow.setMinimumHeight(Ui.dp(c, 38));
        headerRow.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        for (int i = 0; i < headers.length; i++) {
            TextView h = Ui.label(c, headers[i]);
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, weights[i]);
            lp.rightMargin = Ui.dp(c, 8);
            headerRow.addView(h, lp);
        }
        addView(headerRow);

        rows = new LinearLayout(c);
        rows.setOrientation(VERTICAL);
        addView(rows);
    }

    void setOnSelect(OnSelect listener) {
        onSelect = listener;
    }

    /** The row chosen by tapping it, or null. */
    JSONObject selected() {
        return selected;
    }

    void clear() {
        rows.removeAllViews();
        selected = null;
        selectedRow = null;
    }

    void showEmpty(String message) {
        clear();
        TextView t = Ui.body(getContext(), message);
        int pad = Ui.dp(getContext(), 16);
        t.setPadding(0, pad, 0, pad);
        rows.addView(t);
    }

    /** Adds a plain row. Each cell may carry a color from {@code colors} (0 for default). */
    void addRow(JSONObject tag, String[] cells, int[] colors) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(c, 54));
        row.setTag(tag);
        for (int i = 0; i < cells.length; i++) {
            LinearLayout cell = new LinearLayout(c);
            cell.setOrientation(HORIZONTAL);
            TextView heading = Ui.text(c, headers[i], Ui.medium(c), 12, Ui.MUTED);
            cell.addView(heading, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.42f));
            TextView value = Ui.ink(c, cells[i]);
            if (colors != null && colors[i] != 0) {
                value.setTextColor(colors[i]);
            }
            cell.addView(value, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 0.58f));
            row.addView(cell);
        }
        if (onSelect != null || tag != null) {
            row.setOnClickListener(v -> select(row, tag));
        }
        rows.addView(row);
        applyMode(row);
    }

    private void select(View row, JSONObject tag) {
        if (selectedRow != null) {
            selectedRow.setBackground(null);
        }
        selectedRow = row;
        selected = tag;
        row.setBackground(Ui.box(getContext(), Ui.ACTIVE, 0, 8));
        if (onSelect != null) {
            onSelect.onSelect(tag);
        }
    }

    /** Selects the first row, so the action button under the table works right away. */
    void selectFirst() {
        if (rows.getChildCount() > 0 && rows.getChildAt(0).getTag() instanceof JSONObject) {
            View row = rows.getChildAt(0);
            select(row, (JSONObject) row.getTag());
        }
    }

    private void applyMode(LinearLayout row) {
        int side = Ui.dp(getContext(), stacked ? 12 : 8);
        int vertical = Ui.dp(getContext(), stacked ? 10 : 6);
        row.setOrientation(stacked ? VERTICAL : HORIZONTAL);
        row.setPadding(side, vertical, side, vertical);
        for (int i = 0; i < row.getChildCount(); i++) {
            LinearLayout cell = (LinearLayout) row.getChildAt(i);
            cell.getChildAt(0).setVisibility(stacked ? VISIBLE : GONE);
            LayoutParams valueLp = (LayoutParams) cell.getChildAt(1).getLayoutParams();
            valueLp.weight = stacked ? 0.58f : 1f;
            LayoutParams lp = stacked
                    ? new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
                    : new LayoutParams(0, LayoutParams.WRAP_CONTENT, weights[i]);
            if (stacked) {
                lp.bottomMargin = Ui.dp(getContext(), 4);
            } else {
                lp.rightMargin = Ui.dp(getContext(), 8);
            }
            cell.setLayoutParams(lp);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        boolean shouldStack = MeasureSpec.getSize(widthSpec) < Ui.dp(getContext(), STACK_BELOW_DP);
        if (shouldStack != stacked) {
            stacked = shouldStack;
            headerRow.setVisibility(stacked ? GONE : VISIBLE);
            for (int i = 0; i < rows.getChildCount(); i++) {
                if (rows.getChildAt(i) instanceof LinearLayout) {
                    applyMode((LinearLayout) rows.getChildAt(i));
                }
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }
}
