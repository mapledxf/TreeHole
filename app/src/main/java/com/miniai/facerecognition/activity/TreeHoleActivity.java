package com.miniai.facerecognition.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;

import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.miniai.facerecognition.R;
import com.miniai.facerecognition.UserActivity;
import com.miniai.facerecognition.callback.FaceCallback;
import com.miniai.facerecognition.manager.FaceManager;



public class TreeHoleActivity extends AppCompatActivity implements FaceCallback {
    private static final String TAG = "TreeHoleActivity";

    PreviewView previewView;

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA},
                    101
            );
        } else {
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
    public void OnFaceRecognized(String userName) {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this,"Hello " + userName, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void OnFaceUnknown() {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this,"Hello ", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void OnFaceDisappear() {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this,"Bye ", Toast.LENGTH_SHORT).show());
    }

    @Override
    public void OnError(int errorCode) {
        runOnUiThread(() -> Toast.makeText(TreeHoleActivity.this,"Error " + errorCode, Toast.LENGTH_SHORT).show());
    }
}