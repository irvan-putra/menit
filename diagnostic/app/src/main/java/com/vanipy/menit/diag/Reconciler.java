package com.vanipy.menit.diag;

import java.util.*;
/**
 * FROZEN research prototype (synthetic, host-only). Retrospective reconciliation of foreground intervals
 * (authority for boundaries) with header labels (optional reader) and coverage. Not production code.
 * Pure function of its inputs: input order does not matter. Output is an estimate in separate categories;
 * no lower/upper bound is claimed. PROBABLE is off unless explicitly enabled with an explicit cap.
 * NOT implemented: building foreground intervals from real UsageEvents, heartbeat-based coverage, real clock use.
 */
public class Reconciler {
    public enum Label { ALLOWLISTED, NOT_ALLOWLISTED, UNRECOGNISED }
    public enum Cat { RECOGNISED, PROBABLE, WATCHED_OTHER, UNKNOWN_FG, NO_DATA, PENDING, MULTI_WINDOW }
    public static final class Options {
        final long graceMs; final boolean probable; final long probableCapMs; // placeholders, not claimed correct
        Options(long g, boolean p, long cap) { graceMs = g; probable = p; probableCapMs = cap; }
        static Options defaults() { return new Options(30_000, false, 0); }
    }
    public static final class Fg  { final String pkg; final long s, e; final boolean settled;
        Fg(String p,long s,long e,boolean settled){pkg=p;this.s=s;this.e=e;this.settled=settled;} Fg(String p,long s,long e){this(p,s,e,true);} }
    public static final class Obs { final long t; final String pkg; final Label label; final int id;
        Obs(long t,String p,Label l,int id){this.t=t;pkg=p;label=l;this.id=id;} }
    public static final class Cov { final long s, e; Cov(long s,long e){this.s=s;this.e=e;} }
    public static final class Seg { final Cat cat; final String pkg; final int id; final long s, e;
        Seg(Cat c,String p,int id,long s,long e){cat=c;pkg=p;this.id=id;this.s=s;this.e=e;} }

    public static long wallFromElapsed(long elapsedMs, long nowWallMs, long nowElapsedMs) { return nowWallMs - (nowElapsedMs - elapsedMs); }

    public static List<Seg> reconcile(Set<String> watched, List<Fg> fgs, List<Obs> obsIn, List<Cov> cov, Options op) {
        List<Seg> out = new ArrayList<>();
        List<Obs> o = normaliseObs(obsIn);
        List<Cov> c = mergeCov(cov);
        // sweep all foreground intervals (watched and not) so overlaps are visible
        TreeSet<Long> pts = new TreeSet<>();
        for (Fg f : fgs) if (f.e > f.s) { pts.add(f.s); pts.add(f.e); }
        Long[] p = pts.toArray(new Long[0]);
        String runPkg = null; boolean runSettled = true; long runS = 0, runE = 0;
        for (int i = 0; i + 1 < p.length; i++) {
            long a = p[i], b = p[i+1]; Set<String> act = new HashSet<>(); boolean unsettled = false;
            for (Fg f : fgs) if (f.e > f.s && f.s <= a && f.e >= b) { act.add(f.pkg); if (!f.settled) unsettled = true; }
            String single = act.size() == 1 ? act.iterator().next() : null;
            boolean continues = runPkg != null && single != null && single.equals(runPkg) && unsettled == !runSettled && runE == a;
            if (!continues) { flush(out, watched, runPkg, runSettled, runS, runE, o, c, op); runPkg = null; }
            if (act.size() >= 2) { out.add(new Seg(Cat.MULTI_WINDOW, "", -1, a, b)); }
            else if (single != null && watched.contains(single)) {
                if (runPkg == null) { runPkg = single; runSettled = !unsettled; runS = a; }
                runE = b;
            }
        }
        flush(out, watched, runPkg, runSettled, runS, runE, o, c, op);
        return out;
    }
    private static void flush(List<Seg> out, Set<String> w, String pkg, boolean settled, long s, long e, List<Obs> o, List<Cov> c, Options op) {
        if (pkg == null || e <= s) return;
        if (!settled) { out.add(new Seg(Cat.PENDING, pkg, -1, s, e)); return; }
        long cur = s;
        for (Cov cv : c) { long ps = Math.max(s, cv.s), pe = Math.min(e, cv.e); if (pe <= ps) continue;
            if (ps > cur) out.add(new Seg(Cat.NO_DATA, pkg, -1, cur, ps));
            piece(out, pkg, ps, pe, o, op); cur = pe; }
        if (cur < e) out.add(new Seg(Cat.NO_DATA, pkg, -1, cur, e));
    }
    /** Total order and conflict handling: identical same-(pkg,t) observations dedupe; conflicting ones become UNRECOGNISED. */
    private static List<Obs> normaliseObs(List<Obs> in) {
        Map<String, List<Obs>> g = new TreeMap<>();
        for (Obs x : in) g.computeIfAbsent(x.pkg + "\u0000" + String.format("%020d", x.t), k -> new ArrayList<>()).add(x);
        List<Obs> out = new ArrayList<>();
        for (List<Obs> l : g.values()) {
            Obs f = l.get(0); boolean same = true;
            for (Obs x : l) if (x.label != f.label || x.id != f.id) same = false;
            out.add(same ? f : new Obs(f.t, f.pkg, Label.UNRECOGNISED, -1));
        }
        out.sort(Comparator.<Obs>comparingLong(x -> x.t).thenComparing(x -> x.pkg));
        return out;
    }
    private static List<Cov> mergeCov(List<Cov> in) {
        List<Cov> s = new ArrayList<>(in); s.sort(Comparator.comparingLong(x -> x.s));
        List<Cov> m = new ArrayList<>();
        for (Cov x : s) { if (x.e <= x.s) continue;
            if (!m.isEmpty() && x.s <= m.get(m.size()-1).e) { Cov l = m.remove(m.size()-1); m.add(new Cov(l.s, Math.max(l.e, x.e))); } else m.add(x); }
        return m;
    }
    private static void piece(List<Seg> out, String pkg, long ps, long pe, List<Obs> all, Options op) {
        List<Obs> o = new ArrayList<>();
        for (Obs x : all) if (x.pkg.equals(pkg) && x.t >= ps && x.t < pe) o.add(x);
        if (o.isEmpty()) { out.add(new Seg(Cat.UNKNOWN_FG, pkg, -1, ps, pe)); return; }
        if (o.get(0).t > ps) out.add(new Seg(Cat.UNKNOWN_FG, pkg, -1, ps, o.get(0).t));
        for (int i = 0; i < o.size(); i++) {
            Obs a = o.get(i); long next = i + 1 < o.size() ? o.get(i+1).t : pe;
            long labelEnd = Math.min(next, a.t + op.graceMs);
            if (labelEnd > a.t) out.add(a.label == Label.ALLOWLISTED ? new Seg(Cat.RECOGNISED, pkg, a.id, a.t, labelEnd)
                : a.label == Label.NOT_ALLOWLISTED ? new Seg(Cat.WATCHED_OTHER, pkg, -1, a.t, labelEnd)
                : new Seg(Cat.UNKNOWN_FG, pkg, -1, a.t, labelEnd));
            if (next > labelEnd) {
                boolean bracket = op.probable && i + 1 < o.size() && a.label == Label.ALLOWLISTED && o.get(i+1).label == Label.ALLOWLISTED
                    && a.id == o.get(i+1).id && next - labelEnd <= op.probableCapMs;
                out.add(bracket ? new Seg(Cat.PROBABLE, pkg, a.id, labelEnd, next) : new Seg(Cat.UNKNOWN_FG, pkg, -1, labelEnd, next));
            }
        }
    }
    static long ms(List<Seg> s, Cat c, String pkg) { long x=0; for (Seg g: s) if (g.cat==c && (pkg==null||pkg.equals(g.pkg))) x+=g.e-g.s; return x; }

}
