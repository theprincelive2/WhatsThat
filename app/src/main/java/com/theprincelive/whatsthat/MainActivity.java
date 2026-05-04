package com.theprincelive.whatsthat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
 MessageStore store; ListView list; TextView countText; TextView emptyText;
 @Override protected void onCreate(Bundle b){
  super.onCreate(b);
  setContentView(R.layout.activity_main);
  store = new MessageStore(this);
  list = findViewById(R.id.listView);
  countText = findViewById(R.id.countText);
  emptyText = findViewById(R.id.emptyText);
  Button open = findViewById(R.id.openBtn);
  Button clear = findViewById(R.id.clearBtn);
  open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
  clear.setOnClickListener(v -> {store.clearMessages(); load();});
  load();
 }
 @Override protected void onResume(){super.onResume();load();}
 void load(){List<String> data=store.getRecentMessages(); countText.setText(data.size()+" saved items"); emptyText.setVisibility(data.isEmpty()?View.VISIBLE:View.GONE); list.setVisibility(data.isEmpty()?View.GONE:View.VISIBLE); list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,data));}
}
