package com.verba.aimlab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.Log;

import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector;
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult;
import com.google.mediapipe.tasks.components.containers.Detection;
import com.google.mediapipe.tasks.components.containers.Category;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** On-device frame analysis. It observes and records detections; it never sends game input. */
public final class FrameAnalyzer implements AutoCloseable {
    private static final String TAG = "VerbaFrameAnalyzer";
    private final ObjectDetector detector;
    private final File csvFile;
    private long frameCount = 0;
    private long startedAt = SystemClock.elapsedRealtime();
    private long lastDetectionAt = 0;
    private float previousCenterX = Float.NaN;
    private float previousCenterY = Float.NaN;
    private long previousTimestamp = 0;
    private int trackedId = 0;

    public FrameAnalyzer(Context context) throws IOException {
        BaseOptions base = BaseOptions.builder()
            .setModelAssetPath("efficientdet_lite0.tflite")
            .build();
        ObjectDetector.ObjectDetectorOptions options =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setScoreThreshold(0.35f)
                .setMaxResults(15)
                .build();
        detector = ObjectDetector.createFromOptions(context, options);
        File dir = new File(context.getExternalFilesDir(null), "reports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create reports directory");
        csvFile = new File(dir, "verba_detection_" + System.currentTimeMillis() + ".csv");
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write("elapsed_ms,frame,width,height,label,confidence,left,top,right,bottom,center_x,center_y,delta_x,delta_y,velocity_px_s\n");
        }
        Log.i(TAG, "Model loaded; report=" + csvFile.getAbsolutePath());
    }

    public void analyze(Bitmap bitmap) {
        if (bitmap == null || detector == null) return;
        long now = SystemClock.elapsedRealtime();
        frameCount++;
        try {
            MPImage input = new BitmapImageBuilder(bitmap).build();
            ObjectDetectorResult result = detector.detect(input);
            List<Detection> detections = result.detections();
            boolean foundPerson = false;
            for (Detection detection : detections) {
                List<Category> categories = detection.categories();
                if (categories == null || categories.isEmpty()) continue;
                Category category = categories.get(0);
                String label = category.categoryName();
                if (label == null || !label.equalsIgnoreCase("person")) continue;
                foundPerson = true;
                RectF box = detection.boundingBox();
                float cx = box.centerX();
                float cy = box.centerY();
                float dx = cx - bitmap.getWidth() / 2f;
                float dy = cy - bitmap.getHeight() / 2f;
                float velocity = 0f;
                if (!Float.isNaN(previousCenterX) && now > previousTimestamp) {
                    float dt = (now - previousTimestamp) / 1000f;
                    float distance = (float)Math.hypot(cx - previousCenterX, cy - previousCenterY);
                    velocity = distance / Math.max(dt, 0.001f);
                } else {
                    trackedId++;
                }
                previousCenterX = cx;
                previousCenterY = cy;
                previousTimestamp = now;
                lastDetectionAt = now;
                appendRow(now - startedAt, bitmap, label, category.score(), box, cx, cy, dx, dy, velocity);
                Log.d(TAG, String.format(Locale.US,
                    "PERSON id=%d conf=%.2f center=(%.0f,%.0f) offset=(%.0f,%.0f) velocity=%.1fpx/s",
                    trackedId, category.score(), cx, cy, dx, dy, velocity));
            }
            if (!foundPerson && now - lastDetectionAt > 1500) {
                previousCenterX = Float.NaN;
                previousCenterY = Float.NaN;
                previousTimestamp = 0;
            }
            if (frameCount % 30 == 0) {
                float fps = frameCount * 1000f / Math.max(1, now - startedAt);
                Log.i(TAG, String.format(Locale.US, "ANALYSIS frames=%d fps=%.1f detections=%d",
                    frameCount, fps, detections.size()));
            }
            input.close();
        } catch (Exception e) {
            Log.e(TAG, "Frame inference failed", e);
        }
    }

    private void appendRow(long elapsed, Bitmap bitmap, String label, float confidence,
                           RectF box, float cx, float cy, float dx, float dy, float velocity) {
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(String.format(Locale.US,
                "%d,%d,%d,%d,%s,%.4f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f\n",
                elapsed, frameCount, bitmap.getWidth(), bitmap.getHeight(), label, confidence,
                box.left, box.top, box.right, box.bottom, cx, cy, dx, dy, velocity));
        } catch (IOException e) {
            Log.e(TAG, "Cannot write detection report", e);
        }
    }

    public String getReportPath() {
        return csvFile.getAbsolutePath();
    }

    @Override public void close() {
        detector.close();
    }
}
