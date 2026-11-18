package com.vanipy.menit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Made-up usage profile for showing menit to friends ("Rafi"). None of it is
 * real usage: the numbers below are fixed sample values bundled with the app.
 * Demo mode is opt-in from the app screen and is labeled DEMO here and on the
 * widget, so a demo can never be mistaken for the owner's real screen time.
 */
public final class DemoData {
    private static final long M = 60_000L;

    private DemoData() {}

    /** Per-package foreground time for one made-up day: 3 h 12 min total, 47 min AI. */
    public static Map<String, Long> usageMap() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("com.openai.chatgpt", 22 * M);
        m.put("com.anthropic.claude", 15 * M);
        m.put("com.google.android.apps.bard", 10 * M);
        m.put("com.whatsapp", 58 * M);
        m.put("com.android.chrome", 41 * M);
        m.put("com.instagram.android", 33 * M);
        m.put("com.google.android.youtube", 13 * M);
        return m;
    }

    public static Report report(String ownPackage) {
        return new Report(usageMap(), ownPackage);
    }
}
