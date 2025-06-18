package com.miniai.facerecognition.chat;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_DEEPSEEK = 1;

    private final int type;
    private final StringBuilder content;

    public ChatMessage(int type, String content) {
        this.type = type;
        this.content = new StringBuilder(content);
    }

    public int getType() {
        return type;
    }

    public StringBuilder getContent() {
        return content;
    }
}