package com.miniai.facerecognition.callback;

public interface ChatCallback {
    void onChatStart();
    void onChatEnd();

    void onChatError(String message);

    void OnChatEvaluation(String label, String reason);
}