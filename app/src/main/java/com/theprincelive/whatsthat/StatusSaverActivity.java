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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StatusSaverActivity extends Activity {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_STATUS_TREE = "status_tree_uri";
    private static final int REQUEST_STATUS_FOLDER = 44;
    private static final int REQUEST_WRITE_STORAGE = 45;

    TextView folderText;
    TextView emptyText;
    ListView listView;
    List<StatusFile> files = new ArrayList<>();
    StatusFile pendingSave;

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

        TextView intro = copy("Choose WhatsApp's .Statuses folder once, then save viewed photo and video statuses to your gallery.");
        root.addView(intro);

        Button choose = primaryButton("Choose Status Folder");
        choose.setOnClickListener(v -> chooseStatusFolder());
        root.addView(choose, buttonParams(16));

        folderText = copy("");
        folderText.setPadding(0, dp(10), 0, dp(6));
        root.addView(folderText);

        emptyText = copy("");
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setPadding(dp(16), dp(28), dp(16), dp(28));
        root.addView(emptyText, new LinearLayout.LayoutParams(-1, -2));

        listView = new ListView(this);
        listView.setDividerHeight(1);
        listView.setCacheColorHint(Color.TRANSPARENT);
        listView.setOnItemClickListener((parent, view, position, id) -> showStatusActions(files.get(position)));
        root.addView(listView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        loadStatuses();
    }

    void chooseStatusFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_STATUS_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_STATUS_FOLDER || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri treeUri = data.getData();
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        getContentResolver().takePersistableUriPermission(treeUri, flags);
        prefs().edit().putString(PREF_STATUS_TREE, treeUri.toString()).apply();
        loadStatuses();
    }

    void loadStatuses() {
        files.clear();
        Uri tree = savedTree();
        if (tree == null) {
            folderText.setText("No folder selected.");
            emptyText.setText("Tap Choose Status Folder, then select WhatsApp > Media > .Statuses.");
            listView.setAdapter(new StatusAdapter(this, files));
            return;
        }
        folderText.setText("Folder access is enabled.");
        files.addAll(queryStatuses(tree));
        Collections.sort(files, (a, b) -> Long.compare(b.modifiedAt, a.modifiedAt));
        emptyText.setText(files.isEmpty() ? "No statuses found. Open WhatsApp Status first, view a status, then return here." : "");
        emptyText.setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
        listView.setVisibility(files.isEmpty() ? View.GONE : View.VISIBLE);
        listView.setAdapter(new StatusAdapter(this, files));
    }

    List<StatusFile> queryStatuses(Uri treeUri) {
        ArrayList<StatusFile> out = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
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
            prefs().edit().remove(PREF_STATUS_TREE).apply();
        }
        return out;
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

    void saveStatus(StatusFile file) {
        if (needsLegacyWritePermission()) {
            pendingSave = file;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
            return;
        }
        copyStatusToGallery(file);
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
                copyStatusToGallery(file);
            } else {
                Toast.makeText(this, "Storage permission is needed to save on this Android version.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void copyStatusToGallery(StatusFile file) {
        Uri target = createMediaTarget(file);
        if (target == null) {
            Toast.makeText(this, "Could not create save location.", Toast.LENGTH_SHORT).show();
            return;
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
            Toast.makeText(this, "Status saved to gallery.", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not save this status.", Toast.LENGTH_SHORT).show();
        }
    }

    Uri createMediaTarget(StatusFile file) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueName(file.name));
        values.put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, (file.isVideo() ? "Movies" : "Pictures") + "/WhatsThat Statuses");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
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
        String raw = prefs().getString(PREF_STATUS_TREE, null);
        return raw == null ? null : Uri.parse(raw);
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
        button.setTextColor(Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(0, 107, 85));
        bg.setCornerRadius(dp(18));
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
