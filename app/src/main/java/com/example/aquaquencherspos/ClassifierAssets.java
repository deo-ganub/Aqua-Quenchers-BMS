package com.example.aquaquencherspos;

import android.content.Context;
import com.aquaquenchers.classifier.ExpenseCategoryClassifier;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Approved model bytes only. No raw training data, network inference or synthetic release fallback. */
final class ClassifierAssets {
    private static ExpenseCategoryClassifier cached;
    static ExpenseCategoryClassifier load(byte[] bytes,String digest) {
        if (bytes==null || bytes.length>2*1024*1024 || digest==null || !digest.matches("[a-fA-F0-9]{64}"))
            return ExpenseCategoryClassifier.unavailable();
        return ExpenseCategoryClassifier.fromPackage(bytes,digest,false);
    }
    static synchronized ExpenseCategoryClassifier get(Context context) {
        if (cached!=null) return cached;
        if (BuildConfig.CLASSIFIER_SHA256.isEmpty()) return cached=ExpenseCategoryClassifier.unavailable();
        try (InputStream input=context.getAssets().open("expense_model.json")) {
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int n;
            while ((n=input.read(chunk))!=-1) {
                if (out.size()+n>2*1024*1024) return cached=ExpenseCategoryClassifier.unavailable();
                out.write(chunk,0,n);
            }
            return cached=load(out.toByteArray(),BuildConfig.CLASSIFIER_SHA256);
        } catch (Exception e) { return cached=ExpenseCategoryClassifier.unavailable(); }
    }
}
