package com.example.aquaquencherspos;
import static org.junit.Assert.assertEquals;
import com.aquaquenchers.classifier.ExpenseCategoryClassifier;
import org.junit.Test;
public class ClassifierAssetsTest {
    @Test public void missingApprovalKeepsManualEntry() {
        assertEquals(ExpenseCategoryClassifier.Status.UNAVAILABLE,ClassifierAssets.load(new byte[0],"").suggest("fuel").status);
    }
    @Test public void oversizedPackageIsUnavailable() {
        assertEquals(ExpenseCategoryClassifier.Status.UNAVAILABLE,ClassifierAssets.load(new byte[2*1024*1024+1],"0".repeat(64)).suggest("fuel").status);
    }
    @Test public void wrongDigestOrMalformedModelIsUnavailable() {
        assertEquals(ExpenseCategoryClassifier.Status.UNAVAILABLE,ClassifierAssets.load("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),"0".repeat(64)).suggest("fuel").status);
    }
}
