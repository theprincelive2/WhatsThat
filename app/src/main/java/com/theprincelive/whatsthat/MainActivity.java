package com.theprincelive.whatsthat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
 MessageStore store; ListView list; TextView countText; TextView emptyText; EditText searchBox; ArrayAdapter<String> adapter;
 @Override protected void onCreate(Bundle b){
  super.onCreate(b);
  setContentView(R.layout.activity_main);
  store = new MessageStore(this);
  list = findViewById(R.id.listView);
  countText = findViewById(R.id.countText);
  emptyText = findViewById(R.id.emptyText);
  searchBox = findViewById(R.id.searchBox);
  Button open = findViewById(R.id.openBtn);
  Button clear = findViewById(R.id.clearBtn);
  open.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
  clear.setOnClickListener(v -> {store.clearMessages(); load();});
  searchBox.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){} public void onTextChanged(CharSequence s,int a,int b,int c){load();} public void afterTextChanged(Editable e){}});
  load();
 }
 @Override protected void onResume(){super.onResume();load();}
 void load(){List<String> data=store.getRecentMessages(); String q=searchBox.getText().toString().toLowerCase(); List<String> filtered=new ArrayList<>(); for(String x:data){ if(q.isEmpty()||x.toLowerCase().contains(q)) filtered.add(x);} countText.setText(filtered.size()+" saved items"); emptyText.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE); list.setVisibility(filtered.isEmpty()?View.GONE:View.VISIBLE); adapter=new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,filtered); list.setAdapter(adapter);} }
