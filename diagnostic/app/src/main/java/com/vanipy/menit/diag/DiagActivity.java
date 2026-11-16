package com.vanipy.menit.diag;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Synthetic-only diagnostic. No real chats, no other apps. All chat text on screen is fixed fake text. */
public class DiagActivity extends Activity {
    private LinearLayout root;
    private DiagStore store;
    private long trialStart = -1, trialEnd = -1;
    private String screen = "home";   // home | chatA | chatB | unrec
    private TextView headerView;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = DiagStore.get(this);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(18); root.setPadding(p, dp(24), p, dp(24));
        sv.addView(root); sv.setBackgroundColor(Color.WHITE); setContentView(sv);
        SharedPreferences sp = getSharedPreferences("diag", MODE_PRIVATE);
        if (sp.getInt("tb", -2) == store.boot()) { trialStart = sp.getLong("ts", -1); trialEnd = sp.getLong("te", -1); }  // elapsed ms; only valid in the same boot
    }
    @Override protected void onResume() { super.onResume(); store.fgStart(DiagStore.el()); render(); }
    @Override protected void onPause() { store.fgEnd(DiagStore.el()); super.onPause(); }

    private void render() {
        root.removeAllViews(); headerView = null;
        if (screen.equals("home")) home(); else chat();
    }

    private void home() {
        text("Menit Diagnostic", 24, true);
        text("Synthetic test only. It never reads your chats or other apps.", 14, false);
        space(10);
        text("What the optional reader does", 15, true);
        text("If you turn on the accessibility service, it reads only this app's own fake chat screens, "
            + "compares the header with three fake names, and stores only a label and a time. It cannot be "
            + "pointed at other apps from here. Data stays on this device (no network permission, backups off).", 13, false);
        boolean on = isServiceEnabled();
        text("Reader: " + (on ? "ON" : "OFF") + "  |  Usage access: " + (UsageCompare.hasAccess(this) ? "ON" : "OFF"), 14, true);
        button("Open accessibility settings (turn reader on/off)", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        button("Open usage access settings (optional)", v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        final SharedPreferences sp = getSharedPreferences("diag", MODE_PRIVATE);
        CheckBox poll = new CheckBox(this); poll.setText("Re-read header every 5 s while a fake chat is open (test)");
        poll.setChecked(sp.getBoolean("poll", false));
        poll.setOnCheckedChangeListener((c, v) -> sp.edit().putBoolean("poll", v).apply()); root.addView(poll);
        space(12);
        text("Trial", 15, true);
        text(trialStart < 0 ? "No trial running." : trialEnd < trialStart ? "Trial running since " + fmt(DiagStore.wallOf(trialStart)) : "Last trial: " + (trialEnd - trialStart) / 1000 + " s", 14, false);
        button("Start trial (note your stopwatch)", v -> { trialStart = DiagStore.el(); trialEnd = -1; sp.edit().putInt("tb", store.boot()).putLong("ts", trialStart).putLong("te", -1).apply(); render(); });
        button("Stop trial", v -> { trialEnd = DiagStore.el(); sp.edit().putLong("te", trialEnd).apply(); render(); });
        space(8);
        text("Open a fake chat", 15, true);
        button("Chat A (on the AI list)", v -> { screen = "chatA"; render(); });
        button("Chat B (not on the list)", v -> { screen = "chatB"; render(); });
        button("Unrecognised screen", v -> { screen = "unrec"; render(); });
        space(8);
        text("Suggested trials: leave Chat A open untouched for 60 s; scroll for 60 s; switch A to B; "
            + "go home mid-trial; lock the screen; wait 3 min; force-stop and reopen. Split-screen is not covered here.", 12, false);
        space(8);
        text("Report", 15, true);
        text(trialStart < 0 ? "Run a trial first." : report(), 12, false);
        text("Saved archives on this device: " + store.archiveCount(), 12, false);
        button("Archive current data and start fresh (keeps a copy)", v -> { store.archive(); sp.edit().remove("ts").remove("te").remove("tb").apply(); trialStart = trialEnd = -1; render(); });
        button("Permanently delete archived copies", v -> new android.app.AlertDialog.Builder(this)
            .setMessage("This permanently deletes " + store.archiveCount() + " archived copies from this device. It cannot be undone. Uninstalling the app or clearing its data also removes them.")
            .setPositiveButton("Delete permanently", (d, w) -> { store.deleteArchives(); render(); })
            .setNegativeButton("Keep", null).show());
    }

    private void chat() {
        final String title = screen.equals("chatA") ? "Synthetic chat A" : screen.equals("chatB") ? "Synthetic chat B" : "Synthetic screen";
        TextView hv = text(title, 20, true); hv.setId(screen.equals("unrec") ? R.id.decoy_header : R.id.chat_header);
        for (int i = 1; i <= 25; i++) text("Synthetic message " + i + ": fixed filler text for the test.", 14, false);
        button("Back", v -> { screen = "home"; render(); });
    }

    @Override public void onBackPressed() { if (!screen.equals("home")) { screen = "home"; render(); } else super.onBackPressed(); }

    private String report() {
        long a = trialStart, b = trialEnd >= trialStart ? trialEnd : DiagStore.el();   // elapsed-realtime domain
        boolean running = trialEnd < trialStart;
        List<Reconciler.Fg> fgs = new ArrayList<>();
        for (long[] f : store.fg) { long s = Math.max(a, f[0]), e = Math.min(b, f[1]); if (e > s) fgs.add(new Reconciler.Fg(DiagStore.PKG, s, e, f[2] == 1)); }   // killed intervals stay PENDING
        if (store.openFgStart() >= 0) fgs.add(new Reconciler.Fg(DiagStore.PKG, Math.max(a, store.openFgStart()), b, !running));
        List<Reconciler.Obs> os = new ArrayList<>(); for (Reconciler.Obs o : store.obs) if (o.t >= a && o.t <= b) os.add(o);
        List<Reconciler.Cov> cov = store.coverage(3 * DiagService.HEARTBEAT_MS);
        Set<String> w = new HashSet<>(Collections.singletonList(DiagStore.PKG));
        List<Reconciler.Seg> segs = Reconciler.reconcile(w, fgs, os, cov, Reconciler.Options.defaults());
        StringBuilder sb = new StringBuilder();
        sb.append("Trial ").append((b - a) / 1000.0).append(" s").append(running ? " (running)" : "").append("\n");
        sb.append("Foreground by app lifecycle: ").append(sum(fgs)).append(" s\n");
        for (Reconciler.Cat c : Reconciler.Cat.values()) sb.append(c).append(": ").append(Reconciler.ms(segs, c, null) / 1000.0).append(" s\n");
        sb.append("Observations in trial: ").append(os.size()).append("\n");
        sb.append("Log lines skipped as malformed: ").append(store.skipped).append("; foreground intervals lost to a kill (shown as PENDING): ").append(store.killed).append("\n");
        sb.append(UsageCompare.describe(this, DiagStore.wallOf(a), DiagStore.wallOf(b))).append("\n");
        sb.append(clockLine()).append("\n");
        sb.append("Grace window is a placeholder (30 s). Output is an estimate, not exact attention time.");
        return sb.toString();
    }
    private double sum(List<Reconciler.Fg> f) { long x = 0; for (Reconciler.Fg g : f) x += g.e - g.s; return x / 1000.0; }

    /** Which clock does AccessibilityEvent.getEventTime follow? Compare it with uptime and elapsed at receipt. */
    private String clockLine() {
        if (store.clocks.isEmpty()) return "Clock check: no accessibility events yet.";
        long n = 0, nearUp = 0, nearEl = 0; long maxLag = 0;
        for (long[] c : store.clocks) { n++; long dUp = Math.abs(c[1] - c[0]), dEl = Math.abs(c[2] - c[0]); if (dUp <= dEl) nearUp++; else nearEl++; maxLag = Math.max(maxLag, Math.min(dUp, dEl)); }
        return "Clock check: eventTime closer to uptime in " + nearUp + "/" + n + ", to elapsed-realtime in " + nearEl + "/" + n
            + ", worst delivery lag " + maxLag + " ms. (Differs only after deep sleep, so a long idle test is needed to tell them apart.)";
    }

    private boolean isServiceEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(DiagStore.PKG + "/");
    }
    private static String fmt(long t) { return new java.text.SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(t)); }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int sp, boolean bold) { TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.rgb(24, 28, 36)); if (bold) t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(0, dp(3), 0, dp(3)); root.addView(t); return t; }
    private void button(String s, View.OnClickListener l) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(l); root.addView(b); }
    private void space(int d) { View v = new View(this); root.addView(v, new LinearLayout.LayoutParams(1, dp(d))); }
}
