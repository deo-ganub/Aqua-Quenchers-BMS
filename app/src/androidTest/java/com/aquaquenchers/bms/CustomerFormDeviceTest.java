package com.aquaquenchers.bms;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.UiAutomation;
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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real HTTPS/local official Auth + REST. Refuses an absent or nonlocal fixture.
 * No session injection, fake saves, production credentials or real-model claims.
 */
@RunWith(AndroidJUnit4.class)
public class CustomerFormDeviceTest {
    private ActivityScenario<MainActivity> scenario;
    private JSONObject fixture;

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
    private void login(String username,String password,boolean owner){await("Sign in",()->hasButton("Sign in"));fill("Username",username);fill("Password",password);click("Sign in");await("authenticated navigation",()->ui(a->a.findViewById(R.id.navigation) instanceof ViewGroup&&((ViewGroup)a.findViewById(R.id.navigation)).getChildCount()==(owner?7:5)));assertEquals(owner,ui(MainActivity::isOwner));}
    private UiAutomation automation(){UiAutomation u=InstrumentationRegistry.getInstrumentation().getUiAutomation();AccessibilityServiceInfo info=u.getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;u.setServiceInfo(info);return u;}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,java.util.function.Predicate<AccessibilityNodeInfo> predicate){if(root==null)return null;if(predicate.test(root))return root;for(int i=0;i<root.getChildCount();i++){AccessibilityNodeInfo match=node(root.getChild(i),predicate);if(match!=null)return match;}return null;}
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){long end=SystemClock.elapsedRealtime()+20000;while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(automation().getRootInActiveWindow(),predicate);if(found!=null)return found;SystemClock.sleep(100);}throw new AssertionError("Missing dialog/system control: "+label);}
    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    @Test public void invalidDuplicateAndCancelledCreationNeverChangesCustomers()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"customer-form-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_CUSTOMER_FORM_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;boolean owner=fixture.optBoolean("owner");login(fixture.getString("username"),fixture.getString("password"),owner);click("Customers");click("Add Customer");click("Save Customer");await("required details",()->message("Complete the required fields."));
            fill("Full name","LOCAL UNSAVED CUSTOMER");fill("Address",fixture.getString("address"));fill("Contact number",fixture.getString("duplicate_contact"));select("Customer type",0);click("Save Customer");await("required type",()->message("Choose Household, Dealer, or Walk-in."));select("Customer type",1);
            for(String bad:new String[]{"","0917 555","091755500000","abcdefghijk"}){fill("Contact number",bad);click("Save Customer");String expected=bad.isEmpty()?"Complete the required fields.":"Please enter a valid contact number. Use 11 digits.";await("invalid contact "+bad,()->message(expected));assertTrue(hasButton("Save Customer"));}
            fill("Contact number",fixture.getString("duplicate_contact"));fill("Address","");click("Save Customer");await("required address",()->message("Complete the required fields."));fill("Address",fixture.getString("address"));fill("Full name","");click("Save Customer");await("required name",()->message("Complete the required fields."));fill("Full name","LOCAL UNSAVED CUSTOMER");
            for(int position:new int[]{1,2,3}){select("Customer type",position);click("Save Customer");await("actual duplicate rejection",()->ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE)&&message("Customer already exists."));assertEquals("LOCAL UNSAVED CUSTOMER",field("Full name"));assertEquals(fixture.getString("duplicate_contact"),field("Contact number"));}
            String contact=fixture.getString("duplicate_contact"),spaced=contact.substring(0,4)+" "+contact.substring(4,7)+" "+contact.substring(7);fill("Contact number",spaced);click("Save Customer");await("display spaces normalized before duplicate check",()->ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE)&&message("Customer already exists."));
            active.recreate();await("creation draft restored",()->hasButton("Save Customer"));assertEquals("LOCAL UNSAVED CUSTOMER",field("Full name"));assertEquals(spaced,field("Contact number"));assertEquals(fixture.getString("address"),field("Address"));assertEquals(3,(int)ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Customer type")).getSelectedItemPosition()));click("Cancel");await("cancel returns customers",()->hasButton("Add Customer"));assertFalse(message("Customer saved."));click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
