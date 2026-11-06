package com.example.aquaquencherspos;

import android.os.SystemClock;

/**
 * The signed-in account. Tokens stay in memory only, so nothing sensitive is
 * written to the device and closing the app ends the session.
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

    private Session() {
    }

    static void start(String access, String refresh, String id, String userRole, String name, String display) {
        accessToken = access;
        refreshToken = refresh;
        userId = id;
        role = userRole;
        username = name;
        fullName = display;
        touch();
    }

    static void clear() {
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
