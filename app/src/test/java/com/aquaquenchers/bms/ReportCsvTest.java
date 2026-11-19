package com.aquaquenchers.bms;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class ReportCsvTest {
    @Test
    public void amountsUseTwoDecimalsWithoutSymbols() {
        assertEquals("1234.50", ReportCsv.amount(123450));
        assertEquals("0.05", ReportCsv.amount(5));
        assertEquals("-20.00", ReportCsv.amount(-2000));
    }

    @Test
    public void escapesCommasQuotesAndLineBreaks() {
        assertEquals("plain", ReportCsv.escape("plain"));
        assertEquals("\"Fuel, gas\"", ReportCsv.escape("Fuel, gas"));
        assertEquals("\"say \"\"hi\"\"\"", ReportCsv.escape("say \"hi\""));
        assertEquals("\"a\nb\"", ReportCsv.escape("a\nb"));
    }

    @Test
    public void rendersSummaryRowsWithNetIncome() {
        String csv = new String(ReportCsv.render("Aqua Quenchers", "1–15 Nov 2026", "19 Nov 2026",
                500000, 300000, 120000, 180000, 50000), StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertEquals("Item,Amount (PHP)", lines[5]);
        assertEquals("Revenue,5000.00", lines[6]);
        assertEquals("Payments received,3000.00", lines[7]);
        assertEquals("Expenses,1200.00", lines[8]);
        assertEquals("Net income,1800.00", lines[9]);
        assertEquals("Outstanding balances,500.00", lines[10]);
    }
}
