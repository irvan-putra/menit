package com.vanipy.menit;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int INK = Color.rgb(24, 28, 36);
    private static final int MUTED = Color.rgb(96, 104, 118);
    private static final int ACCENT = Color.rgb(31, 111, 235);
    private static final int AMBER = Color.rgb(196, 134, 20);

    private LinearLayout root;
    private boolean week = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        root.setPadding(p, dp(28), p, dp(28));
        sv.addView(root);
        sv.setBackgroundColor(Color.WHITE);
        setContentView(sv);
    }

    @Override protected void onResume() {
        super.onResume();
        render();
    }

    private void render() { render(false); }

    /** forcePermission: skip the access check (used once after a SecurityException, so no loop). */
    private void render(boolean forcePermission) {
        root.removeAllViews();
        text("menit", 28, INK, true);
        text("How much of your phone time goes to AI", 15, MUTED, false);
        space(16);
        if (forcePermission || !UsageReader.hasAccess(this)) {
            renderPermission();
            return;
        }
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.addView(tab("Today", !week, false));
        tabs.addView(tab("Last 7 days", week, true));
        root.addView(tabs);
        space(16);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        long todayStart = cal.getTimeInMillis();
        int days = week ? 7 : 1;
        Map<String, Long> sum = new HashMap<>();
        StringBuilder perDay = new StringBuilder();
        for (int i = days - 1; i >= 0; i--) {
            Calendar d = (Calendar) cal.clone();
            d.add(Calendar.DAY_OF_YEAR, -i);
            long s = d.getTimeInMillis();
            Calendar n = (Calendar) d.clone();
            n.add(Calendar.DAY_OF_YEAR, 1);
            long e = Math.min(n.getTimeInMillis(), now);
            Map<String, Long> m;
            try { m = UsageReader.read(this, s, e); }
            catch (SecurityException ex) { render(true); return; }
            for (Map.Entry<String, Long> en : m.entrySet()) sum.merge(en.getKey(), en.getValue(), Long::sum);
            if (week) {
                Report dr = new Report(m, getPackageName());
                perDay.append(DateFormat.format("EEE d MMM", s)).append("   ")
                      .append(Report.format(dr.aiMs)).append("\n");
            }
        }
        Report r = new Report(sum, getPackageName());

        text(Report.format(r.aiMs), 40, ACCENT, true);
        int pct = Report.percent(r.aiMs, r.totalMs);
        text(pct < 0 ? "No screen time recorded yet"
                : "in dedicated AI apps (estimate), " + pct + "% of your " + Report.format(r.totalMs) + " of screen time",
                15, INK, false);
        space(20);

        if (r.aiRows.isEmpty()) {
            text("No AI app use found for this period.", 15, MUTED, false);
        } else {
            for (Report.Row row : r.aiRows) bar(row, r.aiMs, ACCENT);
        }

        if (week) {
            space(20);
            text("Per day (AI apps)", 14, MUTED, true);
            text(perDay.toString().trim(), 15, INK, false);
        }

        space(24);
        text("May include AI: " + Report.format(r.mixedMs), 18, AMBER, true);
        text("Browsers and chat apps can host AI (a chatbot website, a bot in a chat), "
                + "but Android does not tell this app what you did inside them. "
                + "This time is shown separately and is not added to the AI total.", 13, MUTED, false);
        space(8);
        for (Report.Row row : r.mixedRows) bar(row, Math.max(r.mixedMs, 1), AMBER);

        space(24);
        text("How this is measured", 14, INK, true);
        text("An estimate from Android's usage events: time an app was the one on screen. "
                + "Screen off ends the session. Android's records can have gaps, and how many days "
                + "are kept depends on the phone. Split screen and pop-up windows count only the last app touched. "
                + "Apps in the built-in list count as AI, but an AI app also has non-AI screens "
                + "(settings, account pages, a built-in browser), so even that number is not exact. "
                + "Time on screen is not attention. "
                + "This app has no network permission and no upload, export or sharing feature, and backups are off.", 13, MUTED, false);
        space(12);
        text("Version " + version(), 12, MUTED, false);
    }

    private void renderPermission() {
        text("One permission needed", 20, INK, true);
        space(8);
        text("menit reads which app was on screen and for how long, using Android's Usage access setting. "
                + "It sees app names and times only: no messages, no page contents, no screenshots.\n\n"
                + "The app asks for no network permission and has no upload, export or sharing feature. It stores nothing on the phone.\n\n"
                + "You can turn it off at any time in the same setting.", 15, INK, false);
        space(20);
        Button go = new Button(this);
        go.setText("Open Usage access settings");
        go.setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(i); }
            catch (Exception e) { startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)); }
        });
        root.addView(go);
        space(12);
        text("In the list, pick menit and switch on \"Allow usage tracking\", then come back.", 13, MUTED, false);
    }

    private View tab(String label, boolean selected, boolean isWeek) {
        Button t = new Button(this);
        t.setText(label);
        t.setAllCaps(false);
        t.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        t.setOnClickListener(v -> { week = isWeek; render(); });
        return t;
    }

    private void bar(Report.Row row, long whole, int color) {
        String name = AiCatalog.nameOf(row.pkg);
        if (name == null) name = row.pkg;
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        TextView n = new TextView(this);
        n.setText(name); n.setTextColor(INK); n.setTextSize(15);
        TextView t = new TextView(this);
        t.setText(Report.format(row.ms)); t.setTextColor(MUTED); t.setTextSize(15);
        t.setGravity(Gravity.END);
        line.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        line.addView(t, new LinearLayout.LayoutParams(-2, -2));
        root.addView(line);
        LinearLayout track = new LinearLayout(this);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackgroundColor(Color.rgb(236, 239, 244));
        View fill = new View(this);
        fill.setBackgroundColor(color);
        float frac = Math.max(0.02f, Math.min(1f, row.ms / (float) whole));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), frac));
        track.addView(new View(this), new LinearLayout.LayoutParams(0, dp(6), 1f - frac));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(6));
        lp.bottomMargin = dp(10); lp.topMargin = dp(3);
        root.addView(track, lp);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        root.addView(t);
        return t;
    }

    private void space(int dp) { root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dp))); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private String version() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (PackageManager.NameNotFoundException e) { return "?"; }
    }
}
