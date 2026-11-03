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
public class CustomerEditDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL CUSTOMER EDIT STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private JSONObject read(String resource,JSONObject fields){AtomicReference<JSONObject> value=new AtomicReference<>();ui(a->{a.query(resource,fields,value::set);return null;});await("real "+resource,()->value.get()!=null);return value.get();}
    private JSONObject customer()throws Exception{return read("customer",new JSONObject().put("id",fixture.getString("customer_id")));}
    private int count(View v,String label){int n=v instanceof Button&&label.contentEquals(((Button)v).getText())?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=count(((ViewGroup)v).getChildAt(i),label);return n;}
    private void open(){click("Customers");fill("Search by name or contact number",fixture.optString("contact"));click("Search");await("one matching customer",()->hasText(fixture.optString("contact"))&&ui(a->count(a.getWindow().getDecorView(),"View customer")==1));click("View customer");await("profile",()->hasButton("Edit Customer"));}
    private void unchanged()throws Exception{JSONObject actual=customer();assertEquals(fixture.getString("old_name"),actual.getString("name"));assertEquals(fixture.optString("old_type","dealer"),actual.getString("customer_type"));assertEquals(fixture.getString("contact"),actual.getString("contact_number"));assertEquals(1,actual.getJSONArray("orders").length());assertEquals(27000,actual.getJSONArray("orders").getJSONObject(0).getLong("total_centavos"));}
    @Test public void cancelledInvalidAndProspectiveCustomerEdit()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"customer-edit-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CUSTOMER_EDIT_UI",fixture.getString("kind"));resultFile=new File(directory,"customer-edit-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),fixture.optBoolean("owner"));open();assertEquals(fixture.optBoolean("owner"),hasButton("Apply history dates"));assertTrue(hasText("Order history and unpaid orders"));assertTrue(hasText("Payment history"));click("Edit Customer");fill("Full name","CANCELLED LOCAL EDIT");click("Cancel");unchanged();open();click("Edit Customer");fill("Full name","");click("Save Customer");await("required fields",()->message("Complete the required fields."));unchanged();fill("Full name",fixture.getString("new_name"));select("Customer type",0);click("Save Customer");await("required type",()->message("Choose Household, Dealer, or Walk-in."));unchanged();select("Customer type",fixture.optInt("new_type_index",1));fill("Contact number","0917 555");click("Save Customer");await("invalid contact",()->message("Please enter a valid contact number. Use 11 digits."));unchanged();fill("Contact number",fixture.getString("duplicate_contact"));click("Save Customer");await("real duplicate contact",()->message("Customer already exists."));unchanged();phase("invalid_edits_unchanged");
            fill("Contact number",fixture.getString("contact"));fill("Address",fixture.getString("new_address"));active.recreate();await("restored edit",()->hasButton("Save Customer"));assertEquals(fixture.getString("new_name"),field("Full name"));assertEquals(fixture.getString("contact"),field("Contact number"));assertEquals(fixture.optInt("new_type_index",1),(int)ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Customer type")).getSelectedItemPosition()));click("Save Customer");await("saved same customer",()->message("Customer saved."));JSONObject actual=customer();assertEquals(fixture.getString("customer_id"),actual.getString("id"));assertEquals(fixture.optString("new_type","household"),actual.getString("customer_type"));assertEquals(fixture.getString("new_name"),actual.getString("name"));JSONObject order=actual.getJSONArray("orders").getJSONObject(0);assertEquals(3000,order.getLong("unit_price_centavos"));assertEquals(27000,order.getLong("total_centavos"));assertEquals(fixture.optString("order_customer_name",fixture.getString("old_name")),order.getString("customer_name"));assertEquals(1,actual.getJSONArray("payments").length());phase("profile_saved_snapshots_retained");click("Create Order");await("prospective selected-type price",()->hasText("Unit price: "+Rules.money(fixture.optLong("new_unit",3500))));fill("Gallon quantity","2");await("prospective total",()->hasText("Total: "+Rules.money(2*fixture.optLong("new_unit",3500))));click("Cancel");assertEquals(1,customer().getJSONArray("orders").length());result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
