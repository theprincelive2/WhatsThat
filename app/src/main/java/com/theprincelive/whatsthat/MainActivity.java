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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_RETENTION_DAYS = "retention_days";
    private static final String PREF_ONBOARDED = "onboarded";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PREF_SHOW_OTHER = "show_other_notices";
    private static final String PREF_FILTER_SYSTEM = "filter_system_generated";

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
    Button deleteSelectedBtn;
    Button readSelectedBtn;
    Button hideSelectedBtn;
    Button clearSelectionBtn;
    ImageButton exportBtn;
    ImageButton settingsBtn;
    Spinner retentionSpinner;
    LinearLayout accessBanner;
    LinearLayout selectionRow;
    List<SavedMessage> visibleRows = new ArrayList<>();
    Set<String> selectedKeys = new HashSet<>();
    Set<String> systemKeys = new HashSet<>();
    String activeSender;
    boolean lockStarted;
    boolean restoreListPosition;
    int savedListPosition;
    int savedListTop;

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
        selectionRow = findViewById(R.id.selectionRow);
        deleteSelectedBtn = findViewById(R.id.deleteSelectedBtn);
        readSelectedBtn = findViewById(R.id.readSelectedBtn);
        hideSelectedBtn = findViewById(R.id.hideSelectedBtn);
        clearSelectionBtn = findViewById(R.id.clearSelectionBtn);
        ImageButton statusSaverBtn = findViewById(R.id.statusSaverBtn);
        exportBtn = findViewById(R.id.exportBtn);
        settingsBtn = findViewById(R.id.settingsBtn);
        retentionSpinner = findViewById(R.id.retentionSpinner);
        accessBanner = findViewById(R.id.accessBanner);
        ImageButton clear = findViewById(R.id.clearBtn);

        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        modeBtn.setOnClickListener(v -> showInboxMode(!showingOtherNotices()));
        filterBtn.setOnClickListener(v -> toggleSystemFilter());
        statusSaverBtn.setOnClickListener(v -> startActivity(new Intent(this, StatusSaverActivity.class)));
        clear.setOnClickListener(v -> confirmClearAll());
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        exportBtn.setOnClickListener(v -> shareCsv());
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelected());
        readSelectedBtn.setOnClickListener(v -> markSelectedRead());
        hideSelectedBtn.setOnClickListener(v -> confirmHideSelected());
        clearSelectionBtn.setOnClickListener(v -> clearSelection());
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                selectedKeys.clear();
                load();
            }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            if (!selectedKeys.isEmpty()) {
                toggleSelection(msg);
                return;
            }
            if (activeSender == null && msg.messageCount > 1) {
                rememberListPosition();
                Intent intent = new Intent(this, ConversationActivity.class);
                intent.putExtra("packageName", msg.packageName);
                intent.putExtra("sender", msg.sender);
                startActivity(intent);
                return;
            }
            rememberListPosition();
            store.markMessageRead(msg.id);
            Intent intent = new Intent(this, MessageDetailActivity.class);
            intent.putExtra("id", msg.id);
            intent.putExtra("sender", msg.sender);
            intent.putExtra("body", msg.body);
            intent.putExtra("time", msg.time);
            startActivity(intent);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            toggleSelection(msg);
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
        int hiddenSystemCount = 0;
        for (SavedMessage m : rows) {
            if (filteringSystemGenerated() && isSystemGenerated(m)) {
                hiddenSystemCount++;
                continue;
            }
            String v = (m.sender + " " + m.body + " " + m.time).toLowerCase(Locale.getDefault());
            boolean senderMatches = activeSender == null || activeSender.equals(m.sender);
            if (senderMatches && (q.isEmpty() || v.contains(q))) matching.add(m);
        }
        List<SavedMessage> filtered = activeSender == null ? groupConversations(matching) : collapseRepeatedMessages(matching);
        visibleRows.clear();
        visibleRows.addAll(filtered);
        updateSystemKeys(filtered);
        pruneSelection();

        int count = filtered.size();
        countText.setText(count + countLabel(count, otherMode));
        if (!hasAccess) {
            latestText.setText("Turn on Notification Access so saved alerts can appear here.");
        } else if (otherMode && !captureOtherNotices()) {
            latestText.setText("Other notices are off. Tap Other notices to start capturing non-WhatsApp notifications.");
        } else if (activeSender != null) {
            latestText.setText("Showing saved alerts from " + activeSender + ".");
        } else if (filteringSystemGenerated() && hiddenSystemCount > 0) {
            latestText.setText(filterSummary(hiddenSystemCount, count == 0 ? "" : " Latest: " + filtered.get(0).time));
        } else {
            latestText.setText(count == 0 ? emptyModeText(otherMode) : "Latest: " + filtered.get(0).time);
        }
        modeBtn.setText(otherMode ? "Other notices" : "WhatsApp");
        modeBtn.setCompoundDrawablesWithIntrinsicBounds(otherMode ? R.drawable.ic_bell : R.drawable.ic_chat, 0, 0, 0);
        updateFilterButton();
        emptyText.setText(emptyModeText(otherMode));
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        updateSelectionActions();
        list.setAdapter(new MessageAdapter(this, filtered, selectedKeys, systemKeys));
        restoreListPositionIfNeeded(filtered.size());
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
                if (!row.read) summary.unreadCount++;
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
                    summary.count,
                    summary.unreadCount,
                    summary.unreadCount == 0
            ));
        }
        return out;
    }

    List<SavedMessage> collapseRepeatedMessages(List<SavedMessage> rows) {
        ArrayList<SavedMessage> collapsed = new ArrayList<>();
        SavedMessage previous = null;
        int count = 0;
        for (SavedMessage row : rows) {
            if (previous != null && sameMessage(previous, row)) {
                count++;
            } else {
                addCollapsedMessage(collapsed, previous, count);
                previous = row;
                count = 1;
            }
        }
        addCollapsedMessage(collapsed, previous, count);
        return collapsed;
    }

    void addCollapsedMessage(List<SavedMessage> out, SavedMessage msg, int count) {
        if (msg == null) return;
        out.add(new SavedMessage(
                msg.id,
                msg.sender,
                msg.body,
                msg.time,
                msg.shortTime,
                msg.dateLabel,
                msg.packageName,
                msg.receivedAt,
                count,
                msg.unreadCount,
                msg.read
        ));
    }

    boolean sameMessage(SavedMessage a, SavedMessage b) {
        return keyPart(a.packageName).equals(keyPart(b.packageName))
                && keyPart(a.sender).equals(keyPart(b.sender))
                && keyPart(a.body).equals(keyPart(b.body));
    }

    void rememberListPosition() {
        savedListPosition = list.getFirstVisiblePosition();
        View top = list.getChildAt(0);
        savedListTop = top == null ? 0 : top.getTop() - list.getPaddingTop();
        restoreListPosition = true;
    }

    void restoreListPositionIfNeeded(int itemCount) {
        if (!restoreListPosition) return;
        int position = Math.min(savedListPosition, Math.max(0, itemCount - 1));
        list.post(() -> {
            list.setSelectionFromTop(position, savedListTop);
            restoreListPosition = false;
        });
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

    void toggleSelection(SavedMessage msg) {
        String key = selectionKey(msg);
        if (selectedKeys.contains(key)) {
            selectedKeys.remove(key);
        } else {
            selectedKeys.add(key);
        }
        updateSelectionActions();
        list.setAdapter(new MessageAdapter(this, visibleRows, selectedKeys, systemKeys));
    }

    void clearSelection() {
        selectedKeys.clear();
        updateSelectionActions();
        list.setAdapter(new MessageAdapter(this, visibleRows, selectedKeys, systemKeys));
    }

    void pruneSelection() {
        HashSet<String> visibleKeys = new HashSet<>();
        for (SavedMessage msg : visibleRows) visibleKeys.add(selectionKey(msg));
        selectedKeys.retainAll(visibleKeys);
    }

    void updateSystemKeys(List<SavedMessage> rows) {
        systemKeys.clear();
        if (filteringSystemGenerated()) return;
        for (SavedMessage msg : rows) {
            if (isSystemGenerated(msg)) systemKeys.add(selectionKey(msg));
        }
    }

    void updateSelectionActions() {
        if (selectionRow == null || deleteSelectedBtn == null || readSelectedBtn == null || hideSelectedBtn == null) return;
        int count = selectedKeys.size();
        selectionRow.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        deleteSelectedBtn.setText(count == 0 ? "Delete" : "Delete (" + count + ")");
        readSelectedBtn.setText(count == 0 ? "Read" : "Read (" + count + ")");
        hideSelectedBtn.setText(count == 0 ? "Hide" : "Hide (" + count + ")");
    }

    void confirmDeleteSelected() {
        int count = selectedKeys.size();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete selected?")
                .setMessage("This removes selected saved items from the current inbox.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void deleteSelected() {
        int deleted = 0;
        for (SavedMessage msg : selectedMessages()) {
            deleted += store.deleteConversation(msg.packageName, msg.sender);
        }
        selectedKeys.clear();
        activeSender = null;
        load();
        Toast.makeText(this, "Deleted " + deleted + itemCountLabel(deleted) + ".", Toast.LENGTH_SHORT).show();
    }

    void markSelectedRead() {
        int updated = 0;
        for (SavedMessage msg : selectedMessages()) {
            updated += store.markConversationRead(msg.packageName, msg.sender);
        }
        selectedKeys.clear();
        load();
        Toast.makeText(this, "Marked " + updated + itemCountLabel(updated) + " read.", Toast.LENGTH_SHORT).show();
    }

    void confirmHideSelected() {
        int count = selectedKeys.size();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Hide selected patterns?")
                .setMessage("Future matching notices will not be saved, and matching saved copies will be removed.")
                .setPositiveButton("Hide", (dialog, which) -> hideSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void hideSelected() {
        int removed = 0;
        for (SavedMessage msg : selectedMessages()) {
            NotificationRules.hideSimilar(this, msg.packageName, msg.sender, msg.body);
            removed += store.deleteSimilar(msg.packageName, msg.sender, msg.body);
        }
        selectedKeys.clear();
        load();
        Toast.makeText(this, "Hidden selected pattern" + (removed == 1 ? "." : "s."), Toast.LENGTH_SHORT).show();
    }

    List<SavedMessage> selectedMessages() {
        ArrayList<SavedMessage> selected = new ArrayList<>();
        for (SavedMessage msg : visibleRows) {
            if (selectedKeys.contains(selectionKey(msg))) selected.add(msg);
        }
        return selected;
    }

    void showMessageActions(SavedMessage msg) {
        boolean otherMode = showingOtherNotices();
        ArrayList<String> actions = new ArrayList<>();
        if (isSystemGenerated(msg)) actions.add("Ignore system notices like this");
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
        if ("Ignore system notices like this".equals(action)) {
            confirmIgnoreSystemLikeThis(msg);
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

    void confirmIgnoreSystemLikeThis(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Ignore system notices like this?")
                .setMessage("Future matching system notices will not be saved, and existing matching copies will be removed.")
                .setPositiveButton("Ignore", (dialog, which) -> {
                    NotificationRules.hideSimilar(this, msg.packageName, msg.sender, msg.body);
                    int removed = store.deleteSimilar(msg.packageName, msg.sender, msg.body);
                    activeSender = null;
                    load();
                    Toast.makeText(this, "Ignored system notice. Removed " + removed + " items.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteApp(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Delete all from this app?")
                .setMessage("Remove saved notices from " + AppLabels.label(this, msg.packageName) + ".")
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
        if (csv.trim().equals("sender,message,app,package,received_at")) {
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

    void showInboxMode(boolean nextOtherMode) {
        if (showingOtherNotices() == nextOtherMode) return;
        prefs().edit()
                .putBoolean(PREF_SHOW_OTHER, nextOtherMode)
                .putBoolean(PREF_CAPTURE_OTHER, nextOtherMode || captureOtherNotices())
                .apply();
        activeSender = null;
        searchBox.setText("");
        selectedKeys.clear();
        Toast.makeText(this, nextOtherMode ? "Other notifications will now be captured." : "Showing WhatsApp inbox.", Toast.LENGTH_SHORT).show();
        load();
    }

    void toggleSystemFilter() {
        boolean next = !filteringSystemGenerated();
        prefs().edit().putBoolean(PREF_FILTER_SYSTEM, next).apply();
        selectedKeys.clear();
        activeSender = null;
        Toast.makeText(this, next ? "System notifications hidden." : "Showing all saved notifications.", Toast.LENGTH_SHORT).show();
        load();
    }

    boolean showingOtherNotices() {
        return prefs().getBoolean(PREF_SHOW_OTHER, false);
    }

    boolean filteringSystemGenerated() {
        return prefs().getBoolean(PREF_FILTER_SYSTEM, false);
    }

    boolean captureOtherNotices() {
        return prefs().getBoolean(PREF_CAPTURE_OTHER, false);
    }

    void updateFilterButton() {
        if (filterBtn == null) return;
        boolean active = filteringSystemGenerated();
        filterBtn.setText(active ? "Filter on" : "Filter");
        filterBtn.setTextColor(active ? android.graphics.Color.WHITE : getResources().getColor(R.color.brand_text));
        filterBtn.setBackgroundResource(active ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
    }

    String countLabel(int count, boolean otherMode) {
        if (otherMode) return count == 1 ? " notice" : " notices";
        return count == 1 ? " chat" : " chats";
    }

    String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unknown" : value;
    }

    String selectionKey(SavedMessage msg) {
        return keyPart(msg.packageName) + "\u001f" + keyPart(msg.sender);
    }

    String itemCountLabel(int count) {
        return count == 1 ? " item" : " items";
    }

    String filterSummary(int hiddenCount, String suffix) {
        return "Filter hiding " + hiddenCount + (hiddenCount == 1 ? " system notice." : " system notices.") + suffix;
    }

    String keyPart(String value) {
        return value == null ? "" : value;
    }

    String emptyModeText(boolean otherMode) {
        if (filteringSystemGenerated()) return otherMode
                ? "No human-to-human notices match this view.\nTap Filter to show system notifications too."
                : "No human-to-human WhatsApp messages match this view.\nTap Filter to show system notifications too.";
        if (otherMode) return captureOtherNotices()
                ? "No other notices yet.\nNew non-WhatsApp notifications will appear here."
                : "Tap Other notices to start capturing non-WhatsApp notifications.";
        return "No WhatsApp messages yet.\nNew WhatsApp alerts will appear here like chats.";
    }

    boolean isSystemGenerated(SavedMessage msg) {
        String sender = normalizeForFilter(msg.sender);
        String body = normalizeForFilter(msg.body);
        String pkg = normalizeForFilter(msg.packageName);

        if (isSystemBody(body)) return true;
        if ((pkg.equals("com.whatsapp") || pkg.equals("com.whatsapp.w4b")) && sender.equals("whatsapp")) return true;
        if (pkg.startsWith("android") || pkg.contains("systemui") || pkg.contains("launcher")) return true;
        if (pkg.contains("packageinstaller") || pkg.contains("permissioncontroller")) return true;
        if (pkg.contains("download") || pkg.contains("updater") || pkg.contains("settings")) return true;
        if (pkg.equals("com.google.android.gms") || pkg.equals("com.android.vending")) return true;
        return false;
    }

    boolean isSystemBody(String body) {
        if (body.isEmpty()) return true;
        if (body.equals("checking for new messages")) return true;
        if (body.matches("\\d+ new messages?")) return true;
        if (body.matches("\\d+ messages? from \\d+ chats?")) return true;
        if (body.contains("checking for new messages")) return true;
        if (body.contains("backup in progress") || body.contains("backup complete")) return true;
        if (body.contains("whatsapp web is currently active")) return true;
        if (body.contains("you may have new messages")) return true;
        if (body.contains("new messages from")) return true;
        return body.equals("new message") || body.equals("new messages");
    }

    String normalizeForFilter(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
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
        int unreadCount;

        ThreadSummary(SavedMessage latest) {
            this.latest = latest;
            this.unreadCount = latest.read ? 0 : 1;
        }
    }
}
