package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.widget.ImageView;

/**
 * Bundled screensaver photos that rotate every five minutes.
 */
final class WallpaperLoader {

    static final long ROTATE_MS = 5 * 60_000L;

    private static final String PREFS = "tvhome_prefs";
    private static final String KEY_INDEX = "screensaver_photo_index";
    private static final String KEY_SHOWN_AT = "screensaver_photo_shown_at";

    private static final int[] PHOTOS = {
            R.drawable.screensaver_coast,
            R.drawable.screensaver_alps,
            R.drawable.screensaver_sunset,
            R.drawable.screensaver_lake
    };

    private WallpaperLoader() {
    }

    static Drawable load(Context ctx) {
        return drawableAt(ctx, currentIndex(ctx, false));
    }

    static void showCurrent(ImageView view) {
        if (view == null) return;
        Drawable photo = load(view.getContext());
        if (photo != null) view.setImageDrawable(photo);
    }

    static void showNext(final ImageView view) {
        if (view == null) return;
        final Drawable photo = drawableAt(view.getContext(), currentIndex(view.getContext(), true));
        if (photo == null) return;
        view.animate().cancel();
        view.animate().alpha(0f).setDuration(480).withEndAction(() -> {
            view.setImageDrawable(photo);
            view.animate().alpha(1f).setDuration(640).start();
        }).start();
    }

    static Runnable startRotation(final ImageView view, final Handler handler) {
        showCurrent(view);
        final Runnable rotate = new Runnable() {
            @Override
            public void run() {
                showNext(view);
                handler.postDelayed(this, ROTATE_MS);
            }
        };
        handler.postDelayed(rotate, msUntilNext(view.getContext()));
        return rotate;
    }

    private static Drawable drawableAt(Context ctx, int index) {
        try {
            return ctx.getDrawable(PHOTOS[Math.floorMod(index, PHOTOS.length)]);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int currentIndex(Context ctx, boolean advanceNow) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int index = sp.getInt(KEY_INDEX, 0);
        long shownAt = sp.getLong(KEY_SHOWN_AT, 0L);
        long now = System.currentTimeMillis();
        if (advanceNow) {
            index = Math.floorMod(index + 1, PHOTOS.length);
            sp.edit().putInt(KEY_INDEX, index).putLong(KEY_SHOWN_AT, now).apply();
            return index;
        }
        if (shownAt <= 0L) {
            sp.edit().putInt(KEY_INDEX, index).putLong(KEY_SHOWN_AT, now).apply();
            return Math.floorMod(index, PHOTOS.length);
        }
        int steps = (int) Math.max(0L, (now - shownAt) / ROTATE_MS);
        if (steps > 0) {
            index = Math.floorMod(index + steps, PHOTOS.length);
            sp.edit().putInt(KEY_INDEX, index)
                    .putLong(KEY_SHOWN_AT, shownAt + steps * ROTATE_MS)
                    .apply();
        }
        return Math.floorMod(index, PHOTOS.length);
    }

    private static long msUntilNext(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long shownAt = sp.getLong(KEY_SHOWN_AT, 0L);
        if (shownAt <= 0L) return ROTATE_MS;
        long elapsed = Math.max(0L, System.currentTimeMillis() - shownAt);
        long remaining = ROTATE_MS - (elapsed % ROTATE_MS);
        return remaining <= 0L ? ROTATE_MS : remaining;
    }
}
