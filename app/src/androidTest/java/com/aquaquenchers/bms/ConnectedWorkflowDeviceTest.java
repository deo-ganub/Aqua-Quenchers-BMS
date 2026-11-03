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
public class ConnectedWorkflowDeviceTest {
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
    private String totp(long at)throws Exception{String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",secret=fixture.getString("owner_totp_secret").replace("=","").toUpperCase(java.util.Locale.ROOT);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();int bits=0,value=0;for(char c:secret.toCharArray()){int index=chars.indexOf(c);assertTrue(index>=0);value=(value<<5)|index;bits+=5;if(bits>=8){bits-=8;bytes.write((value>>bits)&255);}}byte[] counter=java.nio.ByteBuffer.allocate(8).putLong(at/30).array();Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(bytes.toByteArray(),"HmacSHA1"));byte[] h=mac.doFinal(counter);int offset=h[h.length-1]&15;int code=((h[offset]&127)<<24)|((h[offset+1]&255)<<16)|((h[offset+2]&255)<<8)|(h[offset+3]&255);return String.format(java.util.Locale.ROOT,"%06d",code%1000000);}
    private JSONObject query(String resource){AtomicReference<JSONObject> reply=new AtomicReference<>();ui(a->{a.query(resource,a.json(),reply::set);return null;});await("real "+resource+" reply",()->reply.get()!=null);return reply.get();}
    private void confirmTransition(String action,String next){click(action);dialogClick("Save");await(next,()->hasButton(next));}
    private void awaitPdfSaved(MainActivity activity){long end=SystemClock.elapsedRealtime()+30000;while(SystemClock.elapsedRealtime()<end){AtomicReference<Boolean> saved=new AtomicReference<>(false);InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{TextView m=activity.findViewById(R.id.message);saved.set(m.getVisibility()==View.VISIBLE&&m.getText().toString().startsWith("Report exported successfully."));});if(saved.get())return;SystemClock.sleep(100);}fail("PDF stream was not acknowledged as saved.");}

    @Test public void ownerAndStaffUseRealBackendAndManagedMfa()throws Exception{
        assertEquals("Refusing nonlocal build", "https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);
        assertTrue("Connected fixture requires debug-only user CA trust",BuildConfig.DEBUG);
        android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        fixture=new JSONObject(new String(Files.readAllBytes(new File(context.getFilesDir(),"connected-test-credentials.json").toPath()),StandardCharsets.UTF_8));
        assertEquals("SYNTHETIC_LOCAL_CONNECTED_UI",fixture.getString("kind"));
        resultFile=new File(context.getFilesDir(),"connected-test-result.json");
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;phase("owner_login");login(fixture.getString("owner_username"),fixture.getString("owner_password"),true);
            click("Expenses");click("Add Expense");select("Category",1);fill("Amount","50.00");click("Suggest category");await("blank suggestion validation",()->message("Enter remarks to request a suggestion."));
            fill("Remarks (optional)","gasolina para sa motor");click("Suggest category");await("missing reviewed model fallback",()->hasText("Category suggestion unavailable. Choose a category manually."));fill("Remarks (optional)","");
            active.recreate();await("expense restoration",()->hasButton("Save Expense"));assertEquals("50.00",field("Amount"));assertEquals(1,(int)ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Category")).getSelectedItemPosition()));
            click("Save Expense");await("expense saved",()->message("Expense saved."));phase("expense_saved");
            click("Reports");click("Generate Report");JSONObject before=fixture.getJSONObject("baseline_today");long expense=before.getLong("expenses_centavos")+5000;
            await("real report",()->hasText("Expenses: "+Rules.money(expense)));assertTrue(hasText("Revenue: "+Rules.money(before.getLong("revenue_centavos"))));assertTrue(hasText("Payments received: "+Rules.money(before.getLong("payments_received_centavos"))));assertTrue(hasText("Net income: "+Rules.money(before.getLong("payments_received_centavos")-expense)));
            click("Open Report Preview");assertTrue(hasText("Report Preview"));MainActivity exportActivity=ui(a->a);click("Export PDF");
            AccessibilityNodeInfo filename=awaitNode("PDF filename",v->"android.widget.EditText".contentEquals(v.getClassName())&&v.isEditable());Bundle nameArgs=new Bundle();nameArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,fixture.getString("pdf_filename"));assertTrue(filename.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,nameArgs));dialogClick("Save");
            awaitPdfSaved(exportActivity);InstrumentationRegistry.getInstrumentation().waitForIdleSync();SystemClock.sleep(500);
            if(scenario.getState()!=androidx.lifecycle.Lifecycle.State.RESUMED)automation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
            await("return from PDF viewer",()->scenario.getState()==androidx.lifecycle.Lifecycle.State.RESUMED);assertTrue("Expected Report Preview after viewer return",hasButton("Back to Reports"));result.put("pdf_filename",fixture.getString("pdf_filename"));phase("pdf_exported");click("Back to Reports");
            click("Settings");click("Staff accounts");click("Create Staff account");fill("Full name",fixture.getString("new_staff_name"));fill("Username",fixture.getString("new_staff_username"));fill("Contact number",fixture.getString("new_staff_contact"));fill("Temporary password",fixture.getString("new_staff_password"));click("Save Staff Account");
            long now=System.currentTimeMillis()/1000;java.util.Set<String> valid=new java.util.HashSet<>();valid.add(totp(now-30));valid.add(totp(now));valid.add(totp(now+30));String invalid="000000";for(int i=0;valid.contains(invalid);i++)invalid=String.format(java.util.Locale.ROOT,"%06d",i+1);dialogFill("Verification code",invalid);dialogClick("Verify and save");await("real MFA422 mapping",()->message("Enter a valid verification code."));
            JSONArray accounts=query("staff").getJSONArray("items");for(int i=0;i<accounts.length();i++)assertNotEquals(fixture.getString("new_staff_username"),accounts.getJSONObject(i).getString("username"));
            click("Save Staff Account");dialogFill("Verification code",totp(System.currentTimeMillis()/1000));dialogClick("Verify and save");await("managed MFA Staff save",()->message("Staff account saved."));phase("managed_staff_created");
            click("Settings");click("Log out");await("Owner logout",()->hasButton("Sign in"));
            login(fixture.getString("staff_username"),fixture.getString("staff_password"),false);assertFalse(hasButton("Expenses"));assertFalse(hasButton("Reports"));phase("staff_login");
            click("Customers");click("Add Customer");fill("Full name",fixture.getString("customer_name"));fill("Contact number",fixture.getString("customer_contact"));fill("Address","LOCAL SYNTHETIC CONNECTED TEST");click("Save Customer");await("required type",()->message("Choose Household, Dealer, or Walk-in."));select("Customer type",1);click("Save Customer");await("customer save",()->message("Customer saved."));
            fill("Search by name or contact number",fixture.getString("customer_name"));click("Search");await("customer search",()->hasText(fixture.optString("customer_name")));click("View customer");click("Create Order");await("selected new customer",()->ui(a->{Spinner s=a.findViewById(R.id.content).findViewWithTag("Customer");return s!=null&&s.getSelectedItem()!=null&&s.getSelectedItem().toString().contains(fixture.optString("customer_name"));}));fill("Gallon quantity","2");click("Save Order");await("order save",()->hasButton("For Pickup"));
            confirmTransition("For Pickup","Record Pickup");click("Record Pickup");fill("Actual collected gallons","2");click("Save Pickup");await("pickup save",()->hasButton("Start Refilling"));confirmTransition("Start Refilling","Ready for Delivery");confirmTransition("Ready for Delivery","Record Delivery");
            click("Record Delivery");fill("Gallons delivered this trip","1");click("Save Delivery");await("partial delivery",()->hasText("Pending: 1"));click("Record Delivery");fill("Gallons delivered this trip","1");click("Save Delivery");await("final delivery",()->hasText("Status: Delivered"));phase("order_delivered");
            click("Record Payment");fill("Payment amount (₱)","71.00");click("Save Payment");await("overpayment",()->message("Payment cannot exceed the ₱70.00 balance."));fill("Payment amount (₱)","20.00");click("Save Payment");await("payment save",()->hasText("Payment recorded."));click("Done");await("partial payment",()->hasText("Balance: ₱50.00"));
            click("POS");fill("Customer name",fixture.getString("counter_name"));fill("Gallon quantity","1");fill("Cash received (₱)","50.00");click("Continue to payment");dialogClick("Complete Sale");await("POS save",()->hasText("Walk-in sale completed."));assertTrue(hasText("Payment status: Paid"));phase("payment_and_pos_saved");
            click("Settings");assertTrue(hasButton("Orders"));assertTrue(hasButton("Pending gallons"));click("My account");await("own account",()->hasText("Role: Staff"));click("Edit profile");fill("Username",fixture.getString("renamed_staff_username"));click("Save profile");await("profile save",()->message("Profile updated."));result.put("staff_username",fixture.getString("renamed_staff_username"));phase("profile_updated");
            click("Change password");fill("Current password","incorrect current password");fill("New password",fixture.getString("updated_staff_password"));fill("Confirm password","different long confirmation");click("Save password");await("password mismatch",()->message("New passwords do not match."));fill("Confirm password",fixture.getString("updated_staff_password"));click("Save password");await("incorrect current password",()->message("Current password is incorrect."));fill("Current password",fixture.getString("staff_password"));click("Save password");await("managed password update",()->message("Password updated."));result.put("new_password_applied",true);phase("password_updated");
            fill("Username",fixture.getString("renamed_staff_username"));fill("Password",fixture.getString("staff_password"));click("Sign in");await("old password rejected",()->message("Invalid username or password."));login(fixture.getString("renamed_staff_username"),fixture.getString("updated_staff_password"),false);click("Settings");click("Log out");await("Staff logout",()->hasButton("Sign in"));assertEquals(0,(int)ui(a->((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()));result.put("completed",true);phase("completed");
        }
    }
}
