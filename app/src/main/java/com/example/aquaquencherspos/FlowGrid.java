package com.example.aquaquencherspos;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Equal-width columns that collapse as space shrinks: a four-column statistic
 * row on the tablet becomes two columns, and a two-column form becomes one on a
 * phone. Every screen gets its phone layout from this rather than a second layout.
 */
public final class FlowGrid extends ViewGroup {

    private final int maxColumns;
    private final int minColumnWidth;
    private final int horizontalGap;
    private final int verticalGap;

    public FlowGrid(Context c, int maxColumns, int minColumnDp, int verticalGapDp) {
        super(c);
        this.maxColumns = maxColumns;
        this.minColumnWidth = Ui.dp(c, minColumnDp);
        this.horizontalGap = Ui.dp(c, 16);
        this.verticalGap = Ui.dp(c, verticalGapDp);
    }

    /** Two-column form that stacks on a phone. */
    static FlowGrid form(Context c) {
        return new FlowGrid(c, 2, 240, 12);
    }

    /** Up to the given number of statistics per row. */
    static FlowGrid stats(Context c, int columns) {
        return new FlowGrid(c, columns, 130, 12);
    }

    private int columnsFor(int width) {
        int fit = (width + horizontalGap) / (minColumnWidth + horizontalGap);
        return Math.max(1, Math.min(maxColumns, fit));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int columns = columnsFor(width - getPaddingLeft() - getPaddingRight());
        int columnWidth = (width - getPaddingLeft() - getPaddingRight() - horizontalGap * (columns - 1)) / columns;
        int childSpec = MeasureSpec.makeMeasureSpec(columnWidth, MeasureSpec.EXACTLY);
        int height = getPaddingTop() + getPaddingBottom();
        int rowHeight = 0;
        int column = 0;
        boolean first = true;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            child.measure(childSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            column++;
            if (column == columns) {
                height += rowHeight + (first ? 0 : verticalGap);
                first = false;
                rowHeight = 0;
                column = 0;
            }
        }
        if (column > 0) {
            height += rowHeight + (first ? 0 : verticalGap);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int columns = columnsFor(width - getPaddingLeft() - getPaddingRight());
        int columnWidth = (width - getPaddingLeft() - getPaddingRight() - horizontalGap * (columns - 1)) / columns;
        int y = getPaddingTop();
        int rowHeight = 0;
        int column = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) {
                continue;
            }
            int x = getPaddingLeft() + column * (columnWidth + horizontalGap);
            child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            column++;
            if (column == columns) {
                y += rowHeight + verticalGap;
                rowHeight = 0;
                column = 0;
            }
        }
    }
}
