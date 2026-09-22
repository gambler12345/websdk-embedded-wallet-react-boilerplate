package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
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

public class AutoClickAccessibilityService extends AccessibilityService {
    private static final String PREFS = "autoclick_prefs", KEY_X = "x", KEY_Y = "y", KEY_I = "interval";
    private static final long[] INTERVALS = {0, 10, 25, 50, 100, 250, 500, 1000};
    private static final long PRESS_DURATION_MS = 45;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View target;
    private LinearLayout controls;
    private WindowManager.LayoutParams targetLp;
    private Button startPause;
    private Button stopButton;
    private TextView intervalLabel;
    private SharedPreferences prefs;

    private boolean running = false;
    private boolean paused = false;
    private boolean gestureInFlight = false;
    private long runGeneration = 0;
    private long gestureSerial = 0;
    private long activeGestureSerial = 0;

    private int intervalIndex = 2;
    private float xFraction = .5f, yFraction = .45f;

    @Override
    protected void onServiceConnected() {
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        xFraction = prefs.getFloat(KEY_X, .5f);
        yFraction = prefs.getFloat(KEY_Y, .45f);
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, prefs.getInt(KEY_I, 2)));
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        showTarget();
        showControls();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color, float radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int sw() { return getResources().getDisplayMetrics().widthPixels; }
    private int sh() { return getResources().getDisplayMetrics().heightPixels; }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void showTarget() {
        TextView m = new TextView(this);
        m.setText("+");
        m.setTextColor(Color.WHITE);
        m.setTextSize(27);
        m.setGravity(Gravity.CENTER);
        GradientDrawable targetBg = bg(Color.rgb(215, 25, 32), 100);
        targetBg.setStroke(dp(2), Color.WHITE);
        m.setBackground(targetBg);
        m.setElevation(dp(8));

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

        m.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            int sx, sy;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (running || paused) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = e.getRawX();
                        dy = e.getRawY();
                        sx = targetLp.x;
                        sy = targetLp.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        targetLp.x = clamp(sx + Math.round(e.getRawX() - dx), 0, Math.max(0, sw() - targetLp.width));
                        targetLp.y = clamp(sy + Math.round(e.getRawY() - dy), 0, Math.max(0, sh() - targetLp.height));
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

        target = m;
        wm.addView(target, targetLp);
    }

    private Button small(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        b.setBackground(bg(Color.rgb(45, 47, 53), 18));
        return b;
    }

    private void showControls() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.HORIZONTAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(dp(7), dp(6), dp(7), dp(6));
        p.setBackground(bg(Color.argb(238, 16, 17, 20), 22));
        p.setElevation(dp(12));

        Button minus = small("−");
        minus.setOnClickListener(v -> changeInterval(-1));
        p.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(44)));

        intervalLabel = new TextView(this);
        intervalLabel.setTextColor(Color.WHITE);
        intervalLabel.setTextSize(12);
        intervalLabel.setGravity(Gravity.CENTER);
        updateInterval();
        p.addView(intervalLabel, new LinearLayout.LayoutParams(dp(66), dp(44)));

        Button plus = small("+");
        plus.setOnClickListener(v -> changeInterval(1));
        p.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(44)));

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
        LinearLayout.LayoutParams startLp = new LinearLayout.LayoutParams(dp(82), dp(44));
        startLp.setMargins(dp(7), 0, 0, 0);
        p.addView(startPause, startLp);

        stopButton = new Button(this);
        stopButton.setText("STOP");
        stopButton.setTextColor(Color.WHITE);
        stopButton.setTextSize(12);
        stopButton.setAllCaps(false);
        stopButton.setBackground(bg(Color.rgb(175, 30, 35), 18));
        stopButton.setOnClickListener(v -> stopLoop());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(dp(70), dp(44));
        stopLp.setMargins(dp(7), 0, 0, 0);
        p.addView(stopButton, stopLp);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(28);
        controls = p;
        wm.addView(controls, lp);
    }

    private void changeInterval(int d) {
        if (running || paused) return;
        intervalIndex = Math.max(0, Math.min(INTERVALS.length - 1, intervalIndex + d));
        prefs.edit().putInt(KEY_I, intervalIndex).apply();
        updateInterval();
    }

    private void updateInterval() {
        if (intervalLabel != null) {
            long ms = INTERVALS[intervalIndex];
            intervalLabel.setText(ms == 0 ? "MAX" : ms + " ms");
        }
    }

    private void startLoop() {
        if (running) return;
        savePosition();
        paused = false;
        running = true;
        runGeneration++;
        updateRunningUi();
        scheduleNext(0, runGeneration);
    }

    private void pauseLoop() {
        if (!running) return;
        running = false;
        paused = true;
        runGeneration++;
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
        if (!paused) return;
        paused = false;
        running = true;
        runGeneration++;
        updateRunningUi();
        scheduleNext(0, runGeneration);
    }

    private void stopLoop() {
        running = false;
        paused = false;
        runGeneration++;
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
            ((TextView) target).setText("•");
            target.setAlpha(.62f);
        }
    }

    private void setTargetTouchable(boolean touch) {
        if (target == null) return;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touch) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        targetLp.flags = flags;
        wm.updateViewLayout(target, targetLp);
    }

    private void scheduleNext(long delay, long generation) {
        if (!running || generation != runGeneration) return;
        handler.postDelayed(() -> {
            if (!running || generation != runGeneration) return;
            if (gestureInFlight) {
                scheduleNext(10, generation);
                return;
            }
            performPressRelease(generation);
        }, Math.max(0, delay));
    }

    private void performPressRelease(long generation) {
        if (!running || generation != runGeneration || gestureInFlight) return;

        float x = targetLp.x + targetLp.width / 2f;
        float y = targetLp.y + targetLp.height / 2f;
        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, PRESS_DURATION_MS);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();

        final long thisGesture = ++gestureSerial;
        activeGestureSerial = thisGesture;
        gestureInFlight = true;

        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription description) {
                if (activeGestureSerial == thisGesture) gestureInFlight = false;
                if (running && generation == runGeneration) {
                    scheduleNext(INTERVALS[intervalIndex], generation);
                }
            }

            @Override
            public void onCancelled(GestureDescription description) {
                if (activeGestureSerial == thisGesture) gestureInFlight = false;
                if (running && generation == runGeneration) {
                    scheduleNext(Math.max(10, INTERVALS[intervalIndex]), generation);
                }
            }
        }, handler);

        if (!accepted) {
            if (activeGestureSerial == thisGesture) gestureInFlight = false;
            scheduleNext(Math.max(25, INTERVALS[intervalIndex]), generation);
        }
    }

    private void savePosition() {
        if (targetLp == null || prefs == null) return;
        xFraction = Math.max(0f, Math.min(1f, (targetLp.x + targetLp.width / 2f) / Math.max(1f, sw())));
        yFraction = Math.max(0f, Math.min(1f, (targetLp.y + targetLp.height / 2f) / Math.max(1f, sh())));
        prefs.edit().putFloat(KEY_X, xFraction).putFloat(KEY_Y, yFraction).apply();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        if (target == null) return;
        targetLp.x = clamp(Math.round(xFraction * sw() - targetLp.width / 2f), 0, Math.max(0, sw() - targetLp.width));
        targetLp.y = clamp(Math.round(yFraction * sh() - targetLp.height / 2f), 0, Math.max(0, sh() - targetLp.height));
        wm.updateViewLayout(target, targetLp);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() { stopLoop(); }

    @Override
    public void onDestroy() {
        running = false;
        paused = false;
        runGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (wm != null) {
            if (target != null) try { wm.removeView(target); } catch (Exception ignored) {}
            if (controls != null) try { wm.removeView(controls); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }
}
