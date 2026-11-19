package com.aquaquenchers.bms;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.json.JSONObject;

/** Renders an already authorized report snapshot; no reads, writes or calculations. */
final class ReportPdf {
    private static final int PAGE_W = 595, PAGE_H = 842, LEFT = 40, RIGHT = PAGE_W - 40;
    private static final int BRAND = 0xFF2C6396, BRAND_DARK = 0xFF1F4D75, INK = 0xFF0F172A, MUTED = 0xFF64748B, LINE = 0xFFE2E8F0, ZEBRA = 0xFFF1F6FB;

    private ReportPdf() {}

    static byte[] render(JSONObject report) {
        PdfDocument document = new PdfDocument();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create());
            Canvas canvas = page.getCanvas();
            drawHeader(canvas, report);
            drawSummaryTable(canvas, report);
            drawFooter(canvas, report);
            document.finishPage(page);
            document.writeTo(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF could not be generated.");
        } finally {
            document.close();
        }
    }

    private static void drawHeader(Canvas c, JSONObject report) {
        Paint band = new Paint();
        band.setColor(BRAND);
        c.drawRect(0, 0, PAGE_W, 130, band);
        band.setColor(BRAND_DARK);
        c.drawRect(0, 130, PAGE_W, 136, band);

        Paint title = text(26, Color.WHITE, true);
        c.drawText(orDefault(report.optString("station"), "Aqua Quenchers"), LEFT, 62, title);
        Paint sub = text(14, 0xFFDCEBF7, false);
        c.drawText("Financial summary", LEFT, 88, sub);
        Paint period = text(12, 0xFFDCEBF7, false);
        c.drawText(report.optString("start_date") + " to " + report.optString("end_date"), LEFT, 110, period);
    }

    private static void drawSummaryTable(Canvas c, JSONObject report) {
        int top = 190, rowH = 44, headH = 36;
        Paint fill = new Paint();

        // Column header
        fill.setColor(BRAND_DARK);
        c.drawRect(LEFT, top, RIGHT, top + headH, fill);
        Paint headLabel = text(12, Color.WHITE, true);
        c.drawText("ITEM", LEFT + 14, top + 23, headLabel);
        Paint headAmount = text(12, Color.WHITE, true);
        headAmount.setTextAlign(Paint.Align.RIGHT);
        c.drawText("AMOUNT", RIGHT - 14, top + 23, headAmount);

        String[][] rows = {
                {"Revenue", Rules.money(report.optLong("revenue_centavos")), "plain"},
                {"Payments received", Rules.money(report.optLong("payments_received_centavos")), "plain"},
                {"Expenses", Rules.money(report.optLong("expenses_centavos")), "plain"},
                {"Net income", Rules.money(report.optLong("net_income_centavos")), "total"},
                {"Outstanding balances", Rules.money(report.optLong("outstanding_centavos")), "plain"},
        };

        int y = top + headH;
        for (int i = 0; i < rows.length; i++) {
            boolean total = "total".equals(rows[i][2]);
            fill.setColor(total ? 0xFFE0EEF9 : (i % 2 == 1 ? ZEBRA : Color.WHITE));
            c.drawRect(LEFT, y, RIGHT, y + rowH, fill);

            Paint label = text(total ? 15 : 14, INK, total);
            c.drawText(rows[i][0], LEFT + 14, y + 28, label);

            Paint amount = text(total ? 15 : 14, total ? BRAND_DARK : INK, total);
            amount.setTextAlign(Paint.Align.RIGHT);
            c.drawText(rows[i][1], RIGHT - 14, y + 28, amount);
            y += rowH;
        }

        // Row separators and outer border
        Paint line = new Paint();
        line.setColor(LINE);
        line.setStrokeWidth(1f);
        for (int i = 0; i <= rows.length; i++) {
            float yy = top + headH + i * rowH;
            c.drawLine(LEFT, yy, RIGHT, yy, line);
        }
        c.drawLine(LEFT, top, LEFT, y, line);
        c.drawLine(RIGHT, top, RIGHT, y, line);

        Paint note = text(12, MUTED, false);
        c.drawText("Net income = Payments received - Expenses", LEFT, y + 26, note);
    }

    private static void drawFooter(Canvas c, JSONObject report) {
        Paint rule = new Paint();
        rule.setColor(LINE);
        c.drawLine(LEFT, PAGE_H - 60, RIGHT, PAGE_H - 60, rule);
        Paint generated = text(11, MUTED, false);
        c.drawText("Generated " + Rules.timestamp(report.optString("generated_at")), LEFT, PAGE_H - 40, generated);
        Paint confidential = text(11, MUTED, false);
        confidential.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Owner use only", RIGHT, PAGE_H - 40, confidential);
    }

    private static Paint text(float size, int color, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.create("sans-serif", Typeface.NORMAL));
        return p;
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }
}
