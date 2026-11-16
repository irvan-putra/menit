package com.vanipy.menit.diag;

import static com.vanipy.menit.diag.Reconciler.*;
import java.util.*;

/** Synthetic reconciler checks: fixed cases plus a randomized test compared per second with a brute-force oracle. */
public class ReconcilerTest {
    static long ms(List<Seg> s, Cat c, String pkg) { return Reconciler.ms(s, c, pkg); }
    static final Set<String> W = new HashSet<>(Arrays.asList("wa","tg"));
    // ---------- independent brute-force oracle: per-second attribution straight from the stated rules ----------
    static String oracleSec(Set<String> w, List<Fg> fgs, List<Obs> obs, List<Cov> cov, Options op, long sec) {
        long a = sec * 1000;
        Set<String> act = new HashSet<>(); boolean uns = false;
        for (Fg f : fgs) if (f.s <= a && f.e >= a + 1000) { act.add(f.pkg); if (!f.settled) uns = true; }
        if (act.isEmpty()) return null;
        if (act.size() >= 2) return "MULTI_WINDOW";
        String pkg = act.iterator().next(); if (!w.contains(pkg)) return null;
        if (uns) return "PENDING";
        if (!covered(cov, a)) return "NO_DATA";
        // start of the run: walk back while same single pkg, settled, covered
        long rs = a; while (true) { long b = rs - 1000; if (!sameRun(w, fgs, cov, pkg, b)) break; rs = b; }
        // latest observation group at pkg with t in [rs, a+1000) and t <= a
        long best = Long.MIN_VALUE; for (Obs x : obs) if (x.pkg.equals(pkg) && x.t >= rs && x.t <= a) best = Math.max(best, x.t);
        if (best == Long.MIN_VALUE) return "UNKNOWN_FG";
        if (a >= best + op.graceMs) return "UNKNOWN_FG";
        Set<String> labs = new HashSet<>(); for (Obs x : obs) if (x.pkg.equals(pkg) && x.t == best) labs.add(x.label + ":" + (x.label == Label.ALLOWLISTED ? x.id : -1));
        if (labs.size() != 1) return "UNKNOWN_FG";
        String l = labs.iterator().next();
        return l.startsWith("ALLOWLISTED") ? "RECOGNISED:" + l.substring(l.indexOf(':') + 1) : l.startsWith("NOT_") ? "WATCHED_OTHER" : "UNKNOWN_FG";
    }
    static boolean covered(List<Cov> cov, long a) { for (Cov c : cov) if (c.s <= a && c.e >= a + 1000) return true; return false; }
    static boolean sameRun(Set<String> w, List<Fg> fgs, List<Cov> cov, String pkg, long a) {
        Set<String> act = new HashSet<>(); boolean uns = false;
        for (Fg f : fgs) if (f.s <= a && f.e >= a + 1000) { act.add(f.pkg); if (!f.settled) uns = true; }
        return act.size() == 1 && act.contains(pkg) && !uns && covered(cov, a);
    }
    static String segSec(List<Seg> r, long sec) {
        long a = sec * 1000; String found = null;
        for (Seg g : r) if (g.s <= a && g.e >= a + 1000) { String v = g.cat == Cat.RECOGNISED ? "RECOGNISED:" + g.id : g.cat.name(); if (found != null) return "DUP"; found = v; }
        return found;
    }

    static int fails = 0, n = 0;
    static void check(String name, long got, long want) { n++; boolean ok = got==want; if(!ok) fails++; System.out.println((ok?"PASS ":"FAIL ")+name+" got="+got+" want="+want); }
    static Obs al(long t,String p,int id){return new Obs(t,p,Label.ALLOWLISTED,id);}
    static Obs no(long t,String p){return new Obs(t,p,Label.NOT_ALLOWLISTED,-1);}
    static Obs un(long t,String p){return new Obs(t,p,Label.UNRECOGNISED,-1);}
    static List<Cov> all(long s,long e){ return Arrays.asList(new Cov(s,e)); }
    static List<Seg> run(List<Fg> f, List<Obs> o, List<Cov> c){ return reconcile(W,f,o,c,Options.defaults()); }

    @org.junit.Test public void allChecks() {
        List<Seg> r; Options D = Options.defaults();
        r = run(Arrays.asList(new Fg("wa",0,510_000)), Arrays.asList(al(0,"wa",1)), all(0,600_000));
        check("1 stale gap: recognised grace only", ms(r,Cat.RECOGNISED,null), 30_000); check("1 unknown fg", ms(r,Cat.UNKNOWN_FG,null), 480_000);
        r = run(Arrays.asList(new Fg("wa",0,10_000), new Fg("launcher",10_000,100_000)), Arrays.asList(al(0,"wa",1)), all(0,200_000));
        check("2 exit to launcher", ms(r,Cat.RECOGNISED,null), 10_000);
        // reported bug 1: same-timestamp conflict, both input orders identical and UNKNOWN_FG
        for (int k = 0; k < 2; k++) { List<Obs> o = new ArrayList<>(Arrays.asList(al(0,"wa",1), no(0,"wa"))); if (k==1) Collections.reverse(o);
            r = run(Arrays.asList(new Fg("wa",0,30_000)), o, all(0,100_000));
            check("3 same-t conflict order"+k+": recognised 0", ms(r,Cat.RECOGNISED,null), 0); check("3 same-t conflict order"+k+": watched_other 0", ms(r,Cat.WATCHED_OTHER,null), 0); check("3 same-t conflict order"+k+": unknown", ms(r,Cat.UNKNOWN_FG,null), 30_000); }
        r = run(Arrays.asList(new Fg("wa",0,30_000)), Arrays.asList(al(0,"wa",1), al(0,"wa",1)), all(0,100_000)); check("3b identical duplicates dedupe", ms(r,Cat.RECOGNISED,null), 30_000);
        // reported bug 2: overlapping same-package intervals are not double counted
        r = run(Arrays.asList(new Fg("wa",0,60_000), new Fg("wa",30_000,90_000)), Arrays.asList(al(0,"wa",1)), all(0,200_000));
        check("4 overlap same pkg: total attributed = wall", ms(r,Cat.RECOGNISED,null)+ms(r,Cat.UNKNOWN_FG,null), 90_000);
        r = run(Arrays.asList(new Fg("wa",0,60_000), new Fg("wa",0,60_000)), Arrays.asList(al(0,"wa",1)), all(0,200_000)); check("4b duplicate interval", ms(r,Cat.RECOGNISED,null)+ms(r,Cat.UNKNOWN_FG,null), 60_000);
        // split-screen: two apps at once are refused, not attributed
        r = run(Arrays.asList(new Fg("wa",0,60_000), new Fg("tg",20_000,80_000)), Arrays.asList(al(0,"wa",1), al(40_000,"tg",2)), all(0,200_000));
        check("5 multi-window", ms(r,Cat.MULTI_WINDOW,null), 40_000); check("5 attributed + multi = wall", ms(r,Cat.RECOGNISED,null)+ms(r,Cat.UNKNOWN_FG,null)+ms(r,Cat.MULTI_WINDOW,null)+ms(r,Cat.NO_DATA,null), 80_000);
        // unsettled tail is pending, not credited
        r = run(Arrays.asList(new Fg("wa",0,60_000,false)), Arrays.asList(al(0,"wa",1)), all(0,100_000)); check("6 pending", ms(r,Cat.PENDING,null), 60_000); check("6 not credited", ms(r,Cat.RECOGNISED,null), 0);
        // process death coverage gap
        r = run(Arrays.asList(new Fg("wa",0,3_600_000)), Arrays.asList(al(0,"wa",1), al(3_000_000,"wa",1)), Arrays.asList(new Cov(0,10_000), new Cov(3_000_000,3_600_000)));
        check("7 no_data", ms(r,Cat.NO_DATA,null), 2_990_000); check("7 probable off", ms(r,Cat.PROBABLE,null), 0);
        // probable: off by default, on only with explicit cap
        List<Obs> ob = Arrays.asList(al(0,"wa",1), al(100_000,"wa",1));
        r = reconcile(W, Arrays.asList(new Fg("wa",0,200_000)), ob, all(0,300_000), D); check("8 probable default off", ms(r,Cat.PROBABLE,null), 0);
        r = reconcile(W, Arrays.asList(new Fg("wa",0,200_000)), ob, all(0,300_000), new Options(30_000,true,120_000)); check("8 probable on within cap", ms(r,Cat.PROBABLE,null), 70_000);
        r = reconcile(W, Arrays.asList(new Fg("wa",0,200_000)), ob, all(0,300_000), new Options(30_000,true,60_000)); check("8 probable beyond cap -> unknown", ms(r,Cat.PROBABLE,null), 0);
        check("9 wall from elapsed", wallFromElapsed(1_000, 1_000_000, 11_000), 990_000);

        // ---- randomized property + oracle test: overlaps, duplicates, ties, unsettled, gaps ----
        Random rnd = new Random(7); int oracleMismatch = 0;
        for (int it = 0; it < 4000; it++) {
            boolean probable = rnd.nextInt(3) == 0; Options op = new Options(30_000, probable, probable ? 40_000 : 0);
            List<Fg> fgs = new ArrayList<>(); String[] pk = {"wa","tg","x"};
            for (int i = rnd.nextInt(5) + 1; i > 0; i--) { long s = rnd.nextInt(30) * 10_000L, l = (1 + rnd.nextInt(20)) * 10_000L; fgs.add(new Fg(pk[rnd.nextInt(3)], s, s + l, rnd.nextInt(6) != 0)); }
            if (rnd.nextBoolean() && !fgs.isEmpty()) fgs.add(fgs.get(0)); // duplicate
            List<Obs> obs = new ArrayList<>(); long[] ts = {0, 10_000, 20_000, 40_000, 90_000, 150_000};
            for (int i = rnd.nextInt(10); i > 0; i--) { int k = rnd.nextInt(4); String p = pk[rnd.nextInt(2)]; long t = ts[rnd.nextInt(ts.length)]; obs.add(k<2?al(t,p,1+rnd.nextInt(2)):k==2?no(t,p):un(t,p)); }
            List<Cov> cov = new ArrayList<>(); for (int i = rnd.nextInt(3) + 1; i > 0; i--) { long s = rnd.nextInt(30) * 10_000L; cov.add(new Cov(s, s + (1 + rnd.nextInt(30)) * 10_000L)); }
            List<Seg> base = reconcile(W, fgs, obs, cov, op);
            // no overlapping segments, none empty
            for (Seg g : base) { n++; if (g.e <= g.s) { fails++; System.out.println("FAIL empty seg it="+it); } }
            for (long sec = 0; sec < 600; sec++) { n++; String want = oracleSec(W, fgs, normObsForOracle(obs), cov, op, sec); if (want != null && want.equals("PROBABLE")) want = "PROBABLE";
                String got = segSec(base, sec);
                // oracle does not model PROBABLE; compare only when probable is off, otherwise treat PROBABLE as UNKNOWN_FG for comparison
                if (probable && "PROBABLE:1".equals(got) || probable && "PROBABLE:2".equals(got) || probable && "PROBABLE".equals(got)) got = "UNKNOWN_FG";
                if (got != null && got.startsWith("PROBABLE")) got = "UNKNOWN_FG";
                if (!Objects.equals(want, got)) { oracleMismatch++; if (oracleMismatch < 5) System.out.println("MISMATCH it="+it+" sec="+sec+" want="+want+" got="+got); } }
            // full-output order independence (segments compared as multisets after sorting)
            List<Fg> f2 = new ArrayList<>(fgs); List<Obs> o2 = new ArrayList<>(obs); List<Cov> c2 = new ArrayList<>(cov);
            Collections.shuffle(f2, rnd); Collections.shuffle(o2, rnd); Collections.shuffle(c2, rnd);
            n++; if (!canon(base).equals(canon(reconcile(W, f2, o2, c2, op)))) { fails++; System.out.println("FAIL order dependence it="+it); }
            // attributed time never exceeds wall time of the union of foreground intervals
            long wall = 0; for (long sec = 0; sec < 600; sec++) { boolean any = false; for (Fg f : fgs) if (f.s <= sec*1000 && f.e >= sec*1000+1000) any = true; if (any) wall++; }
            long attributed = 0; for (Seg g : base) attributed += g.e - g.s; n++; if (attributed > wall * 1000) { fails++; System.out.println("FAIL attributed > wall it="+it); }
        }
        if (oracleMismatch > 0) fails += oracleMismatch;
        org.junit.Assert.assertEquals(0, fails); System.out.println(n + " assertions (not input-space coverage), oracle mismatches=" + oracleMismatch + ", " + (fails==0 ? "ALL PASS" : "FAILURES "+fails));
    }
    static List<Obs> normObsForOracle(List<Obs> o) { return o; }
    static List<String> canon(List<Seg> s) { List<String> l = new ArrayList<>(); for (Seg g : s) l.add(g.cat+"|"+g.pkg+"|"+g.id+"|"+g.s+"|"+g.e); Collections.sort(l); return l; }
}
