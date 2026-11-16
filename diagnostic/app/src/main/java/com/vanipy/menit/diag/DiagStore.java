package com.vanipy.menit.diag;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import java.io.*;
import java.util.*;

/**
 * Local diagnostic log. Timestamps and typed labels only, never header text or message text.
 * Reconciliation uses elapsed-realtime (monotonic, counts deep sleep). Wall-clock is kept only for display.
 * The active file rotates into the archive folder when it grows past a size limit. Nothing is deleted automatically.
 */
final class DiagStore {
    static final String PKG = "com.vanipy.menit.diag";
    static final long ROTATE_BYTES = 256 * 1024;
    private static DiagStore inst;
    private final Context ctx;
    private final File file;
    private final int boot;
    List<Reconciler.Obs> obs; List<long[]> fg; List<Long> heartbeats; List<long[]> clocks; int skipped, killed;
    private long openFgStart = -1;

    static synchronized DiagStore get(Context c) { if (inst == null) inst = new DiagStore(c.getApplicationContext()); return inst; }

    private DiagStore(Context c) {
        ctx = c;
        file = new File(c.getFilesDir(), "diag-log.csv");
        boot = Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        load();
        append("S," + boot + "," + el() + "," + System.currentTimeMillis());   // process start marker
    }
    static long el() { return SystemClock.elapsedRealtime(); }
    /** Wall-clock for display only. */
    static long wallOf(long elapsed) { return System.currentTimeMillis() - (SystemClock.elapsedRealtime() - elapsed); }
    int boot() { return boot; }

    private synchronized void load() {
        List<String> lines = new ArrayList<>();
        if (file.exists()) try (BufferedReader r = new BufferedReader(new FileReader(file))) { String l; while ((l = r.readLine()) != null) lines.add(l); } catch (IOException ignored) { }
        LogParser p = LogParser.parse(lines, boot, PKG);
        obs = p.obs; fg = p.fg; heartbeats = p.heartbeats; clocks = p.clocks; skipped = p.skipped; killed = p.killed;
    }
    private synchronized void append(String line) {
        try {
            if (file.length() > ROTATE_BYTES) {
                File dir = new File(file.getParentFile(), "archive"); dir.mkdirs();
                boolean moved = file.renameTo(new File(dir, "diag-" + System.currentTimeMillis() + ".csv"));
                // The session continues in the new file: write a fresh start marker so a later kill is still seen.
                // Not when the line being written is itself the start marker, and not when the rename failed (the old file is still the log).
                if (moved && openFgStart >= 0 && !line.startsWith("FS,")) try (FileWriter m = new FileWriter(file, true)) { m.write("FS," + boot + "," + el() + "\n"); }
            }
            try (FileWriter w = new FileWriter(file, true)) { w.write(line + "\n"); }
        } catch (IOException ignored) { }
    }
    synchronized void addObs(long el, Reconciler.Label l, int id) { obs.add(new Reconciler.Obs(el, PKG, l, id)); append("O," + boot + "," + el + "," + l + "," + id); }
    synchronized void addHeartbeat(long el) { heartbeats.add(el); append("H," + boot + "," + el); }
    synchronized void addClock(long evt, long up, long el, long wall) { clocks.add(new long[]{evt, up, el, wall}); append("C," + boot + "," + evt + "," + up + "," + el + "," + wall); }
    synchronized void fgStart(long el) { openFgStart = el; append("FS," + boot + "," + el); }
    synchronized void fgEnd(long el) { if (openFgStart >= 0 && el > openFgStart) { fg.add(new long[]{openFgStart, el, 1}); append("F," + boot + "," + openFgStart + "," + el); } openFgStart = -1; }
    synchronized long openFgStart() { return openFgStart; }

    /** Archive: moves the active log aside and starts fresh. Nothing is deleted. */
    synchronized boolean archive() {
        long prevOpen = openFgStart;
        if (file.exists()) {
            File dir = new File(file.getParentFile(), "archive"); dir.mkdirs();
            if (!file.renameTo(new File(dir, "diag-" + System.currentTimeMillis() + ".csv"))) return false;   // nothing changed: keep memory and file as they were
        }
        obs = new ArrayList<>(); fg = new ArrayList<>(); heartbeats = new ArrayList<>(); clocks = new ArrayList<>(); skipped = killed = 0;
        append("S," + boot + "," + el() + "," + System.currentTimeMillis());
        openFgStart = prevOpen >= 0 ? el() : -1;           // keep the current session, restarted at now
        if (openFgStart >= 0) append("FS," + boot + "," + openFgStart);
        return true;
    }
    synchronized int archiveCount() { File[] f = new File(file.getParentFile(), "archive").listFiles(); return f == null ? 0 : f.length; }
    /** Irreversible. Only called from a separate, confirmed action. */
    synchronized void deleteArchives() { File[] f = new File(file.getParentFile(), "archive").listFiles(); if (f != null) for (File x : f) x.delete(); }

    /** Coverage from heartbeats: beats closer than maxGap are one run, closed at the last beat. */
    synchronized List<Reconciler.Cov> coverage(long maxGapMs) {
        List<Reconciler.Cov> out = new ArrayList<>(); List<Long> h = new ArrayList<>(heartbeats); Collections.sort(h);
        long s = -1, last = -1;
        for (long t : h) { if (s < 0) { s = t; last = t; } else if (t - last <= maxGapMs) last = t; else { if (last > s) out.add(new Reconciler.Cov(s, last)); s = t; last = t; } }
        if (s >= 0 && last > s) out.add(new Reconciler.Cov(s, last));
        return out;
    }
}
