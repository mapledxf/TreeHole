package com.miniai.facerecognition.callback;

public interface FaceCallback {
    void OnSessionStart(String userName);

    void OnSessionResume(String userName);

    void OnFaceDisappear();

    void OnSessionEnd();

    void OnError(int errorCode);

}
