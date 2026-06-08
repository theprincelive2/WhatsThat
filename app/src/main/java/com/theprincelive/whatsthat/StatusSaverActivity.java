package com.theprincelive.whatsthat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StatusSaverActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_STATUS_TREE = "status_tree_uri";
    private static final String PREF_STATUS_TREE_WHATSAPP = "status_tree_uri_whatsapp";
    private static final String PREF_STATUS_TREE_BUSINESS = "status_tree_uri_business";
    private static final String PREF_STATUS_MODE = "status_mode";
    private static final String MODE_WHATSAPP = "whatsapp";
    private static final String MODE_BUSINESS = "business";
    private static final String FILTER_ALL = "all";
    private static final String FILTER_PHOTOS = "photos";
    private static final String FILTER_VIDEOS = "videos";
    private static final int REQUEST_STATUS_FOLDER = 44;
    private static final int REQUEST_WRITE_STORAGE = 45;
    private static final String WHATSAPP_STATUS_DOC = "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses";
    private static final String BUSINESS_STATUS_DOC = "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses";

    Button findWhatsAppBtn;
    Button findBusinessBtn;
    Button chooseBtn;
    Button refreshBtn;
    Button allFilterBtn;
    Button photosFilterBtn;
    Button videosFilterBtn;
    Button saveSelectedBtn;
    Button clearSelectionBtn;
    LinearLayout selectionRow;
    LinearLayout previewCard;
    ImageView previewImage;
    TextView previewLabel;
    TextView previewMeta;
    TextView previewHint;
    Button previewOpenBtn;
    Button previewSaveBtn;
    TextView galleryHeading;
    HorizontalScrollView galleryScroller;
    LinearLayout galleryStrip;
    TextView listHeading;
    TextView sourceText;
    TextView folderText;
    TextView emptyText;
    Button openWhatsAppBtn;
    EditText searchBox;
    ListView listView;
    List<StatusFile> allFiles = new ArrayList<>();
    List<StatusFile> visibleFiles = new ArrayList<>();
    List<StatusFile> pendingBulkSave = new ArrayList<>();
    Set<String> selectedUris = new HashSet<>();
    int pendingBulkSkipped = 0;
    StatusFile pendingSave;
    String pendingMode;
    String activeFilter = FILTER_ALL;
    boolean newestFirst = true;
    boolean refreshAfterWhatsAppLaunch = false;
    StatusFile featuredFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(18), dp(24), dp(18), dp(18));

        root.addView(BackNav.button(this, false), new LinearLayout.LayoutParams(dp(96), dp(42)));

        TextView title = new TextView(this);
        title.setText("Status Saver");
        title.setTextColor(Color.rgb(17, 27, 24));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        title.setPadding(0, dp(14), 0, 0);
        root.addView(title);

        TextView intro = copy("1. View a status in WhatsApp.\n2. Approve the .Statuses folder once.\n3. Return here to preview and save.");
        root.addView(intro);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        findWhatsAppBtn = primaryButton("Find WhatsApp");
        findWhatsAppBtn.setOnClickListener(v -> selectMode(MODE_WHATSAPP));
        buttonRow.addView(findWhatsAppBtn, new LinearLayout.LayoutParams(0, dp(50), 1));

        findBusinessBtn = secondaryButton("Business");
        findBusinessBtn.setOnClickListener(v -> selectMode(MODE_BUSINESS));
        LinearLayout.LayoutParams businessParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        businessParams.setMargins(dp(10), 0, 0, 0);
        buttonRow.addView(findBusinessBtn, businessParams);
        root.addView(buttonRow, buttonParams(16));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        refreshBtn = primaryButton("Refresh");
        refreshBtn.setOnClickListener(v -> {
            loadStatuses();
            Toast.makeText(this, "Statuses refreshed.", Toast.LENGTH_SHORT).show();
        });
        actionRow.addView(refreshBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        chooseBtn = secondaryButton("Options");
        chooseBtn.setOnClickListener(v -> showMoreActions());
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        moreParams.setMargins(dp(10), 0, 0, 0);
        actionRow.addView(chooseBtn, moreParams);
        root.addView(actionRow, buttonParams(10));

        searchBox = new EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Search statuses");
        searchBox.setTextSize(14);
        searchBox.setPadding(dp(14), 0, dp(14), 0);
        searchBox.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedUris.clear();
                applyFilter();
            }
            public void afterTextChanged(Editable s) { }
        });
        root.addView(searchBox, buttonParams(10));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        allFilterBtn = primaryButton("All media");
        allFilterBtn.setOnClickListener(v -> setFilter(FILTER_ALL));
        filterRow.addView(allFilterBtn, new LinearLayout.LayoutParams(0, dp(42), 1));

        photosFilterBtn = secondaryButton("Photos");
        photosFilterBtn.setOnClickListener(v -> setFilter(FILTER_PHOTOS));
        LinearLayout.LayoutParams photosParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        photosParams.setMargins(dp(8), 0, 0, 0);
        filterRow.addView(photosFilterBtn, photosParams);

        videosFilterBtn = secondaryButton("Videos");
        videosFilterBtn.setOnClickListener(v -> setFilter(FILTER_VIDEOS));
        LinearLayout.LayoutParams videosParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        videosParams.setMargins(dp(8), 0, 0, 0);
        filterRow.addView(videosFilterBtn, videosParams);
        root.addView(filterRow, buttonParams(10));

        selectionRow = new LinearLayout(this);
        selectionRow.setOrientation(LinearLayout.HORIZONTAL);
        saveSelectedBtn = primaryButton("Save selected");
        saveSelectedBtn.setOnClickListener(v -> saveSelectedStatuses());
        selectionRow.addView(saveSelectedBtn, new LinearLayout.LayoutParams(0, dp(44), 1));

        clearSelectionBtn = secondaryButton("Clear");
        clearSelectionBtn.setOnClickListener(v -> clearSelection());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        clearParams.setMargins(dp(8), 0, 0, 0);
        selectionRow.addView(clearSelectionBtn, clearParams);
        root.addView(selectionRow, buttonParams(10));

        sourceText = copy("");
        sourceText.setTextColor(Color.rgb(0, 107, 85));
        sourceText.setTypeface(Typeface.DEFAULT_BOLD);
        sourceText.setPadding(0, dp(14), 0, 0);
        root.addView(sourceText);

        folderText = copy("");
        folderText.setPadding(0, dp(6), 0, dp(6));
        root.addView(folderText);

        previewCard = buildPreviewCard();
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.setMargins(0, dp(10), 0, 0);
        root.removeView(searchBox);
        root.removeView(filterRow);
        root.removeView(selectionRow);
        root.addView(previewCard, previewParams);

        galleryHeading = copy("Media gallery");
        galleryHeading.setTextColor(Color.rgb(17, 27, 24));
        galleryHeading.setTypeface(Typeface.DEFAULT_BOLD);
        galleryHeading.setPadding(0, dp(12), 0, dp(4));
        root.addView(galleryHeading);

        galleryStrip = new LinearLayout(this);
        galleryStrip.setOrientation(LinearLayout.HORIZONTAL);
        galleryStrip.setPadding(0, 0, dp(2), 0);
        galleryScroller = new HorizontalScrollView(this);
        galleryScroller.setHorizontalScrollBarEnabled(false);
        galleryScroller.addView(galleryStrip, new HorizontalScrollView.LayoutParams(-2, -2));
        root.addView(galleryScroller, new LinearLayout.LayoutParams(-1, dp(92)));

        root.addView(searchBox, buttonParams(10));
        root.addView(filterRow, buttonParams(10));
        root.addView(selectionRow, buttonParams(10));

        emptyText = copy("");
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(16), dp(28), dp(16), dp(28));
        root.addView(emptyText, new LinearLayout.LayoutParams(-1, -2));

        openWhatsAppBtn = primaryButton("Open WhatsApp");
        openWhatsAppBtn.setOnClickListener(v -> openActiveWhatsApp());
        root.addView(openWhatsAppBtn, buttonParams(4));

        listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            StatusFile file = visibleFiles.get(position);
            if (!selectedUris.isEmpty()) {
                toggleSelection(file);
            } else {
                featuredFile = file;
                updateFeaturedStatus(findStatusDocumentId(savedTree(activeMode())) != null);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelection(visibleFiles.get(position));
            return true;
        });

        listHeading = copy("Recent statuses");
        listHeading.setTextColor(Color.rgb(17, 27, 24));
        listHeading.setTypeface(Typeface.DEFAULT_BOLD);
        listHeading.setPadding(0, dp(10), 0, dp(4));
        root.addView(listHeading);
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        migrateOldStatusFolder();
        loadStatuses();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (listView != null) {
            loadStatuses();
            if (refreshAfterWhatsAppLaunch) {
                refreshAfterWhatsAppLaunch = false;
                Toast.makeText(this, "Statuses refreshed.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void setFilter(String filter) {
        selectedUris.clear();
        activeFilter = filter;
        applyFilter();
    }

    void selectMode(String mode) {
        prefs().edit().putString(PREF_STATUS_MODE, mode).apply();
        Uri saved = savedTree(mode);
        if (saved == null) {
            chooseStatusFolder(MODE_BUSINESS.equals(mode) ? BUSINESS_STATUS_DOC : WHATSAPP_STATUS_DOC, mode);
        } else {
            loadStatuses();
        }
    }

    void showMoreActions() {
        String[] actions = {
                "Saved Statuses",
                "Change " + modeLabel(activeMode()) + " Folder",
                newestFirst ? "Sort: Oldest First" : "Sort: Newest First"
        };
        new AlertDialog.Builder(this)
                .setTitle("Status Saver")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, SavedStatusesActivity.class));
                    } else if (which == 1) {
                        chooseStatusFolder(null, activeMode());
                    } else {
                        newestFirst = !newestFirst;
                        sortAllFiles();
                        applyFilter();
                    }
                })
                .show();
    }

    void chooseStatusFolder(String initialDocumentId, String mode) {
        pendingMode = mode;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialDocumentId != null) {
            Uri initialUri = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", initialDocumentId);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
        }
        startActivityForResult(intent, REQUEST_STATUS_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_STATUS_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri treeUri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        getContentResolver().takePersistableUriPermission(treeUri, flags);
        String mode = pendingMode == null ? activeMode() : pendingMode;
        pendingMode = null;
        prefs().edit()
                .putString(PREF_STATUS_MODE, mode)
                .putString(prefKeyForMode(mode), treeUri.toString())
                .apply();
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        if (findStatusDocumentId(treeUri) != null && treeDocumentId != null && !treeDocumentId.endsWith("/.Statuses")) {
            Toast.makeText(this, "Found .Statuses inside the selected folder.", Toast.LENGTH_SHORT).show();
        }
        loadStatuses();
    }

    void loadStatuses() {
        allFiles.clear();
        visibleFiles.clear();
        selectedUris.clear();
        updateProviderButtons();
        updateFilterButtons();
        String mode = activeMode();
        Uri tree = savedTree(mode);
        if (tree == null) {
            sourceText.setText("Set up " + modeLabel(mode) + " statuses");
            folderText.setText(modeLabel(mode) + " status folder is not set.");
            emptyText.setText("Tap " + modeLabel(mode) + ", approve the .Statuses folder once, then viewed statuses will appear here.");
            emptyText.setVisibility(View.VISIBLE);
            updateOpenWhatsAppButton(false);
            listView.setVisibility(View.GONE);
            if (listHeading != null) listHeading.setVisibility(View.GONE);
            updateFeaturedStatus(false);
            updateGalleryStrip(false);
            updateSelectionActions();
            listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
            return;
        }
        folderText.setText(modeLabel(mode) + " status folder is set. Use Options to change it.");
        boolean hasStatusFolder = findStatusDocumentId(tree) != null;
        allFiles.addAll(queryStatuses(tree));
        sortAllFiles();
        markSavedStatuses();
        applyFilter(hasStatusFolder);
    }

    void applyFilter() {
        applyFilter(findStatusDocumentId(savedTree(activeMode())) != null);
    }

    void applyFilter(boolean hasStatusFolder) {
        visibleFiles.clear();
        sortAllFiles();
        String query = searchQuery();
        for (StatusFile file : allFiles) {
            if (FILTER_PHOTOS.equals(activeFilter) && !file.isImage()) continue;
            if (FILTER_VIDEOS.equals(activeFilter) && !file.isVideo()) continue;
            if (!query.isEmpty() && !file.name.toLowerCase(Locale.getDefault()).contains(query)) continue;
            visibleFiles.add(file);
        }
        updateFilterButtons();
        sourceText.setText(statusSummary(hasStatusFolder));
        emptyText.setText(emptyMessage(hasStatusFolder));
        emptyText.setVisibility(visibleFiles.isEmpty() ? View.VISIBLE : View.GONE);
        updateOpenWhatsAppButton(shouldShowOpenWhatsAppButton(hasStatusFolder));
        listView.setVisibility(visibleFiles.isEmpty() ? View.GONE : View.VISIBLE);
        if (listHeading != null) {
            listHeading.setText(visibleFiles.size() <= 1 ? "Recent status" : "Recent statuses");
            listHeading.setVisibility(visibleFiles.isEmpty() ? View.GONE : View.VISIBLE);
        }
        updateFeaturedStatus(hasStatusFolder);
        updateGalleryStrip(hasStatusFolder);
        updateSelectionActions();
        listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
    }

    LinearLayout buildPreviewCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(247, 250, 248));
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), Color.rgb(225, 232, 227));
        card.setBackground(bg);

        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        previewImage.setBackgroundColor(Color.rgb(17, 27, 24));
        card.addView(previewImage, new LinearLayout.LayoutParams(-1, dp(170)));

        previewLabel = new TextView(this);
        previewLabel.setTextColor(Color.rgb(17, 27, 24));
        previewLabel.setTextSize(18);
        previewLabel.setTypeface(Typeface.DEFAULT_BOLD);
        previewLabel.setPadding(0, dp(12), 0, 0);
        card.addView(previewLabel);

        previewMeta = copy("");
        previewMeta.setPadding(0, dp(5), 0, 0);
        card.addView(previewMeta);

        previewHint = copy("");
        previewHint.setPadding(0, dp(5), 0, 0);
        card.addView(previewHint);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(12), 0, 0);

        previewOpenBtn = primaryButton("Preview");
        previewOpenBtn.setOnClickListener(v -> {
            if (featuredFile != null) openStatusPreview(featuredFile);
        });
        actions.addView(previewOpenBtn, new LinearLayout.LayoutParams(0, dp(46), 1));

        previewSaveBtn = secondaryButton("Save");
        previewSaveBtn.setOnClickListener(v -> {
            if (featuredFile != null) saveStatus(featuredFile);
        });
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        saveParams.setMargins(dp(10), 0, 0, 0);
        actions.addView(previewSaveBtn, saveParams);
        card.addView(actions);

        card.setOnClickListener(v -> {
            if (featuredFile != null) openStatusPreview(featuredFile);
        });
        return card;
    }

    void updateFeaturedStatus(boolean hasStatusFolder) {
        if (previewCard == null) return;
        if (!hasStatusFolder || visibleFiles.isEmpty()) {
            featuredFile = null;
            previewCard.setVisibility(View.GONE);
            return;
        }
        if (featuredFile == null || !containsVisibleFile(featuredFile)) {
            featuredFile = visibleFiles.get(0);
        }
        previewCard.setVisibility(View.VISIBLE);
        previewImage.setPadding(0, 0, 0, 0);
        if (featuredFile.isImage()) {
            previewImage.setImageURI(featuredFile.uri);
        } else {
            Bitmap thumbnail = videoThumbnail(featuredFile);
            if (thumbnail != null) {
                previewImage.setImageBitmap(thumbnail);
            } else {
                previewImage.setImageResource(R.drawable.ic_video);
                previewImage.setPadding(dp(54), dp(54), dp(54), dp(54));
            }
        }
        previewLabel.setText(featuredFile.isVideo() ? "Video status" : "Photo status");
        previewMeta.setText(featuredFile.name + " - " + sizeText(featuredFile.size));
        previewHint.setText(featuredFile.modifiedAt > 0
                ? DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault()).format(new Date(featuredFile.modifiedAt))
                : "Tap another status below to preview it here.");
        previewSaveBtn.setText(featuredFile.saved ? "Saved" : "Save");
        previewSaveBtn.setEnabled(!featuredFile.saved);
        styleButton(previewSaveBtn, false);
    }

    boolean containsVisibleFile(StatusFile file) {
        String key = file.uri.toString();
        for (StatusFile visible : visibleFiles) {
            if (visible.uri.toString().equals(key)) return true;
        }
        return false;
    }

    void updateGalleryStrip(boolean hasStatusFolder) {
        if (galleryHeading == null || galleryScroller == null || galleryStrip == null) return;
        galleryStrip.removeAllViews();
        if (!hasStatusFolder || visibleFiles.isEmpty()) {
            galleryHeading.setVisibility(View.GONE);
            galleryScroller.setVisibility(View.GONE);
            return;
        }
        galleryHeading.setVisibility(View.VISIBLE);
        galleryScroller.setVisibility(View.VISIBLE);
        galleryHeading.setText("Media gallery (" + visibleFiles.size() + ")");
        String featuredKey = featuredFile == null ? "" : featuredFile.uri.toString();
        for (StatusFile file : visibleFiles) {
            galleryStrip.addView(galleryTile(file, file.uri.toString().equals(featuredKey)));
        }
    }

    View galleryTile(StatusFile file, boolean selected) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(3), dp(3), dp(3), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? Color.rgb(229, 245, 236) : Color.rgb(247, 248, 246));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(selected ? 2 : 1), selected ? Color.rgb(0, 107, 85) : Color.rgb(225, 232, 227));
        tile.setBackground(bg);

        ImageView thumb = new ImageView(this);
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(Color.rgb(17, 27, 24));
        if (file.isImage()) {
            thumb.setImageURI(file.uri);
        } else {
            Bitmap thumbnail = videoThumbnail(file);
            if (thumbnail != null) {
                thumb.setImageBitmap(thumbnail);
            } else {
                thumb.setImageResource(R.drawable.ic_video);
                thumb.setPadding(dp(18), dp(18), dp(18), dp(18));
            }
        }
        tile.addView(thumb, new LinearLayout.LayoutParams(dp(68), dp(68)));
        tile.setOnClickListener(v -> {
            featuredFile = file;
            updateFeaturedStatus(findStatusDocumentId(savedTree(activeMode())) != null);
            updateGalleryStrip(true);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(82), dp(82));
        params.setMargins(0, 0, dp(8), 0);
        tile.setLayoutParams(params);
        return tile;
    }

    String sizeText(long size) {
        if (size <= 0) return "unknown size";
        if (size >= 1024 * 1024) return String.format(Locale.getDefault(), "%.1f MB", size / 1024f / 1024f);
        return String.format(Locale.getDefault(), "%.0f KB", size / 1024f);
    }

    Bitmap videoThumbnail(StatusFile file) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, file.uri);
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
        if (selectionRow == null || saveSelectedBtn == null || clearSelectionBtn == null) return;
        int count = selectedUris.size();
        int visibility = count == 0 ? View.GONE : View.VISIBLE;
        selectionRow.setVisibility(visibility);
        saveSelectedBtn.setText(count == 0 ? "Save selected" : "Save selected (" + count + ")");
    }

    String emptyMessage(boolean hasStatusFolder) {
        if (!hasStatusFolder) return "That folder does not contain .Statuses. Tap Find WhatsApp or choose WhatsApp > Media > .Statuses.";
        if (allFiles.isEmpty()) return "Open WhatsApp, view a status, then return here and tap Refresh.";
        if (!searchQuery().isEmpty()) return "No statuses match your search.";
        if (FILTER_PHOTOS.equals(activeFilter)) return "No photo statuses in this folder right now.";
        if (FILTER_VIDEOS.equals(activeFilter)) return "No video statuses in this folder right now.";
        return "No statuses found.";
    }

    boolean shouldShowOpenWhatsAppButton(boolean hasStatusFolder) {
        return hasStatusFolder && allFiles.isEmpty() && visibleFiles.isEmpty() && searchQuery().isEmpty();
    }

    void updateOpenWhatsAppButton(boolean show) {
        if (openWhatsAppBtn == null) return;
        openWhatsAppBtn.setText("Open " + modeLabel(activeMode()));
        openWhatsAppBtn.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    void openActiveWhatsApp() {
        String pkg = MODE_BUSINESS.equals(activeMode()) ? "com.whatsapp.w4b" : "com.whatsapp";
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Toast.makeText(this, modeLabel(activeMode()) + " is not installed on this phone.", Toast.LENGTH_SHORT).show();
            return;
        }
        refreshAfterWhatsAppLaunch = true;
        startActivity(launch);
    }

    void sortAllFiles() {
        Collections.sort(allFiles, (a, b) -> newestFirst ? Long.compare(b.modifiedAt, a.modifiedAt) : Long.compare(a.modifiedAt, b.modifiedAt));
    }

    String searchQuery() {
        return searchBox == null ? "" : searchBox.getText().toString().trim().toLowerCase(Locale.getDefault());
    }

    List<StatusFile> queryStatuses(Uri treeUri) {
        ArrayList<StatusFile> out = new ArrayList<>();
        String statusDocumentId = findStatusDocumentId(treeUri);
        if (statusDocumentId == null) return out;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, statusDocumentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return out;
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.getLong(3);
                long modifiedAt = cursor.getLong(4);
                if (mime == null || (!mime.startsWith("image/") && !mime.startsWith("video/"))) continue;
                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                out.add(new StatusFile(name == null ? "status" : name, mime, fileUri, size, modifiedAt));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not read that folder. Choose the .Statuses folder again.", Toast.LENGTH_LONG).show();
            prefs().edit().remove(prefKeyForMode(activeMode())).apply();
        }
        return out;
    }

    String findStatusDocumentId(Uri selectedTree) {
        if (selectedTree == null) return null;
        String selectedId = DocumentsContract.getTreeDocumentId(selectedTree);
        if (selectedId != null && selectedId.endsWith("/.Statuses")) return selectedId;
        if (selectedId == null) return null;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(selectedTree, selectedId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return null;
            while (cursor.moveToNext()) {
                String childId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                boolean folder = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (folder && ".Statuses".equals(name)) {
                    return childId;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    void showStatusActions(StatusFile file) {
        String[] actions = {"Save to gallery", "Share"};
        new AlertDialog.Builder(this)
                .setTitle(file.isVideo() ? "Video status" : "Photo status")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) saveStatus(file);
                    if (which == 1) shareStatus(file);
                })
                .show();
    }

    void openStatusPreview(StatusFile file) {
        Intent intent = new Intent(this, StatusPreviewActivity.class);
        intent.putExtra(StatusPreviewActivity.EXTRA_URI, file.uri.toString());
        intent.putExtra(StatusPreviewActivity.EXTRA_NAME, file.name);
        intent.putExtra(StatusPreviewActivity.EXTRA_MIME, file.mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(intent);
    }

    void saveStatus(StatusFile file) {
        if (file.saved) {
            Toast.makeText(this, "This status is already saved.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (needsLegacyWritePermission()) {
            pendingSave = file;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
        copyStatusToGallery(file, true);
    }

    void saveSelectedStatuses() {
        pendingBulkSave.clear();
        pendingBulkSkipped = 0;
        int skipped = 0;
        for (StatusFile file : visibleFiles) {
            if (selectedUris.contains(file.uri.toString())) {
                if (file.saved) {
                    skipped++;
                } else {
                    pendingBulkSave.add(file);
                }
            }
        }
        pendingBulkSkipped = skipped;
        if (pendingBulkSave.isEmpty()) {
            clearSelection();
            pendingBulkSkipped = 0;
            Toast.makeText(this, skipped == 0 ? "No statuses selected." : "Selected statuses are already saved.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (needsLegacyWritePermission()) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
        copySelectedToGallery();
    }

    boolean needsLegacyWritePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WRITE_STORAGE && pendingSave != null) {
            StatusFile file = pendingSave;
            pendingSave = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copyStatusToGallery(file, true);
            } else {
                Toast.makeText(this, "Storage permission is needed to save on this Android version.", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_WRITE_STORAGE && !pendingBulkSave.isEmpty()) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                copySelectedToGallery();
            } else {
                pendingBulkSave.clear();
                pendingBulkSkipped = 0;
                Toast.makeText(this, "Storage permission is needed to save on this Android version.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void copySelectedToGallery() {
        int saved = 0;
        for (StatusFile file : pendingBulkSave) {
            if (copyStatusToGallery(file, false)) saved++;
        }
        pendingBulkSave.clear();
        markSavedStatuses();
        clearSelection();
        Toast.makeText(this, saveSummary(saved, pendingBulkSkipped), Toast.LENGTH_SHORT).show();
        pendingBulkSkipped = 0;
    }

    String saveSummary(int saved, int skipped) {
        if (skipped > 0) {
            return "Saved " + saved + statusCountLabel(saved) + ". " + skipped + " already saved.";
        }
        return "Saved " + saved + statusCountLabel(saved) + ".";
    }

    boolean copyStatusToGallery(StatusFile file, boolean showToast) {
        Uri target = createMediaTarget(file);
        if (target == null) {
            if (showToast) Toast.makeText(this, "Could not create save location.", Toast.LENGTH_SHORT).show();
            return false;
        }
        try (InputStream input = getContentResolver().openInputStream(file.uri);
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
            if (showToast) Toast.makeText(this, "Status saved to gallery.", Toast.LENGTH_SHORT).show();
            file.saved = true;
            return true;
        } catch (IOException e) {
            getContentResolver().delete(target, null, null);
            if (showToast) Toast.makeText(this, "Could not save this status.", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    Uri createMediaTarget(StatusFile file) {
        String outputName = uniqueName(file.name);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, outputName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, file.isVideo() ? "Movies/WhatsThat/Status Videos" : "Pictures/WhatsThat/Status Photos");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            File folder = new File(android.os.Environment.getExternalStoragePublicDirectory(file.isVideo() ? android.os.Environment.DIRECTORY_MOVIES : android.os.Environment.DIRECTORY_PICTURES), file.isVideo() ? "WhatsThat/Status Videos" : "WhatsThat/Status Photos");
            if (!folder.exists()) folder.mkdirs();
            values.put(MediaStore.MediaColumns.DATA, new File(folder, outputName).getAbsolutePath());
        }
        Uri collection = file.isVideo() ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return getContentResolver().insert(collection, values);
    }

    void shareStatus(StatusFile file) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(file.mimeType);
        send.putExtra(Intent.EXTRA_STREAM, file.uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(send, "Share status"));
    }

    String uniqueName(String name) {
        String safe = name == null || name.trim().isEmpty() ? "status" : name;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return base + "_whatsthat_" + System.currentTimeMillis() + ext;
    }

    void markSavedStatuses() {
        Set<String> savedNames = SavedStatusIndex.load(this);
        for (StatusFile file : allFiles) {
            file.saved = SavedStatusIndex.isSaved(savedNames, file.name);
        }
    }

    Uri savedTree() {
        return savedTree(activeMode());
    }

    Uri savedTree(String mode) {
        String raw = prefs().getString(prefKeyForMode(mode), null);
        return raw == null ? null : Uri.parse(raw);
    }

    String activeMode() {
        return prefs().getString(PREF_STATUS_MODE, MODE_WHATSAPP);
    }

    String prefKeyForMode(String mode) {
        return MODE_BUSINESS.equals(mode) ? PREF_STATUS_TREE_BUSINESS : PREF_STATUS_TREE_WHATSAPP;
    }

    String modeLabel(String mode) {
        return MODE_BUSINESS.equals(mode) ? "WhatsApp Business" : "WhatsApp";
    }

    String statusCountLabel(int count) {
        return count == 1 ? " status" : " statuses";
    }

    String filterSuffix() {
        if (FILTER_PHOTOS.equals(activeFilter)) return " - Photos";
        if (FILTER_VIDEOS.equals(activeFilter)) return " - Videos";
        return "";
    }

    String statusSummary(boolean hasStatusFolder) {
        if (!hasStatusFolder) return modeLabel(activeMode()) + " folder needs .Statuses";
        if (allFiles.isEmpty()) return modeLabel(activeMode()) + " ready";
        return modeLabel(activeMode()) + " - " + visibleFiles.size() + statusCountLabel(visibleFiles.size()) + filterSuffix();
    }

    void updateProviderButtons() {
        if (findWhatsAppBtn == null || findBusinessBtn == null || chooseBtn == null || refreshBtn == null) return;
        String mode = activeMode();
        boolean whatsappSet = savedTree(MODE_WHATSAPP) != null;
        boolean businessSet = savedTree(MODE_BUSINESS) != null;
        findWhatsAppBtn.setText(whatsappSet ? "WhatsApp" : "Set WhatsApp");
        findBusinessBtn.setText(businessSet ? "Business" : "Set Business");
        styleButton(findWhatsAppBtn, MODE_WHATSAPP.equals(mode));
        styleButton(findBusinessBtn, MODE_BUSINESS.equals(mode));
        chooseBtn.setText("Options");
        refreshBtn.setEnabled(savedTree(mode) != null);
    }

    void updateFilterButtons() {
        if (allFilterBtn == null || photosFilterBtn == null || videosFilterBtn == null) return;
        allFilterBtn.setText("All media (" + countFilesForFilter(FILTER_ALL) + ")");
        photosFilterBtn.setText("Photos (" + countFilesForFilter(FILTER_PHOTOS) + ")");
        videosFilterBtn.setText("Videos (" + countFilesForFilter(FILTER_VIDEOS) + ")");
        styleButton(allFilterBtn, FILTER_ALL.equals(activeFilter));
        styleButton(photosFilterBtn, FILTER_PHOTOS.equals(activeFilter));
        styleButton(videosFilterBtn, FILTER_VIDEOS.equals(activeFilter));
    }

    int countFilesForFilter(String filter) {
        String query = searchQuery();
        int count = 0;
        for (StatusFile file : allFiles) {
            if (FILTER_PHOTOS.equals(filter) && !file.isImage()) continue;
            if (FILTER_VIDEOS.equals(filter) && !file.isVideo()) continue;
            if (!query.isEmpty() && !file.name.toLowerCase(Locale.getDefault()).contains(query)) continue;
            count++;
        }
        return count;
    }

    void migrateOldStatusFolder() {
        SharedPreferences preferences = prefs();
        String old = preferences.getString(PREF_STATUS_TREE, null);
        if (old != null && preferences.getString(PREF_STATUS_TREE_WHATSAPP, null) == null) {
            preferences.edit()
                    .putString(PREF_STATUS_TREE_WHATSAPP, old)
                    .remove(PREF_STATUS_TREE)
                    .apply();
        }
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
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

    Button primaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, true);
        return button;
    }

    Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        styleButton(button, false);
        return button;
    }

    void styleButton(Button button, boolean primary) {
        button.setTextColor(primary ? Color.WHITE : Color.rgb(0, 107, 85));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? Color.rgb(0, 107, 85) : Color.rgb(247, 248, 246));
        bg.setCornerRadius(dp(18));
        if (!primary) bg.setStroke(dp(1), Color.rgb(231, 234, 230));
        button.setBackground(bg);
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
