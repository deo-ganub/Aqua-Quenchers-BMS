package com.aquaquenchers.bms;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** Native shared shell. Financial actions always require a confirmed backend response. */
public final class MainActivity extends Activity {
    private static final long INACTIVITY=30*60*1000L;
    private LinearLayout content,navigation;
    private TextView message;
    private ProgressBar loading;
    private int outstanding=0,screen=0,loginGeneration=0;
    private String currentPage="Sign in",draftScope="Sign in";
    private Session state;
    private final Handler clock=new Handler(Looper.getMainLooper());
    private OperationsUi operations;
    private OwnerUi ownerUi;
    private final Set<Dialog> dialogs=new HashSet<>();
    static final class Session {
        Backend backend=new Backend(BuildConfig.SUPABASE_URL,BuildConfig.SUPABASE_ANON_KEY);
        JSONObject profile,reportSnapshot;byte[] pendingPdf;
        com.aquaquenchers.classifier.ExpenseSuggestionController expenseState=new com.aquaquenchers.classifier.ExpenseSuggestionController();
        long lastInteraction=SystemClock.elapsedRealtime();
        String username="",route="",routeEntity=""; boolean skipCapture;
        final Map<String,String> drafts=new HashMap<>(),keys=new HashMap<>();
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);content=findViewById(R.id.content);navigation=findViewById(R.id.navigation);message=findViewById(R.id.message);loading=findViewById(R.id.loading);
        Object retained=getLastNonConfigurationInstance();state=retained instanceof Session?(Session)retained:new Session();
        if(saved!=null){state.route=saved.getString("route",state.route);state.routeEntity=saved.getString("route_entity",state.routeEntity);ArrayList<String> keys=saved.getStringArrayList("draft_keys"),values=saved.getStringArrayList("draft_values");if(keys!=null&&values!=null)for(int i=0;i<Math.min(keys.size(),values.size());i++)state.drafts.put(keys.get(i),values.get(i));}
        if(saved!=null){ArrayList<String> hashes=saved.getStringArrayList("request_hashes"),ids=saved.getStringArrayList("request_ids");if(hashes!=null&&ids!=null)for(int i=0;i<Math.min(hashes.size(),ids.size());i++)state.keys.put(hashes.get(i),ids.get(i));}
        operations=new OperationsUi(this);ownerUi=new OwnerUi(this);
        if(state.profile==null)signIn();else{navigation();restoreRoute();}
        clock.postDelayed(idleCheck,60000);
    }
    private final Runnable idleCheck=new Runnable(){public void run(){if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY)endSession("Session expired. Sign in again.");clock.postDelayed(this,60000);}};
    @Override public void onUserInteraction(){super.onUserInteraction();if(state!=null){if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}state.lastInteraction=SystemClock.elapsedRealtime();}}
    @Override protected void onResume(){super.onResume();if(state!=null&&state.profile!=null){if(SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY)endSession("Session expired. Sign in again.");else query("me",json(),p->{boolean changed=!state.profile.optString("role").equals(p.optString("role"));state.profile=p;navigation();if(changed)showHome();});}}
    @Override protected void onDestroy(){clock.removeCallbacksAndMessages(null);dismissDialogs();if(ownerUi!=null)ownerUi.close();if(state!=null){state.expenseState.cancelPending();if(isFinishing())state.backend.close();}super.onDestroy();}
    @Override public Object onRetainNonConfigurationInstance(){captureDrafts();return state;}
    @Override protected void onSaveInstanceState(Bundle out){captureDrafts();out.putString("route",state.route);out.putString("route_entity",state.routeEntity);out.putStringArrayList("request_hashes",new ArrayList<>(state.keys.keySet()));out.putStringArrayList("request_ids",new ArrayList<>(state.keys.values()));out.putStringArrayList("draft_keys",new ArrayList<>(state.drafts.keySet()));out.putStringArrayList("draft_values",new ArrayList<>(state.drafts.values()));super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(state.profile!=null)showHome();else super.onBackPressed();}
    private void captureDrafts(){if(content==null||state==null||"Sign in".equals(currentPage))return;if(state.skipCapture){state.skipCapture=false;return;}capture(content);}
    private void capture(View view){if(view instanceof EditText){EditText e=(EditText)view;if(e.getTag()!=null&&!e.getTag().toString().toLowerCase(java.util.Locale.ROOT).contains("verification code")&&(e.getInputType()&android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD)==0)state.drafts.put(draftScope+"/"+e.getTag(),e.getText().toString());}if(view instanceof Spinner&&view.getTag()!=null)state.drafts.put(draftScope+"/select/"+view.getTag(),Integer.toString(((Spinner)view).getSelectedItemPosition()));if(view instanceof android.view.ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)capture(g.getChildAt(i));}}
    public com.aquaquenchers.classifier.ExpenseSuggestionController expenseState(){return state.expenseState;}
    public JSONObject reportSnapshot(){return state.reportSnapshot;}
    public void reportSnapshot(JSONObject report){if(isOwner())state.reportSnapshot=report;}
    public byte[] pendingPdf(){return state.pendingPdf;}
    public void pendingPdf(byte[] bytes){if(isOwner())state.pendingPdf=bytes;}
    public boolean isOwner(){return state.profile!=null&&"owner".equalsIgnoreCase(state.profile.optString("role"));}
    public String recorder(){return state.profile==null?"":state.profile.optString("full_name",state.username);}
    public Backend backend(){return state.backend;}
    public LinearLayout page(String title){return page(title,"");}
    public LinearLayout page(String title,String entity){captureDrafts();currentPage=title;draftScope=(state.profile==null?"":state.profile.optString("user_id")+"/")+title+"/"+entity;if(!"Sign in".equals(title)){state.route=title;state.routeEntity=entity;}screen++;content.removeAllViews();message.setVisibility(View.GONE);TextView heading=text(content,title);heading.setTypeface(getResources().getFont(R.font.poppins_regular));heading.setTextSize(getResources().getDimension(R.dimen.title_size)/getResources().getDisplayMetrics().scaledDensity);return content;}
    public TextView text(LinearLayout parent,String value){TextView t=new TextView(this);t.setText(value);t.setTextColor(Color.rgb(18,66,85));t.setTextSize(16);t.setPadding(0,10,0,10);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    public EditText input(LinearLayout parent,String label,String initial,int inputType){text(parent,label);EditText e=new EditText(this);e.setTag(label);e.setContentDescription(label);e.setHint(label);e.setInputType(inputType);e.setText(state.drafts.getOrDefault(draftScope+"/"+label,initial));e.setMinHeight(dp(48));e.setSingleLine((inputType&android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    public Spinner select(LinearLayout parent,String label,String[] values){text(parent,label);Spinner spinner=new Spinner(this);spinner.setTag(label);spinner.setContentDescription(label);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);spinner.setAdapter(adapter);restoreSelection(spinner);spinner.setMinimumHeight(dp(48));parent.addView(spinner,new LinearLayout.LayoutParams(-1,-2));return spinner;}
    public String choice(String key){return state.drafts.getOrDefault(draftScope+"/choice/"+key,"");}
    public void rememberChoice(String key,String id){state.drafts.put(draftScope+"/choice/"+key,id==null?"":id);}
    public void restoreSelection(Spinner spinner){String saved=state.drafts.get(draftScope+"/select/"+spinner.getTag());if(saved!=null)try{int index=Integer.parseInt(saved);if(index>=0&&index<spinner.getCount())spinner.setSelection(index);}catch(NumberFormatException ignored){}}
    public Button button(LinearLayout parent,String label,Runnable action){Button button=new Button(this);button.setText(label);button.setAllCaps(false);button.setMinHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);parent.addView(button,lp);button.setOnClickListener(v->{try{if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}action.run();}catch(IllegalArgumentException e){error(e.getMessage());}catch(Exception e){error("Check the entered details and try again.");}});return button;}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    public void showDialog(Dialog dialog){dialogs.add(dialog);dialog.setOnDismissListener(d->dialogs.remove(dialog));if(dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();}
    private void dismissDialogs(){for(Dialog d:new ArrayList<>(dialogs))if(d.isShowing())d.dismiss();dialogs.clear();}
    private boolean canWrite(){if(isDestroyed())return false;if(state.profile==null){error("Sign in to continue.");return false;}if(SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return false;}return true;}
    public void runValidated(Runnable action){try{action.run();}catch(IllegalArgumentException e){error(e.getMessage());}}
    public void error(String text){message.setText(text);message.setTextColor(Color.rgb(179,38,30));message.setVisibility(View.VISIBLE);}
    public void info(String text){message.setText(text);message.setTextColor(Color.rgb(0,100,65));message.setVisibility(View.VISIBLE);}
    public String today(){return LocalDate.now(Rules.MANILA).toString();}
    public String timestamp(String encoded){return Rules.timestamp(encoded);}
    public String money(long value){return Rules.money(value);}
    public JSONObject json(Object...pairs){JSONObject result=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)result.put(String.valueOf(pairs[i]),pairs[i+1]);return result;}catch(JSONException e){throw new IllegalArgumentException("Invalid request.");}}
    private String requestHash(String payload){try{String scoped=(state.profile==null?"":state.profile.optString("user_id"))+"/"+payload;byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(scoped.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hash=new StringBuilder();for(byte b:bytes)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return hash.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private void busy(boolean yes){outstanding=Math.max(0,outstanding+(yes?1:-1));loading.setVisibility(outstanding>0?View.VISIBLE:View.GONE);}
    public void query(String resource,JSONObject fields,Consumer<JSONObject> success){
        JSONObject request;
        try{request=new JSONObject(fields.toString());request.put("resource",resource);}catch(JSONException e){error("Invalid query.");return;}
        int origin=screen;
        rpc("aq_query",request,result->{
            if(origin!=screen)return;
            try{
                boolean validRole="owner".equals(result.optString("role"))||"staff".equals(result.optString("role"));
                if("me".equals(resource)&&(!Rules.uuid(result.optString("user_id"))||!result.optBoolean("active")||!validRole)){
                    error("The service did not confirm an active account. Sign in again.");return;
                }
                success.accept(result);
            }catch(IllegalArgumentException e){error(e.getMessage());}
            catch(Exception e){error("The response could not be displayed. Refresh and try again.");}
        },false);
    }
    public void command(String operation,JSONObject fields,Consumer<JSONObject> success){if(!canWrite())return;try{fields.remove("request_id");fields.put("operation",operation);String fingerprint=requestHash(fields.toString());String key=state.keys.computeIfAbsent(fingerprint,k->UUID.randomUUID().toString());fields.put("request_id",key);rpc("aq_command",fields,result->{String idField="update_profile".equals(operation)?"user_id":"id";if(!Rules.uuid(result.optString(idField))||(!"update_profile".equals(operation)&&!operation.endsWith("customer")&&result.optString("reference").isEmpty())){error("The service did not confirm this save. Keep the inputs and retry safely.");return;}state.keys.remove(fingerprint);if("update_profile".equals(operation))state.profile=result;if("create_expense".equals(operation))state.expenseState=new com.aquaquenchers.classifier.ExpenseSuggestionController();state.drafts.keySet().removeIf(k->k.startsWith(draftScope+"/"));state.skipCapture=true;success.accept(result);},true);}catch(JSONException e){error("Invalid command.");}}
    public void accountCommand(JSONObject fields,Consumer<JSONObject> success){if(!canWrite()||!isOwner())return;Session sent=state;try{fields.remove("request_id");JSONObject publicFields=new JSONObject(fields.toString());publicFields.remove("temporary_password");String fingerprint=requestHash("staff-admin/"+publicFields.toString());fields.put("request_id",state.keys.computeIfAbsent(fingerprint,k->UUID.randomUUID().toString()));busy(true);sent.backend.call("/functions/v1/staff-admin",fields,r->{busy(false);if(sent!=state)return;if(!Rules.uuid(r.optString("user_id"))||!"staff".equals(r.optString("role"))){error("The service did not confirm this account change. Retry with the same details.");return;}state.keys.remove(fingerprint);state.drafts.keySet().removeIf(k->k.startsWith(draftScope+"/"));state.skipCapture=true;success.accept(r);},e->{busy(false);if(sent==state){if(e.status==401||(e.status==403&&!"Enter a valid verification code.".equals(e.getMessage()))){endSession("Session expired. Sign in again.");return;}if(e.status==400)state.keys.remove(fingerprint);error(e.getMessage());}});}catch(JSONException e){error("Invalid account details.");}}
    private void rpc(String function,JSONObject request,Consumer<JSONObject> success,boolean mutation){rpcAttempt(function,request,success,mutation,false);}
    private void rpcAttempt(String function,JSONObject request,Consumer<JSONObject> success,boolean mutation,boolean refreshed){Session sent=state;busy(true);state.backend.call("/rest/v1/rpc/"+function,json("p_request",request),r->{busy(false);if(sent==state)success.accept(r);},e->{busy(false);if(sent!=state)return;if(e.status==401){if(refreshed){endSession("Session expired. Sign in again.");return;}refreshThen(()->rpcAttempt(function,request,success,mutation,true));}else if(e.status==403&&"Session expired. Sign in again.".equals(e.getMessage())){endSession(e.getMessage());}else if(e.status==403){endSession("Access denied. Sign in again.");}else error(e.getMessage());});}
    private void refreshThen(Runnable retry){Session pending=state;if(state.backend.refreshToken.isEmpty()||SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}state.backend.call("/auth/v1/token?grant_type=refresh_token",json("refresh_token",state.backend.refreshToken),r->{if(pending!=state||isDestroyed())return;acceptTokens(r);retry.run();},e->{if(pending==state&&!isDestroyed())endSession("Session expired. Sign in again.");});}
    public void acceptTokens(JSONObject response){JSONObject s=response.optJSONObject("session");if(s==null)s=response;state.backend.accessToken=s.optString("access_token");if(s.has("refresh_token"))state.backend.refreshToken=s.optString("refresh_token");}
    private void signIn(){navigation.removeAllViews();LinearLayout p=page("Sign in");text(p,"Water refilling business management");EditText username=input(p,"Username",state.username,android.text.InputType.TYPE_CLASS_TEXT);EditText password=input(p,"Password","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);button(p,"Sign in",()->{String u=username.getText().toString().trim(),pass=password.getText().toString();username.setError(null);password.setError(null);if(u.isEmpty())username.setError("Enter your username.");if(pass.isEmpty())password.setError("Enter your password.");if(u.isEmpty()||pass.isEmpty()){error(u.isEmpty()?"Enter your username.":"Enter your password.");return;}Session pending=state;int attempt=++loginGeneration;busy(true);state.backend.call("/functions/v1/username-login",json("username",u,"password",pass),r->{busy(false);if(pending!=state||attempt!=loginGeneration||isDestroyed())return;acceptTokens(r);state.username=u;state.lastInteraction=SystemClock.elapsedRealtime();password.setText("");query("me",json(),profile->{if(pending!=state||attempt!=loginGeneration||isDestroyed())return;state.profile=profile;navigation();restoreRoute();});},e->{busy(false);if(pending==state&&attempt==loginGeneration&&!isDestroyed())error(e.status==0?e.getMessage():"Invalid username or password.");});});text(p,"Account recovery is handled by the designated administrator.");}
    private void navigation(){navigation.removeAllViews();String[] routes=isOwner()?new String[]{"Dashboard","Orders","Customers","Payments","Expenses","Reports","Settings"}:new String[]{"Home","Orders","POS","Customers","Settings"};for(String route:routes){Button b=new Button(this);b.setText(route);b.setAllCaps(false);navigation.addView(b,new LinearLayout.LayoutParams(-2,dp(52)));b.setOnClickListener(v->navigate(route));}}
    private void restoreRoute(){if(ownerUi.restore(state.route,state.routeEntity)||operations.restore(state.route,state.routeEntity))return;showHome();}
    private void navigate(String route){switch(route){case"Dashboard":case"Home":showHome();break;case"Orders":operations.orders();break;case"Customers":operations.customers();break;case"POS":operations.pos();break;case"Payments":operations.payments();break;case"Expenses":ownerUi.expenses();break;case"Reports":ownerUi.reports();break;case"Settings":ownerUi.settings();break;default:showHome();}}
    public void showHome(){LinearLayout p=page(isOwner()?"Dashboard":"Home");text(p,recorder()+" • "+(isOwner()?"Owner":"Staff"));if(isOwner()){EditText start=input(p,"Start date",today(),android.text.InputType.TYPE_CLASS_DATETIME);EditText end=input(p,"End date",today(),android.text.InputType.TYPE_CLASS_DATETIME);LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);p.addView(summary);Runnable load=()->{Rules.period(start.getText().toString(),end.getText().toString());query("dashboard",json("start_date",start.getText().toString(),"end_date",end.getText().toString()),r->{summary.removeAllViews();ownerUi.summary(summary,r);});};button(p,"Apply period",load);runValidated(load);button(p,"Inventory",ownerUi::inventory);button(p,"Customer balances",operations::customers);button(p,"Create Order",()->operations.newOrder(null));button(p,"Walk-in Sale",operations::pos);button(p,"Reports",ownerUi::reports);}else query("home",json(),r->{text(p,"Hello, "+recorder()+".");text(p,"Orders: "+r.optInt("orders_count"));text(p,"For Pickup: "+r.optInt("for_pickup_count"));text(p,"Ready for Delivery: "+r.optInt("ready_for_delivery_count"));text(p,"Pending / carry-over gallons: "+r.optInt("pending_quantity"));});button(p,"For Pickup",()->operations.ordersFiltered("For Pickup"));button(p,"Ready for Delivery",()->operations.ordersFiltered("Ready for Delivery"));if(!isOwner()){button(p,"New order",()->operations.newOrder(null));button(p,"Walk-in sale",operations::pos);}button(p,"Pending / carry-over",operations::pending);query("orders",json(),r->{text(p,"Recent orders");JSONArray items=r.optJSONArray("items");if(items!=null)for(int i=0;i<Math.min(items.length(),10);i++){JSONObject o=items.optJSONObject(i);if(o!=null)button(p,o.optString("reference")+" • "+o.optString("customer_name")+" • "+Rules.label(o.optString("status"))+(isOwner()?"":" • "+o.optInt("quantity")+" gallons • Balance "+money(o.optLong("balance_centavos")))+" • View order",()->operations.order(o.optString("id")));}});button(p,"View Orders",operations::orders);}
    public void endSession(String reason){dismissDialogs();state.expenseState.cancelPending();ownerUi.close();Backend old=state.backend;if(!old.accessToken.isEmpty()){old.call("/rest/v1/rpc/aq_logout",json(),r->{},e->{});old.call("/auth/v1/logout?scope=local",json(),r->{},e->{});}old.close();state=new Session();currentPage="Sign in";draftScope="Sign in";operations=new OperationsUi(this);ownerUi=new OwnerUi(this);signIn();info(reason);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(state.profile==null||!isOwner()||SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}ownerUi.activityResult(request,result,data);}
    public void openOrders(){operations.orders();}
    public void openPayments(){operations.payments();}
    public void openPending(){operations.pending();}
}
