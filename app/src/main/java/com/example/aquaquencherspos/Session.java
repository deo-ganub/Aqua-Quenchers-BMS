package com.example.aquaquencherspos;

import android.os.SystemClock;

/**
 * The signed-in account. Tokens stay in memory only, so nothing sensitive is
 * written to the device and tokens are cleared when the process ends or the user signs out.
 */
public final class Session {

    /** Requirement A-01.4: reauthenticate after 30 minutes of inactivity. */
    static final long IDLE_LIMIT_MS = 30L * 60L * 1000L;

    static volatile String accessToken;
    static volatile String refreshToken;
    static String userId;
    static String role;
    static String username;
    static String fullName;
    private static volatile long lastActivity;
    private static volatile long generation;

    static final class Credentials {
        final long generation;
        final String access, refresh, userId;
        Credentials(long generation, String access, String refresh, String userId) {
            this.generation=generation; this.access=access; this.refresh=refresh; this.userId=userId;
        }
    }
    static synchronized long generation() { return generation; }
    static synchronized boolean isCurrent(long expected) { return generation==expected; }
    static synchronized Credentials snapshot() {
        return accessToken==null ? null : new Credentials(generation,accessToken,refreshToken,userId);
    }
    static synchronized Credentials snapshot(long expected) {
        return generation==expected ? snapshot() : null;
    }
    static synchronized boolean installTokens(long expected, String access, String refresh) {
        if (generation!=expected || accessToken==null || access==null || access.isEmpty()
                || refresh==null || refresh.isEmpty()) return false;
        accessToken=access; refreshToken=refresh; return true;
    }

    private Session() {
    }

    static synchronized void start(String access, String refresh, String id, String userRole, String name, String display) {
        generation++;
        accessToken = access;
        refreshToken = refresh;
        userId = id;
        role = userRole;
        username = name;
        fullName = display;
        touch();
    }

    static synchronized void clear() {
        generation++;
        accessToken = null;
        refreshToken = null;
        userId = null;
        role = null;
        username = null;
        fullName = null;
    }

    static void touch() {
        lastActivity = SystemClock.elapsedRealtime();
    }

    static boolean isSignedIn() {
        return accessToken != null;
    }

    static boolean isIdleTooLong() {
        return SystemClock.elapsedRealtime() - lastActivity >= IDLE_LIMIT_MS;
    }

    static String displayName() {
        return fullName != null && !fullName.isEmpty() ? fullName : username;
    }
}
