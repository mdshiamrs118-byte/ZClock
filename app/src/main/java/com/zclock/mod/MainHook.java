package com.zclock.mod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String SYSUI = "com.android.systemui";
    private static final String HIDE_TAG = "zclock_hide";
    private static final String CUSTOM_TAG = "zclock_custom";

    private static XSharedPreferences xp;
    private static boolean receiverRegistered;
    private static final Set<TextView> clocks = Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>());
    private static final Set<TextView> secLoops = Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>());
    private static final Set<ViewGroup> switches = Collections.newSetFromMap(new WeakHashMap<ViewGroup, Boolean>());
    private static final WeakHashMap<TextView, String> sigs = new WeakHashMap<>();

    private static synchronized Prefs prefs() {
        if (xp.hasFileChanged()) xp.reload();
        return new Prefs(xp);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (lp.packageName.equals(Prefs.PKG)) {
            // lets the settings app show "module active"
            XposedHelpers.findAndHookMethod(Prefs.PKG + ".MainActivity", lp.classLoader,
                    "isModuleActive", XC_MethodReplacement.returnConstant(true));
            return;
        }
        if (!lp.packageName.equals(SYSUI)) return;

        xp = new XSharedPreferences(Prefs.PKG, Prefs.NAME);
        xp.makeWorldReadable();

        try { hookStatusBarClock(lp.classLoader); } catch (Throwable t) { XposedBridge.log("ZClock sb: " + t); }
        try { hookKeyguardClock(lp.classLoader); } catch (Throwable t) { XposedBridge.log("ZClock ks: " + t); }
    }

    // =====================================================================
    //  Status bar clock  (com.android.systemui.statusbar.policy.Clock)
    // =====================================================================
    private void hookStatusBarClock(ClassLoader cl) {
        Class<?> clock = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.policy.Clock", cl);
        if (clock == null) { XposedBridge.log("ZClock: Clock class not found"); return; }

        XC_MethodHook attach = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                TextView tv = (TextView) param.thisObject;
                ensureReceiver(tv.getContext());
                clocks.add(tv);
                applyClock(tv);
                startSeconds(tv);
            }
        };
        XC_MethodHook reapply = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                TextView tv = (TextView) param.thisObject;
                sigs.remove(tv);   // system just reset size/colour -> force re-apply
                applyClock(tv);
            }
        };
        XC_MethodHook light = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                applyClock((TextView) param.thisObject);
            }
        };
        XposedBridge.hookAllMethods(clock, "onAttachedToWindow", attach);
        XposedBridge.hookAllMethods(clock, "onDensityOrFontScaleChanged", reapply);
        XposedBridge.hookAllMethods(clock, "onDarkChanged", reapply);
        XposedBridge.hookAllMethods(clock, "updateClock", light);

        // replace the displayed text with our own pattern
        XposedBridge.hookAllMethods(clock, "getSmallTime", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Prefs p = prefs();
                    if (!p.b(Prefs.SB_ON)) return;
                    String pat = p.sbPattern();
                    if (pat == null) return;
                    param.setResult(new SimpleDateFormat(pat, Locale.getDefault()).format(new Date()));
                } catch (Throwable ignored) { /* bad custom pattern -> keep stock text */ }
            }
        });
    }

    private static void applyClock(TextView tv) {
        Prefs p = prefs();
        if (!p.b(Prefs.SB_ON)) return;
        String sig = p.sig(Prefs.SB_KEYS);
        if (sig.equals(sigs.get(tv))) return;
        sigs.put(tv, sig);

        float d = tv.getResources().getDisplayMetrics().density;
        int size = p.i(Prefs.SB_SIZE);
        if (size > 0) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        int w = p.i(Prefs.SB_WEIGHT);
        if (w > 0) tv.setTypeface(Typeface.create(Typeface.DEFAULT, Math.min(1000, w), false));
        tv.setLetterSpacing(p.i(Prefs.SB_SPACING) / 100f);
        tv.setScaleX(p.i(Prefs.SB_SX) / 100f);
        tv.setScaleY(p.i(Prefs.SB_SY) / 100f);
        tv.setTranslationX(p.i(Prefs.SB_X) * d);
        tv.setTranslationY(p.i(Prefs.SB_Y) * d);
        int col = p.i(Prefs.SB_COLOR);
        if (col != 0) tv.setTextColor(col);

        String pat = p.sbPattern();
        boolean two = pat != null && pat.contains("\n");
        tv.setSingleLine(!two);
        if (two) { tv.setMaxLines(2); tv.setLineSpacing(0f, 0.85f); }

        ViewParent par = tv.getParent();
        if (par instanceof ViewGroup) ((ViewGroup) par).setClipChildren(false);
        tv.requestLayout();
    }

    private static void startSeconds(final TextView tv) {
        if (!secLoops.add(tv)) return;
        tv.post(new Runnable() {
            @Override public void run() {
                Prefs p = prefs();
                String pat = p.sbPattern();
                if (!tv.isAttachedToWindow() || !p.b(Prefs.SB_ON) || pat == null || !pat.contains("s")) {
                    secLoops.remove(tv);
                    return;
                }
                if (tv.getWindowVisibility() == View.VISIBLE) {
                    try { XposedHelpers.callMethod(tv, "updateClock"); } catch (Throwable ignored) {}
                }
                tv.postDelayed(this, 1000 - System.currentTimeMillis() % 1000);
            }
        });
    }

    // =====================================================================
    //  Lock screen clock  (com.android.keyguard.KeyguardClockSwitch)
    // =====================================================================
    private void hookKeyguardClock(ClassLoader cl) {
        Class<?> ks = XposedHelpers.findClassIfExists("com.android.keyguard.KeyguardClockSwitch", cl);
        if (ks == null) { XposedBridge.log("ZClock: KeyguardClockSwitch not found"); return; }

        XposedBridge.hookAllMethods(ks, "onFinishInflate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                ViewGroup g = (ViewGroup) param.thisObject;
                ensureReceiver(g.getContext());
                switches.add(g);
                setupKeyguard(g);
            }
        });

        // Skip drawing of the stock clock views while a custom style is active
        XposedHelpers.findAndHookMethod(TextView.class, "onDraw", Canvas.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (HIDE_TAG.equals(((View) param.thisObject).getTag())) param.setResult(null);
            }
        });
    }

    private static View field(Object o, String name) {
        try { return (View) XposedHelpers.getObjectField(o, name); } catch (Throwable t) { return null; }
    }

    private static void setupKeyguard(ViewGroup g) {
        Prefs p = prefs();
        View[] stock = {field(g, "mClockView"), field(g, "mClockViewBold")};
        CustomClockView cv = (CustomClockView) g.findViewWithTag(CUSTOM_TAG);
        float d = g.getResources().getDisplayMetrics().density;
        int style = p.i(Prefs.LS_STYLE);

        if (!p.b(Prefs.LS_ON) || style <= 0) {
            if (cv != null) cv.setVisibility(View.GONE);
            for (View v : stock) {
                if (v == null) continue;
                v.setTag(null);
                setHeight(v, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (p.b(Prefs.LS_ON) && v instanceof TextView) tweakStock((TextView) v, p, d);
            }
            return;
        }

        int h = CustomClockView.heightPx(p, d);
        if (cv == null) {
            cv = new CustomClockView(g.getContext(), p);
            cv.setTag(CUSTOM_TAG);
            ViewGroup.LayoutParams lp;
            if (g instanceof RelativeLayout) {
                RelativeLayout.LayoutParams rl = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
                rl.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                lp = rl;
            } else {
                lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
            }
            g.addView(cv, lp);
        } else {
            cv.setPrefs(p);
            setHeight(cv, h);
        }
        cv.setVisibility(View.VISIBLE);
        cv.invalidate();

        for (View v : stock) {
            if (v == null) continue;
            v.setTag(HIDE_TAG);
            setHeight(v, h);   // keeps the date/status area positioned below our clock
        }
    }

    private static void tweakStock(TextView t, Prefs p, float d) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, p.i(Prefs.LS_SIZE) * 0.72f);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, p.i(Prefs.LS_WEIGHT), false));
        t.setScaleX(p.i(Prefs.LS_SX) / 100f);
        t.setScaleY(p.i(Prefs.LS_SY) / 100f);
        t.setTranslationX(p.i(Prefs.LS_X) * d);
        t.setTranslationY(p.i(Prefs.LS_Y) * d);
        t.setTextColor(p.i(Prefs.LS_COLOR));
    }

    private static void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null || lp.height == h) return;
        lp.height = h;
        v.setLayoutParams(lp);
    }

    // =====================================================================
    //  Live refresh from the settings app
    // =====================================================================
    private static synchronized void ensureReceiver(Context ctx) {
        if (receiverRegistered) return;
        try {
            ctx.getApplicationContext().registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { refreshAll(); }
            }, new IntentFilter(Prefs.ACTION_REFRESH));
            receiverRegistered = true;
        } catch (Throwable t) { XposedBridge.log("ZClock receiver: " + t); }
    }

    private static void refreshAll() {
        for (TextView tv : new ArrayList<>(clocks)) {
            sigs.remove(tv);
            applyClock(tv);
            try { XposedHelpers.callMethod(tv, "updateClock"); } catch (Throwable ignored) {}
            startSeconds(tv);
        }
        for (ViewGroup g : new ArrayList<>(switches)) {
            try { setupKeyguard(g); } catch (Throwable t) { XposedBridge.log("ZClock refresh: " + t); }
        }
    }
}
