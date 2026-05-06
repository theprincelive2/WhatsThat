package com.theprincelive.whatsthat;

import android.net.Uri;

class StatusFile {
    final String name;
    final String mimeType;
    final Uri uri;
    final long size;
    final long modifiedAt;

    StatusFile(String name, String mimeType, Uri uri, long size, long modifiedAt) {
        this.name = name;
        this.mimeType = mimeType;
        this.uri = uri;
        this.size = size;
        this.modifiedAt = modifiedAt;
    }

    boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }
}
