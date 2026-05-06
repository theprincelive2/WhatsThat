package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_RETENTION_DAYS = "retention_days";
    private static final String PREF_ONBOARDED = "onboarded";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PREF_SHOW_OTHER = "show_other_notices";

    MessageStore store;
    ListView list;
    TextView countText;
    TextView emptyText;
    TextView latestText;
    TextView statusText;
    EditText searchBox;
    Button open;
    Button modeBtn;
    Button filterBtn;
    Button exportBtn;
    Button settingsBtn;
    Spinner retentionSpinner;
    LinearLayout accessBanner;
    String activeSender;
    boolean lockStarted;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        store = new MessageStore(this);
        list = findViewById(R.id.listView);
        countText = findViewById(R.id.countText);
        emptyText = findViewById(R.id.emptyText);
        latestText = findViewById(R.id.latestText);
        statusText = findViewById(R.id.statusText);
        searchBox = findViewById(R.id.searchBox);
        open = findViewById(R.id.openBtn);
        modeBtn = findViewById(R.id.modeBtn);
        filterBtn = findViewById(R.id.filterBtn);
        exportBtn = findViewById(R.id.exportBtn);
        settingsBtn = findViewById(R.id.settingsBtn);
        retentionSpinner = findViewById(R.id.retentionSpinner);
        accessBanner = findViewById(R.id.accessBanner);
        filterBtn.setSingleLine(true);
        Button clear = findViewById(R.id.clearBtn);

        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        modeBtn.setOnClickListener(v -> toggleInboxMode());
        clear.setOnClickListener(v -> confirmClearAll());
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        filterBtn.setOnClickListener(v -> {
            activeSender = null;
            load();
        });
        exportBtn.setOnClickListener(v -> shareCsv());
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { load(); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            if (activeSender == null && msg.messageCount > 1) {
                Intent intent = new Intent(this, ConversationActivity.class);
                intent.putExtra("packageName", msg.packageName);
                intent.putExtra("sender", msg.sender);
                startActivity(intent);
                return;
            }
            Intent intent = new Intent(this, MessageDetailActivity.class);
            intent.putExtra("id", msg.id);
            intent.putExtra("sender", msg.sender);
            intent.putExtra("body", msg.body);
            intent.putExtra("time", msg.time);
            startActivity(intent);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            showMessageActions(msg);
            return true;
        });
        setupRetention();
        requireUnlock();
        load();
        showOnboardingIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        requireUnlock();
        load();
    }

    void requireUnlock() {
        if (AppLock.isEnabled(this) && !AppLock.isUnlocked() && !lockStarted) {
            lockStarted = true;
            Intent intent = new Intent(this, LockActivity.class);
            intent.putExtra(LockActivity.MODE, LockActivity.MODE_UNLOCK);
            startActivity(intent);
        } else if (!AppLock.isEnabled(this) || AppLock.isUnlocked()) {
            lockStarted = false;
        }
    }

    boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    void load() {
        boolean hasAccess = enabled();
        boolean otherMode = showingOtherNotices();
        open.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        accessBanner.setVisibility(hasAccess && activeSender == null ? View.GONE : View.VISIBLE);
        statusText.setText(otherMode ? "Other notification inbox" : "WhatsApp notification inbox");
        applyRetention();
        store.deleteWhatsAppNoise();

        List<SavedMessage> rows = store.getRecentStructured(otherMode);
        String q = searchBox.getText().toString().toLowerCase(Locale.getDefault());
        List<SavedMessage> matching = new ArrayList<>();
        for (SavedMessage m : rows) {
            String v = (m.sender + " " + m.body + " " + m.time).toLowerCase(Locale.getDefault());
            boolean senderMatches = activeSender == null || activeSender.equals(m.sender);
            if (senderMatches && (q.isEmpty() || v.contains(q))) matching.add(m);
        }
        List<SavedMessage> filtered = activeSender == null ? groupConversations(matching) : matching;

        int count = filtered.size();
        countText.setText(count + countLabel(count, otherMode));
        if (!hasAccess) {
            latestText.setText("Turn on Notification Access so saved alerts can appear here.");
        } else if (otherMode && !captureOtherNotices()) {
            latestText.setText("Other notices are off. Tap Other notices to start capturing non-WhatsApp notifications.");
        } else if (activeSender != null) {
            latestText.setText("Showing saved alerts from " + activeSender + ".");
        } else {
            latestText.setText(count == 0 ? emptyModeText(otherMode) : "Latest: " + filtered.get(0).time);
        }
        modeBtn.setText(otherMode ? "Other notices" : "WhatsApp");
        filterBtn.setText(activeSender == null ? "All" : "Clear filter");
        emptyText.setText(emptyModeText(otherMode));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new MessageAdapter(this, filtered));
    }

    List<SavedMessage> groupConversations(List<SavedMessage> rows) {
        LinkedHashMap<String, ThreadSummary> summaries = new LinkedHashMap<>();
        for (SavedMessage row : rows) {
            String key = safe(row.packageName) + "\u001f" + safe(row.sender);
            ThreadSummary summary = summaries.get(key);
            if (summary == null) {
                summaries.put(key, new ThreadSummary(row));
            } else {
                summary.count++;
            }
        }

        ArrayList<SavedMessage> out = new ArrayList<>();
        for (Map.Entry<String, ThreadSummary> entry : summaries.entrySet()) {
            ThreadSummary summary = entry.getValue();
            SavedMessage latest = summary.latest;
            out.add(new SavedMessage(
                    latest.id,
                    latest.sender,
                    latest.body,
                    latest.time,
                    latest.shortTime,
                    latest.dateLabel,
                    latest.packageName,
                    latest.receivedAt,
                    summary.count
            ));
        }
        return out;
    }

    void setupRetention() {
        int days = prefs().getInt(PREF_RETENTION_DAYS, 0);
        retentionSpinner.setSelection(positionForDays(days));
        retentionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int selectedDays = daysForPosition(position);
                if (selectedDays != prefs().getInt(PREF_RETENTION_DAYS, 0)) {
                    prefs().edit().putInt(PREF_RETENTION_DAYS, selectedDays).apply();
                    applyRetention();
                    load();
                }
            }

            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    void applyRetention() {
        int days = prefs().getInt(PREF_RETENTION_DAYS, 0);
        if (days > 0) store.deleteOlderThanDays(days);
    }

    void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(showingOtherNotices() ? "Clear other notices?" : "Clear WhatsApp messages?")
                .setMessage("This removes the saved items in the current inbox only.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    store.clearMessages(showingOtherNotices());
                    activeSender = null;
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void showMessageActions(SavedMessage msg) {
        boolean otherMode = showingOtherNotices();
        ArrayList<String> actions = new ArrayList<>();
        actions.add("Filter by sender");
        actions.add("Delete this message");
        actions.add("Delete all from this sender");
        actions.add("Hide messages like this");
        if (otherMode) actions.add("Delete all from this app");

        new AlertDialog.Builder(this)
                .setTitle(msg.sender == null ? "Message actions" : msg.sender)
                .setItems(actions.toArray(new String[0]), (dialog, which) -> handleMessageAction(actions.get(which), msg, otherMode))
                .show();
    }

    void handleMessageAction(String action, SavedMessage msg, boolean otherMode) {
        if ("Filter by sender".equals(action)) {
            activeSender = msg.sender;
            searchBox.setText("");
            load();
            Toast.makeText(this, "Showing " + msg.sender, Toast.LENGTH_SHORT).show();
        } else if ("Delete this message".equals(action)) {
            store.deleteMessage(msg.id);
            load();
        } else if ("Delete all from this sender".equals(action)) {
            confirmDeleteSender(msg, otherMode);
        } else if ("Hide messages like this".equals(action)) {
            confirmHideSimilar(msg);
        } else if ("Delete all from this app".equals(action)) {
            confirmDeleteApp(msg);
        }
    }

    void confirmDeleteSender(SavedMessage msg, boolean otherMode) {
        new AlertDialog.Builder(this)
                .setTitle("Delete all from sender?")
                .setMessage("Remove saved items from " + safe(msg.sender) + " in this inbox.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteSender(msg.sender, otherMode);
                    activeSender = null;
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmHideSimilar(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Hide messages like this?")
                .setMessage("Future notifications with the same app, sender, and message text will not be saved. Existing matching rows will be removed.")
                .setPositiveButton("Hide", (dialog, which) -> {
                    NotificationRules.hideSimilar(this, msg.packageName, msg.sender, msg.body);
                    int removed = store.deleteSimilar(msg.packageName, msg.sender, msg.body);
                    activeSender = null;
                    load();
                    Toast.makeText(this, "Hidden rule saved. Removed " + removed + " items.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteApp(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Delete all from this app?")
                .setMessage("Remove saved notices from " + safe(msg.packageName) + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deletePackage(msg.packageName);
                    activeSender = null;
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void shareCsv() {
        String csv = store.exportCsv(showingOtherNotices());
        if (csv.trim().equals("sender,message,package,received_at")) {
            Toast.makeText(this, "No messages to export yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/csv");
        send.putExtra(Intent.EXTRA_SUBJECT, "WhatsThat message export");
        send.putExtra(Intent.EXTRA_TEXT, csv);
        startActivity(Intent.createChooser(send, "Export WhatsThat CSV"));
    }

    void showOnboardingIfNeeded() {
        if (prefs().getBoolean(PREF_ONBOARDED, false)) return;
        prefs().edit().putBoolean(PREF_ONBOARDED, true).apply();
        new AlertDialog.Builder(this)
                .setTitle("Set up WhatsThat")
                .setMessage("WhatsThat saves new WhatsApp and WhatsApp Business notifications after you grant Notification Access. Use the inbox chip to opt into other app notifications. It cannot read old chats or messages that never appeared as notifications.")
                .setPositiveButton("Enable access", (dialog, which) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    void toggleInboxMode() {
        boolean nextOtherMode = !showingOtherNotices();
        prefs().edit()
                .putBoolean(PREF_SHOW_OTHER, nextOtherMode)
                .putBoolean(PREF_CAPTURE_OTHER, nextOtherMode || captureOtherNotices())
                .apply();
        activeSender = null;
        searchBox.setText("");
        Toast.makeText(this, nextOtherMode ? "Other notifications will now be captured." : "Showing WhatsApp inbox.", Toast.LENGTH_SHORT).show();
        load();
    }

    boolean showingOtherNotices() {
        return prefs().getBoolean(PREF_SHOW_OTHER, false);
    }

    boolean captureOtherNotices() {
        return prefs().getBoolean(PREF_CAPTURE_OTHER, false);
    }

    String countLabel(int count, boolean otherMode) {
        if (otherMode) return count == 1 ? " notice" : " notices";
        return count == 1 ? " chat" : " chats";
    }

    String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unknown" : value;
    }

    String emptyModeText(boolean otherMode) {
        if (otherMode) return captureOtherNotices()
                ? "No other notices yet.\nNew non-WhatsApp notifications will appear here."
                : "Tap Other notices to start capturing non-WhatsApp notifications.";
        return "No WhatsApp messages yet.\nNew WhatsApp alerts will appear here like chats.";
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

    static class ThreadSummary {
        final SavedMessage latest;
        int count = 1;

        ThreadSummary(SavedMessage latest) {
            this.latest = latest;
        }
    }
}
