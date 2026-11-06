package com.example.aquaquencherspos;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONObject;

/** Owner sign-in (A-01). */
public class MainActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private TextView tvUsernameError;
    private TextView tvPasswordError;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Session.isSignedIn()) {
            // Reopened from the launcher while signed in. The shell applies the idle rule.
            startActivity(new Intent(this, OwnerDashboardActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.loginRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvUsernameError = findViewById(R.id.tvUsernameError);
        tvPasswordError = findViewById(R.id.tvPasswordError);
        btnLogin = findViewById(R.id.btnLogin);
        btnLogin.setOnClickListener(v -> attemptLogin());

    }

    private void showError(TextView view, String message) {
        view.setText(message);
        view.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        showError(tvUsernameError, username.isEmpty() ? "Enter your username." : null);
        showError(tvPasswordError, password.isEmpty() ? "Enter your password." : null);
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }

        setBusy(true);
        Api.login(username, password, this::onSignedIn, error -> {
            setBusy(false);
            if (error.connection) {
                showError(tvUsernameError, null);
                showError(tvPasswordError, "Unable to connect. Check the internet connection and try again.");
            } else {
                // The same wording for wrong, unknown and inactive accounts (A-01.3).
                showError(tvUsernameError, "Invalid username or password.");
                showError(tvPasswordError, "Access denied. Check your account details.");
            }
        });
    }

    private void onSignedIn(JSONObject session) {
        setBusy(false);
        if (!"owner".equals(session.optString("role"))) {
            // Only the Owner module exists so far, so a Staff account stops here.
            showError(tvUsernameError, null);
            showError(tvPasswordError, "The Staff app is not available yet. Use an Owner account.");
            return;
        }
        Session.start(
                session.optString("access_token"),
                session.optString("refresh_token"),
                session.optString("user_id"),
                session.optString("role"),
                session.optString("username"),
                session.isNull("full_name") ? null : session.optString("full_name"));
        etPassword.setText("");
        startActivity(new Intent(this, OwnerDashboardActivity.class));
        finish();
    }

    private void setBusy(boolean busy) {
        btnLogin.setEnabled(!busy);
        btnLogin.setAlpha(busy ? 0.6f : 1f);
        btnLogin.setText(busy ? "Signing in…" : "Sign in");
    }
}
