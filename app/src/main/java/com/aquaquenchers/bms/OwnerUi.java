package com.aquaquenchers.bms;

import android.app.*;
import android.content.*;


import android.net.Uri;
import android.text.*;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import com.aquaquenchers.classifier.*;

/** Owner financial/settings workflows (Figma Owner 9–12) and the shared Settings page. All persistence is confirmed by the backend. */
final class OwnerUi {
 private final MainActivity a;
 private final Ui ui;
 private final ExecutorService predictions=Executors.newSingleThreadExecutor();
 private ExpenseCategoryClassifier cachedClassifier;
 private JSONObject report;
 private byte[] pdf;
 private static final int PDF_REQUEST=201;
 private static final int TEXT=android.text.InputType.TYPE_CLASS_TEXT;
 private static final int MONEY=android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL;
 OwnerUi(MainActivity activity){a=activity;ui=activity.ui();report=a.reportSnapshot();pdf=a.pendingPdf();}
 boolean restore(String route,String entity){
  if(a.isOwner()&&"Edit Staff account".equals(route)&&Rules.uuid(entity)){a.page(route,entity);a.query("staff",a.json(),r->{JSONArray users=r.optJSONArray("items");if(users!=null)for(int i=0;i<users.length();i++){JSONObject user=users.optJSONObject(i);if(user!=null&&entity.equals(user.optString("user_id"))){staffForm(user);return;}}staffAccounts();a.error("Staff account no longer available.");});return true;}
  if("Edit profile".equals(route)&&Rules.uuid(entity)){a.page(route,entity);a.query("me",a.json(),me->{if(entity.equals(me.optString("user_id")))editProfile(me);else{settings();a.error("Access denied.");}});return true;}
  switch(route){case "Expenses":if(a.isOwner()){expenses();return true;}break;case "Add Expense":if(a.isOwner()){expense();return true;}break;case "Financial summary":if(a.isOwner()){reports();return true;}break;case "Report Preview":if(a.isOwner()){reports();return true;}break;case "Inventory":if(a.isOwner()){inventory();return true;}break;case "Create Staff account":if(a.isOwner()){staffForm(null);return true;}break;case "Settings":settings();return true;case "My account":profile();return true;case "Change password":password();return true;case "Staff accounts":if(a.isOwner()){staffAccounts();return true;}break;}return false;}
 private ExpenseCategoryClassifier.Result predict(String remarks){if(cachedClassifier==null)cachedClassifier=ClassifierAssets.load(a.getApplicationContext());return cachedClassifier.suggest(remarks);}
 void close(){predictions.shutdown();}
 private boolean owner(){if(a.isOwner())return true;a.error("Access denied.");return false;}
 private EditText dateInput(LinearLayout p,String label){EditText e=a.input(p,label,a.today(),TEXT);e.setFocusable(false);e.setOnClickListener(v->{java.time.LocalDate d;try{d=Rules.date(value(e));}catch(IllegalArgumentException ignored){d=Rules.date(a.today());}a.showDialog(new DatePickerDialog(a,(picker,y,m,day)->e.setText(java.time.LocalDate.of(y,m+1,day).toString()),d.getYear(),d.getMonthValue()-1,d.getDayOfMonth()));});return e;}
 private String value(EditText e){return e.getText().toString();}
 private void api(String method,String path,JSONObject body,java.util.function.Consumer<JSONObject> success){Backend expected=a.backend();expected.call(method,path,body,r->{if(expected==a.backend()&&!a.isDestroyed()&&!a.isFinishing())success.accept(r);},e->{if(expected==a.backend()&&!a.isDestroyed()&&!a.isFinishing()){if(e.status==401&&!path.contains("username-login"))a.endSession("Session expired. Sign in again.");else if(path.equals("/functions/v1/username-login")&&(e.status==400||e.status==401))a.error("Current password is incorrect.");else a.error(e.getMessage());}});}
 private LinearLayout stack(LinearLayout parent){LinearLayout c=new LinearLayout(a);c.setOrientation(LinearLayout.VERTICAL);parent.addView(c,new LinearLayout.LayoutParams(-1,-2));return c;}
 private LinearLayout[] columns(LinearLayout parent,float left,float right){
  LinearLayout h=new LinearLayout(a);boolean wide=a.tablet();h.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);parent.addView(h,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout l=new LinearLayout(a),r=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);r.setOrientation(LinearLayout.VERTICAL);
  if(wide){h.addView(l,new LinearLayout.LayoutParams(0,-2,left));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,right);rp.leftMargin=ui.dp(16);h.addView(r,rp);}else{h.addView(l,new LinearLayout.LayoutParams(-1,-2));h.addView(r,new LinearLayout.LayoutParams(-1,-2));}
  return new LinearLayout[]{l,r};
 }
 private LinearLayout[] twoUp(LinearLayout parent){
  LinearLayout h=new LinearLayout(a);boolean wide=a.tablet();h.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);parent.addView(h,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout l=new LinearLayout(a),r=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);r.setOrientation(LinearLayout.VERTICAL);
  if(wide){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.rightMargin=ui.dp(8);h.addView(l,lp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=ui.dp(8);h.addView(r,rp);}else{h.addView(l,new LinearLayout.LayoutParams(-1,-2));h.addView(r,new LinearLayout.LayoutParams(-1,-2));}
  return new LinearLayout[]{l,r};
 }
 private void stock(LinearLayout p,JSONObject r){
  String[] keys={"filled_available","reserved_filled","empty_quantity","pending_quantity"};String[] labels={"Filled available","Reserved filled","Empty gallons","Pending delivery gallons"};int[] bg={Ui.GREEN_BG,Ui.TONAL,Ui.GREY_BG,Ui.AMBER_BG},fg={Ui.GREEN,Ui.PRIMARY,Ui.INK,Ui.AMBER};
  LinearLayout r1=new LinearLayout(a),r2=new LinearLayout(a);p.addView(r1,ui.lp(-1,-2,0,0,0,8));p.addView(r2,ui.lp(-1,-2,0,0,0,8));
  for(int i=0;i<4;i++){LinearLayout t=new LinearLayout(a);t.setOrientation(LinearLayout.VERTICAL);t.setBackground(ui.shape(bg[i],0,12));t.setPadding(ui.dp(12),ui.dp(10),ui.dp(12),ui.dp(10));t.addView(ui.tv(Long.toString(r.optLong(keys[i])),22,fg[i],ui.headBold));t.addView(ui.tv(labels[i],12,Ui.INK,ui.bodySemi));t.setContentDescription(labels[i]+": "+r.optLong(keys[i]));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(i%2==0?0:ui.dp(4),0,i%2==0?ui.dp(4):0,0);(i<2?r1:r2).addView(t,lp);}
  ui.muted(p,"Inventory updated: "+a.timestamp(r.optString("updated_at"))).setTextSize(11);
 }
 void inventory(){if(!owner())return;LinearLayout p=a.page("Inventory");a.query("inventory",a.json(),r->{LinearLayout c=ui.card(p);ui.heading(c,"Gallon stock");stock(c,r);JSONArray rows=r.optJSONArray("pending_orders");LinearLayout carry=ui.card(p);ui.heading(carry,"Carry-over details");if(rows==null||rows.length()==0)ui.muted(carry,"No pending delivery gallons.");else for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;LinearLayout line=ui.column(carry);line.setPadding(0,ui.dp(6),0,ui.dp(6));LinearLayout top=ui.row(line);top.addView(ui.tv(row.optString("customer_name"),15,Ui.INK,ui.headBold),new LinearLayout.LayoutParams(0,-2,1));top.addView(ui.pill(row.optLong("pending_quantity")+" left",Ui.AMBER_BG,Ui.AMBER));ui.muted(line,row.optString("reference")+" • Original date: "+row.optString("business_date")+" • Pending: "+row.optLong("pending_quantity")).setTextSize(12);}
  a.button(p,"Carry-over details",Ui.TONAL_BTN,a::openPending);});}

 // ------------------------------------------------------------------ expenses
 void expenses(){
  if(!owner())return;LinearLayout p=a.page("Expenses");
  boolean split=a.tablet();LinearLayout[] cols=split?columns(p,1f,1.3f):new LinearLayout[]{p,null};LinearLayout listSide=cols[0];
  String[] periods={"Today","This week","This month","Custom"};int[] per={1};String savedPer=a.choice("expense_period");if(!savedPer.isEmpty())try{per[0]=Math.max(0,Math.min(3,Integer.parseInt(savedPer)));}catch(NumberFormatException ignored){}
  String[] categories=new String[8];categories[0]="All categories";System.arraycopy(Rules.CATEGORIES,0,categories,1,7);int[] cat={0};
  LinearLayout custom=new LinearLayout(a);custom.setOrientation(LinearLayout.VERTICAL);EditText start=dateInput(custom,"Start date"),end=dateInput(custom,"End date");
  LinearLayout records=new LinearLayout(a);records.setOrientation(LinearLayout.VERTICAL);long[] filterRequest={0};Runnable[] load=new Runnable[1];
  ui.chips(listSide,periods,per[0],i->{per[0]=i;a.rememberChoice("expense_period",Integer.toString(i));custom.setVisibility(i==3?View.VISIBLE:View.GONE);if(i!=3)load[0].run();});
  ui.chips(listSide,categories,0,i->{cat[0]=i;load[0].run();});
  custom.setVisibility(per[0]==3?View.VISIBLE:View.GONE);a.button(custom,"Apply filters",Ui.TONAL_BTN,()->load[0].run());listSide.addView(custom,new LinearLayout.LayoutParams(-1,-2));listSide.addView(records,new LinearLayout.LayoutParams(-1,-2));
  load[0]=()->{
   LocalDate now=LocalDate.parse(a.today());String from,to;
   if(per[0]==0){from=to=now.toString();}else if(per[0]==1){from=now.minusDays(now.getDayOfWeek().getValue()-1L).toString();to=now.toString();}else if(per[0]==2){from=now.withDayOfMonth(1).toString();to=now.toString();}else{from=value(start);to=value(end);}
   try{Rules.period(from,to);}catch(IllegalArgumentException e){a.error(e.getMessage());return;}
   JSONObject fields=a.json("start_date",from,"end_date",to);if(cat[0]>0)try{fields.put("category",categories[cat[0]]);}catch(JSONException e){a.error("Choose a category.");return;}
   long requested=++filterRequest[0];a.clearMessage();
   a.query("expenses",fields,r->{if(requested!=filterRequest[0])return;records.removeAllViews();
    JSONArray rows=r.optJSONArray("items");int n=rows==null?0:rows.length();
    LinearLayout total=new LinearLayout(a);total.setOrientation(LinearLayout.VERTICAL);total.setBackground(ui.shape(Ui.PRIMARY,0,14));total.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));
    total.addView(ui.tv("Total expenses",12,0xDDFFFFFF,ui.body));total.addView(ui.tv(a.money(r.optLong("total_centavos")),26,Ui.WHITE,ui.headBold));total.addView(ui.tv("Filtered total · "+n+(n==1?" expense":" expenses")+" · newest first",12,0xDDFFFFFF,ui.body));
    total.setContentDescription("Filtered total: "+a.money(r.optLong("total_centavos")));records.addView(total,ui.lp(-1,-2,0,0,0,10));
    if(n==0)ui.muted(records,"No expenses in this period.");
    else for(int i=0;i<n;i++){JSONObject e=rows.optJSONObject(i);if(e==null)continue;LinearLayout c=ui.card(records);c.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(10));LinearLayout row=ui.row(c);LinearLayout names=new LinearLayout(a);names.setOrientation(LinearLayout.VERTICAL);String remarks=e.optString("remarks");names.addView(ui.tv(remarks.isEmpty()?e.optString("category"):remarks,14,Ui.INK,ui.bodySemi));names.addView(ui.tv(shortDate(e.optString("business_date"))+" · "+e.optString("category")+" · "+e.optString("reference")+" · Recorded by: "+e.optString("recorded_by_name"),11,Ui.MUTED,ui.body));row.addView(names,new LinearLayout.LayoutParams(0,-2,1));row.addView(ui.tv(a.money(e.optLong("amount_centavos")),15,Ui.INK,ui.headBold));}});
  };
  if(split)expenseForm(cols[1]);else a.button(p,"Add Expense",Ui.FILLED,this::expense);
  load[0].run();
 }
 private static String shortDate(String iso){try{return LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM",Locale.ENGLISH));}catch(Exception e){return iso;}}
 private void expense(){if(!owner())return;LinearLayout p=a.page("Add Expense");expenseForm(p);}
 private void expenseForm(LinearLayout host){
  ExpenseSuggestionController state=a.expenseState();if(state.isPending())state.cancelPending();
  LinearLayout form=ui.card(host);ui.heading(form,"Add expense");
  LinearLayout[] two=twoUp(form);EditText date=dateInput(two[0],"Date"),amount=a.input(two[1],"Amount","",MONEY);amount.setHint("₱0.00");
  EditText remarks=a.input(form,"Remarks (optional)","",TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);remarks.setMinLines(3);remarks.setGravity(Gravity.TOP);
  TextView counter=ui.muted(form,value(remarks).codePointCount(0,value(remarks).length())+" / 500 characters");counter.setTextSize(11);
  LinearLayout catCard=ui.card(host);
  LinearLayout head=ui.row(catCard);head.addView(ui.tv("Category",13,Ui.MUTED,ui.bodySemi),new LinearLayout.LayoutParams(0,-2,1));
  String[] options=new String[8];options[0]="Choose category";System.arraycopy(Rules.CATEGORIES,0,options,1,7);int[] sel={0};
  if(state.category()!=null)for(int i=1;i<options.length;i++)if(options[i].equals(state.category()))sel[0]=i;
  TextView result=ui.tv(state.message(),13,Ui.PRIMARY,ui.body);result.setBackground(ui.shape(Ui.TONAL,0,12));result.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(10));result.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
  Button[] applyButton={null};boolean[] applying={false};Ui.Chips[] chips=new Ui.Chips[1];
  state.setRemarks(value(remarks));
  Button suggest=new Button(a);suggest.setText("Suggest category");ui.styled(suggest,Ui.TONAL_BTN);suggest.setMinHeight(ui.dp(44));head.addView(suggest,new LinearLayout.LayoutParams(-2,ui.dp(44)));
  catCard.addView(result,ui.lp(-1,-2,0,8,0,8));
  LinearLayout choices=ui.row(catCard);
  applyButton[0]=a.button(choices,"Apply suggestion",Ui.FILLED,()->{if(state.applySuggestion()){applying[0]=true;sel[0]=Arrays.asList(options).indexOf(state.category());chips[0].select(sel[0]-1,false);applying[0]=false;result.setText(state.message());}else a.error("No reliable suggestion. Choose a category manually.");});
  LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-2,-2);alp.rightMargin=ui.dp(8);applyButton[0].setLayoutParams(alp);applyButton[0].setVisibility(state.canApplySuggestion()?View.VISIBLE:View.GONE);
  Button manual=a.button(choices,"Choose manually",Ui.OUTLINE,()->{state.chooseManually(sel[0]==0?null:options[sel[0]]);result.setText("Choose a category manually.");applyButton[0].setVisibility(View.GONE);});manual.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
  TextView catError=ui.smallError(catCard);
  String[] names=Rules.CATEGORIES;
  chips[0]=ui.chips(catCard,names,sel[0]-1,i->{int position=i+1;sel[0]=position;catError.setVisibility(View.GONE);if(!applying[0]&&!("suggestion".equals(state.selectionSource())&&options[position].equals(state.category()))){state.chooseManually(options[position]);result.setText(state.message());applyButton[0].setVisibility(View.GONE);}});
  catCard.removeView(catError);catCard.addView(catError);
  remarks.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){counter.setText(s.toString().codePointCount(0,s.length())+" / 500 characters");state.setRemarks(s.toString());result.setText(state.message());applyButton[0].setVisibility(View.GONE);}public void afterTextChanged(Editable s){}});
  suggest.setOnClickListener(v->{try{state.setRemarks(value(remarks));ExpenseSuggestionController.Request request=state.beginRequest();a.clearMessage();result.setText("Processing suggestion…");predictions.execute(()->{ExpenseCategoryClassifier.Result prediction=predict(request.remarks);a.runOnUiThread(()->{if(state.complete(request,prediction)){result.setText(state.message());applyButton[0].setVisibility(state.canApplySuggestion()?View.VISIBLE:View.GONE);}});});}catch(IllegalArgumentException e){a.error(e.getMessage());}});
  LinearLayout foot=ui.row(host);foot.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);foot.addView(ui.tv("Recorded by "+a.recorder()+"  ",12,Ui.MUTED,ui.body));
  Button cancel=a.button(foot,"Cancel",Ui.OUTLINE,this::expenses);cancel.setLayoutParams(ui.lp(-2,-2,0,0,8,0));
  Button save=a.button(foot,"Save expense",Ui.FILLED,()->{
   boolean bad=false;
   if(value(date).trim().isEmpty()){a.fieldError(date,"Complete the required fields.");bad=true;}else{try{Rules.date(value(date));}catch(IllegalArgumentException e){a.fieldError(date,e.getMessage());bad=true;}}
   if(sel[0]==0){catError.setText("Complete the required fields.");catError.setVisibility(View.VISIBLE);bad=true;}
   long cents=0;try{cents=Rules.centavos(value(amount),false);}catch(IllegalArgumentException e){a.fieldError(amount,"Enter a valid expense amount.");bad=true;}
   final long amountCents=cents;
   try{Rules.remarks(value(remarks));}catch(IllegalArgumentException e){a.fieldError(remarks,e.getMessage());bad=true;}
   if(bad)return;
   JSONObject fields=a.json("business_date",value(date),"category",options[sel[0]],"amount_centavos",cents,"remarks",value(remarks),"selection_source",state.selectionSource());
   if(state.acceptedModelVersion()!=null)try{fields.put("accepted_model_version",state.acceptedModelVersion());}catch(JSONException e){throw new IllegalArgumentException("Invalid model version.");}
   a.command("create_expense",fields,r->{expenses();a.info("Expense saved.",options[sel[0]]+" · "+a.money(amountCents));});});
  save.setLayoutParams(new LinearLayout.LayoutParams(-2,-2));
 }

 // ------------------------------------------------------------------ reports
 void reports(){
  if(!owner())return;LinearLayout p=a.page("Financial summary");
  LinearLayout[] cols=columns(p,1f,1.5f);
  LinearLayout period=ui.card(cols[0]);ui.heading(period,"Report period");
  EditText start=null,end=null;
  String[] presets={"This week","This month","Custom"};
  LinearLayout dates=new LinearLayout(a);dates.setOrientation(LinearLayout.VERTICAL);
  EditText s=dateInput(dates,"Start date"),e=dateInput(dates,"End date");
  ui.chips(period,presets,2,i->{LocalDate now=LocalDate.parse(a.today());if(i==0){s.setText(now.minusDays(now.getDayOfWeek().getValue()-1L).toString());e.setText(now.toString());}else if(i==1){s.setText(now.withDayOfMonth(1).toString());e.setText(now.toString());}});
  period.addView(dates,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout preview=new LinearLayout(a);preview.setOrientation(LinearLayout.VERTICAL);cols[1].addView(preview,new LinearLayout.LayoutParams(-1,-2));
  long[] request={0};
  Runnable generate=()->{try{Rules.period(value(s),value(e));}catch(IllegalArgumentException x){a.fieldError(e,x.getMessage());return;}a.clearMessage();long requested=++request[0];a.query("report",a.json("start_date",value(s),"end_date",value(e)),r->{if(requested!=request[0])return;report=r;a.reportSnapshot(r);preview.removeAllViews();reportCard(preview,r);});};
  a.button(period,"Update report",Ui.FILLED,generate);
  if(report!=null)reportCard(preview,report);else a.runValidated(generate);
 }
 private void reportCard(LinearLayout parent,JSONObject r){
  LinearLayout card=ui.card(parent);LinearLayout head=ui.row(card);
  LinearLayout names=new LinearLayout(a);names.setOrientation(LinearLayout.VERTICAL);names.addView(ui.tv("Financial summary",20,Ui.INK,ui.headBold));
  names.addView(ui.tv(r.optString("station","Aqua Quenchers")+" · "+HomeUi.rangeLabel(r.optString("start_date"),r.optString("end_date"))+" · Generated "+generated(r),12,Ui.MUTED,ui.body));
  head.addView(names,new LinearLayout.LayoutParams(0,-2,1));
  Button export=new Button(a);export.setText("Export PDF");ui.styled(export,Ui.FILLED);export.setMinHeight(ui.dp(44));export.setOnClickListener(v->{try{exportPdf();}catch(IllegalArgumentException x){a.error(x.getMessage());}});head.addView(export,new LinearLayout.LayoutParams(-2,ui.dp(44)));
  String[] labels={"Revenue","Payments received","Outstanding balances","Expenses","Net income"};String[] keys={"revenue_centavos","payments_received_centavos","outstanding_centavos","expenses_centavos","net_income_centavos"};
  LinearLayout top=new LinearLayout(a),bottom=new LinearLayout(a);card.addView(top,ui.lp(-1,-2,0,10,0,8));card.addView(bottom,ui.lp(-1,-2,0,0,0,8));
  for(int i=0;i<5;i++){boolean dark=i==4;LinearLayout t=new LinearLayout(a);t.setOrientation(LinearLayout.VERTICAL);t.setBackground(ui.shape(dark?Ui.PRIMARY:Ui.GREY_BG,0,12));t.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(10));t.addView(ui.tv(labels[i],12,dark?0xDDFFFFFF:Ui.MUTED,ui.body));t.addView(ui.tv(a.money(r.optLong(keys[i])),20,dark?Ui.WHITE:Ui.INK,ui.headBold));t.setContentDescription(labels[i]+": "+a.money(r.optLong(keys[i])));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(ui.dp(i%3==0?0:4),0,ui.dp(4),0);(i<3?top:bottom).addView(t,lp);}
  ui.muted(card,"Net income = payments received "+a.money(r.optLong("payments_received_centavos"))+" − expenses "+a.money(r.optLong("expenses_centavos")));
 }
 private void exportPdf(){if(!owner())return;if(report==null)throw new IllegalArgumentException("Generate a report first.");pdf=renderPdf(report);a.pendingPdf(pdf);Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Aqua-Quenchers-"+report.optString("start_date")+".pdf");a.startActivityForResult(intent,PDF_REQUEST);}
 private String generated(JSONObject r){String s=Rules.shortStamp(r.optString("generated_at"));return s.isEmpty()?a.timestamp(r.optString("generated_at")):s;}
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

 // ------------------------------------------------------------------ settings and accounts
 private void menuRow(LinearLayout menu,String label,Runnable open){
  if(menu.getChildCount()>0)ui.divider(menu);
  LinearLayout row=ui.row(menu);row.setPadding(0,ui.dp(10),0,ui.dp(10));row.setClickable(true);row.setContentDescription(label);
  row.addView(ui.tv(label,15,Ui.INK,ui.head),new LinearLayout.LayoutParams(0,-2,1));row.addView(ui.tv("›",18,Ui.MUTED,ui.head));
  row.setOnClickListener(v->{try{open.run();}catch(IllegalArgumentException e){a.error(e.getMessage());}});
 }
 void settings(){
  LinearLayout p=a.page("Settings");LinearLayout menu=ui.card(p);
  if(a.isOwner()){menuRow(menu,"Inventory",this::inventory);menuRow(menu,"Staff accounts",this::staffAccounts);menuRow(menu,"Change password",this::password);}
  else{menuRow(menu,"Edit profile",this::profile);menuRow(menu,"Change password",this::password);menuRow(menu,"Pending gallons",a::openPending);menuRow(menu,"Collect payments",a::openPayments);}
  a.button(p,"Log out",Ui.DANGER_OUTLINE,()->a.endSession("Signed out."));
 }
 private void profile(){LinearLayout p=a.page("My account");a.query("me",a.json(),me->editProfile(me));}
 private void validateAccount(EditText name,EditText username,EditText contact){
  boolean bad=false;
  if(value(name).trim().isEmpty()){a.fieldError(name,"Complete the required fields.");bad=true;}else if(value(name).codePointCount(0,value(name).length())>160){a.fieldError(name,"Use 160 characters or fewer.");bad=true;}
  if(value(username).trim().isEmpty()){a.fieldError(username,"Complete the required fields.");bad=true;}
  if(value(contact).trim().isEmpty()){a.fieldError(contact,"Complete the required fields.");bad=true;}else{try{Rules.contact(value(contact));}catch(IllegalArgumentException e){a.fieldError(contact,e.getMessage());bad=true;}}
  if(bad)throw new FieldErrors();
 }
 /** Field messages are already shown beside their inputs; nothing more to display. */
 private static final class FieldErrors extends RuntimeException{FieldErrors(){super(null,null,false,false);}}
 private void editProfile(JSONObject me){
  LinearLayout p=a.page("Edit profile",me.optString("user_id"));LinearLayout card=ui.card(p);
  EditText name=a.input(card,"Full name",me.optString("full_name"),TEXT),username=a.input(card,"Username",me.optString("username"),TEXT),contact=a.input(card,"Contact number",me.optString("contact_number"),android.text.InputType.TYPE_CLASS_PHONE);
  a.mainAction("Save profile",Ui.FILLED,()->{try{validateAccount(name,username,contact);}catch(FieldErrors e){return;}a.command("update_profile",a.json("full_name",value(name),"username",value(username),"contact_number",value(contact)),r->{settings();a.info("Profile updated.");});});
 }
 private void password(){
  LinearLayout p=a.page("Change password");int secret=TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;LinearLayout card=ui.card(p);
  EditText current=a.input(card,"Current password","",secret),next=a.input(card,"New password","",secret),confirmation=a.input(card,a.isOwner()?"Confirm new password":"Confirm password","",secret);
  a.mainAction("Save password",Ui.FILLED,()->{
   String currentPassword=value(current),newPassword=value(next),confirmed=value(confirmation);boolean bad=false;
   if(currentPassword.isEmpty()){a.fieldError(current,"Enter your current password.");bad=true;}
   try{Rules.password(newPassword);}catch(IllegalArgumentException e){a.fieldError(next,e.getMessage());bad=true;}
   if(!newPassword.equals(confirmed)){a.fieldError(confirmation,"New passwords do not match.");bad=true;}
   if(bad)return;a.clearMessage();
   a.query("me",a.json(),me->api("POST","/functions/v1/username-login",a.json("username",me.optString("username"),"password",currentPassword),session->{
    JSONObject identity=session.optJSONObject("user");if(identity==null||!me.optString("user_id").equals(identity.optString("id"))){a.fieldError(current,"Current password is incorrect.");return;}
    a.acceptTokens(session);
    api("GET","/auth/v1/user",null,user->{
     if(!me.optString("user_id").equals(user.optString("id"))){a.endSession("Access denied. Sign in again.");return;}
     Runnable update=()->api("PUT","/auth/v1/user",a.json("password",newPassword),saved->{if(!me.optString("user_id").equals(saved.optString("id"))){a.error("Password update was not confirmed. Sign in again to check the outcome.");return;}a.endSession("Password updated.");});
     JSONArray factors=user.optJSONArray("factors");boolean requiresMfa=false;if(factors!=null)for(int i=0;i<factors.length();i++){JSONObject factor=factors.optJSONObject(i);if(factor!=null&&"verified".equals(factor.optString("status"))){requiresMfa=true;break;}}
     if(requiresMfa)confirmManagedMfa("Verify password change",update);else update.run();
    });
   }));
  });
 }

 void staffAccounts(){
  if(!owner())return;LinearLayout p=a.page("Staff accounts");
  LinearLayout top=ui.row(p);top.setGravity(Gravity.END);Button add=new Button(a);add.setText("+ Add staff");ui.styled(add,Ui.TONAL_BTN);add.setMinHeight(ui.dp(44));add.setOnClickListener(v->staffForm(null));top.addView(add,new LinearLayout.LayoutParams(-2,ui.dp(44)));
  a.query("staff",a.json(),r->{JSONArray rows=r.optJSONArray("items");if(rows==null||rows.length()==0){ui.muted(p,"No staff accounts yet.");return;}
   for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;
    LinearLayout c=ui.card(p);c.setClickable(true);c.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));LinearLayout line=ui.row(c);
    line.addView(ui.avatar(row.optString("full_name")),new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
    LinearLayout names=new LinearLayout(a);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(ui.dp(10),0,0,0);names.addView(ui.tv(row.optString("full_name"),15,Ui.INK,ui.headBold));names.addView(ui.tv("@"+row.optString("username")+" · "+row.optString("contact_number"),12,Ui.MUTED,ui.body));
    line.addView(names,new LinearLayout.LayoutParams(0,-2,1));line.addView(row.optBoolean("active")?ui.pill("Active",Ui.GREEN_BG,Ui.GREEN):ui.pill("Inactive",Ui.GREY_BG,Ui.MUTED));
    c.setContentDescription(row.optString("full_name")+" • "+row.optString("username")+" • "+(row.optBoolean("active")?"Active":"Inactive"));c.setOnClickListener(v->staffForm(row));}
  });
 }
 private void staffForm(JSONObject user){
  if(!owner())return;LinearLayout p=a.page(user==null?"Create Staff account":"Edit Staff account",user==null?"":user.optString("user_id"));
  LinearLayout card=ui.card(p);ui.heading(card,user==null?"New staff account":"Edit account · "+user.optString("full_name"));
  LinearLayout[] one=twoUp(card),two=twoUp(card);
  EditText name=a.input(one[0],"Full name",user==null?"":user.optString("full_name"),TEXT),username=a.input(one[1],"Username",user==null?"":user.optString("username"),TEXT),contact=a.input(two[0],"Contact number",user==null?"":user.optString("contact_number"),android.text.InputType.TYPE_CLASS_PHONE);
  EditText temporary=user==null?a.input(two[1],"Temporary password","",TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD):null;
  LinearLayout status=ui.row(card);status.addView(ui.tv("Role: Staff     ",13,Ui.MUTED,ui.body));status.addView(ui.tv("Status  ",13,Ui.MUTED,ui.bodySemi));
  int[] active={user==null||user.optBoolean("active")?0:1};String savedStatus=a.choice("staff_status");if(!savedStatus.isEmpty())active[0]=Integer.parseInt(savedStatus);
  Ui.Chips seg=ui.segmented(status,new String[]{"Active","Inactive"},active[0],i->{active[0]=i;a.rememberChoice("staff_status",Integer.toString(i));});
  seg.view.getLayoutParams().width=ui.dp(220);
  a.mainAction("Save account",Ui.FILLED,()->{
   try{validateAccount(name,username,contact);}catch(FieldErrors e){return;}
   if(temporary!=null){if(value(temporary).isEmpty()){a.fieldError(temporary,"Complete the required fields.");return;}try{Rules.password(value(temporary));}catch(IllegalArgumentException e){a.fieldError(temporary,e.getMessage());return;}}
   JSONObject payload=a.json("operation",user==null?"create":"update","full_name",value(name),"username",value(username),"contact_number",value(contact),"active",active[0]==0);try{if(user!=null)payload.put("user_id",user.optString("user_id"));else payload.put("temporary_password",value(temporary));}catch(JSONException e){throw new IllegalArgumentException("Invalid account details.");}confirmStaffMfa(payload);});
 }
 private void confirmStaffMfa(JSONObject payload){confirmManagedMfa("Verify Staff account change",()->a.accountCommand(payload,r->{staffAccounts();a.info("Staff account saved.");}));}
 private void confirmManagedMfa(String title,Runnable verified){
  LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(ui.dp(20),ui.dp(8),ui.dp(20),0);TextView codeLabel=ui.label(form,"Verification code");
  // A one-time code must not enter general form drafts or saved instance state.
  EditText code=new EditText(a);ui.styleInput(code);code.setSaveEnabled(false);code.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);code.setContentDescription("Verification code");code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);form.addView(code);
  a.showDialog(new android.app.AlertDialog.Builder(a).setTitle(title).setView(form).setNegativeButton("Cancel",(dialog,which)->code.setText("")).setPositiveButton("Verify and save",(dialog,which)->{String entered=value(code);code.setText("");a.runValidated(()->verifyMfa(entered,verified));}).create());
 }

 private void verifyMfa(String code,Runnable verified){if(!code.matches("[0-9]{6}"))throw new IllegalArgumentException("Enter a valid verification code.");api("GET","/auth/v1/user",null,user->{JSONArray factors=user.optJSONArray("factors");String id="";if(factors!=null)for(int i=0;i<factors.length();i++){JSONObject factor=factors.optJSONObject(i);if("verified".equals(factor.optString("status"))&&"totp".equals(factor.optString("factor_type"))){id=factor.optString("id");break;}}if(id.isEmpty()){a.error("A managed MFA factor must be enrolled by the designated administrator.");return;}String path="/auth/v1/factors/"+id;api("POST",path+"/challenge",a.json(),challenge->verifyCode(path,challenge.optString("id"),code,verified));});}
 private void verifyCode(String path,String challenge,String code,Runnable verified){Backend expected=a.backend();expected.call(path+"/verify",a.json("challenge_id",challenge,"code",code),session->{if(expected!=a.backend()||a.isDestroyed()||a.isFinishing())return;if(session.optString("access_token").isEmpty()&&session.optJSONObject("session")==null){a.error("Verification was not confirmed. Try again.");return;}a.acceptTokens(session);verified.run();},e->{if(expected!=a.backend()||a.isDestroyed()||a.isFinishing())return;if(e.status==401){a.endSession("Session expired. Sign in again.");return;}a.error(e.status==400||e.status==403||e.status==422?"Enter a valid verification code.":e.getMessage());});}

}
