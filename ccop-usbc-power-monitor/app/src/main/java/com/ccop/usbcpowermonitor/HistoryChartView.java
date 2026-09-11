package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryChartView extends View {
    public enum Mode { POWER, SOC }

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint interrupt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<HistoryStore.Point> points = new ArrayList<>();
    private Mode mode = Mode.POWER;

    public HistoryChartView(Context c, Mode m) {
        super(c);
        mode = m;
        grid.setColor(Color.rgb(43, 56, 68));
        grid.setStrokeWidth(1f);
        text.setColor(Color.rgb(151, 164, 178));
        text.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
        p1.setColor(Color.rgb(72, 229, 139));
        p1.setStrokeWidth(2.4f * getResources().getDisplayMetrics().density);
        p1.setStyle(Paint.Style.STROKE);
        p2.setColor(Color.rgb(117, 164, 255));
        p2.setStrokeWidth(2.0f * getResources().getDisplayMetrics().density);
        p2.setStyle(Paint.Style.STROKE);
        p3.setColor(Color.rgb(255, 210, 105));
        p3.setStrokeWidth(2.0f * getResources().getDisplayMetrics().density);
        p3.setStyle(Paint.Style.STROKE);
        interrupt.setColor(Color.rgb(255, 114, 114));
        interrupt.setStrokeWidth(1f * getResources().getDisplayMetrics().density);
    }

    public void setPoints(List<HistoryStore.Point> p) {
        points = p == null ? new ArrayList<>() : p;
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w = getWidth(), h = getHeight();
        c.drawColor(Color.rgb(8, 13, 17));
        float left = 48f, right = w - 10f, top = 12f, bottom = h - 28f;
        if (right <= left || bottom <= top) return;
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            c.drawLine(left, y, right, y, grid);
        }
        if (points == null || points.size() < 2) {
            c.drawText("Noch nicht genug Verlauf", left + 8, top + 24, text);
            return;
        }
        long minTs = points.get(0).ts;
        long maxTs = points.get(points.size() - 1).ts;
        if (maxTs <= minTs) maxTs = minTs + 1;

        if (mode == Mode.POWER) drawPower(c, left, right, top, bottom, minTs, maxTs);
        else drawSoc(c, left, right, top, bottom, minTs, maxTs);

        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.GERMANY);
        c.drawText(f.format(new Date(minTs)), left, h - 7f, text);
        String end = f.format(new Date(maxTs));
        c.drawText(end, right - text.measureText(end), h - 7f, text);
    }

    private void drawPower(Canvas c, float left, float right, float top, float bottom, long minTs, long maxTs) {
        double max = 1.0;
        for (HistoryStore.Point p : points) {
            if (!Double.isNaN(p.batteryW)) max = Math.max(max, Math.abs(p.batteryW));
            if (!Double.isNaN(p.sourceW)) max = Math.max(max, Math.abs(p.sourceW));
        }
        max *= 1.12;
        c.drawText(String.format(Locale.GERMANY, "%.1f W", max), 4f, top + 10f, text);
        c.drawText("0 W", 8f, bottom, text);
        drawSeries(c, points, left, right, top, bottom, minTs, maxTs, max, 0, p1);
        drawSeries(c, points, left, right, top, bottom, minTs, maxTs, max, 1, p2);
        drawInterruptions(c, left, right, top, bottom, minTs, maxTs);
        c.drawText("Netto Akku W", left + 8, top + 13, p1);
        c.drawText("Quelle W", left + 112, top + 13, p2);
    }

    private void drawSoc(Canvas c, float left, float right, float top, float bottom, long minTs, long maxTs) {
        c.drawText("100%", 4f, top + 10f, text);
        c.drawText("0%", 12f, bottom, text);
        drawSocSeries(c, left, right, top, bottom, minTs, maxTs, 0, p1);
        drawSocSeries(c, left, right, top, bottom, minTs, maxTs, 1, p2);
        drawSocSeries(c, left, right, top, bottom, minTs, maxTs, 2, p3);
        c.drawText("Handy", left + 8, top + 13, p1);
        c.drawText("Quelle", left + 72, top + 13, p2);
        c.drawText("Gesamt", left + 138, top + 13, p3);
    }

    private void drawSeries(Canvas c, List<HistoryStore.Point> pts, float left, float right, float top, float bottom,
                            long minTs, long maxTs, double maxY, int which, Paint paint) {
        Path path = new Path();
        boolean started = false;
        for (HistoryStore.Point p : pts) {
            double v = which == 0 ? p.batteryW : p.sourceW;
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            float x = left + (right - left) * (p.ts - minTs) / (float) (maxTs - minTs);
            float y = bottom - (bottom - top) * (float) (Math.abs(v) / maxY);
            if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
        }
        if (started) c.drawPath(path, paint);
    }

    private void drawSocSeries(Canvas c, float left, float right, float top, float bottom, long minTs, long maxTs,
                               int which, Paint paint) {
        Path path = new Path();
        boolean started = false;
        for (HistoryStore.Point p : points) {
            double v = which == 0 ? p.phonePct : (which == 1 ? p.sourceSoc : p.aggregateSoc);
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            v = Math.max(0, Math.min(100, v));
            float x = left + (right - left) * (p.ts - minTs) / (float) (maxTs - minTs);
            float y = bottom - (bottom - top) * (float) (v / 100.0);
            if (!started) { path.moveTo(x, y); started = true; } else path.lineTo(x, y);
        }
        if (started) c.drawPath(path, paint);
    }

    private void drawInterruptions(Canvas c, float left, float right, float top, float bottom, long minTs, long maxTs) {
        boolean last = points.get(0).charging;
        for (int i = 1; i < points.size(); i++) {
            boolean now = points.get(i).charging;
            if (now != last) {
                float x = left + (right - left) * (points.get(i).ts - minTs) / (float) (maxTs - minTs);
                c.drawLine(x, top, x, bottom, interrupt);
            }
            last = now;
        }
    }
}
