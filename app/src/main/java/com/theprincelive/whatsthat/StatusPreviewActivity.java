package com.theprincelive.whatsthat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StatusPreviewActivity extends Activity {
    static final String EXTRA_URI = "uri";
    static final String EXTRA_NAME = "name";
    static final String EXTRA_MIME = "mime";
    private static final int REQUEST_WRITE_STORAGE = 46;

    Uri statusUri;
    String statusName;
    String statusMime;
    Button saveButton;
    boolean pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusUri = Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        statusName = getIntent().getStringExtra(EXTRA_NAME);
        statusMime = getIntent().getStringExtra(EXTRA_MIME);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(18), dp(24), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText(isVideo() ? "Video Status" : "Photo Status");
        title.setTextColor(Color.rgb(17, 27, 24));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title);

        TextView name = new TextView(this);
        name.setText(statusName == null ? "" : statusName);
        name.setTextColor(Color.rgb(91, 104, 98));
        name.setTextSize(13);
        name.setPadding(0, dp(8), 0, dp(14));
        root.addView(name);

        if (isVideo()) {
            VideoView video = new VideoView(this);
            video.setVideoURI(statusUri);
            MediaController controller = new MediaController(this);
            controller.setAnchorView(video);
            video.setMediaController(controller);
            video.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                video.start();
            });
            root.addView(video, new LinearLayout.LayoutParams(-1, 0, 1));
        } else {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.rgb(17, 27, 24));
            image.setImageURI(statusUri);
            root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(14), 0, 0);

        saveButton = primaryButton(alreadySaved() ? "Save again" : "Save");
        saveButton.setOnClickListener(v -> saveStatus());
        actions.addView(saveButton, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button share = secondaryButton("Share");
        share.setOnClickListener(v -> shareStatus());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        shareParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(share, shareParams);
        root.addView(actions);

        setContentView(root);
    }

    void saveStatus() {
        if (needsLegacyWritePermission()) {
            pendingSave = true;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
        copyStatusToGallery();
    }

    boolean needsLegacyWritePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE && pendingSave) {
            pendingSave = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copyStatusToGallery();
            } else {
                Toast.makeText(this, "Storage permission is needed to save on this Android version.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void copyStatusToGallery() {
        Uri target = createMediaTarget();
        if (target == null) {
            Toast.makeText(this, "Could not create save location.", Toast.LENGTH_SHORT).show();
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(statusUri);
             OutputStream output = getContentResolver().openOutputStream(target)) {
            if (input == null || output == null) throw new IOException("Missing stream");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                getContentResolver().update(target, done, null, null);
            }
            if (saveButton != null) saveButton.setText("Save again");
            Toast.makeText(this, "Status saved.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not save this status.", Toast.LENGTH_SHORT).show();
        }
    }

    Uri createMediaTarget() {
        String outputName = uniqueName(statusName);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, outputName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, statusMime);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, isVideo() ? "Movies/WhatsThat/Status Videos" : "Pictures/WhatsThat/Status Photos");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            File folder = new File(android.os.Environment.getExternalStoragePublicDirectory(isVideo() ? android.os.Environment.DIRECTORY_MOVIES : android.os.Environment.DIRECTORY_PICTURES), isVideo() ? "WhatsThat/Status Videos" : "WhatsThat/Status Photos");
            if (!folder.exists()) folder.mkdirs();
            values.put(MediaStore.MediaColumns.DATA, new File(folder, outputName).getAbsolutePath());
        }
        Uri collection = isVideo() ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return getContentResolver().insert(collection, values);
    }

    void shareStatus() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(statusMime);
        send.putExtra(Intent.EXTRA_STREAM, statusUri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share status"));
    }

    boolean isVideo() {
        return statusMime != null && statusMime.startsWith("video/");
    }

    boolean alreadySaved() {
        return SavedStatusIndex.isSaved(SavedStatusIndex.load(this), statusName);
    }

    String uniqueName(String name) {
        String safe = name == null || name.trim().isEmpty() ? "status" : name;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return base + "_whatsthat_" + System.currentTimeMillis() + ext;
    }

    Button primaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0, 107, 85));
        bg.setCornerRadius(dp(18));
        button.setBackground(bg);
        return button;
    }

    Button secondaryButton(String text) {
        Button button = baseButton(text);
        button.setTextColor(Color.rgb(0, 107, 85));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 248, 246));
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(231, 234, 230));
        button.setBackground(bg);
        return button;
    }

    Button baseButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
