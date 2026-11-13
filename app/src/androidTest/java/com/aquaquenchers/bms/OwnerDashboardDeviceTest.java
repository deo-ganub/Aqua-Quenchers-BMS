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
public class OwnerDashboardDeviceTest {
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
    private boolean idle(){return ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE);}
    private void contentClick(String label){await(label,()->ui(a->find(a.findViewById(R.id.content),v->v instanceof Button&&label.contentEquals(((Button)v).getText()))!=null)&&idle());ui(a->{find(a.findViewById(R.id.content),v->v instanceof Button&&label.contentEquals(((Button)v).getText())).performClick();return null;});}
    private boolean figures(JSONObject r){String[] labels={"Revenue","Payments received","Expenses","Net income","Receivables"};String[] keys={"revenue_centavos","payments_received_centavos","expenses_centavos","net_income_centavos","outstanding_centavos"};for(int i=0;i<keys.length;i++)if(!hasText(labels[i]+": "+Rules.money(r.optLong(keys[i]))))return false;JSONObject stock=r.optJSONObject("inventory");String[] stockKeys={"filled_available","reserved_filled","empty_quantity","pending_quantity"};String[] stockLabels={"Filled available","Reserved filled","Empty gallons","Pending delivery gallons"};for(int i=0;i<stockKeys.length;i++)if(!hasText(stockLabels[i]+": "+stock.optLong(stockKeys[i])))return false;return hasText("Inventory updated: "+ui(a->a.timestamp(stock.optString("updated_at"))));}
    private void period(String start,String end,String action){contentClick("Select dashboard period");dialogFill("START DATE",start);dialogFill("END DATE",end);dialogClick(action);}
    @Test public void realPeriodsCancelValidationInventoryAndQuickActions()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"owner-dashboard-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_OWNER_DASHBOARD_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),true);String today=fixture.getString("today"),past=fixture.getString("past");JSONObject current=fixture.getJSONObject("figures").getJSONObject(today),previous=fixture.getJSONObject("figures").getJSONObject(past);await("live dashboard",()->idle()&&figures(current));assertTrue(hasText(" • Owner"));assertTrue(hasText("Aqua Quenchers"));await("recent order",()->hasText(fixture.optString("reference")));period(past,past,"Cancel");assertEquals(today,field("Start date"));assertTrue(figures(current));period(past,past,"Apply period");await("past-period figures",()->idle()&&figures(previous));assertEquals(past,field("Start date"));assertEquals(past,field("End date"));period(today,past,"Apply period");await("invalid period",()->message("The end date cannot be earlier than the start date."));assertTrue(figures(previous));assertEquals(past,field("Start date"));period(today,today,"Apply period");await("corrected period",()->idle()&&figures(current));assertFalse(message("The end date cannot be earlier than the start date."));active.recreate();await("dashboard restored",()->idle()&&hasButton("Select dashboard period")&&figures(current));
            contentClick("Inventory");await("inventory screen",()->hasButton("Carry-over details"));JSONObject inventory=current.getJSONObject("inventory");for(String[] pair:new String[][]{{"filled_available","Filled available"},{"reserved_filled","Reserved filled"},{"empty_quantity","Empty gallons"},{"pending_quantity","Pending delivery gallons"}})assertTrue(hasText(pair[1]+": "+inventory.getLong(pair[0])));assertTrue(hasText("Inventory updated: "+ui(a->a.timestamp(inventory.optString("updated_at")))));assertFalse(hasButton("Adjust stock"));assertFalse(hasButton("Save inventory"));JSONArray pending=fixture.getJSONArray("pending_orders");for(int i=0;i<pending.length();i++){JSONObject row=pending.getJSONObject(i);assertTrue(hasText(row.getString("customer_name")+" • "+row.getString("reference")+" • Original date: "+row.getString("business_date")+" • Pending: "+row.getLong("pending_quantity")));}contentClick("Carry-over details");await("inventory carry-over route",()->hasButton("Back to Orders"));for(int i=0;i<pending.length();i++){JSONObject row=pending.getJSONObject(i);assertTrue(hasText(row.getString("reference")));assertTrue(hasText("Pending gallons: "+row.getLong("pending_quantity")));}click("Dashboard");contentClick("Customer balances");await("customer balances",()->hasButton("Add Customer"));click("Dashboard");contentClick("Create Order");assertTrue(hasButton("Save Order"));click("Cancel");click("Dashboard");contentClick("Walk-in Sale");assertTrue(hasButton("Continue to payment"));click("Cancel");click("Dashboard");contentClick("Reports");assertTrue(hasButton("Generate Report"));click("Dashboard");contentClick("View Orders");assertTrue(hasButton("Search / filter"));click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
