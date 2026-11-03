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
public class StaffHomeDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL CONNECTED STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    @Test public void actualCountersQueueLinksAndOwnerDenial()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"staff-home-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_STAFF_HOME_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),false);JSONObject home=fixture.getJSONObject("home");await("authoritative home counters",()->hasText("Orders: "+home.optLong("orders_count"))&&hasText("For Pickup: "+home.optLong("for_pickup_count"))&&hasText("Ready for Delivery: "+home.optLong("ready_for_delivery_count"))&&hasText("Pending / carry-over gallons: "+home.optLong("pending_quantity")));assertTrue(hasText(" • Staff"));assertTrue(hasText("Hello, "+fixture.getString("full_name")+"."));JSONObject current=fixture.getJSONObject("current_order");await("current work card",()->ui(a->find(a.getWindow().getDecorView(),v->v instanceof Button&&((Button)v).getText().toString().contains(current.optString("reference"))&&((Button)v).getText().toString().endsWith("View order"))!=null));String card=ui(a->((Button)find(a.getWindow().getDecorView(),v->v instanceof Button&&((Button)v).getText().toString().contains(current.optString("reference"))&&((Button)v).getText().toString().endsWith("View order"))).getText().toString());assertTrue(card.contains(current.getString("customer_name")));assertTrue(card.contains(Rules.label(current.getString("status"))));assertTrue(card.contains(current.getInt("quantity")+" gallons"));assertTrue(card.contains("Balance "+Rules.money(current.getLong("balance_centavos"))));assertFalse(hasButton("Select dashboard period"));assertFalse(hasButton("Reports"));assertFalse(hasButton("Expenses"));assertFalse(hasButton("Staff accounts"));
            click("For Pickup");await("For Pickup filter",()->ui(a->{Spinner status=a.findViewById(R.id.content).findViewWithTag("Status");return status!=null&&"For Pickup".equals(status.getSelectedItem().toString());}));if(home.getInt("for_pickup_count")==0)await("empty pickup queue",()->hasText("No orders found."));click("Home");click("Ready for Delivery");await("ready filter",()->ui(a->{Spinner status=a.findViewById(R.id.content).findViewWithTag("Status");return status!=null&&"Ready for Delivery".equals(status.getSelectedItem().toString());}));if(home.getInt("ready_for_delivery_count")==0)await("empty ready queue",()->hasText("No orders found."));click("Home");click("Pending / carry-over");if(home.getLong("pending_quantity")==0)await("empty pending queue",()->hasText("No pending deliveries."));click("Home");click("New order");assertEquals(fixture.getString("today"),field("Order date"));assertTrue(hasButton("Save Order"));click("Cancel");click("Home");click("Walk-in sale");assertTrue(hasButton("Continue to payment"));click("Cancel");click("Settings");assertTrue(hasButton("Payments"));assertTrue(hasButton("Pending gallons"));assertFalse(hasButton("Inventory"));assertFalse(hasButton("Staff accounts"));
            for(String resource:new String[]{"report","expenses","staff"}){AtomicReference<JSONObject> forbidden=new AtomicReference<>();ui(a->{a.query(resource,a.json(),forbidden::set);return null;});await("server authoritative "+resource+" denial",()->message("Access denied.")&&ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE));assertNull(forbidden.get());assertFalse(hasButton("Sign in"));assertEquals(5,(int)ui(a->((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()));assertTrue(hasButton("Payments"));}click("Home");await("Staff workspace retained",()->hasText("Orders: "+home.optLong("orders_count")));click("Settings");click("Log out");await("explicit logout",()->hasButton("Sign in"));
        }
    }
}
