package com.vanipy.menit.diag;

import java.util.*;

/**
 * Parses the local diagnostic log. Pure Java so it can be tested on the host.
 * Every line starts with its boot count; only lines of the current boot are used, because elapsed-realtime restarts at boot.
 * Lines: S,b,el,wall (process start) | O,b,el,label,id | H,b,el | FS,b,el | F,b,start,end | C,b,evt,uptime,el,wall
 * A bad line is counted and skipped; it never stops later lines from loading.
 */
final class LogParser {
    final List<Reconciler.Obs> obs = new ArrayList<>();
    final List<long[]> fg = new ArrayList<>();        // {start, end, settled(1)/killed(0)} in elapsed ms
    final List<Long> heartbeats = new ArrayList<>();
    final List<long[]> clocks = new ArrayList<>();    // {eventTime, uptime, elapsed, wall}
    int skipped = 0, killed = 0;
    private long openFs = -1, maxEl = -1;

    static LogParser parse(Iterable<String> lines, int boot, String pkg) {
        LogParser p = new LogParser();
        for (String l : lines) { try { p.line(l, boot, pkg); } catch (RuntimeException e) { p.skipped++; } }
        p.closeOpenAsKilled();   // an unmatched start marker means the process died while in the foreground
        return p;
    }
    private void line(String l, int boot, String pkg) {
        String[] f = l.split(",", -1);
        if (f.length < 3) { skipped++; return; }
        if (Integer.parseInt(f[1]) != boot) return;              // other boot: not comparable, not an error
        switch (f[0]) {
            case "S": closeOpenAsKilled(); see(Long.parseLong(f[2])); break;
            case "O": { long el = Long.parseLong(f[2]); if (f.length != 5) throw new IllegalArgumentException();
                obs.add(new Reconciler.Obs(el, pkg, Reconciler.Label.valueOf(f[3]), Integer.parseInt(f[4]))); see(el); break; }
            case "H": { long el = Long.parseLong(f[2]); heartbeats.add(el); see(el); break; }
            case "FS": { closeOpenAsKilled(); openFs = Long.parseLong(f[2]); see(openFs); break; }
            case "F": { long s = Long.parseLong(f[2]), e = Long.parseLong(f[3]); if (e < s) throw new IllegalArgumentException();
                fg.add(new long[]{s, e, 1}); openFs = -1; see(e); break; }
            case "C": { if (f.length != 6) throw new IllegalArgumentException();
                clocks.add(new long[]{Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]), Long.parseLong(f[5])}); break; }
            default: skipped++;
        }
    }
    private void see(long el) { if (el > maxEl) maxEl = el; }
    private void closeOpenAsKilled() {
        if (openFs >= 0) { long end = Math.max(openFs, maxEl); if (end > openFs) fg.add(new long[]{openFs, end, 0});
            killed++;   // counted even when nothing else was logged after the start, so a zero-length kill is still visible
            openFs = -1; }
    }
}
