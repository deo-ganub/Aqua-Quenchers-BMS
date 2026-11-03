package com.aquaquenchers.classifier;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Synthetic numerical/runtime compatibility only; no station quality or timing claim. */
@RunWith(AndroidJUnit4.class)
public class ClassifierParityDeviceTest {
    private byte[] asset(String name) throws Exception {
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(name);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096];int count;
            while((count=in.read(buffer))!=-1)out.write(buffer,0,count);
            return out.toByteArray();
        }
    }

    @Test @SuppressWarnings("unchecked") public void pythonGoldenVectorsMatchOnAndroid() throws Exception {
        Map<String,Object> golden=(Map<String,Object>)StrictJson.parse(new String(asset("golden.json"),StandardCharsets.UTF_8));
        byte[] bytes=asset("synthetic-model.json");
        Model model=Model.load(bytes,(String)golden.get("sha256"),true);
        ExpenseCategoryClassifier classifier=new ExpenseCategoryClassifier(model);
        List<?> cases=(List<?>)golden.get("cases");assertEquals(8,cases.size());
        for(Object item:cases){
            Map<String,Object> vector=(Map<String,Object>)item;
            String remarks=(String)vector.get("remarks");
            List<String> tokens=TextFeatures.tokens(remarks);assertEquals(vector.get("tokens"),tokens);
            Map<Integer,Double> actual=TextFeatures.vector(tokens,model);
            List<?> expected=(List<?>)vector.get("features");assertEquals(expected.size(),actual.size());
            for(Object feature:expected){
                List<?> pair=(List<?>)feature;int index=((Number)pair.get(0)).intValue();
                assertTrue(actual.containsKey(index));assertEquals(((Number)pair.get(1)).doubleValue(),actual.get(index),1e-6);
            }
            if(vector.get("probabilities")!=null){
                double[] probabilities=classifier.probabilities(actual);List<?> wanted=(List<?>)vector.get("probabilities");
                assertEquals(7,probabilities.length);
                for(int i=0;i<7;i++)assertEquals(((Number)wanted.get(i)).doubleValue(),probabilities[i],1e-6);
            }
            ExpenseCategoryClassifier.Result result=classifier.suggest(remarks);
            assertEquals(vector.get("status"),result.status.name());assertEquals(vector.get("category"),result.suggestedCategory);
            assertEquals(vector.get("reason"),result.reason.name());
        }
        assertEquals(ExpenseCategoryClassifier.Status.UNAVAILABLE,
                ExpenseCategoryClassifier.fromPackage(bytes,Model.digest(bytes),false).suggest("fuel").status);
    }
}
