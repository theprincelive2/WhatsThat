package com.theprincelive.whatsthat;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MessageDetailActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView tv = new TextView(this);
        tv.setPadding(40, 40, 40, 40);
        tv.setTextSize(18);

        String sender = getIntent().getStringExtra("sender");
        String body = getIntent().getStringExtra("body");
        String time = getIntent().getStringExtra("time");

        tv.setText(sender + "\n\n" + body + "\n\n" + time);

        setContentView(tv);
    }
}
