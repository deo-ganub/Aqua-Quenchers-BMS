package com.example.aquaquencherspos;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owner shell: header, the seven navigation options and the 30-minute idle rule. */
public class OwnerDashboardActivity extends AppCompatActivity {

    private static final String[] SECTIONS = {
            "Dashboard", "Orders", "Customers", "Payments", "Expenses", "Reports", "Settings"
    };
    private static final int[] ICONS = {
            R.drawable.ic_aq_dashboard, R.drawable.ic_aq_orders, R.drawable.ic_aq_customers,
            R.drawable.ic_aq_payments, R.drawable.ic_aq_expenses, R.drawable.ic_aq_reports,
            R.drawable.ic_aq_settings
    };
    private static final long IDLE_CHECK_MS = 60_000L;

    private final Map<String, LinearLayout> navItems = new LinkedHashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idleCheck = new Runnable() {
        @Override
        public void run() {
            if (Session.isIdleTooLong()) {
                expireSession();
            } else {
                handler.postDelayed(this, IDLE_CHECK_MS);
            }
        }
    };
    private DrawerLayout drawer;
    private boolean expiring;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!Session.isSignedIn()) {
            // The process was restarted: tokens are memory-only, so sign in again.
            super.onCreate(null);
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owner_dashboard);

        View root = findViewById(R.id.shellRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        if (root instanceof DrawerLayout) {
            drawer = (DrawerLayout) root;
            findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        }
        buildNavigation();
        if (savedInstanceState == null) {
            openRoot(new DashboardScreen());
        }
    }

    private void buildNavigation() {
        LinearLayout list = findViewById(R.id.navList);
        for (int i = 0; i < SECTIONS.length; i++) {
            String section = SECTIONS[i];
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(this, 14);
            item.setPadding(pad, 0, pad, 0);
            ImageView icon = new ImageView(this);
            icon.setImageResource(ICONS[i]);
            icon.setImageTintList(Ui.tint(Ui.MUTED));
            item.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
            TextView label = Ui.text(this, section, Ui.medium(this), 14, Ui.INK);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelLp.leftMargin = Ui.dp(this, 12);
            item.addView(label, labelLp);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 48));
            lp.bottomMargin = Ui.dp(this, 12);
            list.addView(item, lp);
            item.setOnClickListener(v -> {
                if (drawer != null) {
                    drawer.closeDrawers();
                }
                openRoot(rootFor(section));
            });
            navItems.put(section, item);
        }
    }

    private Fragment rootFor(String section) {
        switch (section) {
            case "Orders":
                return new OrderScreens.Orders();
            case "Customers":
                return new CustomerScreens.Customers();
            case "Payments":
                return new PaymentScreens.Payments();
            case "Expenses":
                return new ExpenseScreens.Expenses();
            case "Reports":
                return new ReportScreens.Reports();
            case "Settings":
                return new SettingsScreens.Settings();
            default:
                return new DashboardScreen();
        }
    }

    /** Called by each screen so the current section stays highlighted. */
    void highlight(String section) {
        for (Map.Entry<String, LinearLayout> e : navItems.entrySet()) {
            boolean active = e.getKey().equals(section);
            LinearLayout item = e.getValue();
            item.setBackgroundResource(active ? R.drawable.aq_nav_active : 0);
            ((ImageView) item.getChildAt(0)).setImageTintList(Ui.tint(active ? Ui.PRIMARY : Ui.MUTED));
            ((TextView) item.getChildAt(1)).setTextColor(active ? Ui.PRIMARY : Ui.INK);
        }
    }

    /** A top-level section: starts a fresh back stack. */
    void openRoot(Fragment screen) {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        fm.beginTransaction().setReorderingAllowed(true)
                .replace(R.id.workspaceContainer, screen).commit();
    }

    void open(Fragment screen) {
        getSupportFragmentManager().beginTransaction().setReorderingAllowed(true)
                .replace(R.id.workspaceContainer, screen).addToBackStack(null).commit();
    }

    void back() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        } else {
            openRoot(new DashboardScreen());
        }
    }

    void signOut() {
        Api.logout(Session.accessToken);
        Session.clear();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** Requirement: idle or deactivated accounts are returned to sign-in. */
    void expireSession() {
        if (expiring || isFinishing()) {
            return;
        }
        expiring = true;
        handler.removeCallbacks(idleCheck);
        Api.logout(Session.accessToken);
        Session.clear();
        Dialogs.message(this, "Session expired. Sign in again.",
                "The account was idle for 30 minutes or its active access was removed. Protected requests are denied.",
                "Sign in", this::signOut);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        Session.touch();
        if (Session.isSignedIn() && Session.serverTouchDue()) {
            Session.serverTouched();
            Api.rpc("aq_session_touch", new JSONObject(), result -> {
            }, error -> {
                if (error.sessionExpired) {
                    expireSession();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Session.isSignedIn()) {
            return;
        }
        if (Session.isIdleTooLong()) {
            expireSession();
            return;
        }
        handler.postDelayed(idleCheck, IDLE_CHECK_MS);
        // Also catches an account deactivated while the app was in the background.
        Api.rpc("whoami", new JSONObject(), me -> Session.serverTouched(), error -> {
            if (error.sessionExpired) {
                expireSession();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(idleCheck);
    }
}
