package com.zclock.mod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Draws all the custom lock-screen clock styles. Uses only framework classes so it can live inside SystemUI. */
public class CustomClockView extends View {
    private Prefs prefs;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEE, d MMM", Locale.getDefault());
    private final RectF oval = new RectF();
    private boolean rxRegistered;

    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidate(); schedule(); }
    };
    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { invalidate(); }
    };

    /** When true, parents stop clipping their children so the clock can be moved anywhere. */
    public boolean unclipAncestors;

    public CustomClockView(Context c, Prefs prefs) {
        super(c);
        this.prefs = prefs;
        applyOffset();
    }

    public void setPrefs(Prefs prefs) {
        this.prefs = prefs;
        applyOffset();
        schedule();
        invalidate();
    }

    private void applyOffset() {
        if (prefs == null) return;
        float d = getResources().getDisplayMetrics().density;
        setTranslationX(prefs.i(Prefs.LS_X) * d);
        setTranslationY(prefs.i(Prefs.LS_Y) * d);
    }

    private void unclip() {
        if (!unclipAncestors) return;
        android.view.ViewParent par = getParent();
        for (int i = 0; i < 10 && par instanceof ViewGroup; i++) {
            ViewGroup vg = (ViewGroup) par;
            vg.setClipChildren(false);
            vg.setClipToPadding(false);
            par = vg.getParent();
        }
    }

    public static int heightPx(Prefs pr, float density) {
        return Math.round(pr.i(Prefs.LS_SIZE) * density * pr.i(Prefs.LS_SY) / 100f);
    }

    // ---------- ticking ----------
    private int style() { return Math.max(1, prefs.i(Prefs.LS_STYLE)); }

    private void schedule() {
        handler.removeCallbacks(tick);
        if (prefs == null || !isAttachedToWindow() || getWindowVisibility() != VISIBLE) return;
        int st = style();
        boolean sec = prefs.b(Prefs.LS_SEC) && st >= 3 && st <= 6;
        long now = System.currentTimeMillis();
        handler.postDelayed(tick, sec ? 1000 - now % 1000 : 60000 - now % 60000 + 30);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        unclip();
        if (!rxRegistered) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            try { getContext().registerReceiver(rx, f); rxRegistered = true; } catch (Throwable ignored) {}
        }
        schedule();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
        if (rxRegistered) {
            try { getContext().unregisterReceiver(rx); } catch (Throwable ignored) {}
            rxRegistered = false;
        }
    }

    @Override protected void onWindowVisibilityChanged(int v) {
        super.onWindowVisibilityChanged(v);
        if (v == VISIBLE) { invalidate(); schedule(); } else handler.removeCallbacks(tick);
    }

    @Override protected void onMeasure(int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        int hh = prefs == null ? 0 : heightPx(prefs, d);
        setMeasuredDimension(resolveSize((int) (360 * d), w), resolveSize(hh, h));
    }

    // ---------- drawing ----------
    private static int clampW(int w) { return Math.max(1, Math.min(1000, w)); }
    private static Typeface tf(int w) { return Typeface.create(Typeface.DEFAULT, clampW(w), false); }
    private static int alpha(int c, float f) {
        return (c & 0x00FFFFFF) | (Math.round(Color.alpha(c) * f) << 24);
    }
    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private void text(Canvas c, String t, float x, float cy, float size, Paint.Align a) {
        p.setTextSize(size);
        p.setTextAlign(a);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(t, x, cy - (fm.ascent + fm.descent) / 2f, p);
    }

    @Override protected void onDraw(Canvas c) {
        if (prefs == null) return;
        float d = getResources().getDisplayMetrics().density;
        float S = prefs.i(Prefs.LS_SIZE) * d;
        float sx = prefs.i(Prefs.LS_SX) / 100f, sy = prefs.i(Prefs.LS_SY) / 100f;
        int color = prefs.i(Prefs.LS_COLOR), weight = prefs.i(Prefs.LS_WEIGHT);
        boolean date = prefs.b(Prefs.LS_DATE), is24 = prefs.b(Prefs.LS_24H);

        Calendar cal = Calendar.getInstance();
        int hr = cal.get(Calendar.HOUR_OF_DAY), min = cal.get(Calendar.MINUTE), sec = cal.get(Calendar.SECOND);
        if (!is24) { hr %= 12; if (hr == 0) hr = 12; }
        String H = two(hr), M = two(min);

        float dateH = date ? S * 0.16f : 0f;
        float mainH = S - dateH;
        float cy = -dateH / 2f;

        c.save();
        c.translate(getWidth() / 2f, getHeight() / 2f);
        c.scale(sx, sy);

        p.reset();
        p.setAntiAlias(true);
        p.setColor(color);
        p.setTypeface(tf(weight));

        switch (style()) {
            case 1: stacked(c, H, M, mainH, cy, color, weight); break;
            case 2: split(c, H, M, mainH, cy, color, weight); break;
            case 3: analogClassic(c, cal, hr, min, sec, mainH, cy, color); break;
            case 4: analogMinimal(c, cal, hr, min, sec, mainH, cy, color); break;
            case 5: binary(c, hr, min, sec, mainH, cy, color); break;
            case 6: ring(c, H, M, min, sec, mainH, cy, color, weight); break;
            default: outline(c, H, M, mainH, cy, color, weight); break;
        }

        if (date) {
            p.reset();
            p.setAntiAlias(true);
            p.setColor(alpha(color, 0.8f));
            p.setTypeface(tf(500));
            text(c, dateFmt.format(cal.getTime()), 0, S / 2f - dateH / 2f, dateH * 0.62f, Paint.Align.CENTER);
        }
        c.restore();
    }

    private void stacked(Canvas c, String H, String M, float mainH, float cy, int color, int w) {
        float size = mainH * 0.5f;
        p.setColor(color);
        text(c, H, 0, cy - mainH * 0.245f, size, Paint.Align.CENTER);
        p.setColor(alpha(color, 0.8f));
        text(c, M, 0, cy + mainH * 0.245f, size, Paint.Align.CENTER);
    }

    private void split(Canvas c, String H, String M, float mainH, float cy, int color, int w) {
        float size = mainH * 0.62f;
        p.setTextSize(size);
        p.setTypeface(tf(w + 300));
        float wh = p.measureText(H);
        p.setTypeface(tf(Math.max(100, w - 400)));
        float wm = p.measureText(M);
        float gap = size * 0.1f, x0 = -(wh + gap + wm) / 2f;
        p.setTypeface(tf(w + 300));
        p.setColor(color);
        text(c, H, x0, cy, size, Paint.Align.LEFT);
        p.setTypeface(tf(Math.max(100, w - 400)));
        p.setColor(alpha(color, 0.75f));
        text(c, M, x0 + wh + gap, cy, size, Paint.Align.LEFT);
    }

    private void hand(Canvas c, float cx, float cy, float deg, float len, float w, int col) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(w);
        p.setColor(col);
        double a = Math.toRadians(deg);
        c.drawLine(cx, cy, cx + (float) Math.sin(a) * len, cy - (float) Math.cos(a) * len, p);
    }

    private void analogClassic(Canvas c, Calendar cal, int hr, int min, int sec, float mainH, float cy, int color) {
        float r = mainH / 2f * 0.96f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.04f);
        p.setColor(color);
        c.drawCircle(0, cy, r, p);
        for (int i = 0; i < 12; i++) {
            float len = (i % 3 == 0) ? r * 0.16f : r * 0.08f;
            p.setStrokeWidth(i % 3 == 0 ? r * 0.04f : r * 0.025f);
            double a = Math.toRadians(i * 30);
            float s = (float) Math.sin(a), co = (float) Math.cos(a);
            c.drawLine(s * (r * 0.86f), cy - co * (r * 0.86f), s * (r * 0.86f - len), cy - co * (r * 0.86f - len), p);
        }
        float hDeg = ((hr % 12) + min / 60f + sec / 3600f) * 30f, mDeg = (min + sec / 60f) * 6f;
        hand(c, 0, cy, hDeg, r * 0.5f, r * 0.07f, color);
        hand(c, 0, cy, mDeg, r * 0.78f, r * 0.05f, color);
        if (prefs.b(Prefs.LS_SEC)) hand(c, 0, cy, sec * 6f, r * 0.85f, r * 0.02f, 0xFFFF5252);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.drawCircle(0, cy, r * 0.05f, p);
    }

    private void analogMinimal(Canvas c, Calendar cal, int hr, int min, int sec, float mainH, float cy, int color) {
        float r = mainH / 2f * 0.96f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(color, 0.7f));
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            c.drawCircle((float) Math.sin(a) * r * 0.92f, cy - (float) Math.cos(a) * r * 0.92f,
                    i % 3 == 0 ? r * 0.04f : r * 0.022f, p);
        }
        float hDeg = ((hr % 12) + min / 60f + sec / 3600f) * 30f, mDeg = (min + sec / 60f) * 6f;
        hand(c, 0, cy, hDeg, r * 0.5f, r * 0.1f, color);
        hand(c, 0, cy, mDeg, r * 0.78f, r * 0.05f, alpha(color, 0.9f));
        p.setStyle(Paint.Style.FILL);
        if (prefs.b(Prefs.LS_SEC)) {
            double a = Math.toRadians(sec * 6);
            p.setColor(0xFFFF5252);
            c.drawCircle((float) Math.sin(a) * r * 0.8f, cy - (float) Math.cos(a) * r * 0.8f, r * 0.04f, p);
        }
        p.setColor(color);
        c.drawCircle(0, cy, r * 0.045f, p);
    }

    private void binary(Canvas c, int hr, int min, int sec, float mainH, float cy, int color) {
        int[] dg = {hr / 10, hr % 10, min / 10, min % 10, sec / 10, sec % 10};
        int n = prefs.b(Prefs.LS_SEC) ? 6 : 4;
        float cell = mainH / 4f, gap = cell * 0.5f, r = cell * 0.32f;
        float width = n * cell + (n / 2 - 1) * gap;
        float startX = -width / 2f + cell / 2f;
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < n; k++) {
            float x = startX + k * cell + (k / 2) * gap;
            for (int row = 0; row < 4; row++) {
                boolean on = ((dg[k] >> (3 - row)) & 1) == 1;
                p.setColor(alpha(color, on ? 1f : 0.18f));
                c.drawCircle(x, cy - mainH / 2f + cell / 2f + row * cell, r, p);
            }
        }
    }

    private void ring(Canvas c, String H, String M, int min, int sec, float mainH, float cy, int color, int w) {
        float r = mainH / 2f * 0.92f, sw = r * 0.09f;
        oval.set(-r, cy - r, r, cy + r);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(sw);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(alpha(color, 0.18f));
        c.drawOval(oval, p);
        p.setColor(color);
        c.drawArc(oval, -90, Math.max(4f, (min + sec / 60f) * 6f), false, p);
        if (prefs.b(Prefs.LS_SEC)) {
            double a = Math.toRadians(sec * 6);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF5252);
            c.drawCircle((float) Math.sin(a) * (r - sw * 1.6f), cy - (float) Math.cos(a) * (r - sw * 1.6f), sw * 0.5f, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setTypeface(tf(w));
        text(c, H + ":" + M, 0, cy, r * 0.55f, Paint.Align.CENTER);
    }

    private void outline(Canvas c, String H, String M, float mainH, float cy, int color, int w) {
        float size = mainH * 0.8f;
        String t = H + ":" + M;
        p.setStyle(Paint.Style.FILL);
        p.setColor(alpha(color, 0.12f));
        text(c, t, 0, cy, size, Paint.Align.CENTER);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, size * 0.025f));
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        text(c, t, 0, cy, size, Paint.Align.CENTER);
    }
}
