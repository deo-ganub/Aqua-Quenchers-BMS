package com.aquaquenchers.bms;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import com.aquaquenchers.classifier.InferenceBenchmark;
import java.util.*;
import org.json.JSONObject;
/** Run separately with an approved model on the declared Owner tablet. No host timing claim. */
@RunWith(AndroidJUnit4.class)
public class ClassifierDeviceBenchmark {
 @Test public void measureOnDeclaredOwnerTablet() throws Exception {
  android.os.Bundle args=InstrumentationRegistry.getArguments();String declared=args.getString("owner_tablet_id","");if(declared.isEmpty())throw new AssertionError("Declare owner_tablet_id; benchmark NOT RUN.");
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  List<String> inputs=Arrays.asList("gasolina para sa motor","electricity water maintenance supplies salary","fuel motor water","unknown vocabulary");
  Map<String,Object> r=InferenceBenchmark.measure(()->ClassifierAssets.load(context),inputs,declared+" / "+Build.MANUFACTURER+" "+Build.MODEL+" / API"+Build.VERSION.SDK_INT);
  try(java.io.OutputStream out=context.openFileOutput("classifier-device-benchmark.json",0)){out.write(new JSONObject(r).toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  org.junit.Assert.assertEquals(Boolean.TRUE,r.get("latency_gate_pass"));org.junit.Assert.assertEquals(Boolean.TRUE,r.get("heap_gate_pass"));
 }
}
