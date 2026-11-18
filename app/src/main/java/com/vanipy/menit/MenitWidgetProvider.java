package com.vanipy.menit;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/** Home-screen widget: today's AI time at a glance. Tap opens menit. */
public class MenitWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] appWidgetIds) {
        for (int id : appWidgetIds) mgr.updateAppWidget(id, buildViews(context));
    }

    public static void updateAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(context, MenitWidgetProvider.class));
        if (ids.length > 0) mgr.updateAppWidget(ids, null);
        for (int id : ids) mgr.updateAppWidget(id, buildViews(context));
    }

    static RemoteViews buildViews(Context c) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_menit);
        boolean demo = DemoMode.isOn(c);
        v.setViewVisibility(R.id.widget_demo_badge, demo ? View.VISIBLE : View.GONE);

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
            v.setTextViewText(R.id.widget_detail, "Usage access is off, so today's AI time cannot be read.");
            v.setViewVisibility(R.id.widget_progress, View.GONE);
            v.setTextViewText(R.id.widget_apps, "");
        } else {
            int pct = Report.percent(r.aiMs, r.totalMs);
            v.setTextViewText(R.id.widget_ai_time, Report.format(r.aiMs));
            v.setTextViewText(R.id.widget_detail, pct < 0
                    ? "No screen time recorded yet today"
                    : "with AI, of " + Report.format(r.totalMs) + " on the phone (" + pct + "%)");
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

        PendingIntent pi = PendingIntent.getActivity(c, 0,
                new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        return v;
    }
}
