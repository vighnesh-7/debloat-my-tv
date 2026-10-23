package com.gathin.tvhome;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private static final long STATS_MS = 5_000L;
    private static final long TOAST_MS = 1_000L;
    private static final long SCREENSAVER_IDLE_MS = 5 * 60_000L;
    private static final Set<String> PROTECTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.gathin.tvhome",
            "com.android.vending",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.inputmethod.latin",
            "com.android.bluetooth",
            "com.google.android.tv.remote.service",
            "com.android.tv",
            "com.droidlogic.tvinput",
            "com.droidlogic.droidlivetv",
            "com.android.tv.settings",
            "com.android.systemui",
            "android"
    ));
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private TextView btnWifi;
    private TextView statCpu;
    private TextView statStorage;
    private LinearLayout usbStack;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private String lastConnectedSsid = null;
    private boolean wifiReady = false;
    private Runnable revertWifiRunnable = null;
    private BroadcastReceiver networkReceiver = null;
    private BroadcastReceiver bluetoothReceiver = null;
    private BroadcastReceiver usbReceiver = null;
    private InputManager inputManager = null;
    private final InputManager.InputDeviceListener inputDeviceListener = new InputManager.InputDeviceListener() {
        @Override
        public void onInputDeviceAdded(int deviceId) {
            updateUsbStatus();
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            updateUsbStatus();
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            updateUsbStatus();
        }
    };
    private TextView eventToast;
    private Runnable hideToastRunnable = null;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };
    private final Runnable statsTick = new Runnable() {
        @Override
        public void run() {
            updateSystemStats();
            handler.postDelayed(this, STATS_MS);
        }
    };
    private final Runnable idleScreensaver = () -> {
        if (isFinishing()) return;
        try {
            Intent intent = new Intent(this, ScreensaverActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        } catch (Exception ignored) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        glass.setClickable(false);
        glass.setFocusable(false);
        glass.bringToFront();
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.35f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.70f));
        greeting = findViewById(R.id.greeting);
        btnWifi = findViewById(R.id.btnWifi);
        statCpu = findViewById(R.id.statCpu);
        statStorage = findViewById(R.id.statStorage);
        usbStack = findViewById(R.id.usbStack);
        eventToast = findViewById(R.id.eventToast);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density));
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, R.anim.tile_enter), 0.06f));
        adapter = new AppAdapter(this, this, COLUMNS);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row to the bottom of the screen; the grid hides below until you press Down.
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            int dockRow = (int) ((101 + 24 + 6 + 14) * density);   // card + item padding + DockDecoration offsets
            int want = Math.max(0, h - dockRow - (int) (30 * density));
            if (grid.getPaddingTop() != want) {
                grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
            }
        });

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                if (springY.isRunning()) springY.animateToFinalPosition(glassTargetY);
                else glass.setTranslationY(glassTargetY);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_Y);
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
        }

        View btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnInputs) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        ambient.setAlpha(0f);
        adapter.reload();
        grid.scheduleLayoutAnimation();
        updateGreeting();
        updateWifiLabel();
        updateSystemStats();
        handler.postDelayed(minuteTick, MINUTE_MS);
        handler.postDelayed(statsTick, STATS_MS);
        registerEventReceivers();
        scheduleScreensaver();

        // Return to the tile we left from, or the first one.
        grid.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && grid.findContainingViewHolder(focused) != null) return;
            focusTile(lastFocused);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(statsTick);
        handler.removeCallbacks(idleScreensaver);
        unregisterEventReceivers();
        if (revertWifiRunnable != null) {
            handler.removeCallbacks(revertWifiRunnable);
            revertWifiRunnable = null;
        }
        if (hideToastRunnable != null) {
            handler.removeCallbacks(hideToastRunnable);
            hideToastRunnable = null;
        }
    }

    private void registerEventReceivers() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            networkReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateWifiLabel();
                }
            };
            registerReceiver(networkReceiver, filter);
        } catch (Exception ignored) {
        }
        try {
            IntentFilter bt = new IntentFilter();
            bt.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            bt.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            bluetoothReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                        showEventToast("Remote disconnected");
                    } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                        if (state == BluetoothAdapter.STATE_OFF) {
                            showEventToast("Remote disconnected");
                        }
                    }
                }
            };
            registerReceiver(bluetoothReceiver, bt);
        } catch (Exception ignored) {
        }
        try {
            IntentFilter usb = new IntentFilter();
            usb.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            usb.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            usb.addAction("android.hardware.usb.action.USB_STATE");
            usbReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUsbStatus();
                }
            };
            registerReceiver(usbReceiver, usb);
            IntentFilter media = new IntentFilter();
            media.addAction(Intent.ACTION_MEDIA_MOUNTED);
            media.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            media.addAction(Intent.ACTION_MEDIA_REMOVED);
            media.addAction(Intent.ACTION_MEDIA_EJECT);
            media.addDataScheme("file");
            registerReceiver(usbReceiver, media);
        } catch (Exception ignored) {
        }
        try {
            inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
            if (inputManager != null) {
                inputManager.registerInputDeviceListener(inputDeviceListener, handler);
            }
        } catch (Exception ignored) {
        }
    }

    private void unregisterEventReceivers() {
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
            } catch (Exception ignored) {
            }
            networkReceiver = null;
        }
        if (bluetoothReceiver != null) {
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (Exception ignored) {
            }
            bluetoothReceiver = null;
        }
        if (usbReceiver != null) {
            try {
                unregisterReceiver(usbReceiver);
            } catch (Exception ignored) {
            }
            usbReceiver = null;
        }
        if (inputManager != null) {
            try {
                inputManager.unregisterInputDeviceListener(inputDeviceListener);
            } catch (Exception ignored) {
            }
            inputManager = null;
        }
    }

    private void showEventToast(String message) {
        if (eventToast == null || message == null || message.isEmpty()) return;
        eventToast.setText(message);
        eventToast.setVisibility(View.VISIBLE);
        eventToast.animate().cancel();
        eventToast.animate().alpha(1f).setDuration(120).start();
        if (hideToastRunnable != null) {
            handler.removeCallbacks(hideToastRunnable);
        }
        hideToastRunnable = () -> {
            eventToast.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                eventToast.setVisibility(View.GONE);
                hideToastRunnable = null;
            }).start();
        };
        handler.postDelayed(hideToastRunnable, TOAST_MS);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        scheduleScreensaver();
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                triggerDockMenuForCurrentTile();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) {
                    event.startTracking();
                } else if (event.isLongPress() || event.getRepeatCount() >= 2) {
                    if (triggerDockMenuForCurrentTile()) {
                        return true;
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scheduleScreensaver();
        return super.dispatchTouchEvent(ev);
    }

    private void scheduleScreensaver() {
        handler.removeCallbacks(idleScreensaver);
        handler.postDelayed(idleScreensaver, SCREENSAVER_IDLE_MS);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) {
            if (triggerDockMenuForCurrentTile()) {
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    public boolean triggerDockMenuForCurrentTile() {
        View focused = getCurrentFocus();
        int pos = -1;
        View card = null;
        if (focused != null) {
            if (focused.getId() == R.id.card) {
                card = focused;
            } else {
                card = focused.findViewById(R.id.card);
            }
            if (card != null && card.getParent() instanceof View) {
                pos = grid.getChildAdapterPosition((View) card.getParent());
            }
        }
        if (pos < 0) {
            pos = lastFocused;
            RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
            if (vh != null) card = vh.itemView.findViewById(R.id.card);
        }
        if (pos >= 0 && card != null) {
            AppAdapter.Entry entry = adapter.getItem(pos);
            if (entry != null) {
                showTileMenu(pos, card, entry);
                return true;
            }
        }
        return false;
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = AppAdapter.GLASS_EXTRA_DP * density;
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            glass.setRadius(18f * density * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y);
        }
        View item = (View) card.getParent();
        if (position < adapter.getDockCount()) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.smoothScrollBy(0, dy);
            showAmbient(position);
        } else {
            showAmbient(position);
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, false);
        }
    }

    @Override
    public void onTileLongClick(int position, View card, AppAdapter.Entry entry) {
        showTileMenu(position, card, entry);
    }

    private void showTileMenu(int position, View anchor, AppAdapter.Entry entry) {
        View panel = getLayoutInflater().inflate(R.layout.popup_dock_menu, root, false);
        TextView menuTitle = panel.findViewById(R.id.menuTitle);
        if (menuTitle != null) {
            menuTitle.setText(entry.label);
        }
        LinearLayout rows = panel.findViewById(R.id.rows);

        int width = (int) (240 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(16f * density);

        boolean dock = adapter.isDockPosition(position);

        if (adapter.canMoveLeft(position)) {
            addDockMenuRow(rows, popup, R.drawable.ic_move_left, "Move Left", () -> {
                adapter.moveLeft(position);
                grid.post(() -> focusTile(position - 1));
            });
        }

        if (adapter.canMoveRight(position)) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addDockMenuRow(rows, popup, R.drawable.ic_move_right, "Move Right", () -> {
                adapter.moveRight(position);
                grid.post(() -> focusTile(position + 1));
            });
        }

        if (dock) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addDockMenuRow(rows, popup, R.drawable.ic_remove, "Remove from Dock", () -> {
                adapter.removeFromDock(position);
                grid.post(() -> focusTile(Math.max(0, position)));
                showEventToast(entry.label + " moved to apps below");
            });
        }

        if (!PROTECTED_PACKAGES.contains(entry.pkg)) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addDockMenuRow(rows, popup, R.drawable.ic_uninstall, "Uninstall", () -> uninstallApp(entry));
        }

        if (rows.getChildCount() == 0) {
            return;
        }

        // Measure panel to position above anchor accurately
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = panel.getMeasuredHeight();

        int[] loc = new int[2];
        anchor.getLocationInWindow(loc);
        int anchorX = loc[0];
        int anchorY = loc[1];
        int x = anchorX + (anchor.getWidth() - width) / 2;
        int y = anchorY - popupHeight - (int) (12 * density);
        if (y < (int) (16 * density)) {
            y = anchorY + anchor.getHeight() + (int) (12 * density);
        }

        panel.setAlpha(0f);
        panel.setScaleY(0.92f);
        panel.setPivotY(1f);

        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        panel.animate().alpha(1f).scaleY(1f).setDuration(200)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();

        if (rows.getChildCount() > 0) {
            rows.post(() -> rows.getChildAt(0).requestFocus());
        }
    }

    private void uninstallApp(AppAdapter.Entry entry) {
        Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + entry.pkg));
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, false);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Intent fallback = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.pkg));
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException ex) {
                showEventToast("Can't uninstall " + entry.label);
            }
        }
    }

    private void addDockMenuRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- top shelf -------------------------------------------------------------------------------

    /** Colour bloom behind the home screen; the focused app's name is not shown. */
    private void showAmbient(int position) {
        int color = adapter.ambientColorAt(position);
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(500).start();
            heroShown = true;
        } else if (color != ambientColor) {
            ambient.animate().alpha(0.40f).setDuration(180).withEndAction(() -> {
                setAmbient(color);
                ambient.animate().alpha(1f).setDuration(450).start();
            }).start();
        }
    }

    /** Deep, vivid ambient glow matching tvOS saturated backdrop reflection. */
    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        ambientGlow.setColors(new int[]{
                0xD9000000 | c,  // 85% opacity vivid core!
                0x80000000 | c,  // 50% mid
                0x28000000 | c,  // 16% outer
                0x00000000 | c   // transparent edge
        });
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(120).start();
        ambient.animate().alpha(0f).setDuration(200).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(140).start();
        hideHero();
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < adapter.getDockCount()) return false;          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            target = position + COLUMNS;
            if (target >= count) return true;   // no tile below this column: stay
        }
        focusTile(target);
        return true;
    }

    @Override
    public boolean onTileHorizontalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        if (count <= 0) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (position >= count - 1) return true;
            focusTile(position + 1);
            return true;
        }
        if (position <= 0) return true;
        focusTile(position - 1);
        return true;
    }

    private void focusTile(int position) {
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(position);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
        } else {
            grid.smoothScrollToPosition(position);
            grid.post(() -> {
                RecyclerView.ViewHolder v = grid.findViewHolderForAdapterPosition(position);
                if (v != null) v.itemView.findViewById(R.id.card).requestFocus();
            });
        }
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
    }

    private void updateWifiLabel() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network net = cm != null ? cm.getActiveNetwork() : null;
            NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
            if (caps == null) {
                if (wifiReady && lastConnectedSsid != null) {
                    showEventToast("Wi-Fi disconnected");
                }
                lastConnectedSsid = null;
                wifiReady = true;
                setWifiIconConnected(false);
                btnWifi.setText(R.string.offline);
                return;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                lastConnectedSsid = "Ethernet";
                setWifiIconConnected(false);
                btnWifi.setText(R.string.ethernet);
                return;
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                }
                if (ssid == null || ssid.isEmpty() || ssid.contains("unknown") || ssid.equals("0x")) {
                    ssid = getString(R.string.wifi);
                }

                setWifiIconConnected(true);
                if (!wifiReady) {
                    lastConnectedSsid = ssid;
                    wifiReady = true;
                    btnWifi.setText(ssid);
                    return;
                }
                if (lastConnectedSsid == null || !lastConnectedSsid.equals(ssid)) {
                    lastConnectedSsid = ssid;
                    String notifyText = ssid + " Wi-Fi is connected";
                    btnWifi.setText(notifyText);
                    showEventToast(notifyText);

                    if (revertWifiRunnable != null) {
                        handler.removeCallbacks(revertWifiRunnable);
                    }
                    final String finalSsid = ssid;
                    revertWifiRunnable = () -> {
                        btnWifi.setText(finalSsid);
                        revertWifiRunnable = null;
                    };
                    handler.postDelayed(revertWifiRunnable, 6000L);
                } else if (revertWifiRunnable == null) {
                    btnWifi.setText(ssid);
                }
                return;
            }
            setWifiIconConnected(false);
            btnWifi.setText(R.string.wifi);
        } catch (Exception ignored) {
            setWifiIconConnected(false);
            btnWifi.setText(R.string.wifi);
        }
    }

    /** Bright green Wi-Fi glyph when associated; white (pill tint) otherwise. No pill bloom. */
    private void setWifiIconConnected(boolean connected) {
        if (btnWifi == null) return;
        if (connected) {
            btnWifi.setCompoundDrawableTintList(null);
            btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_wifi_connected, 0, 0, 0);
        } else {
            btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_wifi, 0, 0, 0);
            btnWifi.setCompoundDrawableTintList(
                    getResources().getColorStateList(R.drawable.pill_text, getTheme()));
        }
    }

    /** Updates free internal storage and USB presence. */
    private void updateSystemStats() {
        if (statStorage == null) {
            updateUsbStatus();
            return;
        }
        try {
            File dataDir = Environment.getDataDirectory();
            StatFs stat = new StatFs(dataDir.getPath());
            statStorage.setText("Memory " + formatStorage(stat.getAvailableBytes()) + " Free");
            updateUsbStatus();
        } catch (Exception ignored) {
            updateUsbStatus();
        }
    }

    private void updateUsbStatus() {
        UsbStatus.bind(usbStack);
    }

    private String formatStorage(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            double gb = (double) bytes / (1024L * 1024L * 1024L);
            return String.format(Locale.US, "%.1f GB", gb);
        } else {
            long mb = bytes / (1024L * 1024L);
            return mb + " MB";
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown: HDMI 1–3, bar, Live TV, bar, AV at the bottom. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (240 * density));
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(16f * density);

        List<TvInputInfo> hdmiInputs = new ArrayList<>();
        List<TvInputInfo> avInputs = new ArrayList<>();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput()) continue;
                if (info.getType() == TvInputInfo.TYPE_HDMI) {
                    java.util.regex.Matcher named = java.util.regex.Pattern
                            .compile("Hdmi(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                            .matcher(info.getId());
                    if (named.find()) hdmiInputs.add(info);
                } else {
                    avInputs.add(info);
                }
            }
            Collections.sort(hdmiInputs, (a, b) -> Integer.compare(hdmiPort(a), hdmiPort(b)));
            for (TvInputInfo info : hdmiInputs) {
                int port = hdmiPort(info);
                int state = tim.getInputState(info.getId());
                addInputRow(rows, popup, R.drawable.ic_input, "HDMI " + port, state,
                        () -> openInput(info));
            }
        }

        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv == null && !liveTvPkg.isEmpty()) {
            liveTv = getPackageManager().getLaunchIntentForPackage(liveTvPkg);
        }
        if (liveTv == null && "com.android.tv".equals(liveTvPkg)) {
            liveTv = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.android.tv", "com.android.tv.TvActivity"));
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent finalLiveTv = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(finalLiveTv));
        }
        if (tim != null && !avInputs.isEmpty()) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            for (TvInputInfo info : avInputs) {
                CharSequence l = info.loadLabel(this);
                String label = l == null ? "" : l.toString().trim();
                if (label.isEmpty()) label = String.valueOf(info.getId());
                int state = tim.getInputState(info.getId());
                addInputRow(rows, popup, R.drawable.ic_av, label, state, () -> openInput(info));
            }
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void openInput(TvInputInfo info) {
        String id = info.getId();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setData(TvContract.buildChannelUriForPassthroughInput(id));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(fallback);
            } catch (Exception ex) {
                Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static int hdmiPort(TvInputInfo info) {
        java.util.regex.Matcher named = java.util.regex.Pattern
                .compile("Hdmi(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(info.getId());
        return named.find() ? Integer.parseInt(named.group(1)) : 99;
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
