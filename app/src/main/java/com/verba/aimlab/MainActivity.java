package com.verba.aimlab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.view.Gravity;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4101;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12, 14, 18));
        getWindow().setNavigationBarColor(Color.rgb(12, 14, 18));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 36, 28, 24);
        root.setBackgroundColor(Color.rgb(12, 14, 18));

        TextView title = new TextView(this);
        title.setText("VERBA AIM  /  LAB");
        title.setTextColor(Color.rgb(235, 238, 242));
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Экспериментальная среда анализа игрового видео");
        subtitle.setTextColor(Color.rgb(155, 164, 176));
        subtitle.setTextSize(14);
        subtitle.setPadding(0, 10, 0, 28);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("СТАТУС: ОЖИДАНИЕ ЗАПИСИ");
        status.setTextColor(Color.rgb(225, 65, 75));
        status.setTextSize(14);
        status.setPadding(0, 12, 0, 24);
        root.addView(status);

        Button capture = button(this, "НАЧАТЬ ЗАХВАТ ЭКРАНА");
        capture.setOnClickListener(v -> requestCapture());
        root.addView(capture);

        Button stop = button(this, "ОСТАНОВИТЬ АНАЛИЗ");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            status.setText("СТАТУС: ОСТАНОВЛЕНО");
        });
        root.addView(stop);

        TextView note = new TextView(this);
        note.setText("\nТекущая сборка: базовый экран захвата. Следующий этап — анализ видеокадров, измерение движения прицела и экспорт отчёта. Управление игровыми касаниями и скрытие от античита не реализуются.");
        note.setTextColor(Color.rgb(155, 164, 176));
        note.setTextSize(13);
        note.setPadding(0, 24, 0, 0);
        root.addView(note);
        setContentView(root);
    }

    private Button button(Context c, String text) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(150, 35, 48)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = 12;
        b.setLayoutParams(p);
        return b;
    }

    private void requestCapture() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAPTURE && resultCode == RESULT_OK && data != null) {
            Intent service = new Intent(this, CaptureService.class);
            service.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode);
            service.putExtra(CaptureService.EXTRA_DATA, data);
            startForegroundService(service);
            status.setText("СТАТУС: ЗАХВАТ АКТИВЕН");
        }
    }
}
