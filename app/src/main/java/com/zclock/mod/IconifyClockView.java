package com.zclock.mod;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Hosts one of the 60 ported Iconify lock screen clock layouts (res/layout/zclock_ls_N.xml).
 * Layouts are inflated with the module's own resources, so it also works inside SystemUI.
 * Layouts and assets originate from Iconify (https://github.com/Mahmud0808/Iconify), GPL-3.0.
 */
public class IconifyClockView extends FrameLayout {
    private final Context modCtx;
    private Prefs prefs;
    private View content;
    private String key = "";
    private int targetH = 1;

    /** When true, parents stop clipping their children so the clock can be moved anywhere. */
    public boolean unclipAncestors;

    public IconifyClockView(Context host, Prefs prefs) {
        super(host);
        setClipChildren(false);
        setClipToPadding(false);
        modCtx = moduleContext(host);
        apply(prefs);
    }

    private static Context moduleContext(Context c) {
        if (Prefs.PKG.equals(c.getPackageName())) return c;
        try { return c.createPackageContext(Prefs.PKG, Context.CONTEXT_IGNORE_SECURITY); }
        catch (Throwable t) { return c; }
    }

    public int targetHeight() { return targetH; }

    public void apply(Prefs p) {
        this.prefs = p;
        int n = p.i(Prefs.LS_STYLE) - Prefs.ICONIFY_BASE + 1;
        boolean recolor = p.b(Prefs.LS_RECOLOR);
        String k = n + "|" + recolor + "|" + (recolor ? p.i(Prefs.LS_COLOR) : 0);
        if (!k.equals(key) || content == null) {
            key = k;
            inflate(n, recolor ? p.i(Prefs.LS_COLOR) : 0, recolor);
        }
        if (content == null) { targetH = 1; return; }

        float d = getResources().getDisplayMetrics().density;
        float base = p.i(Prefs.LS_SIZE) / 120f;
        float sx = base * p.i(Prefs.LS_SX) / 100f, sy = base * p.i(Prefs.LS_SY) / 100f;
        int sw = getResources().getDisplayMetrics().widthPixels;
        content.measure(View.MeasureSpec.makeMeasureSpec(sw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int nat = Math.max(1, Math.round(content.getMeasuredHeight() * sy));
        targetH = p.layoutH(nat, d);
        content.setScaleX(sx);
        content.setScaleY(sy);
        setTranslationX(p.i(Prefs.LS_X) * d);
        setTranslationY(p.transY(nat, targetH, d));

        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && lp.height != targetH) { lp.height = targetH; setLayoutParams(lp); }
    }

    private void inflate(int n, int color, boolean recolor) {
        removeAllViews();
        content = null;
        if (n < 1) return;
        try {
            int id = modCtx.getResources().getIdentifier("zclock_ls_" + n, "layout", Prefs.PKG);
            if (id == 0) return;
            View v = LayoutInflater.from(modCtx).inflate(id, null);
            if (recolor) recolor(v, color);
            addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            content = v;
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static void recolor(View v, int color) {
        if (v instanceof TextView) ((TextView) v).setTextColor(color);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) recolor(g.getChildAt(i), color);
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!unclipAncestors) return;
        ViewParent par = getParent();
        for (int i = 0; i < 10 && par instanceof ViewGroup; i++) {
            ViewGroup vg = (ViewGroup) par;
            vg.setClipChildren(false);
            vg.setClipToPadding(false);
            par = vg.getParent();
        }
    }
}
