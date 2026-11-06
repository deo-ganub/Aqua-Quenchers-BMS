package com.example.aquaquencherspos;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** A left-aligned row of buttons that wraps onto the next line when it runs out of room. */
public final class WrapRow extends ViewGroup {

    private final int gap;

    public WrapRow(Context c) {
        super(c);
        gap = Ui.dp(c, 16);
    }

    static WrapRow of(Context c, View... views) {
        WrapRow row = new WrapRow(c);
        for (View v : views) {
            row.addView(v);
        }
        return row;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        int child = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
        int free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) {
                continue;
            }
            v.measure(child, free);
            if (x > 0 && x + v.getMeasuredWidth() > width) {
                x = 0;
                y += rowHeight + gap;
                rowHeight = 0;
            }
            x += v.getMeasuredWidth() + gap;
            rowHeight = Math.max(rowHeight, v.getMeasuredHeight());
        }
        setMeasuredDimension(width, y + rowHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int x = 0;
        int y = 0;
        int rowHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) {
                continue;
            }
            if (x > 0 && x + v.getMeasuredWidth() > width) {
                x = 0;
                y += rowHeight + gap;
                rowHeight = 0;
            }
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            x += v.getMeasuredWidth() + gap;
            rowHeight = Math.max(rowHeight, v.getMeasuredHeight());
        }
    }
}
