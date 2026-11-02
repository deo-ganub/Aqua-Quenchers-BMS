package com.aquaquenchers.bms;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Native device validation; compilation is not a claim of device execution. */
@RunWith(AndroidJUnit4.class)
public class SignInDeviceTest {
 @Test public void missingUsernameNeverOpensProtectedNavigation(){
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   scenario.onActivity(activity->{LinearLayout navigation=activity.findViewById(R.id.navigation);assertEquals(0,navigation.getChildCount());Button signIn=findButton(activity.findViewById(R.id.content),"Sign in");assertNotNull(signIn);signIn.performClick();TextView message=activity.findViewById(R.id.message);assertEquals("Enter your username.",message.getText().toString());assertEquals(0,navigation.getChildCount());});
  }
 }
 private Button findButton(View v,String text){if(v instanceof Button&&text.equals(((Button)v).getText().toString()))return (Button)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){Button b=findButton(g.getChildAt(i),text);if(b!=null)return b;}}return null;}
}
