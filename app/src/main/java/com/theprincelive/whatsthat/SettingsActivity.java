package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends Activity {
    private static final int REQUEST_IMPORT_CSV = 72;
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PREF_RETENTION_DAYS = "retention_days";

    Button otherCaptureBtn;
    Button retentionBtn;
    Button lockBtn;
    Button disableLockBtn;
    Button hiddenRulesBtn;
    Button lockOnCloseBtn;
    Button biometricBtn;
    Button decoyBtn;
    Button disableDecoyBtn;
    MessageStore store;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = MessageStore.getInstance(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.ios_bg));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), 0, dp(18), dp(24));
        root.setBackgroundColor(getColor(R.color.ios_bg));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(6), dp(28), dp(6), dp(22));
        header.setBackgroundColor(Color.TRANSPARENT);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        header.addView(BackNav.button(this, false), new LinearLayout.LayoutParams(dp(96), dp(42)));

        TextView title = title("Settings");
        title.setTextColor(getColor(R.color.ios_ink));
        title.setPadding(0, dp(14), 0, 0);
        header.addView(title);
        TextView intro = copy("Security and storage dashboard");
        intro.setTextColor(getColor(R.color.ios_muted));
        header.addView(intro);

        root.addView(section("App Lock"));

        LinearLayout lockCard = dashboardCard();
        TextView lockLabel = smallLabel("APP LOCK");
        lockCard.addView(lockLabel);
        lockBtn = primaryButton("");
        lockBtn.setTextSize(18);
        lockBtn.setOnClickListener(v -> openLockSetup());
        lockCard.addView(lockBtn, new LinearLayout.LayoutParams(-1, dp(54)));
        root.addView(lockCard, cardParams(6));

        LinearLayout securityRow = tileRow();
        biometricBtn = tileButton("");
        biometricBtn.setOnClickListener(v -> toggleBiometric());
        securityRow.addView(biometricBtn, tileParams(0));

        lockOnCloseBtn = tileButton("");
        lockOnCloseBtn.setOnClickListener(v -> toggleLockOnClose());
        securityRow.addView(lockOnCloseBtn, tileParams(8));
        root.addView(securityRow, cardParams(10));

        Button access = primaryButton("Open Notification Access");
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        root.addView(access, buttonParams(10));

        root.addView(section("Data"));
        LinearLayout dataRow = tileRow();
        Button exportBtn = tileButton("Export\nMessages");
        exportBtn.setOnClickListener(v -> showExportOptions());
        dataRow.addView(exportBtn, tileParams(0));

        Button importBtn = tileButton("Import\nMessages");
        importBtn.setOnClickListener(v -> pickImportCsv());
        dataRow.addView(importBtn, tileParams(8));
        root.addView(dataRow, cardParams(6));

        retentionBtn = rowButton("");
        retentionBtn.setOnClickListener(v -> showRetentionOptions());
        root.addView(retentionBtn, buttonParams(10));

        Button cleanupBtn = rowButton("Cleanup Tools");
        cleanupBtn.setOnClickListener(v -> showCleanupTools());
        root.addView(cleanupBtn, buttonParams(10));

        root.addView(section("Capture"));
        otherCaptureBtn = rowButton("");
        otherCaptureBtn.setOnClickListener(v -> toggleOtherCapture());
        root.addView(otherCaptureBtn, buttonParams(6));

        Button statusSaverBtn = rowButton("WhatsApp Status Saver");
        statusSaverBtn.setOnClickListener(v -> startActivity(new Intent(this, StatusSaverActivity.class)));
        root.addView(statusSaverBtn, buttonParams(10));

        hiddenRulesBtn = rowButton("");
        hiddenRulesBtn.setOnClickListener(v -> clearHiddenRules());
        root.addView(hiddenRulesBtn, buttonParams(10));

        disableLockBtn = rowButton("Turn Off App Lock");
        disableLockBtn.setOnClickListener(v -> openLockDisable());
        root.addView(disableLockBtn, buttonParams(10));

        decoyBtn = rowButton("");
        decoyBtn.setOnClickListener(v -> openDecoySetup());
        root.addView(decoyBtn, buttonParams(10));

        disableDecoyBtn = rowButton("Disable Decoy PIN");
        disableDecoyBtn.setOnClickListener(v -> disableDecoyPin());
        root.addView(disableDecoyBtn, buttonParams(10));

        root.addView(section("What WhatsThat Saves"));
        root.addView(copy("WhatsThat saves new notifications after you grant Notification Access. WhatsApp mode ignores status noise such as \"Checking for new messages\" and grouped \"2 new messages\" alerts."));

        root.addView(section("Other Notices"));
        root.addView(copy("Other notices are opt-in. When enabled, non-WhatsApp notifications are saved into a separate inbox so they do not mix with WhatsApp messages."));

        root.addView(section("Status Saver"));
        root.addView(copy("Status Saver works only for statuses you have already viewed in WhatsApp. Choose the .Statuses folder once, then save photos or videos locally."));

        root.addView(section("Privacy Limits"));
        root.addView(copy("The app stores notification text locally on this phone. It cannot read old chats, muted WhatsApp chats, deleted messages, or anything that never appeared as a notification."));

        updateOtherButton();
        updateRetentionButton();
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

    void showExportOptions() {
        String[] options = {"WhatsApp messages", "Other notices"};
        new AlertDialog.Builder(this)
                .setTitle("Export Messages")
                .setItems(options, (dialog, which) -> shareCsv(which == 1))
                .show();
    }

    void shareCsv(boolean otherNotices) {
        dbExecutor.execute(() -> {
            String csv = store.exportCsv(otherNotices);
            runOnUiThread(() -> {
                if (csv.trim().equals("sender,message,app,package,received_at")) {
                    Toast.makeText(SettingsActivity.this, "No messages to export yet.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/csv");
                send.putExtra(Intent.EXTRA_SUBJECT, "WhatsThat message export");
                send.putExtra(Intent.EXTRA_TEXT, csv);
                startActivity(Intent.createChooser(send, "Export WhatsThat CSV"));
            });
        });
    }

    void pickImportCsv() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] types = {"text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
        startActivityForResult(intent, REQUEST_IMPORT_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_CSV || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importCsv(data.getData());
    }

    void importCsv(Uri uri) {
        dbExecutor.execute(() -> {
            try {
                String csv = readText(uri);
                int imported = store.importCsv(csv);
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Imported " + imported + itemCountLabel(imported) + ".", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Could not import this file.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    String readText(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) throw new IllegalArgumentException("No input stream");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    String itemCountLabel(int count) {
        return count == 1 ? " item" : " items";
    }

    void showRetentionOptions() {
        String[] options = {"Keep forever", "Delete after 7 days", "Delete after 30 days", "Delete after 90 days"};
        int current = positionForDays(prefs().getInt(PREF_RETENTION_DAYS, 0));
        new AlertDialog.Builder(this)
                .setTitle("Message Retention")
                .setSingleChoiceItems(options, current, (dialog, which) -> {
                    int days = daysForPosition(which);
                    prefs().edit().putInt(PREF_RETENTION_DAYS, days).apply();
                    dbExecutor.execute(() -> {
                        store.deleteOlderThanDays(days);
                        runOnUiThread(() -> {
                            updateRetentionButton();
                            dialog.dismiss();
                            Toast.makeText(SettingsActivity.this, retentionSummary(days), Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void updateRetentionButton() {
        if (retentionBtn == null) return;
        retentionBtn.setText("Message Retention: " + retentionSummary(prefs().getInt(PREF_RETENTION_DAYS, 0)));
    }

    String retentionSummary(int days) {
        if (days == 7) return "7 days";
        if (days == 30) return "30 days";
        if (days == 90) return "90 days";
        return "Keep forever";
    }

    int daysForPosition(int position) {
        if (position == 1) return 7;
        if (position == 2) return 30;
        if (position == 3) return 90;
        return 0;
    }

    int positionForDays(int days) {
        if (days == 7) return 1;
        if (days == 30) return 2;
        if (days == 90) return 3;
        return 0;
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
            dbExecutor.execute(() -> {
                int removed = store.deleteWhatsAppNoise();
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Removed " + removed + " WhatsApp noise items.", Toast.LENGTH_SHORT).show());
            });
        } else if (which == 1) {
            confirmCleanup("Clear Other notices?", "This removes all saved non-WhatsApp notifications.", () -> {
                dbExecutor.execute(() -> {
                    store.clearMessages(true);
                    runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Other notices cleared.", Toast.LENGTH_SHORT).show());
                });
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
            dbExecutor.execute(() -> {
                int removed = store.deleteOlderThanDays(days);
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Removed " + removed + " old notices.", Toast.LENGTH_SHORT).show());
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
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

    void openDecoySetup() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, AppLock.hasDecoyPin(this) ? LockActivity.MODE_CHANGE : LockActivity.MODE_SET);
        intent.putExtra("is_decoy", true);
        startActivity(intent);
    }

    void disableDecoyPin() {
        AppLock.disableDecoy(this);
        updateLockButtons();
        Toast.makeText(this, "Decoy PIN disabled.", Toast.LENGTH_SHORT).show();
    }

    void updateLockButtons() {
        if (lockBtn == null || lockOnCloseBtn == null || biometricBtn == null || disableLockBtn == null || decoyBtn == null || disableDecoyBtn == null) return;
        boolean enabled = AppLock.isEnabled(this);
        lockBtn.setText(enabled ? "Change App Lock PIN" : "Set App Lock PIN");
        lockOnCloseBtn.setText(AppLock.lockOnClose(this) ? "Lock When App Closes: On" : "Lock When App Closes: Off");
        lockOnCloseBtn.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        biometricBtn.setText(AppLock.biometricEnabled(this) ? "Biometric Unlock: On" : "Biometric Unlock: Off");
        biometricBtn.setVisibility(enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? android.view.View.VISIBLE : android.view.View.GONE);
        disableLockBtn.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);

        boolean decoyEnabled = AppLock.hasDecoyPin(this);
        decoyBtn.setText(decoyEnabled ? "Change Decoy PIN" : "Set Decoy PIN");
        decoyBtn.setVisibility(enabled ? android.view.View.VISIBLE : android.view.View.GONE);
        disableDecoyBtn.setVisibility(enabled && decoyEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
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
        view.setTextColor(getColor(R.color.ios_ink));
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    TextView section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.brand_green));
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, dp(22), 0, dp(6));
        return view;
    }

    TextView smallLabel(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.brand_muted));
        view.setTextSize(12);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(0, 0, 0, dp(10));
        return view;
    }

    TextView copy(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.brand_muted));
        view.setTextSize(14);
        view.setLineSpacing(dp(4), 1.0f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    Button primaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.brand_green));
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        return button;
    }

    Button secondaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(getColor(R.color.brand_green));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.brand_surface));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), getColor(R.color.brand_line));
        button.setBackground(bg);
        return button;
    }

    Button tileButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(getColor(R.color.ios_ink));
        button.setTextSize(15);
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.ios_surface));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), getColor(R.color.ios_border));
        button.setBackground(bg);
        return button;
    }

    Button rowButton(String text) {
        Button button = baseButton(text);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);
        button.setTextColor(getColor(R.color.ios_ink));
        button.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.ios_surface));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), getColor(R.color.ios_border));
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

    LinearLayout dashboardCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.ios_surface));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), getColor(R.color.ios_border));
        card.setBackground(bg);
        return card;
    }

    LinearLayout tileRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    LinearLayout.LayoutParams tileParams(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(106), 1);
        params.setMargins(dp(leftMargin), 0, 0, 0);
        return params;
    }

    LinearLayout.LayoutParams cardParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
