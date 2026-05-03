package com.theprincelive.whatsthat;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
public class WhatsNotificationService extends NotificationListenerService {
 @Override public void onNotificationPosted(StatusBarNotification sbn){if("com.whatsapp".equals(sbn.getPackageName())) Log.d("WhatsThat","Message captured");}
}
