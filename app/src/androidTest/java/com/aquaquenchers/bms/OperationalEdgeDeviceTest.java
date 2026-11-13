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
    private void openOrder(String reference){click("Orders");fill("Search Order ID or customer",reference);click("Search / filter");await("filtered order",()->hasText(reference)&&ui(a->count(a.getWindow().getDecorView(),"View Order")==1));click("View Order");await("operational details",()->hasButton(fixture.optBoolean("delivery_resume_only")?"Record Delivery":"Record Pickup"));}
    private void partialDeliveryPreview()throws Exception{
        if(!fixture.optBoolean("delivery_resume_only")){click("Start Refilling");dialogClick("Save");await("refilling",()->hasButton("Ready for Delivery"));click("Ready for Delivery");dialogClick("Save");await("ready",()->hasButton("Record Delivery"));}click("Record Delivery");
        assertTrue(hasText(fixture.getString("reference")+" · "+fixture.getString("customer_name")));assertTrue(hasText("Previously delivered: 0"));assertTrue(hasText("Remaining gallons: 9"));assertTrue(hasText("Order reservation: 9"));assertTrue(hasText("Recorded at actual server time on save."));
        if(fixture.getBoolean("owner")){assertTrue(hasText("Recording account: "+fixture.getString("recorder")));assertTrue(hasText("Today: "+fixture.getString("today")+" (Asia/Manila); actual delivery time is assigned on save."));}else assertFalse(hasText("Recording account:"));
        JSONObject baseline=order(),stock=read("inventory",new JSONObject());
        for(String[] bad:new String[][]{{"0","Quantity must be greater than zero."},{"-1","Quantity must be greater than zero."},{"1.5","Enter a whole-number gallon quantity."},{"abc","Enter a whole-number gallon quantity."},{"10","Delivered quantity cannot exceed the remaining 9 gallons."}}){fill("Gallons delivered this trip",bad[0]);click("Save Delivery");await("invalid delivery",()->message(bad[1]));assertEquals(baseline.toString(),order().toString());assertEquals(stock.toString(),read("inventory",new JSONObject()).toString());}
        fill("Gallons delivered this trip","4");assertTrue(hasText("Cumulative delivered: 4"));assertTrue(hasText("Pending after saving: 5"));assertTrue(hasText("Result: Undelivered"));click("Cancel");await("delivery Cancel",()->hasButton("Record Delivery"));assertEquals(baseline.toString(),order().toString());click("Record Delivery");fill("Gallons delivered this trip","4");activeScenarioRecreate();assertEquals("4",field("Gallons delivered this trip"));assertTrue("Restored quantity must refresh delivery preview",hasText("Pending after saving: 5"));phase("submitting_partial_delivery");click("Save Delivery");await("partial delivery saved",()->message("Delivery recorded.")&&hasText("Status: Undelivered"));JSONObject partial=order();assertEquals(4,partial.getInt("delivered_quantity"));assertEquals(5,partial.getInt("pending_quantity"));assertEquals(5,partial.getInt("reserved_quantity"));assertEquals(27000,partial.getLong("total_centavos"));assertEquals(1,partial.getJSONArray("deliveries").length());
    }
    private void activeScenarioRecreate(){scenario.recreate();await("restored delivery",()->hasButton("Save Delivery"));}
    private void pendingRows(View view,JSONArray rows,java.util.List<String> labels){if(view instanceof TextView&&!(view instanceof EditText)){String label=((TextView)view).getText().toString();for(int i=0;i<rows.length();i++)if(label.startsWith(rows.optJSONObject(i).optString("reference")+" · ")){labels.add(label);break;}}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)pendingRows(((ViewGroup)view).getChildAt(i),rows,labels);}
    private void pendingOnly()throws Exception{
        click("Orders");click("Pending gallons");JSONArray expected=fixture.getJSONArray("pending");await("both carryover rows",()->hasText(expected.optJSONObject(0).optString("reference"))&&hasText(expected.optJSONObject(1).optString("reference")));java.util.List<String> labels=ui(a->{java.util.List<String> rows=new java.util.ArrayList<>();pendingRows(a.findViewById(R.id.content),expected,rows);return rows;});assertEquals(expected.length(),labels.size());
        for(int i=0;i<expected.length();i++){JSONObject row=expected.getJSONObject(i);assertTrue("Oldest original date first",labels.get(i).startsWith(row.getString("reference")+" · "+row.getString("customer_name")));assertTrue(hasText("Original date: "+row.getString("business_date")+" · "+row.getInt("days_waiting")+" days waiting"));assertTrue(hasText("Pending gallons: 5"));}
        result.put("completed",true);phase("queue_verified");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
    }
    @Test public void rejectedCollectionRescheduleRotationAndShortPickup()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);
        File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"operational-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_OPERATIONAL_UI",fixture.getString("kind"));resultFile=new File(directory,"operational-test-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));if(fixture.optBoolean("pending_only")){pendingOnly();return;}openOrder(fixture.getString("reference"));if(fixture.optBoolean("delivery_resume_only")){partialDeliveryPreview();result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));return;}long initialEmpty=empty();
            click("Record Pickup");if(fixture.optBoolean("full_cycle")){assertTrue(hasText(fixture.getString("reference")+" · "+fixture.getString("customer_name")));assertTrue(hasText(fixture.getString("contact_number")));assertTrue(hasText(fixture.getString("address")));assertTrue(hasText("Pickup date: "+fixture.getString("today")));assertTrue(hasText("Original expected gallons: 10"));assertTrue(hasText("Fixed unit price: ₱30.00"));assertTrue(hasButton("Reschedule Pickup"));assertTrue(hasButton("Cancel"));}String[][] rejected={{"0","Quantity must be greater than zero."},{"-1","Quantity must be greater than zero."},{"2.5","Enter a whole-number gallon quantity."},{"abc","Enter a whole-number gallon quantity."},{"11","Collected quantity cannot exceed the expected quantity."},{"8","Adjusted total cannot be less than the amount already paid."}};
            for(String[] test:rejected){fill("Actual collected gallons",test[0]);click("Save Pickup");await("invalid collection message",()->message(test[1]));unchanged(initialEmpty);}phase("collection_rejected_without_effects");
            click("Cancel");await("order details",()->hasButton("Record Pickup"));click("Reschedule Pickup");fill("Notes (optional)","CANCELLED LOCAL NOTE");click("Cancel");await("cancelled reschedule",()->hasButton("Record Pickup"));unchanged(initialEmpty);assertFalse(order().optString("reschedule_notes").contains("CANCELLED"));
            click("Reschedule Pickup");fill("New pickup date",fixture.getString("past_date"));click(fixture.getBoolean("owner")?"Save Pickup Result":"Save result");await("past date rejected",()->message("Select today or a future date."));unchanged(initialEmpty);
            fill("New pickup date",fixture.getString("future_date"));fill("Notes (optional)",fixture.getString("notes"));active.recreate();await("restored reschedule",()->hasButton(fixture.optBoolean("owner")?"Save Pickup Result":"Save result"));assertEquals(fixture.getString("future_date"),field("New pickup date"));assertEquals(fixture.getString("notes"),field("Notes (optional)"));click(fixture.getBoolean("owner")?"Save Pickup Result":"Save result");await("saved reschedule",()->message("Order rescheduled."));assertEquals("rescheduled",order().getString("status"));assertEquals(fixture.getString("notes"),order().getString("reschedule_notes"));assertEquals(initialEmpty,empty());phase("reschedule_verified");
            click("Resume For Pickup");dialogClick("Cancel");await("cancelled transition",()->hasButton("Resume For Pickup"));assertEquals("rescheduled",order().getString("status"));click("Resume For Pickup");dialogClick("Save");await("resumed collection",()->hasButton("Record Pickup"));click("Record Pickup");fill("Actual collected gallons","9");if(fixture.optBoolean("full_cycle")){assertTrue(hasText("Shortfall: 1"));assertTrue(hasText("Adjusted order gallons: 9"));assertTrue(hasText("Adjusted total: ₱270.00"));active.recreate();await("restored pickup",()->hasButton("Save Pickup"));assertEquals("9",field("Actual collected gallons"));assertTrue("Restored quantity must refresh pickup preview",hasText("Adjusted total: ₱270.00"));}click("Save Pickup");await("actual short pickup",()->message("Pickup recorded."));JSONObject o=order();assertEquals("picked_up",o.getString("status"));assertEquals(10,o.getInt("expected_quantity"));assertEquals(9,o.getInt("quantity"));assertEquals(1,o.getInt("shortfall"));assertEquals(27000,o.getLong("total_centavos"));assertEquals(initialEmpty+9,empty());assertEquals(fixture.getString("notes"),o.getString("reschedule_notes"));if(fixture.optBoolean("full_cycle"))partialDeliveryPreview();result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
