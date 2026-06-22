package com.theprincelive.whatsthat;

import android.app.Activity;
import android.hardware.biometrics.BiometricPrompt;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LockActivity extends Activity {
    public static final String MODE = "mode";
    public static final String MODE_UNLOCK = "unlock";
    public static final String MODE_SET = "set";
    public static final String MODE_CHANGE = "change";
    public static final String MODE_DISABLE = "disable";

    String mode;
    String pendingPin;
    int step;
    TextView title;
    TextView helper;
    EditText pinInput;
    Button actionButton;
    Button biometricButton;
    boolean biometricPromptShown;
    CancellationSignal biometricSignal;
    boolean isDecoyMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getStringExtra(MODE);
        if (mode == null) mode = MODE_UNLOCK;
        isDecoyMode = getIntent().getBooleanExtra("is_decoy", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(56), dp(28), dp(28));
        root.setBackgroundColor(getColor(R.color.ios_bg));

        TextView brand = new TextView(this);
        brand.setText("WhatsThat");
        brand.setTextColor(getColor(R.color.brand_green));
        brand.setTextSize(30);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setIncludeFontPadding(false);
        root.addView(brand);

        title = new TextView(this);
        title.setTextColor(getColor(R.color.ios_ink));
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(28), 0, 0);
        root.addView(title);

        helper = new TextView(this);
        helper.setTextColor(getColor(R.color.ios_muted));
        helper.setTextSize(14);
        helper.setGravity(Gravity.CENTER);
        helper.setLineSpacing(dp(4), 1.0f);
        helper.setPadding(0, dp(10), 0, dp(18));
        root.addView(helper);

        pinInput = new EditText(this);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});
        pinInput.setTextSize(24);
        pinInput.setTextColor(getColor(R.color.ios_ink));
        pinInput.setHint("0000");
        pinInput.setSingleLine(true);
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(getColor(R.color.ios_surface));
        inputBg.setCornerRadius(dp(18));
        inputBg.setStroke(dp(1), getColor(R.color.ios_border));
        pinInput.setBackground(inputBg);
        root.addView(pinInput, new LinearLayout.LayoutParams(-1, dp(58)));

        actionButton = button("Continue", true);
        actionButton.setOnClickListener(v -> handleAction());
        root.addView(actionButton, params(18));

        biometricButton = button("Use biometric unlock", false);
        biometricButton.setOnClickListener(v -> showBiometricPrompt(false));
        root.addView(biometricButton, params(10));

        Button cancel = button("Cancel", false);
        cancel.setOnClickListener(v -> cancelFlow());
        root.addView(cancel, params(10));

        refreshText();
        setContentView(root);
        if (canUseBiometric()) pinInput.postDelayed(() -> showBiometricPrompt(true), 350);
    }

    void handleAction() {
        String pin = pinInput.getText().toString();
        if (!AppLock.validPin(pin)) {
            Toast.makeText(this, "Enter a 4-digit PIN.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (MODE_UNLOCK.equals(mode)) {
            if (AppLock.verifyPin(this, pin)) {
                AppLock.setUnlocked(true);
                AppLock.setDecoySession(false);
                finish();
            } else if (AppLock.hasDecoyPin(this) && AppLock.verifyDecoyPin(this, pin)) {
                AppLock.setUnlocked(true);
                AppLock.setDecoySession(true);
                finish();
            } else {
                showError();
            }
            return;
        }

        if (MODE_DISABLE.equals(mode)) {
            if (AppLock.verifyPin(this, pin)) {
                AppLock.disable(this);
                Toast.makeText(this, "App lock turned off.", Toast.LENGTH_SHORT).show();
                finish();
            } else showError();
            return;
        }

        if (MODE_CHANGE.equals(mode) && step == 0) {
            boolean verified = isDecoyMode ? AppLock.verifyDecoyPin(this, pin) : AppLock.verifyPin(this, pin);
            if (verified) {
                step = 1;
                pinInput.setText("");
                refreshText();
            } else showError();
            return;
        }

        if (pendingPin == null) {
            pendingPin = pin;
            pinInput.setText("");
            step = 2;
            refreshText();
            return;
        }

        if (!pendingPin.equals(pin)) {
            pendingPin = null;
            step = MODE_CHANGE.equals(mode) ? 1 : 0;
            pinInput.setText("");
            refreshText();
            Toast.makeText(this, "PINs did not match. Try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDecoyMode) {
            AppLock.setDecoyPin(this, pin);
            Toast.makeText(this, MODE_CHANGE.equals(mode) ? "Decoy PIN changed." : "Decoy PIN enabled.", Toast.LENGTH_SHORT).show();
        } else {
            AppLock.setPin(this, pin);
            Toast.makeText(this, MODE_CHANGE.equals(mode) ? "PIN changed." : "App lock enabled.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    void refreshText() {
        if (MODE_UNLOCK.equals(mode)) {
            title.setText("Enter PIN");
            helper.setText(canUseBiometric() ? "Use biometric unlock or enter your PIN." : "Unlock WhatsThat to view saved notifications.");
            actionButton.setText("Unlock");
        } else if (MODE_DISABLE.equals(mode)) {
            title.setText("Turn Off App Lock");
            helper.setText("Enter your PIN to remove app lock.");
            actionButton.setText("Turn Off");
        } else if (MODE_CHANGE.equals(mode) && step == 0) {
            title.setText(isDecoyMode ? "Change Decoy PIN" : "Change PIN");
            helper.setText(isDecoyMode ? "Enter your current Decoy PIN first." : "Enter your current PIN first.");
            actionButton.setText("Continue");
        } else if (pendingPin == null) {
            title.setText(isDecoyMode ? (MODE_CHANGE.equals(mode) ? "New Decoy PIN" : "Set Decoy PIN") : (MODE_CHANGE.equals(mode) ? "New PIN" : "Set App Lock"));
            helper.setText(isDecoyMode ? "Create a 4-digit Decoy PIN for WhatsThat." : "Create a 4-digit PIN for WhatsThat.");
            actionButton.setText("Continue");
        } else {
            title.setText(isDecoyMode ? "Confirm Decoy PIN" : "Confirm PIN");
            helper.setText(isDecoyMode ? "Re-enter the same 4-digit Decoy PIN." : "Re-enter the same 4-digit PIN.");
            actionButton.setText(isDecoyMode ? "Save Decoy PIN" : "Save PIN");
        }
        if (biometricButton != null) {
            biometricButton.setVisibility(canUseBiometric() ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    boolean canUseBiometric() {
        return MODE_UNLOCK.equals(mode)
                && AppLock.biometricEnabled(this)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;
    }

    void showBiometricPrompt(boolean automatic) {
        if (!canUseBiometric()) return;
        if (automatic && biometricPromptShown) return;
        biometricPromptShown = true;
        if (biometricSignal != null) biometricSignal.cancel();
        biometricSignal = new CancellationSignal();

        Handler handler = new Handler(Looper.getMainLooper());
        java.util.concurrent.Executor executor = command -> handler.post(command);
        BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Unlock WhatsThat")
                .setSubtitle("Confirm it is you to view saved notifications.")
                .setNegativeButton("Use PIN", executor, (dialog, which) -> { })
                .build();
        prompt.authenticate(biometricSignal, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                AppLock.setUnlocked(true);
                finish();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                if (!automatic && errString != null) {
                    Toast.makeText(LockActivity.this, errString, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                Toast.makeText(LockActivity.this, "Biometric not recognized. Try again or enter PIN.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    void showError() {
        pinInput.setText("");
        Toast.makeText(this, "Wrong PIN.", Toast.LENGTH_SHORT).show();
    }

    void cancelFlow() {
        if (biometricSignal != null) biometricSignal.cancel();
        if (MODE_UNLOCK.equals(mode)) finishAffinity();
        else finish();
    }

    @Override
    public void onBackPressed() {
        cancelFlow();
    }

    Button button(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : getColor(R.color.brand_green));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? getColor(R.color.brand_green) : getColor(R.color.ios_surface));
        bg.setCornerRadius(dp(18));
        if (!primary) bg.setStroke(dp(1), getColor(R.color.ios_border));
        button.setBackground(bg);
        return button;
    }

    LinearLayout.LayoutParams params(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
