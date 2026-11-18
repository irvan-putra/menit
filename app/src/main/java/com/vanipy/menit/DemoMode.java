package com.vanipy.menit;

import android.content.Context;

/** Opt-in switch that swaps the owner's real usage for the bundled made-up profile. */
public final class DemoMode {
    private static final String PREFS = "menit_prefs";
    private static final String KEY = "demo_mode";

    private DemoMode() {}

    public static boolean isOn(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static void set(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
        MenitWidgetProvider.updateAll(c);
    }
}
