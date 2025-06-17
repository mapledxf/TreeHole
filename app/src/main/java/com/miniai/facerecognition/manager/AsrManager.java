package com.miniai.facerecognition.manager;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.k2fsa.sherpa.ncnn.IAsrService;
import com.k2fsa.sherpa.ncnn.IRecognitionCallback;
import com.miniai.facerecognition.App;

public class AsrManager {
    private static final String TAG = "[TreeHole]AsrManager";
    private static final String SERVICE_ACTION = "com.k2fsa.sherpa.ncnn.RECOGNITION_SERVICE_ACTION";
    private static final String SERVICE_PACKAGE_NAME = "com.k2fsa.sherpa.ncnn";

    private IAsrService sherpaNcnnService;
    private final IRecognitionCallback.Stub clientCallback = new IRecognitionCallback.Stub() {
        @Override
        public void onResult(String result) {
            Log.d(TAG, "Final Result: " + result);

        }

        @Override
        public void onPartialResult(String partialResult) {
            Log.d(TAG, "Partial Result: " + partialResult);
        }

        @Override
        public void onError(String errorMessage) {
            Log.e(TAG, "Error from service: " + errorMessage);
        }
    };

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            sherpaNcnnService = IAsrService.Stub.asInterface(service);
            isServiceBound = true;
            Log.d(TAG, "ASR Service connected");
            // 连接成功后可以立即初始化模型
            try {
                sherpaNcnnService.initModel();
            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected: ", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            sherpaNcnnService = null;
            isServiceBound = false;
            Log.d(TAG, "Service disconnected");
        }
    };
    private boolean isServiceBound = false;

    private static final class Holder {
        private static final AsrManager INSTANCE = new AsrManager();
    }

    /**
     * Default constructor
     */
    private AsrManager() {
    }

    /**
     * Single instance.
     *
     * @return the instance.
     */
    public static AsrManager getInstance() {
        return Holder.INSTANCE;
    }

    public boolean init() {
        if (!isServiceBound) {
            Intent intent = new Intent();
            ComponentName componentName = new ComponentName(
                    "com.k2fsa.sherpa.ncnn",          // Service 所在包名
                    "com.k2fsa.sherpa.ncnn.AsrService" // Service 的完整类名
            );
            intent.setComponent(componentName);
            boolean bindResult = App.getInstance().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
            Log.i(TAG, "Attempting to bind service, result: " + bindResult);
        }
        return true;
    }

    public boolean start() {
        if (!isServiceBound || sherpaNcnnService == null) {
            Log.e(TAG, "Service not bound yet");
            return false;
        }
        try {
            if (!sherpaNcnnService.isRecording()) {
                // Ensure model is initialized before calling startRecording
                // if (!initializedInOnServiceConnected) {
                //     sherpaNcnnService.initModel();
                // }

                boolean success = sherpaNcnnService.startRecording(clientCallback);

                if (success) {
                    return true;
                } else {
                    Log.e(TAG, "start: Failed to start recording. Check logs.");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "start: ", e);
            return false;
        }
    }

    public boolean stop() {
        if (!isServiceBound || sherpaNcnnService == null) {
            Log.e(TAG, "Service not bound yet");
            return false;
        }
        try {
            if (sherpaNcnnService.isRecording()) {
                sherpaNcnnService.stopRecording();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "start: ", e);
            return false;
        }
    }

    public void release() {
        if (isServiceBound) {
            App.getInstance().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
