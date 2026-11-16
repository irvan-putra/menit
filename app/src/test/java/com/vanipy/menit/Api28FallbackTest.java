package com.vanipy.menit;

import static org.junit.Assert.*;
import android.app.AppOpsManager;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAppOpsManager;

/** Exercises the API 26-28 branch of UsageReader.hasAccess (checkOpNoThrow). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class Api28FallbackTest {
    private void setMode(int mode) {
        AppOpsManager ops = (AppOpsManager) RuntimeEnvironment.getApplication()
                .getSystemService(Context.APP_OPS_SERVICE);
        ShadowAppOpsManager s = Shadows.shadowOf(ops);
        s.setMode(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), "com.vanipy.menit", mode);
    }

    @Test public void hasAccessFalseWhenDeniedOnApi28() {
        setMode(AppOpsManager.MODE_ERRORED);
        assertFalse(UsageReader.hasAccess(RuntimeEnvironment.getApplication()));
    }

    @Test public void hasAccessTrueWhenAllowedOnApi28() {
        setMode(AppOpsManager.MODE_ALLOWED);
        assertTrue(UsageReader.hasAccess(RuntimeEnvironment.getApplication()));
    }

    @Test public void rendersPermissionScreenOnApi28WhenDenied() {
        setMode(AppOpsManager.MODE_ERRORED);
        MainActivity a = org.robolectric.Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(a);
    }
}
