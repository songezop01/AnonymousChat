package com.example.anonymouschat;

public class Message {
    String fromUid;
    String text;
    String nickname;
    long timestamp;

    public Message(String fromUid, String text, String nickname, long timestamp) {
        this.fromUid = fromUid;
        this.text = text;
        this.nickname = nickname;
        this.timestamp = timestamp;
    }

    public String getFromUid() {
        return fromUid;
    }

    public String getText() {
        return text;
    }

    public String getNickname() {
        return nickname;
    }

    public long getTimestamp() {
        return timestamp;
    }
}