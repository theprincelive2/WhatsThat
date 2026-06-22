package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConversationActivity extends Activity {
    String packageName;
    String sender;
    MessageStore store;
    LinearLayout messages;
    LinearLayout selectionRow;
    TextView subtitle;
    Button deleteSelectedBtn;
    Button hideSelectedBtn;
    Button clearSelectionBtn;
    ScrollView scroll;
    Set<Long> selectedIds = new HashSet<>();
    Map<Long, List<Long>> groupedMessageIds = new HashMap<>();
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("packageName");
        sender = getIntent().getStringExtra("sender");
        store = MessageStore.getInstance(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.ios_bg));

        LinearLayout headerContainer = new LinearLayout(this);
        headerContainer.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(18), dp(14), dp(14));
        header.setBackgroundColor(getColor(R.color.ios_header));

        header.addView(BackNav.button(this, false), new LinearLayout.LayoutParams(dp(76), dp(42)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(6), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(safe(sender));
        title.setTextColor(getColor(R.color.ios_ink));
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setSingleLine(true);
        titleBlock.addView(title);

        subtitle = new TextView(this);
        subtitle.setTextColor(getColor(R.color.ios_muted));
        subtitle.setTextSize(12);
        subtitle.setSingleLine(true);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        Button actions = new Button(this);
        actions.setText("Actions");
        actions.setAllCaps(false);
        actions.setTextSize(17);
        actions.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        actions.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        actions.setTextColor(getColor(R.color.ios_blue));
        actions.setBackgroundColor(Color.TRANSPARENT);
        actions.setPadding(0, 0, dp(8), 0);
        actions.setOnClickListener(v -> showConversationActions());
        header.addView(actions, new LinearLayout.LayoutParams(dp(84), dp(42)));
        
        headerContainer.addView(header);
        
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(getColor(R.color.ios_border));
        headerContainer.addView(headerDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        
        root.addView(headerContainer);

        selectionRow = new LinearLayout(this);
        selectionRow.setOrientation(LinearLayout.VERTICAL);

        View selectionDivider = new View(this);
        selectionDivider.setBackgroundColor(getColor(R.color.ios_border));
        selectionRow.addView(selectionDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout selectionBtnRow = new LinearLayout(this);
        selectionBtnRow.setOrientation(LinearLayout.HORIZONTAL);
        selectionBtnRow.setPadding(dp(14), dp(8), dp(14), dp(8));
        selectionBtnRow.setBackgroundColor(getColor(R.color.ios_header));
        selectionBtnRow.setGravity(Gravity.CENTER_VERTICAL);

        deleteSelectedBtn = selectionBarButton("Delete", getColor(R.color.ios_red));
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelected());
        selectionBtnRow.addView(deleteSelectedBtn, new LinearLayout.LayoutParams(0, dp(44), 1));

        hideSelectedBtn = selectionBarButton("Hide", getColor(R.color.ios_blue));
        hideSelectedBtn.setOnClickListener(v -> confirmHideSelected());
        LinearLayout.LayoutParams hideParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        hideParams.setMargins(dp(8), 0, 0, 0);
        selectionBtnRow.addView(hideSelectedBtn, hideParams);

        clearSelectionBtn = selectionBarButton("Clear", getColor(R.color.ios_muted));
        clearSelectionBtn.setOnClickListener(v -> clearSelection());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        selectionBtnRow.addView(clearSelectionBtn, clearParams);
        
        selectionRow.addView(selectionBtnRow);
        root.addView(selectionRow);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        updateSelectionActions();
        loadMessages();
    }

    void loadMessages() {
        dbExecutor.execute(() -> {
            store.markConversationRead(packageName, sender);
            final List<SavedMessage> rawRows = store.getConversation(packageName, sender);
            final List<SavedMessage> rows = collapseRepeatedMessages(rawRows);

            runOnUiThread(() -> {
                messages.removeAllViews();
                subtitle.setText(AppLabels.label(ConversationActivity.this, packageName) + " - " + rawRows.size() + (rawRows.size() == 1 ? " saved notice" : " saved notices"));
                if (rawRows.isEmpty()) {
                    TextView empty = new TextView(ConversationActivity.this);
                    empty.setText("No saved notices remain in this conversation.");
                    empty.setTextColor(getColor(R.color.ios_muted));
                    empty.setTextSize(15);
                    empty.setGravity(Gravity.CENTER);
                    messages.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                    return;
                }

                String lastDate = "";
                for (SavedMessage msg : rows) {
                    if (!safe(msg.dateLabel).equals(lastDate)) {
                        messages.addView(dateChip(msg.dateLabel), centeredParams(0, 8));
                        lastDate = safe(msg.dateLabel);
                    }
                    messages.addView(messageBubble(msg), bubbleParams(msg));
                }
                updateSelectionActions();
                if (scroll != null) {
                    scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
                }
            });
        });
    }

    View dateChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(safe(text).toUpperCase());
        chip.setTextColor(getColor(R.color.ios_muted));
        chip.setTextSize(11);
        chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.ios_border));
        bg.setCornerRadius(dp(10));
        chip.setBackground(bg);
        return chip;
    }

    View messageBubble(SavedMessage msg) {
        boolean selected = selectedIds.contains(msg.id);
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? getColor(R.color.ios_selection) : getColor(R.color.ios_surface));
        if (selected) {
            bg.setStroke(dp(2), getColor(R.color.ios_blue));
        } else {
            bg.setStroke(dp(1), getColor(R.color.ios_border));
        }
        bg.setCornerRadius(dp(16));
        bubble.setBackground(bg);

        TextView body = new TextView(this);
        body.setText(safe(msg.body));
        body.setTextColor(getColor(R.color.ios_ink));
        body.setTextSize(16);
        body.setLineSpacing(dp(4), 1.0f);
        bubble.addView(body);

        TextView time = new TextView(this);
        time.setText(msg.messageCount > 1 ? safe(msg.shortTime) + " - x" + msg.messageCount : safe(msg.shortTime));
        time.setTextColor(getColor(R.color.ios_muted));
        time.setTextSize(11);
        time.setGravity(Gravity.RIGHT);
        time.setPadding(0, dp(6), 0, 0);
        bubble.addView(time);

        bubble.setOnClickListener(v -> {
            if (selectedIds.isEmpty()) {
                showMessageActions(msg);
            } else {
                toggleSelection(msg);
            }
        });
        bubble.setOnLongClickListener(v -> {
            toggleSelection(msg);
            return true;
        });
        return bubble;
    }

    void toggleSelection(SavedMessage msg) {
        if (selectedIds.contains(msg.id)) {
            selectedIds.remove(msg.id);
        } else {
            selectedIds.add(msg.id);
        }
        loadMessages();
    }

    void clearSelection() {
        selectedIds.clear();
        loadMessages();
    }

    void updateSelectionActions() {
        if (selectionRow == null || deleteSelectedBtn == null || hideSelectedBtn == null) return;
        int count = selectedIds.size();
        selectionRow.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        deleteSelectedBtn.setText(count == 0 ? "Delete" : "Delete (" + count + ")");
        hideSelectedBtn.setText(count == 0 ? "Hide" : "Hide (" + count + ")");
    }

    void showMessageActions(SavedMessage msg) {
        String[] actions = {"Copy message", "Share message", "Delete message"};
        new AlertDialog.Builder(this)
                .setTitle(safe(msg.time))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) copyMessage(msg);
                    else if (which == 1) shareMessage(msg);
                    else confirmDeleteMessage(msg);
                })
                .show();
    }

    void showConversationActions() {
        String[] actions = {"Export conversation", "Message Retention", "Delete conversation"};
        new AlertDialog.Builder(this)
                .setTitle(safe(sender))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) shareConversation();
                    else if (which == 1) showRetentionDialog();
                    else confirmDeleteConversation();
                })
                .show();
    }

    void showRetentionDialog() {
        String prefKey = "retention_days|" + packageName + "|" + sender;
        int currentVal = getSharedPreferences("whatsthat_prefs", MODE_PRIVATE).getInt(prefKey, -1);
        int selection;
        if (currentVal == -1) selection = 0;
        else if (currentVal == 0) selection = 1;
        else if (currentVal == 1) selection = 2;
        else if (currentVal == 3) selection = 3;
        else if (currentVal == 7) selection = 4;
        else if (currentVal == 30) selection = 5;
        else selection = 0;
        
        String[] options = {
            "Follow global default",
            "Keep forever",
            "Delete after 1 day",
            "Delete after 3 days",
            "Delete after 7 days",
            "Delete after 30 days"
        };
        
        new AlertDialog.Builder(this)
                .setTitle("Message Retention - " + safe(sender))
                .setSingleChoiceItems(options, selection, (dialog, which) -> {
                    int days;
                    if (which == 0) days = -1;
                    else if (which == 1) days = 0;
                    else if (which == 2) days = 1;
                    else if (which == 3) days = 3;
                    else if (which == 4) days = 7;
                    else if (which == 5) days = 30;
                    else days = -1;
                    
                    if (days == -1) {
                        getSharedPreferences("whatsthat_prefs", MODE_PRIVATE).edit().remove(prefKey).apply();
                    } else {
                        getSharedPreferences("whatsthat_prefs", MODE_PRIVATE).edit().putInt(prefKey, days).apply();
                    }
                    
                    dialog.dismiss();
                    String summary = which == 0 ? "Following global default" : options[which];
                    Toast.makeText(ConversationActivity.this, "Retention set: " + summary, Toast.LENGTH_SHORT).show();
                    
                    dbExecutor.execute(() -> {
                        store.deleteOlderThanDays(getSharedPreferences("whatsthat_prefs", MODE_PRIVATE).getInt("retention_days", 0));
                        loadMessages();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void copyMessage(SavedMessage msg) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("WhatsThat message", formatMessage(msg)));
        Toast.makeText(this, "Message copied.", Toast.LENGTH_SHORT).show();
    }

    void shareMessage(SavedMessage msg) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, formatMessage(msg));
        startActivity(Intent.createChooser(send, "Share message"));
    }

    void shareConversation() {
        dbExecutor.execute(() -> {
            StringBuilder out = new StringBuilder();
            for (SavedMessage msg : store.getConversation(packageName, sender)) {
                out.append(formatMessage(msg)).append("\n\n");
            }
            final String text = out.toString().trim();
            runOnUiThread(() -> {
                if (text.isEmpty()) {
                    Toast.makeText(ConversationActivity.this, "No messages to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_SUBJECT, "WhatsThat conversation export");
                send.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(send, "Export conversation"));
            });
        });
    }

    void confirmDeleteMessage(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle(msg.messageCount > 1 ? "Delete repeated messages?" : "Delete this message?")
                .setMessage(msg.messageCount > 1 ? "This removes the repeated local copies in this group." : "This only removes the local copy saved in WhatsThat.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        deleteMessageGroup(msg);
                        runOnUiThread(this::loadMessages);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteConversation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete conversation?")
                .setMessage("This removes every saved notice from " + safe(sender) + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbExecutor.execute(() -> {
                        store.deleteConversation(packageName, sender);
                        runOnUiThread(this::finish);
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteSelected() {
        int count = selectedIds.size();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete selected messages?")
                .setMessage("This removes " + count + statusCountLabel(count) + " from this conversation.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedMessages())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void deleteSelectedMessages() {
        dbExecutor.execute(() -> {
            int deleted = 0;
            for (Long id : new ArrayList<>(selectedIds)) {
                List<Long> ids = groupedMessageIds.get(id);
                if (ids == null || ids.isEmpty()) {
                    deleted += store.deleteMessage(id);
                } else {
                    for (Long groupedId : ids) deleted += store.deleteMessage(groupedId);
                }
            }
            final int finalDeleted = deleted;
            runOnUiThread(() -> {
                selectedIds.clear();
                loadMessages();
                Toast.makeText(ConversationActivity.this, "Deleted " + finalDeleted + statusCountLabel(finalDeleted) + ".", Toast.LENGTH_SHORT).show();
            });
        });
    }

    void confirmHideSelected() {
        int count = selectedIds.size();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Hide messages like these?")
                .setMessage("Future matching notices will not be saved, and matching saved copies will be removed.")
                .setPositiveButton("Hide", (dialog, which) -> hideSelectedMessages())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void hideSelectedMessages() {
        dbExecutor.execute(() -> {
            int removed = 0;
            for (SavedMessage msg : store.getConversation(packageName, sender)) {
                if (!selectedIds.contains(msg.id)) continue;
                NotificationRules.hideSimilar(ConversationActivity.this, msg.packageName, msg.sender, msg.body);
                removed += store.deleteSimilar(msg.packageName, msg.sender, msg.body);
            }
            final int finalRemoved = removed;
            runOnUiThread(() -> {
                selectedIds.clear();
                loadMessages();
                Toast.makeText(ConversationActivity.this, "Hidden selected pattern" + (finalRemoved == 1 ? "." : "s."), Toast.LENGTH_SHORT).show();
            });
        });
    }

    void deleteMessageGroup(SavedMessage msg) {
        List<Long> ids = groupedMessageIds.get(msg.id);
        if (ids == null || ids.isEmpty()) {
            store.deleteMessage(msg.id);
            return;
        }
        for (Long id : ids) store.deleteMessage(id);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbExecutor.shutdown();
    }

    List<SavedMessage> collapseRepeatedMessages(List<SavedMessage> rows) {
        groupedMessageIds.clear();
        ArrayList<SavedMessage> collapsed = new ArrayList<>();
        SavedMessage previous = null;
        ArrayList<Long> currentIds = new ArrayList<>();
        int count = 0;
        for (SavedMessage row : rows) {
            if (previous != null && sameMessage(previous, row)) {
                currentIds.add(row.id);
                count++;
            } else {
                addCollapsedMessage(collapsed, previous, currentIds, count);
                previous = row;
                currentIds = new ArrayList<>();
                currentIds.add(row.id);
                count = 1;
            }
        }
        addCollapsedMessage(collapsed, previous, currentIds, count);
        return collapsed;
    }

    void addCollapsedMessage(List<SavedMessage> out, SavedMessage msg, List<Long> ids, int count) {
        if (msg == null) return;
        groupedMessageIds.put(msg.id, new ArrayList<>(ids));
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
        return safe(a.packageName).equals(safe(b.packageName))
                && safe(a.sender).equals(safe(b.sender))
                && safe(a.body).equals(safe(b.body));
    }

    String formatMessage(SavedMessage msg) {
        return safe(sender) + "\n" + safe(msg.body) + "\n" + safe(msg.time);
    }

    String statusCountLabel(int count) {
        return count == 1 ? " message" : " messages";
    }

    Button selectionBarButton(String text, int textColor) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(textColor);
        button.setBackgroundColor(Color.TRANSPARENT);
        return button;
    }

    LinearLayout.LayoutParams bubbleParams(SavedMessage msg) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), selectedIds.contains(msg.id) ? 0 : dp(54), dp(6));
        return params;
    }

    LinearLayout.LayoutParams centeredParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(top), 0, dp(bottom));
        return params;
    }

    String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unknown" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
