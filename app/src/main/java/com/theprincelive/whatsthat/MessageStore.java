package com.theprincelive.whatsthat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "whatsthat.db";
    private static final int DB_VERSION = 1;

    public MessageStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, body TEXT, package_name TEXT, received_at INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS messages");
        onCreate(db);
    }

    public void saveMessage(String sender, String body, String packageName, long receivedAt) {
        if (body == null || body.trim().isEmpty()) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("sender", clean(sender));
        values.put("body", clean(body));
        values.put("package_name", packageName);
        values.put("received_at", receivedAt);
        db.insert("messages", null, values);
    }

    public List<String> getRecentMessages() {
        ArrayList<String> rows = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT sender, body, received_at FROM messages ORDER BY received_at DESC LIMIT 300", null);
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        try {
            while (c.moveToNext()) {
                String sender = c.getString(0);
                String body = c.getString(1);
                long time = c.getLong(2);
                rows.add(sender + "\n" + body + "\n" + fmt.format(new Date(time)));
            }
        } finally {
            c.close();
        }
        return rows;
    }

    public void clearMessages() {
        getWritableDatabase().delete("messages", null, null);
    }

    private String clean(String value) {
        if (value == null) return "Unknown";
        return value.trim();
    }
}
