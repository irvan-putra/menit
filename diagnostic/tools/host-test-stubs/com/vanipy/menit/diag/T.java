package com.vanipy.menit.diag;
import android.content.Context; import android.os.SystemClock; import java.io.*; import java.lang.reflect.*; import java.nio.file.*; import java.util.*;
public class T {
  static int fails=0; static void chk(String n, boolean ok){ System.out.println((ok?"PASS ":"FAIL ")+n); if(!ok) fails++; }
  static DiagStore fresh(Context c) throws Exception { Field f=DiagStore.class.getDeclaredField("inst"); f.setAccessible(true); f.set(null,null); return DiagStore.get(c); }
  static Context ctx() throws Exception { Context c=new Context(); c.dir=Files.createTempDirectory("st").toFile(); return c; }
  public static void main(String[] a) throws Exception {
    // 1 archive while foreground, then clean close, reload
    Context c=ctx(); SystemClock.now=1000; DiagStore s=fresh(c); SystemClock.now=2000; s.fgStart(2000); SystemClock.now=3000; s.addObs(3000,Reconciler.Label.ALLOWLISTED,1);
    SystemClock.now=4000; s.archive(); chk("1 open session kept after archive", s.openFgStart()>=0);
    SystemClock.now=6000; s.fgEnd(6000); s=fresh(c);
    chk("1 after reload: one settled interval", s.fg.size()==1 && s.fg.get(0)[2]==1 && s.fg.get(0)[1]==6000);
    // 2 archive while foreground, then kill (no fgEnd), more heartbeat, reload
    c=ctx(); SystemClock.now=1000; s=fresh(c); s.fgStart(2000); SystemClock.now=4000; s.archive(); SystemClock.now=5000; s.addHeartbeat(5000); s=fresh(c);
    chk("2 kill after archive is seen as killed interval", s.killed==1 && s.fg.size()==1 && s.fg.get(0)[2]==0);
    // 3 archive while NOT foreground adds no FS
    c=ctx(); SystemClock.now=1000; s=fresh(c); s.addHeartbeat(1500); s.archive(); s=fresh(c);
    chk("3 archive in background: no killed", s.killed==0 && s.fg.isEmpty());
    // 4 rotation while foreground, then kill
    c=ctx(); SystemClock.now=1000; s=fresh(c); s.fgStart(1000);
    int i=0; File arch=new File(c.dir,"archive"); while(!arch.exists() && i<100000){ SystemClock.now=2000+i; s.addHeartbeat(SystemClock.now); i++; }
    chk("4 rotation happened", arch.exists());
    SystemClock.now+=10; s.addHeartbeat(SystemClock.now); long last=SystemClock.now; s=fresh(c);
    chk("4 kill after rotation seen: killed=1 and interval reaches last beat", s.killed==1 && s.fg.size()==1 && s.fg.get(0)[1]==last);
    // 5 rotation then clean close
    c=ctx(); SystemClock.now=1000; s=fresh(c); s.fgStart(1000); arch=new File(c.dir,"archive"); i=0; while(!arch.exists() && i<100000){ SystemClock.now=2000+i; s.addHeartbeat(SystemClock.now); i++; }
    SystemClock.now+=5; s.fgEnd(SystemClock.now); long end=SystemClock.now; s=fresh(c);
    chk("5 clean close after rotation: settled, start original, killed 0", s.killed==0 && s.fg.size()==1 && s.fg.get(0)[0]==1000 && s.fg.get(0)[2]==1 && s.fg.get(0)[1]==end);
    // 6 rotation lands exactly on FS write
    c=ctx(); SystemClock.now=1000; s=fresh(c); File f=new File(c.dir,"diag-log.csv"); i=0; while(f.length()<=256*1024){ SystemClock.now=2000+i; s.addHeartbeat(SystemClock.now); i++; }
    SystemClock.now+=5; s.fgStart(SystemClock.now); s.fgEnd(SystemClock.now+50); s=fresh(c);
    chk("6 rotation exactly at fgStart: no spurious kill", s.killed==0);
    // 7 rotation whose rename fails (archive path is a regular file): no FS spam, no spurious kills
    c=ctx(); SystemClock.now=1000; s=fresh(c); new File(c.dir,"archive").createNewFile(); s.fgStart(1000); f=new File(c.dir,"diag-log.csv");
    for(i=0;i<40000;i++){ SystemClock.now=2000+i; s.addHeartbeat(SystemClock.now); }
    long fs=0; for(String l:Files.readAllLines(f.toPath())) if(l.startsWith("FS,")) fs++;
    chk("7 rotation rename fails: exactly one FS line in the log", fs==1);
    s=fresh(c); chk("7 reload after failed rotation: killed=1 only", s.killed==1);
    System.out.println(fails==0?"ALL PASS":"FAILS="+fails);
  }
}
