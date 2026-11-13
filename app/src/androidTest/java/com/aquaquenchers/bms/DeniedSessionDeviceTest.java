package com.aquaquenchers.bms;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A real committed mutation with lost response, plus an externally prepared
 * expired test-session timestamp. No fake response authorizes a save.
 */
@RunWith(AndroidJUnit4.class)
public class DeniedSessionDeviceTest {
    private ActivityScenario<MainActivity> scenario;
    private File directory;
    private JSONObject fixture;
    private final JSONObject progress=new JSONObject();
    private <T>T ui(Function<MainActivity,T> action){AtomicReference<T> result=new AtomicReference<>();scenario.onActivity(a->result.set(action.apply(a)));return result.get();}
    private Button find(View root,String text){if(root instanceof Button&&text.contentEquals(((Button)root).getText()))return (Button)root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){Button b=find(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
    private boolean button(String name){return ui(a->find(a.getWindow().getDecorView(),name)!=null);}
    private boolean message(String text){return ui(a->{TextView m=a.findViewById(R.id.message);return m.getVisibility()==View.VISIBLE&&text.contentEquals(m.getText());});}
    private void waitFor(String label,BooleanSupplier condition){long end=SystemClock.elapsedRealtime()+60000;while(SystemClock.elapsedRealtime()<end){if(condition.getAsBoolean())return;SystemClock.sleep(100);}fail("Timed out: "+label);}
    private void click(String name){waitFor(name,()->button(name));ui(a->{find(a.getWindow().getDecorView(),name).performClick();return null;});}
    private void fill(String label,String value){ui(a->{EditText e=a.findViewById(R.id.content).findViewWithTag(label);assertNotNull(label,e);e.setText(value);return null;});}
    private void stage(String value)throws Exception{progress.put("run_id",fixture.getString("run_id"));progress.put("phase",value);Files.write(new File(directory,"denied-session-result.json").toPath(),progress.toString().getBytes(StandardCharsets.UTF_8));}
    private void login()throws Exception{fill("Username",fixture.getString("owner_username"));fill("Password",fixture.getString("owner_password"));click("Sign in");waitFor("real Owner navigation",()->ui(a->a.isOwner()&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==7));}
    private JSONObject read(String resource){AtomicReference<JSONObject> data=new AtomicReference<>();ui(a->{a.query(resource,a.json(),data::set);return null;});waitFor("real "+resource+" read",()->data.get()!=null);return data.get();}
    private void staffLogin()throws Exception{fill("Username",fixture.getString("username"));fill("Password",fixture.getString("password"));click("Sign in");waitFor("dedicated Staff navigation",()->ui(a->!a.isOwner()&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==5));}
    private void deniedQuery(String resource,String expected){AtomicReference<JSONObject> forbidden=new AtomicReference<>();ui(a->{a.query(resource,a.json(),forbidden::set);return null;});waitFor("invalid session signed out",()->button("Sign in"));assertNull(forbidden.get());assertTrue(message(expected));assertEquals(0,(int)ui(a->((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()));}
    private void ready(String name){waitFor(name,()->new File(directory,name+"-"+fixture.optString("run_id")+".json").isFile());}
    @Test public void inactiveAndExpiredDeniedRechecksStillSignOut()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"denied-session-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_DENIED_SESSION_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;staffLogin();stage("ready_for_deactivation");ready("deactivated");deniedQuery("inventory","Access denied. Sign in again.");stage("inactive_denied");ready("reactivated");staffLogin();stage("ready_for_expiry");ready("expired");deniedQuery("expenses","Session expired. Sign in again.");progress.put("completed",true);stage("completed");
        }
    }
}
