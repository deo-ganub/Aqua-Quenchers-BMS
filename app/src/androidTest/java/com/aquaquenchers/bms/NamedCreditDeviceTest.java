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
public class NamedCreditDeviceTest {
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
    private void phase(String name)throws Exception{result.put("run_id",fixture.getString("run_id"));result.put("phase",name);Files.write(resultFile.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));System.out.println("LOCAL NAMED CREDIT STEP: "+name);}
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    private JSONObject read(String resource,JSONObject fields){AtomicReference<JSONObject> value=new AtomicReference<>();ui(a->{a.query(resource,fields,value::set);return null;});await("real "+resource,()->value.get()!=null);return value.get();}
    private JSONArray sales()throws Exception{return read("sales",new JSONObject().put("search",fixture.getString("customer_name"))).getJSONArray("items");}
    private JSONObject sale(String reference)throws Exception{return read("sale",new JSONObject().put("reference",reference));}
    private void noSale(long filled)throws Exception{assertEquals(0,sales().length());assertEquals(filled,read("inventory",new JSONObject()).getLong("physical_filled"));}
    private int count(View v,String label){int n=v instanceof Button&&label.contentEquals(((Button)v).getText())?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=count(((ViewGroup)v).getChildAt(i),label);return n;}
    private void payment(String reference){if(!fixture.optBoolean("owner"))click("Settings");click("Payments");fill("Find customer, Order ID or Walk-in credit reference",reference);click("Search balances");await("one exact credit reference",()->hasText(reference)&&ui(a->count(a.getWindow().getDecorView(),"Record Payment")==1));click("Record Payment");}
    @Test public void creditCancellationValidationAndPartialReceipt()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"credit-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CREDIT_UI",fixture.getString("kind"));resultFile=new File(directory,"credit-test-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("login");login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));click(fixture.getBoolean("owner")?"Walk-in Sale":"POS");await("actual availability",()->hasText("Filled available:"));long filled=read("inventory",new JSONObject()).getLong("physical_filled");long available=read("inventory",new JSONObject()).getLong("filled_available");
            click("Continue to payment");await("required name",()->message("Enter the customer name."));noSale(filled);fill("Customer name",fixture.getString("customer_name"));fill("Cash received (₱)","0");fill("Gallon quantity","2.5");click("Continue to payment");await("whole gallons",()->message("Enter a whole-number gallon quantity."));noSale(filled);fill("Gallon quantity","0");click("Continue to payment");await("positive gallons",()->message("Quantity must be greater than zero."));noSale(filled);
            fill("Gallon quantity","1");fill("Cash received (₱)","-1");click("Continue to payment");await("negative cash",()->message("Cash received cannot be negative."));noSale(filled);fill("Cash received (₱)","0");fill("Gallon quantity",Long.toString(available+1));click("Continue to payment");await("stock limit",()->message("Not enough filled gallons."));noSale(filled);fill("Gallon quantity","1");click("Continue to payment");dialogClick("Cancel");noSale(filled);assertEquals("1",field("Gallon quantity"));assertEquals("0",field("Cash received (₱)"));phase("invalid_and_cancelled_without_effects");
            active.recreate();await("restored POS",()->hasButton("Continue to payment"));assertEquals(fixture.getString("customer_name"),field("Customer name"));assertEquals("0",field("Cash received (₱)"));click("Continue to payment");dialogClick("Confirm and save");await("named unpaid sale",()->hasText("Walk-in sale completed.")&&hasText("Payment status: Unpaid"));JSONArray rows=sales();assertEquals(1,rows.length());JSONObject sold=rows.getJSONObject(0);String reference=sold.getString("reference");result.put("sale_id",sold.getString("id"));result.put("reference",reference);assertEquals(3500,sold.getLong("total_centavos"));assertEquals(3500,sold.getLong("balance_centavos"));assertEquals(0,sale(reference).getJSONArray("payments").length());assertEquals(filled-1,read("inventory",new JSONObject()).getLong("physical_filled"));phase("credit_saved");
            payment(reference);fill("Payment amount (₱)","10");click("Cancel");assertEquals(0,sale(reference).getJSONArray("payments").length());payment(reference);fill("Payment amount (₱)","36");click("Save Payment");await("overpayment",()->message("Payment cannot exceed the ₱35.00 balance."));assertEquals(0,sale(reference).getJSONArray("payments").length());fill("Payment amount (₱)","0");click("Save Payment");await("nonpositive payment",()->message("Enter an amount greater than zero and not above the balance."));fill("Payment amount (₱)","17.50");click("Save Payment");await("actual partial receipt",()->hasText("Payment recorded.")&&hasText("Payment status: Partially Paid"));JSONObject actual=sale(reference);assertEquals(1,actual.getJSONArray("payments").length());assertEquals(1750,actual.getLong("balance_centavos"));assertEquals(1750,actual.getJSONArray("payments").getJSONObject(0).getLong("amount_centavos"));result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
    @Test public void savedProfileSelectionSurvivesRotationWithoutSale()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"credit-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CREDIT_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));click(fixture.getBoolean("owner")?"Walk-in Sale":"POS");await("saved profiles loaded",()->ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Saved customer (optional)")).getCount()>1));select("Saved customer (optional)",1);await("profile name filled",()->!field("Customer name").isEmpty());String selected=ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Saved customer (optional)")).getSelectedItem().toString());fill("Customer name","LOCAL UNSAVED PROFILE OVERRIDE");fill("Gallon quantity","1");fill("Cash received (₱)","0");JSONObject before=read("inventory",new JSONObject());active.recreate();await("profile restored",()->ui(a->{Spinner profile=a.findViewById(R.id.content).findViewWithTag("Saved customer (optional)");return profile!=null&&selected.equals(profile.getSelectedItem().toString());}));assertEquals("LOCAL UNSAVED PROFILE OVERRIDE",field("Customer name"));click("Continue to payment");dialogClick("Cancel");click("Cancel");assertEquals(before.toString(),read("inventory",new JSONObject()).toString());click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
