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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.content.res.ColorStateList;

public class MainActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_RETENTION_DAYS = "retention_days";
    private static final String PREF_ONBOARDED = "onboarded";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PREF_SHOW_OTHER = "show_other_notices";
    private static final String PREF_FILTER_SYSTEM = "filter_system_generated";

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    MessageStore store;
    ListView list;
    TextView countText;
    TextView emptyText;
    TextView latestText;
    TextView statusText;
    TextView privacyText;
    TextView topViewTitle;
    TextView topViewDetail;
    EditText searchBox;
    Button open;
    Button modeBtn;
    Button filterBtn;
    Button deleteSelectedBtn;
    Button readSelectedBtn;
    Button hideSelectedBtn;
    Button clearSelectionBtn;
    Button blockedRulesBtn;
    View settingsBtn;
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
        store = MessageStore.getInstance(this);
        list = findViewById(R.id.listView);
        countText = findViewById(R.id.countText);
        emptyText = findViewById(R.id.emptyText);
        latestText = findViewById(R.id.latestText);
        statusText = findViewById(R.id.statusText);
        privacyText = findViewById(R.id.privacyText);
        topViewTitle = findViewById(R.id.topViewTitle);
        topViewDetail = findViewById(R.id.topViewDetail);
        searchBox = findViewById(R.id.searchBox);
        open = findViewById(R.id.openBtn);
        modeBtn = findViewById(R.id.modeBtn);
        filterBtn = findViewById(R.id.filterBtn);
        selectionRow = findViewById(R.id.selectionRow);
        deleteSelectedBtn = findViewById(R.id.deleteSelectedBtn);
        readSelectedBtn = findViewById(R.id.readSelectedBtn);
        hideSelectedBtn = findViewById(R.id.hideSelectedBtn);
        clearSelectionBtn = findViewById(R.id.clearSelectionBtn);
        blockedRulesBtn = findViewById(R.id.blockedRulesBtn);
        View whatsappBtn = findViewById(R.id.whatsappBtn);
        View statusSaverBtn = findViewById(R.id.statusSaverBtn);
        settingsBtn = findViewById(R.id.settingsBtn);
        accessBanner = findViewById(R.id.accessBanner);
        View clear = findViewById(R.id.clearBtn);

        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        modeBtn.setOnClickListener(v -> showInboxMode(!showingOtherNotices()));
        filterBtn.setOnClickListener(v -> toggleSystemFilter());
        blockedRulesBtn.setOnClickListener(v -> startActivity(new Intent(this, HiddenRulesActivity.class)));
        whatsappBtn.setOnClickListener(v -> showWhatsAppInbox());
        statusSaverBtn.setOnClickListener(v -> startActivity(new Intent(this, StatusSaverActivity.class)));
        clear.setOnClickListener(v -> confirmClearAll());
        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
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
            dbExecutor.execute(() -> store.markMessageRead(msg.id));
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

        dbExecutor.execute(() -> {
            applyRetention();
            store.deleteWhatsAppNoise();
            store.deleteHiddenByRules();

            final List<SavedMessage> rows = store.getRecentStructured(otherMode);
            final String q = searchBox.getText().toString().toLowerCase(Locale.getDefault());
            final List<SavedMessage> matching = new ArrayList<>();
            int hiddenSystemCountVal = 0;
            for (SavedMessage m : rows) {
                if (filteringSystemGenerated() && isSystemGenerated(m)) {
                    hiddenSystemCountVal++;
                    continue;
                }
                String v = (m.sender + " " + m.body + " " + m.time).toLowerCase(Locale.getDefault());
                boolean senderMatches = activeSender == null || activeSender.equals(m.sender);
                if (senderMatches && (q.isEmpty() || v.contains(q))) matching.add(m);
            }
            final List<SavedMessage> filtered = activeSender == null ? groupConversations(matching) : collapseRepeatedMessages(matching);
            final int hiddenSystemCount = hiddenSystemCountVal;

            runOnUiThread(() -> {
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
                modeBtn.setText("Switch");
                modeBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                updateTopStatusCard(otherMode);
                updateFilterButton();
                updateBlockedRulesButton();
                emptyText.setText(emptyModeText(otherMode));
                emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
                updateSelectionActions();
                updateBottomNavigationHighlights(otherMode);
                setListAdapter(filtered);
                restoreListPositionIfNeeded(filtered.size());
            });
        });
    }

    void updateBottomNavigationHighlights(boolean otherMode) {
        Button whatsappBtn = findViewById(R.id.whatsappBtn);
        if (whatsappBtn != null) {
            int activeColor = getResources().getColor(otherMode ? R.color.ios_muted : R.color.ios_blue);
            whatsappBtn.setTextColor(activeColor);
            whatsappBtn.setCompoundDrawableTintList(ColorStateList.valueOf(activeColor));
        }
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

    void applyRetention() {
        int days = prefs().getInt(PREF_RETENTION_DAYS, 0);
        if (days > 0) store.deleteOlderThanDays(days);
    }

    void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle(showingOtherNotices() ? "Clear other notices?" : "Clear WhatsApp messages?")
                .setMessage("This removes the saved items in the current inbox only.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        store.clearMessages(showingOtherNotices());
                        runOnUiThread(() -> {
                            activeSender = null;
                            load();
                        });
                    });
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
        setListAdapter(visibleRows);
    }

    void clearSelection() {
        selectedKeys.clear();
        updateSelectionActions();
        setListAdapter(visibleRows);
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
        dbExecutor.execute(() -> {
            int deleted = 0;
            for (SavedMessage msg : selectedMessages()) {
                deleted += store.deleteConversation(msg.packageName, msg.sender);
            }
            final int finalDeleted = deleted;
            runOnUiThread(() -> {
                selectedKeys.clear();
                activeSender = null;
                load();
                Toast.makeText(MainActivity.this, "Deleted " + finalDeleted + itemCountLabel(finalDeleted) + ".", Toast.LENGTH_SHORT).show();
            });
        });
    }

    void markSelectedRead() {
        dbExecutor.execute(() -> {
            int updated = 0;
            for (SavedMessage msg : selectedMessages()) {
                updated += store.markConversationRead(msg.packageName, msg.sender);
            }
            final int finalUpdated = updated;
            runOnUiThread(() -> {
                selectedKeys.clear();
                load();
                Toast.makeText(MainActivity.this, "Marked " + finalUpdated + itemCountLabel(finalUpdated) + " read.", Toast.LENGTH_SHORT).show();
            });
        });
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
        dbExecutor.execute(() -> {
            int removed = 0;
            for (SavedMessage msg : selectedMessages()) {
                NotificationRules.hideSimilar(MainActivity.this, msg.packageName, msg.sender, msg.body);
                removed += store.deleteSimilar(msg.packageName, msg.sender, msg.body);
            }
            final int finalRemoved = removed;
            runOnUiThread(() -> {
                selectedKeys.clear();
                load();
                Toast.makeText(MainActivity.this, "Hidden selected pattern" + (finalRemoved == 1 ? "." : "s."), Toast.LENGTH_SHORT).show();
            });
        });
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
            dbExecutor.execute(() -> {
                store.deleteMessage(msg.id);
                runOnUiThread(this::load);
            });
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
                    dbExecutor.execute(() -> {
                        store.deleteSender(msg.sender, otherMode);
                        runOnUiThread(() -> {
                            activeSender = null;
                            load();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmHideSimilar(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Hide messages like this?")
                .setMessage("Future notifications with the same app, sender, and message text will not be saved. Existing matching rows will be removed.")
                .setPositiveButton("Hide", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        NotificationRules.hideSimilar(MainActivity.this, msg.packageName, msg.sender, msg.body);
                        int removed = store.deleteSimilar(msg.packageName, msg.sender, msg.body);
                        runOnUiThread(() -> {
                            activeSender = null;
                            load();
                            Toast.makeText(MainActivity.this, "Hidden rule saved. Removed " + removed + " items.", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmIgnoreSystemLikeThis(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Ignore system notices like this?")
                .setMessage("Future matching system notices will not be saved, and existing matching copies will be removed.")
                .setPositiveButton("Ignore", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        NotificationRules.hideSimilar(MainActivity.this, msg.packageName, msg.sender, msg.body);
                        int removed = store.deleteSimilar(msg.packageName, msg.sender, msg.body);
                        runOnUiThread(() -> {
                            activeSender = null;
                            load();
                            Toast.makeText(MainActivity.this, "Ignored system notice. Removed " + removed + " items.", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteApp(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Delete all from this app?")
                .setMessage("Remove saved notices from " + AppLabels.label(this, msg.packageName) + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        store.deletePackage(msg.packageName);
                        runOnUiThread(() -> {
                            activeSender = null;
                            load();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
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

    void showWhatsAppInbox() {
        prefs().edit().putBoolean(PREF_SHOW_OTHER, false).apply();
        activeSender = null;
        selectedKeys.clear();
        searchBox.setText("");
        Toast.makeText(this, "Showing WhatsApp inbox.", Toast.LENGTH_SHORT).show();
        load();
    }

    void toggleSystemFilter() {
        boolean next = !filteringSystemGenerated();
        prefs().edit().putBoolean(PREF_FILTER_SYSTEM, next).apply();
        selectedKeys.clear();
        activeSender = null;
        Toast.makeText(this, next ? "Clean view hides system notices." : "Showing all saved notices.", Toast.LENGTH_SHORT).show();
        load();
    }

    boolean showingOtherNotices() {
        return prefs().getBoolean(PREF_SHOW_OTHER, false);
    }

    boolean filteringSystemGenerated() {
        return prefs().getBoolean(PREF_FILTER_SYSTEM, true);
    }

    boolean captureOtherNotices() {
        return prefs().getBoolean(PREF_CAPTURE_OTHER, false);
    }

    void updateFilterButton() {
        if (filterBtn == null) return;
        boolean active = filteringSystemGenerated();
        filterBtn.setText(active ? "Clean On" : "All notices");
        
        int textColor = active ? android.graphics.Color.WHITE : getResources().getColor(R.color.ios_muted);
        int tintColor = active ? getResources().getColor(R.color.ios_blue) : getResources().getColor(R.color.ios_border);
        
        filterBtn.setTextColor(textColor);
        filterBtn.setBackgroundTintList(ColorStateList.valueOf(tintColor));
        filterBtn.setBackgroundResource(R.drawable.bg_ios_chip);
        
        filterBtn.setContentDescription(active ? "Clean view is hiding system notices" : "All notices are visible");
        if (privacyText != null) {
            privacyText.setText(active
                    ? "Clean view hides system notices. Long-press a row for cleanup actions."
                    : "All notices are visible, including system notices. Long-press a row for cleanup actions.");
        }
    }

    void updateTopStatusCard(boolean otherMode) {
        if (topViewTitle == null || topViewDetail == null) return;
        topViewTitle.setText(otherMode ? "Other notices" : "WhatsApp");
        if (filteringSystemGenerated()) {
            topViewDetail.setText("Clean view is hiding system notices");
        } else {
            topViewDetail.setText("All saved notices are visible");
        }
    }

    void updateBlockedRulesButton() {
        if (blockedRulesBtn == null) return;
        int count = NotificationRules.count(this);
        blockedRulesBtn.setText(count == 0 ? "Blocked" : "Blocked (" + count + ")");
        blockedRulesBtn.setBackgroundResource(R.drawable.bg_ios_chip);
        blockedRulesBtn.setTextColor(getResources().getColor(R.color.ios_blue));
        blockedRulesBtn.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.ios_border)));
        blockedRulesBtn.setContentDescription(count == 0 ? "Blocked notices" : count + " blocked notice rules");
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
        return "Clean view hiding " + hiddenCount + (hiddenCount == 1 ? " system notice." : " system notices.") + suffix;
    }

    String keyPart(String value) {
        return value == null ? "" : value;
    }

    String emptyModeText(boolean otherMode) {
        if (filteringSystemGenerated()) return otherMode
                ? "No human-to-human notices match this view.\nTap Clean view to show system notifications too."
                : "No human-to-human WhatsApp messages match this view.\nTap Clean view to show system notifications too.";
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
        if (body.equals("charging") || body.startsWith("charging ")) return true;
        if (body.equals("downloading") || body.startsWith("downloading ")) return true;
        if (body.equals("download complete") || body.equals("download completed")) return true;
        if (body.contains("download in progress")) return true;
        if (body.contains("running in the background")) return true;
        return body.equals("new message") || body.equals("new messages");
    }

    String normalizeForFilter(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }

    void setListAdapter(List<SavedMessage> items) {
        SwipeItemLayout.OnSwipeActionListener swipeListener = new SwipeItemLayout.OnSwipeActionListener() {
            @Override
            public void onDelete(SavedMessage msg) {
                deleteMessageConversation(msg);
            }

            @Override
            public void onHide(SavedMessage msg) {
                hideMessageConversation(msg);
            }

            @Override
            public void onToggleRead(SavedMessage msg) {
                toggleMessageRead(msg);
            }
        };
        list.setAdapter(new MessageAdapter(this, items, selectedKeys, systemKeys, swipeListener));
    }

    void deleteMessageConversation(SavedMessage msg) {
        dbExecutor.execute(() -> {
            store.deleteConversation(msg.packageName, msg.sender);
            runOnUiThread(() -> {
                load();
                Toast.makeText(this, "Deleted conversation with " + msg.sender + ".", Toast.LENGTH_SHORT).show();
            });
        });
    }

    void hideMessageConversation(SavedMessage msg) {
        dbExecutor.execute(() -> {
            NotificationRules.hideSimilar(this, msg.packageName, msg.sender, msg.body);
            store.deleteSimilar(msg.packageName, msg.sender, msg.body);
            runOnUiThread(() -> {
                load();
                Toast.makeText(this, "Hidden conversation pattern.", Toast.LENGTH_SHORT).show();
            });
        });
    }

    void toggleMessageRead(SavedMessage msg) {
        dbExecutor.execute(() -> {
            if (msg.unreadCount > 0 || !msg.read) {
                store.markConversationRead(msg.packageName, msg.sender);
            } else {
                store.markConversationUnread(msg.packageName, msg.sender);
            }
            runOnUiThread(() -> {
                load();
            });
        });
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
