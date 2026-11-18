package com.aquaquenchers.bms;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.graphics.drawable.*;
import android.graphics.drawable.shapes.RectShape;
import android.os.*;
import android.text.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * Native shared shell. The whole UI is laid out on a fixed Figma design canvas (Owner 1194 × 834 tablet frame,
 * Staff 440 wide phone frame) scaled to the screen, so every design unit from the Figma file maps 1:1.
 * Financial actions always require a confirmed backend response.
 */
public final class MainActivity extends Activity {
    private static final long INACTIVITY = 30 * 60 * 1000L;
    static final int OWNER_W = 1194, OWNER_H = 834, STAFF_W = 440, STAFF_H = 882;
    /** Tablet sign-in frame: smaller than the O1 frame so the sign-in fills the screen; parts are anchored to the real edges. */
    static final int SIGN_W = 1000, SIGN_H = 700;

    private Ui ui;
    private CanvasHost host;
    private LinearLayout canvas, body, titleActions, dock, bottomNav, sideNav, bannerHost;
    private ScrollView scroll;
    private FrameLayout viewport;
    private TextView appTitle, backAction, fab;
    private ProgressBar loading;
    private View currentBanner;
    private int bannerIndex = 0, outstanding = 0, screen = 0, loginGeneration = 0, designW, designH;
    private String currentPage = "Sign in", draftScope = "Sign in", shellRole = "";
    private Session state;
    private final Handler clock = new Handler(Looper.getMainLooper());
    private OperationsUi operations;
    private OwnerUi ownerUi;
    private HomeUi home;
    private final Map<String, View> navItems = new LinkedHashMap<>();
    private final Set<Dialog> dialogs = new HashSet<>();

    static final class Session {
        Backend backend = new Backend(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY);
        JSONObject profile, reportSnapshot; byte[] pendingPdf;
        com.aquaquenchers.classifier.ExpenseSuggestionController expenseState = new com.aquaquenchers.classifier.ExpenseSuggestionController();
        long lastInteraction = SystemClock.elapsedRealtime();
        String username = "", route = "", routeEntity = "", signInNotice = ""; boolean skipCapture;
        final Map<String, String> drafts = new HashMap<>(), keys = new HashMap<>();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!BuildConfig.DEBUG) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Object retained = getLastNonConfigurationInstance();
        state = retained instanceof Session ? (Session) retained : new Session();
        if (saved != null) {
            state.route = saved.getString("route", state.route); state.routeEntity = saved.getString("route_entity", state.routeEntity);
            ArrayList<String> keys = saved.getStringArrayList("draft_keys"), values = saved.getStringArrayList("draft_values");
            if (keys != null && values != null) for (int i = 0; i < Math.min(keys.size(), values.size()); i++) state.drafts.put(keys.get(i), values.get(i));
            ArrayList<String> hashes = saved.getStringArrayList("request_hashes"), ids = saved.getStringArrayList("request_ids");
            if (hashes != null && ids != null) for (int i = 0; i < Math.min(hashes.size(), ids.size()); i++) state.keys.put(hashes.get(i), ids.get(i));
        }
        host = new CanvasHost(this);
        setContentView(host);
        host.onResize = this::checkScale;
        ui = new Ui(this);
        operations = new OperationsUi(this); ownerUi = new OwnerUi(this); home = new HomeUi(this);
        if (state.profile == null) signIn(); else { shell(); restoreRoute(); }
        clock.postDelayed(idleCheck, 60000);
    }
    private final Runnable idleCheck = new Runnable() { public void run() { if (state.profile != null && SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) endSession("Session expired. Sign in again."); clock.postDelayed(this, 60000); } };
    @Override public void onUserInteraction() { super.onUserInteraction(); if (state != null) { if (state.profile != null && SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) { endSession("Session expired. Sign in again."); return; } state.lastInteraction = SystemClock.elapsedRealtime(); } }
    @Override protected void onResume() {
        super.onResume();
        applyDensity();
        if (state != null && state.profile != null) {
            if (SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) endSession("Session expired. Sign in again.");
            else query("me", json(), p -> { boolean changed = !state.profile.optString("role").equals(p.optString("role")); state.profile = p; if (changed) { shell(); showHome(); } });
        }
    }
    @Override protected void onDestroy() { clock.removeCallbacksAndMessages(null); dismissDialogs(); if (ownerUi != null) ownerUi.close(); if (state != null) { state.expenseState.cancelPending(); if (isFinishing()) state.backend.close(); } super.onDestroy(); }
    @Override public Object onRetainNonConfigurationInstance() { captureDrafts(); return state; }
    @Override protected void onSaveInstanceState(Bundle out) { captureDrafts(); out.putString("route", state.route); out.putString("route_entity", state.routeEntity); out.putStringArrayList("request_hashes", new ArrayList<>(state.keys.keySet())); out.putStringArrayList("request_ids", new ArrayList<>(state.keys.values())); out.putStringArrayList("draft_keys", new ArrayList<>(state.drafts.keySet())); out.putStringArrayList("draft_values", new ArrayList<>(state.drafts.values())); super.onSaveInstanceState(out); }
    @Override public void onBackPressed() { if (state.profile != null) goBack(); else super.onBackPressed(); }

    private void captureDrafts() { if (body == null || state == null || "Sign in".equals(currentPage)) return; if (state.skipCapture) { state.skipCapture = false; return; } capture(body); }
    private void capture(View view) {
        if (view instanceof EditText) { EditText e = (EditText) view; if (e.getTag() != null && !e.getTag().toString().toLowerCase(Locale.ROOT).contains("verification code") && (e.getInputType() & InputType.TYPE_TEXT_VARIATION_PASSWORD) == 0) state.drafts.put(draftScope + "/" + e.getTag(), e.getText().toString()); }
        if (view instanceof ViewGroup) { ViewGroup g = (ViewGroup) view; for (int i = 0; i < g.getChildCount(); i++) capture(g.getChildAt(i)); }
    }
    public com.aquaquenchers.classifier.ExpenseSuggestionController expenseState() { return state.expenseState; }
    public JSONObject reportSnapshot() { return state.reportSnapshot; }
    public void reportSnapshot(JSONObject report) { if (isOwner()) state.reportSnapshot = report; }
    public byte[] pendingPdf() { return state.pendingPdf; }
    public void pendingPdf(byte[] bytes) { if (isOwner()) state.pendingPdf = bytes; }
    public boolean isOwner() { return state.profile != null && "owner".equalsIgnoreCase(state.profile.optString("role")); }
    public String recorder() { return state.profile == null ? "" : state.profile.optString("full_name", state.username); }
    public JSONObject profile() { return state.profile; }
    public Backend backend() { return state.backend; }
    Ui ui() { return ui; }
    OperationsUi operations() { return operations; }
    OwnerUi ownerUi() { return ownerUi; }
    HomeUi home() { return home; }

    // =====================================================================================================
    // Design canvas
    // =====================================================================================================
    /** Hosts the design canvas at a fixed design width, centred, and applies system-bar and keyboard insets. */
    static final class CanvasHost extends FrameLayout {
        int topColor = Ui.WHITE, bottomColor = Ui.WHITE, sideColor = Ui.BG;
        int insetTop, insetBottom, barsBottom, barsLeft, barsRight;
        Runnable onResize;
        final Paint paint = new Paint();
        CanvasHost(Context c) {
            super(c);
            setWillNotDraw(false);
            setOnApplyWindowInsetsListener((v, insets) -> {
                int top, left, right, bottom, ime;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = bars.top; left = bars.left; right = bars.right; bottom = bars.bottom; ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                } else {
                    // Android 10 (API 29): stable insets are the system bars; system-window insets also include the keyboard.
                    top = insets.getStableInsetTop(); left = insets.getStableInsetLeft(); right = insets.getStableInsetRight(); bottom = insets.getStableInsetBottom();
                    ime = insets.getSystemWindowInsetBottom();
                }
                insetTop = top; insetBottom = Math.max(bottom, ime); barsBottom = bottom; barsLeft = left; barsRight = right;
                if (onResize != null) post(onResize);
                setPadding(left, top, right, insetBottom);
                return Build.VERSION.SDK_INT >= 30 ? WindowInsets.CONSUMED : insets.consumeSystemWindowInsets();
            });
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); if (onResize != null) post(onResize); }
        /** Usable size for the design scale: excludes system bars but not the keyboard. */
        int[] usable() { return new int[]{getWidth() - barsLeft - barsRight, getHeight() - insetTop - barsBottom}; }
        @Override protected void onDraw(Canvas c) {
            paint.setColor(sideColor); c.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setColor(topColor); c.drawRect(0, 0, getWidth(), insetTop, paint);
            paint.setColor(bottomColor); c.drawRect(0, getHeight() - insetBottom, getWidth(), getHeight(), paint);
        }
    }
    /** Available window size in px, excluding system bars. */
    private int[] available() {
        int w, h;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowMetrics m = getWindowManager().getCurrentWindowMetrics();
            android.graphics.Insets in = m.getWindowInsets().getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            w = m.getBounds().width() - in.left - in.right; h = m.getBounds().height() - in.top - in.bottom;
        } else {
            DisplayMetrics r = new DisplayMetrics(); getWindowManager().getDefaultDisplay().getRealMetrics(r);
            int status = dimen("status_bar_height"), nav = dimen("navigation_bar_height");
            w = r.widthPixels; h = r.heightPixels - status - nav;
        }
        return new int[]{w, h};
    }
    private int dimen(String name) { int id = getResources().getIdentifier(name, "dimen", "android"); return id > 0 ? getResources().getDimensionPixelSize(id) : 0; }
    private boolean tabletDevice() { return getResources().getConfiguration().smallestScreenWidthDp >= 600; }
    /** One design unit = this many px, so the design frame fits the window. */
    private void design(int w, int h) {
        designW = w; designH = h;
        int want = w > h ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : tabletDevice() ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        if (getRequestedOrientation() != want) setRequestedOrientation(want);
        applyDensity();
    }
    /** Rebuilds the current screen when the real window size gives a different design scale (rotation, multi-window). */
    private void checkScale() {
        if (designW == 0 || host.getWidth() == 0) return;
        int[] av = host.usable();
        if (av[0] <= 0 || av[1] <= 0) return;
        float s = Math.min(av[0] / (float) designW, av[1] / (float) designH);
        if (Math.abs(s - getResources().getDisplayMetrics().density) < 0.01f) return;
        captureDrafts();
        if (state.profile == null) signIn(); else { body = null; shellRole = ""; shell(); restoreRoute(); }
    }
    private void applyDensity() {
        if (designW == 0) return;
        int[] av = host != null && host.getWidth() > 0 ? host.usable() : available();
        if (av[0] <= 0 || av[1] <= 0) av = available();
        float s = Math.min(av[0] / (float) designW, av[1] / (float) designH);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        dm.density = s; dm.scaledDensity = s; dm.densityDpi = Math.round(s * 160);
    }
    private void mountCanvas(LinearLayout c) {
        host.removeAllViews();
        canvas = c;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(designW > designH ? -1 : ui.px(designW), -1, Gravity.CENTER_HORIZONTAL);
        host.addView(c, lp);
        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setIndeterminate(true); loading.setVisibility(outstanding > 0 ? View.VISIBLE : View.GONE);
        host.addView(loading, new FrameLayout.LayoutParams(designW > designH ? -1 : ui.px(designW), ui.px(4), Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        host.requestApplyInsets();
        host.invalidate();
    }
    /** Tablet layout (header + sidebar, Figma O frames) for Owner and Staff. Phones use the phone shell. Permissions still follow isOwner(). */
    boolean wide() { return tabletDevice(); }
    boolean tablet() { return wide(); }

    // =====================================================================================================
    // Shell (Owner tablet: header + sidebar. Staff phone: app bar + bottom navigation + dock)
    // =====================================================================================================
    private void shell() {
        shellRole = layoutKey();
        navItems.clear();
        if (wide()) { design(OWNER_W, OWNER_H); ownerShell(); } else { design(STAFF_W, STAFF_H); staffShell(); }
    }
    private void ownerShell() {
        LinearLayout c = ui.vbox(0); c.setBackgroundColor(Ui.BG);
        host.topColor = Ui.WHITE; host.bottomColor = Ui.BG; host.sideColor = Ui.BG;
        // Header 64: brand + account label
        LinearLayout header = ui.hbox(16); header.setBackgroundColor(Ui.WHITE); ui.pad(header, 24, 12, 24, 12);
        c.addView(header, new LinearLayout.LayoutParams(-1, ui.px(64)));
        ui.grow(header, ui.tv("AQUA QUENCHERS", 17, 26, Ui.PRIMARY, ui.p6));
        TextView account = ui.tv(isOwner() ? "OWNER ACCOUNT" : "STAFF ACCOUNT", 12, 16, Ui.INK, ui.o4); ui.hug(header, account);
        // Workspace: sidebar 194 + main content
        LinearLayout work = ui.hbox(0); work.setGravity(Gravity.TOP);
        c.addView(work, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout side = ui.vbox(12); side.setBackgroundColor(Ui.WHITE); ui.pad(side, 16, 24, 16, 0);
        work.addView(side, new LinearLayout.LayoutParams(ui.px(194), -1));
        sideNav = side;
        for (String route : isOwner() ? new String[]{"Dashboard", "Orders", "Customers", "Payments", "Expenses", "Reports", "Settings"} : new String[]{"Home", "Orders", "POS", "Customers", "Settings"}) {
            View item = sideItem(route);
            item.setOnClickListener(v -> navigate(route)); item.setContentDescription(route); navItems.put(route, item);
            side.addView(item, new LinearLayout.LayoutParams(ui.px(162), ui.px(48)));
        }
        FrameLayout main = new FrameLayout(this);
        work.addView(main, new LinearLayout.LayoutParams(0, -1, 1));
        scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        main.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        viewport = main;
        LinearLayout content = ui.vbox(14); ui.pad(content, 24, 20, 24, 20);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        body = content;
        dock = null; bottomNav = null; fab = null; appTitle = null; backAction = null;
        mountCanvas(c);
    }
    private View sideItem(String route) {
        FrameLayout item = new FrameLayout(this); item.setClickable(true);
        ImageView icon = ui.icon(sideIcon(route), Ui.NAV_MUTED);
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(ui.px(20), ui.px(20), Gravity.CENTER_VERTICAL); ip.leftMargin = ui.px(12); item.addView(icon, ip);
        TextView label = ui.tv("Home".equals(route) ? "Today" : "POS".equals(route) ? "Walk-in sale" : route, 13.5f, 20, Ui.INK, ui.p5);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL); lp.leftMargin = ui.px(44); item.addView(label, lp);
        View bar = new View(this); bar.setBackground(ui.shape(Ui.PRIMARY, 0, 2)); bar.setVisibility(View.GONE);
        item.addView(bar, new FrameLayout.LayoutParams(ui.px(3), ui.px(24), Gravity.CENTER_VERTICAL));
        item.setTag(new View[]{icon, label, bar});
        return item;
    }
    private int sideIcon(String route) {
        switch (route) { case "Dashboard": case "Home": return R.drawable.ic_dashboard; case "POS": return R.drawable.ic_tab_pos; case "Orders": return R.drawable.ic_orders; case "Customers": return R.drawable.ic_customers; case "Payments": return R.drawable.ic_payments; case "Expenses": return R.drawable.ic_expenses; case "Reports": return R.drawable.ic_reports; default: return R.drawable.ic_settings; }
    }
    private void staffShell() {
        LinearLayout c = ui.vbox(0); c.setBackgroundColor(Ui.BG);
        host.topColor = Ui.WHITE; host.bottomColor = Ui.WHITE; host.sideColor = Ui.BG;
        // App bar 64
        FrameLayout bar = new FrameLayout(this); bar.setBackgroundColor(Ui.WHITE);
        c.addView(bar, new LinearLayout.LayoutParams(-1, ui.px(64)));
        LinearLayout row = ui.hbox(12); ui.pad(row, 20, 0, 20, 0);
        bar.addView(row, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout back = new FrameLayout(this); back.setClickable(true); back.setContentDescription("Back");
        ImageView arrow = ui.icon(R.drawable.ic_back, 0xFF0284C7);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(ui.px(18), ui.px(18), Gravity.CENTER_VERTICAL); ap.leftMargin = ui.px(4); back.addView(arrow, ap);
        TextView backLabel = ui.tv("Back", 14, 22, 0xFF141414, ui.p5);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL); bp.leftMargin = ui.px(24); back.addView(backLabel, bp);
        back.setOnClickListener(v -> goBack());
        row.addView(back, new LinearLayout.LayoutParams(ui.px(64), ui.px(48)));
        backAction = backLabel;
        appTitle = ui.single(ui.tv("", 24, 35, Ui.INK, ui.p6));
        ui.grow(row, appTitle);
        TextView account = ui.tv(isOwner() ? "OWNER ACCOUNT" : "STAFF ACCOUNT", 10, 16, Ui.INK, ui.o4); account.setGravity(Gravity.END);
        FrameLayout.LayoutParams acp = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP); acp.topMargin = ui.px(1); acp.rightMargin = ui.px(20);
        bar.addView(account, acp);
        // Viewport: scroll body + floating action
        FrameLayout vp = new FrameLayout(this); vp.setBackgroundColor(Ui.BG);
        c.addView(vp, new LinearLayout.LayoutParams(-1, 0, 1));
        viewport = vp;
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        vp.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = ui.vbox(16); ui.pad(content, 20, 20);
        scroll.addView(content, new FrameLayout.LayoutParams(-1, -2));
        body = content;
        fab = ui.button("", Ui.FILLED, 56, 28, 16, null); fab.setVisibility(View.GONE);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-2, ui.px(56), Gravity.END | Gravity.BOTTOM); fp.rightMargin = ui.px(20); fp.bottomMargin = ui.px(16);
        fab.setElevation(ui.px(4));
        vp.addView(fab, fp);
        // Dock (single main action) above the navigation
        dock = ui.vbox(8); dock.setBackground(topStroke(Ui.WHITE, Ui.BORDER)); ui.pad(dock, 16, 16); dock.setVisibility(View.GONE);
        c.addView(dock, new LinearLayout.LayoutParams(-1, -2));
        // Bottom navigation 80
        LinearLayout nav = ui.hbox(4); nav.setBackground(topStroke(Ui.WHITE, 0xFFCCCCCC)); ui.pad(nav, 15, 8, 15, 8);
        c.addView(nav, new LinearLayout.LayoutParams(-1, ui.px(80)));
        bottomNav = nav;
        for (String route : isOwner() ? new String[]{"Home", "Orders", "Customers", "Payments", "More"} : new String[]{"Home", "Orders", "POS", "Customers", "Settings"}) {
            View item = bottomItem(route);
            item.setOnClickListener(v -> navigate(route)); item.setContentDescription(route); navItems.put(route, item);
            nav.addView(item, new LinearLayout.LayoutParams(ui.px(80), ui.px(64)));
        }
        sideNav = null; titleActions = null;
        mountCanvas(c);
    }
    private Drawable topStroke(int fill, int stroke) {
        GradientDrawable line = new GradientDrawable(); line.setColor(stroke);
        GradientDrawable base = new GradientDrawable(); base.setColor(fill);
        LayerDrawable d = new LayerDrawable(new Drawable[]{line, base});
        d.setLayerInset(1, 0, Math.max(1, ui.px(1)), 0, 0);
        return d;
    }
    private View bottomItem(String route) {
        LinearLayout item = ui.vbox(4); item.setGravity(Gravity.CENTER_HORIZONTAL); ui.pad(item, 6, 6); item.setClickable(true);
        ImageView icon = ui.icon(tabIcon(route), Ui.NAV_MUTED);
        item.addView(icon, new LinearLayout.LayoutParams(ui.px(24), ui.px(24)));
        TextView label = ui.tv(route, 10, 18, Ui.NAV_MUTED, ui.p5); label.setGravity(Gravity.CENTER); label.setSingleLine(true);
        item.addView(label, new LinearLayout.LayoutParams(-1, -2));
        item.setTag(new View[]{icon, label, null});
        return item;
    }
    private int tabIcon(String route) {
        switch (route) { case "Home": return R.drawable.ic_tab_home; case "Orders": return R.drawable.ic_tab_orders; case "POS": return R.drawable.ic_tab_pos; case "Customers": return R.drawable.ic_tab_customers; case "Payments": return R.drawable.ic_payments; default: return R.drawable.ic_tab_settings; }
    }
    private void highlight(String route) {
        boolean side = sideNav != null;
        if (!side && isOwner()) route = "Dashboard".equals(route) ? "Home" : ("Expenses".equals(route) || "Reports".equals(route) || "Settings".equals(route)) ? "More" : route;
        for (Map.Entry<String, View> e : navItems.entrySet()) {
            boolean on = e.getKey().equals(route); View item = e.getValue(); View[] parts = (View[]) item.getTag();
            if (side) { item.setBackground(ui.shape(on ? Ui.NAV_TONAL : Ui.WHITE, 0, 12)); parts[2].setVisibility(on ? View.VISIBLE : View.GONE); ((TextView) parts[1]).setTextColor(on ? Ui.PRIMARY : Ui.INK); }
            else { item.setBackground(on ? ui.shape(Ui.NAV_TONAL, Ui.NAV_TONAL_STROKE, 12) : null); ((TextView) parts[1]).setTextColor(on ? Ui.PRIMARY : Ui.NAV_MUTED); }
            ((ImageView) parts[0]).setColorFilter(on ? (side ? Ui.PRIMARY : 0xFF0284C7) : Ui.NAV_MUTED);
        }
    }

    // =====================================================================================================
    // Pages
    // =====================================================================================================
    private static final Set<String> ROOTS = new HashSet<>(Arrays.asList("Sign in", "Home", "Dashboard", "Orders", "Customers", "Walk-in Sale / POS", "Settings", "Payments"));
    static String displayTitle(String title) {
        switch (title) {
            case "Home": return "Today";
            case "Walk-in Sale / POS": return "Walk-in sale";
            case "Create Order": return "New order";
            case "Order Details": return "Order";
            case "Customer Profile": return "Customer";
            case "Add Customer": return "Add customer";
            case "Edit Customer": return "Edit customer";
            case "Record Pickup": return "Record pickup";
            case "Reschedule Pickup": return "Reschedule";
            case "Record Delivery": return "Record delivery";
            case "Record Payment": case "Payment recorded": return "Collect payment";
            case "Sale saved": return "Walk-in sale";
            case "Pending gallons / Carry-over Details": return "Pending gallons";
            case "Financial summary": case "Report Preview": return "Reports";
            case "My account": case "Edit profile": return "Edit profile";
            case "Change password": return "Change password";
            case "Inventory": return "Inventory";
            case "Staff accounts": case "Create Staff account": case "Edit Staff account": return "Staff accounts";
            case "Expenses": return "Expenses";
            case "Payment history": return "Payment history";
            default: return title;
        }
    }
    private String layoutKey() { return wide() ? (isOwner() ? "owner" : "staff-wide") : isOwner() ? "owner-phone" : "staff"; }
    private String navRouteFor(String page) {
        switch (page) {
            case "Home": case "Dashboard": return isOwner() ? "Dashboard" : "Home";
            case "Orders": case "Order Details": case "Record Pickup": case "Reschedule Pickup": case "Record Delivery": case "Create Order": case "Pending gallons / Carry-over Details": return "Orders";
            case "Customers": case "Customer Profile": case "Add Customer": case "Edit Customer": return "Customers";
            case "Walk-in Sale / POS": case "Sale saved": return isOwner() ? "Orders" : "POS";
            case "Payments": case "Record Payment": case "Payment history": case "Payment recorded": return isOwner() ? "Payments" : "Orders";
            case "Expenses": case "Add Expense": return "Expenses";
            case "Financial summary": case "Report Preview": return "Reports";
            default: return "Settings";
        }
    }
    public LinearLayout page(String title) { return page(title, ""); }
    public LinearLayout page(String title, String entity) {
        captureDrafts(); currentPage = title;
        draftScope = (state.profile == null ? "" : state.profile.optString("user_id") + "/") + title + "/" + entity;
        if (!"Sign in".equals(title)) { state.route = title; state.routeEntity = entity; }
        screen++;
        dismissDialogs();
        if (!layoutKey().equals(shellRole) || body == null) shell();
        applyDensity();
        body.removeAllViews(); currentBanner = null; bannerHost = body; bannerIndex = 0;
        scroll.scrollTo(0, 0);
        if (dock != null) { dock.removeAllViews(); dock.setVisibility(View.GONE); }
        if (fab != null) fab.setVisibility(View.GONE);
        highlight(navRouteFor(title));
        if (wide()) {
            LinearLayout head = ui.H(body, 10);
            head.setMinimumHeight(ui.px(39));
            String nav = navRouteFor(title), ownerTitle = isOwner() ? nav : "POS".equals(nav) ? "Walk-in sale" : displayTitle(nav);
            TextView heading = ui.tv(ownerTitle, 26, 39, Ui.INK, ui.p6);
            ui.grow(head, heading);
            titleActions = ui.hbox(10); ui.hug(head, titleActions);
            heading.setContentDescription(ownerTitle);
            bannerIndex = 1;
        } else {
            boolean root = ROOTS.contains(title);
            ((View) backAction.getParent()).setVisibility(root ? View.GONE : View.VISIBLE);
            appTitle.setText(displayTitle(title));
            if (root) ui.size(appTitle, 24, 35); else ui.size(appTitle, 22, 32);
            appTitle.setContentDescription(displayTitle(title));
        }
        return body;
    }
    /** Owner title row actions (e.g. "Walk-in sale", "+ New order", "Outstanding" chip). */
    LinearLayout titleActions() { return titleActions; }
    /** Two side-by-side panes (Owner): fixed-width left column, flexible right column, 16 apart. */
    LinearLayout[] panes(float leftWidth) {
        if (!wide()) { LinearLayout left = ui.V(body, 16), right = ui.V(body, 16); return new LinearLayout[]{left, right}; }
        LinearLayout row = ui.H(body, 16); row.setGravity(Gravity.TOP);
        LinearLayout left = ui.vbox(14), right = ui.vbox(14);
        row.addView(left, new LinearLayout.LayoutParams(ui.px(leftWidth), -2));
        row.addView(right, new LinearLayout.LayoutParams(0, -2, 1));
        return new LinearLayout[]{left, right};
    }
    /** Where page banners (saved / error messages) are shown. */
    void bannerAt(LinearLayout container, int index) { bannerHost = container; bannerIndex = index; }
    LinearLayout currentContent() { return body; }
    /** Staff dock with the screen's single main action. */
    public TextView mainAction(String label, int style, Runnable action) {
        if (dock == null) { if (body == null) return null; return ownerAction(actionRow(body), label, style, action); }
        dock.removeAllViews(); dock.setVisibility(View.VISIBLE);
        TextView b = ui.dockButton(label, style, () -> guarded(action));
        ui.fill(dock, b);
        return b;
    }
    public void actionNote(String note) { if (dock == null) { if (body != null) { TextView t = ui.tv(note, 13, 18, Ui.MUTED, ui.o4); t.setGravity(Gravity.END); ui.fill(body, t); } return; } TextView t = ui.tv(note, 13, 18, Ui.MUTED, ui.o4); t.setGravity(Gravity.CENTER); ui.fill(dock, t); }
    /** Staff dock emptied for custom content (e.g. POS totals above the button). */
    public LinearLayout dockBox() { if (dock == null) return null; dock.removeAllViews(); dock.setVisibility(View.VISIBLE); return dock; }
    public void floatingAction(String label, Runnable action) {
        if (fab == null) return;
        fab.setText(label); fab.setVisibility(View.VISIBLE); fab.setContentDescription(label.replace("+", "").trim());
        fab.setOnClickListener(v -> guarded(action));
        ui.pad(fab, 20, 0);
    }
    /** Owner right-aligned action row (Cancel / Save) inside a pane. */
    LinearLayout actionRow(LinearLayout parent) { LinearLayout r = ui.H(parent, 10); r.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); return r; }
    TextView ownerAction(LinearLayout row, String label, int style, Runnable action) { return ui.hug(row, ui.ownerButton(label, style, () -> guarded(action))); }
    void guarded(Runnable action) {
        try {
            if (state.profile != null && SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) { endSession("Session expired. Sign in again."); return; }
            action.run();
        } catch (IllegalArgumentException e) { error(e.getMessage()); }
        catch (Exception e) { error("Check the entered details and try again."); }
    }

    // ---------- fields ----------
    /** Labelled text field (label 13/18 Open Sans 600, 6 gap, input, inline error). */
    public EditText input(LinearLayout parent, String label, String initial, int inputType) {
        LinearLayout f = ui.V(parent, 6);
        ui.label(f, label);
        EditText e = field(f, label, initial, inputType);
        return e;
    }
    /** An input without a visible label (search boxes; the placeholder carries the meaning). */
    public EditText inputPlain(LinearLayout parent, String label, String initial, int inputType) {
        LinearLayout f = ui.V(parent, 6);
        return field(f, label, initial, inputType);
    }
    private EditText field(LinearLayout f, String label, String initial, int inputType) {
        EditText e = ui.input(wide(), label, inputType);
        e.setTag(label); e.setContentDescription(label);
        e.setText(state.drafts.getOrDefault(draftScope + "/" + label, initial));
        if ((inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) { e.setSingleLine(false); e.setGravity(Gravity.TOP | Gravity.START); ui.pad(e, wide() ? 14 : 16, 12); e.setMinLines(1); e.setMinHeight(ui.px(wide() ? 46 : 52)); }
        ui.fill(f, e);
        TextView err = ui.fieldError(f); e.setTag(R.id.field_error, err);
        e.addTextChangedListener(watcher(() -> clearFieldError(e)));
        return e;
    }
    static TextWatcher watcher(Runnable changed) {
        return new TextWatcher() { public void beforeTextChanged(CharSequence s, int a, int b, int c) {} public void onTextChanged(CharSequence s, int a, int b, int c) { changed.run(); } public void afterTextChanged(Editable s) {} };
    }
    /** Quantity control: stepper, typed value and quick picks share one EditText. */
    public EditText quantity(LinearLayout parent, String label, String initial, int[] picks, String[] names, String unit, boolean centered, float pickGap, TextView[] unitOut) {
        EditText e = new EditText(this); e.setTag(label); e.setContentDescription(label);
        e.setText(state.drafts.getOrDefault(draftScope + "/" + label, initial));
        TextView u = ui.stepper(parent, e, unit, wide(), centered);
        if (unitOut != null) unitOut[0] = u;
        Ui.Tabs[] chips = new Ui.Tabs[1];
        chips[0] = ui.tabs(parent, names, -1, true, wide(), pickGap, i -> { if (picks[i] >= 0) e.setText(Integer.toString(picks[i])); });
        Runnable sync = () -> { String v = e.getText().toString().trim(); int found = -1; for (int i = 0; i < picks.length; i++) if (Integer.toString(picks[i]).equals(v)) found = i; chips[0].select(found, false); };
        e.addTextChangedListener(watcher(() -> { clearFieldError(e); sync.run(); }));
        TextView err = ui.fieldError(parent); e.setTag(R.id.field_error, err);
        e.setTag(R.id.button_style, chips[0]);
        sync.run();
        return e;
    }
    public void fieldError(EditText field, String text) {
        TextView err = field.getTag(R.id.field_error) instanceof TextView ? (TextView) field.getTag(R.id.field_error) : null;
        if (field.getBackground() != null) ui.flagInvalid(field, true); else field.setTextColor(Ui.DANGER);
        if (err != null) { err.setText(text); err.setVisibility(View.VISIBLE); } else error(text);
    }
    public void clearFieldError(EditText field) {
        if (field.getBackground() != null) ui.flagInvalid(field, false); else field.setTextColor(Ui.INK);
        if (field.getTag(R.id.field_error) instanceof TextView) { TextView err = (TextView) field.getTag(R.id.field_error); err.setText(""); err.setVisibility(View.GONE); }
    }
    public String choice(String key) { return state.drafts.getOrDefault(draftScope + "/choice/" + key, ""); }
    public void rememberChoice(String key, String id) { state.drafts.put(draftScope + "/choice/" + key, id == null ? "" : id); }
    public int dp(int value) { return ui.px(value); }
    /** Figma modal: 45% black dim, white card radius 20, padding 28, 14 spacing, fixed design width. */
    LinearLayout modal(float width, Dialog[] out) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = ui.vbox(14); card.setBackground(ui.shape(Ui.WHITE, 0, 20)); ui.pad(card, 28, 28);
        ScrollView sv = new ScrollView(this); sv.addView(card, new FrameLayout.LayoutParams(-1, -2));
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ui.px(Math.min(width, designW - 24)), WindowManager.LayoutParams.WRAP_CONTENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); w.setDimAmount(0.45f);
        }
        if (out != null) out[0] = d;
        return card;
    }
    /** Staff bottom sheet (Figma S09): white, top radius 22, grabber, padding 18. */
    LinearLayout sheet(Dialog[] out) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = ui.vbox(14);
        GradientDrawable g = new GradientDrawable(); g.setColor(Ui.WHITE); float r = ui.px(22); g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(g); ui.pad(root, 18, 18);
        d.setContentView(root);
        Window w = d.getWindow();
        if (w != null) { w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); w.setGravity(Gravity.BOTTOM); w.setLayout(ui.px(designW), WindowManager.LayoutParams.WRAP_CONTENT); w.setDimAmount(0.45f); }
        if (out != null) out[0] = d;
        return root;
    }
    public void showDialog(Dialog dialog) { dialogs.add(dialog); dialog.setOnDismissListener(d -> dialogs.remove(dialog)); if (!BuildConfig.DEBUG && dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); dialog.show(); }
    private void dismissDialogs() { for (Dialog d : new ArrayList<>(dialogs)) if (d.isShowing()) d.dismiss(); dialogs.clear(); }
    private boolean canWrite() { if (isDestroyed()) return false; if (state.profile == null) { error("Sign in to continue."); return false; } if (SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) { endSession("Session expired. Sign in again."); return false; } return true; }
    public void runValidated(Runnable action) { try { action.run(); } catch (IllegalArgumentException e) { error(e.getMessage()); } }

    // ---------- banners ----------
    public void clearMessage() { if (currentBanner != null && currentBanner.getParent() instanceof ViewGroup) ((ViewGroup) currentBanner.getParent()).removeView(currentBanner); currentBanner = null; }
    public void banner(int kind, String title, String detail) {
        if (bannerHost == null) return;
        clearMessage();
        LinearLayout b = ui.banner(null, kind, title, detail, wide());
        int index = Math.min(bannerIndex, bannerHost.getChildCount());
        bannerHost.addView(b, index, new LinearLayout.LayoutParams(-1, -2));
        currentBanner = b;
        if (bannerHost == body) scroll.post(() -> scroll.smoothScrollTo(0, 0));
    }
    public void error(String text) { if (text == null || text.isEmpty()) text = "Check the entered details and try again."; banner(Ui.ERROR, text, errorDetail(text)); }
    private static String errorDetail(String text) {
        switch (text) {
            case "Not enough empty gallons.": return "Status and stock were not changed.";
            case "Follow the next eligible order stage.": return "The saved status was not changed.";
            case "Not enough reserved gallons for this order.": return "Nothing was saved.";
            case "Not enough filled gallons.": return "Nothing was saved and stock is unchanged.";
            default: return null;
        }
    }
    public void error(String title, String detail) { banner(Ui.ERROR, title, detail); }
    public void info(String text) { banner(Ui.SUCCESS, text, null); }
    public void info(String title, String detail) { banner(Ui.SUCCESS, title, detail); }

    // ---------- formatting and requests ----------
    public String today() { return LocalDate.now(Rules.MANILA).toString(); }
    public String timestamp(String encoded) { return Rules.timestamp(encoded); }
    public String money(long value) { return Rules.money(value); }
    public JSONObject json(Object... pairs) { JSONObject result = new JSONObject(); try { for (int i = 0; i < pairs.length; i += 2) result.put(String.valueOf(pairs[i]), pairs[i + 1]); return result; } catch (JSONException e) { throw new IllegalArgumentException("Invalid request."); } }
    private String requestHash(String payload) { try { String scoped = (state.profile == null ? "" : state.profile.optString("user_id")) + "/" + payload; byte[] bytes = java.security.MessageDigest.getInstance("SHA-256").digest(scoped.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder hash = new StringBuilder(); for (byte b : bytes) hash.append(String.format(Locale.ROOT, "%02x", b & 255)); return hash.toString(); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
    private void busy(boolean yes) { outstanding = Math.max(0, outstanding + (yes ? 1 : -1)); if (loading != null) loading.setVisibility(outstanding > 0 ? View.VISIBLE : View.GONE); }
    public void query(String resource, JSONObject fields, Consumer<JSONObject> success) {
        JSONObject request;
        try { request = new JSONObject(fields.toString()); request.put("resource", resource); } catch (JSONException e) { error("Invalid query."); return; }
        int origin = screen;
        rpc("aq_query", request, result -> {
            if (origin != screen) return;
            try {
                boolean validRole = "owner".equals(result.optString("role")) || "staff".equals(result.optString("role"));
                if ("me".equals(resource) && (!Rules.uuid(result.optString("user_id")) || !result.optBoolean("active") || !validRole)) { error("The service did not confirm an active account. Sign in again."); return; }
                success.accept(result);
            } catch (IllegalArgumentException e) { error(e.getMessage()); }
            catch (Exception e) { error("The response could not be displayed. Refresh and try again."); }
        }, false);
    }
    public void command(String operation, JSONObject fields, Consumer<JSONObject> success) { if (!canWrite()) return; try { fields.remove("request_id"); fields.put("operation", operation); String fingerprint = requestHash(fields.toString()); String key = state.keys.computeIfAbsent(fingerprint, k -> UUID.randomUUID().toString()); fields.put("request_id", key); rpc("aq_command", fields, result -> { String idField = "update_profile".equals(operation) ? "user_id" : "id"; if (!Rules.uuid(result.optString(idField)) || (!"update_profile".equals(operation) && !operation.endsWith("customer") && result.optString("reference").isEmpty())) { error("The service did not confirm this save. Keep the inputs and retry safely."); return; } state.keys.remove(fingerprint); if ("update_profile".equals(operation)) state.profile = result; if ("create_expense".equals(operation)) state.expenseState = new com.aquaquenchers.classifier.ExpenseSuggestionController(); state.drafts.keySet().removeIf(k -> k.startsWith(draftScope + "/")); state.skipCapture = true; success.accept(result); }, true); } catch (JSONException e) { error("Invalid command."); } }
    public void accountCommand(JSONObject fields, Consumer<JSONObject> success) { if (!canWrite() || !isOwner()) return; Session sent = state; try { fields.remove("request_id"); JSONObject publicFields = new JSONObject(fields.toString()); publicFields.remove("temporary_password"); String fingerprint = requestHash("staff-admin/" + publicFields.toString()); fields.put("request_id", state.keys.computeIfAbsent(fingerprint, k -> UUID.randomUUID().toString())); busy(true); sent.backend.call("/functions/v1/staff-admin", fields, r -> { busy(false); if (sent != state) return; if (!Rules.uuid(r.optString("user_id")) || !"staff".equals(r.optString("role"))) { error("The service did not confirm this account change. Retry with the same details."); return; } state.keys.remove(fingerprint); state.drafts.keySet().removeIf(k -> k.startsWith(draftScope + "/")); state.skipCapture = true; success.accept(r); }, e -> { busy(false); if (sent == state) { if (e.status == 401 || (e.status == 403 && !"Enter a valid verification code.".equals(e.getMessage()))) { endSession("Session expired. Sign in again."); return; } if (e.status == 400) state.keys.remove(fingerprint); error(e.getMessage()); } }); } catch (JSONException e) { error("Invalid account details."); } }
    private void rpc(String function, JSONObject request, Consumer<JSONObject> success, boolean mutation) { rpcAttempt(function, request, success, mutation, false); }
    private void rpcAttempt(String function, JSONObject request, Consumer<JSONObject> success, boolean mutation, boolean refreshed) { Session sent = state; busy(true); state.backend.call("/rest/v1/rpc/" + function, json("p_request", request), r -> { busy(false); if (sent == state) success.accept(r); }, e -> { busy(false); if (sent != state) return; if (e.status == 401) { if (refreshed) { endSession("Session expired. Sign in again."); return; } refreshThen(() -> rpcAttempt(function, request, success, mutation, true)); } else if (e.status == 403 && "Session expired. Sign in again.".equals(e.getMessage())) { endSession(e.getMessage()); } else if (e.status == 403) { revalidateDeniedSession(sent); } else error(e.getMessage()); }); }
    private void revalidateDeniedSession(Session sent) {
        if (sent.profile == null) { endSession("Access denied. Sign in again."); return; }
        busy(true);
        sent.backend.call("/rest/v1/rpc/aq_me", json(), profile -> {
            busy(false); if (sent != state || isDestroyed()) return;
            String role = profile.optString("role");
            if (!profile.optBoolean("active") || !sent.profile.optString("user_id").equals(profile.optString("user_id")) || !("owner".equals(role) || "staff".equals(role))) { endSession("Access denied. Sign in again."); return; }
            boolean roleChanged = !role.equals(sent.profile.optString("role"));
            state.profile = profile; if (roleChanged) { shell(); showHome(); }
            accessDenied();
        }, failure -> {
            busy(false); if (sent != state || isDestroyed()) return;
            if (failure.status == 401 || failure.status == 403) endSession(failure.status == 401 || "Session expired. Sign in again.".equals(failure.getMessage()) ? "Session expired. Sign in again." : "Access denied. Sign in again.");
            else error("Access denied. Check your connection and try again.");
        });
    }
    /** Figma T4: Staff reaching an Owner-only area. */
    void accessDenied() {
        if (isOwner()) { error("Access denied."); return; }
        page("Settings");
        error("Access denied.", "Owner-only settings, expenses and financial reports are not available to Staff.");
        mainAction("Back to Today", Ui.FILLED, this::showHome);
    }
    private void refreshThen(Runnable retry) { Session pending = state; if (state.backend.refreshToken.isEmpty() || SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) { endSession("Session expired. Sign in again."); return; } state.backend.call("/auth/v1/token?grant_type=refresh_token", json("refresh_token", state.backend.refreshToken), r -> { if (pending != state || isDestroyed()) return; acceptTokens(r); retry.run(); }, e -> { if (pending == state && !isDestroyed()) endSession("Session expired. Sign in again."); }); }
    public void acceptTokens(JSONObject response) { JSONObject s = response.optJSONObject("session"); if (s == null) s = response; state.backend.accessToken = s.optString("access_token"); if (s.has("refresh_token")) state.backend.refreshToken = s.optString("refresh_token"); }

    // =====================================================================================================
    // Sign in (Figma O1/O1b–d tablet, S01/S02/S01c–d phone)
    // =====================================================================================================
    private EditText signUser, signPass;
    private LinearLayout signCard;
    private void signIn() {
        navItems.clear(); body = null; shellRole = ""; sideNav = null; bottomNav = null; dock = null; fab = null; titleActions = null;
        currentPage = "Sign in"; draftScope = "Sign in"; screen++;
        boolean owner = tabletDevice();
        if (owner) design(SIGN_W, SIGN_H); else design(STAFF_W, STAFF_H);
        LinearLayout c = ui.vbox(0);
        ScrollView sv = new ScrollView(this); sv.setFillViewport(true);
        c.addView(sv, new LinearLayout.LayoutParams(-1, -1));
        FrameLayout frame = new FrameLayout(this);
        frame.setMinimumHeight(owner ? Math.max(ui.px(SIGN_H), host.usable()[1] > 0 ? host.usable()[1] : available()[1]) : ui.px(STAFF_H));
        sv.addView(frame, new FrameLayout.LayoutParams(-1, -2));
        if (owner) ownerSignIn(frame); else staffSignIn(frame);
        mountCanvas(c);
        if (!state.signInNotice.isEmpty()) { String n = state.signInNotice; state.signInNotice = ""; signError(n, true, false); }
    }
    private View ellipse(FrameLayout p, float x, float y, float w, float h, int fill, int stroke) {
        View v = new View(this);
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(fill); if (stroke != 0) d.setStroke(Math.max(1, ui.px(1)), stroke);
        v.setBackground(d);
        place(p, v, x, y, w, h);
        return v;
    }
    private <T extends View> T place(FrameLayout p, T v, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : ui.px(w), h < 0 ? (int) h : ui.px(h));
        p.addView(v, lp); v.setTranslationX(ui.px(x)); v.setTranslationY(ui.px(y));
        return v;
    }
    private Drawable linear(float x0, float y0, float x1, float y1, int[] colors, float[] stops) {
        ShapeDrawable d = new ShapeDrawable(new RectShape());
        d.setShaderFactory(new ShapeDrawable.ShaderFactory() {
            @Override public Shader resize(int w, int h) { return new LinearGradient(x0 * w, y0 * h, x1 * w, y1 * h, colors, stops, Shader.TileMode.CLAMP); }
        });
        return d;
    }
    private static int alpha(int color, float a) { return (Math.round(a * 255) << 24) | (color & 0xFFFFFF); }
    private FrameLayout tile(float size, float radius, float iconSize, int fill, int stroke) {
        FrameLayout t = new FrameLayout(this); t.setBackground(ui.shape(fill, stroke, radius));
        ImageView drop = ui.icon(R.drawable.ic_drop, Ui.WHITE);
        t.addView(drop, new FrameLayout.LayoutParams(ui.px(iconSize), ui.px(iconSize), Gravity.CENTER));
        return t;
    }
    /** Places v inside p anchored by gravity; x/y are offsets in design units away from the anchored edges. */
    private <T extends View> T anchor(FrameLayout p, T v, int gravity, float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w < 0 ? (int) w : ui.px(w), h < 0 ? (int) h : ui.px(h), gravity);
        p.addView(v, lp);
        int hg = gravity & Gravity.HORIZONTAL_GRAVITY_MASK, vg = gravity & Gravity.VERTICAL_GRAVITY_MASK;
        v.setTranslationX(hg == Gravity.RIGHT || hg == Gravity.END ? -ui.px(x) : ui.px(x));
        v.setTranslationY(vg == Gravity.BOTTOM ? -ui.px(y) : ui.px(y));
        return v;
    }
    private View blob(int fill, int stroke) {
        View v = new View(this);
        GradientDrawable d = new GradientDrawable(); d.setShape(GradientDrawable.OVAL); d.setColor(fill); if (stroke != 0) d.setStroke(Math.max(1, ui.px(1)), stroke);
        v.setBackground(d);
        return v;
    }
    /** Tablet sign-in (Figma O1): brand panel on the left, sign-in card centred on the right; stretches to the screen. */
    private void ownerSignIn(FrameLayout f) {
        host.topColor = Ui.BG; host.bottomColor = Ui.BG; host.sideColor = Ui.BG;
        f.setBackgroundColor(Ui.BG);
        int[] av = host.usable()[0] > 0 ? host.usable() : available();
        float unitsW = av[0] / getResources().getDisplayMetrics().density;
        float panelW = Math.max(440, Math.round(unitsW * 535f / 1194f));
        float areaW = unitsW - panelW;
        // Brand panel with the Figma gradient and ambient shapes
        FrameLayout panel = new FrameLayout(this); panel.setClipChildren(true);
        panel.setBackground(linear(-0.07f, 0.5f, 1.01f, 0.8f, new int[]{0xFF0369A1, 0xFF075985, 0xFF082F49}, new float[]{0, 0.62f, 1}));
        f.addView(panel, new FrameLayout.LayoutParams(ui.px(panelW), -1));
        anchor(panel, blob(alpha(0x38BDF8, 0.16f), 0), Gravity.TOP | Gravity.LEFT, panelW - 270, -180, 410, 410);
        anchor(panel, blob(alpha(0x7DD3FC, 0.13f), 0), Gravity.BOTTOM | Gravity.LEFT, -200, -226, 500, 500);
        anchor(panel, blob(alpha(0xFFFFFF, 0.04f), alpha(0xBAE6FD, 0.28f)), Gravity.CENTER_VERTICAL | Gravity.LEFT, panelW - 265, -42, 330, 330);
        anchor(panel, blob(alpha(0xFFFFFF, 0.04f), alpha(0xBAE6FD, 0.20f)), Gravity.CENTER_VERTICAL | Gravity.LEFT, panelW - 210, -42, 220, 220);
        place(panel, tile(48, 14, 28, alpha(0xFFFFFF, 0.16f), alpha(0xFFFFFF, 0.27f)), 48, 48, 48, 48);
        place(panel, ui.tv("AQUA QUENCHER", 19, 28, Ui.WHITE, ui.p6), 112, 51, -2, -2);
        place(panel, ui.tv("WATER REFILLING STATION", 10, 15, 0xFFBAE6FD, ui.p5), 112, 77, -2, -2);
        anchor(panel, ui.tv("Every refill.\nEvery detail.\nIn one place.", 43, 64, Ui.WHITE, ui.p6), Gravity.CENTER_VERTICAL | Gravity.LEFT, 46, -40, panelW - 80, -2);
        FrameLayout highlight = new FrameLayout(this); highlight.setBackground(ui.shape(alpha(0xFFFFFF, 0.13f), alpha(0xFFFFFF, 0.18f), 18));
        anchor(panel, highlight, Gravity.BOTTOM | Gravity.LEFT, 48, 92, panelW - 96, 98);
        place(highlight, tile(50, 14, 28, alpha(0xFFFFFF, 0.17f), 0), 18, 22, 50, 50);
        place(highlight, ui.tv("One connected workspace", 16, 24, Ui.WHITE, ui.p6), 83, 23, -2, -2);
        place(highlight, ui.tv("Customers  ·  Orders  ·  Payments", 13, 18, 0xFFD6F2FF, ui.o4), 83, 52, -2, -2);
        anchor(panel, ui.tv("AQUA QUENCHER  •  BMS", 11, 16, 0xFFBAE6FD, ui.p5), Gravity.BOTTOM | Gravity.LEFT, 49, 36, -2, -2);
        // Sign-in area
        FrameLayout area = new FrameLayout(this); area.setClipChildren(true); area.setBackgroundColor(Ui.BG);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-1, -1); rlp.leftMargin = ui.px(panelW); f.addView(area, rlp);
        anchor(area, blob(alpha(0xE0F2FE, 0.65f), 0), Gravity.TOP | Gravity.RIGHT, -66, -92, 210, 210);
        TextView portal = ui.tv("STATION PORTAL", 11, 16, Ui.PRIMARY, ui.p6); portal.setGravity(Gravity.CENTER_VERTICAL); ui.pad(portal, 18, 0); portal.setBackground(ui.shape(0xFFE0F2FE, 0, 16));
        anchor(area, portal, Gravity.TOP | Gravity.RIGHT, 35, 34, -2, 32);
        LinearLayout card = ui.vbox(0); card.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 22)); card.setElevation(ui.px(6)); ui.pad(card, 40, 36, 40, 22);
        float cardW = Math.min(512, areaW - 48);
        anchor(area, card, Gravity.CENTER, 0, -14, cardW, -2);
        signCard = card;
        LinearLayout pill = ui.hbox(8); pill.setBackground(ui.shape(0xFFE0F2FE, 0, 15)); ui.pad(pill, 9, 0, 14, 0);
        card.addView(pill, new LinearLayout.LayoutParams(-2, ui.px(30)));
        ImageView pd = ui.icon(R.drawable.ic_drop, 0xFF0284C7); pill.addView(pd, new LinearLayout.LayoutParams(ui.px(16), ui.px(16)));
        ui.hug(pill, ui.tv("AUTHORIZED ACCESS", 11, 16, Ui.PRIMARY, ui.p6));
        ui.space(card, 22);
        ui.text(card, "Welcome back", 32, Ui.INK, ui.p6);
        ui.space(card, 1);
        TextView sub = ui.text(card, "Sign in with your Owner or Staff account.", 15, 20, Ui.NAV_MUTED, ui.o4); sub.setPadding(ui.px(1), 0, 0, 0);
        ui.space(card, 26); ui.divider(card); ui.space(card, 21);
        LinearLayout form = ui.V(card, 17);
        signUser = signField(form, "Username", 44, 10, cardW - 80, state.username, false);
        signPass = signField(form, "Password", 44, 10, cardW - 80, "", true);
        TextView go = ui.button("Sign in", Ui.FILLED, 44, 10, 13.5f, this::attemptSignIn);
        ui.size(go, 13.5f, 20); ui.fill(form, go);
        ui.space(card, 40);
        FrameLayout note = new FrameLayout(this); note.setBackground(ui.shape(Ui.BG, 0, 10));
        card.addView(note, new LinearLayout.LayoutParams(-1, ui.px(42)));
        ImageView lock = ui.icon(R.drawable.ic_lock, 0xFF0284C7); FrameLayout.LayoutParams lkp = new FrameLayout.LayoutParams(ui.px(17), ui.px(17), Gravity.CENTER_VERTICAL); lkp.leftMargin = ui.px(13); note.addView(lock, lkp);
        TextView nt = ui.tv("Access is limited to authorized accounts.", 12, 16, Ui.NAV_MUTED, ui.o4); FrameLayout.LayoutParams ntp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL); ntp.leftMargin = ui.px(42); note.addView(nt, ntp);
        TextView foot = ui.tv("Aqua Quenchers  ·  Business Management System", 12, 16, Ui.NAV_MUTED, ui.o4); foot.setGravity(Gravity.CENTER_HORIZONTAL);
        anchor(area, foot, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 28, -2, -2);
    }
    private void staffSignIn(FrameLayout f) {
        host.topColor = Ui.PRIMARY; host.bottomColor = Ui.BG; host.sideColor = Ui.BG;
        f.setBackgroundColor(Ui.BG);
        FrameLayout header = new FrameLayout(this); header.setClipChildren(true);
        header.setBackground(linear(-0.01f, 0.48f, 1.04f, 0.61f, new int[]{0xFF0284C7, 0xFF0369A1, 0xFF075985}, new float[]{0, 0.5f, 1}));
        f.addView(header, new FrameLayout.LayoutParams(-1, ui.px(346)));
        ellipse(header, 255, -80, 310, 310, alpha(0x7DD3FC, 0.18f), 0);
        ellipse(header, -100, 261, 400, 265, alpha(0x7DD3FC, 0.13f), 0);
        ellipse(header, 225, 102, 255, 255, alpha(0xFFFFFF, 0.04f), alpha(0xBAE6FD, 0.16f));
        place(header, tile(46, 14, 27, alpha(0xFFFFFF, 0.17f), alpha(0xFFFFFF, 0.25f)), 24, 27, 46, 46);
        place(header, ui.tv("Aqua Quencher", 20, 30, Ui.WHITE, ui.p6), 83, 29, -2, -2);
        place(header, ui.tv("WATER REFILLING STATION", 9, 14, 0xFFD7F3FF, ui.p5), 83, 57, -2, -2);
        place(header, ui.tv("STATION WORKSPACE", 11, 16, 0xFFDBF2FF, ui.p6), 25, 139, -2, -2);
        place(header, ui.tv("Your shift starts\nhere.", 34, 51, Ui.WHITE, ui.p6), 22, 165, 386, -2);
        LinearLayout card = ui.vbox(0); card.setBackground(ui.shape(Ui.WHITE, Ui.BORDER, 22)); card.setElevation(ui.px(6)); ui.pad(card, 24, 23, 24, 19);
        place(f, card, 20, 310, 400, -2);
        signCard = card;
        ui.text(card, "Welcome back", 23, 34, Ui.INK, ui.p6);
        ui.space(card, 1);
        ui.text(card, "Sign in with your Owner or Staff account.", 13, 18, Ui.NAV_MUTED, ui.o4);
        ui.space(card, 22); ui.divider(card); ui.space(card, 18);
        LinearLayout form = ui.V(card, 12);
        signUser = signField(form, "Username", 50, 12, 352, state.username, false);
        signPass = signField(form, "Password", 50, 12, 352, "", true);
        TextView go = ui.button("Sign in", Ui.FILLED, 48, 12, 14, this::attemptSignIn); ui.size(go, 14, 21); ui.fill(form, go);
        ui.space(card, 38);
        LinearLayout note = ui.H(card, 8); note.setGravity(Gravity.CENTER); note.setMinimumHeight(ui.px(24));
        note.addView(ui.icon(R.drawable.ic_restricted, 0xFF0284C7), new LinearLayout.LayoutParams(ui.px(15), ui.px(15)));
        ui.hug(note, ui.tv("Authorized accounts only.", 12, 16, Ui.NAV_MUTED, ui.o4));
        LinearLayout foot = ui.vbox(10); foot.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM); fp.bottomMargin = ui.px(12); f.addView(foot, fp);
        ui.hug(foot, ui.tv("Aqua Quencher  ·  Business workspace", 12, 16, Ui.NAV_MUTED, ui.o4));
        ui.hug(foot, ui.tv("Ready when you are.", 13, 20, Ui.PRIMARY, ui.p5));
    }
    /** Sign-in field: Poppins 600 14/21 label, input, and an icon + message validation row. */
    private EditText signField(LinearLayout form, String label, float h, float r, float w, String value, boolean secret) {
        LinearLayout field = ui.V(form, 6);
        ui.text(field, label, 14, 21, Ui.INK, ui.p6);
        FrameLayout box = new FrameLayout(this);
        field.addView(box, new LinearLayout.LayoutParams(-1, ui.px(h)));
        EditText e = new EditText(this);
        e.setTag(label); e.setContentDescription(label); e.setHint(label); e.setText(value);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : 0)); e.setSingleLine(true);
        e.setTypeface(ui.o4); e.setTextColor(0xFF334155); e.setHintTextColor(0xFF94A3B8); e.setIncludeFontPadding(false); ui.size(e, 14, 19);
        e.setGravity(Gravity.CENTER_VERTICAL); ui.pad(e, 14, 0, 40, 0);
        e.setBackground(ui.shape(Ui.WHITE, 0xFFCBD5E1, r));
        box.addView(e, new FrameLayout.LayoutParams(-1, -1));
        ImageView mark = ui.icon(R.drawable.ic_error, 0xFFB91C1C); mark.setVisibility(View.GONE);
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(ui.px(16), ui.px(16), Gravity.END | Gravity.CENTER_VERTICAL); mp.rightMargin = ui.px(14); box.addView(mark, mp);
        LinearLayout row = ui.hbox(8); row.setVisibility(View.GONE); row.setMinimumHeight(ui.px(24));
        row.addView(ui.icon(R.drawable.ic_error, 0xFFB91C1C), new LinearLayout.LayoutParams(ui.px(16), ui.px(16)));
        TextView msg = ui.tv("", 12, 16, 0xFFB91C1C, ui.o4); ui.hug(row, msg);
        ui.fill(field, row);
        e.setTag(R.id.field_error, new Object[]{box, mark, row, msg, r});
        e.setImeOptions(secret ? android.view.inputmethod.EditorInfo.IME_ACTION_DONE : android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
        if (secret) e.setOnEditorActionListener((v, action, ev) -> { if (action == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) { attemptSignIn(); return true; } return false; });
        e.addTextChangedListener(watcher(() -> markSign(e, null)));
        return e;
    }
    private void markSign(EditText e, String message) {
        Object[] parts = (Object[]) e.getTag(R.id.field_error);
        boolean bad = message != null;
        e.setBackground(ui.shape(Ui.WHITE, bad ? 0xFFEF4444 : 0xFFCBD5E1, (Float) parts[4]));
        ((View) parts[1]).setVisibility(bad ? View.VISIBLE : View.GONE);
        boolean showRow = bad && !message.isEmpty();
        ((View) parts[2]).setVisibility(showRow ? View.VISIBLE : View.GONE);
        if (showRow) ((TextView) parts[3]).setText(message);
    }
    /** Figma O1b/O1d: one message under Username; both fields outlined. O1c: each missing field. */
    private void signError(String message, boolean both, boolean unused) {
        if (signUser == null) return;
        markSign(signUser, message);
        if (both) markSign(signPass, "");
    }
    private void attemptSignIn() {
        String u = signUser.getText().toString().trim(), pass = signPass.getText().toString();
        markSign(signUser, null); markSign(signPass, null);
        if (u.isEmpty()) markSign(signUser, "Enter your username.");
        if (pass.isEmpty()) markSign(signPass, "Enter your password.");
        if (u.isEmpty() || pass.isEmpty()) return;
        Session pending = state; int attempt = ++loginGeneration; busy(true);
        state.backend.call("/functions/v1/username-login", json("username", u, "password", pass), r -> {
            busy(false); if (pending != state || attempt != loginGeneration || isDestroyed()) return;
            acceptTokens(r); state.username = u; state.lastInteraction = SystemClock.elapsedRealtime(); signPass.setText("");
            Session s = state;
            busy(true);
            s.backend.call("/rest/v1/rpc/aq_query", json("p_request", json("resource", "me")), profile -> {
                busy(false); if (pending != state || attempt != loginGeneration || isDestroyed()) return;
                String role = profile.optString("role");
                if (!Rules.uuid(profile.optString("user_id")) || !profile.optBoolean("active") || !("owner".equals(role) || "staff".equals(role))) { signError("Invalid username or password.", true, false); return; }
                state.profile = profile; shell(); restoreRoute();
            }, e -> { busy(false); if (pending == state && attempt == loginGeneration && !isDestroyed()) signError(e.status == 0 ? e.getMessage() : "Invalid username or password.", true, false); });
        }, e -> {
            busy(false);
            if (pending == state && attempt == loginGeneration && !isDestroyed()) {
                if (e.status == 429) signError("Sign-in unavailable. Try again later.", false, false);
                else if (e.status == 0) signError(e.getMessage(), false, false);
                else signError("Invalid username or password.", true, false);
            }
        });
    }

    // =====================================================================================================
    // Navigation
    // =====================================================================================================
    private void restoreRoute() { if (ownerUi.restore(state.route, state.routeEntity) || operations.restore(state.route, state.routeEntity)) return; showHome(); }
    private void navigate(String route) { switch (route) { case "Dashboard": case "Home": showHome(); break; case "Orders": operations.orders(); break; case "Customers": operations.customers(); break; case "POS": operations.pos(); break; case "Payments": operations.payments(); break; case "Expenses": ownerUi.expenses(); break; case "Reports": ownerUi.reports(); break; case "Settings": case "More": ownerUi.settings(); break; default: showHome(); } }
    /** Header Back: the parent of the current page, never a status shortcut. */
    void goBack() {
        String entity = state.routeEntity == null ? "" : state.routeEntity;
        switch (currentPage) {
            case "Order Details": case "Create Order": case "Pending gallons / Carry-over Details": case "Sale saved": operations.orders(); break;
            case "Record Pickup": case "Reschedule Pickup": case "Record Delivery": if (Rules.uuid(entity)) operations.order(entity); else operations.orders(); break;
            case "Customer Profile": case "Add Customer": operations.customers(); break;
            case "Edit Customer": if (Rules.uuid(entity)) operations.customer(entity); else operations.customers(); break;
            case "Record Payment": case "Payment history": case "Payment recorded": if (isOwner()) operations.payments(); else operations.orders(); break;
            case "Add Expense": ownerUi.expenses(); break;
            case "Expenses": case "Financial summary": ownerUi.settings(); break;
            case "Report Preview": ownerUi.reports(); break;
            case "Inventory": case "Staff accounts": case "My account": case "Change password": case "Edit profile": ownerUi.settings(); break;
            case "Create Staff account": case "Edit Staff account": ownerUi.staffAccounts(); break;
            default: showHome();
        }
    }
    public void showHome() { home.show(); }
    public void endSession(String reason) {
        dismissDialogs(); state.expenseState.cancelPending(); ownerUi.close();
        Backend old = state.backend;
        if (!old.accessToken.isEmpty()) { old.call("/rest/v1/rpc/aq_logout", json(), r -> {}, e -> {}); old.call("/auth/v1/logout?scope=local", json(), r -> {}, e -> {}); }
        old.close(); String user = state.username; state = new Session(); state.username = user;
        operations = new OperationsUi(this); ownerUi = new OwnerUi(this); home = new HomeUi(this);
        if (reason.startsWith("Session expired") || reason.startsWith("Access denied")) state.signInNotice = reason;
        signIn();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (state.profile == null || !isOwner() || SystemClock.elapsedRealtime() - state.lastInteraction >= INACTIVITY) { endSession("Session expired. Sign in again."); return; } ownerUi.activityResult(request, result, data); }
    public void openOrders() { operations.orders(); }
    public void openPayments() { operations.payments(); }
    public void openPending() { operations.pending(); }
}
