package com.theprincelive.whatsthat;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;

class BackNav {
    static Button button(Activity activity, boolean darkHeader) {
        Button button = new Button(activity);
        button.setText("‹ Back");
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setTextSize(17);
        button.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        button.setTextColor(Color.parseColor("#007AFF")); // iOS System Blue
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        button.setOnClickListener(v -> activity.finish());
        return button;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}

