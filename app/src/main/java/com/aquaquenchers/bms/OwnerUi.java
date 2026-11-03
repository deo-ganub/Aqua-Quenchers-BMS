package com.aquaquenchers.bms;

import android.app.*;
import android.content.*;


import android.net.Uri;
import android.text.*;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.aquaquenchers.classifier.*;

/** Owner financial/settings workflows. All persistence is confirmed by the backend. */
final class OwnerUi {
 private final MainActivity a;
 private final ExecutorService predictions=Executors.newSingleThreadExecutor();
 private ExpenseCategoryClassifier cachedClassifier;
 private JSONObject report;
 private byte[] pdf;
 private static final int PDF_REQUEST=201;
 private static final int TEXT=android.text.InputType.TYPE_CLASS_TEXT;
 private static final int MONEY=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
 OwnerUi(MainActivity activity){a=activity;report=a.reportSnapshot();pdf=a.pendingPdf();}
 boolean restore(String route,String entity){
  if(a.isOwner()&&"Edit Staff account".equals(route)&&Rules.uuid(entity)){a.page(route,entity);a.query("staff",a.json(),r->{JSONArray users=r.optJSONArray("items");if(users!=null)for(int i=0;i<users.length();i++){JSONObject user=users.optJSONObject(i);if(user!=null&&entity.equals(user.optString("user_id"))){staffForm(user);return;}}staff();a.error("Staff account no longer available.");});return true;}
  if("Edit profile".equals(route)&&Rules.uuid(entity)){a.page(route,entity);a.query("me",a.json(),me->{if(entity.equals(me.optString("user_id")))editProfile(me);else{profile();a.error("Access denied.");}});return true;}
  switch(route){case "Expenses":if(a.isOwner()){expenses();return true;}break;case "Add Expense":if(a.isOwner()){expense();return true;}break;case "Financial summary":if(a.isOwner()){reports();return true;}break;case "Report Preview":if(a.isOwner()){reportPreview();return true;}break;case "Inventory":if(a.isOwner()){inventory();return true;}break;case "Create Staff account":if(a.isOwner()){staffForm(null);return true;}break;case "Settings":settings();return true;case "My account":profile();return true;case "Change password":password();return true;case "Staff accounts":if(a.isOwner()){staff();return true;}break;}return false;}
 private ExpenseCategoryClassifier.Result predict(String remarks){if(cachedClassifier==null)cachedClassifier=ClassifierAssets.load(a.getApplicationContext());return cachedClassifier.suggest(remarks);}
 void close(){predictions.shutdown();}
 private boolean owner(){if(a.isOwner())return true;a.error("Access denied.");return false;}
 private EditText dateInput(LinearLayout p,String label){EditText e=a.input(p,label,a.today(),TEXT);e.setFocusable(false);e.setOnClickListener(v->{java.time.LocalDate d;try{d=Rules.date(value(e));}catch(IllegalArgumentException ignored){d=Rules.date(a.today());}a.showDialog(new DatePickerDialog(a,(picker,y,m,day)->e.setText(java.time.LocalDate.of(y,m+1,day).toString()),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()));});return e;}
 private String value(EditText e){return e.getText().toString();}
 private void api(String method,String path,JSONObject body,java.util.function.Consumer<JSONObject> success){Backend expected=a.backend();expected.call(method,path,body,r->{if(expected==a.backend()&&!a.isDestroyed()&&!a.isFinishing())success.accept(r);},e->{if(expected==a.backend()&&!a.isDestroyed()&&!a.isFinishing()){if(e.status==401&&!path.contains("username-login"))a.endSession("Session expired. Sign in again.");else if(path.equals("/functions/v1/username-login")&&(e.status==400||e.status==401))a.error("Current password is incorrect.");else a.error(e.getMessage());}});}
 void summary(LinearLayout p,JSONObject r){
  a.text(p,r.optString("station","Aqua Quenchers"));a.text(p,r.optString("start_date")+" to "+r.optString("end_date"));
  String[] labels={"Revenue","Payments received","Expenses","Net income",r.has("inventory")?"Receivables":"Outstanding balances"};String[] keys={"revenue_centavos","payments_received_centavos","expenses_centavos","net_income_centavos","outstanding_centavos"};
  for(int i=0;i<labels.length;i++)a.text(p,labels[i]+": "+a.money(r.optLong(keys[i])));
  a.text(p,"Net income = Payments received − Expenses");
  JSONObject stock=r.optJSONObject("inventory");if(stock!=null)stock(p,stock);
 }
 private void stock(LinearLayout p,JSONObject r){String[] keys={"filled_available","reserved_filled","empty_quantity","pending_quantity"};String[] labels={"Filled available","Reserved filled","Empty gallons","Pending delivery gallons"};for(int i=0;i<keys.length;i++)a.text(p,labels[i]+": "+r.optLong(keys[i]));a.text(p,"Inventory updated: "+a.timestamp(r.optString("updated_at")));}
 void inventory(){if(!owner())return;LinearLayout p=a.page("Inventory");a.query("inventory",a.json(),r->{stock(p,r);JSONArray rows=r.optJSONArray("pending_orders");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);a.text(p,row.optString("customer_name")+" • "+row.optString("reference")+" • Original date: "+row.optString("business_date")+" • Pending: "+row.optLong("pending_quantity"));}a.button(p,"Carry-over details",a::openPending);});}
 void expenses(){if(!owner())return;LinearLayout p=a.page("Expenses");EditText start=dateInput(p,"Start date"),end=dateInput(p,"End date");String[] filters=new String[8];filters[0]="All categories";System.arraycopy(Rules.CATEGORIES,0,filters,1,7);Spinner category=a.select(p,"Category filter",filters);LinearLayout records=new LinearLayout(a);records.setOrientation(LinearLayout.VERTICAL);p.addView(records);long[] filterRequest={0};
  Runnable load=()->{Rules.period(value(start),value(end));JSONObject fields=a.json("start_date",value(start),"end_date",value(end));if(category.getSelectedItemPosition()>0)try{fields.put("category",category.getSelectedItem());}catch(JSONException e){throw new IllegalArgumentException("Choose a category.");}long requested=++filterRequest[0];a.query("expenses",fields,r->{if(requested!=filterRequest[0])return;records.removeAllViews();a.text(records,"Filtered total: "+a.money(r.optLong("total_centavos")));JSONArray rows=r.optJSONArray("items");if(rows==null||rows.length()==0)a.text(records,"No expenses in this period.");else for(int i=0;i<rows.length();i++){JSONObject e=rows.optJSONObject(i);a.text(records,e.optString("reference")+" • "+e.optString("business_date")+" • "+e.optString("category")+" • "+a.money(e.optLong("amount_centavos"))+"\n"+e.optString("remarks")+"\nRecorded by: "+e.optString("recorded_by_name"));}});};a.button(p,"Apply filters",load);a.button(p,"Add Expense",this::expense);a.runValidated(load);
 }
 private void expense(){if(!owner())return;LinearLayout p=a.page("Add Expense");ExpenseSuggestionController state=a.expenseState();if(state.isPending())state.cancelPending();EditText date=dateInput(p,"Date");String[] options=new String[8];options[0]="Choose category";System.arraycopy(Rules.CATEGORIES,0,options,1,7);Spinner category=a.select(p,"Category",options);EditText amount=a.input(p,"Amount","",MONEY),remarks=a.input(p,"Remarks (optional)","",TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);TextView result=a.text(p,state.message());boolean[] applying={false};Button[] applyButton={null};
  state.setRemarks(value(remarks));category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> v){}public void onItemSelected(android.widget.AdapterView<?> v,View child,int position,long id){if(!applying[0]&&!("suggestion".equals(state.selectionSource())&&options[position].equals(state.category()))){state.chooseManually(position==0?null:options[position]);result.setText(state.message());if(applyButton[0]!=null)applyButton[0].setVisibility(View.GONE);}}});
  remarks.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){state.setRemarks(s.toString());result.setText(state.message());if(applyButton[0]!=null)applyButton[0].setVisibility(View.GONE);}public void afterTextChanged(Editable s){}});
  a.button(p,"Suggest category",()->{state.setRemarks(value(remarks));ExpenseSuggestionController.Request request=state.beginRequest();result.setText("Processing suggestion…");predictions.execute(()->{ExpenseCategoryClassifier.Result prediction=predict(request.remarks);a.runOnUiThread(()->{if(state.complete(request,prediction)){result.setText(state.message());if(applyButton[0]!=null)applyButton[0].setVisibility(state.canApplySuggestion()?View.VISIBLE:View.GONE);}});});});
  applyButton[0]=a.button(p,"Apply suggestion",()->{if(state.applySuggestion()){applying[0]=true;category.setSelection(Arrays.asList(options).indexOf(state.category()));category.post(()->applying[0]=false);result.setText(state.message());}else a.error("No reliable suggestion. Choose a category manually.");});applyButton[0].setVisibility(state.canApplySuggestion()?View.VISIBLE:View.GONE);
  a.button(p,"Choose manually",()->{state.chooseManually(category.getSelectedItemPosition()==0?null:category.getSelectedItem().toString());result.setText("Choose a category manually.");applyButton[0].setVisibility(View.GONE);});a.text(p,"Recorded by: "+a.recorder());
  a.button(p,"Save Expense",()->{if(value(date).trim().isEmpty())throw new IllegalArgumentException("Complete the required fields.");Rules.date(value(date));Rules.remarks(value(remarks));if(category.getSelectedItemPosition()==0)throw new IllegalArgumentException("Complete the required fields.");long cents;try{cents=Rules.centavos(value(amount),false);}catch(IllegalArgumentException e){throw new IllegalArgumentException("Enter a valid expense amount.");}JSONObject fields=a.json("business_date",value(date),"category",category.getSelectedItem(),"amount_centavos",cents,"remarks",value(remarks),"selection_source",state.selectionSource());if(state.acceptedModelVersion()!=null)try{fields.put("accepted_model_version",state.acceptedModelVersion());}catch(JSONException e){throw new IllegalArgumentException("Invalid model version.");}a.command("create_expense",fields,r->{expenses();a.info("Expense saved.");});});a.button(p,"Cancel",this::expenses);
 }
 void reports(){if(!owner())return;LinearLayout p=a.page("Financial summary");EditText start=dateInput(p,"Start date"),end=dateInput(p,"End date");LinearLayout preview=new LinearLayout(a);preview.setOrientation(LinearLayout.VERTICAL);p.addView(preview);long[] request={0};
  a.button(p,"Generate Report",()->{Rules.period(value(start),value(end));a.clearMessage();long requested=++request[0];a.query("report",a.json("start_date",value(start),"end_date",value(end)),r->{if(requested!=request[0])return;report=r;a.reportSnapshot(r);preview.removeAllViews();summary(preview,r);a.text(preview,"Generated: "+generated(r));});});
  a.button(p,"Open Report Preview",this::reportPreview);if(report!=null){summary(preview,report);a.text(preview,"Generated: "+generated(report));}
 }
 private void reportPreview(){if(!owner())return;if(report==null){reports();a.error("Generate a report first.");return;}LinearLayout p=a.page("Report Preview");summary(p,report);a.text(p,"Generated: "+generated(report));a.button(p,"Export PDF",this::exportPdf);a.button(p,"Back to Reports",this::reports);}
 private void exportPdf(){if(!owner())return;if(report==null)throw new IllegalArgumentException("Generate a report first.");pdf=renderPdf(report);a.pendingPdf(pdf);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Aqua-Quenchers-"+report.optString("start_date")+".pdf");a.startActivityForResult(intent,PDF_REQUEST);}
 private String generated(JSONObject r){return a.timestamp(r.optString("generated_at"));}
 private byte[] renderPdf(JSONObject r){return ReportPdf.render(r);}
 void activityResult(int request,int result,Intent data){
  if(request!=PDF_REQUEST)return;
  if(result!=Activity.RESULT_OK||data==null){pdf=null;a.pendingPdf(null);return;}
  if(pdf==null)return;Uri uri=data.getData();if(uri==null)return;
  try(OutputStream out=a.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();out.write(pdf);out.flush();}
  catch(IOException|SecurityException e){a.error("PDF export failed. Try again.");return;}
  pdf=null;a.pendingPdf(null);a.info("Report exported successfully.");
  try{a.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));}
  catch(ActivityNotFoundException|SecurityException e){a.info("Report exported successfully. Open the saved PDF from Documents.");}
 }
 void settings(){LinearLayout p=a.page("Settings");if(a.isOwner()){a.button(p,"Inventory",this::inventory);a.button(p,"Staff accounts",this::staff);}else{a.button(p,"Payments",a::openPayments);a.button(p,"Orders",a::openOrders);a.button(p,"Pending gallons",a::openPending);}a.button(p,"My account",this::profile);a.button(p,"Change password",this::password);a.button(p,"Log out",()->a.endSession("Signed out."));a.button(p,a.isOwner()?"Back to dashboard":"Back to Home",a::showHome);}
 private void profile(){LinearLayout p=a.page("My account");a.query("me",a.json(),me->{a.text(p,me.optString("full_name"));a.text(p,"Role: "+Rules.label(me.optString("role")));a.text(p,"Username: "+me.optString("username"));a.text(p,"Contact number: "+me.optString("contact_number"));a.button(p,"Edit profile",()->editProfile(me));a.button(p,"Change password",this::password);a.button(p,"Back to Settings",this::settings);});}
 private void validateAccount(EditText name,EditText username,EditText contact){if(value(name).trim().isEmpty()||value(username).trim().isEmpty()||value(contact).trim().isEmpty())throw new IllegalArgumentException("Complete the required fields.");if(value(name).codePointCount(0,value(name).length())>160)throw new IllegalArgumentException("Use 160 characters or fewer.");Rules.contact(value(contact));}
 private void editProfile(JSONObject me){LinearLayout p=a.page("Edit profile",me.optString("user_id"));EditText name=a.input(p,"Full name",me.optString("full_name"),TEXT),username=a.input(p,"Username",me.optString("username"),TEXT),contact=a.input(p,"Contact number",me.optString("contact_number"),android.text.InputType.TYPE_CLASS_PHONE);a.button(p,"Save profile",()->{validateAccount(name,username,contact);a.command("update_profile",a.json("full_name",value(name),"username",value(username),"contact_number",value(contact)),r->{profile();a.info("Profile updated.");});});a.button(p,"Cancel",this::profile);}
 private void password(){
  LinearLayout p=a.page("Change password");int secret=TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;
  EditText current=a.input(p,"Current password","",secret),next=a.input(p,"New password","",secret),confirmation=a.input(p,a.isOwner()?"Confirm new password":"Confirm password","",secret);
  a.button(p,"Save password",()->{
   String currentPassword=value(current),newPassword=value(next),confirmed=value(confirmation);
   if(currentPassword.isEmpty())throw new IllegalArgumentException("Enter your current password.");Rules.password(newPassword);if(!newPassword.equals(confirmed))throw new IllegalArgumentException("New passwords do not match.");a.clearMessage();
   a.query("me",a.json(),me->api("POST","/functions/v1/username-login",a.json("username",me.optString("username"),"password",currentPassword),session->{
    JSONObject identity=session.optJSONObject("user");if(identity==null||!me.optString("user_id").equals(identity.optString("id"))){a.error("Current password is incorrect.");return;}
    a.acceptTokens(session);
    api("GET","/auth/v1/user",null,user->{
     if(!me.optString("user_id").equals(user.optString("id"))){a.endSession("Access denied. Sign in again.");return;}
     Runnable update=()->api("PUT","/auth/v1/user",a.json("password",newPassword),saved->{if(!me.optString("user_id").equals(saved.optString("id"))){a.error("Password update was not confirmed. Sign in again to check the outcome.");return;}a.endSession("Password updated.");});
     JSONArray factors=user.optJSONArray("factors");boolean requiresMfa=false;if(factors!=null)for(int i=0;i<factors.length();i++){JSONObject factor=factors.optJSONObject(i);if(factor!=null&&"verified".equals(factor.optString("status"))){requiresMfa=true;break;}}
     if(requiresMfa)confirmManagedMfa("Verify password change",update);else update.run();
    });
   }));
  });a.button(p,"Cancel",this::settings);
 }

 private void staff(){if(!owner())return;LinearLayout p=a.page("Staff accounts");a.button(p,"Create Staff account",()->staffForm(null));a.query("staff",a.json(),r->{JSONArray rows=r.optJSONArray("items");if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);a.button(p,row.optString("full_name")+" • "+row.optString("username")+" • "+(row.optBoolean("active")?"Active":"Inactive"),()->staffForm(row));}});}
 private void staffForm(JSONObject user){if(!owner())return;LinearLayout p=a.page(user==null?"Create Staff account":"Edit Staff account",user==null?"":user.optString("user_id"));EditText name=a.input(p,"Full name",user==null?"":user.optString("full_name"),TEXT),username=a.input(p,"Username",user==null?"":user.optString("username"),TEXT),contact=a.input(p,"Contact number",user==null?"":user.optString("contact_number"),android.text.InputType.TYPE_CLASS_PHONE);a.text(p,"Role: Staff");Spinner active=a.select(p,"Status",new String[]{"Active","Inactive"});if(user!=null)active.setSelection(user.optBoolean("active")?0:1);a.restoreSelection(active);EditText temporary=user==null?a.input(p,"Temporary password","",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD):null;
  a.button(p,"Save Staff Account",()->{validateAccount(name,username,contact);if(temporary!=null){if(value(temporary).isEmpty())throw new IllegalArgumentException("Complete the required fields.");Rules.password(value(temporary));}JSONObject payload=a.json("operation",user==null?"create":"update","full_name",value(name),"username",value(username),"contact_number",value(contact),"active",active.getSelectedItemPosition()==0);try{if(user!=null)payload.put("user_id",user.optString("user_id"));else payload.put("temporary_password",value(temporary));}catch(JSONException e){throw new IllegalArgumentException("Invalid account details.");}confirmStaffMfa(payload);});a.button(p,"Cancel",this::staff);
 }
 private void confirmStaffMfa(JSONObject payload){confirmManagedMfa("Verify Staff account change",()->a.accountCommand(payload,r->{staff();a.info("Staff account saved.");}));}
 private void confirmManagedMfa(String title,Runnable verified){
  LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);TextView codeLabel=a.text(form,"Verification code");
  // A one-time code must not enter general form drafts or saved instance state.
  EditText code=new EditText(a);code.setTypeface(codeLabel.getTypeface());code.setSaveEnabled(false);code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);code.setContentDescription("Verification code");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);form.addView(code);
  a.showDialog(new android.app.AlertDialog.Builder(a).setTitle(title).setView(form).setNegativeButton("Cancel",(dialog,which)->code.setText("")).setPositiveButton("Verify and save",(dialog,which)->{String entered=value(code);code.setText("");a.runValidated(()->verifyMfa(entered,verified));}).create());
 }

 private void verifyMfa(String code,Runnable verified){if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter a valid verification code.");api("GET","/auth/v1/user",null,user->{JSONArray factors=user.optJSONArray("factors");String id="";if(factors!=null)for(int i=0;i<factors.length();i++){JSONObject factor=factors.optJSONObject(i);if("verified".equals(factor.optString("status"))&&"totp".equals(factor.optString("factor_type"))){id=factor.optString("id");break;}}if(id.isEmpty()){a.error("A managed MFA factor must be enrolled by the designated administrator.");return;}String path="/auth/v1/factors/"+id;api("POST",path+"/challenge",a.json(),challenge->verifyCode(path,challenge.optString("id"),code,verified));});}
 private void verifyCode(String path,String challenge,String code,Runnable verified){Backend expected=a.backend();expected.call(path+"/verify",a.json("challenge_id",challenge,"code",code),session->{if(expected!=a.backend()||a.isDestroyed()||a.isFinishing())return;if(session.optString("access_token").isEmpty()&&session.optJSONObject("session")==null){a.error("Verification was not confirmed. Try again.");return;}a.acceptTokens(session);verified.run();},e->{if(expected!=a.backend()||a.isDestroyed()||a.isFinishing())return;if(e.status==401){a.endSession("Session expired. Sign in again.");return;}a.error(e.status==400||e.status==403||e.status==422?"Enter a valid verification code.":e.getMessage());});}

}
