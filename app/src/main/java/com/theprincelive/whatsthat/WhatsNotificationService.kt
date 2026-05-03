package com.theprincelive.whatsthat
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
class WhatsNotificationService: NotificationListenerService() {
 override fun onNotificationPosted(sbn: StatusBarNotification) {
  if (sbn.packageName == "com.whatsapp") {
   val extras = sbn.notification.extras
   val title = extras.getString("android.title") ?: "Unknown"
   val text = extras.getCharSequence("android.text")?.toString() ?: ""
   Log.d("WhatsThat", "$title: $text")
  }
 }
}
