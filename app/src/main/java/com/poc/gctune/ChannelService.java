package com.poc.gctune;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.SurfaceControl;

/**
 * Foreground service that owns the uid1000 channel while a command is pending.
 *
 * Moved out of the Activity so that backgrounding the UI cannot drop the
 * payload layer or the ACK receiver. Decision logic (ACK numbering, busy
 * semantics, Settings fallback) is byte identical to the old Activity poller.
 *
 * Wake mechanism: the payload spawns a uid1000 watcher (see DAEMON_BOOT in
 * Main) which blocks on `logcat -s GCTune:I` - an idle watcher costs exactly
 * zero battery (no polling, no timers, no wake locks). Logging
 * "WAKE <ack>" makes it run `dumpsys SurfaceFlinger --latency "<our layer>"`
 * which re-executes the payload as uid1000. Retries: 0/+5/+15/+45 s, at most
 * 3 of them, never past the deadline.
 */
public class ChannelService extends Service {

    public interface Cb {
        void onDone(boolean ok, String out);
    }

    /** Set by Main while its UI is alive, cleared in onDestroy. */
    public static volatile Cb cb;

    private static final String ACK_KEY = "gc_tune_ack";
    private static final String CH = "gctune_run";
    private static final int NOTIF_ID = 101;
    /** Retry delays after the first wake: 5 s, then +15 s, then +45 s. */
    private static final long[] RETRY_MS = {5000L, 15000L, 45000L};

    /** Payload SurfaceControl; released on ACK, timeout, cancel or destroy. */
    private static SurfaceControl payloadLayer;

    private final Handler h = new Handler(Looper.getMainLooper());

    private int curAck;
    private long deadline;
    private boolean daemon;
    private volatile int bcAck = 0;
    private volatile String bcOut = null;
    private int retries = 0;
    private boolean running = false;
    private boolean receiverOn = false;
    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver ackRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            bcAck = i.getIntExtra("n", 0);
            bcOut = i.getStringExtra("o");
            String dbg = i.getStringExtra("e");
            if (dbg != null) {
                try {
                    String s = new String(Base64.decode(dbg, Base64.DEFAULT), "UTF-8");
                    Log.i("GCTune", "settings put rc/err: " + s.replace('\n', ' '));
                } catch (Throwable ignored) {}
            }
            Log.i("GCTune", "ack broadcast n=" + bcAck
                    + " out=" + (bcOut == null ? -1 : bcOut.length()));
        }
    };

    /** Same decision ladder as the old Main poller (broadcast primary, settings fallback). */
    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (bcAck == curAck && bcOut != null) { done(true, bcOut); return; }
            String v = null;
            try { v = Settings.System.getString(getContentResolver(), ACK_KEY); } catch (Throwable ignored) {}
            String want = curAck + "|";
            if (v != null && v.startsWith(want)) { done(true, v.substring(want.length())); return; }
            if (bcAck == curAck && v == null) { done(true, null); return; }
            if (System.currentTimeMillis() > deadline) { done(false, null); return; }
            h.postDelayed(this, 350L);
        }
    };

    private final Runnable waker = new Runnable() {
        @Override
        public void run() {
            if (!running || !daemon) return;
            Log.i("GCTune", "WAKE " + curAck);
            if (retries < RETRY_MS.length) {
                long at = System.currentTimeMillis() + RETRY_MS[retries];
                retries++;
                if (at < deadline) h.postDelayed(this, RETRY_MS[retries - 1]);
            }
        }
    };

    /** Entry point from Main; layer is created inside the service. */
    public static void start(Context ctx, int ack, String layerName, long deadlineMs, boolean daemon) {
        Intent i = new Intent(ctx, ChannelService.class);
        i.putExtra("ack", ack);
        i.putExtra("layer", layerName);
        i.putExtra("deadline", deadlineMs);
        i.putExtra("daemon", daemon);
        try {
            ctx.startForegroundService(i);
        } catch (Throwable t) {
            Log.e("GCTune", "startForegroundService failed: " + t);
            try { ctx.startService(i); } catch (Throwable t2) { Log.e("GCTune", "startService failed: " + t2); }
        }
    }

    /** Cancel path: release layer, receiver, wake lock, notification. */
    public static void stop(Context ctx) {
        try { ctx.stopService(new Intent(ctx, ChannelService.class)); } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { cleanup(); stopSelf(); return START_NOT_STICKY; }

        if (running) {
            // busy semantics: Main never starts a second run, but be safe
            Log.i("GCTune", "superseding pending ack " + curAck);
            cleanup();
        }

        curAck = i.getIntExtra("ack", 0);
        deadline = i.getLongExtra("deadline", System.currentTimeMillis() + 60000L);
        daemon = i.getBooleanExtra("daemon", true);
        bcAck = 0;
        bcOut = null;
        retries = 0;
        running = true;

        setLayer(i.getStringExtra("layer"));
        showNotification();

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "gctune:run");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(Math.max(5000L, deadline - System.currentTimeMillis() + 60000L));
        } catch (Throwable t) {
            Log.e("GCTune", "wakelock fail: " + t);
        }

        try {
            registerReceiver(ackRx, new IntentFilter("com.poc.gctune.ACK"), 2);
            receiverOn = true;
        } catch (Throwable t) {
            Log.e("GCTune", "receiver register failed: " + t);
        }

        // layer exists now: a wake can only be handled, never lost
        if (daemon) h.post(waker);
        h.post(poller);
        return START_NOT_STICKY;
    }

    private void showNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CH) == null) {
                NotificationChannel ch = new NotificationChannel(CH, getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
            Notification n = new Notification.Builder(this, CH)
                    .setSmallIcon(android.R.drawable.ic_popup_sync)
                    .setContentTitle(getString(R.string.app_name))
                    .setContentText(getString(R.string.channel_notif_running))
                    .setWhen(System.currentTimeMillis())
                    .setOngoing(true)
                    .build();
            startForeground(NOTIF_ID, n);
        } catch (Throwable t) {
            Log.e("GCTune", "startForeground fail: " + t);
        }
    }

    private void done(boolean ok, String out) {
        Cb c = cb;
        cleanup();
        stopSelf();
        if (c != null) {
            c.onDone(ok, out);
        } else {
            Log.i("GCTune", "run finished ok=" + ok + " but no UI attached");
        }
    }

    private void cleanup() {
        running = false;
        h.removeCallbacks(poller);
        h.removeCallbacks(waker);
        if (receiverOn) {
            try { unregisterReceiver(ackRx); } catch (Throwable ignored) {}
            receiverOn = false;
        }
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        wakeLock = null;
        setLayer(null);
        try { stopForeground(true); } catch (Throwable ignored) {}
    }

    private static void setLayer(String name) {
        try {
            if (payloadLayer != null) {
                payloadLayer.release();
                payloadLayer = null;
            }
            if (name == null) return;
            payloadLayer = new SurfaceControl.Builder()
                    .setName(name)
                    .setHidden(false)
                    .build();
            Log.i("GCTune", "layer built valid=" + payloadLayer.isValid());
        } catch (Throwable e) {
            Log.e("GCTune", "layer fail: " + e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }
}
