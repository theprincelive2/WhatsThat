package com.theprincelive.whatsthat;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    public long getItemId(int position) { return position; }

    public View getView(int position, View convertView, ViewGroup parent) {
        SavedMessage msg = items.get(position);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 20, 24, 20);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(28);
        bg.setStroke(1, Color.rgb(226, 232, 240));
        card.setBackground(bg);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView avatar = new TextView(context);
        avatar.setText(initial(msg.sender));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(16);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(Color.rgb(37, 99, 235));
        avatarBg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBg);
        top.addView(avatar, new LinearLayout.LayoutParams(56, 56));

        LinearLayout nameBlock = new LinearLayout(context);
        nameBlock.setOrientation(LinearLayout.VERTICAL);
        nameBlock.setPadding(16, 0, 0, 0);

        TextView sender = new TextView(context);
        sender.setText(msg.sender == null ? "Unknown" : msg.sender);
        sender.setTextColor(Color.rgb(15, 23, 42));
        sender.setTextSize(16);
        sender.setTypeface(Typeface.DEFAULT_BOLD);
        nameBlock.addView(sender);

        TextView time = new TextView(context);
        time.setText(msg.time == null ? "" : msg.time);
        time.setTextColor(Color.rgb(100, 116, 139));
        time.setTextSize(12);
        nameBlock.addView(time);
        top.addView(nameBlock, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(top);

        TextView body = new TextView(context);
        body.setText(msg.body == null ? "" : msg.body);
        body.setTextColor(Color.rgb(30, 41, 59));
        body.setTextSize(15);
        body.setPadding(0, 16, 0, 0);
        card.addView(body);

        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(4, 8, 4, 8);
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        return outer;
    }

    private String initial(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }
}
