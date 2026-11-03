package com.aquaquenchers.bms;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Requires a real, already-exhausted synthetic login bucket; never resets it. */
@RunWith(AndroidJUnit4.class)
public class LoginRateLimitDeviceTest {
    private Button button(View root){if(root instanceof Button&&"Sign in".contentEquals(((Button)root).getText()))return (Button)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){Button b=button(((ViewGroup)root).getChildAt(i));if(b!=null)return b;}return null;}
    @Test public void actualRateLimitKeepsSignInAndReportsAvailability()throws Exception{
        assertTrue(BuildConfig.DEBUG);assertEquals("https://127.0.0.1:55436",BuildConfig.SUPABASE_URL);
        File directory=InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir();
        JSONObject fixture=new JSONObject(new String(Files.readAllBytes(new File(directory,"operational-test-credentials.json").toPath()),StandardCharsets.UTF_8));assertEquals("SYNTHETIC_LOCAL_OPERATIONAL_UI",fixture.getString("kind"));assertTrue(fixture.getBoolean("owner"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(a->{((EditText)a.findViewById(R.id.content).findViewWithTag("Username")).setText(fixture.optString("username"));((EditText)a.findViewById(R.id.content).findViewWithTag("Password")).setText(fixture.optString("password"));button(a.getWindow().getDecorView()).performClick();});
            AtomicReference<String> text=new AtomicReference<>("");long deadline=SystemClock.elapsedRealtime()+30000;
            while(text.get().isEmpty()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->text.set(((TextView)a.findViewById(R.id.message)).getText().toString()));SystemClock.sleep(100);}
            assertEquals("Sign-in unavailable. Try again later.",text.get());
            scenario.onActivity(a->{assertEquals(0,((ViewGroup)a.findViewById(R.id.navigation)).getChildCount());assertNotNull(button(a.getWindow().getDecorView()));assertEquals(fixture.optString("username"),((EditText)a.findViewById(R.id.content).findViewWithTag("Username")).getText().toString());});
        }
    }
}
