package com.theprincelive.whatsthat;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MessageDetailActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sender = getIntent().getStringExtra("sender");
        String body = getIntent().getStringExtra("body");
        String time = getIntent().getStringExtra("time");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        root.setBackgroundColor(Color.rgb(246, 241, 232));

        TextView title = new TextView(this);
        title.setText(sender == null ? "Unknown" : sender);
        title.setTextColor(Color.rgb(23, 27, 24));
        title.setTextSize(30);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setIncludeFontPadding(false);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(time == null ? "" : time);
        subtitle.setTextColor(Color.rgb(111, 117, 108));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(8), 0, dp(20));
        root.addView(subtitle);

        TextView message = new TextView(this);
        message.setText(body == null ? "" : body);
        message.setTextColor(Color.rgb(37, 48, 43));
        message.setTextSize(18);
        message.setLineSpacing(dp(5), 1.0f);
        message.setPadding(dp(20), dp(20), dp(20), dp(20));
        message.setGravity(Gravity.START);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.rgb(231, 222, 208));
        message.setBackground(bg);
        root.addView(message, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
