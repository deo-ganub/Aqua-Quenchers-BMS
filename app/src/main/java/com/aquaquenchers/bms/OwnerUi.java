package com.aquaquenchers.bms;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.aquaquenchers.classifier.*;

/**
 * Owner expenses (O9), reports (O10) and settings (O11–O13), plus the Staff "My account" screens (T1–T4).
 * All persistence is confirmed by the backend.
 */
final class OwnerUi {
    private final MainActivity a;
    private final Ui ui;
    private final ExecutorService predictions = Executors.newSingleThreadExecutor();
    private ExpenseCategoryClassifier cachedClassifier;
    private JSONObject report;
    private byte[] pdf;
    private static final int PDF_REQUEST = 201, CSV_REQUEST = 202;
    private byte[] csv;
    private static final int TEXT = InputType.TYPE_CLASS_TEXT;
    private static final int MONEY = InputType.TYPE_CLASS_TEXT;
    private static final int SECRET = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
    OwnerUi(MainActivity activity) { a = activity; ui = activity.ui(); report = a.reportSnapshot(); pdf = a.pendingPdf(); }

    boolean restore(String route, String entity) {
        if (a.isOwner() && "Edit Staff account".equals(route) && Rules.uuid(entity)) { staffAccounts(entity); return true; }
        switch (route) {
            case "Expenses": case "Add Expense": if (a.isOwner()) { expenses(); return true; } break;
            case "Financial summary": case "Report Preview": if (a.isOwner()) { reports(); return true; } break;
            case "Gallons at station": if (a.isOwner()) { inventory(); return true; } break;
            case "Staff accounts": case "Create Staff account": if (a.isOwner()) { staffAccounts(); return true; } break;
            case "Settings": settings(); return true;
            case "My account": case "Edit profile": profile(); return true;
            case "Change password": password(); return true;
        }
        return false;
    }
    private ExpenseCategoryClassifier.Result predict(String remarks) { if (cachedClassifier == null) cachedClassifier = ClassifierAssets.load(a.getApplicationContext()); return cachedClassifier.suggest(remarks); }
    void close() { predictions.shutdown(); }
    private boolean owner() { if (a.isOwner()) return true; a.accessDenied(); return false; }
    private String value(EditText e) { return e.getText().toString(); }
    private void api(String method, String path, JSONObject body, java.util.function.Consumer<JSONObject> success) {
        Backend expected = a.backend();
        expected.call(method, path, body, r -> { if (expected == a.backend() && !a.isDestroyed() && !a.isFinishing()) success.accept(r); }, e -> {
            if (expected == a.backend() && !a.isDestroyed() && !a.isFinishing()) {
                if (e.status == 401 && !path.contains("username-login")) a.endSession("Session expired. Sign in again.");
                else if (path.equals("/functions/v1/username-login") && (e.status == 400 || e.status == 401)) a.error("Current password is incorrect.");
                else a.error(e.getMessage());
            }
        });
    }
    private static String shortDate(String iso) { try { return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)); } catch (Exception e) { return iso; } }
    private LinearLayout card(LinearLayout p, float gap) { return ui.card(p, gap, 18); }
    private TextView dropChip(LinearLayout row, String label, boolean on, Runnable click) {
        TextView t = ui.tv(label, 14, 21, on ? Ui.WHITE : Ui.INK, ui.p5); ui.pad(t, 14, 8); t.setBackground(ui.shape(on ? Ui.PRIMARY : Ui.WHITE, on ? 0 : Ui.FIELD, 20));
        t.setClickable(true); t.setOnClickListener(v -> click.run()); ui.hug(row, t); return t;
    }

    // ================================================================================================ inventory
    void inventory() { settingsPage("Gallons at station", null); }
    /** Customer-owned gallons the station is holding now (picked up or dropped off, not yet returned). */
    private void inventoryPane(LinearLayout pane) {
        a.query("inventory", a.json(), r -> {
            LinearLayout c = card(pane, 12);
            LinearLayout head = ui.H(c, 8);
            ui.grow(head, ui.tv("Gallons at the station", 22, 33, Ui.INK, ui.p6));
            String stamp = Rules.shortStamp(r.optString("updated_at"));
            if (!stamp.isEmpty()) ui.hug(head, ui.chip("Updated " + stamp, Ui.GREY_BG, Ui.MUTED));
            LinearLayout tiles = ui.H(c, 12);
            String[] keys = {"at_station", "refilling", "pending_quantity", "to_claim"};
            String[] labels = {"At the station", "Refilling", "To deliver", "To claim"};
            int[] bg = {Ui.TONAL, Ui.GREY_BG, Ui.AMBER_BG, Ui.GREEN_BG}, fg = {Ui.PRIMARY, Ui.INK, Ui.AMBER, Ui.GREEN};
            for (int i = 0; i < 4; i++) {
                LinearLayout t = ui.vbox(0); ui.bg(t, bg[i], 0, 12); ui.pad(t, 14, 14);
                ui.text(t, Long.toString(r.optLong(keys[i])), 28, 42, fg[i], ui.p6);
                ui.text(t, labels[i], 13, 18, Ui.INK, ui.o6);
                t.setContentDescription(labels[i] + ": " + r.optLong(keys[i]));
                ui.grow(tiles, t);
            }
            LinearLayout list = card(pane, 10);
            ui.text(list, "Whose gallons we have", 17, 26, Ui.INK, ui.p6);
            JSONArray rows = r.optJSONArray("gallons");
            if (rows == null || rows.length() == 0) ui.text(list, "No customer gallons at the station.", 14, 19, Ui.MUTED, ui.o4);
            else for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                LinearLayout line = ui.H(list, 12); ui.pad(line, 0, 8); line.setClickable(true);
                String oid = row.optString("id");
                line.setOnClickListener(v -> { if (Rules.uuid(oid)) a.operations().order(oid); });
                LinearLayout names = ui.hbox(14); ui.grow(line, names);
                ui.hug(names, ui.tv(row.optString("customer_name"), 15, 22, Ui.INK, ui.p5));
                ui.hug(names, ui.tv(row.optString("reference") + ("counter".equals(row.optString("channel")) ? " · walk-in" : ""), 13, 18, Ui.MUTED, ui.o4));
                long days = 0; try { days = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(row.optString("business_date")), LocalDate.parse(a.today()))); } catch (Exception ignored) { }
                ui.hug(line, ui.tv("Since " + shortDate(row.optString("business_date")) + " · " + (days == 0 ? "today" : days == 1 ? "1 day" : days + " days"), 13, 18, Ui.MUTED, ui.o4));
                ui.hug(line, ui.statusChip(row.optString("status"), OperationsUi.pending(row)));
                long left = row.optLong("quantity") - row.optLong("delivered_quantity");
                ui.hug(line, ui.chip(left + (left == 1 ? " gallon" : " gallons"), Ui.AMBER_BG, Ui.AMBER));
            }
            ui.banner(pane, Ui.INFO, "These gallons belong to customers", "The list updates from pickups, walk-in drop-offs, deliveries and claims. The station keeps no gallon stock of its own.", true);
        });
    }

    // ================================================================================================ expenses (O9)
    private static final String[] PERIODS = {"Today", "This week", "This month", "Custom"};
    private String expFrom, expTo;
    void expenses() {
        if (!owner()) return;
        a.page("Expenses");
        LinearLayout[] panes = a.panes(380);
        LinearLayout list = panes[0];
        a.bannerAt(panes[1], 0);
        int[] per = {1}; String savedPer = a.choice("expense_period"); if (!savedPer.isEmpty()) try { per[0] = Math.max(0, Math.min(3, Integer.parseInt(savedPer))); } catch (NumberFormatException ignored) { }
        int[] cat = {0};
        LinearLayout filters = ui.hbox(8); ui.hug(list, filters);
        // Phones: the add form opens on its own page so the list stays readable.
        if (!a.wide()) ui.fill(list, ui.button("+  Add expense", Ui.FILLED, 46, 12, 15, () -> addExpensePage()));
        LinearLayout records = ui.V(list, 14);
        long[] filterRequest = {0}; Runnable[] load = new Runnable[1];
        TextView[] chips = new TextView[2];
        chips[0] = dropChip(filters, PERIODS[per[0]] + " ▾", true, () -> {
            PopupMenu m = new PopupMenu(a, chips[0]); for (int i = 0; i < PERIODS.length; i++) m.getMenu().add(0, i, i, PERIODS[i]);
            m.setOnMenuItemClickListener(item -> { int i = item.getItemId(); if (i == 3) customPeriod(() -> { per[0] = 3; chips[0].setText("Custom ▾"); load[0].run(); }); else { per[0] = i; a.rememberChoice("expense_period", Integer.toString(i)); chips[0].setText(PERIODS[i] + " ▾"); load[0].run(); } return true; });
            m.show();
        });
        chips[1] = dropChip(filters, "All categories ▾", false, () -> {
            PopupMenu m = new PopupMenu(a, chips[1]); m.getMenu().add(0, 0, 0, "All categories"); for (int i = 0; i < Rules.CATEGORIES.length; i++) m.getMenu().add(0, i + 1, i + 1, Rules.CATEGORIES[i]);
            m.setOnMenuItemClickListener(item -> { cat[0] = item.getItemId(); chips[1].setText((cat[0] == 0 ? "All categories" : Rules.CATEGORIES[cat[0] - 1]) + " ▾"); load[0].run(); return true; });
            m.show();
        });
        load[0] = () -> {
            LocalDate now = LocalDate.parse(a.today()); String from, to;
            if (per[0] == 0) { from = to = now.toString(); } else if (per[0] == 1) { from = now.minusDays(now.getDayOfWeek().getValue() - 1L).toString(); to = now.toString(); } else if (per[0] == 2) { from = now.withDayOfMonth(1).toString(); to = now.toString(); } else { from = expFrom == null ? now.toString() : expFrom; to = expTo == null ? now.toString() : expTo; }
            JSONObject fields = a.json("start_date", from, "end_date", to); if (cat[0] > 0) try { fields.put("category", Rules.CATEGORIES[cat[0] - 1]); } catch (JSONException e) { return; }
            long requested = ++filterRequest[0];
            a.query("expenses", fields, r -> {
                if (requested != filterRequest[0]) return; records.removeAllViews();
                JSONArray rows = r.optJSONArray("items"); int n = rows == null ? 0 : rows.length();
                LinearLayout total = ui.V(records, 2); ui.bg(total, Ui.PRIMARY, 0, 16); ui.pad(total, 14, 14);
                ui.text(total, "Total expenses", 12, 16, 0xFFD9EDFF, ui.o4);
                ui.text(total, a.money(r.optLong("total_centavos")), 26, 39, Ui.WHITE, ui.p6);
                ui.text(total, n + (n == 1 ? " expense" : " expenses") + " · newest first", 12, 16, 0xFFD9EDFF, ui.o4);
                total.setContentDescription("Filtered total: " + a.money(r.optLong("total_centavos")));
                if (n == 0) ui.text(records, "No expenses in this period.", 14, 19, Ui.MUTED, ui.o4);
                else for (int i = 0; i < n; i++) {
                    JSONObject e = rows.optJSONObject(i); if (e == null) continue;
                    LinearLayout item = ui.H(records, 8); ui.bg(item, Ui.WHITE, Ui.BORDER, 12); ui.pad(item, 12, 12);
                    LinearLayout names = ui.vbox(2); ui.grow(item, names);
                    String remarks = e.optString("remarks");
                    ui.text(names, remarks.isEmpty() ? e.optString("category") : remarks, 14, 21, Ui.INK, ui.p5).setSingleLine(true);
                    ui.text(names, shortDate(e.optString("business_date")) + " · " + e.optString("category"), 12, 16, Ui.MUTED, ui.o4);
                    ui.hug(item, ui.tv(a.money(e.optLong("amount_centavos")), 15, 22, Ui.INK, ui.p6));
                    item.setContentDescription(e.optString("reference") + " " + e.optString("category") + " " + a.money(e.optLong("amount_centavos")) + " recorded by " + e.optString("recorded_by_name"));
                }
            });
        };
        if (a.wide()) expenseForm(panes[1]);
        load[0].run();
    }
    /** Phone add-expense screen: the same form as the tablet, on its own page. */
    private void addExpensePage() {
        LinearLayout pageBody = a.page("Add expense");
        expenseForm(pageBody);
    }
    private void customPeriod(Runnable applied) {
        Dialog[] d = new Dialog[1];
        LinearLayout card = a.modal(460, d);
        ui.text(card, "Select period", 22, 33, Ui.INK, ui.p6);
        EditText s = a.operations().date(card, "Start date", expFrom == null ? a.today() : expFrom), e = a.operations().date(card, "End date", expTo == null ? a.today() : expTo);
        LinearLayout row = ui.H(card, 10); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ui.hug(row, ui.button("Cancel", Ui.OUTLINE, 46, 12, 15, () -> d[0].dismiss()));
        ui.hug(row, ui.button("Apply period", Ui.FILLED, 46, 12, 15, () -> {
            String from = OperationsUi.iso(s), to = OperationsUi.iso(e);
            try { Rules.period(from, to); } catch (IllegalArgumentException x) { a.fieldError(e, x.getMessage()); return; }
            expFrom = from; expTo = to; d[0].dismiss(); applied.run();
        }));
        a.showDialog(d[0]);
    }
    private void expenseForm(LinearLayout pane) {
        ExpenseSuggestionController state = a.expenseState(); if (state.isPending()) state.cancelPending();
        LinearLayout form = card(pane, 12);
        ui.text(form, "Add expense", 22, 33, Ui.INK, ui.p6);
        LinearLayout two = ui.H(form, 12); two.setGravity(Gravity.TOP);
        LinearLayout left = ui.vbox(0), right = ui.vbox(0); ui.grow(two, left); ui.grow(two, right);
        EditText date = a.operations().date(left, "Date", a.today(), true);
        EditText amount = a.input(right, "Amount", "", MONEY); amount.setHint("₱0.00");
        EditText remarks = a.input(form, "Remarks (optional)", "", TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        remarks.setMinHeight(ui.px(70)); remarks.setGravity(Gravity.TOP | Gravity.START);
        TextView counter = ui.tv(value(remarks).codePointCount(0, value(remarks).length()) + " / 500 characters", 12, 16, Ui.MUTED, ui.o4);
        LinearLayout rf = (LinearLayout) remarks.getParent(); rf.addView(counter, rf.indexOfChild(remarks) + 1, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout catCard = card(pane, 12);
        LinearLayout head = ui.H(catCard, 8);
        ui.grow(head, ui.tv("Category", 13, 18, Ui.MUTED, ui.o6));
        TextView suggest = ui.button("Suggest category", Ui.TONAL_BTN, 38, 12, 14, null); ui.hug(head, suggest);
        if (cachedClassifier == null) cachedClassifier = ClassifierAssets.load(a.getApplicationContext());
        if (cachedClassifier.isSynthetic()) ui.text(catCard, "Demo model trained on synthetic examples. Not a measure of station accuracy.", 12, 16, Ui.AMBER, ui.o4);
        LinearLayout result = ui.banner(catCard, Ui.INFO, "", "", true); result.setVisibility(View.GONE);
        LinearLayout choices = ui.H(catCard, 10); choices.setVisibility(View.GONE);
        String[] cats = Rules.CATEGORIES;
        int[] sel = {-1};
        if (state.category() != null) for (int i = 0; i < cats.length; i++) if (cats[i].equals(state.category())) sel[0] = i;
        List<TextView> catChips = new ArrayList<>();
        int perRow = a.wide() ? 4 : 3; LinearLayout r1 = ui.H(catCard, 8), r2 = ui.H(catCard, 8), r3 = ui.H(catCard, 8);
        TextView catError = ui.fieldError(catCard);
        boolean[] applying = {false};
        TextView[] save = {null};
        Runnable paintSave = () -> { if (save[0] != null) { ui.style(save[0], sel[0] < 0 ? Ui.DISABLED : Ui.FILLED); save[0].setEnabled(true); } };
        Runnable paintChips = () -> { for (int i = 0; i < catChips.size(); i++) ui.paintTab(catChips.get(i), i == sel[0]); paintSave.run(); };
        Runnable showState = () -> {
            String msg = state.message();
            boolean has = msg != null && !msg.isEmpty();
            result.setVisibility(has ? View.VISIBLE : View.GONE);
            if (has) {
                boolean suggested = state.canApplySuggestion();
                String titleText = suggested && state.suggestedCategory() != null ? "Suggested: " + state.suggestedCategory() : msg;
                String detail = suggested ? "Based on your remarks. Apply it or choose another category." : null;
                int kind = msg.startsWith("Suggestion applied") ? Ui.SUCCESS : (msg.startsWith("No reliable") || msg.startsWith("Category suggestion unavailable") || msg.startsWith("Remarks changed")) ? Ui.WARNING : Ui.INFO;
                ui.setBanner(result, kind, titleText, detail, true);
            }
            choices.setVisibility(state.canApplySuggestion() ? View.VISIBLE : View.GONE);
        };
        for (int i = 0; i < cats.length; i++) {
            final int index = i;
            TextView t = ui.tv(cats[i], 14, 21, Ui.INK, ui.p5); ui.pad(t, 14, 8); t.setTag(R.id.button_shape, new float[]{20}); t.setClickable(true);
            t.setOnClickListener(v -> { sel[0] = index; catError.setVisibility(View.GONE); paintChips.run(); if (!applying[0] && !("suggestion".equals(state.selectionSource()) && cats[index].equals(state.category()))) { state.chooseManually(cats[index]); showState.run(); } });
            ui.hug(i < perRow ? r1 : i < perRow * 2 ? r2 : r3, t); catChips.add(t);
        }
        catCard.removeView(catError); catCard.addView(catError);
        ui.hug(choices, ui.button("Apply suggestion", Ui.FILLED, 40, 12, 14, () -> {
            if (state.applySuggestion()) { applying[0] = true; sel[0] = Arrays.asList(cats).indexOf(state.category()); paintChips.run(); applying[0] = false; showState.run(); }
            else a.error("No reliable suggestion. Choose a category manually.");
        }));
        ui.hug(choices, ui.button("Choose manually", Ui.OUTLINE, 40, 12, 14, () -> { state.chooseManually(sel[0] < 0 ? null : cats[sel[0]]); showState.run(); }));
        state.setRemarks(value(remarks));
        remarks.addTextChangedListener(MainActivity.watcher(() -> { String s = value(remarks); counter.setText(s.codePointCount(0, s.length()) + " / 500 characters"); state.setRemarks(s); showState.run(); }));
        suggest.setOnClickListener(v -> {
            try {
                state.setRemarks(value(remarks)); ExpenseSuggestionController.Request request = state.beginRequest(); a.clearMessage();
                ui.setBanner(result, Ui.INFO, "Processing suggestion…", null, true); result.setVisibility(View.VISIBLE);
                predictions.execute(() -> { ExpenseCategoryClassifier.Result prediction = predict(request.remarks); a.runOnUiThread(() -> { if (state.complete(request, prediction)) showState.run(); }); });
            } catch (IllegalArgumentException e) { ui.setBanner(result, Ui.WARNING, e.getMessage(), null, true); result.setVisibility(View.VISIBLE); }
        });
        LinearLayout foot = a.actionRow(pane);
        ui.hug(foot, ui.tv("Recorded by " + a.recorder(), 13, 18, Ui.MUTED, ui.o4));
        a.ownerAction(foot, "Cancel", Ui.OUTLINE, this::expenses);
        save[0] = a.ownerAction(foot, "Save expense", Ui.FILLED, () -> {
            boolean bad = false;
            String iso = OperationsUi.iso(date);
            if (iso.isEmpty()) { a.fieldError(date, "Complete the required fields."); bad = true; } else { try { Rules.date(iso); } catch (IllegalArgumentException e) { a.fieldError(date, e.getMessage()); bad = true; } }
            if (sel[0] < 0) { catError.setText("Complete the required fields."); catError.setVisibility(View.VISIBLE); bad = true; }
            long cents = 0; try { cents = Rules.centavos(value(amount).replace("₱", "").replace(",", "").trim(), false); } catch (IllegalArgumentException e) { a.fieldError(amount, "Enter a valid expense amount."); bad = true; }
            final long amountCents = cents;
            try { Rules.remarks(value(remarks)); } catch (IllegalArgumentException e) { a.fieldError(remarks, e.getMessage()); bad = true; }
            if (bad) return;
            JSONObject fields = a.json("business_date", iso, "category", cats[sel[0]], "amount_centavos", cents, "remarks", value(remarks), "selection_source", state.selectionSource());
            if (state.acceptedModelVersion() != null) try { fields.put("accepted_model_version", state.acceptedModelVersion()); } catch (JSONException e) { throw new IllegalArgumentException("Invalid model version."); }
            String category = cats[sel[0]];
            a.command("create_expense", fields, r -> { expenses(); a.info("Expense saved.", category + " · " + a.money(amountCents) + " · " + shortDate(iso)); });
        });
        paintChips.run(); showState.run();
    }

    // ================================================================================================ reports (O10)
    void reports() {
        if (!owner()) return;
        a.page("Financial summary");
        LinearLayout[] panes = a.panes(380);
        a.bannerAt(panes[1], 0);
        LinearLayout period = card(panes[0], 12);
        ui.text(period, "Report period", 17, 26, Ui.INK, ui.p6);
        LocalDate now = LocalDate.parse(a.today());
        String startIso = report != null ? report.optString("start_date", now.minusDays(now.getDayOfWeek().getValue() - 1L).toString()) : now.minusDays(now.getDayOfWeek().getValue() - 1L).toString();
        String endIso = report != null ? report.optString("end_date", now.toString()) : now.toString();
        EditText[] se = new EditText[2];
        Ui.Tabs presets = ui.tabs(period, new String[]{"This week", "This month", "Custom"}, 0, false, true, 8, i -> {
            if (i == 0) { a.operations().setIso(se[0], now.minusDays(now.getDayOfWeek().getValue() - 1L).toString(), false); a.operations().setIso(se[1], now.toString(), false); }
            else if (i == 1) { a.operations().setIso(se[0], now.withDayOfMonth(1).toString(), false); a.operations().setIso(se[1], now.toString(), false); }
        });
        se[0] = a.operations().date(period, "Start date", startIso);
        se[1] = a.operations().date(period, "End date", endIso);
        LinearLayout preview = ui.V(panes[1], 14);
        long[] request = {0};
        Runnable generate = () -> {
            String s = OperationsUi.iso(se[0]), e = OperationsUi.iso(se[1]);
            try { Rules.period(s, e); } catch (IllegalArgumentException x) {
                a.fieldError(se[1], x.getMessage());
                if (report != null) { preview.removeAllViews(); reportCard(preview, report, true); }
                return;
            }
            a.clearMessage(); long requested = ++request[0];
            a.query("report", a.json("start_date", s, "end_date", e), r -> { if (requested != request[0]) return; report = r; a.reportSnapshot(r); preview.removeAllViews(); reportCard(preview, r, false); });
        };
        ui.fill(period, ui.button("Update report", Ui.FILLED, 46, 12, 15, () -> a.guarded(generate)));
        if (report != null) reportCard(preview, report, false); else a.runValidated(generate);
        presets.select(startIso.equals(now.withDayOfMonth(1).toString()) ? 1 : startIso.equals(now.minusDays(now.getDayOfWeek().getValue() - 1L).toString()) ? 0 : 2, false);
    }
    private void reportCard(LinearLayout parent, JSONObject r, boolean stale) {
        LinearLayout card = card(parent, 12);
        LinearLayout head = ui.H(card, 8);
        LinearLayout names = ui.vbox(2); ui.grow(head, names);
        ui.text(names, "Financial summary", 22, 33, Ui.INK, ui.p6);
        ui.text(names, r.optString("station", "Aqua Quenchers") + " · " + HomeUi.rangeLabel(r.optString("start_date"), r.optString("end_date")) + " · Generated " + generated(r), 13, 18, Ui.MUTED, ui.o4);
        ui.hug(head, ui.button("Export PDF", Ui.FILLED, 46, 12, 15, () -> a.guarded(this::exportPdf)));
        ui.hug(head, ui.button("Export CSV", Ui.OUTLINE, 46, 12, 15, () -> a.guarded(this::exportCsv)));
        if (stale) ui.banner(card, Ui.WARNING, "Showing the last valid report", "Fix the dates on the left to update it.", true);
        String[] labels = {"Revenue", "Payments received", "Outstanding balances", "Expenses", "Net income"};
        String[] keys = {"revenue_centavos", "payments_received_centavos", "outstanding_centavos", "expenses_centavos", "net_income_centavos"};
        LinearLayout top = ui.H(card, 12), bottom = ui.H(card, 12);
        for (int i = 0; i < 5; i++) {
            boolean dark = i == 4;
            LinearLayout t = ui.vbox(2); ui.bg(t, dark ? Ui.PRIMARY : Ui.GREY_BG, 0, 12); ui.pad(t, 14, 14);
            ui.text(t, labels[i], 12, 16, dark ? 0xFFD9EDFF : Ui.MUTED, ui.o4);
            ui.text(t, a.money(r.optLong(keys[i])), 22, 33, dark ? Ui.WHITE : Ui.INK, ui.p6);
            t.setContentDescription(labels[i] + ": " + a.money(r.optLong(keys[i])));
            ui.grow(i < 3 ? top : bottom, t);
        }
        ui.text(card, "Net income = payments received " + a.money(r.optLong("payments_received_centavos")) + " − expenses " + a.money(r.optLong("expenses_centavos")), 13, 18, Ui.MUTED, ui.o4);
    }
    private void exportCsv() {
        if (!owner()) return;
        if (report == null) throw new IllegalArgumentException("Generate a report first.");
        csv = ReportCsv.render(report.optString("station", "Aqua Quenchers"), HomeUi.rangeLabel(report.optString("start_date"), report.optString("end_date")),
                a.timestamp(report.optString("generated_at")), report.optLong("revenue_centavos"), report.optLong("payments_received_centavos"),
                report.optLong("expenses_centavos"), report.optLong("net_income_centavos"), report.optLong("outstanding_centavos"));
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/csv").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "Aqua-Quenchers-" + report.optString("start_date") + ".csv");
        a.startActivityForResult(intent, CSV_REQUEST);
    }
    private void exportPdf() { if (!owner()) return; if (report == null) throw new IllegalArgumentException("Generate a report first."); pdf = ReportPdf.render(report); a.pendingPdf(pdf); Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "Aqua-Quenchers-" + report.optString("start_date") + ".pdf"); a.startActivityForResult(intent, PDF_REQUEST); }
    private String generated(JSONObject r) { try { return OffsetDateTime.parse(r.optString("generated_at")).atZoneSameInstant(Rules.MANILA).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)); } catch (Exception e) { return a.timestamp(r.optString("generated_at")); } }
    void activityResult(int request, int result, Intent data) {
        if (request == CSV_REQUEST) { saveCsv(result, data); return; }
        if (request != PDF_REQUEST) return;
        if (result != Activity.RESULT_OK || data == null) { pdf = null; a.pendingPdf(null); return; }
        if (pdf == null) return; Uri uri = data.getData(); if (uri == null) return;
        try (OutputStream out = a.getContentResolver().openOutputStream(uri)) { if (out == null) throw new IOException(); out.write(pdf); out.flush(); }
        catch (IOException | SecurityException e) { a.error("PDF export failed. Try again."); return; }
        pdf = null; a.pendingPdf(null);
        reports();
        a.info("Report exported successfully.", report == null ? "" : "Financial summary · " + HomeUi.rangeLabel(report.optString("start_date"), report.optString("end_date")) + " · PDF");
        try { a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
        catch (ActivityNotFoundException | SecurityException ignored) { }
    }

    private void saveCsv(int result, Intent data) {
        if (result != Activity.RESULT_OK || data == null) { csv = null; return; }
        if (csv == null) return;
        Uri uri = data.getData(); if (uri == null) return;
        try (OutputStream out = a.getContentResolver().openOutputStream(uri)) { if (out == null) throw new IOException(); out.write(csv); out.flush(); }
        catch (IOException | SecurityException e) { a.error("CSV export failed. Try again."); return; }
        csv = null;
        a.info("Report exported successfully.", report == null ? "" : "Financial summary · " + HomeUi.rangeLabel(report.optString("start_date"), report.optString("end_date")) + " · CSV");
    }

    // ================================================================================================ settings
    void settings() {
        if (a.wide() && a.isOwner()) settingsPage("Gallons at station", null);
        else if (a.isOwner()) ownerPhoneSettings();
        else staffSettings();
    }
    /** Owner phone "More" tab: Expenses, Reports and Settings in one short list. */
    void more() {
        LinearLayout p = a.page("More");
        LinearLayout list = ui.V(p, 10);
        moreRow(list, "Expenses", "Record and review expenses", () -> expenses());
        moreRow(list, "Reports", "Financial summary PDF", () -> reports());
        moreRow(list, "Settings", "Gallons at station, staff, password, authenticator", () -> settings());
    }
    private void moreRow(LinearLayout parent, String title, String detail, Runnable open) {
        LinearLayout row = ui.H(parent, 12); ui.pad(row, 16, 14); ui.bg(row, Ui.WHITE, Ui.BORDER, 16);
        row.setMinimumHeight(ui.px(64)); row.setClickable(true); row.setContentDescription(title);
        LinearLayout text = ui.grow(row, ui.vbox(2));
        ui.text(text, title, 16, 22, Ui.INK, ui.p6);
        ui.text(text, detail, 13, 18, Ui.MUTED, ui.o4);
        ui.hug(row, ui.tv("›", 18, 27, Ui.MUTED, ui.p5));
        row.setOnClickListener(v -> open.run());
    }
    /** Owner phone Settings: one row per section, each opening its existing page. */
    private void ownerPhoneSettings() {
        LinearLayout p = a.page("Settings");
        LinearLayout list = ui.V(p, 10);
        moreRow(list, "Gallons at station", "Customer gallons we are holding", () -> settingsPage("Gallons at station", null));
        moreRow(list, "Staff accounts", "Add, edit or deactivate staff", () -> settingsPage("Staff accounts", null));
        moreRow(list, "Change password", "At least 15 characters", () -> settingsPage("Change password", null));
        moreRow(list, "Authenticator", "Set up or remove the code app", () -> settingsPage("Authenticator", null));
        moreRow(list, "Log out", "Sign out of this device", () -> a.endSession("Signed out."));
    }
    /** Owner settings (O11–O13): menu on the left (300), selected section on the right. */
    private void settingsPage(String section, String entity) {
        if (!owner()) return;
        LinearLayout pageBody = a.page("Staff accounts".equals(section) && entity != null ? "Edit Staff account" : section, entity == null ? "" : entity);
        if (!a.wide()) {
            LinearLayout content = ui.V(pageBody, 16);
            if ("Gallons at station".equals(section)) inventoryPane(content);
            else if ("Change password".equals(section)) passwordForm(content);
            else if ("Authenticator".equals(section)) authenticatorPane(content);
            else staffPane(content, entity);
            return;
        }
        LinearLayout[] panes = a.panes(300);
        LinearLayout menu = ui.V(panes[0], 4); ui.bg(menu, Ui.WHITE, Ui.BORDER, 16); ui.pad(menu, 8, 8);
        String[] items = {"Gallons at station", "Staff accounts", "Change password", "Authenticator"};
        for (String item : items) {
            boolean on = item.equals(section);
            LinearLayout row = ui.H(menu, 8); ui.pad(row, 14, 14); row.setMinimumHeight(ui.px(55)); row.setClickable(true); row.setContentDescription(item);
            if (on) row.setBackground(ui.shape(Ui.TONAL, 0, 10));
            ui.grow(row, ui.tv(item, 15, 22, on ? Ui.PRIMARY : Ui.INK, ui.p5));
            ui.hug(row, ui.tv("›", 18, 27, Ui.MUTED, ui.p5));
            row.setOnClickListener(v -> { if ("Staff accounts".equals(item)) staffAccounts(); else settingsPage(item, null); });
        }
        ui.fill(panes[0], ui.button("Log out", Ui.DANGER_OUTLINE, 46, 12, 15, () -> a.endSession("Signed out.")));
        a.bannerAt(panes[1], 0);
        if ("Gallons at station".equals(section)) inventoryPane(panes[1]);
        else if ("Change password".equals(section)) passwordForm(panes[1]);
        else if ("Authenticator".equals(section)) authenticatorPane(panes[1]);
        else staffPane(panes[1], entity);
    }
    void staffAccounts() { settingsPage("Staff accounts", null); }
    private void staffAccounts(String userId) { settingsPage("Staff accounts", userId); }
    private void staffPane(LinearLayout pane, String selected) {
        LinearLayout listCard = card(pane, 12);
        LinearLayout head = ui.H(listCard, 8);
        ui.grow(head, ui.tv("Staff accounts", 22, 33, Ui.INK, ui.p6));
        LinearLayout formHost = ui.V(pane, 14);
        ui.hug(head, ui.button("+  Add staff", Ui.TONAL_BTN, 40, 12, 14, () -> { formHost.removeAllViews(); staffForm(formHost, null); }));
        LinearLayout rows = ui.V(listCard, 12);
        a.query("staff", a.json(), r -> {
            JSONArray users = r.optJSONArray("items");
            if (users == null || users.length() == 0) { ui.text(rows, "No staff accounts yet.", 14, 19, Ui.MUTED, ui.o4); staffForm(formHost, null); return; }
            JSONObject chosen = null;
            for (int i = 0; i < users.length(); i++) { JSONObject u = users.optJSONObject(i); if (u != null && u.optString("user_id").equals(selected)) chosen = u; }
            // Tablets open the first account's edit form; phones wait for a tap so the list stays clean.
            if (chosen == null && a.wide()) chosen = users.optJSONObject(0);
            for (int i = 0; i < users.length(); i++) {
                JSONObject u = users.optJSONObject(i); if (u == null) continue;
                boolean on = chosen != null && u.optString("user_id").equals(chosen.optString("user_id"));
                LinearLayout item = ui.H(rows, 12); ui.bg(item, on ? Ui.TONAL : Ui.WHITE, on ? Ui.PRIMARY : Ui.BORDER, 12); ui.pad(item, 12, 12); item.setClickable(true);
                TextView av = ui.avatar(u.optString("full_name"), 40, 14.4f); if (on) av.setBackground(null);
                ui.sized(item, av, 40, 40);
                LinearLayout names = ui.vbox(1); ui.grow(item, names);
                ui.text(names, u.optString("full_name"), 15, 22, Ui.INK, ui.p6);
                ui.text(names, "@" + u.optString("username") + " · " + OperationsUi.displayContact(u.optString("contact_number")), 12, 16, Ui.MUTED, ui.o4);
                ui.hug(item, u.optBoolean("active") ? ui.chip("Active", Ui.GREEN_BG, Ui.GREEN) : ui.chip("Inactive", Ui.GREY_BG, Ui.MUTED));
                item.setContentDescription(u.optString("full_name") + " • " + u.optString("username") + " • " + (u.optBoolean("active") ? "Active" : "Inactive"));
                item.setOnClickListener(v -> staffAccounts(u.optString("user_id")));
            }
            if (chosen != null) staffForm(formHost, chosen);
        });
    }
    private void validateAccount(EditText name, EditText username, EditText contact) {
        boolean bad = false;
        if (value(name).trim().isEmpty()) { a.fieldError(name, "Complete the required fields."); bad = true; } else if (value(name).codePointCount(0, value(name).length()) > 160) { a.fieldError(name, "Use 160 characters or fewer."); bad = true; }
        if (value(username).trim().isEmpty()) { a.fieldError(username, "Complete the required fields."); bad = true; }
        if (value(contact).trim().isEmpty()) { a.fieldError(contact, "Complete the required fields."); bad = true; } else { try { Rules.contact(value(contact).replace(" ", "")); } catch (IllegalArgumentException e) { a.fieldError(contact, e.getMessage()); bad = true; } }
        if (bad) throw new FieldErrors();
    }
    /** Field messages are already shown beside their inputs. */
    private static final class FieldErrors extends RuntimeException { FieldErrors() { super(null, null, false, false); } }
    /** Figma O12: staff account form with managed MFA verification before saving. */
    private void staffForm(LinearLayout host, JSONObject user) {
        LinearLayout card = card(host, 12);
        ui.text(card, user == null ? "New staff account" : "Edit account · " + user.optString("full_name"), 17, 26, Ui.INK, ui.p6);
        LinearLayout r1 = ui.H(card, 12), r2 = ui.H(card, 12); r1.setGravity(Gravity.TOP); r2.setGravity(Gravity.TOP);
        LinearLayout c1 = ui.vbox(0), c2 = ui.vbox(0), c3 = ui.vbox(0), c4 = ui.vbox(0);
        ui.grow(r1, c1); ui.grow(r1, c2); ui.grow(r2, c3); ui.grow(r2, c4);
        EditText name = a.input(c1, "Full name", user == null ? "" : user.optString("full_name"), TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText username = a.input(c2, "Username", user == null ? "" : user.optString("username"), TEXT);
        EditText contact = a.input(c3, "Contact number", user == null ? "" : OperationsUi.displayContact(user.optString("contact_number")), InputType.TYPE_CLASS_PHONE);
        EditText temporary = a.input(c4, "Temporary password", "", SECRET);
        temporary.setHint(user == null ? "At least 15 characters" : "Leave blank to keep");
        LinearLayout status = ui.H(card, 14);
        ui.hug(status, ui.tv("Role: Staff", 13, 18, Ui.MUTED, ui.o6));
        ui.hug(status, ui.tv("Status", 13, 18, Ui.MUTED, ui.o4));
        int[] active = {user == null || user.optBoolean("active") ? 0 : 1};
        LinearLayout segHost = ui.vbox(0); status.addView(segHost, new LinearLayout.LayoutParams(ui.px(200), -2));
        ui.segmented(segHost, new String[]{"Active", "Inactive"}, active[0], i -> active[0] = i);
        LinearLayout row = a.actionRow(host);
        a.ownerAction(row, "Cancel", Ui.OUTLINE, this::staffAccounts);
        a.ownerAction(row, "Save account", Ui.FILLED, () -> {
            try { validateAccount(name, username, contact); } catch (FieldErrors e) { return; }
            String temp = value(temporary);
            if (user == null && temp.isEmpty()) { a.fieldError(temporary, "Complete the required fields."); return; }
            if (!temp.isEmpty()) try { Rules.password(temp); } catch (IllegalArgumentException e) { a.fieldError(temporary, e.getMessage()); return; }
            JSONObject payload = a.json("operation", user == null ? "create" : "update", "full_name", value(name).trim(), "username", value(username).trim(), "contact_number", value(contact).replace(" ", ""), "active", active[0] == 0);
            try { if (user != null) payload.put("user_id", user.optString("user_id")); if (!temp.isEmpty()) payload.put("temporary_password", temp); } catch (JSONException e) { throw new IllegalArgumentException("Invalid account details."); }
            String summary = value(name).trim() + " · @" + value(username).trim() + " · " + (active[0] == 0 ? "Active" : "Inactive");
            confirmManagedMfa("this account change", () -> a.accountCommand(payload, r -> { staffAccounts(r.optString("user_id")); a.info("Staff account saved", summary); }));
        });
    }
    /** Figma O12b/O12c: "Verify it’s you" with six code boxes. */
    private void confirmManagedMfa(String purpose, Runnable verified) {
        Dialog[] d = new Dialog[1];
        LinearLayout card = a.modal(460, d);
        ui.text(card, "Verify it’s you", 22, 33, Ui.INK, ui.p6);
        ui.text(card, "Enter the 6-digit code from your authenticator app to save " + purpose + ".", 14, 19, Ui.MUTED, ui.o4);
        LinearLayout boxes = ui.H(card, 8); boxes.setGravity(Gravity.CENTER);
        EditText[] digits = new EditText[6];
        for (int i = 0; i < 6; i++) {
            final int index = i;
            EditText e = new EditText(a); e.setSaveEnabled(false); e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setGravity(Gravity.CENTER); e.setTypeface(ui.p5); e.setTextColor(Ui.INK); e.setIncludeFontPadding(false); ui.size(e, 24, 36); e.setPadding(0, 0, 0, 0);
            e.setBackground(ui.shape(Ui.WHITE, Ui.FIELD, 10)); e.setContentDescription("Verification code digit " + (i + 1));
            boxes.addView(e, new LinearLayout.LayoutParams(ui.px(54), ui.px(58)));
            e.addTextChangedListener(MainActivity.watcher(() -> {
                // A pasted or multi-digit entry fills this box and the boxes after it, one digit each.
                String typed = e.getText().toString().replaceAll("[^0-9]", "");
                if (typed.length() > 1) {
                    for (int k = 0; k < typed.length() && index + k < 6; k++) digits[index + k].setText(String.valueOf(typed.charAt(k)));
                    digits[Math.min(index + typed.length(), 5)].requestFocus();
                    return;
                }
                if (typed.length() == 1 && index < 5) digits[index + 1].requestFocus();
            }));
            digits[i] = e;
        }
        TextView err = ui.fieldError(card); err.setGravity(Gravity.CENTER);
        LinearLayout row = ui.H(card, 10); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ui.hug(row, ui.button("Cancel", Ui.OUTLINE, 46, 12, 15, () -> d[0].dismiss()));
        ui.hug(row, ui.button("Verify and save", Ui.FILLED, 46, 12, 15, () -> {
            StringBuilder code = new StringBuilder(); for (EditText e : digits) code.append(e.getText().toString());
            for (EditText e : digits) e.setText("");
            if (!code.toString().matches("[0-9]{6}")) { err.setText("Enter a valid verification code."); err.setVisibility(View.VISIBLE); for (EditText e : digits) e.setBackground(ui.shape(Ui.WHITE, Ui.DANGER, 10)); digits[0].requestFocus(); return; }
            d[0].dismiss();
            verifyMfa(code.toString(), verified);
        }));
        a.showDialog(d[0]);
        digits[0].requestFocus();
    }
    private void verifyMfa(String code, Runnable verified) { api("GET", "/auth/v1/user", null, user -> { JSONArray factors = user.optJSONArray("factors"); String id = ""; if (factors != null) for (int i = 0; i < factors.length(); i++) { JSONObject factor = factors.optJSONObject(i); if ("verified".equals(factor.optString("status")) && "totp".equals(factor.optString("factor_type"))) { id = factor.optString("id"); break; } } if (id.isEmpty()) { a.error("Set up the authenticator app first in Settings → Authenticator."); return; } String path = "/auth/v1/factors/" + id; api("POST", path + "/challenge", a.json(), challenge -> verifyCode(path, challenge.optString("id"), code, verified)); }); }
    private void verifyCode(String path, String challenge, String code, Runnable verified) { Backend expected = a.backend(); expected.call(path + "/verify", a.json("challenge_id", challenge, "code", code), session -> { if (expected != a.backend() || a.isDestroyed() || a.isFinishing()) return; if (session.optString("access_token").isEmpty() && session.optJSONObject("session") == null) { a.error("Verification was not confirmed. Try again."); return; } a.acceptTokens(session); verified.run(); }, e -> { if (expected != a.backend() || a.isDestroyed() || a.isFinishing()) return; if (e.status == 401) { a.endSession("Session expired. Sign in again."); return; } a.error(e.status == 400 || e.status == 403 || e.status == 422 ? "Enter a valid verification code." : e.getMessage()); }); }

    // ================================================================================================ password (O13 / T3)
    // ================================================================================================ authenticator (Owner managed MFA setup)
    /** Owner sets up the authenticator app on the tablet by scanning a QR code or typing the setup key. */
    private void authenticatorPane(LinearLayout p) { authenticatorStatus(ui.V(p, 12)); }
    private void authenticatorStatus(LinearLayout box) {
        box.removeAllViews();
        LinearLayout card = ui.card(box, 12, a.wide() ? 18 : 16);
        ui.text(card, "Authenticator app", 22, 33, Ui.INK, ui.p6);
        TextView status = ui.tv("Checking setup...", 14, 19, Ui.MUTED, ui.o4);
        card.addView(status, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout actions = ui.H(card, 10); actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        api("GET", "/auth/v1/user", null, user -> {
            if (hasVerifiedTotp(user)) {
                status.setText("Authenticator app set up and active.");
                ui.hug(actions, ui.button("Remove authenticator", Ui.DANGER_OUTLINE, 46, 12, 15, () -> removeAuthenticator(box)));
                return;
            }
            status.setText("Authenticator app not set up yet. Set it up before adding staff accounts or changing the password.");
            ui.hug(actions, ui.button("Set up authenticator", Ui.FILLED, 46, 12, 15, () -> startEnrollment(box)));
        });
    }
    /** Removal needs a current code, so someone holding an unlocked session cannot quietly remove the second factor. */
    private void removeAuthenticator(LinearLayout box) {
        confirmManagedMfa("this change", () -> api("GET", "/auth/v1/user", null, user -> {
            String id = verifiedTotpId(user);
            if (id.isEmpty()) { a.error("No authenticator is set up."); authenticatorStatus(box); return; }
            api("DELETE", "/auth/v1/factors/" + id, a.json(), r -> {
                a.info("Authenticator removed. Set it up again before changing staff accounts or the password.");
                authenticatorStatus(box);
            });
        }));
    }
    private static String verifiedTotpId(JSONObject user) {
        JSONArray factors = user.optJSONArray("factors");
        if (factors != null) for (int i = 0; i < factors.length(); i++) {
            JSONObject f = factors.optJSONObject(i);
            if (f != null && "verified".equals(f.optString("status")) && "totp".equals(f.optString("factor_type"))) return f.optString("id");
        }
        return "";
    }
    private static boolean hasVerifiedTotp(JSONObject user) { return !verifiedTotpId(user).isEmpty(); }
    private void startEnrollment(LinearLayout box) {
        api("POST", "/auth/v1/factors", a.json("factor_type", "totp", "friendly_name", "Aqua Quenchers Owner"), factor -> showEnrollment(box, factor));
    }
    private void showEnrollment(LinearLayout box, JSONObject factor) {
        String id = factor.optString("id");
        JSONObject totp = factor.optJSONObject("totp");
        String secret = totp == null ? "" : totp.optString("secret");
        String uri = totp == null ? "" : totp.optString("uri");
        if (id.isEmpty() || secret.isEmpty() || uri.isEmpty()) { a.error("The authenticator could not be created. Try again."); return; }
        box.removeAllViews();
        LinearLayout card = ui.card(box, 12, a.wide() ? 18 : 16);
        ui.text(card, "Add to your phone", 22, 33, Ui.INK, ui.p6);
        ui.text(card, "Open Google Authenticator, Authy or a similar app. Tap Add account and scan this code. Then type the 6-digit number the app shows.", 14, 19, Ui.MUTED, ui.o4);
        android.graphics.Bitmap qr = qrBitmap(uri);
        if (qr != null) {
            ImageView image = new ImageView(a);
            image.setImageBitmap(qr);
            image.setContentDescription("Authenticator setup QR code");
            card.addView(image, new LinearLayout.LayoutParams(ui.px(240), ui.px(240)));
        }
        ui.text(card, "Can't scan? Choose \"Enter a setup key\" in the app and type this key:", 13, 18, Ui.MUTED, ui.o4);
        ui.text(card, secret, 16, 22, Ui.INK, ui.p5);
        LinearLayout copyRow = ui.H(card, 10);
        ui.hug(copyRow, ui.button("Copy key", Ui.OUTLINE, 44, 12, 14, () -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Authenticator setup key", secret));
            a.info("Setup key copied.");
        }));
        EditText code = a.input(card, "6-digit code from the app", "", InputType.TYPE_CLASS_NUMBER);
        LinearLayout row = ui.H(card, 10); row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        ui.hug(row, ui.button("Cancel", Ui.OUTLINE, 46, 12, 15, () -> cancelEnrollment(box, id)));
        ui.hug(row, ui.button("Confirm", Ui.FILLED, 46, 12, 15, () -> {
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9]{6}")) { a.error("Enter the 6-digit code from the app."); return; }
            String path = "/auth/v1/factors/" + id;
            api("POST", path + "/challenge", a.json(), challenge -> verifyCode(path, challenge.optString("id"), value, () -> {
                a.info("Authenticator set up.");
                authenticatorStatus(box);
            }));
        }));
    }
    /** Removes the unfinished factor so a new setup key can be generated next time. */
    private void cancelEnrollment(LinearLayout box, String id) {
        api("DELETE", "/auth/v1/factors/" + id, a.json(), r -> authenticatorStatus(box));
    }
    /** Builds a QR bitmap from the otpauth:// URI. The secret is never logged. */
    private static android.graphics.Bitmap qrBitmap(String text) {
        try {
            com.google.zxing.common.BitMatrix matrix = new com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, 512, 512);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888);
            for (int x = 0; x < 512; x++) for (int y = 0; y < 512; y++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            return bitmap;
        } catch (Exception e) { return null; }
    }
    private void password() { if (a.wide() && a.isOwner()) settingsPage("Change password", null); else { LinearLayout p = a.page("Change password"); passwordForm(p); } }
    private void passwordForm(LinearLayout p) {
        boolean ow = a.wide();
        LinearLayout card = ui.card(p, 12, ow ? 18 : 16);
        if (ow) ui.text(card, "Change password", 22, 33, Ui.INK, ui.p6);
        EditText current = a.input(card, "Current password", "", SECRET), next = a.input(card, "New password", "", SECRET);
        LinearLayout nf = (LinearLayout) next.getParent();
        nf.addView(ui.tv("At least 15 characters. A long passphrase works well.", 12, 16, Ui.MUTED, ui.o4), new LinearLayout.LayoutParams(-1, -2));
        EditText confirmation = a.input(card, "Confirm new password", "", SECRET);
        TextView[] saveRef = {null};
        Runnable paint = () -> { if (saveRef[0] != null) { boolean ready = !value(current).isEmpty() && value(next).codePointCount(0, value(next).length()) >= 15 && value(next).equals(value(confirmation)); ui.style(saveRef[0], ready ? Ui.FILLED : Ui.DISABLED); saveRef[0].setEnabled(true); } };
        for (EditText e : new EditText[]{current, next, confirmation}) e.addTextChangedListener(MainActivity.watcher(paint));
        Runnable save = () -> {
            String currentPassword = value(current), newPassword = value(next), confirmed = value(confirmation); boolean bad = false;
            if (currentPassword.isEmpty()) { a.fieldError(current, "Enter your current password."); bad = true; }
            try { Rules.password(newPassword); } catch (IllegalArgumentException e) { a.fieldError(next, e.getMessage()); bad = true; }
            if (!newPassword.equals(confirmed)) { a.fieldError(confirmation, "New passwords do not match."); bad = true; }
            if (bad) return; a.clearMessage();
            a.query("me", a.json(), me -> {
                Backend expected = a.backend();
                expected.call("POST", "/functions/v1/username-login", a.json("username", me.optString("username"), "password", currentPassword), session -> {
                    if (expected != a.backend() || a.isDestroyed()) return;
                    JSONObject identity = session.optJSONObject("user"); if (identity == null || !me.optString("user_id").equals(identity.optString("id"))) { a.fieldError(current, "Current password is incorrect."); return; }
                    a.acceptTokens(session);
                    api("GET", "/auth/v1/user", null, user -> {
                        if (!me.optString("user_id").equals(user.optString("id"))) { a.endSession("Access denied. Sign in again."); return; }
                        Runnable update = () -> api("PUT", "/auth/v1/user", a.json("password", newPassword), saved -> {
                            if (!me.optString("user_id").equals(saved.optString("id"))) { a.error("Password update was not confirmed. Sign in again to check the outcome."); return; }
                            password(); a.info("Password updated.");
                        });
                        JSONArray factors = user.optJSONArray("factors"); boolean requiresMfa = false;
                        if (factors != null) for (int i = 0; i < factors.length(); i++) { JSONObject factor = factors.optJSONObject(i); if (factor != null && "verified".equals(factor.optString("status"))) { requiresMfa = true; break; } }
                        if (requiresMfa) confirmManagedMfa("the new password", update); else update.run();
                    });
                }, e -> { if (expected == a.backend() && !a.isDestroyed()) { if (e.status == 400 || e.status == 401) a.fieldError(current, "Current password is incorrect."); else a.error(e.getMessage()); } });
            });
        };
        if (ow) {
            LinearLayout row = a.actionRow(p);
            a.ownerAction(row, "Cancel", Ui.OUTLINE, this::settings);
            saveRef[0] = a.ownerAction(row, "Save password", Ui.FILLED, save);
            TextView foot = ui.tv("Forgot your password? The administrator verifies and resets it outside the app.", 13, 18, Ui.MUTED, ui.o4); foot.setGravity(Gravity.CENTER); ui.fill(p, foot);
        } else {
            saveRef[0] = a.mainAction("Save password", Ui.FILLED, save);
            a.actionNote("Forgot it? The administrator verifies and resets it outside the app");
        }
        paint.run();
    }

    // ================================================================================================ Staff settings (T1, T2)
    private void staffSettings() {
        LinearLayout p = a.page("Settings");
        JSONObject me = a.profile();
        LinearLayout head = ui.card(p, 12, 16);
        LinearLayout r = ui.H(head, 14);
        String name = me == null ? "" : me.optString("full_name");
        ui.sized(r, ui.avatar(name, 56, 20.16f), 56, 56);
        LinearLayout names = ui.vbox(2); ui.grow(r, names);
        ui.text(names, name, 19, 28, Ui.INK, ui.p6);
        ui.text(names, (a.isOwner() ? "Owner" : "Staff") + " · @" + (me == null ? "" : me.optString("username")) + " · " + OperationsUi.displayContact(me == null ? "" : me.optString("contact_number")), 12, 16, Ui.MUTED, ui.o4);
        LinearLayout menu = ui.V(p, 0); ui.bg(menu, Ui.WHITE, Ui.BORDER, 16); ui.pad(menu, 4, 4);
        boolean ownerPhone = a.isOwner();
        String[] items = ownerPhone ? new String[]{"Expenses", "Reports", "Gallons at station", "Staff accounts", "Change password", "Edit profile"} : new String[]{"Edit profile", "Change password", "Pending gallons", "Collect payments"};
        Runnable[] actions = ownerPhone ? new Runnable[]{this::expenses, this::reports, this::inventory, this::staffAccounts, this::password, this::profile} : new Runnable[]{this::profile, this::password, a::openPending, a::openPayments};
        for (int i = 0; i < items.length; i++) {
            final Runnable go = actions[i];
            LinearLayout row = ui.H(menu, 8); ui.pad(row, 16, 16); row.setMinimumHeight(ui.px(62)); row.setClickable(true); row.setContentDescription(items[i]);
            ui.grow(row, ui.tv(items[i], 16, 24, Ui.INK, ui.p5));
            ui.hug(row, ui.tv("›", 20, 30, Ui.MUTED, ui.p5));
            row.setOnClickListener(v -> a.guarded(go));
        }
        ui.fill(p, ui.button("Log out", Ui.DANGER_OUTLINE, 52, 14, 16, () -> a.endSession("Signed out.")));
    }
    private void profile() { a.page("Edit profile"); a.query("me", a.json(), this::editProfile); }
    private void editProfile(JSONObject me) {
        LinearLayout p = a.page("Edit profile", me.optString("user_id"));
        LinearLayout card = ui.card(p, 16, 16);
        EditText name = a.input(card, "Full name", me.optString("full_name"), TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS), username = a.input(card, "Username", me.optString("username"), TEXT), contact = a.input(card, "Contact number", OperationsUi.displayContact(me.optString("contact_number")), InputType.TYPE_CLASS_PHONE);
        a.mainAction("Save profile", Ui.FILLED, () -> {
            try { validateAccount(name, username, contact); } catch (FieldErrors e) { return; }
            String n = value(name).trim(), u = value(username).trim();
            a.command("update_profile", a.json("full_name", n, "username", u, "contact_number", value(contact).replace(" ", "")), r -> { editProfile(r); a.info("Profile updated.", n + " · @" + u); });
        });
    }
}
