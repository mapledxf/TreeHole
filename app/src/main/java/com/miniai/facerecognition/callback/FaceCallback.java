package com.miniai.facerecognition.callback;

public interface FaceCallback {
    void OnFaceRecognized(String userName);

    void OnFaceUnknown();

    void OnFaceDisappear();

    void OnError(int errorCode);

}
