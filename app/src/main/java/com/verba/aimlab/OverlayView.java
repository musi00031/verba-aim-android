package com.verba.aimlab;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Non-interactive visual layer: bounding boxes and estimated upper-body aim markers only. */
public final class OverlayView extends View {
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<FrameAnalyzer.Target> targets = new ArrayList<>();
    private int frameWidth = 1;
    private int frameHeight = 1;

    public OverlayView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3f);
        boxPaint.setColor(Color.rgb(230, 65, 82));
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(Color.rgb(255, 190, 70));
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setFakeBoldText(true);
        centerPaint.setColor(Color.argb(150, 235, 240, 248));
        centerPaint.setStrokeWidth(2f);
        centerPaint.setStyle(Paint.Style.STROKE);
    }

    public void updateTargets(List<FrameAnalyzer.Target> next, int width, int height) {
        List<FrameAnalyzer.Target> copy = new ArrayList<>(next);
        post(() -> {
            targets = copy;
            frameWidth = Math.max(1, width);
            frameHeight = Math.max(1, height);
            invalidate();
        });
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / (float)frameWidth;
        float sy = getHeight() / (float)frameHeight;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawCircle(cx, cy, 13f, centerPaint);
        canvas.drawLine(cx - 20, cy, cx + 20, cy, centerPaint);
        canvas.drawLine(cx, cy - 20, cx, cy + 20, centerPaint);

        for (FrameAnalyzer.Target target : targets) {
            float left = target.left * sx;
            float top = target.top * sy;
            float right = target.right * sx;
            float bottom = target.bottom * sy;
            RectF rect = new RectF(left, top, right, bottom);
            boxPaint.setColor(target.confidence >= 0.55f
                ? Color.rgb(80, 220, 160) : Color.rgb(230, 65, 82));
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint);

            float ax = target.aimX * sx;
            float ay = target.aimY * sy;
            canvas.drawCircle(ax, ay, 10f, markerPaint);
            canvas.drawCircle(ax, ay, 19f, centerPaint);
            canvas.drawText("ID " + target.id + "  " + Math.round(target.confidence * 100) + "%",
                left, Math.max(30f, top - 10f), textPaint);
        }
    }
}
