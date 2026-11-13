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
public class PartialCashDeviceTest {
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
    @Test public void actualSomeCashCreditThenFinalReceiptSettlesExactlyOnce()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"partial-cash-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_PARTIAL_CASH_UI",fixture.getString("kind"));resultFile=new File(directory,"partial-cash-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));click(fixture.getBoolean("owner")?"Walk-in Sale":"POS");await("real available stock",()->hasText("Filled available:"));long filled=read("inventory",new JSONObject()).getLong("physical_filled");fill("Customer name",fixture.getString("customer_name"));fill("Gallon quantity","1");fill("Cash received (₱)","abc");click("Continue to payment");await("nonnumeric cash rejected",()->message("Enter a valid cash amount."));noSale(filled);fill("Cash received (₱)","10.00");await("partial preview",()->hasText("Payment status: Partially Paid")&&hasText("Remaining credit: ₱25.00"));click("Continue to payment");dialogClick("Cancel");noSale(filled);assertEquals("10.00",field("Cash received (₱)"));click("Continue to payment");phase("submitting_partial_sale");dialogClick("Confirm and save");await("actual partially paid sale",()->hasText("Walk-in sale completed.")&&hasText("Payment status: Partially Paid")&&hasText("Balance: ₱25.00"));JSONArray rows=sales();assertEquals(1,rows.length());JSONObject sold=rows.getJSONObject(0);String reference=sold.getString("reference");result.put("sale_id",sold.getString("id"));result.put("reference",reference);JSONObject partial=sale(reference);assertEquals(3500,partial.getLong("total_centavos"));assertEquals(2500,partial.getLong("balance_centavos"));assertEquals("partially_paid",partial.getString("payment_status"));assertEquals(1,partial.getJSONArray("payments").length());assertEquals(1000,partial.getJSONArray("payments").getJSONObject(0).getLong("amount_centavos"));assertEquals(filled-1,read("inventory",new JSONObject()).getLong("physical_filled"));phase("partial_sale_verified");payment(reference);assertTrue(hasText("Outstanding balance: ₱25.00"));fill("Payment amount (₱)","25.00");phase("submitting_final_receipt");click("Save Payment");await("actual final receipt",()->hasText("Payment recorded.")&&hasText("Payment status: Paid")&&hasText("Balance: ₱0.00"));JSONObject done=sale(reference);assertEquals(0,done.getLong("balance_centavos"));assertEquals("paid",done.getString("payment_status"));JSONArray receipts=done.getJSONArray("payments");assertEquals(2,receipts.length());long sum=0;for(int i=0;i<receipts.length();i++)sum+=receipts.getJSONObject(i).getLong("amount_centavos");assertEquals(3500,sum);assertNotEquals(receipts.getJSONObject(0).getString("reference"),receipts.getJSONObject(1).getString("reference"));assertEquals(filled-1,read("inventory",new JSONObject()).getLong("physical_filled"));click("Done");assertEquals(reference,field("Find customer, Order ID or Walk-in credit reference"));click("Payment history");for(int i=0;i<receipts.length();i++){String receipt=receipts.getJSONObject(i).getString("reference");await("saved receipt history",()->hasText(receipt));}result.put("completed",true);phase("completed");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
