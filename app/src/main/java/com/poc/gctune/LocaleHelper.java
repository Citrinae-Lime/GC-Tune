package com.poc.gctune;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * Manages per-app language preferences for GC Tune on Android 13+ (API 33+).
 * Direct integration with Android's native LocaleManager and android:localeConfig.
 */
/*
 * =========================================================================================
 * COMPATIBILITY ADAPTATION (minSdkVersion = 30, targetSdkVersion = 36):
 *
 * Although the primary targets are modern Android 16 (SDK 36) devices where native
 * LocaleManager and android:localeConfig handle per-app language selection automatically,
 * minSdkVersion has been adjusted to 30 (Android 11).
 *
 * 1. On Android 13+ (API 33-36):
 *    All operations are delegated to Api33Impl which directly invokes android.app.LocaleManager
 *    and android.os.LocaleList.
 * 2. On Android 11-12 (API 30-32):
 *    android.app.LocaleManager does not exist in the Android SDK framework.
 *    To avoid VerifyError / NoClassDefFoundError on API 30-32 devices, framework calls to
 *    LocaleManager are encapsulated inside the static inner class Api33Impl (loaded lazily
 *    only when SDK_INT >= 33).
 *    On API < 33, the chosen locale tag is stored in SharedPreferences and system settings
 *    navigation falls back to the application details screen.
 * =========================================================================================
 */
public final class LocaleHelper {

    public static final String LANG_AUTO = "";
    public static final String LANG_EN = "en";
    public static final String LANG_RU = "ru";
    public static final String LANG_ZH = "zh-Hans";
    public static final String LANG_VI = "vi";

    private static final String PREF_NAME = "gctune_prefs";
    private static final String KEY_LOCALE = "app_language";

    private LocaleHelper() {}

    /** Gets the currently configured language tag ("" for system default, "en", "ru", "zh-Hans"). */
    public static String getLanguage(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            return Api33Impl.getLanguage(context);
        }
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getString(KEY_LOCALE, LANG_AUTO);
    }

    /** Applies a new language preference and syncs with system LocaleManager on API 33+. */
    public static void setLanguage(Context context, String langTag) {
        if (Build.VERSION.SDK_INT >= 33) {
            Api33Impl.setLanguage(context, langTag);
        } else {
            SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().putString(KEY_LOCALE, langTag == null ? LANG_AUTO : langTag).apply();
        }
    }

    /** Returns human readable label for the currently selected language. */
    public static String getLanguageDisplayName(Context context) {
        String lang = getLanguage(context);
        if (LANG_EN.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_en);
        } else if (LANG_RU.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_ru);
        } else if (LANG_ZH.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_zh);
        } else if (LANG_VI.equalsIgnoreCase(lang)) {
            return context.getString(R.string.lang_vi);
        }
        return context.getString(R.string.lang_default);
    }

    /** Shows a language selection dialog. */
    public static void showLanguageDialog(final Activity activity, final Runnable onSelected) {
        final String[] tags = {LANG_AUTO, LANG_EN, LANG_RU, LANG_ZH, LANG_VI};
        final String[] names = {
                activity.getString(R.string.lang_default),
                activity.getString(R.string.lang_en),
                activity.getString(R.string.lang_ru),
                activity.getString(R.string.lang_zh),
                activity.getString(R.string.lang_vi)
        };

        String currentTag = getLanguage(activity);
        int selectedIndex = 0;
        for (int i = 0; i < tags.length; i++) {
            if (tags[i].equalsIgnoreCase(currentTag)) {
                selectedIndex = i;
                break;
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.prefs_language))
                .setSingleChoiceItems(names, selectedIndex, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        setLanguage(activity, tags[which]);
                        if (onSelected != null) {
                            onSelected.run();
                        } else {
                            activity.recreate();
                        }
                    }
                })
                .setNeutralButton(activity.getString(R.string.prefs_system_language_btn),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openSystemLocaleSettings(activity);
                            }
                        })
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show();
    }

    /** Opens system-level Per-App Language settings screen (or App Details on API < 33). */
    public static boolean openSystemLocaleSettings(Context context) {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Intent intent = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS);
                intent.setData(Uri.fromParts("package", context.getPackageName(), null));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            } catch (Throwable ignored) {
            }
        }
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", context.getPackageName(), null));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Isolated inner class for API 33+ LocaleManager calls to prevent class verification
     * errors on Android 11-12 (API 30-32).
     */
    private static class Api33Impl {
        static String getLanguage(Context context) {
            android.app.LocaleManager lm = context.getSystemService(android.app.LocaleManager.class);
            if (lm != null) {
                android.os.LocaleList list = lm.getApplicationLocales();
                if (!list.isEmpty()) {
                    Locale loc = list.get(0);
                    String lang = loc.getLanguage();
                    if ("zh".equalsIgnoreCase(lang)) return LANG_ZH;
                    if ("ru".equalsIgnoreCase(lang)) return LANG_RU;
                    if ("en".equalsIgnoreCase(lang)) return LANG_EN;
                    if ("vi".equalsIgnoreCase(lang)) return LANG_VI;
                    return loc.toLanguageTag();
                }
            }
            return LANG_AUTO;
        }

        static void setLanguage(Context context, String langTag) {
            android.app.LocaleManager lm = context.getSystemService(android.app.LocaleManager.class);
            if (lm != null) {
                if (langTag == null || langTag.isEmpty()) {
                    lm.setApplicationLocales(android.os.LocaleList.getEmptyLocaleList());
                } else {
                    lm.setApplicationLocales(android.os.LocaleList.forLanguageTags(langTag));
                }
            }
        }
    }
}
