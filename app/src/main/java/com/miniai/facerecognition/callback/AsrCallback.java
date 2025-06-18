package com.miniai.facerecognition.callback;

public interface AsrCallback {
    void onResult(String result);
    void onPartialResult(String partialResult);
    void onError(String error);
}