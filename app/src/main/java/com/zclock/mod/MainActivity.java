package com.zclock.mod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private Prefs prefs;
    private CustomClockView preview;
    private IconifyClockView ipreview;
    private LinearLayout root;

    /** Hooked by the module (returns true when LSPosed has injected us). */
    static boolean isModuleActive() { return false; }

    @SuppressWarnings({"deprecation", "WorldReadableFiles"})
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try { sp = getSharedPreferences(Prefs.NAME, MODE_WORLD_READABLE); }
        catch (SecurityException e) { sp = getSharedPreferences(Prefs.NAME, MODE_PRIVATE); }
        prefs = new Prefs(sp);

        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(32), dp(16), dp(32));
        sv.addView(root);
        setContentView(sv);

        TextView title = new TextView(this);
        title.setText("ZClock");
        title.setTextSize(26);
        root.addView(title);

        TextView status = new TextView(this);
        status.setText(isModuleActive()
                ? "✔ Module is active"
                : "✘ Module not active. Enable it in LSPosed, tick \"System UI\" as scope, then restart System UI.");
        status.setPadding(0, dp(4), 0, dp(12));
        root.addView(status);

        // ---- live preview of the lock-screen clock ----
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(0xFF202124);
        box.setClipChildren(false);
        preview = new CustomClockView(this, prefs);
        box.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                CustomClockView.heightPx(prefs, getResources().getDisplayMetrics().density), Gravity.CENTER));
        ipreview = new IconifyClockView(this, prefs);
        box.addView(ipreview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)));
        TextView hint = new TextView(this);
        hint.setText("Preview of the lock screen clock (\"Stock clock\" style is previewed as Stacked). Iconify styles keep their own fonts and colours.");
        hint.setTextSize(12);
        root.addView(hint);

        // ---- Lock screen ----
        header("Lock screen clock");
        addSwitch("Enable", Prefs.LS_ON);
        addSpinner("Style", Prefs.LS_STYLE, Prefs.LS_STYLES);
        addSeek("Size / height (dp)", Prefs.LS_SIZE, 40, 320, " dp");
        addSeek("Font weight", Prefs.LS_WEIGHT, 100, 900, "");
        addSeek("Stretch width", Prefs.LS_SX, 40, 200, " %");
        addSeek("Stretch height", Prefs.LS_SY, 40, 200, " %");
        addSeek("Position X", Prefs.LS_X, -250, 250, " dp");
        addSeek("Position Y", Prefs.LS_Y, -250, 250, " dp");
        addColor("Colour", Prefs.LS_COLOR, false);
        addSwitch("Recolour Iconify clocks with this colour", Prefs.LS_RECOLOR);
        addSwitch("24-hour time", Prefs.LS_24H);
        addSwitch("Show date", Prefs.LS_DATE);
        addSwitch("Show seconds (analog / binary / ring)", Prefs.LS_SEC);

        // ---- Status bar ----
        header("Status bar clock");
        addSwitch("Enable", Prefs.SB_ON);
        addSpinner("Format", Prefs.SB_STYLE, Prefs.SB_STYLES);
        addText("Custom pattern (used with \"Custom pattern\", e.g. EEE h:mm a, \\n = new line)", Prefs.SB_FORMAT, "HH:mm");
        addSeek("Text size (0 = system)", Prefs.SB_SIZE, 0, 40, " sp");
        addSeek("Font weight (0 = system)", Prefs.SB_WEIGHT, 0, 900, "");
        addSeek("Stretch width", Prefs.SB_SX, 50, 250, " %");
        addSeek("Stretch height", Prefs.SB_SY, 50, 250, " %");
        addSeek("Position X", Prefs.SB_X, -60, 60, " dp");
        addSeek("Position Y", Prefs.SB_Y, -30, 30, " dp");
        addSeek("Letter spacing", Prefs.SB_SPACING, -10, 40, " /100 em");
        addColor("Colour (system = follows status bar)", Prefs.SB_COLOR, true);

        header("Tools");
        Button restart = new Button(this);
        restart.setText("Restart System UI (needs root)");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.android.systemui"}); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "No root. Reboot or restart System UI from LSPosed.", Toast.LENGTH_LONG).show(); }
            }
        });
        root.addView(restart);
        Button reset = new Button(this);
        reset.setText("Reset all settings");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().clear().commit();
                sendRefresh();
                recreate();
            }
        });
        root.addView(reset);
        changed();
    }

    // ---------- helpers ----------
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void sendRefresh() { sendBroadcast(new Intent(Prefs.ACTION_REFRESH)); }

    private void changed() {
        boolean iconify = prefs.i(Prefs.LS_STYLE) >= Prefs.ICONIFY_BASE;
        preview.setVisibility(iconify ? View.GONE : View.VISIBLE);
        ipreview.setVisibility(iconify ? View.VISIBLE : View.GONE);
        if (iconify) {
            ipreview.apply(prefs);
            ViewGroup.LayoutParams lp = ipreview.getLayoutParams();
            lp.height = ipreview.targetHeight();
            ipreview.setLayoutParams(lp);
        } else {
            ViewGroup.LayoutParams lp = preview.getLayoutParams();
            lp.height = CustomClockView.heightPx(prefs, getResources().getDisplayMetrics().density);
            preview.setLayoutParams(lp);
            preview.setPrefs(prefs);
        }
    }

    private void header(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(18);
        tv.setPadding(0, dp(24), 0, dp(6));
        root.addView(tv);
    }

    private void addSwitch(String label, final String key) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setChecked(prefs.b(key));
        s.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean c) {
                sp.edit().putBoolean(key, c).commit();
                changed();
                sendRefresh();
            }
        });
        s.setPadding(0, dp(8), 0, dp(8));
        root.addView(s);
    }

    private void addSpinner(String label, final String key, String[] items) {
        TextView tv = new TextView(this);
        tv.setText(label);
        root.addView(tv);
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items));
        s.setSelection(Math.max(0, Math.min(items.length - 1, prefs.i(key))));
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, View v, int pos, long id) {
                if (pos == prefs.i(key)) return;
                sp.edit().putInt(key, pos).commit();
                changed();
                sendRefresh();
            }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });
        root.addView(s);
    }

    private void addSeek(final String label, final String key, final int min, int max, final String unit) {
        final TextView tv = new TextView(this);
        tv.setText(label + ": " + prefs.i(key) + unit);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(prefs.i(key) - min);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean fromUser) {
                if (!fromUser) return;
                int v = prog + min;
                tv.setText(label + ": " + v + unit);
                sp.edit().putInt(key, v).apply();
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {
                sp.edit().putInt(key, s.getProgress() + min).commit();
                sendRefresh();
            }
        });
        root.addView(sb);
    }

    private void addText(String label, final String key, String def) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setPadding(0, dp(8), 0, 0);
        root.addView(tv);
        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setText(prefs.s(key, def));
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable e) {
                sp.edit().putString(key, e.toString()).commit();
                sendRefresh();
            }
        });
        root.addView(et);
    }

    private void addColor(final String label, final String key, final boolean allowNone) {
        final Button btn = new Button(this);
        btn.setAllCaps(false);
        final Runnable refresh = new Runnable() {
            @Override public void run() {
                int c = prefs.i(key);
                btn.setText(label + ": " + (c == 0 && allowNone ? "system" : String.format("#%08X", c)));
                if (c != 0) btn.setTextColor(Color.alpha(c) > 0 && Color.luminance(c) > 0.5f ? Color.BLACK : Color.WHITE);
                if (c != 0) btn.setBackgroundColor(c);
            }
        };
        refresh.run();
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { colorDialog(key, allowNone, refresh); }
        });
        root.addView(btn);
    }

    private void colorDialog(final String key, boolean allowNone, final Runnable done) {
        int[] presets = {0xFFFFFFFF, 0xFF000000, 0xFFFF5252, 0xFFFFB74D, 0xFFFFEE58, 0xFF69F0AE, 0xFF40C4FF, 0xFFB388FF, 0xFFFF80AB};
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(8), dp(16), 0);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        col.addView(hs);
        final EditText hex = new EditText(this);
        hex.setHint("#RRGGBB or #AARRGGBB");
        hex.setSingleLine(true);
        col.addView(hex);

        final AlertDialog.Builder ab = new AlertDialog.Builder(this).setTitle("Colour").setView(col);
        final int[] chosen = {Integer.MIN_VALUE};
        for (final int c : presets) {
            View sw = new View(this);
            sw.setBackgroundColor(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            sw.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    hex.setText(String.format("#%08X", c));
                    chosen[0] = c;
                }
            });
            row.addView(sw, lp);
        }
        ab.setPositiveButton("Set", (d, w) -> {
            try {
                int c = Color.parseColor(hex.getText().toString().trim());
                sp.edit().putInt(key, c).commit();
                done.run(); changed(); sendRefresh();
            } catch (Exception e) { Toast.makeText(this, "Invalid colour", Toast.LENGTH_SHORT).show(); }
        });
        if (allowNone) ab.setNeutralButton("System", (d, w) -> {
            sp.edit().putInt(key, 0).commit();
            done.run(); changed(); sendRefresh();
        });
        ab.setNegativeButton("Cancel", null).show();
    }
}
