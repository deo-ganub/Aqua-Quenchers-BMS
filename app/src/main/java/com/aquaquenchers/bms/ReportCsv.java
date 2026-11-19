package com.aquaquenchers.bms;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/** Builds the financial summary as CSV from an already authorized report snapshot; no reads or writes. */
final class ReportCsv {
    private ReportCsv() {}

    static byte[] render(String station, String period, String generated, long revenueCentavos, long paymentsCentavos,
                         long expensesCentavos, long netIncomeCentavos, long outstandingCentavos) {
        StringBuilder csv = new StringBuilder();
        row(csv, "Aqua Quenchers financial summary");
        row(csv, "Station", station);
        row(csv, "Period", period);
        row(csv, "Generated", generated);
        row(csv);
        row(csv, "Item", "Amount (PHP)");
        row(csv, "Revenue", amount(revenueCentavos));
        row(csv, "Payments received", amount(paymentsCentavos));
        row(csv, "Expenses", amount(expensesCentavos));
        row(csv, "Net income", amount(netIncomeCentavos));
        row(csv, "Outstanding balances", amount(outstandingCentavos));
        row(csv);
        row(csv, "Net income = Payments received - Expenses");
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Centavos to a plain decimal such as 1234.50 or -20.00 (no currency symbol or grouping). */
    static String amount(long centavos) {
        return BigDecimal.valueOf(centavos, 2).toPlainString();
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(escape(cells[i]));
        }
        csv.append("\r\n");
    }

    /** RFC 4180 quoting: wrap in quotes when the cell has a comma, quote or line break; double inner quotes. */
    static String escape(String cell) {
        String value = cell == null ? "" : cell;
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
