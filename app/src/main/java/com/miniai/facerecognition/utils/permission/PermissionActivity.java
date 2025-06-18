package com.miniai.facerecognition.utils.permission;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;


/**
 * @author Xuefeng Ding
 * Created 2019-12-09 15:57
 */
public class PermissionActivity extends Activity {
    private static final String TAG = "[TreeHole]" + "PermissionActivity";

    private final static int REQUEST_CODE_PERMISSION = 202;

    /**
     * onCreate
     * @param savedInstanceState saved instance
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String[] permissions = getIntent().getStringArrayExtra(PermissionHelper.EXTRA_PERMISSION);
        requestPermission(permissions);
    }

    private void requestPermission(String[] permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && permission != null && PermissionHelper.missPermissions(this, permission)) {
            Log.d(TAG, "requesting permission");
            requestPermissions(permission, REQUEST_CODE_PERMISSION);
        } else {
            onPermissionResult(true);
        }
    }

    private void onPermissionResult(boolean granted) {
        Intent intent = new Intent(PermissionHelper.ACTION_CHECK_PERMISSION);
        intent.putExtra(PermissionHelper.EXTRA_PERMISSION_RESULT, granted);
        sendBroadcast(intent);
        finish();
        Log.d(TAG, "onRequestPermissionsResult: granted:" + granted);
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
     *     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
     *
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                onPermissionResult(false);
                return;
            }
        }
        onPermissionResult(true);
    }
}
