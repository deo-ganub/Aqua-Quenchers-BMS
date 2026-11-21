package com.aquaquenchers.bms;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FuzzyTest {
    @Test public void exactAndPartialNamesMatch() {
        assertTrue(Fuzzy.matches("Maria Santos", "Maria Santos", "09175550125"));
        assertTrue(Fuzzy.matches("maria", "Maria Santos", "09175550125"));
        assertTrue(Fuzzy.matches("santos", "Maria Santos", "09175550125"));
        assertTrue(Fuzzy.matches("MARIASANTOS", "Maria Santos", "09175550125"));
    }

    @Test public void smallTyposStillMatch() {
        assertTrue(Fuzzy.matches("Mria", "Maria Santos", ""));          // missing letter
        assertTrue(Fuzzy.matches("Maira", "Maria Santos", ""));         // swapped letters
        assertTrue(Fuzzy.matches("Santso", "Maria Santos", ""));
        assertTrue(Fuzzy.matches("maria sntos", "Maria Santos", ""));   // each word checked
        assertTrue(Fuzzy.matches("Liza Ramoz", "Liza Ramos", ""));
        assertTrue(Fuzzy.matches("Pena", "Ana Peña", ""));              // accents ignored
    }

    @Test public void unrelatedSearchesDoNotMatch() {
        assertFalse(Fuzzy.matches("zzz", "Maria Santos", "09175550125"));
        assertFalse(Fuzzy.matches("Cruz", "Maria Santos", ""));
        assertFalse(Fuzzy.matches("Liz", "Luz Reyes", ""));             // 3 letters: no typos allowed
        assertFalse(Fuzzy.matches("maria cruz", "Maria Santos", ""));   // every word must match
    }

    @Test public void contactNumbersMatchByDigits() {
        assertTrue(Fuzzy.matches("09684464985", "Sandy", "09684464985"));
        assertTrue(Fuzzy.matches("0968 446", "Sandy", "09684464985"));
        assertTrue(Fuzzy.matches("4985", "Sandy", "09684464985"));
        assertFalse(Fuzzy.matches("0917", "Sandy", "09684464985"));
    }

    @Test public void closerMatchesScoreHigher() {
        int exact = Fuzzy.score("Maria", "Maria", "");
        int prefix = Fuzzy.score("Mar", "Maria Santos", "");
        int typo = Fuzzy.score("Mraia", "Maria Santos", "");
        assertTrue(exact > prefix);
        assertTrue(prefix > typo);
        assertEquals(0, Fuzzy.score("  ", "Anyone", ""));
    }
}
