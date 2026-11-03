package com.aquaquenchers.bms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Client checks improve input feedback; PostgreSQL remains authoritative. */
public final class Rules {
    public static final ZoneId MANILA = ZoneId.of("Asia/Manila");
    public static final String[] CATEGORIES = {"Fuel","Electricity","Water","Maintenance","Supplies","Salary","Others"};
    private Rules() {}
    public static boolean uuid(String value){if(value==null)return false;try{return java.util.UUID.fromString(value).toString().equalsIgnoreCase(value);}catch(IllegalArgumentException e){return false;}}
    public static int quantity(String text) {
        if (text == null || !text.trim().matches("-?[0-9]+")) throw new IllegalArgumentException("Enter a whole-number gallon quantity.");
        try { int n = Integer.parseInt(text.trim()); if (n <= 0) throw new IllegalArgumentException("Quantity must be greater than zero."); return n; }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a valid gallon quantity."); }
    }
    public static long centavos(String text, boolean allowZero) {
        try {
            if (text == null || !text.trim().matches("[0-9]+(?:\\.[0-9]{1,2})?")) throw new NumberFormatException();
            long cents = new BigDecimal(text.trim()).movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
            if (cents < 0 || (!allowZero && cents == 0)) throw new NumberFormatException();
            return cents;
        } catch (ArithmeticException | NumberFormatException e) { throw new IllegalArgumentException("Enter a valid amount with up to two decimal places."); }
    }
    public static String timestamp(String encoded){if(encoded==null||encoded.isEmpty())return "Not available";try{return java.time.OffsetDateTime.parse(encoded).atZoneSameInstant(MANILA).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM uuuu, h:mm a 'Asia/Manila'",Locale.ENGLISH));}catch(java.time.DateTimeException e){throw new IllegalArgumentException("The server returned an invalid timestamp.");}}
    public static String money(long cents) { return "₱" + BigDecimal.valueOf(cents,2).setScale(2).toPlainString(); }
    public static void contact(String s) { if (!s.matches("[0-9]{11}")) throw new IllegalArgumentException("Please enter a valid contact number. Use 11 digits."); }
    public static String customerContact(String s) { String normalized=s==null?"":s.replace(" ","");contact(normalized);return normalized; }
    public static void remarks(String s) { if (s.codePointCount(0,s.length()) > 500) throw new IllegalArgumentException("Use 500 characters or fewer."); }
    public static void password(String s) { if (s.codePointCount(0,s.length()) < 15) throw new IllegalArgumentException("Use at least 15 characters."); }
    public static LocalDate date(String s) { try { return LocalDate.parse(s); } catch (Exception e) { throw new IllegalArgumentException("Enter a valid date (YYYY-MM-DD)."); } }
    public static void period(String start,String end) { if(date(end).isBefore(date(start))) throw new IllegalArgumentException("The end date cannot be earlier than the start date."); }
    public static String label(String value){if(value==null||value.isEmpty())return "";switch(value){case "household":return "Household";case "dealer":return "Dealer";case "walk_in":return "Walk-in";case "ordered":return "Ordered";case "for_pickup":return "For Pickup";case "picked_up":return "Picked Up";case "refilling":return "Refilling";case "ready_for_delivery":return "Ready for Delivery";case "undelivered":return "Undelivered";case "delivered":return "Delivered";case "rescheduled":return "Rescheduled";case "paid":return "Paid";case "partially_paid":return "Partially Paid";case "unpaid":return "Unpaid";default:String text=value.replace('_',' ');return Character.toUpperCase(text.charAt(0))+text.substring(1);}}
    public static String paymentStatus(long total,long paid) { if (paid<0 || paid>total || total<0) throw new IllegalArgumentException("Invalid payment totals."); return paid==total?"Paid":paid==0?"Unpaid":"Partially Paid"; }
    public static long fixedPrice(String type) { if ("dealer".equals(type.toLowerCase(Locale.ROOT))) return 3000; if ("household".equals(type)||"walk_in".equals(type)) return 3500; throw new IllegalArgumentException("Invalid customer type."); }
}
