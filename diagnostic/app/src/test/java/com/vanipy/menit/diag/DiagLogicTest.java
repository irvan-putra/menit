package com.vanipy.menit.diag;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

/** Synthetic checks for the throttle and the log parser. No Android classes. */
public class DiagLogicTest {
    private static final String PKG = "com.vanipy.menit.diag";

    @Test public void throttleReadsTrailingEvent() {
        Throttle t = new Throttle(1000);
        assertEquals(Throttle.Act.READ_NOW, t.onEvent(0));
        assertEquals(Throttle.Act.SCHEDULE, t.onEvent(500));      // A to B inside the gap schedules a read
        assertEquals(1000, t.dueAt());
        assertEquals(Throttle.Act.NONE, t.onEvent(700));          // only one pending read
        assertTrue(t.onTimer(1000));
        assertFalse(t.onTimer(1001));
    }
    @Test public void throttleEventAfterDueMakesTimerNoop() {
        Throttle t = new Throttle(1000); t.onEvent(0); t.onEvent(500);
        assertEquals(Throttle.Act.READ_NOW, t.onEvent(1200));
        assertFalse(t.onTimer(1200));
    }

    /** Regression: A then B within 1 s. With the old drop-and-forget throttle B was never read and A got the whole grace window. */
    @Test public void switchWithinOneSecondIsNotCreditedToA() {
        Set<String> w = new HashSet<>(Collections.singletonList(PKG));
        List<Reconciler.Fg> fg = Collections.singletonList(new Reconciler.Fg(PKG, 0, 60_000, true));
        List<Reconciler.Cov> cov = Collections.singletonList(new Reconciler.Cov(0, 100_000));
        // fixed behaviour: A at 0, trailing read of B at 1000
        List<Reconciler.Obs> fixed = Arrays.asList(new Reconciler.Obs(0, PKG, Reconciler.Label.ALLOWLISTED, 1), new Reconciler.Obs(1000, PKG, Reconciler.Label.NOT_ALLOWLISTED, -1));
        List<Reconciler.Seg> r = Reconciler.reconcile(w, fg, fixed, cov, Reconciler.Options.defaults());
        assertEquals(1000, Reconciler.ms(r, Reconciler.Cat.RECOGNISED, null));
        // old behaviour for contrast: B dropped, A stays active for the grace window
        List<Reconciler.Obs> old = Collections.singletonList(new Reconciler.Obs(0, PKG, Reconciler.Label.ALLOWLISTED, 1));
        r = Reconciler.reconcile(w, fg, old, cov, Reconciler.Options.defaults());
        assertEquals(30_000, Reconciler.ms(r, Reconciler.Cat.RECOGNISED, null));
    }

    @Test public void malformedLineDoesNotDropLaterLines() {
        List<String> lines = Arrays.asList("S,5,100,9", "O,5,200,ALLOWLISTED,1", "O,5,abc,ALLOWLISTED,1", "garbage", "O,5,300,NOT_ALLOWLISTED,-1", "H,5,310", "O,5,400,BOGUS,1", "H,5,500");
        LogParser p = LogParser.parse(lines, 5, PKG);
        assertEquals(2, p.obs.size());
        assertEquals(2, p.heartbeats.size());
        assertEquals(3, p.skipped);
    }
    @Test public void otherBootLinesAreIgnoredNotCounted() {
        LogParser p = LogParser.parse(Arrays.asList("O,4,200,ALLOWLISTED,1", "H,4,300", "H,5,10"), 5, PKG);
        assertEquals(0, p.obs.size()); assertEquals(1, p.heartbeats.size()); assertEquals(0, p.skipped);
    }
    @Test public void unmatchedForegroundStartBecomesKilledPendingInterval() {
        List<String> lines = Arrays.asList("S,5,0,1", "FS,5,1000", "H,5,5000", "H,5,10000", "S,5,50000,2", "FS,5,51000", "F,5,51000,60000");
        LogParser p = LogParser.parse(lines, 5, PKG);
        assertEquals(2, p.fg.size());
        assertArrayEquals(new long[]{1000, 10000, 0}, p.fg.get(0));   // ended at last sign of life, flagged not settled
        assertArrayEquals(new long[]{51000, 60000, 1}, p.fg.get(1));
        assertEquals(1, p.killed);
    }
    @Test public void killedIntervalIsPendingInReconciler() {
        Set<String> w = new HashSet<>(Collections.singletonList(PKG));
        List<Reconciler.Fg> fg = Collections.singletonList(new Reconciler.Fg(PKG, 1000, 10000, false));
        List<Reconciler.Obs> o = Collections.singletonList(new Reconciler.Obs(1000, PKG, Reconciler.Label.ALLOWLISTED, 1));
        List<Reconciler.Seg> r = Reconciler.reconcile(w, fg, o, Collections.singletonList(new Reconciler.Cov(0, 20000)), Reconciler.Options.defaults());
        assertEquals(9000, Reconciler.ms(r, Reconciler.Cat.PENDING, null)); assertEquals(0, Reconciler.ms(r, Reconciler.Cat.RECOGNISED, null));
    }

    /** Archive while in the foreground writes a fresh S then FS; a trial in the same session then keeps its interval. */
    @Test public void sessionSurvivesArchive() {
        List<String> afterArchive = Arrays.asList("S,5,1000,9", "FS,5,1000", "O,5,1500,ALLOWLISTED,1", "H,5,2000");
        LogParser p = LogParser.parse(afterArchive, 5, PKG);
        assertEquals(1, p.obs.size()); assertEquals(1, p.fg.size());
        assertArrayEquals(new long[]{1000, 2000, 0}, p.fg.get(0));
        // and a clean close later settles it
        p = LogParser.parse(Arrays.asList("S,5,1000,9", "FS,5,1000", "O,5,1500,ALLOWLISTED,1", "F,5,1000,4000"), 5, PKG);
        assertArrayEquals(new long[]{1000, 4000, 1}, p.fg.get(0)); assertEquals(0, p.killed);
    }
    /** Rotation puts the old FS in the archive and a fresh FS in the new file; a kill with no later lines still counts. */
    @Test public void zeroLengthKilledSessionIsCounted() {
        LogParser p = LogParser.parse(Arrays.asList("FS,5,7000"), 5, PKG);
        assertEquals(1, p.killed); assertEquals(0, p.fg.size());
        p = LogParser.parse(Arrays.asList("FS,5,7000", "S,5,9000,1"), 5, PKG);   // restart time is not a sign of life, so no interval is credited
        assertEquals(1, p.killed); assertEquals(0, p.fg.size());
    }
}
