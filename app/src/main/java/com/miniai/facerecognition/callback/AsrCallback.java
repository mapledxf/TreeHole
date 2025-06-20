package com.miniai.facerecognition.callback;

public interface AsrCallback {
    void onAsrStart();
    void onAsrStop();
    void OnAsrConnected();
    void OnAsrDisconnected();
    void onAsrFinalResult(String result);
    void onAsrPartialResult(String partialResult);
    void onAsrError(String error);
}