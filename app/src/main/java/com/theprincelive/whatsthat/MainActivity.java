package com.theprincelive.whatsthat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
    private MessageStore store;
    private ListView list;
    private TextView countText;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new MessageStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 42, 28, 28);
        root.setBackgroundColor(Color.rgb(245, 247, 251));

        TextView title = new TextView(this);
        title.setText("WhatsThat");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(19, 28, 43));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your private notification history for your own phone.");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(92, 101, 116));
        subtitle.setPadding(0, 6, 0, 22);
        root.addView(subtitle);

        countText = new TextView(this);
        countText.setTextSize(16);
        countText.setTypeface(Typeface.DEFAULT_BOLD);
        countText.setTextColor(Color.rgb(11, 95, 255));
        countText.setPadding(0, 0, 0, 18);
        root.addView(countText);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, 20);

        Button open = new Button(this);
        open.setText("Enable Access");
        open.setAllCaps(false);
        Button clear = new Button(this);
        clear.setText("Clear History");
        clear.setAllCaps(false);

        actions.addView(open, new LinearLayout.LayoutParams(0, 120, 1));
        actions.addView(clear, new LinearLayout.LayoutParams(0, 120, 1));
        root.addView(actions);

        emptyText = new TextView(this);
        emptyText.setText("No saved notifications yet. Enable access, then wait for a new WhatsApp notification.");
        emptyText.setTextSize(16);
        emptyText.setTextColor(Color.rgb(92, 101, 116));
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(20, 70, 20, 20);
        root.addView(emptyText);

        list = new ListView(this);
        list.setDividerHeight(10);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        open.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });

        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                store.clearMessages();
                load();
            }
        });

        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        List<String> messages = store.getRecentMessages();
        countText.setText(messages.size() + " saved item" + (messages.size() == 1 ? "" : "s"));
        emptyText.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(messages.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages));
    }
}
