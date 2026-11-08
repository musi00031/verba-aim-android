package com.verba.aimlab;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureService extends Service {
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_DATA = "projection_data";
    private static final String TAG = "VerbaCapture";
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private ExecutorService analysisExecutor;
    private FrameAnalyzer analyzer;
    private volatile long lastFrameAt = 0;

    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() {
            Log.i(TAG, "MediaProjection stopped by system");
            stopSelf();
        }
    };

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, "capture")
            .setContentTitle("VERBA AIM LAB")
            .setContentText("Захват и анализ кадров активны")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        startForeground(42, notification);
        if (intent != null && intent.hasExtra(EXTRA_DATA) && projection == null) {
            int result = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
            Intent data;
            if (Build.VERSION.SDK_INT >= 33) {
                data = intent.getParcelableExtra(EXTRA_DATA, Intent.class);
            } else {
                //noinspection deprecation
                data = intent.getParcelableExtra(EXTRA_DATA);
            }
            if (data == null) {
                Log.e(TAG, "Missing MediaProjection permission result");
                stopSelf();
                return START_NOT_STICKY;
            }
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(result, data);
            projection.registerCallback(projectionCallback, null);
            analysisExecutor = Executors.newSingleThreadExecutor();
            startCapture();
        }
        return START_NOT_STICKY;
    }

    private void startCapture() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int density = metrics.densityDpi;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(r -> {
            Image image = null;
            try {
                image = r.acquireLatestImage();
                if (image == null) return;
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - lastFrameAt < 250) return;
                lastFrameAt = now;
                Bitmap frame = imageToBitmap(image);
                if (frame != null && analysisExecutor != null) {
                    analysisExecutor.execute(() -> {
                        try {
                            if (analyzer == null) analyzer = new FrameAnalyzer(getApplicationContext());
                            analyzer.analyze(frame);
                        } catch (Exception e) {
                            Log.e(TAG, "Cannot initialize frame analyzer", e);
                        } finally {
                            frame.recycle();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Frame capture failed", e);
            } finally {
                if (image != null) image.close();
            }
        }, null);
        display = projection.createVirtualDisplay("VerbaAimCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, null);
        Log.i(TAG, "Capture started " + width + "x" + height + "; analysis interval=250ms");
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes == null || planes.length == 0) return null;
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int rowOffset = y * rowStride;
            for (int x = 0; x < width; x++) {
                int offset = rowOffset + x * pixelStride;
                if (offset + 3 >= bytes.length) continue;
                int red = bytes[offset] & 0xff;
                int green = bytes[offset + 1] & 0xff;
                int blue = bytes[offset + 2] & 0xff;
                int alpha = bytes[offset + 3] & 0xff;
                pixels[y * width + x] = Color.argb(alpha, red, green, blue);
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
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
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
        if (analyzer != null) {
            analyzer.close();
            analyzer = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
