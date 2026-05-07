package com.theprincelive.whatsthat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    Button savedStatusesBtn;
    Button allFilterBtn;
    Button photosFilterBtn;
    Button videosFilterBtn;
    Button saveSelectedBtn;
    Button clearSelectionBtn;
    LinearLayout selectionRow;
    TextView sourceText;
    TextView folderText;
    TextView emptyText;
    ListView listView;
    List<StatusFile> allFiles = new ArrayList<>();
    List<StatusFile> visibleFiles = new ArrayList<>();
    List<StatusFile> pendingBulkSave = new ArrayList<>();
    Set<String> selectedUris = new HashSet<>();
    StatusFile pendingSave;
    String pendingMode;
    String activeFilter = FILTER_ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(18), dp(24), dp(18), dp(18));

        TextView title = new TextView(this);
        title.setText("Status Saver");
        title.setTextColor(Color.rgb(17, 27, 24));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setIncludeFontPadding(false);
        root.addView(title);

        TextView intro = copy("Use Find WhatsApp first. Android still needs you to approve folder access once, but WhatsThat will open the picker near the usual Status folder.");
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

        chooseBtn = secondaryButton("Choose Folder Manually");
        chooseBtn.setOnClickListener(v -> chooseStatusFolder(null, activeMode()));
        root.addView(chooseBtn, buttonParams(10));

        refreshBtn = secondaryButton("Refresh Statuses");
        refreshBtn.setOnClickListener(v -> {
            loadStatuses();
            Toast.makeText(this, "Statuses refreshed.", Toast.LENGTH_SHORT).show();
        });
        root.addView(refreshBtn, buttonParams(10));

        savedStatusesBtn = secondaryButton("Saved Statuses");
        savedStatusesBtn.setOnClickListener(v -> startActivity(new Intent(this, SavedStatusesActivity.class)));
        root.addView(savedStatusesBtn, buttonParams(10));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        allFilterBtn = primaryButton("All");
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
                openStatusPreview(file);
            }
        });
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            toggleSelection(visibleFiles.get(position));
            return true;
        });
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        migrateOldStatusFolder();
        loadStatuses();
    }

    void setFilter(String filter) {
        selectedUris.clear();
        activeFilter = filter;
        applyFilter();
    }

    void selectMode(String mode) {
        String previousMode = activeMode();
        prefs().edit().putString(PREF_STATUS_MODE, mode).apply();
        Uri saved = savedTree(mode);
        if (saved == null || previousMode.equals(mode)) {
            chooseStatusFolder(MODE_BUSINESS.equals(mode) ? BUSINESS_STATUS_DOC : WHATSAPP_STATUS_DOC, mode);
        } else {
            loadStatuses();
        }
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
            sourceText.setText("Viewing " + modeLabel(mode) + " statuses");
            folderText.setText(modeLabel(mode) + " status folder is not set.");
            emptyText.setText("Tap " + modeLabel(mode) + " to approve its .Statuses folder once.");
            emptyText.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
            updateSelectionActions();
            listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
            return;
        }
        folderText.setText(modeLabel(mode) + " status folder is set. Tap the same button again to change it.");
        boolean hasStatusFolder = findStatusDocumentId(tree) != null;
        allFiles.addAll(queryStatuses(tree));
        Collections.sort(allFiles, (a, b) -> Long.compare(b.modifiedAt, a.modifiedAt));
        applyFilter(hasStatusFolder);
    }

    void applyFilter() {
        applyFilter(findStatusDocumentId(savedTree(activeMode())) != null);
    }

    void applyFilter(boolean hasStatusFolder) {
        visibleFiles.clear();
        for (StatusFile file : allFiles) {
            if (FILTER_PHOTOS.equals(activeFilter) && !file.isImage()) continue;
            if (FILTER_VIDEOS.equals(activeFilter) && !file.isVideo()) continue;
            visibleFiles.add(file);
        }
        updateFilterButtons();
        sourceText.setText("Viewing " + modeLabel(activeMode()) + " - " + visibleFiles.size() + statusCountLabel(visibleFiles.size()) + filterSuffix());
        emptyText.setText(emptyMessage(hasStatusFolder));
        emptyText.setVisibility(visibleFiles.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(visibleFiles.isEmpty() ? View.GONE : View.VISIBLE);
        updateSelectionActions();
        listView.setAdapter(new StatusAdapter(this, visibleFiles, selectedUris));
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
        if (allFiles.isEmpty()) return "No statuses found. Open WhatsApp Status first, view a status, then return here.";
        if (FILTER_PHOTOS.equals(activeFilter)) return "No photo statuses in this folder right now.";
        if (FILTER_VIDEOS.equals(activeFilter)) return "No video statuses in this folder right now.";
        return "No statuses found.";
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
        if (needsLegacyWritePermission()) {
            pendingSave = file;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
        copyStatusToGallery(file, true);
    }

    void saveSelectedStatuses() {
        pendingBulkSave.clear();
        for (StatusFile file : visibleFiles) {
            if (selectedUris.contains(file.uri.toString())) pendingBulkSave.add(file);
        }
        if (pendingBulkSave.isEmpty()) {
            clearSelection();
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
        clearSelection();
        Toast.makeText(this, "Saved " + saved + statusCountLabel(saved) + ".", Toast.LENGTH_SHORT).show();
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
            return true;
        } catch (IOException e) {
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

    void updateProviderButtons() {
        if (findWhatsAppBtn == null || findBusinessBtn == null || chooseBtn == null || refreshBtn == null) return;
        String mode = activeMode();
        boolean whatsappSet = savedTree(MODE_WHATSAPP) != null;
        boolean businessSet = savedTree(MODE_BUSINESS) != null;
        findWhatsAppBtn.setText(MODE_WHATSAPP.equals(mode) && whatsappSet ? "Change WhatsApp" : (whatsappSet ? "WhatsApp Set" : "Find WhatsApp"));
        findBusinessBtn.setText(MODE_BUSINESS.equals(mode) && businessSet ? "Change Business" : (businessSet ? "Business Set" : "Business"));
        styleButton(findWhatsAppBtn, MODE_WHATSAPP.equals(mode));
        styleButton(findBusinessBtn, MODE_BUSINESS.equals(mode));
        chooseBtn.setText("Choose " + modeLabel(mode) + " Folder Manually");
        refreshBtn.setEnabled(savedTree(mode) != null);
    }

    void updateFilterButtons() {
        if (allFilterBtn == null || photosFilterBtn == null || videosFilterBtn == null) return;
        styleButton(allFilterBtn, FILTER_ALL.equals(activeFilter));
        styleButton(photosFilterBtn, FILTER_PHOTOS.equals(activeFilter));
        styleButton(videosFilterBtn, FILTER_VIDEOS.equals(activeFilter));
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
