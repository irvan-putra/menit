package com.vanipy.menit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Home-screen widget: today's AI time at a glance. Tap opens menit. */
public class MenitWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) mgr.updateAppWidget(id, buildViews(context, mgr.getAppWidgetOptions(id)));
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, MenitWidgetProvider.class));
        for (int id : ids) mgr.updateAppWidget(id, buildViews(context, mgr.getAppWidgetOptions(id)));
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager mgr, int id, Bundle options) {
        mgr.updateAppWidget(id, buildViews(c, options));
    }

    static RemoteViews buildViews(Context c, Bundle options) {
        int height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 48);
        return buildViews(c, height < 110, options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 48) < 100);
    }

    static RemoteViews buildViews(Context c) { return buildViews(c, false); }

    static RemoteViews buildViews(Context c, boolean compact) { return buildViews(c, compact, false); }

    static RemoteViews buildViews(Context c, boolean compact, boolean tiny) {
        RemoteViews v = new RemoteViews(c.getPackageName(), tiny ? R.layout.widget_menit_tiny : compact ? R.layout.widget_menit_compact : R.layout.widget_menit);
        boolean demo = DemoMode.isOn(c);
        v.setViewVisibility(R.id.widget_demo_badge, View.GONE);

        Report r = null;
        if (demo) {
            r = DemoData.report(c.getPackageName());
        } else if (UsageReader.hasAccess(c)) {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
            Map<String, Long> m = new HashMap<>();
            try {
                m = UsageReader.read(c, cal.getTimeInMillis(), System.currentTimeMillis());
            } catch (SecurityException ignored) {
            }
            r = new Report(m, c.getPackageName());
        }

        if (r == null) {
            v.setTextViewText(R.id.widget_ai_time, "Open menit");
            v.setTextViewText(R.id.widget_detail, compact ? "Usage access off - tap to set up" : "Usage access is off, so today's AI time cannot be read.");
            v.setViewVisibility(R.id.widget_progress, View.GONE);
            v.setTextViewText(R.id.widget_apps, "");
        } else {
            int pct = Report.percent(r.aiMs, r.totalMs);
            v.setTextViewText(R.id.widget_ai_time, Report.format(r.aiMs));
            v.setTextViewText(R.id.widget_detail, pct < 0
                    ? "No screen time recorded yet today"
                    : compact ? pct + "% of " + Report.format(r.totalMs) : "with AI, of " + Report.format(r.totalMs) + " on the phone (" + pct + "%)");
            v.setProgressBar(R.id.widget_progress, 100, Math.max(pct, 0), false);
            v.setViewVisibility(R.id.widget_progress, View.VISIBLE);
            StringBuilder apps = new StringBuilder();
            for (int i = 0; i < r.aiRows.size() && i < 3; i++) {
                if (i > 0) apps.append("  ·  ");
                String name = AiCatalog.nameOf(r.aiRows.get(i).pkg);
                apps.append(name == null ? r.aiRows.get(i).pkg : name)
                    .append(' ').append(Report.format(r.aiRows.get(i).ms));
            }
            if (apps.length() == 0) apps.append("No AI app use found today");
            v.setTextViewText(R.id.widget_apps, apps.toString());
        }

        if (tiny) {
            v.setContentDescription(R.id.widget_root, r == null ? "menit: usage access off. Tap to set up." : "menit: " + Report.format(r.aiMs) + " with AI, of " + Report.format(r.totalMs) + " on the phone. Tap for details.");
            v.setTextViewText(R.id.widget_ai_time, r == null ? "Off" : String.valueOf(r.aiMs / 60000L));
            v.setTextViewText(R.id.widget_detail, r == null ? "access" : "AI min");
            v.setViewVisibility(R.id.widget_progress, View.GONE);
        }

        PendingIntent pi = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        return v;
    }
}
