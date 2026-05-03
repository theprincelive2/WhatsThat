package com.theprincelive.whatsthat;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;

public class MainActivity extends Activity {
 MessageStore store; ListView list;
 @Override protected void onCreate(Bundle b){
  super.onCreate(b);
  store = new MessageStore(this);
  LinearLayout root = new LinearLayout(this);
  root.setOrientation(LinearLayout.VERTICAL);
  Button open = new Button(this); open.setText("Open Settings");
  Button clear = new Button(this); clear.setText("Clear Saved Items");
  list = new ListView(this);
  root.addView(open); root.addView(clear); root.addView(list,new LinearLayout.LayoutParams(-1,-1));
  setContentView(root);
  open.setOnClickListener(new View.OnClickListener(){public void onClick(View v){startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));}});
  clear.setOnClickListener(new View.OnClickListener(){public void onClick(View v){store.clearMessages();load();}});
  load();
 }
 @Override protected void onResume(){super.onResume();load();}
 void load(){list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, store.getRecentMessages()));}
}
