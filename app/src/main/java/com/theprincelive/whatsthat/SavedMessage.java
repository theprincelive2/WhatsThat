package com.theprincelive.whatsthat;

public class SavedMessage {
    public final String sender;
    public final String body;
    public final String time;

    public SavedMessage(String sender, String body, String time) {
        this.sender = sender;
        this.body = body;
        this.time = time;
    }
}
