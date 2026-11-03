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
public class OwnProfileNegativeDeviceTest {
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
    @Test public void ownProfileValidationRotationAndCancelPreserveStableIdentity()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"own-profile-negative-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_OWN_PROFILE_NEGATIVE_UI",fixture.getString("kind"));
        try(ActivityScenario<MainActivity> active=ActivityScenario.launch(MainActivity.class)){
            scenario=active;boolean owner=fixture.optBoolean("owner");login(fixture.getString("username"),fixture.getString("password"),owner);click("Settings");click("My account");await("current account",()->hasButton("Edit profile"));assertTrue(hasText("Role: "+(owner?"Owner":"Staff")));assertTrue(hasText(fixture.getString("full_name")));click("Edit profile");assertEquals(fixture.getString("username"),field("Username"));assertEquals(fixture.getString("contact"),field("Contact number"));
            for(String label:new String[]{"Full name","Username","Contact number"}){String previous=field(label);fill(label,"");click("Save profile");await("required "+label,()->message("Complete the required fields."));fill(label,previous);}
            fill("Full name",new String(new char[161]).replace('\0','x'));click("Save profile");await("long name",()->message("Use 160 characters or fewer."));fill("Full name","LOCAL UNSAVED OWN PROFILE");for(String bad:new String[]{"0917 555","091755500000","abcdefghijk"}){fill("Contact number",bad);click("Save profile");await("invalid contact",()->message("Please enter a valid contact number. Use 11 digits."));}fill("Contact number",fixture.getString("contact"));
            for(String bad:new String[]{"ab","invalid user@!"}){fill("Username",bad);click("Save profile");await("server rejects invalid alias",()->ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE)&&message("Enter a valid username."));}
            fill("Username",fixture.getString("duplicate_username"));click("Save profile");await("real duplicate username",()->ui(a->a.findViewById(R.id.loading).getVisibility()!=View.VISIBLE)&&message("Username already exists."));active.recreate();await("edit profile restored",()->hasButton("Save profile"));assertEquals("LOCAL UNSAVED OWN PROFILE",field("Full name"));assertEquals(fixture.getString("duplicate_username"),field("Username"));assertEquals(fixture.getString("contact"),field("Contact number"));click("Cancel");await("original account reloaded",()->hasButton("Edit profile"));assertTrue(hasText(fixture.getString("full_name")));assertTrue(hasText("Username: "+fixture.getString("username")));assertTrue(hasText("Contact number: "+fixture.getString("contact")));assertFalse(message("Profile updated."));click("Back to Settings");click("Log out");await("logout",()->hasButton("Sign in"));
        }
    }
}
