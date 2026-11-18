package com.vanipy.widgethost;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import java.util.*;

/** Disposable emulator-only host. No network permission, personal data, or release signing. */
public class HostActivity extends Activity {
    private static AppWidgetHost host;
    private AppWidgetHostView activeView;
    private int activeId, activeCellW, activeCellH;
    private final android.content.BroadcastReceiver resizeReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(android.content.Context c, android.content.Intent intent) {
            if (activeView == null) return;
            int x = intent.getIntExtra("spanX", 2), y = intent.getIntExtra("spanY", 1);
            android.view.ViewGroup.LayoutParams lp = activeView.getLayoutParams();
            lp.width = activeCellW*x; lp.height = activeCellH*y; activeView.setLayoutParams(lp);
            int w = Math.round(lp.width/getResources().getDisplayMetrics().density)-16;
            int h = Math.round(lp.height/getResources().getDisplayMetrics().density)-16;
            Bundle opts = new Bundle();
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,w);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,h);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h);
            AppWidgetManager.getInstance(HostActivity.this).updateAppWidgetOptions(activeId,opts);
            activeView.updateAppWidgetSize(opts, java.util.Collections.singletonList(new android.util.SizeF(w,h)));
            android.util.Log.i("WidgetQualification", "SAME_VIEW_RESIZE id="+activeId+" span="+x+"x"+y);
        }
    };
    private static final Map<String,Integer> ids = new HashMap<>();
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView label(String s, int sp) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(Color.WHITE); return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(resizeReceiver,
                new android.content.IntentFilter("com.vanipy.widgethost.RESIZE"), RECEIVER_EXPORTED);
        else registerReceiver(resizeReceiver,new android.content.IntentFilter("com.vanipy.widgethost.RESIZE"));
        boolean expanded = getIntent().getBooleanExtra("expanded", false);
        boolean strips = getIntent().getBooleanExtra("strips", false);
        boolean intermediate = getIntent().getBooleanExtra("intermediate", false);
        int single = getIntent().getIntExtra("single", -1);
        int spanX = getIntent().getIntExtra("spanX", 1);
        int spanY = getIntent().getIntExtra("spanY", 1);
        if (host == null) {
            host = new AppWidgetHost(getApplicationContext(), 5261);
            host.deleteHost();
        }
        host.startListening();
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(1);
        root.setPadding(dp(24), dp(12), dp(24), dp(12)); root.setBackgroundColor(Color.rgb(47,65,88));
        root.addView(label("5 × 6 grid · Android widget host", 17));
        root.addView(label(single >= 0 ? "Span " + spanX + " × " + spanY : intermediate ? "Six 2 × 2 widgets" : expanded ? "Expanded widgets · separate page" : strips ? "Six 5 × 1 slots" : "Six 1 × 1 counters", 13));
        root.addView(label("Synthetic profiles · not Samsung One UI", 11));
        GridLayout grid = new GridLayout(this); grid.setColumnCount(5); grid.setRowCount(6);
        int gridW = getResources().getDisplayMetrics().widthPixels - dp(48);
        int cellW = gridW / 5;
        int cellH = dp(96);
        root.addView(grid, new LinearLayout.LayoutParams(gridW, cellH * 6));
        String[] pkgs = {"com.vanipy.intodo", "com.vanipy.saku", "com.vanipy.menit", "com.vanipy.tumbuh", "com.vanipy.denyut", "com.vanipy.kemas"};
        List<AppWidgetProviderInfo> providers = mgr.getInstalledProviders();
        int added = 0;
        for (int i = 0; i < pkgs.length; i++) {
            AppWidgetProviderInfo info = null;
            for (AppWidgetProviderInfo p : providers) if (p.provider.getPackageName().equals(pkgs[i])) { info = p; break; }
            int cols = single >= 0 ? spanX : intermediate ? 2 : expanded || strips ? 5 : 1;
            int rows = single >= 0 ? spanY : intermediate ? 2 : expanded ? 3 : 1;
            int row = single >= 0 ? 0 : intermediate ? (i / 2) * 2 : expanded ? (i % 2) * 3 : strips ? i : i / 3;
            int col = single >= 0 ? 0 : intermediate ? (i % 2) * 2 : expanded || strips ? 0 : i % 3 + 1;
            if (single >= 0 && single != i) continue;
            if (expanded && i < getIntent().getIntExtra("start", 0)) continue;
            if (expanded && i >= getIntent().getIntExtra("start", 0) + 2) continue;
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(row, rows), GridLayout.spec(col, cols));
            lp.width = cellW * cols; lp.height = cellH * rows;
            if (info == null) { TextView missing=label(pkgs[i].substring(11)+"\nnot installed", 10);grid.addView(missing,lp);continue; }
            boolean fresh = !ids.containsKey(pkgs[i]);
            int id = fresh ? host.allocateAppWidgetId() : ids.get(pkgs[i]);
            Bundle opts = new Bundle();
            int contentW = Math.round(lp.width / getResources().getDisplayMetrics().density) - 16;
            int contentH = Math.round(lp.height / getResources().getDisplayMetrics().density) - 16;
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, contentW);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, contentW);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, contentH);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, contentH);
            if (fresh && !mgr.bindAppWidgetIdIfAllowed(id, info.provider, opts)) { root.addView(label("BIND FAILED: "+pkgs[i], 11));continue; }
            ids.put(pkgs[i], id);
            mgr.updateAppWidgetOptions(id, opts);
            AppWidgetHostView view = host.createView(this,id,info);
            grid.addView(view,lp);
            if (single >= 0) { activeView=view; activeId=id; activeCellW=cellW; activeCellH=cellH; }
            view.updateAppWidgetSize(opts, java.util.Collections.singletonList(new android.util.SizeF(
                    lp.width / getResources().getDisplayMetrics().density,
                    lp.height / getResources().getDisplayMetrics().density)));
            added++;
            android.util.Log.i("WidgetQualification", "BOUND " + pkgs[i]+" id="+id+" content="+contentW+"x"+contentH);
        }
        root.addView(label("Bound providers: "+added+" / "+(single >= 0 ? 1 : expanded ? 2 : 6), 12));
        setContentView(root);
        if (added != (single >= 0 ? 1 : expanded ? 2 : 6)) throw new IllegalStateException("Missing or unbound providers: " + added);
    }
    @Override protected void onDestroy() { unregisterReceiver(resizeReceiver);super.onDestroy(); }
}
