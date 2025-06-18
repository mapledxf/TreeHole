package com.miniai.facerecognition.callback;

public interface AsrCallback {
    void onAsrFinalResult(String result);
    void onAsrPartialResult(String partialResult);
    void onAsrError(String error);
}