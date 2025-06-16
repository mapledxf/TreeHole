package com.miniai.facerecognition;

import android.app.Application;

import com.miniai.facerecognition.manager.FaceManager;

public class App extends Application {
    private static final String TAG = "[TreeHole]";

    private static App application;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;
        init();
    }

    public static App getInstance() {
        return application;
    }

    public void init() {
        FaceManager.getInstance().init();
    }
}
