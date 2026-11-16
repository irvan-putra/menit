package com.vanipy.menit;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads foreground events from UsageStatsManager. Needs the Usage access permission. */
public final class UsageReader {
    private UsageReader() {}

    public static boolean hasAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        } else {
            // API 26-28: the older call (deprecated later, still correct here).
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), c.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** Foreground ms per package within [start, end). */
    public static Map<String, Long> read(Context c, long start, long end) {
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        // Look back 12 h so a session that began before the window still gets clipped correctly.
        UsageEvents events = usm.queryEvents(start - 12L * 3600_000L, end);
        List<Event> list = new ArrayList<>();
        UsageEvents.Event ev = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(ev);
            int t = ev.getEventType();
            if (t == Event.RESUMED || t == Event.PAUSED || t == Event.STOPPED
                    || t == Event.SCREEN_OFF || t == Event.SCREEN_ON || t == Event.SHUTDOWN) {
                list.add(new Event(ev.getPackageName(), t, ev.getTimeStamp()));
            }
        }
        return Sessionizer.foregroundMs(list, start, end);
    }
}
