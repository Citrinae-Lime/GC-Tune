package com.poc.gctune;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * First run onboarding: points the user to
 *   Ultra Game Mode -> Supported Games -> Other -> GC Tune
 * and jumps there automatically the very first time.
 */
public class SetupActivity extends Activity {

    private static final String PKG = "com.poc.gctune";
    private static final String GAME_PKG = "com.vivo.gamecube";

    private Handler h;
    private ContentResolver cr;
    private SharedPreferences prefs;

    private TextView statusText;
    private ImageView statusDot;
    private M3Icons.Icon iconDotOn, iconDotOff;

    private final Runnable autoJump = new Runnable() {
        @Override
        public void run() {
            if (!isGame()) openGameSettings();
        }
    };

    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            refreshStatus();
            h.postDelayed(this, 1200L);
        }
    };

    /** "Supported Games" screen of Game Cube (exported, verified on device). */
    static Intent gameSettingsIntent() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(GAME_PKG,
                GAME_PKG + ".GameCubeSettings$AddGameActivity"));
        return i;
    }

    /** Fallback: the "Ultra Game Mode" main screen. */
    static Intent gameMainIntent() {
        Intent i = new Intent();
        i.setComponent(new ComponentName(GAME_PKG, GAME_PKG + ".GameCubeMainActivity"));
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        h = new Handler(Looper.getMainLooper());
        cr = getContentResolver();
        prefs = getSharedPreferences("gctune_m3", MODE_PRIVATE);

        if (prefs.getBoolean("setup_done", false)) {
            goMain();
            finish();
            return;
        }

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);

        iconDotOn = M3Icons.dot(Ui.ACCENT, Ui.dp(this, 9));
        iconDotOff = M3Icons.dotHollow(Ui.MUTED, Ui.dp(this, 9));

        buildUi();
        refreshStatus();

        if (!prefs.getBoolean("auto_jumped", false)) {
            prefs.edit().putBoolean("auto_jumped", true).apply();
            h.postDelayed(autoJump, 1500L);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        h.postDelayed(statusTicker, 600L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(statusTicker);
        h.removeCallbacks(autoJump);
    }

    // ------------------------------------------------------------------ UI
    private void buildUi() {
        LinearLayout root = Ui.col(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 24), Ui.dp(this, 32));

        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        ImageView mark = new ImageView(this);
        mark.setImageDrawable(M3Icons.terminal(Ui.ACCENT, Ui.dp(this, 44)));
        mark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams markLp = new LinearLayout.LayoutParams(-2, -2);
        markLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(mark, markLp);

        TextView title = Ui.label(this, getString(R.string.app_name), 28f, Ui.TEXT);
        title.setLetterSpacing(-0.01f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-2, -2);
        titleLp.gravity = Gravity.CENTER_HORIZONTAL;
        titleLp.topMargin = Ui.dp(this, 16);
        root.addView(title, titleLp);

        LinearLayout statusRow = Ui.row(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setBackground(Ui.pillShape(this, Ui.SURFACE_HIGH, Ui.BORDER_SOFT, 1.2f));
        statusRow.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        statusDot = new ImageView(this);
        statusDot.setImageDrawable(iconDotOff);
        statusDot.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(-2, -2);
        dotLp.rightMargin = Ui.dp(this, 7);
        statusRow.addView(statusDot, dotLp);
        statusText = Ui.text(this, "", 11.5f, Ui.MUTED);
        statusRow.addView(statusText);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-2, -2);
        statusLp.gravity = Gravity.CENTER_HORIZONTAL;
        statusLp.topMargin = Ui.dp(this, 12);
        root.addView(statusRow, statusLp);

        TextView path = Ui.text(this, getString(R.string.setup_guide_path), 11.5f, Ui.MUTED);
        path.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(-2, -2);
        pathLp.gravity = Gravity.CENTER_HORIZONTAL;
        pathLp.topMargin = Ui.dp(this, 8);
        pathLp.bottomMargin = Ui.dp(this, 30);
        root.addView(path, pathLp);

        root.addView(settingsButton());

        TextView later = Ui.label(this, getString(R.string.setup_open_app), 13.5f, Ui.MUTED);
        later.setGravity(Gravity.CENTER);
        later.setPadding(Ui.dp(this, 20), Ui.dp(this, 12), Ui.dp(this, 20), Ui.dp(this, 12));
        later.setBackground(Ui.pillRipple(this, 0x00000000, 0x306CE5A8));
        LinearLayout.LayoutParams laterLp = new LinearLayout.LayoutParams(-2, -2);
        laterLp.gravity = Gravity.CENTER_HORIZONTAL;
        laterLp.topMargin = Ui.dp(this, 12);
        later.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.tap(v); goMain(); }
        });
        root.addView(later, laterLp);

        root.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1f));

        setContentView(root);
    }

    private View settingsButton() {
        LinearLayout btn = Ui.row(this);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(Ui.pillRipple(this, Ui.PRIMARY, 0x40FFFFFF));
        btn.setPadding(Ui.dp(this, 22), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        btn.setElevation(Ui.dp(this, 3));

        TextView t = Ui.label(this, getString(R.string.game_cube_supported_games), 14.5f, Ui.ACCENT);
        btn.addView(t);

        ImageView chevron = new ImageView(this);
        chevron.setImageDrawable(M3Icons.chevron(Ui.ACCENT, Ui.dp(this, 18)));
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(-2, -2);
        chLp.leftMargin = Ui.dp(this, 8);
        btn.addView(chevron, chLp);

        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Ui.tap(v); openGameSettings(); }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        btn.setLayoutParams(lp);
        return btn;
    }

    // -------------------------------------------------------------- actions
    private void openGameSettings() {
        try {
            startActivity(gameSettingsIntent());
        } catch (Throwable t) {
            try {
                startActivity(gameMainIntent());
            } catch (Throwable t2) {
                Toast.makeText(this, getString(R.string.setup_open_settings_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goMain() {
        startActivity(new Intent(this, Main.class));
        finish();
    }

    private boolean isGame() {
        try {
            return PKG.equals(Settings.System.getString(cr, "current_game_package"));
        } catch (Throwable t) {
            return false;
        }
    }

    private void refreshStatus() {
        if (statusDot == null) return;
        if (isGame()) {
            statusDot.setImageDrawable(iconDotOn);
            statusText.setText(getString(R.string.status_in_game_list));
            statusText.setTextColor(Ui.ACCENT);
        } else {
            statusDot.setImageDrawable(iconDotOff);
            statusText.setText(getString(R.string.status_not_in_game_list));
            statusText.setTextColor(Ui.MUTED);
        }
    }
}
