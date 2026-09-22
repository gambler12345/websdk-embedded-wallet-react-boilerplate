package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    private static final long[] INTERVALS = {0, 10, 25, 50, 100, 250, 500, 1000};
    private static final long PRESS_DURATION_MS = 8;
    private static final long MIN_CLICK_PERIOD_MS = 16;
    private static final long RANDOM_STEP_MS = 10;
    private static final long RANDOM_MIN_ALLOWED_MS = 20;
    private static final long RANDOM_MAX_ALLOWED_MS = 5000;

    private static volatile AutoClickAccessibilityService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View target;
    private LinearLayout controls;
    private LinearLayout randomRow;
    private WindowManager.LayoutParams targetLp;
    private Button startPause;
    private Button stopButton;
    private Button intervalMinus;
    private Button intervalPlus;
    private Button modeButton;
    private TextView intervalLabel;
    private TextView randomMinLabel;
    private TextView randomMaxLabel;
    private SharedPreferences prefs;

    private boolean running = false;
    private boolean paused = false;
    private boolean randomMode = false;
    private long runGeneration = 0;

    private int intervalIndex = 2;
    private long randomMinMs = 80;
    private long randomMaxMs = 140;
    private float xFraction = .5f;
    private float yFraction = .45f;

    public static void requestShowOverlays(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OVERLAY_VISIBLE, true).apply();
        AutoClickAccessibilityService service = instance;
        if (service != null) {
            service.handler.post(service::ensureOverlaysVisible);
        }
    }

    public static void requestHideOverlays(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_OVERLAY_VISIBLE, false).apply();
        AutoClickAccessibilityService service = instance;
        if (service != null) {
            service.handler.post(service::hideOverlays);
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        xFraction = prefs.getFloat(KEY_X, .5f);
        yFraction = prefs.getFloat(KEY_Y, .45f);
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, prefs.getInt(KEY_I, 2)));
        randomMode = prefs.getBoolean(KEY_RANDOM_MODE, false);
        randomMinMs = clampLong(prefs.getLong(KEY_RANDOM_MIN, 80), RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        randomMaxMs = clampLong(prefs.getLong(KEY_RANDOM_MAX, 140), RANDOM_MIN_ALLOWED_MS, RANDOM_MAX_ALLOWED_MS);
        if (randomMaxMs < randomMinMs) randomMaxMs = randomMinMs;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        if (prefs.getBoolean(KEY_OVERLAY_VISIBLE, false)) {
            ensureOverlaysVisible();
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private int sw() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int sh() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private long clampLong(long v, long min, long max) {
        return Math.max(min, Math.min(max, v));
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
                size,
                size,
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

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (running || paused) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = e.getRawX();
                        downRawY = e.getRawY();
                        startX = targetLp.x;
                        startY = targetLp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        targetLp.x = clamp(startX + Math.round(e.getRawX() - downRawX), 0, Math.max(0, sw() - targetLp.width));
                        targetLp.y = clamp(startY + Math.round(e.getRawY() - downRawY), 0, Math.max(0, sh() - targetLp.height));
                        wm.updateViewLayout(target, targetLp);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        savePosition();
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

        LinearLayout timingRow = new LinearLayout(this);
        timingRow.setOrientation(LinearLayout.HORIZONTAL);
        timingRow.setGravity(Gravity.CENTER_VERTICAL);

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
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        randomRowLp.setMargins(0, dp(5), 0, 0);
        randomRow.setLayoutParams(randomRowLp);

        TextView minTitle = compactLabel("MIN", 10, 34);
        randomRow.addView(minTitle);
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
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
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

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(24);
        controls = panel;
        wm.addView(controls, lp);
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
            modeButton.setBackground(bg(
                    randomMode ? Color.rgb(73, 88, 190) : Color.rgb(45, 47, 53),
                    15
            ));
        }
        if (randomRow != null) {
            randomRow.setVisibility(randomMode ? View.VISIBLE : View.GONE);
        }
        if (intervalMinus != null) {
            intervalMinus.setEnabled(!randomMode);
            intervalMinus.setAlpha(randomMode ? .35f : 1f);
        }
        if (intervalPlus != null) {
            intervalPlus.setEnabled(!randomMode);
            intervalPlus.setAlpha(randomMode ? .35f : 1f);
        }
        if (intervalLabel != null) {
            intervalLabel.setAlpha(randomMode ? .45f : 1f);
        }
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
        prefs.edit()
                .putLong(KEY_RANDOM_MIN, randomMinMs)
                .putLong(KEY_RANDOM_MAX, randomMaxMs)
                .apply();
    }

    private void updateRandomLabels() {
        if (randomMinLabel != null) randomMinLabel.setText(randomMinMs + "ms");
        if (randomMaxLabel != null) randomMaxLabel.setText(randomMaxMs + "ms");
    }

    private long currentClickPeriodMs() {
        if (randomMode) {
            long min = Math.max(MIN_CLICK_PERIOD_MS, randomMinMs);
            long max = Math.max(min, randomMaxMs);
            if (max == min) return min;
            return ThreadLocalRandom.current().nextLong(min, max + 1);
        }

        long selected = INTERVALS[intervalIndex];
        if (selected == 0) return MIN_CLICK_PERIOD_MS;
        return Math.max(MIN_CLICK_PERIOD_MS, selected);
    }

    private void startLoop() {
        if (running || targetLp == null) return;
        savePosition();
        paused = false;
        running = true;
        long generation = ++runGeneration;
        handler.removeCallbacksAndMessages(null);
        updateRunningUi();
        scheduleTap(generation, 0);
    }

    private void pauseLoop() {
        if (!running) return;
        running = false;
        paused = true;
        ++runGeneration;
        handler.removeCallbacksAndMessages(null);
        if (startPause != null) {
            startPause.setText("WEITER");
            startPause.setBackground(bg(Color.rgb(210, 135, 25), 18));
        }
        if (target != null) {
            setTargetTouchable(false);
            ((TextView) target).setText("Ⅱ");
            target.setAlpha(.82f);
        }
    }

    private void resumeLoop() {
        if (!paused || targetLp == null) return;
        paused = false;
        running = true;
        long generation = ++runGeneration;
        handler.removeCallbacksAndMessages(null);
        updateRunningUi();
        scheduleTap(generation, 0);
    }

    private void stopLoop() {
        running = false;
        paused = false;
        ++runGeneration;
        handler.removeCallbacksAndMessages(null);

        if (startPause != null) {
            startPause.setText("START");
            startPause.setBackground(bg(Color.rgb(36, 135, 74), 18));
        }
        if (target != null) {
            setTargetTouchable(true);
            ((TextView) target).setText("+");
            target.setAlpha(1f);
        }
    }

    private void updateRunningUi() {
        if (startPause != null) {
            startPause.setText("PAUSE");
            startPause.setBackground(bg(Color.rgb(210, 135, 25), 18));
        }
        if (target != null) {
            setTargetTouchable(false);
            ((TextView) target).setText(randomMode ? "R" : "•");
            target.setAlpha(.62f);
        }
    }

    private void setTargetTouchable(boolean touchable) {
        if (target == null || targetLp == null || wm == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        targetLp.flags = flags;
        try {
            wm.updateViewLayout(target, targetLp);
        } catch (Exception ignored) {
        }
    }

    private void scheduleTap(long generation, long delayMs) {
        if (!running || generation != runGeneration) return;
        handler.postDelayed(() -> {
            if (!running || generation != runGeneration || targetLp == null) return;
            dispatchSingleTap();
            // In RANDOM mode this value is redrawn after every click.
            scheduleTap(generation, currentClickPeriodMs());
        }, Math.max(0, delayMs));
    }

    private void dispatchSingleTap() {
        if (targetLp == null) return;

        float x = targetLp.x + targetLp.width / 2f;
        float y = targetLp.y + targetLp.height / 2f;
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, PRESS_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                // Timer-driven loop: callback does not control repetition.
            }

            @Override
            public void onCancelled(GestureDescription description) {
                // Timer continues and tries again on the next scheduled cycle.
            }
        }, null);
    }

    private void savePosition() {
        if (targetLp == null || prefs == null) return;
        xFraction = Math.max(0f, Math.min(1f,
                (targetLp.x + targetLp.width / 2f) / Math.max(1f, sw())));
        yFraction = Math.max(0f, Math.min(1f,
                (targetLp.y + targetLp.height / 2f) / Math.max(1f, sh())));
        prefs.edit().putFloat(KEY_X, xFraction).putFloat(KEY_Y, yFraction).apply();
    }

    private void hideOverlays() {
        if (prefs != null) {
            prefs.edit().putBoolean(KEY_OVERLAY_VISIBLE, false).apply();
        }
        stopLoop();
        removeOverlays();
    }

    private void removeOverlays() {
        if (wm != null) {
            if (target != null) {
                try { wm.removeView(target); } catch (Exception ignored) {}
            }
            if (controls != null) {
                try { wm.removeView(controls); } catch (Exception ignored) {}
            }
        }
        target = null;
        targetLp = null;
        controls = null;
        randomRow = null;
        startPause = null;
        stopButton = null;
        intervalMinus = null;
        intervalPlus = null;
        modeButton = null;
        intervalLabel = null;
        randomMinLabel = null;
        randomMaxLabel = null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        hideOverlays();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (target == null || targetLp == null || wm == null) return;
        targetLp.x = clamp(Math.round(xFraction * sw() - targetLp.width / 2f), 0, Math.max(0, sw() - targetLp.width));
        targetLp.y = clamp(Math.round(yFraction * sh() - targetLp.height / 2f), 0, Math.max(0, sh() - targetLp.height));
        try {
            wm.updateViewLayout(target, targetLp);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
        stopLoop();
    }

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
