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
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";

    public MessageStore(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, body TEXT, package_name TEXT, received_at INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    public void saveMessage(String sender, String body, String packageName, long receivedAt) {
        if (body == null || body.trim().isEmpty()) return;
        if (!isAllowedPackage(packageName)) return;
        String cleanBody = clean(body);
        String cleanSender = clean(sender);
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT sender, body, received_at FROM messages WHERE package_name=? OR package_name=? ORDER BY id DESC LIMIT 1", new String[]{PKG_MAIN, PKG_BUSINESS});
        try {
            if (c.moveToFirst()) {
                String lastSender = c.getString(0);
                String lastBody = c.getString(1);
                long lastTime = c.getLong(2);
                if (cleanSender.equals(lastSender) && cleanBody.equals(lastBody) && Math.abs(receivedAt - lastTime) < 8000) return;
            }
        } finally { c.close(); }
        ContentValues values = new ContentValues();
        values.put("sender", cleanSender);
        values.put("body", cleanBody);
        values.put("package_name", clean(packageName));
        values.put("received_at", receivedAt);
        db.insert("messages", null, values);
    }

    public List<SavedMessage> getRecentStructured() {
        ArrayList<SavedMessage> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT sender, body, received_at FROM messages WHERE package_name=? OR package_name=? ORDER BY received_at DESC LIMIT 300", new String[]{PKG_MAIN, PKG_BUSINESS});
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        try {
            while (c.moveToNext()) rows.add(new SavedMessage(c.getString(0), c.getString(1), fmt.format(new Date(c.getLong(2)))));
        } finally { c.close(); }
        return rows;
    }

    public void removeNonWhatsAppRows() {
        getWritableDatabase().delete("messages", "package_name<>? AND package_name<>?", new String[]{PKG_MAIN, PKG_BUSINESS});
    }

    public List<String> getRecentMessages() {
        ArrayList<String> out = new ArrayList<>();
        for (SavedMessage m : getRecentStructured()) out.add(m.sender + "\n" + m.body + "\n" + m.time);
        return out;
    }

    public void clearMessages() { getWritableDatabase().delete("messages", null, null); }
    private boolean isAllowedPackage(String value) { return PKG_MAIN.equals(value) || PKG_BUSINESS.equals(value); }
    private String clean(String value) { return value == null ? "Unknown" : value.trim(); }
}
