package com.miniai.facerecognition.callback;

public interface FaceCallback {
    void OnFaceSessionStart(String userName);

    void OnFaceSessionResume(String userName);

    void OnFaceDisappear();

    void OnFaceSessionEnd();

    void OnError(int errorCode);

}
