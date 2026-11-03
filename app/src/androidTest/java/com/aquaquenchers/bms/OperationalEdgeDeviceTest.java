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
public class OperationalEdgeDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL OPERATIONAL STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private JSONObject read(String resource,JSONObject fields){AtomicReference<JSONObject> data=new AtomicReference<>();ui(a->{a.query(resource,fields,data::set);return null;});await("real "+resource,()->data.get()!=null);return data.get();}
    private JSONObject order()throws Exception{return read("order",new JSONObject().put("id",fixture.getString("order_id")));}
    private long empty(){return read("inventory",new JSONObject()).optLong("empty_quantity");}
    private void unchanged(long stock)throws Exception{JSONObject o=order();assertEquals("for_pickup",o.getString("status"));assertEquals(10,o.getInt("quantity"));assertEquals(30000,o.getLong("total_centavos"));assertEquals(stock,empty());}
    private int count(View v,String label){int n=v instanceof Button&&label.contentEquals(((Button)v).getText())?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=count(((ViewGroup)v).getChildAt(i),label);return n;}
    private void openOrder(String reference){click("Orders");fill("Search Order ID or customer",reference);click("Search / filter");await("filtered order",()->hasText(reference)&&ui(a->count(a.getWindow().getDecorView(),"View Order")==1));click("View Order");await("pickup details",()->hasButton("Record Pickup"));}
    @Test public void rejectedCollectionRescheduleRotationAndShortPickup()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);
        File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"operational-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_OPERATIONAL_UI",fixture.getString("kind"));resultFile=new File(directory,"operational-test-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));openOrder(fixture.getString("reference"));long initialEmpty=empty();
            click("Record Pickup");String[][] rejected={{"0","Quantity must be greater than zero."},{"-1","Quantity must be greater than zero."},{"2.5","Enter a whole-number gallon quantity."},{"abc","Enter a whole-number gallon quantity."},{"11","Collected quantity cannot exceed the expected quantity."},{"8","Adjusted total cannot be less than the amount already paid."}};
            for(String[] test:rejected){fill("Actual collected gallons",test[0]);click("Save Pickup");await("invalid collection message",()->message(test[1]));unchanged(initialEmpty);}phase("collection_rejected_without_effects");
            click("Cancel");await("order details",()->hasButton("Record Pickup"));click("Reschedule Pickup");fill("Notes (optional)","CANCELLED LOCAL NOTE");click("Cancel");await("cancelled reschedule",()->hasButton("Record Pickup"));unchanged(initialEmpty);assertFalse(order().optString("reschedule_notes").contains("CANCELLED"));
            click("Reschedule Pickup");fill("New pickup date",fixture.getString("past_date"));click(fixture.getBoolean("owner")?"Save Pickup Result":"Save result");await("past date rejected",()->message("Select today or a future date."));unchanged(initialEmpty);
            fill("New pickup date",fixture.getString("future_date"));fill("Notes (optional)",fixture.getString("notes"));active.recreate();await("restored reschedule",()->hasButton(fixture.optBoolean("owner")?"Save Pickup Result":"Save result"));assertEquals(fixture.getString("future_date"),field("New pickup date"));assertEquals(fixture.getString("notes"),field("Notes (optional)"));click(fixture.getBoolean("owner")?"Save Pickup Result":"Save result");await("saved reschedule",()->message("Order rescheduled."));assertEquals("rescheduled",order().getString("status"));assertEquals(fixture.getString("notes"),order().getString("reschedule_notes"));assertEquals(initialEmpty,empty());phase("reschedule_verified");
            click("Resume For Pickup");dialogClick("Cancel");await("cancelled transition",()->hasButton("Resume For Pickup"));assertEquals("rescheduled",order().getString("status"));click("Resume For Pickup");dialogClick("Save");await("resumed collection",()->hasButton("Record Pickup"));click("Record Pickup");fill("Actual collected gallons","9");click("Save Pickup");await("actual short pickup",()->message("Pickup recorded."));JSONObject o=order();assertEquals("picked_up",o.getString("status"));assertEquals(10,o.getInt("expected_quantity"));assertEquals(9,o.getInt("quantity"));assertEquals(1,o.getInt("shortfall"));assertEquals(27000,o.getLong("total_centavos"));assertEquals(initialEmpty+9,empty());assertEquals(fixture.getString("notes"),o.getString("reschedule_notes"));result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
