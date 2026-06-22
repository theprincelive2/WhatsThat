package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

public class HiddenRulesActivity extends Activity {
    LinearLayout list;
    TextView summary;
    Button clearAllBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#F2F2F7"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        root.addView(BackNav.button(this, false), new LinearLayout.LayoutParams(dp(96), dp(42)));

        TextView title = title("Blocked Notices");
        title.setTextColor(Color.parseColor("#1C1C1E"));
        title.setPadding(0, dp(14), 0, 0);
        root.addView(title);

        summary = copy("");
        summary.setTextColor(Color.parseColor("#8E8E93"));
        root.addView(summary);

        clearAllBtn = secondaryButton("Clear All Blocked Notices");
        clearAllBtn.setTextColor(Color.parseColor("#FF3B30"));
        clearAllBtn.setOnClickListener(v -> confirmClearAll());
        root.addView(clearAllBtn, buttonParams(18));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        setContentView(scroll);
        loadRules();
    }

    void loadRules() {
        list.removeAllViews();
        List<NotificationRules.Rule> rules = NotificationRules.list(this);
        int count = rules.size();
        summary.setText(count == 0
                ? "No blocked notification rules yet. Long-press a saved notice and choose Hide messages like this to add one."
                : "These notifications are blocked before they are saved. Unblock a rule if something important was hidden.");
        clearAllBtn.setVisibility(count == 0 ? View.GONE : View.VISIBLE);

        if (count == 0) return;

        for (NotificationRules.Rule rule : rules) {
            list.addView(ruleRow(rule), rowParams());
        }
    }

    View ruleRow(NotificationRules.Rule rule) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#E5E5EA"));
        row.setBackground(bg);

        TextView sender = new TextView(this);
        sender.setText(rule.sender);
        sender.setTextColor(Color.parseColor("#1C1C1E"));
        sender.setTextSize(16);
        sender.setTypeface(Typeface.DEFAULT_BOLD);
        sender.setSingleLine(true);
        row.addView(sender);

        TextView body = copy(rule.body);
        body.setTextColor(Color.parseColor("#3A3A3C"));
        body.setPadding(0, dp(6), 0, 0);
        row.addView(body);

        TextView pkg = new TextView(this);
        pkg.setText(AppLabels.label(this, rule.packageName));
        pkg.setTextColor(Color.parseColor("#007AFF"));
        pkg.setTextSize(12);
        pkg.setPadding(0, dp(8), 0, 0);
        row.addView(pkg);

        Button unblock = secondaryButton("Unblock");
        unblock.setTextColor(Color.parseColor("#007AFF"));
        unblock.setPadding(dp(16), 0, dp(16), 0);
        unblock.setOnClickListener(v -> {
            NotificationRules.remove(this, rule.value);
            Toast.makeText(this, "Notification rule unblocked.", Toast.LENGTH_SHORT).show();
            loadRules();
        });
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-2, dp(36));
        btnParams.gravity = Gravity.END;
        btnParams.setMargins(0, dp(10), 0, 0);
        row.addView(unblock, btnParams);
        return row;
    }

    void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("Clear blocked notices?")
                .setMessage("Future notifications matching these rules will start saving again.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    NotificationRules.clear(this);
                    loadRules();
                    Toast.makeText(this, "Blocked notices cleared.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    TextView title(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(17, 27, 24));
        view.setTextSize(28);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    TextView copy(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.rgb(91, 104, 98));
        view.setTextSize(14);
        view.setLineSpacing(dp(4), 1.0f);
        view.setPadding(0, dp(8), 0, 0);
        return view;
    }

    Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.parseColor("#007AFF"));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), Color.parseColor("#E5E5EA"));
        button.setBackground(bg);
        return button;
    }

    LinearLayout.LayoutParams buttonParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(50));
        params.setMargins(0, dp(topMargin), 0, 0);
        return params;
    }

    LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(14), 0, 0);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
