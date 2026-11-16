package com.vanipy.menit;

import static org.junit.Assert.*;
import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAppOpsManager;
import org.robolectric.shadows.ShadowUsageStatsManager;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiRenderTest {
    private static final int W = 720, H = 1480;
    private static final long M = 60_000L;

    private void setUsageMode(int mode) {
        AppOpsManager ops = (AppOpsManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.APP_OPS_SERVICE);
        ShadowAppOpsManager saom = Shadows.shadowOf(ops);
        saom.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), "com.vanipy.menit", mode);
    }

    private void snapshot(MainActivity a, String outName) throws Exception {
        View v = a.getWindow().getDecorView();
        v.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
                  View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
        int h = Math.max(v.getMeasuredHeight(), H);
        v.layout(0, 0, W, h);
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        v.draw(new Canvas(bmp));
        File out = new File(System.getProperty("menit.snapshotDir", "build/ui-snapshots"), "ui-" + outName + ".png");
        out.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
        assertTrue(outName, out.length() > 5000);
    }

    @Test public void permissionScreenWhenDenied() throws Exception {
        setUsageMode(AppOpsManager.MODE_ERRORED);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        snapshot(a, "permission");
    }

    @Test public void dataScreenTodayAndWeek() throws Exception {
        setUsageMode(AppOpsManager.MODE_ALLOWED);
        long now = System.currentTimeMillis();
        UsageStatsManager usm = (UsageStatsManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.USAGE_STATS_SERVICE);
        ShadowUsageStatsManager susm = Shadows.shadowOf(usm);
        // ChatGPT 45 min, WhatsApp 30 min, a game 60 min, ending a minute ago
        long t = now - 137 * M;
        susm.addEvent("com.openai.chatgpt", t, Event.RESUMED);
        susm.addEvent("com.openai.chatgpt", t + 45 * M, Event.PAUSED);
        susm.addEvent("com.whatsapp", t + 45 * M, Event.RESUMED);
        susm.addEvent("com.whatsapp", t + 75 * M, Event.PAUSED);
        susm.addEvent("com.example.game", t + 75 * M, Event.RESUMED);
        susm.addEvent("com.example.game", t + 135 * M, Event.PAUSED);
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        snapshot(a, "data-today");
        Button week = findButtonWithText(a.getWindow().getDecorView(), "Last 7 days");
        assertNotNull("week tab exists", week);
        week.performClick();
        snapshot(a, "data-week");
    }

    private Button findButtonWithText(View root, String text) {
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View c = g.getChildAt(i);
                if (c instanceof Button && text.equals(((Button) c).getText().toString())) return (Button) c;
                Button r = findButtonWithText(c, text);
                if (r != null) return r;
            }
        }
        return null;
    }
}
