package com.verba.aimlab;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 4101;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 15));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 15));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 38, 28, 26);
        root.setBackgroundColor(Color.rgb(9, 11, 15));

        TextView eyebrow = text("FIELD TEST / BUILD 0.3", 11, Color.rgb(220, 70, 82));
        eyebrow.setLetterSpacing(0.16f);
        root.addView(eyebrow);

        TextView title = text("VERBA AIM LAB", 28, Color.rgb(242, 245, 249));
        title.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD));
        title.setPadding(0, 7, 0, 2);
        root.addView(title);

        TextView subtitle = text("ВИЗУАЛЬНАЯ АНАЛИТИКА ТЕСТОВОГО СЕРВЕРА", 11, Color.rgb(139, 151, 166));
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        status = text("●  СИСТЕМА ОЖИДАЕТ ЗАПУСКА", 13, Color.rgb(228, 171, 76));
        status.setPadding(0, 15, 0, 15);
        root.addView(status);

        TextView panel = text("МОДУЛИ АНАЛИЗА", 11, Color.rgb(139, 151, 166));
        panel.setPadding(0, 8, 0, 10);
        root.addView(panel);
        addInfo(root, "01  DETECTION", "Обнаружение объектов класса person");
        addInfo(root, "02  TRACKING", "Сопоставление целей между кадрами");
        addInfo(root, "03  AIM MARKER", "Визуальная точка ориентирования");
        addInfo(root, "04  TELEMETRY", "CSV-журнал координат и движения");

        Button overlay = button("РАЗРЕШИТЬ ЭКРАННЫЙ ОВЕРЛЕЙ");
        overlay.setOnClickListener(v -> requestOverlayPermission());
        root.addView(overlay);

        Button capture = button("НАЧАТЬ АНАЛИЗ ЭКРАНА");
        capture.setOnClickListener(v -> requestCapture());
        root.addView(capture);

        Button stop = button("ОСТАНОВИТЬ АНАЛИЗ");
        stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(47, 54, 64)));
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, CaptureService.class));
            status.setText("●  АНАЛИЗ ОСТАНОВЛЕН");
            status.setTextColor(Color.rgb(228, 171, 76));
        });
        root.addView(stop);

        TextView footer = text("Режим наблюдения: приложение отображает найденные объекты и измеряет движение. Игровые касания и скрытое управление не выполняются.", 12, Color.rgb(126, 137, 151));
        footer.setPadding(0, 24, 0, 0);
        root.addView(footer);
        setContentView(root);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void addInfo(LinearLayout root, String title, String detail) {
        TextView item = text(title + "\n" + detail, 13, Color.rgb(218, 224, 232));
        item.setPadding(14, 12, 12, 12);
        item.setBackgroundColor(Color.rgb(19, 23, 30));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = 7;
        root.addView(item, lp);
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(151, 38, 52)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = 9;
        b.setLayoutParams(lp);
        return b;
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            status.setText("●  ОВЕРЛЕЙ РАЗРЕШЁН");
            status.setTextColor(Color.rgb(93, 204, 151));
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void requestCapture() {
        if (!Settings.canDrawOverlays(this)) {
            status.setText("СНАЧАЛА РАЗРЕШИТЕ ЭКРАННЫЙ ОВЕРЛЕЙ");
            status.setTextColor(Color.rgb(228, 171, 76));
            requestOverlayPermission();
            return;
        }
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
            status.setText("●  ЗАХВАТ И АНАЛИЗ АКТИВНЫ");
            status.setTextColor(Color.rgb(93, 204, 151));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (status != null && Settings.canDrawOverlays(this)) {
            status.setText("●  ОВЕРЛЕЙ ГОТОВ");
            status.setTextColor(Color.rgb(93, 204, 151));
        }
    }
}
