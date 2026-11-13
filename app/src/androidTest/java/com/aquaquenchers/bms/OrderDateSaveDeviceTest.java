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
public class OrderDateSaveDeviceTest {
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
    private void inspectCalendar(AccessibilityNodeInfo n,String month){if(n==null)return;String d=String.valueOf(n.getContentDescription()),t=String.valueOf(n.getText());if(n.getContentDescription()!=null||t.matches("[0-9]{1,2}"))System.out.println("CALENDAR NODE: description="+d+" numericText="+(t.matches("[0-9]{1,2}")?t:"")+" clickable="+n.isClickable()+" enabled="+n.isEnabled()+" actions="+n.getActions());for(int i=0;i<n.getChildCount();i++)inspectCalendar(n.getChild(i),month);}
    private void chooseDate(String value){
        String previous=field("Order date");ui(a->{((EditText)a.findViewById(R.id.content).findViewWithTag("Order date")).performClick();return null;});
        java.time.LocalDate from=java.time.LocalDate.parse(previous),to=java.time.LocalDate.parse(value);
        long months=java.time.temporal.ChronoUnit.MONTHS.between(from.withDayOfMonth(1),to.withDayOfMonth(1));
        for(long i=0;i<Math.abs(months);i++){String direction=months>0?"Next month":"Previous month";AccessibilityNodeInfo control=awaitNode(direction,n->n.getContentDescription()!=null&&direction.contentEquals(n.getContentDescription())&&n.isClickable());assertTrue(control.performAction(AccessibilityNodeInfo.ACTION_CLICK));SystemClock.sleep(150);}
        String month=to.getMonth().getDisplayName(java.time.format.TextStyle.FULL,java.util.Locale.US);String day="(?s).*\\b0?"+to.getDayOfMonth()+"\\b.*";
        AccessibilityNodeInfo cell;try{cell=awaitNode("calendar day "+value,n->{String d=String.valueOf(n.getContentDescription());return (n.getActions()&AccessibilityNodeInfo.ACTION_CLICK)!=0&&n.isEnabled()&&d.contains(month)&&d.contains(String.valueOf(to.getYear()))&&d.matches(day);});}catch(AssertionError failure){inspectCalendar(automation().getRootInActiveWindow(),month);throw failure;}assertTrue(cell.performAction(AccessibilityNodeInfo.ACTION_CLICK));dialogClick("OK");await("selected date",()->value.equals(field("Order date")));
    }
    @Test public void realPastAndFutureSavesKeepServerEncodingDate()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"order-date-save-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_ORDER_DATE_SAVE_UI",fixture.getString("kind"));File progress=new File(directory,"order-date-save-result.json");JSONObject result=new JSONObject().put("run_id",fixture.getString("run_id"));JSONArray saved=new JSONArray();
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),fixture.getBoolean("owner"));JSONArray dates=fixture.getJSONArray("dates");for(int i=0;i<dates.length();i++){String date=dates.getString(i);click("Orders");click("Create Order");assertEquals("New form defaults to today even after previous confirmed save",fixture.getString("today"),field("Order date"));await("customer loaded",()->ui(a->((Spinner)a.findViewById(R.id.content).findViewWithTag("Customer")).getCount()>1));ui(a->{Spinner customer=a.findViewById(R.id.content).findViewWithTag("Customer");int index=-1;for(int n=1;n<customer.getCount();n++)if(customer.getItemAtPosition(n).toString().startsWith(fixture.optString("customer_name")+" · "))index=n;assertTrue(index>0);customer.setSelection(index);return null;});fill("Gallon quantity","2");await("Dealer price",()->hasText("Unit price: ₱30.00")&&hasText("Total: ₱60.00"));chooseDate(date);result.put("submitting_date",date);result.put("saved_dates",saved);Files.write(progress.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));click("Save Order");await("actual dated save",()->message("Order saved.")&&hasText("Order date: "+date));assertTrue(hasText("Recorded by: "+fixture.getString("full_name")));assertTrue(hasText("Order total: ₱60.00"));assertTrue(hasText("Payment status: Unpaid"));saved.put(date);result.put("saved_dates",saved);Files.write(progress.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));}result.put("completed",true);Files.write(progress.toPath(),result.toString().getBytes(StandardCharsets.UTF_8));click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
