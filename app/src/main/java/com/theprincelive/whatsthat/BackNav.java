package com.theprincelive.whatsthat;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.Button;

class BackNav {
    static Button button(Activity activity, boolean darkHeader) {
        Button button = new Button(activity);
        button.setText("< Back");
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(darkHeader ? Color.WHITE : Color.rgb(0, 107, 85));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(darkHeader ? Color.rgb(0, 121, 96) : Color.rgb(247, 248, 246));
        bg.setCornerRadius(dp(activity, 18));
        bg.setStroke(dp(activity, 1), darkHeader ? Color.rgb(42, 145, 121) : Color.rgb(231, 234, 230));
        button.setBackground(bg);
        button.setOnClickListener(v -> activity.finish());
        return button;
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
