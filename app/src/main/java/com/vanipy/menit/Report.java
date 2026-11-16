package com.vanipy.menit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Splits per-package foreground time into AI, mixed and everything else. */
public final class Report {
    public static final class Row {
        public final String pkg;
        public final long ms;
        Row(String pkg, long ms) { this.pkg = pkg; this.ms = ms; }
    }

    public final long totalMs;
    public final long aiMs;
    public final long mixedMs;
    public final List<Row> aiRows = new ArrayList<>();
    public final List<Row> mixedRows = new ArrayList<>();

    public Report(Map<String, Long> perPackage, String ownPackage) {
        long total = 0, ai = 0, mixed = 0;
        for (Map.Entry<String, Long> e : perPackage.entrySet()) {
            String pkg = e.getKey();
            long ms = e.getValue();
            if (pkg.equals(ownPackage)) continue; // measuring ourselves is noise
            total += ms;
            AiCatalog.Kind k = AiCatalog.kindOf(pkg);
            if (k == AiCatalog.Kind.AI) { ai += ms; aiRows.add(new Row(pkg, ms)); }
            else if (k == AiCatalog.Kind.MIXED) { mixed += ms; mixedRows.add(new Row(pkg, ms)); }
        }
        totalMs = total; aiMs = ai; mixedMs = mixed;
        Collections.sort(aiRows, (a, b) -> Long.compare(b.ms, a.ms));
        Collections.sort(mixedRows, (a, b) -> Long.compare(b.ms, a.ms));
    }

    public static String format(long ms) {
        long min = Math.round(ms / 60000.0);
        if (min < 1 && ms > 0) return "<1 min";
        if (min < 60) return min + " min";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    /** Percent of screen time, or -1 when there is no data. */
    public static int percent(long part, long whole) {
        return whole <= 0 ? -1 : (int) Math.round(100.0 * part / whole);
    }
}
