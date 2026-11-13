package com.zclock.mod;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.lang.ref.WeakReference;
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
    private static final String ICONIFY_TAG = "zclock_iconify";

    private static XSharedPreferences xp;
    private static boolean receiverRegistered;
    private static WeakReference<Object> panelRef;
    private static final Set<TextView> clocks = Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>());
    private static final Set<TextView> secLoops = Collections.newSetFromMap(new WeakHashMap<TextView, Boolean>());
    private static final Set<ViewGroup> slices = Collections.newSetFromMap(new WeakHashMap<ViewGroup, Boolean>());
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

        hookNotificationPosition(cl);

        // Stock lock screen date (KeyguardSliceView): optionally hide it
        Class<?> slice = XposedHelpers.findClassIfExists("com.android.keyguard.KeyguardSliceView", cl);
        if (slice != null) {
            XC_MethodHook dateHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    ViewGroup g = (ViewGroup) param.thisObject;
                    ensureReceiver(g.getContext());
                    slices.add(g);
                    applyDateVisibility(g);
                }
            };
            XposedBridge.hookAllMethods(slice, "onFinishInflate", dateHook);
            XposedBridge.hookAllMethods(slice, "showSlice", dateHook);
            XposedBridge.hookAllMethods(slice, "onMeasure", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    ViewGroup g = (ViewGroup) param.thisObject;
                    slices.add(g);
                    applyDateVisibility(g);
                }
            });
            // the date cell lives in the inner Row, which does override onMeasure
            Class<?> row = XposedHelpers.findClassIfExists("com.android.keyguard.KeyguardSliceView$Row", cl);
            if (row != null) {
                XposedBridge.hookAllMethods(row, "onMeasure", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        applyDateVisibility((ViewGroup) param.thisObject);
                    }
                });
            }
        } else {
            XposedBridge.log("ZClock: KeyguardSliceView not found");
        }

        // Skip drawing of the stock clock views while a custom style is active
        XposedHelpers.findAndHookMethod(TextView.class, "onDraw", Canvas.class, new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (HIDE_TAG.equals(((View) param.thisObject).getTag())) param.setResult(null);
            }
        });
    }

    /**
     * On Android 11 the date is NOT the "title": it is a cell inside the slice's row, tagged with its
     * Uri (content://com.android.systemui.keyguard/date). We match by that tag and, as a fallback,
     * by the text itself, then hide/show it.
     */
    /**
     * The notification list on the lock screen starts at stackScrollerPadding, computed by
     * KeyguardClockPositionAlgorithm.run(). We add our offset to that result.
     */
    private void hookNotificationPosition(ClassLoader cl) {
        Class<?> alg = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.KeyguardClockPositionAlgorithm", cl);
        if (alg == null) { XposedBridge.log("ZClock: KeyguardClockPositionAlgorithm not found"); }
        else {
            XposedBridge.hookAllMethods(alg, "run", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args == null || param.args.length == 0 || param.args[0] == null) return;
                        int px = prefs().notifOffsetPx(Resources.getSystem().getDisplayMetrics().density);
                        if (px == 0) return;
                        Object r = param.args[0];
                        for (String n : new String[]{"stackScrollerPadding", "stackScrollerPaddingExpanded"}) {
                            try {
                                XposedHelpers.setIntField(r, n, Math.max(0, XposedHelpers.getIntField(r, n) + px));
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable t) { XposedBridge.log("ZClock notif: " + t); }
                }
            });
        }
        // remember the panel so a settings change can re-run the positioning immediately
        Class<?> panel = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.NotificationPanelViewController", cl);
        if (panel != null) {
            XposedBridge.hookAllMethods(panel, "positionClockAndNotifications", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    panelRef = new WeakReference<>(param.thisObject);
                }
            });
        }
    }

    private static void applyDateVisibility(ViewGroup g) {
        try {
            boolean hide = prefs().b(Prefs.LS_HIDE_DATE);
            String today = null;
            try {
                java.util.Locale loc = java.util.Locale.getDefault();
                today = new SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEEMMMd"), loc)
                        .format(new Date());
            } catch (Throwable ignored) {}
            setDateViews(g, hide, today);
        } catch (Throwable t) { XposedBridge.log("ZClock date: " + t); }
    }

    private static void setDateViews(View v, boolean hide, String today) {
        if (v instanceof TextView) {
            Object tag = v.getTag();
            CharSequence text = ((TextView) v).getText();
            boolean isDate = (tag != null && tag.toString().toLowerCase().endsWith("/date"))
                    || (today != null && text != null && today.contentEquals(text));
            if (isDate) {
                int want = hide ? View.GONE : View.VISIBLE;
                if (v.getVisibility() != want && (hide || text.length() > 0)) v.setVisibility(want);
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) setDateViews(vg.getChildAt(i), hide, today);
        }
    }

    /** Keeps the date/alarm row under the clock when "notifications follow the clock" is on. */
    private static void moveStatusArea(ViewGroup g, Prefs p, float d) {
        View area = field(g, "mKeyguardStatusArea");
        if (area == null) area = field(g, "mStatusArea");
        if (area == null) {
            int id = g.getResources().getIdentifier("keyguard_status_area", "id", "com.android.systemui");
            if (id != 0) area = g.findViewById(id);
        }
        if (area != null) {
            area.setTranslationY(p.b(Prefs.LS_ON) && p.b(Prefs.LS_PUSH) ? p.i(Prefs.LS_Y) * d : 0f);
        }
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
        moveStatusArea(g, p, d);

        if (!p.b(Prefs.LS_ON) || style <= 0) {
            if (cv != null) cv.setVisibility(View.GONE);
            View ic = g.findViewWithTag(ICONIFY_TAG);
            if (ic != null) ic.setVisibility(View.GONE);
            for (View v : stock) {
                if (v == null) continue;
                v.setTag(null);
                setHeight(v, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (p.b(Prefs.LS_ON) && v instanceof TextView) tweakStock((TextView) v, p, d);
            }
            return;
        }

        // h  = the clock view's own height (never shrunk, so nothing gets clipped)
        // lh = the height the layout reserves for it (moves the date + notifications)
        int h, lh;
        IconifyClockView iv = (IconifyClockView) g.findViewWithTag(ICONIFY_TAG);
        if (style >= Prefs.ICONIFY_BASE) {
            // ported Iconify layout
            if (cv != null) cv.setVisibility(View.GONE);
            if (iv == null) {
                iv = new IconifyClockView(g.getContext(), p);
                iv.setTag(ICONIFY_TAG);
                iv.unclipAncestors = true;
                place(g, iv, iv.targetHeight(), iv.layoutHeight(), true);
            } else {
                iv.apply(p);
                place(g, iv, iv.targetHeight(), iv.layoutHeight(), false);
            }
            h = iv.targetHeight();
            lh = iv.layoutHeight();
            iv.setVisibility(View.VISIBLE);
        } else {
            // built-in canvas styles
            if (iv != null) iv.setVisibility(View.GONE);
            h = CustomClockView.heightPx(p, d);
            lh = CustomClockView.layoutHeightPx(p, d);
            if (cv == null) {
                cv = new CustomClockView(g.getContext(), p);
                cv.setTag(CUSTOM_TAG);
                cv.unclipAncestors = true;
                place(g, cv, h, lh, true);
            } else {
                cv.setPrefs(p);
                place(g, cv, h, lh, false);
            }
            cv.setVisibility(View.VISIBLE);
            cv.invalidate();
        }

        for (View v : stock) {
            if (v == null) continue;
            v.setTag(HIDE_TAG);
            setHeight(v, lh);   // the stock views decide how much room the date/notifications get
        }
    }

    /**
     * Puts our clock view at the top of the switch with its full height h, while a (possibly negative)
     * bottom margin makes the parent measure only lh. That moves everything below the clock without
     * shrinking the clock view itself, so its drawing is never clipped.
     */
    private static void place(ViewGroup g, View v, int h, int lh, boolean add) {
        ViewGroup.LayoutParams lp;
        if (add) {
            if (g instanceof RelativeLayout) {
                RelativeLayout.LayoutParams rl = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
                rl.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                lp = rl;
            } else {
                lp = new ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h);
            }
        } else {
            lp = v.getLayoutParams();
            if (lp == null) return;
            lp.height = h;
        }
        if (lp instanceof ViewGroup.MarginLayoutParams) ((ViewGroup.MarginLayoutParams) lp).bottomMargin = lh - h;
        if (add) g.addView(v, lp); else v.setLayoutParams(lp);
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
        for (ViewGroup g : new ArrayList<>(slices)) applyDateVisibility(g);
        try {
            Object panel = panelRef == null ? null : panelRef.get();
            if (panel != null) XposedHelpers.callMethod(panel, "positionClockAndNotifications");
        } catch (Throwable ignored) {}
        for (ViewGroup g : new ArrayList<>(switches)) {
            try { setupKeyguard(g); } catch (Throwable t) { XposedBridge.log("ZClock refresh: " + t); }
        }
    }
}
