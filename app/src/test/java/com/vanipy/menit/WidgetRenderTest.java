package com.vanipy.menit;

import static org.junit.Assert.*;
import android.app.AppOpsManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** Resource rendering checks only. Real host qualification is separate. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WidgetRenderTest {
    private int W = 660, H = 320; // ~4x2 cells at density 2

    private void snapshot(RemoteViews rv, Context c, String outName) throws Exception {
        FrameLayout parent = new FrameLayout(c);
        View v = rv.apply(c, parent);
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
        v.layout(0, 0, W, v.getMeasuredHeight());
        Bitmap bmp = Bitmap.createBitmap(W, v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        File out = new File(System.getProperty("menit.snapshotDir", "build/ui-snapshots"), outName);
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(out.length() > 300);
    }

    @Test
    @Config(qualifiers = "mdpi")
    public void allThirtyGridSpans() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        DemoMode.set(c, true);
        for (int columns = 1; columns <= 5; columns++) {
            for (int rows = 1; rows <= 6; rows++) {
                W = columns * 73 - 16;
                H = rows * 96 - 16;
                android.os.Bundle options = new android.os.Bundle();
                options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, W);
                options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, H);
                RemoteViews rv = MenitWidgetProvider.buildViews(c, options);
                View view = rv.apply(c, new FrameLayout(c));
                assertEquals(View.GONE, view.findViewById(R.id.widget_demo_badge).getVisibility());
                snapshot(rv, c, "span-" + columns + "x" + rows + ".png");
            }
        }
        DemoMode.set(c, false);
    }

    @Test
    @Config(qualifiers = "mdpi")
    public void counterAndCompactResize() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        DemoMode.set(c, true);
        android.os.Bundle options = new android.os.Bundle();
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 64);
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 64);
        W = 48; H = 48;
        snapshot(MenitWidgetProvider.buildViews(c, options), c, "widget-counter-48dp.png");
        W = 64; H = 64;
        snapshot(MenitWidgetProvider.buildViews(c, options), c, "widget-counter-64dp.png");
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 350);
        W = 350;
        snapshot(MenitWidgetProvider.buildViews(c, options), c, "widget-strip-350x64dp.png");
        options.putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180);
        H = 180;
        snapshot(MenitWidgetProvider.buildViews(c, options), c, "widget-expanded-350x180dp.png");
        DemoMode.set(c, false);
    }

    @Test
    public void widgetDemoMode() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        c.getSharedPreferences("menit_prefs", Context.MODE_PRIVATE).edit().putBoolean("demo_mode", true).commit();
        RemoteViews rv = MenitWidgetProvider.buildViews(c);
        snapshot(rv, c, "widget-demo.png");
        c.getSharedPreferences("menit_prefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void widgetNoAccess() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), "com.vanipy.menit", AppOpsManager.MODE_IGNORED);
        snapshot(MenitWidgetProvider.buildViews(c), c, "widget-no-access.png");
    }

    @Test
    public void widgetDemoNeverShowsRealUsage() throws Exception {
        Context c = RuntimeEnvironment.getApplication();
        // Usage access granted, real usage would be readable - demo must still win.
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        Shadows.shadowOf(ops).setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), "com.vanipy.menit", AppOpsManager.MODE_ALLOWED);
        c.getSharedPreferences("menit_prefs", Context.MODE_PRIVATE).edit().putBoolean("demo_mode", true).commit();
        FrameLayout parent = new FrameLayout(c);
        View v = MenitWidgetProvider.buildViews(c).apply(c, parent);
        android.widget.TextView badge = v.findViewById(R.id.widget_demo_badge);
        android.widget.TextView time = v.findViewById(R.id.widget_ai_time);
        assertEquals(View.GONE, badge.getVisibility());
        assertEquals("47 min", time.getText().toString());
        c.getSharedPreferences("menit_prefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void demoReportMatchesClaimedNumbers() {
        Report r = DemoData.report("com.vanipy.menit");
        assertEquals(192 * 60_000L, r.totalMs);
        assertEquals(47 * 60_000L, r.aiMs);
        assertEquals(132 * 60_000L, r.mixedMs);
        assertEquals(3, r.aiRows.size());
    }
}
