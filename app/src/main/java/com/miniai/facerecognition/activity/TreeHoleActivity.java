package com.miniai.facerecognition.activity;

import android.Manifest;
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
import com.miniai.facerecognition.manager.AsrManager;
import com.miniai.facerecognition.manager.ChatManager;
import com.miniai.facerecognition.manager.FaceManager;
import com.miniai.facerecognition.utils.permission.PermissionHelper;

public class TreeHoleActivity extends AppCompatActivity implements FaceCallback, AsrCallback, ChatCallback {
    private static final String TAG = "[TreeHole]TreeHoleActivity";

    private PreviewView previewView;
    private View status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_treehole);

        previewView = findViewById(R.id.preview_view);
        previewView.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserActivity.class);
            startActivity(intent);
//            onResult("我的老师打我");  //测试deepseek
        });
        RecyclerView chatRecyclerView = findViewById(R.id.chat_recycler_view);
        ChatManager.getInstance().init(this, chatRecyclerView, this);

        status = findViewById(R.id.status);

        PermissionHelper permissionHelper = new PermissionHelper(granted -> {
            if (granted) {
                Log.d(TAG, "onCreate: permission granted");
            }
        });
        permissionHelper.checkPermissions(getApplicationContext(),
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.INTERNET);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!FaceManager.getInstance().isRunning()) {
            FaceManager.getInstance().startFaceRecognition(this, previewView, this);
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
    public void OnSessionStart(String userName) {
        runOnUiThread(() -> {
            updateStatus(userName);
            Toast.makeText(TreeHoleActivity.this, "Hello " + userName, Toast.LENGTH_SHORT).show();
            AsrManager.getInstance().startAsr(this);
        });
    }

    private void updateStatus(String userName) {
        if (UserInfo.DEFAULT_NAME.equals(userName)) {
            status.setBackgroundResource(android.R.color.holo_blue_dark);
        } else {
            status.setBackgroundResource(android.R.color.holo_green_dark);
        }
    }

    @Override
    public void OnSessionResume(String userName) {
        runOnUiThread(() -> updateStatus(userName));
    }

    @Override
    public void OnFaceDisappear() {
        runOnUiThread(() -> {
            status.setBackgroundResource(android.R.color.holo_orange_dark);
            Toast.makeText(TreeHoleActivity.this, "Please show your face ", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void OnSessionEnd() {
        runOnUiThread(() -> {
            status.setBackgroundResource(android.R.color.holo_red_dark);
            Toast.makeText(TreeHoleActivity.this, "Bye ", Toast.LENGTH_SHORT).show();
            AsrManager.getInstance().stopAsr();
            ChatManager.getInstance().reset();
        });
    }

    @Override
    public void OnError(int errorCode) {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this, "Error " + errorCode, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onResult(String result) {
        Log.d(TAG, "onResult: " + result);
        runOnUiThread(() -> ChatManager.getInstance().addUserMessage(result));
    }

    @Override
    public void onPartialResult(String partialResult) {
        Log.d(TAG, "onPartialResult: " + partialResult);
    }

    @Override
    public void onError(String error) {
        Log.d(TAG, "onError: " + error);
    }

    @Override
    public void onChatStart() {
        Log.d(TAG, "onChatStart: ");
    }

    @Override
    public void onChatEnd() {
        Log.d(TAG, "onChatEnd: ");
    }

    @Override
    public void onChatError(String message) {
        Log.e(TAG, "onChatError: " + message);
    }

    @Override
    public void OnEvaluation(String label, String reason) {
        if (!"无".equals(label)) {
            runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this, label + " \n" + reason, Toast.LENGTH_LONG).show());
        }
    }
}