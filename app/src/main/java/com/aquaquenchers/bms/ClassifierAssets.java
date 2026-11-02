package com.aquaquenchers.bms;
import android.content.Context;
import com.aquaquenchers.classifier.ExpenseCategoryClassifier;
import java.io.*;
/** The release digest is reviewed and pinned in the signed APK, never accepted from a response. */
final class ClassifierAssets {
 static ExpenseCategoryClassifier load(Context context){
  if(!BuildConfig.CLASSIFIER_SHA256.matches("[a-f0-9]{64}"))return ExpenseCategoryClassifier.unavailable();
  try(InputStream in=context.getAssets().open("expense-model.json");ByteArrayOutputStream out=new ByteArrayOutputStream()){
   byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>2*1024*1024)return ExpenseCategoryClassifier.unavailable();out.write(buffer,0,n);}
   return ExpenseCategoryClassifier.fromPackage(out.toByteArray(),BuildConfig.CLASSIFIER_SHA256,false);
  }catch(Exception e){return ExpenseCategoryClassifier.unavailable();}
 }
}
