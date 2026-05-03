package com.theprincelive.whatsthat;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
public class MainActivity extends Activity {
 @Override protected void onCreate(Bundle b){super.onCreate(b);TextView tv=new TextView(this);tv.setText("WhatsThat\nEnable Notification Access");setContentView(tv);startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}
}
