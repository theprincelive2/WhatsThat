package com.theprincelive.whatsthat
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class MainActivity: AppCompatActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  val tv = TextView(this)
  tv.text = "WhatsThat\nEnable Notification Access for WhatsApp history.\nSettings will open now."
  tv.textSize = 18f
  val layout = LinearLayout(this)
  layout.addView(tv)
  setContentView(layout)
  startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
 }
}
