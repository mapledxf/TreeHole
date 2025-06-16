package com.miniai.facerecognition.manager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
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

    enum STATE {
        QUITING, IDLE, WAITING, WORKING, ANONYMOUS
    }

    private STATE state = STATE.IDLE;

    private UserInfo currentUser = null;

    private final Handler handler = new Handler();

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
                        changeState(result, callback);

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

    private void changeState(Pair<Integer, UserInfo> result, FaceCallback callback) {
        switch (result.first) {
            //未检测到人脸
            case UserInfo.NO_FACE:
                // 未检测到人脸情况，之前是
                // 1. 检测到人脸，等待注册人脸结果
                // 2. 检测到人脸，但未找到注册信息。
                // 3. 检测到人脸，已找到注册结果。
                // 设置当前状态为退出中，等待5秒，如果还是没有识别到人脸，则发送结束事件。
                if (state == STATE.WAITING
                        || state == STATE.ANONYMOUS
                        || state == STATE.WORKING
                ) {
                    Log.d(TAG, "from " + state + " to " + STATE.QUITING);
                    callback.OnFaceDisappear();
                    state = STATE.QUITING;
                    handler.removeCallbacksAndMessages(null);
                    handler.postDelayed(() -> {
                        currentUser = null;
                        Log.d(TAG, "from " + state + " to " + STATE.IDLE);
                        state = STATE.IDLE;
                        callback.OnSessionEnd();
                    }, 5000);
//                                } else if (state == STATE.QUITING) {
                }
                break;
            case UserInfo.UNKNOWN:
                // 检测到人脸，但是没有找到注册信息
                if (state == STATE.QUITING) {
                    // 检测到人脸，但是没有找到注册信息，如果当前是退出阶段，等待还未超时，
                    if (currentUser == null) {
                        // 检测到人脸，但是没有找到注册信息，如果当前是退出阶段，等待还未超时，
                        // 如果之前没有任何人脸信息，则等待2秒内是否有注册信息返回，超时则认为是匿名用户。
                        Log.d(TAG, "from " + state + " to " + STATE.WAITING + " previous is null");
                        state = STATE.WAITING;
                        handler.removeCallbacksAndMessages(null);
                        handler.postDelayed(() -> {
                            Log.d(TAG, "from " + state + " to " + STATE.ANONYMOUS);
                            state = STATE.ANONYMOUS;
                            currentUser = result.second;
                            callback.OnSessionStart(currentUser.userName);
                        }, 2000);
                    } else if (currentUser.userId == -1) {
                        // 检测到人脸，但是没有找到注册信息，如果当前是退出阶段，等待还未超时，
                        // 如果之前是匿名用户，则判断是否为同一人，如果是则继续session，不是则继续等待超时。
                        if (getSimilarity(currentUser.featData, result.second.featData) > RECOGNIZE_THRESHOLD) {
                            handler.removeCallbacksAndMessages(null);
                            Log.d(TAG, "from " + state + " to " + STATE.ANONYMOUS + " previous is unknown");
                            state = STATE.ANONYMOUS;
                            callback.OnSessionResume(currentUser.userName);
                        }
                    } else if (currentUser.userName.equals(result.second.userName)) {
                        // 检测到人脸，但是没有找到注册信息，如果当前是退出阶段，等待还未超时，
                        // 如果之前是注册用户，则判断是否是同一个用户，是则继续session，不是则继续等待超时。
                        handler.removeCallbacksAndMessages(null);
                        Log.d(TAG, "from " + state + " to " + STATE.WORKING + " previous is " + currentUser.userName);
                        state = STATE.WORKING;
                        callback.OnSessionResume(currentUser.userName);
                    }
                } else if (state == STATE.IDLE) {
                    // 检测到人脸，但是没有找到注册信息
                    // 如果当前是待机状态，则等待注册信息2秒，超时则认为是匿名用户
                    Log.d(TAG, "from " + state + " to " + STATE.WAITING);
                    state = STATE.WAITING;
                    handler.removeCallbacksAndMessages(null);
                    handler.postDelayed(() -> {
                        Log.d(TAG, "from " + state + " to " + STATE.ANONYMOUS);
                        state = STATE.ANONYMOUS;
                        currentUser = result.second;
                        callback.OnSessionStart(currentUser.userName);
                    }, 2000);
//                                } else if (state == STATE.WORKING) {
//                                } else if (state == STATE.WAITING) {
//                                } else if (state == STATE.ANONYMOUS) {
                }
                break;
            case UserInfo.SUCCESS:
                // 检测到人脸，找到注册信息
                if (state == STATE.WAITING) {
                    // 检测到人脸，找到注册信息
                    // 如果之前是在等待注册信息，则直接变为注册状态。
                    handler.removeCallbacksAndMessages(null);
                    Log.d(TAG, "from " + state + " to " + STATE.WORKING);
                    state = STATE.WORKING;
                    callback.OnSessionStart(result.second.userName);
                } else if (state == STATE.QUITING) {
                    // 检测到人脸，找到注册信息
                    // 如果之前是在等待退出
                    if (currentUser == null) {
                        // 检测到人脸，找到注册信息
                        // 如果之前是在等待退出
                        // 如果之前没有人脸信息，则直接变为注册状态。
                        handler.removeCallbacksAndMessages(null);
                        Log.d(TAG, "from " + state + " to " + STATE.WORKING);
                        state = STATE.WORKING;
                        currentUser = result.second;
                        callback.OnSessionStart(currentUser.userName);
                    } else if (currentUser.userId == -1) {
                        // 检测到人脸，找到注册信息
                        // 如果之前是在等待退出
                        // 如果之前是匿名，则判断是否为同一人，是则继续，不是则继续等待超时
                        if (getSimilarity(currentUser.featData, result.second.featData) > RECOGNIZE_THRESHOLD) {
                            handler.removeCallbacksAndMessages(null);
                            Log.d(TAG, "from " + state + " to " + STATE.WORKING + " previous is unknow or null");
                            state = STATE.WORKING;
                            callback.OnSessionResume(currentUser.userName);
                        }
                    } else if(currentUser.userName.equals(result.second.userName)){
                        // 检测到人脸，找到注册信息
                        // 如果之前是在等待退出，
                        // 如果之前是注册用户，则判断是否是同一个用户，是则继续session，不是则继续等待超时。
                        handler.removeCallbacksAndMessages(null);
                        Log.d(TAG, "from " + state + " to " + STATE.WORKING + " previous is " + currentUser.userName);
                        state = STATE.WORKING;
                        callback.OnSessionResume(currentUser.userName);
                    }
                } else if (state == STATE.IDLE) {
                    // 检测到人脸，找到注册信息
                    // 如果之前是在待机，
                    // 直接变为注册用户状态
                    handler.removeCallbacksAndMessages(null);
                    Log.d(TAG, "from " + state + " to " + STATE.WORKING);
                    state = STATE.WORKING;
                    currentUser = result.second;
                    callback.OnSessionStart(currentUser.userName);
//                                } else if (state == STATE.ANONYMOUS) {
//                                } else if (state == STATE.WORKING) {
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
        if (userinfo.first == UserInfo.SUCCESS) {
            return false;
        } else {
            insertUser(name, faceBitmap, featData);
            return true;
        }
    }

    public Pair<Integer, UserInfo> searchFace(byte[] feature) {
        UserInfo userInfo = null;
        float maxScore = 0.0f;
        for (UserInfo user : UserDB.userInfos) {
            float score = getSimilarity(user.featData, feature);
            if (maxScore < score) {
                maxScore = score;
                userInfo = user;
            }
        }
        if (userInfo != null && maxScore > RECOGNIZE_THRESHOLD) {
            userInfo.score = maxScore;
            return new Pair<>(UserInfo.SUCCESS, userInfo);
        } else {
            return new Pair<>(UserInfo.UNKNOWN, new UserInfo(-1, UserInfo.DEFAULT_NAME, null, feature));
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

    public float getSimilarity(byte[] first, byte[] second) {
        return FaceSDK.getInstance().compareFeature(first, second);
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
