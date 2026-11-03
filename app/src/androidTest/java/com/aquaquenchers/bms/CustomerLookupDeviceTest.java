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
public class CustomerLookupDeviceTest {
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
    private int receiptCount(View root){int count=root instanceof TextView&&!(root instanceof EditText)&&((TextView)root).getText().toString().contains("Related reference:")?1:0;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)count+=receiptCount(((ViewGroup)root).getChildAt(i));return count;}
    private int customers(View root){int n=root instanceof Button&&"View customer".contentEquals(((Button)root).getText())?1:0;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)n+=customers(((ViewGroup)root).getChildAt(i));return n;}
    private void lookup(String value){click("Customers");fill("Search by name or contact number",value);click("Search");await("one exact customer",()->ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE&&customers(a.getWindow().getDecorView())==1));}
    @Test public void actualNameContactEmptyLookupProfileHistoryAndNoPreviousQuantity()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"customer-lookup-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CUSTOMER_LOOKUP_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;
            boolean owner=fixture.getBoolean("owner");
            login(fixture.getString("username"),fixture.getString("password"),owner);
            JSONObject customer=fixture.getJSONObject("history"),empty=fixture.getJSONObject("empty");
            lookup(customer.getString("name"));
            assertTrue(hasText(customer.getString("name")));
            assertTrue(hasText(customer.getString("contact_number")));
            assertTrue(hasText(Rules.label(customer.getString("customer_type"))));
            assertTrue(hasText("Balance "+Rules.money(customer.getLong("balance_centavos"))));
            assertEquals(owner,hasText("Status: Active"));
            click("View customer");
            await("history loaded",()->hasButton("Edit Customer")&&ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE));
            for(String key:new String[]{"name","contact_number","address"})assertTrue(hasText(customer.getString(key)));
            assertTrue(hasText("Outstanding balance: "+Rules.money(customer.getLong("balance_centavos"))));
            assertEquals(owner,hasButton("Apply history dates"));
            JSONArray orders=customer.getJSONArray("orders"),payments=customer.getJSONArray("payments");
            for(int i=0;
            i<orders.length();
            i++){JSONObject o=orders.getJSONObject(i);
            assertTrue(hasText(o.getString("reference")));
            assertTrue(hasText(o.getInt("quantity")+" gallons"));
            assertTrue(hasText("Total "+Rules.money(o.getLong("total_centavos"))));
            assertTrue(hasText(Rules.label(o.getString("status"))+" · "+Rules.label(o.getString("payment_status"))));
            }for(int i=0;
            i<payments.length();
            i++){JSONObject r=payments.getJSONObject(i);
            assertTrue(hasText(r.getString("reference")));
            assertTrue(hasText(Rules.money(r.getLong("amount_centavos"))));
            assertTrue(hasText("Recorded by: "+r.getString("recorded_by_name")));
            assertTrue(hasText(Rules.timestamp(r.getString("received_at"))));
            }click("Create Order");
            await("existing most recent quantity",()->hasText("Most recent order quantity: "+customer.optInt("previous_order_quantity")+" gallons"));
            assertTrue(hasText("Delivery address: "+customer.getString("address")));
            click("Cancel");
            String contact=customer.getString("contact_number");
            lookup(contact.substring(0,4)+" "+contact.substring(4));
            click("View customer");
            await("profile navigation",()->hasButton("Record Payment"));
            click("View Order");
            await("selected order detail",()->hasText("Order total: "+Rules.money(orders.optJSONObject(0).optLong("total_centavos")))&&hasText(orders.optJSONObject(0).optString("reference")));
            lookup(contact);
            click("View customer");
            await("payment route",()->hasButton("Record Payment"));
            click("Record Payment");
            click("Payment history");
            await("scoped receipts",()->ui(a->receiptCount(a.getWindow().getDecorView()))==payments.length());
            for(int i=0;
            i<payments.length();
            i++)assertTrue(hasText(payments.getJSONObject(i).getString("reference")));
            click("Customers");
            fill("Search by name or contact number",fixture.getString("unknown"));
            click("Search");
            await("actual empty search",()->hasText("No customers found.")&&ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE));
            assertEquals(0,(int)ui(a->customers(a.getWindow().getDecorView())));
            lookup(empty.getString("name"));
            click("View customer");
            await("no-history profile",()->hasButton("Create Order"));
            click("Create Order");
            await("no previous order stays unavailable",()->hasText("Most recent order quantity: Not yet available"));
            assertFalse(hasText("Most recent order quantity: 0 gallons"));
            assertTrue(hasText("Delivery address: "+empty.getString("address")));
            fill("Gallon quantity","2");
            await("selected fixed price",()->hasText("Total: "+Rules.money(2*fixture.optLong("empty_unit"))));
            click("Cancel");
            click("Settings");
            click("Log out");
            await("logout",()->hasButton("Sign in"));
            
        }
    }
}
