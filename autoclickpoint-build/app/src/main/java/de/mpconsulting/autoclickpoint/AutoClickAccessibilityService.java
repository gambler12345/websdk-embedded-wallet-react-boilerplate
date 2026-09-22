package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static final String PREFS = "autoclick_prefs";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_I = "interval";
    private static final String KEY_RANDOM_MODE = "random_mode";
    private static final String KEY_RANDOM_MIN = "random_min_ms";
    private static final String KEY_RANDOM_MAX = "random_max_ms";
    private static final String KEY_OVERLAY_VISIBLE = "overlay_visible";
    private static final String KEY_PANEL_X = "panel_x";
    private static final String KEY_PANEL_Y = "panel_y";
    private static final String KEY_GUARD_ENABLED = "guard_enabled";
    private static final String KEY_GUARD_REFERENCE = "guard_reference";

    private static final long[] INTERVALS = {0, 10, 25, 50, 100, 250, 500, 1000};
    private static final long PRESS_DURATION_MS = 8;
    private static final long MIN_CLICK_PERIOD_MS = 16;
    private static final long RANDOM_STEP_MS = 10;
    private static final long RANDOM_MIN_ALLOWED_MS = 20;
    private static final long RANDOM_MAX_ALLOWED_MS = 5000;

    // Android throttles AccessibilityService screenshots. Keep a margin above the
    // platform's roughly 333 ms minimum so every guarded click can use a fresh frame.
    private static final long GUARD_MIN_CHECK_MS = 380;
    private static final long SCREENSHOT_SETTLE_MS = 55;
    private static final int GUARD_REGION_DP = 96;
    private static final int DESCRIPTOR_SIDE = 16;
    private static final double MATCH_THRESHOLD = 0.90;
    private static final int SHOT_LEARN = 1;
    private static final int SHOT_VERIFY = 2;

    private static volatile AutoClickAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor mainExecutor = command -> handler.post(command);

    private WindowManager wm;
    private View target;
    private LinearLayout controls;
    private LinearLayout randomRow;
    private WindowManager.LayoutParams targetLp;
    private WindowManager.LayoutParams controlsLp;

    private Button startPause;
    private Button stopButton;
    private Button intervalMinus;
    private Button intervalPlus;
    private Button modeButton;
    private Button guardLearnButton;
    private Button guardToggleButton;
    private TextView intervalLabel;
    private TextView randomMinLabel;
    private TextView randomMaxLabel;
    private TextView guardStatus;
    private SharedPreferences prefs;

    private boolean running = false;
    private boolean paused = false;
    private boolean randomMode = false;
    private boolean imageGuardEnabled = false;
    private boolean guardWaiting = false;
    private boolean screenshotInFlight = false;
    private long runGeneration = 0;

    private int intervalIndex = 2;
    private long randomMinMs = 80;
    private long randomMaxMs = 140;
    private float xFraction = .5f;
    private float yFraction = .45f;
    private float panelXFraction = .5f;
    private float panelYFraction = .72f;
    private byte[] guardReference;

    public static void requestShowOverlays(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OVERLAY_VISIBLE, true).apply();
        AutoClickAccessibilityService service = instance;
        if (service != null) service.handler.post(service::ensureOverlaysVisible);
    }

    public static void requestHideOverlays(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OVERLAY_VISIBLE, false).apply();
        AutoClickAccessibilityService service = instance;
        if (service != null) service.handler.post(service::hideOverlays);
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        xFraction = prefs.getFloat(KEY_X, .5f);
        yFraction = prefs.getFloat(KEY_Y, .45f);
        panelXFraction = prefs.getFloat(KEY_PANEL_X, .5f);
        panelYFraction = prefs.getFloat(KEY_PANEL_Y, .72f);
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, prefs.getInt(KEY_I, 2)));
        randomMode = prefs.getBoolean(KEY_RANDOM_MODE, false);
        randomMinMs = clampLong(prefs.getLong(KEY_RANDOM_MIN, 80), RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        randomMaxMs = clampLong(prefs.getLong(KEY_RANDOM_MAX, 140), RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        if (randomMaxMs < randomMinMs) randomMaxMs = randomMinMs;
        guardReference = decodeReference(prefs.getString(KEY_GUARD_REFERENCE, null));
        imageGuardEnabled = prefs.getBoolean(KEY_GUARD_ENABLED, false) && guardReference != null;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (prefs.getBoolean(KEY_OVERLAY_VISIBLE, false)) ensureOverlaysVisible();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private int sw() { return getResources().getDisplayMetrics().widthPixels; }
    private int sh() { return getResources().getDisplayMetrics().heightPixels; }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private long clampLong(long v, long min, long max) { return Math.max(min, Math.min(max, v)); }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private void ensureOverlaysVisible() {
        if (wm == null) wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (prefs == null) prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (target == null) showTarget();
        if (controls == null) showControls();
    }

    private void showTarget() {
        if (target != null || wm == null) return;
        TextView marker = new TextView(this);
        marker.setText("+");
        marker.setTextColor(Color.WHITE);
        marker.setTextSize(27);
        marker.setGravity(Gravity.CENTER);
        GradientDrawable targetBg = bg(Color.rgb(215, 25, 32), 100);
        targetBg.setStroke(dp(2), Color.WHITE);
        marker.setBackground(targetBg);
        marker.setElevation(dp(8));

        int size = dp(58);
        targetLp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        targetLp.gravity = Gravity.TOP | Gravity.START;
        targetLp.x = clamp(Math.round(xFraction * sw() - size / 2f), 0, Math.max(0, sw() - size));
        targetLp.y = clamp(Math.round(yFraction * sh() - size / 2f), 0, Math.max(0, sh() - size));

        marker.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;
            boolean moved;

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (running || paused) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = targetLp.x;
                        startY = targetLp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getRawX() - downRawX) > dp(2) || Math.abs(e.getRawY() - downRawY) > dp(2)) moved = true;
                        targetLp.x = clamp(startX + Math.round(e.getRawX() - downRawX), 0, Math.max(0, sw() - targetLp.width));
                        targetLp.y = clamp(startY + Math.round(e.getRawY() - downRawY), 0, Math.max(0, sh() - targetLp.height));
                        try { wm.updateViewLayout(target, targetLp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        saveTargetPosition();
                        if (moved && guardReference != null) {
                            clearGuardReference("POSITION GEÄNDERT · BILD NEU LERNEN");
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        target = marker;
        wm.addView(target, targetLp);
    }

    private Button small(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(bg(Color.rgb(45, 47, 53), 15));
        return b;
    }

    private TextView compactLabel(String text, float sp, int widthDp) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.WHITE);
        label.setTextSize(sp);
        label.setGravity(Gravity.CENTER);
        label.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), dp(38)));
        return label;
    }

    private void showControls() {
        if (controls != null || wm == null) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(7), dp(6), dp(7), dp(6));
        panel.setBackground(bg(Color.argb(238, 16, 17, 20), 22));
        panel.setElevation(dp(12));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        Button dragHandle = small("↕ ZIEHEN");
        dragHandle.setTextSize(11);
        dragHandle.setBackground(bg(Color.rgb(62, 66, 76), 15));
        headerRow.addView(dragHandle, new LinearLayout.LayoutParams(dp(122), dp(38)));

        Button closeButton = small("✕ BEENDEN");
        closeButton.setTextSize(11);
        closeButton.setBackground(bg(Color.rgb(150, 28, 34), 15));
        closeButton.setOnClickListener(v -> hideOverlays());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(112), dp(38));
        closeLp.setMargins(dp(8), 0, 0, 0);
        headerRow.addView(closeButton, closeLp);
        panel.addView(headerRow);

        LinearLayout guardRow = new LinearLayout(this);
        guardRow.setOrientation(LinearLayout.HORIZONTAL);
        guardRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams guardRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        guardRowLp.setMargins(0, dp(5), 0, 0);
        guardRow.setLayoutParams(guardRowLp);

        guardLearnButton = small("BILD LERNEN");
        guardLearnButton.setTextSize(11);
        guardLearnButton.setBackground(bg(Color.rgb(46, 91, 145), 15));
        guardLearnButton.setOnClickListener(v -> learnGuardReference());
        guardRow.addView(guardLearnButton, new LinearLayout.LayoutParams(dp(122), dp(38)));

        guardToggleButton = small("BILD AUS");
        guardToggleButton.setTextSize(11);
        guardToggleButton.setOnClickListener(v -> toggleImageGuard());
        LinearLayout.LayoutParams guardToggleLp = new LinearLayout.LayoutParams(dp(112), dp(38));
        guardToggleLp.setMargins(dp(8), 0, 0, 0);
        guardRow.addView(guardToggleButton, guardToggleLp);
        panel.addView(guardRow);

        guardStatus = new TextView(this);
        guardStatus.setTextColor(Color.LTGRAY);
        guardStatus.setTextSize(10);
        guardStatus.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams guardStatusLp = new LinearLayout.LayoutParams(dp(242), dp(30));
        guardStatusLp.setMargins(0, dp(2), 0, 0);
        panel.addView(guardStatus, guardStatusLp);

        LinearLayout timingRow = new LinearLayout(this);
        timingRow.setOrientation(LinearLayout.HORIZONTAL);
        timingRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams timingLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timingLp.setMargins(0, dp(4), 0, 0);
        timingRow.setLayoutParams(timingLp);

        intervalMinus = small("−");
        intervalMinus.setOnClickListener(v -> changeInterval(-1));
        timingRow.addView(intervalMinus, new LinearLayout.LayoutParams(dp(36), dp(40)));
        intervalLabel = compactLabel("", 12, 62);
        updateInterval();
        timingRow.addView(intervalLabel);
        intervalPlus = small("+");
        intervalPlus.setOnClickListener(v -> changeInterval(1));
        timingRow.addView(intervalPlus, new LinearLayout.LayoutParams(dp(36), dp(40)));
        modeButton = small("FIX");
        modeButton.setTextSize(11);
        modeButton.setOnClickListener(v -> toggleMode());
        LinearLayout.LayoutParams modeLp = new LinearLayout.LayoutParams(dp(78), dp(40));
        modeLp.setMargins(dp(7), 0, 0, 0);
        timingRow.addView(modeButton, modeLp);
        panel.addView(timingRow);

        randomRow = new LinearLayout(this);
        randomRow.setOrientation(LinearLayout.HORIZONTAL);
        randomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams randomRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        randomRowLp.setMargins(0, dp(5), 0, 0);
        randomRow.setLayoutParams(randomRowLp);

        randomRow.addView(compactLabel("MIN", 10, 34));
        Button minMinus = small("−");
        minMinus.setOnClickListener(v -> changeRandomMin(-RANDOM_STEP_MS));
        randomRow.addView(minMinus, new LinearLayout.LayoutParams(dp(30), dp(36)));
        randomMinLabel = compactLabel("", 11, 50);
        randomRow.addView(randomMinLabel);
        Button minPlus = small("+");
        minPlus.setOnClickListener(v -> changeRandomMin(RANDOM_STEP_MS));
        randomRow.addView(minPlus, new LinearLayout.LayoutParams(dp(30), dp(36)));

        TextView maxTitle = compactLabel("MAX", 10, 38);
        LinearLayout.LayoutParams maxTitleLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        maxTitleLp.setMargins(dp(7), 0, 0, 0);
        maxTitle.setLayoutParams(maxTitleLp);
        randomRow.addView(maxTitle);
        Button maxMinus = small("−");
        maxMinus.setOnClickListener(v -> changeRandomMax(-RANDOM_STEP_MS));
        randomRow.addView(maxMinus, new LinearLayout.LayoutParams(dp(30), dp(36)));
        randomMaxLabel = compactLabel("", 11, 50);
        randomRow.addView(randomMaxLabel);
        Button maxPlus = small("+");
        maxPlus.setOnClickListener(v -> changeRandomMax(RANDOM_STEP_MS));
        randomRow.addView(maxPlus, new LinearLayout.LayoutParams(dp(30), dp(36)));
        panel.addView(randomRow);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionRowLp.setMargins(0, dp(5), 0, 0);
        actionRow.setLayoutParams(actionRowLp);

        startPause = new Button(this);
        startPause.setText("START");
        startPause.setTextColor(Color.WHITE);
        startPause.setTextSize(12);
        startPause.setAllCaps(false);
        startPause.setBackground(bg(Color.rgb(36, 135, 74), 18));
        startPause.setOnClickListener(v -> {
            if (running) pauseLoop();
            else if (paused) resumeLoop();
            else startLoop();
        });
        actionRow.addView(startPause, new LinearLayout.LayoutParams(dp(108), dp(42)));

        stopButton = new Button(this);
        stopButton.setText("STOP");
        stopButton.setTextColor(Color.WHITE);
        stopButton.setTextSize(12);
        stopButton.setAllCaps(false);
        stopButton.setBackground(bg(Color.rgb(175, 30, 35), 18));
        stopButton.setOnClickListener(v -> stopLoop());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(dp(92), dp(42));
        stopLp.setMargins(dp(8), 0, 0, 0);
        actionRow.addView(stopButton, stopLp);
        panel.addView(actionRow);

        updateRandomLabels();
        updateModeUi();
        updateGuardUi(null);

        controlsLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        controlsLp.gravity = Gravity.TOP | Gravity.START;
        controlsLp.x = clamp(Math.round(panelXFraction * sw() - dp(125)), 0, Math.max(0, sw() - dp(250)));
        controlsLp.y = clamp(Math.round(panelYFraction * sh() - dp(105)), 0, Math.max(0, sh() - dp(210)));

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY;
            int startX, startY;

            @Override public boolean onTouch(View v, MotionEvent e) {
                if (controlsLp == null || controls == null || wm == null) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = controlsLp.x;
                        startY = controlsLp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int width = Math.max(dp(250), controls.getWidth());
                        int height = Math.max(dp(150), controls.getHeight());
                        controlsLp.x = clamp(startX + Math.round(e.getRawX() - downRawX), 0, Math.max(0, sw() - width));
                        controlsLp.y = clamp(startY + Math.round(e.getRawY() - downRawY), 0, Math.max(0, sh() - height));
                        try { wm.updateViewLayout(controls, controlsLp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        savePanelPosition();
                        return true;
                    default:
                        return false;
                }
            }
        });

        controls = panel;
        wm.addView(controls, controlsLp);
        controls.post(() -> {
            if (controls == null || controlsLp == null || wm == null) return;
            int width = Math.max(1, controls.getWidth());
            int height = Math.max(1, controls.getHeight());
            controlsLp.x = clamp(Math.round(panelXFraction * sw() - width / 2f), 0, Math.max(0, sw() - width));
            controlsLp.y = clamp(Math.round(panelYFraction * sh() - height / 2f), 0, Math.max(0, sh() - height));
            try { wm.updateViewLayout(controls, controlsLp); } catch (Exception ignored) {}
        });
    }

    private void toggleMode() {
        if (running || paused) return;
        randomMode = !randomMode;
        prefs.edit().putBoolean(KEY_RANDOM_MODE, randomMode).apply();
        updateModeUi();
    }

    private void updateModeUi() {
        if (modeButton != null) {
            modeButton.setText(randomMode ? "RANDOM" : "FIX");
            modeButton.setBackground(bg(randomMode ? Color.rgb(73, 88, 190) : Color.rgb(45, 47, 53), 15));
        }
        if (randomRow != null) randomRow.setVisibility(randomMode ? View.VISIBLE : View.GONE);
        if (intervalMinus != null) {
            intervalMinus.setEnabled(!randomMode);
            intervalMinus.setAlpha(randomMode ? .35f : 1f);
        }
        if (intervalPlus != null) {
            intervalPlus.setEnabled(!randomMode);
            intervalPlus.setAlpha(randomMode ? .35f : 1f);
        }
        if (intervalLabel != null) intervalLabel.setAlpha(randomMode ? .45f : 1f);
    }

    private void changeInterval(int delta) {
        if (running || paused || randomMode) return;
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, intervalIndex + delta));
        prefs.edit().putInt(KEY_I, intervalIndex).apply();
        updateInterval();
    }

    private void updateInterval() {
        if (intervalLabel == null) return;
        long ms = INTERVALS[intervalIndex];
        intervalLabel.setText(ms == 0 ? "MAX" : ms + " ms");
    }

    private void changeRandomMin(long delta) {
        if (running || paused) return;
        randomMinMs = clampLong(randomMinMs + delta, RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        if (randomMinMs > randomMaxMs) randomMaxMs = randomMinMs;
        persistRandomRange();
        updateRandomLabels();
    }

    private void changeRandomMax(long delta) {
        if (running || paused) return;
        randomMaxMs = clampLong(randomMaxMs + delta, RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        if (randomMaxMs < randomMinMs) randomMinMs = randomMaxMs;
        persistRandomRange();
        updateRandomLabels();
    }

    private void persistRandomRange() {
        if (prefs == null) return;
        prefs.edit().putLong(KEY_RANDOM_MIN, randomMinMs).putLong(KEY_RANDOM_MAX, randomMaxMs).apply();
    }

    private void updateRandomLabels() {
        if (randomMinLabel != null) randomMinLabel.setText(randomMinMs + "ms");
        if (randomMaxLabel != null) randomMaxLabel.setText(randomMaxMs + "ms");
    }

    private long currentClickPeriodMs() {
        if (randomMode) {
            long min = Math.max(MIN_CLICK_PERIOD_MS, randomMinMs);
            long max = Math.max(min, randomMaxMs);
            return max == min ? min : ThreadLocalRandom.current().nextLong(min, max + 1);
        }
        long selected = INTERVALS[intervalIndex];
        return selected == 0 ? MIN_CLICK_PERIOD_MS : Math.max(MIN_CLICK_PERIOD_MS, selected);
    }

    private void learnGuardReference() {
        if (Build.VERSION.SDK_INT < 30) {
            updateGuardUi("BILDPRÜFUNG ERST AB ANDROID 11");
            return;
        }
        if (targetLp == null || screenshotInFlight) return;
        if (running || paused) stopLoop();
        guardWaiting = false;
        updateGuardUi("BILD WIRD GELERNT …");
        requestRegionScreenshot(SHOT_LEARN, runGeneration);
    }

    private void toggleImageGuard() {
        if (running || paused) return;
        if (guardReference == null) {
            learnGuardReference();
            return;
        }
        imageGuardEnabled = !imageGuardEnabled;
        prefs.edit().putBoolean(KEY_GUARD_ENABLED, imageGuardEnabled).apply();
        guardWaiting = false;
        updateGuardUi(null);
    }

    private void clearGuardReference(String statusText) {
        guardReference = null;
        imageGuardEnabled = false;
        guardWaiting = false;
        if (prefs != null) {
            prefs.edit().remove(KEY_GUARD_REFERENCE).putBoolean(KEY_GUARD_ENABLED, false).apply();
        }
        updateGuardUi(statusText);
    }

    private void updateGuardUi(String override) {
        if (guardToggleButton != null) {
            guardToggleButton.setText(imageGuardEnabled ? "BILD AN" : "BILD AUS");
            guardToggleButton.setBackground(bg(
                    imageGuardEnabled ? Color.rgb(31, 126, 75) : Color.rgb(45, 47, 53), 15));
        }
        if (guardLearnButton != null) {
            guardLearnButton.setText(guardReference == null ? "BILD LERNEN" : "BILD NEU LERNEN");
        }
        if (guardStatus != null) {
            String value = override;
            if (value == null) {
                if (guardReference == null) value = "BILD: keine Referenz";
                else if (!imageGuardEnabled) value = "BILD: Referenz gespeichert · Wächter AUS";
                else value = "BILD: Wächter AN · Prüfung vor jedem Klick";
            }
            guardStatus.setText(value);
            guardStatus.setTextColor(guardWaiting ? Color.rgb(255, 190, 70) : Color.LTGRAY);
        }
    }

    private void startLoop() {
        if (running || targetLp == null) return;
        if (imageGuardEnabled && guardReference == null) {
            updateGuardUi("BILD FEHLT · ZUERST BILD LERNEN");
            return;
        }
        saveTargetPosition();
        paused = false;
        running = true;
        guardWaiting = false;
        long generation = ++runGeneration;
        updateRunningUi();
        if (imageGuardEnabled) scheduleGuardCheck(generation, 0);
        else scheduleTap(generation, 0);
    }

    private void pauseLoop() {
        if (!running) return;
        running = false;
        paused = true;
        guardWaiting = false;
        ++runGeneration;
        if (startPause != null) {
            startPause.setText("WEITER");
            startPause.setBackground(bg(Color.rgb(210, 135, 25), 18));
        }
        if (target != null) {
            setTargetTouchable(false);
            ((TextView) target).setText("Ⅱ");
            target.setAlpha(.82f);
        }
        updateGuardUi(null);
    }

    private void resumeLoop() {
        if (!paused || targetLp == null) return;
        paused = false;
        running = true;
        guardWaiting = false;
        long generation = ++runGeneration;
        updateRunningUi();
        if (imageGuardEnabled) scheduleGuardCheck(generation, 0);
        else scheduleTap(generation, 0);
    }

    private void stopLoop() {
        running = false;
        paused = false;
        guardWaiting = false;
        ++runGeneration;
        if (startPause != null) {
            startPause.setText("START");
            startPause.setBackground(bg(Color.rgb(36, 135, 74), 18));
        }
        if (target != null) {
            setTargetTouchable(true);
            ((TextView) target).setText("+");
            target.setAlpha(1f);
        }
        updateGuardUi(null);
    }

    private void updateRunningUi() {
        if (startPause != null) {
            startPause.setText("PAUSE");
            startPause.setBackground(bg(Color.rgb(210, 135, 25), 18));
        }
        if (target != null) {
            setTargetTouchable(false);
            ((TextView) target).setText(imageGuardEnabled ? "G" : (randomMode ? "R" : "•"));
            target.setAlpha(.62f);
        }
    }

    private void setGuardWaiting(double similarity) {
        guardWaiting = true;
        if (startPause != null) {
            startPause.setText("WARTET");
            startPause.setBackground(bg(Color.rgb(166, 103, 22), 18));
        }
        if (target != null) {
            ((TextView) target).setText("?");
            target.setAlpha(.72f);
        }
        updateGuardUi("WARTET AUF BILD · " + Math.round(similarity * 100) + "%");
    }

    private void setGuardMatched(double similarity) {
        boolean wasWaiting = guardWaiting;
        guardWaiting = false;
        if (wasWaiting) updateRunningUi();
        updateGuardUi("BILD OK · " + Math.round(similarity * 100) + "%");
    }

    private void setTargetTouchable(boolean touchable) {
        if (target == null || targetLp == null || wm == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        targetLp.flags = flags;
        try { wm.updateViewLayout(target, targetLp); } catch (Exception ignored) {}
    }

    private void scheduleTap(long generation, long delayMs) {
        if (!running || generation != runGeneration) return;
        handler.postDelayed(() -> {
            if (!running || generation != runGeneration || targetLp == null) return;
            dispatchSingleTap();
            scheduleTap(generation, currentClickPeriodMs());
        }, Math.max(0, delayMs));
    }

    private void scheduleGuardCheck(long generation, long delayMs) {
        if (!running || generation != runGeneration || !imageGuardEnabled) return;
        handler.postDelayed(() -> {
            if (!running || generation != runGeneration || !imageGuardEnabled || targetLp == null) return;
            if (screenshotInFlight) {
                scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);
                return;
            }
            requestRegionScreenshot(SHOT_VERIFY, generation);
        }, Math.max(0, delayMs));
    }

    private void requestRegionScreenshot(int purpose, long generation) {
        if (Build.VERSION.SDK_INT < 30 || screenshotInFlight || targetLp == null) {
            if (purpose == SHOT_VERIFY && running && generation == runGeneration) {
                scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);
            }
            return;
        }

        screenshotInFlight = true;
        hideOverlayForScreenshot();
        handler.postDelayed(() -> {
            if (targetLp == null) {
                screenshotInFlight = false;
                restoreOverlayAfterScreenshot();
                return;
            }
            if (purpose == SHOT_VERIFY && (!running || generation != runGeneration || !imageGuardEnabled)) {
                screenshotInFlight = false;
                restoreOverlayAfterScreenshot();
                return;
            }

            try {
                takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, new AccessibilityService.TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(AccessibilityService.ScreenshotResult result) {
                        byte[] descriptor = descriptorFromScreenshot(result);
                        screenshotInFlight = false;
                        restoreOverlayAfterScreenshot();

                        if (descriptor == null) {
                            handleScreenshotFailure(purpose, generation, "BILDPRÜFUNG FEHLER");
                            return;
                        }

                        if (purpose == SHOT_LEARN) {
                            guardReference = descriptor;
                            imageGuardEnabled = true;
                            guardWaiting = false;
                            prefs.edit()
                                    .putString(KEY_GUARD_REFERENCE, Base64.encodeToString(descriptor, Base64.NO_WRAP))
                                    .putBoolean(KEY_GUARD_ENABLED, true)
                                    .apply();
                            updateGuardUi("REFERENZ GELERNT · WÄCHTER AN");
                            return;
                        }

                        if (!running || generation != runGeneration || !imageGuardEnabled || guardReference == null) return;
                        double similarity = similarity(guardReference, descriptor);
                        if (similarity >= MATCH_THRESHOLD) {
                            setGuardMatched(similarity);
                            dispatchSingleTap();
                            long next = Math.max(GUARD_MIN_CHECK_MS, currentClickPeriodMs());
                            scheduleGuardCheck(generation, next);
                        } else {
                            setGuardWaiting(similarity);
                            scheduleGuardCheck(generation, GUARD_MIN_CHECK_MS);
                        }
                    }

                    @Override
                    public void onFailure(int errorCode) {
                        screenshotInFlight = false;
                        restoreOverlayAfterScreenshot();
                        String text = errorCode == ERROR_TAKE_SCREENSHOT_SECURE_WINDOW
                                ? "BILDPRÜFUNG GESPERRT · KEIN KLICK"
                                : "BILDPRÜFUNG FEHLER " + errorCode + " · KEIN KLICK";
                        handleScreenshotFailure(purpose, generation, text);
                    }
                });
            } catch (Exception e) {
                screenshotInFlight = false;
                restoreOverlayAfterScreenshot();
                handleScreenshotFailure(purpose, generation, "BILDPRÜFUNG FEHLER · KEIN KLICK");
            }
        }, SCREENSHOT_SETTLE_MS);
    }

    private void handleScreenshotFailure(int purpose, long generation, String text) {
        guardWaiting = purpose == SHOT_VERIFY;
        updateGuardUi(text);
        if (purpose == SHOT_VERIFY && running && generation == runGeneration && imageGuardEnabled) {
            if (startPause != null) {
                startPause.setText("WARTET");
                startPause.setBackground(bg(Color.rgb(166, 103, 22), 18));
            }
            if (target != null) ((TextView) target).setText("?");
            scheduleGuardCheck(generation, 550);
        }
    }

    private void hideOverlayForScreenshot() {
        if (target != null) target.setAlpha(0f);
        if (controls != null) controls.setAlpha(0f);
    }

    private void restoreOverlayAfterScreenshot() {
        if (controls != null) controls.setAlpha(1f);
        if (target != null) {
            if (running) target.setAlpha(guardWaiting ? .72f : .62f);
            else if (paused) target.setAlpha(.82f);
            else target.setAlpha(1f);
        }
    }

    private byte[] descriptorFromScreenshot(AccessibilityService.ScreenshotResult result) {
        HardwareBuffer buffer = null;
        Bitmap hardwareBitmap = null;
        Bitmap softwareBitmap = null;
        try {
            buffer = result.getHardwareBuffer();
            hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
            if (hardwareBitmap == null) return null;
            softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false);
            if (softwareBitmap == null) return null;
            return descriptorFromBitmap(softwareBitmap);
        } catch (Exception e) {
            return null;
        } finally {
            if (softwareBitmap != null) softwareBitmap.recycle();
            if (hardwareBitmap != null) hardwareBitmap.recycle();
            if (buffer != null) try { buffer.close(); } catch (Exception ignored) {}
        }
    }

    private byte[] descriptorFromBitmap(Bitmap bitmap) {
        if (targetLp == null || bitmap == null || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) return null;

        float centerXScreen = targetLp.x + targetLp.width / 2f;
        float centerYScreen = targetLp.y + targetLp.height / 2f;
        float scaleX = bitmap.getWidth() / Math.max(1f, sw());
        float scaleY = bitmap.getHeight() / Math.max(1f, sh());
        int cx = Math.round(centerXScreen * scaleX);
        int cy = Math.round(centerYScreen * scaleY);
        int cropW = Math.max(DESCRIPTOR_SIDE, Math.round(dp(GUARD_REGION_DP) * scaleX));
        int cropH = Math.max(DESCRIPTOR_SIDE, Math.round(dp(GUARD_REGION_DP) * scaleY));
        cropW = Math.min(cropW, bitmap.getWidth());
        cropH = Math.min(cropH, bitmap.getHeight());
        int left = clamp(cx - cropW / 2, 0, Math.max(0, bitmap.getWidth() - cropW));
        int top = clamp(cy - cropH / 2, 0, Math.max(0, bitmap.getHeight() - cropH));

        Bitmap crop = null;
        Bitmap scaled = null;
        try {
            crop = Bitmap.createBitmap(bitmap, left, top, cropW, cropH);
            scaled = Bitmap.createScaledBitmap(crop, DESCRIPTOR_SIDE, DESCRIPTOR_SIDE, true);
            byte[] data = new byte[DESCRIPTOR_SIDE * DESCRIPTOR_SIDE * 3];
            int p = 0;
            for (int y = 0; y < DESCRIPTOR_SIDE; y++) {
                for (int x = 0; x < DESCRIPTOR_SIDE; x++) {
                    int c = scaled.getPixel(x, y);
                    data[p++] = (byte) Color.red(c);
                    data[p++] = (byte) Color.green(c);
                    data[p++] = (byte) Color.blue(c);
                }
            }
            return data;
        } catch (Exception e) {
            return null;
        } finally {
            if (scaled != null && scaled != crop) scaled.recycle();
            if (crop != null) crop.recycle();
        }
    }

    private double similarity(byte[] reference, byte[] current) {
        if (reference == null || current == null || reference.length != current.length || reference.length == 0) return 0.0;
        long diff = 0;
        for (int i = 0; i < reference.length; i++) {
            diff += Math.abs((reference[i] & 0xff) - (current[i] & 0xff));
        }
        double maxDiff = 255.0 * reference.length;
        return Math.max(0.0, Math.min(1.0, 1.0 - diff / maxDiff));
    }

    private byte[] decodeReference(String encoded) {
        if (encoded == null || encoded.isEmpty()) return null;
        try {
            byte[] decoded = Base64.decode(encoded, Base64.NO_WRAP);
            return decoded.length == DESCRIPTOR_SIDE * DESCRIPTOR_SIDE * 3 ? decoded : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void dispatchSingleTap() {
        if (targetLp == null) return;
        float x = targetLp.x + targetLp.width / 2f;
        float y = targetLp.y + targetLp.height / 2f;
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(path, 0, PRESS_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription description) {}
            @Override public void onCancelled(GestureDescription description) {}
        }, null);
    }

    private void saveTargetPosition() {
        if (targetLp == null || prefs == null) return;
        xFraction = Math.max(0f, Math.min(1f, (targetLp.x + targetLp.width / 2f) / Math.max(1f, sw())));
        yFraction = Math.max(0f, Math.min(1f, (targetLp.y + targetLp.height / 2f) / Math.max(1f, sh())));
        prefs.edit().putFloat(KEY_X, xFraction).putFloat(KEY_Y, yFraction).apply();
    }

    private void savePanelPosition() {
        if (controls == null || controlsLp == null || prefs == null) return;
        int width = Math.max(1, controls.getWidth());
        int height = Math.max(1, controls.getHeight());
        panelXFraction = Math.max(0f, Math.min(1f, (controlsLp.x + width / 2f) / Math.max(1f, sw())));
        panelYFraction = Math.max(0f, Math.min(1f, (controlsLp.y + height / 2f) / Math.max(1f, sh())));
        prefs.edit().putFloat(KEY_PANEL_X, panelXFraction).putFloat(KEY_PANEL_Y, panelYFraction).apply();
    }

    private void hideOverlays() {
        if (prefs != null) prefs.edit().putBoolean(KEY_OVERLAY_VISIBLE, false).apply();
        stopLoop();
        removeOverlays();
    }

    private void removeOverlays() {
        if (wm != null) {
            if (target != null) try { wm.removeView(target); } catch (Exception ignored) {}
            if (controls != null) try { wm.removeView(controls); } catch (Exception ignored) {}
        }
        target = null;
        targetLp = null;
        controls = null;
        controlsLp = null;
        randomRow = null;
        startPause = null;
        stopButton = null;
        intervalMinus = null;
        intervalPlus = null;
        modeButton = null;
        guardLearnButton = null;
        guardToggleButton = null;
        intervalLabel = null;
        randomMinLabel = null;
        randomMaxLabel = null;
        guardStatus = null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        hideOverlays();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (target != null && targetLp != null && wm != null) {
            targetLp.x = clamp(Math.round(xFraction * sw() - targetLp.width / 2f), 0, Math.max(0, sw() - targetLp.width));
            targetLp.y = clamp(Math.round(yFraction * sh() - targetLp.height / 2f), 0, Math.max(0, sh() - targetLp.height));
            try { wm.updateViewLayout(target, targetLp); } catch (Exception ignored) {}
        }
        if (controls != null && controlsLp != null && wm != null) {
            controls.post(() -> {
                if (controls == null || controlsLp == null || wm == null) return;
                int width = Math.max(1, controls.getWidth());
                int height = Math.max(1, controls.getHeight());
                controlsLp.x = clamp(Math.round(panelXFraction * sw() - width / 2f), 0, Math.max(0, sw() - width));
                controlsLp.y = clamp(Math.round(panelYFraction * sh() - height / 2f), 0, Math.max(0, sh() - height));
                try { wm.updateViewLayout(controls, controlsLp); } catch (Exception ignored) {}
            });
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stopLoop(); }

    @Override
    public void onDestroy() {
        running = false;
        paused = false;
        ++runGeneration;
        handler.removeCallbacksAndMessages(null);
        removeOverlays();
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
