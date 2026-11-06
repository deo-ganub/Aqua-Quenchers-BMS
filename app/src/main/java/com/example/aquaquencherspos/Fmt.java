package com.example.aquaquencherspos;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Peso, gallon and Asia/Manila date formatting used on every screen. */
public final class Fmt {

    static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd", Locale.ENGLISH);
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter EVENT =
            DateTimeFormatter.ofPattern("dd MMM · h:mm a", Locale.ENGLISH);

    private Fmt() {
    }

    static String peso(double amount) {
        String digits = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US))
                .format(Math.abs(amount));
        return (amount < 0 ? "−₱" : "₱") + digits;
    }

    static String gallons(int count) {
        return count + (count == 1 ? " gallon" : " gallons");
    }

    static LocalDate today() {
        return LocalDate.now(MANILA);
    }

    static LocalDate monthStart() {
        return today().withDayOfMonth(1);
    }

    static String date(LocalDate d) {
        return d == null ? "" : LONG.format(d);
    }

    static String date(String iso) {
        return iso == null || iso.isEmpty() || "null".equals(iso) ? "" : LONG.format(LocalDate.parse(iso));
    }

    static String shortDate(String iso) {
        return iso == null || iso.isEmpty() || "null".equals(iso) ? "" : SHORT.format(LocalDate.parse(iso));
    }

    /** "01–15 Oct 2026" when both dates share a month, otherwise the two full dates. */
    static String range(LocalDate start, LocalDate end) {
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return DAY.format(start) + "–" + LONG.format(end);
        }
        return LONG.format(start) + " – " + LONG.format(end);
    }

    private static OffsetDateTime parseStamp(String iso) {
        return OffsetDateTime.parse(iso.replace(' ', 'T'));
    }

    static String stamp(String iso) {
        return iso == null || iso.isEmpty() ? "" : STAMP.format(parseStamp(iso).atZoneSameInstant(MANILA));
    }

    static String event(String iso) {
        return iso == null || iso.isEmpty() ? "" : EVENT.format(parseStamp(iso).atZoneSameInstant(MANILA));
    }

    /** Displays 09175550125 as 0917 555 0125. */
    static String contact(String digits) {
        if (digits == null || digits.length() != 11) {
            return digits == null ? "" : digits;
        }
        return digits.substring(0, 4) + " " + digits.substring(4, 7) + " " + digits.substring(7);
    }
}
