package com.theprincelive.whatsthat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SavedStatusesActivity extends Activity {
    LinearLayout selectionRow;
    Button deleteSelectedBtn;
    Button clearSelectionBtn;
    Button sortBtn;
    TextView countText;
    TextView emptyText;
    EditText searchBox;
    ListView listView;
    List<StatusFile> savedFiles = new ArrayList<>();
    List<StatusFile> visibleFiles = new ArrayList<>();
    Set<String> selectedUris = new HashSet<>();
    boolean newestFirst = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#F2F2F7"));
        root.setPadding(dp(18), dp(24), dp(18), dp(18));

        root.addView(BackNav.button(this, false), new LinearLayout.LayoutParams(dp(96), dp(42)));

        TextView title = new TextView(this);
        title.setText("Saved Statuses");
        title.setTextColor(Color.parseColor("#1C1C1E"));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setPadding(0, dp(14), 0, 0);
        root.addView(title);

        TextView intro = copy("Photos and videos saved from WhatsThat appear here after they are copied to your phone.");
        intro.setTextColor(Color.parseColor("#8E8E93"));
        root.addView(intro);

        Button refresh = new Button(this);
        refresh.setText("Refresh Saved Statuses");
        refresh.setAllCaps(false);
        refresh.setTextSize(14);
        refresh.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        refresh.setTextColor(Color.parseColor("#007AFF"));
        GradientDrawable refreshBg = new GradientDrawable();
        refreshBg.setColor(Color.WHITE);
        refreshBg.setCornerRadius(dp(12));
        refreshBg.setStroke(dp(1), Color.parseColor("#E5E5EA"));
        refresh.setBackground(refreshBg);
        refresh.setOnClickListener(v -> loadSavedStatuses());
        root.addView(refresh, buttonParams(14));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Search saved statuses");
        searchBox.setTextSize(14);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.parseColor("#E5E5EA"));
        searchBg.setCornerRadius(dp(10));
        searchBox.setBackground(searchBg);
        searchBox.setPadding(dp(14), dp(10), dp(14), dp(10));
        searchBox.setTextColor(Color.parseColor("#1C1C1E"));
        searchBox.setHintTextColor(Color.parseColor("#8E8E93"));
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedUris.clear();
                applySearchAndSort();
            }
            public void afterTextChanged(Editable s) { }
        });
        root.addView(searchBox, buttonParams(10));

        sortBtn = new Button(this);
        sortBtn.setText("Sort: Newest");
        sortBtn.setAllCaps(false);
        sortBtn.setTextSize(14);
        sortBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        sortBtn.setTextColor(Color.parseColor("#007AFF"));
        GradientDrawable sortBg = new GradientDrawable();
        sortBg.setColor(Color.WHITE);
        sortBg.setCornerRadius(dp(12));
        sortBg.setStroke(dp(1), Color.parseColor("#E5E5EA"));
        sortBtn.setBackground(sortBg);
        sortBtn.setOnClickListener(v -> {
            newestFirst = !newestFirst;
            applySearchAndSort();
        });
        root.addView(sortBtn, buttonParams(10));

        selectionRow = new LinearLayout(this);
        selectionRow.setOrientation(LinearLayout.HORIZONTAL);
        deleteSelectedBtn = dangerButton("Delete selected");
        deleteSelectedBtn.setOnClickListener(v -> confirmDeleteSelected());
        selectionRow.addView(deleteSelectedBtn, new LinearLayout.LayoutParams(0, dp(44), 1));

        clearSelectionBtn = new Button(this);
        clearSelectionBtn.setText("Clear");
        clearSelectionBtn.setAllCaps(false);
        clearSelectionBtn.setTextSize(14);
        clearSelectionBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        clearSelectionBtn.setTextColor(Color.parseColor("#007AFF"));
        GradientDrawable clearBg = new GradientDrawable();
        clearBg.setColor(Color.WHITE);
        clearBg.setCornerRadius(dp(12));
        clearBg.setStroke(dp(1), Color.parseColor("#E5E5EA"));
        clearSelectionBtn.setBackground(clearBg);
        clearSelectionBtn.setOnClickListener(v -> clearSelection());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        selectionRow.addView(clearSelectionBtn, clearParams);
        root.addView(selectionRow, buttonParams(10));

        countText = copy("");
        countText.setTextColor(Color.parseColor("#8E8E93"));
        countText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        countText.setPadding(0, dp(14), 0, dp(6));
        root.addView(countText);

        emptyText = copy("");
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(16), dp(28), dp(16), dp(28));
        root.addView(emptyText, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            StatusFile file = visibleFiles.get(position);
            if (!selectedUris.isEmpty()) {
                toggleSelection(file);
            } else {
                openPreview(file);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelection(visibleFiles.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        loadSavedStatuses();
    }

    void loadSavedStatuses() {
        savedFiles.clear();
        visibleFiles.clear();
        selectedUris.clear();
        savedFiles.addAll(querySaved(false));
        savedFiles.addAll(querySaved(true));
        applySearchAndSort();
    }

    void applySearchAndSort() {
        visibleFiles.clear();
        String query = searchQuery();
        for (StatusFile file : savedFiles) {
            if (!query.isEmpty() && !file.name.toLowerCase(Locale.getDefault()).contains(query)) continue;
            visibleFiles.add(file);
        }
        Collections.sort(visibleFiles, (a, b) -> newestFirst ? Long.compare(b.modifiedAt, a.modifiedAt) : Long.compare(a.modifiedAt, b.modifiedAt));
        if (sortBtn != null) sortBtn.setText(newestFirst ? "Sort: Newest" : "Sort: Oldest");
        countText.setText(savedCountSummary());
        emptyText.setText(emptySavedMessage());
        emptyText.setVisibility(visibleFiles.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(visibleFiles.isEmpty() ? View.GONE : View.VISIBLE);
        updateSelectionActions();
        listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
    }

    String emptySavedMessage() {
        if (savedFiles.isEmpty()) return "No saved statuses yet. Save a photo or video from Status Saver first.";
        return "No saved statuses match your search.";
    }

    String savedCountSummary() {
        int photos = 0;
        int videos = 0;
        for (StatusFile file : visibleFiles) {
            if (file.isVideo()) {
                videos++;
            } else if (file.isImage()) {
                photos++;
            }
        }
        return visibleFiles.size() + statusCountLabel(visibleFiles.size()) + " saved - "
                + photos + photoCountLabel(photos) + ", "
                + videos + videoCountLabel(videos);
    }

    String searchQuery() {
        return searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.getDefault());
    }

    void toggleSelection(StatusFile file) {
        String key = file.uri.toString();
        if (selectedUris.contains(key)) {
            selectedUris.remove(key);
        } else {
            selectedUris.add(key);
        }
        updateSelectionActions();
        listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
    }

    void clearSelection() {
        selectedUris.clear();
        updateSelectionActions();
        listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
    }

    void updateSelectionActions() {
        if (selectionRow == null || deleteSelectedBtn == null) return;
        int count = selectedUris.size();
        selectionRow.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        deleteSelectedBtn.setText(count == 0 ? "Delete selected" : "Delete selected (" + count + ")");
    }

    void confirmDeleteSelected() {
        int count = selectedUris.size();
        if (count == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete saved statuses?")
                .setMessage("This removes " + count + statusCountLabel(count) + " from your phone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelected())
                .setNegativeButton("Cancel", null)
                .show();
    }

    void deleteSelected() {
        int deleted = 0;
        ArrayList<String> targets = new ArrayList<>(selectedUris);
        for (String rawUri : targets) {
            try {
                deleted += getContentResolver().delete(Uri.parse(rawUri), null, null);
            } catch (Exception ignored) {
            }
        }
        loadSavedStatuses();
        Toast.makeText(this, "Deleted " + deleted + statusCountLabel(deleted) + ".", Toast.LENGTH_SHORT).show();
    }

    List<StatusFile> querySaved(boolean videos) {
        ArrayList<StatusFile> out = new ArrayList<>();
        Uri collection = videos ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection;
        String[] args;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = MediaStore.MediaColumns.RELATIVE_PATH + "=?";
            args = new String[]{videos ? "Movies/WhatsThat/Status Videos/" : "Pictures/WhatsThat/Status Photos/"};
        } else {
            selection = MediaStore.MediaColumns.DATA + " LIKE ?";
            String folder = new File(android.os.Environment.getExternalStoragePublicDirectory(videos ? android.os.Environment.DIRECTORY_MOVIES : android.os.Environment.DIRECTORY_PICTURES), videos ? "WhatsThat/Status Videos" : "WhatsThat/Status Photos").getAbsolutePath();
            args = new String[]{folder + "%"};
        }
        try (Cursor cursor = getContentResolver().query(collection, columns, selection, args, null)) {
            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.getLong(3);
                long modifiedAt = cursor.getLong(4) * 1000L;
                Uri uri = ContentUris.withAppendedId(collection, id);
                out.add(new StatusFile(name == null ? "saved status" : name, mime, uri, size, modifiedAt));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void openPreview(StatusFile file) {
        Intent intent = new Intent(this, StatusPreviewActivity.class);
        intent.putExtra(StatusPreviewActivity.EXTRA_URI, file.uri.toString());
        intent.putExtra(StatusPreviewActivity.EXTRA_NAME, file.name);
        intent.putExtra(StatusPreviewActivity.EXTRA_MIME, file.mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    String statusCountLabel(int count) {
        return count == 1 ? " status" : " statuses";
    }

    String photoCountLabel(int count) {
        return count == 1 ? " photo" : " photos";
    }

    String videoCountLabel(int count) {
        return count == 1 ? " video" : " videos";
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

    Button dangerButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(Color.parseColor("#FF3B30"));
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

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
