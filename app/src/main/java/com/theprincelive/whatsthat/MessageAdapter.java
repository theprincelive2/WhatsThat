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
import java.util.Set;

public class MessageAdapter extends BaseAdapter {
    private final Context context;
    private final List<SavedMessage> items;
    private final Set<String> selectedKeys;
    private final Set<String> systemKeys;
    private final SwipeItemLayout.OnSwipeActionListener swipeListener;

    public MessageAdapter(Context context, List<SavedMessage> items) {
        this(context, items, null, null, null);
    }

    public MessageAdapter(Context context, List<SavedMessage> items, Set<String> selectedKeys) {
        this(context, items, selectedKeys, null, null);
    }

    public MessageAdapter(Context context, List<SavedMessage> items, Set<String> selectedKeys, Set<String> systemKeys) {
        this(context, items, selectedKeys, systemKeys, null);
    }

    public MessageAdapter(Context context, List<SavedMessage> items, Set<String> selectedKeys, Set<String> systemKeys, SwipeItemLayout.OnSwipeActionListener swipeListener) {
        this.context = context;
        this.items = items;
        this.selectedKeys = selectedKeys;
        this.systemKeys = systemKeys;
        this.swipeListener = swipeListener;
    }

    public int getCount() { return items.size(); }
    public Object getItem(int position) { return items.get(position); }
    public long getItemId(int position) { return items.get(position).id; }

    public View getView(int position, View convertView, ViewGroup parent) {
        SavedMessage msg = items.get(position);
        boolean selected = selectedKeys != null && selectedKeys.contains(selectionKey(msg));
        boolean system = systemKeys != null && systemKeys.contains(selectionKey(msg));

        LinearLayout outer = new LinearLayout(context);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12), 0, dp(12), 0);
        if (selected) outer.setBackgroundColor(context.getColor(R.color.brand_green_soft));

        if (position == 0 || !safe(items.get(position - 1).dateLabel).equals(safe(msg.dateLabel))) {
            TextView group = new TextView(context);
            group.setText(safe(msg.dateLabel));
            group.setTextColor(context.getColor(R.color.brand_muted));
            group.setTextSize(12);
            group.setTypeface(Typeface.DEFAULT_BOLD);
            group.setGravity(Gravity.CENTER);
            group.setPadding(0, dp(12), 0, dp(8));
            outer.addView(group);
        }

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView avatar = new TextView(context);
        avatar.setText(initial(msg.sender));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(19);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setColor(avatarColor(position));
        avatarBg.setShape(GradientDrawable.OVAL);
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        avatarParams.setMargins(0, 0, dp(12), 0);
        row.addView(avatar, avatarParams);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView sender = new TextView(context);
        sender.setText(msg.sender == null ? "Unknown" : msg.sender);
        sender.setTextColor(context.getColor(R.color.brand_ink));
        sender.setTextSize(16);
        sender.setTypeface(msg.unreadCount > 0 || !msg.read ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        sender.setSingleLine(true);
        top.addView(sender, new LinearLayout.LayoutParams(0, -2, 1));

        if (system) {
            TextView systemBadge = systemBadge();
            LinearLayout.LayoutParams systemParams = new LinearLayout.LayoutParams(-2, dp(22));
            systemParams.setMargins(dp(8), 0, dp(8), 0);
            top.addView(systemBadge, systemParams);
        }

        TextView time = new TextView(context);
        time.setText(msg.shortTime == null ? "" : msg.shortTime);
        time.setTextColor(context.getColor(R.color.brand_green));
        time.setTextSize(12);
        time.setGravity(Gravity.RIGHT);
        top.addView(time);
        content.addView(top);

        TextView body = new TextView(context);
        body.setText(msg.body == null ? "" : msg.body);
        body.setTextColor(msg.unreadCount > 0 || !msg.read ? context.getColor(R.color.brand_ink) : context.getColor(R.color.brand_muted));
        body.setTextSize(14);
        body.setTypeface(msg.unreadCount > 0 || !msg.read ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        body.setLineSpacing(dp(2), 1.0f);
        body.setMaxLines(1);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setPadding(0, dp(5), 0, 0);
        content.addView(body);

        String meta = metaText(msg, system);
        if (!meta.isEmpty()) {
            TextView metaView = new TextView(context);
            metaView.setText(meta);
            metaView.setTextColor(system ? context.getColor(R.color.brand_brass) : context.getColor(R.color.brand_muted));
            metaView.setTextSize(12);
            metaView.setSingleLine(true);
            metaView.setEllipsize(TextUtils.TruncateAt.END);
            metaView.setPadding(0, dp(4), 0, 0);
            content.addView(metaView);
        }

        row.addView(content, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = new TextView(context);
        LinearLayout.LayoutParams badgeParams;
        if (msg.unreadCount > 0) {
            badge.setText(String.valueOf(msg.unreadCount));
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(context.getColor(R.color.brand_unread));
            badgeBg.setShape(GradientDrawable.OVAL);
            badge.setBackground(badgeBg);
            badgeParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            badgeParams.setMargins(dp(10), 0, dp(0), 0);
        } else if (msg.messageCount > 1) {
            badge.setText(String.valueOf(msg.messageCount));
            badge.setTextColor(context.getColor(R.color.brand_muted));
            badge.setTextSize(11);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(context.getColor(R.color.brand_chip));
            badgeBg.setShape(GradientDrawable.OVAL);
            badge.setBackground(badgeBg);
            badgeParams = new LinearLayout.LayoutParams(dp(24), dp(24));
            badgeParams.setMargins(dp(10), 0, dp(0), 0);
        } else if (!msg.read) {
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setColor(context.getColor(R.color.brand_unread));
            dotBg.setShape(GradientDrawable.OVAL);
            badge.setBackground(dotBg);
            badgeParams = new LinearLayout.LayoutParams(dp(9), dp(9));
            badgeParams.setMargins(dp(10), 0, dp(2), 0);
        } else {
            badgeParams = new LinearLayout.LayoutParams(dp(9), dp(9));
            badgeParams.setMargins(dp(10), 0, dp(2), 0);
        }
        row.addView(badge, badgeParams);

        SwipeItemLayout swipeLayout = new SwipeItemLayout(context);
        swipeLayout.setUpViews(row, msg, swipeListener);
        boolean inSelectionMode = selectedKeys != null && !selectedKeys.isEmpty();
        swipeLayout.setSwipeEnabled(!inSelectionMode && swipeListener != null);
        outer.addView(swipeLayout, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(context);
        divider.setBackgroundColor(context.getColor(R.color.brand_line));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.setMargins(dp(64), 0, 0, 0);
        outer.addView(divider, dividerParams);
        return outer;
    }

    private String initial(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        return name.trim().substring(0, 1).toUpperCase();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String selectionKey(SavedMessage msg) {
        return safe(msg.packageName) + "\u001f" + safe(msg.sender);
    }

    private TextView systemBadge() {
        TextView badge = new TextView(context);
        badge.setText("System");
        badge.setTextColor(context.getColor(R.color.brand_muted));
        badge.setTextSize(10);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(context.getColor(R.color.brand_chip));
        bg.setCornerRadius(dp(11));
        badge.setBackground(bg);
        return badge;
    }

    private String metaText(SavedMessage msg, boolean system) {
        if (system) return "System-generated notice";
        if (msg.messageCount <= 1) return "";
        if (msg.unreadCount > 0) {
            return msg.unreadCount + countLabel(msg.unreadCount, " unread") + " in " + msg.messageCount + countLabel(msg.messageCount, " saved notice");
        }
        return msg.messageCount + countLabel(msg.messageCount, " saved notice");
    }

    private String countLabel(int count, String label) {
        return count == 1 ? label : label + "s";
    }

    private int avatarColor(int position) {
        int[] colors = {
                Color.rgb(0, 107, 85),
                Color.rgb(7, 94, 84),
                Color.rgb(18, 140, 126),
                Color.rgb(66, 133, 91),
                Color.rgb(45, 106, 79)
        };
        return colors[position % colors.length];
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
