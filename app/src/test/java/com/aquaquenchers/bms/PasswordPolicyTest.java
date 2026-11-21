package com.aquaquenchers.bms;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class PasswordPolicyTest {
    @Test public void longUnrelatedPassphraseIsAccepted() {
        Rules.password("blue river morning tide", "staff.test", "Synthetic Staff");
        Rules.password("Mabuhay ang tubig 2026!");
    }

    @Test public void weakPasswordsAreRejected() {
        String[] bad = {"short", "aaaaaaaaaaaaaaaa", "abcabcabcabcabcabc", "MyPassword2026!!x", "qwerty-river-blue", "tide 1234567 river", "AquaQuenchers2026!!"};
        for (String p : bad) assertThrows(p, IllegalArgumentException.class, () -> Rules.password(p, "staff.test", "Synthetic Staff"));
    }

    @Test public void ownNameOrUsernameIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Rules.password("synthetic river tide", "staff.test", "Synthetic Staff"));
        assertThrows(IllegalArgumentException.class, () -> Rules.password("river staff tide moon", "staff.test", "Ana Reyes"));
    }
}
