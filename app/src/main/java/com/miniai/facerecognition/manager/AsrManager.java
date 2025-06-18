package com.miniai.facerecognition.manager;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.k2fsa.sherpa.ncnn.IAsrService;
import com.k2fsa.sherpa.ncnn.IRecognitionCallback;
import com.miniai.facerecognition.App;
import com.miniai.facerecognition.callback.AsrCallback;

public class AsrManager {
    private static final String TAG = "[TreeHole]AsrManager";
    private IAsrService asrService;
    private AudioRecord audioRecord = null;
    private Thread recordingThread;
    private boolean isRecording = false;
    private boolean isRecognizing = false;
    private AsrCallback asrCallback = null;

    private final IRecognitionCallback.Stub clientCallback = new IRecognitionCallback.Stub() {
        @Override
        public void onResult(String result) {
            Log.d(TAG, "onResult: " + result);
            if (asrCallback != null) {
                asrCallback.onAsrFinalResult(result);
            }
        }

        @Override
        public void onPartialResult(String partialResult) {
            Log.d(TAG, "onPartialResult: " + partialResult);
            if (asrCallback != null) {
                asrCallback.onAsrPartialResult(partialResult);
            }
        }

        @Override
        public void onError(String errorMessage) {
            Log.e(TAG, "onError: " + errorMessage);
            if (asrCallback != null) {
                asrCallback.onAsrError(errorMessage);
            }
        }
    };

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            asrService = IAsrService.Stub.asInterface(service);
            isServiceBound = true;
            Log.d(TAG, "ASR Service connected");
            // 连接成功后可以立即初始化模型
            try {
                asrService.initModel(clientCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected: ", e);
            }
            startRecording();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            asrService = null;
            isServiceBound = false;
            Log.d(TAG, "Service disconnected");
            stopRecording();
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
            if (!bindResult) {
                new Handler().postDelayed(() -> {
                    Log.w(TAG, "绑定失败，尝试重新绑定...");
                    init();
                }, 2000); // 2秒后重试
            }
            Log.i(TAG, "Attempting to bind service, result: " + bindResult);
        }
        return true;
    }

    public void setAsrCallback(AsrCallback callback) {
        this.asrCallback = callback;
    }

    public void startAsr() {
        if (!isServiceBound || asrService == null) {
            Log.e(TAG, "Service not bound yet");
            return;
        }
        try {
            if (!isRecognizing) {
                asrService.reset(true);
                isRecognizing = true;
                Log.d(TAG, "startAsr: ");
            }
        } catch (Exception e) {
            Log.e(TAG, "start: ", e);
        }
    }

    public void stopAsr() {
        if (!isServiceBound || asrService == null) {
            Log.e(TAG, "Service not bound yet");
            return;
        }
        if (isRecognizing) {
            isRecognizing = false;
            Log.d(TAG, "stopAsr: ");
        }
    }

    public void release() {
        if (isServiceBound) {
            App.getInstance().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    private void startRecording() {
        if (isRecording) {
            Log.d(TAG, "startRecording: skip");
            return;
        }

        isRecording = true;

        int sampleRateInHz = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int numBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
        int audioSource = MediaRecorder.AudioSource.MIC;

        if (ActivityCompat.checkSelfPermission(App.getInstance(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "startRecording: missing permission");
        }

        audioRecord = new AudioRecord(
                audioSource,
                sampleRateInHz,
                channelConfig,
                audioFormat,
                numBytes * 2
        );

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "Failed to initialize AudioRecord");
            audioRecord = null;
            return;
        }

        audioRecord.startRecording();

        recordingThread = new Thread(() -> {
            float interval = 0.1f; // 100 ms
            int bufferSize = (int) (interval * sampleRateInHz); // in samples
            short[] buffer = new short[bufferSize];

            //noinspection ConditionalBreakInInfiniteLoop
            while (isRecording) {
                if (audioRecord == null) break;
                if (isRecognizing) {
                    int ret = audioRecord.read(buffer, 0, buffer.length);
                    if (ret > 0) {
                        float[] samples = new float[ret];
                        for (int i = 0; i < ret; i++) {
                            samples[i] = buffer[i] / 32768.0f;
                        }
                        // 这里应该调用 processSamples 方法
                        if (asrService != null) {
                            try {
                                asrService.processSamples(samples);
                            } catch (RemoteException e) {
                                Log.e(TAG, "Failed to process samples", e);
                            }
                        } else {
                            Log.e(TAG, "startRecording: asrService is null");
                        }
                    }
                }
            }
        });
        recordingThread.start();
        Log.i(TAG, "Started recording");
    }

    private void stopRecording() {
        if (!isRecording) {
            Log.w(TAG, "Recording is not in progress.");
            return;
        }
        isRecording = false;
        if (recordingThread != null) {
            try {
                recordingThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, "Recording thread interrupted", e);
            }
            recordingThread = null;
        }
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
            audioRecord.release();
            audioRecord = null;
        }
        Log.i(TAG, "Stopped recording");
    }
}
