package com.verba.aimlab;

import android.app.*;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.os.Build;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "projection_data";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() { stopSelf(); }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "capture")
            .setContentTitle("VERBA AIM LAB")
            .setContentText("Захват экрана активен")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        startForeground(42, notification);
        if (intent != null && intent.hasExtra(EXTRA_DATA)) {
            int result = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data = intent.getParcelableExtra(EXTRA_DATA);
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            projection.registerCallback(projectionCallback, null);
            startCapture();
        }
        return START_NOT_STICKY;
    }

    private void startCapture() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels, height = metrics.heightPixels, density = metrics.densityDpi;
        reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            android.media.Image image = r.acquireLatestImage();
            if (image != null) {
                // Frame-analysis pipeline entry point. No game input is synthesized.
                image.close();
            }
        }, null);
        display = projection.createVirtualDisplay("VerbaAimCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("capture", "Screen capture", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public void onDestroy() {
        if (display != null) display.release();
        if (reader != null) reader.close();
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
        }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
