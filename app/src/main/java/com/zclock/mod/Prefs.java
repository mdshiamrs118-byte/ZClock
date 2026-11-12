package com.zclock.mod;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;

/** Thin wrapper so the same code works with SharedPreferences (app) and XSharedPreferences (SystemUI). */
public final class Prefs {
    public static final String PKG = "com.zclock.mod";
    public static final String NAME = "zclock_prefs";
    public static final String ACTION_REFRESH = "com.zclock.mod.REFRESH";

    // ---- Status bar clock ----
    public static final String SB_ON = "sb_on", SB_STYLE = "sb_style", SB_FORMAT = "sb_format",
            SB_SIZE = "sb_size", SB_WEIGHT = "sb_weight", SB_SX = "sb_sx", SB_SY = "sb_sy",
            SB_X = "sb_x", SB_Y = "sb_y", SB_SPACING = "sb_spacing", SB_COLOR = "sb_color";
    public static final String[] SB_KEYS = {SB_ON, SB_STYLE, SB_FORMAT, SB_SIZE, SB_WEIGHT, SB_SX,
            SB_SY, SB_X, SB_Y, SB_SPACING, SB_COLOR};

    // ---- Lock screen clock ----
    public static final String LS_ON = "ls_on", LS_STYLE = "ls_style", LS_SIZE = "ls_size",
            LS_WEIGHT = "ls_weight", LS_SX = "ls_sx", LS_SY = "ls_sy", LS_X = "ls_x", LS_Y = "ls_y",
            LS_COLOR = "ls_color", LS_RECOLOR = "ls_recolor", LS_PUSH = "ls_push", LS_HIDE_DATE = "ls_hide_date", LS_24H = "ls_24h", LS_DATE = "ls_date", LS_SEC = "ls_sec";

    /** Styles 0-7 are built in; styles from ICONIFY_BASE on are the ported Iconify layouts. */
    public static final int ICONIFY_BASE = 8, ICONIFY_COUNT = 60;
    public static final String[] LS_STYLES = buildLsStyles();

    private static String[] buildLsStyles() {
        String[] base = {"Stock clock (modified)", "Stacked digital", "Bold + thin", "Analog classic",
                "Analog minimal", "Binary dots", "Progress ring", "Outline"};
        String[] all = new String[ICONIFY_BASE + ICONIFY_COUNT];
        System.arraycopy(base, 0, all, 0, base.length);
        for (int i = 0; i < ICONIFY_COUNT; i++) all[ICONIFY_BASE + i] = "Iconify " + (i + 1);
        return all;
    }

    public static final String[] SB_STYLES = {
            "System default", "24-hour  (14:05)", "12-hour + AM/PM  (2:05 PM)", "12-hour  (2:05)",
            "24-hour + seconds", "12-hour + seconds", "Day + 24-hour  (Thu 14:05)",
            "Day, date + time", "Date + 24-hour  (12 Nov 14:05)", "Stacked (two lines)",
            "Custom pattern"};
    private static final String[] SB_PATTERNS = {null, "HH:mm", "h:mm a", "h:mm", "HH:mm:ss",
            "h:mm:ss a", "EEE HH:mm", "EEE, MMM d  h:mm a", "d MMM  HH:mm", "HH\nmm", null};

    private static final Map<String, Integer> DEF = new HashMap<>();
    static {
        DEF.put(SB_ON, 1); DEF.put(SB_STYLE, 0); DEF.put(SB_SIZE, 0); DEF.put(SB_WEIGHT, 0);
        DEF.put(SB_SX, 100); DEF.put(SB_SY, 100); DEF.put(SB_X, 0); DEF.put(SB_Y, 0);
        DEF.put(SB_SPACING, 0); DEF.put(SB_COLOR, 0);
        DEF.put(LS_ON, 1); DEF.put(LS_STYLE, 1); DEF.put(LS_SIZE, 120); DEF.put(LS_WEIGHT, 600);
        DEF.put(LS_SX, 100); DEF.put(LS_SY, 100); DEF.put(LS_X, 0); DEF.put(LS_Y, 0);
        DEF.put(LS_COLOR, 0xFFFFFFFF); DEF.put(LS_RECOLOR, 0); DEF.put(LS_PUSH, 1); DEF.put(LS_HIDE_DATE, 0); DEF.put(LS_24H, 1); DEF.put(LS_DATE, 1); DEF.put(LS_SEC, 0);
    }

    private final SharedPreferences sp;
    public Prefs(SharedPreferences sp) { this.sp = sp; }

    public int i(String k) { Integer d = DEF.get(k); return sp.getInt(k, d == null ? 0 : d); }
    public boolean b(String k) { Integer d = DEF.get(k); return sp.getBoolean(k, d != null && d == 1); }
    public String s(String k, String d) { return sp.getString(k, d); }

    /**
     * "Move notifications with the clock": instead of only shifting the drawing, the clock's layout
     * height grows/shrinks by the Y offset, so everything below it (date, notifications) follows.
     */
    public int layoutH(int base, float density) {
        if (!b(LS_PUSH)) return base;
        return Math.max(1, base + Math.round(i(LS_Y) * density));
    }

    /** Translation that keeps the drawn clock at (base/2 + Y) whatever layout height is used. */
    public float transY(int base, int layout, float density) {
        float y = i(LS_Y) * density;
        if (!b(LS_PUSH)) return y;
        return base / 2f + y - layout / 2f;
    }

    public String sig(String[] keys) {
        Map<String, ?> all = sp.getAll();
        StringBuilder sb = new StringBuilder();
        for (String k : keys) sb.append(all.get(k)).append('|');
        return sb.toString();
    }

    /** Pattern for the status bar clock, or null for "system default". */
    public String sbPattern() {
        int idx = i(SB_STYLE);
        if (idx >= SB_PATTERNS.length - 1) return s(SB_FORMAT, "HH:mm").replace("\\n", "\n");
        if (idx <= 0) return null;
        return SB_PATTERNS[idx];
    }
}
