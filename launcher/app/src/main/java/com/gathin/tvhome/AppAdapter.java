package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * the rest is alphabetical.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    private static final String PREFS_NAME = "tvhome_prefs";
    private static final String KEY_DOCK_APPS = "custom_dock_apps";
    private static final String KEY_GRID_ORDER = "custom_grid_order";

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Called for D-pad Left/Right on a tile. Return true when handled. */
        boolean onTileHorizontalKey(int position, int keyCode);

        /** Long-press tooltip for dock and grid tiles. */
        void onTileLongClick(int position, View card, Entry entry);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
        }
    }

    static final float FOCUS_SCALE = 1.22f;
    /** Extra glass halo around a focused tile, in dp. */ 
    static final float GLASS_EXTRA_DP = 18f;

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (custom from SharedPreferences or res/values/config.xml dock_apps). */
    private final List<String> favourites = new ArrayList<>();
    /** Custom order of apps below the dock. Empty means alphabetical. */
    private final List<String> gridOrder = new ArrayList<>();
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int dockSize) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        loadFavourites();
        loadCsv(KEY_GRID_ORDER, gridOrder);
        ensureLiveTvOnDock();
    }

    private void loadFavourites() {
        favourites.clear();
        SharedPreferences sp = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String saved = sp.getString(KEY_DOCK_APPS, null);
        if (saved != null && !saved.trim().isEmpty()) {
            loadCsv(KEY_DOCK_APPS, favourites);
        } else {
            favourites.addAll(Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        }
    }

    private void ensureLiveTvOnDock() {
        String liveTv = ctx.getString(R.string.live_tv_package).trim();
        if (liveTv.isEmpty()) return;
        int idx = favourites.indexOf(liveTv);
        if (idx >= 0 && idx < dockSize) return;
        if (idx >= dockSize) {
            favourites.remove(idx);
        }
        if (favourites.size() < dockSize) {
            favourites.add(liveTv);
        } else {
            favourites.add(dockSize - 1, liveTv);
        }
        saveFavourites();
    }

    private void loadCsv(String key, List<String> dest) {
        dest.clear();
        String saved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null);
        if (saved == null || saved.trim().isEmpty()) return;
        for (String p : saved.split(",")) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty() && !dest.contains(trimmed)) {
                dest.add(trimmed);
            }
        }
    }

    private void saveCsv(String key, List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(values.get(i));
        }
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, sb.toString())
                .apply();
    }

    public void saveFavourites() {
        saveCsv(KEY_DOCK_APPS, favourites);
    }

    private void persistGridOrderFromItems() {
        gridOrder.clear();
        int dockCount = getDockCount();
        for (int i = dockCount; i < items.size(); i++) {
            gridOrder.add(items.get(i).pkg);
        }
        saveCsv(KEY_GRID_ORDER, gridOrder);
    }

    public boolean moveDockLeft(int position) {
        int dockCount = getDockCount();
        if (position > 0 && position < dockCount && position < items.size()) {
            String pkgCurrent = items.get(position).pkg;
            String pkgPrev = items.get(position - 1).pkg;
            int idxCurrent = favourites.indexOf(pkgCurrent);
            int idxPrev = favourites.indexOf(pkgPrev);
            if (idxCurrent >= 0 && idxPrev >= 0) {
                Collections.swap(favourites, idxCurrent, idxPrev);
                saveFavourites();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean moveDockRight(int position) {
        int dockCount = getDockCount();
        if (position >= 0 && position < dockCount - 1 && position + 1 < items.size()) {
            String pkgCurrent = items.get(position).pkg;
            String pkgNext = items.get(position + 1).pkg;
            int idxCurrent = favourites.indexOf(pkgCurrent);
            int idxNext = favourites.indexOf(pkgNext);
            if (idxCurrent >= 0 && idxNext >= 0) {
                Collections.swap(favourites, idxCurrent, idxNext);
                saveFavourites();
                reload();
                return true;
            }
        }
        return false;
    }

    public boolean removeFromDock(int position) {
        int dockCount = getDockCount();
        if (position >= 0 && position < dockCount && position < items.size()) {
            String pkg = items.get(position).pkg;
            favourites.remove(pkg);
            saveFavourites();
            if (!gridOrder.contains(pkg)) {
                gridOrder.add(0, pkg);
                saveCsv(KEY_GRID_ORDER, gridOrder);
            }
            reload();
            return true;
        }
        return false;
    }

    /** Swap this tile with its left neighbour. Dock stays in the dock; grid stays in the grid. */
    public boolean moveLeft(int position) {
        int dockCount = getDockCount();
        if (position > 0 && position < dockCount) {
            return moveDockLeft(position);
        }
        if (position > dockCount && position < items.size()) {
            Collections.swap(items, position, position - 1);
            persistGridOrderFromItems();
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    public boolean moveRight(int position) {
        int dockCount = getDockCount();
        if (position >= 0 && position < dockCount - 1) {
            return moveDockRight(position);
        }
        if (position >= dockCount && position < items.size() - 1) {
            Collections.swap(items, position, position + 1);
            persistGridOrderFromItems();
            notifyDataSetChanged();
            return true;
        }
        return false;
    }

    public boolean canMoveLeft(int position) {
        int dockCount = getDockCount();
        if (position > 0 && position < dockCount) return true;
        return position > dockCount && position < items.size();
    }

    public boolean canMoveRight(int position) {
        int dockCount = getDockCount();
        if (position >= 0 && position < dockCount - 1) return true;
        return position >= dockCount && position < items.size() - 1;
    }

    public boolean isDockPosition(int position) {
        return position >= 0 && position < getDockCount();
    }

    public int getDockCount() {
        int count = 0;
        for (Entry e : items) {
            if (favourites.contains(e.pkg)) {
                count++;
            } else {
                break;
            }
        }
        return Math.min(dockSize, count);
    }

    public Entry getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
        }
        return null;
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        List<Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        collectActivities(pm, fresh, seen,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
        collectActivities(pm, fresh, seen,
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
        for (String pkg : ctx.getResources().getStringArray(R.array.extra_apps)) {
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || seen.contains(pkg)) continue;
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null || launch.getComponent() == null) continue;
            try {
                CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
                Drawable icon = pm.getApplicationIcon(pkg);
                Drawable banner = pm.getApplicationInfo(pkg, 0).loadBanner(pm);
                fresh.add(new Entry(String.valueOf(label), launch.getComponent(), banner, icon));
                seen.add(pkg);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            int ga = gridOrder.indexOf(a.pkg), gb = gridOrder.indexOf(b.pkg);
            if (ga >= 0 || gb >= 0) {
                if (ga < 0) return 1;
                if (gb < 0) return -1;
                return Integer.compare(ga, gb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        android.util.Log.i("TVHOME", "reload items: fresh=" + fresh.size() + " favs=" + favourites);
        items.clear();
        items.addAll(fresh);
        for (int i = 0; i < items.size(); i++) {
            android.util.Log.i("TVHOME_ITEMS", "pos " + i + ": " + items.get(i).label + " (" + items.get(i).pkg + ")");
        }
        notifyDataSetChanged();
    }

    private void collectActivities(PackageManager pm, List<Entry> fresh, Set<String> seen, Intent probe) {
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg) || seen.contains(pkg)) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon));
            seen.add(pkg);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener, this);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.name.setText(e.label);
        h.name.setVisibility(View.GONE);
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < items.size()) {
                launch(items.get(pos));
            }
        });
        h.card.setOnLongClickListener(v -> {
            int pos = h.getAdapterPosition();
            if (pos == RecyclerView.NO_POSITION || pos >= items.size()) return false;
            listener.onTileLongClick(pos, h.card, items.get(pos));
            return true;
        });
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Intent fallback = ctx.getPackageManager().getLeanbackLaunchIntentForPackage(e.pkg);
            if (fallback == null) fallback = ctx.getPackageManager().getLaunchIntentForPackage(e.pkg);
            try {
                if (fallback != null) {
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(fallback);
                } else {
                    Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception ignored) {
                Toast.makeText(ctx, "Can't open " + e.label, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.70f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        // Deep vibrant saturated color for Apple TV 4K glass reflection
        hsv[1] = Math.min(1.0f, hsv[1] * 1.45f);
        hsv[2] = Math.max(0.70f, Math.min(1.0f, hsv[2] * 1.25f));
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener, AppAdapter adapter) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            final float radius = density * 16f;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnFocusChangeListener((view, hasFocus) -> {
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                if (hasFocus) {
                    card.animate().scaleX(FOCUS_SCALE).scaleY(FOCUS_SCALE)
                            .setDuration(220)
                            .setInterpolator(new OvershootInterpolator(1.15f))
                            .start();
                    listener.onTileFocused(pos, card);
                } else {
                    card.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                }
            });

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return listener.onTileVerticalKey(pos, keyCode);
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return listener.onTileHorizontalKey(pos, keyCode);
                }
                return false;
            });
        }
    }
}
