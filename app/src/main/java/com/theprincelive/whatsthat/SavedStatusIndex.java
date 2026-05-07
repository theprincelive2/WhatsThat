package com.theprincelive.whatsthat;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

class SavedStatusIndex {
    static Set<String> load(Context context) {
        HashSet<String> names = new HashSet<>();
        addSaved(context, names, false);
        addSaved(context, names, true);
        return names;
    }

    static boolean isSaved(Set<String> savedNames, String sourceName) {
        return savedNames.contains(baseName(sourceName));
    }

    static String baseName(String name) {
        String safe = name == null || name.trim().isEmpty() ? "status" : name;
        int marker = safe.indexOf("_whatsthat_");
        if (marker > 0) {
            return safe.substring(0, marker);
        }
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) : safe;
    }

    private static void addSaved(Context context, Set<String> names, boolean videos) {
        Uri collection = videos ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {MediaStore.MediaColumns.DISPLAY_NAME};
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
        try (Cursor cursor = context.getContentResolver().query(collection, columns, selection, args, null)) {
            if (cursor == null) return;
            while (cursor.moveToNext()) {
                names.add(baseName(cursor.getString(0)));
            }
        } catch (Exception ignored) {
        }
    }
}
