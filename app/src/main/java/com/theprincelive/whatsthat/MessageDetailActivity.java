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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MessageDetailActivity extends Activity {
    long messageId;
    String sender;
    String body;
    String time;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        messageId = getIntent().getLongExtra("id", -1);
        sender = getIntent().getStringExtra("sender");
        body = getIntent().getStringExtra("body");
        time = getIntent().getStringExtra("time");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(236, 229, 221));

        TextView title = new TextView(this);
        title.setText(sender == null ? "Unknown" : sender);
        title.setTextColor(Color.rgb(17, 27, 24));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(time == null ? "" : time);
        subtitle.setTextColor(Color.rgb(91, 104, 98));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        root.addView(subtitle);

        TextView message = new TextView(this);
        message.setText(body == null ? "" : body);
        message.setTextColor(Color.rgb(17, 27, 24));
        message.setTextSize(17);
        message.setLineSpacing(dp(5), 1.0f);
        message.setPadding(dp(20), dp(20), dp(20), dp(20));
        message.setGravity(Gravity.START);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(220, 248, 198));
        bg.setCornerRadius(dp(18));
        message.setBackground(bg);
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(0, dp(18), 0, 0);

        Button copy = actionButton("Copy message", true);
        copy.setOnClickListener(v -> copyMessage());
        actions.addView(copy, new LinearLayout.LayoutParams(-1, dp(48)));

        Button share = actionButton("Share message", false);
        share.setOnClickListener(v -> shareMessage());
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.setMargins(0, dp(10), 0, 0);
        actions.addView(share, actionParams);

        Button delete = actionButton("Delete this message", false);
        delete.setOnClickListener(v -> confirmDeleteMessage());
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-1, dp(48));
        deleteParams.setMargins(0, dp(10), 0, 0);
        actions.addView(delete, deleteParams);

        Button deleteSender = actionButton("Delete all from sender", false);
        deleteSender.setOnClickListener(v -> confirmDeleteSender());
        LinearLayout.LayoutParams senderParams = new LinearLayout.LayoutParams(-1, dp(48));
        senderParams.setMargins(0, dp(10), 0, 0);
        actions.addView(deleteSender, senderParams);
        root.addView(actions);

        setContentView(root);
    }

    void copyMessage() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        manager.setPrimaryClip(ClipData.newPlainText("WhatsThat message", formattedMessage()));
        Toast.makeText(this, "Message copied.", Toast.LENGTH_SHORT).show();
    }

    void shareMessage() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, formattedMessage());
        startActivity(Intent.createChooser(send, "Share message"));
    }

    void confirmDeleteMessage() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this message?")
                .setMessage("This only removes the local copy saved in WhatsThat.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (messageId >= 0) new MessageStore(this).deleteMessage(messageId);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    void confirmDeleteSender() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all from this sender?")
                .setMessage("This removes every locally saved message from " + safe(sender) + ".")
                .setPositiveButton("Delete", (dialog, which) -> {
                    new MessageStore(this).deleteSender(sender);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    String formattedMessage() {
        return safe(sender) + "\n\n" + safe(body) + "\n\n" + safe(time);
    }

    Button actionButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.rgb(0, 107, 85));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? Color.rgb(0, 107, 85) : Color.WHITE);
        bg.setCornerRadius(dp(16));
        if (!primary) bg.setStroke(dp(1), Color.rgb(222, 226, 223));
        button.setBackground(bg);
        return button;
    }

    String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
