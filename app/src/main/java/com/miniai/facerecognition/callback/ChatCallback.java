package com.miniai.facerecognition.callback;

import com.miniai.facerecognition.chat.ChatMessage;

import java.util.List;

public interface ChatCallback {
    void onChatStart();

    void onChatError(String message);

    void OnChatEnd(List<ChatMessage> messages);
}