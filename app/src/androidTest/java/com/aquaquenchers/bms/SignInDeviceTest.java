package com.aquaquenchers.bms;
import android.test.ActivityInstrumentationTestCase2;
import android.view.*;
import android.widget.*;
/** Native device validation. Run with connectedDebugAndroidTest; not claimed executed on host. */
public class SignInDeviceTest extends ActivityInstrumentationTestCase2<MainActivity> {
 public SignInDeviceTest(){super(MainActivity.class);}
 public void testMissingUsernameNeverOpensProtectedNavigation() throws Throwable {
  MainActivity activity=getActivity();getInstrumentation().waitForIdleSync();
  LinearLayout navigation=activity.findViewById(R.id.navigation);assertEquals(0,navigation.getChildCount());
  Button signIn=findButton(activity.findViewById(R.id.content),"Sign in");assertNotNull(signIn);
  runTestOnUiThread(()->signIn.performClick());getInstrumentation().waitForIdleSync();
  TextView message=activity.findViewById(R.id.message);assertEquals("Enter your username.",message.getText().toString());assertEquals(0,navigation.getChildCount());
 }
 private Button findButton(View v,String text){if(v instanceof Button&&text.equals(((Button)v).getText().toString()))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
}
