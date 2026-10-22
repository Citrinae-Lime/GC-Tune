package com.poc.gctune;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

/**
 * ADB bridge: an exported ContentProvider driven ONLY from adb shell
 * (uid 2000) or root (uid 0). Every other caller is rejected by
 * {@link Binder#getCallingUid()} - no permission in the manifest can be
 * abused, because the identity check happens per transaction.
 *
 * Protocol (all through the `content` shell tool):
 *
 *   # send a command through the uid1000 channel, wait up to 8 s for the result
 *   adb shell content call --uri content://com.poc.gctune.bridge \
 *       --method exec --arg "id" --extra wait:i:8
 *
 *   # same call without waiting (async) - ack comes back immediately
 *   adb shell content call --uri content://com.poc.gctune.bridge \
 *       --method exec --arg "getprop ro.product.model" --extra wait:i:0
 *   adb shell settings get secure gc_tune_adb_out     # ack|STATUS|rc|base64
 *
 *   # bridge state (main alive / busy / toggles)
 *   adb shell content call --uri content://com.poc.gctune.bridge --method status
 *
 * The mirror key `Settings.Secure.gc_tune_adb_out` is written by Main:
 * "PENDING" while the channel runs, then "OK"/"FAIL" with rc + base64 output,
 * so scripts can poll a single stable line instead of parsing Bundle dumps.
 *
 * exec() blocks its binder thread on a latch (max ~20 s) while Main runs the
 * command on the UI thread and releases it from finishRun(); a timeout just
 * returns status=pending - the settings mirror still completes later.
 */
public class BridgeProvider extends ContentProvider {

    public static final String AUTHORITY = "com.poc.gctune.bridge";
    public static final String OUT_KEY = "gc_tune_adb_out";

    /** Implemented by Main while its UI is alive. */
    public interface Bridge {
        Bundle exec(String cmd, int waitSec);
        Bundle status();
    }

    /** Registered by Main in onCreate, cleared in onDestroy. */
    public static volatile Bridge bridgeImpl;

    private static final String TAG = "GCTune";

    @Override
    public boolean onCreate() {
        return true;
    }

    /** Only adb (shell uid 2000) and root (uid 0) may talk to the bridge. */
    private static boolean callerAllowed() {
        int uid = Binder.getCallingUid();
        return uid == 0 || uid == 2000;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        if (!callerAllowed()) {
            out.putString("status", "denied");
            out.putString("msg", "only shell/root (uid 2000/0) allowed, caller uid "
                    + Binder.getCallingUid() + " denied");
            Log.w(TAG, "bridge denied uid=" + Binder.getCallingUid());
            return out;
        }

        if ("ping".equals(method)) {
            out.putString("status", "ok");
            out.putString("pkg", "com.poc.gctune");
            return out;
        }

        Bridge b = bridgeImpl;
        if (b == null) {
            // Provider instantiation already brought the process up: try to
            // show the UI so the next call can work (background starts may be
            // blocked by the OS - then the caller just gets app_closed).
            try {
                if (getContext() != null) {
                    android.content.Intent i = new android.content.Intent(getContext(), Main.class);
                    i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(i);
                }
            } catch (Throwable ignored) {}
            out.putString("status", "app_closed");
            out.putString("msg", "GC Tune is not running — open the app and try again");
            return out;
        }

        if ("status".equals(method)) {
            try {
                return b.status();
            } catch (Throwable t) {
                out.putString("status", "error");
                out.putString("msg", String.valueOf(t));
                return out;
            }
        }

        if ("exec".equals(method)) {
            int wait = extras != null ? extras.getInt("wait", 8) : 8;
            if (wait < 0) wait = 0;
            if (wait > 20) wait = 20;
            try {
                Bundle res = b.exec(arg, wait);
                return res != null ? res : pendingOut(arg);
            } catch (Throwable t) {
                Log.e(TAG, "bridge exec failed: " + t);
                out.putString("status", "error");
                out.putString("msg", String.valueOf(t));
                return out;
            }
        }

        out.putString("status", "unknown_method");
        out.putString("msg", "methods: exec, status, ping");
        return out;
    }

    private Bundle pendingOut(String cmd) {
        Bundle b = new Bundle();
        b.putString("status", "pending");
        b.putString("cmd", cmd);
        return b;
    }

    // ------------------------------------------------------- unused CRUD
    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
