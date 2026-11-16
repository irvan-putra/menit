package com.vanipy.menit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns raw usage events into foreground milliseconds per package.
 * Model: one app is in the foreground at a time. The screen going off,
 * a shutdown, or another app resuming ends the current session.
 * Split screen, pop-up windows and picture-in-picture are not modelled.
 */
public final class Sessionizer {
    private Sessionizer() {}

    /** Events are sorted here (stable) so out-of-order input cannot invent time. Only time inside [start, end) is counted. */
    public static Map<String, Long> foregroundMs(List<Event> events, long start, long end) {
        Map<String, Long> totals = new HashMap<>();
        String current = null;
        long since = 0;
        List<Event> sorted = new ArrayList<>(events);
        sorted.sort((a, b) -> Long.compare(a.time, b.time));
        for (Event e : sorted) {
            switch (e.type) {
                case Event.RESUMED:
                    close(totals, current, since, e.time, start, end);
                    current = e.pkg;
                    since = e.time;
                    break;
                case Event.PAUSED:
                case Event.STOPPED:
                    if (current != null && current.equals(e.pkg)) {
                        close(totals, current, since, e.time, start, end);
                        current = null;
                    }
                    break;
                case Event.SCREEN_OFF:
                case Event.SHUTDOWN:
                    close(totals, current, since, e.time, start, end);
                    current = null;
                    break;
                default:
                    break;
            }
        }
        // Still open at the end of the data: count up to the window end.
        close(totals, current, since, end, start, end);
        return totals;
    }

    private static void close(Map<String, Long> totals, String pkg, long from, long to,
                              long start, long end) {
        if (pkg == null) return;
        long a = Math.max(from, start);
        long b = Math.min(to, end);
        if (b > a) totals.merge(pkg, b - a, Long::sum);
    }
}
