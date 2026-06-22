package com.theprincelive.whatsthat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.graphics.Typeface;
import android.media.MediaMetadataRetriever;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class StatusAdapter extends BaseAdapter {
    private final Context context;
    private final List<StatusFile> files;
    private final Set<String> selectedUris;

    StatusAdapter(Context context, List<StatusFile> files, Set<String> selectedUris) {
        this.context = context;
        this.files = files;
        this.selectedUris = selectedUris;
    }

    @Override
    public int getCount() {
        return files.size();
    }

    @Override
    public Object getItem(int position) {
        return files.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        StatusFile file = files.get(position);
        boolean selected = selectedUris != null && selectedUris.contains(file.uri.toString());

        android.widget.FrameLayout container = new android.widget.FrameLayout(context);
        android.widget.AbsListView.LayoutParams layoutParams = new android.widget.AbsListView.LayoutParams(
                android.widget.AbsListView.LayoutParams.MATCH_PARENT, dp(110));
        container.setLayoutParams(layoutParams);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(context.getColor(R.color.ios_surface));
        bg.setCornerRadius(dp(12));
        bg.setStroke(dp(1), context.getColor(R.color.ios_border));
        container.setBackground(bg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            container.setClipToOutline(true);
        }

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(context.getColor(R.color.ios_bg));
        if (file.isImage()) {
            preview.setImageURI(file.uri);
        } else {
            ThumbnailLoader.loadVideoThumbnail(context, file.uri, preview, R.drawable.ic_video);
        }
        container.addView(preview, new android.widget.FrameLayout.LayoutParams(-1, -1));

        if (file.isVideo()) {
            android.widget.FrameLayout playCircle = new android.widget.FrameLayout(context);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(Color.argb(128, 0, 0, 0));
            playCircle.setBackground(circleBg);

            TextView playSymbol = new TextView(context);
            playSymbol.setText("▶");
            playSymbol.setTextColor(Color.WHITE);
            playSymbol.setTextSize(14);
            playSymbol.setGravity(Gravity.CENTER);
            
            android.widget.FrameLayout.LayoutParams symbolParams = new android.widget.FrameLayout.LayoutParams(-2, -2);
            symbolParams.gravity = Gravity.CENTER;
            symbolParams.setMargins(dp(2), 0, 0, 0);
            playCircle.addView(playSymbol, symbolParams);

            android.widget.FrameLayout.LayoutParams playCircleParams = new android.widget.FrameLayout.LayoutParams(dp(36), dp(36));
            playCircleParams.gravity = Gravity.CENTER;
            container.addView(playCircle, playCircleParams);
        }

        if (file.saved) {
            TextView savedBadge = new TextView(context);
            savedBadge.setText("✓");
            savedBadge.setTextColor(Color.WHITE);
            savedBadge.setTextSize(10);
            savedBadge.setGravity(Gravity.CENTER);
            savedBadge.setTypeface(Typeface.DEFAULT_BOLD);
            
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            badgeBg.setColor(context.getColor(R.color.ios_green));
            savedBadge.setBackground(badgeBg);

            android.widget.FrameLayout.LayoutParams badgeParams = new android.widget.FrameLayout.LayoutParams(dp(20), dp(20));
            badgeParams.gravity = Gravity.BOTTOM | Gravity.END;
            badgeParams.setMargins(0, 0, dp(6), dp(6));
            container.addView(savedBadge, badgeParams);
        }

        if (selected) {
            View overlay = new View(context);
            int tint = context.getColor(R.color.ios_blue);
            overlay.setBackgroundColor((tint & 0x00FFFFFF) | 0x40000000);
            container.addView(overlay, new android.widget.FrameLayout.LayoutParams(-1, -1));

            TextView selectBadge = new TextView(context);
            selectBadge.setText("✓");
            selectBadge.setTextColor(Color.WHITE);
            selectBadge.setTextSize(10);
            selectBadge.setGravity(Gravity.CENTER);
            selectBadge.setTypeface(Typeface.DEFAULT_BOLD);

            GradientDrawable selectBg = new GradientDrawable();
            selectBg.setShape(GradientDrawable.OVAL);
            selectBg.setColor(context.getColor(R.color.ios_blue));
            selectBadge.setBackground(selectBg);

            android.widget.FrameLayout.LayoutParams selectParams = new android.widget.FrameLayout.LayoutParams(dp(20), dp(20));
            selectParams.gravity = Gravity.TOP | Gravity.END;
            selectParams.setMargins(0, dp(6), dp(6), 0);
            container.addView(selectBadge, selectParams);
        }

        return container;
    }

    String sizeText(long size) {
        if (size <= 0) return "unknown size";
        if (size >= 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / 1024f / 1024f);
        return String.format(Locale.getDefault(), "%.0f KB", size / 1024f);
    }



    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
