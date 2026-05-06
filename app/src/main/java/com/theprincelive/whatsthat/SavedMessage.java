package com.theprincelive.whatsthat;

public class SavedMessage {
    public final long id;
    public final String sender;
    public final String body;
    public final String time;
    public final String shortTime;
    public final String dateLabel;
    public final String packageName;
    public final long receivedAt;
    public final int messageCount;
    public final int unreadCount;
    public final boolean read;

    public SavedMessage(long id, String sender, String body, String time, String shortTime, String dateLabel, String packageName, long receivedAt) {
        this(id, sender, body, time, shortTime, dateLabel, packageName, receivedAt, 1, 0, true);
    }

    public SavedMessage(long id, String sender, String body, String time, String shortTime, String dateLabel, String packageName, long receivedAt, int messageCount) {
        this(id, sender, body, time, shortTime, dateLabel, packageName, receivedAt, messageCount, 0, true);
    }

    public SavedMessage(long id, String sender, String body, String time, String shortTime, String dateLabel, String packageName, long receivedAt, int messageCount, int unreadCount, boolean read) {
        this.id = id;
        this.sender = sender;
        this.body = body;
        this.time = time;
        this.shortTime = shortTime;
        this.dateLabel = dateLabel;
        this.packageName = packageName;
        this.receivedAt = receivedAt;
        this.messageCount = Math.max(1, messageCount);
        this.unreadCount = Math.max(0, unreadCount);
        this.read = read;
    }
}
