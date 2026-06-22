package com.theprincelive.whatsthat;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhatsNotificationService extends NotificationListenerService {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";
    private static final String ACTION_TRIGGER_AUTO_SAVE = "com.theprincelive.whatsthat.ACTION_TRIGGER_AUTO_SAVE";

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private static final long AUTO_SAVE_INTERVAL = 60 * 60 * 1000; // 1 hour

    private final Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            triggerAutoSave();
            autoSaveHandler.postDelayed(this, AUTO_SAVE_INTERVAL);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        autoSaveHandler.postDelayed(autoSaveRunnable, 10000); // 10 seconds delay initially, then every 1 hour
    }

    @Override
    public void onDestroy() {
        autoSaveHandler.removeCallbacks(autoSaveRunnable);
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TRIGGER_AUTO_SAVE.equals(intent.getAction())) {
            triggerAutoSave();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        boolean whatsapp = pkg.equals(PKG_MAIN) || pkg.equals(PKG_BUSINESS);

        if (whatsapp) {
            triggerAutoSave();
        }

        if (!whatsapp && !captureOtherNotices()) return;

        Bundle extras = sbn.getNotification().extras;
        CharSequence titleCs = extras.getCharSequence("android.title");
        String title = titleCs == null ? appLabel(pkg) : titleCs.toString();
        
        if (title != null) {
            title = title.trim().replace("\uFE0F", "").replace("\uFE0E", "").replace("\u200B", "").trim();
            if (whatsapp) {
                if (title.startsWith("WhatsApp: ")) {
                    title = title.substring("WhatsApp: ".length()).trim();
                } else if (title.startsWith("WhatsApp Business: ")) {
                    title = title.substring("WhatsApp Business: ".length()).trim();
                } else if (title.startsWith("WA Business: ")) {
                    title = title.substring("WA Business: ".length()).trim();
                }
            }
        }
        
        String text = notificationText(extras);
        if (text.trim().isEmpty()) return;
        if (whatsapp && isWhatsAppNoise(title, text)) return;
        if (!whatsapp && isSystemNoise(pkg, title, text)) return;
        if (whatsapp && isLikelyOwnReply(title, text)) return;

        dbExecutor.execute(() -> {
            if (NotificationRules.isHidden(getApplicationContext(), pkg, title, text)) return;
            MessageStore.getInstance(getApplicationContext()).saveMessage(title, text, pkg, System.currentTimeMillis());
        });
    }

    private void triggerAutoSave() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean autoSaveEnabled = prefs.getBoolean("auto_save_statuses", false);
        if (!autoSaveEnabled) return;

        dbExecutor.execute(() -> {
            try {
                runAutoSaveWork();
            } catch (Exception ignored) {
            }
        });
    }

    private void runAutoSaveWork() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // 1. WhatsApp status folder
        String whatsappUriStr = prefs.getString("status_tree_uri_whatsapp", null);
        if (whatsappUriStr != null) {
            autoSaveFromTree(Uri.parse(whatsappUriStr));
        }

        // 2. WhatsApp Business status folder
        String businessUriStr = prefs.getString("status_tree_uri_business", null);
        if (businessUriStr != null) {
            autoSaveFromTree(Uri.parse(businessUriStr));
        }
    }

    private void autoSaveFromTree(Uri treeUri) {
        String statusDocumentId = findStatusDocumentId(treeUri);
        if (statusDocumentId == null) return;

        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, statusDocumentId);
        String[] columns = {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
        };

        Set<String> savedNames = SavedStatusIndex.load(getApplicationContext());

        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                long size = cursor.getLong(3);
                long modifiedAt = cursor.getLong(4);

                if (mime == null || (!mime.startsWith("image/") && !mime.startsWith("video/"))) continue;

                // Check if already saved
                if (SavedStatusIndex.isSaved(savedNames, name)) {
                    continue;
                }

                // Prepare file uri
                Uri fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                boolean isVideo = mime.startsWith("video/");

                // Perform copy status to gallery
                copyStatusToGallery(name, mime, fileUri, isVideo);
            }
        } catch (Exception ignored) {
        }
    }

    private String findStatusDocumentId(Uri selectedTree) {
        if (selectedTree == null) return null;
        try {
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
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean copyStatusToGallery(String name, String mimeType, Uri sourceUri, boolean isVideo) {
        Uri target = createMediaTarget(name, mimeType, isVideo);
        if (target == null) return false;

        try (InputStream input = getContentResolver().openInputStream(sourceUri);
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
            return true;
        } catch (IOException e) {
            try {
                getContentResolver().delete(target, null, null);
            } catch (Exception ignored) {}
            return false;
        }
    }

    private Uri createMediaTarget(String name, String mimeType, boolean isVideo) {
        String outputName = uniqueName(name);
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, outputName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, isVideo ? "Movies/WhatsThat/Status Videos" : "Pictures/WhatsThat/Status Photos");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        } else {
            File folder = new File(android.os.Environment.getExternalStoragePublicDirectory(isVideo ? android.os.Environment.DIRECTORY_MOVIES : android.os.Environment.DIRECTORY_PICTURES), isVideo ? "WhatsThat/Status Videos" : "WhatsThat/Status Photos");
            if (!folder.exists()) folder.mkdirs();
            values.put(MediaStore.MediaColumns.DATA, new File(folder, outputName).getAbsolutePath());
        }
        Uri collection = isVideo ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        return getContentResolver().insert(collection, values);
    }

    private String uniqueName(String name) {
        String safe = name == null || name.trim().isEmpty() ? "status" : name;
        int dot = safe.lastIndexOf('.');
        String base = dot > 0 ? safe.substring(0, dot) : safe;
        String ext = dot > 0 ? safe.substring(dot) : "";
        return base + "_whatsthat_" + System.currentTimeMillis() + ext;
    }


    private String notificationText(Bundle extras) {
        CharSequence text = extras.getCharSequence("android.text");
        if (hasText(text)) return text.toString();

        CharSequence bigText = extras.getCharSequence("android.bigText");
        if (hasText(bigText)) return bigText.toString();

        CharSequence[] lines = extras.getCharSequenceArray("android.textLines");
        if (lines != null) {
            for (int i = lines.length - 1; i >= 0; i--) {
                if (hasText(lines[i])) return lines[i].toString();
            }
        }
        return "";
    }

    private boolean hasText(CharSequence value) {
        return value != null && !value.toString().trim().isEmpty();
    }

    private boolean captureOtherNotices() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getBoolean(PREF_CAPTURE_OTHER, false);
    }

    private boolean isWhatsAppNoise(String title, String text) {
        String cleanTitle = title == null ? "" : title.trim().toLowerCase(Locale.US);
        String cleanText = text == null ? "" : text.trim().toLowerCase(Locale.US);
        if (cleanText.equals("checking for new messages")) return true;
        if (cleanText.matches("\\d+ new messages?")) return true;
        if (cleanText.matches("\\d+ messages? from \\d+ chats?")) return true;
        if (cleanTitle.equals("whatsapp") && cleanText.contains("new messages")) return true;
        return cleanTitle.equals("whatsapp") && cleanText.contains("checking");
    }

    private boolean isSystemNoise(String packageName, String title, String text) {
        String cleanPackage = packageName == null ? "" : packageName.trim().toLowerCase(Locale.US);
        String cleanText = text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);

        if (cleanText.equals("charging") || cleanText.startsWith("charging ")) return true;
        if (cleanText.equals("downloading") || cleanText.startsWith("downloading ")) return true;
        if (cleanText.equals("download complete") || cleanText.equals("download completed")) return true;
        if (cleanText.contains("download in progress")) return true;
        if (cleanText.contains("running in the background")) return true;
        if (cleanPackage.startsWith("android") || cleanPackage.contains("systemui")) return true;
        if (cleanPackage.contains("launcher") || cleanPackage.contains("packageinstaller")) return true;
        if (cleanPackage.contains("permissioncontroller") || cleanPackage.contains("updater")) return true;
        if (cleanPackage.equals("com.google.android.gms") || cleanPackage.equals("com.android.vending")) return true;
        return cleanPackage.contains("settings") && (cleanText.contains("system") || cleanText.contains("permission"));
    }

    private boolean isLikelyOwnReply(String title, String text) {
        String cleanTitle = title == null ? "" : title.trim().toLowerCase(Locale.US);
        String cleanText = text == null ? "" : text.trim().toLowerCase(Locale.US);
        if (cleanTitle.equals("you") || cleanTitle.equals("me")) return true;
        return cleanText.startsWith("you:") || cleanText.startsWith("me:");
    }

    private String appLabel(String packageName) {
        try {
            PackageManager manager = getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
            return manager.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName == null ? "Unknown" : packageName;
        }
    }
}
