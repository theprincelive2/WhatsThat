package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
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

    private MessageStore store;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private LinearLayout contentContainer;
    private String currentTheme;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(ThemeManager.wrapContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        currentTheme = ThemeManager.getThemePref(this);
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
        header.setPadding(dp(6), dp(28), dp(6), dp(16));
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

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String theme = ThemeManager.getThemePref(this);
        if (!theme.equals(currentTheme)) {
            recreate();
            return;
        }
        refreshUI();
    }

    private void refreshUI() {
        contentContainer.removeAllViews();

        // --- GROUP 1: SECURITY & ACCESS ---
        contentContainer.addView(section("SECURITY & ACCESS"));
        LinearLayout securityCard = createGroupedCard();

        boolean lockEnabled = AppLock.isEnabled(this);
        addClickableRow(securityCard, "App Lock PIN", lockEnabled ? "Change PIN" : "Set PIN", true, () -> openLockSetup());

        if (lockEnabled) {
            addDivider(securityCard);
            addClickableRow(securityCard, "Turn Off App Lock", null, true, () -> openLockDisable());
            
            addDivider(securityCard);
            boolean decoyEnabled = AppLock.hasDecoyPin(this);
            addClickableRow(securityCard, "Decoy PIN", decoyEnabled ? "Change Decoy" : "Set Decoy", true, () -> openDecoySetup());
            
            if (decoyEnabled) {
                addDivider(securityCard);
                addClickableRow(securityCard, "Disable Decoy PIN", null, true, () -> disableDecoyPin());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addDivider(securityCard);
                addToggleRow(securityCard, "Biometric Unlock", AppLock.biometricEnabled(this), (buttonView, isChecked) -> toggleBiometric());
            }

            addDivider(securityCard);
            addToggleRow(securityCard, "Lock When App Closes", AppLock.lockOnClose(this), (buttonView, isChecked) -> toggleLockOnClose());
        }

        addDivider(securityCard);
        addClickableRow(securityCard, "Notification Listener Access", enabled() ? "Granted" : "Required", true, () -> {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        });

        contentContainer.addView(securityCard);
        contentContainer.addView(footer("WhatsThat requires notification access to detect and save notifications. App Lock PIN is stored securely on device."));

        // --- GROUP 2: PREFERENCES ---
        contentContainer.addView(section("PREFERENCES"));
        LinearLayout prefCard = createGroupedCard();

        addClickableRow(prefCard, "Theme Mode", getThemeLabel(), true, () -> showThemeSelector());
        addDivider(prefCard);
        addToggleRow(prefCard, "Capture Other Notices", captureOther(), (buttonView, isChecked) -> toggleOtherCapture());
        addDivider(prefCard);
        addClickableRow(prefCard, "WhatsApp Status Saver", null, true, () -> {
            startActivity(new Intent(this, StatusSaverActivity.class));
        });

        contentContainer.addView(prefCard);
        contentContainer.addView(footer("Other notices are saved to a separate inbox to keep WhatsApp messages clean. Status Saver works for viewed statuses."));

        // --- GROUP 3: DATA & STORAGE ---
        contentContainer.addView(section("DATA & STORAGE"));
        LinearLayout dataCard = createGroupedCard();

        addClickableRow(dataCard, "Message Retention", retentionSummary(prefs().getInt(PREF_RETENTION_DAYS, 0)), true, () -> showRetentionOptions());
        addDivider(dataCard);
        addClickableRow(dataCard, "Blocked Notices", getBlockedRulesSummary(), true, () -> clearHiddenRules());
        addDivider(dataCard);
        addClickableRow(dataCard, "Cleanup Tools", null, true, () -> showCleanupTools());
        addDivider(dataCard);
        addClickableRow(dataCard, "Export Messages (CSV)", null, true, () -> showExportOptions());
        addDivider(dataCard);
        addClickableRow(dataCard, "Import Messages (CSV)", null, true, () -> pickImportCsv());

        contentContainer.addView(dataCard);
        contentContainer.addView(footer("Message retention automatically deletes old notifications. Privacy limits: all data stays local on your phone."));
    }

    private LinearLayout createGroupedCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(getColor(R.color.ios_surface));
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), getColor(R.color.ios_border));
        card.setBackground(gd);
        return card;
    }

    private void addDivider(LinearLayout card) {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(1));
        lp.setMargins(dp(16), 0, 0, 0);
        divider.setBackgroundColor(getColor(R.color.ios_border));
        card.addView(divider, lp);
    }

    private void addClickableRow(LinearLayout card, String titleText, String detailText, boolean showChevron, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setClickable(true);
        row.setFocusable(true);

        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        row.setOnClickListener(v -> onClick.run());

        TextView titleView = new TextView(this);
        titleView.setText(titleText);
        titleView.setTextColor(getColor(R.color.ios_ink));
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));

        if (detailText != null && !detailText.isEmpty()) {
            TextView detailView = new TextView(this);
            detailView.setText(detailText);
            detailView.setTextColor(getColor(R.color.ios_muted));
            detailView.setTextSize(15);
            detailView.setGravity(android.view.Gravity.END);
            LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(-2, -2);
            detailLp.setMargins(0, 0, showChevron ? dp(8) : 0, 0);
            row.addView(detailView, detailLp);
        }

        if (showChevron) {
            TextView chevron = new TextView(this);
            chevron.setText("›");
            chevron.setTextColor(getColor(R.color.ios_muted));
            chevron.setTextSize(20);
            chevron.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(chevron, new LinearLayout.LayoutParams(-2, -2));
        }

        card.addView(row);
    }

    private Switch addToggleRow(LinearLayout card, String titleText, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView titleView = new TextView(this);
        titleView.setText(titleText);
        titleView.setTextColor(getColor(R.color.ios_ink));
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT);
        row.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int activeColor = getColor(R.color.ios_green);
            int inactiveColor = Color.parseColor("#E5E5EA");
            if ("dark".equals(ThemeManager.getThemePref(this))) {
                inactiveColor = Color.parseColor("#3A3A3C");
            }
            int thumbColor = Color.WHITE;

            int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
            };
            int[] thumbColors = new int[] { thumbColor, thumbColor };
            int[] trackColors = new int[] { activeColor, inactiveColor };

            toggle.setThumbTintList(new android.content.res.ColorStateList(states, thumbColors));
            toggle.setTrackTintList(new android.content.res.ColorStateList(states, trackColors));
        }

        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        card.addView(row);
        return toggle;
    }

    private String getThemeLabel() {
        String current = ThemeManager.getThemePref(this);
        if ("light".equals(current)) return "Clear Mode";
        if ("dark".equals(current)) return "Dark Mode";
        return "System Default";
    }

    private void showThemeSelector() {
        String[] options = {"Clear Mode", "Dark Mode", "System Default"};
        String current = ThemeManager.getThemePref(this);
        int selection = 2;
        if ("light".equals(current)) selection = 0;
        else if ("dark".equals(current)) selection = 1;

        new AlertDialog.Builder(this)
                .setTitle("Theme Mode")
                .setSingleChoiceItems(options, selection, (dialog, which) -> {
                    String theme = "system";
                    if (which == 0) theme = "light";
                    else if (which == 1) theme = "dark";

                    ThemeManager.setThemePref(this, theme);
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String getBlockedRulesSummary() {
        int count = NotificationRules.count(this);
        return count == 0 ? "None" : count + (count == 1 ? " rule" : " rules");
    }

    private boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private void toggleOtherCapture() {
        boolean next = !captureOther();
        prefs().edit().putBoolean(PREF_CAPTURE_OTHER, next).apply();
        Toast.makeText(this, next ? "Other notices capture enabled." : "Other notices capture disabled.", Toast.LENGTH_SHORT).show();
        refreshUI();
    }

    private void showExportOptions() {
        String[] options = {"WhatsApp messages", "Other notices"};
        new AlertDialog.Builder(this)
                .setTitle("Export Messages")
                .setItems(options, (dialog, which) -> shareCsv(which == 1))
                .show();
    }

    private void shareCsv(boolean otherNotices) {
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

    private void pickImportCsv() {
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

    private void importCsv(Uri uri) {
        dbExecutor.execute(() -> {
            try {
                String csv = readText(uri);
                int imported = store.importCsv(csv);
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, "Imported " + imported + itemCountLabel(imported) + ".", Toast.LENGTH_SHORT).show();
                    refreshUI();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(SettingsActivity.this, "Could not import this file.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String readText(Uri uri) throws Exception {
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

    private String itemCountLabel(int count) {
        return count == 1 ? " item" : " items";
    }

    private void showRetentionOptions() {
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
                            refreshUI();
                            dialog.dismiss();
                            Toast.makeText(SettingsActivity.this, retentionSummary(days), Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String retentionSummary(int days) {
        if (days == 7) return "7 days";
        if (days == 30) return "30 days";
        if (days == 90) return "90 days";
        return "Keep forever";
    }

    private int daysForPosition(int position) {
        if (position == 1) return 7;
        if (position == 2) return 30;
        if (position == 3) return 90;
        return 0;
    }

    private int positionForDays(int days) {
        if (days == 7) return 1;
        if (days == 30) return 2;
        if (days == 90) return 3;
        return 0;
    }

    private void clearHiddenRules() {
        startActivity(new Intent(this, HiddenRulesActivity.class));
    }

    private void showCleanupTools() {
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

    private void handleCleanupAction(int which) {
        if (which == 0) {
            dbExecutor.execute(() -> {
                int removed = store.deleteWhatsAppNoise();
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, "Removed " + removed + " WhatsApp noise items.", Toast.LENGTH_SHORT).show();
                    refreshUI();
                });
            });
        } else if (which == 1) {
            confirmCleanup("Clear Other notices?", "This removes all saved non-WhatsApp notifications.", () -> {
                dbExecutor.execute(() -> {
                    store.clearMessages(true);
                    runOnUiThread(() -> {
                        Toast.makeText(SettingsActivity.this, "Other notices cleared.", Toast.LENGTH_SHORT).show();
                        refreshUI();
                    });
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

    private void confirmDeleteOlderThan(int days) {
        confirmCleanup("Delete old notices?", "This removes saved notifications older than " + days + " days.", () -> {
            dbExecutor.execute(() -> {
                int removed = store.deleteOlderThanDays(days);
                runOnUiThread(() -> {
                    Toast.makeText(SettingsActivity.this, "Removed " + removed + " old notices.", Toast.LENGTH_SHORT).show();
                    refreshUI();
                });
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    private void confirmCleanup(String title, String message, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Delete", (dialog, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleLockOnClose() {
        boolean next = !AppLock.lockOnClose(this);
        AppLock.setLockOnClose(this, next);
        Toast.makeText(this, next ? "PIN required when WhatsThat closes." : "PIN required after app restart only.", Toast.LENGTH_SHORT).show();
        refreshUI();
    }

    private void toggleBiometric() {
        if (!AppLock.isEnabled(this)) {
            Toast.makeText(this, "Set an app lock PIN first.", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Toast.makeText(this, "Biometric unlock needs Android 9 or newer.", Toast.LENGTH_SHORT).show();
            refreshUI();
            return;
        }
        boolean next = !AppLock.biometricEnabled(this);
        AppLock.setBiometricEnabled(this, next);
        Toast.makeText(this, next ? "Biometric unlock enabled." : "Biometric unlock disabled.", Toast.LENGTH_SHORT).show();
        refreshUI();
    }

    private void openLockSetup() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, AppLock.isEnabled(this) ? LockActivity.MODE_CHANGE : LockActivity.MODE_SET);
        startActivity(intent);
    }

    private void openLockDisable() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, LockActivity.MODE_DISABLE);
        startActivity(intent);
    }

    private void openDecoySetup() {
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra(LockActivity.MODE, AppLock.hasDecoyPin(this) ? LockActivity.MODE_CHANGE : LockActivity.MODE_SET);
        intent.putExtra("is_decoy", true);
        startActivity(intent);
    }

    private void disableDecoyPin() {
        AppLock.disableDecoy(this);
        Toast.makeText(this, "Decoy PIN disabled.", Toast.LENGTH_SHORT).show();
        refreshUI();
    }

    private boolean captureOther() {
        return prefs().getBoolean(PREF_CAPTURE_OTHER, false);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.ios_ink));
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private TextView section(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.ios_muted));
        view.setTextSize(13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setPadding(dp(6), dp(18), dp(6), dp(8));
        return view;
    }

    private TextView copy(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.brand_muted));
        view.setTextSize(14);
        view.setLineSpacing(dp(4), 1.0f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    private TextView footer(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(getColor(R.color.ios_muted));
        view.setTextSize(12);
        view.setLineSpacing(dp(3), 1.0f);
        view.setPadding(dp(6), dp(6), dp(6), dp(14));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
