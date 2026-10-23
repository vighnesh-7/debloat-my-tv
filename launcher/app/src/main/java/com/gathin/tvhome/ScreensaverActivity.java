package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

public class ScreensaverActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView greeting;
    private TextView statStorage;
    private LinearLayout usbStack;
    private ImageView wallpaper;
    private View ambientView;
    private GradientDrawable ambientDrawable;
    private Runnable rotateWallpaper;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateSystemStats();
            handler.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_screensaver);

        greeting = findViewById(R.id.screensaverGreeting);
        statStorage = findViewById(R.id.screensaverStatStorage);
        usbStack = findViewById(R.id.screensaverUsbStack);
        ambientView = findViewById(R.id.screensaverAmbient);
        wallpaper = findViewById(R.id.screensaverWallpaper);

        ambientDrawable = new GradientDrawable();
        ambientDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientDrawable.setGradientCenter(0.40f, 0.45f);
        ambientDrawable.setColors(new int[]{0x661E293B, 0x330F172A, 0x00000000});
        if (ambientView != null) {
            ambientView.setBackground(ambientDrawable);
            ambientView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                    ambientDrawable.setGradientRadius((r - l) * 0.75f));
        }

        updateGreeting();
        updateSystemStats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        updateSystemStats();
        handler.postDelayed(tickRunnable, 60_000L);
        rotateWallpaper = WallpaperLoader.startRotation(wallpaper, handler);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tickRunnable);
        if (rotateWallpaper != null) {
            handler.removeCallbacks(rotateWallpaper);
            rotateWallpaper = null;
        }
        if (wallpaper != null) wallpaper.animate().cancel();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void updateGreeting() {
        if (greeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateSystemStats() {
        if (statStorage != null) {
            try {
                File dataDir = Environment.getDataDirectory();
                StatFs stat = new StatFs(dataDir.getPath());
                statStorage.setText("Memory " + formatStorage(stat.getAvailableBytes()) + " Free");
            } catch (Exception ignored) {
            }
        }
        UsbStatus.bind(usbStack);
    }

    private String formatStorage(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            double gb = (double) bytes / (1024L * 1024L * 1024L);
            return String.format(Locale.US, "%.1f GB", gb);
        }
        return (bytes / (1024L * 1024L)) + " MB";
    }
}
