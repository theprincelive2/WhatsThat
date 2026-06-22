package com.theprincelive.whatsthat;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThumbnailLoader {
    private static final LruCache<String, Bitmap> cache = new LruCache<>(50);
    private static final ExecutorService executor = Executors.newFixedThreadPool(3);

    public static void loadVideoThumbnail(Context context, Uri uri, ImageView imageView, int placeholderRes) {
        final String key = uri.toString();
        Bitmap cached = cache.get(key);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        imageView.setImageResource(placeholderRes);
        imageView.setTag(key);

        executor.execute(() -> {
            Bitmap bitmap = extractThumbnail(context, uri);
            if (bitmap != null) {
                cache.put(key, bitmap);
                imageView.post(() -> {
                    if (key.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                });
            }
        });
    }

    private static Bitmap extractThumbnail(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception ignored) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {}
        }
    }
}
