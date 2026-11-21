package com.aquaquenchers.bms;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saves that could not reach the server because there was no connection. Each entry keeps the
 * exact request (with its original request_id) so that sending it later is the same request the
 * server may already have received; the server's idempotency table guarantees it is applied at
 * most once. Entries belong to one signed-in account and are only sent for that account.
 * Nothing here is ever shown as "saved" until the server confirms it.
 */
public final class OfflineQueue {
    public static final int MAX_ENTRIES = 50;
    public static final int MAX_PER_ACCOUNT = 30;
    public static final long MAX_AGE_MS = 72L * 60 * 60 * 1000;
    /** Saves of an account that never signs in again are purged after this, signed in or not. */
    public static final long PURGE_AGE_MS = 14L * 24 * 60 * 60 * 1000;

    /** Turns bytes into a protected form and back (Android Keystore AES-GCM on the device). */
    public interface Sealer { byte[] seal(byte[] plain) throws Exception; byte[] open(byte[] sealed) throws Exception; }

    public static final class Entry {
        public final String requestId, userId, operation, label; public final JSONObject request; public final long createdAt;
        Entry(String requestId, String userId, String operation, String label, JSONObject request, long createdAt) {
            this.requestId = requestId; this.userId = userId; this.operation = operation; this.label = label; this.request = request; this.createdAt = createdAt;
        }
    }
    /** What to do with an entry after a send attempt. */
    public enum Outcome { SENT, KEEP, REJECTED }

    private final List<Entry> entries = new ArrayList<>();

    public synchronized List<Entry> all() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    public synchronized int size() { return entries.size(); }
    public synchronized int sizeFor(String userId) { int n = 0; for (Entry e : entries) if (e.userId.equals(userId)) n++; return n; }

    /** Adds a request; the same request_id is never queued twice. Returns false when the queue is full. */
    public synchronized boolean add(String userId, JSONObject request, long now) {
        String id = request.optString("request_id");
        if (id.isEmpty() || userId == null || userId.isEmpty()) throw new IllegalArgumentException("A request_id and account are required.");
        for (Entry e : entries) if (e.requestId.equals(id)) return true;
        if (entries.size() >= MAX_ENTRIES || sizeFor(userId) >= MAX_PER_ACCOUNT) return false;
        String op = request.optString("operation");
        try { entries.add(new Entry(id, userId, op, label(op), new JSONObject(request.toString()), now)); } catch (JSONException e) { throw new IllegalArgumentException("Invalid request."); }
        return true;
    }
    public synchronized void remove(String requestId) { entries.removeIf(e -> e.requestId.equals(requestId)); }
    /** Entries this account may send now (oldest first). Old entries are dropped as expired. */
    public synchronized List<Entry> sendable(String userId, long now) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.userId.equals(userId) && now - e.createdAt <= MAX_AGE_MS) out.add(e);
        return out;
    }
    /** This account's entries past the send limit. They are NOT removed here: the caller first asks
     *  the server whether the request arrived (a lost reply looks the same as no connection). */
    public synchronized List<Entry> expired(String userId, long now) {
        List<Entry> old = new ArrayList<>();
        for (Entry e : entries) if (e.userId.equals(userId) && now - e.createdAt > MAX_AGE_MS) old.add(e);
        return old;
    }
    /** Removes and returns entries of any account older than the hard purge age. */
    public synchronized List<Entry> purge(long now) {
        List<Entry> old = new ArrayList<>();
        for (Entry e : entries) if (now - e.createdAt > PURGE_AGE_MS || now < e.createdAt - PURGE_AGE_MS) old.add(e);
        entries.removeAll(old);
        return old;
    }

    /** Decide from the HTTP status: 0 = no connection (keep), 401 = sign-in needed (keep), 2xx handled by caller, others = rejected. */
    public static Outcome outcome(int status) { return outcome(status, ""); }
    /** A 403 that only says the session expired is kept: signing in again lets it through. */
    public static Outcome outcome(int status, String message) { if (status == 403 && "Session expired. Sign in again.".equals(message)) return Outcome.KEEP; return status == 0 || status == 401 || status == 408 || status == 429 || status >= 500 ? Outcome.KEEP : Outcome.REJECTED; }

    static String label(String operation) {
        switch (operation) {
            case "create_order": return "New order";
            case "create_counter_order": return "Walk-in";
            case "create_customer": return "New customer";
            case "update_customer": return "Customer change";
            case "transition_order": return "Order step";
            case "record_pickup": return "Pickup";
            case "reschedule_pickup": return "Reschedule";
            case "record_delivery": return "Delivery";
            case "record_payment": return "Payment";
            case "create_expense": return "Expense";
            case "update_profile": return "Profile change";
            default: return "Save";
        }
    }

    public synchronized byte[] serialize(Sealer sealer) throws Exception {
        JSONArray a = new JSONArray();
        for (Entry e : entries) a.put(new JSONObject().put("request_id", e.requestId).put("user_id", e.userId).put("operation", e.operation).put("request", e.request).put("created_at", e.createdAt));
        return sealer.seal(a.toString().getBytes(StandardCharsets.UTF_8));
    }
    public static OfflineQueue deserialize(byte[] sealed, Sealer sealer) throws Exception {
        OfflineQueue q = new OfflineQueue();
        if (sealed == null || sealed.length == 0) return q;
        JSONArray a = new JSONArray(new String(sealer.open(sealed), StandardCharsets.UTF_8));
        for (int i = 0; i < a.length() && i < MAX_ENTRIES; i++) {
            JSONObject o = a.getJSONObject(i); String op = o.optString("operation");
            q.entries.add(new Entry(o.getString("request_id"), o.getString("user_id"), op, label(op), o.getJSONObject("request"), o.getLong("created_at")));
        }
        return q;
    }
}
