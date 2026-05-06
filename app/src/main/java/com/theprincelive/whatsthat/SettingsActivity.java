package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";

    Button otherCaptureBtn;
    Button lockBtn;
    Button disableLockBtn;
    Button hiddenRulesBtn;
    Button lockOnCloseBtn;
    Button biometricBtn;
    MessageStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new MessageStore(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(Color.WHITE);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = title("Settings");
        root.addView(title);
        root.addView(copy("Control what WhatsThat captures and review the privacy limits of notification history."));

        Button access = primaryButton("Open Notification Access");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(access, buttonParams(18));

        otherCaptureBtn = secondaryButton("");
        otherCaptureBtn.setOnClickListener(v -> toggleOtherCapture());
        root.addView(otherCaptureBtn, buttonParams(10));

        hiddenRulesBtn = secondaryButton("");
        hiddenRulesBtn.setOnClickListener(v -> clearHiddenRules());
        root.addView(hiddenRulesBtn, buttonParams(10));

        Button cleanupBtn = secondaryButton("Cleanup Tools");
        cleanupBtn.setOnClickListener(v -> showCleanupTools());
        root.addView(cleanupBtn, buttonParams(10));

        lockBtn = secondaryButton("");
        lockBtn.setOnClickListener(v -> openLockSetup());
        root.addView(lockBtn, buttonParams(10));

        lockOnCloseBtn = secondaryButton("");
        lockOnCloseBtn.setOnClickListener(v -> toggleLockOnClose());
        root.addView(lockOnCloseBtn, buttonParams(10));

        biometricBtn = secondaryButton("");
        biometricBtn.setOnClickListener(v -> toggleBiometric());
        root.addView(biometricBtn, buttonParams(10));

        disableLockBtn = secondaryButton("Turn Off App Lock");
        disableLockBtn.setOnClickListener(v -> openLockDisable());
        root.addView(disableLockBtn, buttonParams(10));

        root.addView(section("What WhatsThat Saves"));
        root.addView(copy("WhatsThat saves new notifications after you grant Notification Access. WhatsApp mode ignores status noise such as \"Checking for new messages\" and grouped \"2 new messages\" alerts."));

        root.addView(section("Other Notices"));
        root.addView(copy("Other notices are opt-in. When enabled, non-WhatsApp notifications are saved into a separate inbox so they do not mix with WhatsApp messages."));

        root.addView(section("Privacy Limits"));
        root.addView(copy("The app stores notification text locally on this phone. It cannot read old chats, muted WhatsApp chats, deleted messages, or anything that never appeared as a notification."));

        updateOtherButton();
        updateLockButtons();
        updateHiddenRulesButton();
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLockButtons();
        updateHiddenRulesButton();
    }

    void toggleOtherCapture() {
        boolean next = !captureOther();
        prefs().edit().putBoolean(PREF_CAPTURE_OTHER, next).apply();
        updateOtherButton();
        Toast.makeText(this, next ? "Other notices capture enabled." : "Other notices capture disabled.", Toast.LENGTH_SHORT).show();
    }

    void updateOtherButton() {
        otherCaptureBtn.setText(captureOther() ? "Stop Capturing Other Notices" : "Capture Other Notices");
    }

    void clearHiddenRules() {
        startActivity(new Intent(this, HiddenRulesActivity.class));
    }

    void showCleanupTools() {
        String[] actions = {
                "Remove WhatsApp noise",
                "Clear Other notices",
                "Delete notices older than 7 days",
                "Delete notices older than 30 days",
                "Delete notices older than 90 days"
        };
        new AlertDialog.Builder(this)
                .setTitle("Cleanup Tools")
                .setItems(actions, (dialog, which) -> handleCleanupAction(which))
                .show();
    }

    void handleCleanupAction(int which) {
        if (which == 0) {
            int removed = store.deleteWhatsAppNoise();
            Toast.makeText(this, "Removed " + removed + " WhatsApp noise items.", Toast.LENGTH_SHORT).show();
        } else if (which == 1) {
            confirmCleanup("Clear Other notices?", "This removes all saved non-WhatsApp notifications.", () -> {
                store.clearMessages(true);
                Toast.makeText(this, "Other notices cleared.", Toast.LENGTH_SHORT).show();
            });
        } else if (which == 2) {
            confirmDeleteOlderThan(7);
        } else if (which == 3) {
            confirmDeleteOlderThan(30);
        } else if (which == 4) {
            confirmDeleteOlderThan(90);
        }
    }

    void confirmDeleteOlderThan(int days) {
        confirmCleanup("Delete old notices?", "This removes saved notifications older than " + days + " days.", () -> {
            int removed = store.deleteOlderThanDays(days);
            Toast.makeText(this, "Removed " + removed + " old notices.", Toast.LENGTH_SHORT).show();
        });
    }

    void confirmCleanup(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void updateHiddenRulesButton() {
        if (hiddenRulesBtn == null) return;
        int count = NotificationRules.count(this);
        hiddenRulesBtn.setText(count == 0 ? "Blocked Notices" : "Blocked Notices (" + count + ")");
        hiddenRulesBtn.setEnabled(true);
    }

    void toggleLockOnClose() {
        boolean next = !AppLock.lockOnClose(this);
        AppLock.setLockOnClose(this, next);
        updateLockButtons();
        Toast.makeText(this, next ? "PIN required when WhatsThat closes." : "PIN required after app restart only.", Toast.LENGTH_SHORT).show();
    }

    void toggleBiometric() {
        if (!AppLock.isEnabled(this)) {
            Toast.makeText(this, "Set an app lock PIN first.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Biometric unlock needs Android 9 or newer.", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean next = !AppLock.biometricEnabled(this);
        AppLock.setBiometricEnabled(this, next);
        updateLockButtons();
        Toast.makeText(this, next ? "Biometric unlock enabled." : "Biometric unlock disabled.", Toast.LENGTH_SHORT).show();
    }

    void openLockSetup() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, AppLock.isEnabled(this) ? LockActivity.MODE_CHANGE : LockActivity.MODE_SET);
        startActivity(intent);
    }

    void openLockDisable() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, LockActivity.MODE_DISABLE);
        startActivity(intent);
    }

    void updateLockButtons() {
        if (lockBtn == null || lockOnCloseBtn == null || biometricBtn == null || disableLockBtn == null) return;
        boolean enabled = AppLock.isEnabled(this);
        lockBtn.setText(enabled ? "Change App Lock PIN" : "Set App Lock PIN");
        lockOnCloseBtn.setText(AppLock.lockOnClose(this) ? "Lock When App Closes: On" : "Lock When App Closes: Off");
        lockOnCloseBtn.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        biometricBtn.setText(AppLock.biometricEnabled(this) ? "Biometric Unlock: On" : "Biometric Unlock: Off");
        biometricBtn.setVisibility(enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? android.view.View.VISIBLE : android.view.View.GONE);
        disableLockBtn.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    boolean captureOther() {
        return prefs().getBoolean(PREF_CAPTURE_OTHER, false);
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(17, 27, 24));
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    TextView section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(0, 107, 85));
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(22), 0, dp(6));
        return view;
    }

    TextView copy(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(91, 104, 98));
        view.setTextSize(14);
        view.setLineSpacing(dp(4), 1.0f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    Button primaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0, 107, 85));
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        return button;
    }

    Button secondaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.rgb(0, 107, 85));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 248, 246));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(231, 234, 230));
        button.setBackground(bg);
        return button;
    }

    Button baseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        return button;
    }

    LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
