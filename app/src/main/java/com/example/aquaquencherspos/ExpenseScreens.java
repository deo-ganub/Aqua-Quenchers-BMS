package com.example.aquaquencherspos;

import android.content.Context;
import com.aquaquenchers.classifier.ExpenseCategoryClassifier;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Expense ledger and Add Expense with the local category suggestion (A-09). */
public final class ExpenseScreens {

    static final String[] CATEGORIES = ExpenseClassifier.LABELS.toArray(new String[0]);
    private static final ExecutorService CLASSIFIER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ExpenseScreens() {
    }

    /** Figma "02 · Expenses": newest first, with the filtered total. */
    public static class Expenses extends BaseScreen {
        private JSONArray all = new JSONArray();
        private LocalDate from = Fmt.monthStart();
        private LocalDate to = Fmt.today();
        private Field rangeField;
        private Field categoryField;
        private LinearLayout statsHolder;
        private Table table;
        private TextView footer;

        @Override
        String section() {
            return "Expenses";
        }

        @Override
        void build() {
            head("Expenses", "Record and categorize station operating costs");
            add(WrapRow.of(c, Ui.primary(c, "Add Expense", v -> go(new AddExpense()))));

            LinearLayout card = Ui.titledCard(c, "Expense ledger");
            FlowGrid form = FlowGrid.form(c);
            rangeField = Field.dropdown(c, "Date range", Fmt.range(from, to), () -> Dialogs.period(c, "Select expense dates",
                    "Expenses are filtered by their entered date.", from, to, (a, b) -> {
                        from = a;
                        to = b;
                        rangeField.setValue(Fmt.range(a, b));
                        refresh();
                    }));
            categoryField = Field.dropdown(c, "Category", "All categories", () -> {
                String[] choices = new String[CATEGORIES.length + 1];
                choices[0] = "All categories";
                System.arraycopy(CATEGORIES, 0, choices, 1, CATEGORIES.length);
                Dialogs.options(c, "Expense category", choices, pick -> {
                    categoryField.setValue(pick);
                    render();
                });
            });
            form.addView(rangeField);
            form.addView(categoryField);
            card.addView(Ui.spaced(c, form, 16));
            statsHolder = new LinearLayout(c);
            statsHolder.setOrientation(LinearLayout.VERTICAL);
            card.addView(Ui.spaced(c, statsHolder, 16));
            table = new Table(c, new String[]{"Date", "Category", "Amount", "Remarks / Recorded by"},
                    new float[]{156, 192, 192, 364});
            card.addView(Ui.spaced(c, table, 12));
            footer = Ui.body(c, "");
            card.addView(footer);
            page.addView(card);
            refresh();
        }

        private void refresh() {
            load("expenses?select=*&expense_date=gte." + from + "&expense_date=lte." + to
                    + "&order=expense_date.desc,created_at.desc", rows -> {
                all = rows;
                render();
            });
        }

        private void render() {
            table.clear();
            double total = 0;
            int shown = 0;
            for (int n = 0; n < all.length(); n++) {
                JSONObject e = all.optJSONObject(n);
                if ("All categories".equals(categoryField.value()) || categoryField.value().equals(s(e, "category"))) {
                    String remarks = s(e, "remarks");
                    table.addRow(null, new String[]{
                            Fmt.shortDate(s(e, "expense_date")), s(e, "category"), Fmt.peso(d(e, "amount")),
                            (remarks.isEmpty() ? "—" : remarks) + " / " + s(e, "recorded_by_name")
                    }, null);
                    total += d(e, "amount");
                    shown++;
                }
            }
            if (shown == 0) {
                table.showEmpty("No expenses found for this period.");
            }
            statsHolder.removeAllViews();
            statsHolder.addView(Ui.stat(c, "Filtered expenses", Fmt.peso(total)));
            footer.setText(shown + (shown == 1 ? " record" : " records") + " · Total " + Fmt.peso(total) + " · Newest first");
        }
    }

    /** Figma "03 · Add Expense" and its suggestion states. */
    public static class AddExpense extends BaseScreen {
        private Field date;
        private Field category;
        private Field amount;
        private Field remarks;
        private TextView suggestionNote;
        private String source = "manual";
        private String modelVersion;
        /** Remarks the current suggestion was made for; a change makes it stale. */
        private String suggestedFor;
        private long suggestionGeneration;
        private boolean predictionBusy;

        @Override
        String section() {
            return "Expenses";
        }

        @Override
        void build() {
            head("Add Expense", "Record a dated operating cost");
            LinearLayout card = Ui.titledCard(c, "Expense details");
            FlowGrid form = FlowGrid.form(c);
            date = Field.date(c, "Date required", Fmt.today());
            category = Field.dropdown(c, "Category required", "Select category", this::chooseCategory);
            amount = Field.money(c, "Amount greater than zero", "₱0.00");
            remarks = Field.longText(c, "Remarks optional · 500 characters max", null);
            remarks.setOnChange(this::remarksEdited);
            form.addView(date);
            form.addView(category);
            form.addView(amount);
            form.addView(remarks);
            card.addView(Ui.spaced(c, form, 12));

            Button suggest = Ui.secondary(c, "Suggest category", v -> requestSuggestion());
            suggestionNote = Ui.body(c, "You can also choose a category manually.");
            suggestionNote.setMinHeight(Ui.dp(c, 44));
            suggestionNote.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(Ui.spaced(c, WrapRow.of(c, suggest, suggestionNote), 12));
            card.addView(Ui.spaced(c, Field.readOnly(c, "Recorded by", recordedBy()), 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Cancel", v -> back()),
                    Ui.primary(c, "Save Expense", v -> submit())));
            page.addView(card);
        }

        private void chooseCategory() {
            Dialogs.options(c, "Expense category", CATEGORIES, pick -> {
                suggestionGeneration++;
                category.setValue(pick);
                category.setError(null);
                // A manual choice overrides any suggestion that was applied.
                source = "manual";
                modelVersion = null;
            });
        }

        private void remarksEdited() {
            suggestionGeneration++;
            if (suggestedFor != null && !suggestedFor.equals(remarks.rawValue())) {
                suggestedFor = null;
                source = "manual";
                modelVersion = null;
                suggestionNote.setText("Remarks changed. Request a new suggestion or choose a category manually.");
            }
        }

        private void requestSuggestion() {
            String text = remarks.value();
            if (text.isEmpty()) {
                Dialogs.message(c, "Enter remarks to request a suggestion.",
                        "Remarks are optional for a manual expense. Enter remarks to request a suggestion, "
                                + "or choose Category and save normally.", "Back to expense", null);
                return;
            }
            if (text.codePointCount(0, text.length()) > 500) {
                Dialogs.message(c, "Use 500 characters or fewer.",
                        "Shorten the remarks before requesting a suggestion or saving. Your other inputs are retained.",
                        "Back to expense", null);
                return;
            }
            if (predictionBusy) return;
            predictionBusy=true;
            long generation=++suggestionGeneration, session=Session.generation();
            Context app = c.getApplicationContext();
            // Classification runs on a background executor (B-01.4).
            CLASSIFIER.execute(() -> {
                ExpenseCategoryClassifier model = null;
                ExpenseCategoryClassifier.Result prediction = null;
                try {
                    model = ClassifierAssets.get(app);
                    prediction = model.suggest(text);
                } catch (Exception e) {
                    prediction = null;
                    model = null;
                }
                ExpenseCategoryClassifier finalModel = model;
                ExpenseCategoryClassifier.Result finalPrediction = prediction;
                MAIN.post(() -> {
                    predictionBusy=false;
                    if (isAdded() && getView()!=null && isResumed() && Session.isCurrent(session)
                            && generation==suggestionGeneration && text.equals(remarks.value())) {
                        showSuggestion(finalModel, finalPrediction, text, generation, session);
                    }
                });
            });
        }

        private void showSuggestion(ExpenseCategoryClassifier model, ExpenseCategoryClassifier.Result prediction, String forText, long generation, long session) {
            if (model == null || prediction==null || prediction.status==ExpenseCategoryClassifier.Status.UNAVAILABLE) {
                Dialogs.message(c, "Category suggestion unavailable. Choose a category manually.",
                        "You can still select Category and save a valid expense normally.", "Choose manually", this::chooseCategory);
                return;
            }
            if (prediction.suggestedCategory == null) {
                Dialogs.message(c, "No reliable suggestion. Choose a category manually.",
                        "Review the remarks and choose one of the existing categories. The expense has not been saved.",
                        "Choose manually", this::chooseCategory);
                return;
            }
            suggestedFor = forText;
            Dialogs.confirmChoice(c, "Suggested category: " + prediction.suggestedCategory + ".",
                    "Review this suggestion before saving the expense. You can apply it or choose a category manually.",
                    "Apply suggestion", () -> {
                        if (!Session.isCurrent(session) || Session.isIdleTooLong() || generation!=suggestionGeneration
                                || !forText.equals(remarks.value()) || !isAdded()) return;
                        category.setValue(prediction.suggestedCategory);
                        category.setError(null);
                        source = "suggestion";
                        modelVersion = prediction.modelVersion;
                        suggestionNote.setText("Suggestion applied. Review before saving.");
                        Dialogs.message(c, "Suggestion applied. Review before saving.",
                                "Date, Amount and Remarks are unchanged. Select Save Expense to record the confirmed category.",
                                "Back to expense", null);
                    }, "Choose manually", this::chooseCategory);
        }

        private void submit() {
            if (date.dateValue() == null || category.value().isEmpty() || "Select category".equals(category.value())) {
                Dialogs.correct(c, "Complete the required fields.",
                        "Expense date or category is missing. Enter the required values before saving. The ledger is unchanged.");
                return;
            }
            BigDecimal value = money(amount.value());
            if (value == null || value.signum() <= 0) {
                amount.setError("Enter a valid expense amount.");
                return;
            }
            String text = remarks.value();
            if (text.codePointCount(0, text.length()) > 500) {
                remarks.setError("Use 500 characters or fewer.");
                return;
            }
            save("Saving expense…", "Please wait while the expense is recorded.", "save_expense",
                    params("p_date", date.isoDate(), "p_category", category.value(), "p_amount", value.doubleValue(),
                            "p_remarks", text, "p_source", source, "p_model_version", modelVersion),
                    r -> Dialogs.done(c, "Expense saved.",
                            Fmt.date(s(r, "expense_date")) + " · " + s(r, "category") + " · " + Fmt.peso(d(r, "amount"))
                                    + (s(r, "remarks").isEmpty() ? "" : "\n" + s(r, "remarks")) + " · " + s(r, "recorded_by_name"),
                            this::back));
        }
    }
}
