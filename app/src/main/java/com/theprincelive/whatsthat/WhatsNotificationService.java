package com.theprincelive.whatsthat;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class WhatsNotificationService extends NotificationListenerService {
 @Override public void onNotificationPosted(StatusBarNotification sbn){
   String pkg = sbn.getPackageName();
   if(pkg == null) return;
   CharSequence titleCs = sbn.getNotification().extras.getCharSequence("android.title");
   CharSequence textCs = sbn.getNotification().extras.getCharSequence("android.text");
   String title = titleCs == null ? "Unknown" : titleCs.toString();
   String text = textCs == null ? "" : textCs.toString();
   new MessageStore(getApplicationContext()).saveMessage(title,text,pkg,System.currentTimeMillis());
 }
}
