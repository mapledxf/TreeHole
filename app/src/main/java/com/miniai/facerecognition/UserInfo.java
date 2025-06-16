package com.miniai.facerecognition;

import android.graphics.Bitmap;

public class UserInfo {

    public static final int SUCCESS = 0;
    public static final int NO_FACE = -1;
    public static final int MULTI_FACE = -2;
    public static final int FAKE = -3;
    public static final int UNKNOWN = -4;

    public String userName;
    public Bitmap faceImage;
    public byte[] featData;
    public int userId;

    public float score = -1f;

    public UserInfo() {

    }

    public UserInfo(int userId, String userName, Bitmap faceImage, byte[] featData) {
        this.userId = userId;
        this.userName = userName;
        this.faceImage = faceImage;
        this.featData = featData;
    }
}
