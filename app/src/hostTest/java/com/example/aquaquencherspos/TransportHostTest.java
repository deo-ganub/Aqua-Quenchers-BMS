package com.example.aquaquencherspos;

import android.os.Handler;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Real Api/Session code against in-memory HTTPS connections, never a host/network socket. */
public final class TransportHostTest {
    interface Scenario { Reply respond(Connection c) throws Exception; }
    static volatile Scenario scenario;
    static final class Reply {
        final int code; final String body;
        Reply(int code,String body) { this.code=code; this.body=body; }
    }
    static final class Connection extends HttpURLConnection {
        final ByteArrayOutputStream body=new ByteArrayOutputStream();
        Reply reply;
        Connection(URL u) { super(u); }
        public void disconnect() {} public boolean usingProxy() { return false; } public void connect() {}
        public OutputStream getOutputStream() { return body; }
        String body() { return body.toString(java.nio.charset.StandardCharsets.UTF_8); }
        public int getResponseCode() throws IOException {
            if (getInstanceFollowRedirects()) throw new IOException("Redirects must be disabled.");
            try { reply=scenario.respond(this);return reply.code; }
            catch (Exception e) { throw new IOException(e); }
        }
        public InputStream getInputStream() { return new ByteArrayInputStream(reply.body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        public InputStream getErrorStream() { return getInputStream(); }
    }
    static int checks;
    static void check(boolean value,String label) { if (!value) throw new AssertionError(label);checks++; }
    static Reply ok() { return new Reply(200,"{}"); }
    static Reply expired() { return new Reply(401,"{\"message\":\"Session expired. Sign in again.\"}"); }
    static void startA() { Session.clear();Session.start("access-A","refresh-A","actor-A","owner","fixture.a","Fixture A"); }
    static void startB() { Session.clear();Session.start("access-B","refresh-B","actor-B","owner","fixture.b","Fixture B"); }
    static void await(BooleanSupplier ready) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while (!ready.getAsBoolean() && System.nanoTime()<end) Thread.sleep(2);
        check(ready.getAsBoolean(),"bounded completion");
    }
    static void callback() throws Exception { await(() -> Handler.pending()>0);Handler.drain(); }
    static void held(CountDownLatch latch) throws Exception { check(latch.await(5,TimeUnit.SECONDS),"bounded latch"); }
    static void sessionTests() {
        startA();Session.Credentials old=Session.snapshot();
        startB();check(!Session.installTokens(old.generation,"stale","stale"),"stale token rejected");
        check(Session.accessToken.equals("access-B"),"new identity retained");
        check(old.userId.equals("actor-A"),"snapshot immutable");
        check(Session.snapshot(old.generation)==null,"atomic expected-generation snapshot cannot select a new identity");
        check(Session.snapshot(Session.generation()).userId.equals("actor-B"),"current generation snapshot remains correctly bound");
        android.os.SystemClock.now=1000;Session.touch();
        android.os.SystemClock.now=1000+Session.IDLE_LIMIT_MS;check(Session.isIdleTooLong(),"idle boundary");
        Session.clear();check(Session.snapshot()==null && Session.userId==null,"local cleanup");
    }
    static void staleCompletion() throws Exception {
        startA();AtomicInteger callbacks=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        scenario=c -> { if (c.getURL().getPath().endsWith("fixture")) { entered.countDown();held(release); }return ok(); };
        Api.rpc("fixture",new JSONObject(),v -> callbacks.incrementAndGet(),e -> callbacks.incrementAndGet());
        held(entered);startB();release.countDown();callback();
        check(callbacks.get()==0,"old completion cannot reach new account");
    }
    static void staleRefresh() throws Exception {
        startA();AtomicInteger operations=new AtomicInteger(),callbacks=new AtomicInteger();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        scenario=c -> {
            if (c.getURL().getPath().endsWith("aq_session_touch")) return expired();
            if (c.getURL().getPath().endsWith("/token")) { entered.countDown();held(release);return new Reply(200,"{\"access_token\":\"renewed-A\",\"refresh_token\":\"renewed-R\",\"user\":{\"id\":\"actor-A\"}}"); }
            operations.incrementAndGet();return ok();
        };
        Api.rpc("fixture",new JSONObject(),v -> callbacks.incrementAndGet(),e -> callbacks.incrementAndGet());
        held(entered);startB();release.countDown();callback();
        check(Session.accessToken.equals("access-B"),"late refresh cannot overwrite new account");
        check(operations.get()==0 && callbacks.get()==0,"no mutation replay after identity replacement");
    }
    static void immutableRequest() throws Exception {
        startA();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<String> payload=new AtomicReference<>();
        scenario=c -> { if (c.getURL().getPath().endsWith("aq_session_touch")) { entered.countDown();held(release); }
            else payload.set(c.body());return ok(); };
        JSONObject original=new JSONObject().put("p_key","fixture-key").put("amount",35);
        Api.rpc("fixture",original,v -> {},e -> {throw new AssertionError(e);});held(entered);
        original.put("amount",999);release.countDown();callback();
        check(new JSONObject(payload.get()).getInt("amount")==35,"request snapshot fixed before background work");
    }
    static void deniedGuard() throws Exception {
        startA();AtomicInteger ops=new AtomicInteger();AtomicReference<Api.ApiError> error=new AtomicReference<>();
        scenario=c -> { if (c.getURL().getPath().endsWith("aq_session_touch")) return new Reply(403,"{\"message\":\"Session expired. Sign in again.\"}");ops.incrementAndGet();return ok(); };
        Api.rpc("fixture",new JSONObject(),v -> {throw new AssertionError("guard bypass");},error::set);callback();
        check(ops.get()==0 && error.get().sessionExpired,"session guard rejects before protected operation");
    }
    static void singleRefresh() throws Exception {
        startA();AtomicInteger refreshes=new AtomicInteger();AtomicReference<Api.ApiError> error=new AtomicReference<>();
        scenario=c -> {
            if (c.getURL().getPath().endsWith("/token")) { refreshes.incrementAndGet();return new Reply(200,"{\"access_token\":\"new-A\",\"refresh_token\":\"new-R\",\"user\":{\"id\":\"actor-A\"}}"); }
            if (c.getURL().getPath().endsWith("aq_session_touch")) return "Bearer access-A".equals(c.getRequestProperty("Authorization")) ? expired() : ok();
            return expired();
        };
        Api.rpc("fixture",new JSONObject(),v -> {throw new AssertionError("unexpected success");},error::set);callback();
        check(refreshes.get()==1 && error.get().sessionExpired,"one refresh maximum across guard and operation");
    }
    static void retrySameIntent() throws Exception {
        startA();List<String> payloads=new CopyOnWriteArrayList<>();AtomicInteger refreshes=new AtomicInteger();
        scenario=c -> {
            if (c.getURL().getPath().endsWith("aq_session_touch")) return ok();
            if (c.getURL().getPath().endsWith("/token")) { refreshes.incrementAndGet();return new Reply(200,"{\"access_token\":\"new-A\",\"refresh_token\":\"new-R\",\"user\":{\"id\":\"actor-A\"}}"); }
            payloads.add(c.body());return payloads.size()==1 ? expired() : ok();
        };
        AtomicBoolean done=new AtomicBoolean();Api.rpc("fixture",new JSONObject().put("p_key","same-key"),v -> done.set(true),e -> {throw new AssertionError(e);});callback();
        check(done.get() && payloads.size()==2 && payloads.get(0).equals(payloads.get(1)),"401 retry retains exact intent");
        check(refreshes.get()==1,"one refresh");
    }
    static void boundedBodyAndHonestFailure() throws Exception {
        startA();AtomicReference<Api.ApiError> error=new AtomicReference<>();
        scenario=c -> c.getURL().getPath().endsWith("aq_session_touch") ? ok() : new Reply(200,"x".repeat(4*1024*1024+1));
        Api.rpc("fixture",new JSONObject(),v -> {throw new AssertionError("oversize accepted");},error::set);callback();
        check(error.get().connection,"oversize response fails");
        check(!error.get().detail.contains("Nothing was saved") && error.get().detail.contains("does not confirm"),"honest unknown outcome");
    }
    static void logoutIdentity() throws Exception {
        startA();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);List<String> tokens=new CopyOnWriteArrayList<>();
        scenario=c -> { entered.countDown();held(release);tokens.add(c.getRequestProperty("Authorization"));return ok(); };
        Api.signOut();held(entered);startB();release.countDown();await(() -> tokens.size()==2);
        check(tokens.stream().allMatch("Bearer access-A"::equals),"logout only captured identity");
        check(Session.accessToken.equals("access-B"),"logout cannot clear newer identity");
    }
    public static void main(String[] args) {
        try {
            URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
                protected URLConnection openConnection(URL u) { return new Connection(u); }
            } : null);
            sessionTests();staleCompletion();staleRefresh();immutableRequest();deniedGuard();singleRefresh();retrySameIntent();boundedBodyAndHonestFailure();logoutIdentity();
            System.out.println("PASS: "+checks+" native transport/session assertions; in-memory HTTPS only; no accounts or network.");System.exit(0);
        } catch (Throwable t) {t.printStackTrace();System.exit(1);}
    }
}
