package com.example.aquaquencherspos;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.LinearLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

/** Financial summary, Report Preview and PDF export (A-10). */
public final class ReportScreens {

    private ReportScreens() {
    }

    /** Figma "04 · Reports". */
    public static class Reports extends BaseScreen {
        private Field start;
        private Field end;
        private LinearLayout resultHolder;
        /** Kept on rejection: the previous valid report stays visible. */
        private JSONObject lastReport;

        @Override
        String section() {
            return "Reports";
        }

        @Override
        void build() {
            head("Financial summary", "Revenue, payments, customer balances, expenses and net income");
            LinearLayout period = Ui.titledCard(c, "Select period");
            FlowGrid form = FlowGrid.form(c);
            start = Field.date(c, "Start date", Fmt.monthStart());
            end = Field.date(c, "End date", Fmt.today());
            form.addView(start);
            form.addView(end);
            period.addView(Ui.spaced(c, form, 16));
            period.addView(WrapRow.of(c, Ui.primary(c, "Generate Report", v -> generate())));
            page.addView(period);
            resultHolder = new LinearLayout(c);
            resultHolder.setOrientation(LinearLayout.VERTICAL);
            page.addView(resultHolder);
            if (lastReport != null) {
                render(lastReport);
            } else {
                generate();
            }
        }

        private void generate() {
            LocalDate from = start.dateValue();
            LocalDate to = end.dateValue();
            if (to.isBefore(from)) {
                Dialogs.correct(c, "The end date cannot be earlier than the start date.",
                        "Start: " + Fmt.date(from) + " · End: " + Fmt.date(to)
                                + "\nCorrect the period. The previous valid financial summary is retained.");
                return;
            }
            call("financial_summary", params("p_start", from.toString(), "p_end", to.toString()), report -> {
                lastReport = report;
                render(report);
            });
        }

        private void render(JSONObject r) {
            resultHolder.removeAllViews();
            LinearLayout card = Ui.titledCard(c, "Consolidated financial summary");
            FlowGrid stats = FlowGrid.stats(c, 4);
            stats.addView(Ui.stat(c, "Revenue", Fmt.peso(d(r, "revenue"))));
            stats.addView(Ui.stat(c, "Payments received", Fmt.peso(d(r, "payments_received"))));
            stats.addView(Ui.stat(c, "Expenses", Fmt.peso(d(r, "expenses"))));
            stats.addView(Ui.stat(c, "Net income", Fmt.peso(d(r, "net_income"))));
            card.addView(Ui.spaced(c, stats, 12));
            card.addView(Ui.spaced(c, Ui.ink(c, "Outstanding customer balances: "
                    + Fmt.peso(d(r, "outstanding_balances"))), 12));
            card.addView(Ui.spaced(c, Ui.body(c, "Net income = " + Fmt.peso(d(r, "payments_received")) + " − "
                    + Fmt.peso(d(r, "expenses")) + " = " + Fmt.peso(d(r, "net_income"))
                    + ". Revenue is displayed separately from cash collected."), 16));
            card.addView(WrapRow.of(c, Ui.secondary(c, "Report Preview", v -> {
                ReportPreview preview = new ReportPreview();
                Bundle b = new Bundle();
                b.putString("report", r.toString());
                preview.setArguments(b);
                go(preview);
            })));
            resultHolder.addView(card);
        }
    }

    /** Figma "05 · Report Preview". */
    public static class ReportPreview extends BaseScreen {
        private JSONObject report;

        @Override
        String section() {
            return "Reports";
        }

        @Override
        void build() {
            try {
                report = new JSONObject(requireArguments().getString("report"));
            } catch (JSONException e) {
                back();
                return;
            }
            head("Report Preview", "Review the selected report before exporting");
            LinearLayout card = Ui.titledCard(c, "Aqua Quenchers · Financial Report");
            card.addView(Ui.spaced(c, Ui.ink(c, period(report) + " · Generated: " + Fmt.date(s(report, "generated_on"))), 12));
            Table t = new Table(c, new String[]{"Measure", "Amount"}, new float[]{457, 447});
            for (String[] row : measures(report)) {
                t.addRow(null, row, null);
            }
            card.addView(Ui.spaced(c, t, 8));
            card.addView(Ui.spaced(c, Ui.body(c, "Net income = payments received less expenses."), 16));
            card.addView(WrapRow.of(c,
                    Ui.secondary(c, "Export PDF", v -> export()),
                    Ui.secondary(c, "Back to Reports", v -> back())));
            page.addView(card);
        }

        private void export() {
            try {
                Uri file = writePdf(c, report);
                Dialogs.confirmChoice(c, "Report exported successfully.",
                        "The exported PDF includes the business name, covered period and generation date. "
                                + "It is saved in Downloads.",
                        "Open PDF", () -> {
                            Intent open = new Intent(Intent.ACTION_VIEW);
                            open.setDataAndType(file, "application/pdf");
                            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try {
                                startActivity(open);
                            } catch (android.content.ActivityNotFoundException e) {
                                Dialogs.correct(c, "No PDF viewer found.", "The report is saved in Downloads.");
                            }
                        },
                        "Back to Reports", this::back);
            } catch (IOException e) {
                Dialogs.correct(c, "The report was not exported.", "Please try again. Nothing else was changed.");
            }
        }
    }

    private static String period(JSONObject r) {
        return "Covered period: " + Fmt.range(LocalDate.parse(BaseScreen.s(r, "start_date")),
                LocalDate.parse(BaseScreen.s(r, "end_date")));
    }

    private static String[][] measures(JSONObject r) {
        return new String[][]{
                {"Revenue", Fmt.peso(BaseScreen.d(r, "revenue"))},
                {"Payments received", Fmt.peso(BaseScreen.d(r, "payments_received"))},
                {"Outstanding balances", Fmt.peso(BaseScreen.d(r, "outstanding_balances"))},
                {"Expenses", Fmt.peso(BaseScreen.d(r, "expenses"))},
                {"Net income", Fmt.peso(BaseScreen.d(r, "net_income"))},
        };
    }

    /** Writes the same summary as the preview to Downloads (B-05.3). */
    private static Uri writePdf(Context c, JSONObject r) throws IOException {
        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(595, 842, 1).create());
        Canvas canvas = page.getCanvas();
        Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
        title.setTypeface(Ui.semibold(c));
        title.setTextSize(20);
        title.setColor(Ui.INK);
        Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        body.setTypeface(Ui.regular(c));
        body.setTextSize(12);
        body.setColor(Ui.BODY);
        Paint value = new Paint(body);
        value.setColor(Ui.INK);
        Paint heading = new Paint(body);
        heading.setTypeface(Ui.medium(c));
        heading.setColor(Ui.INK);
        Paint line = new Paint();
        line.setColor(Ui.BORDER);

        float left = 48;
        float y = 72;
        canvas.drawText("Aqua Quenchers · Financial Report", left, y, title);
        y += 26;
        canvas.drawText(period(r), left, y, body);
        y += 18;
        canvas.drawText("Generated: " + Fmt.date(BaseScreen.s(r, "generated_on")), left, y, body);
        y += 34;
        canvas.drawText("Measure", left, y, heading);
        canvas.drawText("Amount", 340, y, heading);
        y += 10;
        canvas.drawLine(left, y, 547, y, line);
        for (String[] row : measures(r)) {
            y += 26;
            canvas.drawText(row[0], left, y, value);
            canvas.drawText(row[1], 340, y, value);
            y += 10;
            canvas.drawLine(left, y, 547, y, line);
        }
        y += 28;
        canvas.drawText("Net income = payments received less expenses.", left, y, body);
        pdf.finishPage(page);

        String name = "AquaQuenchers_Financial_Report_" + BaseScreen.s(r, "start_date") + "_"
                + BaseScreen.s(r, "end_date") + ".pdf";
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
        ContentResolver resolver = c.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            pdf.close();
            throw new IOException("Downloads is not available.");
        }
        try (OutputStream out = resolver.openOutputStream(uri)) {
            if (out == null) {
                throw new IOException("Downloads is not available.");
            }
            pdf.writeTo(out);
        } catch (IOException e) {
            resolver.delete(uri, null, null);
            throw e;
        } finally {
            pdf.close();
        }
        return uri;
    }
}
