package com.miniai.facerecognition.callback;

public interface TtsCallback {
    void onTtsStart();
    void onTtsFinish();
    void onTtsError();
}