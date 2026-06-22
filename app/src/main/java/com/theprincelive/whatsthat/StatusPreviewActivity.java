package com.theprincelive.whatsthat;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

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

    // Gestures and controls
    private ScaleGestureDetector scaleGestureDetector;
    private float scaleFactor = 1.0f;
    private float startY;
    private float startX;
    private boolean isDraggingDown = false;
    private int touchSlop;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        statusUri = Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        statusName = getIntent().getStringExtra(EXTRA_NAME);
        statusMime = getIntent().getStringExtra(EXTRA_MIME);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        // Start fade-in transition
        getWindow().getDecorView().setAlpha(0f);
        getWindow().getDecorView().animate().alpha(1f).setDuration(250).start();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 1. Media Container
        FrameLayout mediaContainer = new FrameLayout(this);
        mediaContainer.setBackgroundColor(Color.BLACK);
        root.addView(mediaContainer, new FrameLayout.LayoutParams(-1, -1));

        // Create references for overlays
        final LinearLayout headerBar = new LinearLayout(this);
        final LinearLayout actionsBar = new LinearLayout(this);

        if (isVideo()) {
            VideoView video = new VideoView(this);
            video.setVideoURI(statusUri);
            mediaContainer.addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

            // Custom Video Controls Overlay
            FrameLayout videoControlsOverlay = new FrameLayout(this);
            videoControlsOverlay.setBackgroundColor(Color.parseColor("#1A000000")); // very soft dim

            // Centered Play/Pause circle
            TextView playPauseBtn = new TextView(this);
            playPauseBtn.setGravity(Gravity.CENTER);
            playPauseBtn.setTextSize(26);
            playPauseBtn.setTextColor(Color.WHITE);
            playPauseBtn.setText("❚❚"); // Pause initially

            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setColor(Color.parseColor("#66000000"));
            circleBg.setShape(GradientDrawable.OVAL);
            playPauseBtn.setBackground(circleBg);

            FrameLayout.LayoutParams playPauseParams = new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER);
            videoControlsOverlay.addView(playPauseBtn, playPauseParams);

            // Timeline container at bottom of video controls
            LinearLayout timeline = new LinearLayout(this);
            timeline.setOrientation(LinearLayout.HORIZONTAL);
            timeline.setGravity(Gravity.CENTER_VERTICAL);
            timeline.setPadding(dp(12), dp(8), dp(12), dp(8));
            GradientDrawable timelineBg = new GradientDrawable();
            timelineBg.setColor(Color.parseColor("#B31C1C1E")); // frosted dark grey
            timelineBg.setCornerRadius(dp(12));
            timeline.setBackground(timelineBg);

            TextView elapsed = new TextView(this);
            elapsed.setTextColor(Color.WHITE);
            elapsed.setTextSize(11);
            elapsed.setText("00:00");
            timeline.addView(elapsed);

            SeekBar seekBar = new SeekBar(this);
            seekBar.setProgressTintList(ColorStateList.valueOf(Color.parseColor("#007AFF"))); // iOS system blue
            seekBar.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
            LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, -2, 1);
            seekParams.setMargins(dp(8), 0, dp(8), 0);
            timeline.addView(seekBar, seekParams);

            TextView duration = new TextView(this);
            duration.setTextColor(Color.WHITE);
            duration.setTextSize(11);
            duration.setText("00:00");
            timeline.addView(duration);

            FrameLayout.LayoutParams timelineParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
            timelineParams.setMargins(dp(16), 0, dp(16), dp(16));
            videoControlsOverlay.addView(timeline, timelineParams);

            mediaContainer.addView(videoControlsOverlay, new FrameLayout.LayoutParams(-1, -1));

            // Setup custom media control listeners
            final FrameLayout fControls = videoControlsOverlay;
            videoControlsOverlay.setOnClickListener(v -> {
                boolean visible = fControls.getAlpha() == 1.0f;
                float targetAlpha = visible ? 0.0f : 1.0f;
                fControls.animate().alpha(targetAlpha).setDuration(250).start();
                headerBar.animate().alpha(targetAlpha).setDuration(250).start();
                actionsBar.animate().alpha(targetAlpha).setDuration(250).start();
            });

            timeline.setOnClickListener(v -> {}); // prevent timeline clicks from toggling controls

            playPauseBtn.setOnClickListener(v -> {
                if (video.isPlaying()) {
                    video.pause();
                    playPauseBtn.setText("▶");
                } else {
                    video.start();
                    playPauseBtn.setText("❚❚");
                }
            });

            video.setOnPreparedListener(mp -> {
                mp.setLooping(true);
                video.start();
                seekBar.setMax(video.getDuration());
                duration.setText(formatTime(video.getDuration()));
                startProgressUpdater(video, seekBar, elapsed, duration);
            });

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    if (fromUser) {
                        video.seekTo(progress);
                        elapsed.setText(formatTime(progress));
                    }
                }
                @Override
                public void onStartTrackingTouch(SeekBar sb) {}
                @Override
                public void onStopTrackingTouch(SeekBar sb) {}
            });
        } else {
            ImageView image = new ImageView(this);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setBackgroundColor(Color.BLACK);
            image.setImageURI(statusUri);
            mediaContainer.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

            // Pinch-to-Zoom
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector detector) {
                    scaleFactor *= detector.getScaleFactor();
                    scaleFactor = Math.max(1.0f, Math.min(scaleFactor, 5.0f));
                    image.setScaleX(scaleFactor);
                    image.setScaleY(scaleFactor);
                    return true;
                }
            });

            image.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (scaleFactor > 1.0f) {
                        scaleFactor = 1.0f;
                        image.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start();
                    }
                }
                return true;
            });
        }

        // 2. Floating Top Header Overlay
        headerBar.setOrientation(LinearLayout.VERTICAL);
        headerBar.setBackgroundColor(Color.parseColor("#A6000000")); // dark semi-translucent
        headerBar.setPadding(dp(18), dp(24), dp(18), dp(14));

        LinearLayout topNav = new LinearLayout(this);
        topNav.setOrientation(LinearLayout.HORIZONTAL);
        topNav.setGravity(Gravity.CENTER_VERTICAL);
        topNav.addView(BackNav.button(this, true), new LinearLayout.LayoutParams(dp(96), dp(42)));
        headerBar.addView(topNav);

        TextView title = new TextView(this);
        title.setText(isVideo() ? "Video Status" : "Photo Status");
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setIncludeFontPadding(false);
        title.setPadding(0, dp(8), 0, 0);
        headerBar.addView(title);

        TextView name = new TextView(this);
        name.setText(statusName == null ? "" : statusName);
        name.setTextColor(Color.parseColor("#8E8E93"));
        name.setTextSize(12);
        name.setPadding(0, dp(4), 0, 0);
        headerBar.addView(name);

        FrameLayout.LayoutParams headerParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(headerBar, headerParams);

        // 3. Floating Bottom Actions Overlay
        actionsBar.setOrientation(LinearLayout.HORIZONTAL);
        actionsBar.setBackgroundColor(Color.parseColor("#A6000000")); // dark semi-translucent
        actionsBar.setPadding(dp(18), dp(14), dp(18), dp(18));

        saveButton = primaryButton(alreadySaved() ? "Save again" : "Save");
        saveButton.setOnClickListener(v -> saveStatus());
        actionsBar.addView(saveButton, new LinearLayout.LayoutParams(0, dp(50), 1));

        Button share = secondaryButton("Share");
        share.setOnClickListener(v -> shareStatus());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        shareParams.setMargins(dp(10), 0, 0, 0);
        actionsBar.addView(share, shareParams);

        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(actionsBar, actionsParams);

        setContentView(root);
    }

    private void startProgressUpdater(VideoView video, SeekBar seekBar, TextView elapsed, TextView duration) {
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (video.isPlaying()) {
                    int current = video.getCurrentPosition();
                    int dur = video.getDuration();
                    if (dur > 0) {
                        seekBar.setMax(dur);
                        seekBar.setProgress(current);
                        elapsed.setText(formatTime(current));
                        duration.setText(formatTime(dur));
                    }
                }
                progressHandler.postDelayed(this, 250);
            }
        };
        progressHandler.post(progressRunnable);
    }

    private String formatTime(int ms) {
        int seconds = (ms / 1000) % 60;
        int minutes = (ms / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // Disable swipe down to dismiss if currently zoom scaled
        if (scaleFactor > 1.0f) {
            return super.dispatchTouchEvent(ev);
        }

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getRawY();
                startX = ev.getRawX();
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getRawY() - startY;
                float dx = ev.getRawX() - startX;
                if (!isDraggingDown && dy > Math.abs(dx) && dy > touchSlop) {
                    isDraggingDown = true;
                    // Cancel touches on child views to prevent click actions
                    MotionEvent cancelEvent = MotionEvent.obtain(ev);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }
                if (isDraggingDown) {
                    View root = getWindow().getDecorView();
                    root.setTranslationY(Math.max(0, dy));
                    float alpha = 1.0f - (Math.max(0, dy) / root.getHeight());
                    root.setAlpha(Math.max(0.5f, alpha));
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingDown) {
                    isDraggingDown = false;
                    View root = getWindow().getDecorView();
                    float dyEnd = ev.getRawY() - startY;
                    if (dyEnd > root.getHeight() * 0.20) {
                        root.animate()
                                .translationY(root.getHeight())
                                .alpha(0f)
                                .setDuration(250)
                                .withEndAction(this::finish)
                                .start();
                    } else {
                        root.animate()
                                .translationY(0)
                                .alpha(1.0f)
                                .setDuration(250)
                                .start();
                    }
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void finish() {
        View root = getWindow().getDecorView();
        root.animate()
                .translationY(root.getHeight())
                .alpha(0f)
                .setDuration(250)
                .withEndAction(() -> {
                    super.finish();
                    overridePendingTransition(0, 0);
                })
                .start();
    }

    @Override
    protected void onDestroy() {
        progressHandler.removeCallbacks(progressRunnable);
        super.onDestroy();
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
            getContentResolver().delete(target, null, null);
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
