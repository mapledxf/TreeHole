package com.miniai.facerecognition.utils.permission;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

/**
 * @author Xuefeng Ding
 * Created 2019-12-09 16:00
 */
public class PermissionHelper {
    private static final String TAG = "[TreeHole]" + "PermissionHelper";
    static final String ACTION_CHECK_PERMISSION = "com.vwm.speech.action.CHECK_PERMISSION";
    static final String EXTRA_PERMISSION = "permission";
    static final String EXTRA_PERMISSION_RESULT = "permission_result";

    private final OnPermissionResultCallback callback;

    public PermissionHelper(OnPermissionResultCallback listener) {
        this.callback = listener;
    }

    static boolean missPermissions(Context context, String[] permissions) {
        for (String permission : permissions) {
            if (context.checkPermission(permission, Process.myPid(), Process.myUid()) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "permission missing: " + permission);
                return true;
            }
        }
        return false;
    }

    /**
     * check if has the permissions
     * @param context context
     * @param permissions permissions
     */
    public void checkPermissions(Context context, String... permissions) {
        if (PermissionHelper.missPermissions(context, permissions)) {
            Log.e(TAG, "checkPermissions: false");
            registerInitReceiver(context);

            Intent intent = new Intent(context, PermissionActivity.class);
            intent.putExtra(EXTRA_PERMISSION, permissions);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            Log.d(TAG, "checkPermissions: true");
            callback.onPermissionGranted(true);
        }
    }

    private void registerInitReceiver(Context context) {
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                callback.onPermissionGranted(intent.getBooleanExtra(EXTRA_PERMISSION_RESULT, false));
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(ACTION_CHECK_PERMISSION));
    }

    public interface OnPermissionResultCallback {
        void onPermissionGranted(boolean granted);
    }
}
