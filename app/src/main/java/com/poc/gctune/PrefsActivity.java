package com.poc.gctune;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Minimal settings screen. */
/*
 * =========================================================================================
 * EXTENSION:
 * Integrated language selection preference (LocaleHelper) allowing users to switch
 * languages directly in-app or jump to system Per-App Language settings (Android 13+ / 14+).
 * =========================================================================================
 */
public class PrefsActivity extends Activity {

    private SharedPreferences prefs;
    private EditText timeoutInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        prefs = getSharedPreferences("gctune_m3", MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        storeTimeout();
    }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Ui.BG);

        LinearLayout root = Ui.col(this);
        root.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 28));
        scroll.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -2));

        root.addView(header());
        root.addView(switchRow());
        root.addView(daemonSwitchRow());
        root.addView(timeoutRow());
        root.addView(languageRow());
        root.addView(navRow(getString(R.string.game_cube_supported_games), new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(SetupActivity.gameSettingsIntent());
                } catch (Throwable t) {
                    try { startActivity(SetupActivity.gameMainIntent()); } catch (Throwable ignored) {}
                }
            }
        }));
        root.addView(navRow(getString(R.string.prefs_first_run), new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean("setup_done", false).apply();
                startActivity(new Intent(PrefsActivity.this, SetupActivity.class));
                finish();
            }
        }));

        TextView about = Ui.text(this, getString(R.string.prefs_about), 11f, Ui.MUTED);
        about.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams aboutLp = new LinearLayout.LayoutParams(-2, -2);
        aboutLp.gravity = Gravity.CENTER_HORIZONTAL;
        aboutLp.topMargin = Ui.dp(this, 24);
        root.addView(about, aboutLp);

        setContentView(scroll);
    }

    private View header() {
        LinearLayout row = Ui.row(this);

        ImageView back = new ImageView(this);
        back.setImageDrawable(M3Icons.arrowBack(Ui.TEXT, Ui.dp(this, 20)));
        back.setScaleType(ImageView.ScaleType.FIT_CENTER);
        back.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        row.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));

        TextView title = Ui.label(this, getString(R.string.nav_settings), 20f, Ui.TEXT);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.leftMargin = Ui.dp(this, 10);
        row.addView(title, titleLp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 12);
        row.setLayoutParams(lp);
        return row;
    }

    private View switchRow() {
        LinearLayout row = rowBase();

        TextView t = Ui.text(this, getString(R.string.prefs_require_game), 14f, Ui.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean("require_game", true));
        sw.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, Ui.MUTED}));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.PRIMARY, 0x553D7D56}));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton btn, boolean checked) {
                prefs.edit().putBoolean("require_game", checked).apply();
            }
        });
        row.addView(sw);
        return row;
    }

    private View daemonSwitchRow() {
        LinearLayout row = rowBase();

        TextView t = Ui.text(this, getString(R.string.prefs_daemon), 14f, Ui.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(prefs.getBoolean("daemon_on", true));
        sw.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.ACCENT, Ui.MUTED}));
        sw.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_checked}, new int[]{}},
                new int[]{Ui.PRIMARY, 0x553D7D56}));
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton btn, boolean checked) {
                prefs.edit().putBoolean("daemon_on", checked).apply();
            }
        });
        row.addView(sw);
        return row;
    }

    private View timeoutRow() {
        LinearLayout row = rowBase();

        TextView t = Ui.text(this, getString(R.string.prefs_timeout), 14f, Ui.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        timeoutInput = new EditText(this);
        timeoutInput.setText(String.valueOf(prefs.getInt("timeout_sec", 60)));
        timeoutInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        timeoutInput.setTextSize(13f);
        timeoutInput.setTextColor(Ui.TEXT);
        timeoutInput.setGravity(Gravity.CENTER);
        timeoutInput.setBackground(Ui.shape(this, Ui.INPUT_BG, 8f, Ui.BORDER, 1f));
        timeoutInput.setPadding(Ui.dp(this, 8), Ui.dp(this, 5), Ui.dp(this, 8), Ui.dp(this, 5));
        timeoutInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { storeTimeout(); }
        });
        row.addView(timeoutInput, new LinearLayout.LayoutParams(Ui.dp(this, 64), -2));
        return row;
    }

    private View languageRow() {
        LinearLayout row = rowBase();

        LinearLayout col = Ui.col(this);
        col.addView(Ui.text(this, getString(R.string.prefs_language), 14f, Ui.TEXT));
        TextView sub = Ui.text(this, LocaleHelper.getLanguageDisplayName(this), 11.5f, Ui.MUTED);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
        subLp.topMargin = Ui.dp(this, 2);
        col.addView(sub, subLp);
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(M3Icons.chevron(Ui.MUTED, Ui.dp(this, 18)));
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.tap(v);
                LocaleHelper.showLanguageDialog(PrefsActivity.this, new Runnable() {
                    @Override public void run() {
                        recreate();
                    }
                });
            }
        });
        row.setClickable(true);
        return row;
    }

    private View navRow(String label, View.OnClickListener onClick) {
        LinearLayout row = rowBase();

        TextView t = Ui.text(this, label, 14f, Ui.TEXT);
        row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(M3Icons.chevron(Ui.MUTED, Ui.dp(this, 18)));
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(chevron, new LinearLayout.LayoutParams(-2, -2));

        row.setOnClickListener(onClick);
        row.setClickable(true);
        return row;
    }

    private LinearLayout rowBase() {
        LinearLayout row = Ui.row(this);
        row.setPadding(0, Ui.dp(this, 15), 0, Ui.dp(this, 15));
        row.setMinimumHeight(Ui.dp(this, 30));
        return row;
    }

    private void storeTimeout() {
        if (timeoutInput == null) return;
        int v;
        try {
            v = Integer.parseInt(timeoutInput.getText().toString().trim());
        } catch (Exception e) {
            return;
        }
        if (v < 3) v = 3;
        if (v > 90) v = 90;
        prefs.edit().putInt("timeout_sec", v).apply();
    }
}
