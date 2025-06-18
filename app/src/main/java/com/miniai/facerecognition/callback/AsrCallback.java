package com.miniai.facerecognition.callback;

public interface AsrCallback {
    void onAsrStatusChanged();
    void onAsrFinalResult(String result);
    void onAsrPartialResult(String partialResult);
    void onAsrError(String error);
}