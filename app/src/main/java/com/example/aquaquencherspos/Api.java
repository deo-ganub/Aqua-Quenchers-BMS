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

    private static long loginAttempt;

    public static void select(String path, Callback<JSONArray> ok, Failure no) {
        run(c -> new JSONArray(send("/rest/v1/" + path,"GET",null,c)),ok,no,true);
    }
    public static void rpc(String function, JSONObject params, Callback<JSONObject> ok, Failure no) {
        JSONObject request=copy(params);
        run(c -> new JSONObject(send("/rest/v1/rpc/"+function,"POST",request,c)),ok,no,true);
    }
    public static void login(String username,String password,Callback<JSONObject> ok,Failure no) {
        synchronized (Api.class) { loginAttempt++; }
        run(c -> new JSONObject(send("/functions/v1/identity","POST",
            new JSONObject().put("action","login").put("username",username).put("password",password),null)),ok,no,false);
    }
    public static void identity(JSONObject body,Callback<JSONObject> ok,Failure no) {
        JSONObject request=copy(body);
        run(c -> new JSONObject(send("/functions/v1/identity","POST",request,c)),ok,no,true);
    }
    public static void authGet(String path,Callback<JSONObject> ok,Failure no) {
        run(c -> new JSONObject(send("/auth/v1/"+path,"GET",null,c)),ok,no,true);
    }
    public static void authPost(String path,JSONObject body,Callback<JSONObject> ok,Failure no) {
        JSONObject request=copy(body);
        run(c -> new JSONObject(send("/auth/v1/"+path,"POST",request,c)),ok,no,true);
    }
    private static JSONObject copy(JSONObject body) {
        if (body==null) return null;
        try { return new JSONObject(body.toString()); }
        catch (JSONException e) { throw new IllegalArgumentException("Invalid request."); }
    }
    private interface Work<T> { T call(Session.Credentials credentials) throws IOException,ApiError,JSONException; }
    private static synchronized long attempt() { return loginAttempt; }
    private static <T> void run(Work<T> work,Callback<T> ok,Failure no,boolean signedIn) {
        final long generation=Session.generation(), attempt=attempt();
        final Session.Credentials credentials=signedIn ? Session.snapshot() : null;
        EXECUTOR.execute(() -> {
            try {
                if (!Session.isCurrent(generation) || signedIn && credentials==null)
                    throw new ApiError(EXPIRED,"",false,true);
                T value=work.call(credentials);
                MAIN.post(() -> { if (Session.isCurrent(generation) && (signedIn || attempt()==attempt)) ok.onResult(value); });
            } catch (ApiError e) {
                MAIN.post(() -> { if (Session.isCurrent(generation) && (signedIn || attempt()==attempt)) no.onFailure(e); });
            } catch (IOException e) {
                ApiError error=new ApiError("Unable to confirm the request.",
                    "Check the connection and review the original record before retrying. A timeout does not confirm whether a save completed.",true,false);
                MAIN.post(() -> { if (Session.isCurrent(generation) && (signedIn || attempt()==attempt)) no.onFailure(error); });
            } catch (JSONException e) {
                ApiError error=new ApiError("Unexpected server response.","Review the original operation before retrying.",false,false);
                MAIN.post(() -> { if (Session.isCurrent(generation) && (signedIn || attempt()==attempt)) no.onFailure(error); });
            }
        });
    }
    private static void current(Session.Credentials c) throws ApiError {
        if (c!=null && !Session.isCurrent(c.generation)) throw new ApiError(EXPIRED,"",false,true);
    }
    private static String send(String endpoint,String method,JSONObject body,Session.Credentials credentials)
            throws IOException,ApiError {
        Session.Credentials c=credentials;
        boolean refreshed=false;
        if (c!=null) {
            current(c);
            Response guard=exchange("/rest/v1/rpc/aq_session_touch","POST",new JSONObject(),c.access);
            if (guard.status==401) {
                c=refreshSession(c); refreshed=true;
                guard=exchange("/rest/v1/rpc/aq_session_touch","POST",new JSONObject(),c.access);
            }
            current(c);
            if (guard.status<200 || guard.status>=300) throw toError(guard,true);
        }
        current(c);
        Response response=exchange(endpoint,method,body,c==null ? null : c.access);
        if (c!=null && response.status==401 && !refreshed) {
            c=refreshSession(c); current(c);
            response=exchange(endpoint,method,body,c.access);
        }
        current(c);
        if (response.status>=200 && response.status<300) return response.text;
        throw toError(response,credentials!=null);
    }
    private static synchronized Session.Credentials refreshSession(Session.Credentials expected)
            throws IOException,ApiError {
        current(expected);
        Session.Credentials latest=Session.snapshot(expected.generation);
        if (latest==null) throw new ApiError(EXPIRED,"",false,true);
        if (!latest.access.equals(expected.access)) return latest;
        try {
            if (latest.refresh==null) throw new ApiError(EXPIRED,"",false,true);
            Response r=exchange("/auth/v1/token?grant_type=refresh_token","POST",
                new JSONObject().put("refresh_token",latest.refresh),null);
            if (r.status<200 || r.status>=300) throw new ApiError(EXPIRED,"",false,true);
            JSONObject session=new JSONObject(r.text);
            if (!latest.userId.equals(session.getJSONObject("user").getString("id"))
                || !Session.installTokens(expected.generation,session.getString("access_token"),session.getString("refresh_token")))
                throw new ApiError(EXPIRED,"",false,true);
            Session.Credentials refreshed=Session.snapshot(expected.generation);
            if (refreshed==null) throw new ApiError(EXPIRED,"",false,true);
            return refreshed;
        } catch (JSONException e) { throw new ApiError(EXPIRED,"",false,true); }
    }
    /** Captures the outgoing identity. Local cleanup never waits for remote connectivity. */
    public static void signOut() {
        Session.Credentials old=Session.snapshot();
        if (old==null) return;
        EXECUTOR.execute(() -> {
            try { exchange("/rest/v1/rpc/aq_logout","POST",new JSONObject(),old.access); }
            catch (IOException ignored) { /* Best effort; local cleanup is still required. */ }
            try { exchange("/auth/v1/logout?scope=local","POST",new JSONObject(),old.access); }
            catch (IOException ignored) { /* Provider invalidation is not promised on failure. */ }
        });
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
        if (!"https".equals(url.getProtocol())) throw new IOException("HTTPS is required.");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        try {
            c.setInstanceFollowRedirects(false);
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
            java.io.ByteArrayOutputStream text=new java.io.ByteArrayOutputStream();
            if (stream!=null) {
                try (InputStream input=stream) {
                    byte[] buffer=new byte[8192]; int count;
                    while ((count=input.read(buffer))!=-1) {
                        if (text.size()+count>4*1024*1024) throw new IOException("Response is too large.");
                        text.write(buffer,0,count);
                    }
                }
            }
            return new Response(status,new String(text.toByteArray(),StandardCharsets.UTF_8));
        } finally {
            c.disconnect();
        }
    }
}
