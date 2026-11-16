package com.vanipy.menit;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class SessionizerTest {
    private static final long M = 60_000L;

    private static Event e(String p, int t, long min) { return new Event(p, t, min * M); }

    @Test public void simpleSession() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.PAUSED, 10)), 0, 100 * M);
        assertEquals(10 * M, (long) r.get("a"));
    }

    @Test public void switchingAppsEndsPrevious() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("b", Event.RESUMED, 5), e("b", Event.PAUSED, 8)), 0, 100 * M);
        assertEquals(5 * M, (long) r.get("a"));
        assertEquals(3 * M, (long) r.get("b"));
    }

    @Test public void screenOffEndsSession() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("x", Event.SCREEN_OFF, 4)), 0, 100 * M);
        assertEquals(4 * M, (long) r.get("a"));
    }

    @Test public void staleOpenSessionCountsToWindowEnd() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 90)), 0, 100 * M);
        assertEquals(10 * M, (long) r.get("a"));
    }

    @Test public void sessionStartingBeforeWindowIsClipped() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.PAUSED, 30)), 20 * M, 100 * M);
        assertEquals(10 * M, (long) r.get("a"));
    }

    @Test public void pauseOfOtherPackageIgnored() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("b", Event.PAUSED, 3), e("a", Event.PAUSED, 6)), 0, 100 * M);
        assertEquals(6 * M, (long) r.get("a"));
    }

    @Test public void reportSplitsAiMixedOther() {
        Map<String, Long> m = new HashMap<>();
        m.put("com.openai.chatgpt", 30 * M);
        m.put("com.whatsapp", 20 * M);
        m.put("com.example.game", 50 * M);
        m.put("com.vanipy.menit", 9 * M);
        Report r = new Report(m, "com.vanipy.menit");
        assertEquals(100 * M, r.totalMs);
        assertEquals(30 * M, r.aiMs);
        assertEquals(20 * M, r.mixedMs);
        assertEquals(30, Report.percent(r.aiMs, r.totalMs));
        assertEquals(-1, Report.percent(0, 0));
    }

    @Test public void formatting() {
        assertEquals("<1 min", Report.format(20_000));
        assertEquals("45 min", Report.format(45 * M));
        assertEquals("1 h 5 min", Report.format(65 * M));
    }

    @Test public void outOfOrderEventsAreSorted() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.PAUSED, 10), e("a", Event.RESUMED, 0)), 0, 100 * M);
        assertEquals(10 * M, (long) r.get("a"));
    }

    @Test public void shutdownEndsSession() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("x", Event.SHUTDOWN, 7)), 0, 100 * M);
        assertEquals(7 * M, (long) r.get("a"));
    }

    @Test public void stoppedEndsSessionLikePaused() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.STOPPED, 2)), 0, 100 * M);
        assertEquals(2 * M, (long) r.get("a"));
    }

    @Test public void noEventsNoTime() {
        assertEquals(0, Sessionizer.foregroundMs(java.util.Collections.<Event>emptyList(), 0, M).size());
    }

    @Test public void sessionFullyOutsideWindowIgnored() {
        Map<String, Long> r = Sessionizer.foregroundMs(Arrays.asList(
            e("a", Event.RESUMED, 0), e("a", Event.PAUSED, 5)), 10 * M, 20 * M);
        assertEquals(null, r.get("a"));
    }

    @Test public void catalogHasNoOverlapAndKnowsKinds() {
        assertEquals(AiCatalog.Kind.AI, AiCatalog.kindOf("com.anthropic.claude"));
        assertEquals(AiCatalog.Kind.MIXED, AiCatalog.kindOf("com.whatsapp"));
        assertEquals(AiCatalog.Kind.OTHER, AiCatalog.kindOf("com.example.game"));
        assertEquals(null, AiCatalog.nameOf("com.example.game"));
    }
}
