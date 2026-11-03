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
public class CarryoverQueueDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL CARRYOVER STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private JSONObject read(String resource,JSONObject fields){AtomicReference<JSONObject> reply=new AtomicReference<>();ui(a->{a.query(resource,fields,reply::set);return null;});await("real "+resource,()->reply.get()!=null);return reply.get();}
    private JSONObject order()throws Exception{return read("order",new JSONObject().put("id",fixture.getString("order_id")));}
    @Test public void populatedCarryoverCardRetainsDateAndCompletesDelivery()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"carryover-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CARRYOVER_UI",fixture.getString("kind"));resultFile=new File(directory,"carryover-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),false);JSONObject expected=fixture.getJSONObject("home_seeded");await("populated live home counts",()->hasText("Orders: "+expected.optLong("orders_count"))&&hasText("Pending / carry-over gallons: 2"));String reference=fixture.getString("reference");await("current order card",()->ui(a->find(a.getWindow().getDecorView(),v->v instanceof Button&&((Button)v).getText().toString().contains(reference)&&((Button)v).getText().toString().endsWith("View order")&&v.isEnabled())!=null));ui(a->{find(a.getWindow().getDecorView(),v->v instanceof Button&&((Button)v).getText().toString().contains(reference)&&((Button)v).getText().toString().endsWith("View order")).performClick();return null;});await("card opens selected order",()->hasText("Pending: 2")&&hasButton("Record Delivery"));assertFalse(hasText("Order timeline"));click("Home");click("Pending / carry-over");await("original date/waiting days",()->hasText(reference)&&hasText("Original date: "+fixture.optString("business_date")+" · 1 days waiting")&&hasText("Pending gallons: 2"));click("View Order");await("carryover order",()->hasButton("Record Delivery")&&hasText(reference));click("Record Delivery");JSONObject original=order();JSONObject stock=read("inventory",new JSONObject());
            fill("Gallons delivered this trip","0");click("Save Delivery");await("nonpositive rejected",()->message("Quantity must be greater than zero."));assertEquals(original.toString(),order().toString());fill("Gallons delivered this trip","3");click("Save Delivery");await("overdelivery rejected",()->message("Delivered quantity cannot exceed the remaining 2 gallons."));assertEquals(original.toString(),order().toString());assertEquals(stock.toString(),read("inventory",new JSONObject()).toString());fill("Gallons delivered this trip","2");active.recreate();await("delivery form restored",()->hasButton("Save Delivery"));assertEquals("2",field("Gallons delivered this trip"));click("Save Delivery");await("final delivery",()->message("Delivery recorded.")&&hasText("Status: Delivered"));JSONObject done=order();assertEquals("delivered",done.getString("status"));assertEquals(3,done.getInt("delivered_quantity"));assertEquals(0,done.getInt("pending_quantity"));assertEquals(0,done.getInt("reserved_quantity"));assertEquals(fixture.getString("business_date"),done.getString("business_date"));assertEquals(9000,done.getLong("total_centavos"));assertEquals(2,done.getJSONArray("deliveries").length());phase("delivery_verified");click("Home");await("updated work counts",()->hasText("Orders: "+fixture.optJSONObject("home_before").optLong("orders_count"))&&hasText("Pending / carry-over gallons: 0"));click("Pending / carry-over");await("no carryover left",()->hasText("No pending deliveries."));result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
