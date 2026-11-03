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
public class PdfCancellationDeviceTest {
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
    private AccessibilityNodeInfo awaitNode(String label,java.util.function.Predicate<AccessibilityNodeInfo> predicate){UiAutomation service=automation();long end=SystemClock.elapsedRealtime()+30000;java.util.Set<String> observed=new java.util.HashSet<>();while(SystemClock.elapsedRealtime()<end){AccessibilityNodeInfo found=node(service.getRootInActiveWindow(),predicate);if(found!=null)return found;for(android.view.accessibility.AccessibilityWindowInfo window:service.getWindows()){AccessibilityNodeInfo root=window.getRoot();if(root!=null){observed.add(String.valueOf(root.getPackageName()));found=node(root,predicate);if(found!=null)return found;}}SystemClock.sleep(100);}throw new AssertionError("Missing control: "+label+"; observed packages: "+observed);}

    private void dialogClick(String label){AccessibilityNodeInfo n=awaitNode(label,v->v.isClickable()&&v.isEnabled()&&v.getText()!=null&&label.equalsIgnoreCase(v.getText().toString()));assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));}
    private void dialogFill(String label,String value){AccessibilityNodeInfo n=awaitNode(label,v->"android.widget.EditText".contentEquals(v.getClassName())&&v.getContentDescription()!=null&&label.contentEquals(v.getContentDescription()));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args));}
    @Test public void realDocumentPickerCancelNeverAcknowledgesExport()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"financial-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_FINANCIAL_READ_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),true);click("Reports");fill("Start date",fixture.getString("receipt_date"));fill("End date",fixture.getString("receipt_date"));click("Generate Report");await("generated report",()->hasText("Generated:"));click("Open Report Preview");click("Export PDF");awaitNode("actual document picker",v->v.getPackageName()!=null&&"com.android.documentsui".contentEquals(v.getPackageName()));assertTrue(automation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));SystemClock.sleep(1000);if(scenario.getState()!=androidx.lifecycle.Lifecycle.State.RESUMED)assertTrue(automation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));await("return after cancellation",()->scenario.getState()==androidx.lifecycle.Lifecycle.State.RESUMED);assertTrue(hasButton("Back to Reports"));assertNull(ui(MainActivity::pendingPdf));assertFalse(hasText("Report exported successfully."));active.recreate();await("preview restored",()->hasButton("Export PDF"));assertFalse(hasText("Report exported successfully."));click("Back to Reports");assertEquals(fixture.getString("receipt_date"),field("Start date"));click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
    @Test public void controlledOutputFailureNeverAcknowledgesSave()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"financial-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_FINANCIAL_READ_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;login(fixture.getString("username"),fixture.getString("password"),true);click("Reports");fill("Start date",fixture.getString("receipt_date"));fill("End date",fixture.getString("receipt_date"));click("Generate Report");await("generated report",()->hasText("Generated:"));click("Open Report Preview");MainActivity activity=ui(a->a);click("Export PDF");awaitNode("actual document picker",v->v.getPackageName()!=null&&"com.android.documentsui".contentEquals(v.getPackageName()));
            // Controlled negative platform-result fixtures. No fake Auth, report,
            // successful export or business mutation is substituted.
            File missing=new File(directory,"missing-output-"+java.util.UUID.randomUUID());assertFalse(missing.exists());android.net.Uri[] denied={android.net.Uri.fromFile(new File(missing,"report.pdf")),android.net.Uri.parse("content://com.android.contacts/contacts/1/photo")};
            assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED,activity.checkSelfPermission(android.Manifest.permission.WRITE_CONTACTS));
            for(android.net.Uri uri:denied){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{try(java.io.OutputStream unexpected=activity.getContentResolver().openOutputStream(uri)){fail("Negative output fixture unexpectedly accessible");}catch(java.io.IOException expected){assertEquals("file",uri.getScheme());}catch(SecurityException expected){assertEquals("content",uri.getScheme());}activity.onActivityResult(201,android.app.Activity.RESULT_OK,new android.content.Intent().setData(uri));assertEquals("PDF export failed. Try again.",((TextView)activity.findViewById(R.id.message)).getText().toString());assertNotNull("Failed export must retain retry payload",activity.pendingPdf());});}
            assertFalse(missing.exists());assertTrue(automation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));SystemClock.sleep(1000);if(scenario.getState()!=androidx.lifecycle.Lifecycle.State.RESUMED)assertTrue(automation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));await("return after failure/cancel",()->scenario.getState()==androidx.lifecycle.Lifecycle.State.RESUMED);assertNull(ui(MainActivity::pendingPdf));assertFalse(hasText("Report exported successfully."));assertTrue(hasButton("Export PDF"));click("Back to Reports");click("Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
