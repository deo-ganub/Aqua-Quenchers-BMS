package com.aquaquenchers.bms;

import static org.junit.Assert.*;

import java.security.SecureRandom;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;
import org.junit.Test;

public class OfflineQueueTest {
    /** Same AES-256-GCM format as OfflineStore, with a software key instead of the Android Keystore. */
    static final class TestSealer implements OfflineQueue.Sealer {
        final SecretKeySpec key; TestSealer() { byte[] k = new byte[32]; new SecureRandom().nextBytes(k); key = new SecretKeySpec(k, "AES"); }
        public byte[] seal(byte[] p) throws Exception { Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key); byte[] iv = c.getIV(), b = c.doFinal(p); byte[] out = new byte[1 + iv.length + b.length]; out[0] = (byte) iv.length; System.arraycopy(iv, 0, out, 1, iv.length); System.arraycopy(b, 0, out, 1 + iv.length, b.length); return out; }
        public byte[] open(byte[] s) throws Exception { int n = s[0]; byte[] iv = java.util.Arrays.copyOfRange(s, 1, 1 + n); Cipher c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv)); return c.doFinal(s, 1 + n, s.length - 1 - n); }
    }
    static JSONObject req(String id, String op) throws Exception { return new JSONObject().put("operation", op).put("request_id", id).put("customer_id", "c1").put("quantity", 10); }

    @Test public void sameRequestIsQueuedOnceAndOnlyForItsAccount() throws Exception {
        OfflineQueue q = new OfflineQueue();
        assertTrue(q.add("u1", req("r1", "create_order"), 1000));
        assertTrue(q.add("u1", req("r1", "create_order"), 2000));
        assertTrue(q.add("u2", req("r2", "record_payment"), 3000));
        assertEquals(2, q.size());
        assertEquals(1, q.sendable("u1", 4000).size());
        assertEquals("r1", q.sendable("u1", 4000).get(0).requestId);
        assertEquals("New order", q.sendable("u1", 4000).get(0).label);
        assertEquals("r2", q.sendable("u2", 4000).get(0).requestId);
    }

    @Test public void fullQueueRefusesMore() throws Exception {
        OfflineQueue q = new OfflineQueue();
        for (int i = 0; i < OfflineQueue.MAX_ENTRIES; i++) assertTrue(q.add("u" + (i % 2), req("r" + i, "create_order"), i));
        assertFalse(q.add("u", req("extra", "create_order"), 99));
    }

    @Test public void expiredEntriesAreCheckedNotDroppedAndOldOnesPurged() throws Exception {
        OfflineQueue q = new OfflineQueue();
        q.add("u", req("old", "record_delivery"), 0);
        q.add("u", req("new", "record_delivery"), OfflineQueue.MAX_AGE_MS);
        q.add("other", req("theirs", "record_payment"), 0);
        assertEquals(1, q.expired("u", OfflineQueue.MAX_AGE_MS + 1).size());
        assertEquals(3, q.size()); // nothing removed until the server is asked
        assertEquals(0, q.purge(OfflineQueue.MAX_AGE_MS + 1).size());
        assertEquals(2, q.purge(OfflineQueue.PURGE_AGE_MS + 1).size());
        assertEquals("new", q.all().get(0).requestId);
    }

    @Test public void perAccountCapLeavesRoomForOthers() throws Exception {
        OfflineQueue q = new OfflineQueue();
        for (int i = 0; i < OfflineQueue.MAX_PER_ACCOUNT; i++) assertTrue(q.add("busy", req("b" + i, "create_order"), i));
        assertFalse(q.add("busy", req("b-extra", "create_order"), 99));
        assertTrue(q.add("other", req("o1", "create_order"), 99));
    }

    @Test public void expiredSessionIsKeptOtherDenialsAreNot() {
        assertEquals(OfflineQueue.Outcome.KEEP, OfflineQueue.outcome(403, "Session expired. Sign in again."));
        assertEquals(OfflineQueue.Outcome.REJECTED, OfflineQueue.outcome(403, "Access denied."));
    }

    @Test public void outcomesKeepOnlyRetryableFailures() {
        assertEquals(OfflineQueue.Outcome.KEEP, OfflineQueue.outcome(0));
        assertEquals(OfflineQueue.Outcome.KEEP, OfflineQueue.outcome(401));
        assertEquals(OfflineQueue.Outcome.KEEP, OfflineQueue.outcome(503));
        assertEquals(OfflineQueue.Outcome.REJECTED, OfflineQueue.outcome(400));
        assertEquals(OfflineQueue.Outcome.REJECTED, OfflineQueue.outcome(403));
    }

    @Test public void storedFormIsEncryptedAndRoundTrips() throws Exception {
        TestSealer s = new TestSealer(); OfflineQueue q = new OfflineQueue();
        q.add("u1", new JSONObject().put("operation", "create_customer").put("request_id", "r9").put("contact_number", "09175550999").put("address", "24 Mabini St."), 5);
        byte[] blob = q.serialize(s);
        String raw = new String(blob, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(raw.contains("09175550999")); assertFalse(raw.contains("Mabini")); assertFalse(raw.contains("r9"));
        OfflineQueue back = OfflineQueue.deserialize(blob, s);
        assertEquals("09175550999", back.all().get(0).request.getString("contact_number"));
        blob[blob.length - 1] ^= 1; // tampering is detected by GCM
        final byte[] bad = blob; assertThrows(Exception.class, () -> OfflineQueue.deserialize(bad, s));
    }
}
