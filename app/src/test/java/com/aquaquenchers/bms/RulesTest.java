package com.aquaquenchers.bms;
import org.junit.Test;
import static org.junit.Assert.*;
public class RulesTest {
 @Test public void confirmationIdentifiersMustBeCanonical(){assertTrue(Rules.uuid("10000000-0000-4000-8000-000000000001"));for(String value:new String[]{"","1-1-1-1-1","not saved"})assertFalse(Rules.uuid(value));assertFalse(Rules.uuid(null));}
 @Test public void exactMoneyAndPrices(){assertEquals(75000,Rules.centavos("750.00",false));assertEquals(3500,Rules.fixedPrice("household"));assertEquals(3000,Rules.fixedPrice("dealer"));assertEquals("₱0.01",Rules.money(1));assertEquals("Paid",Rules.paymentStatus(3500,3500));assertEquals("Partially Paid",Rules.paymentStatus(3500,1));assertEquals("Unpaid",Rules.paymentStatus(3500,0));assertEquals("Partially Paid",Rules.label("partially_paid"));assertEquals("Ready for Delivery",Rules.label("ready_for_delivery"));}
 @Test public void positiveWholeQuantity(){assertEquals(8,Rules.quantity("8"));for(String s:new String[]{"0","-1","1.5","abc","999999999999"})assertThrows(IllegalArgumentException.class,()->Rules.quantity(s));}
 @Test public void rejectRoundedAndInvalidMoney(){for(String s:new String[]{"1.001","-1","NaN","1e4","","0"})assertThrows(IllegalArgumentException.class,()->Rules.centavos(s,false));assertEquals(0,Rules.centavos("0",true));}
 @Test public void receiptPresentationUsesManilaCutoff(){assertEquals("15 Oct 2026, 12:30 AM Asia/Manila",Rules.timestamp("2026-10-14T16:30:00Z"));assertEquals("14 Oct 2026, 11:59 PM Asia/Manila",Rules.timestamp("2026-10-14T15:59:00+00:00"));assertThrows(IllegalArgumentException.class,()->Rules.timestamp("not a timestamp"));}
 @Test public void unicodeAndDateBoundaries(){Rules.remarks("😀".repeat(500));assertThrows(IllegalArgumentException.class,()->Rules.remarks("😀".repeat(501)));Rules.password("a".repeat(15));assertThrows(IllegalArgumentException.class,()->Rules.password("short"));Rules.period("2026-10-01","2026-10-15");assertThrows(IllegalArgumentException.class,()->Rules.period("2026-10-15","2026-10-01"));Rules.contact("09175550188");assertThrows(IllegalArgumentException.class,()->Rules.contact("0917 5550188"));}
}
