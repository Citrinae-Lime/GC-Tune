package com.poc.gctune;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import android.os.PowerManager;

/**
 * Background survival service for the "Неубиваемый фон" toggle.
 *
 * Started as a foreground service with a MIN-importance ongoing notification
 * (invisible in doze, honest about what is running) so the process sits at
 * foreground-service oom_adj instead of the cached bucket. Combined stack:
 *
 *  - START_STICKY: the system restarts the service after a low-memory kill
 *  - onTaskRemoved: swipe-out schedules a 2 s watchdog alarm restart
 *  - KeepAliveReceiver: re-raises priority every ~12 min, even in doze
 *  - uid1000 channel hardening (deviceidle whitelist / appops / standby)
 *    applied from the "Прочее" page - privileges, not just permissions
 *
 * force-stop by the user still disables everything until the next manual
 * launch - that is an Android invariant no app can bypass.
 */

/*
 * =========================================================================================
 * I18N MODIFICATION NOTE:
 * The hardcoded notification strings ("GC Tune" and "Защита фона активна") have been
 * extracted to R.string.app_name and R.string.keepalive_notif_text for multi-language
 * support. All original author comments, WakeLock handling, and lifecycle behaviors are
 * preserved intact.
 * =========================================================================================
 */
public class KeepAliveService extends Service {

    private static final String TAG = "GCTune";
    private static final String CH = "gctune_keep";
    private static final int NOTIF_ID = 102;
    private PowerManager.WakeLock wakeLock;

    /** Alarm-based restart path used by onTaskRemoved. */
    public static void start(Context c) {
        Intent i = new Intent(c, KeepAliveService.class);
        try {
            c.startForegroundService(i);
        } catch (Throwable t) {
            try { c.startService(i); } catch (Throwable t2) {
                Log.e(TAG, "keep service start failed: " + t2);
            }
        }
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, KeepAliveService.class)); } catch (Throwable ignored) {}
    }

    private boolean keepOn() {
        try {
            SharedPreferences p = getSharedPreferences("gctune_m3", MODE_PRIVATE);
            return p.getBoolean("keep_alive", false);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!keepOn()) {
            releaseWakeLock();
            stopSelf();
            return START_NOT_STICKY;
        }
        showNotification();
        acquireWakeLock();
        // persist across memory kills; null-intent restart lands here too
        return START_STICKY;
    }

    private void acquireWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GCTune:KeepAlive");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Throwable t) {
            Log.e(TAG, "wake lock acquire failed: " + t);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {}
        wakeLock = null;
    }

    private void showNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
            Notification n = new Notification.Builder(this, CH)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.keepalive_notif_text))
                    .setWhen(System.currentTimeMillis())
                    .setOngoing(true)
                    .build();
            startForeground(NOTIF_ID, n);
        } catch (Throwable t) {
            Log.e(TAG, "keep-alive foreground failed: " + t);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // the user swiped the app away: come back in ~2 s via the alarm path
        if (keepOn()) {
            KeepAliveReceiver.schedule(this, System.currentTimeMillis() + 2000L);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        releaseWakeLock();
        // system-initiated death: keep-alive alarm chain still re-raises us
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
