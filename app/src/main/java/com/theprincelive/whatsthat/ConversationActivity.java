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
import java.util.List;

public class ConversationActivity extends Activity {
    String packageName;
    String sender;
    MessageStore store;
    LinearLayout messages;
    TextView subtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        packageName = getIntent().getStringExtra("packageName");
        sender = getIntent().getStringExtra("sender");
        store = new MessageStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(236, 229, 221));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(14), dp(18), dp(14), dp(14));
        header.setBackgroundColor(Color.rgb(0, 107, 85));

        TextView back = new TextView(this);
        back.setText("<");
        back.setTextColor(Color.WHITE);
        back.setTextSize(28);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setPadding(dp(6), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText(safe(sender));
        title.setTextColor(Color.WHITE);
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        titleBlock.addView(title);

        subtitle = new TextView(this);
        subtitle.setTextColor(Color.rgb(207, 234, 225));
        subtitle.setTextSize(13);
        subtitle.setSingleLine(true);
        titleBlock.addView(subtitle);
        header.addView(titleBlock, new LinearLayout.LayoutParams(0, -2, 1));

        Button actions = headerButton("Actions");
        actions.setOnClickListener(v -> showConversationActions());
        header.addView(actions, new LinearLayout.LayoutParams(dp(92), dp(42)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        messages = new LinearLayout(this);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(14), dp(12), dp(14), dp(18));
        scroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        loadMessages();
    }

    void loadMessages() {
        store.markConversationRead(packageName, sender);
        messages.removeAllViews();
        List<SavedMessage> rows = store.getConversation(packageName, sender);
        subtitle.setText(AppLabels.label(this, packageName) + " - " + rows.size() + (rows.size() == 1 ? " saved notice" : " saved notices"));
        if (rows.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved notices remain in this conversation.");
            empty.setTextColor(Color.rgb(91, 104, 98));
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
            messages.addView(messageBubble(msg), bubbleParams());
        }
    }

    View dateChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(safe(text));
        chip.setTextColor(Color.rgb(91, 104, 98));
        chip.setTextSize(12);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(248, 249, 247));
        bg.setCornerRadius(dp(12));
        chip.setBackground(bg);
        return chip;
    }

    View messageBubble(SavedMessage msg) {
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(10));
        bubble.setBackground(bg);

        TextView body = new TextView(this);
        body.setText(safe(msg.body));
        body.setTextColor(Color.rgb(17, 27, 24));
        body.setTextSize(16);
        body.setLineSpacing(dp(4), 1.0f);
        bubble.addView(body);

        TextView time = new TextView(this);
        time.setText(safe(msg.shortTime));
        time.setTextColor(Color.rgb(91, 104, 98));
        time.setTextSize(11);
        time.setGravity(Gravity.RIGHT);
        time.setPadding(0, dp(6), 0, 0);
        bubble.addView(time);

        bubble.setOnClickListener(v -> showMessageActions(msg));
        return bubble;
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
        String[] actions = {"Export conversation", "Delete conversation"};
        new AlertDialog.Builder(this)
                .setTitle(safe(sender))
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) shareConversation();
                    else confirmDeleteConversation();
                })
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
        StringBuilder out = new StringBuilder();
        for (SavedMessage msg : store.getConversation(packageName, sender)) {
            out.append(formatMessage(msg)).append("\n\n");
        }
        if (out.length() == 0) {
            Toast.makeText(this, "No messages to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "WhatsThat conversation export");
        send.putExtra(Intent.EXTRA_TEXT, out.toString().trim());
        startActivity(Intent.createChooser(send, "Export conversation"));
    }

    void confirmDeleteMessage(SavedMessage msg) {
        new AlertDialog.Builder(this)
                .setTitle("Delete this message?")
                .setMessage("This only removes the local copy saved in WhatsThat.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteMessage(msg.id);
                    loadMessages();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteConversation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete conversation?")
                .setMessage("This removes every saved notice from " + safe(sender) + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    store.deleteConversation(packageName, sender);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    String formatMessage(SavedMessage msg) {
        return safe(sender) + "\n" + safe(msg.body) + "\n" + safe(msg.time);
    }

    Button headerButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(18, 140, 126));
        bg.setCornerRadius(dp(16));
        button.setBackground(bg);
        return button;
    }

    LinearLayout.LayoutParams bubbleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), dp(54), dp(6));
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
