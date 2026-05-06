package com.theprincelive.whatsthat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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

class StatusAdapter extends BaseAdapter {
    private final Context context;
    private final List<StatusFile> files;

    StatusAdapter(Context context, List<StatusFile> files) {
        this.context = context;
        this.files = files;
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
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));

        ImageView preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(Color.rgb(239, 241, 239));
        if (file.isImage()) {
            preview.setImageURI(file.uri);
        } else {
            Bitmap thumbnail = videoThumbnail(file);
            if (thumbnail != null) {
                preview.setImageBitmap(thumbnail);
            } else {
                preview.setImageResource(R.drawable.ic_video);
                preview.setPadding(dp(18), dp(18), dp(18), dp(18));
            }
        }
        row.addView(preview, new LinearLayout.LayoutParams(dp(68), dp(68)));

        LinearLayout textWrap = new LinearLayout(context);
        textWrap.setOrientation(LinearLayout.VERTICAL);
        textWrap.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(file.isVideo() ? "Video status" : "Photo status");
        title.setTextColor(Color.rgb(17, 27, 24));
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        textWrap.addView(title);

        TextView meta = new TextView(context);
        meta.setText(file.name + " - " + sizeText(file.size));
        meta.setTextColor(Color.rgb(91, 104, 98));
        meta.setTextSize(13);
        meta.setPadding(0, dp(5), 0, 0);
        textWrap.addView(meta);

        TextView time = new TextView(context);
        time.setText(file.modifiedAt > 0 ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(new Date(file.modifiedAt)) : "");
        time.setTextColor(Color.rgb(111, 117, 108));
        time.setTextSize(12);
        time.setPadding(0, dp(5), 0, 0);
        textWrap.addView(time);

        row.addView(textWrap, new LinearLayout.LayoutParams(0, -2, 1));
        return row;
    }

    String sizeText(long size) {
        if (size <= 0) return "unknown size";
        if (size >= 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / 1024f / 1024f);
        return String.format(Locale.getDefault(), "%.0f KB", size / 1024f);
    }

    Bitmap videoThumbnail(StatusFile file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, file.uri);
            return retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
