package com.miniai.facerecognition.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.miniai.facerecognition.R;
import com.miniai.facerecognition.UserActivity;
import com.miniai.facerecognition.UserInfo;
import com.miniai.facerecognition.callback.AsrCallback;
import com.miniai.facerecognition.callback.ChatCallback;
import com.miniai.facerecognition.callback.FaceCallback;
import com.miniai.facerecognition.callback.TtsCallback;
import com.miniai.facerecognition.chat.ChatMessage;
import com.miniai.facerecognition.manager.AsrManager;
import com.miniai.facerecognition.manager.ChatManager;
import com.miniai.facerecognition.manager.FaceManager;
import com.miniai.facerecognition.manager.ReportManager;
import com.miniai.facerecognition.manager.TestManager;
import com.miniai.facerecognition.manager.TtsManager;
import com.miniai.facerecognition.utils.permission.PermissionHelper;

import java.util.List;
import java.util.Queue;

public class TreeHoleActivity extends AppCompatActivity implements FaceCallback, AsrCallback, ChatCallback, TtsCallback {
    private static final String TAG = "[TreeHole]TreeHoleActivity";

    private PreviewView previewView;
    private View faceStatus;
    private View asrStatus;

    private Queue<String> testQueries;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_treehole);

        previewView = findViewById(R.id.preview_view);
        previewView.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserActivity.class);
            startActivity(intent);
        });
        RecyclerView chatRecyclerView = findViewById(R.id.chat_recycler_view);

        View pushToTalk = findViewById(R.id.p2t);
//        pushToTalk.setOnTouchListener((v, event) -> {
//            switch (event.getAction()) {
//                case MotionEvent.ACTION_DOWN:
//                    v.setPressed(true);
//                    AsrManager.getInstance().startAsr();
//                    return true;
//                case MotionEvent.ACTION_UP:
//                    v.setPressed(false);
//                    AsrManager.getInstance().stopAsr();
//                    return true;
//            }
//            return false;
//        });
        pushToTalk.setOnClickListener(v -> {
            testQueries = TestManager.getInstance().getQueries("Danger");
            if (!testQueries.isEmpty()) {
                onAsrFinalResult(testQueries.remove());
            }

        });
        pushToTalk.setVisibility(View.GONE);

        faceStatus = findViewById(R.id.face_status);
        asrStatus = findViewById(R.id.asr_status);

        PermissionHelper permissionHelper = new PermissionHelper(granted -> {
            if (granted) {
                Log.d(TAG, "onCreate: permission granted");
            }
        });
        permissionHelper.checkPermissions(getApplicationContext(), Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.INTERNET);

        ChatManager.getInstance().init(this, chatRecyclerView);

        FaceManager.getInstance().setFaceCallback(this);
        ChatManager.getInstance().setChatCallback(this);
        AsrManager.getInstance().setAsrCallback(this);
        TtsManager.getInstance().setTtsCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!FaceManager.getInstance().isRunning()) {
            FaceManager.getInstance().startFaceRecognition(this, previewView);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            FaceManager.getInstance().stopFaceRecognition();
        } catch (Exception e) {
            Log.e(TAG, "onPause: ", e);
        }
    }

    @Override
    public void OnFaceSessionStart(String userName) {
        runOnUiThread(() -> {
            updateStatus(userName);
            Toast.makeText(TreeHoleActivity.this, "Hello " + userName, Toast.LENGTH_SHORT).show();
            AsrManager.getInstance().startAsr();
        });
    }

    private void updateStatus(String userName) {
        if (UserInfo.DEFAULT_NAME.equals(userName)) {
            faceStatus.setBackgroundResource(android.R.color.holo_blue_dark);
        } else {
            faceStatus.setBackgroundResource(android.R.color.holo_green_dark);
        }
    }

    @Override
    public void OnFaceSessionResume(String userName) {
        runOnUiThread(() -> updateStatus(userName));
    }

    @Override
    public void OnFaceDisappear() {
        runOnUiThread(() -> {
            faceStatus.setBackgroundResource(android.R.color.holo_orange_dark);
            Toast.makeText(TreeHoleActivity.this, "Please show your face ", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void OnFaceSessionEnd(String userName) {
        runOnUiThread(() -> {
            ReportManager.getInstance().report(userName, ChatManager.getInstance().getMessages());
            faceStatus.setBackgroundResource(android.R.color.holo_red_dark);
            Toast.makeText(TreeHoleActivity.this, "Bye ", Toast.LENGTH_SHORT).show();
            AsrManager.getInstance().stopAsr();
            ChatManager.getInstance().reset();
            TtsManager.getInstance().stop();
        });
    }

    @Override
    public void OnError(int errorCode) {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this, "Error " + errorCode, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAsrStatusChanged() {
        runOnUiThread(() -> {
            if (!AsrManager.getInstance().isAsrConnected()) {
                asrStatus.setBackgroundResource(android.R.color.darker_gray);
            } else if (AsrManager.getInstance().isAsrRecognizing()) {
                asrStatus.setBackgroundResource(android.R.color.holo_green_dark);
            } else {
                asrStatus.setBackgroundResource(android.R.color.holo_red_dark);
            }
        });
    }

    @Override
    public void onAsrFinalResult(String result) {
        Log.d(TAG, "onResult: " + result);
        AsrManager.getInstance().stopAsr();
        runOnUiThread(() -> ChatManager.getInstance().addUserMessage(result));
    }

    @Override
    public void onAsrPartialResult(String partialResult) {
        Log.d(TAG, "onPartialResult: " + partialResult);
    }

    @Override
    public void onAsrError(String error) {
        Log.d(TAG, "onError: " + error);
    }

    @Override
    public void onChatStart() {
        Log.d(TAG, "onChatStart: ");
        TtsManager.getInstance().stop();
    }

    @Override
    public void onChatError(String message) {
        Log.e(TAG, "onChatError: " + message);
    }

    @Override
    public void OnChatEnd(List<ChatMessage> messages) {
        Log.d(TAG, "OnChatEnd: ");
    }

    @Override
    public void onTtsStart() {
        AsrManager.getInstance().stopAsr();
    }

    @Override
    public void onTtsFinish() {
        AsrManager.getInstance().startAsr();
        if (testQueries != null && !testQueries.isEmpty()) {
            onAsrFinalResult(testQueries.remove());
        }
    }

    @Override
    public void onTtsError() {

    }
}