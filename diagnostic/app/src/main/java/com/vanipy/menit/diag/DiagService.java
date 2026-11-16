package com.vanipy.menit.diag;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/**
 * Diagnostic reader. Reads only this app's own synthetic screens: the service config limits events to this
 * package, and the code checks the package of every node tree before looking at a single node.
 * The header text is compared with three synthetic names and then dropped. Only a typed label is stored.
 */
public class DiagService extends AccessibilityService {
    static final long HEARTBEAT_MS = 5_000;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Throttle throttle = new Throttle(1000);
    private final Runnable trailing = new Runnable() { @Override public void run() { if (throttle.onTimer(DiagStore.el())) pollOwnWindow(); } };
    private DiagStore store;

    private final Runnable beat = new Runnable() {
        @Override public void run() {
            store.addHeartbeat(DiagStore.el());
            SharedPreferences sp = getSharedPreferences("diag", MODE_PRIVATE);
            if (sp.getBoolean("poll", false)) pollOwnWindow();
            h.postDelayed(this, HEARTBEAT_MS);
        }
    };

    @Override protected void onServiceConnected() { super.onServiceConnected(); store = DiagStore.get(this); h.post(beat); }
    @Override public boolean onUnbind(android.content.Intent i) { h.removeCallbacks(beat); h.removeCallbacks(trailing); return super.onUnbind(i); }
    @Override public void onInterrupt() { }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (store == null || e == null || !DiagStore.PKG.contentEquals(String.valueOf(e.getPackageName()))) return;
        long now = DiagStore.el();
        store.addClock(e.getEventTime(), SystemClock.uptimeMillis(), now, System.currentTimeMillis());
        switch (throttle.onEvent(now)) {
            case READ_NOW: record(now, e.getSource()); break;
            case SCHEDULE: h.postDelayed(trailing, Math.max(0, throttle.dueAt() - now)); break;   // trailing read: last state of a burst is not dropped
            default: break;
        }
    }

    private void pollOwnWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;                                   // nothing visible to us
        if (!DiagStore.PKG.contentEquals(String.valueOf(root.getPackageName()))) return; // another app: touch nothing
        record(DiagStore.el(), root);
    }

    private void record(long el, AccessibilityNodeInfo node) {
        Reconciler.Label label = Reconciler.Label.UNRECOGNISED; int id = -1;
        if (node != null && DiagStore.PKG.contentEquals(String.valueOf(node.getPackageName()))) {
            AccessibilityNodeInfo root = node;
            while (root.getParent() != null) root = root.getParent();
            List<AccessibilityNodeInfo> hs = root.findAccessibilityNodeInfosByViewId(DiagStore.PKG + ":id/chat_header");
            if (hs != null && !hs.isEmpty() && hs.get(0).getText() != null) {
                String t = hs.get(0).getText().toString();
                if (t.equals("Synthetic chat A")) { label = Reconciler.Label.ALLOWLISTED; id = 1; }
                else if (t.equals("Synthetic chat B")) label = Reconciler.Label.NOT_ALLOWLISTED;
                // header text is dropped here
            }
        }
        store.addObs(el, label, id);
    }
}
