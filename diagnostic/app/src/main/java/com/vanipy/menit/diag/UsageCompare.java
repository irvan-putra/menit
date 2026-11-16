package com.vanipy.menit.diag;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

/** Optional: compare this app's own usage events with the lifecycle foreground, and measure how late they appear. */
final class UsageCompare {
    private UsageCompare() {}
    static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode = android.os.Build.VERSION.SDK_INT >= 29
            ? ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
            : ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());   // API 26-28
        return mode == AppOpsManager.MODE_ALLOWED;
    }
    static String describe(Context c, long a, long b) {
        if (!hasAccess(c)) return "Usage events: access off, no comparison.";
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long q = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(a - 60_000, q);
        UsageEvents.Event e = new UsageEvents.Event();
        long open = -1, total = 0; int pairs = 0, unpaired = 0; long lastStamp = 0;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (!DiagStore.PKG.equals(e.getPackageName())) continue;
            long t = e.getTimeStamp(); int type = e.getEventType();
            if (type == UsageEvents.Event.ACTIVITY_RESUMED) { open = t; }
            else if ((type == UsageEvents.Event.ACTIVITY_PAUSED || type == UsageEvents.Event.ACTIVITY_STOPPED) && open >= 0) {
                long s = Math.max(a, open), en = Math.min(b, t); if (en > s) total += en - s; pairs++; open = -1; lastStamp = t;
            }
        }
        if (open >= 0) { unpaired++; long s = Math.max(a, open); if (b > s) total += b - s; }
        return "Usage events foreground: " + total / 1000.0 + " s (" + pairs + " resume/pause pairs, " + unpaired
            + " open); newest pause stamp is " + (lastStamp == 0 ? "none" : (q - lastStamp) + " ms before this query") + ".";
    }
}
