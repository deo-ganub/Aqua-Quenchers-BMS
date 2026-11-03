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
public class FinancialHistoryDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL FINANCIAL HISTORY STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private int count(View v,String text,boolean buttons){int n=v instanceof TextView&&!(v instanceof EditText)&&(!buttons||v instanceof Button)&&((TextView)v).getText().toString().contains(text)?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=count(((ViewGroup)v).getChildAt(i),text,buttons);return n;}
    private int count(String text,boolean buttons){return ui(a->count(a.getWindow().getDecorView(),text,buttons));}
    private boolean report(JSONObject expected){String[] labels={"Revenue","Payments received","Expenses","Net income","Outstanding balances"};String[] keys={"revenue_centavos","payments_received_centavos","expenses_centavos","net_income_centavos","outstanding_centavos"};for(int i=0;i<keys.length;i++)if(!hasText(labels[i]+": "+Rules.money(expected.optLong(keys[i]))))return false;return true;}
    private void history(String date,int orders,int receipts){fill("History start date",date);fill("History end date",date);click("Apply history dates");await("business/receipt date history",()->count("View Order",true)==orders&&count("Related reference:",false)==receipts);}
    @Test public void periodsExpenseFiltersAndBusinessVersusReceiptHistory()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);
        File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"financial-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_FINANCIAL_READ_UI",fixture.getString("kind"));resultFile=new File(directory,"financial-test-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),true);click("Reports");
            for(String date:new String[]{fixture.getString("business_date"),fixture.getString("receipt_date")}){fill("Start date",date);fill("End date",date);click("Generate Report");JSONObject expected=fixture.getJSONObject("reports").getJSONObject(date);await("exact protected report",()->report(expected));click("Open Report Preview");assertTrue(report(expected));click("Back to Reports");assertEquals(date,field("Start date"));assertEquals(date,field("End date"));}
            fill("Start date",fixture.getString("receipt_date"));fill("End date",fixture.getString("business_date"));click("Generate Report");await("reversed report dates rejected",()->message("The end date cannot be earlier than the start date."));assertTrue("Rejected dates must preserve previous valid report",report(fixture.getJSONObject("reports").getJSONObject(fixture.getString("receipt_date"))));fill("End date",fixture.getString("receipt_date"));click("Generate Report");await("corrected report",()->report(fixture.optJSONObject("reports").optJSONObject(fixture.optString("receipt_date"))));active.recreate();await("report restored",()->hasButton("Open Report Preview"));assertTrue(report(fixture.getJSONObject("reports").getJSONObject(fixture.getString("receipt_date"))));phase("reports_verified");
            click("Expenses");fill("Start date",fixture.getString("business_date"));fill("End date",fixture.getString("business_date"));select("Category filter",1);click("Apply filters");await("exact Fuel total",()->hasText("Filtered total: "+Rules.money(fixture.optLong("fuel_total"))));select("Category filter",2);click("Apply filters");await("empty category",()->hasText("Filtered total: ₱0.00")&&hasText("No expenses in this period."));phase("expense_filters_verified");
            click("Customers");fill("Search by name or contact number",fixture.getString("customer_name"));click("Search");await("single filtered customer",()->count("View customer",true)==1&&hasText(fixture.optString("customer_name")));click("View customer");await("history controls",()->hasButton("Apply history dates"));history(fixture.getString("receipt_date"),0,2);history(fixture.getString("business_date"),1,0);assertTrue(hasText("No payments recorded."));click("Clear history dates");await("all history restored",()->count("View Order",true)==1&&count("Related reference:",false)==2);assertEquals("",field("History start date"));assertEquals("",field("History end date"));active.recreate();await("customer route restored",()->hasButton("Apply history dates"));await("history after rotation",()->count("View Order",true)==1&&count("Related reference:",false)==2);result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
