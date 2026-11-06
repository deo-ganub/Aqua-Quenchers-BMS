package com.example.aquaquencherspos;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** The design's dialog family: messages, saving states, choosers and date periods. */
public final class Dialogs {

    private Dialogs() {
    }

    private static LinearLayout panel(Context c, String title, int titleColor) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(c, 24);
        p.setPadding(pad, pad, pad, pad);
        p.setBackground(Ui.box(c, Ui.WHITE, Ui.BORDER, 16));
        if (title != null) {
            p.addView(Ui.spaced(c, Ui.text(c, title, Ui.semibold(c), 20, titleColor), 8));
        }
        return p;
    }

    private static AlertDialog show(Context c, View content, boolean cancelable, int widthDp) {
        ScrollView scroll = new ScrollView(c);
        scroll.addView(content);
        AlertDialog d = new AlertDialog.Builder(c).setView(scroll).setCancelable(cancelable).create();
        d.show();
        if (d.getWindow() != null) {
            d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int screen = c.getResources().getDisplayMetrics().widthPixels;
            d.getWindow().setLayout(Math.min(Ui.dp(c, widthDp), screen - Ui.dp(c, 32)),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }

    /** Title, optional lines of detail, and one full-width button. */
    static void message(Context c, String title, String detail, String button, Runnable onClose) {
        LinearLayout p = panel(c, title, Ui.INK);
        if (!TextUtils.isEmpty(detail)) {
            p.addView(Ui.spaced(c, Ui.body(c, detail), 16));
        }
        AlertDialog[] holder = new AlertDialog[1];
        p.addView(Ui.primary(c, button, v -> {
            holder[0].dismiss();
            if (onClose != null) {
                onClose.run();
            }
        }), Ui.matchWrap());
        holder[0] = show(c, p, false, 520);
    }

    static void done(Context c, String title, String detail, Runnable onClose) {
        message(c, title, detail, "Done", onClose);
    }

    static void correct(Context c, String title, String detail) {
        message(c, title, detail, "Correct details", null);
    }

    /** Two-button confirmation, e.g. recording a Walk-in balance as credit. */
    static void confirm(Context c, String title, String detail, String yes, Runnable onYes) {
        LinearLayout p = panel(c, title, Ui.INK);
        p.addView(Ui.spaced(c, Ui.body(c, detail), 16));
        AlertDialog[] holder = new AlertDialog[1];
        Button cancel = Ui.secondary(c, "Cancel", v -> holder[0].dismiss());
        Button ok = Ui.primary(c, yes, v -> {
            holder[0].dismiss();
            onYes.run();
        });
        p.addView(WrapRow.of(c, cancel, ok));
        holder[0] = show(c, p, false, 520);
    }

    /** Two actions with no Cancel, e.g. Apply suggestion or Choose manually. */
    static void confirmChoice(Context c, String title, String detail,
                              String first, Runnable onFirst, String second, Runnable onSecond) {
        LinearLayout p = panel(c, title, Ui.INK);
        p.addView(Ui.spaced(c, Ui.body(c, detail), 16));
        AlertDialog[] holder = new AlertDialog[1];
        Button a = Ui.primary(c, first, v -> {
            holder[0].dismiss();
            onFirst.run();
        });
        Button b = Ui.secondary(c, second, v -> {
            holder[0].dismiss();
            onSecond.run();
        });
        p.addView(WrapRow.of(c, a, b));
        holder[0] = show(c, p, true, 520);
    }

    /** The "Saving…" state shown while a request is running. */
    static AlertDialog saving(Context c, String title, String detail) {
        LinearLayout p = panel(c, title, Ui.INK);
        p.addView(Ui.spaced(c, Ui.body(c, detail), 16));
        Button busy = Ui.primary(c, "Saving…", null);
        Ui.setEnabled(busy, false);
        p.addView(busy, Ui.matchWrap());
        return show(c, p, false, 520);
    }

    /** A plain list of choices. */
    static void options(Context c, String title, String[] choices, Consumer<String> onPick) {
        LinearLayout p = panel(c, title, Ui.INK);
        AlertDialog[] holder = new AlertDialog[1];
        for (String choice : choices) {
            Button b = Ui.listButton(c, choice, v -> {
                holder[0].dismiss();
                onPick.accept(choice);
            });
            p.addView(Ui.spaced(c, b, 8));
        }
        holder[0] = show(c, p, true, 420);
    }

    /** Start and end date chooser used by the Dashboard, Orders, Payments and Expenses filters. */
    static void period(Context c, String title, String intro, LocalDate start, LocalDate end,
                       BiConsumer<LocalDate, LocalDate> onApply) {
        LinearLayout p = panel(c, title, Ui.INK);
        p.addView(Ui.spaced(c, Ui.body(c, intro), 12));
        Field from = Field.date(c, "Start date", start);
        Field to = Field.date(c, "End date", end);
        FlowGrid grid = new FlowGrid(c, 2, 170, 12);
        grid.addView(from);
        grid.addView(to);
        p.addView(Ui.spaced(c, grid, 12));
        TextView note = Ui.body(c, "The end date must be on or after the start date.");
        p.addView(Ui.spaced(c, note, 16));
        AlertDialog[] holder = new AlertDialog[1];
        Button cancel = Ui.secondary(c, "Cancel", v -> holder[0].dismiss());
        Button apply = Ui.primary(c, "Apply period", v -> {
            if (to.dateValue().isBefore(from.dateValue())) {
                to.setError("The end date cannot be earlier than the start date.");
                return;
            }
            holder[0].dismiss();
            onApply.accept(from.dateValue(), to.dateValue());
        });
        p.addView(WrapRow.of(c, cancel, apply));
        holder[0] = show(c, p, true, 460);
    }
}
