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
public class ManualExpenseValidationDeviceTest {
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
    @Test public void actualManualValidationUnicodeBoundaryAndUnavailableModelKeepInputs()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"manual-expense-validation-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_MANUAL_EXPENSE_VALIDATION_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),true);click("Expenses");click("Add Expense");assertTrue(hasText("Recorded by: "+fixture.getString("full_name")));String[] categories={"Choose category","Fuel","Electricity","Water","Maintenance","Supplies","Salary","Others"};ui(a->{Spinner category=a.findViewById(R.id.content).findViewWithTag("Category");assertEquals(categories.length,category.getCount());for(int i=0;i<categories.length;i++)assertEquals(categories[i],category.getItemAtPosition(i).toString());return null;});select("Category",7);fill("Amount","2.50");fill("Date","");click("Save Expense");await("required date",()->message("Complete the required fields."));fill("Date",fixture.getString("today"));select("Category",0);click("Save Expense");await("required category",()->message("Complete the required fields."));select("Category",7);for(String bad:new String[]{"0","-1","abc","0.001"}){fill("Amount",bad);click("Save Expense");await("invalid amount",()->message("Enter a valid expense amount."));}fill("Amount","2.50");fill("Remarks (optional)","");click("Suggest category");await("blank suggestion remarks",()->message("Enter remarks to request a suggestion."));StringBuilder unicode=new StringBuilder();for(int i=0;i<501;i++)unicode.append("😀");fill("Remarks (optional)",unicode.toString());for(String action:new String[]{"Suggest category","Save Expense"}){click(action);await("501Unicode rejected",()->message("Use 500 characters or fewer."));assertEquals(unicode.toString(),field("Remarks (optional)"));}String boundary=unicode.substring(0,1000);assertEquals(500,boundary.codePointCount(0,boundary.length()));fill("Remarks (optional)",boundary);click("Suggest category");await("actual unavailable model preserves manual entry",()->hasText("Category suggestion unavailable. Choose a category manually."));assertFalse("Obsolete501-character validation must clear after a valid request",message("Use 500 characters or fewer."));assertEquals(boundary,field("Remarks (optional)"));assertEquals("2.50",field("Amount"));assertEquals(fixture.getString("today"),field("Date"));assertEquals(7,(int)ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Category")).getSelectedItemPosition()));assertFalse(hasButton("Save suggestion"));assertFalse(message("Expense saved."));click("Cancel");await("expense list",()->hasButton("Add Expense"));click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
