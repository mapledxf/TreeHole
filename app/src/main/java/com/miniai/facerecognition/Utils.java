package com.miniai.facerecognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;

import androidx.appcompat.app.AlertDialog;

public class Utils {

    public static Rect getBestRect(int width, int height, Rect srcRect) {
        if (srcRect == null) {
            return null;
        }
        Rect rect = new Rect(srcRect);

        int maxOverFlow = Math.max(-rect.left, Math.max(-rect.top, Math.max(rect.right - width, rect.bottom - height)));
        if (maxOverFlow >= 0) {
            rect.inset(maxOverFlow, maxOverFlow);
            return rect;
        }

        int padding = rect.height() / 2;

        if (!(rect.left - padding > 0 && rect.right + padding < width && rect.top - padding > 0 && rect.bottom + padding < height)) {
            padding = Math.min(Math.min(Math.min(rect.left, width - rect.right), height - rect.bottom), rect.top);
        }
        rect.inset(-padding, -padding);
        return rect;
    }

    public static Bitmap crop(final Bitmap src, final int srcX, int srcY, int srcCroppedW, int srcCroppedH, int newWidth, int newHeight) {
        final int srcWidth = src.getWidth();
        final int srcHeight = src.getHeight();
        float scaleWidth = ((float) newWidth) / srcCroppedW;
        float scaleHeight = ((float) newHeight) / srcCroppedH;

        final Matrix m = new Matrix();

        m.setScale(1.0f, 1.0f);
        m.postScale(scaleWidth, scaleHeight);
        final Bitmap cropped = Bitmap.createBitmap(src, srcX, srcY, srcCroppedW, srcCroppedH, m,
                true /* filter */);
        return cropped;
    }

    public static void showAlertDialog(Context context, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        // Set the dialog title and message
        builder.setTitle("Warning!");
        builder.setMessage(message + "\nYou may not able to test our SDK!\nContact US and Purchase License and Enjoy!");

        // Set positive button and its click listener
        builder.setPositiveButton("OK", (dialog, which) -> {
            // Handle positive button click, if needed
            dialog.dismiss();
        });

        // Set negative button and its click listener
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            // Handle negative button click, if needed
            dialog.dismiss();
        });

        // Create and show the AlertDialog
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }
}
