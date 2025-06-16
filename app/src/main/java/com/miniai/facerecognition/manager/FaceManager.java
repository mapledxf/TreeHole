package com.miniai.facerecognition.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.fm.face.FaceBox;
import com.fm.face.FaceSDK;
import com.google.common.util.concurrent.ListenableFuture;
import com.miniai.facerecognition.App;
import com.miniai.facerecognition.BitmapUtils;
import com.miniai.facerecognition.UserDB;
import com.miniai.facerecognition.UserInfo;
import com.miniai.facerecognition.Utils;
import com.miniai.facerecognition.callback.FaceCallback;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FaceManager {
    private static final String TAG = "[TreeHole]FaceManager";

    private static final int FRAME_WIDTH = 720;
    private static final int FRAME_HEIGHT = 1280;
    private static final float LIVENESS_THRESHOLD = 0.5f;
    private static final float RECOGNIZE_THRESHOLD = 0.78f;
    private final AtomicBoolean isRunning;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private UserDB userDB;

    enum STATE{
        QUITING, IDLE, WAITING, WORKING, ANONYMOUS
    }

    private STATE state = STATE.IDLE;

    private String userName = "";

    private Handler handler = new Handler();

    private static final class Holder {
        private static final FaceManager INSTANCE = new FaceManager();
    }

    /**
     * Default constructor
     */
    private FaceManager() {
        isRunning = new AtomicBoolean(false);
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static FaceManager getInstance() {
        return Holder.INSTANCE;
    }

    public boolean init() {
        Context application = App.getInstance();
        Log.d(TAG, "init FaceSDK");
        cameraProviderFuture = ProcessCameraProvider.getInstance(application);
        userDB = new UserDB(application);

        int ret = FaceSDK.createInstance(application).init(application.getAssets());
        switch (ret) {
            case FaceSDK.SDK_SUCCESS:
                userDB.loadUsers();
                Log.d(TAG, "FaceSDK init success");
                return true;
            case FaceSDK.SDK_ACTIVATE_APPID_ERROR:
                Utils.showAlertDialog(application, "AppID Error");
                return false;
            case FaceSDK.SDK_ACTIVATE_INVALID_LICENSE:
                Utils.showAlertDialog(application, "Invalid License");
                return false;
            case FaceSDK.SDK_ACTIVATE_LICENSE_EXPIRED:
                Utils.showAlertDialog(application, "License Expired");
                return false;
            case FaceSDK.SDK_NO_ACTIVATED:
                Utils.showAlertDialog(application, "Not Activated");
                return false;
            case FaceSDK.SDK_INIT_ERROR:
            default:
                Utils.showAlertDialog(application, "Init Error");
                return false;
        }
    }


    public void startFaceRecognition(AppCompatActivity activity, PreviewView previewView, FaceCallback callback) {
        isRunning.set(true);

        cameraProviderFuture.addListener(() -> {
            try {
                ImageAnalysis imageFrameAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(FRAME_WIDTH, FRAME_HEIGHT))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), image -> {
                    if (isRunning.get()) {
                        // Rotated bitmap for the FaceNet model
                        @SuppressLint("UnsafeOptInUsageError")
                        Bitmap frameBitmap = BitmapUtils.imageToBitmap(Objects.requireNonNull(image.getImage()), image.getImageInfo().getRotationDegrees());
                        Pair<Integer, UserInfo> result = detectFace(frameBitmap);

                        switch (result.first) {
                            case UserInfo.NO_FACE:
                                if (state == STATE.WAITING) {
                                    state = STATE.QUITING;
                                    handler.removeCallbacksAndMessages(null);
                                    handler.postDelayed(() -> {
                                        state = STATE.IDLE;
                                        callback.OnFaceDisappear();
                                    }, 3000);
                                } else if (state == STATE.ANONYMOUS) {
                                    state = STATE.QUITING;
                                    handler.removeCallbacksAndMessages(null);
                                    handler.postDelayed(() -> {
                                        state = STATE.IDLE;
                                        callback.OnFaceDisappear();
                                    }, 3000);
                                } else if (state == STATE.QUITING) {

                                } else if (state == STATE.WORKING) {
                                    state = STATE.QUITING;
                                    handler.removeCallbacksAndMessages(null);
                                    handler.postDelayed(() -> {
                                        state = STATE.IDLE;
                                        callback.OnFaceDisappear();
                                    }, 3000);
                                } else if (state == STATE.IDLE) {
                                    state = STATE.QUITING;
                                    handler.removeCallbacksAndMessages(null);
                                    handler.postDelayed(() -> {
                                        state = STATE.IDLE;
                                        callback.OnFaceDisappear();
                                    }, 3000);
                                }
                                break;
                            case UserInfo.UNKNOWN:
                                if (state == STATE.WAITING) {

                                } else if (state == STATE.ANONYMOUS) {

                                } else if (state == STATE.QUITING) {
                                    handler.removeCallbacksAndMessages(null);
                                    if (TextUtils.isEmpty(userName)) {
                                        state = STATE.ANONYMOUS;
                                    } else {
                                        state = STATE.WORKING;
                                    }
                                } else if (state == STATE.WORKING) {

                                } else if (state == STATE.IDLE) {
                                    state = STATE.WAITING;
                                    handler.removeCallbacksAndMessages(null);
                                    handler.postDelayed(() -> {
                                        state = STATE.ANONYMOUS;
                                        callback.OnFaceUnknown();
                                    }, 1000);
                                }
                                break;
                            case UserInfo.SUCCESS:
                                if (state == STATE.WAITING) {
                                    handler.removeCallbacksAndMessages(null);
                                    state = STATE.WORKING;
                                    callback.OnFaceRecognized(result.second.userName);
                                } else if (state == STATE.ANONYMOUS) {

                                } else if (state == STATE.QUITING) {
                                    handler.removeCallbacksAndMessages(null);
                                    if (TextUtils.isEmpty(userName)) {
                                        state = STATE.ANONYMOUS;
                                    } else {
                                        state = STATE.WORKING;
                                    }
                                } else if (state == STATE.WORKING) {

                                } else if (state == STATE.IDLE) {
                                    handler.removeCallbacksAndMessages(null);
                                    state = STATE.WORKING;
                                    userName = result.second.userName;
                                    callback.OnFaceRecognized(userName);
                                }
                                break;
                            case UserInfo.FAKE:
//                                if (state != STATE.ERROR) {
//                                    state = STATE.ERROR;
//                                    callback.OnError(UserInfo.FAKE);
//                                }
                                break;
                            case UserInfo.MULTI_FACE:
//                                if (state != STATE.ERROR) {
//                                    state = STATE.ERROR;
//                                    callback.OnError(UserInfo.MULTI_FACE);
//                                }
                                break;

                        }
                    }
                    image.close();
                });


                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                cameraProviderFuture.get().bindToLifecycle(
                        activity,
                        new CameraSelector.Builder()
                                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                .build(),
                        preview,
                        imageFrameAnalysis
                );
            } catch (Exception e) {
                Log.e(TAG, "run: ", e);
            }
        }, ContextCompat.getMainExecutor(App.getInstance()));
    }

    public Bitmap cropFaceBitmap(Bitmap frameBitmap, FaceBox faceBox) {
        Rect faceRect = new Rect(faceBox.left, faceBox.top, faceBox.right, faceBox.bottom);
        Rect cropRect = Utils.getBestRect(frameBitmap.getWidth(), frameBitmap.getHeight(), faceRect);
        return Utils.crop(frameBitmap, cropRect.left, cropRect.top, cropRect.width(), cropRect.height(), 250, 250);
    }

    public Pair<Integer, FaceBox> getFaceBox(Bitmap frameBitmap) {
        float livenessScore;
        List<FaceBox> faceResults = FaceSDK.getInstance().detectFace(frameBitmap);
        if (faceResults != null && !faceResults.isEmpty()) {
            if (faceResults.size() == 1) {
                FaceBox faceResult = faceResults.get(0);
                livenessScore = FaceSDK.getInstance().checkLiveness(frameBitmap, faceResult);
                if (livenessScore > LIVENESS_THRESHOLD) {
                    return new Pair<>(UserInfo.SUCCESS, faceResult);
                } else {
                    return new Pair<>(UserInfo.FAKE, null);
                }
            } else {
                return new Pair<>(UserInfo.MULTI_FACE, null);
            }
        } else {
            return new Pair<>(UserInfo.NO_FACE, null);
        }
    }

    public Pair<Integer, UserInfo> detectFace(Bitmap frameBitmap) {
        Pair<Integer, FaceBox> faceBox = getFaceBox(frameBitmap);
        if (faceBox.first != UserInfo.SUCCESS) {
            return new Pair<>(faceBox.first, null);
        }

        byte[] feature = FaceSDK.getInstance().extractFeature(frameBitmap, faceBox.second);
        return searchFace(feature);
    }

    public boolean insertUser(String name, Bitmap faceBitmap, Bitmap frameBitmap, FaceBox faceBox) {
        byte[] featData = FaceSDK.getInstance().extractFeature(frameBitmap, faceBox);
        Pair<Integer, UserInfo> userinfo = FaceManager.getInstance().searchFace(featData);
        if( userinfo.first == UserInfo.SUCCESS){
            return false;
        } else {
            insertUser(name, faceBitmap, featData);
            return true;
        }
    }

    public Pair<Integer, UserInfo> searchFace(byte[] feature) {
        UserInfo userInfo = new UserInfo();
        float maxScore = 0.0f;
        for (UserInfo user : UserDB.userInfos) {
            float score = FaceSDK.getInstance().compareFeature(user.featData, feature);
            if (maxScore < score) {
                maxScore = score;
                userInfo = user;
            }
        }
        if (maxScore > RECOGNIZE_THRESHOLD) {
            userInfo.score = maxScore;
            return new Pair<>(UserInfo.SUCCESS, userInfo);
        } else {
            return new Pair<>(UserInfo.UNKNOWN, null);
        }
    }

    public void stopFaceRecognition() {
        isRunning.set(false);
        try {
            cameraProviderFuture.get().unbindAll();
        } catch (Exception e) {
            Log.e(TAG, "stop: ", e);
        }
    }

    public void deleteUser(String userName) {
        userDB.deleteUser(userName);
    }

    public void deleteAllUser() {
        userDB.deleteAllUser();
    }

    private void insertUser(String name, Bitmap faceImage, byte[] featData) {
        int userId = userDB.insertUser(name, faceImage, featData);
        UserInfo face = new UserInfo(userId, name, faceImage, featData);
        UserDB.userInfos.add(face);
    }
}
