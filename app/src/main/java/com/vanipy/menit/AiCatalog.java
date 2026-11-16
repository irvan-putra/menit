package com.vanipy.menit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which packages count as AI and which are "mixed" (AI may happen inside,
 * but the app also does other things). Mixed apps are never added to the AI
 * total: usage stats cannot tell what you did inside them.
 */
public final class AiCatalog {
    public enum Kind { AI, MIXED, OTHER }

    private static final Map<String, String> AI = new LinkedHashMap<>();
    private static final Map<String, String> MIXED = new LinkedHashMap<>();

    static {
        AI.put("com.openai.chatgpt", "ChatGPT");
        AI.put("com.google.android.apps.bard", "Gemini");
        AI.put("com.anthropic.claude", "Claude");
        AI.put("com.microsoft.copilot", "Microsoft Copilot");
        AI.put("ai.perplexity.app.android", "Perplexity");
        AI.put("ai.x.grok", "Grok");
        AI.put("com.deepseek.chat", "DeepSeek");
        AI.put("ai.character.app", "Character.AI");
        AI.put("com.poe.android", "Poe");
        AI.put("ai.mistral.chat", "Mistral Vibe (ex-Le Chat)");

        MIXED.put("com.android.chrome", "Chrome");
        MIXED.put("com.sec.android.app.sbrowser", "Samsung Internet");
        MIXED.put("org.mozilla.firefox", "Firefox");
        MIXED.put("com.microsoft.emmx", "Edge");
        MIXED.put("com.brave.browser", "Brave");
        MIXED.put("com.opera.browser", "Opera");
        MIXED.put("com.whatsapp", "WhatsApp");
        MIXED.put("com.whatsapp.w4b", "WhatsApp Business");
        MIXED.put("org.telegram.messenger", "Telegram");
        MIXED.put("com.google.android.googlequicksearchbox", "Google app");
        MIXED.put("com.google.android.apps.messaging", "Messages");
        MIXED.put("com.instagram.android", "Instagram");
        MIXED.put("com.snapchat.android", "Snapchat");
        MIXED.put("com.samsung.android.bixby.agent", "Bixby");
    }

    private AiCatalog() {}

    public static Kind kindOf(String pkg) {
        if (AI.containsKey(pkg)) return Kind.AI;
        if (MIXED.containsKey(pkg)) return Kind.MIXED;
        return Kind.OTHER;
    }

    /** Display name from the catalog, or null if the package is not listed. */
    public static String nameOf(String pkg) {
        String n = AI.get(pkg);
        return n != null ? n : MIXED.get(pkg);
    }
}
