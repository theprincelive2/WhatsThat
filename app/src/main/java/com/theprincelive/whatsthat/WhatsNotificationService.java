package com.theprincelive.whatsthat;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.Locale;

public class WhatsNotificationService extends NotificationListenerService {
    private static final String PREFS = "whatsthat_prefs";
    private static final String PREF_CAPTURE_OTHER = "capture_other_notices";
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        boolean whatsapp = pkg.equals(PKG_MAIN) || pkg.equals(PKG_BUSINESS);
        if (!whatsapp && !captureOtherNotices()) return;

        CharSequence titleCs = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence textCs = sbn.getNotification().extras.getCharSequence("android.text");
        String title = titleCs == null ? appLabel(pkg) : titleCs.toString();
        String text = textCs == null ? "" : textCs.toString();
        if (text.trim().isEmpty()) return;
        if (whatsapp && isWhatsAppNoise(title, text)) return;
        if (whatsapp && isLikelyOwnReply(title, text)) return;
        if (NotificationRules.isHidden(getApplicationContext(), pkg, title, text)) return;

        new MessageStore(getApplicationContext()).saveMessage(title, text, pkg, System.currentTimeMillis());
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
        if (cleanTitle.equals("whatsapp") && cleanText.contains("new messages")) return true;
        return cleanTitle.equals("whatsapp") && cleanText.contains("checking");
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
