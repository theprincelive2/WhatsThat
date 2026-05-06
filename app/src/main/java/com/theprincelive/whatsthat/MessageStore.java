package com.theprincelive.whatsthat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "whatsthat.db";
    private static final int DB_VERSION = 3;
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";

    public MessageStore(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, body TEXT, package_name TEXT, received_at INTEGER, read_at INTEGER DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN read_at INTEGER DEFAULT 0");
            } catch (Exception ignored) { }
        }
    }

    public void saveMessage(String sender, String body, String packageName, long receivedAt) {
        if (body == null || body.trim().isEmpty()) return;
        String cleanBody = clean(body);
        String cleanSender = clean(sender);
        String cleanPackage = clean(packageName);
        SQLiteDatabase db = getWritableDatabase();
        long duplicateCutoff = receivedAt - 120000L;
        Cursor c = db.rawQuery(
                "SELECT id FROM messages WHERE package_name=? AND sender=? AND body=? AND received_at>? LIMIT 1",
                new String[]{cleanPackage, cleanSender, cleanBody, String.valueOf(duplicateCutoff)}
        );
        try {
            if (c.moveToFirst()) return;
        } finally { c.close(); }
        ContentValues values = new ContentValues();
        values.put("sender", cleanSender);
        values.put("body", cleanBody);
        values.put("package_name", cleanPackage);
        values.put("received_at", receivedAt);
        values.put("read_at", 0);
        db.insert("messages", null, values);
    }

    public List<SavedMessage> getRecentStructured(boolean otherNotices) {
        ArrayList<SavedMessage> rows = new ArrayList<>();
        String where = otherNotices ? "package_name<>? AND package_name<>?" : "(package_name=? OR package_name=?)";
        Cursor c = getReadableDatabase().rawQuery("SELECT id, sender, body, package_name, received_at, read_at FROM messages WHERE " + where + " ORDER BY received_at DESC LIMIT 300", new String[]{PKG_MAIN, PKG_BUSINESS});
        SimpleDateFormat fullFmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        DateFormat shortFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        try {
            while (c.moveToNext()) {
                long receivedAt = c.getLong(4);
                boolean read = c.getLong(5) > 0;
                Date receivedDate = new Date(receivedAt);
                rows.add(new SavedMessage(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        fullFmt.format(receivedDate),
                        shortFmt.format(receivedDate),
                        dateLabel(receivedAt),
                        c.getString(3),
                        receivedAt,
                        1,
                        read ? 0 : 1,
                        read
                ));
            }
        } finally { c.close(); }
        return rows;
    }

    public List<SavedMessage> getRecentStructured() {
        return getRecentStructured(false);
    }

    public List<SavedMessage> getConversation(String packageName, String sender) {
        ArrayList<SavedMessage> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, sender, body, package_name, received_at, read_at FROM messages WHERE package_name=? AND sender=? ORDER BY received_at ASC",
                new String[]{clean(packageName), clean(sender)}
        );
        SimpleDateFormat fullFmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        DateFormat shortFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        try {
            while (c.moveToNext()) {
                long receivedAt = c.getLong(4);
                boolean read = c.getLong(5) > 0;
                Date receivedDate = new Date(receivedAt);
                rows.add(new SavedMessage(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        fullFmt.format(receivedDate),
                        shortFmt.format(receivedDate),
                        dateLabel(receivedAt),
                        c.getString(3),
                        receivedAt,
                        1,
                        read ? 0 : 1,
                        read
                ));
            }
        } finally { c.close(); }
        return rows;
    }

    public String exportCsv(boolean otherNotices) {
        StringBuilder out = new StringBuilder();
        out.append("sender,message,package,received_at\n");
        String where = otherNotices ? "package_name<>? AND package_name<>?" : "(package_name=? OR package_name=?)";
        Cursor c = getReadableDatabase().rawQuery("SELECT sender, body, package_name, received_at FROM messages WHERE " + where + " ORDER BY received_at DESC", new String[]{PKG_MAIN, PKG_BUSINESS});
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            while (c.moveToNext()) {
                out.append(csv(c.getString(0))).append(',')
                        .append(csv(c.getString(1))).append(',')
                        .append(csv(c.getString(2))).append(',')
                        .append(csv(fmt.format(new Date(c.getLong(3))))).append('\n');
            }
        } finally { c.close(); }
        return out.toString();
    }

    public String exportCsv() {
        return exportCsv(false);
    }

    public int markConversationRead(String packageName, String sender) {
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        return getWritableDatabase().update(
                "messages",
                values,
                "package_name=? AND sender=? AND read_at=0",
                new String[]{clean(packageName), clean(sender)}
        );
    }

    public int markMessageRead(long id) {
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        return getWritableDatabase().update("messages", values, "id=? AND read_at=0", new String[]{String.valueOf(id)});
    }

    public int markInboxRead(boolean otherNotices) {
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        String where = otherNotices ? "package_name<>? AND package_name<>? AND read_at=0" : "(package_name=? OR package_name=?) AND read_at=0";
        return getWritableDatabase().update("messages", values, where, new String[]{PKG_MAIN, PKG_BUSINESS});
    }

    public int deleteMessage(long id) {
        return getWritableDatabase().delete("messages", "id=?", new String[]{String.valueOf(id)});
    }

    public int deleteSender(String sender) {
        return getWritableDatabase().delete("messages", "sender=?", new String[]{clean(sender)});
    }

    public int deleteSender(String sender, boolean otherNotices) {
        String packageWhere = otherNotices ? "package_name<>? AND package_name<>?" : "(package_name=? OR package_name=?)";
        return getWritableDatabase().delete("messages", "sender=? AND " + packageWhere, new String[]{clean(sender), PKG_MAIN, PKG_BUSINESS});
    }

    public int deletePackage(String packageName) {
        return getWritableDatabase().delete("messages", "package_name=?", new String[]{clean(packageName)});
    }

    public int deleteConversation(String packageName, String sender) {
        return getWritableDatabase().delete("messages", "package_name=? AND sender=?", new String[]{clean(packageName), clean(sender)});
    }

    public int deleteSimilar(String packageName, String sender, String body) {
        return getWritableDatabase().delete(
                "messages",
                "package_name=? AND sender=? AND body=?",
                new String[]{clean(packageName), clean(sender), clean(body)}
        );
    }

    public int deleteOlderThanDays(int days) {
        if (days <= 0) return 0;
        long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
        return getWritableDatabase().delete("messages", "received_at<?", new String[]{String.valueOf(cutoff)});
    }

    public void clearMessages(boolean otherNotices) {
        if (otherNotices) {
            getWritableDatabase().delete("messages", "package_name<>? AND package_name<>?", new String[]{PKG_MAIN, PKG_BUSINESS});
        } else {
            getWritableDatabase().delete("messages", "package_name=? OR package_name=?", new String[]{PKG_MAIN, PKG_BUSINESS});
        }
    }

    public int deleteWhatsAppNoise() {
        return getWritableDatabase().delete(
                "messages",
                "(package_name=? OR package_name=?) AND (lower(body)=? OR lower(body) LIKE ? OR lower(body) LIKE ?)",
                new String[]{PKG_MAIN, PKG_BUSINESS, "checking for new messages", "% new message", "% new messages"}
        );
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
    private String clean(String value) { return value == null ? "Unknown" : value.trim(); }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private String dateLabel(long time) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(time);
        if (sameDay(now, then)) return "Today";
        now.add(Calendar.DATE, -1);
        if (sameDay(now, then)) return "Yesterday";
        return new SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(new Date(time));
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
