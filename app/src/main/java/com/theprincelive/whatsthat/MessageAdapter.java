package com.theprincelive.whatsthat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

public class MessageAdapter extends BaseAdapter {
    private final Context context;
    private final List<SavedMessage> items;

    public MessageAdapter(Context context, List<SavedMessage> items) {
        this.context = context;
        this.items = items;
    }

    public int getCount() { return items.size(); }
    public Object getItem(int position) { return items.get(position); }
    public long getItemId(int position) { return items.get(position).id; }

    public View getView(int position, View convertView, ViewGroup parent) {
        SavedMessage msg = items.get(position);

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(10), dp(6), dp(10), dp(8));

        if (position == 0 || !safe(items.get(position - 1).dateLabel).equals(safe(msg.dateLabel))) {
            TextView group = new TextView(context);
            group.setText(safe(msg.dateLabel));
            group.setTextColor(Color.rgb(111, 117, 108));
            group.setTextSize(12);
            group.setTypeface(Typeface.DEFAULT_BOLD);
            group.setPadding(dp(4), dp(8), 0, dp(6));
            outer.addView(group);
        }

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.WHITE);
        cardBg.setCornerRadius(dp(20));
        cardBg.setStroke(dp(1), Color.rgb(231, 222, 208));
        card.setBackground(cardBg);

        TextView accent = new TextView(context);
        GradientDrawable accentBg = new GradientDrawable();
        accentBg.setColor(Color.rgb(15, 77, 57));
        accentBg.setCornerRadius(dp(6));
        accent.setBackground(accentBg);
        LinearLayout.LayoutParams accentParams = new LinearLayout.LayoutParams(dp(4), dp(58));
        accentParams.setMargins(0, 0, dp(12), 0);
        card.addView(accent, accentParams);

        TextView avatar = new TextView(context);
        avatar.setText(initial(msg.sender));
        avatar.setTextColor(Color.rgb(15, 77, 57));
        avatar.setTextSize(15);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(Color.rgb(221, 235, 226));
        avatarBg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarParams.setMargins(0, 0, dp(14), 0);
        card.addView(avatar, avatarParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView sender = new TextView(context);
        sender.setText(msg.sender == null ? "Unknown" : msg.sender);
        sender.setTextColor(Color.rgb(23, 27, 24));
        sender.setTextSize(16);
        sender.setTypeface(Typeface.DEFAULT_BOLD);
        sender.setSingleLine(true);
        top.addView(sender, new LinearLayout.LayoutParams(0, -2, 1));

        TextView time = new TextView(context);
        time.setText(msg.shortTime == null ? "" : msg.shortTime);
        time.setTextColor(Color.rgb(111, 117, 108));
        time.setTextSize(11);
        time.setGravity(Gravity.RIGHT);
        top.addView(time);
        content.addView(top);

        TextView body = new TextView(context);
        body.setText(msg.body == null ? "" : msg.body);
        body.setTextColor(Color.rgb(37, 48, 43));
        body.setTextSize(14);
        body.setLineSpacing(dp(3), 1.0f);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(7), 0, 0);
        content.addView(body);

        card.addView(content, new LinearLayout.LayoutParams(0, -2, 1));
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return outer;
    }

    private String initial(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
