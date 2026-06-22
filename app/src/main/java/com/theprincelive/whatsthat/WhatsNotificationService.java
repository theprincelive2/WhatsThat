package com.theprincelive.whatsthat;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhatsNotificationService extends NotificationListenerService {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        boolean whatsapp = pkg.equals(PKG_MAIN) || pkg.equals(PKG_BUSINESS);
        if (!whatsapp && !captureOtherNotices()) return;

        Bundle extras = sbn.getNotification().extras;
        CharSequence titleCs = extras.getCharSequence("android.title");
        String title = titleCs == null ? appLabel(pkg) : titleCs.toString();
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
