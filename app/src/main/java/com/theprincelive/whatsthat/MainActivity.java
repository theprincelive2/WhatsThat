package com.theprincelive.whatsthat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    MessageStore store;
    ListView list;
    TextView countText;
    TextView emptyText;
    TextView latestText;
    TextView statusText;
    EditText searchBox;
    Button open;

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
        Button clear = findViewById(R.id.clearBtn);

        open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        clear.setOnClickListener(v -> {
            store.clearMessages();
            load();
        });
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { load(); }
            public void afterTextChanged(Editable e) { }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            SavedMessage msg = (SavedMessage) parent.getItemAtPosition(position);
            Intent intent = new Intent(this, MessageDetailActivity.class);
            intent.putExtra("sender", msg.sender);
            intent.putExtra("body", msg.body);
            intent.putExtra("time", msg.time);
            startActivity(intent);
        });
        load();
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
        statusText.setText(hasAccess ? "Notification access is active" : "Message vault needs access");

        List<SavedMessage> rows = store.getRecentStructured();
        String q = searchBox.getText().toString().toLowerCase(Locale.getDefault());
        List<SavedMessage> filtered = new ArrayList<>();
        for (SavedMessage m : rows) {
            String v = (m.sender + " " + m.body + " " + m.time).toLowerCase(Locale.getDefault());
            if (q.isEmpty() || v.contains(q)) filtered.add(m);
        }

        int count = filtered.size();
        countText.setText(count + (count == 1 ? " saved item" : " saved items"));
        latestText.setText(count == 0 ? "New WhatsApp alerts will appear here." : "Latest capture: " + filtered.get(0).time);
        emptyText.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        list.setAdapter(new MessageAdapter(this, filtered));
    }
}
