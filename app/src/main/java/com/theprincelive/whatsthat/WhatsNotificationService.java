package com.theprincelive.whatsthat;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class WhatsNotificationService extends NotificationListenerService {
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (pkg == null) return;
        if (!pkg.equals(PKG_MAIN) && !pkg.equals(PKG_BUSINESS)) return;

        CharSequence titleCs = sbn.getNotification().extras.getCharSequence("android.title");
        CharSequence textCs = sbn.getNotification().extras.getCharSequence("android.text");
        String title = titleCs == null ? "Unknown" : titleCs.toString();
        String text = textCs == null ? "" : textCs.toString();
        if (text.trim().isEmpty()) return;

        new MessageStore(getApplicationContext()).saveMessage(title, text, pkg, System.currentTimeMillis());
    }
}
