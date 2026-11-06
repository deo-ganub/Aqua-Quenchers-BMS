package com.example.aquaquencherspos;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;

/** A labelled form control: text input, read-only value, dropdown or date. */
public final class Field extends LinearLayout {

    private final TextView input;
    private final TextView error;
    private LocalDate date;
    private Runnable onChange;

    private Field(Context c, String label, TextView control) {
        super(c);
        setOrientation(VERTICAL);
        if (label != null) {
            addView(Ui.spaced(c, Ui.label(c, label), 4));
        }
        input = control;
        input.setTypeface(Ui.regular(c));
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(0xFF334155);
        input.setHintTextColor(Ui.MUTED);
        input.setMinHeight(Ui.dp(c, 44));
        input.setGravity(Gravity.CENTER_VERTICAL);
        int side = Ui.dp(c, 15);
        input.setPadding(side, Ui.dp(c, 6), side, Ui.dp(c, 6));
        input.setBackground(Ui.box(c, Ui.WHITE, Ui.INPUT_BORDER, 10));
        addView(input, Ui.matchWrap());
        error = Ui.text(c, "", Ui.regular(c), 12, Ui.ERROR);
        error.setVisibility(GONE);
        addView(error, Ui.matchWrap());
    }

    private static Field editable(Context c, String label, int type, String hint) {
        EditText edit = new EditText(c);
        edit.setInputType(type);
        edit.setHint(hint);
        edit.setSingleLine(type != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        Field f = new Field(c, label, edit);
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int d) {
                f.setError(null);
                if (f.onChange != null) {
                    f.onChange.run();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        return f;
    }

    static Field text(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, hint);
    }

    static Field plain(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, hint);
    }

    static Field longText(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, hint);
    }

    /** Text rather than a number keypad, so "2.5" reaches validation and gets its specific message. */
    static Field quantity(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, hint);
    }

    static Field money(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED, hint);
    }

    static Field phone(Context c, String label, String hint) {
        return editable(c, label, InputType.TYPE_CLASS_PHONE, hint);
    }

    static Field password(Context c, String label) {
        return editable(c, label, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
    }

    static Field readOnly(Context c, String label, String value) {
        Field f = new Field(c, label, new TextView(c));
        f.setValue(value);
        return f;
    }

    /** Opens a chooser. The screen supplies what happens on tap, usually {@link Dialogs#options}. */
    static Field dropdown(Context c, String label, String value, Runnable onTap) {
        Field f = new Field(c, label, new TextView(c));
        f.input.setCompoundDrawablePadding(Ui.dp(c, 8));
        f.setValue(value);
        f.input.setOnClickListener(v -> onTap.run());
        return f;
    }

    static Field date(Context c, String label, LocalDate initial) {
        Field f = new Field(c, label, new TextView(c));
        f.setDate(initial);
        f.input.setOnClickListener(v -> f.pickDate());
        return f;
    }

    static Field search(Context c, String label, String hint) {
        Field f = editable(c, label, InputType.TYPE_CLASS_TEXT, hint);
        f.input.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_aq_search, 0, 0, 0);
        f.input.setCompoundDrawablePadding(Ui.dp(c, 8));
        f.input.getCompoundDrawablesRelative()[0].setTint(Ui.MUTED);
        return f;
    }

    private void pickDate() {
        LocalDate start = date != null ? date : Fmt.today();
        DatePickerDialog dialog = new DatePickerDialog(getContext(),
                (view, year, month, day) -> {
                    setDate(LocalDate.of(year, month + 1, day));
                    setError(null);
                    if (onChange != null) {
                        onChange.run();
                    }
                },
                start.getYear(), start.getMonthValue() - 1, start.getDayOfMonth());
        dialog.show();
    }

    void setOnChange(Runnable callback) {
        onChange = callback;
    }

    void setDate(LocalDate value) {
        date = value;
        input.setText(Fmt.date(value));
    }

    LocalDate dateValue() {
        return date;
    }

    /** The picked date in the plain format database functions expect, or null. */
    String isoDate() {
        return date == null ? null : date.toString();
    }

    String value() {
        return input.getText().toString().trim();
    }

    /** The exact typed text, for passwords where spaces count. */
    String rawValue() {
        return input.getText().toString();
    }

    void setValue(String value) {
        input.setText(value);
    }

    void setValueColor(int color) {
        input.setTextColor(color);
    }

    void setError(String message) {
        boolean show = message != null;
        error.setText(show ? message : "");
        error.setVisibility(show ? VISIBLE : GONE);
        input.setBackground(Ui.box(getContext(), Ui.WHITE, show ? 0xFFEF4444 : Ui.INPUT_BORDER, 10));
    }
}
