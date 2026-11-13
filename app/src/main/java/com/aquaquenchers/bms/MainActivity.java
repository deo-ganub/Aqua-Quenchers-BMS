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
    private LinearLayout content,navigation,bottomNavigation,actionBar,topbar;
    private ScrollView sidebar,scroll;
    private TextView message,brand,accountLabel;
    private ProgressBar loading;
    private Ui ui;
    private int outstanding=0,screen=0,loginGeneration=0;
    private String currentPage="Sign in",draftScope="Sign in";
    private Session state;
    private final Handler clock=new Handler(Looper.getMainLooper());
    private OperationsUi operations;
    private OwnerUi ownerUi;
    private HomeUi home;
    private EditText signInUser;
    private final Map<String,View> navItems=new LinkedHashMap<>();
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
        super.onCreate(saved);if(!BuildConfig.DEBUG)getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);content=findViewById(R.id.content);navigation=findViewById(R.id.navigation);message=findViewById(R.id.message);loading=findViewById(R.id.loading);
        bottomNavigation=findViewById(R.id.bottom_navigation);actionBar=findViewById(R.id.action_bar);topbar=findViewById(R.id.topbar);sidebar=findViewById(R.id.sidebar);scroll=findViewById(R.id.scroll);brand=findViewById(R.id.brand);accountLabel=findViewById(R.id.account_label);
        ui=new Ui(this);brand.setTypeface(ui.headBold);accountLabel.setTypeface(ui.body);
        Object retained=getLastNonConfigurationInstance();state=retained instanceof Session?(Session)retained:new Session();
        if(saved!=null){state.route=saved.getString("route",state.route);state.routeEntity=saved.getString("route_entity",state.routeEntity);ArrayList<String> keys=saved.getStringArrayList("draft_keys"),values=saved.getStringArrayList("draft_values");if(keys!=null&&values!=null)for(int i=0;i<Math.min(keys.size(),values.size());i++)state.drafts.put(keys.get(i),values.get(i));}
        if(saved!=null){ArrayList<String> hashes=saved.getStringArrayList("request_hashes"),ids=saved.getStringArrayList("request_ids");if(hashes!=null&&ids!=null)for(int i=0;i<Math.min(hashes.size(),ids.size());i++)state.keys.put(hashes.get(i),ids.get(i));}
        operations=new OperationsUi(this);ownerUi=new OwnerUi(this);home=new HomeUi(this);
        if(state.profile==null)signIn();else{navigation();restoreRoute();}
        clock.postDelayed(idleCheck,60000);
    }
    private final Runnable idleCheck=new Runnable(){public void run(){if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY)endSession("Session expired. Sign in again.");clock.postDelayed(this,60000);}};
    @Override public void onUserInteraction(){super.onUserInteraction();if(state!=null){if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}state.lastInteraction=SystemClock.elapsedRealtime();}}
    @Override protected void onResume(){super.onResume();if(state!=null&&state.profile!=null){if(SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY)endSession("Session expired. Sign in again.");else query("me",json(),p->{boolean changed=!state.profile.optString("role").equals(p.optString("role"));state.profile=p;navigation();if(changed)showHome();});}}
    @Override protected void onDestroy(){clock.removeCallbacksAndMessages(null);dismissDialogs();if(ownerUi!=null)ownerUi.close();if(state!=null){state.expenseState.cancelPending();if(isFinishing())state.backend.close();}super.onDestroy();}
    @Override public Object onRetainNonConfigurationInstance(){captureDrafts();return state;}
    @Override protected void onSaveInstanceState(Bundle out){captureDrafts();out.putString("route",state.route);out.putString("route_entity",state.routeEntity);out.putStringArrayList("request_hashes",new ArrayList<>(state.keys.keySet()));out.putStringArrayList("request_ids",new ArrayList<>(state.keys.values()));out.putStringArrayList("draft_keys",new ArrayList<>(state.drafts.keySet()));out.putStringArrayList("draft_values",new ArrayList<>(state.drafts.values()));super.onSaveInstanceState(out);}
    @Override public void onBackPressed(){if(state.profile!=null)goBack();else super.onBackPressed();}
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
    Ui ui(){return ui;}
    OperationsUi operations(){return operations;}
    OwnerUi ownerUi(){return ownerUi;}
    boolean tablet(){return ui.tablet();}

    // ---------- page frame ----------
    private static final Set<String> ROOTS=new HashSet<>(Arrays.asList("Sign in","Home","Dashboard","Orders","Customers","Walk-in Sale / POS","Expenses","Financial summary","Settings"));
    static String displayTitle(String title){
        switch(title){
            case"Home":return "Today";
            case"Walk-in Sale / POS":return "Walk-in sale";
            case"Create Order":return "New order";
            case"Order Details":return "Order";
            case"Customer Profile":return "Customer";
            case"Add Customer":return "Add customer";
            case"Edit Customer":return "Edit customer";
            case"Record Pickup":return "Record pickup";
            case"Reschedule Pickup":return "Reschedule pickup";
            case"Record Delivery":return "Record delivery";
            case"Record Payment":return "Collect payment";
            case"Sale saved":return "Walk-in sale";
            case"Pending gallons / Carry-over Details":return "Pending gallons";
            case"Financial summary":return "Reports";
            case"Report Preview":return "Report preview";
            case"Add Expense":return "Add expense";
            case"My account":return "Edit profile";
            case"Create Staff account":return "Add staff account";
            case"Edit Staff account":return "Edit staff account";
            case"Staff accounts":return "Staff accounts";
            default:return title;
        }
    }
    private String navRouteFor(String page){
        switch(page){
            case"Home":case"Dashboard":return isOwner()?"Dashboard":"Home";
            case"Orders":case"Order Details":case"Record Pickup":case"Reschedule Pickup":case"Record Delivery":case"Create Order":case"Pending gallons / Carry-over Details":return "Orders";
            case"Customers":case"Customer Profile":case"Add Customer":case"Edit Customer":return "Customers";
            case"Walk-in Sale / POS":case"Sale saved":return isOwner()?"Orders":"POS";
            case"Payments":case"Record Payment":case"Payment history":return isOwner()?"Payments":"Settings";
            case"Expenses":case"Add Expense":return "Expenses";
            case"Financial summary":case"Report Preview":return "Reports";
            default:return "Settings";
        }
    }
    public LinearLayout page(String title){return page(title,"");}
    public LinearLayout page(String title,String entity){
        captureDrafts();currentPage=title;draftScope=(state.profile==null?"":state.profile.optString("user_id")+"/")+title+"/"+entity;
        if(!"Sign in".equals(title)){state.route=title;state.routeEntity=entity;}
        screen++;content.removeAllViews();message.setVisibility(View.GONE);
        actionBar.removeAllViews();actionBar.setVisibility(View.GONE);
        int pad=(int)getResources().getDimension(R.dimen.page_padding);content.setPadding(pad,pad,pad,pad);content.setBackgroundColor(Ui.BG);
        scroll.scrollTo(0,0);
        if("Sign in".equals(title))return content;
        highlight(navRouteFor(title));
        LinearLayout header=ui.row(content);
        header.setPadding(0,0,0,ui.dp(8));
        if(!ROOTS.contains(title)&&!("Payments".equals(title)&&isOwner())&&state.profile!=null){
            TextView back=ui.tv("‹ Back",14,Ui.PRIMARY,ui.head);back.setPadding(0,ui.dp(8),ui.dp(12),ui.dp(8));back.setClickable(true);back.setContentDescription("Back");
            back.setOnClickListener(v->goBack());header.addView(back);
        }
        TextView heading=ui.tv(displayTitle(title),getResources().getDimension(R.dimen.title_size)/getResources().getDisplayMetrics().scaledDensity,Ui.INK,ui.headBold);
        heading.setSingleLine(true);heading.setEllipsize(TextUtils.TruncateAt.END);heading.setContentDescription(displayTitle(title));
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        return content;
    }
    /** The title row of the current page (Figma puts page actions such as "+ New order" on it). */
    LinearLayout headerRow(){View first=content.getChildCount()>0?content.getChildAt(0):null;return first instanceof LinearLayout?(LinearLayout)first:null;}
    /** One-button bottom action area: the single main action of a screen. */
    public Button mainAction(String label,int style,Runnable action){
        actionBar.removeAllViews();actionBar.setVisibility(View.VISIBLE);
        Button b=button(actionBar,label,style,action);
        ((LinearLayout.LayoutParams)b.getLayoutParams()).topMargin=0;b.setMinHeight(ui.dp(52));
        return b;
    }
    public void actionNote(String note){TextView t=ui.tv(note,12,Ui.MUTED,ui.body);t.setGravity(Gravity.CENTER);actionBar.addView(t,ui.lp(-1,-2,0,6,0,0));}
    public LinearLayout actionBar(){actionBar.removeAllViews();actionBar.setVisibility(View.VISIBLE);return actionBar;}

    // ---------- shared widgets (restyled) ----------
    public TextView text(LinearLayout parent,String value){TextView t=ui.tv(value,14,Ui.INK,ui.body);t.setPadding(0,ui.dp(5),0,ui.dp(5));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    public EditText input(LinearLayout parent,String label,String initial,int inputType){
        ui.label(parent,label);EditText e=new EditText(this);e.setTag(label);e.setContentDescription(label);e.setHint(label);e.setInputType(inputType);e.setText(state.drafts.getOrDefault(draftScope+"/"+label,initial));
        ui.styleInput(e);e.setSingleLine((inputType&android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE)==0);
        parent.addView(e,new LinearLayout.LayoutParams(-1,-2));
        TextView err=ui.smallError(parent);e.setTag(R.id.field_error,err);
        e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){clearFieldError(e);}public void afterTextChanged(Editable s){}});
        return e;
    }
    /** Search/field input without a visible label (the placeholder carries the meaning). */
    public EditText inputPlain(LinearLayout parent,String label,String initial,int inputType){
        EditText e=new EditText(this);e.setTag(label);e.setContentDescription(label);e.setHint(label);e.setInputType(inputType);e.setText(state.drafts.getOrDefault(draftScope+"/"+label,initial));
        ui.styleInput(e);e.setSingleLine(true);parent.addView(e,ui.lp(-1,-2,0,0,0,8));
        return e;
    }
    LinearLayout currentContent(){return content;}
    /** Quantity control for any screen: stepper, typed entry and quick picks share one EditText. */
    public EditText quantity(LinearLayout parent,String label,String initial,int[] picks,String unit){
        String[] names=new String[picks.length];for(int i=0;i<picks.length;i++)names[i]=Integer.toString(picks[i]);
        return quantity(parent,label,initial,picks,names,unit);
    }
    public EditText quantity(LinearLayout parent,String label,String initial,int[] picks,String[] names,String unit){
        EditText e=new EditText(this);e.setTag(label);e.setContentDescription(label);e.setText(state.drafts.getOrDefault(draftScope+"/"+label,initial));
        ui.stepper(parent,e,unit);
        Ui.Chips[] chips=new Ui.Chips[1];
        chips[0]=ui.quickPicks(parent,names,-1,i->e.setText(Integer.toString(picks[i])));
        Runnable sync=()->{String v=e.getText().toString().trim();int found=-1;for(int i=0;i<picks.length;i++)if(Integer.toString(picks[i]).equals(v))found=i;chips[0].select(found,false);if(found<0)for(TextView t:chips[0].items){t.setBackground(ui.shape(Ui.WHITE,Ui.BORDER,40));t.setTextColor(Ui.INK);}};
        e.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){clearFieldError(e);sync.run();}public void afterTextChanged(Editable s){}});
        TextView err=ui.smallError(parent);e.setTag(R.id.field_error,err);sync.run();
        return e;
    }
    /** Money control with quick amounts (Exact / peso picks). */
    public void quickAmounts(LinearLayout parent,EditText target,String[] labels,String[] values){
        ui.quickPicks(parent,labels,-1,i->target.setText(values[i]));
    }
    public void fieldError(EditText field,String text){
        TextView err=field.getTag(R.id.field_error) instanceof TextView?(TextView)field.getTag(R.id.field_error):null;
        ui.flagInvalid(field,true);if(err!=null){err.setText(text);err.setVisibility(View.VISIBLE);}
        field.requestFocus();
    }
    public void clearFieldError(EditText field){
        ui.flagInvalid(field,false);
        if(field.getTag(R.id.field_error) instanceof TextView){TextView err=(TextView)field.getTag(R.id.field_error);err.setText("");err.setVisibility(View.GONE);}
    }
    public Spinner select(LinearLayout parent,String label,String[] values){
        ui.label(parent,label);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(ui.shape(Ui.WHITE,0xFFCCD6E0,12));
        Spinner spinner=new Spinner(this);spinner.setTag(label);spinner.setContentDescription(label);spinner.setBackground(null);spinner.setPadding(ui.dp(10),0,ui.dp(30),0);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);spinner.setAdapter(adapter);restoreSelection(spinner);spinner.setMinimumHeight(ui.dp(48));
        frame.addView(spinner,new FrameLayout.LayoutParams(-1,ui.dp(48)));
        TextView arrow=ui.tv("▾",14,Ui.MUTED,ui.body);FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.END|Gravity.CENTER_VERTICAL);ap.rightMargin=ui.dp(12);arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);frame.addView(arrow,ap);
        parent.addView(frame,new LinearLayout.LayoutParams(-1,-2));return spinner;
    }
    public String choice(String key){return state.drafts.getOrDefault(draftScope+"/choice/"+key,"");}
    public void rememberChoice(String key,String id){state.drafts.put(draftScope+"/choice/"+key,id==null?"":id);}
    public void restoreSelection(Spinner spinner){String saved=state.drafts.get(draftScope+"/select/"+spinner.getTag());if(saved!=null)try{int index=Integer.parseInt(saved);if(index>=0&&index<spinner.getCount())spinner.setSelection(index);}catch(NumberFormatException ignored){}}
    private static int styleFor(String label){String l=label.toLowerCase(Locale.ROOT);if(l.startsWith("cancel")||l.startsWith("back")||l.equals("done"))return Ui.OUTLINE;if(l.startsWith("log out"))return Ui.DANGER_OUTLINE;return Ui.FILLED;}
    public Button button(LinearLayout parent,String label,Runnable action){return button(parent,label,styleFor(label),action);}
    public Button button(LinearLayout parent,String label,int style,Runnable action){Button button=new Button(this);button.setText(label);ui.styled(button,style);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=ui.dp(8);parent.addView(button,lp);button.setOnClickListener(v->{try{if(state.profile!=null&&SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}action.run();}catch(IllegalArgumentException e){error(e.getMessage());}catch(Exception e){error("Check the entered details and try again.");}});return button;}
    public int dp(int value){return ui.dp(value);}
    public void showDialog(Dialog dialog){dialogs.add(dialog);dialog.setOnDismissListener(d->dialogs.remove(dialog));if(!BuildConfig.DEBUG&&dialog.getWindow()!=null)dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.show();}
    private void dismissDialogs(){for(Dialog d:new ArrayList<>(dialogs))if(d.isShowing())d.dismiss();dialogs.clear();}
    private boolean canWrite(){if(isDestroyed())return false;if(state.profile==null){error("Sign in to continue.");return false;}if(SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return false;}return true;}
    public void runValidated(Runnable action){try{action.run();}catch(IllegalArgumentException e){error(e.getMessage());}}
    public void clearMessage(){message.setText("");message.setVisibility(View.GONE);}
    private void banner(CharSequence text,int bg,int fg){
        message.setText(text);message.setTextColor(fg);message.setTypeface(ui.body);message.setTextSize(13);
        message.setBackground(ui.shape(bg,0,12));message.setPadding(ui.dp(14),ui.dp(10),ui.dp(14),ui.dp(10));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);int m=ui.dp(12);lp.setMargins(m,m,m,0);message.setLayoutParams(lp);
        message.setVisibility(View.VISIBLE);
    }
    public void error(String text){banner(text,Ui.DANGER_BG,Ui.DANGER);}
    public void info(String text){banner(text,Ui.GREEN_BG,Ui.GREEN);}
    /** Success banner with the saved record on a second line, as in the Figma confirmations. */
    public void info(String title,String detail){
        SpannableStringBuilder s=new SpannableStringBuilder(title);s.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,s.length(),0);
        if(detail!=null&&!detail.isEmpty())s.append("\n").append(detail);
        banner(s,Ui.GREEN_BG,Ui.GREEN);
    }
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
    private void rpcAttempt(String function,JSONObject request,Consumer<JSONObject> success,boolean mutation,boolean refreshed){Session sent=state;busy(true);state.backend.call("/rest/v1/rpc/"+function,json("p_request",request),r->{busy(false);if(sent==state)success.accept(r);},e->{busy(false);if(sent!=state)return;if(e.status==401){if(refreshed){endSession("Session expired. Sign in again.");return;}refreshThen(()->rpcAttempt(function,request,success,mutation,true));}else if(e.status==403&&"Session expired. Sign in again.".equals(e.getMessage())){endSession(e.getMessage());}else if(e.status==403){revalidateDeniedSession(sent);}else error(e.getMessage());});}
    private void revalidateDeniedSession(Session sent){
        if(sent.profile==null){endSession("Access denied. Sign in again.");return;}
        busy(true);
        sent.backend.call("/rest/v1/rpc/aq_me",json(),profile->{
            busy(false);if(sent!=state||isDestroyed())return;
            String role=profile.optString("role");
            if(!profile.optBoolean("active")||!sent.profile.optString("user_id").equals(profile.optString("user_id"))||!("owner".equals(role)||"staff".equals(role))){endSession("Access denied. Sign in again.");return;}
            boolean roleChanged=!role.equals(sent.profile.optString("role"));
            state.profile=profile;navigation();if(roleChanged)showHome();
            accessDenied();
        },failure->{
            busy(false);if(sent!=state||isDestroyed())return;
            if(failure.status==401||failure.status==403){endSession(failure.status==401||"Session expired. Sign in again.".equals(failure.getMessage())?"Session expired. Sign in again.":"Access denied. Sign in again.");}
            else error("Access denied. Check your connection and try again.");
        });
    }
    /** Staff reaching an Owner-only area see the Figma "Access denied" state with a way back. */
    private void accessDenied(){
        error("Access denied.");
        if(!isOwner()&&actionBar!=null){mainAction("Back to Today",Ui.FILLED,this::showHome);}
    }
    private void refreshThen(Runnable retry){Session pending=state;if(state.backend.refreshToken.isEmpty()||SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}state.backend.call("/auth/v1/token?grant_type=refresh_token",json("refresh_token",state.backend.refreshToken),r->{if(pending!=state||isDestroyed())return;acceptTokens(r);retry.run();},e->{if(pending==state&&!isDestroyed())endSession("Session expired. Sign in again.");});}
    public void acceptTokens(JSONObject response){JSONObject s=response.optJSONObject("session");if(s==null)s=response;state.backend.accessToken=s.optString("access_token");if(s.has("refresh_token"))state.backend.refreshToken=s.optString("refresh_token");}

    // ---------- sign in (Staff phone / Owner tablet) ----------
    private void signIn(){
        navigation.removeAllViews();bottomNavigation.removeAllViews();navItems.clear();
        topbar.setVisibility(View.GONE);sidebar.setVisibility(View.GONE);bottomNavigation.setVisibility(View.GONE);
        LinearLayout p=page("Sign in");content.setPadding(0,0,0,0);
        boolean wide=ui.tablet();
        LinearLayout root=p;
        LinearLayout formHost;
        if(wide){
            LinearLayout split=new LinearLayout(this);split.setOrientation(LinearLayout.HORIZONTAL);p.addView(split,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout hero=hero(true);split.addView(hero,new LinearLayout.LayoutParams(0,ui.dp(660),1f));
            LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setPadding(ui.dp(40),ui.dp(24),ui.dp(40),ui.dp(24));right.setGravity(Gravity.CENTER_HORIZONTAL);
            split.addView(right,new LinearLayout.LayoutParams(0,-2,1.15f));
            LinearLayout chipRow=ui.row(right);chipRow.setGravity(Gravity.END);TextView portal=ui.pill("OWNER PORTAL",Ui.TONAL,Ui.PRIMARY);portal.setTypeface(ui.headBold);chipRow.addView(portal);
            formHost=signInCard(right,"OWNER ACCESS","Sign in to manage the station.","Access is limited to authorized accounts.");
            LinearLayout.LayoutParams cp=(LinearLayout.LayoutParams)formHost.getLayoutParams();cp.topMargin=ui.dp(60);
            TextView foot=ui.tv("Aqua Quenchers  ·  Business Management System",12,Ui.MUTED,ui.body);foot.setGravity(Gravity.CENTER);right.addView(foot,ui.lp(-1,-2,0,10,0,0));
        }else{
            LinearLayout hero=hero(false);p.addView(hero,new LinearLayout.LayoutParams(-1,ui.dp(300)));
            LinearLayout holder=new LinearLayout(this);holder.setOrientation(LinearLayout.VERTICAL);holder.setPadding(ui.dp(16),0,ui.dp(16),ui.dp(16));p.addView(holder,new LinearLayout.LayoutParams(-1,-2));
            formHost=signInCard(holder,null,"Sign in with your staff account.","Authorized accounts only.");
            LinearLayout.LayoutParams cp=(LinearLayout.LayoutParams)formHost.getLayoutParams();cp.topMargin=-ui.dp(70);
            TextView l1=ui.tv("Aqua Quenchers  ·  Staff workspace",12,Ui.MUTED,ui.body);l1.setGravity(Gravity.CENTER);holder.addView(l1,ui.lp(-1,-2,0,14,0,0));
            TextView l2=ui.tv("Ready when you are.",13,Ui.PRIMARY,ui.body);l2.setGravity(Gravity.CENTER);holder.addView(l2,ui.lp(-1,-2,0,6,0,0));
        }
    }
    private LinearLayout hero(boolean wide){
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF0B7CBF,0xFF044A75});hero.setBackground(g);
        int pad=ui.dp(wide?48:20);hero.setPadding(pad,ui.dp(wide?48:28),pad,ui.dp(wide?32:20));
        LinearLayout brandRow=ui.row(hero);
        FrameLayout logo=new FrameLayout(this);logo.setBackground(ui.shape(0x33FFFFFF,0x55FFFFFF,12));
        ImageView drop=ui.icon(R.drawable.ic_drop,Ui.WHITE,22);FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(ui.dp(22),ui.dp(22),Gravity.CENTER);logo.addView(drop,dp);
        brandRow.addView(logo,new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);names.setPadding(ui.dp(10),0,0,0);
        names.addView(ui.tv("Aqua Quenchers",wide?19:17,Ui.WHITE,ui.headBold));
        TextView sub=ui.tv("WATER REFILLING STATION",9,0xCCFFFFFF,ui.body);sub.setLetterSpacing(0.08f);names.addView(sub);
        brandRow.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        if(!wide){TextView staff=ui.pill("STAFF",0x33FFFFFF,Ui.WHITE);staff.setTypeface(ui.headBold);staff.setTextSize(10);brandRow.addView(staff);}
        View grow=new View(this);hero.addView(grow,new LinearLayout.LayoutParams(-1,0,1));
        if(wide){
            for(String s:new String[]{"Every refill.","Every detail.","In one place."})hero.addView(ui.tv(s,36,Ui.WHITE,ui.headBold),ui.lp(-1,-2,0,0,0,6));
            View grow2=new View(this);hero.addView(grow2,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout glass=new LinearLayout(this);glass.setOrientation(LinearLayout.VERTICAL);glass.setBackground(ui.shape(0x26FFFFFF,0x40FFFFFF,16));glass.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(14));
            glass.addView(ui.tv("One connected workspace",15,Ui.WHITE,ui.headBold));glass.addView(ui.tv("Customers  ·  Orders  ·  Payments",12,0xDDFFFFFF,ui.body));
            hero.addView(glass,ui.lp(-1,-2,0,0,0,18));
            hero.addView(ui.tv("AQUA QUENCHERS  •  BMS",10,0xCCFFFFFF,ui.body));
        }else{
            TextView kicker=ui.tv("STAFF WORKSPACE",10,Ui.WHITE,ui.headBold);kicker.setLetterSpacing(0.06f);hero.addView(kicker);
            hero.addView(ui.tv("Your shift starts here.",30,Ui.WHITE,ui.headBold),ui.lp(-1,-2,0,6,0,70));
        }
        return hero;
    }
    private LinearLayout signInCard(LinearLayout parent,String chip,String subtitle,String footnote){
        LinearLayout card=ui.card(parent);card.setBackground(ui.shape(Ui.WHITE,Ui.BORDER,24));card.setPadding(ui.dp(22),ui.dp(22),ui.dp(22),ui.dp(18));card.setElevation(ui.dp(4));
        if(chip!=null){TextView c=ui.pill(chip,Ui.TONAL,Ui.PRIMARY);c.setTypeface(ui.headBold);LinearLayout.LayoutParams cl=ui.lp(-2,-2,0,0,0,8);card.addView(c,cl);}
        card.addView(ui.tv("Welcome back",24,Ui.INK,ui.headBold));
        card.addView(ui.tv(subtitle,13,Ui.MUTED,ui.body));
        ui.divider(card);
        EditText username=input(card,"Username",state.username,android.text.InputType.TYPE_CLASS_TEXT);
        EditText password=input(card,"Password","",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        signInUser=username;
        button(card,"Sign in",Ui.FILLED,()->{String u=username.getText().toString().trim(),pass=password.getText().toString();clearFieldError(username);clearFieldError(password);if(u.isEmpty())fieldError(username,"Enter your username.");if(pass.isEmpty())fieldError(password,"Enter your password.");if(u.isEmpty()||pass.isEmpty())return;Session pending=state;int attempt=++loginGeneration;busy(true);state.backend.call("/functions/v1/username-login",json("username",u,"password",pass),r->{busy(false);if(pending!=state||attempt!=loginGeneration||isDestroyed())return;acceptTokens(r);state.username=u;state.lastInteraction=SystemClock.elapsedRealtime();password.setText("");query("me",json(),profile->{if(pending!=state||attempt!=loginGeneration||isDestroyed())return;state.profile=profile;navigation();restoreRoute();});},e->{busy(false);if(pending==state&&attempt==loginGeneration&&!isDestroyed()){if(e.status==429||e.status==0){error(e.status==429?"Sign-in unavailable. Try again later.":e.getMessage());}else{fieldError(username,"Invalid username or password.");ui.flagInvalid(password,true);}}});});
        LinearLayout note=ui.row(card);note.setGravity(Gravity.CENTER);note.setPadding(0,ui.dp(14),0,0);
        note.addView(ui.icon(R.drawable.ic_lock,Ui.PRIMARY,14));
        TextView nt=ui.tv("  "+footnote,12,Ui.MUTED,ui.body);note.addView(nt);
        text(card,"Account recovery is handled by the designated administrator.").setTextSize(12);
        return card;
    }

    // ---------- navigation ----------
    private void navigation(){
        navigation.removeAllViews();bottomNavigation.removeAllViews();navItems.clear();
        boolean owner=isOwner();boolean side=owner&&ui.tablet();
        topbar.setVisibility(View.VISIBLE);brand.setVisibility(owner||ui.tablet()?View.VISIBLE:View.GONE);
        accountLabel.setText(owner?"OWNER ACCOUNT":"STAFF ACCOUNT");accountLabel.setLetterSpacing(0.06f);
        sidebar.setVisibility(side?View.VISIBLE:View.GONE);bottomNavigation.setVisibility(side?View.GONE:View.VISIBLE);
        String[] routes=owner?new String[]{"Dashboard","Orders","Customers","Payments","Expenses","Reports","Settings"}:new String[]{"Home","Orders","POS","Customers","Settings"};
        for(String route:routes){
            View item=side?sideItem(route):bottomItem(route);
            item.setOnClickListener(v->navigate(route));item.setContentDescription(route);navItems.put(route,item);
            if(side)navigation.addView(item,ui.lp(-1,-2,0,0,0,4));else bottomNavigation.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        highlight(navRouteFor(currentPage));
    }
    private int iconFor(String route){
        switch(route){case"Dashboard":case"Home":return R.drawable.ic_home;case"Orders":return R.drawable.ic_orders;case"POS":return R.drawable.ic_pos;case"Customers":return R.drawable.ic_customers;case"Payments":return R.drawable.ic_payments;case"Expenses":return R.drawable.ic_expenses;case"Reports":return R.drawable.ic_reports;default:return R.drawable.ic_settings;}
    }
    private View sideItem(String route){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.HORIZONTAL);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(ui.dp(14),ui.dp(12),ui.dp(10),ui.dp(12));item.setClickable(true);
        ImageView icon=ui.icon(iconFor(route),Ui.MUTED,20);item.addView(icon);
        TextView label=ui.tv(route,14,Ui.INK,ui.head);label.setPadding(ui.dp(12),0,0,0);item.addView(label);
        item.setTag(new View[]{icon,label});return item;
    }
    private View bottomItem(String route){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(ui.dp(2),ui.dp(8),ui.dp(2),ui.dp(8));item.setClickable(true);
        ImageView icon=ui.icon(iconFor(route),Ui.MUTED,22);item.addView(icon);
        TextView label=ui.tv(route,10,Ui.MUTED,ui.head);label.setGravity(Gravity.CENTER);label.setSingleLine(true);item.addView(label,ui.lp(-2,-2,0,2,0,0));
        item.setTag(new View[]{icon,label});return item;
    }
    private void highlight(String route){
        for(Map.Entry<String,View> e:navItems.entrySet()){
            boolean on=e.getKey().equals(route);View item=e.getValue();View[] parts=(View[])item.getTag();
            item.setBackground(on?ui.shape(Ui.TONAL,0,12):null);
            ((ImageView)parts[0]).setColorFilter(on?Ui.PRIMARY:Ui.MUTED);((TextView)parts[1]).setTextColor(on?Ui.PRIMARY:(sidebar.getVisibility()==View.VISIBLE?Ui.INK:Ui.MUTED));
        }
    }
    private void restoreRoute(){if(ownerUi.restore(state.route,state.routeEntity)||operations.restore(state.route,state.routeEntity))return;showHome();}
    private void navigate(String route){switch(route){case"Dashboard":case"Home":showHome();break;case"Orders":operations.orders();break;case"Customers":operations.customers();break;case"POS":operations.pos();break;case"Payments":operations.payments();break;case"Expenses":ownerUi.expenses();break;case"Reports":ownerUi.reports();break;case"Settings":ownerUi.settings();break;default:showHome();}}
    /** Header Back: the parent of the current page, never a status shortcut. */
    private void goBack(){
        String entity=state.routeEntity==null?"":state.routeEntity;
        switch(currentPage){
            case"Order Details":case"Create Order":case"Pending gallons / Carry-over Details":case"Sale saved":operations.orders();break;
            case"Record Pickup":case"Reschedule Pickup":case"Record Delivery":if(Rules.uuid(entity))operations.order(entity);else operations.orders();break;
            case"Customer Profile":case"Add Customer":operations.customers();break;
            case"Edit Customer":if(Rules.uuid(entity))operations.customer(entity);else operations.customers();break;
            case"Record Payment":case"Payment history":case"Payments":if(isOwner())operations.payments();else ownerUi.settings();break;
            case"Add Expense":ownerUi.expenses();break;
            case"Report Preview":ownerUi.reports();break;
            case"Inventory":case"Staff accounts":case"My account":case"Change password":case"Edit profile":ownerUi.settings();break;
            case"Create Staff account":case"Edit Staff account":ownerUi.staffAccounts();break;
            default:showHome();
        }
    }
    public void showHome(){home.show();}
    public void endSession(String reason){dismissDialogs();state.expenseState.cancelPending();ownerUi.close();Backend old=state.backend;if(!old.accessToken.isEmpty()){old.call("/rest/v1/rpc/aq_logout",json(),r->{},e->{});old.call("/auth/v1/logout?scope=local",json(),r->{},e->{});}old.close();state=new Session();currentPage="Sign in";draftScope="Sign in";operations=new OperationsUi(this);ownerUi=new OwnerUi(this);home=new HomeUi(this);signIn();
        if(reason.startsWith("Session expired")||reason.startsWith("Access denied")){if(signInUser!=null)fieldError(signInUser,reason);}else info(reason);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(state.profile==null||!isOwner()||SystemClock.elapsedRealtime()-state.lastInteraction>=INACTIVITY){endSession("Session expired. Sign in again.");return;}ownerUi.activityResult(request,result,data);}
    public void openOrders(){operations.orders();}
    public void openPayments(){operations.payments();}
    public void openPending(){operations.pending();}
}
