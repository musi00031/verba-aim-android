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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Visible visual-analysis pipeline. It does not inject or synthesize game input. */
public final class FrameAnalyzer implements AutoCloseable {
    private static final String TAG = "VerbaFrameAnalyzer";
    private static final long TRACK_TTL_MS = 1200L;
    private final ObjectDetector detector;
    private final File csvFile;
    private final Listener listener;
    private final List<Track> tracks = new ArrayList<>();
    private long frameCount = 0;
    private final long startedAt = SystemClock.elapsedRealtime();
    private int nextTrackId = 1;

    public interface Listener {
        void onTargets(List<Target> targets, int frameWidth, int frameHeight);
    }

    public static final class Target {
        public final int id;
        public final float left, top, right, bottom;
        public final float aimX, aimY;
        public final float confidence;
        public final float velocityX, velocityY;

        Target(int id, RectF box, float aimX, float aimY, float confidence,
               float velocityX, float velocityY) {
            this.id = id;
            this.left = box.left;
            this.top = box.top;
            this.right = box.right;
            this.bottom = box.bottom;
            this.aimX = aimX;
            this.aimY = aimY;
            this.confidence = confidence;
            this.velocityX = velocityX;
            this.velocityY = velocityY;
        }
    }

    private static final class Track {
        int id;
        RectF box;
        long timestamp;
        float vx, vy;
        float confidence;
        Track(int id, RectF box, long timestamp, float confidence) {
            this.id = id;
            this.box = new RectF(box);
            this.timestamp = timestamp;
            this.confidence = confidence;
        }
    }

    public FrameAnalyzer(Context context, Listener listener) throws IOException {
        this.listener = listener;
        BaseOptions base = BaseOptions.builder()
            .setModelAssetPath("efficientdet_lite0.tflite")
            .build();
        ObjectDetector.ObjectDetectorOptions options =
            ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.IMAGE)
                .setScoreThreshold(0.30f)
                .setMaxResults(20)
                .build();
        detector = ObjectDetector.createFromOptions(context, options);
        File dir = new File(context.getExternalFilesDir(null), "reports");
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create reports directory");
        csvFile = new File(dir, "verba_tracking_" + System.currentTimeMillis() + ".csv");
        try (FileWriter writer = new FileWriter(csvFile, false)) {
            writer.write("elapsed_ms,frame,track_id,label,confidence,left,top,right,bottom,center_x,center_y,aim_x,aim_y,velocity_x_px_s,velocity_y_px_s,frame_width,frame_height\n");
        }
        Log.i(TAG, "Detector ready; CSV=" + csvFile.getAbsolutePath());
    }

    public void analyze(Bitmap bitmap) {
        if (bitmap == null) return;
        long now = SystemClock.elapsedRealtime();
        frameCount++;
        MPImage input = null;
        try {
            input = new BitmapImageBuilder(bitmap).build();
            ObjectDetectorResult result = detector.detect(input);
            List<Detection> detections = result.detections();
            Set<Integer> used = new HashSet<>();
            List<Target> visible = new ArrayList<>();

            for (Detection detection : detections) {
                List<Category> categories = detection.categories();
                if (categories == null || categories.isEmpty()) continue;
                Category category = categories.get(0);
                String label = category.categoryName();
                if (label == null || !label.equalsIgnoreCase("person")) continue;

                RectF box = detection.boundingBox();
                int bestIndex = -1;
                float bestScore = 0.12f;
                for (int i = 0; i < tracks.size(); i++) {
                    Track candidate = tracks.get(i);
                    if (used.contains(candidate.id)) continue;
                    float score = associationScore(candidate.box, box);
                    if (score > bestScore) {
                        bestScore = score;
                        bestIndex = i;
                    }
                }

                Track track;
                if (bestIndex >= 0) {
                    track = tracks.get(bestIndex);
                    float dt = Math.max(0.001f, (now - track.timestamp) / 1000f);
                    float oldCx = track.box.centerX();
                    float oldCy = track.box.centerY();
                    float newCx = box.centerX();
                    float newCy = box.centerY();
                    track.vx = (newCx - oldCx) / dt;
                    track.vy = (newCy - oldCy) / dt;
                    track.box.set(box);
                    track.timestamp = now;
                    track.confidence = category.score();
                } else {
                    track = new Track(nextTrackId++, box, now, category.score());
                    tracks.add(track);
                }
                used.add(track.id);

                float aimX = box.centerX();
                float aimY = box.top + box.height() * 0.16f;
                Target target = new Target(track.id, box, aimX, aimY, category.score(), track.vx, track.vy);
                visible.add(target);
                appendRow(now - startedAt, bitmap, target);
            }

            tracks.removeIf(t -> now - t.timestamp > TRACK_TTL_MS);
            if (listener != null) listener.onTargets(visible, bitmap.getWidth(), bitmap.getHeight());

            if (frameCount % 30 == 0) {
                float fps = frameCount * 1000f / Math.max(1, now - startedAt);
                Log.i(TAG, String.format(Locale.US,
                    "ANALYSIS frames=%d fps=%.1f persons=%d tracks=%d",
                    frameCount, fps, visible.size(), tracks.size()));
            }
        } catch (Exception e) {
            Log.e(TAG, "Frame inference failed", e);
        } finally {
            if (input != null) input.close();
        }
    }

    private float associationScore(RectF a, RectF b) {
        float intersection = Math.max(0f, Math.min(a.right, b.right) - Math.max(a.left, b.left))
            * Math.max(0f, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        float iou = union > 0f ? intersection / union : 0f;
        float distance = (float)Math.hypot(a.centerX() - b.centerX(), a.centerY() - b.centerY());
        float scale = Math.max(1f, (float)Math.hypot(a.width(), a.height()));
        float proximity = Math.max(0f, 1f - distance / (scale * 2.2f));
        return Math.max(iou, proximity * 0.45f);
    }

    private void appendRow(long elapsed, Bitmap bitmap, Target t) {
        try (FileWriter writer = new FileWriter(csvFile, true)) {
            writer.write(String.format(Locale.US,
                "%d,%d,%d,person,%.4f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.1f,%.2f,%.2f,%d,%d\n",
                elapsed, frameCount, t.id, t.confidence, t.left, t.top, t.right, t.bottom,
                (t.left + t.right) / 2f, (t.top + t.bottom) / 2f, t.aimX, t.aimY,
                t.velocityX, t.velocityY, bitmap.getWidth(), bitmap.getHeight()));
        } catch (IOException e) {
            Log.e(TAG, "Cannot write tracking report", e);
        }
    }

    public String getReportPath() {
        return csvFile.getAbsolutePath();
    }

    @Override public void close() {
        detector.close();
    }
}
