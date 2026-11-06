package com.example.aquaquencherspos;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All network access: reads, saves through database functions, and identity calls. */
public final class Api {

    public interface Callback<T> {
        void onResult(T value);
    }

    public interface Failure {
        void onFailure(ApiError error);
    }

    public static final class ApiError extends Exception {
        final String detail;
        final boolean connection;
        final boolean sessionExpired;

        ApiError(String message, String detail, boolean connection, boolean sessionExpired) {
            super(message);
            this.detail = detail == null ? "" : detail;
            this.connection = connection;
            this.sessionExpired = sessionExpired;
        }
    }

    private static final String EXPIRED = "Session expired. Sign in again.";
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Api() {
    }

    /** Reads a table or view. The path holds the PostgREST query, e.g. "orders_view?select=*". */
    public static void select(String path, Callback<JSONArray> ok, Failure no) {
        run(() -> new JSONArray(send("/rest/v1/" + path, "GET", null, true)), ok, no);
    }

    /** Calls a database function that returns a JSON object. */
    public static void rpc(String function, JSONObject params, Callback<JSONObject> ok, Failure no) {
        run(() -> new JSONObject(send("/rest/v1/rpc/" + function, "POST", params, true)), ok, no);
    }

    /** Signs in through the protected username mapping. */
    public static void login(String username, String password, Callback<JSONObject> ok, Failure no) {
        run(() -> {
            JSONObject body = new JSONObject().put("action", "login")
                    .put("username", username).put("password", password);
            return new JSONObject(send("/functions/v1/identity", "POST", body, false));
        }, ok, no);
    }

    public static void identity(JSONObject body, Callback<JSONObject> ok, Failure no) {
        run(() -> new JSONObject(send("/functions/v1/identity", "POST", body, true)), ok, no);
    }

    public static void authGet(String path, Callback<JSONObject> ok, Failure no) {
        run(() -> new JSONObject(send("/auth/v1/" + path, "GET", null, true)), ok, no);
    }

    public static void authPost(String path, JSONObject body, Callback<JSONObject> ok, Failure no) {
        run(() -> new JSONObject(send("/auth/v1/" + path, "POST", body, true)), ok, no);
    }

    private interface Work<T> {
        T call() throws IOException, ApiError, JSONException;
    }

    private static <T> void run(Work<T> work, Callback<T> ok, Failure no) {
        EXECUTOR.execute(() -> {
            try {
                T value = work.call();
                MAIN.post(() -> ok.onResult(value));
            } catch (ApiError e) {
                MAIN.post(() -> no.onFailure(e));
            } catch (IOException e) {
                ApiError error = new ApiError("Unable to connect.",
                        "Check the internet connection and try again. Nothing was saved.", true, false);
                MAIN.post(() -> no.onFailure(error));
            } catch (JSONException e) {
                ApiError error = new ApiError("Unexpected server response.",
                        "Please try again.", false, false);
                MAIN.post(() -> no.onFailure(error));
            }
        });
    }

    private static String send(String endpoint, String method, JSONObject body, boolean signedIn)
            throws IOException, ApiError {
        Response response = exchange(endpoint, method, body, signedIn ? Session.accessToken : null);
        if (signedIn && response.status == 401 && refreshSession()) {
            response = exchange(endpoint, method, body, Session.accessToken);
        }
        if (response.status >= 200 && response.status < 300) {
            return response.text;
        }
        throw toError(response, signedIn);
    }

    private static synchronized boolean refreshSession() throws IOException {
        String refresh = Session.refreshToken;
        if (refresh == null) {
            return false;
        }
        try {
            JSONObject body = new JSONObject().put("refresh_token", refresh);
            Response r = exchange("/auth/v1/token?grant_type=refresh_token", "POST", body, null);
            if (r.status < 200 || r.status >= 300) {
                return false;
            }
            JSONObject session = new JSONObject(r.text);
            Session.accessToken = session.getString("access_token");
            Session.refreshToken = session.getString("refresh_token");
            return true;
        } catch (JSONException e) {
            return false;
        }
    }

    private static ApiError toError(Response response, boolean signedIn) {
        String message = "Request failed (" + response.status + ").";
        String detail = "";
        try {
            JSONObject error = new JSONObject(response.text);
            for (String key : new String[]{"message", "msg", "error_description", "error"}) {
                String value = error.optString(key, "");
                if (!value.isEmpty()) {
                    message = value;
                    break;
                }
            }
            String details = error.optString("details", "");
            if (!details.isEmpty() && !"null".equals(details)) {
                detail = details;
            }
        } catch (JSONException ignored) {
            // Not JSON: keep the status message.
        }
        boolean expired = EXPIRED.equals(message) || (signedIn && response.status == 401);
        return new ApiError(expired ? EXPIRED : message, detail, false, expired);
    }

    private static final class Response {
        final int status;
        final String text;

        Response(int status, String text) {
            this.status = status;
            this.text = text;
        }
    }

    private static Response exchange(String endpoint, String method, JSONObject body, String token)
            throws IOException {
        URL url = new URL(SupabaseConfig.SUPABASE_URL + endpoint);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setRequestProperty("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) {
                c.setRequestProperty("Authorization", "Bearer " + token);
            }
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                try (OutputStream out = c.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = c.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? c.getInputStream() : c.getErrorStream();
            StringBuilder text = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        text.append(line);
                    }
                }
            }
            return new Response(status, text.toString());
        } finally {
            c.disconnect();
        }
    }
}
