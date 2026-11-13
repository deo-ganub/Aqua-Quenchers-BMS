package com.aquaquenchers.bms;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real HTTPS/local official Auth + REST. Refuses an absent or nonlocal fixture.
 * No session injection, fake saves, production credentials or real-model claims.
 */
@RunWith(AndroidJUnit4.class)
public class ManagedAccountDeviceTest {
    private ActivityScenario<MainActivity> scenario;
    private JSONObject fixture;
    private File resultFile;
    private final JSONObject result=new JSONObject();

    private <T> T ui(Function<MainActivity,T> action){AtomicReference<T> value=new AtomicReference<>();scenario.onActivity(a->value.set(action.apply(a)));return value.get();}
    private View find(View view,java.util.function.Predicate<View> match){if(match.test(view))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),match);if(found!=null)return found;}}return null;}
    private Button button(MainActivity a,String label){return (Button)find(a.getWindow().getDecorView(),v->v instanceof Button&&label.contentEquals(((Button)v).getText()));}
    private boolean hasButton(String label){return ui(a->button(a,label)!=null);}
    private boolean hasText(String text){return ui(a->find(a.getWindow().getDecorView(),v->v instanceof TextView&&!(v instanceof EditText)&&((TextView)v).getText().toString().contains(text))!=null);}
    private void await(String label,BooleanSupplier condition){long end=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<end){try{if(condition.getAsBoolean())return;}catch(IllegalStateException ignored){}SystemClock.sleep(100);}String message=ui(a->((TextView)a.findViewById(R.id.message)).getText().toString());fail("Timed out: "+label+"; UI message: "+message);}
    private void click(String label){await(label,()->hasButton(label));ui(a->{button(a,label).performClick();return null;});}
    private void fill(String label,String value){ui(a->{EditText field=a.findViewById(R.id.content).findViewWithTag(label);assertNotNull("Missing field: "+label,field);field.setText(value);return null;});}
    private String field(String label){return ui(a->((EditText)a.findViewById(R.id.content).findViewWithTag(label)).getText().toString());}
    private boolean message(String text){return ui(a->{TextView view=a.findViewById(R.id.message);return view.getVisibility()==View.VISIBLE&&view.getText().toString().equals(text);});}
    private void select(String label,int position){ui(a->{Spinner field=a.findViewById(R.id.content).findViewWithTag(label);assertNotNull(field);field.setSelection(position);return null;});}
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL MANAGED ACCOUNT STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private String totp(long at)throws Exception{String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",secret=fixture.getString("owner_totp_secret").replace("=","").toUpperCase(java.util.Locale.ROOT);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();int bits=0,value=0;for(char c:secret.toCharArray()){int index=chars.indexOf(c);assertTrue(index>=0);value=(value<<5)|index;bits+=5;if(bits>=8){bits-=8;bytes.write((value>>bits)&255);}}byte[] counter=java.nio.ByteBuffer.allocate(8).putLong(at/30).array();Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(bytes.toByteArray(),"HmacSHA1"));byte[] h=mac.doFinal(counter);int offset=h[h.length-1]&15;int code=((h[offset]&127)<<24)|((h[offset+1]&255)<<16)|((h[offset+2]&255)<<8)|(h[offset+3]&255);return String.format(java.util.Locale.ROOT,"%06d",code%1000000);}
    private JSONObject query(String resource){AtomicReference<JSONObject> reply=new AtomicReference<>();ui(a->{a.query(resource,a.json(),reply::set);return null;});await("real "+resource+" reply",()->reply.get()!=null);return reply.get();}
    private JSONObject target()throws Exception{JSONArray rows=query("staff").getJSONArray("items");for(int i=0;i<rows.length();i++){JSONObject p=rows.getJSONObject(i);if(fixture.getString("target_id").equals(p.getString("user_id")))return p;}throw new AssertionError("Stable Staff identity missing");}
    private String card(String name,String username,boolean active){return name+" • "+username+" • "+(active?"Active":"Inactive");}
    @Test public void actualMfaRenameDeactivateAndReactivate()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"managed-account-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_MANAGED_ACCOUNT_UI",fixture.getString("kind"));resultFile=new File(directory,"managed-account-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("owner_username"),fixture.getString("owner_password"),true);click("Settings");click("Staff accounts");String old=card(fixture.getString("old_name"),fixture.getString("old_username"),true);click(old);fill("Full name","CANCELLED LOCAL STAFF EDIT");click("Cancel");await("cancelled account edit",()->hasButton(old));assertEquals(fixture.getString("old_name"),target().getString("full_name"));click(old);fill("Full name",fixture.getString("new_name"));fill("Username",fixture.getString("new_username"));select("Status",1);click("Save Staff Account");dialogClick("Cancel");assertTrue(target().getBoolean("active"));assertEquals(fixture.getString("old_username"),target().getString("username"));active.recreate();await("account fields restored",()->hasButton("Save Staff Account"));assertEquals(fixture.getString("new_username"),field("Username"));assertEquals(1,(int)ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Status")).getSelectedItemPosition()));
            click("Save Staff Account");long firstStep=System.currentTimeMillis()/30000;dialogFill("Verification code",totp(System.currentTimeMillis()/1000));dialogClick("Verify and save");await("managed inactive save",()->message("Staff account saved."));JSONObject inactive=target();assertFalse(inactive.getBoolean("active"));assertEquals(fixture.getString("new_username"),inactive.getString("username"));assertEquals(fixture.getString("target_id"),inactive.getString("user_id"));phase("waiting_inactive_checks");File ready=new File(directory,"managed-inactive-ready-"+fixture.getString("run_id")+".json");await("external old-session/new-login denial",ready::isFile);
            click(card(fixture.getString("new_name"),fixture.getString("new_username"),false));select("Status",0);while(System.currentTimeMillis()/30000==firstStep)SystemClock.sleep(100);click("Save Staff Account");dialogFill("Verification code",totp(System.currentTimeMillis()/1000));dialogClick("Verify and save");await("managed reactivation",()->message("Staff account saved."));JSONObject restored=target();assertTrue(restored.getBoolean("active"));assertEquals(fixture.getString("target_id"),restored.getString("user_id"));result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
