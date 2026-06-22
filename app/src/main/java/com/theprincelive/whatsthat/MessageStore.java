package com.theprincelive.whatsthat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "whatsthat.db";
    private static final int DB_VERSION = 4;
    private static final String PKG_MAIN = "com.whatsapp";
    private static final String PKG_BUSINESS = "com.whatsapp.w4b";
    private final Context context;
    private static MessageStore instance;

    public static synchronized MessageStore getInstance(Context context) {
        if (instance == null) {
            instance = new MessageStore(context.getApplicationContext());
            final MessageStore inst = instance;
            new Thread(() -> {
                try {
                    inst.normalizeDatabaseSenders();
                } catch (Exception ignored) {}
            }).start();
        }
        return instance;
    }

    private MessageStore(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context.getApplicationContext();
    }

    public void normalizeDatabaseSenders() {
        android.database.sqlite.SQLiteDatabase db = getWritableDatabase();
        android.database.Cursor c = db.rawQuery("SELECT DISTINCT sender, package_name FROM messages", null);
        try {
            while (c.moveToNext()) {
                String original = c.getString(0);
                String pkg = c.getString(1);
                if (original == null) continue;
                String cleaned = original.trim();
                cleaned = cleaned.replace("\uFE0F", "");
                cleaned = cleaned.replace("\uFE0E", "");
                cleaned = cleaned.replace("\u200B", "");
                cleaned = cleaned.trim();
                
                boolean whatsapp = PKG_MAIN.equals(pkg) || PKG_BUSINESS.equals(pkg);
                if (whatsapp) {
                    if (cleaned.startsWith("WhatsApp: ")) {
                        cleaned = cleaned.substring("WhatsApp: ".length()).trim();
                    } else if (cleaned.startsWith("WhatsApp Business: ")) {
                        cleaned = cleaned.substring("WhatsApp Business: ".length()).trim();
                    } else if (cleaned.startsWith("WA Business: ")) {
                        cleaned = cleaned.substring("WA Business: ".length()).trim();
                    }
                }
                
                if (!cleaned.equals(original)) {
                    ContentValues cv = new ContentValues();
                    cv.put("sender", cleaned);
                    db.update("messages", cv, "sender=? AND package_name=?", new String[]{original, pkg});
                }
            }
        } catch (Exception ignored) {
        } finally {
            c.close();
        }
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, sender TEXT, body TEXT, package_name TEXT, received_at INTEGER, read_at INTEGER DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_pkg_sender ON messages(package_name, sender)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_received ON messages(received_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE messages ADD COLUMN read_at INTEGER DEFAULT 0");
            } catch (Exception ignored) { }
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_pkg_sender ON messages(package_name, sender)");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_received ON messages(received_at DESC)");
            } catch (Exception ignored) { }
        }
    }

    public boolean saveMessage(String sender, String body, String packageName, long receivedAt) {
        return saveMessageInternal(getWritableDatabase(), sender, body, packageName, receivedAt);
    }

    private boolean saveMessageInternal(SQLiteDatabase db, String sender, String body, String packageName, long receivedAt) {
        if (body == null || body.trim().isEmpty()) return false;
        String cleanBody = clean(body);
        String cleanPackage = clean(packageName);
        String cleanSender = clean(sender);
        
        boolean whatsapp = PKG_MAIN.equals(cleanPackage) || PKG_BUSINESS.equals(cleanPackage);
        if (whatsapp) {
            if (cleanSender.startsWith("WhatsApp: ")) {
                cleanSender = cleanSender.substring("WhatsApp: ".length()).trim();
            } else if (cleanSender.startsWith("WhatsApp Business: ")) {
                cleanSender = cleanSender.substring("WhatsApp Business: ".length()).trim();
            } else if (cleanSender.startsWith("WA Business: ")) {
                cleanSender = cleanSender.substring("WA Business: ".length()).trim();
            }
        }
        long duplicateCutoff = receivedAt - 120000L;
        Cursor c = db.rawQuery(
                "SELECT id FROM messages WHERE package_name=? AND sender=? AND body=? AND received_at>? LIMIT 1",
                new String[]{cleanPackage, cleanSender, cleanBody, String.valueOf(duplicateCutoff)}
        );
        try {
            if (c.moveToFirst()) return false;
        } finally { c.close(); }
        ContentValues values = new ContentValues();
        values.put("sender", cleanSender);
        values.put("body", cleanBody);
        values.put("package_name", cleanPackage);
        values.put("received_at", receivedAt);
        values.put("read_at", 0);
        return db.insert("messages", null, values) != -1;
    }

    public List<SavedMessage> getRecentStructured(boolean otherNotices) {
        if (isDecoy()) {
            return getDecoyMessages(otherNotices);
        }
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
        if (isDecoy()) {
            return getDecoyConversation(packageName, sender);
        }
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
        out.append("sender,message,app,package,received_at\n");
        String where = otherNotices ? "package_name<>? AND package_name<>?" : "(package_name=? OR package_name=?)";
        Cursor c = getReadableDatabase().rawQuery("SELECT sender, body, package_name, received_at FROM messages WHERE " + where + " ORDER BY received_at DESC", new String[]{PKG_MAIN, PKG_BUSINESS});
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            while (c.moveToNext()) {
                out.append(csv(c.getString(0))).append(',')
                        .append(csv(c.getString(1))).append(',')
                        .append(csv(AppLabels.label(context, c.getString(2)))).append(',')
                        .append(csv(c.getString(2))).append(',')
                        .append(csv(fmt.format(new Date(c.getLong(3))))).append('\n');
            }
        } finally { c.close(); }
        return out.toString();
    }

    public String exportCsv() {
        return exportCsv(false);
    }

    public int importCsv(String csvText) {
        if (csvText == null || csvText.trim().isEmpty()) return 0;
        ArrayList<List<String>> rows = parseCsv(csvText);
        int imported = 0;
        boolean first = true;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (List<String> row : rows) {
                if (row.size() < 5) continue;
                if (first && "sender".equalsIgnoreCase(row.get(0).trim()) && "message".equalsIgnoreCase(row.get(1).trim())) {
                    first = false;
                    continue;
                }
                first = false;
                String sender = row.get(0);
                String body = row.get(1);
                String packageName = row.get(3);
                long receivedAt = parseExportTime(row.get(4));
                if (body == null || body.trim().isEmpty()) continue;
                if (saveMessageInternal(db, sender, body, packageName, receivedAt)) imported++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return imported;
    }

    public int markConversationRead(String packageName, String sender) {
        if (isDecoy()) return 1;
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        return getWritableDatabase().update(
                "messages",
                values,
                "package_name=? AND sender=? AND read_at=0",
                new String[]{clean(packageName), clean(sender)}
        );
    }

    public int markConversationUnread(String packageName, String sender) {
        if (isDecoy()) return 1;
        ContentValues values = new ContentValues();
        values.put("read_at", 0);
        return getWritableDatabase().update(
                "messages",
                values,
                "package_name=? AND sender=?",
                new String[]{clean(packageName), clean(sender)}
        );
    }

    public int markMessageRead(long id) {
        if (isDecoy()) return 1;
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        return getWritableDatabase().update("messages", values, "id=? AND read_at=0", new String[]{String.valueOf(id)});
    }

    public int markInboxRead(boolean otherNotices) {
        if (isDecoy()) return 1;
        ContentValues values = new ContentValues();
        values.put("read_at", System.currentTimeMillis());
        String where = otherNotices ? "package_name<>? AND package_name<>? AND read_at=0" : "(package_name=? OR package_name=?) AND read_at=0";
        return getWritableDatabase().update("messages", values, where, new String[]{PKG_MAIN, PKG_BUSINESS});
    }

    public int deleteMessage(long id) {
        if (isDecoy()) return 1;
        return getWritableDatabase().delete("messages", "id=?", new String[]{String.valueOf(id)});
    }

    public int deleteSender(String sender) {
        if (isDecoy()) return 1;
        return getWritableDatabase().delete("messages", "sender=?", new String[]{clean(sender)});
    }

    public int deleteSender(String sender, boolean otherNotices) {
        if (isDecoy()) return 1;
        String packageWhere = otherNotices ? "package_name<>? AND package_name<>?" : "(package_name=? OR package_name=?)";
        return getWritableDatabase().delete("messages", "sender=? AND " + packageWhere, new String[]{clean(sender), PKG_MAIN, PKG_BUSINESS});
    }

    public int deletePackage(String packageName) {
        if (isDecoy()) return 1;
        return getWritableDatabase().delete("messages", "package_name=?", new String[]{clean(packageName)});
    }

    public int deleteConversation(String packageName, String sender) {
        if (isDecoy()) return 1;
        return getWritableDatabase().delete("messages", "package_name=? AND sender=?", new String[]{clean(packageName), clean(sender)});
    }

    public int deleteSimilar(String packageName, String sender, String body) {
        return getWritableDatabase().delete(
                "messages",
                "package_name=? AND sender=? AND body=?",
                new String[]{clean(packageName), clean(sender), clean(body)}
        );
    }

    public int deleteHiddenByRules() {
        List<NotificationRules.Rule> rules = NotificationRules.list(context);
        if (rules.isEmpty()) return 0;

        ArrayList<Long> ids = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id, sender, body, package_name FROM messages", null);
        try {
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String sender = c.getString(1);
                String body = c.getString(2);
                String packageName = c.getString(3);
                if (NotificationRules.isHidden(rules, packageName, sender, body)) ids.add(id);
            }
        } finally { c.close(); }

        if (ids.isEmpty()) return 0;

        int deleted = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Long id : ids) {
                deleted += db.delete("messages", "id=?", new String[]{String.valueOf(id)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return deleted;
    }

    public int deleteOlderThanDays(int days) {
        if (AppLock.isUnlocked() && AppLock.isDecoySession()) {
            return 1;
        }

        // 1. Load custom retention rules from SharedPreferences
        android.content.SharedPreferences prefs = context.getSharedPreferences("whatsthat_prefs", Context.MODE_PRIVATE);
        java.util.Map<String, ?> all = prefs.getAll();

        List<String[]> customRules = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith("retention_days|")) {
                String[] parts = entry.getKey().split("\\|");
                if (parts.length == 3) {
                    try {
                        int customDays = Integer.parseInt(entry.getValue().toString());
                        customRules.add(new String[]{parts[1], parts[2], String.valueOf(customDays)});
                    } catch (Exception ignored) {}
                }
            }
        }

        int deletedCount = 0;
        List<String[]> exclusions = new ArrayList<>();

        // 2. Perform custom cleanups and collect exclusions
        for (String[] rule : customRules) {
            String pkg = rule[0];
            String snd = rule[1];
            int customDays = Integer.parseInt(rule[2]);
            if (customDays > 0) {
                long cutoff = System.currentTimeMillis() - (customDays * 24L * 60L * 60L * 1000L);
                deletedCount += getWritableDatabase().delete("messages", "package_name=? AND sender=? AND received_at<?", new String[]{clean(pkg), clean(snd), String.valueOf(cutoff)});
            }
            exclusions.add(new String[]{pkg, snd});
        }

        // 3. Perform global cleanup excluding custom conversations if days > 0
        if (days > 0) {
            long cutoff = System.currentTimeMillis() - (days * 24L * 60L * 60L * 1000L);
            StringBuilder selection = new StringBuilder("received_at<?");
            List<String> args = new ArrayList<>();
            args.add(String.valueOf(cutoff));

            for (String[] excl : exclusions) {
                selection.append(" AND NOT (package_name=? AND sender=?)");
                args.add(clean(excl[0]));
                args.add(clean(excl[1]));
            }

            deletedCount += getWritableDatabase().delete("messages", selection.toString(), args.toArray(new String[0]));
        }

        return deletedCount;
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
    private String clean(String value) {
        if (value == null) return "Unknown";
        String val = value.trim();
        val = val.replace("\uFE0F", "");
        val = val.replace("\uFE0E", "");
        val = val.replace("\u200B", "");
        return val.trim();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private long parseExportTime(String value) {
        if (value == null || value.trim().isEmpty()) return System.currentTimeMillis();
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(value.trim()).getTime();
        } catch (ParseException ignored) {
            return System.currentTimeMillis();
        }
    }

    private ArrayList<List<String>> parseCsv(String text) {
        ArrayList<List<String>> rows = new ArrayList<>();
        ArrayList<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (ch == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (ch != '\r') {
                cell.append(ch);
            }
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            rows.add(row);
        }
        return rows;
    }

    private boolean isDecoy() {
        return AppLock.isUnlocked() && AppLock.isDecoySession();
    }

    private List<SavedMessage> getDecoyMessages(boolean otherNotices) {
        ArrayList<SavedMessage> rows = new ArrayList<>();
        SimpleDateFormat fullFmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        DateFormat shortFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        long now = System.currentTimeMillis();

        if (otherNotices) {
            rows.add(createDecoyMsg(1001, "Weather Alert", "Thunderstorms and heavy rain expected this evening. Stay safe.", "com.sec.android.easyMute", now - 1000 * 60 * 15, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(1002, "Google Play Store", "5 apps updated successfully in the background.", "com.android.vending", now - 1000 * 60 * 60 * 2, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(1003, "System Care", "Battery charge completed. Unplug charger to preserve battery health.", "android", now - 1000 * 60 * 60 * 5, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(1004, "Drive", "Backup of 12 files completed successfully.", "com.google.android.apps.docs", now - 1000 * 60 * 60 * 12, fullFmt, shortFmt, true));
        } else {
            rows.add(createDecoyMsg(2001, "John (Project)", "Meeting rescheduled to 3 PM. See you there.", "com.whatsapp", now - 1000 * 60 * 10, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(2002, "Uber Support", "Your ride has been successfully booked for tomorrow morning.", "com.whatsapp", now - 1000 * 60 * 60 * 3, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(2003, "Delivery Update", "Your package is out for delivery today. Track on the app.", "com.whatsapp", now - 1000 * 60 * 60 * 6, fullFmt, shortFmt, true));
        }
        return rows;
    }

    private List<SavedMessage> getDecoyConversation(String packageName, String sender) {
        ArrayList<SavedMessage> rows = new ArrayList<>();
        SimpleDateFormat fullFmt = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        DateFormat shortFmt = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        long now = System.currentTimeMillis();

        if ("Weather Alert".equals(sender)) {
            rows.add(createDecoyMsg(100101, sender, "Morning Update: Expected high of 28°C, low of 18°C.", packageName, now - 1000 * 60 * 60 * 8, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(1001, sender, "Thunderstorms and heavy rain expected this evening. Stay safe.", packageName, now - 1000 * 60 * 15, fullFmt, shortFmt, true));
        } else if ("Google Play Store".equals(sender)) {
            rows.add(createDecoyMsg(100201, sender, "Google Chrome and Android System WebView are updating...", packageName, now - 1000 * 60 * 60 * 2 - 1000 * 60 * 5, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(1002, sender, "5 apps updated successfully in the background.", packageName, now - 1000 * 60 * 60 * 2, fullFmt, shortFmt, true));
        } else if ("John (Project)".equals(sender)) {
            rows.add(createDecoyMsg(200101, sender, "Hey, is the document ready?", packageName, now - 1000 * 60 * 60 * 2, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(200102, sender, "Yeah, I'm almost done.", packageName, now - 1000 * 60 * 60 * 1, fullFmt, shortFmt, true));
            rows.add(createDecoyMsg(2001, sender, "Meeting rescheduled to 3 PM. See you there.", packageName, now - 1000 * 60 * 10, fullFmt, shortFmt, true));
        } else {
            rows.add(createDecoyMsg(300001, sender, "This is an archived service update notice.", packageName, now - 1000 * 60 * 60 * 24, fullFmt, shortFmt, true));
        }
        return rows;
    }

    private SavedMessage createDecoyMsg(long id, String sender, String body, String pkg, long receivedAt, SimpleDateFormat fullFmt, DateFormat shortFmt, boolean read) {
        Date d = new Date(receivedAt);
        return new SavedMessage(
                id,
                sender,
                body,
                fullFmt.format(d),
                shortFmt.format(d),
                dateLabel(receivedAt),
                pkg,
                receivedAt,
                1,
                read ? 0 : 1,
                read
        );
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
