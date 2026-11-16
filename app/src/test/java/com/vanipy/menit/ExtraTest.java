package com.vanipy.menit;
import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class ExtraTest {
    private static final long M = 60_000L;
    private static Event e(String p, int t, long min) { return new Event(p, t, min * M); }

    @Test public void resumeBeforeWindowOnlyCountsToWindowEnd() {
        // open session starting before window, no further events: clipped [20,100)
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(e("a", Event.RESUMED, 0)), 20*M, 100*M);
        assertEquals(80*M, (long) r.get("a"));
    }
    @Test public void sessionSpanningWholeWindowClippedBothSides() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.PAUSED, 150)), 20*M, 100*M);
        assertEquals(80*M, (long) r.get("a"));
    }
    @Test public void activitySwitchInsideSameAppIsContinuous() {
        // two RESUMED same package (activity move): no gap, no double count
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.RESUMED, 10), e("a", Event.PAUSED, 25)), 0, 100*M);
        assertEquals(25*M, (long) r.get("a"));
    }
    @Test public void screenOnOffCycleWithNewResume() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("x", Event.SCREEN_OFF, 5),
            e("x", Event.SCREEN_ON, 8), e("a", Event.RESUMED, 8), e("a", Event.PAUSED, 12)), 0, 100*M);
        assertEquals(9*M, (long) r.get("a")); // 0-5 + 8-12
    }
    @Test public void screenOnWithoutResumeDoesNotInventTime() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("x", Event.SCREEN_OFF, 5), e("x", Event.SCREEN_ON, 8)), 0, 100*M);
        assertEquals(5*M, (long) r.get("a"));
    }
    @Test public void equalTimestampResumeResume() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 5), e("b", Event.RESUMED, 5), e("b", Event.PAUSED, 9)), 0, 100*M);
        assertNull(r.get("a"));
        assertEquals(4*M, (long) r.get("b"));
    }
    @Test public void pausedWithoutResumeIsNoop() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(e("a", Event.PAUSED, 5)), 0, 100*M);
        assertTrue(r.isEmpty());
    }
    @Test public void latePauseOfOtherPackageDoesNotCloseCurrent() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("b", Event.RESUMED, 5), e("a", Event.PAUSED, 6), e("b", Event.PAUSED, 10)), 0, 100*M);
        assertEquals(5*M, (long) r.get("a"));
        assertEquals(5*M, (long) r.get("b"));
    }
    @Test public void pauseThenResumeSameAppSplits() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.PAUSED, 4), e("a", Event.RESUMED, 6), e("a", Event.PAUSED, 10)), 0, 100*M);
        assertEquals(8*M, (long) r.get("a"));
    }
    @Test public void reportMixedNeverInAiTotal() {
        Map<String, Long> m = new HashMap<>();
        m.put("com.android.chrome", 60*M);
        Report r = new Report(m, "com.vanipy.menit");
        assertEquals(0, r.aiMs);
        assertEquals(60*M, r.mixedMs);
        assertEquals(0, Report.percent(r.aiMs, r.totalMs));
    }
    @Test public void formatBoundaries() {
        assertEquals("<1 min", Report.format(29_999)); // rounds to nearest minute: under 30 s is <1
        assertEquals("1 min", Report.format(30_000));
        assertEquals("1 min", Report.format(60_000));
        assertEquals("1 h 0 min", Report.format(60*M)); // 60 min shows as hours
        assertEquals("2 h 0 min", Report.format(120*M));
    }
    @Test public void reverseOrderInputSortedSafely() {
        List<Event> ev = new ArrayList<>();
        for (int i = 10; i >= 1; i--) ev.add(e("a", i % 2 == 0 ? Event.RESUMED : Event.PAUSED, i));
        // just ensure no exception and no negative times
        Map<String, Long> r = Sessionizer.foregroundMs(ev, 0, 100*M);
        for (Long v : r.values()) assertTrue(v >= 0);
    }
    @Test public void zeroLengthSessionDropped() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 5), e("b", Event.RESUMED, 5), e("b", Event.PAUSED, 5)), 0, 100*M);
        assertTrue(r.isEmpty());
    }
}
