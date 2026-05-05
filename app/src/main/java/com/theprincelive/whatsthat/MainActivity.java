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
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_RETENTION_DAYS = "retention_days";
    private static final String PREF_ONBOARDED = "onboarded";

    MessageStore store;
    ListView list;
    TextView countText;
    TextView emptyText;
    TextView latestText;
    TextView statusText;
    EditText searchBox;
    Button open;
    Button filterBtn;
    Button exportBtn;
    Spinner retentionSpinner;
    LinearLayout accessBanner;
    String activeSender;

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
        filterBtn = findViewById(R.id.filterBtn);
        exportBtn = findViewById(R.id.exportBtn);
        retentionSpinner = findViewById(R.id.retentionSpinner);
        accessBanner = findViewById(R.id.accessBanner);
        filterBtn.setSingleLine(true);
        Button clear = findViewById(R.id.clearBtn);

        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        clear.setOnClickListener(v -> confirmClearAll());
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
            Intent intent = new Intent(this, MessageDetailActivity.class);
            intent.putExtra("id", msg.id);
            intent.putExtra("sender", msg.sender);
            intent.putExtra("body", msg.body);
            intent.putExtra("time", msg.time);
            startActivity(intent);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            activeSender = msg.sender;
            searchBox.setText("");
            load();
            Toast.makeText(this, "Showing " + msg.sender, Toast.LENGTH_SHORT).show();
            return true;
        });
        setupRetention();
        load();
        showOnboardingIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    boolean enabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    void load() {
        boolean hasAccess = enabled();
        open.setVisibility(hasAccess ? View.GONE : View.VISIBLE);
        accessBanner.setVisibility(hasAccess && activeSender == null ? View.GONE : View.VISIBLE);
        statusText.setText(hasAccess ? "WhatsApp notification inbox" : "Enable access to start capturing alerts");
        applyRetention();

        List<SavedMessage> rows = store.getRecentStructured();
        String q = searchBox.getText().toString().toLowerCase(Locale.getDefault());
        List<SavedMessage> filtered = new ArrayList<>();
        for (SavedMessage m : rows) {
            String v = (m.sender + " " + m.body + " " + m.time).toLowerCase(Locale.getDefault());
            boolean senderMatches = activeSender == null || activeSender.equals(m.sender);
            if (senderMatches && (q.isEmpty() || v.contains(q))) filtered.add(m);
        }

        int count = filtered.size();
        countText.setText(count + (count == 1 ? " chat" : " chats"));
        if (!hasAccess) {
            latestText.setText("Turn on Notification Access so new WhatsApp alerts appear here.");
        } else if (activeSender != null) {
            latestText.setText("Showing saved alerts from " + activeSender + ".");
        } else {
            latestText.setText(count == 0 ? "New WhatsApp alerts will appear here." : "Latest: " + filtered.get(0).time);
        }
        filterBtn.setText(activeSender == null ? "All" : "Clear filter");
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new MessageAdapter(this, filtered));
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
                .setTitle("Clear all saved messages?")
                .setMessage("This removes the local notification history stored by WhatsThat.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    store.clearMessages();
                    activeSender = null;
                    load();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void shareCsv() {
        String csv = store.exportCsv();
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
                .setMessage("WhatsThat saves new WhatsApp and WhatsApp Business notifications after you grant Notification Access. It cannot read old chats or messages that never appeared as notifications.")
                .setPositiveButton("Enable access", (dialog, which) -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
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
}
